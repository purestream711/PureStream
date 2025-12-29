package com.purestream.ui.screens

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.purestream.data.model.AuthenticationStatus
import com.purestream.data.model.PlexPinResponse
import com.purestream.data.repository.PlexAuthRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class PlexAuthViewModel(application: Application) : AndroidViewModel(application) {
    
    private val authRepository = PlexAuthRepository(application)
    
    var authStatus by mutableStateOf(AuthenticationStatus.IDLE)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    var pinResponse by mutableStateOf<PlexPinResponse?>(null)
        private set
    
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    
    var authToken by mutableStateOf<String?>(null)
        private set
    
    var showErrorDialog by mutableStateOf(false)
        private set
    
    var authenticationMethod by mutableStateOf<String?>(null)
        private set

    // Track if user just logged out (to skip auto-check)
    var justLoggedOut by mutableStateOf(false)
        private set

    // Separate QR code authentication state
    var qrAuthStatus by mutableStateOf(AuthenticationStatus.IDLE)
        private set

    var qrErrorMessage by mutableStateOf<String?>(null)
        private set

    // OAuth redirect authentication state
    var oauthStatus by mutableStateOf(AuthenticationStatus.IDLE)
        private set

    var oauthErrorMessage by mutableStateOf<String?>(null)
        private set

    // WebView authentication state
    var webAuthUrl by mutableStateOf<String?>(null)
        private set

    private var pinAuthJob: Job? = null
    
    fun signInWithCredentials() {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please enter both email and password"
            showErrorDialog = true
            return
        }
        
        Log.d("PlexAuthViewModel", "Starting credential authentication")
        authStatus = AuthenticationStatus.LOADING
        errorMessage = null
        authenticationMethod = "credentials"
        
        viewModelScope.launch {
            val result = authRepository.signInWithCredentials(email, password)
            
            if (result.isSuccess) {
                val token = result.getOrNull()
                Log.d("PlexAuthViewModel", "Credential authentication successful, token: $token")
                
                // Cancel any running PIN authentication since credentials succeeded
                pinAuthJob?.cancel()
                pinAuthJob = null
                Log.d("PlexAuthViewModel", "Cancelled PIN authentication job due to successful credentials auth")
                
                authToken = token
                authStatus = AuthenticationStatus.SUCCESS
            } else {
                Log.e("PlexAuthViewModel", "Credential authentication failed: ${result.exceptionOrNull()?.message}")
                authStatus = AuthenticationStatus.ERROR
                errorMessage = result.exceptionOrNull()?.message ?: "Invalid credentials"
                showErrorDialog = true
            }
        }
    }
    
    fun createPinForQRCode() {
        // If credentials authentication already succeeded, don't start QR code auth
        if (authenticationMethod == "credentials" && authStatus == AuthenticationStatus.SUCCESS) {
            Log.d("PlexAuthViewModel", "Skipping QR code authentication - credentials auth already succeeded")
            return
        }

        try {
            Log.d("PlexAuthViewModel", "Starting PIN authentication")
            qrAuthStatus = AuthenticationStatus.LOADING
            qrErrorMessage = null

            viewModelScope.launch {
                try {
                    // Use v1 PIN API for QR code to get short 4-digit code
                    val result = authRepository.createPinV1ForQRCode()

                    if (result.isSuccess) {
                        val pin = result.getOrNull()
                        if (pin != null && pin.id > 0 && pin.code.isNotBlank()) {
                            pinResponse = pin
                            Log.d("PlexAuthViewModel", "QR code PIN created successfully: ${pin.code}")
                            qrAuthStatus = AuthenticationStatus.WAITING_FOR_PIN
                            waitForPinAuthentication()
                        } else {
                            Log.e("PlexAuthViewModel", "PIN response is invalid")
                            qrAuthStatus = AuthenticationStatus.ERROR
                            qrErrorMessage = "Received invalid PIN from Plex server"
                        }
                    } else {
                        Log.e("PlexAuthViewModel", "Failed to create PIN: ${result.exceptionOrNull()?.message}")
                        qrAuthStatus = AuthenticationStatus.ERROR
                        qrErrorMessage = result.exceptionOrNull()?.message ?: "Failed to connect to Plex server"
                    }
                } catch (e: Exception) {
                    Log.e("PlexAuthViewModel", "Exception in createPinForQRCode", e)
                    qrAuthStatus = AuthenticationStatus.ERROR
                    qrErrorMessage = "Error connecting to Plex: ${e.message}"
                }
            }
        } catch (e: Exception) {
            Log.e("PlexAuthViewModel", "Exception in createPinForQRCode (outer)", e)
            qrAuthStatus = AuthenticationStatus.ERROR
            qrErrorMessage = "Unexpected error: ${e.message}"
        }
    }
    
    private fun waitForPinAuthentication() {
        val pinId = pinResponse?.id?.toString() ?: return

        pinAuthJob = viewModelScope.launch {
            Log.d("PlexAuthViewModel", "Starting cancellable PIN authentication with ID: $pinId")
            val timeoutMillis = 5 * 60 * 1000L // 5 minutes
            val startTime = System.currentTimeMillis()

            while (isActive && System.currentTimeMillis() - startTime < timeoutMillis) {
                try {
                    val result = authRepository.checkPinStatus(pinId)

                    if (result.isSuccess) {
                        val pinResponse = result.getOrNull()
                        if (pinResponse?.authToken != null) {
                            Log.d("PlexAuthViewModel", "PIN authentication successful, token received")
                            authToken = pinResponse.authToken
                            // Save token to SharedPreferences (same as email/password auth)
                            authRepository.saveAuthToken(pinResponse.authToken)
                            authStatus = AuthenticationStatus.SUCCESS
                            qrAuthStatus = AuthenticationStatus.SUCCESS
                            authenticationMethod = "qr_code"
                            pinAuthJob = null
                            return@launch
                        }
                    }
                    
                    // Check if job is still active before delaying
                    if (isActive) {
                        delay(2000) // Check every 2 seconds
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e("PlexAuthViewModel", "Error checking PIN status: ${e.message}")
                    }
                    break
                }
            }
            
            // Only update status if job wasn't cancelled
            if (isActive) {
                Log.e("PlexAuthViewModel", "PIN authentication timed out")
                qrAuthStatus = AuthenticationStatus.ERROR
                qrErrorMessage = "PIN authentication timed out"
            }
            pinAuthJob = null
        }
    }
    
    fun clearError() {
        errorMessage = null
        authStatus = AuthenticationStatus.IDLE
    }
    
    fun dismissErrorDialog() {
        showErrorDialog = false
        errorMessage = null
        // Clear password field when error dialog is dismissed
        password = ""
    }
    
    fun resetQrAuth() {
        // Cancel any running PIN authentication job
        pinAuthJob?.cancel()
        pinAuthJob = null

        qrAuthStatus = AuthenticationStatus.IDLE
        qrErrorMessage = null
        pinResponse = null
    }

    fun resetAuth() {
        // Cancel any running PIN authentication job
        pinAuthJob?.cancel()
        pinAuthJob = null

        authStatus = AuthenticationStatus.IDLE
        errorMessage = null
        pinResponse = null
        email = ""
        password = ""
        authToken = null
        showErrorDialog = false
        authenticationMethod = null
        qrAuthStatus = AuthenticationStatus.IDLE
        qrErrorMessage = null

        // CRITICAL FIX: Reset OAuth status to prevent auto-navigation after logout
        oauthStatus = AuthenticationStatus.IDLE
        oauthErrorMessage = null
        webAuthUrl = null
    }
    
    fun isAuthenticated(): Boolean {
        return authRepository.isAuthenticated()
    }
    
    fun checkForStoredAuth() {
        Log.d("PlexAuthViewModel", "Checking for stored authentication")
        authStatus = AuthenticationStatus.LOADING
        errorMessage = null
        
        viewModelScope.launch {
            val result = authRepository.validateStoredToken()
            
            if (result.isSuccess) {
                val token = result.getOrNull()
                Log.d("PlexAuthViewModel", "Stored token is valid")
                authToken = token
                authStatus = AuthenticationStatus.SUCCESS
            } else {
                Log.d("PlexAuthViewModel", "No valid stored token found: ${result.exceptionOrNull()?.message}")
                authStatus = AuthenticationStatus.IDLE
            }
        }
    }
    
    fun dismissSuccess() {
        authStatus = AuthenticationStatus.IDLE
        authToken = null
    }
    
    fun monitorPinAuthentication(pinId: String) {
        Log.d("PlexAuthViewModel", "Starting PIN monitoring for ID: $pinId")
        qrAuthStatus = AuthenticationStatus.WAITING_FOR_PIN
        qrErrorMessage = null
        
        pinAuthJob = viewModelScope.launch {
            Log.d("PlexAuthViewModel", "Starting cancellable PIN monitoring with ID: $pinId")
            val timeoutMillis = 5 * 60 * 1000L // 5 minutes
            val startTime = System.currentTimeMillis()
            
            while (isActive && System.currentTimeMillis() - startTime < timeoutMillis) {
                try {
                    val result = authRepository.checkPinStatus(pinId)
                    
                    if (result.isSuccess) {
                        val pinResponse = result.getOrNull()
                        if (pinResponse?.authToken != null) {
                            Log.d("PlexAuthViewModel", "PIN authentication successful, token received")
                            authToken = pinResponse.authToken
                            // Save token to SharedPreferences (same as email/password auth)
                            authRepository.saveAuthToken(pinResponse.authToken)
                            authStatus = AuthenticationStatus.SUCCESS
                            qrAuthStatus = AuthenticationStatus.SUCCESS
                            authenticationMethod = "qr_code"
                            pinAuthJob = null
                            return@launch
                        }
                    }
                    
                    // Check if job is still active before delaying
                    if (isActive) {
                        delay(2000) // Check every 2 seconds
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e("PlexAuthViewModel", "Error checking PIN status: ${e.message}")
                    }
                    break
                }
            }
            
            // Only update status if job wasn't cancelled
            if (isActive) {
                Log.e("PlexAuthViewModel", "PIN authentication timed out")
                qrAuthStatus = AuthenticationStatus.ERROR
                qrErrorMessage = "PIN authentication failed"
            }
            pinAuthJob = null
        }
    }
    
    /**
     * Initiate OAuth redirect login
     * Opens browser to plex.tv for user authentication
     * User will be redirected back to app with token via purestream://auth-callback
     */
    fun initiateOAuthLogin(onOAuthUrlReady: (String) -> Unit) {
        Log.d("PlexAuthViewModel", "Initiating OAuth redirect login")
        oauthStatus = AuthenticationStatus.LOADING
        oauthErrorMessage = null
        authenticationMethod = "oauth_redirect"

        // Generate OAuth URL with redirect to purestream:// scheme
        val clientId = "purestream-android" // Package identifier for Plex
        val redirectUri = "purestream://auth-callback"
        val oauthUrl = authRepository.generateOAuthUrl(clientId, redirectUri)

        Log.d("PlexAuthViewModel", "OAuth URL generated: $oauthUrl")
        oauthStatus = AuthenticationStatus.WAITING_FOR_PIN // Reuse WAITING_FOR_PIN for "waiting for user action"
        onOAuthUrlReady(oauthUrl)
    }

    /**
     * Handle OAuth callback from purestream://auth-callback
     * This is called from MainActivity when the deep link callback is received
     */
    fun handleOAuthCallback(callbackUri: String) {
        Log.d("PlexAuthViewModel", "Handling OAuth callback")

        // Extract token from the callback URI
        val token = authRepository.extractOAuthToken(callbackUri)

        if (token != null) {
            Log.d("PlexAuthViewModel", "OAuth token extracted successfully")
            viewModelScope.launch {
                val result = authRepository.handleOAuthCallback(token)

                if (result.isSuccess) {
                    Log.d("PlexAuthViewModel", "OAuth authentication successful")
                    authToken = token
                    authStatus = AuthenticationStatus.SUCCESS
                    oauthStatus = AuthenticationStatus.SUCCESS
                } else {
                    Log.e("PlexAuthViewModel", "OAuth callback processing failed: ${result.exceptionOrNull()?.message}")
                    authStatus = AuthenticationStatus.ERROR
                    oauthStatus = AuthenticationStatus.ERROR
                    oauthErrorMessage = result.exceptionOrNull()?.message ?: "Failed to process OAuth callback"
                }
            }
        } else {
            Log.e("PlexAuthViewModel", "Failed to extract token from OAuth callback")
            authStatus = AuthenticationStatus.ERROR
            oauthStatus = AuthenticationStatus.ERROR
            oauthErrorMessage = "Invalid authentication response"
        }
    }

    /**
     * Initiate WebView-based authentication
     * Generates a PIN and creates URL for in-app WebView
     */
    fun initiateWebViewAuth() {
        Log.d("PlexAuthViewModel", "Initiating WebView authentication")

        // CRITICAL: Set loading state immediately BEFORE launching coroutine
        // This prevents blank screen during navigation
        oauthStatus = AuthenticationStatus.LOADING
        oauthErrorMessage = null
        authenticationMethod = "webview"

        viewModelScope.launch {
            // Step 1: Generate PIN
            val pinResult = authRepository.createPin()

            if (pinResult.isSuccess) {
                val pin = pinResult.getOrNull()!!
                pinResponse = pin

                // Step 2: Generate web auth URL
                val clientId = "f7f96c82-17e5-4b11-a52f-74b1107bd0fb"
                val redirectUri = "purestream://auth-callback"
                val authUrl = authRepository.generateWebAuthUrl(clientId, pin.code, redirectUri)

                Log.d("PlexAuthViewModel", "WebView auth URL generated")

                // Step 3: Trigger WebView screen
                webAuthUrl = authUrl
                oauthStatus = AuthenticationStatus.WAITING_FOR_PIN

                // Step 4: JavaScript interface in WebView will capture the authToken
                // from Plex's AUTH_COMPLETE message and call handleWebViewToken()
                // No polling needed - token is captured directly from JavaScript
            } else {
                Log.e("PlexAuthViewModel", "Failed to generate PIN for WebView auth")
                oauthStatus = AuthenticationStatus.ERROR
                oauthErrorMessage = "Failed to initialize authentication"
            }
        }
    }

    /**
     * Handle token received from WebView redirect
     * Called when WebView intercepts purestream://auth-callback
     */
    fun handleWebViewToken(token: String) {
        Log.d("PlexAuthViewModel", "WebView token received (length: ${token.length})")

        viewModelScope.launch {
            val result = authRepository.handleOAuthCallback(token)

            if (result.isSuccess) {
                Log.d("PlexAuthViewModel", "WebView authentication successful")
                authToken = token
                authStatus = AuthenticationStatus.SUCCESS
                oauthStatus = AuthenticationStatus.SUCCESS
                webAuthUrl = null  // Close WebView
            } else {
                Log.e("PlexAuthViewModel", "WebView token validation failed: ${result.exceptionOrNull()?.message}")
                oauthStatus = AuthenticationStatus.ERROR
                oauthErrorMessage = "Authentication failed"
                webAuthUrl = null  // Close WebView on error
            }
        }
    }

    /**
     * Cancel WebView authentication
     * Called when user presses back button or cancels
     */
    fun cancelWebViewAuth() {
        Log.d("PlexAuthViewModel", "WebView authentication cancelled")
        webAuthUrl = null
        oauthStatus = AuthenticationStatus.IDLE
        oauthErrorMessage = null
    }

    fun logout() {
        Log.d("PlexAuthViewModel", "User logging out")
        // Cancel any running PIN authentication job
        pinAuthJob?.cancel()
        pinAuthJob = null
        authRepository.clearAuthToken()
        resetAuth()
        justLoggedOut = true  // Mark that user explicitly logged out
    }

    fun clearLogoutFlag() {
        justLoggedOut = false
    }

    /**
     * Enter Demo Mode - for Google Play reviewers
     * Bypasses Plex authentication and shows demo content
     */
    fun enterDemoMode() {
        Log.d("PlexAuthViewModel", "Entering Demo Mode")

        // Save demo token to SharedPreferences
        authRepository.saveAuthToken(com.purestream.data.demo.DemoData.DEMO_AUTH_TOKEN)

        // Set authentication states to success
        authStatus = AuthenticationStatus.SUCCESS
        oauthStatus = AuthenticationStatus.SUCCESS
        authenticationMethod = "demo"
        authToken = com.purestream.data.demo.DemoData.DEMO_AUTH_TOKEN

        Log.d("PlexAuthViewModel", "Demo Mode activated successfully")
    }

    /**
     * Check if currently in Demo Mode
     */
    fun isDemoMode(): Boolean {
        val token = authRepository.getAuthToken()
        return com.purestream.data.demo.DemoData.isDemoToken(token)
    }
}