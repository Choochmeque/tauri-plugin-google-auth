package app.tauri.googleauth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

class GoogleSignInActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "GoogleSignInActivity"
    }
    
    private lateinit var authorizationClient: AuthorizationClient
    private lateinit var authorizationLauncher: ActivityResultLauncher<IntentSenderRequest>
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

        authorizationClient = Identity.getAuthorizationClient(this)
        
        authorizationLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            Log.d(TAG, "Authorization launcher result received: resultCode=${result.resultCode}")
            handleAuthorizationResult(result.resultCode, result.data)
        }
        
        startAuthorization()
    }
    
    private fun startAuthorization() {
        val requestedScopes = mutableListOf<Scope>()
        for (scope in scopes) {
            requestedScopes.add(Scope(scope))
        }

        val authorizationRequest = AuthorizationRequest.Builder()
            .setRequestedScopes(requestedScopes)
            .requestOfflineAccess(clientId!!, true) // force consent to get refresh token
            .build()
        
        authorizationClient.authorize(authorizationRequest)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    val pendingIntent = authorizationResult.pendingIntent
                    
                    if (pendingIntent != null) {
                        try {
                            val intentSenderRequest = IntentSenderRequest.Builder(
                                pendingIntent.intentSender
                            ).build()
                            authorizationLauncher.launch(intentSenderRequest)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to launch authorization", e)
                            finishWithError("Failed to launch authorization: ${e.message}")
                        }
                    } else {
                        finishWithError("No pending intent available")
                    }
                } else {
                    val serverAuthCode = authorizationResult.serverAuthCode
                    val accessToken = authorizationResult.accessToken
                    
                    if (serverAuthCode != null) {
                        finishWithSuccess(serverAuthCode)
                    } else if (accessToken != null) {
                        finishWithError("Authorization flow did not return auth code")
                    } else {
                        finishWithError("No authorization code or access token received")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Authorization failed: ${e.javaClass.simpleName}: ${e.message}", e)
                finishWithError("Authorization failed: ${e.message}")
            }
    }
    
    private fun handleAuthorizationResult(resultCode: Int, data: Intent?) {
        // Log intent extras when canceled
        if (resultCode == RESULT_CANCELED && data != null) {
            
            var errorMessage = "User cancelled the authorization"
            
            // Try to parse the authorization result even on cancellation to get error details
            try {
                val authorizationResult = authorizationClient.getAuthorizationResultFromIntent(data)
            } catch (e: Exception) {
                if (e is com.google.android.gms.common.api.ApiException) {
                    
                    // Parse specific error codes
                    errorMessage = when {
                        e.message?.contains("UNREGISTERED_ON_API_CONSOLE") == true -> 
                            "App not configured in Google Cloud Console. Please ensure OAuth 2.0 Client ID is properly set up with correct package name and SHA-1 fingerprint."
                        e.message?.contains("[28405]") == true ->
                            "OAuth consent screen not configured. Please configure the OAuth consent screen in Google Cloud Console."
                        e.statusCode == 12501 -> "Sign-in cancelled by user"
                        e.statusCode == 12500 -> "Sign-in failed. Please check your Google Cloud Console configuration."
                        e.statusCode == 10 -> "Developer error: Invalid configuration"
                        e.statusCode == 8 -> {
                            // Status code 8 can have various meanings, check the message
                            when {
                                e.message?.contains("28405") == true -> 
                                    "OAuth consent screen not configured. Please configure the OAuth consent screen in Google Cloud Console."
                                else -> e.message ?: "Configuration error"
                            }
                        }
                        else -> e.message ?: "Authorization failed"
                    }
                }
            }
            
            finishWithError(errorMessage)
            return
        }
        
        if (resultCode == RESULT_OK && data != null) {
            try {
                val authorizationResult = authorizationClient.getAuthorizationResultFromIntent(data)
                
                val serverAuthCode = authorizationResult.serverAuthCode
                val accessToken = authorizationResult.accessToken
                val grantedScopes = authorizationResult.grantedScopes
                
                if (serverAuthCode != null) {
                    finishWithSuccess(serverAuthCode)
                } else if (accessToken != null) {
                    finishWithError("Authorization flow did not return auth code. Ensure offline access is requested.")
                } else {
                    finishWithError("No authorization code received")
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Failed to get authorization result: ${e.statusCode}", e)
                finishWithError("Failed to get authorization result: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error processing result", e)
                finishWithError("Unexpected error: ${e.message}")
            }
        } else if (resultCode == RESULT_CANCELED) {
            // This case is for RESULT_CANCELED without data
            finishWithError("Sign-in cancelled")
        } else {
            finishWithError("Authorization failed with result code: $resultCode")
        }
    }
    
    private fun finishWithSuccess(authCode: String) {
        val intent = Intent().apply {
            val prefix = GoogleSignInPlugin.RESULT_EXTRA_PREFIX
            putExtra(prefix + GoogleSignInPlugin.AUTH_CODE, authCode)
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