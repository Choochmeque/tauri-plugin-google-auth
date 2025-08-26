# Android Google Sign-In Setup Guide (Using Credential Manager API)

This guide will help you configure Google Sign-In for your Android app using the modern **Credential Manager API** with the `tauri-plugin-google-auth` plugin.

> **Note**: This implementation uses Google's new Credential Manager API which replaces the deprecated GoogleSignIn API. Credential Manager provides a more secure and unified authentication experience.

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
   - Search for and enable "Google Identity Toolkit API"

4. Create OAuth 2.0 credentials:
   - Go to "APIs & Services" > "Credentials"
   - Click "Create Credentials" > "OAuth client ID"
   - Select "Android" as the application type
   - Enter your app's package name (e.g., `com.example.myapp`)
   - Provide the SHA-1 certificate fingerprint (see below)
   - Click "Create"

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

### 1.3 Configure Web Client (Required for ID Token)

**Important**: For Android with Credential Manager, you need a **Web OAuth 2.0 client ID**:

1. In Google Cloud Console, create another OAuth 2.0 client
2. Select "Web application" as the application type
3. Give it a name (e.g., "Android Web Client for Credential Manager")
4. You don't need to add any authorized URLs for mobile use
5. Click "Create" and copy the **Web Client ID**
6. **Use this Web Client ID in your Android app's `signIn` method**

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
# Credential Manager
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.** { *; }

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
} from 'tauri-plugin-google-auth';

// Sign in with Google
async function handleSignIn() {
  try {
    const tokens = await signIn({
      // IMPORTANT: Use the Web Client ID for Credential Manager
      clientId: 'YOUR_WEB_CLIENT_ID.apps.googleusercontent.com',
      scopes: ['email', 'profile'], // Note: Additional scopes not directly supported by Credential Manager
      hostedDomain: 'example.com', // Optional: restrict to specific domain
      loginHint: 'user@example.com' // Optional: pre-fill email (not supported in new API)
    });
    
    console.log('Sign-in successful:', tokens);
    console.log('ID Token:', tokens.idToken);
    console.log('Access Token:', tokens.accessToken);
    console.log('Refresh Token:', tokens.refreshToken);
    console.log('Expires At:', tokens.expiresAt);
    
    // Note about tokens with Credential Manager:
    // - idToken: Available and contains user information
    // - accessToken: Not provided by Credential Manager API
    // - refreshToken: Not available with Credential Manager
    
  } catch (error) {
    console.error('Sign in failed:', error);
    
    // Handle specific error cases
    if (error.includes('cancelled')) {
      console.log('User cancelled sign-in');
    } else if (error.includes('No credentials')) {
      console.log('No saved credentials found');
    }
  }
}

// Sign out
async function handleSignOut() {
  try {
    await signOut();
    console.log('User signed out');
    // Note: Credential Manager manages credentials at system level
    // Sign out clears app's current user state
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
    // Gets fresh tokens from saved credentials
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
   - The Credential Manager will present a bottom sheet with available Google accounts
   - Users can select an account or add a new one
   - The authentication happens through Google Play Services

## Important Differences with Credential Manager

### What's Changed from the Deprecated API:

1. **Authentication UI**: Now uses a system-managed bottom sheet instead of a separate activity
2. **Token Management**: Credentials are managed by the Android system
3. **No Direct Access Token**: Credential Manager doesn't provide OAuth access tokens
4. **Automatic Credential Storage**: System automatically saves and manages credentials
5. **Unified Experience**: Same UI for passwords, passkeys, and federated sign-in

### Available Data:

With Credential Manager, you get:
- **ID Token**: JWT containing user information (always available)
- **User Profile**: Email, name, profile picture
- **User ID**: Unique identifier

Not directly available:
- **Access Token**: Use ID token for authentication
- **Refresh Token**: Use `restorePreviousSignIn()` or `refreshToken()` for fresh credentials

## Security Best Practices

1. **Client ID Security**: 
   - Use environment variables or build configuration for Client ID
   - Never hardcode sensitive information

2. **ID Token Validation**:
   - Always validate ID tokens on your backend
   - Verify the token signature and claims

3. **Nonce Usage**:
   - The plugin automatically generates a nonce for enhanced security
   - This prevents replay attacks

4. **Session Management**:
   - Implement proper session timeout
   - Clear sensitive data when user signs out

## Troubleshooting

### Common Issues

1. **"No credentials available"**:
   - User has no Google account on device
   - Google Play Services not available or outdated
   - Check SHA-1 fingerprint matches in Google Cloud Console

2. **Sign-in fails silently**:
   - Verify you're using the Web Client ID (not Android Client ID)
   - Check package name matches exactly
   - Ensure SHA-1 fingerprint is correct for your build type

3. **"User cancelled the sign-in flow"**:
   - User dismissed the credential picker
   - This is normal user behavior, handle gracefully

4. **Credential Manager not working on older devices**:
   - Requires Google Play Services with Credential Manager support
   - Falls back to no credentials on unsupported devices

## Testing on Emulator

To test on an Android emulator:
1. Use an emulator with Google Play Services (API 21+)
2. Sign in to a Google account on the emulator
3. Ensure the emulator has internet connectivity
4. Google Play Services should be up to date

## Migration from Deprecated GoogleSignIn

If you're migrating from the old GoogleSignIn API:

1. **Update Dependencies**: Replace `play-services-auth` with Credential Manager libraries
2. **Change Sign-In Flow**: Replace GoogleSignInClient with CredentialManager
3. **Update Token Handling**: Adapt to ID token-only authentication
4. **Modify Error Handling**: Update for new exception types
5. **Test Thoroughly**: The UX is different with the bottom sheet

## Additional Resources

- [Credential Manager Documentation](https://developer.android.com/identity/sign-in/credential-manager)
- [Google Identity Services](https://developers.google.com/identity)
- [Migrate from Google Sign-In](https://developer.android.com/identity/sign-in/credential-manager-migration)
- [ID Token Verification](https://developers.google.com/identity/sign-in/android/backend-auth)
- [Tauri Mobile Documentation](https://tauri.app/v1/guides/building/mobile)