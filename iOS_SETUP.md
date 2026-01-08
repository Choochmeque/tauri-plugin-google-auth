# iOS Google Sign-In Setup Guide

This guide will help you configure Google Sign-In for your iOS app using the `tauri-plugin-google-auth` plugin.

## Prerequisites

1. A Google Cloud Console project with OAuth 2.0 credentials
2. An iOS app with a valid Bundle ID
3. Xcode installed on your Mac

## Step 1: Configure Google Cloud Console

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the Google Sign-In API:
   - Navigate to "APIs & Services" > "Library"
   - Search for "Google Sign-In API" and enable it

4. Create OAuth 2.0 credentials:
   - Go to "APIs & Services" > "Credentials"
   - Click "Create Credentials" > "OAuth client ID"
   - Select "iOS" as the application type
   - Enter your app's Bundle ID (e.g., `com.example.myapp`)
   - Click "Create"
   - Save the Client ID (you'll need it in your app)

## Step 2: Configure Your iOS App

### Info.plist Configuration

Add the following to your app's `Info.plist` file:

```xml
<!-- Google Sign-In Configuration -->
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLSchemes</key>
        <array>
            <!-- Replace with your REVERSED_CLIENT_ID from Google -->
            <string>com.googleusercontent.apps.YOUR_REVERSED_CLIENT_ID</string>
        </array>
    </dict>
</array>

<!-- Required for Google Sign-In -->
<key>LSApplicationQueriesSchemes</key>
<array>
    <string>googlechrome</string>
    <string>safari</string>
</array>
```

**Important:** The `REVERSED_CLIENT_ID` is your Client ID in reverse domain notation. For example:
- If your Client ID is: `123456789-abcdef.apps.googleusercontent.com`
- Your reversed Client ID is: `com.googleusercontent.apps.123456789-abcdef`

### AppDelegate Configuration

The plugin uses SimpleGoogleSignIn library which handles the authentication flow automatically. No additional AppDelegate configuration is required as the plugin manages the URL handling internally.

## Step 3: Using the Plugin in Your Tauri App

### Installation

Make sure the plugin is added to your Tauri project:

```bash
# In your Tauri project root
cargo add tauri-plugin-google-auth
```

### JavaScript/TypeScript Usage

```typescript
import { signIn, signOut, refreshToken } from '@choochmeque/tauri-plugin-google-auth-api';

// Sign in
async function handleSignIn() {
  try {
    const tokens = await signIn({
      clientId: 'YOUR_IOS_CLIENT_ID.apps.googleusercontent.com',
      scopes: ['email', 'profile'], // Optional additional scopes
      hostedDomain: 'example.com', // Optional: restrict to specific domain
      loginHint: 'user@example.com' // Optional: pre-fill email
    });
    
    console.log('Sign-in successful:', tokens);
    console.log('ID Token:', tokens.idToken);
    console.log('Access Token:', tokens.accessToken);
    console.log('Refresh Token:', tokens.refreshToken);
    console.log('Expires At:', tokens.expiresAt);
  } catch (error) {
    console.error('Sign in failed:', error);
  }
}

// Sign out
async function handleSignOut() {
  try {
    await signOut();
    console.log('User signed out');
  } catch (error) {
    console.error('Sign out failed:', error);
  }
}

// Refresh access token
async function refreshUserToken(storedRefreshToken: string) {
  try {
    const tokens = await refreshToken({
      refreshToken: storedRefreshToken,
      clientId: 'YOUR_IOS_CLIENT_ID.apps.googleusercontent.com'
    });
    console.log('Refreshed tokens:', tokens);
    console.log('New Access Token:', tokens.accessToken);
  } catch (error) {
    console.error('Token refresh failed:', error);
  }
}
```

## Step 4: Testing

1. Build your Tauri iOS app:
   ```bash
   npm run tauri ios build
   ```

2. Open the generated Xcode project and run on a device or simulator

3. Test the sign-in flow:
   - The SimpleGoogleSignIn library will present a native authentication view
   - Users can sign in with their Google account
   - The plugin will return the user profile and tokens

## Important Notes

### Scopes

The plugin requests basic profile and email scopes by default. You can request additional scopes:

```typescript
const user = await signIn({
  clientId: 'YOUR_CLIENT_ID',
  scopes: [
    'https://www.googleapis.com/auth/drive.readonly',
    'https://www.googleapis.com/auth/calendar'
  ]
});
```

### Token Management

- **ID Token**: Used to verify the user's identity
- **Access Token**: Used to access Google APIs
- **Refresh Token**: Used to obtain new access tokens (may not always be available on iOS)

The `expiresAt` field indicates when the access token expires (Unix timestamp in seconds).

### Error Handling

The plugin provides detailed error messages for common scenarios:
- User cancellation
- Network errors
- Invalid configuration
- Token refresh failures

### Security Best Practices

1. **Never hardcode your Client ID** in production apps - use environment variables or configuration files
2. **Validate ID tokens** on your backend server before trusting user identity
3. **Use HTTPS** for all API communications
4. **Implement proper session management** in your app

## Troubleshooting

### Common Issues

1. **"No root view controller found"**: Ensure your app has properly initialized its UI before calling sign-in

2. **URL scheme errors**: Double-check that your reversed client ID in Info.plist matches exactly

3. **Sign-in window doesn't appear**: Verify that the SimpleGoogleSignIn library is properly linked and that you're calling from the main thread

4. **Token refresh fails**: Some tokens may expire - implement proper error handling and re-authentication flow

## Additional Resources

- [Google Sign-In iOS Documentation](https://developers.google.com/identity/sign-in/ios)
- [Google OAuth 2.0 Scopes](https://developers.google.com/identity/protocols/oauth2/scopes)
- [Tauri Mobile Documentation](https://tauri.app/develop/#developing-your-mobile-application)