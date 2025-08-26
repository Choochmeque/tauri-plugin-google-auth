use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SignInRequest {
  pub client_id: String,
  #[serde(skip_serializing_if = "Option::is_none")]
  pub client_secret: Option<String>,
  #[serde(skip_serializing_if = "Option::is_none")]
  pub scopes: Option<Vec<String>>,
  #[serde(skip_serializing_if = "Option::is_none")]
  pub hosted_domain: Option<String>,
  #[serde(skip_serializing_if = "Option::is_none")]
  pub login_hint: Option<String>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TokenResponse {
  pub id_token: String,
  pub access_token: String,
  pub refresh_token: Option<String>,
  pub expires_at: Option<i64>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SignInResponse {
  pub id_token: String,
  pub access_token: String,
  pub refresh_token: Option<String>,
  pub expires_at: Option<i64>,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SignOutRequest {}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SignOutResponse {
  pub success: bool,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RefreshTokenRequest {}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RefreshTokenResponse {
  pub id_token: String,
  pub access_token: String,
  pub refresh_token: Option<String>,
  pub expires_at: Option<i64>,
}