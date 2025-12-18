package com.purestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.purestream.data.model.*
import com.purestream.data.repository.PlexRepository
import com.purestream.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class HomeState(
    val currentProfile: Profile? = null,
    val contentSections: List<ContentSection> = emptyList(),
    val featuredMovie: ContentItem? = null,
    val watchHistory: List<WatchHistoryItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val plexRepository: PlexRepository = PlexRepository(),
    private val profileRepository: ProfileRepository? = null
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeState())
    val uiState: StateFlow<HomeState> = _uiState.asStateFlow()
    
    fun setCurrentProfile(profile: Profile) {
        _uiState.value = _uiState.value.copy(currentProfile = profile)
        loadContent()
    }
    
    fun refreshCurrentProfile() {
        viewModelScope.launch {
            try {
                val currentProfile = _uiState.value.currentProfile
                if (currentProfile != null && profileRepository != null) {
                    android.util.Log.i("HomeViewModel", "*** REFRESHING CURRENT PROFILE *** Before: '${currentProfile.name}' (Filter: ${currentProfile.profanityFilterLevel})")

                    // Re-fetch the profile from database to get any updates (e.g., from premium status changes, dashboard collections)
                    val refreshedProfile = profileRepository.getProfileById(currentProfile.id)
                    if (refreshedProfile != null) {
                        android.util.Log.w("HomeViewModel", "*** PROFILE REFRESHED *** After: '${refreshedProfile.name}' (Filter: ${refreshedProfile.profanityFilterLevel})")
                        android.util.Log.w("HomeViewModel", "*** Dashboard Collections Count: ${refreshedProfile.dashboardCollections?.size ?: 0}")
                        _uiState.value = _uiState.value.copy(currentProfile = refreshedProfile)

                        // FIX: Reload content to reflect updated dashboard collections
                        android.util.Log.i("HomeViewModel", "*** RELOADING CONTENT with refreshed profile ***")
                        loadContent()
                    } else {
                        android.util.Log.e("HomeViewModel", "Failed to fetch refreshed profile from database")
                    }
                } else {
                    android.util.Log.w("HomeViewModel", "Cannot refresh profile - currentProfile or profileRepository is null")
                }
            } catch (e: Exception) {
                // Don't show error for profile refresh - it's a background operation
                android.util.Log.e("HomeViewModel", "Failed to refresh current profile: ${e.message}", e)
            }
        }
    }
    
    fun setPlexConnection(serverUrl: String, token: String) {
        com.purestream.utils.PlexConnectionHelper.setPlexConnection(
            viewModel = this,
            plexRepository = plexRepository,
            serverUrl = serverUrl,
            token = token
        )
    }
    
    fun setPlexConnectionWithAuth(token: String) {
        com.purestream.utils.PlexConnectionHelper.setPlexConnectionWithAuthSimple(
            viewModel = this,
            plexRepository = plexRepository,
            authToken = token,
            onError = { errorMessage ->
                _uiState.value = _uiState.value.copy(
                    error = errorMessage,
                    isLoading = false
                )
            }
        )
        loadContent()
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        // Note: Actual search functionality is handled by SearchViewModel
    }
    
    fun loadContent(maxRetries: Int = 10) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            loadContentWithRetry(maxRetries, 0)
        }
    }

    private suspend fun loadContentWithRetry(maxRetries: Int, currentAttempt: Int) {
        try {
            // Check if we have a valid Plex connection
            if (!plexRepository.hasValidConnection()) {
                android.util.Log.w("HomeViewModel", "Cannot load content - no valid Plex connection (Attempt ${currentAttempt + 1}/$maxRetries)")

                if (currentAttempt < maxRetries) {
                    // Retry with exponential backoff
                    val delayMs = (1000L * (currentAttempt + 1)) // 1s, 2s, 3s delays
                    android.util.Log.d("HomeViewModel", "Retry attempt ${currentAttempt + 1}/$maxRetries after ${delayMs}ms delay")
                    delay(delayMs)
                    loadContentWithRetry(maxRetries, currentAttempt + 1)
                } else {
                    _uiState.value = _uiState.value.copy(
                        contentSections = emptyList(),
                        featuredMovie = null,
                        error = "No authentication token found. Please try again.",
                        isLoading = false
                    )
                }
                return
            }

            // Get selected libraries from current profile
            val selectedLibraries = _uiState.value.currentProfile?.selectedLibraries ?: emptyList()

            // Check if user has selected any libraries
            if (selectedLibraries.isEmpty()) {
                android.util.Log.w("HomeViewModel", "No libraries selected")
                _uiState.value = _uiState.value.copy(
                    contentSections = emptyList(),
                    featuredMovie = null,
                    error = "Please select at least one library in Settings",
                    isLoading = false
                )
                return
            }

            // Get dashboard collections from current profile
            val dashboardCollections = _uiState.value.currentProfile?.dashboardCollections

            android.util.Log.d("HomeViewModel", "=== LOAD CONTENT ===")
            android.util.Log.d("HomeViewModel", "Selected libraries: $selectedLibraries")
            android.util.Log.d("HomeViewModel", "Dashboard collections count: ${dashboardCollections?.size ?: 0}")

            // Only log enabled collections to reduce verbosity (especially with 193+ collections)
            val enabledCollections = dashboardCollections?.filter { it.isEnabled }?.sortedBy { it.order } ?: emptyList()
            android.util.Log.d("HomeViewModel", "Enabled collections count: ${enabledCollections.size}")
            enabledCollections.take(10).forEach { col ->
                android.util.Log.d("HomeViewModel", "  [${col.order}] ${col.title} (${col.id}): type=${col.type}")
            }
            if (enabledCollections.size > 10) {
                android.util.Log.d("HomeViewModel", "  ... and ${enabledCollections.size - 10} more enabled collections")
            }

            // Load dashboard sections with customized collections
            val sectionsResult = plexRepository.getHomeDashboardSections(selectedLibraries, dashboardCollections)

            android.util.Log.d("HomeViewModel", "Sections result: ${if (sectionsResult.isSuccess) "SUCCESS" else "FAILURE"}")

            sectionsResult.fold(
                onSuccess = { sections ->
                    android.util.Log.d("HomeViewModel", "Got ${sections.size} sections from repository")
                    sections.forEachIndexed { idx, section ->
                        android.util.Log.d("HomeViewModel", "  Section $idx: ${section.title} (${section.items.size} items)")
                    }

                    // Extract featured movie from "Recommended for You" section (first movie)
                    val featuredContentItem = sections
                        .find { it.title == "Recommended for You" }
                        ?.items?.firstOrNull()

                    _uiState.value = _uiState.value.copy(
                        contentSections = sections,
                        featuredMovie = featuredContentItem,
                        isLoading = false
                    )

                    android.util.Log.d("HomeViewModel", "Updated UI state with ${sections.size} sections")
                },
                onFailure = { exception ->
                    if (currentAttempt < maxRetries) {
                        // Retry with exponential backoff
                        val delayMs = (1000L * (currentAttempt + 1)) // 1s, 2s, 3s delays
                        android.util.Log.d("HomeViewModel", "Retry attempt ${currentAttempt + 1}/$maxRetries after ${delayMs}ms delay")
                        delay(delayMs)
                        loadContentWithRetry(maxRetries, currentAttempt + 1)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to load content after $maxRetries attempts: ${exception.message}",
                            isLoading = false
                        )
                    }
                }
            )
        } catch (e: Exception) {
            if (currentAttempt < maxRetries) {
                // Retry with exponential backoff
                val delayMs = (1000L * (currentAttempt + 1)) // 1s, 2s, 3s delays
                android.util.Log.d("HomeViewModel", "Retry attempt ${currentAttempt + 1}/$maxRetries after ${delayMs}ms delay (Exception: ${e.message})")
                delay(delayMs)
                loadContentWithRetry(maxRetries, currentAttempt + 1)
            } else {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load content after $maxRetries attempts: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Clear all cached data on logout
     * Resets the ViewModel to initial state
     */
    fun clearData() {
        android.util.Log.d("HomeViewModel", "Clearing all cached data")
        _uiState.value = HomeState()

        // Clear PlexRepository connection
        plexRepository.clearConnection()
    }
}