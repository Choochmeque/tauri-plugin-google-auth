# Android Google Sign-In Setup Guide

This guide will help you configure Google Sign-In for your Android app using the `tauri-plugin-google-auth` plugin.

## Prerequisites

1. A Google Cloud Console project with OAuth 2.0 credentials
2. An Android app with a valid package name
3. Android Studio installed (for SHA-1 certificate fingerprint)
4. Minimum Android SDK 21 (Android 5.0)

## Step 1: Configure Google Cloud Console

### 1.1 Create OAuth 2.0 Credentials

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable required APIs:
   - Navigate to "APIs & Services" > "Library"
   - Search for and enable "Google Sign-In API" or "Google Identity Toolkit API"

4. Create OAuth 2.0 credentials:
   - Go to "APIs & Services" > "Credentials"
   - Click "Create Credentials" > "OAuth client ID"
   - Select "Android" as the application type
   - Enter your app's package name (e.g., `com.example.myapp`)
   - Provide the SHA-1 certificate fingerprint (see below)
   - Click "Create"
   - Save the Client ID (you'll need it in your app)

### 1.2 Get SHA-1 Certificate Fingerprint

#### For Debug Build:
```bash
# On macOS/Linux
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

# On Windows
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

#### For Release Build:
```bash
keytool -list -v -keystore your-release-key.keystore -alias your-key-alias
```

Copy the SHA-1 fingerprint and add it to your OAuth 2.0 Android client configuration in Google Cloud Console.

### 1.3 Configure Additional Client (Optional)

For some OAuth flows, you may also need a Web client:

1. In Google Cloud Console, create another OAuth 2.0 client
2. Select "Web application" as the application type
3. Give it a name (e.g., "Android Web Client")
4. Add `http://localhost` to authorized redirect URIs
5. Click "Create" and copy the Client ID and Client Secret

## Step 2: Configure Your Android App

### 2.1 AndroidManifest.xml

The plugin automatically adds the required permissions. Your main app's `AndroidManifest.xml` should include:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 2.2 ProGuard Rules (For Release Builds)

If you're using ProGuard/R8 for release builds, add these rules to your `proguard-rules.pro`:

```proguard
# Google Sign-In
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# Kotlin Coroutines
-keepattributes Signature
-keepattributes *Annotation*
```

## Step 3: Using the Plugin in Your Tauri App

### Installation

Make sure the plugin is added to your Tauri project:

```bash
# In your Tauri project root
cargo add tauri-plugin-google-auth
```

### JavaScript/TypeScript Usage

```typescript
import { 
  signIn, 
  signOut, 
  refreshToken 
} from '@choochmeque/tauri-plugin-google-auth-api';

// Sign in with Google
async function handleSignIn() {
  try {
    const tokens = await signIn({
      clientId: 'YOUR_ANDROID_CLIENT_ID.apps.googleusercontent.com',
      clientSecret: 'YOUR_CLIENT_SECRET', // Optional, may be needed for certain flows
      scopes: ['email', 'profile'], // OAuth scopes to request
      hostedDomain: 'example.com', // Optional: restrict to specific domain
      loginHint: 'user@example.com' // Optional: pre-fill email
    });
    
    console.log('Sign-in successful:', tokens);
    console.log('ID Token:', tokens.idToken);
    console.log('Access Token:', tokens.accessToken);
    console.log('Refresh Token:', tokens.refreshToken);
    console.log('Expires At:', tokens.expiresAt); // Unix timestamp in milliseconds
    
  } catch (error) {
    console.error('Sign in failed:', error);
    
    // Handle specific error cases
    if (error.includes('cancelled')) {
      console.log('User cancelled sign-in');
    } else if (error.includes('network')) {
      console.log('Network error occurred');
    }
  }
}

// Sign out
async function handleSignOut() {
  try {
    await signOut({
      accessToken: 'USER_ACCESS_TOKEN' // Optional: revoke token with Google
    });
    console.log('User signed out');
  } catch (error) {
    console.error('Sign out failed:', error);
  }
}

// Refresh credentials
async function refreshUserToken() {
  try {
    const tokens = await refreshToken();
    console.log('Refreshed tokens:', tokens);
    console.log('New ID Token:', tokens.idToken);
    console.log('New Access Token:', tokens.accessToken);
  } catch (error) {
    console.error('Token refresh failed:', error);
  }
}
```

## Step 4: Testing

1. Build your Tauri Android app:
   ```bash
   npm run tauri android build
   ```

2. Install the APK on a device or emulator:
   ```bash
   adb install path/to/your-app.apk
   ```

3. Test the sign-in flow:
   - The plugin will open a web view for Google authentication
   - Users can sign in with their Google account
   - The plugin will capture the authorization code and exchange it for tokens

## Important Implementation Details

### OAuth Flow

The Android implementation uses a traditional OAuth 2.0 authorization code flow:

1. Opens a web view with Google's authorization endpoint
2. User authenticates and grants permissions
3. Captures the authorization code from the redirect
4. Exchanges the code for tokens (ID token, access token, and optionally refresh token)

### Available Tokens

The plugin provides:
- **ID Token**: JWT containing user information
- **Access Token**: OAuth token for accessing Google APIs
- **Refresh Token**: Token for obtaining new access tokens (when offline scope is requested)
- **Expiration Time**: Unix timestamp in milliseconds indicating when the access token expires

### Supported Parameters

All standard OAuth parameters are supported:
- `clientId`: Required - Your Android OAuth client ID
- `clientSecret`: Optional - May be required for certain flows
- `scopes`: OAuth scopes to request (email and profile are common)
- `hostedDomain`: Restrict authentication to a specific Google Workspace domain
- `loginHint`: Pre-fill the email field in the sign-in form

## Security Best Practices

1. **Client ID Security**: 
   - Use environment variables or build configuration for Client ID
   - Never hardcode sensitive information in your source code

2. **Token Validation**:
   - Always validate ID tokens on your backend server
   - Verify the token signature and claims before trusting the user identity

3. **Secure Storage**:
   - The plugin stores tokens securely using Android's SharedPreferences with encryption
   - Clear tokens when user signs out

4. **HTTPS Only**:
   - All OAuth communications use HTTPS
   - Ensure your redirect URIs use secure protocols

5. **Session Management**:
   - Implement proper session timeout
   - Clear sensitive data when user signs out

## Troubleshooting

### Common Issues

1. **"Configuration error" on sign-in**:
   - Check that your SHA-1 fingerprint is added to Google Cloud Console
   - Ensure your package name matches exactly with the one in Google Cloud Console
   - Verify the client ID is correct

2. **Sign-in fails silently**:
   - Check that internet permissions are granted
   - Verify Google Play Services is available on the device
   - Check logcat for detailed error messages

3. **"User cancelled the sign-in flow"**:
   - User closed the authentication web view
   - This is normal user behavior, handle gracefully in your app

4. **Token refresh fails**:
   - Ensure offline access scope was requested during initial sign-in
   - Check that refresh token is being stored properly
   - Verify client secret is provided if required

## Testing on Emulator

To test on an Android emulator:
1. Use an emulator with Google Play Services (API 21+)
2. Ensure the emulator has internet connectivity
3. The SHA-1 fingerprint for debug builds should work on the emulator

## Additional Resources

- [Google Sign-In for Android](https://developers.google.com/identity/sign-in/android)
- [OAuth 2.0 for Mobile Apps](https://developers.google.com/identity/protocols/oauth2/native-app)
- [Google OAuth 2.0 Scopes](https://developers.google.com/identity/protocols/oauth2/scopes)
- [ID Token Verification](https://developers.google.com/identity/sign-in/android/backend-auth)
- [Tauri Mobile Documentation](https://tauri.app/v1/guides/building/mobile)