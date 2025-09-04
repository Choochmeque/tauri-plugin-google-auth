package app.tauri.googleauth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.WebView
import androidx.activity.result.ActivityResult
import app.tauri.annotation.ActivityCallback
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSArray
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.Scope
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@InvokeArg
class SignInArgs {
    lateinit var clientId: String
    var clientSecret: String? = null
    lateinit var scopes: List<String>
    var hostedDomain: String? = null
    var loginHint: String? = null
    var redirectUri: String? = ""
}

@InvokeArg
class SignOutArgs {
    var accessToken: String? = null
}

@InvokeArg
class RefreshTokenArgs {
    lateinit var refreshToken: String
    lateinit var clientId: String
    var clientSecret: String? = null
}

@TauriPlugin
class GoogleSignInPlugin(private val activity: Activity) : Plugin(activity) {
    
    companion object {
        private const val TAG = "GoogleSignInPlugin"
        
        
        const val TITLE = "title"
        const val SUBTITLE = "subtitle"
        const val CLIENT_ID = "clientId"
        const val CLIENT_SECRET = "clientSecret"
        const val SCOPES = "scopes"
        const val REDIRECT_URI = "redirectUri"
        const val AUTH_CODE = "authCode"
        const val GRANTED_SCOPES = "grantedScopes"
        const val ERROR_MESSAGE = "errorMessage"
        
        var RESULT_EXTRA_PREFIX = ""
    }
    
    private val scope = CoroutineScope(Dispatchers.Main)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private lateinit var authorizationClient: AuthorizationClient
    private lateinit var signInClient: SignInClient
    
    override fun load(webView: WebView) {
        super.load(webView)
        RESULT_EXTRA_PREFIX = activity.packageName + "."
        
        authorizationClient = Identity.getAuthorizationClient(activity)
        signInClient = Identity.getSignInClient(activity)
    }
    
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
                putExtra(CLIENT_SECRET, args.clientSecret)
                putExtra(SCOPES, args.scopes.toTypedArray())
                putExtra(REDIRECT_URI, args.redirectUri)
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
        val authCode = data?.getStringExtra(RESULT_EXTRA_PREFIX + AUTH_CODE)
        val errorMessage = data?.getStringExtra(RESULT_EXTRA_PREFIX + ERROR_MESSAGE)
        
        if (errorMessage != null) {
            invoke.reject(errorMessage)
            return
        }
        
        if (authCode == null) {
            invoke.reject("No authorization code received")
            return
        }
        
        val clientId = data?.getStringExtra(RESULT_EXTRA_PREFIX + CLIENT_ID)
        val clientSecret = data?.getStringExtra(RESULT_EXTRA_PREFIX + CLIENT_SECRET)
        val redirectUri = data?.getStringExtra(RESULT_EXTRA_PREFIX + REDIRECT_URI) ?: ""
        val grantedScopes = data?.getStringArrayExtra(RESULT_EXTRA_PREFIX + GRANTED_SCOPES)
        
        if (clientId == null) {
            invoke.reject("Client ID not found")
            return
        }
        
        scope.launch {
            try {
                val tokenResponse = exchangeAuthCodeForTokens(
                    authCode,
                    clientId,
                    clientSecret,
                    redirectUri
                )
                
                val tokenObject = createTokenResponse(tokenResponse, grantedScopes?.toList())
                invoke.resolve(tokenObject)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to exchange auth code", e)
                invoke.reject("Failed to complete sign-in: ${e.message}")
            }
        }
    }
    
    @Command
    fun signOut(invoke: Invoke) {
        scope.launch {
            try {
                val args = invoke.parseArgs(SignOutArgs::class.java)

                // Revoke the access token with Google's servers if it exists
                val accessToken = args.accessToken
                if (accessToken != null) {
                    try {
                        revokeAccessToken(accessToken)
                        Log.d(TAG, "Access token revoked successfully")
                    } catch (e: Exception) {
                        // Log the error but continue with sign-out
                        // Token might already be invalid or network might be unavailable
                        Log.w(TAG, "Failed to revoke access token: ${e.message}")
                    }
                }
                
                // Sign out from Google Sign-In client to clear the cached account
                // This ensures account selection prompt on next sign-in
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
    
    @Command
    fun refreshToken(invoke: Invoke) {
        scope.launch {
            try {
                val args = invoke.parseArgs(RefreshTokenArgs::class.java)

                val tokenResponse = refreshAccessToken(args.refreshToken, args.clientId, args.clientSecret)
                
                val tokenObject = createTokenResponse(tokenResponse)
                invoke.resolve(tokenObject)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh token", e)
                invoke.reject("Failed to refresh token: ${e.message}")
            }
        }
    }
    
    private suspend fun exchangeAuthCodeForTokens(
        authCode: String,
        clientId: String,
        clientSecret: String?,
        redirectUri: String
    ): Map<String, Any?> = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("code", authCode)
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", redirectUri)
            .apply {
                clientSecret?.let { add("client_secret", it) }
            }
            .build()
        
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(formBody)
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            throw Exception("Token exchange failed: $errorBody")
        }
        
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from token endpoint")
        
        gson.fromJson(responseBody, Map::class.java) as Map<String, Any?>
    }
    
    private suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String?
    ): Map<String, Any?> = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .apply {
                clientSecret?.let { add("client_secret", it) }
            }
            .build()
        
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(formBody)
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            throw Exception("Token refresh failed: $errorBody")
        }
        
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from token endpoint")
        
        gson.fromJson(responseBody, Map::class.java) as Map<String, Any?>
    }
    
    private suspend fun revokeAccessToken(accessToken: String) = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("token", accessToken)
            .build()
        
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/revoke")
            .post(formBody)
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            Log.w(TAG, "Token revocation response: $errorBody")
            // Google's revocation endpoint returns 400 if token is already invalid
            // We don't throw here as this is not critical for sign-out
            if (response.code != 400) {
                throw Exception("Token revocation failed with code ${response.code}")
            }
        }
    }
    
    private fun createTokenResponse(tokenResponse: Map<String, Any?>, grantedScopes: List<String>? = null): JSObject {
        val expiresIn = (tokenResponse["expires_in"] as? Number)?.toLong() ?: 3600
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)
        
        return JSObject().apply {
            put("idToken", tokenResponse["id_token"] as? String ?: "")
            put("accessToken", tokenResponse["access_token"] as? String ?: "")
            put("refreshToken", tokenResponse["refresh_token"] as? String ?: "")
            put("expiresAt", expiresAt)
            
            // Include granted scopes if available, otherwise try to parse from the token response
            val scopes = grantedScopes ?: (tokenResponse["scope"] as? String)?.split(" ") ?: emptyList()
            put("scopes", JSArray().apply {
                scopes.forEach { put(it) }
            })
        }
    }
}