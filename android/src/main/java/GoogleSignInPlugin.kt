package app.tauri.googleauth

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.webkit.WebView
import androidx.activity.result.ActivityResult
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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
    var flowType: String? = null
}

@InvokeArg
class SignOutArgs {
    var accessToken: String? = null
    var flowType: String? = null
}

@InvokeArg
class RefreshTokenArgs {
    var refreshToken: String? = null
    lateinit var clientId: String
    var clientSecret: String? = null
    var scopes: List<String>? = null
    var flowType: String? = null
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
    private lateinit var credentialManager: CredentialManager

    override fun load(webView: WebView) {
        super.load(webView)
        RESULT_EXTRA_PREFIX = activity.packageName + "."

        authorizationClient = Identity.getAuthorizationClient(activity)
        signInClient = Identity.getSignInClient(activity)
        credentialManager = CredentialManager.create(activity)
    }
    
    @Command
    fun signIn(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(SignInArgs::class.java)

            if (args.clientId.isEmpty()) {
                invoke.reject("Client ID is required")
                return
            }

            if (args.flowType == "web") {
                if (args.clientSecret == null) {
                    invoke.reject("clientSecret is required for web flow")
                    return
                }
                signInWeb(invoke, args)
            } else {
                signInNative(invoke, args)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sign-in", e)
            invoke.reject("Failed to start sign-in: ${e.message}")
        }
    }

    private fun signInWeb(invoke: Invoke, args: SignInArgs) {
        val intent = Intent(activity, GoogleSignInActivity::class.java).apply {
            putExtra(CLIENT_ID, args.clientId)
            putExtra(CLIENT_SECRET, args.clientSecret)
            putExtra(SCOPES, args.scopes.toTypedArray())
            putExtra(REDIRECT_URI, args.redirectUri)
            putExtra(TITLE, "Sign in with Google")
            putExtra(SUBTITLE, "Choose an account")
        }
        startActivityForResult(invoke, intent, "signInResult")
    }

    private fun signInNative(invoke: Invoke, args: SignInArgs) {
        scope.launch {
            try {
                // Step 1: Get ID token via CredentialManager (using main activity)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(args.clientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                // Step 2: Get access token via AuthorizationClient
                startNativeAuthorization(invoke, idToken, args.scopes)

            } catch (e: GetCredentialCancellationException) {
                invoke.reject("Sign-in cancelled: ${e.message}")
            } catch (e: NoCredentialException) {
                invoke.reject("No Google account found: ${e.message}")
            } catch (e: GetCredentialException) {
                invoke.reject("Credential error [${e.type}]: ${e.message}")
            } catch (e: Exception) {
                invoke.reject("Sign-in failed [${e.javaClass.simpleName}]: ${e.message}")
            }
        }
    }

    private var pendingNativeInvoke: Invoke? = null
    private var pendingIdToken: String? = null

    private fun startNativeAuthorization(invoke: Invoke, idToken: String, scopes: List<String>) {
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(scopes.map { Scope(it) })
            .build()

        authorizationClient.authorize(authRequest)
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                    if (pendingIntent != null) {
                        // Need to launch activity for user consent
                        pendingNativeInvoke = invoke
                        pendingIdToken = idToken
                        val intent = Intent(activity, NativeSignInActivity::class.java).apply {
                            putExtra(NativeSignInActivity.EXTRA_PENDING_INTENT, pendingIntent)
                        }
                        startActivityForResult(invoke, intent, "nativeAuthorizationResult")
                    } else {
                        invoke.reject("No pending intent available for authorization")
                    }
                } else {
                    // Already authorized
                    val accessToken = authResult.accessToken
                    if (accessToken != null) {
                        val grantedScopes = authResult.grantedScopes.map { it.toString() }.toTypedArray()
                        resolveNativeSignIn(invoke, idToken, accessToken, grantedScopes)
                    } else {
                        invoke.reject("Failed to get access token")
                    }
                }
            }
            .addOnFailureListener { e ->
                invoke.reject("Authorization failed [${e.javaClass.simpleName}]: ${e.message}")
            }
    }

    private fun resolveNativeSignIn(invoke: Invoke, idToken: String?, accessToken: String, grantedScopes: Array<String>) {
        val tokenObject = JSObject().apply {
            put("idToken", idToken ?: "")
            put("accessToken", accessToken)
            put("refreshToken", "")
            put("expiresAt", 0)
            put("scopes", JSArray().apply {
                grantedScopes.forEach { put(it) }
            })
        }
        invoke.resolve(tokenObject)
    }

    @ActivityCallback
    private fun nativeAuthorizationResult(invoke: Invoke, result: ActivityResult) {
        val idToken = pendingIdToken
        pendingNativeInvoke = null
        pendingIdToken = null

        if (result.resultCode == Activity.RESULT_CANCELED) {
            val error = result.data?.getStringExtra(NativeSignInActivity.RESULT_ERROR)
            invoke.reject(error ?: "Authorization cancelled")
            return
        }

        val data = result.data
        if (data == null) {
            invoke.reject("No data received from authorization")
            return
        }

        val accessToken = data.getStringExtra(NativeSignInActivity.RESULT_ACCESS_TOKEN)
        val grantedScopes = data.getStringArrayExtra(NativeSignInActivity.RESULT_GRANTED_SCOPES)

        if (accessToken == null) {
            invoke.reject("No access token received")
            return
        }

        resolveNativeSignIn(invoke, idToken, accessToken, grantedScopes ?: emptyArray())
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
        
        if (authCode == null || data == null) {
            invoke.reject("No authorization code received")
            return
        }

        val clientId = data.getStringExtra(RESULT_EXTRA_PREFIX + CLIENT_ID)
        val clientSecret = data.getStringExtra(RESULT_EXTRA_PREFIX + CLIENT_SECRET)
        val redirectUri = data.getStringExtra(RESULT_EXTRA_PREFIX + REDIRECT_URI) ?: ""
        val grantedScopes = data.getStringArrayExtra(RESULT_EXTRA_PREFIX + GRANTED_SCOPES)
        
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

                if (args.flowType == "web") {
                    signOutWeb(invoke, args)
                } else {
                    signOutNative(invoke, args)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sign-out failed", e)
                invoke.reject("Sign-out failed: ${e.message}")
            }
        }
    }

    private suspend fun signOutWeb(invoke: Invoke, args: SignOutArgs) {
        val accessToken = args.accessToken
        if (accessToken != null) {
            try {
                revokeAccessToken(accessToken)
                Log.d(TAG, "Access token revoked successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to revoke access token: ${e.message}")
            }
        }

        try {
            signInClient.signOut().await()
            Log.d(TAG, "Signed out from Google Sign-In client")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sign out from Google Sign-In client: ${e.message}")
        }

        val ret = JSObject()
        ret.put("success", true)
        invoke.resolve(ret)
    }

    private suspend fun signOutNative(invoke: Invoke, args: SignOutArgs) {
        val accessToken = args.accessToken
        if (accessToken != null) {
            try {
                revokeAccessToken(accessToken)
                Log.d(TAG, "Access token revoked successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to revoke access token: ${e.message}")
            }
        }

        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Log.d(TAG, "Credential state cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear credential state: ${e.message}")
        }

        val ret = JSObject()
        ret.put("success", true)
        invoke.resolve(ret)
    }
    
    @Command
    fun refreshToken(invoke: Invoke) {
        scope.launch {
            try {
                val args = invoke.parseArgs(RefreshTokenArgs::class.java)

                if (args.flowType == "web") {
                    if (args.clientSecret == null) {
                        invoke.reject("clientSecret is required for web flow")
                        return@launch
                    }
                    if (args.refreshToken == null) {
                        invoke.reject("refreshToken is required for web flow")
                        return@launch
                    }
                    refreshWeb(invoke, args)
                } else {
                    if (args.scopes.isNullOrEmpty()) {
                        invoke.reject("scopes is required for native flow refresh")
                        return@launch
                    }
                    refreshNative(invoke, args)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh token", e)
                invoke.reject("Failed to refresh token: ${e.message}")
            }
        }
    }

    private suspend fun refreshWeb(invoke: Invoke, args: RefreshTokenArgs) {
        val tokenResponse = refreshAccessToken(args.refreshToken!!, args.clientId, args.clientSecret)
        val tokenObject = createTokenResponse(tokenResponse)
        invoke.resolve(tokenObject)
    }

    private suspend fun refreshNative(invoke: Invoke, args: RefreshTokenArgs) {
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(args.scopes!!.map { Scope(it) })
            .build()

        val authResult = authorizationClient.authorize(authRequest).await()
        val accessToken = authResult.accessToken

        if (accessToken == null) {
            invoke.reject("Failed to get access token")
            return
        }

        val tokenObject = JSObject().apply {
            put("idToken", "")
            put("accessToken", accessToken)
            put("refreshToken", "")
            put("expiresAt", 0)
            put("scopes", JSArray().apply {
                authResult.grantedScopes.forEach { put(it.toString()) }
            })
        }
        invoke.resolve(tokenObject)
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