package com.purestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import coil.compose.AsyncImage
import com.purestream.data.model.*
import com.purestream.ui.components.DemoModePlaybackBlockedDialog
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
    isPremium: Boolean = false,
    isDemoMode: Boolean = false
) {
    val isMobile = rememberIsMobile()
    val playButtonFocusRequester = remember { FocusRequester() }

    // Scroll state for mobile LazyColumn (for parallax effect)
    val scrollState = if (isMobile) rememberLazyListState() else null

    // Animation state
    var contentVisible by remember { mutableStateOf(false) }
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "content_alpha"
    )
    val contentScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0.95f,
        animationSpec = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "content_scale"
    )

    // Parallax backdrop alpha calculation (mobile only)
    val backdropAlpha by remember {
        derivedStateOf {
            if (isMobile && scrollState != null) {
                val offset = scrollState.firstVisibleItemScrollOffset.toFloat()
                val maxScroll = 600f
                (1f - (offset / maxScroll)).coerceIn(0f, 1f)
            } else {
                1f // No parallax on TV
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        contentVisible = true
        // Only request focus if not mobile
        if (!isMobile) {
            try {
                playButtonFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    // Ensure focus is on Play button initially or when progress clears
    LaunchedEffect(progressPercentage) {
        if (!isMobile && (progressPercentage == null || progressPercentage <= 0f)) {
            try {
                playButtonFocusRequester.requestFocus()
            } catch (e: Exception) {}
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
            .background(Color.Black)
    ) {
        // Rich Background Image with consistent overlay
        movie.artUrl?.let { artUrl ->
            AsyncImage(
                model = artUrl,
                contentDescription = null,
                modifier = if (isMobile) {
                    Modifier.fillMaxSize()
                } else {
                    // TV: Positioned to right, faded edges
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            // Fade out left side (Transparent -> Black)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0.0f to Color.Transparent,
                                    0.2f to Color.Transparent,
                                    0.6f to Color.Black
                                ),
                                blendMode = BlendMode.DstIn
                            )
                            // Fade out bottom (Black -> Transparent)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.Black,
                                    0.6f to Color.Black,
                                    1.0f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                },
                contentScale = ContentScale.Crop,
                alpha = if (isMobile) 0.4f else 0.6f
            )
        }
        
        // Deep Vertical and Horizontal Gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isMobile) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F172A).copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black
                            )
                        )
                    } else {
                        // TV: Horizontal gradient to darken left side for text readability
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.95f),
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 1500f // Adjust based on screen width approx
                        )
                    }
                )
        )

        // Pulsating Glow behind content
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "glow")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.25f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(5000),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "glow_alpha"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF8B5CF6).copy(alpha = glowAlpha), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset.Unspecified,
                        radius = 1200f
                    )
                )
        )

        // Main Content - Different layouts for Mobile vs TV
        if (isMobile && scrollState != null) {
            // Mobile: LazyColumn with Hero Layout and Parallax
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = contentAlpha
                        scaleX = contentScale
                        scaleY = contentScale
                    }
            ) {
                // Hero Section with Backdrop and Clearlogo
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(650.dp)
                    ) {
                        // Backdrop Image with Parallax
                        movie.artUrl?.let { artUrl ->
                            AsyncImage(
                                model = artUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { 
                                        alpha = backdropAlpha 
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                0.0f to Color.Black,
                                                0.75f to Color.Black,
                                                1.0f to Color.Transparent
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Dark Gradient Overlay - subtle top shadow and bottom fade
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.4f),
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Content Overlay (Centered)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Clearlogo (Centered)
                            if (movie.logoUrl != null) {
                                AsyncImage(
                                    model = movie.logoUrl,
                                    contentDescription = movie.title,
                                    modifier = Modifier
                                        .height(120.dp)
                                        .widthIn(max = 300.dp),
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.Center
                                )
                            } else {
                                // Fallback to text title
                                Text(
                                    text = movie.title,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 38.sp
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            // Metadata Row (Year, Duration, Genres)
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${movie.year ?: ""}  •  ${formatDuration(movie.duration ?: 0)}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                movie.contentRating?.let {
                                    Text(
                                        text = "  •  $it",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Rating badges row (if available)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                movie.rating?.let {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(String.format("%.1f", it), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }

                                // Protection Status Badge
                                val isAnalyzed = !canAnalyzeProfanity
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            if (isAnalyzed) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAnalyzed) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = if (isAnalyzed) Color(0xFF10B981) else Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isAnalyzed) "Protected" else "Unprotected",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Content Section (Buttons and text content)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.4f to Color.Transparent
                                )
                            )
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Watch/Resume Button
                        Button(
                            onClick = { onPlayClick(progressPosition ?: 0L) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .focusRequester(playButtonFocusRequester),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (progressPercentage != null && progressPercentage > 0f) "RESUME" else "WATCH",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = Color.White
                            )
                        }

                        // Restart Button (Always present for focus stability)
                        val hasProgress = progressPercentage != null && progressPercentage > 0f
                        OutlinedButton(
                            onClick = { onPlayClick(0L) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (hasProgress) 48.dp else 0.dp)
                                .graphicsLayer { alpha = if (hasProgress) 1f else 0f }
                                .focusProperties { canFocus = hasProgress },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            if (hasProgress) {
                                Icon(Icons.Default.Refresh, "Restart", tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("RESTART FROM BEGINNING", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Progress Bar
                        progressPercentage?.let { progress ->
                            if (progress > 0f && progress < 0.90f) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )
                            }
                        }

                        // Summary
                        Text(
                            text = movie.summary ?: "",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 24.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Studio (if available)
                        movie.studio?.let {
                            Text(
                                text = "Studio: $it",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Profanity Level (if known)
                        if (movie.profanityLevel != ProfanityLevel.UNKNOWN && !isDemoMode) {
                            ProfanityLevelCard(movie.profanityLevel ?: ProfanityLevel.UNKNOWN)
                        }
                    }
                }

                // Analysis Section (Glass Panel Tile)
                val (lvl, _, _) = com.purestream.utils.LevelCalculator.calculateLevel(currentProfile?.totalFilteredWordsCount ?: 0)
                if (isPremium || isDemoMode || lvl >= 10) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 32.dp)
                                .background(Color(0xFF1A1C2E).copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)),
                                    RoundedCornerShape(24.dp)
                                )
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Psychology, null, tint = Color(0xFF8B5CF6))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "CONTENT ANALYSIS",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp,
                                    color = Color.White
                                )
                            }

                            if (canAnalyzeProfanity) {
                                Button(
                                    onClick = { onAnalyzeProfanityClick?.invoke(movie) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isAnalyzingSubtitles,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isAnalyzingSubtitles) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                                    else Text("ANALYZE NOW", color = Color.White)
                                }
                            }

                            subtitleAnalysisResult?.let {
                                SubtitleAnalysisCard(it, currentProfile?.profanityFilterLevel ?: ProfanityFilterLevel.MODERATE)
                            }
                        }
                    }
                }

                // Bottom padding
                item {
                    Spacer(Modifier.height(64.dp))
                }
            }
        } else {
            // TV: Layout with movie details on left, actions trigger specific views
            var isAnalysisExpanded by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = contentAlpha
                        scaleX = contentScale
                        scaleY = contentScale
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Column: Movie Details (Limited width to reveal background)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .padding(top = 40.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title or Clearlogo (50% larger)
                        if (movie.logoUrl != null) {
                            AsyncImage(
                                model = movie.logoUrl,
                                contentDescription = movie.title,
                                modifier = Modifier
                                    .height(94.dp)
                                    .widthIn(max = 375.dp),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart
                            )
                        } else {
                            Text(
                                text = movie.title,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                lineHeight = 32.sp
                            )
                        }

                        // Meta Row (Smaller fonts)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MetadataBadge(movie.contentRating ?: "NR", Color.White.copy(alpha = 0.1f))
                            Text(
                                text = "${movie.year}  •  ${formatDuration(movie.duration ?: 0)}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            // Rating
                            movie.rating?.let {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(String.format("%.1f", it), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // Protection Status Badge (TV)
                            val isAnalyzed = !canAnalyzeProfanity
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        if (isAnalyzed) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAnalyzed) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = if (isAnalyzed) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isAnalyzed) "Protected" else "Unprotected",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Summary with auto-scroll (3 lines max)
                        movie.summary?.let { summary ->
                            AutoScrollingText(
                                text = summary,
                                maxLines = 3,
                                fontSize = 14.sp,
                                color = Color(0xFFE0E0E0),
                                lineHeight = 20.sp
                            )
                        }

                        // Progress Indicator (below description, above buttons)
                        progressPercentage?.let { progress ->
                            if (progress > 0f && progress < 0.90f) {
                                val totalDurationMs = movie.duration ?: 0L
                                val remainingMs = ((1f - progress) * totalDurationMs).toLong()
                                val remainingMinutes = (remainingMs / (1000 * 60)).toInt()

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth(0.8f)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.weight(1f).height(4.dp),
                                        color = Color.White,
                                        trackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                    Text(
                                        text = "${remainingMinutes}m left",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Actions (Buttons 50% smaller) - Explicit uniform spacing
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            // Play button - custom smaller version
                            val interactionSource = remember { MutableInteractionSource() }
                            val isActuallyFocused by interactionSource.collectIsFocusedAsState()
                            
                            val isResume = progressPercentage != null && progressPercentage > 0f && progressPercentage < 0.9f

                            Surface(
                                onClick = { onPlayClick(if (isResume) progressPosition ?: 0L else 0L) },
                                modifier = Modifier
                                    .height(28.dp)
                                    .focusRequester(playButtonFocusRequester)
                                    .focusable(interactionSource = interactionSource),
                                color = if (isActuallyFocused) Color.White else Color(0xFF8B5CF6),
                                contentColor = if (isActuallyFocused) Color.Black else Color.White,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = if (isResume) "RESUME" else "PLAY",
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Restart Button (Square, same height as Play)
                            if (isResume) {

                                val restartInteractionSource = remember { MutableInteractionSource() }
                                val isRestartFocused by restartInteractionSource.collectIsFocusedAsState()

                                Surface(
                                    onClick = { onPlayClick(0L) },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .focusable(interactionSource = restartInteractionSource),
                                    color = if (isRestartFocused) Color.White else Color.White.copy(alpha = 0.1f),
                                    contentColor = if (isRestartFocused) Color.Black else Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (!isRestartFocused) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Refresh, "Restart", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            // Analysis Toggle Button
                            val (lvl, _, _) = com.purestream.utils.LevelCalculator.calculateLevel(currentProfile?.totalFilteredWordsCount ?: 0)
                            if (isPremium || isDemoMode || lvl >= 10) {
                                val analysisInteractionSource = remember { MutableInteractionSource() }
                                val isAnalysisFocused by analysisInteractionSource.collectIsFocusedAsState()

                                Surface(
                                    onClick = {
                                        if (canAnalyzeProfanity && subtitleAnalysisResult == null && !isAnalyzingSubtitles) {
                                            onAnalyzeProfanityClick?.invoke(movie)
                                        }
                                        isAnalysisExpanded = !isAnalysisExpanded
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .focusable(interactionSource = analysisInteractionSource),
                                    color = if (isAnalysisExpanded) Color(0xFF8B5CF6) 
                                           else if (isAnalysisFocused) Color.White 
                                           else Color.White.copy(alpha = 0.1f),
                                    contentColor = if (isAnalysisExpanded) Color.White 
                                                   else if (isAnalysisFocused) Color.Black 
                                                   else Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (!isAnalysisExpanded && !isAnalysisFocused) BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)) else null
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isAnalyzingSubtitles) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                color = if (isAnalysisFocused) Color.Black else Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(Icons.Default.Psychology, "Content Analysis", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                
                                // Expanded Analysis Tile
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isAnalysisExpanded,
                                    enter = androidx.compose.animation.expandHorizontally(expandFrom = Alignment.Start) + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkHorizontally(shrinkTowards = Alignment.Start) + androidx.compose.animation.fadeOut()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(480.dp)
                                            .background(Color(0xFF1A1C2E).copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                                            .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                            .padding(16.dp)
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Psychology, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        "CONTENT ANALYSIS",
                                                        fontWeight = FontWeight.Black,
                                                        letterSpacing = 1.sp,
                                                        fontSize = 12.sp,
                                                        color = Color.White
                                                    )
                                                }
                                                if (isAnalyzingSubtitles) {
                                                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                                }
                                            }

                                            subtitleAnalysisResult?.let {
                                                SubtitleAnalysisCard(it, currentProfile?.profanityFilterLevel ?: ProfanityFilterLevel.MODERATE)
                                            } ?: run {
                                                if (!isAnalyzingSubtitles) {
                                                    Text("Click to start analysis", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                                                } else {
                                                    Text("Analyzing...", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Profanity Level (if known)
                        if (movie.profanityLevel != ProfanityLevel.UNKNOWN && !isDemoMode) {
                            ProfanityLevelCard(movie.profanityLevel ?: ProfanityLevel.UNKNOWN)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataBadge(text: String, backgroundColor: Color) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
    }
}

@Composable
private fun ActionPlayButton(
    text: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isActuallyFocused by interactionSource.collectIsFocusedAsState()
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(56.dp)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource),
        color = if (isActuallyFocused) Color.White else Color(0xFF8B5CF6),
        contentColor = if (isActuallyFocused) Color.Black else Color.White,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Text(text, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(Color(0xFF1A1C2E).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        content()
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
    // Analysis Details - No card wrapper, no header (already shown above)
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
                    fontSize = 12.sp,
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
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (isMobile) {
                        // Mobile: 3x3 grid layout (3 words per row, 3 rows, +more for 9th+ words)
                        val maxWordsToShow =
                            if (analysisResult.detectedWords.size > 8) 8 else analysisResult.detectedWords.size
                        val censoredWords =
                            analysisResult.detectedWords.take(maxWordsToShow).map { word ->
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
                            val wordWidth =
                                word.length * averageCharWidth + 32 // padding + background
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
                                                analysisResult.detectedWords.getOrNull(
                                                    originalWordIndex
                                                ) ?: word
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
                        // TV: 4 words per line, 3 rows max
                        val wordsPerRow = 4
                        val maxLines = 3
                        val maxWordsToShow = wordsPerRow * maxLines // 12
                        
                        val totalWords = analysisResult.detectedWords.size
                        val showMore = totalWords > maxWordsToShow
                        
                        val wordsToDisplay = if (showMore) {
                            analysisResult.detectedWords.take(maxWordsToShow - 1)
                        } else {
                            analysisResult.detectedWords
                        }
                        
                        val chunks = wordsToDisplay.map { censorWord(it) }.chunked(wordsPerRow)
                        
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            chunks.forEachIndexed { rowIndex, rowWords ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    rowWords.forEachIndexed { wordIndex, censoredWord ->
                                        // Re-calculate original index to get color correctly
                                        val originalIndex = rowIndex * wordsPerRow + wordIndex
                                        val originalWord = analysisResult.detectedWords[originalIndex]
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
                                                fontWeight = FontWeight.Medium,
                                                softWrap = false,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    
                                    // Add +More bubble at the end of the last row if needed
                                    if (showMore && rowIndex == chunks.size - 1) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = Color(0xFF6B7280).copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "+${totalWords - (maxWordsToShow - 1)} more",
                                                fontSize = 12.sp,
                                                color = Color(0xFFB3B3B3),
                                                fontWeight = FontWeight.Medium,
                                                softWrap = false,
                                                maxLines = 1
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
            fontSize = 12.sp,
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

