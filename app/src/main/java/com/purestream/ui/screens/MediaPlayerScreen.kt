package com.purestream.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.activity.compose.BackHandler
import org.videolan.libvlc.MediaPlayer as VLCMediaPlayer
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.IconButton as TvIconButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import com.purestream.data.model.*
import com.purestream.ui.components.VLCMediaPlayer
import com.purestream.ui.viewmodel.MediaPlayerViewModel
import com.purestream.ui.viewmodel.SeekDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.purestream.ui.theme.getAnimatedButtonBackgroundColor
import com.purestream.ui.theme.animatedProfileBorder
import com.purestream.utils.rememberIsMobile
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.view.View
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun MediaPlayerScreen(
    videoUrl: String,
    title: String,
    currentFilterLevel: ProfanityFilterLevel,
    subtitleAlignment: Float = 0f, // -1f (left) to 1f (right)
    hasAnalysis: Boolean = false,
    onFilterLevelChange: (ProfanityFilterLevel) -> Unit,
    onSubtitleAlignmentChange: (Float) -> Unit,
    onBackClick: () -> Unit,
    movie: Movie? = null,
    tvShow: TvShow? = null,
    episode: Episode? = null,
    isPremium: Boolean = false,
    currentProfile: Profile? = null,
    contentId: String = "unknown",
    mediaPlayerViewModel: MediaPlayerViewModel? = null // Accept shared ViewModel
) {
    val context = LocalContext.current
    val isMobile = rememberIsMobile()
    val coroutineScope = rememberCoroutineScope()
    // Use shared ViewModel if provided, otherwise create a new one
    val viewModel = mediaPlayerViewModel ?: viewModel { MediaPlayerViewModel(context) }
    val playerState by viewModel.uiState.collectAsStateWithLifecycle()

    // Retrieve stored media objects from ViewModel
    val storedMovie = remember { viewModel.getStoredMovie() }
    val storedEpisode = remember { viewModel.getStoredEpisode() }
    val storedTvShow = remember { viewModel.getStoredTvShow() }

    // Use stored objects if available, otherwise fall back to parameters
    val actualMovie = storedMovie ?: movie
    val actualEpisode = storedEpisode ?: episode
    val actualTvShow = storedTvShow ?: tvShow

    val logoUrl = remember(actualMovie, actualTvShow, actualEpisode) {
        // Try to get clearLogo from movie
        actualMovie?.logoUrl?.takeIf { it.isNotEmpty() }
            ?: actualMovie?.images?.find { it.type == "clearLogo" }?.url
            // Try to get clearLogo from tv show (for episode)
            ?: actualTvShow?.logoUrl?.takeIf { it.isNotEmpty() }
            ?: actualTvShow?.images?.find { it.type == "clearLogo" }?.url
            // Try to get clearLogo from episode (rare)
            ?: actualEpisode?.logoUrl?.takeIf { it.isNotEmpty() }
            ?: actualEpisode?.images?.find { it.type == "clearLogo" }?.url
    }

    android.util.Log.d("MediaPlayerScreen", "Retrieved media objects: movie=${actualMovie?.title}, episode=${actualEpisode?.title}, tvShow=${actualTvShow?.title}")

    // Picture-in-Picture state for mobile
    var isInPipMode by remember { mutableStateOf(false) }

    // Function to enter PiP mode (mobile only)
    fun enterPipMode() {
        if (!isMobile) return

        val activity = context as? Activity ?: return

        // Check if PiP is supported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPipFeature = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            if (!hasPipFeature) return

            try {
                // Calculate aspect ratio (16:9 standard for most videos)
                val aspectRatio = Rational(16, 9)

                val pipParams = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build()

                activity.enterPictureInPictureMode(pipParams)
                android.util.Log.d("MediaPlayerScreen", "Entered PiP mode")
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerScreen", "Failed to enter PiP mode", e)
            }
        }
    }

    // Force landscape orientation and hide status bar on mobile
    LaunchedEffect(isMobile) {
        if (isMobile) {
            val activity = context as? Activity
            activity?.let {
                // Force landscape
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

                // Hide status bar and navigation bar (fullscreen/immersive mode)
                WindowCompat.setDecorFitsSystemWindows(it.window, false)
                it.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
        }
    }

    // Restore orientation and system UI when leaving screen (mobile only)
    DisposableEffect(isMobile) {
        onDispose {
            if (isMobile) {
                val activity = context as? Activity
                activity?.let {
                    // Restore orientation
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

                    // Restore system UI
                    WindowCompat.setDecorFitsSystemWindows(it.window, true)
                    it.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }
    
    // Use the passed contentId parameter for timing preferences and analysis
    android.util.Log.d("MediaPlayerScreen", "Using passed contentId: $contentId")
    
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) } // Start visible after loading
    var autoHideTrigger by remember { mutableStateOf(0) } // Trigger to reset auto-hide timer
    var showTemporaryPadlock by remember { mutableStateOf(false) } // Show padlock when it turns green
    var showGearMenu by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }

    // Collect video URL from ViewModel if not provided via parameter
    val viewModelVideoUrl by viewModel.videoUrl.collectAsStateWithLifecycle()
    var currentVideoUrl by remember(videoUrl, viewModelVideoUrl) {
        mutableStateOf(if (videoUrl.isNotEmpty()) videoUrl else viewModelVideoUrl ?: "")
    }

    var isExiting by remember { mutableStateOf(false) }

    // Trigger URL loading if missing
    LaunchedEffect(Unit) {
        if (currentVideoUrl.isEmpty()) {
            android.util.Log.d("MediaPlayerScreen", "Video URL is empty, requesting load from ViewModel")
            viewModel.loadVideoUrl(actualMovie, actualEpisode)
        }
    }

    // Level-up celebration state
    var showCelebration by remember { mutableStateOf(false) }
    var levelUpData by remember { mutableStateOf<MediaPlayerViewModel.LevelUpResult?>(null) }

    // Achievement badge card state
    var pendingAchievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    var currentAchievementIndex by remember { mutableIntStateOf(0) }
    var showBadgeCard by remember { mutableStateOf(false) }

    // Lock orientation to portrait when celebration or badge card shows (mobile only)
    DisposableEffect(showCelebration, showBadgeCard, isMobile) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation

        if ((showCelebration || showBadgeCard) && isMobile && activity != null) {
            // Force portrait before showing celebration or badge card
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onDispose {
            // Restore original orientation when celebration and badge cards are dismissed
            if ((showCelebration || showBadgeCard) && isMobile && activity != null && originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }

    // Focus management for navigation
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    
    // Player reference for LibVLC MediaPlayer
    var currentPlayer: VLCMediaPlayer? by remember { mutableStateOf(null) }

    // Detect PiP mode changes (mobile only)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && isMobile) {
                // User is leaving the app - enter PiP if playing
                android.util.Log.d("MediaPlayerScreen", "ON_PAUSE detected - isPlaying: $isPlaying, isExiting: $isExiting")
                if (isPlaying && !isExiting) {
                    android.util.Log.d("MediaPlayerScreen", "Entering PiP mode")
                    enterPipMode()
                } else {
                    android.util.Log.d("MediaPlayerScreen", "Skipping PiP - user is exiting or not playing")
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Monitor PiP mode state changes via configuration
    if (isMobile && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val activity = context as? Activity

        LaunchedEffect(configuration) {
            activity?.let {
                val inPip = it.isInPictureInPictureMode
                if (isInPipMode != inPip) {
                    isInPipMode = inPip
                    android.util.Log.d("MediaPlayerScreen", "PiP mode changed: $isInPipMode")

                    // When exiting PiP, show controls again
                    if (!isInPipMode) {
                        showControls = true
                    }
                }
            }
        }
    }

    // Handle back button presses
    BackHandler {
        when {
            showGearMenu -> {
                // If settings menu is open, close it first
                showGearMenu = false
            }
            showControls -> {
                // If UI is visible, hide it
                showControls = false
            }
            else -> {
                // If UI is hidden, immediately exit and ensure player is stopped
                android.util.Log.d("MediaPlayerScreen", "Back pressed with UI hidden - forcing exit")
            
            // Set exit flag first to prevent VLCMediaPlayer from rendering
            isExiting = true
            
            // Clear video URL to ensure no restart
            currentVideoUrl = ""

            // Stop progress tracking
            viewModel.stopProgressTracking(actualMovie, actualTvShow, actualEpisode)
            android.util.Log.d("MediaPlayerScreen", "Stopped progress tracking on back press")

            // Release wake lock when exiting
            viewModel.releaseWakeLock()

            // Force stop the current player with comprehensive error handling
            currentPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                        android.util.Log.d("MediaPlayerScreen", "Player stopped")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MediaPlayerScreen", "Error stopping player: ${e.message}")
                }
                
                try {
                    player.detachViews()
                    android.util.Log.d("MediaPlayerScreen", "Player views detached")
                } catch (e: Exception) {
                    android.util.Log.w("MediaPlayerScreen", "Error detaching views: ${e.message}")
                }
                
                try {
                    player.release()
                    android.util.Log.d("MediaPlayerScreen", "Player released")
                } catch (e: Exception) {
                    android.util.Log.w("MediaPlayerScreen", "Error releasing player: ${e.message}")
                }
            }
            currentPlayer = null

                // Save session progress before exiting
                coroutineScope.launch {
                    val levelUpResult = viewModel.saveSessionProgress()
                    if (levelUpResult.leveledUp || levelUpResult.unlockedAchievements.isNotEmpty()) {
                        levelUpData = levelUpResult
                        pendingAchievements = levelUpResult.unlockedAchievements
                        currentAchievementIndex = 0

                        if (levelUpResult.leveledUp) {
                            android.util.Log.d("MediaPlayerScreen",
                                "Level up! ${levelUpResult.oldLevel} → ${levelUpResult.newLevel}")
                            showCelebration = true
                        } else {
                            // No level up, but achievements unlocked - show first badge card
                            showBadgeCard = true
                        }
                    } else {
                        // Exit to previous screen
                        android.util.Log.d("MediaPlayerScreen", "Calling onBackClick to exit")
                        onBackClick()
                    }
                }
            }
        }
    }
    
    // Cleanup when screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            android.util.Log.d("MediaPlayerScreen", "Screen disposing - comprehensive cleanup")
            isExiting = true
            currentVideoUrl = "" // Ensure video URL is cleared

            // Stop progress tracking
            viewModel.stopProgressTracking(actualMovie, actualTvShow, actualEpisode)
            android.util.Log.d("MediaPlayerScreen", "Stopped progress tracking on dispose")

            // Release wake lock when exiting
            viewModel.releaseWakeLock()

            currentPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                        android.util.Log.d("MediaPlayerScreen", "Player stopped on dispose")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MediaPlayerScreen", "Error stopping player on dispose: ${e.message}")
                }
                
                try {
                    player.detachViews()
                    android.util.Log.d("MediaPlayerScreen", "Player views detached on dispose")
                } catch (e: Exception) {
                    android.util.Log.w("MediaPlayerScreen", "Error detaching views on dispose: ${e.message}")
                }
                
                try {
                    player.release()
                    android.util.Log.d("MediaPlayerScreen", "Player released on dispose")
                } catch (e: Exception) {
                    android.util.Log.w("MediaPlayerScreen", "Error releasing player on dispose: ${e.message}")
                }
            }
            currentPlayer = null
            android.util.Log.d("MediaPlayerScreen", "Screen disposal complete")
        }
    }
    
    // Load subtitle timing preference when screen loads
    LaunchedEffect(contentId) {
        android.util.Log.d("MediaPlayerScreen", "Loading subtitle timing preference for content: $contentId")
        viewModel.loadSubtitleTimingPreference(contentId)
    }
    
    // Monitor analysis status for current content and filter level
    LaunchedEffect(contentId, currentFilterLevel) {
        android.util.Log.d("MediaPlayerScreen", "Starting analysis monitoring for contentId: $contentId")
        // Initial check
        viewModel.checkAnalysisStatus(contentId, currentFilterLevel)
        // Start continuous monitoring
        viewModel.monitorAnalysisStatus(contentId, currentFilterLevel)
    }
    
    // Load filtered subtitles when screen loads, filter level changes, or analysis becomes available
    LaunchedEffect(contentId, currentFilterLevel, playerState.hasAnalysis) {
        android.util.Log.d("MediaPlayerScreen", "Loading filtered subtitles with contentId: $contentId, filter level: $currentFilterLevel, hasAnalysis: ${playerState.hasAnalysis}")
        
        // Skip loading for demo content as it's handled by MainActivity/ViewModel directly
        if (contentId.isNotEmpty() && contentId != "unknown" && !contentId.startsWith("demo_")) {
            // Use contentId-based loading for analyzed subtitles
            viewModel.loadFilteredSubtitlesByContentId(contentId, currentFilterLevel)
        } else if (contentId.startsWith("demo_")) {
            android.util.Log.d("MediaPlayerScreen", "Skipping subtitle load for demo content (already set)")
        } else {
            android.util.Log.w("MediaPlayerScreen", "Invalid contentId provided: $contentId")
        }
    }

    // Hide subtitles by default - user can show them if needed
    LaunchedEffect(Unit) {
        android.util.Log.d("MediaPlayerScreen", "Hiding subtitles by default")
        viewModel.setSubtitlesEnabled(false)
    }

    // Resume from saved progress if exists
    LaunchedEffect(currentPlayer, currentProfile) {
        currentPlayer?.let { player ->
            currentProfile?.let { profile ->
                val ratingKey = movie?.ratingKey ?: episode?.ratingKey
                if (ratingKey != null) {
                    // Wait a bit for player to be fully initialized
                    delay(500)
                    val savedPosition = viewModel.getSavedProgress(profile.id, ratingKey)
                    if (savedPosition != null && savedPosition > 5000) { // Only resume if >5 seconds in
                        android.util.Log.d("MediaPlayerScreen", "Resuming from saved position: ${savedPosition}ms")
                        player.time = savedPosition
                    }
                }
            }
        }
    }

    // Focus the play button when UI becomes visible
    // Focus the play button when UI becomes visible
    LaunchedEffect(showControls) {
        if (showControls) {
            android.util.Log.d("MediaPlayerScreen", "UI shown - requesting focus on play button")
            delay(300) // Give more time for composition
            try {
                focusRequester.requestFocus()
                android.util.Log.d("MediaPlayerScreen", "Focus requested successfully")
            } catch (e: IllegalStateException) {
                android.util.Log.w("MediaPlayerScreen", "Focus request failed, will retry: ${e.message}")
                // Retry after a longer delay
                delay(500)
                try {
                    focusRequester.requestFocus()
                } catch (e2: Exception) {
                    android.util.Log.e("MediaPlayerScreen", "Focus request failed again: ${e2.message}")
                }
            }
        }
    }
    
    // Auto-hide controls after 5 seconds of no interaction (not in PiP mode)
    LaunchedEffect(showControls, isInPipMode, autoHideTrigger) {
        if (showControls && !isInPipMode) {
            delay(5000) // Hide after 5 seconds
            showControls = false
        }
    }

    // Show padlock for 3 seconds when it turns green and UI is hidden
    val isContentProtected = (playerState.hasAnalysis && currentFilterLevel != ProfanityFilterLevel.NONE) || contentId.startsWith("demo_")
    LaunchedEffect(isContentProtected) {
        if (isContentProtected && !showControls) {
            showTemporaryPadlock = true
            delay(3000)
            showTemporaryPadlock = false
        }
    }
    
    // Monitor playback position for profanity filtering (handled by VLCMediaPlayer now)
    LaunchedEffect(currentPlayer, playerState, isExiting) {
        while (!isExiting && isActive) {
            currentPlayer?.let { player ->
                try {
                    val currentPosition = player.time
                    duration = player.length
                    
                    // Update player position in ViewModel
                    viewModel.updatePlayerPosition(currentPosition)
                    
                    // Audio muting is handled by VLCMediaPlayer component
                    // No need to manually set volume here
                } catch (e: IllegalStateException) {
                    // Player has been released, stop monitoring
                    android.util.Log.w("MediaPlayerScreen", "Player released, stopping position monitoring: ${e.message}")
                    return@LaunchedEffect
                } catch (e: Exception) {
                    android.util.Log.w("MediaPlayerScreen", "Error getting player position: ${e.message}")
                }
            }
            
            kotlinx.coroutines.delay(100) // Check every 100ms
        }
    }
    
    // Keep screen on during playback to prevent TV power-off
    val view = LocalView.current
    LaunchedEffect(Unit) {
        view.keepScreenOn = true
        android.util.Log.d("MediaPlayerScreen", "Keep screen on flag set - preventing TV power-off")
    }
    
    // Clear flag when exiting
    DisposableEffect(Unit) {
        onDispose {
            view.keepScreenOn = false
            android.util.Log.d("MediaPlayerScreen", "Keep screen on flag cleared")
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .onKeyEvent { keyEvent ->
                android.util.Log.d("MediaPlayerScreen", "Key event: ${keyEvent.key}, type: ${keyEvent.type}, showControls: $showControls")
                when {
                    keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyDown && !showControls -> {
                        // Select button - toggle play/pause only when UI is hidden
                        android.util.Log.d("MediaPlayerScreen", "Select button pressed (UI hidden) - toggling play/pause")
                        currentPlayer?.let { player ->
                            if (player.isPlaying) {
                                player.pause()
                                viewModel.onPlaybackStateChanged(false)
                            } else {
                                player.play()
                                viewModel.onPlaybackStateChanged(true)
                            }
                        }
                        true
                    }
                    keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown && !showControls -> {
                        // Down button - show UI and focus play/pause button
                        android.util.Log.d("MediaPlayerScreen", "Down button pressed - showing UI")
                        showControls = true
                        // Focus will be handled by LaunchedEffect
                        true
                    }
                    keyEvent.key == Key.DirectionLeft && !showControls -> {
                        // Left button - continuous seeking backward
                        when (keyEvent.type) {
                            KeyEventType.KeyDown -> {
                                android.util.Log.d("MediaPlayerScreen", "Left button DOWN - starting continuous rewind")
                                viewModel.startContinuousSeeking(SeekDirection.BACKWARD)
                                true
                            }
                            KeyEventType.KeyUp -> {
                                android.util.Log.d("MediaPlayerScreen", "Left button UP - stopping continuous rewind")
                                viewModel.stopContinuousSeeking()
                                true
                            }
                            else -> false
                        }
                    }
                    keyEvent.key == Key.DirectionRight && !showControls -> {
                        // Right button - continuous seeking forward
                        when (keyEvent.type) {
                            KeyEventType.KeyDown -> {
                                android.util.Log.d("MediaPlayerScreen", "Right button DOWN - starting continuous forward")
                                viewModel.startContinuousSeeking(SeekDirection.FORWARD)
                                true
                            }
                            KeyEventType.KeyUp -> {
                                android.util.Log.d("MediaPlayerScreen", "Right button UP - stopping continuous forward")
                                viewModel.stopContinuousSeeking()
                                true
                            }
                            else -> false
                        }
                    }
                    else -> {
                        android.util.Log.d("MediaPlayerScreen", "Unhandled key event: ${keyEvent.key}")
                        false
                    }
                }
            }
            .focusable()
    ) {
        // LibVLC Video Player with comprehensive profanity filtering
        if (!isExiting && currentVideoUrl.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                VLCMediaPlayer(
                    videoUrl = currentVideoUrl,
                    filteredSubtitleResult = playerState.filteredSubtitleResult,
                    currentFilterLevel = currentFilterLevel,
                    showSubtitles = playerState.subtitlesEnabled,
                    subtitleTimingOffsetMs = playerState.subtitleTimingOffsetMs,
                    onPlayerReady = { player ->
                        currentPlayer = player
                        // Set player reference in ViewModel for seek functionality
                        viewModel.setMediaPlayer(player)
                        android.util.Log.d("MediaPlayerScreen", "LibVLC MediaPlayer ready and playing")

                        // Seek to start position if resuming playback
                        if (playerState.startPosition > 0L) {
                            player.time = playerState.startPosition
                            android.util.Log.d("MediaPlayerScreen", "⏩ Resumed playback at ${playerState.startPosition}ms")
                            // Clear the start position after seeking
                            viewModel.setStartPosition(0L)
                        }

                        // Acquire wake lock when player starts
                        viewModel.acquireWakeLock()

                        // Start progress tracking with stored media objects
                        currentProfile?.let { profile ->
                            val videoDuration = player.length
                            if (videoDuration > 0) {
                                viewModel.startProgressTracking(
                                    movie = actualMovie,
                                    tvShow = actualTvShow,
                                    episode = actualEpisode,
                                    profileId = profile.id,
                                    duration = videoDuration
                                )
                                android.util.Log.d("MediaPlayerScreen", "Started progress tracking for ${actualMovie?.title ?: actualEpisode?.title}")
                            }
                        }
                    },
                    onError = { error ->
                        android.util.Log.e("MediaPlayerScreen", "LibVLC error: $error")
                        // Error is logged - retry logic could be implemented for specific error types
                    },
                onPositionChange = { position ->
                    // Position updates are handled by the VLCMediaPlayer component
                },
                onDurationChange = { dur ->
                    duration = dur
                },
                onPlayingStateChange = { playing ->
                    isPlaying = playing
                    // Manage wake lock based on playback state
                    viewModel.onPlaybackStateChanged(playing)

                    // Handle progress tracking based on playback state
                    if (playing) {
                        viewModel.resumeProgressTracking(actualMovie, actualTvShow, actualEpisode)
                        android.util.Log.d("MediaPlayerScreen", "Resumed progress tracking")
                    } else {
                        viewModel.pauseProgressTracking(actualMovie, actualTvShow, actualEpisode)
                        android.util.Log.d("MediaPlayerScreen", "Paused progress tracking")
                    }
                },
                onEnded = {
                    android.util.Log.d("MediaPlayerScreen", "Video ended - marking complete and returning")

                    coroutineScope.launch {
                        // Mark as 100% complete
                        viewModel.completePlayback(
                            movie = actualMovie,
                            tvShow = actualTvShow,
                            episode = actualEpisode
                        )

                        // Stop progress tracking
                        viewModel.stopProgressTracking(actualMovie, actualTvShow, actualEpisode)

                        // Save session progress before exiting
                        val levelUpResult = viewModel.saveSessionProgress()
                        if (levelUpResult.leveledUp || levelUpResult.unlockedAchievements.isNotEmpty()) {
                            levelUpData = levelUpResult
                            pendingAchievements = levelUpResult.unlockedAchievements
                            currentAchievementIndex = 0

                            if (levelUpResult.leveledUp) {
                                android.util.Log.d("MediaPlayerScreen",
                                    "Level up! ${levelUpResult.oldLevel} → ${levelUpResult.newLevel}")
                                showCelebration = true
                            } else {
                                // No level up, but achievements unlocked - show first badge card
                                showBadgeCard = true
                            }
                        } else {
                            // Navigate back immediately
                            onBackClick()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Mobile double-tap seek zones are now handled by the unified MobileTouchOverlay below
        }
        }
        
        // Unified Mobile Touch Overlay - Handles both single tap (toggle controls) and double tap (seek)
        if (isMobile && !isInPipMode) {
            val modifier = if (showControls) {
                 Modifier
                     .fillMaxWidth()
                     .fillMaxHeight(0.7f) // Leave bottom 30% for controls when visible
                     .align(Alignment.TopCenter)
            } else {
                 Modifier.fillMaxSize()
            }

            Row(modifier = modifier) {
                // Left 30% - Double tap back, Single tap toggle
                Box(
                    modifier = Modifier
                        .weight(0.3f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    viewModel.seekBackward(10)
                                    android.util.Log.d("MediaPlayerScreen", "Double-tap left: seek backward 10s")
                                },
                                onTap = {
                                    showControls = !showControls
                                }
                            )
                        }
                )
                
                // Center 40% - Single tap toggle only
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    showControls = !showControls
                                }
                            )
                        }
                )
                
                // Right 30% - Double tap forward, Single tap toggle
                Box(
                    modifier = Modifier
                        .weight(0.3f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    viewModel.seekForward(10)
                                    android.util.Log.d("MediaPlayerScreen", "Double-tap right: seek forward 10s")
                                },
                                onTap = {
                                    showControls = !showControls
                                }
                            )
                        }
                )
            }
        } else if (!isMobile) {
            // TV: Keep existing fullscreen toggle behavior for D-pad compatibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable {
                        android.util.Log.d("MediaPlayerScreen", "TV fullscreen tap - toggling controls")
                        showControls = !showControls
                    }
            )
        }

        // Subtitle handling is now done internally by VLCMediaPlayer component

        // Debug overlay removed for cleaner UI

        // Audio mute indicator is handled by VLCMediaPlayer component

        // Seek Timeline Overlay (shows during continuous seeking)
        if (playerState.showSeekTimeline) {
            SeekTimelineOverlay(
                currentPosition = playerState.seekPreviewPosition,  // Use preview position for smooth UI
                duration = duration,
                seekDirection = playerState.seekState.seekDirection,
                seekIncrement = playerState.seekState.currentSeekIncrement,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Player Controls (hidden when in PiP mode)
        if (showControls && !isInPipMode) {
            PlayerControls(
                isPlaying = isPlaying,
                currentPosition = playerState.currentPosition,
                duration = duration,
                title = title,
                currentFilterLevel = currentFilterLevel,
                subtitleAlignment = subtitleAlignment,
                subtitlesEnabled = playerState.subtitlesEnabled,
                hasAnalysis = playerState.hasAnalysis, // Use live analysis state from ViewModel
                isDemoContent = contentId.startsWith("demo_"), // Check if content is demo
                focusRequester = focusRequester,
                onPlayPause = {
                    currentPlayer?.let { player ->
                        if (player.isPlaying) {
                            player.pause()
                            viewModel.onPlaybackStateChanged(false)
                        } else {
                            player.play()
                            viewModel.onPlaybackStateChanged(true)
                        }
                    }
                },
                onSeek = { position ->
                    currentPlayer?.time = position
                },
                onGearClick = {
                    showGearMenu = true
                },
                onSubtitleAlignmentChange = onSubtitleAlignmentChange,
                onSubtitleToggle = {
                    viewModel.toggleSubtitles()
                },
                onInteraction = { autoHideTrigger++ },
                logoUrl = logoUrl,
                onBackClick = {
                    // Set exit flag to prevent PiP mode when user explicitly exits
                    isExiting = true
                    // Stop playback to ensure clean exit
                    currentPlayer?.let { player ->
                        player.pause()
                        viewModel.onPlaybackStateChanged(false)
                    }

                    // Save session progress before exiting
                    coroutineScope.launch {
                        val levelUpResult = viewModel.saveSessionProgress()
                        if (levelUpResult.leveledUp || levelUpResult.unlockedAchievements.isNotEmpty()) {
                            levelUpData = levelUpResult
                            pendingAchievements = levelUpResult.unlockedAchievements
                            currentAchievementIndex = 0

                            if (levelUpResult.leveledUp) {
                                android.util.Log.d("MediaPlayerScreen",
                                    "Level up! ${levelUpResult.oldLevel} → ${levelUpResult.newLevel}")
                                showCelebration = true
                            } else {
                                // No level up, but achievements unlocked - show first badge card
                                showBadgeCard = true
                            }
                        } else {
                            onBackClick()
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        
        // Gear Menu Dialog (hidden when in PiP mode)
        if (showGearMenu && !isInPipMode) {
            GearMenuDialog(
                currentFilterLevel = currentFilterLevel,
                subtitlesEnabled = playerState.subtitlesEnabled,
                hasAnalysis = playerState.hasAnalysis,
                onFilterLevelChange = onFilterLevelChange,
                onSubtitleToggle = {
                    viewModel.toggleSubtitles()
                },
                onTimingAdjust = { adjustment ->
                    viewModel.adjustSubtitleTiming(contentId, adjustment)
                    android.util.Log.d("MediaPlayerScreen", "Adjusting subtitle timing by: ${adjustment}ms for content: $contentId")
                },
                onDismiss = { showGearMenu = false },
                currentOffset = playerState.subtitleTimingOffsetMs, // Already in milliseconds
                isPremium = isPremium,
                currentProfile = currentProfile
            )
        }

        // Temporary Security Padlock Overlay (top right)
        AnimatedVisibility(
            visible = showTemporaryPadlock && !showControls && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp)
        ) {
            SecurityPadlock(isContentProtected = true)
        }
    }

    // Show level-up celebration overlay when user levels up
    if (showCelebration && levelUpData != null) {
        LevelUpCelebrationScreen(
            oldLevel = levelUpData!!.oldLevel,
            newLevel = levelUpData!!.newLevel,
            totalFilteredWords = levelUpData!!.wordsFiltered,
            onDismiss = {
                showCelebration = false
                // After level-up screen, show badge cards if any
                if (pendingAchievements.isNotEmpty()) {
                    currentAchievementIndex = 0
                    showBadgeCard = true
                } else {
                    onBackClick()
                }
            }
        )
    }

    // Show badge card for newly unlocked achievements
    if (showBadgeCard && pendingAchievements.isNotEmpty() && currentAchievementIndex < pendingAchievements.size) {
        val achievement = pendingAchievements[currentAchievementIndex]
        AchievementUnlockCelebrationScreen(
            achievement = achievement,
            onDismiss = {
                // Move to next achievement or exit
                currentAchievementIndex++
                if (currentAchievementIndex >= pendingAchievements.size) {
                    // All achievements shown, exit
                    showBadgeCard = false
                    onBackClick()
                }
                // Otherwise the next achievement will show automatically due to index change
            }
        )
    }
}

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    title: String,
    currentFilterLevel: ProfanityFilterLevel,
    subtitleAlignment: Float,
    subtitlesEnabled: Boolean,
    hasAnalysis: Boolean,
    isDemoContent: Boolean = false,
    focusRequester: FocusRequester,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onGearClick: () -> Unit,
    onSubtitleAlignmentChange: (Float) -> Unit,
    onSubtitleToggle: () -> Unit,
    onBackClick: () -> Unit,
    onInteraction: () -> Unit = {},
    logoUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val isMobile = rememberIsMobile()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        // Top Bar - Title and Padlock
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .height(45.dp)
                        .widthIn(max = 180.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Analysis Status Padlock (green if analysis complete and filter enabled, red otherwise)
            // Force green for demo content
            val isContentProtected = (hasAnalysis && currentFilterLevel != ProfanityFilterLevel.NONE) || isDemoContent
            SecurityPadlock(isContentProtected = isContentProtected)
        }
        
        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Progress Bar
            if (duration > 0) {
                val progress = currentPosition.toFloat() / duration.toFloat()
                
                Column {
                    Slider(
                        value = progress,
                        onValueChange = { newProgress ->
                            onInteraction()
                            onSeek((newProgress.toDouble() * duration.toDouble()).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF6366F1),
                            inactiveTrackColor = Color.Gray
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Bottom Row - Centered and Grouped Controls
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Back Button - Left Side (Mobile Only)
                if (isMobile) {
                    var backButtonHovered by remember { mutableStateOf(false) }
                    val backButtonInteractionSource = remember { MutableInteractionSource() }

                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .animatedProfileBorder(
                                borderWidth = 2.dp,
                                interactionSource = backButtonInteractionSource
                            )
                            .hoverable(backButtonInteractionSource)
                            .size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 2. Play/Pause Button - Truly Centered
                var playButtonHovered by remember { mutableStateOf(false) }
                val playButtonInteractionSource = remember { MutableInteractionSource() }
                
                if (isMobile) {
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .animatedProfileBorder(
                                borderWidth = 3.dp,
                                interactionSource = playButtonInteractionSource
                            )
                            .hoverable(playButtonInteractionSource)
                            .size(48.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .focusRequester(focusRequester)
                            .size(34.dp)
                            .onFocusChanged { 
                                playButtonHovered = it.isFocused
                                if (it.isFocused) onInteraction()
                            }
                            .focusable(interactionSource = playButtonInteractionSource)
                            .clickable(
                                interactionSource = playButtonInteractionSource,
                                indication = null,
                                onClick = onPlayPause
                            )
                            .background(
                                color = if (playButtonHovered) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // 3. Right Group (Subtitle Toggle and Settings)
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Subtitle Toggle Button
                    var subtitleButtonHovered by remember { mutableStateOf(false) }
                    val subtitleButtonInteractionSource = remember { MutableInteractionSource() }
                    val isSubtitleActive = subtitlesEnabled && hasAnalysis

                    if (isMobile) {
                        IconButton(
                            onClick = onSubtitleToggle,
                            enabled = hasAnalysis,
                            modifier = Modifier
                                .animatedProfileBorder(
                                    borderWidth = 2.dp,
                                    interactionSource = subtitleButtonInteractionSource
                                )
                                .hoverable(subtitleButtonInteractionSource)
                                .size(48.dp)
                        ) {
                            Icon(
                                if (subtitlesEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                contentDescription = "Toggle Subtitles",
                                tint = if (!hasAnalysis) Color.White.copy(alpha = 0.5f) else if (isSubtitleActive) Color(0xFF10B981) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .onFocusChanged { 
                                    subtitleButtonHovered = it.isFocused
                                    if (it.isFocused) onInteraction()
                                }
                                .focusable(interactionSource = subtitleButtonInteractionSource)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyUp && 
                                        (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                                        onSubtitleToggle()
                                        true
                                    } else false
                                }
                                .clickable(
                                    interactionSource = subtitleButtonInteractionSource,
                                    indication = null,
                                    enabled = hasAnalysis,
                                    onClick = onSubtitleToggle
                                )
                                .background(
                                    color = if (subtitleButtonHovered) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.1f),
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                if (subtitlesEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                contentDescription = "Toggle Subtitles",
                                tint = if (!hasAnalysis) Color.White.copy(alpha = 0.5f) else if (isSubtitleActive) Color(0xFF10B981) else Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Gear (Settings) Button
                    var gearButtonHovered by remember { mutableStateOf(false) }
                    val gearButtonInteractionSource = remember { MutableInteractionSource() }

                    if (isMobile) {
                        IconButton(
                            onClick = onGearClick,
                            modifier = Modifier
                                .animatedProfileBorder(
                                    borderWidth = 2.dp,
                                    interactionSource = gearButtonInteractionSource
                                )
                                .hoverable(gearButtonInteractionSource)
                                .size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .onFocusChanged { gearButtonHovered = it.isFocused }
                                .focusable(interactionSource = gearButtonInteractionSource)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyUp && 
                                        (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                                        onGearClick()
                                        true
                                    } else false
                                }
                                .clickable(
                                    interactionSource = gearButtonInteractionSource,
                                    indication = null,
                                    onClick = onGearClick
                                )
                                .background(
                                    color = if (gearButtonHovered) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.1f),
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GearMenuDialog(
    currentFilterLevel: ProfanityFilterLevel,
    subtitlesEnabled: Boolean,
    hasAnalysis: Boolean = false,
    onFilterLevelChange: (ProfanityFilterLevel) -> Unit,
    onSubtitleToggle: () -> Unit,
    onTimingAdjust: (Long) -> Unit,
    onDismiss: () -> Unit,
    currentOffset: Long = 0L,
    isPremium: Boolean = false,
    currentProfile: Profile? = null
) {
    val isMobile = rememberIsMobile()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)),
        text = {
            val columnModifier = if (isMobile) {
                Modifier // Start the chain with the Modifier object
                    .height(220.dp)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier
                    .height(300.dp)
                    .verticalScroll(rememberScrollState())
            }

            Column(
                modifier = columnModifier,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Profanity Filter Level Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Profanity Filter Level", // Provide the text to display
                        fontSize = if (isMobile) 12.sp else 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    // ... other composables for this section can go here
                }

                // Subtitle Timing Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Subtitle Timing",
                        fontSize = if (isMobile) 12.sp else 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    
                    Text(
                        text = "Current offset: ${currentOffset}ms",
                        fontSize = if (isMobile) 10.sp else 12.sp,
                        color = Color.Gray
                    )
                    
                    // Helper labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Move Earlier (-)",
                            color = Color(0xFFEF4444),
                            fontSize = if (isMobile) 10.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Move Later (+)",
                            color = Color(0xFF10B981),
                            fontSize = if (isMobile) 10.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    val timingOptions = listOf(
                        "-5s" to -5000L,
                        "-1s" to -1000L,
                        "-0.5s" to -500L,
                        "+0.5s" to 500L,
                        "+1s" to 1000L,
                        "+5s" to 5000L
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 8.dp, Alignment.CenterHorizontally)
                    ) {
                        for ((label, adjustment) in timingOptions) {
                            val timingButtonInteractionSource = remember { MutableInteractionSource() }
                            val isEnabled = hasAnalysis
                            
                            if (isMobile) {
                                Button(
                                    onClick = { onTimingAdjust(adjustment) },
                                    enabled = isEnabled,
                                    modifier = Modifier
                                        .animatedProfileBorder(
                                            borderWidth = 2.dp,
                                            interactionSource = timingButtonInteractionSource
                                        )
                                        .hoverable(timingButtonInteractionSource)
                                        .height(28.dp)
                                        .width(38.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (adjustment < 0) Color(0xFFEF4444) else Color(0xFF10B981),
                                        contentColor = Color.White,
                                        disabledContainerColor = (if (adjustment < 0) Color(0xFFEF4444) else Color(0xFF10B981)).copy(alpha = 0.2f),
                                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                var timingButtonHovered by remember { mutableStateOf(false) }
                                TvButton(
                                    onClick = { onTimingAdjust(adjustment) },
                                    enabled = isEnabled,
                                    modifier = Modifier
                                        .animatedProfileBorder(
                                            borderWidth = 2.dp,
                                            interactionSource = timingButtonInteractionSource
                                        )
                                        .hoverable(timingButtonInteractionSource)
                                        .focusable(enabled = isEnabled, interactionSource = timingButtonInteractionSource)
                                        .onFocusChanged { timingButtonHovered = it.isFocused }
                                        .height(36.dp)
                                        .width(58.dp),
                                    colors = TvButtonDefaults.colors(
                                        containerColor = (if (adjustment < 0) Color(0xFFEF4444) else Color(0xFF10B981)),
                                        focusedContainerColor = if (adjustment < 0) Color(0xFFDC2626) else Color(0xFF059669),
                                        contentColor = Color.White,
                                        disabledContainerColor = (if (adjustment < 0) Color(0xFFEF4444) else Color(0xFF10B981)).copy(alpha = 0.1f),
                                        disabledContentColor = Color.White.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                
                // Filter Settings Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Profanity Filter Level",
                        fontSize = if (isMobile) 12.sp else 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    
                    ProfanityFilterLevel.values().forEach { level ->
                        val isChildProfile = currentProfile?.profileType == ProfileType.CHILD
                        val isFilterEditable = when {
                            isChildProfile -> level == ProfanityFilterLevel.STRICT  // Only STRICT for child
                            !isPremium -> level == ProfanityFilterLevel.MILD       // Only MILD for free users
                            else -> true  // All levels for premium adult profiles
                        }
                        val isLocked = !isFilterEditable
                        
                        var filterButtonHovered by remember { mutableStateOf(false) }
                        val filterButtonInteractionSource = remember { MutableInteractionSource() }
                        val isHovered by filterButtonInteractionSource.collectIsHoveredAsState()
                        val isFocused by filterButtonInteractionSource.collectIsFocusedAsState()
                        val backgroundColor = when {
                            isFocused -> Color(0xFF8B5CF6) // Purple fill when focused
                            isHovered -> Color(0xFF8B5CF6).copy(alpha = 0.7f) // Lighter purple when hovered
                            else -> Color.Transparent
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = backgroundColor,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .clickable(enabled = isFilterEditable) {
                                    if (isFilterEditable) {
                                        onFilterLevelChange(level)
                                    }
                                }
                                .hoverable(filterButtonInteractionSource)
                                .focusable(enabled = isFilterEditable, interactionSource = filterButtonInteractionSource)
                                .onFocusChanged { filterButtonHovered = it.isFocused }
                                .padding(if (isMobile) 4.dp else 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentFilterLevel == level,
                                onClick = { 
                                    if (isFilterEditable) {
                                        onFilterLevelChange(level)
                                    }
                                },
                                enabled = isFilterEditable,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF8B5CF6),
                                    unselectedColor = Color.Gray
                                )
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = getFilterLevelText(level),
                                fontSize = if (isMobile) 10.sp else 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isFilterEditable) Color.White else Color.Gray
                            )
                            
                            // Show lock icon for locked options
                            if (isLocked && !isPremium) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Premium Feature",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(if (isMobile) 10.dp else 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val doneButtonInteractionSource = remember { MutableInteractionSource() }
            val isHovered by doneButtonInteractionSource.collectIsHoveredAsState()
            val isFocused by doneButtonInteractionSource.collectIsFocusedAsState()
            val doneButtonBackgroundColor = when {
                isFocused -> Color(0xFF8B5CF6) // Purple fill when focused
                isHovered -> Color(0xFF8B5CF6).copy(alpha = 0.7f) // Lighter purple when hovered
                else -> Color.Transparent
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .hoverable(doneButtonInteractionSource)
                    .focusable(interactionSource = doneButtonInteractionSource),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = doneButtonBackgroundColor,
                    contentColor = Color.White
                )
            ) {
                Text("Done")
            }
        },
        containerColor = Color(0xFF191b2c).copy(alpha = 0.95f), // Glassy dark
        textContentColor = Color.White
    )
}

private fun getFilterLevelText(level: ProfanityFilterLevel): String {
    return when (level) {
        ProfanityFilterLevel.NONE -> "None"
        ProfanityFilterLevel.MILD -> "Mild"
        ProfanityFilterLevel.MODERATE -> "Moderate"
        ProfanityFilterLevel.STRICT -> "Strict"
    }
}

private fun getFilterLevelDescription(level: ProfanityFilterLevel): String {
    return when (level) {
        ProfanityFilterLevel.NONE -> "No filtering applied - all content unfiltered"
        ProfanityFilterLevel.MILD -> "Filters worst of the worst profanity (fuck, bitch, ass)"
        ProfanityFilterLevel.MODERATE -> "Filters strongest + some lesser profanity (shit, cunt, etc.)"
        ProfanityFilterLevel.STRICT -> "Filters everything including mild religious terms"
    }
}

private fun getFilterLevelColor(level: ProfanityFilterLevel): Color {
    return when (level) {
        ProfanityFilterLevel.NONE -> Color(0xFF10B981)
        ProfanityFilterLevel.MILD -> Color(0xFFF59E0B)
        ProfanityFilterLevel.MODERATE -> Color(0xFFEF4444)
        ProfanityFilterLevel.STRICT -> Color(0xFF7C2D12)
    }
}

@Composable
fun SeekTimelineOverlay(
    currentPosition: Long,
    duration: Long,
    seekDirection: SeekDirection?,
    seekIncrement: Long,
    modifier: Modifier = Modifier
) {
    // Simple timeline overlay matching the normal player controls style
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        // Progress Bar and timestamps only (matching PlayerControls style)
        if (duration > 0) {
            val progress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

            Column {
                Slider(
                    value = progress,
                    onValueChange = { }, // Read-only during seeking
                    enabled = false, // Disable interaction during seeking
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF6366F1),
                        inactiveTrackColor = Color.Gray,
                        disabledThumbColor = Color.White,
                        disabledActiveTrackColor = Color(0xFF6366F1),
                        disabledInactiveTrackColor = Color.Gray
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(duration),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    val hours = milliseconds / (1000 * 60 * 60)
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun SecurityPadlock(
    isContentProtected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = if (isContentProtected) Color(0xFF10B981).copy(alpha = 0.8f) else Color(0xFFEF4444).copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Icon(
            if (isContentProtected) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = if (isContentProtected) "Content Protected" else "Content Not Protected",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}