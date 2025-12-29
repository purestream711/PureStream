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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
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
    onPlayFeatured: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val isMobile = rememberIsMobile()

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

    Box(modifier = Modifier.fillMaxSize()) {
        // Conditional background based on loading state and content availability
        when {
            isLoading || (contentSections.isEmpty() && displayFeaturedContent == null) -> {
                // Simple NetflixDarkGray background while loading
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NetflixDarkGray)
                )
            }
            else -> {
                // Use hero image background when content is loaded with Palette extraction
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(heroBackgroundUrl)
                        .allowHardware(false) // Required for Palette API to access bitmap
                        .listener(
                            onSuccess = { _, result ->
                                // Extract palette from loaded image
                                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                                bitmap?.let { bmp ->
                                    val palette = Palette.from(bmp).generate()
                                    val swatch = palette.dominantSwatch
                                    if (swatch != null) {
                                        val rgb = swatch.rgb
                                        val r = (rgb shr 16 and 0xFF) / 255f
                                        val g = (rgb shr 8 and 0xFF) / 255f
                                        val b = (rgb and 0xFF) / 255f
                                        // Calculate luminance (0.0 = black, 1.0 = white)
                                        imageLuminance = 0.299f * r + 0.587f * g + 0.114f * b
                                        android.util.Log.d("HomeScreen", "Image luminance: $imageLuminance")
                                    }
                                }
                            }
                        )
                        .build(),
                    contentDescription = "Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 1.0f // Full visibility, gradients will handle the fade
                )
                
                // Dynamic vertical gradient based on image brightness
                val isBrightImage = imageLuminance > 0.5f
                val gradientAlphas = if (isBrightImage) {
                    // Bright images need heavy scrim for text readability
                    listOf(0.3f, 0.3f, 0.3f, 0.5f, 0.65f, 0.8f, 0.9f, 0.95f)
                } else {
                    // Dark images need subtle scrim
                    listOf(0.02f, 0.02f, 0.02f, 0.05f, 0.15f, 0.4f, 0.6f, 0.7f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = gradientAlphas.map { Color.Black.copy(alpha = it) },
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )
                
                // Dynamic horizontal gradient for sidebar protection
                val sidebarAlpha = if (isBrightImage) 0.8f else 0.7f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = sidebarAlpha), // Sidebar protection
                                    Color.Black.copy(alpha = 0.1f),         // Quick fade
                                    Color.Transparent,                        // Clear center
                                    Color.Transparent
                                ),
                                startX = 0f,
                                endX = 200f  // Narrow sidebar protection
                            )
                        )
                )
            }
        }
        
        
        // Content over background
        if (isMobile) {
            // Mobile: Use Box to overlay bottom nav on top of content
            Box(modifier = Modifier.fillMaxSize()) {

                // Main content - fills entire screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .verticalScroll(scrollState)
                ) {
                    // Mobile content goes here - will copy from TV layout
                    // Hero Section - Only show when we have content
                    displayFeaturedContent?.let { featuredContent ->
                        SimpleHeroSection(
                            featuredContent = featuredContent,
                            backgroundImageUrl = heroBackgroundUrl,
                            onPlayClick = onPlayFeatured,
                            playButtonFocusRequester = heroPlayButtonFocusRequester,
                            firstMovieFocusRequester = firstMovieFocusRequester,
                            sidebarFocusRequester = sidebarFocusRequester,
                            scrollState = scrollState
                        )
                    }
                    
                    // Content Sections with loading and error states (mobile)
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFFF5B800)
                                )
                            }
                        }
                        error != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Error loading content",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = error,
                                        color = Color(0xFFB3B3B3),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = onRetry,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFF5B800),
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                        else -> {
                            // Content sections for mobile
                            contentSections.forEachIndexed { index, section ->
                                ContentSectionRow(
                                    section = section,
                                    onContentClick = onContentClick,
                                    heroFocusRequester = if (index == 0) heroPlayButtonFocusRequester else null,
                                    firstMovieFocusRequester = if (index == 0) firstMovieFocusRequester else null,
                                    isFirstSection = index == 0
                                )
                            }

                            // Level-Up Tracker
                            if (currentProfile != null) {
                                LevelUpTrackerCard(
                                    currentProfile = currentProfile,
                                    modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
                                )
                            }

                            // Fixed bottom padding for bottom nav overlay
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }

                // Bottom Navigation (Overlay) - animated visibility
                androidx.compose.animation.AnimatedVisibility(
                    visible = isBottomNavVisible,
                    enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) +
                            androidx.compose.animation.slideInVertically(
                                animationSpec = androidx.compose.animation.core.tween(300),
                                initialOffsetY = { it }
                            ),
                    exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) +
                           androidx.compose.animation.slideOutVertically(
                               animationSpec = androidx.compose.animation.core.tween(300),
                               targetOffsetY = { it }
                           ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    BottomNavigation(
                        currentProfile = currentProfile,
                        onHomeClick = { /* Already on home */ },
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
            // TV: Horizontal layout (existing)
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Sidebar - Fixed width with transparent background
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .fillMaxHeight()
                        .background(Color.Transparent)
                ) {
                    LeftSidebar(
                        currentProfile = currentProfile,
                        onHomeClick = { /* Already on home */ },
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
                
                // Main Content Area
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .padding(start = 8.dp)
                        .verticalScroll(scrollState)
                ) {
                    // Hero Section - Only show when we have content
                    displayFeaturedContent?.let { featuredContent ->
                        SimpleHeroSection(
                            featuredContent = featuredContent,
                            backgroundImageUrl = heroBackgroundUrl,
                            onPlayClick = onPlayFeatured,
                            playButtonFocusRequester = heroPlayButtonFocusRequester,
                            firstMovieFocusRequester = firstMovieFocusRequester,
                            sidebarFocusRequester = sidebarFocusRequester,
                            scrollState = scrollState
                        )
                    }
            
            // Content Sections with loading and error states (no additional background)
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFF5B800)
                        )
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Error loading content",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = error,
                                color = Color(0xFFB3B3B3),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF5B800),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                contentSections.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No Plex Libraries Selected",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "To view content, please select libraries in your profile settings.",
                                fontSize = 14.sp,
                                color = Color(0xFFB3B3B3),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
                else -> {
                    // Content Sections
                    contentSections.forEachIndexed { index, section ->
                        ContentSectionRow(
                            section = section,
                            onContentClick = onContentClick,
                            heroFocusRequester = if (index == 0) heroPlayButtonFocusRequester else null,
                            firstMovieFocusRequester = if (index == 0) firstMovieFocusRequester else null,
                            isFirstSection = index == 0
                        )
                    }
                }
            }

                    // Level-Up Tracker
                    if (currentProfile != null) {
                        LevelUpTrackerCard(
                            currentProfile = currentProfile,
                            modifier = Modifier.padding(top = 24.dp, start = 48.dp, end = 48.dp)
                        )
                    }

                    // Bottom spacing
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimpleHeroSection(
    featuredContent: ContentItem,
    backgroundImageUrl: String,
    onPlayClick: () -> Unit,
    playButtonFocusRequester: FocusRequester,
    firstMovieFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(top = 16.dp)
    ) {
        
        // Create an InteractionSource to track the Column's focus state
        val interactionSource = remember { MutableInteractionSource() }

        // Subscribe to the "IsFocused" state directly - guarantees recomposition when focus changes
        val isFocused by interactionSource.collectIsFocusedAsState()

        val coroutineScope = rememberCoroutineScope()

        // Apply scroll to top when hero section gains focus
        LaunchedEffect(isFocused) {
            if (isFocused) {
                coroutineScope.launch {
                    // Scroll to absolute top (0) to show entire hero section
                    scrollState.animateScrollTo(0)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                .width(700.dp)
                .focusRequester(playButtonFocusRequester)
                .focusable(interactionSource = interactionSource)  // Pass interactionSource to focusable
                .onKeyEvent { keyEvent ->
                    android.util.Log.d("HeroSection", "Key event received: ${keyEvent.key}, type: ${keyEvent.type}")
                    when (keyEvent.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.DirectionCenter -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                android.util.Log.d("HeroSection", "Play key pressed, calling onPlayClick()")
                                // Emit a "Press" interaction for visual feedback
                                coroutineScope.launch {
                                    val press = PressInteraction.Press(androidx.compose.ui.geometry.Offset.Zero)
                                    interactionSource.emit(press)
                                    interactionSource.emit(PressInteraction.Release(press))
                                }
                                onPlayClick()
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                android.util.Log.d("HeroSection", "Down key pressed, moving to first movie")
                                firstMovieFocusRequester.requestFocus()
                                true
                            } else false
                        }
                        else -> {
                            android.util.Log.d("HeroSection", "Unhandled key: ${keyEvent.key}")
                            false
                        }
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,  // Disable default ripple for custom styling
                    onClick = onPlayClick
                )
                .focusProperties {
                    left = sidebarFocusRequester
                }
                .tvCardFocusIndicator(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            Text(
                text = featuredContent.title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Movie Info Row (Year, Duration, Rating)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Year
                featuredContent.year?.let { year ->
                    if (year > 0) {
                        Text(
                            text = year.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                // Duration
                featuredContent.duration?.let { duration ->
                    if (duration > 0) {
                        Text(
                            text = formatDuration(duration),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
                
                // Rating Badge - Show actual content rating if available
                featuredContent.contentRating?.let { contentRating ->
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF374151),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = contentRating,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                // Star Rating
                featuredContent.rating?.let { rating ->
                    if (rating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "★",
                                fontSize = 14.sp,
                                color = Color(0xFFFBBF24)
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            
            // Description
            featuredContent.summary?.let { summary ->
                Text(
                    text = summary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Action button - Visual only (Column handles all interaction)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                // REPLACED Button WITH Surface to avoid focus competition
                // Column handles clicks/keys, this is just a visual indicator
                Surface(
                    color = if (isFocused) Color(0xFFF5B800) else Color(0xFF8B5CF6),
                    contentColor = if (isFocused) Color.Black else Color.White,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Play",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContentSectionRow(
    section: ContentSection,
    onContentClick: (ContentItem) -> Unit,
    heroFocusRequester: FocusRequester? = null,
    firstMovieFocusRequester: FocusRequester? = null,
    isFirstSection: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Section Title
        Text(
            text = section.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        
        // Content Row
        LazyRow(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .then(
                    if (isFirstSection && heroFocusRequester != null) {
                        Modifier.focusProperties {
                            up = heroFocusRequester
                        }
                    } else {
                        Modifier
                    }
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(section.items) { index, content ->
                SimpleContentCard(
                    content = content,
                    onClick = { onContentClick(content) },
                    modifier = if (isFirstSection && index == 0 && firstMovieFocusRequester != null) {
                        Modifier.focusRequester(firstMovieFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@Composable
fun SimpleContentCard(
    content: ContentItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = 150.dp
    val cardHeight = 200.dp
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    Card(
        modifier = modifier
            .size(cardWidth, cardHeight)
            .animatedPosterBorder(interactionSource = interactionSource)
            .tvCardFocusIndicator()
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused
                
                // Play sound when gaining focus (not when losing focus)
                if (!wasFocused && focusState.isFocused) {
                    android.util.Log.d("HomeScreen", "Content card gained focus - playing MOVE sound")
                    soundManager.playSound(SoundManager.Sound.MOVE)
                }
            }
            .clickable { 
                android.util.Log.d("HomeScreen", "Content card clicked - playing CLICK sound")
                soundManager.playSound(SoundManager.Sound.CLICK)
                onClick() 
            },
        colors = CardDefaults.cardColors(
            containerColor = BackgroundCard
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 4.dp
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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