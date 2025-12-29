package com.purestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*
import android.view.KeyEvent
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import coil.compose.AsyncImage
import com.purestream.data.model.*
import com.purestream.ui.theme.*
import com.purestream.ui.theme.tvCardFocusIndicator
import com.purestream.ui.theme.tvButtonFocus
import com.purestream.ui.theme.getAnimatedButtonBackgroundColor
import com.purestream.ui.theme.animatedPosterBorder
import com.purestream.utils.rememberIsMobile

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
    episodeProgressMap: Map<String, Float> = emptyMap(),  // Add progress map parameter
    onSeasonSelect: (Season) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onBackClick: () -> Unit,
    onRetrySeasons: () -> Unit = {},
    onRetryEpisodes: () -> Unit = {},
    onAnalyzeProfanityClick: ((Episode) -> Unit)? = null,
    isAnalyzingSubtitles: Boolean = false,
    subtitleAnalysisResult: SubtitleAnalysisResult? = null,
    subtitleAnalysisError: String? = null,
    onClearAnalysisError: () -> Unit = {},
    currentProfile: Profile? = null
) {
    val isMobile = rememberIsMobile()
    val heroSectionFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }
    val firstSeasonFocusRequester = remember { FocusRequester() }
    val firstEpisodeFocusRequester = remember { FocusRequester() }

    // Auto-focus hero section when screen loads
    LaunchedEffect(Unit) {
        try {
            // Add small delay to ensure composables are laid out
            kotlinx.coroutines.delay(100)
            heroSectionFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Silently fail if focus request fails
            android.util.Log.w("TvShowDetailsScreen", "Focus request failed: ${e.message}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Background Image with overlay
        tvShow.artUrl?.let { artUrl ->
            AsyncImage(
                model = artUrl,
                contentDescription = tvShow.title,
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

        // Main Content - conditional layout for mobile vs TV
        if (isMobile) {
            // Mobile: Use Column with vertical scroll for better UX
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Mobile Hero Section (no back button)
                MobileTvShowHeroSection(
                    tvShow = tvShow,
                    episodes = episodes,
                    seasons = seasons,
                    onEpisodeClick = onEpisodeClick,
                    subtitleAnalysisResult = subtitleAnalysisResult,
                    subtitleAnalysisError = subtitleAnalysisError,
                    onClearAnalysisError = onClearAnalysisError,
                    currentProfile = currentProfile
                )

                // Seasons Section (for multi-season shows)
                if (seasons.size > 1) {
                    when {
                        isLoadingSeasons -> {
                            Text(
                                text = "Seasons",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        seasonsError != null -> {
                            Column {
                                Text(
                                    text = "Seasons",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                SeasonsErrorContent(
                                    error = seasonsError,
                                    onRetry = onRetrySeasons,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                )
                            }
                        }
                        seasons.isNotEmpty() -> {
                            Column {
                                Text(
                                    text = "Seasons",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(seasons.size) { index ->
                                        val season = seasons[index]
                                        SeasonBubble(
                                            season = season,
                                            isSelected = selectedSeason?.ratingKey == season.ratingKey,
                                            onClick = { onSeasonSelect(season) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Episodes Section
                when {
                    isLoadingEpisodes -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
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
                                    text = "Loading episodes...",
                                    fontSize = 16.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    episodesError != null -> {
                        EpisodesErrorContent(
                            error = episodesError,
                            onRetry = onRetryEpisodes,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                    episodes.isNotEmpty() -> {
                        Column {
                            // Episode section title
                            val title = if (seasons.size > 1 && selectedSeason != null) {
                                "${selectedSeason.title} Episodes"
                            } else {
                                "Episodes"
                            }

                            Text(
                                text = title,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Episodes Grid - 2 columns for mobile
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.height(400.dp)
                            ) {
                                items(episodes) { episode ->
                                    EpisodeGridCard(
                                        episode = episode,
                                        onClick = { onEpisodeClick(episode) },
                                        onAnalyzeProfanityClick = onAnalyzeProfanityClick,
                                        isAnalyzingSubtitles = isAnalyzingSubtitles,
                                        progressPercentage = episodeProgressMap[episode.ratingKey]  // Pass progress
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // No episodes found
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = "No episodes",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No episodes found",
                                    fontSize = 16.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // TV: Use LazyColumn for scrolling (existing layout)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
            // Focusable Hero Section with Poster and Details
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
                    subtitleAnalysisResult = subtitleAnalysisResult,
                    subtitleAnalysisError = subtitleAnalysisError,
                    onClearAnalysisError = onClearAnalysisError,
                    currentProfile = currentProfile
                )
            }

            // Seasons Section (for multi-season shows)  
            if (seasons.size > 1) {
                item {
                    when {
                        isLoadingSeasons -> {
                            Text(
                                text = "Seasons",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        seasonsError != null -> {
                            Column {
                                Text(
                                    text = "Seasons",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                SeasonsErrorContent(
                                    error = seasonsError,
                                    onRetry = onRetrySeasons,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                )
                            }
                        }
                        seasons.isNotEmpty() -> {
                            Column {
                                Text(
                                    text = "Seasons",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.focusProperties {
                                        up = heroSectionFocusRequester
                                        down = firstEpisodeFocusRequester
                                    }
                                ) {
                                    items(seasons.size) { index ->
                                        val season = seasons[index]
                                        SeasonBubble(
                                            season = season,
                                            isSelected = selectedSeason?.ratingKey == season.ratingKey,
                                            onClick = { onSeasonSelect(season) },
                                            modifier = if (index == 0) Modifier.focusRequester(firstSeasonFocusRequester) else Modifier
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Episodes Section
            item {
                when {
                    isLoadingEpisodes -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
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
                                    text = "Loading episodes...",
                                    fontSize = 16.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    episodesError != null -> {
                        EpisodesErrorContent(
                            error = episodesError,
                            onRetry = onRetryEpisodes,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                    episodes.isNotEmpty() -> {
                        Column {
                            // Episode section title
                            val title = if (seasons.size > 1 && selectedSeason != null) {
                                "${selectedSeason.title} Episodes"
                            } else {
                                "Episodes"
                            }

                            Text(
                                text = title,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    else -> {
                        // No episodes found
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = "No episodes",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No episodes found",
                                    fontSize = 16.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

                // Episodes Grid (4 per row)
                if (episodes.isNotEmpty()) {
                    val chunkedEpisodes = episodes.chunked(4)
                    items(chunkedEpisodes.size) { rowIndex ->
                        val episodeRow = chunkedEpisodes[rowIndex]
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = if (rowIndex == 0) {
                                Modifier.focusProperties {
                                    up = if (seasons.size > 1) firstSeasonFocusRequester else heroSectionFocusRequester
                                }
                            } else {
                                Modifier
                            }
                        ) {
                            items(episodeRow.size) { episodeIndex ->
                                val episode = episodeRow[episodeIndex]
                                EpisodeGridCard(
                                    episode = episode,
                                    onClick = { onEpisodeClick(episode) },
                                    onAnalyzeProfanityClick = onAnalyzeProfanityClick,
                                    isAnalyzingSubtitles = isAnalyzingSubtitles,
                                    progressPercentage = episodeProgressMap[episode.ratingKey],  // Pass progress
                                    modifier = if (rowIndex == 0 && episodeIndex == 0) {
                                        Modifier.focusRequester(firstEpisodeFocusRequester)
                                    } else {
                                        Modifier
                                    }
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
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val animatedBackgroundColor = getAnimatedButtonBackgroundColor(
        interactionSource = interactionSource,
        defaultColor = if (isSelected) Color(0xFF8B5CF6) else Color(0xFF2A2A2A) // Purple when selected
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .tvButtonFocus()
            .onFocusChanged { isFocused = it.isFocused },
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedBackgroundColor,
            contentColor = if (isSelected) Color.White else Color(0xFFB3B3B3)
        ),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isFocused || isSelected) 6.dp else 2.dp
        ),
        border = if (isFocused) androidx.compose.foundation.BorderStroke(
            width = 3.dp,
            color = FocusRed
        ) else null,
        interactionSource = interactionSource
    ) {
        Text(
            text = "Season ${season.seasonNumber}",
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeasonPosterCard(
    season: Season,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .aspectRatio(2f / 3f) // Standard poster aspect ratio
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .tvCardFocusIndicator(),
        colors = CardDefaults.cardColors(
            containerColor = BackgroundCard
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused || isSelected) 8.dp else 4.dp
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Season Poster
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixDarkGray)
            ) {
                season.thumbUrl?.let { thumbUrl ->
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = season.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: run {
                    // No poster URL available - show default background with season icon
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

                // Selection indicator at top right
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(
                                color = NetflixRed,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                            .size(12.dp)
                    )
                }
            }

            // Season number indicator at top left
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
                    text = "S${season.seasonNumber}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
fun EpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    progressPercentage: Float? = null // 0.0 to 1.0, null if not started
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
            // Episode Thumbnail
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF374151))
            ) {
                episode.thumbUrl?.let { thumbUrl ->
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = episode.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Play Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x40000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Episode Number
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(
                            color = Color(0xCC000000),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "E${episode.episodeNumber}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Indicators
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Profanity Level
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = when (episode.profanityLevel ?: ProfanityLevel.UNKNOWN) {
                                    ProfanityLevel.NONE -> Color(0xFF10B981)
                                    ProfanityLevel.LOW -> Color(0xFFF59E0B)
                                    ProfanityLevel.MEDIUM -> Color(0xFFEF4444)
                                    ProfanityLevel.HIGH -> Color(0xFF7C2D12)
                                    ProfanityLevel.UNKNOWN -> Color(0xFF6B7280)
                                },
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )

                    // Subtitle Indicator
                    if (episode.hasSubtitles) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFF10B981),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "CC",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Episode Info
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = episode.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    episode.summary?.let { summary ->
                        Text(
                            text = summary,
                            fontSize = 14.sp,
                            color = Color(0xFFB3B3B3),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Episode Meta
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    episode.duration?.let { duration ->
                        Text(
                            text = formatDurationUtil(duration),
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }

                    episode.airDate?.let { airDate ->
                        Text(
                            text = airDate,
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }

                    episode.rating?.let { rating ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = String.format("%.1f", rating),
                                fontSize = 12.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }
                }
            }
        }

            // Progress bar at bottom (hide when 90%+ complete)
            progressPercentage?.let { progress ->
                if (progress < 0.90f) {
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
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EpisodeGridCard(
    episode: Episode,
    onClick: () -> Unit,
    onAnalyzeProfanityClick: ((Episode) -> Unit)? = null,
    isAnalyzingSubtitles: Boolean = false,
    progressPercentage: Float? = null, // 0.0 to 1.0, null if not started
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .width(200.dp) // Larger width for 4-column layout
            .aspectRatio(16f / 9f) // Standard episode thumbnail aspect ratio
            .animatedPosterBorder(
                shape = RoundedCornerShape(8.dp),
                interactionSource = interactionSource
            )
            .clickable { onClick() }
            .focusable(interactionSource = interactionSource)
            .hoverable(interactionSource)
            .onFocusChanged { isFocused = it.isFocused }
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
            // Episode Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixDarkGray)
            ) {
                episode.thumbUrl?.let { thumbUrl ->
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = episode.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: run {
                    // No thumbnail URL available - show default background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NetflixDarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "No thumbnail available",
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Subtle overlay on focus (no action buttons)
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x40000000))
                    )
                }

                // Episode Number
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
                        text = "E${episode.episodeNumber}",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }


                // Duration indicator at bottom right
                episode.duration?.let { duration ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(
                                color = BackgroundOverlay,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatDurationUtil(duration),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Progress bar at bottom (hide when 90%+ complete)
            progressPercentage?.let { progress ->
                if (progress < 0.90f) {
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
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EpisodesErrorContent(
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
                text = "Error Loading Episodes",
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeasonsErrorContent(
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
                text = "Error Loading Seasons",
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
    subtitleAnalysisResult: SubtitleAnalysisResult? = null,
    subtitleAnalysisError: String? = null,
    onClearAnalysisError: () -> Unit = {},
    currentProfile: Profile? = null
) {
    // Create an InteractionSource to track the Column's focus state
    val interactionSource = remember { MutableInteractionSource() }

    // Subscribe to the "IsFocused" state directly - guarantees recomposition when focus changes
    val isFocused by interactionSource.collectIsFocusedAsState()

    val coroutineScope = rememberCoroutineScope()

    Row(
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        // TV Show Poster (Left Side) - Reduced size to not block content below
        Card(
            modifier = Modifier
                .width(200.dp)
                .height(300.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            tvShow.thumbUrl?.let { thumbUrl ->
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = "${tvShow.title} Poster",
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
                    Icons.Default.Tv,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF6B7280)
                )
            }
        }

        // TV Show Details (Right Side) - Separate back button from hero content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Back Button - separate focusable element outside hero section
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .focusRequester(backButtonFocusRequester)
                    .background(
                        color = Color(0x80000000),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .size(36.dp)
                    .focusProperties {
                        down = heroSectionFocusRequester
                    }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // Hero Content Section - Make this section focusable (without back button)
            Column(
                modifier = Modifier
                    .focusRequester(heroSectionFocusRequester)
                    .focusable(interactionSource = interactionSource)  // Pass interactionSource to focusable
                    .onKeyEvent { keyEvent ->
                        android.util.Log.d("TvShowHeroSection", "Key event received: ${keyEvent.key}, type: ${keyEvent.type}")
                        when (keyEvent.key) {
                            Key.Enter,
                            Key.NumPadEnter,
                            Key.DirectionCenter -> {
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    android.util.Log.d("TvShowHeroSection", "Play key pressed, playing first episode")
                                    if (episodes.isNotEmpty()) {
                                        onEpisodeClick(episodes.first())
                                    }
                                    true
                                } else false
                            }
                            Key.DirectionDown -> {
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    android.util.Log.d("TvShowHeroSection", "Down key pressed, trying to navigate down")
                                    try {
                                        // Try to navigate to seasons first when they exist, then episodes
                                        when {
                                            seasons.size > 1 -> {
                                                android.util.Log.d("TvShowHeroSection", "Moving to first season (multiple seasons)")
                                                firstSeasonFocusRequester.requestFocus()
                                            }
                                            episodes.isNotEmpty() -> {
                                                android.util.Log.d("TvShowHeroSection", "Moving to first episode (single season)")
                                                firstEpisodeFocusRequester.requestFocus()
                                            }
                                            else -> {
                                                android.util.Log.d("TvShowHeroSection", "No content to navigate to")
                                                // No content below, stay focused
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("TvShowHeroSection", "Failed to navigate down: ${e.message}")
                                    }
                                    true
                                } else false
                            }
                            Key.DirectionUp -> {
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    android.util.Log.d("TvShowHeroSection", "Up key pressed, moving to back button")
                                    try {
                                        backButtonFocusRequester.requestFocus()
                                    } catch (e: Exception) {
                                        android.util.Log.w("TvShowHeroSection", "Failed to navigate to back button: ${e.message}")
                                    }
                                    true
                                } else false
                            }
                            else -> {
                                android.util.Log.d("TvShowHeroSection", "Unhandled key: ${keyEvent.key}")
                                false
                            }
                        }
                    }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,  // Disable default ripple for custom styling
                        onClick = {
                            if (episodes.isNotEmpty()) {
                                onEpisodeClick(episodes.first())
                            }
                        }
                    )
                    .focusProperties {
                        up = backButtonFocusRequester
                        when {
                            seasons.size > 1 -> down = firstSeasonFocusRequester
                            episodes.isNotEmpty() -> down = firstEpisodeFocusRequester
                            // Don't set down property if no content below
                        }
                    }
                    .tvCardFocusIndicator(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // TV Show Title
                Text(
                    text = tvShow.title,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // TV Show Info Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    tvShow.year?.let { year ->
                        Text(
                            text = year.toString(),
                            fontSize = 14.sp,
                            color = Color(0xFFB3B3B3)
                        )
                    }

                    tvShow.seasonCount?.let { seasonCount ->
                        Text(
                            text = "$seasonCount Season${if (seasonCount != 1) "s" else ""}",
                            fontSize = 14.sp,
                            color = Color(0xFFB3B3B3)
                        )
                    }

                    tvShow.episodeCount?.let { episodeCount ->
                        Text(
                            text = "$episodeCount Episodes",
                            fontSize = 14.sp,
                            color = Color(0xFFB3B3B3)
                        )
                    }

                    tvShow.contentRating?.let { rating ->
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

                    tvShow.rating?.let { rating ->
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
                }

                // TV Show Summary with auto-scroll
                tvShow.summary?.let { summary ->
                    AutoScrollingText(
                        text = summary,
                        maxLines = 3,
                        fontSize = 14.sp,
                        color = Color(0xFFE0E0E0),
                        lineHeight = 20.sp
                    )
                }

                // Action Buttons Row - Visual only (Column handles all interaction)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // REPLACED Button WITH Surface to avoid focus competition
                    // Column handles clicks/keys, this is just a visual indicator
                    Surface(
                        color = if (isFocused) Color(0xFFF5B800) else Color(0xFF8B5CF6),  // Yellow if focused, Purple if not
                        contentColor = if (isFocused) Color.Black else Color.White,      // Black text if focused, White if not
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Play",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Subtitle Analysis Results
                subtitleAnalysisResult?.let { result ->
                    SubtitleAnalysisCard(
                        analysisResult = result,
                        currentFilterLevel = currentProfile?.profanityFilterLevel ?: ProfanityFilterLevel.MODERATE
                    )
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
                                    text = "Episode Analysis Failed",
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

@Composable
fun MobileTvShowHeroSection(
    tvShow: TvShow,
    episodes: List<Episode>,
    seasons: List<Season>,
    onEpisodeClick: (Episode) -> Unit,
    subtitleAnalysisResult: SubtitleAnalysisResult? = null,
    subtitleAnalysisError: String? = null,
    onClearAnalysisError: () -> Unit = {},
    currentProfile: Profile? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // TV Show Poster (Top - Mobile) with top padding
        Card(
            modifier = Modifier
                .width(240.dp)
                .height(360.dp)
                .align(Alignment.CenterHorizontally)
                .padding(top = 32.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            tvShow.thumbUrl?.let { thumbUrl ->
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = "${tvShow.title} Poster",
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
                    Icons.Default.Tv,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF6B7280)
                )
            }
        }

        // TV Show Details (Below Poster - Mobile)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // TV Show Title
            Text(
                text = tvShow.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // TV Show Info Row
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                tvShow.year?.let { year ->
                    Text(
                        text = year.toString(),
                        fontSize = 12.sp,
                        color = Color(0xFFB3B3B3)
                    )
                }

                tvShow.seasonCount?.let { seasonCount ->
                    Text(
                        text = "$seasonCount Season${if (seasonCount != 1) "s" else ""}",
                        fontSize = 12.sp,
                        color = Color(0xFFB3B3B3)
                    )
                }

                tvShow.episodeCount?.let { episodeCount ->
                    Text(
                        text = "$episodeCount Episodes",
                        fontSize = 12.sp,
                        color = Color(0xFFB3B3B3)
                    )
                }

                tvShow.contentRating?.let { rating ->
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF374151),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = rating,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                tvShow.rating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            fontSize = 12.sp,
                            color = Color(0xFFB3B3B3)
                        )
                    }
                }
            }

            // TV Show Summary
            tvShow.summary?.let { summary ->
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = Color(0xFFE0E0E0),
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play Button
                Button(
                    onClick = {
                        if (episodes.isNotEmpty()) {
                            onEpisodeClick(episodes.first())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF5B800),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Play",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Subtitle Analysis Results
            subtitleAnalysisResult?.let { result ->
                SubtitleAnalysisCard(
                    analysisResult = result,
                    currentFilterLevel = currentProfile?.profanityFilterLevel ?: ProfanityFilterLevel.MODERATE
                )
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
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Episode Analysis Failed",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = error,
                                fontSize = 12.sp,
                                color = Color(0xFFE0E0E0)
                            )
                        }

                        IconButton(
                            onClick = onClearAnalysisError,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationUtil(durationMs: Long): String {
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