use serde::de::DeserializeOwned;
use tauri::{
    AppHandle, Runtime,
    plugin::{PluginApi, PluginHandle},
};

use crate::models::{
    RefreshTokenRequest, SignInRequest, SignOutRequest, SignOutResponse, TokenResponse,
};

#[cfg(target_os = "ios")]
tauri::ios_plugin_binding!(init_plugin_google_auth);

// initializes the Kotlin or Swift plugin classes
#[allow(clippy::needless_pass_by_value)]
pub fn init<R: Runtime, C: DeserializeOwned>(
    _app: &AppHandle<R>,
    api: PluginApi<R, C>,
) -> crate::Result<GoogleAuth<R>> {
    #[cfg(target_os = "android")]
    let handle = api.register_android_plugin("app.tauri.googleauth", "GoogleSignInPlugin")?;
    #[cfg(target_os = "ios")]
    let handle = api.register_ios_plugin(init_plugin_google_auth)?;
    Ok(GoogleAuth(handle))
}

/// Access to the google-auth APIs.
pub struct GoogleAuth<R: Runtime>(PluginHandle<R>);

impl<R: Runtime> GoogleAuth<R> {
    pub fn sign_in(&self, payload: SignInRequest) -> crate::Result<TokenResponse> {
        self.0
            .run_mobile_plugin("signIn", payload)
            .map_err(Into::into)
    }

    pub fn sign_out(&self, payload: SignOutRequest) -> crate::Result<SignOutResponse> {
        self.0
            .run_mobile_plugin("signOut", payload)
            .map_err(Into::into)
    }

    pub fn refresh_token(&self, payload: RefreshTokenRequest) -> crate::Result<TokenResponse> {
        self.0
            .run_mobile_plugin("refreshToken", payload)
            .map_err(Into::into)
    }
}
