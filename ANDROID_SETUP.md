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
3. Configure [OAuth consent screen](https://console.cloud.google.com/apis/credentials/consent) (required)

#### Configure for Native Flow (default)

Uses [Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg) + [AuthorizationClient](https://developer.android.com/identity/authorization). No `client_secret` needed, but no `refresh_token` returned.

1. Create **Android** OAuth 2.0 client (for app verification):
   - Go to "APIs & Services" > "Credentials"
   - Click "Create Credentials" > "OAuth client ID"
   - Select "Android" as the application type
   - Enter your app's package name (e.g., `com.example.myapp`)
   - Provide the SHA-1 certificate fingerprint (see below)
   - Click "Create"
   - **Note:** You do NOT use this Client ID in your code. It's only used by Google to verify your app.

2. Create **Web Application** OAuth 2.0 client (for your code):
   - Click "Create Credentials" > "OAuth client ID"
   - Select "Web application" as the application type
   - Click "Create"
   - **Save this Client ID** - this is what you pass to `clientId` in your code

##### Get SHA-1 Certificate Fingerprint

Required for Android Client ID. Google uses this to verify your app.

**Debug Keystore (Development):**

```bash
# macOS/Linux
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android

# Windows
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android
```

Default credentials: alias `androiddebugkey`, password `android`.

**Release Keystore (Production):**

```bash
keytool -list -v -keystore your-release-key.keystore -alias your-key-alias
```

**Important:** Register both debug and release SHA-1 fingerprints if you want sign-in to work in both build types.

#### Configure for Web Flow

Uses [OAuth 2.0 authorization code](https://developers.google.com/identity/protocols/oauth2/native-app) exchange via Custom Tabs. Requires `client_secret`, returns `refresh_token`.

1. Create **Web Application** OAuth 2.0 client:
   - Go to "APIs & Services" > "Credentials"
   - Click "Create Credentials" > "OAuth client ID"
   - Select "Web application" as the application type
   - Click "Create"
   - **Save the Client ID and Client Secret**

**Note:** Android Client ID is NOT required for web flow.

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

// Sign in with Google (native flow)
async function handleSignIn() {
  try {
    const tokens = await signIn({
      clientId: 'YOUR_WEB_CLIENT_ID.apps.googleusercontent.com',
      scopes: ['email', 'profile']
    });
    
    console.log('Sign-in successful:', tokens);
    console.log('ID Token:', tokens.idToken);
    console.log('Access Token:', tokens.accessToken);
    console.log('Expires At:', tokens.expiresAt); // Unix timestamp in seconds
    
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

// Sign out (native flow)
async function handleSignOut() {
  try {
    await signOut();
    console.log('User signed out');
  } catch (error) {
    console.error('Sign out failed:', error);
  }
}

// Refresh access token (native flow - silent, no user prompt)
async function handleRefreshToken() {
  try {
    const tokens = await refreshToken({
      clientId: 'YOUR_WEB_CLIENT_ID.apps.googleusercontent.com',
      scopes: ['email', 'profile']
    });
    console.log('Refreshed tokens:', tokens);
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

## Authentication Flow Types

Android supports two authentication flows via the `flowType` option:

| flowType | client_secret | refresh_token |
|----------|---------------|---------------|
| `"native"` (default) | Not needed | No |
| `"web"` | Required | Yes |

### Native Flow (default)

Uses [Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg) and [AuthorizationClient](https://developer.android.com/identity/authorization).

**Required Client IDs:**
- **Web Client ID** - passed as `clientId` parameter, used as [ID token audience](https://developer.android.com/identity/sign-in/credential-manager-siwg#set-google)
- **Android Client ID** - configured in [Google Cloud Console](https://console.cloud.google.com/apis/credentials) with your package name and SHA-1, matched automatically by the SDK

```typescript
const tokens = await signIn({
  clientId: 'YOUR_WEB_CLIENT_ID.apps.googleusercontent.com',
  scopes: ['email', 'profile']
});

// Refresh (silent)
const newTokens = await refreshToken({
  clientId: 'YOUR_WEB_CLIENT_ID.apps.googleusercontent.com',
  scopes: ['email', 'profile']
});
```

### Web Flow

Uses OAuth 2.0 authorization code exchange.

```typescript
const tokens = await signIn({
  clientId: 'YOUR_WEB_CLIENT_ID.apps.googleusercontent.com',
  clientSecret: 'YOUR_WEB_CLIENT_SECRET',
  scopes: ['email', 'profile'],
  flowType: 'web'
});

// Refresh
const newTokens = await refreshToken({
  refreshToken: tokens.refreshToken,
  clientId: 'YOUR_WEB_CLIENT_ID.apps.googleusercontent.com',
  clientSecret: 'YOUR_WEB_CLIENT_SECRET',
  flowType: 'web'
});
```

## Important Implementation Details

### OAuth Flow

The web flow uses a traditional OAuth 2.0 authorization code flow:

1. Opens a web view with Google's authorization endpoint
2. User authenticates and grants permissions
3. Captures the authorization code from the redirect
4. Exchanges the code for tokens (ID token, access token, and optionally refresh token)

### Available Tokens

The plugin provides:
- **ID Token**: JWT containing user information
- **Access Token**: OAuth token for accessing Google APIs
- **Refresh Token**: Token for obtaining new access tokens (web flow only)
- **Expiration Time**: Unix timestamp in seconds indicating when the access token expires

### Supported Parameters

- `clientId`: Required - Your **Web Application** client ID
- `clientSecret`: Required for web flow only
- `scopes`: OAuth scopes to request
- `flowType`: `"native"` (default) or `"web"`

## Troubleshooting

### Common Issues

1. **"Developer console is not set up correctly [28444]"**:
   This error means Google cannot verify your app. Check:
   - **OAuth consent screen** is configured in Google Cloud Console (even "Testing" mode works)
   - **Android Client ID** exists with correct package name and SHA-1 fingerprint
   - **Web Client ID** exists (required for native flow)
   - Your test user email is added to the OAuth consent screen if in "Testing" mode
   - SHA-1 matches your build type (debug keystore for `tauri android dev`)

2. **"Configuration error" on sign-in**:
   - Check that your SHA-1 fingerprint is added to Google Cloud Console
   - Ensure your package name matches exactly with the one in Google Cloud Console
   - Verify the client ID is correct

3. **Sign-in fails silently**:
   - Check that internet permissions are granted
   - Verify Google Play Services is available on the device
   - Check logcat for detailed error messages

4. **"User cancelled the sign-in flow"**:
   - User closed the authentication web view
   - This is normal user behavior, handle gracefully in your app

5. **"Sign-in cancelled: activity is cancelled by the user"** (native flow):
   This error occurs when the account picker appears but fails after selecting an account. The cause is almost always using the **wrong client ID type**:
   - You must pass the **Web Application client ID** to the `clientId` parameter, NOT the Android client ID
   - The Android client ID (with your package name + SHA-1) is only used internally by Google to verify your app
   - Create both client types in Google Cloud Console, but only pass the Web client ID to your code

   ```typescript
   // CORRECT: Use Web Application client ID
   signIn({ clientId: 'WEB_CLIENT_ID.apps.googleusercontent.com', scopes: [...] })

   // WRONG: Using Android client ID will cause this error
   ```

6. **Token refresh fails**:
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
- [Tauri Mobile Documentation](https://tauri.app/develop/#developing-your-mobile-application)