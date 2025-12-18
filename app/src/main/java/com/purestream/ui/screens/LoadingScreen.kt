package com.purestream.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.ui.platform.LocalContext
import com.purestream.R
import com.purestream.ui.theme.*
import com.purestream.ui.theme.NetflixDarkGray
import com.purestream.ui.viewmodel.HomeViewModel
import com.purestream.data.model.Profile
import com.purestream.utils.rememberIsMobile
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoadingScreen(
    onLoadingComplete: () -> Unit,
    homeViewModel: HomeViewModel? = null,
    moviesViewModel: com.purestream.ui.viewmodel.MoviesViewModel? = null,
    tvShowsViewModel: com.purestream.ui.viewmodel.TvShowsViewModel? = null,
    currentProfile: Profile? = null
) {
    val context = LocalContext.current
    val isMobile = rememberIsMobile()
    
    // Loading states
    var currentLoadingText by remember { mutableStateOf("Loading Profile...") }
    var loadingProgress by remember { mutableStateOf(0f) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Loading text options
    val loadingTexts = listOf(
        "Loading Profile...",
        "Connecting to Plex...",
        "Loading Libraries...",
        "Curating Dashboard...",
        "Almost Ready..."
    )
    
    // Animate progress bar
    val animatedProgress by animateFloatAsState(
        targetValue = loadingProgress,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "progress_animation"
    )
    
    // Handle actual data loading
    LaunchedEffect(Unit) {
        try {
            // Step 1: Loading Profile (20%)
            currentLoadingText = loadingTexts[0]
            loadingProgress = 0.2f
            delay(500)
            
            if (currentProfile == null) {
                errorMessage = "No profile selected"
                hasError = true
                return@LaunchedEffect
            }
            
            // Step 2: Connecting to Plex (40%)
            currentLoadingText = loadingTexts[1]
            loadingProgress = 0.4f
            delay(500)
            
            // Setup Plex connection
            val authRepository = com.purestream.data.repository.PlexAuthRepository(context)
            val authToken = authRepository.getAuthToken()
            
            if (authToken == null) {
                // Give a brief delay and try again - auth token might still be persisting
                delay(200)
                val retryToken = authRepository.getAuthToken()
                if (retryToken == null) {
                    errorMessage = "No Plex authentication found"
                    hasError = true
                    return@LaunchedEffect
                } else {
                    // Use the retry token
                    setupViewModelsWithAuth(
                        retryToken, 
                        homeViewModel, 
                        moviesViewModel, 
                        tvShowsViewModel, 
                        currentProfile
                    )
                }
            } else {
                // Use the original token
                setupViewModelsWithAuth(
                    authToken, 
                    homeViewModel, 
                    moviesViewModel, 
                    tvShowsViewModel, 
                    currentProfile
                )
            }
            
            // Step 3: Loading Libraries (60%)
            currentLoadingText = loadingTexts[2]
            loadingProgress = 0.6f
            delay(500)
            
            // Step 4: Curating Dashboard (80%)
            currentLoadingText = loadingTexts[3]
            loadingProgress = 0.8f
            delay(1000)
            
            // Step 5: Almost Ready (100%)
            currentLoadingText = loadingTexts[4]
            loadingProgress = 1.0f
            delay(500)
            
            // Complete loading
            onLoadingComplete()
            
        } catch (e: Exception) {
            errorMessage = "Failed to load: ${e.message}"
            hasError = true
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixDarkGray)
    ) {
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section - Logo
        Box(
            modifier = Modifier.weight(2f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.purestream_logo),
                contentDescription = "Pure Stream Logo",
                modifier = Modifier.size(600.dp)
            )
        }
        
        // Bottom section - Loading content and progress
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (hasError) {
                // Error state
                Text(
                    text = "Loading Failed",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = NetflixRed,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = errorMessage,
                    fontSize = 16.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                Button(
                    onClick = onLoadingComplete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = Color.White
                    )
                ) {
                    Text("Continue Anyway")
                }
            } else {
                // Loading state
                Text(
                    text = currentLoadingText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Loading progress bar
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .width(400.dp)
                        .height(8.dp)
                        .progressSemantics(),
                    color = NetflixRed,
                    trackColor = NetflixGray
                )
                
                // Progress percentage (optional visual feedback)
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    fontSize = 16.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
    } // Close the Box for background
}

/**
 * Helper function to setup ViewModels with authentication token
 */

private fun setupViewModelsWithAuth(
    authToken: String,
    homeViewModel: HomeViewModel?,
    moviesViewModel: com.purestream.ui.viewmodel.MoviesViewModel?,
    tvShowsViewModel: com.purestream.ui.viewmodel.TvShowsViewModel?,
    currentProfile: Profile
) {
    // Initialize ViewModels with profile-based library filtering
    homeViewModel?.let { hvm ->
        hvm.setCurrentProfile(currentProfile)
        hvm.setPlexConnectionWithAuth(authToken)
    }
    
    // Initialize Movies ViewModel with profile's selected libraries
    moviesViewModel?.let { mvm ->
        mvm.setPlexConnectionWithAuth(authToken, currentProfile.selectedLibraries)
    }
    
    // Initialize TV Shows ViewModel with profile's selected libraries
    tvShowsViewModel?.let { tvm ->
        tvm.setPlexConnectionWithAuth(authToken, currentProfile.selectedLibraries)
    }
}