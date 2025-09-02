const COMMANDS: &[&str] = &["sign_in", "sign_out", "refresh_token"];

fn main() {
    tauri_plugin::Builder::new(COMMANDS)
        .android_path("android")
        .ios_path("ios")
        .build();
}
