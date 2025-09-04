use tauri::{command, AppHandle, Runtime};

use crate::models::*;
use crate::GoogleAuthExt;
use crate::Result;

#[command]
pub(crate) async fn sign_in<R: Runtime>(
    app: AppHandle<R>,
    payload: SignInRequest,
) -> Result<crate::TokenResponse> {
    app.google_auth().sign_in(payload)
}

#[command]
pub(crate) async fn sign_out<R: Runtime>(
    app: AppHandle<R>,
    payload: SignOutRequest,
) -> Result<SignOutResponse> {
    app.google_auth().sign_out(payload)
}

#[command]
pub(crate) async fn refresh_token<R: Runtime>(
    app: AppHandle<R>,
    payload: RefreshTokenRequest,
) -> Result<TokenResponse> {
    app.google_auth().refresh_token(payload)
}
