package com.purestream.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.purestream.ui.theme.getAnimatedButtonBackgroundColor
import com.purestream.ui.theme.animatedPosterBorder
import com.purestream.data.model.*
import com.purestream.ui.components.LeftSidebar
import com.purestream.ui.components.BottomNavigation
import com.purestream.utils.rememberIsMobile
import com.purestream.ui.theme.*
import com.purestream.ui.theme.tvButtonFocus
import androidx.compose.foundation.hoverable
import java.io.File
import java.text.SimpleDateFormat
import com.purestream.utils.SoundManager
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data class to represent grouped movies for filtered subtitles
private data class GroupedMovie(
    val contentId: String,
    val movieTitle: String,
    val filterLevelsCount: Int,
    val lastModified: Long,
    val totalSize: Long,
    val files: List<File>
)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    currentProfile: Profile?,
    appSettings: AppSettings?,
    availableLibraries: List<PlexLibrary>,
    onSearchClick: () -> Unit,
    onHomeClick: () -> Unit,
    onMoviesClick: () -> Unit,
    onTvShowsClick: () -> Unit,
    onSwitchUser: () -> Unit,
    onProfanityFilterChange: (ProfanityFilterLevel) -> Unit,
    onLibrarySelectionChange: (List<String>) -> Unit,
    onSettingToggle: (String, Boolean) -> Unit,
    onMuteDurationChange: (Int) -> Unit,
    onAddCustomProfanity: (String) -> Unit,
    onAddWhitelistWord: (String) -> Unit,
    onRemoveCustomProfanity: (String) -> Unit,
    onRemoveWhitelistWord: (String) -> Unit,
    onLogout: () -> Unit,
    onUpgradeClick: () -> Unit = {},
    onFreeVersionClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Focus management
    val sidebarFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() } // For Pure Stream Pro tab
    val upgradeButtonFocusRequester = remember { FocusRequester() }
    val profanityFilterTabFocusRequester = remember { FocusRequester() } // For Profanity Filter tab
    val profanityFilterFocusRequester = remember { FocusRequester() } // For filter buttons
    val filteredSubtitlesTabFocusRequester = remember { FocusRequester() } // For Filtered Subtitles tab
    val filteredSubtitlesFocusRequester = remember { FocusRequester() } // For filtered subtitles content
    val logoutTabFocusRequester = remember { FocusRequester() } // For Logout tab
    val logoutButtonFocusRequester = remember { FocusRequester() } // For logout button

    // Get the real premium status from app settings
    val isPremium = appSettings?.isPremium ?: false
    val isMobile = rememberIsMobile()

    Box(modifier = modifier.fillMaxSize()) {
        // Netflix-style background gradient system
        // Main radial vignette with bright illuminated center
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,                        // Fully bright center
                            Color.Transparent,                        // Extended bright area
                            Color.Transparent,                        // Even more bright area
                            Color.Transparent,                        // Maximum visibility zone
                            Color.Transparent,                        // Keep center well-lit
                            Color.Black.copy(alpha = 0.08f),        // Very gentle start
                            Color.Black.copy(alpha = 0.25f),        // Gradual darkening
                            Color.Black.copy(alpha = 0.5f),         // Medium edge fade
                            Color.Black.copy(alpha = 0.75f)         // Strong outer edges
                        ),
                        radius = 1600f,
                        center = androidx.compose.ui.geometry.Offset(0.5f, 0.4f)
                    )
                )
        )

        // Left-side content readability gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),        // Strong left sidebar area
                            Color.Black.copy(alpha = 0.6f),         // Content area protection
                            Color.Black.copy(alpha = 0.3f),         // Transition zone
                            Color.Black.copy(alpha = 0.1f),         // Light overlay
                            Color.Black.copy(alpha = 0.02f),        // Barely visible
                            Color.Transparent,                        // Clear image area
                            Color.Transparent,                        // Full visibility
                            Color.Transparent,                        // Bright center maintained
                            Color.Transparent                         // Right edge clear
                        ),
                        startX = 0f,
                        endX = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Top/bottom cinematic bars effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),         // Top cinematic bar
                            Color.Black.copy(alpha = 0.1f),         // Fade to center
                            Color.Transparent,                        // Clear center viewing
                            Color.Transparent,                        // Extended clear center
                            Color.Transparent,                        // More clear center
                            Color.Black.copy(alpha = 0.1f),         // Start bottom fade
                            Color.Black.copy(alpha = 0.5f)          // Bottom cinematic bar
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        if (isMobile) {
            // Mobile: Column layout with bottom navigation
            Column(modifier = Modifier.fillMaxSize()) {
                // Main Content Area - Tabbed Settings (takes available space)
                TabbedSettingsContent(
                    currentProfile = currentProfile,
                    appSettings = appSettings,
                    availableLibraries = availableLibraries,
                    onProfanityFilterChange = onProfanityFilterChange,
                    onLibrarySelectionChange = onLibrarySelectionChange,
                    onSettingToggle = onSettingToggle,
                    onMuteDurationChange = onMuteDurationChange,
                    onAddCustomProfanity = onAddCustomProfanity,
                    onAddWhitelistWord = onAddWhitelistWord,
                    onRemoveCustomProfanity = onRemoveCustomProfanity,
                    onRemoveWhitelistWord = onRemoveWhitelistWord,
                    onLogout = onLogout,
                    onUpgradeClick = onUpgradeClick,
                    onFreeVersionClick = onFreeVersionClick,
                    contentFocusRequester = contentFocusRequester,
                    sidebarFocusRequester = sidebarFocusRequester,
                    upgradeButtonFocusRequester = upgradeButtonFocusRequester,
                    profanityFilterTabFocusRequester = profanityFilterTabFocusRequester,
                    profanityFilterFocusRequester = profanityFilterFocusRequester,
                    filteredSubtitlesTabFocusRequester = filteredSubtitlesTabFocusRequester,
                    filteredSubtitlesFocusRequester = filteredSubtitlesFocusRequester,
                    logoutTabFocusRequester = logoutTabFocusRequester,
                    logoutButtonFocusRequester = logoutButtonFocusRequester,
                    isMobile = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Transparent)
                )
                
                // Bottom Navigation (Mobile)
                BottomNavigation(
                    currentProfile = currentProfile,
                    onHomeClick = onHomeClick,
                    onSearchClick = onSearchClick,
                    onMoviesClick = onMoviesClick,
                    onTvShowsClick = onTvShowsClick,
                    onSettingsClick = { /* Already on settings */ },
                    onProfileClick = onSwitchUser,
                    currentSection = "settings"
                )
            }
        } else {
            // TV: Row layout with left sidebar (existing)
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
                        onMoviesClick = onMoviesClick,
                        onTvShowsClick = onTvShowsClick,
                        onSettingsClick = { /* Already on settings */ },
                        onProfileClick = onSwitchUser,
                        sidebarFocusRequester = sidebarFocusRequester,
                        heroPlayButtonFocusRequester = contentFocusRequester,
                        currentSection = "settings",
                        modifier = Modifier.fillMaxHeight()
                    )
                }

                // Main Content Area - Tabbed Settings
                TabbedSettingsContent(
                    currentProfile = currentProfile,
                    appSettings = appSettings,
                    availableLibraries = availableLibraries,
                    onProfanityFilterChange = onProfanityFilterChange,
                    onLibrarySelectionChange = onLibrarySelectionChange,
                    onSettingToggle = onSettingToggle,
                    onMuteDurationChange = onMuteDurationChange,
                    onAddCustomProfanity = onAddCustomProfanity,
                    onAddWhitelistWord = onAddWhitelistWord,
                    onRemoveCustomProfanity = onRemoveCustomProfanity,
                    onRemoveWhitelistWord = onRemoveWhitelistWord,
                    onLogout = onLogout,
                    onUpgradeClick = onUpgradeClick,
                    onFreeVersionClick = onFreeVersionClick,
                    contentFocusRequester = contentFocusRequester,
                    sidebarFocusRequester = sidebarFocusRequester,
                    upgradeButtonFocusRequester = upgradeButtonFocusRequester,
                    profanityFilterTabFocusRequester = profanityFilterTabFocusRequester,
                    profanityFilterFocusRequester = profanityFilterFocusRequester,
                    filteredSubtitlesTabFocusRequester = filteredSubtitlesTabFocusRequester,
                    filteredSubtitlesFocusRequester = filteredSubtitlesFocusRequester,
                    logoutTabFocusRequester = logoutTabFocusRequester,
                    logoutButtonFocusRequester = logoutButtonFocusRequester,
                    isMobile = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Transparent)
                )
            }
        }
    }
}

// Tab definition enum
enum class SettingsTab(val title: String) {
    PURE_STREAM_PRO("Pure Stream Pro"),
    PROFANITY_FILTER("Profanity Filter"),
    FILTERED_SUBTITLES("Filtered Subtitles"),
    DASHBOARD_CUSTOMIZATION("Dashboard Customization"),
    LOGOUT("Logout")
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TabbedSettingsContent(
    currentProfile: Profile?,
    appSettings: AppSettings?,
    availableLibraries: List<PlexLibrary>,
    onProfanityFilterChange: (ProfanityFilterLevel) -> Unit,
    onLibrarySelectionChange: (List<String>) -> Unit,
    onSettingToggle: (String, Boolean) -> Unit,
    onMuteDurationChange: (Int) -> Unit,
    onAddCustomProfanity: (String) -> Unit,
    onAddWhitelistWord: (String) -> Unit,
    onRemoveCustomProfanity: (String) -> Unit,
    onRemoveWhitelistWord: (String) -> Unit,
    onLogout: () -> Unit,
    onUpgradeClick: () -> Unit,
    onFreeVersionClick: () -> Unit,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    upgradeButtonFocusRequester: FocusRequester,
    profanityFilterTabFocusRequester: FocusRequester,
    profanityFilterFocusRequester: FocusRequester,
    filteredSubtitlesTabFocusRequester: FocusRequester,
    filteredSubtitlesFocusRequester: FocusRequester,
    logoutTabFocusRequester: FocusRequester,
    logoutButtonFocusRequester: FocusRequester,
    isMobile: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(SettingsTab.PURE_STREAM_PRO) }
    val isPremium = appSettings?.isPremium ?: false

    // Focus the first tab when settings screen loads
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100) // Small delay to ensure composition is complete
        contentFocusRequester.requestFocus()
    }

    // Handle focus requests when selectedTab changes for immediate navigation
    LaunchedEffect(selectedTab) {
        kotlinx.coroutines.delay(50) // Small delay to ensure content is composed
        when (selectedTab) {
            SettingsTab.PURE_STREAM_PRO -> {
                if (!isPremium) {
                    upgradeButtonFocusRequester.requestFocus()
                }
                // If premium, keep focus on tab (no specific content to focus)
            }
            SettingsTab.LOGOUT -> {
                logoutButtonFocusRequester.requestFocus()
            }
            // Don't interfere with Profanity Filter and Filtered Subtitles - they work correctly
            else -> {
                // No action needed for working tabs
            }
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Profile Settings Header
        Text(
            text = "Profile Settings",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Current Profile Display
        currentProfile?.let { profile ->
            ProfileInfoCard(profile = profile, isPremium = isPremium)

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Tabs Row - horizontally scrollable on mobile
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .then(
                    if (isMobile) {
                        Modifier.horizontalScroll(rememberScrollState())
                    } else {
                        Modifier
                    }
                )
        ) {
            SettingsTab.values().forEachIndexed { index, tab ->
                SettingsTabButton(
                    tab = tab,
                    isSelected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    modifier = Modifier.focusRequester(
                        when (tab) {
                            SettingsTab.PURE_STREAM_PRO -> contentFocusRequester
                            SettingsTab.PROFANITY_FILTER -> profanityFilterTabFocusRequester
                            SettingsTab.FILTERED_SUBTITLES -> filteredSubtitlesTabFocusRequester
                            SettingsTab.LOGOUT -> logoutTabFocusRequester
                            else -> remember { FocusRequester() }
                        }
                    ).focusProperties {
                        if (index == 0) {
                            left = sidebarFocusRequester
                        }
                        // Add down navigation to content for the SELECTED tab (not the current tab button)
                        when (selectedTab) {
                            SettingsTab.PURE_STREAM_PRO -> {
                                if (!isPremium) {
                                    down = upgradeButtonFocusRequester
                                } else {
                                    // If premium, navigate to sidebar since no upgrade button
                                    down = sidebarFocusRequester
                                }
                            }
                            SettingsTab.PROFANITY_FILTER -> {
                                // Navigate to profanity filter buttons
                                down = profanityFilterFocusRequester
                            }
                            SettingsTab.FILTERED_SUBTITLES -> {
                                // Navigate to filtered subtitles content
                                down = filteredSubtitlesFocusRequester
                            }
                            SettingsTab.LOGOUT -> {
                                // Navigate to logout button
                                down = logoutButtonFocusRequester
                            }
                            else -> {
                                // For other tabs, navigate down to sidebar if no specific content
                                down = sidebarFocusRequester
                            }
                        }
                    }
                )
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = BackgroundCard,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            when (selectedTab) {
                SettingsTab.PURE_STREAM_PRO -> {
                    PureStreamProTabContent(
                        isPremium = isPremium,
                        onUpgradeClick = onUpgradeClick,
                        onFreeVersionClick = onFreeVersionClick,
                        upgradeButtonFocusRequester = upgradeButtonFocusRequester,
                        sidebarFocusRequester = sidebarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                        isMobile = isMobile
                    )
                }
                SettingsTab.PROFANITY_FILTER -> {
                    ProfanityFilterTabContent(
                        currentProfile = currentProfile,
                        isPremium = isPremium,
                        onProfanityFilterChange = onProfanityFilterChange,
                        onMuteDurationChange = onMuteDurationChange,
                        onAddCustomProfanity = onAddCustomProfanity,
                        onAddWhitelistWord = onAddWhitelistWord,
                        onRemoveCustomProfanity = onRemoveCustomProfanity,
                        onRemoveWhitelistWord = onRemoveWhitelistWord,
                        onUpgradeClick = onUpgradeClick,
                        profanityFilterFocusRequester = profanityFilterFocusRequester,
                        profanityFilterTabFocusRequester = profanityFilterTabFocusRequester
                    )
                }
                SettingsTab.FILTERED_SUBTITLES -> {
                    FilteredSubtitlesTabContent(
                        filteredSubtitlesFocusRequester = filteredSubtitlesFocusRequester,
                        filteredSubtitlesTabFocusRequester = filteredSubtitlesTabFocusRequester
                    )
                }
                SettingsTab.DASHBOARD_CUSTOMIZATION -> {
                    DashboardCustomizationTabContent()
                }
                SettingsTab.LOGOUT -> {
                    LogoutTabContent(
                        onLogout = onLogout,
                        logoutButtonFocusRequester = logoutButtonFocusRequester,
                        logoutTabFocusRequester = logoutTabFocusRequester
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SettingsTabButton(
    tab: SettingsTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .animatedPosterBorder(
                shape = RoundedCornerShape(12.dp),
                interactionSource = interactionSource
            )
            .tvIconFocusIndicator()
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused

                // Play sound when gaining focus (not when losing focus)
                if (!wasFocused && focusState.isFocused) {
                    android.util.Log.d("SettingsScreen", "Tab button gained focus - playing MOVE sound")
                    soundManager.playSound(SoundManager.Sound.MOVE)
                }
            }
            .clickable {
                android.util.Log.d("SettingsScreen", "Tab button clicked - playing CLICK sound")
                soundManager.playSound(SoundManager.Sound.CLICK)
                onClick()
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF8B5CF6) else Color(0xFF8B5CF6).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = tab.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.White else Color(0xFF8B5CF6),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ProfanityFilterTabContent(
    currentProfile: Profile?,
    isPremium: Boolean,
    onProfanityFilterChange: (ProfanityFilterLevel) -> Unit,
    onMuteDurationChange: (Int) -> Unit,
    onAddCustomProfanity: (String) -> Unit,
    onAddWhitelistWord: (String) -> Unit,
    onRemoveCustomProfanity: (String) -> Unit,
    onRemoveWhitelistWord: (String) -> Unit,
    onUpgradeClick: () -> Unit = {},
    profanityFilterFocusRequester: FocusRequester,
    profanityFilterTabFocusRequester: FocusRequester,
    upgradeButtonFocusRequester: FocusRequester? = null,
    sidebarFocusRequester: FocusRequester? = null
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        currentProfile?.let { profile ->
            // Profanity Filter Setting
            item {
                ProfanityFilterSetting(
                    currentLevel = profile.profanityFilterLevel,
                    onLevelChange = onProfanityFilterChange,
                    isPremium = isPremium,
                    currentProfile = profile,
                    profanityFilterFocusRequester = profanityFilterFocusRequester,
                    profanityFilterTabFocusRequester = profanityFilterTabFocusRequester
                )
            }

            // Custom Profanity Settings
            item {
                CustomProfanitySetting(
                    title = "Add Custom Profanity",
                    description = "Add words to be filtered regardless of filter level",
                    placeholder = "Enter word to add to filter...",
                    onSubmit = if (isPremium) onAddCustomProfanity else { _ -> },
                    onRemoveWord = if (isPremium) onRemoveCustomProfanity else { _ -> },
                    isPremium = isPremium, // Use dynamic premium state
                    existingWords = currentProfile?.customFilteredWords ?: emptyList()
                )
            }

            item {
                WhitelistProfanitySetting(
                    title = "Whitelist Profanity",
                    description = "Remove words from the profanity filter",
                    placeholder = "Enter word to remove from filter...",
                    onSubmit = if (isPremium) onAddWhitelistWord else { _ -> },
                    onRemoveWord = if (isPremium) onRemoveWhitelistWord else { _ -> },
                    isPremium = isPremium, // Use dynamic premium state
                    existingWords = currentProfile?.whitelistedWords ?: emptyList()
                )
            }

            // Audio Mute Duration
            item {
                MuteDurationSetting(
                    currentDuration = profile.audioMuteDuration,
                    onDurationChange = if (isPremium) onMuteDurationChange else { _ -> },
                    isPremium = isPremium // Use dynamic premium state
                )
            }

        }
    }
}

@Composable
private fun FilteredSubtitlesTabContent(
    filteredSubtitlesFocusRequester: FocusRequester,
    filteredSubtitlesTabFocusRequester: FocusRequester
) {
    val context = LocalContext.current
    val groupedMovies = remember { mutableStateOf<List<GroupedMovie>>(emptyList()) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // Load and group subtitle files by movie
    LaunchedEffect(Unit) {
        try {
            val subtitlesDir = File(context.filesDir, "filtered_subtitles")
            if (subtitlesDir.exists() && subtitlesDir.isDirectory) {
                val files = subtitlesDir.listFiles { file ->
                    file.isFile && file.name.endsWith("_filtered.srt")
                } ?: emptyArray()

                // Group files by contentId (movie)
                val grouped = files.groupBy { file ->
                    // Parse filename format: {contentId}_{filterLevel}_{baseName}_filtered.srt
                    val parts = file.nameWithoutExtension.split("_")
                    if (parts.size >= 2) {
                        parts[0] // contentId is the first part
                    } else {
                        file.nameWithoutExtension // fallback
                    }
                }.mapNotNull { (contentId, movieFiles) ->
                    if (movieFiles.isNotEmpty()) {
                        // Extract movie title from the first file's baseName
                        val firstFile = movieFiles.first()
                        val parts = firstFile.nameWithoutExtension.split("_")
                        val movieTitle = if (parts.size >= 3) {
                            // Remove contentId, filterLevel, and "filtered" suffix
                            parts.drop(2).joinToString(" ").replace("filtered", "").trim()
                        } else {
                            firstFile.nameWithoutExtension.replace("_filtered", "").replace("_", " ")
                        }

                        GroupedMovie(
                            contentId = contentId,
                            movieTitle = movieTitle.ifEmpty { "Unknown Movie" },
                            filterLevelsCount = movieFiles.size,
                            lastModified = movieFiles.maxOf { it.lastModified() },
                            totalSize = movieFiles.sumOf { it.length() },
                            files = movieFiles
                        )
                    } else null
                }.sortedByDescending { it.lastModified }

                groupedMovies.value = grouped
                android.util.Log.d("SettingsScreen", "Found ${files.size} filtered subtitle files grouped into ${grouped.size} movies")
            } else {
                android.util.Log.d("SettingsScreen", "Filtered subtitles directory not found")
                groupedMovies.value = emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsScreen", "Error loading filtered subtitles: ${e.message}", e)
            groupedMovies.value = emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Saved Filtered Subtitles",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Here you can view and manage all saved filtered subtitle files from your viewing sessions.",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Location: /data/user/0/com.purestream/files/filtered_subtitles/",
                fontSize = 12.sp,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Display grouped movies
        if (groupedMovies.value.isNotEmpty()) {
            items(groupedMovies.value.size) { index ->
                val movie = groupedMovies.value[index]
                FilteredSubtitleItem(
                    title = movie.movieTitle,
                    date = dateFormat.format(Date(movie.lastModified)),
                    fileSize = "${movie.totalSize / 1024}KB (${movie.filterLevelsCount} filter levels)",
                    filePath = "${movie.contentId} (${movie.filterLevelsCount} files)",
                    onDelete = {
                        try {
                            android.util.Log.d("SettingsScreen", "Removing all analysis for movie: ${movie.movieTitle} (contentId: ${movie.contentId})")

                            // Get the SubtitleAnalysisRepository to properly delete all analysis
                            val database = com.purestream.data.database.AppDatabase.getDatabase(context)
                            val subtitleAnalysisRepository = com.purestream.data.repository.SubtitleAnalysisRepository(database, context)

                            // Use coroutine to call suspend function
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    // This will delete all database entries and associated files for this contentId
                                    subtitleAnalysisRepository.deleteAnalysisForContent(movie.contentId)
                                    android.util.Log.d("SettingsScreen", "Successfully removed all analysis for ${movie.movieTitle}")

                                    // Refresh the list on the main thread
                                    withContext(Dispatchers.Main) {
                                        // Reload the files to refresh the UI
                                        val subtitlesDir = File(context.filesDir, "filtered_subtitles")
                                        if (subtitlesDir.exists()) {
                                            val files = subtitlesDir.listFiles { file ->
                                                file.isFile && file.name.endsWith("_filtered.srt")
                                            } ?: emptyArray()

                                            val grouped = files.groupBy { file ->
                                                val parts = file.nameWithoutExtension.split("_")
                                                if (parts.size >= 2) parts[0] else file.nameWithoutExtension
                                            }.mapNotNull { (contentId, movieFiles) ->
                                                if (movieFiles.isNotEmpty()) {
                                                    val firstFile = movieFiles.first()
                                                    val parts = firstFile.nameWithoutExtension.split("_")
                                                    val movieTitle = if (parts.size >= 3) {
                                                        parts.drop(2).joinToString(" ").replace("filtered", "").trim()
                                                    } else {
                                                        firstFile.nameWithoutExtension.replace("_filtered", "").replace("_", " ")
                                                    }

                                                    GroupedMovie(
                                                        contentId = contentId,
                                                        movieTitle = movieTitle.ifEmpty { "Unknown Movie" },
                                                        filterLevelsCount = movieFiles.size,
                                                        lastModified = movieFiles.maxOf { it.lastModified() },
                                                        totalSize = movieFiles.sumOf { it.length() },
                                                        files = movieFiles
                                                    )
                                                } else null
                                            }.sortedByDescending { it.lastModified }

                                            groupedMovies.value = grouped
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("SettingsScreen", "Error removing analysis for ${movie.movieTitle}: ${e.message}", e)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SettingsScreen", "Error setting up removal for ${movie.movieTitle}: ${e.message}", e)
                        }
                    },
                    modifier = if (index == 0) {
                        Modifier
                            .focusRequester(filteredSubtitlesFocusRequester)
                            .focusProperties {
                                up = filteredSubtitlesTabFocusRequester
                            }
                    } else {
                        Modifier.focusProperties {
                            up = filteredSubtitlesTabFocusRequester
                        }
                    }
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No filtered subtitles yet",
                            fontSize = 16.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Start watching content to see filtered subtitles here",
                            fontSize = 14.sp,
                            color = TextTertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCustomizationTabContent() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = "Dashboard Customization",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Personalize your Pure Stream dashboard with powerful features coming soon.",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Coming Soon Features Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = BackgroundSecondary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Header with Coming Soon badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "Upcoming Features",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Coming Soon",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }

                    // Summarized features list
                    val features = listOf(
                        "AI Curated Dashboard" to "Smart content recommendations based on your viewing history",
                        "Plex Collections Import" to "Import and filter your existing Plex collections",
                        "Advanced Customization" to "Personalized dashboard layouts and themes"
                    )

                    features.forEach { (title, description) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = description,
                                    fontSize = 11.sp,
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


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FilteredSubtitleItem(
    title: String,
    date: String,
    fileSize: String,
    filePath: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animatedPosterBorder(
                shape = RoundedCornerShape(12.dp),
                interactionSource = interactionSource
            )
            .tvIconFocusIndicator(),
        colors = CardDefaults.cardColors(
            containerColor = BackgroundSecondary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = "Filtered on $date • $fileSize",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = filePath,
                    fontSize = 10.sp,
                    color = TextTertiary,
                    maxLines = 1
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val deleteInteractionSource = remember { MutableInteractionSource() }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .background(
                            color = getAnimatedButtonBackgroundColor(
                                interactionSource = deleteInteractionSource,
                                defaultColor = Color(0xFFDC2626)
                            ),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .hoverable(deleteInteractionSource)
                        .focusable(interactionSource = deleteInteractionSource)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LogoutTabContent(
    onLogout: () -> Unit,
    logoutButtonFocusRequester: FocusRequester,
    logoutTabFocusRequester: FocusRequester
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Logout,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "Logout from PureStream",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "You will be signed out of your Plex account and returned to the login screen.",
                fontSize = 16.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.7f)
            )

            val logoutInteractionSource = remember { MutableInteractionSource() }
            val logoutButtonBackgroundColor = getAnimatedButtonBackgroundColor(
                interactionSource = logoutInteractionSource,
                defaultColor = Color(0xFFDC2626)
            )

            Button(
                onClick = onLogout,
                modifier = Modifier
                    .focusRequester(logoutButtonFocusRequester)
                    .focusProperties {
                        up = logoutTabFocusRequester
                    }
                    .hoverable(logoutInteractionSource)
                    .focusable(interactionSource = logoutInteractionSource)
                    .tvButtonFocus(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = logoutButtonBackgroundColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Confirm Logout",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = BackgroundCard
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            content()
        }
    }
}

@Composable
private fun ProfileInfoCard(profile: Profile, isPremium: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = BackgroundSecondary,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp)
    ) {
        // Profile Avatar
        val context = LocalContext.current
        val avatarResourceId = context.resources.getIdentifier(
            profile.avatarImage,
            "drawable",
            context.packageName
        )

        if (avatarResourceId != 0) {
            Image(
                painter = painterResource(id = avatarResourceId),
                contentDescription = "${profile.name} Avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback to text avatar if image not found
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = Color(0xFF6366F1),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.name.take(2).uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = profile.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${profile.profileType.name.lowercase().replaceFirstChar { it.uppercase() }} Profile",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                // Pro badge
                if (isPremium) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFF59E0B),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PRO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// Helper function to determine if filter level can be edited
private fun isFilterLevelEditable(profile: Profile?, isPremium: Boolean): Boolean {
    // Child profiles are always locked to STRICT
    if (profile?.profileType == ProfileType.CHILD) return false
    // Free users are locked to MILD
    if (!isPremium) return false
    // Premium adult users can change filter levels
    return true
}

// Helper function to get the restriction message
private fun getFilterRestrictionMessage(profile: Profile?, isPremium: Boolean): String {
    return when {
        profile?.profileType == ProfileType.CHILD -> "Child profiles are locked to Strict filtering for safety"
        !isPremium -> "Upgrade to Pro to customize filter levels"
        else -> ""
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ProfanityFilterSetting(
    currentLevel: ProfanityFilterLevel,
    onLevelChange: (ProfanityFilterLevel) -> Unit,
    isPremium: Boolean = true,
    currentProfile: Profile? = null,
    profanityFilterFocusRequester: FocusRequester? = null,
    profanityFilterTabFocusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val isEditable = isFilterLevelEditable(currentProfile, isPremium)
    val restrictionMessage = getFilterRestrictionMessage(currentProfile, isPremium)

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Profanity Filter Level",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isEditable) TextPrimary else TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (!isEditable) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked feature",
                    tint = if (currentProfile?.profileType == ProfileType.CHILD) Color(0xFFEF4444) else Color(0xFF8B5CF6),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (!isEditable && restrictionMessage.isNotEmpty()) {
            Text(
                text = restrictionMessage,
                fontSize = 12.sp,
                color = if (currentProfile?.profileType == ProfileType.CHILD) Color(0xFFEF4444) else Color(0xFF8B5CF6),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ProfanityFilterLevel.values().forEachIndexed { index, level ->
                FilterLevelChip(
                    level = level,
                    isSelected = currentLevel == level,
                    onClick = { if (isEditable) onLevelChange(level) },
                    enabled = isEditable,
                    modifier = if (index == 0 && profanityFilterFocusRequester != null && profanityFilterTabFocusRequester != null) {
                        Modifier
                            .focusRequester(profanityFilterFocusRequester)
                            .focusProperties {
                                up = profanityFilterTabFocusRequester
                            }
                    } else if (profanityFilterTabFocusRequester != null) {
                        Modifier.focusProperties {
                            up = profanityFilterTabFocusRequester
                        }
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FilterLevelChip(
    level: ProfanityFilterLevel,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .animatedPosterBorder(
                shape = RoundedCornerShape(20.dp),
                interactionSource = interactionSource
            )
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused

                // Play sound when gaining focus (not when losing focus)
                if (!wasFocused && focusState.isFocused) {
                    android.util.Log.d("SettingsScreen", "Filter level chip gained focus - playing MOVE sound")
                    soundManager.playSound(SoundManager.Sound.MOVE)
                }
            }
            .clickable {
                if (enabled) {
                    android.util.Log.d("SettingsScreen", "Filter level chip clicked - playing CLICK sound")
                    soundManager.playSound(SoundManager.Sound.CLICK)
                    onClick()
                }
            }
            .tvButtonFocus(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !enabled && isSelected -> Color(0xFF8B5CF6).copy(alpha = 0.7f) // Show selection even when disabled
                !enabled -> BackgroundSecondary.copy(alpha = 0.5f)
                isSelected -> Color(0xFF8B5CF6) // Purple for selected
                else -> BackgroundSecondary
            }
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = level.name,
            fontSize = 12.sp,
            color = when {
                !enabled && isSelected -> Color.White.copy(alpha = 0.8f) // Show selected text even when disabled
                !enabled -> TextTertiary
                isSelected -> Color.White
                else -> TextSecondary
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MuteDurationSetting(
    currentDuration: Int,
    onDurationChange: (Int) -> Unit,
    isPremium: Boolean = true
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Audio Mute Duration",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPremium) TextPrimary else TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (!isPremium) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked feature",
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Text(
            text = if (isPremium) "How long to mute audio when profanity is detected: ${currentDuration}ms" else "Upgrade to Pro to customize mute duration (locked at ${currentDuration}ms)",
            fontSize = 12.sp,
            color = if (isPremium) TextSecondary else Color(0xFF8B5CF6),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1000, 2000, 3000, 5000).forEach { duration ->
                DurationChip(
                    duration = duration,
                    isSelected = currentDuration == duration,
                    onClick = { if (isPremium) onDurationChange(duration) },
                    enabled = isPremium
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DurationChip(
    duration: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .animatedPosterBorder(
                shape = RoundedCornerShape(16.dp),
                interactionSource = interactionSource
            )
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused

                // Play sound when gaining focus (not when losing focus)
                if (!wasFocused && focusState.isFocused) {
                    android.util.Log.d("SettingsScreen", "Duration chip gained focus - playing MOVE sound")
                    soundManager.playSound(SoundManager.Sound.MOVE)
                }
            }
            .clickable {
                if (enabled) {
                    android.util.Log.d("SettingsScreen", "Duration chip clicked - playing CLICK sound")
                    soundManager.playSound(SoundManager.Sound.CLICK)
                    onClick()
                }
            }
            .tvButtonFocus(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> if (enabled) Color(0xFF8B5CF6) else Color(0xFF8B5CF6).copy(alpha = 0.7f)
                !enabled -> BackgroundSecondary.copy(alpha = 0.5f)
                else -> BackgroundSecondary
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = "${duration}ms",
            fontSize = 12.sp,
            color = when {
                !enabled && isSelected -> Color.White.copy(alpha = 0.8f) // Show selected text even when disabled
                !enabled -> TextTertiary
                isSelected -> Color.White
                else -> TextSecondary
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun LibrarySelectionSection(
    availableLibraries: List<PlexLibrary>,
    selectedLibraries: List<String>,
    onSelectionChange: (List<String>) -> Unit
) {
    if (availableLibraries.isEmpty()) {
        Text(
            text = "No Plex libraries found. Please connect to a Plex server.",
            fontSize = 14.sp,
            color = TextSecondary
        )
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Select which libraries to display in PureStream:",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            availableLibraries.forEach { library ->
                LibraryCheckboxItem(
                    library = library,
                    isSelected = selectedLibraries.contains(library.key),
                    onSelectionChange = { isSelected ->
                        val newSelection = if (isSelected) {
                            selectedLibraries + library.key
                        } else {
                            selectedLibraries - library.key
                        }
                        onSelectionChange(newSelection)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LibraryCheckboxItem(
    library: PlexLibrary,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onSelectionChange(!isSelected) }
            .background(
                color = if (isFocused) BackgroundSecondary else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onSelectionChange,
            colors = CheckboxDefaults.colors(
                checkedColor = NetflixRed,
                uncheckedColor = TextSecondary
            )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = library.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = library.type.replaceFirstChar { it.uppercase() } + " Library",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onCheckedChange(!checked) }
            .background(
                color = if (isFocused) BackgroundSecondary else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NetflixRed,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = BackgroundSecondary
            )
        )
    }
}

@Composable
private fun ServerInfoCard(
    serverUrl: String,
    isPremium: Boolean,
    lastSync: Long
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = BackgroundSecondary,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Server: ",
                fontSize = 14.sp,
                color = TextSecondary
            )
            Text(
                text = if (serverUrl.isNotEmpty()) serverUrl else "Not connected",
                fontSize = 14.sp,
                color = TextPrimary
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Premium: ",
                fontSize = 14.sp,
                color = TextSecondary
            )
            Text(
                text = if (isPremium) "Active" else "Free",
                fontSize = 14.sp,
                color = if (isPremium) RatingGreen else TextPrimary
            )
        }

        if (lastSync > 0) {
            Text(
                text = "Last sync: ${java.text.SimpleDateFormat("MMM dd, yyyy HH:mm").format(java.util.Date(lastSync))}",
                fontSize = 12.sp,
                color = TextTertiary
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LogoutButton(onLogout: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val animatedBackgroundColor = getAnimatedButtonBackgroundColor(
        interactionSource = interactionSource,
        defaultColor = Color(0xFFDC2626) // Red background for logout
    )

    // Logout Button
    Button(
        onClick = { showConfirmDialog = true },
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { isFocused = it.isFocused }
            .tvButtonFocus(),
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedBackgroundColor,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Logout from Plex",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // Confirmation Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = BackgroundCard,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD97706), // Orange warning color
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Logout Confirmation",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to logout from your Plex account? You will need to sign in again to access your media libraries.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    )
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        color = TextSecondary
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ProUpgradeSection(
    isPremium: Boolean,
    onUpgradeClick: () -> Unit,
    upgradeButtonFocusRequester: FocusRequester? = null,
    sidebarFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    isMobile: Boolean = false
) {
    if (isPremium) {
        // Show premium status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF10B981) // Green background for premium
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "PureStream Pro",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "You have access to all premium features!",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    } else {
        // Show upgrade plans - conditional layout for mobile vs TV
        if (isMobile) {
            // Mobile: Column layout (stack cards vertically)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Free Plan Card
            Card(
                modifier = if (isMobile) Modifier.fillMaxWidth() else Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = BackgroundCard
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Free Plan",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "Perfect for getting started with content filtering",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "$0/month",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Free features
                    val freeFeatures = listOf(
                        "1 Adult Profile",
                        "Basic Profanity Filtering",
                        "AI Content Analysis",
                        "Access to Your Full Plex Library",
                        "Smart Recommendations",
                        "No Ads. Ever."
                    )

                    freeFeatures.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = feature,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Pro Plan Card
            Card(
                modifier = if (isMobile) Modifier.fillMaxWidth() else Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                ),
                border = BorderStroke(2.dp, Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // Most Popular badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFF59E0B),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "MOST POPULAR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PureStream Pro",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "The complete family streaming solution",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "$4.99/month or $49.99/year (17% annual savings)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Everything in Free plus section
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFF8B5CF6).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Everything in Free, plus:",
                            fontSize = 12.sp,
                            color = Color(0xFF8B5CF6),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pro features
                    val proFeatures = listOf(
                        "Unlimited User Profiles (Adult & Child)",
                        "Customizable Filtering Levels (None to Strict)",
                        "Custom Word Blacklist & Whitelist",
                        "Advanced Content Insights & Detailed Reports",
                        "Priority Support & Early Access Features",
                        "Parental Controls Dashboard"
                    )

                    proFeatures.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = feature,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Upgrade Button
                    var isFocused by remember { mutableStateOf(false) }

                    Button(
                        onClick = onUpgradeClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusable()
                            .onFocusChanged { isFocused = it.isFocused }
                            .tvButtonFocus()
                            .run {
                                if (upgradeButtonFocusRequester != null) {
                                    this.focusRequester(upgradeButtonFocusRequester)
                                        .focusProperties {
                                            if (contentFocusRequester != null) {
                                                up = contentFocusRequester
                                            }
                                            if (sidebarFocusRequester != null) {
                                                left = sidebarFocusRequester
                                            }
                                        }
                                } else {
                                    this
                                }
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Upgrade to Pro",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // TV: Row layout (horizontal cards) - duplicate the existing card structure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Free Plan Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = BackgroundCard
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Free Plan",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = "Perfect for getting started with content filtering",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = "$0/month",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Free features
                        val freeFeatures = listOf(
                            "1 Adult Profile",
                            "Basic Profanity Filtering", 
                            "AI Content Analysis",
                            "Access to Your Full Plex Library",
                            "Smart Recommendations",
                            "No Ads. Ever."
                        )

                        freeFeatures.forEach { feature ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = feature,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                // Pro Plan Card - same as mobile version but with weight(1f)
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(2.dp, Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pure Stream Pro",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Most Popular Badge
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFFFBBF24),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Most Popular",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }

                        Text(
                            text = "Complete family protection with unlimited profiles",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = "$4.99/month", 
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Pro features
                        val proFeatures = listOf(
                            "Everything in Free Plan",
                            "Unlimited Adult & Child Profiles",
                            "Advanced Profanity Filtering",
                            "Custom Word Lists & Whitelists", 
                            "Audio Muting Technology",
                            "Enhanced Content Analysis",
                            "Priority Support"
                        )

                        proFeatures.forEach { feature ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = feature,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Upgrade Button
                        Button(
                            onClick = onUpgradeClick,
                            modifier = upgradeButtonFocusRequester?.let { focusRequester ->
                                Modifier.focusRequester(focusRequester).focusProperties {
                                    up = contentFocusRequester
                                    left = sidebarFocusRequester
                                }
                            } ?: Modifier,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B5CF6),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Upgrade to Pro",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PureStreamProTabContent(
    isPremium: Boolean,
    onUpgradeClick: () -> Unit,
    onFreeVersionClick: () -> Unit = {},
    upgradeButtonFocusRequester: FocusRequester? = null,
    sidebarFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    isMobile: Boolean = false
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            ProUpgradeSection(
                isPremium = isPremium,
                onUpgradeClick = onUpgradeClick,
                upgradeButtonFocusRequester = upgradeButtonFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester,
                contentFocusRequester = contentFocusRequester,
                isMobile = isMobile
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CustomProfanitySetting(
    title: String,
    description: String,
    placeholder: String,
    onSubmit: (String) -> Unit,
    onRemoveWord: (String) -> Unit,
    isPremium: Boolean = true,
    existingWords: List<String> = emptyList()
) {
    var inputText by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    var customWords by remember(existingWords) {
        mutableStateOf(existingWords) // Initialize with existing words
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPremium) TextPrimary else TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            if (!isPremium) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked feature",
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Text(
            text = if (isPremium) description else "Upgrade to Pro to add custom words",
            fontSize = 12.sp,
            color = if (isPremium) TextSecondary else Color(0xFF8B5CF6),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val textFieldInteractionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .animatedPosterBorder(
                        shape = RoundedCornerShape(8.dp),
                        interactionSource = textFieldInteractionSource
                    )
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { if (isPremium) inputText = it },
                    placeholder = {
                        Text(
                            text = if (isPremium) placeholder else "Pro feature locked",
                            fontSize = 14.sp,
                            color = TextTertiary
                        )
                    },
                    singleLine = true,
                    enabled = isPremium,
                    interactionSource = textFieldInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .hoverable(textFieldInteractionSource) // Allow hovering even when disabled
                        .onFocusChanged { isFocused = it.isFocused },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White.copy(alpha = 0.3f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        disabledBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        disabledTextColor = TextTertiary,
                        cursorColor = NetflixRed
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            val addButtonInteractionSource = remember { MutableInteractionSource() }
            val addButtonBackgroundColor = getAnimatedButtonBackgroundColor(
                interactionSource = addButtonInteractionSource,
                defaultColor = NetflixRed
            )

            Button(
                onClick = {
                    if (inputText.isNotBlank() && isPremium) {
                        val word = inputText.trim().lowercase()
                        customWords = customWords + word
                        onSubmit(word)
                        inputText = ""
                    }
                },
                enabled = isPremium && inputText.isNotBlank(),
                modifier = Modifier
                    .hoverable(addButtonInteractionSource)
                    .focusable(interactionSource = addButtonInteractionSource)
                    .tvButtonFocus(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPremium && inputText.isNotBlank()) addButtonBackgroundColor else BackgroundSecondary,
                    contentColor = if (isPremium && inputText.isNotBlank()) Color.White else TextTertiary,
                    disabledContainerColor = BackgroundSecondary,
                    disabledContentColor = TextTertiary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Add",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Display added words as bubbles
        if (customWords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            WordBubblesSection(
                words = customWords,
                onRemoveWord = { word ->
                    customWords = customWords.filter { it != word }
                    onRemoveWord(word) // Also call the callback to persist the change
                },
                bubbleColor = NetflixRed
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WhitelistProfanitySetting(
    title: String,
    description: String,
    placeholder: String,
    onSubmit: (String) -> Unit,
    onRemoveWord: (String) -> Unit,
    isPremium: Boolean = true,
    existingWords: List<String> = emptyList()
) {
    var inputText by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    var whitelistedWords by remember(existingWords) {
        mutableStateOf(existingWords) // Initialize with existing words
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPremium) TextPrimary else TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            if (!isPremium) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked feature",
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Text(
            text = if (isPremium) description else "Upgrade to Pro to whitelist words",
            fontSize = 12.sp,
            color = if (isPremium) TextSecondary else Color(0xFF8B5CF6),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val textFieldInteractionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .animatedPosterBorder(
                        shape = RoundedCornerShape(8.dp),
                        interactionSource = textFieldInteractionSource
                    )
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { if (isPremium) inputText = it },
                    placeholder = {
                        Text(
                            text = if (isPremium) placeholder else "Pro feature locked",
                            fontSize = 14.sp,
                            color = TextTertiary
                        )
                    },
                    singleLine = true,
                    enabled = isPremium,
                    interactionSource = textFieldInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .hoverable(textFieldInteractionSource) // Allow hovering even when disabled
                        .onFocusChanged { isFocused = it.isFocused },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White.copy(alpha = 0.3f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        disabledBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        disabledTextColor = TextTertiary,
                        cursorColor = Color(0xFF10B981)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            val removeButtonInteractionSource = remember { MutableInteractionSource() }
            val removeButtonBackgroundColor = getAnimatedButtonBackgroundColor(
                interactionSource = removeButtonInteractionSource,
                defaultColor = Color(0xFF10B981)
            )

            Button(
                onClick = {
                    if (inputText.isNotBlank() && isPremium) {
                        val word = inputText.trim().lowercase()
                        whitelistedWords = whitelistedWords + word
                        onSubmit(word)
                        inputText = ""
                    }
                },
                enabled = isPremium && inputText.isNotBlank(),
                modifier = Modifier
                    .hoverable(removeButtonInteractionSource)
                    .focusable(interactionSource = removeButtonInteractionSource)
                    .tvButtonFocus(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPremium && inputText.isNotBlank()) removeButtonBackgroundColor else BackgroundSecondary,
                    contentColor = if (isPremium && inputText.isNotBlank()) Color.White else TextTertiary,
                    disabledContainerColor = BackgroundSecondary,
                    disabledContentColor = TextTertiary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Remove",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Display whitelisted words as bubbles
        if (whitelistedWords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            WordBubblesSection(
                words = whitelistedWords,
                onRemoveWord = { word ->
                    whitelistedWords = whitelistedWords.filter { it != word }
                    onRemoveWord(word) // Also call the callback to persist the change
                },
                bubbleColor = Color(0xFF10B981)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WordBubblesSection(
    words: List<String>,
    onRemoveWord: (String) -> Unit,
    bubbleColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        words.forEach { word ->
            WordBubble(
                word = word,
                onRemove = { onRemoveWord(word) },
                bubbleColor = bubbleColor
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WordBubble(
    word: String,
    onRemove: () -> Unit,
    bubbleColor: Color
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .tvButtonFocus(),
        colors = CardDefaults.cardColors(
            containerColor = bubbleColor
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Word text
            Text(
                text = word,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Remove button (trash icon)
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove word",
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DummyProToggle(
    isPremium: Boolean,
    onToggle: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onToggle(!isPremium) }
            .background(
                color = if (isFocused) BackgroundSecondary else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Demo Pro Toggle (Temporary)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF59E0B) // Orange to indicate it's temporary
            )
            Text(
                text = "Toggle to see Free vs Pro features",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        Switch(
            checked = isPremium,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF8B5CF6), // Purple for Pro
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = BackgroundSecondary
            )
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    subtitle: String,
    price: String,
    priceSubtext: String,
    icon: ImageVector,
    iconColor: Color,
    features: List<String>,
    buttonText: String,
    buttonColor: Color,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(300.dp),
        colors = CardDefaults.cardColors(
            containerColor = BackgroundSecondary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Price
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = price,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
                Text(
                    text = priceSubtext,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Features List
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                features.forEach { feature ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = feature,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button
            Button(
                onClick = onButtonClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = buttonText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}


@Composable
private fun ComingSoonFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    features: List<String>,
    iconColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = BackgroundSecondary
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Coming Soon Badge
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Coming Soon",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF59E0B)
                    )
                }
            }

            // Description
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Features List
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                features.forEach { feature ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = feature,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}