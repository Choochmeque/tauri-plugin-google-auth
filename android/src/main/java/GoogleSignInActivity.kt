package app.tauri.googleauth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException

/**
 * Google Sign-In Activity using Native SDK flow (BeginSignInRequest).
 *
 * This flow returns an idToken directly from Google's native SDK,
 * eliminating the need for client_secret on mobile devices.
 *
 * Flow:
 * 1. BeginSignInRequest with GoogleIdTokenRequestOptions
 * 2. User selects Google account via native UI
 * 3. Google returns idToken directly (no authorization code exchange!)
 * 4. Send idToken to your backend for verification
 *
 * Security Benefits:
 * - NO client_secret needed in APK (secure!)
 * - idToken is cryptographically signed by Google
 * - Backend verifies: signature, aud, exp, iss claims
 */
class GoogleSignInActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GoogleSignInActivity"
    }

    private lateinit var signInClient: SignInClient
    private lateinit var signInLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var clientId: String? = null
    private lateinit var scopes: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_google_sign_in)

        clientId = intent.getStringExtra(GoogleSignInPlugin.CLIENT_ID)
        scopes = intent.getStringArrayExtra(GoogleSignInPlugin.SCOPES) ?: emptyArray()

        if (clientId == null) {
            finishWithError("Client ID is required")
            return
        }

        if (scopes.isEmpty()) {
            finishWithError("Scopes are required")
            return
        }

        signInClient = Identity.getSignInClient(this)

        signInLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            Log.d(TAG, "Sign-in launcher result received: resultCode=${result.resultCode}")
            handleSignInResult(result.resultCode, result.data)
        }

        startSignIn()
    }

    private fun startSignIn() {
        // Build Google ID Token request (native flow - returns idToken directly)
        // This is the recommended flow for mobile apps per Google's OAuth 2.0 best practices
        val signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(clientId!!)  // Web Client ID from Google Cloud Console
                    .setFilterByAuthorizedAccounts(false)  // Allow all accounts, not just previously used
                    .build()
            )
            .build()

        signInClient.beginSignIn(signInRequest)
            .addOnSuccessListener { result ->
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(
                        result.pendingIntent.intentSender
                    ).build()
                    signInLauncher.launch(intentSenderRequest)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch sign-in", e)
                    finishWithError("Failed to launch sign-in: ${e.message}")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Sign-in failed: ${e.javaClass.simpleName}: ${e.message}", e)
                finishWithError("Sign-in failed: ${e.message}")
            }
    }

    private fun handleSignInResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_CANCELED) {
            finishWithError("Sign-in cancelled by user")
            return
        }

        if (resultCode == RESULT_OK && data != null) {
            try {
                val credential = signInClient.getSignInCredentialFromIntent(data)
                val idToken = credential.googleIdToken

                if (idToken != null) {
                    // Success! Got idToken directly from native SDK
                    Log.d(TAG, "Successfully received idToken")
                    finishWithSuccess(idToken)
                } else {
                    finishWithError("No ID token received from Google Sign-In")
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Failed to get sign-in credential: ${e.statusCode}", e)

                val errorMessage = when (e.statusCode) {
                    12501 -> "Sign-in cancelled by user"
                    12500 -> "Sign-in failed. Please check your Google Cloud Console configuration."
                    10 -> "Developer error: Invalid configuration. Check package name and SHA-1 fingerprint."
                    else -> "Sign-in failed: ${e.message}"
                }

                finishWithError(errorMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error processing sign-in result", e)
                finishWithError("Unexpected error: ${e.message}")
            }
        } else {
            finishWithError("Sign-in failed with result code: $resultCode")
        }
    }

    private fun finishWithSuccess(idToken: String) {
        val intent = Intent().apply {
            val prefix = GoogleSignInPlugin.RESULT_EXTRA_PREFIX
            putExtra(prefix + GoogleSignInPlugin.ID_TOKEN, idToken)
            putExtra(prefix + GoogleSignInPlugin.CLIENT_ID, clientId)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun finishWithError(errorMessage: String) {
        val intent = Intent().apply {
            val prefix = GoogleSignInPlugin.RESULT_EXTRA_PREFIX
            putExtra(prefix + GoogleSignInPlugin.ERROR_MESSAGE, errorMessage)
        }
        setResult(Activity.RESULT_CANCELED, intent)
        finish()
    }
}
