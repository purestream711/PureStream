package com.purestream.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.purestream.data.model.*
import com.purestream.ui.theme.*
import com.purestream.utils.rememberIsMobile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvShowDetailsScreen(
    tvShow: TvShow,
    seasons: List<Season>,
    selectedSeason: Season?,
    episodes: List<Episode>,
    isLoadingSeasons: Boolean = false,
    isLoadingEpisodes: Boolean = false,
    seasonsError: String? = null,
    episodesError: String? = null,
    episodeProgressMap: Map<String, Float> = emptyMap(),
    onSeasonSelect: (Season) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onBackClick: () -> Unit,
    onRetrySeasons: () -> Unit = {},
    onRetryEpisodes: () -> Unit = {},
    currentProfile: Profile? = null
) {
    val isMobile = rememberIsMobile()
    val heroSectionFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }
    val firstSeasonFocusRequester = remember { FocusRequester() }
    val firstEpisodeFocusRequester = remember { FocusRequester() }

    // Scroll state for mobile LazyColumn (for parallax effect)
    val scrollState = if (isMobile) rememberLazyListState() else null

    var contentVisible by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "content_alpha"
    )
    val contentScale by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0.95f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
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
        delay(100)
        contentVisible = true
        try {
            delay(100)
            heroSectionFocusRequester.requestFocus()
        } catch (e: Exception) {
            android.util.Log.w("TvShowDetailsScreen", "Focus request failed: ${e.message}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        tvShow.artUrl?.let { artUrl ->
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

        val infiniteTransition = rememberInfiniteTransition(label = "glow")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(5000),
                repeatMode = RepeatMode.Reverse
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    scaleX = contentScale
                    scaleY = contentScale
                }
        ) {
            if (isMobile && scrollState != null) {
                // Mobile: LazyColumn with Hero Layout and Parallax
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Hero Section with Backdrop and Clearlogo
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(600.dp)
                        ) {
                            // Backdrop Image with Parallax
                            tvShow.artUrl?.let { artUrl ->
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

                            // Dark Gradient Overlay
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
                                if (tvShow.logoUrl != null) {
                                    AsyncImage(
                                        model = tvShow.logoUrl,
                                        contentDescription = tvShow.title,
                                        modifier = Modifier
                                            .height(120.dp)
                                            .widthIn(max = 300.dp),
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.Center
                                    )
                                } else {
                                    // Fallback to text title
                                    Text(
                                        text = tvShow.title,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 38.sp
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                // Metadata Row (Year, Seasons)
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${tvShow.year ?: ""}  •  ${tvShow.seasonCount} Seasons  •  ${tvShow.episodeCount} Episodes",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    tvShow.contentRating?.let {
                                        Text(
                                            text = "  •  $it",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // Rating badge
                                tvShow.rating?.let { rating ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(String.format("%.1f", rating), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Content Section (No white background, closer spacing)
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
                            // Play First Episode Button
                            Button(
                                onClick = { if (episodes.isNotEmpty()) onEpisodeClick(episodes.first()) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "PLAY FIRST EPISODE",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            }

                            // Summary
                            tvShow.summary?.let {
                                Text(
                                    text = it,
                                    fontSize = 16.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 24.sp,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Studio
                            tvShow.studio?.let {
                                Text(
                                    text = "Studio: $it",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Seasons Section (if multiple seasons)
                            if (seasons.size > 1) {
                                SeasonsLayer(isLoadingSeasons, seasonsError, seasons, selectedSeason, onSeasonSelect, onRetrySeasons, true, null)
                            }

                            // Episodes Section
                            EpisodesLayer(isLoadingEpisodes, episodesError, episodes, selectedSeason, seasons, onEpisodeClick, onRetryEpisodes, episodeProgressMap, true, null, null)

                            // Bottom padding
                            Spacer(Modifier.height(64.dp))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(48.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    item {
                        TvShowHeroSection(
                            tvShow = tvShow,
                            episodes = episodes,
                            seasons = seasons,
                            onEpisodeClick = onEpisodeClick,
                            onBackClick = onBackClick,
                            heroSectionFocusRequester = heroSectionFocusRequester,
                            backButtonFocusRequester = backButtonFocusRequester,
                            firstSeasonFocusRequester = firstSeasonFocusRequester,
                            firstEpisodeFocusRequester = firstEpisodeFocusRequester,
                            currentProfile = currentProfile
                        )
                    }

                    if (seasons.size > 1) {
                        item {
                            SeasonsLayer(isLoadingSeasons, seasonsError, seasons, selectedSeason, onSeasonSelect, onRetrySeasons, false, firstSeasonFocusRequester, heroSectionFocusRequester, firstEpisodeFocusRequester)
                        }
                    }

                    item {
                        EpisodesLayer(isLoadingEpisodes, episodesError, episodes, selectedSeason, seasons, onEpisodeClick, onRetryEpisodes, episodeProgressMap, false, firstEpisodeFocusRequester, if (seasons.size > 1) firstSeasonFocusRequester else heroSectionFocusRequester)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonsLayer(
    isLoading: Boolean,
    error: String?,
    seasons: List<Season>,
    selectedSeason: Season?,
    onSelect: (Season) -> Unit,
    onRetry: () -> Unit,
    isMobile: Boolean,
    firstFocus: FocusRequester?,
    upFocus: FocusRequester? = null,
    downFocus: FocusRequester? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SEASONS", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.5f), letterSpacing = 2.sp)
        
        when {
            isLoading -> CircularProgressIndicator(color = Color(0xFF8B5CF6))
            error != null -> Button(onRetry) { Text("Retry") }
            else -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = if (!isMobile && upFocus != null) Modifier.focusProperties { up = upFocus; if (downFocus != null) down = downFocus } else Modifier
                ) {
                    items(seasons.size) { index ->
                        SeasonBubble(
                            season = seasons[index],
                            isSelected = selectedSeason?.ratingKey == seasons[index].ratingKey,
                            onClick = { onSelect(seasons[index]) },
                            modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodesLayer(
    isLoading: Boolean,
    error: String?,
    episodes: List<Episode>,
    selectedSeason: Season?,
    seasons: List<Season>,
    onEpisodeClick: (Episode) -> Unit,
    onRetry: () -> Unit,
    progressMap: Map<String, Float>,
    isMobile: Boolean,
    firstFocus: FocusRequester?,
    upFocus: FocusRequester?
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val title = if (isMobile && seasons.size > 1 && selectedSeason != null) "${selectedSeason.title.uppercase()} EPISODES" else "EPISODES"
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.5f), letterSpacing = 2.sp)
        
        when {
            isLoading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF8B5CF6)) }
            error != null -> Button(onRetry) { Text("Retry") }
            episodes.isEmpty() -> Text("No episodes found", color = Color.White.copy(alpha = 0.4f))
            else -> {
                if (isMobile) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.height(400.dp)
                    ) {
                        items(episodes) { EpisodeGridCard(it, { onEpisodeClick(it) }, progressPercentage = progressMap[it.ratingKey]) }
                    }
                } else {
                    val chunked = episodes.chunked(4)
                    chunked.forEachIndexed { rowIndex, row ->
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = if (rowIndex == 0 && upFocus != null) Modifier.focusProperties { up = upFocus } else Modifier,
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(row.size) { colIndex ->
                                EpisodeGridCard(
                                    episode = row[colIndex],
                                    onClick = { onEpisodeClick(row[colIndex]) },
                                    progressPercentage = progressMap[row[colIndex].ratingKey],
                                    modifier = if (rowIndex == 0 && colIndex == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeasonBubble(
    season: Season,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> Color(0xFF8B5CF6)
            else -> Color.White.copy(alpha = 0.05f)
        },
        label = "bubble_bg"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { } // Keep for focus system
            .focusable(interactionSource = interactionSource),
        color = bgColor,
        contentColor = if (isFocused) Color.Black else Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            text = if (season.seasonNumber == 0) "Specials" else "Season ${season.seasonNumber}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Black else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvShowHeroSection(
    tvShow: TvShow,
    episodes: List<Episode>,
    seasons: List<Season>,
    onEpisodeClick: (Episode) -> Unit,
    onBackClick: () -> Unit,
    heroSectionFocusRequester: FocusRequester,
    backButtonFocusRequester: FocusRequester,
    firstSeasonFocusRequester: FocusRequester,
    firstEpisodeFocusRequester: FocusRequester,
    currentProfile: Profile? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Info Section (Reduced 50% like MovieDetailsScreen)
    Column(
        modifier = Modifier
            .fillMaxWidth(0.55f)
            .padding(start = 0.dp, bottom = 24.dp)
            .focusRequester(heroSectionFocusRequester)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter -> { if (episodes.isNotEmpty()) onEpisodeClick(episodes.first()); true }
                        Key.DirectionDown -> {
                            if (seasons.size > 1) firstSeasonFocusRequester.requestFocus()
                            else if (episodes.isNotEmpty()) firstEpisodeFocusRequester.requestFocus()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null) {
                if (episodes.isNotEmpty()) onEpisodeClick(episodes.first())
            },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title or Clearlogo (75dp height)
        if (tvShow.logoUrl != null) {
            AsyncImage(
                model = tvShow.logoUrl,
                contentDescription = tvShow.title,
                modifier = Modifier
                    .height(75.dp)
                    .widthIn(max = 300.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart
            )
        } else {
            Text(
                text = tvShow.title,
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
            Surface(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    tvShow.contentRating ?: "NR",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                "${tvShow.year}  •  ${tvShow.seasonCount} Seasons",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            tvShow.rating?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(String.format("%.1f", it), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Summary with auto-scroll (3 lines max)
        tvShow.summary?.let { summary ->
            AutoScrollingText(
                text = summary,
                maxLines = 3,
                fontSize = 14.sp,
                color = Color(0xFFE0E0E0),
                lineHeight = 20.sp
            )
        }

        // Actions (Play button - 50% smaller)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Surface(
                onClick = { if (episodes.isNotEmpty()) onEpisodeClick(episodes.first()) },
                modifier = Modifier
                    .height(28.dp)
                    .focusable(interactionSource = interactionSource),
                color = if (isFocused) Color.White else Color(0xFF8B5CF6),
                contentColor = if (isFocused) Color.Black else Color.White,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                    Text(
                        text = "PLAY FIRST EPISODE",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MobileTvShowHeroSection(
    tvShow: TvShow,
    episodes: List<Episode>,
    seasons: List<Season>,
    onEpisodeClick: (Episode) -> Unit,
    currentProfile: Profile? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(
            modifier = Modifier.width(200.dp).height(300.dp).padding(top = 16.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            AsyncImage(tvShow.thumbUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1C2E).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(tvShow.title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text(tvShow.contentRating ?: "NR", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = Color.White)
                }
                Text("${tvShow.year}  •  ${tvShow.seasonCount} Seasons", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }

            Text(tvShow.summary ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 3)

            Button(
                onClick = { if (episodes.isNotEmpty()) onEpisodeClick(episodes.first()) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("PLAY S1:E1")
            }
        }
    }
}

@Composable
fun EpisodeGridCard(
    episode: Episode,
    onClick: () -> Unit,
    progressPercentage: Float? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Card(
        modifier = modifier
            .width(200.dp)
            .aspectRatio(16f / 9f)
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = episode.thumbUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            
            if (isFocused) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f)))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                val label = "E${episode.episodeNumber}"
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            progressPercentage?.let { progress ->
                if (progress > 0.05f) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.5f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Color.White))
                    }
                }
            }
        }
    }
}

private fun formatDurationUtil(durationMs: Long): String {
    val hours = durationMs / (1000 * 60 * 60)
    val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

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
    val maxHeightPx = with(density) { (lineHeight.value * maxLines).dp.toPx() }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .heightIn(max = with(density) { maxHeightPx.toDp() })
                .then(if (isTruncated) Modifier.verticalScroll(scrollState) else Modifier)
        ) {
            Text(
                text = text,
                fontSize = fontSize,
                color = color,
                lineHeight = lineHeight,
                maxLines = if (isTruncated) Int.MAX_VALUE else maxLines,
                overflow = if (isTruncated) TextOverflow.Visible else TextOverflow.Ellipsis,
                onTextLayout = { if (it.didOverflowHeight || it.lineCount > maxLines) isTruncated = true }
            )
        }
    }

    LaunchedEffect(text, isTruncated) {
        if (isTruncated) {
            delay(3000)
            while (true) {
                scrollState.animateScrollTo(scrollState.maxValue, tween(scrollState.maxValue * 20, easing = LinearEasing))
                delay(5000)
                scrollState.scrollTo(0)
                delay(3000)
            }
        }
    }
}