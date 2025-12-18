package com.purestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import coil.compose.AsyncImage
import com.purestream.data.model.*
import com.purestream.ui.theme.getAnimatedButtonBackgroundColor
import com.purestream.utils.rememberIsMobile
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeDetailsScreen(
    episode: Episode,
    tvShowTitle: String,
    showBackgroundUrl: String? = null,
    progressPercentage: Float? = null,  // Add progress parameter
    progressPosition: Long? = null,  // Position in milliseconds
    onPlayClick: (startPosition: Long) -> Unit,
    onBackClick: () -> Unit,
    onAnalyzeProfanityClick: ((Episode) -> Unit)? = null,
    isAnalyzingSubtitles: Boolean = false,
    subtitleAnalysisResult: SubtitleAnalysisResult? = null,
    subtitleAnalysisError: String? = null,
    onClearAnalysisError: () -> Unit = {},
    canAnalyzeProfanity: Boolean = true,
    currentProfile: Profile? = null,
    isPremium: Boolean = false
) {
    val isMobile = rememberIsMobile()
    val playButtonFocusRequester = remember { FocusRequester() }
    
    // Auto-focus Play button when screen loads
    LaunchedEffect(Unit) {
        try {
            // Add small delay to ensure composables are laid out
            kotlinx.coroutines.delay(100)
            playButtonFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Silently fail if focus request fails
            android.util.Log.w("EpisodeDetailsScreen", "Focus request failed: ${e.message}")
        }
    }
    
    // Auto-focus Play button after successful subtitle analysis
    LaunchedEffect(subtitleAnalysisResult) {
        if (subtitleAnalysisResult != null) {
            try {
                kotlinx.coroutines.delay(50)
                playButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                android.util.Log.w("EpisodeDetailsScreen", "Focus request failed: ${e.message}")
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Background Image with overlay (use show background for consistency)
        showBackgroundUrl?.let { backgroundUrl ->
            AsyncImage(
                model = backgroundUrl,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = if (isMobile) 0.15f else 0.3f
            )
        }
        
        // Additional mobile background dimming for better visibility
        if (isMobile) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        }
        
        // Content Layout - Responsive
        if (isMobile) {
            // Mobile: Vertical layout with scrolling
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Episode Thumbnail (Top - Mobile) with top padding
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .height(180.dp) // 16:9 aspect ratio for episodes
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 32.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            episode.thumbUrl?.let { thumbUrl ->
                                AsyncImage(
                                    model = thumbUrl,
                                    contentDescription = "${episode.title} Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } ?: Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF374151)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Movie,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color(0xFF6B7280)
                                )
                            }

                            // Progress bar at bottom
                            progressPercentage?.let { progress ->
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .align(Alignment.BottomCenter),
                                    color = Color(0xFFF5B800),
                                    trackColor = Color(0xFF374151)
                                )
                            }
                        }
                    }
                }
                
                // Episode Details (Below Thumbnail - Mobile)
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Episode Title
                    Text(
                        text = episode.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    
                    // Show Title and Episode Info
                    Text(
                        text = "$tvShowTitle - Season ${episode.seasonNumber}, Episode ${episode.episodeNumber}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB3B3B3),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    
                    // Episode Info Row with Technical Details
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        episode.year?.let { year ->
                            Text(
                                text = year.toString(),
                                fontSize = 14.sp,
                                color = Color(0xFFB3B3B3)
                            )
                        }
                        
                        episode.duration?.let { duration ->
                            Text(
                                text = formatDuration(duration),
                                fontSize = 14.sp,
                                color = Color(0xFFB3B3B3)
                            )
                        }
                        
                        episode.rating?.let { rating ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = String.format("%.1f", rating),
                                    fontSize = 14.sp,
                                    color = Color(0xFFB3B3B3)
                                )
                            }
                        }
                        
                        // Protection Status Padlock
                        val isAnalyzed = !canAnalyzeProfanity
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isAnalyzed) Color(0xFF10B981).copy(alpha = 0.8f) else Color(0xFFEF4444).copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Icon(
                                if (isAnalyzed) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isAnalyzed) "Content Analyzed" else "Content Not Analyzed",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    // Episode Summary
                    episode.summary?.let { summary ->
                        Text(
                            text = summary,
                            fontSize = 14.sp,
                            color = Color(0xFFE0E0E0),
                            lineHeight = 20.sp
                        )
                    }
                    
                    // Action Buttons (mobile) - Column layout with Analyze Profanity below
                    Column(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // First Row: Play/Resume + Restart buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Show Resume + Restart when there's progress, otherwise show Play
                            if (progressPercentage != null && progressPercentage > 0f) {
                                // Resume Button
                                val resumeButtonInteractionSource = remember { MutableInteractionSource() }
                                val resumeButtonBackgroundColor = getAnimatedButtonBackgroundColor(
                                    interactionSource = resumeButtonInteractionSource,
                                    defaultColor = Color(0xFFF5B800)
                                )

                                Button(
                                    onClick = { onPlayClick(progressPosition ?: 0L) },
                                    modifier = Modifier.height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = resumeButtonBackgroundColor,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Resume",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Resume",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Restart Button
                                val restartButtonInteractionSource = remember { MutableInteractionSource() }
                                val restartButtonBackgroundColor = getAnimatedButtonBackgroundColor(
                                    interactionSource = restartButtonInteractionSource,
                                    defaultColor = Color(0xFF9CA3AF)
                                )

                                IconButton(
                                    onClick = { onPlayClick(0L) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            color = restartButtonBackgroundColor,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Restart",
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.White
                                    )
                                }
                            } else {
                                // Play Button (no progress)
                                val playButtonInteractionSource = remember { MutableInteractionSource() }
                                val playButtonBackgroundColor = getAnimatedButtonBackgroundColor(
                                    interactionSource = playButtonInteractionSource,
                                    defaultColor = Color(0xFFF5B800)
                                )

                                Button(
                                    onClick = { onPlayClick(0L) },
                                    modifier = Modifier.height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = playButtonBackgroundColor,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Play",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Second Row: Analyze Profanity Button - Pro feature only, full width
                        if (isPremium) {
                            onAnalyzeProfanityClick?.let { callback ->
                                val buttonEnabled = canAnalyzeProfanity && !isAnalyzingSubtitles
                                val buttonText = when {
                                    isAnalyzingSubtitles -> "Analyzing..."
                                    !canAnalyzeProfanity -> "Analysis Complete"
                                    else -> "Analyze Profanity"
                                }

                                val isComplete = !canAnalyzeProfanity
                                val buttonColor = when {
                                    isComplete -> Color(0xFF10B981) // Green for completed
                                    buttonEnabled -> Color.White
                                    else -> Color(0xFF6B7280)
                                }

                                OutlinedButton(
                                    onClick = { callback(episode) },
                                    modifier = Modifier
                                        .height(48.dp),
                                    enabled = buttonEnabled,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = buttonColor
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        buttonColor
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isAnalyzingSubtitles) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(
                                            if (canAnalyzeProfanity) Icons.Default.Psychology
                                            else Icons.Default.CheckCircle,
                                            contentDescription = buttonText,
                                            modifier = Modifier.size(16.dp),
                                            tint = buttonColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = buttonText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = buttonColor
                                    )
                                }
                            }
                        }
                    }
                    
                    // Subtitle Analysis Results (mobile) - Pro feature only
                    if (isPremium) {
                        subtitleAnalysisResult?.let { result ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            SubtitleAnalysisCard(
                                analysisResult = result,
                                currentFilterLevel = currentProfile?.profanityFilterLevel ?: ProfanityFilterLevel.MODERATE
                            )
                        }
                    }
                    }
                    
                    // Profanity Level Indicator (only show if not UNKNOWN)
                    val profanityLevel = episode.profanityLevel ?: ProfanityLevel.UNKNOWN
                    if (profanityLevel != ProfanityLevel.UNKNOWN) {
                        ProfanityLevelCard(profanityLevel = profanityLevel)
                    }
                    
                    // Subtitle Analysis Error (mobile)
                    subtitleAnalysisError?.let { error ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Analysis Failed",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = error,
                                        fontSize = 14.sp,
                                        color = Color(0xFFE0E0E0)
                                    )
                                }
                                
                                IconButton(
                                    onClick = onClearAnalysisError,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // TV: Horizontal layout (existing)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                // Left Column: Thumbnail and Analysis Results
                Column(
                    modifier = Modifier.width(400.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Episode Thumbnail (Top Left) - 16:9 aspect ratio for episodes
                    Box(
                        modifier = Modifier
                            .width(400.dp)
                            .height(225.dp) // 16:9 aspect ratio (400/225 = 1.78)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                episode.thumbUrl?.let { thumbUrl ->
                                    AsyncImage(
                                        model = thumbUrl,
                                        contentDescription = "${episode.title} Thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } ?: Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF374151)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Movie,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = Color(0xFF6B7280)
                                    )
                                }

                                // Progress bar at bottom
                                progressPercentage?.let { progress ->
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .align(Alignment.BottomCenter),
                                        color = Color(0xFFF5B800),
                                        trackColor = Color(0xFF374151)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Analysis Results (directly below thumbnail) - Pro feature only
                    if (isPremium) {
                        subtitleAnalysisResult?.let { result ->
                        SubtitleAnalysisCard(
                            analysisResult = result,
                            currentFilterLevel = currentProfile?.profanityFilterLevel ?: ProfanityFilterLevel.MODERATE
                        )
                    }
                    }
                    
                    // Analysis Error (directly below thumbnail)
                    subtitleAnalysisError?.let { error ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Analysis Failed",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = error,
                                        fontSize = 14.sp,
                                        color = Color(0xFFE0E0E0)
                                    )
                                }
                                
                                IconButton(
                                    onClick = onClearAnalysisError,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Right Column: Controls, Episode Details, and Action Buttons
                Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Back Button (to the right of thumbnail)
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .background(
                            color = Color(0x80000000),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                
                // Episode Details (directly beneath back button)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                
                // Episode Title
                Text(
                    text = episode.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                // Show Title and Episode Info
                Text(
                    text = "$tvShowTitle - Season ${episode.seasonNumber}, Episode ${episode.episodeNumber}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFB3B3B3)
                )
                
                // Episode Info Row with Technical Details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    episode.year?.let { year ->
                        Text(
                            text = year.toString(),
                            fontSize = 14.sp,
                            color = Color(0xFFB3B3B3)
                        )
                    }
                    
                    episode.duration?.let { duration ->
                        Text(
                            text = formatDuration(duration),
                            fontSize = 14.sp,
                            color = Color(0xFFB3B3B3)
                        )
                    }
                    
                    episode.rating?.let { rating ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = String.format("%.1f", rating),
                                fontSize = 14.sp,
                                color = Color(0xFFB3B3B3)
                            )
                        }
                    }
                    
                    // Protection Status Padlock (matching MediaPlayer design)
                    val isAnalyzed = !canAnalyzeProfanity // Analysis complete means not canAnalyze
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isAnalyzed) Color(0xFF10B981).copy(alpha = 0.8f) else Color(0xFFEF4444).copy(alpha = 0.8f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Icon(
                            if (isAnalyzed) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isAnalyzed) "Content Analyzed" else "Content Not Analyzed",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Episode Summary with auto-scroll
                episode.summary?.let { summary ->
                    AutoScrollingText(
                        text = summary,
                        maxLines = 4,
                        fontSize = 14.sp,
                        color = Color(0xFFE0E0E0),
                        lineHeight = 20.sp
                    )
                }
                
                // Action Buttons (moved directly beneath episode summary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Show Resume + Restart when there's progress, otherwise show Play
                    if (progressPercentage != null && progressPercentage > 0f) {
                        // Resume Button
                        var isResumeFocused by remember { mutableStateOf(false) }
                        val resumeButtonInteractionSource = remember { MutableInteractionSource() }
                        val resumeButtonBackgroundColor = if (isResumeFocused) Color(0xFFF5B800) else Color(0xFF8B5CF6)
                        val resumeButtonContentColor = if (isResumeFocused) Color.Black else Color.White

                        Button(
                            onClick = { onPlayClick(progressPosition ?: 0L) },
                            modifier = Modifier
                                .height(48.dp)
                                .focusRequester(playButtonFocusRequester)
                                .onFocusChanged { isResumeFocused = it.isFocused }
                                .hoverable(resumeButtonInteractionSource)
                                .focusable(interactionSource = resumeButtonInteractionSource),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = resumeButtonBackgroundColor,
                                contentColor = resumeButtonContentColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Resume",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Resume",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Restart Button
                        var isRestartFocused by remember { mutableStateOf(false) }
                        val restartButtonInteractionSource = remember { MutableInteractionSource() }
                        val restartButtonBackgroundColor = if (isRestartFocused) Color.White else Color(0xFF9CA3AF)
                        val restartButtonIconColor = if (isRestartFocused) Color.Black else Color.White

                        IconButton(
                            onClick = { onPlayClick(0L) },
                            modifier = Modifier
                                .size(48.dp)
                                .onFocusChanged { isRestartFocused = it.isFocused }
                                .background(
                                    color = restartButtonBackgroundColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .hoverable(restartButtonInteractionSource)
                                .focusable(interactionSource = restartButtonInteractionSource)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Restart",
                                modifier = Modifier.size(24.dp),
                                tint = restartButtonIconColor
                            )
                        }
                    } else {
                        // Play Button (no progress)
                        var isPlayFocused by remember { mutableStateOf(false) }
                        val playButtonInteractionSource = remember { MutableInteractionSource() }
                        val playButtonBackgroundColor = if (isPlayFocused) Color(0xFFF5B800) else Color(0xFF8B5CF6)
                        val playButtonContentColor = if (isPlayFocused) Color.Black else Color.White

                        Button(
                            onClick = { onPlayClick(0L) },
                            modifier = Modifier
                                .height(48.dp)
                                .focusRequester(playButtonFocusRequester)
                                .onFocusChanged { isPlayFocused = it.isFocused }
                                .hoverable(playButtonInteractionSource)
                                .focusable(interactionSource = playButtonInteractionSource),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = playButtonBackgroundColor,
                                contentColor = playButtonContentColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Analyze Profanity Button - Pro feature only
                    if (isPremium) {
                        onAnalyzeProfanityClick?.let { callback ->
                        var isAnalyzeFocused by remember { mutableStateOf(false) }
                        val buttonEnabled = canAnalyzeProfanity && !isAnalyzingSubtitles
                        val buttonText = when {
                            isAnalyzingSubtitles -> "Analyzing..."
                            !canAnalyzeProfanity -> "Analysis Complete"
                            else -> "Analyze Profanity"
                        }

                        val isComplete = !canAnalyzeProfanity
                        val buttonColor = when {
                            isAnalyzeFocused -> Color.Black // Black text when focused
                            isComplete -> Color(0xFF10B981) // Green for completed
                            buttonEnabled -> Color.White
                            else -> Color(0xFF6B7280)
                        }

                        val buttonBackgroundColor = if (isAnalyzeFocused) Color.White else Color.Transparent
                        val buttonBorderColor = if (isAnalyzeFocused) Color.White else buttonColor

                        OutlinedButton(
                            onClick = { callback(episode) },
                            modifier = Modifier
                                .height(48.dp)
                                .onFocusChanged { isAnalyzeFocused = it.isFocused },
                            enabled = buttonEnabled,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = buttonColor,
                                containerColor = buttonBackgroundColor
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                buttonBorderColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isAnalyzingSubtitles) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    if (canAnalyzeProfanity) Icons.Default.Psychology 
                                    else Icons.Default.CheckCircle,
                                    contentDescription = buttonText,
                                    modifier = Modifier.size(16.dp),
                                    tint = buttonColor
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = buttonText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = buttonColor
                            )
                        }
                    }
                    }
                }
                
                // Profanity Level Indicator (only show if not UNKNOWN)
                val profanityLevel = episode.profanityLevel ?: ProfanityLevel.UNKNOWN
                if (profanityLevel != ProfanityLevel.UNKNOWN) {
                    ProfanityLevelCard(profanityLevel = profanityLevel)
                }
                }
            }
        }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val hours = durationMs / (1000 * 60 * 60)
    val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)

    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

/**
 * Auto-scrolling text composable for TV layouts
 * Scrolls long text automatically if it exceeds maxLines
 *
 * Pattern: Wait 3s → Scroll to end → Wait 5s → Jump to top → Repeat
 */
@Composable
private fun AutoScrollingText(
    text: String,
    maxLines: Int,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    var isTruncated by remember(text) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Calculate max height in pixels
    val maxHeightPx = with(density) { (lineHeight.value * maxLines).dp.toPx() }

    Column(
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .heightIn(max = with(density) { maxHeightPx.toDp() })
                .then(
                    if (isTruncated) {
                        Modifier.verticalScroll(scrollState)
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(
                text = text,
                fontSize = fontSize,
                color = color,
                lineHeight = lineHeight,
                maxLines = if (isTruncated) Int.MAX_VALUE else maxLines,
                overflow = if (isTruncated) TextOverflow.Visible else TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult ->
                    // Check if text was truncated due to maxLines constraint
                    val wasTruncated = textLayoutResult.didOverflowHeight || textLayoutResult.lineCount > maxLines
                    if (!isTruncated && wasTruncated) {
                        isTruncated = true
                    }
                }
            )
        }
    }

    // Auto-scroll logic if text is truncated
    LaunchedEffect(text, isTruncated) {
        if (isTruncated) {
            // Wait for layout to complete and scrollState to have a valid maxValue
            kotlinx.coroutines.delay(100)

            if (scrollState.maxValue > 0) {
                while (true) {
                    kotlinx.coroutines.delay(3000) // Wait 3 seconds before scrolling

                    // Calculate scroll duration based on distance and speed (30 pixels/second)
                    val scrollDistance = scrollState.maxValue
                    val scrollDuration = (scrollDistance / 30f * 1000).toInt() // Convert to milliseconds

                    // Scroll to bottom smoothly
                    scrollState.animateScrollTo(
                        value = scrollDistance,
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = scrollDuration,
                            easing = androidx.compose.animation.core.LinearEasing
                        )
                    )

                    kotlinx.coroutines.delay(5000) // Wait 5 seconds at the bottom

                    // Instantly jump back to top
                    scrollState.scrollTo(0)
                }
            }
        }
    }
}

