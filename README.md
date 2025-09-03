# Tauri Plugin Google Auth

[![Crates.io](https://img.shields.io/crates/v/tauri-plugin-google-auth)](https://crates.io/crates/tauri-plugin-google-auth)
[![npm](https://img.shields.io/npm/v/@choochmeque/tauri-plugin-google-auth-api)](https://www.npmjs.com/package/@choochmeque/tauri-plugin-google-auth-api)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A Tauri v2 plugin for Google OAuth authentication, providing seamless Google Sign-In integration for mobile and desktop applications.

## Features

- 🔐 **Secure OAuth 2.0 Authentication** - Full OAuth 2.0 implementation with PKCE support
- 📱 **Mobile Support** - Native iOS and Android implementations using platform-specific APIs
- 🖥️ **Desktop Support** - OAuth2 flow with local redirect server for macOS, Windows, and Linux
- 🔄 **Token Management** - Token refresh and revocation support
- 🎯 **TypeScript Support** - Fully typed API with comprehensive JSDoc documentation
- 🛡️ **Security First** - PKCE, secure redirect handling, and proper error management
- ⚙️ **Flexible Configuration** - Customizable redirect URIs, HTML responses, and dynamic port binding

## Installation

### Rust

Add the plugin to your `Cargo.toml`:

```toml
[dependencies]
tauri-plugin-google-auth = "0.2.0"
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
    
    console.log('ID Token:', response.idToken);
    console.log('Access Token:', response.accessToken);
    console.log('Refresh Token:', response.refreshToken);
    console.log('Expires at:', new Date(response.expiresAt));
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
async function refreshUserToken() {
  try {
    const response = await refreshToken();
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
  clientSecret: 'YOUR_CLIENT_SECRET', // Optional: for certain OAuth flows
  scopes: [
    'openid',
    'email',
    'profile',
    'https://www.googleapis.com/auth/drive.readonly'
  ],
  hostedDomain: 'company.com', // Restrict to company domain
  loginHint: 'john.doe@company.com', // Pre-fill the email field
  redirectUri: 'custom://redirect' // Custom redirect URI
});
```

## API Reference

### Types

#### `SignInOptions`

```typescript
interface SignInOptions {
  clientId: string;              // Required: Google OAuth client ID
  clientSecret?: string;         // Required for desktop platforms
  scopes?: string[];            // OAuth scopes to request (openid added automatically)
  hostedDomain?: string;        // Restrict authentication to a specific domain
  loginHint?: string;           // Email hint to pre-fill in the sign-in form
  redirectUri?: string;         // Custom redirect URI (desktop: defaults to random port)
  successHtmlResponse?: string; // Custom HTML shown after auth (desktop only)
}
```

#### `TokenResponse`

```typescript
interface TokenResponse {
  idToken: string;           // JWT ID token (UUID v7 placeholder on desktop currently)
  accessToken: string;       // OAuth access token for API calls
  refreshToken?: string;     // Refresh token (when offline access is granted)
  expiresAt?: number;       // Token expiration timestamp (seconds since epoch)
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

#### `refreshToken(): Promise<TokenResponse>`
Refreshes the current access token using the stored refresh token.

## Error Handling

The plugin provides detailed error information for common scenarios:

```typescript
try {
  await signIn({ clientId: 'YOUR_CLIENT_ID' });
} catch (error) {
  switch (error.code) {
    case 'USER_CANCELLED':
      console.log('User cancelled the sign-in flow');
      break;
    case 'NETWORK_ERROR':
      console.log('Network error occurred');
      break;
    case 'INVALID_CLIENT_ID':
      console.log('Invalid client ID provided');
      break;
    case 'CONFIGURATION_ERROR':
      console.log('Plugin not properly configured');
      break;
    default:
      console.error('Unknown error:', error);
  }
}
```

## Platform Support

| Platform | Status | Implementation |
|----------|--------|---------------|
| iOS      | ✅ Supported | Native Google Sign-In SDK |
| Android  | ✅ Supported | Credential Manager API |
| macOS    | ✅ Supported | OAuth2 with local redirect server |
| Windows  | ✅ Supported | OAuth2 with local redirect server |
| Linux    | ✅ Supported | OAuth2 with local redirect server |

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
- **Permission System**: Fine-grained permissions control access to authentication methods

## Known Issues

### Desktop Platforms
- **Token Refresh Not Implemented**: The `refreshToken()` function is not yet implemented for desktop platforms (macOS, Windows, Linux). You'll need to implement your own token refresh logic using the refresh token returned from the initial sign-in.

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
- Note: Token refresh is not yet implemented for desktop platforms
- Store the refresh token from initial sign-in and implement your own refresh logic
- Ensure offline access scope is requested during initial sign-in

#### Token refresh fails (Mobile)
- Ensure offline access scope is requested during initial sign-in
- Check that refresh token is being stored properly
- Verify client secret is provided if required by your OAuth configuration

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