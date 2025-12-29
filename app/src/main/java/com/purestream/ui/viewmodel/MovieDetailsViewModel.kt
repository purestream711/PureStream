package com.purestream.ui.viewmodel
import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.purestream.data.model.*
import com.purestream.data.repository.PlexRepository
import com.purestream.data.repository.OpenSubtitlesRepository
import com.purestream.data.repository.SubtitleAnalysisRepository
import com.purestream.data.repository.WatchProgressRepository
import com.purestream.data.database.AppDatabase
import com.purestream.profanity.ProfanityFilter
import com.purestream.utils.ImdbIdExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MovieDetailsState(
    val movie: Movie? = null,
    val videoUrl: String? = null,
    val isLoading: Boolean = false,
    val isLoadingVideo: Boolean = false,
    val error: String? = null,
    val videoError: String? = null,
    val isAnalyzingSubtitles: Boolean = false,
    val subtitleAnalysisResult: SubtitleAnalysisResult? = null,
    val subtitleAnalysisError: String? = null,
    val canAnalyzeProfanity: Boolean = true,
    val existingFilterLevels: List<ProfanityFilterLevel> = emptyList(),
    val progressPercentage: Float? = null,  // 0.0 to 1.0, null if not started
    val progressPosition: Long? = null  // Position in milliseconds
)

class MovieDetailsViewModel(
    context: Context,
    private val plexRepository: PlexRepository = PlexRepository(context),
    private val profanityFilter: ProfanityFilter = ProfanityFilter(),
    private val openSubtitlesRepository: OpenSubtitlesRepository = OpenSubtitlesRepository(ProfanityFilter()),
    private val subtitleAnalysisRepository: SubtitleAnalysisRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailsState())
    val uiState: StateFlow<MovieDetailsState> = _uiState.asStateFlow()

    // Watch progress tracking
    private var watchProgressRepository: WatchProgressRepository? = null
    private var currentProfileId: String = ""
    
    fun loadMovieDetails(movieId: String, maxRetries: Int = 10) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            loadMovieDetailsWithRetry(movieId, maxRetries, 0)
        }
    }
    
    private suspend fun loadMovieDetailsWithRetry(movieId: String, maxRetries: Int, currentAttempt: Int) {
        try {
            val movieResult = plexRepository.getMovieDetails(movieId)
            movieResult.fold(
                onSuccess = { movie ->
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    setMovie(movie) // This will trigger checkExistingAnalysis() and load any existing analysis
                },
                onFailure = { exception ->
                    if (currentAttempt < maxRetries) {
                        // Retry with exponential backoff
                        val delayMs = (1000L * (currentAttempt + 1)) // 1s, 2s, 3s delays
                        android.util.Log.d("MovieDetailsViewModel", "Retry attempt ${currentAttempt + 1}/$maxRetries after ${delayMs}ms delay")
                        delay(delayMs)
                        loadMovieDetailsWithRetry(movieId, maxRetries, currentAttempt + 1)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to load movie details after $maxRetries attempts: ${exception.message}",
                            isLoading = false
                        )
                    }
                }
            )
        } catch (e: Exception) {
            if (currentAttempt < maxRetries) {
                // Retry with exponential backoff
                val delayMs = (1000L * (currentAttempt + 1)) // 1s, 2s, 3s delays
                android.util.Log.d("MovieDetailsViewModel", "Retry attempt ${currentAttempt + 1}/$maxRetries after ${delayMs}ms delay (Exception: ${e.message})")
                delay(delayMs)
                loadMovieDetailsWithRetry(movieId, maxRetries, currentAttempt + 1)
            } else {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load movie details after $maxRetries attempts: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    fun setMovie(movie: Movie) {
        _uiState.value = _uiState.value.copy(movie = movie)
        checkCanAnalyzeProfanity() // Check for existing analysis and load if available
        loadProgress(movie.ratingKey) // Load watch progress
    }
    
    fun getVideoUrl(movie: Movie? = null) {
        val targetMovie = movie ?: _uiState.value.movie
        if (targetMovie == null) {
            _uiState.value = _uiState.value.copy(videoError = "No movie provided")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingVideo = true, videoError = null)
            
            try {
                // Get the video URL directly (subtitle analysis is now handled separately via Analyze Profanity button)
                val videoUrlResult = plexRepository.getVideoStreamUrl(targetMovie)
                videoUrlResult.fold(
                    onSuccess = { url ->
                        _uiState.value = _uiState.value.copy(
                            videoUrl = url,
                            isLoadingVideo = false
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            videoError = "Failed to get video URL: ${exception.message}",
                            isLoadingVideo = false
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    videoError = "Failed to get video URL: ${e.message}",
                    isLoadingVideo = false
                )
            }
        }
    }
    
    fun clearVideoError() {
        _uiState.value = _uiState.value.copy(videoError = null)
    }
    
    fun clearVideoUrl() {
        _uiState.value = _uiState.value.copy(videoUrl = null)
    }
    
    fun clearMovieDetails() {
        _uiState.value = _uiState.value.copy(movie = null)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun setPlexConnection(serverUrl: String, token: String) {
        com.purestream.utils.PlexConnectionHelper.setPlexConnection(
            viewModel = this,
            plexRepository = plexRepository,
            serverUrl = serverUrl,
            token = token
        )
    }
    
    fun setPlexConnectionWithAuth(authToken: String) {
        com.purestream.utils.PlexConnectionHelper.setPlexConnectionWithAuthSimple(
            viewModel = this,
            plexRepository = plexRepository,
            authToken = authToken,
            onError = { errorMessage ->
                _uiState.value = _uiState.value.copy(error = errorMessage)
            }
        )
    }
    
    fun analyzeMovieProfanity(movie: Movie, filterLevel: ProfanityFilterLevel) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzingSubtitles = true,
                subtitleAnalysisError = null,
                subtitleAnalysisResult = null
            )
            
            try {
                android.util.Log.d("MovieDetailsViewModel", "Starting automated subtitle search and analysis for: ${movie.title}")
                
                // Use the persistent analysis method to save results to database
                // Extract IMDB ID from movie metadata for more accurate search
                val imdbId = ImdbIdExtractor.extractImdbId(movie.guid)
                android.util.Log.d("MovieDetailsViewModel", "Extracted IMDB ID: $imdbId for movie: ${movie.title}")
                
                val searchResult = openSubtitlesRepository.searchSubtitlesForMovie(movie.title, imdbId, movie.year)
                if (searchResult.isFailure) {
                    throw Exception("Failed to find subtitles: ${searchResult.exceptionOrNull()?.message}")
                }
                
                val subtitles = searchResult.getOrThrow()
                if (subtitles.isEmpty()) {
                    throw Exception("No subtitles available for '${movie.title}'")
                }
                
                val bestSubtitle = subtitles.first()
                val analysisResult = openSubtitlesRepository.downloadAndAnalyzeSubtitlePersistent(
                    subtitleResult = bestSubtitle,
                    contentId = movie.ratingKey,
                    contentType = "movie", 
                    contentTitle = movie.title,
                    showTitle = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    movieTitle = movie.title,
                    episodeInfo = null,
                    filterLevel = filterLevel
                )
                
                analysisResult.fold(
                    onSuccess = { analysis ->
                        android.util.Log.d("MovieDetailsViewModel", "Automated analysis completed successfully for: ${movie.title}")
                        _uiState.value = _uiState.value.copy(
                            isAnalyzingSubtitles = false,
                            subtitleAnalysisResult = analysis,
                            canAnalyzeProfanity = false
                        )
                    },
                    onFailure = { exception ->
                        android.util.Log.e("MovieDetailsViewModel", "Automated analysis failed for: ${movie.title} - ${exception.message}")
                        _uiState.value = _uiState.value.copy(
                            isAnalyzingSubtitles = false,
                            subtitleAnalysisError = "Failed to analyze subtitles: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("MovieDetailsViewModel", "Error during automated subtitle analysis: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isAnalyzingSubtitles = false,
                    subtitleAnalysisError = "Error during subtitle analysis: ${e.message}"
                )
            }
        }
    }
    
    fun clearSubtitleAnalysisError() {
        _uiState.value = _uiState.value.copy(subtitleAnalysisError = null)
    }
    
    fun clearSubtitleAnalysisResult() {
        _uiState.value = _uiState.value.copy(subtitleAnalysisResult = null)
    }
    
    private fun checkExistingAnalysis(contentId: String) {
        viewModelScope.launch {
            try {
                val existingFilterLevels = openSubtitlesRepository.getExistingFilterLevels(contentId)
                _uiState.value = _uiState.value.copy(existingFilterLevels = existingFilterLevels)
                
                // If there are existing analyses, check the current profile's filter level
                // and load that analysis if it exists
                // This will be properly handled by checkCanAnalyzeProfanity when it's called
            } catch (e: Exception) {
                // Ignore errors when checking existing analysis
            }
        }
    }
    
    fun checkCanAnalyzeProfanity() {
        viewModelScope.launch {
            val movie = _uiState.value.movie
            if (movie != null) {
                try {
                    android.util.Log.d("MovieDetailsViewModel", "Checking analysis for movie: ${movie.title} (${movie.ratingKey})")
                    
                    // Check for Demo Mode based on movie ID
                    if (movie.ratingKey.startsWith("demo_")) {
                        android.util.Log.d("MovieDetailsViewModel", "Demo movie detected: ${movie.ratingKey}")
                        val demoAnalysis = com.purestream.data.demo.DemoData.getDemoSubtitleAnalysis(movie.ratingKey)
                        if (demoAnalysis != null) {
                            android.util.Log.d("MovieDetailsViewModel", "Loaded demo analysis for: ${movie.title}")
                            _uiState.value = _uiState.value.copy(
                                subtitleAnalysisResult = demoAnalysis,
                                subtitleAnalysisError = null,
                                canAnalyzeProfanity = false, // Disable button since analysis exists
                                existingFilterLevels = ProfanityFilterLevel.values().toList()
                            )
                            return@launch
                        }
                    }

                    // Get existing filter levels for this content (filter-agnostic)
                    val existingFilterLevels = openSubtitlesRepository.getExistingFilterLevels(movie.ratingKey)
                    val hasAnyAnalysis = existingFilterLevels.isNotEmpty()
                    
                    android.util.Log.d("MovieDetailsViewModel", "Existing filter levels: $existingFilterLevels, hasAnyAnalysis: $hasAnyAnalysis")
                    
                    // If any analysis exists, load it (same detected words regardless of filter level)
                    if (hasAnyAnalysis) {
                        // Load analysis from any available filter level (they should have same detected words)
                        val firstAvailableLevel = existingFilterLevels.first()
                        val existingAnalysis = openSubtitlesRepository.getExistingAnalysis(movie.ratingKey, firstAvailableLevel)
                        
                        android.util.Log.d("MovieDetailsViewModel", "Loaded existing analysis from $firstAvailableLevel level: ${existingAnalysis?.subtitleFileName} with ${existingAnalysis?.profanityWordsCount} profanity words")
                        
                        _uiState.value = _uiState.value.copy(
                            subtitleAnalysisResult = existingAnalysis,
                            subtitleAnalysisError = null,
                            canAnalyzeProfanity = false, // Disable button since analysis exists
                            existingFilterLevels = existingFilterLevels
                        )
                    } else {
                        android.util.Log.d("MovieDetailsViewModel", "No existing analysis found, clearing results")
                        // Clear previous analysis results and allow analysis
                        _uiState.value = _uiState.value.copy(
                            subtitleAnalysisResult = null,
                            subtitleAnalysisError = null,
                            canAnalyzeProfanity = true, // Enable button since no analysis exists
                            existingFilterLevels = existingFilterLevels
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MovieDetailsViewModel", "Error checking analysis: ${e.message}", e)
                    // Ignore errors and allow analysis
                    _uiState.value = _uiState.value.copy(
                        canAnalyzeProfanity = true,
                        subtitleAnalysisResult = null,
                        subtitleAnalysisError = null
                    )
                }
            }
        }
    }
    
    private fun refreshExistingFilterLevels(contentId: String) {
        viewModelScope.launch {
            try {
                val existingFilterLevels = openSubtitlesRepository.getExistingFilterLevels(contentId)
                _uiState.value = _uiState.value.copy(existingFilterLevels = existingFilterLevels)
                android.util.Log.d("MovieDetailsViewModel", "Refreshed existing filter levels: $existingFilterLevels")
            } catch (e: Exception) {
                android.util.Log.e("MovieDetailsViewModel", "Error refreshing filter levels: ${e.message}")
            }
        }
    }
    
    /**
     * Progressive analysis - analyze current filter level first for immediate results,
     * then analyze remaining levels in background. Optimized for TV hardware performance.
     */
    fun analyzeMovieProfanityProgressive(movie: Movie, priorityLevel: ProfanityFilterLevel = ProfanityFilterLevel.MILD) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzingSubtitles = true,
                subtitleAnalysisError = null,
                subtitleAnalysisResult = null
            )
            
            try {
                android.util.Log.d("MovieDetailsViewModel", "Starting progressive analysis for: ${movie.title}, priority level: $priorityLevel")
                
                // Check for Demo Mode
                if (movie.ratingKey.startsWith("demo_")) {
                    android.util.Log.d("MovieDetailsViewModel", "Demo mode analysis requested for: ${movie.title}")
                    delay(1500) // Simulate network delay
                    
                    val demoAnalysis = com.purestream.data.demo.DemoData.getDemoSubtitleAnalysis(movie.ratingKey)
                    if (demoAnalysis != null) {
                        _uiState.value = _uiState.value.copy(
                            isAnalyzingSubtitles = false,
                            subtitleAnalysisResult = demoAnalysis,
                            canAnalyzeProfanity = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isAnalyzingSubtitles = false,
                            subtitleAnalysisError = "Demo analysis data not found"
                        )
                    }
                    return@launch
                }

                // Check existing analyses
                val existingLevels = openSubtitlesRepository.getExistingFilterLevels(movie.ratingKey)
                val allLevels = ProfanityFilterLevel.values().toList()
                val missingLevels = allLevels - existingLevels.toSet()
                
                // If priority level already exists, load it immediately
                if (priorityLevel in existingLevels) {
                    android.util.Log.d("MovieDetailsViewModel", "Priority level $priorityLevel already analyzed, loading immediately")
                    val existingAnalysis = openSubtitlesRepository.getExistingAnalysis(movie.ratingKey, priorityLevel)
                    if (existingAnalysis != null) {
                        _uiState.value = _uiState.value.copy(
                            isAnalyzingSubtitles = false,
                            subtitleAnalysisResult = existingAnalysis,
                            canAnalyzeProfanity = missingLevels.isNotEmpty()
                        )
                        
                        // Continue with background analysis if other levels missing
                        if (missingLevels.isNotEmpty()) {
                            analyzeRemainingLevelsInBackground(movie, missingLevels)
                        }
                        return@launch
                    }
                }
                
                if (missingLevels.isEmpty()) {
                    android.util.Log.d("MovieDetailsViewModel", "All levels analyzed, loading best available result")
                    val bestAnalysis = openSubtitlesRepository.getExistingAnalysis(movie.ratingKey, priorityLevel)
                        ?: openSubtitlesRepository.getExistingAnalysis(movie.ratingKey, existingLevels.firstOrNull() ?: ProfanityFilterLevel.MILD)
                    
                    _uiState.value = _uiState.value.copy(
                        isAnalyzingSubtitles = false,
                        subtitleAnalysisResult = bestAnalysis,
                        canAnalyzeProfanity = false
                    )
                    return@launch
                }
                
                // Search for subtitles once
                val imdbId = ImdbIdExtractor.extractImdbId(movie.guid)
                android.util.Log.d("MovieDetailsViewModel", "Progressive analysis - searching subtitles for: ${movie.title}")
                
                val subtitlesResult = openSubtitlesRepository.searchSubtitlesForMovie(
                    movieTitle = movie.title,
                    imdbId = imdbId,
                    year = movie.year
                )
                
                subtitlesResult.fold(
                    onSuccess = { subtitlesList ->
                        if (subtitlesList.isNotEmpty()) {
                            val bestSubtitle = subtitlesList.first()
                            android.util.Log.d("MovieDetailsViewModel", "Found subtitle, analyzing priority level $priorityLevel first")
                            
                            // Step 1: Analyze priority level first for immediate results
                            if (priorityLevel in missingLevels) {
                                val priorityResult = openSubtitlesRepository.downloadAndAnalyzeSubtitleForAllLevels(
                                    subtitleResult = bestSubtitle,
                                    contentId = movie.ratingKey,
                                    contentType = "movie",
                                    contentTitle = movie.title,
                                    showTitle = null,
                                    seasonNumber = null,
                                    episodeNumber = null,
                                    movieTitle = movie.title,
                                    episodeInfo = null,
                                    filterLevels = listOf(priorityLevel) // Only analyze priority level first
                                )
                                
                                priorityResult.fold(
                                    onSuccess = { analysisMap ->
                                        val priorityAnalysis = analysisMap[priorityLevel]
                                        if (priorityAnalysis != null) {
                                            android.util.Log.d("MovieDetailsViewModel", "Priority level $priorityLevel analyzed successfully - showing immediate results")
                                            
                                            _uiState.value = _uiState.value.copy(
                                                isAnalyzingSubtitles = false,
                                                subtitleAnalysisResult = priorityAnalysis,
                                                canAnalyzeProfanity = false // Disable button to prevent interference with background analysis
                                            )
                                            
                                            // Step 2: Start background analysis for remaining levels
                                            val remainingLevels = missingLevels - priorityLevel
                                            if (remainingLevels.isNotEmpty()) {
                                                android.util.Log.d("MovieDetailsViewModel", "Starting background analysis for ${remainingLevels.size} remaining levels")
                                                analyzeRemainingLevelsInBackground(movie, remainingLevels)
                                            }
                                            
                                            refreshExistingFilterLevels(movie.ratingKey)
                                        } else {
                                            android.util.Log.e("MovieDetailsViewModel", "Priority level analysis failed")
                                            _uiState.value = _uiState.value.copy(
                                                isAnalyzingSubtitles = false,
                                                subtitleAnalysisError = "Failed to analyze priority filter level"
                                            )
                                        }
                                    },
                                    onFailure = { exception ->
                                        android.util.Log.e("MovieDetailsViewModel", "Priority analysis failed: ${exception.message}")
                                        _uiState.value = _uiState.value.copy(
                                            isAnalyzingSubtitles = false,
                                            subtitleAnalysisError = "Failed to analyze: ${exception.message}"
                                        )
                                    }
                                )
                            } else {
                                // Priority level not in missing levels, analyze any missing level
                                val anyMissingLevel = missingLevels.first()
                                val anyResult = openSubtitlesRepository.downloadAndAnalyzeSubtitleForAllLevels(
                                    subtitleResult = bestSubtitle,
                                    contentId = movie.ratingKey,
                                    contentType = "movie",
                                    contentTitle = movie.title,
                                    showTitle = null,
                                    seasonNumber = null,
                                    episodeNumber = null,
                                    movieTitle = movie.title,
                                    episodeInfo = null,
                                    filterLevels = listOf(anyMissingLevel)
                                )
                                
                                anyResult.fold(
                                    onSuccess = { analysisMap ->
                                        val analysis = analysisMap[anyMissingLevel]
                                        if (analysis != null) {
                                            _uiState.value = _uiState.value.copy(
                                                isAnalyzingSubtitles = false,
                                                subtitleAnalysisResult = analysis,
                                                canAnalyzeProfanity = false // Disable button to prevent interference with background analysis
                                            )
                                            
                                            val remainingLevels = missingLevels - anyMissingLevel
                                            if (remainingLevels.isNotEmpty()) {
                                                analyzeRemainingLevelsInBackground(movie, remainingLevels)
                                            }
                                            refreshExistingFilterLevels(movie.ratingKey)
                                        }
                                    },
                                    onFailure = { exception ->
                                        _uiState.value = _uiState.value.copy(
                                            isAnalyzingSubtitles = false,
                                            subtitleAnalysisError = "Analysis failed: ${exception.message}"
                                        )
                                    }
                                )
                            }
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isAnalyzingSubtitles = false,
                                subtitleAnalysisError = "No subtitles found for this movie"
                            )
                        }
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isAnalyzingSubtitles = false,
                            subtitleAnalysisError = "Failed to search for subtitles: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingSubtitles = false,
                    subtitleAnalysisError = "Unexpected error during analysis: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Analyzes remaining filter levels in background without blocking UI
     */
    private fun analyzeRemainingLevelsInBackground(movie: Movie, remainingLevels: List<ProfanityFilterLevel>) {
        viewModelScope.launch {
            try {
                android.util.Log.d("MovieDetailsViewModel", "Background analysis starting for ${remainingLevels.size} levels")
                
                val imdbId = ImdbIdExtractor.extractImdbId(movie.guid)
                val subtitlesResult = openSubtitlesRepository.searchSubtitlesForMovie(
                    movieTitle = movie.title,
                    imdbId = imdbId,
                    year = movie.year
                )
                
                subtitlesResult.fold(
                    onSuccess = { subtitlesList ->
                        if (subtitlesList.isNotEmpty()) {
                            val bestSubtitle = subtitlesList.first()
                            
                            // Analyze remaining levels
                            val backgroundResult = openSubtitlesRepository.downloadAndAnalyzeSubtitleForAllLevels(
                                subtitleResult = bestSubtitle,
                                contentId = movie.ratingKey,
                                contentType = "movie",
                                contentTitle = movie.title,
                                showTitle = null,
                                seasonNumber = null,
                                episodeNumber = null,
                                movieTitle = movie.title,
                                episodeInfo = null,
                                filterLevels = remainingLevels
                            )
                            
                            backgroundResult.fold(
                                onSuccess = { analysisMap ->
                                    android.util.Log.d("MovieDetailsViewModel", "Background analysis completed for ${analysisMap.size} levels")
                                    refreshExistingFilterLevels(movie.ratingKey)
                                },
                                onFailure = { exception ->
                                    android.util.Log.e("MovieDetailsViewModel", "Background analysis failed: ${exception.message}")
                                }
                            )
                        }
                    },
                    onFailure = { exception ->
                        android.util.Log.e("MovieDetailsViewModel", "Background subtitle search failed: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("MovieDetailsViewModel", "Background analysis error: ${e.message}")
            }
        }
    }
    
    /**
     * Keep the original method for compatibility, but make it use progressive analysis
     */
    fun analyzeMovieProfanityAllLevels(movie: Movie) {
        analyzeMovieProfanityProgressive(movie, ProfanityFilterLevel.MILD)
    }

    // Initialize watch progress repository with context and profile
    fun initializeProgressTracking(context: android.content.Context, profileId: String) {
        watchProgressRepository = WatchProgressRepository(AppDatabase.getDatabase(context), context)
        currentProfileId = profileId
        // Load progress for current movie if loaded
        _uiState.value.movie?.let { movie ->
            loadProgress(movie.ratingKey)
        }
    }

    // Load watch progress for the movie
    private fun loadProgress(ratingKey: String) {
        val repository = watchProgressRepository
        val profileId = currentProfileId

        if (repository == null || profileId.isEmpty()) {
            android.util.Log.w("MovieDetailsViewModel", "Cannot load progress - repository=${repository != null}, profileId='$profileId'")
            return
        }

        android.util.Log.d("MovieDetailsViewModel", "📂 Loading progress for movie: $ratingKey, profileId=$profileId")

        viewModelScope.launch {
            try {
                val (progress, position) = withContext(Dispatchers.IO) {
                    val percentage = repository.getProgressPercentage(profileId, ratingKey)
                    val pos = repository.getProgressPosition(profileId, ratingKey)
                    percentage to pos
                }

                _uiState.value = _uiState.value.copy(
                    progressPercentage = if (progress > 0f) progress else null,
                    progressPosition = if (position > 0L) position else null
                )
                android.util.Log.d("MovieDetailsViewModel", "✓ Loaded progress: ${(progress * 100).toInt()}% at position $position ms")
            } catch (e: Exception) {
                android.util.Log.e("MovieDetailsViewModel", "✗ Error loading progress: ${e.message}", e)
            }
        }
    }

    fun reset() {
        _uiState.value = MovieDetailsState()
    }

}