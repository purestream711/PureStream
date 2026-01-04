package com.purestream.ui.screens

import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars

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
    
    // Animation state
    var contentVisible by remember { mutableStateOf(false) }
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(800),
        label = "content_alpha"
    )
    val contentScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0.98f,
        animationSpec = androidx.compose.animation.core.tween(800),
        label = "content_scale"
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        contentVisible = true
    }

    // Bottom navigation visibility for mobile (auto-hide on scroll)
    var isBottomNavVisible by remember { mutableStateOf(true) }

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
            .background(Color.Black)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color.Black)
                )
            )
    ) {
        // Pulsating background glow
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "glow")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.25f,
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
                    radius = 1200f
                )
            )
        )

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
                // Mobile Layout
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SearchHeaderSection(
                            searchQuery = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onSearchSubmit = onSearchSubmit,
                            isSearching = isSearching,
                            searchResults = searchResults,
                            isMobile = true,
                            searchBarFocusRequester = searchBarFocusRequester,
                            voiceSearchFocusRequester = voiceSearchFocusRequester,
                            sidebarFocusRequester = sidebarFocusRequester,
                            resultsFocusRequester = resultsFocusRequester
                        )

                        SearchStateLayer(
                            isLoading = isLoading,
                            searchResults = searchResults,
                            hasSearched = hasSearched,
                            searchQuery = searchQuery,
                            onSearchResultClick = onSearchResultClick,
                            onLoadMore = onLoadMore,
                            resultsFocusRequester = resultsFocusRequester,
                            sidebarFocusRequester = sidebarFocusRequester,
                            searchBarFocusRequester = searchBarFocusRequester,
                            isMobile = true,
                            onBottomNavVisibilityChange = { isBottomNavVisible = it },
                            onSuggestionClick = { onSearchQueryChange(it); onSearchSubmit(it) }
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isBottomNavVisible,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        BottomNavigation(
                            currentProfile = currentProfile,
                            onHomeClick = onHomeClick,
                            onSearchClick = { },
                            onMoviesClick = onMoviesClick,
                            onTvShowsClick = onTvShowsClick,
                            onSettingsClick = onSettingsClick,
                            onProfileClick = onSwitchUser,
                            currentSection = "search",
                            isVisible = true
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
                            onSearchClick = { },
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

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        SearchHeaderSection(
                            searchQuery = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onSearchSubmit = onSearchSubmit,
                            isSearching = isSearching,
                            searchResults = searchResults,
                            isMobile = false,
                            searchBarFocusRequester = searchBarFocusRequester,
                            voiceSearchFocusRequester = voiceSearchFocusRequester,
                            sidebarFocusRequester = sidebarFocusRequester,
                            resultsFocusRequester = resultsFocusRequester,
                            onVoiceSearchStart = onVoiceSearchStart,
                            keyboardController = keyboardController
                        )

                        SearchStateLayer(
                            isLoading = isLoading,
                            searchResults = searchResults,
                            hasSearched = hasSearched,
                            searchQuery = searchQuery,
                            onSearchResultClick = onSearchResultClick,
                            onLoadMore = onLoadMore,
                            resultsFocusRequester = resultsFocusRequester,
                            sidebarFocusRequester = sidebarFocusRequester,
                            searchBarFocusRequester = searchBarFocusRequester,
                            isMobile = false,
                            onSuggestionClick = { onSearchQueryChange(it); onSearchSubmit(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHeaderSection(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<SearchResult>,
    isMobile: Boolean,
    searchBarFocusRequester: FocusRequester,
    voiceSearchFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    resultsFocusRequester: FocusRequester,
    onVoiceSearchStart: () -> Unit = {},
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController? = null
) {
    Column(
        modifier = Modifier.padding(
            start = if (isMobile) 24.dp else 48.dp,
            end = if (isMobile) 24.dp else 48.dp,
            top = if (isMobile) 56.dp else 32.dp,
            bottom = if (isMobile) 24.dp else 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = onQueryChange,
                onSearch = onSearchSubmit,
                focusRequester = searchBarFocusRequester,
                onFocusChange = { if (it && !isMobile) keyboardController?.show() },
                modifier = Modifier.weight(1f).focusProperties {
                    left = sidebarFocusRequester
                    right = if (!isMobile) voiceSearchFocusRequester else FocusRequester.Default
                    down = if (searchResults.isNotEmpty()) resultsFocusRequester else FocusRequester.Default
                }
            )

            if (!isMobile) {
                VoiceSearchButton(
                    onClick = onVoiceSearchStart,
                    focusRequester = voiceSearchFocusRequester,
                    modifier = Modifier.focusProperties {
                        left = searchBarFocusRequester
                        down = if (searchResults.isNotEmpty()) resultsFocusRequester else FocusRequester.Default
                    }
                )
            }
        }

        // Status Hints
        if (isSearching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color(0xFF8B5CF6), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Searching...", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun SearchStateLayer(
    isLoading: Boolean,
    searchResults: List<SearchResult>,
    hasSearched: Boolean,
    searchQuery: String,
    onSearchResultClick: (SearchResult) -> Unit,
    onLoadMore: () -> Unit,
    resultsFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    searchBarFocusRequester: FocusRequester,
    isMobile: Boolean,
    onBottomNavVisibilityChange: (Boolean) -> Unit = {},
    onSuggestionClick: (String) -> Unit
) {
    when {
        isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF8B5CF6))
            }
        }
        searchResults.isEmpty() && hasSearched -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results for \"$searchQuery\"", color = Color.White.copy(alpha = 0.4f), fontSize = 18.sp)
            }
        }
        searchResults.isNotEmpty() -> {
            SearchResultsGrid(
                results = searchResults,
                onResultClick = onSearchResultClick,
                focusRequester = resultsFocusRequester,
                modifier = Modifier.fillMaxSize().focusProperties { left = sidebarFocusRequester; up = searchBarFocusRequester },
                isMobile = isMobile,
                onLoadMore = onLoadMore,
                onBottomNavVisibilityChange = onBottomNavVisibilityChange
            )
        }
        else -> {
            SearchSuggestions(
                onSuggestionClick = onSuggestionClick,
                searchBarFocusRequester = searchBarFocusRequester,
                modifier = Modifier.fillMaxSize(),
                isMobile = isMobile
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = modifier
            .height(60.dp)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { onFocusChange(it.isFocused) },
        color = Color(0xFF1A1C2E).copy(alpha = 0.7f),
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
        ) {
            Icon(Icons.Default.Search, null, tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.4f))
            Spacer(Modifier.width(16.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(Color(0xFF8B5CF6)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search movies, shows...", color = Color.White.copy(alpha = 0.3f), fontSize = 18.sp)
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun VoiceSearchButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        modifier = modifier.size(60.dp).focusRequester(focusRequester).focusable(interactionSource = interactionSource),
        color = if (isFocused) Color.White else Color(0xFF8B5CF6),
        contentColor = if (isFocused) Color.Black else Color.White,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Mic, null, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun SearchSuggestions(
    onSuggestionClick: (String) -> Unit,
    searchBarFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    isMobile: Boolean = false
) {
    Column(modifier = modifier.padding(horizontal = if (isMobile) 24.dp else 48.dp)) {
        Text("POPULAR SEARCHES", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.5f), letterSpacing = 2.sp)
        Spacer(Modifier.height(24.dp))

        val popular = listOf("Action", "Comedy", "Thriller", "Marvel", "Star Wars", "Horror", "Documentary", "Kids")
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isMobile) 2 else 4),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().height(if (isMobile) 240.dp else 120.dp)
        ) {
            itemsIndexed(popular) { index, suggestion ->
                val requester = remember { FocusRequester() }
                SuggestionChip(
                    text = suggestion,
                    onClick = { onSuggestionClick(suggestion) },
                    focusRequester = requester,
                    modifier = Modifier.focusProperties { if (index < 4) up = searchBarFocusRequester }
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp).focusRequester(focusRequester).focusable(interactionSource = interactionSource),
        color = Color(0xFF1A1C2E).copy(alpha = if (isFocused) 0.9f else 0.5f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(text, color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
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
    onLoadMore: () -> Unit = {},
    onBottomNavVisibilityChange: (Boolean) -> Unit = {}
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { idx ->
            if (idx != null && results.isNotEmpty() && idx >= results.size - 10) onLoadMore()
        }
    }

    if (isMobile) {
        LaunchedEffect(gridState) {
            var prevIdx = gridState.firstVisibleItemIndex
            var prevOffset = gridState.firstVisibleItemScrollOffset
            snapshotFlow { Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) }.collect { (idx, offset) ->
                val down = if (idx != prevIdx) idx > prevIdx else offset > prevOffset
                onBottomNavVisibilityChange(idx == 0 && offset < 100 || !down)
                prevIdx = idx; prevOffset = offset
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isMobile) 3 else 6),
        state = gridState,
        contentPadding = PaddingValues(start = if (isMobile) 24.dp else 48.dp, end = if (isMobile) 24.dp else 48.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.then(if (!isMobile) Modifier.focusRequester(focusRequester) else Modifier)
    ) {
        itemsIndexed(results) { index, result ->
            SearchResultCard(
                result = result, 
                onClick = { onResultClick(result) },
                delayIndex = index % 12
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    delayIndex: Int
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val soundManager = remember { com.purestream.utils.SoundManager.getInstance(context) }
    
    var visible by remember { mutableStateOf(false) }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(600, delayMillis = delayIndex * 50),
        label = "card_alpha"
    )
    LaunchedEffect(Unit) { visible = true }

    Card(
        modifier = Modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer { this.alpha = alpha }
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
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(result.thumb, null, Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            if (isFocused) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f)))
            }
        }
    }
}
