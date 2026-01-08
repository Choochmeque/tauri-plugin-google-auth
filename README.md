# Tauri Plugin Google Auth

[![Crates.io](https://img.shields.io/crates/v/tauri-plugin-google-auth)](https://crates.io/crates/tauri-plugin-google-auth)
[![npm](https://img.shields.io/npm/v/@choochmeque/tauri-plugin-google-auth-api)](https://www.npmjs.com/package/@choochmeque/tauri-plugin-google-auth-api)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A Tauri v2 plugin for Google OAuth authentication, providing seamless Google Sign-In integration for mobile and desktop applications.

## Features

- **Secure OAuth 2.0 Authentication** - Full OAuth 2.0 implementation with PKCE support
- **Mobile Support** - Native iOS and Android implementations using platform-specific APIs
- **Desktop Support** - OAuth2 flow with local redirect server for macOS, Windows, and Linux
- **Token Management** - Token refresh and revocation support
- **Security First** - PKCE, secure redirect handling, and proper error management
- **Flexible Configuration** - Customizable redirect URIs, HTML responses, and dynamic port binding

## Installation

### Rust

Add the plugin to your `Cargo.toml`:

```toml
[dependencies]
tauri-plugin-google-auth = "0.3"
```

### JavaScript/TypeScript

Install the JavaScript API package:

```bash
npm install @choochmeque/tauri-plugin-google-auth-api
# or
yarn add @choochmeque/tauri-plugin-google-auth-api
# or
pnpm add @choochmeque/tauri-plugin-google-auth-api
```

## Configuration

### 1. Register the Plugin

In your Tauri app's `src-tauri/src/lib.rs`:

```rust
use tauri_plugin_google_auth;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_google_auth::init())
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

### 2. Configure Permissions

Add to your `src-tauri/capabilities/default.json`:

```json
{
  "permissions": [
    "google-auth:default"
  ]
}
```

### 3. Platform-Specific Setup

#### iOS Setup

1. **Configure Google Sign-In**:
   - Add your Google OAuth client ID to your app
   - Configure URL schemes in `Info.plist`
   - See [iOS_SETUP.md](iOS_SETUP.md) for detailed instructions

2. **Required Info.plist entries**:
```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>YOUR_REVERSED_CLIENT_ID</string>
        </array>
    </dict>
</array>
```

#### Android Setup

**Configure Google Cloud Console**:
   - Create OAuth 2.0 credentials
   - Add your app's SHA-1 fingerprint
   - Configure authorized redirect URIs

See [ANDROID_SETUP.md](ANDROID_SETUP.md) for complete setup instructions.

#### Desktop Setup (macOS, Windows, Linux)

**Configure Google Cloud Console**:
   - Create OAuth 2.0 credentials (Web application type)
   - Add `http://localhost` to authorized redirect URIs
   - Note: The plugin handles dynamic port allocation automatically

**Required fields for desktop**:
   - `clientId`: Your Google OAuth client ID
   - `clientSecret`: Your Google OAuth client secret (required for desktop)
   - `scopes`: At least one scope is required

The desktop implementation uses a local redirect server that:
   - Binds to an available port (or specific port if provided via `redirectUri`)
   - Opens the authorization URL in the default browser
   - Captures the authorization code from the redirect
   - Displays a customizable success message to the user

## Usage

### Basic Example

```typescript
import { signIn, signOut, refreshToken } from '@choochmeque/tauri-plugin-google-auth-api';

// Sign in with Google
async function authenticateUser() {
  try {
    const response = await signIn({
      clientId: 'YOUR_GOOGLE_CLIENT_ID',
      clientSecret: 'YOUR_CLIENT_SECRET', // Required for desktop platforms
      scopes: ['openid', 'email', 'profile'],
      hostedDomain: 'example.com', // Optional: restrict to specific domain
      loginHint: 'user@example.com', // Optional: pre-fill email
      redirectUri: 'http://localhost:8080', // Optional: specify custom redirect URI
      successHtmlResponse: '<h1>Success!</h1>' // Optional: custom success message (desktop)
    })
    
    console.log('ID Token:', response.idToken);
    console.log('Access Token:', response.accessToken);
    console.log('Refresh Token:', response.refreshToken);
    console.log('Expires at:', new Date(response.expiresAt * 1000));
  } catch (error) {
    console.error('Authentication failed:', error);
  }
}

// Sign out
async function logout(accessToken?: string) {
  try {
    // With token revocation (recommended)
    await signOut({ accessToken });
    // Or local sign-out only
    // await signOut();
    console.log('Successfully signed out');
  } catch (error) {
    console.error('Sign out failed:', error);
  }
}

// Refresh tokens
async function refreshUserToken(storedRefreshToken: string) {
  try {
    const response = await refreshToken({
      refreshToken: storedRefreshToken,
      clientId: 'YOUR_GOOGLE_CLIENT_ID',
      clientSecret: 'YOUR_CLIENT_SECRET' // Required for desktop
    });
    console.log('New Access Token:', response.accessToken);
  } catch (error) {
    console.error('Token refresh failed:', error);
  }
}
```

### Advanced Configuration

```typescript
import { signIn } from '@choochmeque/tauri-plugin-google-auth-api';

const response = await signIn({
  clientId: 'YOUR_CLIENT_ID',
  clientSecret: 'YOUR_CLIENT_SECRET', // Required for desktop
  scopes: [
    'openid',
    'email',
    'profile',
    'https://www.googleapis.com/auth/drive.readonly'
  ],
  hostedDomain: 'company.com', // Restrict to company domain
  loginHint: 'john.doe@company.com', // Pre-fill the email field
  redirectUri: 'http://localhost:9000' // Custom port (desktop only)
});
```

## API Reference

### Types

#### `SignInOptions`

```typescript
interface SignInOptions {
  clientId: string;              // Required: Google OAuth client ID
  clientSecret?: string;         // Required for desktop platforms
  scopes?: string[];             // OAuth scopes to request
  hostedDomain?: string;         // Restrict authentication to a specific domain
  loginHint?: string;            // Email hint to pre-fill in the sign-in form
  redirectUri?: string;          // Custom redirect URI (desktop: localhost only)
  successHtmlResponse?: string;  // Custom HTML shown after auth (desktop only)
}
```

#### `TokenResponse`

```typescript
interface TokenResponse {
  idToken?: string;          // JWT ID token (requires 'openid' scope)
  accessToken: string;       // OAuth access token for API calls
  scopes: string[];          // List of scopes granted with the access token
  refreshToken?: string;     // Refresh token (when offline access is granted)
  expiresAt?: number;        // Token expiration timestamp (seconds since epoch)
}
```

### Functions

#### `signIn(options: SignInOptions): Promise<TokenResponse>`
Initiates the Google Sign-In flow with the specified options.

#### `signOut(options?: SignOutOptions): Promise<void>`
Signs out the current user. Can optionally revoke the access token with Google.

```typescript
interface SignOutOptions {
  accessToken?: string;  // Token to revoke (if not provided, local sign-out only)
}
```

#### `refreshToken(options: RefreshTokenOptions): Promise<TokenResponse>`
Refreshes the access token using a refresh token.

```typescript
interface RefreshTokenOptions {
  refreshToken: string;    // Refresh token obtained from initial sign-in
  clientId: string;        // Google OAuth client ID
  clientSecret?: string;   // Client secret (required for desktop)
}
```

## Error Handling

```typescript
try {
  await signIn({ clientId: 'YOUR_CLIENT_ID', scopes: ['openid'] });
} catch (error) {
  console.error('Sign-in failed:', error);
}
```

## Platform Support

| Platform | Status | Implementation |
|----------|--------|---------------|
| iOS      | Supported | Native Google Sign-In SDK via [SimpleGoogleSignIn](https://github.com/Choochmeque/SimpleGoogleSignIn) |
| Android  | Supported | Credential Manager API |
| macOS    | Supported | OAuth2 with local redirect server |
| Windows  | Supported | OAuth2 with local redirect server |
| Linux    | Supported | OAuth2 with local redirect server |

## Security Considerations

- **Token Storage**: Tokens are stored securely using platform-specific encryption
  - iOS: Keychain Services
  - Android: Encrypted SharedPreferences
  - Desktop: Application memory (implement secure storage as needed)
- **HTTPS Only**: All OAuth flows use HTTPS for secure communication
- **PKCE**: Implements Proof Key for Code Exchange for enhanced security on all platforms
- **SSRF Protection**: HTTP client configured to prevent redirect vulnerabilities
- **Dynamic Port Binding**: Desktop platforms use random available ports by default
- **Token Revocation**: Supports proper token revocation with Google's revocation endpoint

## Troubleshooting

### Common Issues

#### iOS: "User cancelled" error immediately after clicking sign-in
- Ensure your URL schemes are properly configured in Info.plist
- Verify your client ID is correct and matches your Google Cloud Console configuration

#### Android: "Configuration error" on sign-in
- Check that your SHA-1 fingerprint is added to Google Cloud Console
- Ensure your package name matches the one in Google Cloud Console
- Verify internet permissions are granted

#### Desktop: Token refresh fails
- Ensure you pass `clientId` and `clientSecret` to `refreshToken()`
- Verify the refresh token is valid and not expired
- Ensure offline access scope was requested during initial sign-in

#### Token refresh fails (Mobile)
- Ensure offline access scope is requested during initial sign-in
- Check that refresh token is being stored properly
- Verify client secret is provided if required by your OAuth configuration

## Demo App

A demo app is available in `examples/google-auth-demo/` that showcases all plugin functionality:

```bash
cd examples/google-auth-demo
pnpm install
pnpm tauri dev
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Tauri](https://tauri.app/) - For the amazing cross-platform framework
- [Google Sign-In SDK](https://developers.google.com/identity) - For OAuth implementation
- The Tauri community for their continuous support

## Support

If you encounter any issues or have questions, please file an issue on the [GitHub repository](https://github.com/choochmeque/tauri-plugin-google-auth/issues).
