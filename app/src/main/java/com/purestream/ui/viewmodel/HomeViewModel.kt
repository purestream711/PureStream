package com.purestream.ui.viewmodel

import android.content.Context
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
    context: Context,
    private val plexRepository: PlexRepository = PlexRepository(context),
    private val profileRepository: ProfileRepository? = null
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeState())
    val uiState: StateFlow<HomeState> = _uiState.asStateFlow()
    
    // Preferences for persisting featured movie selection
    private val prefs = context.getSharedPreferences("purestream_home_prefs", Context.MODE_PRIVATE)
    
    private fun getCachedFeaturedMovieId(profileId: String): String? {
        val keyId = "featured_id_$profileId"
        val keyTs = "featured_ts_$profileId"
        val savedId = prefs.getString(keyId, null)
        val savedTs = prefs.getLong(keyTs, 0L)
        val threeDaysMs = 3 * 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()

        if (savedId != null && (now - savedTs) < threeDaysMs) {
             return savedId
        }
        return null
    }

    private fun selectNewFeaturedMovie(allMovies: List<Movie>, profileId: String): Movie? {
        if (allMovies.isEmpty()) return null
        
        val keyId = "featured_id_$profileId"
        val keyTs = "featured_ts_$profileId"
        
        // Select new: Random Action, Adventure, or Drama from last 20 years
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val startYear = currentYear - 20
        
        val candidates = allMovies.filter { movie ->
            val isTargetGenre = movie.genres?.any { 
                it.tag.equals("Drama", ignoreCase = true) || 
                it.tag.equals("Action", ignoreCase = true) || 
                it.tag.equals("Adventure", ignoreCase = true)
            } == true
            val isRecent = (movie.year ?: 0) >= startYear
            isTargetGenre && isRecent
        }
        
        android.util.Log.d("HomeViewModel", "Found ${candidates.size} candidate featured movies (Action/Adventure/Drama, last 20 years)")
        
        val selected = if (candidates.isNotEmpty()) {
            candidates.random()
        } else {
            // Fallback: Random from all movies if no drama/recent found
            allMovies.random()
        }
        
        prefs.edit()
            .putString(keyId, selected.ratingKey)
            .putLong(keyTs, System.currentTimeMillis())
            .apply()
            
        android.util.Log.d("HomeViewModel", "Selected NEW featured movie: ${selected.title}")
        return selected
    }

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
            val allSelectedLibraries = _uiState.value.currentProfile?.selectedLibraries ?: emptyList()

            // Check if user has selected any libraries
            if (allSelectedLibraries.isEmpty()) {
                android.util.Log.w("HomeViewModel", "No libraries selected")
                _uiState.value = _uiState.value.copy(
                    contentSections = emptyList(),
                    featuredMovie = null,
                    error = "Please select at least one library in Settings",
                    isLoading = false
                )
                return
            }

            // Use preferredDashboardLibraryId if set, otherwise use all selected libraries
            val preferredLibraryId = _uiState.value.currentProfile?.preferredDashboardLibraryId
            val selectedLibraries = if (preferredLibraryId != null && allSelectedLibraries.contains(preferredLibraryId)) {
                android.util.Log.d("HomeViewModel", "Using preferred dashboard library: $preferredLibraryId")
                listOf(preferredLibraryId)
            } else {
                android.util.Log.d("HomeViewModel", "Using all selected libraries (no preferred dashboard library set)")
                allSelectedLibraries
            }

            // Get dashboard collections from current profile
            val dashboardCollections = _uiState.value.currentProfile?.dashboardCollections ?: emptyList()

            // The dashboardCollections now contain the merged AI collections if enabled.
            val allCollections = dashboardCollections

            android.util.Log.d("HomeViewModel", "=== LOAD CONTENT ===")
            android.util.Log.d("HomeViewModel", "Selected libraries: $selectedLibraries")
            android.util.Log.d("HomeViewModel", "Dashboard collections count: ${dashboardCollections.size}")
            android.util.Log.d("HomeViewModel", "Total collections (merged): ${allCollections.size}")

            // Only log enabled collections to reduce verbosity (especially with 193+ collections)
            val enabledCollections = allCollections.filter { it.isEnabled }.sortedBy { it.order }
            android.util.Log.d("HomeViewModel", "Enabled collections count: ${enabledCollections.size}")
            enabledCollections.take(10).forEach { col ->
                android.util.Log.d("HomeViewModel", "  [${col.order}] ${col.title} (${col.id}): type=${col.type}")
            }
            if (enabledCollections.size > 10) {
                android.util.Log.d("HomeViewModel", "  ... and ${enabledCollections.size - 10} more enabled collections")
            }

            // Load dashboard sections with customized collections (merged hardcoded + AI)
            val sectionsResult = plexRepository.getHomeDashboardSections(
                selectedLibraries = selectedLibraries,
                enabledCollections = allCollections,
                profileId = _uiState.value.currentProfile?.id
            )

            android.util.Log.d("HomeViewModel", "Sections result: ${if (sectionsResult.isSuccess) "SUCCESS" else "FAILURE"}")

            sectionsResult.fold(
                onSuccess = { sections ->
                    android.util.Log.d("HomeViewModel", "Got ${sections.size} sections from repository")
                    sections.forEachIndexed { idx, section ->
                        android.util.Log.d("HomeViewModel", "  Section $idx: ${section.title} (${section.items.size} items)")
                    }

                    // Extract featured movie - Random Drama (last 20 years), rotating every 3 days
                    var featuredContentItem: ContentItem? = null
                    val profileId = _uiState.value.currentProfile?.id ?: "guest"
                    
                    try {
                        // Optimization: Check cache first to avoid fetching all movies
                        val cachedId = getCachedFeaturedMovieId(profileId)
                        if (cachedId != null) {
                            val movieResult = plexRepository.getMovieById(cachedId)
                            if (movieResult.isSuccess) {
                                val movie = movieResult.getOrNull()
                                if (movie != null) {
                                    android.util.Log.d("HomeViewModel", "Using cached featured movie: ${movie.title}")
                                    featuredContentItem = ContentItem(
                                        id = movie.ratingKey,
                                        title = movie.title,
                                        type = ContentType.MOVIE,
                                        thumbUrl = movie.thumbUrl,
                                        artUrl = movie.artUrl,
                                        logoUrl = movie.logoUrl,
                                        summary = movie.summary,
                                        year = movie.year,
                                        rating = movie.rating,
                                        duration = movie.duration,
                                        contentRating = movie.contentRating,
                                        profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN,
                                        hasSubtitles = movie.hasSubtitles
                                    )
                                }
                            }
                        }

                        if (featuredContentItem == null) {
                            android.util.Log.d("HomeViewModel", "No valid cached featured movie, selecting new one...")
                            // 1. Fetch all movies from selected libraries to form the candidate pool
                            val allMovies = mutableListOf<Movie>()
                            for (libId in selectedLibraries) {
                                val movies = plexRepository.getMovies(libId, profileId = profileId).getOrNull()
                                if (movies != null) allMovies.addAll(movies)
                            }
                            
                            // 2. Select new featured movie
                            val featuredMovie = selectNewFeaturedMovie(allMovies, profileId)
                            
                            if (featuredMovie != null) {
                                featuredContentItem = ContentItem(
                                    id = featuredMovie.ratingKey,
                                    title = featuredMovie.title,
                                    type = ContentType.MOVIE,
                                    thumbUrl = featuredMovie.thumbUrl,
                                    artUrl = featuredMovie.artUrl,
                                    logoUrl = featuredMovie.logoUrl,
                                    summary = featuredMovie.summary,
                                    year = featuredMovie.year,
                                    rating = featuredMovie.rating,
                                    duration = featuredMovie.duration,
                                    contentRating = featuredMovie.contentRating,
                                    profanityLevel = featuredMovie.profanityLevel ?: ProfanityLevel.UNKNOWN,
                                    hasSubtitles = featuredMovie.hasSubtitles
                                )
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Error selecting featured movie: ${e.message}", e)
                    }

                    // Fallback to "Recommended for You" first movie if custom selection failed
                    if (featuredContentItem == null) {
                        featuredContentItem = sections.find { it.title == "Recommended for You" }?.items?.firstOrNull()
                    }

                    _uiState.value = _uiState.value.copy(
                        contentSections = sections,
                        featuredMovie = featuredContentItem,
                        isLoading = false
                    )

                    // Pre-fetch featured movie details for instant loading on click
                    featuredContentItem?.let { item ->
                        viewModelScope.launch {
                            try {
                                android.util.Log.d("HomeViewModel", "Pre-fetching details for featured movie: ${item.title}")
                                plexRepository.getMovieDetails(item.id)
                            } catch (e: Exception) {
                                // Ignore background pre-fetch errors
                            }
                        }
                    }

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