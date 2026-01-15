use serde::{Deserialize, Serialize};

/// Request for Google Sign-In using Native SDK flow.
///
/// Note: client_secret is NOT required! The Native SDK flow (BeginSignInRequest)
/// returns idToken directly without needing to exchange authorization codes.
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SignInRequest {
    /// Google OAuth2 Web Client ID from Google Cloud Console
    pub client_id: String,
    /// OAuth2 scopes to request (e.g., ["email", "profile"])
    #[serde(skip_serializing_if = "Option::is_none")]
    pub scopes: Option<Vec<String>>,
    /// Restrict sign-in to a specific G Suite domain
    #[serde(skip_serializing_if = "Option::is_none")]
    pub hosted_domain: Option<String>,
    /// Pre-fill the email field in the sign-in flow
    #[serde(skip_serializing_if = "Option::is_none")]
    pub login_hint: Option<String>,
}

/// Response from Google Sign-In using Native SDK flow.
///
/// Note: This flow returns ONLY id_token. Unlike the authorization code flow,
/// access_token and refresh_token are NOT available because we use BeginSignInRequest
/// which returns the idToken directly from Google's native SDK.
#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TokenResponse {
    /// JWT ID token containing user information, cryptographically signed by Google
    pub id_token: String,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SignOutRequest {}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SignOutResponse {
    pub success: bool,
}
