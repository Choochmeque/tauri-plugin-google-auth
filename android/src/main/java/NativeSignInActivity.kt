package app.tauri.googleauth

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.Identity

/**
 * Activity that handles authorization resolution when user consent is required.
 * This activity receives a PendingIntent from AuthorizationClient and launches it
 * to get user consent, then returns the authorization result.
 */
class NativeSignInActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PENDING_INTENT = "pendingIntent"

        const val RESULT_ACCESS_TOKEN = "accessToken"
        const val RESULT_GRANTED_SCOPES = "grantedScopes"
        const val RESULT_ERROR = "error"
    }

    private lateinit var authorizationClient: AuthorizationClient
    private lateinit var authorizationLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authorizationClient = Identity.getAuthorizationClient(this)

        authorizationLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            handleAuthorizationResult(result.resultCode, result.data)
        }

        val pendingIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PENDING_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PENDING_INTENT)
        }

        if (pendingIntent == null) {
            finishWithError("No pending intent provided")
            return
        }

        try {
            val intentSenderRequest = IntentSenderRequest.Builder(
                pendingIntent.intentSender
            ).build()
            authorizationLauncher.launch(intentSenderRequest)
        } catch (e: Exception) {
            finishWithError("Failed to launch authorization: ${e.message}")
        }
    }

    private fun handleAuthorizationResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_CANCELED) {
            finishWithError("Authorization cancelled by user")
            return
        }

        if (resultCode != RESULT_OK || data == null) {
            finishWithError("Authorization failed with result code: $resultCode")
            return
        }

        try {
            val authResult = authorizationClient.getAuthorizationResultFromIntent(data)
            val accessToken = authResult.accessToken

            if (accessToken != null) {
                val grantedScopes = authResult.grantedScopes.map { it.toString() }.toTypedArray()
                finishWithSuccess(accessToken, grantedScopes)
            } else {
                finishWithError("Failed to get access token from authorization result")
            }
        } catch (e: Exception) {
            finishWithError("Auth result error [${e.javaClass.simpleName}]: ${e.message}")
        }
    }

    private fun finishWithSuccess(accessToken: String, grantedScopes: Array<String>) {
        val intent = Intent().apply {
            putExtra(RESULT_ACCESS_TOKEN, accessToken)
            putExtra(RESULT_GRANTED_SCOPES, grantedScopes)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun finishWithError(errorMessage: String) {
        val intent = Intent().apply {
            putExtra(RESULT_ERROR, errorMessage)
        }
        setResult(RESULT_CANCELED, intent)
        finish()
    }
}
