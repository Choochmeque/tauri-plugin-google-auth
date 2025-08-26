use serde::{ser::Serializer, Serialize};

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug, thiserror::Error)]
pub enum Error {
  #[error(transparent)]
  Io(#[from] std::io::Error),
  #[cfg(mobile)]
  #[error(transparent)]
  PluginInvoke(#[from] tauri::plugin::mobile::PluginInvokeError),
  #[error("Authentication failed: {0}")]
  AuthenticationFailed(String),
  #[error("User cancelled the sign-in flow")]
  UserCancelled,
  #[error("No user is currently signed in")]
  NoUserSignedIn,
  #[error("Invalid client ID provided")]
  InvalidClientId,
  #[error("Token refresh failed: {0}")]
  TokenRefreshFailed(String),
  #[error("Network error: {0}")]
  NetworkError(String),
  #[error("Configuration error: {0}")]
  ConfigurationError(String),
}

impl Serialize for Error {
  fn serialize<S>(&self, serializer: S) -> std::result::Result<S::Ok, S::Error>
  where
    S: Serializer,
  {
    serializer.serialize_str(self.to_string().as_ref())
  }
}