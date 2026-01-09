package com.purestream.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import android.view.KeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.PressInteraction
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.palette.graphics.Palette
import android.graphics.drawable.BitmapDrawable
import com.purestream.data.model.*
import com.purestream.ui.components.LeftSidebar
import com.purestream.ui.components.BottomNavigation
import com.purestream.ui.components.HeroSection
import com.purestream.ui.components.LevelUpTrackerCard
import com.purestream.ui.theme.*
import com.purestream.utils.rememberIsMobile
import com.purestream.ui.theme.tvCardFocusIndicator
import com.purestream.ui.theme.tvButtonFocus
import com.purestream.ui.theme.animatedPosterBorder
import androidx.compose.ui.platform.LocalContext
import com.purestream.utils.SoundManager
import com.purestream.ui.theme.getAnimatedButtonBackgroundColor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Focus state management
data class NavigationState(
    val focusedSection: FocusableSection = FocusableSection.HERO,
    val shouldRestoreFocus: Boolean = false
) {
    enum class FocusableSection {
        SIDEBAR, HERO, CONTENT
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    currentProfile: Profile?,
    contentSections: List<ContentSection> = emptyList(),
    featuredContent: ContentItem? = null,
    isLoading: Boolean = false,
    error: String? = null,
    onSearchClick: () -> Unit,
    onContentClick: (ContentItem) -> Unit,
    onSwitchUser: () -> Unit,
    onSettings: () -> Unit,
    onMoviesClick: () -> Unit,
    onTvShowsClick: () -> Unit,
    onPlayFeatured: (Long) -> Unit = {},
    featuredContentProgress: Float? = null,
    featuredContentPosition: Long? = null,
    canAnalyzeProfanity: Boolean = true,
    onRetry: () -> Unit = {},
    onNavigateToLevelUpStats: () -> Unit = {}
) {
    val isMobile = rememberIsMobile()
    val context = LocalContext.current
    
    // Animation state
    var contentVisible by remember { mutableStateOf(false) }
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "content_alpha"
    )
    val contentScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0.98f,
        animationSpec = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "content_scale"
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        contentVisible = true
    }

    // Bottom navigation visibility for mobile (auto-hide on scroll)
    var isBottomNavVisible by remember { mutableStateOf(true) }

    // Use featured content from "Recommended for You" section, fallback to any section
    val displayFeaturedContent = featuredContent ?:
        contentSections.find { it.title == "Recommended for You" }?.items?.firstOrNull() ?:
        contentSections.firstOrNull()?.items?.firstOrNull()
    val heroBackgroundUrl = displayFeaturedContent?.artUrl ?: "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=1920&h=1080&fit=crop"

    // State to track image luminance for dynamic gradient adjustment
    var imageLuminance by remember { mutableStateOf(0.5f) } // Default to medium brightness

    // State for dynamic colors extracted from the hero image
    val dynamicColors = remember(displayFeaturedContent?.id) {
        // Simple color scheme rotation based on content ID hash
        val colorSchemes = listOf(
            listOf(Color(0xFF1A1A2E), Color(0xFF16213E)), // Dark blue scheme
            listOf(Color(0xFF2E1A1A), Color(0xFF3E1616)), // Dark red scheme  
            listOf(Color(0xFF1A2E1A), Color(0xFF163E16)), // Dark green scheme
            listOf(Color(0xFF2E2A1A), Color(0xFF3E3616)), // Dark orange scheme
            listOf(Color(0xFF2E1A2E), Color(0xFF3E163E)), // Dark purple scheme
        )
        val index = Math.abs((displayFeaturedContent?.id ?: "default").hashCode()) % colorSchemes.size
        colorSchemes[index]
    }
    
    val scrollState = rememberScrollState()

    // Scroll detection for auto-hiding bottom nav (mobile only)
    if (isMobile) {
        LaunchedEffect(scrollState) {
            var previousScrollOffset = scrollState.value

            snapshotFlow { scrollState.value }
                .collect { currentOffset ->
                    val isScrollingDown = currentOffset > previousScrollOffset

                    // Show nav when at top, hide when scrolling down, show when scrolling up
                    isBottomNavVisible = when {
                        currentOffset < 100 -> true  // Always show at top
                        isScrollingDown -> false  // Hide when scrolling down
                        else -> true  // Show when scrolling up
                    }

                    previousScrollOffset = currentOffset
                }
        }
    }

    val heroPlayButtonFocusRequester = remember { FocusRequester() }
    val firstMovieFocusRequester = remember { FocusRequester() }
    val sidebarFocusRequester = remember { FocusRequester() }

    // Set initial focus and scroll to top
    LaunchedEffect(displayFeaturedContent) {
        scrollState.scrollTo(0)
        // Only request focus on hero button if we have featured content (hero section exists)
        if (displayFeaturedContent != null) {
            heroPlayButtonFocusRequester.requestFocus()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Background Image with Palette extraction
        if (!isLoading && (contentSections.isNotEmpty() || displayFeaturedContent != null)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(heroBackgroundUrl)
                    .allowHardware(false) 
                    .listener(
                        onSuccess = { _, result ->
                            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                            bitmap?.let { bmp ->
                                val palette = Palette.from(bmp).generate()
                                val swatch = palette.dominantSwatch
                                if (swatch != null) {
                                    val rgb = swatch.rgb
                                    val r = (rgb shr 16 and 0xFF) / 255f
                                    val g = (rgb shr 8 and 0xFF) / 255f
                                    val b = (rgb and 0xFF) / 255f
                                    imageLuminance = 0.299f * r + 0.587f * g + 0.114f * b
                                }
                            }
                        }
                    )
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.6f 
            )
            
            // Rich Overlay Gradients
            val isBrightImage = imageLuminance > 0.5f
            val gradientAlphas = if (isBrightImage) {
                listOf(0.4f, 0.5f, 0.7f, 0.85f, 1.0f)
            } else {
                listOf(0.1f, 0.2f, 0.4f, 0.7f, 1.0f)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = gradientAlphas.map { Color.Black.copy(alpha = it) }
                        )
                    )
            )
        }
        
        // Main Content Layer
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
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        displayFeaturedContent?.let { featuredContent ->
                            SimpleHeroSection(
                                featuredContent = featuredContent,
                                backgroundImageUrl = heroBackgroundUrl,
                                onPlayClick = onPlayFeatured,
                                progress = featuredContentProgress,
                                position = featuredContentPosition,
                                canAnalyzeProfanity = canAnalyzeProfanity,
                                playButtonFocusRequester = heroPlayButtonFocusRequester,
                                firstMovieFocusRequester = firstMovieFocusRequester,
                                sidebarFocusRequester = sidebarFocusRequester,
                                scrollState = scrollState
                            )
                        }
                        
                        ContentStateLayer(
                            isLoading = isLoading,
                            error = error,
                            contentSections = contentSections,
                            onRetry = onRetry,
                            onContentClick = onContentClick,
                            heroFocusRequester = heroPlayButtonFocusRequester,
                            firstMovieFocusRequester = firstMovieFocusRequester,
                            isMobile = true,
                            currentProfile = currentProfile,
                            onNavigateToLevelUpStats = onNavigateToLevelUpStats
                        )
                        
                        Spacer(modifier = Modifier.height(100.dp))
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isBottomNavVisible,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        BottomNavigation(
                            currentProfile = currentProfile,
                            onHomeClick = { },
                            onSearchClick = onSearchClick,
                            onMoviesClick = onMoviesClick,
                            onTvShowsClick = onTvShowsClick,
                            onSettingsClick = onSettings,
                            onProfileClick = onSwitchUser,
                            currentSection = "home"
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.width(80.dp).fillMaxHeight()) {
                        LeftSidebar(
                            currentProfile = currentProfile,
                            onHomeClick = { },
                            onSearchClick = onSearchClick,
                            onMoviesClick = onMoviesClick,
                            onTvShowsClick = onTvShowsClick,
                            onSettingsClick = onSettings,
                            onProfileClick = onSwitchUser,
                            sidebarFocusRequester = sidebarFocusRequester,
                            heroPlayButtonFocusRequester = heroPlayButtonFocusRequester,
                            currentSection = "home",
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        displayFeaturedContent?.let { featuredContent ->
                            SimpleHeroSection(
                                featuredContent = featuredContent,
                                backgroundImageUrl = heroBackgroundUrl,
                                onPlayClick = onPlayFeatured,
                                progress = featuredContentProgress,
                                position = featuredContentPosition,
                                canAnalyzeProfanity = canAnalyzeProfanity,
                                playButtonFocusRequester = heroPlayButtonFocusRequester,
                                firstMovieFocusRequester = firstMovieFocusRequester,
                                sidebarFocusRequester = sidebarFocusRequester,
                                scrollState = scrollState
                            )
                        }
                
                        ContentStateLayer(
                            isLoading = isLoading,
                            error = error,
                            contentSections = contentSections,
                            onRetry = onRetry,
                            onContentClick = onContentClick,
                            heroFocusRequester = heroPlayButtonFocusRequester,
                            firstMovieFocusRequester = firstMovieFocusRequester,
                            isMobile = false,
                            currentProfile = currentProfile,
                            onNavigateToLevelUpStats = onNavigateToLevelUpStats
                        )

                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentStateLayer(
    isLoading: Boolean,
    error: String?,
    contentSections: List<ContentSection>,
    onRetry: () -> Unit,
    onContentClick: (ContentItem) -> Unit,
    heroFocusRequester: FocusRequester,
    firstMovieFocusRequester: FocusRequester,
    isMobile: Boolean,
    currentProfile: Profile?,
    onNavigateToLevelUpStats: () -> Unit
) {
    when {
        isLoading -> {
            Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF8B5CF6))
            }
        }
        error != null -> {
            Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Oops! Something went wrong", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))) {
                        Text("Retry")
                    }
                }
            }
        }
        else -> {
            contentSections.forEachIndexed { index, section ->
                ContentSectionRow(
                    section = section,
                    onContentClick = onContentClick,
                    heroFocusRequester = if (index == 0) heroFocusRequester else null,
                    firstMovieFocusRequester = if (index == 0) firstMovieFocusRequester else null,
                    isFirstSection = index == 0,
                    delayIndex = index
                )
            }

            if (currentProfile != null) {
                InteractiveLevelUpCard(
                    currentProfile = currentProfile,
                    onLevelClick = onNavigateToLevelUpStats,
                    modifier = Modifier.padding(
                        top = 32.dp, 
                        start = if (isMobile) 16.dp else 32.dp, 
                        end = if (isMobile) 16.dp else 32.dp
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimpleHeroSection(
    featuredContent: ContentItem,
    backgroundImageUrl: String,
    onPlayClick: (Long) -> Unit,
    progress: Float? = null,
    position: Long? = null,
    canAnalyzeProfanity: Boolean = true,
    playButtonFocusRequester: FocusRequester,
    firstMovieFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
            val isMobile = rememberIsMobile()
            
            val playInteractionSource = remember { MutableInteractionSource() }
            val isPlayFocused by playInteractionSource.collectIsFocusedAsState()
            
            val restartInteractionSource = remember { MutableInteractionSource() }
            val isRestartFocused by restartInteractionSource.collectIsFocusedAsState()
            
            val coroutineScope = rememberCoroutineScope()
            val context = androidx.compose.ui.platform.LocalContext.current
            val soundManager = remember { com.purestream.utils.SoundManager.getInstance(context) }
        
            LaunchedEffect(isPlayFocused, isRestartFocused) {
                if (isPlayFocused || isRestartFocused) {
                    coroutineScope.launch { scrollState.animateScrollTo(0) }
                }
            }

            // Ensure focus is on Play button initially or when progress clears
            LaunchedEffect(progress) {
                if (progress == null || progress <= 0f) {
                    try {
                        playButtonFocusRequester.requestFocus()
                    } catch (e: Exception) {
                        // Focus requester might not be attached yet
                    }
                }
            }
        
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(if (isMobile) 420.dp else 340.dp)
                    .padding(top = if (isMobile) 0.dp else 32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 40.dp)
                        .then(
                            if (isMobile) Modifier.fillMaxWidth() 
                            else Modifier.padding(start = 32.dp).widthIn(max = 700.dp)
                        )
                        .clickable(
                            enabled = isMobile, // Disable on TV to prevent it from stealing focus (Timbuktu issue)
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                soundManager.playSound(com.purestream.utils.SoundManager.Sound.CLICK)
                                val startPos = if (progress != null && progress > 0f) position ?: 0L else 0L
                                onPlayClick(startPos)
                            }
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // ... (rest of the column content remains the same until buttons)
                    // (I will include the title and meta row to ensure correct replacement)
                    if (featuredContent.logoUrl != null) {
                        if (isMobile) {
                            // Mobile: Center the clearlogo
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = featuredContent.logoUrl,
                                    contentDescription = featuredContent.title,
                                    modifier = Modifier
                                        .height(100.dp)
                                        .widthIn(max = 400.dp),
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.Center
                                )
                            }
                        } else {
                            // TV: Keep clearlogo left-aligned
                            AsyncImage(
                                model = featuredContent.logoUrl,
                                contentDescription = featuredContent.title,
                                modifier = Modifier
                                    .height(94.dp)
                                    .widthIn(max = 375.dp),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart
                            )
                        }
                    } else {
                        Text(
                            text = featuredContent.title,
                            modifier = if (isMobile) Modifier.padding(horizontal = 24.dp) else Modifier,
                            fontSize = if (isMobile) 32.sp else 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            lineHeight = 1.1.sp,
                            textAlign = TextAlign.Start
                        )
                    }
        
                    Row(
                        modifier = if (isMobile) Modifier.padding(horizontal = 24.dp) else Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isMobile) 16.dp else 12.dp)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Text(
                                featuredContent.contentRating ?: "NR",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = if (isMobile) 12.sp else 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            "${featuredContent.year}  •  ${formatDuration(featuredContent.duration ?: 0)}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = if (isMobile) 14.sp else 10.5.sp
                        )
        
                        val isProtected = !canAnalyzeProfanity
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (isProtected) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                    RoundedCornerShape(if (isMobile) 8.dp else 6.dp)
                                )
                                .padding(horizontal = if (isMobile) 12.dp else 9.dp, vertical = if (isMobile) 6.dp else 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isProtected) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isProtected) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(if (isMobile) 16.dp else 12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isProtected) "Protected" else "Unprotected",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isMobile) 12.sp else 9.sp
                            )
                        }
                    }
        
                    Text(
                        featuredContent.summary ?: "",
                        modifier = if (isMobile) Modifier.padding(horizontal = 24.dp) else Modifier.fillMaxWidth(0.65f),
                        fontSize = if (isMobile) 16.sp else 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = if (isMobile) 22.sp else 16.sp,
                        textAlign = TextAlign.Start
                    )
                    
                    // Buttons (25% smaller: 36dp height)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp).then(if (isMobile) Modifier.padding(horizontal = 24.dp) else Modifier)
                    ) {
                        // Resume/Play Button
                        Surface(
                            onClick = {
                                soundManager.playSound(com.purestream.utils.SoundManager.Sound.CLICK)
                                val startPos = if (progress != null && progress > 0f) position ?: 0L else 0L
                                onPlayClick(startPos)
                            },
                            modifier = Modifier
                                .height(36.dp)
                                .focusRequester(playButtonFocusRequester)
                                .focusProperties { 
                                    left = sidebarFocusRequester
                                    down = firstMovieFocusRequester
                                },
                            interactionSource = playInteractionSource,
                            color = if (isPlayFocused || isMobile) Color.White else Color(0xFF8B5CF6),
                            contentColor = if (isPlayFocused || isMobile) Color.Black else Color.White,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = if (progress != null && progress > 0f) "RESUME" else "PLAY",
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.75.sp,
                                    fontSize = 13.sp
                                )
                            }
                        }
        
                        // Restart Button (Always present for focus stability)
                        val hasProgress = progress != null && progress > 0f
                        Surface(
                            onClick = { 
                                soundManager.playSound(com.purestream.utils.SoundManager.Sound.CLICK)
                                onPlayClick(0L) 
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer { alpha = if (hasProgress) 1f else 0f }
                                .focusProperties { 
                                    canFocus = hasProgress
                                    down = firstMovieFocusRequester
                                },
                            interactionSource = restartInteractionSource,
                            color = if (isRestartFocused) Color.White else Color.White.copy(alpha = 0.1f),
                            contentColor = if (isRestartFocused) Color.Black else Color.White,
                            shape = RoundedCornerShape(12.dp),
                            border = if (!isRestartFocused && hasProgress) BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Refresh, "Restart", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
}

@Composable
fun ContentSectionRow(
    section: ContentSection,
    onContentClick: (ContentItem) -> Unit,
    heroFocusRequester: FocusRequester? = null,
    firstMovieFocusRequester: FocusRequester? = null,
    isFirstSection: Boolean = false,
    delayIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(800, delayMillis = indexToDelay(delayIndex)),
        label = "row_alpha"
    )
    
    LaunchedEffect(Unit) { visible = true }

    Column(modifier = modifier.graphicsLayer { this.alpha = alpha }.padding(vertical = 16.dp)) {
        Text(
            text = section.title.uppercase(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = if (rememberIsMobile()) 24.dp else 32.dp, bottom = 12.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = if (rememberIsMobile()) 24.dp else 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.then(
                if (isFirstSection && heroFocusRequester != null) {
                    Modifier.focusProperties { up = heroFocusRequester }
                } else Modifier
            )
        ) {
            itemsIndexed(section.items) { index, content ->
                SimpleContentCard(
                    content = content,
                    onClick = { onContentClick(content) },
                    modifier = if (isFirstSection && index == 0 && firstMovieFocusRequester != null) {
                        Modifier.focusRequester(firstMovieFocusRequester)
                    } else Modifier
                )
            }
        }
    }
}

private fun indexToDelay(index: Int): Int = 300 + (index * 150)

@Composable
fun SimpleContentCard(
    content: ContentItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMobile = rememberIsMobile()
    val cardWidth = if (isMobile) 140.dp else 160.dp
    val cardHeight = if (isMobile) 200.dp else 240.dp
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    Card(
        modifier = modifier
            .size(cardWidth, cardHeight)
                .onFocusChanged { focusState ->
                    val wasFocused = isFocused
                    isFocused = focusState.isFocused
                }
            .clickable(interactionSource = interactionSource, indication = null) {
                soundManager.playSound(SoundManager.Sound.CLICK)
                onClick()
            }
            .focusable(interactionSource = interactionSource)
            .hoverable(interactionSource),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (isFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            AsyncImage(
                model = content.thumbUrl,
                contentDescription = content.title,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        content.title,
                        modifier = Modifier.padding(8.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 2, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NetflixContentSectionRow(
    section: ContentSection,
    onContentClick: (ContentItem) -> Unit,
    heroFocusRequester: FocusRequester,
    isFirstSection: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Section Title - Netflix style positioning
        Text(
            text = section.title,
            style = SectionHeaderStyle,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 80.dp, vertical = 12.dp)
        )
        
        // Content Row - Netflix style spacing
        LazyRow(
            contentPadding = PaddingValues(start = 80.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.then(
                if (isFirstSection) {
                    Modifier.focusProperties {
                        up = heroFocusRequester
                    }
                } else {
                    Modifier
                }
            )
        ) {
            items(section.items) { content ->
                NetflixContentCard(
                    content = content,
                    onClick = { onContentClick(content) }
                )
            }
        }
    }
}

@Composable
fun NetflixContentCard(
    content: ContentItem,
    onClick: () -> Unit
) {
    val cardWidth = 180.dp
    val cardHeight = 280.dp
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    Card(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .animatedPosterBorder(interactionSource = interactionSource)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .tvCardFocusIndicator(),
        colors = CardDefaults.cardColors(
            containerColor = BackgroundCard
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Box {
            // Poster Image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixDarkGray)
            ) {
                content.thumbUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = content.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                // Dark overlay for better text contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    BackgroundOverlay
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )
                
                
                // Subtitle Indicator
                if (content.hasSubtitles) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(
                                color = RatingGreen,
                                shape = RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "CC",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
                
                // Content Info Overlay at Bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = content.title,
                        style = ContentCardTitleStyle,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        content.year?.let { year ->
                            Text(
                                text = year.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }
                        
                        content.rating?.let { rating ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "★",
                                    fontSize = 10.sp,
                                    color = AccentGold
                                )
                                Text(
                                    text = String.format("%.1f", rating),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }
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

@Composable
fun InteractiveLevelUpCard(
    currentProfile: Profile,
    onLevelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { 
                if (it.isFocused) {
                        // Optional: 
                        soundManager.playSound(SoundManager.Sound.CLICK)
                }
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    soundManager.playSound(SoundManager.Sound.LEVELUP_CLICK)
                    onLevelClick()
                }
            )
            // Handle D-pad Select
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp && 
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                    soundManager.playSound(SoundManager.Sound.LEVELUP_CLICK)
                    onLevelClick()
                    true
                } else {
                    false
                }
            }
    ) {
        LevelUpTrackerCard(
            currentProfile = currentProfile,
            isFocused = isFocused
        )
    }
}