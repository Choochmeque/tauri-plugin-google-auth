use tauri::{AppHandle, Runtime, command};

use crate::GoogleAuthExt;
use crate::Result;
use crate::models::{
    RefreshTokenRequest, SignInRequest, SignOutRequest, SignOutResponse, TokenResponse,
};

#[command]
pub async fn sign_in<R: Runtime>(
    app: AppHandle<R>,
    payload: SignInRequest,
) -> Result<TokenResponse> {
    app.google_auth().sign_in(payload)
}

#[command]
pub async fn sign_out<R: Runtime>(
    app: AppHandle<R>,
    payload: SignOutRequest,
) -> Result<SignOutResponse> {
    app.google_auth().sign_out(payload)
}

#[command]
pub async fn refresh_token<R: Runtime>(
    app: AppHandle<R>,
    payload: RefreshTokenRequest,
) -> Result<TokenResponse> {
    app.google_auth().refresh_token(payload)
}
