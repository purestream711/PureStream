package com.purestream.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.gson.GsonBuilder
import com.purestream.data.api.PlexAuthApiService
import com.purestream.data.model.*
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PlexAuthRepository(private val context: Context) {
    
    private val sharedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("plex_auth", Context.MODE_PRIVATE)
    }
    
    private val authApiService: PlexAuthApiService by lazy {
        val gson = GsonBuilder()
            .setLenient()
            .create()
        
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d("PlexAPI", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
            
        Retrofit.Builder()
            .baseUrl("https://plex.tv/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(PlexAuthApiService::class.java)
    }
    
    suspend fun signInWithCredentials(email: String, password: String): Result<String> {
        return try {
            Log.d("PlexAuth", "Attempting sign in for email: $email")
            
            val response = authApiService.signIn(login = email, password = password)
            Log.d("PlexAuth", "Sign in response code: ${response.code()}")
            
            if (response.isSuccessful && response.body()?.user != null) {
                val authToken = response.body()!!.user!!.authToken
                Log.d("PlexAuth", "Authentication successful, token received")
                saveAuthToken(authToken)
                Result.success(authToken)
            } else {
                val errorMsg = if (response.code() == 401) {
                    "Invalid email or password"
                } else if (response.code() == 422) {
                    "Invalid login credentials"
                } else {
                    "Authentication failed: ${response.message()}"
                }
                Log.e("PlexAuth", "Authentication failed: ${response.code()} - ${response.message()}")
                // Log response body for debugging
                response.errorBody()?.let { errorBody ->
                    Log.e("PlexAuth", "Error response body: ${errorBody.string()}")
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("PlexAuth", "Network error during authentication", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
    
    suspend fun createPinV1ForQRCode(): Result<PlexPinResponse> {
        return try {
            Log.d("PlexAuth", "Creating v1 PIN for QR code authentication")
            val response = authApiService.createPinV1(strong = true)
            Log.d("PlexAuth", "V1 PIN creation response code: ${response.code()}")

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    Log.d("PlexAuth", "V1 PIN created successfully: ${responseBody.pin.code}")
                    Result.success(responseBody.pin)
                } else {
                    Log.e("PlexAuth", "V1 PIN creation succeeded but response body is null")
                    Result.failure(Exception("PIN creation succeeded but no data returned"))
                }
            } else {
                Log.e("PlexAuth", "Failed to create v1 PIN: ${response.code()} - ${response.message()}")
                Result.failure(Exception("PIN creation failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("PlexAuth", "Exception during v1 PIN creation", e)
            Result.failure(e)
        }
    }

    suspend fun createPin(): Result<PlexPinResponse> {
        return try {
            Log.d("PlexAuth", "Creating PIN for authentication")

            // Get screen resolution
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val screenResolution = "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"

            // Get Android version
            val platformVersion = Build.VERSION.SDK_INT.toString()

            // Get actual device model name
            val deviceModel = Build.MODEL ?: "Android TV"

            // Create PIN with all required headers for device metadata
            val response = authApiService.createPin(
                strong = true,
                product = "Pure Stream",
                version = "1.0.0",
                clientId = "f7f96c82-17e5-4b11-a52f-74b1107bd0fb",
                platform = "Android TV",
                platformVersion = platformVersion,
                device = "Android TV",
                deviceName = deviceModel,
                model = deviceModel,
                screenResolution = screenResolution,
                deviceScreenResolution = screenResolution
            )

            Log.d("PlexAuth", "PIN creation response code: ${response.code()}")
            Log.d("PlexAuth", "PIN creation response headers: ${response.headers()}")
            
            if (response.isSuccessful) {
                // Check if we have a response body
                val responseBody = response.body()
                if (responseBody != null) {
                    Log.d("PlexAuth", "PIN created successfully: ${responseBody.code}")
                    Result.success(responseBody)
                } else {
                    Log.e("PlexAuth", "PIN creation succeeded but response body is null")
                    Result.failure(Exception("PIN creation succeeded but no data returned"))
                }
            } else {
                Log.e("PlexAuth", "Failed to create PIN: ${response.code()} - ${response.message()}")
                // Log both error body and raw response for debugging
                val errorBody = response.errorBody()
                if (errorBody != null) {
                    val errorString = errorBody.string()
                    Log.e("PlexAuth", "Error response body (raw): $errorString")
                    // Check if it's a JSON parsing error by looking for common JSON error indicators
                    if (errorString.contains("Expected BEGIN_OBJECT")) {
                        Log.e("PlexAuth", "JSON parsing error detected - Plex returned string instead of JSON")
                        Result.failure(Exception("Invalid response format from Plex server"))
                    } else {
                        Result.failure(Exception("PIN creation failed: $errorString"))
                    }
                } else {
                    Result.failure(Exception("PIN creation failed: ${response.message()}"))
                }
            }
        } catch (e: Exception) {
            Log.e("PlexAuth", "Exception during PIN creation", e)
            if (e.message?.contains("Expected BEGIN_OBJECT but was STRING") == true) {
                Log.e("PlexAuth", "JSON parsing error - Plex API returned string instead of expected JSON")
                Result.failure(Exception("Plex server returned unexpected response format"))
            } else {
                Result.failure(Exception("Network error: ${e.message}"))
            }
        }
    }
    
    suspend fun checkPinStatus(pinId: String): Result<PlexPinResponse> {
        return try {
            val response = authApiService.checkPin(pinId)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.pin)
            } else {
                Result.failure(Exception("Failed to check PIN status: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun waitForPinAuthentication(pinId: String, timeoutMinutes: Int = 5): Result<String> {
        val timeoutMillis = timeoutMinutes * 60 * 1000L
        val startTime = System.currentTimeMillis()

        Log.d("PlexAuth", "Waiting for PIN authentication, ID: $pinId")

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            val result = checkPinStatus(pinId)

            if (result.isSuccess) {
                val pinResponse = result.getOrNull()
                if (pinResponse?.authToken != null) {
                    Log.d("PlexAuth", "PIN authentication successful, token received")
                    saveAuthToken(pinResponse.authToken)
                    return Result.success(pinResponse.authToken)
                }
            }

            delay(2000) // Check every 2 seconds
        }

        Log.e("PlexAuth", "PIN authentication timed out")
        return Result.failure(Exception("PIN authentication timed out"))
    }

    /**
     * Generate OAuth URL for redirect-based Plex authentication (DEPRECATED)
     * Users will be directed to plex.tv to login, then redirected back to app with token
     */
    @Deprecated("Use generateWebAuthUrl with PIN-based flow instead")
    fun generateOAuthUrl(clientId: String, redirectUri: String): String {
        // Plex OAuth authentication endpoint
        // This uses the Plex sign-in page with redirect back to our app via custom URI scheme
        return "https://app.plex.tv/auth?clientID=$clientId&redirectUri=${Uri.encode(redirectUri)}&forceLogin=true"
    }

    /**
     * Generate WebView auth URL using PIN-based authentication
     * This is the recommended approach that works reliably on both mobile and TV
     *
     * Format matches Tautulli's implementation: uses #!? and proper context parameters
     *
     * @param clientId The client identifier for the app
     * @param pinCode The PIN code generated from createPin()
     * @param redirectUri The custom URI scheme to redirect to (e.g., purestream://auth-callback)
     * @return The complete URL to load in WebView for authentication
     */
    fun generateWebAuthUrl(clientId: String, pinCode: String, redirectUri: String): String {
        Log.d("PlexAuth", "Generating web auth URL with PIN: $pinCode")

        // Get screen resolution
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenResolution = "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"

        // Determine if device is TV or mobile
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val isTV = uiMode == Configuration.UI_MODE_TYPE_TELEVISION
        val layout = if (isTV) "tv" else "mobile"

        // Get Android version
        val platformVersion = Build.VERSION.SDK_INT.toString()

        // Get actual device model name
        val deviceModel = Build.MODEL ?: "Android TV"

        // Build OAuth parameters map - Tautulli encodes BOTH keys and values
        val params = linkedMapOf(
            "clientID" to clientId,
            "context[device][product]" to "Pure Stream",
            "context[device][version]" to "1.0.0",
            "context[device][platform]" to "Android TV",
            "context[device][platformVersion]" to platformVersion,
            "context[device][device]" to "Android TV",
            "context[device][deviceName]" to deviceModel,
            "context[device][model]" to deviceModel,
            "context[device][screenResolution]" to screenResolution,
            "context[device][layout]" to layout,
            "code" to pinCode
        )

        // URL encode both keys and values (like Tautulli's encodeData function)
        val encodedParams = params.map { (key, value) ->
            "${Uri.encode(key)}=${Uri.encode(value)}"
        }.joinToString("&")

        val url = "https://app.plex.tv/auth/#!?$encodedParams"

        Log.d("PlexAuth", "Generated URL: $url")
        return url
    }

    /**
     * Extract and validate token from OAuth callback URI
     * The token is passed back from Plex.tv as a query parameter or fragment
     */
    fun extractOAuthToken(uri: String): String? {
        return try {
            Log.d("PlexAuth", "Extracting OAuth token from URI")

            // Try query parameter: ?token=XXX
            if (uri.contains("token=")) {
                val token = uri.substringAfter("token=").substringBefore("&").substringBefore("#")
                if (token.isNotEmpty()) {
                    Log.d("PlexAuth", "OAuth token extracted from query parameter")
                    return token
                }
            }

            // Try fragment: #token=XXX
            if (uri.contains("#token=")) {
                val token = uri.substringAfter("#token=").substringBefore("&")
                if (token.isNotEmpty()) {
                    Log.d("PlexAuth", "OAuth token extracted from fragment")
                    return token
                }
            }

            Log.w("PlexAuth", "No valid token found in OAuth callback URI")
            null
        } catch (e: Exception) {
            Log.e("PlexAuth", "Error extracting OAuth token", e)
            null
        }
    }

    /**
     * Validate OAuth token and store it
     * This method should be called after receiving the OAuth callback
     */
    suspend fun handleOAuthCallback(token: String): Result<String> {
        return try {
            Log.d("PlexAuth", "Processing OAuth callback with token (length: ${token.length})")

            // Validate token is not empty
            if (token.isEmpty()) {
                Log.e("PlexAuth", "OAuth token is empty")
                return Result.failure(Exception("Invalid token received from OAuth callback"))
            }

            // Save the token
            saveAuthToken(token)
            Log.d("PlexAuth", "OAuth token saved successfully")

            Result.success(token)
        } catch (e: Exception) {
            Log.e("PlexAuth", "Error handling OAuth callback", e)
            Result.failure(Exception("OAuth callback processing failed: ${e.message}"))
        }
    }

    fun saveAuthToken(token: String) {
        sharedPrefs.edit()
            .putString("auth_token", token)
            .putLong("auth_time", System.currentTimeMillis())
            .apply()
    }
    
    fun getAuthToken(): String? {
        return sharedPrefs.getString("auth_token", null)
    }
    
    fun clearAuthToken() {
        sharedPrefs.edit()
            .remove("auth_token")
            .remove("auth_time")
            .commit()  // Use commit() instead of apply() to ensure synchronous clearing
    }
    
    fun isAuthenticated(): Boolean {
        val token = getAuthToken()
        val authTime = sharedPrefs.getLong("auth_time", 0)
        val currentTime = System.currentTimeMillis()
        
        // Check if token exists and is not older than 30 days
        return token != null && (currentTime - authTime) < (30 * 24 * 60 * 60 * 1000L)
    }
    
    suspend fun validateStoredToken(): Result<String> {
        val token = getAuthToken()
        if (token == null) {
            return Result.failure(Exception("No stored token"))
        }
        
        return try {
            // Test the token by making a simple API call to Plex
            // For now, we'll assume the token is valid if it exists and hasn't expired
            if (isAuthenticated()) {
                Result.success(token)
            } else {
                clearAuthToken()
                Result.failure(Exception("Token expired"))
            }
        } catch (e: Exception) {
            clearAuthToken()
            Result.failure(Exception("Token validation failed: ${e.message}"))
        }
    }
}