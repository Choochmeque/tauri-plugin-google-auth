use serde::de::DeserializeOwned;
use tauri::{plugin::PluginApi, AppHandle, Runtime};

use oauth2::{basic::BasicClient, TokenResponse};
use oauth2::{
    AuthUrl, AuthorizationCode, ClientId, ClientSecret, CsrfToken, PkceCodeChallenge, RedirectUrl,
    RevocationUrl, Scope, TokenUrl,
};
use url::Url;
use uuid::Uuid;

use std::io::{BufRead, BufReader, Write};
use std::net::TcpListener;

use crate::models::*;

// Google OAuth2 URL constants
const GOOGLE_AUTH_URL: &str = "https://accounts.google.com/o/oauth2/auth";
const GOOGLE_TOKEN_URL: &str = "https://oauth2.googleapis.com/token";
const GOOGLE_REVOCATION_URL: &str = "https://oauth2.googleapis.com/revoke";
const LOCALHOST_ADDR: &str = "127.0.0.1";
const DEFAULT_REDIRECT_HOST: &str = "localhost";
const SUCCESS_HTML_RESPONSE: &str = "Go back to your app :)";

pub fn init<R: Runtime, C: DeserializeOwned>(
    app: &AppHandle<R>,
    _api: PluginApi<R, C>,
) -> crate::Result<GoogleAuth<R>> {
    Ok(GoogleAuth(app.clone()))
}

/// Access to the google-auth APIs.
pub struct GoogleAuth<R: Runtime>(AppHandle<R>);

impl<R: Runtime> GoogleAuth<R> {
    pub fn sign_in(&self, payload: SignInRequest) -> crate::Result<SignInResponse> {
        // Validate that scopes are provided
        let scopes = payload.scopes.ok_or_else(|| {
            crate::Error::ConfigurationError(
                "No scopes provided. At least one scope is required for authentication".to_string(),
            )
        })?;

        if scopes.is_empty() {
            return Err(crate::Error::ConfigurationError(
                "Empty scopes array. At least one scope is required for authentication".to_string(),
            ));
        }

        // Parse redirect URI and extract port if provided
        let (redirect_host, port) = if let Some(redirect_uri) = &payload.redirect_uri {
            let parsed_url = Url::parse(redirect_uri).map_err(|e| {
                crate::Error::ConfigurationError(format!("Invalid redirect URI: {}", e))
            })?;

            let host = parsed_url.host_str().ok_or_else(|| {
                crate::Error::ConfigurationError("Redirect URI must have a host".to_string())
            })?;

            // Validate that it's localhost or 127.0.0.1
            if host != DEFAULT_REDIRECT_HOST && host != LOCALHOST_ADDR {
                return Err(crate::Error::ConfigurationError(
                    "Redirect URI must use localhost or 127.0.0.1 for desktop authentication"
                        .to_string(),
                ));
            }

            (host.to_string(), parsed_url.port())
        } else {
            // Default to localhost with no specific port (will bind to random available port)
            (DEFAULT_REDIRECT_HOST.to_string(), None)
        };

        let google_client_id = ClientId::new(payload.client_id);
        let google_client_secret = payload.client_secret.ok_or_else(|| {
            crate::Error::ConfigurationError(
                "Client secret is required for desktop authentication".to_string(),
            )
        })?;
        let google_client_secret = ClientSecret::new(google_client_secret);
        let auth_url = AuthUrl::new(GOOGLE_AUTH_URL.to_string()).map_err(|_| {
            crate::Error::ConfigurationError("Invalid authorization endpoint URL".to_string())
        })?;
        let token_url = TokenUrl::new(GOOGLE_TOKEN_URL.to_string()).map_err(|_| {
            crate::Error::ConfigurationError("Invalid token endpoint URL".to_string())
        })?;

        // Bind to the TCP listener first to get the actual port
        let listener = if let Some(p) = port {
            // Try to bind to the specific port
            TcpListener::bind(format!("{}:{}", LOCALHOST_ADDR, p)).map_err(|e| {
                crate::Error::NetworkError(format!("Failed to bind to port {}: {}", p, e))
            })?
        } else {
            // Bind to any available port (port 0 means OS assigns an available port)
            TcpListener::bind(format!("{}:0", LOCALHOST_ADDR)).map_err(|e| {
                crate::Error::NetworkError(format!("Failed to bind to any available port: {}", e))
            })?
        };

        // Get the actual port that was bound
        let actual_port = listener
            .local_addr()
            .map_err(|e| crate::Error::NetworkError(format!("Failed to get local address: {}", e)))?
            .port();

        // Construct the redirect URL with the actual port
        let redirect_url = format!("http://{}:{}", redirect_host, actual_port);

        // Set up the config for the Google OAuth2 process.
        let client = BasicClient::new(google_client_id)
            .set_client_secret(google_client_secret)
            .set_auth_uri(auth_url)
            .set_token_uri(token_url)
            .set_redirect_uri(RedirectUrl::new(redirect_url.clone()).map_err(|_| {
                crate::Error::ConfigurationError("Invalid redirect URL".to_string())
            })?)
            // Google supports OAuth 2.0 Token Revocation (RFC-7009)
            .set_revocation_url(
                RevocationUrl::new(GOOGLE_REVOCATION_URL.to_string()).map_err(|_| {
                    crate::Error::ConfigurationError("Invalid revocation endpoint URL".to_string())
                })?,
            );

        // Google supports Proof Key for Code Exchange (PKCE - https://oauth.net/2/pkce/).
        // Create a PKCE code verifier and SHA-256 encode it as a code challenge.
        let (pkce_code_challenge, pkce_code_verifier) = PkceCodeChallenge::new_random_sha256();

        // Generate the authorization URL to which we'll redirect the user.
        let mut auth_url_builder = client.authorize_url(CsrfToken::new_random);

        // Add all the scopes from the payload
        for scope in scopes {
            auth_url_builder = auth_url_builder.add_scope(Scope::new(scope));
        }

        let (authorize_url, _csrf_state) = auth_url_builder
            .set_pkce_challenge(pkce_code_challenge)
            .url();

        // Open the authorization URL in the browser
        open::that(authorize_url.to_string())
            .map_err(|e| crate::Error::NetworkError(format!("Failed to open browser: {}", e)))?;

        // Get the success HTML response message (use custom if provided, otherwise default)
        let success_message = payload
            .success_html_response
            .as_deref()
            .unwrap_or(SUCCESS_HTML_RESPONSE);

        let (code, _state) = {
            // The server will terminate itself after collecting the first code.
            let mut stream = listener.incoming().flatten().next().ok_or_else(|| {
                crate::Error::NetworkError(
                    "Listener terminated without accepting a connection".to_string(),
                )
            })?;

            let mut reader = BufReader::new(&stream);

            let mut request_line = String::new();
            reader.read_line(&mut request_line)?;

            let request_path = request_line.split_whitespace().nth(1).ok_or_else(|| {
                crate::Error::NetworkError("Invalid HTTP request format".to_string())
            })?;
            let url = Url::parse(&(format!("http://{}{}", DEFAULT_REDIRECT_HOST, request_path)))
                .map_err(|e| {
                    crate::Error::NetworkError(format!("Failed to parse redirect URL: {}", e))
                })?;

            let code = url
                .query_pairs()
                .find(|(key, _)| key == "code")
                .map(|(_, code)| AuthorizationCode::new(code.into_owned()))
                .ok_or_else(|| {
                    crate::Error::AuthenticationFailed(
                        "Authorization code not found in response".to_string(),
                    )
                })?;

            let state = url
                .query_pairs()
                .find(|(key, _)| key == "state")
                .map(|(_, state)| CsrfToken::new(state.into_owned()))
                .ok_or_else(|| {
                    crate::Error::AuthenticationFailed(
                        "State parameter not found in response".to_string(),
                    )
                })?;

            let response = format!(
                "HTTP/1.1 200 OK\r\ncontent-length: {}\r\n\r\n{}",
                success_message.len(),
                success_message
            );
            stream.write_all(response.as_bytes())?;

            (code, state)
        };

        let token_response = std::thread::spawn(move || -> crate::Result<_> {
            // Create HTTP client with proper security settings
            let http_client = oauth2::reqwest::blocking::Client::builder()
                // Following redirects opens the client up to SSRF vulnerabilities
                .redirect(oauth2::reqwest::redirect::Policy::none())
                .build()
                .map_err(|e| {
                    crate::Error::NetworkError(format!("Failed to build HTTP client: {}", e))
                })?;

            // Exchange the code with a token.
            let token_response = client
                .exchange_code(code)
                .set_pkce_verifier(pkce_code_verifier)
                .request(&http_client)
                .map_err(|e| {
                    crate::Error::AuthenticationFailed(format!(
                        "Failed to exchange code for token: {}",
                        e
                    ))
                })?;

            Ok(token_response)
        })
        .join()
        .map_err(|_| {
            crate::Error::AuthenticationFailed("Token exchange thread panicked".to_string())
        })??;

        // Return the token response
        Ok(SignInResponse {
            id_token: Uuid::now_v7().to_string(), // Generate a UUID v7 for id_token
            access_token: token_response.access_token().secret().to_string(),
            refresh_token: token_response
                .refresh_token()
                .map(|t| t.secret().to_string()),
            expires_at: token_response.expires_in().map(|d| {
                let now = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_secs() as i64;
                now + d.as_secs() as i64
            }),
        })
    }

    pub fn sign_out(&self, payload: SignOutRequest) -> crate::Result<SignOutResponse> {
        // If no access token provided, just return success (local sign out)
        let Some(access_token) = payload.access_token else {
            return Ok(SignOutResponse { success: true });
        };

        // Revoke the token with Google
        let response = std::thread::spawn(move || -> crate::Result<_> {
            // Create HTTP client
            let http_client = oauth2::reqwest::blocking::Client::builder()
                .redirect(oauth2::reqwest::redirect::Policy::none())
                .build()
                .map_err(|e| {
                    crate::Error::NetworkError(format!("Failed to build HTTP client: {}", e))
                })?;

            // Send revocation request
            let response = http_client
                .post(GOOGLE_REVOCATION_URL)
                .form(&[("token", access_token.as_str())])
                .send()
                .map_err(|e| {
                    crate::Error::NetworkError(format!("Failed to revoke token: {}", e))
                })?;

            Ok(response)
        })
        .join()
        .map_err(|_| {
            crate::Error::AuthenticationFailed("Token exchange thread panicked".to_string())
        })??;

        // Check if revocation was successful
        if response.status().is_success() {
            Ok(SignOutResponse { success: true })
        } else {
            // Even if revocation fails, we can still return success
            // as the token might already be invalid or expired
            Ok(SignOutResponse { success: true })
        }
    }

    pub fn refresh_token(
        &self,
        _payload: RefreshTokenRequest,
    ) -> crate::Result<RefreshTokenResponse> {
        Err(crate::Error::ConfigurationError(
            "Google Sign-In refresh token is not implemented for desktop platforms yet".to_string(),
        ))
    }
}
