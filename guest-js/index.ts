import { invoke } from "@tauri-apps/api/core";

/**
 * Response from Google Sign-In using Native SDK flow.
 *
 * Note: This flow returns ONLY idToken. Unlike the authorization code flow,
 * accessToken and refreshToken are NOT available because we use BeginSignInRequest
 * which returns the idToken directly from Google's native SDK.
 *
 * The idToken should be sent to your backend for verification and session creation.
 */
export interface TokenResponse {
  /** JWT ID token containing user information, cryptographically signed by Google */
  idToken: string;
}

/**
 * Configuration options for Google Sign-In.
 *
 * Note: clientSecret is NOT required! The Native SDK flow returns idToken directly
 * without needing to exchange authorization codes.
 */
export interface SignInOptions {
  /**
   * Google OAuth2 Web Client ID from Google Cloud Console.
   * Use the "Web application" type client ID, NOT "Android" type.
   */
  clientId: string;
  /**
   * List of OAuth2 scopes to request.
   * Common scopes: ['email', 'profile']
   * Note: 'openid' is implicitly included.
   */
  scopes?: string[];
  /** Restrict sign-in to a specific G Suite domain */
  hostedDomain?: string;
  /** Pre-fill the email field in the sign-in flow */
  loginHint?: string;
}

/**
 * Initiates Google Sign-In using Native SDK flow (BeginSignInRequest).
 *
 * This flow is recommended for mobile apps because:
 * - NO client_secret required (secure for mobile!)
 * - Returns idToken directly from Google's native SDK
 * - Native Google Sign-In UI (not a webview)
 *
 * @param options - Configuration for the sign-in flow
 * @returns Promise that resolves with { idToken: string }
 *
 * @example
 * ```typescript
 * // 1. Get idToken from native SDK
 * const { idToken } = await signIn({
 *   clientId: 'YOUR_WEB_CLIENT_ID.apps.googleusercontent.com',
 *   scopes: ['email', 'profile']
 * });
 *
 * // 2. Send idToken to your backend for verification
 * // Example with Better Auth:
 * await authClient.signIn.social({
 *   provider: 'google',
 *   idToken: { token: idToken }
 * });
 *
 * // Example with custom backend:
 * await fetch('/api/auth/google', {
 *   method: 'POST',
 *   headers: { 'Content-Type': 'application/json' },
 *   body: JSON.stringify({ idToken })
 * });
 * ```
 *
 * @throws {Error} If authentication fails or user cancels the flow
 */
export async function signIn(options: SignInOptions): Promise<TokenResponse> {
  const response = await invoke<TokenResponse>("plugin:google-auth|sign_in", {
    payload: options,
  });
  return response;
}

/**
 * Signs out the current user.
 *
 * Clears the cached Google account so the user will be prompted
 * to select an account on the next sign-in.
 *
 * @returns Promise that resolves when sign-out is complete
 *
 * @example
 * ```typescript
 * await signOut();
 * // User will need to select account on next signIn()
 * ```
 */
export async function signOut(): Promise<void> {
  await invoke("plugin:google-auth|sign_out", {
    payload: {},
  });
}
