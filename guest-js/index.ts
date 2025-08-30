import { invoke } from '@tauri-apps/api/core'

export interface TokenResponse {
  idToken: string
  accessToken: string
  refreshToken?: string
  expiresAt?: number
}

export interface SignInOptions {
  clientId: string
  clientSecret?: string
  scopes?: string[]
  hostedDomain?: string
  loginHint?: string
  redirectUri?: string
}

export interface SignInResponse {
  idToken: string
  accessToken: string
  refreshToken?: string
  expiresAt?: number
}

export interface RefreshTokenResponse {
  idToken: string
  accessToken: string
  refreshToken?: string
  expiresAt?: number
}

export async function signIn(options: SignInOptions): Promise<TokenResponse> {
  const response = await invoke<SignInResponse>('plugin:google-auth|sign_in', {
    payload: options,
  });
  return response;
}

export async function signOut(): Promise<void> {
  await invoke('plugin:google-auth|sign_out', {
    payload: {},
  });
}

export async function refreshToken(): Promise<TokenResponse> {
  const response = await invoke<RefreshTokenResponse>('plugin:google-auth|refresh_token', {
    payload: {},
  });
  return response;
}