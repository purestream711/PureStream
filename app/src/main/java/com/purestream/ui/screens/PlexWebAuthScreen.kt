package com.purestream.ui.screens

import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.purestream.ui.theme.NetflixDarkGray

/**
 * JavaScript interface to capture auth token from Plex's AUTH_COMPLETE message
 */
class PlexAuthInterface(private val onTokenReceived: (String) -> Unit) {
    @JavascriptInterface
    fun handleAuthComplete(authToken: String) {
        Log.d("PlexWebAuth", "JavaScript interface received auth token (length: ${authToken.length})")
        onTokenReceived(authToken)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlexWebAuthScreen(
    authUrl: String,
    onTokenReceived: (String) -> Unit,
    onCancel: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixDarkGray)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar with back button
            TopAppBar(
                title = {
                    Text(
                        text = "Sign in to Plex",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Cancel",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            )

            // WebView
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            // Enable focus for TV remote navigation
                            isFocusable = true
                            isFocusableInTouchMode = true
                            requestFocus()

                            // Add D-pad key event handling for TV navigation
                            setOnKeyListener { view, keyCode, event ->
                                if (event.action == KeyEvent.ACTION_DOWN) {
                                    val scrollAmount = 100 // pixels to scroll
                                    when (keyCode) {
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            (view as WebView).scrollBy(0, scrollAmount)
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            (view as WebView).scrollBy(0, -scrollAmount)
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            (view as WebView).scrollBy(-scrollAmount, 0)
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            (view as WebView).scrollBy(scrollAmount, 0)
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                            // Simulate a click at the center of the view
                                            (view as WebView).performClick()
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }

                            // Add JavaScript interface to capture AUTH_COMPLETE messages
                            addJavascriptInterface(
                                PlexAuthInterface(onTokenReceived),
                                "PlexAuthBridge"
                            )

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean {
                                    val url = request.url.toString()
                                    Log.d("PlexWebAuth", "WebView loading URL: $url")

                                    // Intercept redirect to our custom URI scheme
                                    if (url.startsWith("purestream://auth-callback")) {
                                        Log.d("PlexWebAuth", "Intercepted auth callback: $url")

                                        // Extract token from URL
                                        val token = extractTokenFromUrl(url)
                                        if (token != null) {
                                            Log.d("PlexWebAuth", "Token extracted successfully")
                                            onTokenReceived(token)
                                        } else {
                                            Log.e("PlexWebAuth", "Failed to extract token from callback URL")
                                            hasError = true
                                            errorMessage = "Authentication failed - invalid response"
                                        }
                                        return true
                                    }

                                    return false
                                }

                                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    Log.d("PlexWebAuth", "Page loaded: $url")

                                    // Inject JavaScript to intercept AUTH_COMPLETE messages
                                    val jsCode = """
                                        (function() {
                                            console.log('[PlexAuth] Interceptor installed');

                                            // Store original postMessage
                                            const originalPostMessage = window.postMessage;

                                            // Override postMessage to intercept AUTH_COMPLETE
                                            window.postMessage = function(message, targetOrigin) {
                                                console.log('[PlexAuth] postMessage intercepted:', message);

                                                try {
                                                    // Handle both object and string message formats
                                                    let authData = null;

                                                    // Check if message is an object with type property
                                                    if (message && typeof message === 'object' && message.type === 'AUTH_COMPLETE') {
                                                        authData = message;
                                                    }
                                                    // Check if message is a string (iFrameSizer format)
                                                    else if (typeof message === 'string' && message.includes('AUTH_COMPLETE')) {
                                                        console.log('[PlexAuth] String message detected, parsing...');

                                                        // Extract JSON from iFrameSizer format: [iFrameSizer]...:message:{JSON}
                                                        const jsonMatch = message.match(/message:(\{.*\})$/);
                                                        if (jsonMatch && jsonMatch[1]) {
                                                            console.log('[PlexAuth] JSON extracted from string');
                                                            authData = JSON.parse(jsonMatch[1]);
                                                        }
                                                    }

                                                    // Process AUTH_COMPLETE data
                                                    if (authData && authData.type === 'AUTH_COMPLETE') {
                                                        console.log('[PlexAuth] AUTH_COMPLETE detected');

                                                        // Extract authToken
                                                        if (authData.response && authData.response.authToken) {
                                                            const authToken = authData.response.authToken;
                                                            console.log('[PlexAuth] Sending token to native (length: ' + authToken.length + ')');

                                                            // Call native Android interface
                                                            if (typeof PlexAuthBridge !== 'undefined') {
                                                                PlexAuthBridge.handleAuthComplete(authToken);
                                                            } else {
                                                                console.error('[PlexAuth] PlexAuthBridge not available');
                                                            }
                                                        }
                                                    }
                                                } catch (e) {
                                                    console.error('[PlexAuth] Error processing message:', e);
                                                }

                                                // Call original postMessage
                                                return originalPostMessage.call(this, message, targetOrigin);
                                            };

                                            console.log('[PlexAuth] Interceptor ready');
                                        })();
                                    """.trimIndent()

                                    view.evaluateJavascript(jsCode, null)
                                    Log.d("PlexWebAuth", "JavaScript interceptor injected")
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: android.webkit.WebResourceError
                                ) {
                                    super.onReceivedError(view, request, error)
                                    Log.e("PlexWebAuth", "WebView error: ${error.description}")
                                    isLoading = false
                                    hasError = true
                                    errorMessage = "Network error: ${error.description}"
                                }
                            }

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                setSupportMultipleWindows(false)
                                // Enable zooming for accessibility
                                builtInZoomControls = true
                                displayZoomControls = false
                                // Zoom out the page for better TV viewing
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                textZoom = 85  // Reduce text size to 85% for TV
                                // Security settings
                                allowFileAccess = false
                                allowContentAccess = false
                                // Enable safe browsing
                                safeBrowsingEnabled = true
                            }

                            Log.d("PlexWebAuth", "Loading auth URL: $authUrl")
                            loadUrl(authUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Loading indicator
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NetflixDarkGray.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color(0xFFE5A00D)
                            )
                            Text(
                                text = "Loading Plex authentication...",
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                // Error state
                if (hasError && errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NetflixDarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "Authentication Error",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6B6B)
                            )
                            Text(
                                text = errorMessage!!,
                                fontSize = 14.sp,
                                color = Color(0xFFB3B3B3)
                            )
                            Button(
                                onClick = onCancel,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE5A00D),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Go Back")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extract auth token from callback URL
 * Supports multiple formats: ?authToken=XXX, #authToken=XXX, ?token=XXX, #token=XXX
 */
private fun extractTokenFromUrl(url: String): String? {
    try {
        val uri = Uri.parse(url)

        // Try authToken parameter (Plex standard)
        uri.getQueryParameter("authToken")?.let { return it }

        // Try token parameter (alternative)
        uri.getQueryParameter("token")?.let { return it }

        // Try fragment for authToken
        val fragment = uri.fragment
        if (fragment != null) {
            if (fragment.contains("authToken=")) {
                return fragment.substringAfter("authToken=").substringBefore("&")
            }
            if (fragment.contains("token=")) {
                return fragment.substringAfter("token=").substringBefore("&")
            }
        }

        Log.w("PlexWebAuth", "No token found in URL: $url")
        return null
    } catch (e: Exception) {
        Log.e("PlexWebAuth", "Error parsing token from URL", e)
        return null
    }
}
