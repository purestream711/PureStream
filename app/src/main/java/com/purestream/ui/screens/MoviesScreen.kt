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
import androidx.compose.runtime.snapshotFlow
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
import com.purestream.ui.theme.animatedPosterBorder
import com.purestream.utils.rememberIsMobile
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MoviesScreen(
    currentProfile: Profile?,
    movies: List<Movie>,
    libraries: List<PlexLibrary> = emptyList(),
    selectedLibraryId: String? = null,
    isLoading: Boolean,
    isLoadingMore: Boolean = false,
    canLoadMore: Boolean = true,
    error: String?,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    lastFocusedMovieId: String? = null,
    progressMap: Map<String, Float> = emptyMap(),  // Add progress map parameter
    onSearchClick: () -> Unit,
    onHomeClick: () -> Unit,
    onTvShowsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchUser: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit = {},
    onLibrarySelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isMobile = rememberIsMobile()

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
                // Determine scroll direction
                val isScrollingDown = when {
                    currentIndex > previousFirstVisibleItemIndex -> true
                    currentIndex < previousFirstVisibleItemIndex -> false
                    else -> currentOffset > previousFirstVisibleItemScrollOffset
                }

                // Show nav when at top, hide when scrolling down, show when scrolling up
                isBottomNavVisible = when {
                    currentIndex == 0 && currentOffset < 100 -> true  // Always show at top
                    isScrollingDown -> false  // Hide when scrolling down
                    else -> true  // Show when scrolling up
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixDarkGray)
    ) {

        if (isMobile) {
            // Mobile Layout: Use Box to overlay bottom nav on top of content
            Box(modifier = Modifier.fillMaxSize()) {

                // Main content - fills entire screen
                Column(modifier = Modifier.fillMaxSize()) {

                    // Library Dropdown (animated visibility)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isBottomNavVisible && libraries.size > 1,
                        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) +
                                androidx.compose.animation.slideInVertically(animationSpec = androidx.compose.animation.core.tween(300)),
                        exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) +
                               androidx.compose.animation.slideOutVertically(animationSpec = androidx.compose.animation.core.tween(300))
                    ) {
                        LibraryDropdown(
                            libraries = libraries,
                            selectedLibraryId = selectedLibraryId,
                            onLibrarySelected = onLibrarySelected,
                            isMobile = true,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 8.dp)
                        )
                    }

                    // Content based on state
                    when {
                        isLoading -> {
                            LoadingContent(modifier = Modifier.fillMaxSize())
                        }
                        error != null -> {
                            // Check if this is a "no libraries selected" error vs a genuine error
                            if (error.contains("No Movie Library Available") || error.contains("no movie libraries selected")) {
                                NoLibrariesSelectedContent(
                                    message = error,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                ErrorContent(
                                    error = error,
                                    onRetry = onRetry,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        movies.isEmpty() -> {
                            EmptyContent(
                                message = "No movies found in your Plex library",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                        // Movies Grid - using provided gridState to preserve scroll position
                        
                        // Infinite scroll detection
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.layoutInfo.visibleItemsInfo }
                                .collect { visibleItems ->
                                    if (canLoadMore && !isLoadingMore && visibleItems.isNotEmpty()) {
                                        val lastVisibleIndex = visibleItems.last().index
                                        val totalItems = gridState.layoutInfo.totalItemsCount
                                        
                                        // Load more when we're 10 items from the end
                                        if (lastVisibleIndex >= totalItems - 10) {
                                            onLoadMore()
                                        }
                                    }
                                }
                        }
                        
                        // Create focus requesters for each movie
                        val movieFocusRequesters = remember(movies) {
                            movies.associate { movie -> 
                                movie.id to FocusRequester() 
                            }
                        }
                        
                        // Focus restoration logic
                        LaunchedEffect(lastFocusedMovieId, movies) {
                            lastFocusedMovieId?.let { focusedId ->
                                movieFocusRequesters[focusedId]?.let { focusRequester ->
                                    // Small delay to ensure grid is laid out
                                    kotlinx.coroutines.delay(100)
                                    try {
                                        focusRequester.requestFocus()
                                        android.util.Log.d("MoviesScreen", "Restored focus to movie: $focusedId")
                                    } catch (e: Exception) {
                                        android.util.Log.w("MoviesScreen", "Failed to restore focus to movie: $focusedId", e)
                                    }
                                }
                            }
                        }
                        
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                state = gridState,
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 80.dp // Always add space for bottom nav overlay
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(movies) { movie ->
                                    MovieGridCard(
                                        movie = movie,
                                        onClick = { onMovieClick(movie) },
                                        focusRequester = null,
                                        isMobile = true,
                                        progressPercentage = progressMap[movie.ratingKey]
                                    )
                                }

                                // Loading more indicator
                                if (isLoadingMore) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = RatingOrange
                                            )
                                        }
                                    }
                                }
                            }
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
                        onHomeClick = onHomeClick,
                        onSearchClick = onSearchClick,
                        onMoviesClick = { /* Already on movies */ },
                        onTvShowsClick = onTvShowsClick,
                        onSettingsClick = onSettingsClick,
                        onProfileClick = onSwitchUser,
                        currentSection = "movies"
                    )
                }
            }
        } else {
            // TV Layout: Row with sidebar and content
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Left Sidebar - Fixed width with transparent background
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .fillMaxHeight()
                        .background(Color.Transparent)
                ) {
                    LeftSidebar(
                        currentProfile = currentProfile,
                        onHomeClick = onHomeClick,
                        onSearchClick = onSearchClick,
                        onMoviesClick = { /* Already on movies */ },
                        onTvShowsClick = onTvShowsClick,
                        onSettingsClick = onSettingsClick,
                        onProfileClick = onSwitchUser,
                        sidebarFocusRequester = sidebarFocusRequester,
                        heroPlayButtonFocusRequester = gridFocusRequester, // Connect to grid instead
                        currentSection = "movies",
                        modifier = Modifier.fillMaxHeight()
                    )
                }
                
                // Main Content Area - Movies Grid
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Library Dropdown (TV)
                        if (libraries.size > 1) {
                            LibraryDropdown(
                                libraries = libraries,
                                selectedLibraryId = selectedLibraryId,
                                onLibrarySelected = onLibrarySelected,
                                isMobile = false,
                                dropdownFocusRequester = dropdownFocusRequester,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                            )
                        }

                        // Content based on state - Same as mobile but with TV-specific settings
                        when {
                            isLoading -> {
                                LoadingContent(modifier = Modifier.fillMaxSize())
                            }
                            error != null -> {
                                // Check if this is a "no libraries selected" error vs a genuine error
                                if (error.contains("No Movie Library Available") || error.contains("no movie libraries selected")) {
                                    NoLibrariesSelectedContent(
                                        message = error,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    ErrorContent(
                                        error = error,
                                        onRetry = onRetry,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            movies.isEmpty() -> {
                                EmptyContent(
                                    message = "No movies found in your Plex library",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                // Movies Grid - using provided gridState to preserve scroll position
                                
                                // Infinite scroll detection
                                LaunchedEffect(gridState) {
                                    snapshotFlow { gridState.layoutInfo.visibleItemsInfo }
                                        .collect { visibleItems ->
                                            if (canLoadMore && !isLoadingMore && visibleItems.isNotEmpty()) {
                                                val lastVisibleIndex = visibleItems.last().index
                                                val totalItems = gridState.layoutInfo.totalItemsCount
                                                
                                                // Load more when we're 10 items from the end
                                                if (lastVisibleIndex >= totalItems - 10) {
                                                    onLoadMore()
                                                }
                                            }
                                        }
                                }
                                
                                // Create focus requesters for each movie (TV only)
                                val movieFocusRequesters = remember(movies) {
                                    movies.associate { movie -> 
                                        movie.id to FocusRequester() 
                                    }
                                }
                                
                                // Focus restoration logic (TV only)
                                LaunchedEffect(lastFocusedMovieId, movies) {
                                    lastFocusedMovieId?.let { focusedId ->
                                        movieFocusRequesters[focusedId]?.let { focusRequester ->
                                            // Small delay to ensure grid is laid out
                                            kotlinx.coroutines.delay(100)
                                            try {
                                                focusRequester.requestFocus()
                                                android.util.Log.d("MoviesScreen", "Restored focus to movie: $focusedId")
                                            } catch (e: Exception) {
                                                android.util.Log.w("MoviesScreen", "Failed to restore focus to movie: $focusedId", e)
                                            }
                                        }
                                    }
                                }
                                
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(6),
                                    state = gridState,
                                    contentPadding = PaddingValues(
                                        start = 32.dp,
                                        end = 32.dp,
                                        top = if (libraries.size <= 1) 32.dp else 0.dp,
                                        bottom = 32.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .focusRequester(gridFocusRequester)
                                        .focusProperties {
                                            left = sidebarFocusRequester
                                            if (libraries.size > 1) {
                                                up = dropdownFocusRequester
                                            }
                                        }
                                ) {
                                    items(movies) { movie ->
                                        MovieGridCard(
                                            movie = movie,
                                            onClick = { onMovieClick(movie) },
                                            focusRequester = movieFocusRequesters[movie.id],
                                            isMobile = false
                                        )
                                    }
                                    
                                    // Loading more indicator
                                    if (isLoadingMore) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    color = RatingOrange
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

@Composable
private fun MovieGridCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    isMobile: Boolean = false,
    progressPercentage: Float? = null // 0.0 to 1.0, null if not started
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    Card(
        modifier = modifier
            .aspectRatio(2f / 3f) // Standard movie poster aspect ratio
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
            // Movie Poster
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixDarkGray)
            ) {
                if (movie.posterUrl != null) {
                    AsyncImage(
                        model = movie.posterUrl,
                        contentDescription = movie.title,
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
                            imageVector = Icons.Default.Movie,
                            contentDescription = "No poster available",
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                // Simple overlay for text readability - much more performant
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    BackgroundOverlay
                                )
                            )
                        )
                )

            }

            // Watch progress indicator at bottom
            if (progressPercentage != null && progressPercentage > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressPercentage.coerceIn(0f, 1f))
                            .background(NetflixRed)
                    )
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
                text = "Loading movies from Plex...",
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
                text = "Error Loading Movies",
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
                    .border(
                        width = if (isFocused) 2.dp else 0.dp,
                        color = if (isFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    ),
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
                imageVector = Icons.Default.Movie,
                contentDescription = "No Movies",
                tint = TextSecondary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No Movies Found",
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
                text = "No Movie Libraries Selected",
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
                text = "To view movies, please select movie libraries in your profile settings.",
                fontSize = 14.sp,
                color = Color(0xFFEF4444),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

