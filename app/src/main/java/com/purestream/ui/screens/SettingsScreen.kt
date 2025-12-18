package com.purestream.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.purestream.ui.theme.getAnimatedButtonBackgroundColor
import com.purestream.ui.theme.animatedPosterBorder
import com.purestream.data.model.*
import com.purestream.data.repository.ProfileRepository
import com.purestream.data.repository.PlexRepository
import com.purestream.ui.components.LeftSidebar
import com.purestream.ui.components.BottomNavigation
import com.purestream.ui.theme.*
import com.purestream.ui.theme.tvButtonFocus
import com.purestream.data.manager.PremiumStatusManager
import com.purestream.data.manager.PremiumStatusState
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
import com.purestream.utils.rememberIsMobile

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
    onMonthlyUpgradeClick: () -> Unit = {},
    onAnnualUpgradeClick: () -> Unit = {},
    onFreeVersionClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isMobile = rememberIsMobile()
    val context = LocalContext.current
    
    // Defensive premium status management - prevents crashes from cross-platform sync issues
    val premiumStatusManager = remember { PremiumStatusManager.getInstance(context) }
    val premiumStatusState by premiumStatusManager.premiumStatus.collectAsState()
    
    // Determine safe premium status with fallback protection
    val safePremiumStatus = try {
        val currentState = premiumStatusState // Store in local variable for smart casting
        when (currentState) {
            is PremiumStatusState.Premium -> true
            is PremiumStatusState.Free -> false
            is PremiumStatusState.Loading -> appSettings?.isPremium ?: false // Fallback during loading
            is PremiumStatusState.Unknown -> appSettings?.isPremium ?: false // Fallback if unknown
            is PremiumStatusState.Error -> {
                android.util.Log.w("SettingsScreen", "Premium status error: ${currentState.message}")
                appSettings?.isPremium ?: false // Fallback on error
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("SettingsScreen", "Error determining premium status", e)
        appSettings?.isPremium ?: false // Ultimate fallback
    }
    
    // Trigger premium status refresh if needed (but don't block UI)
    LaunchedEffect(Unit) {
        try {
            premiumStatusManager.refreshPremiumStatus()
        } catch (e: Exception) {
            android.util.Log.e("SettingsScreen", "Error refreshing premium status", e)
            // Continue without blocking UI
        }
    }
    
    // Focus management
    val sidebarFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() } // For Pure Stream Pro tab
    val upgradeButtonFocusRequester = remember { FocusRequester() }
    val profanityFilterTabFocusRequester = remember { FocusRequester() } // For Profanity Filter tab
    val profanityFilterFocusRequester = remember { FocusRequester() } // For filter buttons
    val filteredSubtitlesTabFocusRequester = remember { FocusRequester() } // For Filtered Subtitles tab
    val filteredSubtitlesFocusRequester = remember { FocusRequester() } // For filtered subtitles content
    val dashboardCustomizationTabFocusRequester = remember { FocusRequester() } // For Dashboard Customization tab
    val dashboardCustomizationFocusRequester = remember { FocusRequester() } // For first collection
    val logoutTabFocusRequester = remember { FocusRequester() } // For Logout tab
    val logoutButtonFocusRequester = remember { FocusRequester() } // For logout button

    // State to trigger scroll reset in Dashboard Customization
    var dashboardScrollResetTrigger by remember { mutableStateOf(0) }

    // Note: Premium status is now managed defensively above with safePremiumStatus

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixDarkGray)
    ) {

        if (isMobile) {
            // Mobile: Column layout with bottom navigation
            Column(modifier = Modifier.fillMaxSize()) {
                // Main content (takes available space)
                TabbedSettingsContent(
                currentProfile = currentProfile,
                appSettings = appSettings,
                safePremiumStatus = safePremiumStatus,
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
                onMonthlyUpgradeClick = onMonthlyUpgradeClick,
                onAnnualUpgradeClick = onAnnualUpgradeClick,
                onFreeVersionClick = onFreeVersionClick,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester,
                upgradeButtonFocusRequester = upgradeButtonFocusRequester,
                dashboardScrollResetTrigger = dashboardScrollResetTrigger,
                onDashboardScrollReset = { dashboardScrollResetTrigger++ },
                profanityFilterTabFocusRequester = profanityFilterTabFocusRequester,
                profanityFilterFocusRequester = profanityFilterFocusRequester,
                filteredSubtitlesTabFocusRequester = filteredSubtitlesTabFocusRequester,
                filteredSubtitlesFocusRequester = filteredSubtitlesFocusRequester,
                dashboardCustomizationTabFocusRequester = dashboardCustomizationTabFocusRequester,
                dashboardCustomizationFocusRequester = dashboardCustomizationFocusRequester,
                logoutTabFocusRequester = logoutTabFocusRequester,
                logoutButtonFocusRequester = logoutButtonFocusRequester,
                isMobile = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Transparent)
            )
            
            // Bottom Navigation for Mobile
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
            // TV: Existing Row layout with sidebar
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
                    safePremiumStatus = safePremiumStatus,
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
                    onMonthlyUpgradeClick = onMonthlyUpgradeClick,
                    onAnnualUpgradeClick = onAnnualUpgradeClick,
                    onFreeVersionClick = onFreeVersionClick,
                    contentFocusRequester = contentFocusRequester,
                    sidebarFocusRequester = sidebarFocusRequester,
                    upgradeButtonFocusRequester = upgradeButtonFocusRequester,
                    dashboardScrollResetTrigger = dashboardScrollResetTrigger,
                    onDashboardScrollReset = { dashboardScrollResetTrigger++ },
                    profanityFilterTabFocusRequester = profanityFilterTabFocusRequester,
                    profanityFilterFocusRequester = profanityFilterFocusRequester,
                    filteredSubtitlesTabFocusRequester = filteredSubtitlesTabFocusRequester,
                    filteredSubtitlesFocusRequester = filteredSubtitlesFocusRequester,
                    dashboardCustomizationTabFocusRequester = dashboardCustomizationTabFocusRequester,
                    dashboardCustomizationFocusRequester = dashboardCustomizationFocusRequester,
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
    safePremiumStatus: Boolean,
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
    onMonthlyUpgradeClick: () -> Unit,
    onAnnualUpgradeClick: () -> Unit,
    onFreeVersionClick: () -> Unit,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    upgradeButtonFocusRequester: FocusRequester,
    profanityFilterTabFocusRequester: FocusRequester,
    profanityFilterFocusRequester: FocusRequester,
    filteredSubtitlesTabFocusRequester: FocusRequester,
    filteredSubtitlesFocusRequester: FocusRequester,
    dashboardCustomizationTabFocusRequester: FocusRequester,
    dashboardCustomizationFocusRequester: FocusRequester,
    logoutTabFocusRequester: FocusRequester,
    logoutButtonFocusRequester: FocusRequester,
    dashboardScrollResetTrigger: Int,
    onDashboardScrollReset: () -> Unit,
    isMobile: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(SettingsTab.PURE_STREAM_PRO) }
    val isPremium = appSettings?.isPremium ?: false

    // Focus the first tab when settings screen loads (TV only)
    LaunchedEffect(Unit) {
        if (!isMobile) {
            kotlinx.coroutines.delay(100) // Small delay to ensure composition is complete
            contentFocusRequester.requestFocus()
        }
    }

    // Handle focus requests when selectedTab changes for immediate navigation (TV only)
    LaunchedEffect(selectedTab) {
        if (!isMobile) {
            kotlinx.coroutines.delay(50) // Small delay to ensure content is composed
            when (selectedTab) {
                SettingsTab.PURE_STREAM_PRO -> {
                    // Double-check premium status to prevent crash
                    val actualIsPremium = appSettings?.isPremium ?: false
                    if (!actualIsPremium && !isPremium) {
                        try {
                            upgradeButtonFocusRequester.requestFocus()
                        } catch (e: IllegalStateException) {
                            android.util.Log.w("SettingsScreen", "Failed to focus upgrade button: ${e.message}")
                        }
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
    }

    Column(
        modifier = modifier
            .padding(horizontal = if (isMobile) 16.dp else 24.dp, vertical = 16.dp)
    ) {
        // Profile Settings Header
        Text(
            text = "Profile Settings",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(top = if (isMobile) 48.dp else 0.dp, bottom = 12.dp)
        )

        // Current Profile Display
        currentProfile?.let { profile ->
            ProfileInfoCard(profile = profile, isPremium = isPremium)

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Tabs Row - Conditional layout for mobile vs TV
        if (isMobile) {
            // Mobile: Horizontal scrollable tabs
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                items(SettingsTab.values().size) { index ->
                    val tab = SettingsTab.values()[index]
                    SettingsTabButton(
                        tab = tab,
                        isSelected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        modifier = Modifier
                    )
                }
            }
        } else {
            // TV: Standard horizontal row with focus management
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
            SettingsTab.values().forEachIndexed { index, tab ->
                SettingsTabButton(
                    tab = tab,
                    isSelected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    modifier = Modifier
                        .focusRequester(
                            when (tab) {
                                SettingsTab.PURE_STREAM_PRO -> contentFocusRequester
                                SettingsTab.PROFANITY_FILTER -> profanityFilterTabFocusRequester
                                SettingsTab.FILTERED_SUBTITLES -> filteredSubtitlesTabFocusRequester
                                SettingsTab.DASHBOARD_CUSTOMIZATION -> dashboardCustomizationTabFocusRequester
                                SettingsTab.LOGOUT -> logoutTabFocusRequester
                                else -> remember { FocusRequester() }
                            }
                        )
                        // FIX: Reset scroll when ANY tab gains focus.
                        // This prevents crashes when navigating from Sidebar -> Tab -> Down to List
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onDashboardScrollReset()
                            }
                        }
                        .focusProperties {
                        if (index == 0) {
                            left = sidebarFocusRequester
                        }
                        // Add down navigation to content for the SELECTED tab (not the current tab button)
                        when (selectedTab) {
                            SettingsTab.PURE_STREAM_PRO -> {
                                if (!isPremium) {
                                    down = upgradeButtonFocusRequester
                                }
                                // If premium, don't set 'down' - focus stays in place
                            }
                            SettingsTab.PROFANITY_FILTER -> {
                                // Navigate to profanity filter buttons
                                down = profanityFilterFocusRequester
                            }
                            SettingsTab.FILTERED_SUBTITLES -> {
                                // Navigate to filtered subtitles content
                                down = filteredSubtitlesFocusRequester
                            }
                            SettingsTab.DASHBOARD_CUSTOMIZATION -> {
                                // Navigate to dashboard customization first collection
                                down = dashboardCustomizationFocusRequester
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
                        isPremium = safePremiumStatus,
                        onMonthlyUpgradeClick = {
                            try {
                                onMonthlyUpgradeClick()
                            } catch (e: Exception) {
                                android.util.Log.e("SettingsScreen", "Error during monthly upgrade", e)
                            }
                        },
                        onAnnualUpgradeClick = {
                            try {
                                onAnnualUpgradeClick()
                            } catch (e: Exception) {
                                android.util.Log.e("SettingsScreen", "Error during annual upgrade", e)
                            }
                        },
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
                        isPremium = safePremiumStatus,
                        onProfanityFilterChange = onProfanityFilterChange,
                        onMuteDurationChange = onMuteDurationChange,
                        onAddCustomProfanity = onAddCustomProfanity,
                        onAddWhitelistWord = onAddWhitelistWord,
                        onRemoveCustomProfanity = onRemoveCustomProfanity,
                        onRemoveWhitelistWord = onRemoveWhitelistWord,
                        onUpgradeClick = onMonthlyUpgradeClick, // Use monthly as default upgrade option
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
                    DashboardCustomizationTabContent(
                        profile = currentProfile,
                        dashboardCustomizationFocusRequester = dashboardCustomizationFocusRequester,
                        dashboardCustomizationTabFocusRequester = dashboardCustomizationTabFocusRequester,
                        scrollResetTrigger = dashboardScrollResetTrigger,
                        isMobile = isMobile,
                        isPremium = safePremiumStatus
                    )
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
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
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
        state = listState,
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
                        Modifier
                            .onKeyEvent { event ->
                                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                    when (event.nativeKeyEvent.keyCode) {
                                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                            if (index > 0) {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(index - 1)
                                                }
                                            }
                                            false
                                        }
                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            if (index < groupedMovies.value.size - 1) {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(index + 1)
                                                }
                                            }
                                            false
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
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
private fun DashboardCustomizationTabContent(
    profile: Profile?,
    dashboardCustomizationFocusRequester: FocusRequester,
    dashboardCustomizationTabFocusRequester: FocusRequester,
    scrollResetTrigger: Int = 0,
    isMobile: Boolean = false,
    isPremium: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Focus requester for the Default button
    val defaultButtonFocusRequester = remember { FocusRequester() }

    // Focus requester for the first collection (separate from default button)
    val firstCollectionFocusRequester = remember { FocusRequester() }

    // FIX 2: Add ListState to control scrolling manually
    val listState = rememberLazyListState()

    // Reset scroll to top whenever the tab gains focus
    LaunchedEffect(scrollResetTrigger) {
        if (scrollResetTrigger > 0) {
            try {
                listState.scrollToItem(0)
            } catch (e: Exception) {
                android.util.Log.e("DashboardCustomization", "Error resetting scroll: ${e.message}")
            }
        }
    }

    // State for all collections (hardcoded + Plex)
    var allCollections by remember { mutableStateOf<List<DashboardCollection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Reorder mode state for TV
    var reorderMode by remember { mutableStateOf(false) }
    var selectedCollectionId by remember { mutableStateOf<String?>(null) }

    // Drag state for mobile
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // FIX 1: State to track which index should grab focus after an update
    var focusTargetIndex by remember { mutableStateOf<Int?>(null) }
    var disabledFocusTargetIndex by remember { mutableStateOf<Int?>(null) }

    // Get repository instances
    val profileRepository = remember { ProfileRepository(context) }
    val plexRepository = remember { PlexRepository() }
    val plexAuthRepository = remember { com.purestream.data.repository.PlexAuthRepository(context) }

    // Load profile collections and Plex collections with improved merging logic
    LaunchedEffect(profile, isPremium, scrollResetTrigger) {
        try {
            // CRITICAL: Always reload fresh from database to avoid stale data
            val freshProfile = profile?.id?.let { profileRepository.getProfileById(it) }

            // CRITICAL: For free users, always reset to defaults first
            val initialCollections = if (!isPremium && freshProfile != null) {
                val currentCollections = freshProfile.dashboardCollections
                val defaultCollections = Profile.getDefaultCollections()

                // Check if collections differ from defaults
                val needsReset = currentCollections.size != defaultCollections.size ||
                    currentCollections.any { current ->
                        val default = defaultCollections.find { it.id == current.id }
                        default == null || current.isEnabled != default.isEnabled || current.order != default.order
                    }

                if (needsReset) {
                    android.util.Log.d("DashboardCustomization", "Free user with custom collections - resetting to defaults")
                    val updatedProfile = freshProfile.copy(dashboardCollections = defaultCollections)
                    profileRepository.updateProfile(updatedProfile)
                    defaultCollections
                } else {
                    currentCollections
                }
            } else {
                // Pro user or no profile - use saved collections (from fresh database read)
                freshProfile?.dashboardCollections ?: profile?.dashboardCollections ?: Profile.getDefaultCollections()
            }

            android.util.Log.d("DashboardCustomization", "Initial collections count: ${initialCollections.size}")

            // 2. Fetch fresh Plex data
            val authToken = plexAuthRepository.getAuthToken()
            android.util.Log.d("DashboardCustomization", "Got auth token: ${if (authToken != null) "YES" else "NO"}")

            val freshPlexCollections = if (authToken != null && plexRepository.getLibrariesWithAuth(authToken).isSuccess) {
                // Get selected libraries from fresh profile
                val selectedLibraries = freshProfile?.selectedLibraries ?: profile?.selectedLibraries ?: emptyList()
                android.util.Log.d("DashboardCustomization", "Selected libraries: $selectedLibraries")

                if (selectedLibraries.isNotEmpty()) {
                    plexRepository.getPlexCollections(selectedLibraries).getOrNull() ?: emptyList()
                } else {
                    emptyList()
                }
            } else {
                android.util.Log.w("DashboardCustomization", "No auth token or connection failed")
                emptyList()
            }

            android.util.Log.d("DashboardCustomization", "Fresh Plex collections count: ${freshPlexCollections.size}")

            // 3. MERGE LOGIC: Combine Saved state with Fresh Data
            // We want to keep the Saved Order, but ensure all Fresh items are present
            val mergedList = mutableListOf<DashboardCollection>()

            // A. Add everything we already know about (preserving order and enabled status)
            mergedList.addAll(initialCollections)

            // B. Process fresh Plex items
            freshPlexCollections.forEach { plexItem ->
                val existingIndex = mergedList.indexOfFirst { it.id == plexItem.id }

                if (existingIndex != -1) {
                    // It exists! Update title and itemCount in case they changed on Plex,
                    // BUT keep the user's saved 'order' and 'isEnabled' status.
                    val existing = mergedList[existingIndex]
                    mergedList[existingIndex] = existing.copy(
                        title = plexItem.title,
                        itemCount = plexItem.itemCount  // Update count from fresh data
                    )
                    android.util.Log.d("DashboardCustomization", "Updated existing: ${plexItem.title} (${plexItem.itemCount} items)")
                } else {
                    // It's a NEW Plex collection not in our save file.
                    // Add it to the end, disabled by default.
                    val nextOrderIndex = (mergedList.maxOfOrNull { it.order } ?: -1) + 1
                    mergedList.add(plexItem.copy(isEnabled = false, order = nextOrderIndex))
                    android.util.Log.d("DashboardCustomization", "Added new collection: ${plexItem.title} (order: $nextOrderIndex)")
                }
            }

            // 4. Final Sort by the 'order' property to ensure UI reflects saved state
            allCollections = mergedList.sortedBy { it.order }
            android.util.Log.d("DashboardCustomization", "Final merged collections count: ${allCollections.size}")

            isLoading = false
        } catch (e: Exception) {
            android.util.Log.e("DashboardCustomization", "Error loading collections: ${e.message}", e)
            // Fallback
            allCollections = profile?.dashboardCollections ?: Profile.getDefaultCollections()
            isLoading = false
        }
    }

    // Split into enabled and disabled
    val enabledCollections = allCollections.filter { it.isEnabled }.sortedBy { it.order }
    val disabledCollections = allCollections.filter { !it.isEnabled }.sortedBy { it.title }

    LazyColumn(
        state = listState, // FIX 2: Attach listState for manual scroll control
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Dashboard Customization",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (!isPremium) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Pro Feature",
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Instructions and Default button - Conditional layout for mobile vs TV
            if (isMobile) {
                // Mobile: Column layout with button below instructions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    // Instructions
                    Text(
                        text = "Customize which sections appear on your home screen and in what order.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = if (isPremium) {
                            "Press select to rearrange, hold select to disable."
                        } else {
                            "Upgrade to Pro to customize your home screen layout."
                        },
                        fontSize = 12.sp,
                        color = if (isPremium) TextSecondary.copy(alpha = 0.8f) else Color(0xFF8B5CF6),
                        fontWeight = if (isPremium) FontWeight.Normal else FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Default button
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()

                    // Define colors based on focus state and premium status
                    val backgroundColor = if (!isPremium) {
                        BackgroundSecondary.copy(alpha = 0.5f) // Dimmed when locked
                    } else if (isFocused) {
                        Color(0xFFF5B800) // Yellow when focused
                    } else {
                        Color(0xFF8B5CF6) // Purple normal
                    }

                    val contentColor = if (!isPremium) {
                        TextTertiary // Dimmed text when locked
                    } else if (isFocused) {
                        Color.Black
                    } else {
                        Color.White
                    }

                    Box(
                        modifier = Modifier
                            .clickable(enabled = isPremium) {
                                coroutineScope.launch {
                                    profile?.let { currentProfile ->
                                        // 1. Logic to Reset Local State Immediately
                                        // We map the CURRENT list (preserving Plex items) but reset their status
                                        val defaults = Profile.getDefaultCollections()

                                        val resetList = allCollections.map { item ->
                                            val defaultItem = defaults.find { it.id == item.id }
                                            if (defaultItem != null) {
                                                // Enable defaults and set correct order
                                                item.copy(isEnabled = true, order = defaultItem.order)
                                            } else {
                                                // Disable everything else
                                                item.copy(isEnabled = false, order = Int.MAX_VALUE)
                                            }
                                        }.sortedBy { it.order }

                                        // 2. Update Database
                                        val updatedProfile = currentProfile.copy(dashboardCollections = resetList)
                                        profileRepository.updateProfile(updatedProfile)

                                        // 3. Update Local UI State Immediately (Fixes delay issue)
                                        allCollections = resetList
                                    }
                                }
                            }
                            .background(
                                color = backgroundColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isPremium) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "Default",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }
                }
            } else {
                // TV: Row layout with button to the right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Instructions on the left
                    Column {
                        Text(
                            text = "Customize which sections appear on your home screen and in what order.",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = if (isPremium) {
                                "Press select to rearrange, hold select to disable."
                            } else {
                                "Upgrade to Pro to customize your home screen layout."
                            },
                            fontSize = 12.sp,
                            color = if (isPremium) TextSecondary.copy(alpha = 0.8f) else Color(0xFF8B5CF6),
                            fontWeight = if (isPremium) FontWeight.Normal else FontWeight.Medium
                        )
                    }

                    // Spacing between text and button
                    Spacer(modifier = Modifier.width(32.dp))

                    // Default button
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()

                    // Define colors based on focus state and premium status
                    val backgroundColor = if (!isPremium) {
                        BackgroundSecondary.copy(alpha = 0.5f) // Dimmed when locked
                    } else if (isFocused) {
                        Color(0xFFF5B800) // Yellow when focused
                    } else {
                        Color(0xFF8B5CF6) // Purple normal
                    }

                    val contentColor = if (!isPremium) {
                        TextTertiary // Dimmed text when locked
                    } else if (isFocused) {
                        Color.Black
                    } else {
                        Color.White
                    }

                    Box(
                        modifier = Modifier
                            .focusRequester(dashboardCustomizationFocusRequester)
                            .focusProperties {
                                up = dashboardCustomizationTabFocusRequester
                                down = firstCollectionFocusRequester
                            }
                            .focusable(interactionSource = interactionSource)
                            .clickable(enabled = isPremium) {
                                coroutineScope.launch {
                                    profile?.let { currentProfile ->
                                        // 1. Logic to Reset Local State Immediately
                                        val defaults = Profile.getDefaultCollections()

                                        val resetList = allCollections.map { item ->
                                            val defaultItem = defaults.find { it.id == item.id }
                                            if (defaultItem != null) {
                                                item.copy(isEnabled = true, order = defaultItem.order)
                                            } else {
                                                item.copy(isEnabled = false, order = Int.MAX_VALUE)
                                            }
                                        }.sortedBy { it.order }

                                        // 2. Update Database
                                        val updatedProfile = currentProfile.copy(dashboardCollections = resetList)
                                        profileRepository.updateProfile(updatedProfile)

                                        // 3. Update Local UI State Immediately
                                        allCollections = resetList
                                    }
                                }
                            }
                            .then(
                                if (isFocused && !isPremium) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = Color(0xFF8B5CF6),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .background(
                                color = backgroundColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isPremium) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "Default",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }
                }
            }

            // Reorder mode indicator
            if (reorderMode) {
                Text(
                    text = "📍 Reorder Mode: Use D-pad Up/Down to move, Select to drop",
                    fontSize = 12.sp,
                    color = Color(0xFF8B5CF6),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Loading state
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF8B5CF6))
                }
            }
        } else {
            // Enabled Collections Section
            item {
                Text(
                    text = "Enabled Collections (${enabledCollections.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (enabledCollections.isEmpty()) {
                item {
                    Text(
                        text = "No enabled collections. Enable some below to customize your dashboard.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(enabledCollections, key = { _, collection -> collection.id }) { index, collection ->
                    // Track the drag start position
                    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
                    val currentIndex = enabledCollections.indexOfFirst { it.id == collection.id }
                    val isDragged = draggedItemIndex == currentIndex

                    // Logic: Only the very first enabled item gets the requester
                    val isTopItem = index == 0
                    val shouldGrabFocus = focusTargetIndex == index

                    // Focus requester only for first item (now points to firstCollectionFocusRequester)
                    val focusRequesterModifier = if (isTopItem) {
                        Modifier.focusRequester(firstCollectionFocusRequester)
                    } else {
                        Modifier
                    }

                    // "Up" navigation ONLY for the very first item (now points to Default button)
                    val upNavigationModifier = if (isTopItem) {
                        Modifier.focusProperties {
                            up = dashboardCustomizationFocusRequester // Points to Default button
                        }
                    } else {
                        Modifier // No focus properties = natural LazyColumn navigation
                    }

                    val itemModifier = focusRequesterModifier.then(upNavigationModifier)

                    // Drag modifier - only apply if Premium
                    val dragModifier = if (isPremium) {
                        Modifier.pointerInput(collection.id) {
                            // ... drag logic remains the same ...
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val actualIndex = enabledCollections.indexOfFirst { it.id == collection.id }
                                    dragStartIndex = actualIndex
                                    draggedItemIndex = actualIndex
                                    dragOffset = Offset.Zero
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount
                                    dragStartIndex?.let { startIdx ->
                                        val draggedY = dragOffset.y
                                        val itemHeight = 80.dp.toPx()
                                        val slotsMoved = (draggedY / itemHeight).roundToInt()
                                        val targetIndex = (startIdx + slotsMoved).coerceIn(0, enabledCollections.size - 1)
                                        draggedItemIndex = targetIndex
                                    }
                                },
                                onDragEnd = {
                                    val startIdx = dragStartIndex
                                    val endIdx = draggedItemIndex
                                    if (startIdx != null && endIdx != null && startIdx != endIdx) {
                                        coroutineScope.launch {
                                            moveCollection(context, profile, startIdx, endIdx, enabledCollections, allCollections) { updated ->
                                                allCollections = updated
                                            }
                                        }
                                    }
                                    draggedItemIndex = null
                                    dragOffset = Offset.Zero
                                    dragStartIndex = null
                                },
                                onDragCancel = {
                                    draggedItemIndex = null
                                    dragOffset = Offset.Zero
                                    dragStartIndex = null
                                }
                            )
                        }
                    } else {
                        Modifier  // No drag modifier for free users
                    }

                    DashboardCollectionItem(
                        collection = collection,
                        isInReorderMode = reorderMode && selectedCollectionId == collection.id,
                        isDragged = isDragged,
                        modifier = itemModifier,
                        forceFocus = shouldGrabFocus,
                        onFocusConsumed = { focusTargetIndex = null },
                        dragModifier = dragModifier,
                        onToggle = { enabled ->
                            if (isPremium) {  // WRAP IN isPremium CHECK
                                if (!enabled) {
                                    // Smart Focus: Calculate where focus should go when disabling
                                    if (enabledCollections.size == 1) {
                                        // Disabling the only/last enabled collection - go back to tab and reset scroll
                                        coroutineScope.launch {
                                            try {
                                                kotlinx.coroutines.delay(100)
                                                listState.scrollToItem(0) // Reset scroll to top
                                                dashboardCustomizationTabFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Ignore
                                            }
                                        }
                                    } else {
                                        // Multiple items - focus on next or previous
                                        val nextIndex = if (index == enabledCollections.size - 1) index - 1 else index
                                        focusTargetIndex = nextIndex
                                    }
                                }
                                coroutineScope.launch {
                                    updateCollectionStatus(context, profile, collection, enabled, allCollections) { updated ->
                                        allCollections = updated
                                    }
                                }
                            }
                        },
                        onSelectForReorder = {
                            if (isPremium) {  // WRAP IN isPremium CHECK
                                if (reorderMode && selectedCollectionId == collection.id) {
                                    reorderMode = false
                                    selectedCollectionId = null
                                } else if (!reorderMode) {
                                    reorderMode = true
                                    selectedCollectionId = collection.id
                                }
                            }
                        },
                        onMoveUp = {
                            if (isPremium && reorderMode && selectedCollectionId == collection.id) {  // ADD isPremium CHECK
                                val currentIdx = enabledCollections.indexOfFirst { it.id == collection.id }
                                if (currentIdx > 0) {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(index - 1)
                                        moveCollection(context, profile, currentIdx, currentIdx - 1, enabledCollections, allCollections) { updated ->
                                            allCollections = updated
                                        }
                                    }
                                }
                            }
                        },
                        onMoveDown = {
                            if (isPremium && reorderMode && selectedCollectionId == collection.id) {  // ADD isPremium CHECK
                                val currentIdx = enabledCollections.indexOfFirst { it.id == collection.id }
                                if (currentIdx < enabledCollections.size - 1) {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(index + 1)
                                        moveCollection(context, profile, currentIdx, currentIdx + 1, enabledCollections, allCollections) { updated ->
                                            allCollections = updated
                                        }
                                    }
                                }
                            }
                        },
                        isMobile = isMobile,
                        isEditable = isPremium  // PASS isPremium AS isEditable
                    )
                }
            }

            // Disabled Collections Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Disabled Collections (${disabledCollections.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (disabledCollections.isEmpty()) {
                item {
                    Text(
                        text = "All collections are enabled.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(disabledCollections, key = { _, collection -> collection.id }) { index, collection ->
                    // THE FIX: If enabled list is empty, the first DISABLED item becomes the "Top Item"
                    // It receives the focus requester AND the "Up" escape hatch to Default button.
                    val isTopItem = enabledCollections.isEmpty() && index == 0
                    val shouldGrabFocus = disabledFocusTargetIndex == index

                    // Focus requester only if this is the top item (when no enabled items)
                    val focusRequesterModifier = if (isTopItem) {
                        Modifier.focusRequester(firstCollectionFocusRequester)
                    } else {
                        Modifier
                    }

                    // "Up" navigation ONLY for the top item (when no enabled items) - points to Default button
                    val upNavigationModifier = if (isTopItem) {
                        Modifier.focusProperties {
                            up = dashboardCustomizationFocusRequester // Points to Default button
                        }
                    } else {
                        Modifier // No focus properties = natural LazyColumn navigation
                    }

                    val itemModifier = focusRequesterModifier.then(upNavigationModifier)

                    DashboardCollectionItem(
                        collection = collection,
                        isInReorderMode = false,
                        isDragged = false,
                        modifier = itemModifier,
                        forceFocus = shouldGrabFocus,
                        onFocusConsumed = { disabledFocusTargetIndex = null },
                        dragModifier = Modifier, // No drag for disabled items
                        onToggle = { enabled ->
                            if (isPremium) {  // WRAP IN isPremium CHECK
                                if (enabled) {
                                    // Smart Focus: If enabling from disabled list
                                    // Focus on next collection below, or go to tab if it's the last one
                                    if (index < disabledCollections.size - 1) {
                                        // Focus on next disabled collection (same index)
                                        disabledFocusTargetIndex = index
                                    } else {
                                        // Last collection - focus should go back to tab and reset scroll
                                        coroutineScope.launch {
                                            try {
                                                kotlinx.coroutines.delay(100)
                                                listState.scrollToItem(0) // Reset scroll to top
                                                dashboardCustomizationTabFocusRequester.requestFocus()
                                            } catch (e: Exception) {
                                                // Ignore
                                            }
                                        }
                                    }
                                }
                                coroutineScope.launch {
                                    updateCollectionStatus(context, profile, collection, enabled, allCollections) { updated ->
                                        allCollections = updated
                                    }
                                }
                            }
                        },
                        onSelectForReorder = {},
                        onMoveUp = {},
                        onMoveDown = {},
                        isMobile = isMobile,
                        isEditable = isPremium  // PASS isPremium AS isEditable
                    )
                }
            }
        }
    }
}

// Updated Status Helper (Ensures order is clean when enabling/disabling)
private suspend fun updateCollectionStatus(
    context: android.content.Context,
    profile: Profile?,
    collection: DashboardCollection,
    enabled: Boolean,
    currentCollections: List<DashboardCollection>,
    onUpdate: (List<DashboardCollection>) -> Unit
) {
    if (profile == null) return
    val profileRepository = ProfileRepository(context)

    try {
        // 1. Calculate new Order
        val newOrder = if (enabled) {
            // If enabling, put at the very bottom of the enabled list
            (currentCollections.filter { it.isEnabled }.maxOfOrNull { it.order } ?: -1) + 1
        } else {
            // If disabling, order doesn't matter (we usually sort disabled by title)
            9999
        }

        // 2. Update the specific item
        val updatedList = currentCollections.map {
            if (it.id == collection.id) {
                it.copy(isEnabled = enabled, order = newOrder)
            } else it
        }

        // 3. Save
        val updatedProfile = profile.copy(dashboardCollections = updatedList)
        profileRepository.updateProfile(updatedProfile)
        onUpdate(updatedList)

        android.util.Log.d("DashboardCustomization", "Updated collection ${collection.title}: enabled=$enabled, order=$newOrder")
    } catch (e: Exception) {
        android.util.Log.e("DashboardCustomization", "Error updating status: ${e.message}", e)
    }
}

// NEW LOGIC: Move items properly (shifting the list) instead of swapping
private suspend fun moveCollection(
    context: android.content.Context,
    profile: Profile?,
    fromIndex: Int,
    toIndex: Int,
    enabledList: List<DashboardCollection>,
    allCollections: List<DashboardCollection>,
    onUpdate: (List<DashboardCollection>) -> Unit
) {
    if (profile == null || fromIndex == toIndex) return

    val profileRepository = ProfileRepository(context)

    try {
        // 1. Extract the enabled list to a mutable list
        val mutableEnabled = enabledList.toMutableList()

        // 2. Perform the move
        val itemToMove = mutableEnabled.removeAt(fromIndex)
        mutableEnabled.add(toIndex, itemToMove)

        // 3. Re-assign 'order' values for the enabled list (0, 1, 2, 3...)
        // This guarantees a clean sequence with no duplicates or gaps
        val updatedEnabled = mutableEnabled.mapIndexed { index, col ->
            col.copy(order = index)
        }

        // 4. Merge back into the Master List
        // We take the newly ordered enabled items + the untouched disabled items
        val disabledItems = allCollections.filter { !it.isEnabled }
        val finalCollectionList = updatedEnabled + disabledItems

        // 5. Save to Profile
        val updatedProfile = profile.copy(dashboardCollections = finalCollectionList)
        profileRepository.updateProfile(updatedProfile)

        // 6. Update UI
        onUpdate(finalCollectionList)

        android.util.Log.d("DashboardCustomization", "Moved collection from $fromIndex to $toIndex: ${itemToMove.title}")
    } catch (e: Exception) {
        android.util.Log.e("DashboardCustomization", "Error moving collection: ${e.message}", e)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DashboardCollectionItem(
    collection: DashboardCollection,
    isInReorderMode: Boolean,
    isDragged: Boolean = false,
    modifier: Modifier = Modifier, // Single modifier parameter (contains focus requester + properties if needed)
    dragModifier: Modifier = Modifier,
    forceFocus: Boolean = false,
    onFocusConsumed: () -> Unit = {},
    onToggle: (Boolean) -> Unit,
    onSelectForReorder: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    isMobile: Boolean = false,
    isEditable: Boolean = true  // NEW: Controls whether item can be edited
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isInReorderMode || isDragged) 1.05f else 1f,
        label = "reorder_scale"
    )

    // Track double-tap for TV remote controls
    var lastTapTime by remember { mutableStateOf(0L) }
    val doubleTapThreshold = 300L // milliseconds

    // Create a local FocusRequester for every item to handle retention
    val itemFocusRequester = remember { FocusRequester() }
    var shouldRetainFocus by remember { mutableStateOf(false) }

    // FIX 1 & 2: Handle both shouldRetainFocus and forceFocus with delay
    LaunchedEffect(shouldRetainFocus, forceFocus) {
        if (shouldRetainFocus || forceFocus) {
            try {
                // Wait 100ms for the LazyColumn to finish recomposing the list
                kotlinx.coroutines.delay(100)
                itemFocusRequester.requestFocus()
                if (forceFocus) {
                    onFocusConsumed() // Notify parent that focus was consumed
                }
            } catch (e: Exception) {
                // Ignore errors if item is no longer on screen
            }
            shouldRetainFocus = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier)
            .then(modifier) // Apply external modifier (contains focusRequester + focusProperties if needed)
            .focusRequester(itemFocusRequester) // Attach the local requester for retention
            .focusable(interactionSource = interactionSource)
            .scale(scale)
            .onKeyEvent { keyEvent ->
                when {
                    keyEvent.type == KeyEventType.KeyUp && (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) -> {
                        if (isEditable) {  // WRAP IN isEditable CHECK
                            val currentTime = System.currentTimeMillis()
                            val timeSinceLastTap = currentTime - lastTapTime

                            if (timeSinceLastTap < doubleTapThreshold && collection.isEnabled) {
                                // Double-tap detected on enabled collection - disable it
                                shouldRetainFocus = true
                                onToggle(false)
                                lastTapTime = 0L // Reset to prevent triple-tap issues
                            } else {
                                // Single tap
                                if (collection.isEnabled) {
                                    // Single tap on enabled collection - enter/exit grab mode
                                    shouldRetainFocus = true
                                    onSelectForReorder()
                                } else {
                                    // Single tap on disabled collection - enable it
                                    shouldRetainFocus = true
                                    onToggle(true)
                                }
                                lastTapTime = currentTime
                            }
                        }
                        true  // Consume event even if not editable
                    }
                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp -> {
                        // D-pad up - move collection up in reorder mode
                        if (isInReorderMode && isEditable) {  // ADD isEditable CHECK
                            shouldRetainFocus = true // Important: Retain focus after move
                            onMoveUp()
                            true
                        } else {
                            // Allow default navigation (to tab or previous item)
                            false
                        }
                    }
                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown -> {
                        // D-pad down - move collection down in reorder mode
                        if (isInReorderMode && isEditable) {  // ADD isEditable CHECK
                            shouldRetainFocus = true // Important: Retain focus after move
                            onMoveDown()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .then(
                when {
                    isInReorderMode -> Modifier.border(
                        width = 3.dp,
                        color = Color(0xFF8B5CF6),
                        shape = RoundedCornerShape(12.dp)
                    )
                    isMobile -> Modifier // No focus border on mobile
                    else -> Modifier.animatedPosterBorder(
                        shape = RoundedCornerShape(12.dp),
                        interactionSource = interactionSource
                    )
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isInReorderMode || isDragged -> Color(0xFF2D1B4E)
                !isEditable -> BackgroundSecondary.copy(alpha = 0.5f)  // DIMMED WHEN LOCKED
                else -> BackgroundSecondary
            }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isInReorderMode || isDragged) {
                        Text(
                            text = "↕️ ",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    // ADD LOCK ICON FOR FREE USERS
                    if (!isEditable) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(14.dp).padding(end = 4.dp)
                        )
                    }

                    Text(
                        text = if (collection.type == CollectionType.PLEX && collection.itemCount > 0) {
                            "${collection.title} (${collection.itemCount})"
                        } else {
                            collection.title
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isEditable) TextPrimary else TextSecondary  // DIM TEXT WHEN LOCKED
                    )
                }
                Text(
                    text = if (collection.type == CollectionType.HARDCODED) "Built-in" else "Plex Collection",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Toggle Switch
            Switch(
                checked = collection.isEnabled,
                onCheckedChange = { if (isEditable) onToggle(it) },  // CONDITIONAL CALLBACK
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF8B5CF6),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF4B5563),
                    disabledCheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    disabledCheckedTrackColor = Color(0xFF8B5CF6).copy(alpha = 0.4f),
                    disabledUncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    disabledUncheckedTrackColor = Color(0xFF4B5563).copy(alpha = 0.4f)
                ),
                enabled = isEditable && !isInReorderMode && !isDragged  // DISABLE SWITCH WHEN NOT EDITABLE
            )
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
private fun PureStreamProTabContent(
    isPremium: Boolean,
    onMonthlyUpgradeClick: () -> Unit,
    onAnnualUpgradeClick: () -> Unit,
    onFreeVersionClick: () -> Unit,
    upgradeButtonFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    isMobile: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .then(if (isMobile) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        if (isPremium) {
            // Pro User Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF8B5CF6))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "🎉 You're a Pure Stream Pro User!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Thank you for supporting Pure Stream! You have access to all premium features including unlimited profiles, custom filtering levels, and future updates.",
                        fontSize = 14.sp,
                        color = Color(0xFF9CA3AF),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Enjoy all premium features without any restrictions!",
                        fontSize = 14.sp,
                        color = Color(0xFF8B5CF6),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Plan Cards - Conditional layout for mobile vs TV
            if (isMobile) {
                // Mobile: Vertical stacking (Free on top, Pro on bottom)
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                // Free Plan Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1F2937)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Free Plan Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Free Plan",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Perfect for getting started with content filtering",
                            fontSize = 11.sp,
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Price
                        Text(
                            text = "$0/month",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Features
                        val freeFeatures = listOf(
                            "1 Adult Profile",
                            "Basic Profanity Filtering (Mild Level)",
                            "Content Analysis & Warnings",
                            "Access to Your Full Plex Library",
                            "Smart Recommendations",
                            "No Ads. Ever."
                        )

                        freeFeatures.forEach { feature ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = feature,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Free Plan Status
                        Box(
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Current Plan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                // Pro Plan Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF8B5CF6))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // Pro Plan Header with Most Popular Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Pure Stream Pro",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            // Most Popular Badge - now directly to the right of the title
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFFFBBF24),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "MOST POPULAR",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }

                        Text(
                            text = "The complete family streaming solution",
                            fontSize = 11.sp,
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Price
                        Text(
                            text = "$4.99/month or $49.99/year (17% annual savings)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // Everything in Free plus text
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFF8B5CF6).copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "Everything in Free, plus:",
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Pro Features
                        val proFeatures = listOf(
                            "Unlimited User Profiles (Adult & Child)",
                            "Customizable Filtering Levels (None to Strict)",
                            "Custom Word Blacklist & Whitelist",
                            "Detailed Media Profanity Analysis",
                            "Curated Dashboard with Plex Collection Integration",
                            "Access to All Future Upgrades"
                        )

                        proFeatures.forEach { feature ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = feature,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Split Upgrade Buttons - only show when not premium
                        if (!isPremium) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Monthly Plan Button
                                Button(
                                    onClick = onMonthlyUpgradeClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .focusRequester(upgradeButtonFocusRequester)
                                        .focusProperties {
                                            up = contentFocusRequester
                                            left = sidebarFocusRequester
                                        },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6366F1),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Monthly Plan",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                
                                // Annual Plan Button
                                Button(
                                    onClick = onAnnualUpgradeClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .focusProperties {
                                            up = contentFocusRequester
                                            left = sidebarFocusRequester
                                        },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF8B5CF6),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Annual Plan",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            // Pro Plan Status
                            Box(
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Current Plan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF8B5CF6)
                                )
                            }
                        }
                    }
                }
                }
            } else {
                // TV: Horizontal row (existing layout)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Free Plan Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(400.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1F2937)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            // Free Plan Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Free Plan",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Text(
                                text = "Perfect for getting started with content filtering",
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // Price
                            Text(
                                text = "$0/month",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Features
                            val freeFeatures = listOf(
                                "1 Adult Profile",
                                "Basic Profanity Filtering (Mild Level)",
                                "Content Analysis & Warnings",
                                "Access to Your Full Plex Library",
                                "Smart Recommendations",
                                "No Ads. Ever."
                            )

                            freeFeatures.forEach { feature ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = feature,
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Free Plan Status
                            Box(
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Current Plan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }

                    // Pro Plan Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(400.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF8B5CF6))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Spacer(modifier = Modifier.height(4.dp))

                            // Pro Plan Header with Most Popular Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Pure Stream Pro",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                // Most Popular Badge - now directly to the right of the title
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = Color(0xFFFBBF24),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "MOST POPULAR",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }

                            Text(
                                text = "The complete family streaming solution",
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // Price
                            Text(
                                text = "$4.99/month or $49.99/year (17% annual savings)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            // Everything in Free plus text
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = Color(0xFF8B5CF6).copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "Everything in Free, plus:",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Pro Features
                            val proFeatures = listOf(
                                "Unlimited User Profiles (Adult & Child)",
                                "Customizable Filtering Levels (None to Strict)",
                                "Custom Word Blacklist & Whitelist",
                                "Detailed Media Profanity Analysis",
                                "Curated Dashboard with Plex Collection Integration",
                                "Access to All Future Upgrades"
                            )

                            proFeatures.forEach { feature ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = feature,
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Split Upgrade Buttons - only show when not premium
                            if (!isPremium) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Monthly Plan Button
                                    Button(
                                        onClick = onMonthlyUpgradeClick,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .focusRequester(upgradeButtonFocusRequester)
                                            .focusProperties {
                                                up = contentFocusRequester
                                                left = sidebarFocusRequester
                                            },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF6366F1),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Monthly Plan",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    
                                    // Annual Plan Button
                                    Button(
                                        onClick = onAnnualUpgradeClick,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .focusProperties {
                                                up = contentFocusRequester
                                                left = sidebarFocusRequester
                                            },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF8B5CF6),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Annual Plan",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            } else {
                                // Pro Plan Status
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Current Plan",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF8B5CF6)
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

