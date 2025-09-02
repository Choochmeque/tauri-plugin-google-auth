import { invoke } from '@tauri-apps/api/core'

/**
 * Response containing authentication tokens from Google OAuth2
 */
export interface TokenResponse {
  /** JWT ID token containing user information (requires 'openid' scope) */
  idToken: string
  /** Access token for making API requests */
  accessToken: string
  /** Refresh token for obtaining new access tokens (optional) */
  refreshToken?: string
  /** Unix timestamp (seconds) when the access token expires */
  expiresAt?: number
}

/**
 * Configuration options for Google OAuth2 sign-in
 */
export interface SignInOptions {
  /** Google OAuth2 client ID from Google Cloud Console */
  clientId: string
  /** Google OAuth2 client secret (required for desktop platforms) */
  clientSecret?: string
  /** List of OAuth2 scopes to request (e.g., ['openid', 'email', 'profile']) */
  scopes?: string[]
  /** Restrict sign-in to a specific G Suite domain */
  hostedDomain?: string
  /** Pre-fill the email field in the sign-in flow */
  loginHint?: string
  /** Custom redirect URI (defaults to localhost with random port on desktop) */
  redirectUri?: string
  /** Custom HTML message shown after successful authentication (desktop only) */
  successHtmlResponse?: string
}

/**
 * Response from a successful sign-in operation
 * @internal Used internally, same as TokenResponse
 */
export interface SignInResponse {
  idToken: string
  accessToken: string
  refreshToken?: string
  expiresAt?: number
}

/**
 * Response from a token refresh operation
 * @internal Used internally, same as TokenResponse
 */
export interface RefreshTokenResponse {
  idToken: string
  accessToken: string
  refreshToken?: string
  expiresAt?: number
}

/**
 * Initiates Google OAuth2 sign-in flow
 * 
 * @param options - Configuration for the sign-in flow
 * @returns Promise that resolves with authentication tokens
 * 
 * @example
 * ```typescript
 * const tokens = await signIn({
 *   clientId: 'your-client-id',
 *   clientSecret: 'your-client-secret', // Required on desktop
 *   scopes: ['openid', 'email', 'profile'],
 *   successHtmlResponse: '<h1>Success!</h1><p>You can close this window.</p>'
 * })
 * console.log('Access token:', tokens.accessToken)
 * ```
 * 
 * @throws {Error} If authentication fails or user cancels the flow
 */
export async function signIn(options: SignInOptions): Promise<TokenResponse> {
  const response = await invoke<SignInResponse>('plugin:google-auth|sign_in', {
    payload: options,
  });
  return response;
}

/**
 * Options for signing out
 */
export interface SignOutOptions {
  /** Access token to revoke with Google (if not provided, performs local sign-out only) */
  accessToken?: string
}

/**
 * Signs out the current user
 * 
 * @param options - Optional configuration for sign-out
 * @returns Promise that resolves when sign-out is complete
 * 
 * @example
 * ```typescript
 * // Revoke token with Google
 * await signOut({ accessToken: 'current-access-token' })
 * 
 * // Local sign-out only (no server call)
 * await signOut()
 * ```
 */
export async function signOut(options?: SignOutOptions): Promise<void> {
  await invoke('plugin:google-auth|sign_out', {
    payload: options || {},
  });
}

/**
 * Refreshes the access token using a refresh token
 * 
 * @returns Promise that resolves with new authentication tokens
 * 
 * @example
 * ```typescript
 * const newTokens = await refreshToken()
 * console.log('New access token:', newTokens.accessToken)
 * ```
 * 
 * @throws {Error} If refresh token is invalid or expired
 * @note Not yet implemented for desktop platforms
 */
export async function refreshToken(): Promise<TokenResponse> {
  const response = await invoke<RefreshTokenResponse>('plugin:google-auth|refresh_token', {
    payload: {},
  });
  return response;
}