package com.purestream.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.purestream.data.model.*
import com.purestream.profanity.FilteredSubtitleManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun VLCMediaPlayer(
    videoUrl: String,
    filteredSubtitleResult: FilteredSubtitleResult? = null,
    currentFilterLevel: ProfanityFilterLevel = ProfanityFilterLevel.MILD,
    showSubtitles: Boolean = false,
    subtitleTimingOffsetMs: Long = 0L,
    onPlayerReady: (MediaPlayer) -> Unit = {},
    onError: (String) -> Unit = {},
    onPositionChange: (Long) -> Unit = {},
    onDurationChange: (Long) -> Unit = {},
    onPlayingStateChange: (Boolean) -> Unit = {},
    onEnded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isAudioMuted by remember { mutableStateOf(false) }
    var currentSubtitleText by remember { mutableStateOf<String?>(null) }
    var showProfanityOverlay by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // Seek tracking state
    var isSeekBuffering by remember { mutableStateOf(false) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }

    // LibVLC instances
    var libVLC: LibVLC? by remember { mutableStateOf(null) }
    var mediaPlayer: MediaPlayer? by remember { mutableStateOf(null) }
    
    // Video surface reference and attachment state
    var videoSurface: VLCVideoLayout? by remember { mutableStateOf(null) }
    var isAttached: Boolean by remember { mutableStateOf(false) }
    
    // Profanity filtering components
    val filteredSubtitleManager = remember {
        FilteredSubtitleManager(
            profanityFilter = com.purestream.profanity.ProfanityFilter(),
            subtitleParser = com.purestream.profanity.SubtitleParser(),
            openSubtitlesRepository = com.purestream.data.repository.OpenSubtitlesRepository(
                com.purestream.profanity.ProfanityFilter()
            )
        )
    }
    
    // Initialize LibVLC
    LaunchedEffect(videoUrl) {
        try {
            Log.d("VLCMediaPlayer", "Initializing LibVLC for URL: ${videoUrl.take(100)}...")
            
            // Clean up any existing instances
            try {
                mediaPlayer?.let { player ->
                    player.stop()
                    player.detachViews()
                    player.release()
                }
                libVLC?.release()
                isAttached = false // Reset attachment state
            } catch (e: Exception) {
                Log.w("VLCMediaPlayer", "Error cleaning up previous instances: ${e.message}")
            }
            
            // Create LibVLC with absolutely minimal configuration for Android TV
            val vlc = try {
                Log.d("VLCMediaPlayer", "Attempting LibVLC creation with no options")
                LibVLC(context)
            } catch (e: Exception) {
                Log.e("VLCMediaPlayer", "Failed to create LibVLC instance: ${e.message}", e)
                playerError = "Failed to initialize LibVLC: ${e.message}"
                isLoading = false
                onError("LibVLC initialization failed: ${e.message}")
                return@LaunchedEffect
            }
            
            libVLC = vlc
            Log.d("VLCMediaPlayer", "LibVLC instance created successfully")
            
            val player = MediaPlayer(vlc)
            mediaPlayer = player
            
            // Will disable embedded subtitles after media is playing
            
            // Set up comprehensive event listener
            player.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Opening -> {
                        Log.d("VLCMediaPlayer", "Media opening...")
                        isLoading = true
                        playerError = null
                    }
                    MediaPlayer.Event.Buffering -> {
                        val bufferingPercent = event.buffering
                        Log.d("VLCMediaPlayer", "Media buffering: ${bufferingPercent}%")

                        // Distinguish seek buffering from initial loading
                        val currentTime = System.currentTimeMillis()
                        val isSeekRelated = (currentTime - lastSeekTime) < 2000L

                        if (isSeekRelated) {
                            isSeekBuffering = bufferingPercent < 100f
                            Log.d("VLCMediaPlayer", "Seek buffering: $isSeekBuffering")
                        } else {
                            isLoading = bufferingPercent < 100f
                        }
                    }
                    MediaPlayer.Event.Playing -> {
                        Log.d("VLCMediaPlayer", "Media playing")
                        isPlaying = true
                        isPlayerReady = true
                        isLoading = false
                        onPlayingStateChange(true)
                        
                        // Disable embedded subtitles to prevent overlap with filtered subtitles
                        try {
                            // LibVLC 4.0.0-eap20 API for subtitle control needs research
                            // Note: spuTracks and setSpuTrack may not be available in this version
                            Log.d("VLCMediaPlayer", "Media ready - embedded subtitle control needs API research")
                        } catch (e: Exception) {
                            Log.w("VLCMediaPlayer", "Could not disable embedded subtitles: ${e.message}")
                        }
                        
                        onPlayerReady(player)
                    }
                    MediaPlayer.Event.Paused -> {
                        Log.d("VLCMediaPlayer", "Media paused")
                        isPlaying = false
                        onPlayingStateChange(false)
                    }
                    MediaPlayer.Event.Stopped -> {
                        Log.d("VLCMediaPlayer", "Media stopped")
                        isPlaying = false
                        onPlayingStateChange(false)
                    }
                    MediaPlayer.Event.EndReached -> {
                        Log.d("VLCMediaPlayer", "Media end reached")
                        isPlaying = false
                        onPlayingStateChange(false)
                        onEnded() // Trigger callback for automatic navigation
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        Log.e("VLCMediaPlayer", "Media error encountered")
                        playerError = "LibVLC playback error occurred"
                        isLoading = false
                        onError("LibVLC playback error")
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        val previousPosition = currentPosition
                        currentPosition = event.timeChanged
                        onPositionChange(currentPosition)

                        // Detect seeks (position jumps > 2 seconds)
                        if (kotlin.math.abs(currentPosition - previousPosition) > 2000) {
                            lastSeekTime = System.currentTimeMillis()
                            Log.d("VLCMediaPlayer", "Seek detected: ${previousPosition}ms -> ${currentPosition}ms")
                        }
                    }
                    MediaPlayer.Event.LengthChanged -> {
                        duration = event.lengthChanged
                        onDurationChange(duration)
                        Log.d("VLCMediaPlayer", "Duration: ${duration}ms")
                    }
                    MediaPlayer.Event.MediaChanged -> {
                        Log.d("VLCMediaPlayer", "Media changed")
                    }
                    else -> {
                        Log.d("VLCMediaPlayer", "Media event: ${event.type}")
                    }
                }
            }
            
            // Load and prepare media
            Log.d("VLCMediaPlayer", "Loading media from URL: $videoUrl")
            val uri = android.net.Uri.parse(videoUrl)
            Log.d("VLCMediaPlayer", "Parsed URI: $uri")
            
            val media = Media(vlc, uri)
            Log.d("VLCMediaPlayer", "Media object created")
            
            // Disable embedded subtitles to prevent overlap with filtered subtitles
            try {
                val subtitleTrackOption = ":sub-track-id=${Integer.MAX_VALUE}"
                media.addOption(subtitleTrackOption)
                Log.d("VLCMediaPlayer", "Added option to disable embedded subtitles: $subtitleTrackOption")
            } catch (e: Exception) {
                Log.w("VLCMediaPlayer", "Could not add subtitle disable option: ${e.message}")
            }
            
            // Set media with embedded subtitles disabled
            player.media = media
            media.release() // Release media reference as player now owns it
            
            Log.d("VLCMediaPlayer", "Media set successfully, player ready")
            
        } catch (e: Exception) {
            Log.e("VLCMediaPlayer", "Error initializing LibVLC", e)
            playerError = "Failed to initialize video player: ${e.message}"
            isLoading = false
            onError("LibVLC initialization failed: ${e.message}")
        }
    }
    
    // Position tracking and profanity filtering logic with manual subtitle timing
    LaunchedEffect(mediaPlayer, filteredSubtitleResult, showSubtitles, currentFilterLevel, subtitleTimingOffsetMs) {
        Log.d("VLCMediaPlayer", "LaunchedEffect triggered - filteredSubtitleResult: ${filteredSubtitleResult != null}, showSubtitles: $showSubtitles, filterLevel: $currentFilterLevel, timingOffset: ${subtitleTimingOffsetMs}ms")
        while (isActive) {
            mediaPlayer?.let { player ->
                val position = try {
                    player.time
                } catch (e: IllegalStateException) {
                    // Player has been released, stop tracking
                    Log.w("VLCMediaPlayer", "Player released, stopping position tracking")
                    return@LaunchedEffect
                } catch (e: Exception) {
                    Log.w("VLCMediaPlayer", "Error getting player time: ${e.message}")
                    return@LaunchedEffect
                }
                
                currentPosition = position
                
                // Handle profanity filtering if we have subtitle results
                filteredSubtitleResult?.let { result ->
                    try {
                        // Apply manual timing offset by adjusting the position used for both audio and subtitle logic
                        val adjustedPosition = position + subtitleTimingOffsetMs
                        
                        // Log timing adjustments for debugging
                        if (subtitleTimingOffsetMs != 0L) {
                            Log.d("VLCMediaPlayer", "Applying timing offset: ${subtitleTimingOffsetMs}ms (position: ${position}ms -> adjusted: ${adjustedPosition}ms)")
                        }
                        
                        // Check if we need to mute audio based on profanity timestamps
                        // Skip audio muting entirely when filter level is NONE
                        val shouldMute = if (currentFilterLevel == ProfanityFilterLevel.NONE) {
                            false
                        } else {
                            filteredSubtitleManager.getCurrentMutingStatus(
                                result.mutingTimestamps, 
                                adjustedPosition
                            )
                        }
                        
                        if (shouldMute != isAudioMuted) {
                            isAudioMuted = shouldMute
                            try {
                                // Use LibVLC volume property for audio control
                                if (shouldMute) {
                                    player.volume = 0
                                    Log.d("VLCMediaPlayer", "Audio muted via volume = 0")
                                } else {
                                    player.volume = 100
                                    Log.d("VLCMediaPlayer", "Audio unmuted via volume = 100")
                                }
                                Log.d("VLCMediaPlayer", "Audio muting changed: $shouldMute at adjusted position ${adjustedPosition}ms (original: ${position}ms, offset: ${subtitleTimingOffsetMs}ms)")
                            } catch (e: Exception) {
                                Log.e("VLCMediaPlayer", "Error setting volume: ${e.message}")
                            }
                        }
                        
                        // Handle subtitle display based on settings and filter level
                        if (showSubtitles) {
                            // Choose subtitle source based on current filter level
                            val subtitleSource = when (currentFilterLevel) {
                                ProfanityFilterLevel.NONE -> result.originalSubtitle
                                else -> result.filteredSubtitle
                            }
                            
                            val currentEntry = filteredSubtitleManager.getCurrentSubtitleEntry(
                                subtitleSource, 
                                adjustedPosition
                            )
                            currentSubtitleText = currentEntry?.displayText
                            
                            // Check if current subtitle line contains profanity for coloring
                            if (currentFilterLevel == ProfanityFilterLevel.NONE) {
                                showProfanityOverlay = false
                            } else {
                                val profanityEntry = filteredSubtitleManager.getCurrentProfanitySubtitle(
                                    result.filteredSubtitle, 
                                    adjustedPosition
                                )
                                showProfanityOverlay = profanityEntry != null
                            }
                        } else {
                            // Only show subtitles during profanity when subtitles are disabled
                            // Skip profanity overlay entirely when filter level is NONE
                            if (currentFilterLevel == ProfanityFilterLevel.NONE) {
                                currentSubtitleText = null
                                showProfanityOverlay = false
                            } else {
                                val profanityEntry = filteredSubtitleManager.getCurrentProfanitySubtitle(
                                    result.filteredSubtitle, 
                                    adjustedPosition
                                )
                                
                                if (profanityEntry != null) {
                                    currentSubtitleText = profanityEntry.displayText
                                    showProfanityOverlay = true
                                } else {
                                    currentSubtitleText = null
                                    showProfanityOverlay = false
                                }
                            }
                        }
                        
                    } catch (e: Exception) {
                        Log.e("VLCMediaPlayer", "Error in profanity filtering", e)
                    }
                } ?: run {
                    // No subtitle filtering, clear any existing subtitles
                    currentSubtitleText = null
                    showProfanityOverlay = false
                }
            }
            
            if (isActive) {
                delay(100) // Update every 100ms for smooth filtering
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            Log.d("VLCMediaPlayer", "Disposing LibVLC resources...")
            try {
                mediaPlayer?.let { player ->
                    try {
                        // Check if player is still valid before calling methods
                        if (player.isPlaying) {
                            player.stop()
                        }
                    } catch (e: IllegalStateException) {
                        Log.w("VLCMediaPlayer", "Player already released during stop: ${e.message}")
                    }
                    
                    try {
                        player.detachViews()
                    } catch (e: IllegalStateException) {
                        Log.w("VLCMediaPlayer", "Player already released during detach: ${e.message}")
                    }
                    
                    try {
                        player.release()
                    } catch (e: IllegalStateException) {
                        Log.w("VLCMediaPlayer", "Player already released during release: ${e.message}")
                    }
                }
                
                libVLC?.let { vlc ->
                    try {
                        vlc.release()
                    } catch (e: IllegalStateException) {
                        Log.w("VLCMediaPlayer", "LibVLC already released: ${e.message}")
                    }
                }
                
                isAttached = false
                Log.d("VLCMediaPlayer", "LibVLC resources disposed successfully")
            } catch (e: Exception) {
                Log.e("VLCMediaPlayer", "Error disposing LibVLC", e)
            }
        }
    }
    
    // Attach player to surface when both are ready (only once)
    LaunchedEffect(mediaPlayer, videoSurface) {
        val player = mediaPlayer
        val surface = videoSurface
        if (player != null && surface != null && !isAttached) {
            try {
                Log.d("VLCMediaPlayer", "Attaching player to video surface...")
                
                // Check if coroutine is still active before proceeding
                if (!isActive) {
                    Log.w("VLCMediaPlayer", "Coroutine cancelled, skipping attachment")
                    return@LaunchedEffect
                }
                
                // Detach any existing views first
                try {
                    player.detachViews()
                } catch (e: Exception) {
                    Log.w("VLCMediaPlayer", "No views to detach: ${e.message}")
                }
                
                // Check again if still active after detach
                if (!isActive) {
                    Log.w("VLCMediaPlayer", "Coroutine cancelled during detach")
                    return@LaunchedEffect
                }
                
                // Now attach to the new surface
                player.attachViews(surface, null, false, false)
                isAttached = true

                // Set aspect ratio to null to fill screen (fix letterboxing on mobile)
                try {
                    player.aspectRatio = null
                    Log.d("VLCMediaPlayer", "Set aspect ratio to fill screen")
                } catch (e: Exception) {
                    Log.w("VLCMediaPlayer", "Could not set aspect ratio: ${e.message}")
                }

                // Start playback after attachment
                if (isActive) {
                    delay(100) // Small delay to ensure surface is ready
                    if (isActive) {
                        Log.d("VLCMediaPlayer", "Starting playback...")
                        player.play()
                    }
                }
            } catch (e: Exception) {
                Log.e("VLCMediaPlayer", "Error attaching player to surface", e)
                if (isActive) {
                    playerError = "Failed to attach video surface: ${e.message}"
                    isLoading = false
                }
            }
        }
    }
    
    // Seek-aware timeout mechanism and fallback playback trigger
    LaunchedEffect(Unit) {
        // First attempt: Wait 5 seconds and try manual play if still loading
        delay(5000)
        if (isActive && isLoading && !isSeekBuffering && mediaPlayer != null && playerError == null) {
            Log.w("VLCMediaPlayer", "Video still loading after 5s (seek buffering: $isSeekBuffering)")
            try {
                mediaPlayer?.play()
            } catch (e: Exception) {
                Log.e("VLCMediaPlayer", "Manual play failed: ${e.message}")
            }
        }

        // Progressive timeout check with seek awareness
        var totalWaitTime = 5000L
        while (isActive && isLoading && playerError == null) {
            delay(5000) // Check every 5 seconds
            totalWaitTime += 5000

            // Skip timeout if currently seek buffering
            if (isSeekBuffering) {
                Log.d("VLCMediaPlayer", "Seek buffering in progress, pausing timeout")
                totalWaitTime = 5000L // Reset timeout
                continue
            }

            // Only trigger timeout after 30s of continuous loading (not seek buffering)
            if (totalWaitTime >= 30000) {
                Log.w("VLCMediaPlayer", "Video loading timeout - 30 seconds elapsed")
                playerError = "Video loading timeout - please check your network connection"
                isLoading = false
                break
            }
        }
    }

    Box(modifier = modifier) {
        // LibVLC Video Surface
        AndroidView(
            factory = { context ->
                VLCVideoLayout(context).also { layout ->
                    videoSurface = layout
                    Log.d("VLCMediaPlayer", "Video surface created")
                }
            },
            update = { layout ->
                // Try to attach player when surface is updated
                val player = mediaPlayer
                if (player != null && !isAttached) {
                    try {
                        Log.d("VLCMediaPlayer", "Attaching player in update block...")
                        player.detachViews()
                        player.attachViews(layout, null, false, false)
                        isAttached = true

                        // Set aspect ratio to null to fill screen (fix letterboxing on mobile)
                        try {
                            player.aspectRatio = null
                            Log.d("VLCMediaPlayer", "Set aspect ratio to fill screen")
                        } catch (e: Exception) {
                            Log.w("VLCMediaPlayer", "Could not set aspect ratio: ${e.message}")
                        }

                        // Start playback
                        player.play()
                        Log.d("VLCMediaPlayer", "Playback started from update block")
                    } catch (e: Exception) {
                        Log.e("VLCMediaPlayer", "Error in update block: ${e.message}")
                        playerError = "Failed to attach video surface: ${e.message}"
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Custom subtitle overlay - Enhanced for profanity filtering
        currentSubtitleText?.let { rawSubtitleText ->
            // Clean Unicode markers from subtitle text for display
            val cleanSubtitleText = rawSubtitleText.replace("\u200B", "").replace("\u200C", "")
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(
                    text = cleanSubtitleText,
                    color = if (showProfanityOverlay) Color(0xFF8B5CF6) else Color.White,
                    fontSize = 18.sp,
                    fontWeight = if (showProfanityOverlay) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        
        // Audio mute indicator - Enhanced styling
        if (isAudioMuted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        color = Color(0xAAEF4444),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🔇 FILTERED",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Loading indicator
        if (isLoading && playerError == null) {
            Box(
                modifier = Modifier.align(Alignment.Center)
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color(0xFF8B5CF6),
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
        
        // Error overlay
        playerError?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
                    .background(
                        color = Color(0xCC000000),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚠️ Playback Error",
                        color = Color(0xFFEF4444),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        
        // Debug info overlay removed for cleaner UI
    }
}


