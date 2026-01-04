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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoadingScreen(
    onLoadingComplete: () -> Unit,
    homeViewModel: HomeViewModel? = null,
    moviesViewModel: com.purestream.ui.viewmodel.MoviesViewModel? = null,
    tvShowsViewModel: com.purestream.ui.viewmodel.TvShowsViewModel? = null,
    currentProfile: Profile? = null,
    workRequestId: String? = null,
    profileRepository: com.purestream.data.repository.ProfileRepository? = null
) {
    val context = LocalContext.current
    val isMobile = rememberIsMobile()

    // Loading states
    var currentLoadingText by remember { mutableStateOf("Loading Profile...") }
    var loadingProgress by remember { mutableStateOf(0f) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isQuotaError by remember { mutableStateOf(false) }

    // Animate progress bar
    val animatedProgress by animateFloatAsState(
        targetValue = loadingProgress,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "progress_animation"
    )

    // Shimmer for progress bar (left-to-right sweep, resets every 3 seconds)
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val progressBarShimmerOffset by infiniteTransition.animateFloat(
        initialValue = -400f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress_bar_shimmer"
    )

    // Handle actual data loading
    LaunchedEffect(key1 = workRequestId) {
        if (workRequestId != null) {
            // AI Curation loading logic with fake progress and creative messages
            val loadingMessages = listOf(
                "Scanning Your Plex Libraries..." to 0.15f,
                "Consulting with Gemini AI..." to 0.30f,
                "Discovering Trending Media..." to 0.45f,
                "Finding All-Time Greats..." to 0.60f,
                "Curating Action-Packed Picks..." to 0.75f,
                "Matching Content to Your Library..." to 0.90f,
                "Finalizing Your Dashboard..." to 0.99f
            )

            var currentMessageIndex = 0
            var hasFinished = false
            var finishTime = 0L
            val minLoadingDuration = 30000L // 30 seconds minimum
            val startTime = System.currentTimeMillis()

            val workManager = WorkManager.getInstance(context)
            val workInfoLiveData = workManager.getWorkInfoByIdLiveData(UUID.fromString(workRequestId))

            val observer = Observer<WorkInfo> { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        hasFinished = true
                        finishTime = System.currentTimeMillis()
                    }
                    WorkInfo.State.FAILED -> {
                        val error = workInfo.outputData.getString("error") ?: "AI curation failed."
                        errorMessage = error

                        // Check if it's a quota error (Gemini API quota exceeded)
                        isQuotaError = error.contains("quota", ignoreCase = true) ||
                                       error.contains("429", ignoreCase = true) ||
                                       error.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                                       error.contains("rate limit", ignoreCase = true)

                        hasError = true
                    }
                    WorkInfo.State.CANCELLED -> {
                        errorMessage = "AI curation was cancelled."
                        hasError = true
                    }
                    else -> {
                        // RUNNING, ENQUEUED, BLOCKED, etc. - continue fake progress
                    }
                }
            }

            workInfoLiveData.observeForever(observer)

            // Fake progress loop
            while (!hasError) {
                val elapsed = System.currentTimeMillis() - startTime
                val targetProgress = (elapsed / minLoadingDuration.toFloat()).coerceAtMost(1f)

                // Update message based on progress
                if (currentMessageIndex < loadingMessages.size) {
                    val (message, threshold) = loadingMessages[currentMessageIndex]
                    if (targetProgress >= threshold) {
                        currentLoadingText = message
                        currentMessageIndex++
                    }
                }

                // Update progress
                if (hasFinished) {
                    // Work finished - check if minimum time elapsed
                    if (elapsed >= minLoadingDuration) {
                        // Minimum time passed, go to 100% and complete
                        loadingProgress = 1.0f
                        delay(100)
                        workInfoLiveData.removeObserver(observer)
                        onLoadingComplete()
                        break
                    } else {
                        // Hold at 99% until minimum time passes
                        loadingProgress = 0.99f
                    }
                } else {
                    // Still working - show fake progress up to 99%
                    loadingProgress = (targetProgress * 0.99f).coerceAtMost(0.99f)
                }

                delay(100) // Update every 100ms
            }

            workInfoLiveData.removeObserver(observer)
        } else {
            // Original loading logic
            try {
                // Loading text options
                val loadingTexts = listOf(
                    "Loading Profile...",
                    "Connecting to Plex...",
                    "Loading Libraries...",
                    "Curating Dashboard...",
                    "Almost Ready..."
                )

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
                    delay(200)
                    val retryToken = authRepository.getAuthToken()
                    if (retryToken == null) {
                        errorMessage = "No Plex authentication found"
                        hasError = true
                        return@LaunchedEffect
                    } else {
                        setupViewModelsWithAuth(retryToken, homeViewModel, moviesViewModel, tvShowsViewModel, currentProfile)
                    }
                } else {
                    setupViewModelsWithAuth(authToken, homeViewModel, moviesViewModel, tvShowsViewModel, currentProfile)
                }

                // Step 3: Loading Libraries (60%)
                currentLoadingText = loadingTexts[2]
                loadingProgress = 0.6f
                
                // Trigger background loading for all ViewModels in parallel
                // This populates the database cache while the user is still on the loading screen
                if (homeViewModel != null && moviesViewModel != null && tvShowsViewModel != null) {
                    android.util.Log.d("LoadingScreen", "Starting background data pre-fetch for Home, Movies, and TV Shows")
                }

                // Wait for HomeViewModel to start loading if it hasn't already
                if (homeViewModel != null) {
                    // Poll for completion (max 15 seconds) to avoid getting stuck
                    var pollCount = 0
                    while (homeViewModel.uiState.value.isLoading && pollCount < 150) {
                        delay(100)
                        pollCount++
                    }
                    android.util.Log.d("LoadingScreen", "Home data loading finished after ${pollCount * 100}ms")
                } else {
                    delay(500)
                }

                // Step 4: Curating Dashboard (80%)
                currentLoadingText = loadingTexts[3]
                loadingProgress = 0.8f
                
                // Final check for Home Screen data
                if (homeViewModel != null && homeViewModel.uiState.value.contentSections.isEmpty()) {
                    android.util.Log.w("LoadingScreen", "Home data still empty, waiting briefly...")
                    delay(1000)
                }

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
                if (isQuotaError) {
                    // Quota exceeded - show friendly message
                    Text(
                        text = "AI Quota Limit Exceeded",
                        fontSize = if (isMobile) 20.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "Using standard collections instead...",
                        fontSize = if (isMobile) 14.sp else 16.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Button(
                        onClick = {
                            // Disable AI curation and navigate to home
                            kotlinx.coroutines.MainScope().launch {
                                currentProfile?.let { profile ->
                                    profileRepository?.updateProfile(
                                        profile.copy(
                                            aiCuratedEnabled = false,
                                            dashboardCollections = Profile.getDefaultCollections()
                                        )
                                    )
                                }
                                onLoadingComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6), // Purple
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .padding(horizontal = if (isMobile) 16.dp else 0.dp)
                    ) {
                        Text(
                            text = "Continue",
                            fontSize = if (isMobile) 14.sp else 16.sp
                        )
                    }
                } else {
                    // Generic error
                    Text(
                        text = "Loading Failed",
                        fontSize = if (isMobile) 20.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = NetflixRed,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = errorMessage,
                        fontSize = if (isMobile) 14.sp else 16.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .padding(bottom = 24.dp)
                            .padding(horizontal = if (isMobile) 16.dp else 32.dp)
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
                
                // Animated Purple progress bar with shimmer (matches LevelUpCelebrationScreen)
                Box(
                    modifier = Modifier.width(400.dp)
                ) {
                    // Create purple shimmer gradient for progress bar
                    val progressBarShimmerBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6B46C1),  // Darker purple
                            AccentPurple,        // Main purple
                            Color(0xFFC4B5FD),  // Light purple
                            AccentPurple,        // Main purple
                            Color(0xFF6B46C1)   // Darker purple
                        ),
                        start = androidx.compose.ui.geometry.Offset(progressBarShimmerOffset - 300f, 0f),
                        end = androidx.compose.ui.geometry.Offset(progressBarShimmerOffset + 300f, 0f)
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    ) {
                        // Draw background track
                        drawRoundRect(
                            color = NetflixGray,
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                        )
                        
                        // Draw animated progress
                        val barWidth = size.width * animatedProgress
                        drawRoundRect(
                            brush = progressBarShimmerBrush,
                            size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                        )
                    }
                }
                
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
    
    // Initialize Movies ViewModel and trigger library load in background
    moviesViewModel?.let { mvm ->
        mvm.reset() // Force reset to clear old data during loading phase
        mvm.setCurrentProfile(currentProfile)
        mvm.setPlexConnectionWithAuth(authToken, currentProfile.selectedLibraries)
        mvm.loadLibraries() // Trigger background fetch
    }
    
    // Initialize TV Shows ViewModel and trigger library load in background
    tvShowsViewModel?.let { tvm ->
        tvm.reset() // Force reset to clear old data during loading phase
        tvm.setCurrentProfile(currentProfile)
        tvm.setPlexConnectionWithAuth(authToken, currentProfile.selectedLibraries)
        tvm.loadLibraries() // Trigger background fetch
    }
}