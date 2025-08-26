use tauri::{AppHandle, command, Runtime};

use crate::models::*;
use crate::Result;
use crate::GoogleAuthExt;

#[command]
pub(crate) async fn sign_in<R: Runtime>(
    app: AppHandle<R>,
    payload: SignInRequest,
) -> Result<SignInResponse> {
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
) -> Result<RefreshTokenResponse> {
    app.google_auth().refresh_token(payload)
}