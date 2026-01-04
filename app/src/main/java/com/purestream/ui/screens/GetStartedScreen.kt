package com.purestream.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.purestream.R
import com.purestream.data.repository.PlexAuthRepository
import com.purestream.ui.theme.NetflixDarkGray

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GetStartedScreen(
    onNavigateToProfileSelection: () -> Unit,
    onNavigateToConnectPlex: () -> Unit,
    onNavigateToFeatureShowcase: () -> Unit,
    skipAutoNavigation: Boolean = false
) {
    val context = LocalContext.current

    Log.d("GetStartedScreen", "=== GETSTARTED SCREEN RENDERED === skipAutoNavigation: $skipAutoNavigation")

    // Show logo for 3 seconds, then check for valid Plex token and navigate accordingly
    LaunchedEffect(skipAutoNavigation) {
        Log.d("GetStartedScreen", "LaunchedEffect triggered - skipAutoNavigation: $skipAutoNavigation")

        if (skipAutoNavigation) {
            // User just logged out - skip auto-navigation and go directly to connect plex
            Log.d("GetStartedScreen", "Skipping auto-navigation (user logged out)")
            delay(100) // Brief delay for smooth transition
            Log.d("GetStartedScreen", "Calling onNavigateToConnectPlex()")
            onNavigateToConnectPlex()
            return@LaunchedEffect
        }

        // Display logo for 3 seconds
        Log.d("GetStartedScreen", "Displaying logo for 3 seconds...")
        delay(3000)

        Log.d("GetStartedScreen", "Checking for stored auth token...")
        val authRepository = PlexAuthRepository(context)
        val validationResult = authRepository.validateStoredToken()

        // Check if feature showcase has been shown before
        val sharedPrefs = context.getSharedPreferences("pure_stream_prefs", android.content.Context.MODE_PRIVATE)
        val hasShownFeatureShowcase = sharedPrefs.getBoolean("has_shown_feature_showcase", false)

        Log.d("GetStartedScreen", "hasShownFeatureShowcase: $hasShownFeatureShowcase, validationResult: ${validationResult.isSuccess}")

        if (!hasShownFeatureShowcase) {
            // First time user, show feature showcase
            Log.d("GetStartedScreen", "Navigating to Feature Showcase")
            onNavigateToFeatureShowcase()
        } else if (validationResult.isSuccess) {
            // Valid token found, go directly to profile selection
            Log.d("GetStartedScreen", "Valid token found - navigating to Profile Selection")
            onNavigateToProfileSelection()
        } else {
            // No valid token, go to sign in
            Log.d("GetStartedScreen", "No valid token - navigating to Connect Plex")
            onNavigateToConnectPlex()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixDarkGray)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Dark blue-gray top
                        Color(0xFF0D0D0D), // Pure black middle
                        Color(0xFF0D0D0D)  // Pure black bottom
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo
            Image(
                painter = painterResource(id = R.drawable.purestream_logo),
                contentDescription = "PureStream Logo",
                modifier = Modifier.size(600.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}