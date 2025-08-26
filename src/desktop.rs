use serde::de::DeserializeOwned;
use tauri::{plugin::PluginApi, AppHandle, Runtime};

use crate::models::*;

pub fn init<R: Runtime, C: DeserializeOwned>(
  app: &AppHandle<R>,
  _api: PluginApi<R, C>,
) -> crate::Result<GoogleAuth<R>> {
  Ok(GoogleAuth(app.clone()))
}

/// Access to the google-auth APIs.
pub struct GoogleAuth<R: Runtime>(AppHandle<R>);

impl<R: Runtime> GoogleAuth<R> {
  pub fn sign_in(&self, _payload: SignInRequest) -> crate::Result<SignInResponse> {
    Err(crate::Error::ConfigurationError(
      "Google Sign-In is not implemented for desktop platforms yet".to_string()
    ))
  }
  
  pub fn sign_out(&self, _payload: SignOutRequest) -> crate::Result<SignOutResponse> {
    Err(crate::Error::ConfigurationError(
      "Google Sign-In is not implemented for desktop platforms yet".to_string()
    ))
  }
  
  pub fn refresh_token(&self, _payload: RefreshTokenRequest) -> crate::Result<RefreshTokenResponse> {
    Err(crate::Error::ConfigurationError(
      "Google Sign-In is not implemented for desktop platforms yet".to_string()
    ))
  }
}