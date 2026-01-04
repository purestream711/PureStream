package com.purestream.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.purestream.data.model.*
import com.purestream.ui.components.LeftSidebar
import com.purestream.ui.components.BottomNavigation
import com.purestream.ui.components.LibraryDropdown
import com.purestream.ui.theme.*
import com.purestream.ui.theme.tvCardFocusIndicator
import com.purestream.ui.theme.tvButtonFocus
import com.purestream.ui.theme.animatedPosterBorder
import com.purestream.utils.rememberIsMobile
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvShowsScreen(
    currentProfile: Profile?,
    tvShows: List<TvShow>,
    libraries: List<PlexLibrary> = emptyList(),
    selectedLibraryId: String? = null,
    isLoading: Boolean,
    error: String?,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    lastFocusedTvShowId: String? = null,
    onSearchClick: () -> Unit,
    onHomeClick: () -> Unit,
    onMoviesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchUser: () -> Unit,
    onTvShowClick: (TvShow) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLibrarySelected: (String) -> Unit = {},
    isLoadingMore: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isMobile = rememberIsMobile()
    
    // Animation state
    var contentVisible by remember { mutableStateOf(false) }
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(800),
        label = "content_alpha"
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        contentVisible = true
    }

    // Bottom navigation visibility for mobile (auto-hide on scroll)
    var isBottomNavVisible by remember { mutableStateOf(true) }

    // Scroll detection for auto-hiding bottom nav (mobile only)
    if (isMobile) {
        LaunchedEffect(gridState) {
            var previousFirstVisibleItemIndex = gridState.firstVisibleItemIndex
            var previousFirstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset

            snapshotFlow {
                Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
            }.collect { (currentIndex, currentOffset) ->
                val isScrollingDown = when {
                    currentIndex > previousFirstVisibleItemIndex -> true
                    currentIndex < previousFirstVisibleItemIndex -> false
                    else -> currentOffset > previousFirstVisibleItemScrollOffset
                }
                isBottomNavVisible = when {
                    currentIndex == 0 && currentOffset < 100 -> true
                    isScrollingDown -> false
                    else -> true
                }
                previousFirstVisibleItemIndex = currentIndex
                previousFirstVisibleItemScrollOffset = currentOffset
            }
        }
    }

    // Focus management (TV only)
    val sidebarFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }
    val dropdownFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }
    val restorationFocusRequester = remember { FocusRequester() }

    // Auto-focus logic (TV only)
    LaunchedEffect(tvShows, isLoading, isMobile, libraries.size, lastFocusedTvShowId) {
        if (!isMobile && !isLoading && tvShows.isNotEmpty()) {
            if (lastFocusedTvShowId != null) {
                // Restore focus to last selected item
                val index = tvShows.indexOfFirst { it.id == lastFocusedTvShowId }
                if (index != -1) {
                    try {
                        gridState.scrollToItem(index)
                        kotlinx.coroutines.delay(300)
                        restorationFocusRequester.requestFocus()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            } else if (libraries.size <= 1) {
                // Default to first item if no restoration target and no dropdown
                try {
                    kotlinx.coroutines.delay(300)
                    firstItemFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color.Black)
                )
            )
    ) {
        // Background Glow
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "glow")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.2f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(6000),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "glow_alpha"
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF8B5CF6).copy(alpha = glowAlpha), Color.Transparent),
                    radius = 1500f
                )
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha }
        ) {
            if (isMobile) {
                // Mobile Layout
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (libraries.size > 1) {
                            LibraryDropdown(
                                libraries = libraries,
                                selectedLibraryId = selectedLibraryId,
                                onLibrarySelected = onLibrarySelected,
                                isMobile = true,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 8.dp)
                            )
                        }

                        TvGridContent(
                            isLoading = isLoading,
                            error = error,
                            tvShows = tvShows,
                            gridState = gridState,
                            isLoadingMore = isLoadingMore,
                            onLoadMore = onLoadMore,
                            onTvShowClick = onTvShowClick,
                            onRetry = onRetry,
                            isMobile = true,
                            columns = 3
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isBottomNavVisible,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        BottomNavigation(
                            currentProfile = currentProfile,
                            onHomeClick = onHomeClick,
                            onSearchClick = onSearchClick,
                            onMoviesClick = onMoviesClick,
                            onTvShowsClick = { },
                            onSettingsClick = onSettingsClick,
                            onProfileClick = onSwitchUser,
                            currentSection = "tv_shows"
                        )
                    }
                }
            } else {
                // TV Layout
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.width(80.dp).fillMaxHeight()) {
                        LeftSidebar(
                            currentProfile = currentProfile,
                            onHomeClick = onHomeClick,
                            onSearchClick = onSearchClick,
                            onMoviesClick = onMoviesClick,
                            onTvShowsClick = { },
                            onSettingsClick = onSettingsClick,
                            onProfileClick = onSwitchUser,
                            sidebarFocusRequester = sidebarFocusRequester,
                            heroPlayButtonFocusRequester = gridFocusRequester,
                            currentSection = "tv_shows",
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (libraries.size > 1) {
                            LibraryDropdown(
                                libraries = libraries,
                                selectedLibraryId = selectedLibraryId,
                                onLibrarySelected = onLibrarySelected,
                                isMobile = false,
                                dropdownFocusRequester = dropdownFocusRequester,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                            )
                        }

                        TvGridContent(
                            isLoading = isLoading,
                            error = error,
                            tvShows = tvShows,
                            gridState = gridState,
                            isLoadingMore = isLoadingMore,
                            onLoadMore = onLoadMore,
                            onTvShowClick = onTvShowClick,
                            onRetry = onRetry,
                            isMobile = false,
                            columns = 6,
                            gridFocusRequester = gridFocusRequester,
                            sidebarFocusRequester = sidebarFocusRequester,
                            dropdownFocusRequester = dropdownFocusRequester,
                            firstItemFocusRequester = firstItemFocusRequester,
                            restorationFocusRequester = restorationFocusRequester,
                            lastFocusedTvShowId = lastFocusedTvShowId,
                            librariesCount = libraries.size
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvGridContent(
    isLoading: Boolean,
    error: String?,
    tvShows: List<TvShow>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onTvShowClick: (TvShow) -> Unit,
    onRetry: () -> Unit,
    isMobile: Boolean,
    columns: Int,
    gridFocusRequester: FocusRequester? = null,
    sidebarFocusRequester: FocusRequester? = null,
    dropdownFocusRequester: FocusRequester? = null,
    firstItemFocusRequester: FocusRequester? = null,
    restorationFocusRequester: FocusRequester? = null,
    lastFocusedTvShowId: String? = null,
    librariesCount: Int = 0
) {
    when {
        isLoading -> LoadingContent(modifier = Modifier.fillMaxSize())
        error != null -> {
            if (error.contains("No TV Show Library Available")) NoLibrariesSelectedContent(error, Modifier.fillMaxSize())
            else ErrorContent(error, onRetry, Modifier.fillMaxSize())
        }
        tvShows.isEmpty() -> EmptyContent("No TV shows found", Modifier.fillMaxSize())
        else -> {
            // Infinite scroll
            LaunchedEffect(gridState) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo }.collect { visibleItems ->
                    if (!isLoadingMore && visibleItems.isNotEmpty()) {
                        if (visibleItems.last().index >= gridState.layoutInfo.totalItemsCount - 10) onLoadMore()
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(
                    start = if (isMobile) 16.dp else 32.dp,
                    end = if (isMobile) 16.dp else 32.dp,
                    top = if (isMobile || librariesCount > 1) 8.dp else 32.dp,
                    bottom = 100.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isMobile && gridFocusRequester != null) {
                        Modifier.focusRequester(gridFocusRequester).focusProperties {
                            left = sidebarFocusRequester ?: FocusRequester.Default
                            if (librariesCount > 1) up = dropdownFocusRequester ?: FocusRequester.Default
                        }
                    } else Modifier)
            ) {
                items(tvShows.size) { index ->
                    val tvShow = tvShows[index]
                    val isRestorationTarget = !isMobile && lastFocusedTvShowId != null && tvShow.id == lastFocusedTvShowId
                    val isFirstItem = !isMobile && index == 0 && lastFocusedTvShowId == null
                    
                    val itemFocusRequester = when {
                        isRestorationTarget -> restorationFocusRequester
                        isFirstItem -> firstItemFocusRequester
                        else -> null
                    }

                    TvShowGridCard(
                        tvShow = tvShow,
                        onClick = { onTvShowClick(tvShow) },
                        isMobile = isMobile,
                        delayIndex = index % (columns * 2),
                        focusRequester = itemFocusRequester
                    )
                }

                if (isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF8B5CF6))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvShowGridCard(
    tvShow: TvShow,
    onClick: () -> Unit,
    isMobile: Boolean,
    delayIndex: Int,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val soundManager = remember { com.purestream.utils.SoundManager.getInstance(context) }
    
    var visible by remember { mutableStateOf(false) }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(600, delayMillis = delayIndex * 80),
        label = "card_entrance"
    )
    LaunchedEffect(Unit) { visible = true }

    Card(
        modifier = Modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer { this.alpha = alpha }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { 
                soundManager.playSound(com.purestream.utils.SoundManager.Sound.CLICK)
                onClick() 
            }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = tvShow.posterUrl,
                contentDescription = tvShow.title,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            
            if (isFocused) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f))
                )
            }

            // Season badge (Glassy)
            tvShow.seasonCount?.let {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Text("${it}S", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun TvShowGridCard(
    tvShow: TvShow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    isMobile: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    Card(
        modifier = modifier
            .aspectRatio(2f / 3f) // Standard TV show poster aspect ratio
            .let { mod -> 
                if (isMobile) {
                    mod.clickable { onClick() }
                } else {
                    mod.let { mod2 ->
                        if (focusRequester != null) {
                            mod2.focusRequester(focusRequester)
                        } else {
                            mod2
                        }
                    }
                    .animatedPosterBorder(interactionSource = interactionSource)
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable { onClick() }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = BackgroundCard
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp // Remove enlargement on focus
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // TV Show Poster
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixDarkGray)
            ) {
                if (tvShow.posterUrl != null) {
                    AsyncImage(
                        model = tvShow.posterUrl,
                        contentDescription = tvShow.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // No poster URL available - show icon
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NetflixDarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "No poster available",
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                // Gradient overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    BackgroundOverlay
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )
                
                
                // Season/Episode count indicator
                tvShow.seasonCount?.let { seasons ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                color = BackgroundOverlay,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${seasons}S",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = NetflixRed,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Loading TV shows from Plex...",
                fontSize = 16.sp,
                color = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = NetflixRed,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Error Loading TV Shows",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = error,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .focusable()
                    .onFocusChanged { isFocused = it.isFocused }
                    .tvButtonFocus(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NetflixRed
                )
            ) {
                Text(
                    text = "Retry",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun EmptyContent(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = "No TV Shows",
                tint = TextSecondary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No TV Shows Found",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = message,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoLibrariesSelectedContent(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
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
                text = "No TV Show Libraries Selected",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = message,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "To view TV shows, please select TV show libraries in your profile settings.",
                fontSize = 14.sp,
                color = Color(0xFFEF4444),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

