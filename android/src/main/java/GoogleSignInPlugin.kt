package app.tauri.googleauth

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.webkit.WebView
import androidx.activity.result.ActivityResult
import app.tauri.annotation.ActivityCallback
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * SignInArgs - Arguments for signIn command.
 *
 * Note: clientSecret is NOT required for Native SDK flow!
 * The native flow returns idToken directly without needing to exchange authorization codes.
 */
@InvokeArg
class SignInArgs {
    lateinit var clientId: String
    lateinit var scopes: List<String>
    var hostedDomain: String? = null
    var loginHint: String? = null
}

@InvokeArg
class SignOutArgs {
    // For native flow, accessToken is typically not available
    // Sign out just clears the cached account
}

/**
 * Tauri Plugin for Google Sign-In using Native SDK flow.
 *
 * This plugin uses BeginSignInRequest which returns an idToken directly,
 * following Google's recommended OAuth 2.0 best practices for mobile apps.
 *
 * Key Benefits:
 * - NO client_secret required (secure for mobile!)
 * - Native Google Sign-In UI
 * - idToken returned directly (no authorization code exchange)
 *
 * Usage:
 * ```typescript
 * const { idToken } = await signIn({
 *   clientId: 'YOUR_WEB_CLIENT_ID.apps.googleusercontent.com',
 *   scopes: ['email', 'profile']
 * });
 *
 * // Send idToken to your backend for verification
 * await fetch('/api/auth/google', {
 *   method: 'POST',
 *   body: JSON.stringify({ idToken })
 * });
 * ```
 */
@TauriPlugin
class GoogleSignInPlugin(private val activity: Activity) : Plugin(activity) {

    companion object {
        private const val TAG = "GoogleSignInPlugin"

        const val TITLE = "title"
        const val SUBTITLE = "subtitle"
        const val CLIENT_ID = "clientId"
        const val SCOPES = "scopes"
        const val ID_TOKEN = "idToken"
        const val ERROR_MESSAGE = "errorMessage"

        var RESULT_EXTRA_PREFIX = ""
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var signInClient: SignInClient

    override fun load(webView: WebView) {
        super.load(webView)
        RESULT_EXTRA_PREFIX = activity.packageName + "."
        signInClient = Identity.getSignInClient(activity)
    }

    /**
     * Sign in with Google using Native SDK flow.
     *
     * Returns: { idToken: string }
     *
     * Note: Unlike the authorization code flow, this does NOT return
     * accessToken or refreshToken. The idToken should be sent to your
     * backend for verification and session creation.
     */
    @Command
    fun signIn(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(SignInArgs::class.java)

            if (args.clientId.isEmpty()) {
                invoke.reject("Client ID is required")
                return
            }

            val intent = Intent(activity, GoogleSignInActivity::class.java).apply {
                putExtra(CLIENT_ID, args.clientId)
                putExtra(SCOPES, args.scopes.toTypedArray())
                putExtra(TITLE, "Sign in with Google")
                putExtra(SUBTITLE, "Choose an account")
            }

            startActivityForResult(invoke, intent, "signInResult")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sign-in", e)
            invoke.reject("Failed to start sign-in: ${e.message}")
        }
    }

    @ActivityCallback
    private fun signInResult(invoke: Invoke, result: ActivityResult) {
        val resultCode = result.resultCode

        if (resultCode == Activity.RESULT_CANCELED) {
            val data = result.data
            val errorMessage = data?.getStringExtra(RESULT_EXTRA_PREFIX + ERROR_MESSAGE)
            if (errorMessage != null) {
                invoke.reject(errorMessage)
            } else {
                invoke.reject("User cancelled sign-in")
            }
            return
        }

        val data = result.data
        val idToken = data?.getStringExtra(RESULT_EXTRA_PREFIX + ID_TOKEN)
        val errorMessage = data?.getStringExtra(RESULT_EXTRA_PREFIX + ERROR_MESSAGE)

        if (errorMessage != null) {
            invoke.reject(errorMessage)
            return
        }

        if (idToken == null) {
            invoke.reject("No ID token received")
            return
        }

        // Return idToken directly - no exchange needed!
        val tokenObject = JSObject().apply {
            put("idToken", idToken)
        }
        invoke.resolve(tokenObject)
    }

    /**
     * Sign out from Google.
     *
     * Clears the cached account so the user will be prompted to
     * select an account on the next sign-in.
     */
    @Command
    fun signOut(invoke: Invoke) {
        scope.launch {
            try {
                // Sign out from Google Sign-In client to clear the cached account
                try {
                    signInClient.signOut().await()
                    Log.d(TAG, "Signed out from Google Sign-In client")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sign out from Google Sign-In client: ${e.message}")
                }

                val ret = JSObject()
                ret.put("success", true)
                invoke.resolve(ret)
            } catch (e: Exception) {
                Log.e(TAG, "Sign-out failed", e)
                invoke.reject("Sign-out failed: ${e.message}")
            }
        }
    }
}
