package com.purestream.ui.screens

import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.purestream.data.api.SearchResult
import com.purestream.data.model.*
import com.purestream.ui.components.LeftSidebar
import com.purestream.ui.components.BottomNavigation
import com.purestream.utils.rememberIsMobile
import com.purestream.ui.theme.NetflixDarkGray
import com.purestream.ui.theme.*
import com.purestream.ui.theme.tvCardFocusIndicator
import com.purestream.ui.theme.tvIconFocusIndicator
import com.purestream.ui.theme.tvButtonFocus
import com.purestream.ui.theme.animatedPosterBorder

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    currentProfile: Profile?,
    searchQuery: String,
    searchResults: List<SearchResult>,
    isSearching: Boolean,
    isLoading: Boolean,
    hasSearched: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onVoiceSearchStart: () -> Unit,
    onSearchResultClick: (SearchResult) -> Unit,
    onLoadMore: () -> Unit,
    onHomeClick: () -> Unit,
    onMoviesClick: () -> Unit,
    onTvShowsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchUser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMobile = rememberIsMobile()

    // Focus management (TV only)
    val sidebarFocusRequester = remember { FocusRequester() }
    val searchBarFocusRequester = remember { FocusRequester() }
    val voiceSearchFocusRequester = remember { FocusRequester() }
    val resultsFocusRequester = remember { FocusRequester() }

    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus search bar when screen loads (TV only)
    LaunchedEffect(Unit) {
        if (!isMobile) {
            searchBarFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixDarkGray)
    ) {

        if (isMobile) {
            // Mobile Layout: Column with content on top, bottom navigation at bottom
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Main Content Area - Search with results (takes remaining space)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Transparent)
                ) {
                    // Mobile Search Layout
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Search Header Section (Mobile)
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            // Screen Title
                            Text(
                                text = "Search",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(
                                    top = 48.dp,
                                    bottom = 16.dp
                                )
                            )

                            // Search Bar (Mobile - no voice search for simplicity)
                            SearchBar(
                                query = searchQuery,
                                onQueryChange = onSearchQueryChange,
                                onSearch = { /* Mobile search submit */ },
                                focusRequester = searchBarFocusRequester,
                                onFocusChange = { /* Mobile focus handling */ },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Search Status/Hints (Mobile)
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search for movies, TV shows, and episodes",
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            // Popular Searches for Mobile - Always visible
                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "Popular Searches",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            val popularSearches = listOf(
                                "Action", "Comedy", "Marvel", "Star Wars",
                                "Horror", "Documentary", "Kids", "Thriller"
                            )

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp) // Fixed height for mobile grid
                            ) {
                                items(popularSearches.size) { index ->
                                    val suggestion = popularSearches[index]
                                    MobileSuggestionChip(
                                        text = suggestion,
                                        onClick = {
                                            onSearchQueryChange(suggestion)
                                            onSearchSubmit(suggestion)
                                        }
                                    )
                                }
                            }
                        }

                        // Search Results (Mobile)
                        when {
                            isLoading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f))
                                }
                            }

                            searchResults.isEmpty() && hasSearched -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No results found for \"$searchQuery\"",
                                        fontSize = 16.sp,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            searchResults.isNotEmpty() -> {
                                SearchResultsGrid(
                                    results = searchResults,
                                    onResultClick = onSearchResultClick,
                                    focusRequester = resultsFocusRequester,
                                    modifier = Modifier.fillMaxSize(),
                                    isMobile = true,
                                    onLoadMore = onLoadMore
                                )
                            }
                        }
                    }
                }

                // Bottom Navigation for Mobile
                BottomNavigation(
                    currentProfile = currentProfile,
                    onHomeClick = onHomeClick,
                    onSearchClick = { /* Already on search */ },
                    onMoviesClick = onMoviesClick,
                    onTvShowsClick = onTvShowsClick,
                    onSettingsClick = onSettingsClick,
                    onProfileClick = onSwitchUser,
                    currentSection = "search"
                )
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
                        onSearchClick = { /* Already on search */ },
                        onMoviesClick = onMoviesClick,
                        onTvShowsClick = onTvShowsClick,
                        onSettingsClick = onSettingsClick,
                        onProfileClick = onSwitchUser,
                        sidebarFocusRequester = sidebarFocusRequester,
                        heroPlayButtonFocusRequester = searchBarFocusRequester,
                        currentSection = "search",
                        modifier = Modifier.fillMaxHeight()
                    )
                }

                // Main Content Area - Search Interface
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Search Header Section
                        Column(
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)
                        ) {
                            // Screen Title
                            Text(
                                text = "Search",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )

                            // Search Bar and Voice Search Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Search Bar
                                SearchBar(
                                    query = searchQuery,
                                    onQueryChange = onSearchQueryChange,
                                    onSearch = onSearchSubmit,
                                    focusRequester = searchBarFocusRequester,
                                    onFocusChange = { isFocused ->
                                        if (isFocused) {
                                            keyboardController?.show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusProperties {
                                            left = sidebarFocusRequester
                                            right = voiceSearchFocusRequester
                                            down = when {
                                                searchResults.isNotEmpty() -> resultsFocusRequester
                                                else -> FocusRequester.Default
                                            }
                                        }
                                )

                                // Voice Search Button
                                VoiceSearchButton(
                                    onClick = onVoiceSearchStart,
                                    focusRequester = voiceSearchFocusRequester,
                                    modifier = Modifier.focusProperties {
                                        left = searchBarFocusRequester
                                        down = when {
                                            searchResults.isNotEmpty() -> resultsFocusRequester
                                            else -> FocusRequester.Default
                                        }
                                    }
                                )
                            }

                            // Search Status/Hints
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search for movies, TV shows, and episodes",
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            } else if (isSearching) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = NetflixRed,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Searching...",
                                        fontSize = 14.sp,
                                        color = TextSecondary
                                    )
                                }
                            } else if (searchResults.isNotEmpty()) {
                                Text(
                                    text = "${searchResults.size} results for \"$searchQuery\"",
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            } else if (searchQuery.isNotEmpty()) {
                                Text(
                                    text = "No results found for \"$searchQuery\"",
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }

                        // Search Results Section
                        if (searchResults.isNotEmpty()) {
                            SearchResultsGrid(
                                results = searchResults,
                                onResultClick = onSearchResultClick,
                                focusRequester = resultsFocusRequester,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusProperties {
                                        left = sidebarFocusRequester
                                        up = searchBarFocusRequester
                                    },
                                isMobile = false,
                                onLoadMore = onLoadMore
                            )
                        } else if (searchQuery.isEmpty()) {
                            // Show search suggestions or recent searches when empty
                            SearchSuggestions(
                                onSuggestionClick = { suggestion ->
                                    onSearchQueryChange(suggestion)
                                    onSearchSubmit(suggestion)
                                },
                                searchBarFocusRequester = searchBarFocusRequester,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SearchBar(
            query: String,
            onQueryChange: (String) -> Unit,
            onSearch: (String) -> Unit,
            focusRequester: FocusRequester,
            onFocusChange: (Boolean) -> Unit,
            modifier: Modifier = Modifier
        ) {
            var isFocused by remember { mutableStateOf(false) }

            Card(
                modifier = modifier
                    .height(56.dp)
                    .focusable()
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        onFocusChange(focusState.isFocused)
                    }
                    .border(
                        width = if (isFocused) 2.dp else 0.dp,
                        color = if (isFocused) Color(0xFF8B5CF6) else Color.Transparent,
                        shape = RoundedCornerShape(28.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = BackgroundCard
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isFocused) NetflixRed else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(NetflixRed),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { onSearch(query) }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search movies, shows, episodes...",
                                    fontSize = 16.sp,
                                    color = TextSecondary
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (query.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        @OptIn(ExperimentalComposeUiApi::class)
        @Composable
        private fun VoiceSearchButton(
            onClick: () -> Unit,
            focusRequester: FocusRequester,
            modifier: Modifier = Modifier
        ) {
            var isFocused by remember { mutableStateOf(false) }

            Card(
                modifier = modifier
                    .size(56.dp)
                    .focusable()
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable { onClick() }
                    .tvIconFocusIndicator(),
                colors = CardDefaults.cardColors(
                    containerColor = NetflixRed
                ),
                shape = CircleShape
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        @Composable
        private fun SearchResultsGrid(
            results: List<SearchResult>,
            onResultClick: (SearchResult) -> Unit,
            focusRequester: FocusRequester,
            modifier: Modifier = Modifier,
            isMobile: Boolean = false,
            onLoadMore: () -> Unit = {}
        ) {
            val gridState = rememberLazyGridState()

            // Detect when user scrolls near the end to load more results
            LaunchedEffect(gridState) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                    .collect { lastVisibleIndex ->
                        if (lastVisibleIndex != null && results.isNotEmpty()) {
                            // Load more when within 10 items of the end
                            if (lastVisibleIndex >= results.size - 10) {
                                onLoadMore()
                            }
                        }
                    }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isMobile) 3 else 6),
                state = gridState,
                contentPadding = PaddingValues(
                    start = if (isMobile) 16.dp else 32.dp,
                    end = if (isMobile) 16.dp else 32.dp,
                    bottom = if (isMobile) 16.dp else 32.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = modifier
                    .let { mod ->
                        if (isMobile) {
                            mod
                        } else {
                            mod.focusRequester(focusRequester)
                        }
                    }
            ) {
                items(results) { result ->
                    SearchResultCard(
                        result = result,
                        onClick = { onResultClick(result) },
                        isMobile = isMobile
                    )
                }
            }
        }

        @OptIn(ExperimentalComposeUiApi::class)
        @Composable
        private fun SearchResultCard(
            result: SearchResult,
            onClick: () -> Unit,
            modifier: Modifier = Modifier,
            isMobile: Boolean = false
        ) {
            var isFocused by remember { mutableStateOf(false) }
            val interactionSource = remember { MutableInteractionSource() }

            Card(
                modifier = modifier
                    .aspectRatio(2f / 3f)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onClick() }
                    .focusable(interactionSource = interactionSource)
                    .onFocusChanged { isFocused = it.isFocused }
                    .animatedPosterBorder(
                        shape = RoundedCornerShape(8.dp),
                        borderWidth = 3.dp,
                        interactionSource = interactionSource
                    )
                    .tvCardFocusIndicator(),
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
                    // Poster/Thumbnail
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NetflixDarkGray)
                    ) {
                        result.thumb?.let { thumbUrl ->
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = result.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
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


                    }
                }
            }
        }

        @Composable
        private fun SearchSuggestions(
            onSuggestionClick: (String) -> Unit,
            searchBarFocusRequester: FocusRequester,
            modifier: Modifier = Modifier
        ) {
            Column(
                modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Popular Searches",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val popularSearches = listOf(
                    "Action",
                    "Comedy",
                    "Marvel",
                    "Star Wars",
                    "Horror",
                    "Documentary",
                    "Kids",
                    "Thriller"
                )

                // Create individual focus requesters for each suggestion chip
                val chipFocusRequesters = remember {
                    popularSearches.map { FocusRequester() }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(popularSearches) { index, suggestion ->
                        val isFirstRow = index < 4 // First row indices: 0, 1, 2, 3

                        SuggestionChip(
                            text = suggestion,
                            onClick = { onSuggestionClick(suggestion) },
                            focusRequester = chipFocusRequesters[index],
                            modifier = Modifier.focusProperties {
                                // First row should navigate UP to search bar
                                if (isFirstRow) {
                                    up = searchBarFocusRequester
                                }
                                // Navigation within the grid (left/right handled automatically)
                                // Down navigation handled automatically by LazyVerticalGrid
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Search Tips",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchTip(
                        icon = Icons.Default.Mic,
                        title = "Voice Search",
                        description = "Use the microphone button for hands-free searching"
                    )
                    SearchTip(
                        icon = Icons.Default.Movie,
                        title = "Search by Genre",
                        description = "Try 'action' or 'comedy'"
                    )
                    SearchTip(
                        icon = Icons.Default.Star,
                        title = "Search by Actor",
                        description = "Find content featuring your favorite actors"
                    )
                }
            }
        }

        @OptIn(ExperimentalComposeUiApi::class)
        @Composable
        private fun SuggestionChip(
            text: String,
            onClick: () -> Unit,
            focusRequester: FocusRequester,
            modifier: Modifier = Modifier
        ) {
            val interactionSource = remember { MutableInteractionSource() }

            Card(
                modifier = modifier
                    .animatedPosterBorder(
                        shape = RoundedCornerShape(20.dp),
                        interactionSource = interactionSource
                    )
                    .tvIconFocusIndicator()
                    .focusRequester(focusRequester)
                    .clickable { onClick() },
                colors = CardDefaults.cardColors(
                    containerColor = BackgroundCard
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = text,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        @Composable
        private fun SearchTip(
            icon: ImageVector,
            title: String,
            description: String,
            modifier: Modifier = Modifier
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        @Composable
        private fun MobileSuggestionChip(
            text: String,
            onClick: () -> Unit,
            modifier: Modifier = Modifier
        ) {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable { onClick() },
                colors = CardDefaults.cardColors(
                    containerColor = BackgroundCard
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = text,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
