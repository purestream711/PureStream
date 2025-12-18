package com.purestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailsScreen(
    movie: Movie,
    progressPercentage: Float? = null,  // 0.0 to 1.0, null if not started
    progressPosition: Long? = null,  // Position in milliseconds
    onPlayClick: (startPosition: Long) -> Unit,
    onBackClick: () -> Unit,
    onAnalyzeProfanityClick: ((Movie) -> Unit)? = null,
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
    
    // Auto-focus Play button when screen loads (TV only)
    LaunchedEffect(Unit) {
        if (!isMobile) {
            playButtonFocusRequester.requestFocus()
        }
    }
    
    // Auto-focus Play button after successful subtitle analysis (TV only)
    LaunchedEffect(subtitleAnalysisResult) {
        if (subtitleAnalysisResult != null && !isMobile) {
            playButtonFocusRequester.requestFocus()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Background Image with overlay
        movie.artUrl?.let { artUrl ->
            AsyncImage(
                model = artUrl,
                contentDescription = movie.title,
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
                // Back Button
                // Back Button - Hidden on mobile per user request
                if (!isMobile) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .background(
                                color = Color(0x80000000),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                            .size(36.dp)
                            .align(Alignment.Start)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
                
                // Movie Poster (Top - Mobile) with top padding
                Card(
                    modifier = Modifier
                        .width(240.dp)
                        .height(360.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 32.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        movie.thumbUrl?.let { thumbUrl ->
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = "${movie.title} Poster",
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
                
                // Movie Details (Below Poster - Mobile)
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Movie Title
                    Text(
                        text = movie.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    
                    // Movie Info Row with Technical Details
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        movie.year?.let { year ->
                            Text(
                                text = year.toString(),
                                fontSize = 14.sp,
                                color = Color(0xFFB3B3B3)
                            )
                        }
                        
                        movie.duration?.let { duration ->
                            Text(
                                text = formatDuration(duration),
                                fontSize = 14.sp,
                                color = Color(0xFFB3B3B3)
                            )
                        }
                        
                        movie.contentRating?.let { rating ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFF374151),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = rating,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                        
                        movie.rating?.let { rating ->
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
                    
                    // Movie Summary
                    movie.summary?.let { summary ->
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
                                    onClick = { callback(movie) },
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
                    
                    // Profanity Level Indicator (only show if not UNKNOWN to remove "Not Rated")
                    val profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN
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
                // Movie Poster (Left Side)
                Card(
                    modifier = Modifier
                        .width(300.dp)
                        .height(450.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        movie.thumbUrl?.let { thumbUrl ->
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = "${movie.title} Poster",
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
                
                // Movie Details (Right Side)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // Back Button
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
                
                // Movie Title
                Text(
                    text = movie.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                // Movie Info Row with Technical Details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    movie.year?.let { year ->
                        Text(
                            text = year.toString(),
                            fontSize = 14.sp,
                            color = Color(0xFFB3B3B3)
                        )
                    }
                    
                    movie.duration?.let { duration ->
                        Text(
                            text = formatDuration(duration),
                            fontSize = 14.sp,
                            color = Color(0xFFB3B3B3)
                        )
                    }
                    
                    movie.contentRating?.let { rating ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFF374151),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = rating,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                    
                    movie.rating?.let { rating ->
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
                
                // Movie Summary with auto-scroll
                movie.summary?.let { summary ->
                    AutoScrollingText(
                        text = summary,
                        maxLines = 3,
                        fontSize = 14.sp,
                        color = Color(0xFFE0E0E0),
                        lineHeight = 20.sp
                    )
                }
                
                // Action Buttons Row (moved directly beneath description)
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
                            onClick = { callback(movie) },
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
                
                // Subtitle Analysis Results (moved up under Play button) - Pro feature only
                if (isPremium) {
                    subtitleAnalysisResult?.let { result ->
                    SubtitleAnalysisCard(
                        analysisResult = result,
                        currentFilterLevel = currentProfile?.profanityFilterLevel ?: ProfanityFilterLevel.MODERATE
                    )
                }
                }
                
                // Profanity Level Indicator (only show if not UNKNOWN to remove "Not Rated")
                val profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN
                if (profanityLevel != ProfanityLevel.UNKNOWN) {
                    ProfanityLevelCard(profanityLevel = profanityLevel)
                }
                
                // Subtitle Analysis Error
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
        }
    }
}

@Composable
fun ProfanityLevelCard(profanityLevel: ProfanityLevel) {
    val (text, color, intensityLevel) = when (profanityLevel) {
        ProfanityLevel.NONE -> Triple("Clean", Color(0xFF10B981), 0)
        ProfanityLevel.LOW -> Triple("Mild Language", Color(0xFFF59E0B), 2)
        ProfanityLevel.MEDIUM -> Triple("Strong Language", Color(0xFFEF4444), 5)
        ProfanityLevel.HIGH -> Triple("Very Strong Language", Color(0xFF7C2D12), 8)
        ProfanityLevel.UNKNOWN -> Triple("Not Rated", Color(0xFF6B7280), -1)
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Intensity Level Badge
            if (intensityLevel >= 0) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(color, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = intensityLevel.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, androidx.compose.foundation.shape.CircleShape)
                )
            }
            
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
fun SubtitleIndicatorCard(hasSubtitles: Boolean) {
    val (text, color, icon) = if (hasSubtitles) {
        Triple("Protected", Color(0xFF10B981), Icons.Default.Lock)
    } else {
        Triple("Unprotected", Color(0xFFEF4444), Icons.Default.LockOpen)
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFB3B3B3),
            modifier = Modifier.width(100.dp)
        )
        
        Text(
            text = value,
            fontSize = 16.sp,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
    }
}

fun getWordBubbleColor(word: String, filterLevel: ProfanityFilterLevel): Color {
    // Use the same word categorization as ProfanityFilter for consistency
    val severityLevel = getWordSeverityLevel(word)
    
    // Return color based on whether this word would be filtered at current level
    return when {
        filterLevel == ProfanityFilterLevel.NONE -> Color(0xFFEF4444) // RED: Nothing filtered at NONE level
        severityLevel.ordinal <= filterLevel.ordinal -> Color(0xFF10B981) // GREEN: Word gets filtered
        else -> Color(0xFFEF4444) // RED: Word not filtered at this level
    }
}

fun getWordSeverityLevel(word: String): ProfanityFilterLevel {
    val lowercaseWord = word.lowercase()
    
    // Define word categories from most severe to least severe
    // MILD: Only the worst of the worst profanity (most severe)
    val mildOnlyWords = setOf(
        "fuck", "fucking", "fucked", "fucker", "fuckers", "fucks", "fuckin'",
        "motherfucker", "motherfuckers", "motherfucking",
        "bitch", "bitches", "ass", "asses", "asshole", "assholes"
    )
    
    // MODERATE: Additional words that require moderate filtering  
    val moderateOnlyWords = setOf(
        "shit", "shits", "shitting", "shitter", "cunt", "cunts", 
        "pussy", "pussies", "cock", "cocks", "cocksucker", "cocksuckers", "dick", "dicks", "dickhead", "dickheads",
        "whore", "whores", "slut", "sluts", "bastard", "bastards", 
        "piss", "pissed", "pissing", "damn", "damned"
    )
    
    // STRICT: Additional words that only require strict filtering (least severe)
    val strictOnlyWords = setOf(
        "god", "gods", "hell", "hells", "goddamn", "goddam", "goddammit", "god damn", "god dammit",
        "oh my god", "omg", "jesus", "christ", "lord", "jesus christ", "holy shit",
        "holy crap", "bloody", "bugger", "bollocks", "crap", "craps", "fag", "faggot",
        "retard", "retards", "gay", "homo", "queer", "lesbian", "tranny", "nigga", 
        "nigger", "son of a bitch", "spic", "chink", "wetback"
    )
    
    // Check from most severe to least severe
    return when {
        lowercaseWord in mildOnlyWords -> ProfanityFilterLevel.MILD
        lowercaseWord in moderateOnlyWords -> ProfanityFilterLevel.MODERATE  
        lowercaseWord in strictOnlyWords -> ProfanityFilterLevel.STRICT
        else -> ProfanityFilterLevel.STRICT // Default unknown words to require strictest filtering
    }
}

@Composable
fun SubtitleAnalysisCard(analysisResult: SubtitleAnalysisResult, currentFilterLevel: ProfanityFilterLevel = ProfanityFilterLevel.MODERATE) {
    val isMobile = rememberIsMobile()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1F2937).copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFFF5B800),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Subtitle Analysis Results",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            // Analysis Details - Simplified
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Profanity Level with Detected Words
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "Profanity Level",
                            fontSize = 14.sp,
                            color = Color(0xFF9CA3AF),
                            fontWeight = FontWeight.Medium
                        )
                        ProfanityLevelBadge(calculateProfanityLevel(analysisResult.profanityWordsCount))
                    }
                    
                    // Detected Words (if any) - Responsive layout
                    if (analysisResult.detectedWords.isNotEmpty()) {
                        Column {
                            Text(
                                text = "Detected Words (${analysisResult.detectedWords.size})",
                                fontSize = 14.sp,
                                color = Color(0xFF9CA3AF),
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            if (isMobile) {
                                // Mobile: 3x3 grid layout (3 words per row, 3 rows, +more for 9th+ words)
                                val maxWordsToShow = if (analysisResult.detectedWords.size > 8) 8 else analysisResult.detectedWords.size
                                val censoredWords = analysisResult.detectedWords.take(maxWordsToShow).map { word ->
                                    censorWord(word)
                                }
                                val hasMoreWords = analysisResult.detectedWords.size > maxWordsToShow
                                
                                // Simple flexible layout with manual row wrapping
                                val allWords = censoredWords.toMutableList()
                                if (hasMoreWords) {
                                    allWords.add("+${analysisResult.detectedWords.size - maxWordsToShow} more")
                                }
                                
                                // Create rows manually by checking estimated width
                                val rows = mutableListOf<MutableList<String>>()
                                var currentRow = mutableListOf<String>()
                                var currentRowEstimatedWidth = 0
                                val maxRowWidth = 280 // Approximate max width for mobile
                                val averageCharWidth = 8 // Approximate character width
                                
                                allWords.forEachIndexed { index, word ->
                                    val wordWidth = word.length * averageCharWidth + 32 // padding + background
                                    val spacingWidth = if (currentRow.isNotEmpty()) 12 else 0 // 6dp spacing
                                    
                                    if (currentRowEstimatedWidth + wordWidth + spacingWidth > maxRowWidth && currentRow.isNotEmpty()) {
                                        rows.add(currentRow)
                                        currentRow = mutableListOf()
                                        currentRowEstimatedWidth = 0
                                    }
                                    
                                    currentRow.add(word)
                                    currentRowEstimatedWidth += wordWidth + spacingWidth
                                }
                                
                                if (currentRow.isNotEmpty()) {
                                    rows.add(currentRow)
                                }
                                
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    rows.forEach { row ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            row.forEach { word ->
                                                val isMoreIndicator = word.startsWith("+")
                                                val originalWordIndex = if (!isMoreIndicator) {
                                                    censoredWords.indexOf(word)
                                                } else -1
                                                
                                                val bubbleColor = if (isMoreIndicator) {
                                                    Color(0xFF6B7280)
                                                } else {
                                                    val originalWord = if (originalWordIndex >= 0) {
                                                        analysisResult.detectedWords.getOrNull(originalWordIndex) ?: word
                                                    } else word
                                                    getWordBubbleColor(originalWord, currentFilterLevel)
                                                }
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            color = bubbleColor.copy(alpha = 0.3f),
                                                            shape = RoundedCornerShape(6.dp)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = word,
                                                        fontSize = 12.sp,
                                                        color = if (isMoreIndicator) Color(0xFFB3B3B3) else Color.White,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // TV: Original 2-row layout (5 per row with +more in 2nd row) 
                                val maxWordsToShow = if (analysisResult.detectedWords.size > 9) 8 else 9
                                val censoredWords = analysisResult.detectedWords.take(maxWordsToShow).map { word ->
                                    censorWord(word)
                                }
                                
                                // Display words in exactly 2 rows of 5 (or 4+1 for +more)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val wordsPerRow = 5
                                    val hasMoreWords = analysisResult.detectedWords.size > maxWordsToShow
                                    
                                    // First row: Always 5 words
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        censoredWords.take(5).forEachIndexed { index, censoredWord ->
                                            val originalWord = analysisResult.detectedWords.getOrNull(index) ?: censoredWord
                                            val bubbleColor = getWordBubbleColor(originalWord, currentFilterLevel)
                                            
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = bubbleColor.copy(alpha = 0.3f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = censoredWord,
                                                    fontSize = 12.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Second row: Remaining words + +more if needed
                                    if (censoredWords.size > 5 || hasMoreWords) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Show remaining words (up to 3 if +more needed, up to 4 if not)
                                            val secondRowWords = if (hasMoreWords) {
                                                censoredWords.drop(5).take(3) // Leave space for +more
                                            } else {
                                                censoredWords.drop(5) // Show all remaining
                                            }
                                            
                                            secondRowWords.forEachIndexed { index, censoredWord ->
                                                val originalWord = analysisResult.detectedWords.getOrNull(5 + index) ?: censoredWord
                                                val bubbleColor = getWordBubbleColor(originalWord, currentFilterLevel)
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            color = bubbleColor.copy(alpha = 0.3f),
                                                            shape = RoundedCornerShape(6.dp)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = censoredWord,
                                                        fontSize = 12.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                            
                                            // Show +more bubble as last item in 2nd row if needed
                                            if (hasMoreWords) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            color = Color(0xFF6B7280).copy(alpha = 0.3f),
                                                            shape = RoundedCornerShape(6.dp)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "+${analysisResult.detectedWords.size - maxWordsToShow} more",
                                                        fontSize = 12.sp,
                                                        color = Color(0xFFB3B3B3),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfanityLevelBadge(profanityLevel: ProfanityLevel) {
    val (text, color) = when (profanityLevel) {
        ProfanityLevel.NONE -> "Clean" to Color(0xFF10B981)
        ProfanityLevel.LOW -> "Mild" to Color(0xFFF59E0B)
        ProfanityLevel.MEDIUM -> "Moderate" to Color(0xFFEF4444)
        ProfanityLevel.HIGH -> "Severe" to Color(0xFF7C2D12)
        ProfanityLevel.UNKNOWN -> "Unknown" to Color(0xFF6B7280)
    }
    
    Box(
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
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

// Calculate profanity level based on word count
private fun calculateProfanityLevel(profanityWordsCount: Int): ProfanityLevel {
    return when {
        profanityWordsCount == 0 -> ProfanityLevel.NONE // Clean = 0 profanity
        profanityWordsCount < 5 -> ProfanityLevel.LOW // Mild = 1-4 profanity
        profanityWordsCount < 10 -> ProfanityLevel.MEDIUM // Moderate = 5-9 profanity
        else -> ProfanityLevel.HIGH // Severe = 10+ profanity
    }
}

// Censor words by showing first letter and asterisks
private fun censorWord(word: String): String {
    if (word.length <= 1) return word
    return word.first() + "*".repeat(word.length - 1)
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

