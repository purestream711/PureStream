package com.purestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import coil.compose.AsyncImage
import com.purestream.data.model.*
import com.purestream.ui.components.DemoModePlaybackBlockedDialog
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
    isPremium: Boolean = false,
    isDemoMode: Boolean = false
) {
    val isMobile = rememberIsMobile()
    val playButtonFocusRequester = remember { FocusRequester() }
    var showDemoModeDialog by remember { mutableStateOf(false) }
    
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

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        contentVisible = true
        try {
            kotlinx.coroutines.delay(100)
            playButtonFocusRequester.requestFocus()
        } catch (e: Exception) {
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
            .background(Color.Black)
    ) {
        // Background Image with overlay
        showBackgroundUrl?.let { backgroundUrl ->
            AsyncImage(
                model = backgroundUrl,
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
        
        // Deep Overlays
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
                            endX = 1500f
                        )
                    }
                )
        )

        // Pulsating Glow
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
                        radius = 1200f
                    )
                )
        )
        
        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    scaleX = contentScale
                    scaleY = contentScale
                }
        ) {
            if (isMobile) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Hero Section
                    Box(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                         // Image with Fade
                         AsyncImage(
                             model = episode.thumbUrl,
                             contentDescription = null,
                             modifier = Modifier
                                 .fillMaxSize()
                                 .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                 .drawWithContent {
                                     drawContent()
                                     drawRect(
                                         brush = Brush.verticalGradient(
                                             0.0f to Color.Black,
                                             0.6f to Color.Black,
                                             1.0f to Color.Transparent
                                         ),
                                         blendMode = BlendMode.DstIn
                                     )
                                 },
                             contentScale = ContentScale.Crop
                         )
                         
                         // Gradient Overlay
                         Box(
                             modifier = Modifier.fillMaxSize()
                                 .background(Brush.verticalGradient(
                                     colors = listOf(Color.Black.copy(alpha=0.3f), Color.Transparent, Color.Transparent, Color.Transparent)
                                 ))
                         )
                         
                         // Bottom Overlay Content
                         Column(
                             modifier = Modifier.fillMaxSize().padding(24.dp),
                             verticalArrangement = Arrangement.Bottom
                         ) {
                             Text(episode.title, fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White, lineHeight = 34.sp)
                             Spacer(Modifier.height(8.dp))
                             val episodeText = if (episode.seasonNumber == 0) "Specials:E${episode.episodeNumber}" else "S${episode.seasonNumber} E${episode.episodeNumber}"
                             Text("$episodeText  •  ${formatDuration(episode.duration ?: 0)}", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                         }
                    }
                    
                    // Details Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(0.0f to Color.Transparent, 0.4f to Color.Transparent))
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
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

                        // Buttons
                        val isResume = progressPercentage != null && progressPercentage > 0f && progressPercentage < 0.9f
                         Button(
                            onClick = { onPlayClick(if (isResume) progressPosition ?: 0L else 0L) },
                            modifier = Modifier.fillMaxWidth().height(56.dp).focusRequester(playButtonFocusRequester),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isResume) "RESUME" else "WATCH", fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White)
                        }
                        
                        if (isResume) {
                             OutlinedButton(
                                onClick = { onPlayClick(0L) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Refresh, "Restart", tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("RESTART FROM BEGINNING", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(episode.summary ?: "", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, lineHeight = 24.sp)
                        
                        // Analysis Section (Glass Panel Tile)
                        val (lvl, _, _) = com.purestream.utils.LevelCalculator.calculateLevel(currentProfile?.totalFilteredWordsCount ?: 0)
                        if (isPremium || isDemoMode || lvl >= 10) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
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
                                        onClick = { onAnalyzeProfanityClick?.invoke(episode) },
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
                }
            } else {
                // TV Layout
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
                            // Episode Title
                            Text(
                                text = episode.title,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                lineHeight = 32.sp
                            )
                            
                            // Show Info
                            val seasonEpisodeText = if (episode.seasonNumber == 0) "Specials:E${episode.episodeNumber}" else "S${episode.seasonNumber}:E${episode.episodeNumber}"
                             Text(
                                text = "$tvShowTitle  •  $seasonEpisodeText",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Meta Row (Smaller fonts)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${episode.year}  •  ${formatDuration(episode.duration ?: 0)}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                // Rating
                                episode.rating?.let {
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
                            episode.summary?.let { summary ->
                                AutoScrollingText(
                                    text = summary,
                                    maxLines = 3,
                                    fontSize = 14.sp,
                                    color = Color(0xFFE0E0E0),
                                    lineHeight = 20.sp
                                )
                            }
                            
                            // Progress Text (if started)
                            progressPercentage?.let { progress ->
                                if (progress > 0f && progress < 0.90f) {
                                    val totalDurationMs = episode.duration ?: 0L
                                    val remainingMs = ((1f - progress) * totalDurationMs).toLong()
                                    val remainingMinutes = (remainingMs / (1000 * 60)).toInt()

                                    Text(
                                        text = "${remainingMinutes}m left",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Actions (Buttons 50% smaller)
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                val isResume = progressPercentage != null && progressPercentage > 0f && progressPercentage < 0.9f

                                // Play button
                                val interactionSource = remember { MutableInteractionSource() }
                                val isActuallyFocused by interactionSource.collectIsFocusedAsState()

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

                                // Restart Button
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
                                                onAnalyzeProfanityClick?.invoke(episode)
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
                        }
                        
                        // Right Side: Episode Thumbnail (Glassy Card) - Positioned at Top Right
                        Column(
                             modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 48.dp),
                             horizontalAlignment = Alignment.End,
                             verticalArrangement = Arrangement.Top
                        ) {
                             Box(modifier = Modifier.width(400.dp)) {
                                 EpisodeHeaderSection(episode, tvShowTitle, progressPercentage, false)
                             }
                        }
                    }
                }
            }
        }

        if (showDemoModeDialog) {
            DemoModePlaybackBlockedDialog(onDismiss = { showDemoModeDialog = false })
        }
    }
}

@Composable
private fun EpisodeHeaderSection(
    episode: Episode,
    tvShowTitle: String,
    progressPercentage: Float?,
    isMobile: Boolean
) {
    // Glass panel styling for thumbnail
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color(0xFF1A1C2E).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
    ) {
        AsyncImage(
            model = episode.thumbUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        progressPercentage?.let { progress ->
            if (progress < 0.90f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).align(Alignment.BottomCenter),
                    color = Color.White,
                    trackColor = Color.Black.copy(alpha = 0.5f)
                )
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

