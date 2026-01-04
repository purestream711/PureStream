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

data class TvShowDetailsState(
    val tvShow: TvShow? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeason: Season? = null,
    val episodes: List<Episode> = emptyList(),
    val currentEpisode: Episode? = null,
    val videoUrl: String? = null,
    val isLoading: Boolean = false,
    val isLoadingSeasons: Boolean = false,
    val isLoadingEpisodes: Boolean = false,
    val isLoadingCurrentEpisode: Boolean = false,
    val isLoadingVideo: Boolean = false,
    val error: String? = null,
    val seasonsError: String? = null,
    val episodesError: String? = null,
    val currentEpisodeError: String? = null,
    val videoError: String? = null,
    val isAnalyzingSubtitles: Boolean = false,
    val subtitleAnalysisResult: SubtitleAnalysisResult? = null,
    val subtitleAnalysisError: String? = null,
    val canAnalyzeProfanity: Boolean = true,
    val existingFilterLevels: List<ProfanityFilterLevel> = emptyList(),
    val episodeProgressMap: Map<String, Float> = emptyMap(), // ratingKey -> progress percentage (0.0-1.0)
    val episodeProgressPositionMap: Map<String, Long> = emptyMap() // ratingKey -> progress position in milliseconds
)

class TvShowDetailsViewModel(
    context: Context,
    private val plexRepository: PlexRepository = PlexRepository(context),
    private val profanityFilter: ProfanityFilter = ProfanityFilter(),
    private val openSubtitlesRepository: OpenSubtitlesRepository = OpenSubtitlesRepository(ProfanityFilter()),
    private val subtitleAnalysisRepository: SubtitleAnalysisRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvShowDetailsState())
    val uiState: StateFlow<TvShowDetailsState> = _uiState.asStateFlow()

    // Watch progress tracking
    private var watchProgressRepository: WatchProgressRepository? = null
    private var currentProfileId: String = ""
    
    fun loadTvShowDetails(showId: String, maxRetries: Int = 10) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            loadTvShowDetailsWithRetry(showId, maxRetries, 0)
        }
    }

    fun setTvShow(tvShow: TvShow) {
        _uiState.value = _uiState.value.copy(tvShow = tvShow)
        loadSeasons(tvShow.ratingKey)
    }

    fun setEpisode(episode: Episode) {
        _uiState.value = _uiState.value.copy(currentEpisode = episode)
        // Load watch progress for this specific episode
        _uiState.value.tvShow?.let {
            loadProgressForEpisodes()
        }
    }
    
    private suspend fun loadTvShowDetailsWithRetry(showId: String, maxRetries: Int, currentAttempt: Int) {
        try {
            val showResult = plexRepository.getTvShowById(showId)
            showResult.fold(
                onSuccess = { tvShow ->
                    if (tvShow != null) {
                        _uiState.value = _uiState.value.copy(
                            tvShow = tvShow,
                            isLoading = false
                        )
                        // Automatically load seasons for the TV show
                        loadSeasons(tvShow.ratingKey)
                    } else {
                        if (currentAttempt < maxRetries) {
                            // Retry with exponential backoff
                            val delayMs = (1000L * (currentAttempt + 1)) // 1s, 2s, 3s delays
                            android.util.Log.d("TvShowDetailsViewModel", "Retry attempt ${currentAttempt + 1}/$maxRetries after ${delayMs}ms delay (null show)")
                            delay(delayMs)
                            loadTvShowDetailsWithRetry(showId, maxRetries, currentAttempt + 1)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                error = "TV Show not found after $maxRetries attempts",
                                isLoading = false
                            )
                        }
                    }
                },
                onFailure = { exception ->
                    if (currentAttempt < maxRetries) {
                        // Retry with exponential backoff
                        val delayMs = (1000L * (currentAttempt + 1)) // 1s, 2s, 3s delays
                        android.util.Log.d("TvShowDetailsViewModel", "Retry attempt ${currentAttempt + 1}/$maxRetries after ${delayMs}ms delay")
                        delay(delayMs)
                        loadTvShowDetailsWithRetry(showId, maxRetries, currentAttempt + 1)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to load TV show details after $maxRetries attempts: ${exception.message}",
                            isLoading = false
                        )
                    }
                }
            )
        } catch (e: Exception) {
            if (currentAttempt < maxRetries) {
                // Retry with exponential backoff
                val delayMs = (1000L * (currentAttempt + 1)) // 1s, 2s, 3s delays
                android.util.Log.d("TvShowDetailsViewModel", "Retry attempt ${currentAttempt + 1}/$maxRetries after ${delayMs}ms delay (Exception: ${e.message})")
                delay(delayMs)
                loadTvShowDetailsWithRetry(showId, maxRetries, currentAttempt + 1)
            } else {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load TV show details after $maxRetries attempts: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    fun loadSeasons(tvShowId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSeasons = true, seasonsError = null)
            
            try {
                val seasonsResult = plexRepository.getSeasons(tvShowId)
                seasonsResult.fold(
                    onSuccess = { seasons ->
                        _uiState.value = _uiState.value.copy(
                            seasons = seasons,
                            isLoadingSeasons = false
                        )
                        
                        // Automatically select the first season and load its episodes
                        if (seasons.isNotEmpty()) {
                            selectSeason(seasons.first())
                        }
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            seasonsError = "Failed to load seasons: ${exception.message}",
                            isLoadingSeasons = false
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    seasonsError = "Failed to load seasons: ${e.message}",
                    isLoadingSeasons = false
                )
            }
        }
    }
    
    fun retryLoadSeasons() {
        _uiState.value.tvShow?.let { tvShow ->
            loadSeasons(tvShow.ratingKey)
        }
    }
    
    fun selectSeason(season: Season) {
        android.util.Log.d("TvShowDetailsViewModel", "Selecting season: ${season.title} (ID: ${season.ratingKey})")
        _uiState.value = _uiState.value.copy(selectedSeason = season)
        loadEpisodes(season.ratingKey)
    }
    
    fun loadEpisodes(seasonId: String) {
        viewModelScope.launch {
            android.util.Log.d("TvShowDetailsViewModel", "Starting to load episodes for season: $seasonId")
            _uiState.value = _uiState.value.copy(isLoadingEpisodes = true, episodesError = null)
            
            try {
                val episodesResult = plexRepository.getEpisodes(seasonId)
                episodesResult.fold(
                    onSuccess = { episodes ->
                        android.util.Log.d("TvShowDetailsViewModel", "Successfully loaded ${episodes.size} episodes")
                        _uiState.value = _uiState.value.copy(
                            episodes = episodes,
                            isLoadingEpisodes = false
                        )

                        // Load watch progress for episodes
                        loadProgressForEpisodes()
                    },
                    onFailure = { exception ->
                        android.util.Log.e("TvShowDetailsViewModel", "Failed to load episodes: ${exception.message}")
                        _uiState.value = _uiState.value.copy(
                            episodesError = "Failed to load episodes: ${exception.message}",
                            isLoadingEpisodes = false
                        )
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("TvShowDetailsViewModel", "Exception loading episodes", e)
                _uiState.value = _uiState.value.copy(
                    episodesError = "Failed to load episodes: ${e.message}",
                    isLoadingEpisodes = false
                )
            }
        }
    }
    
    fun getVideoUrl(episode: Episode) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingVideo = true, videoError = null)
            
            try {
                // Get the video URL directly (subtitle analysis is now handled separately via Analyze Profanity button)
                
                // Now get the video URL
                val videoUrlResult = plexRepository.getEpisodeVideoStreamUrl(episode)
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
    
    fun playFirstEpisode() {
        val firstEpisode = _uiState.value.episodes.firstOrNull()
        if (firstEpisode != null) {
            getVideoUrl(firstEpisode)
        } else {
            _uiState.value = _uiState.value.copy(
                videoError = "No episodes available to play"
            )
        }
    }
    
    fun clearVideoUrl() {
        _uiState.value = _uiState.value.copy(videoUrl = null)
    }
    
    fun clearVideoError() {
        _uiState.value = _uiState.value.copy(videoError = null)
    }
    
    fun clearSeasonsError() {
        _uiState.value = _uiState.value.copy(seasonsError = null)
    }
    
    fun clearEpisodesError() {
        _uiState.value = _uiState.value.copy(episodesError = null)
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
    
    fun analyzeEpisodeProfanity(episode: Episode, filterLevel: ProfanityFilterLevel) {
        val tvShow = _uiState.value.tvShow ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzingSubtitles = true,
                subtitleAnalysisError = null,
                subtitleAnalysisResult = null
            )
            
            try {
                android.util.Log.d("TvShowDetailsViewModel", "Starting automated subtitle search and analysis for:")
                android.util.Log.d("TvShowDetailsViewModel", "  - Show: '${tvShow.title}' (${tvShow.year})")
                android.util.Log.d("TvShowDetailsViewModel", "  - Episode: S${episode.seasonNumber}E${episode.episodeNumber} - '${episode.title}'")
                
                // Use the persistent analysis method to save results to database
                // Extract IMDB ID from TV show metadata for more accurate search
                val imdbId = ImdbIdExtractor.extractImdbIdForTvShow(tvShow.guid, episode.guid)
                android.util.Log.d("TvShowDetailsViewModel", "Extracted IMDB ID: $imdbId for show: ${tvShow.title}")
                
                val searchResult = openSubtitlesRepository.searchSubtitlesForEpisode(tvShow.title, episode.seasonNumber, episode.episodeNumber, tvShow.year, imdbId)
                if (searchResult.isFailure) {
                    throw Exception("Failed to find episode subtitles: ${searchResult.exceptionOrNull()?.message}")
                }
                
                val subtitles = searchResult.getOrThrow()
                if (subtitles.isEmpty()) {
                    throw Exception("No subtitles available for '${tvShow.title}' S${episode.seasonNumber}E${episode.episodeNumber}")
                }
                
                val bestSubtitle = subtitles.first()
                val episodeInfo = "S${episode.seasonNumber.toString().padStart(2, '0')}E${episode.episodeNumber.toString().padStart(2, '0')}"
                // Use combined contentId format to match playback navigation
                val contentId = "${tvShow.ratingKey}_${episode.ratingKey}"
                val analysisResult = openSubtitlesRepository.downloadAndAnalyzeSubtitlePersistent(
                    subtitleResult = bestSubtitle,
                    contentId = contentId,
                    contentType = "episode",
                    contentTitle = episode.title,
                    showTitle = tvShow.title,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    movieTitle = tvShow.title,
                    episodeInfo = episodeInfo,
                    filterLevel = filterLevel
                )
                
                analysisResult.fold(
                    onSuccess = { analysis ->
                        android.util.Log.d("TvShowDetailsViewModel", "Automated analysis completed successfully for episode: ${episode.title}")
                        _uiState.value = _uiState.value.copy(
                            isAnalyzingSubtitles = false,
                            subtitleAnalysisResult = analysis,
                            canAnalyzeProfanity = false
                        )
                    },
                    onFailure = { exception ->
                        android.util.Log.e("TvShowDetailsViewModel", "Automated analysis failed for episode: ${episode.title} - ${exception.message}")
                        _uiState.value = _uiState.value.copy(
                            isAnalyzingSubtitles = false,
                            subtitleAnalysisError = "Failed to analyze subtitles: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("TvShowDetailsViewModel", "Error during automated subtitle analysis: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isAnalyzingSubtitles = false,
                    subtitleAnalysisError = "Error during subtitle analysis: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Analyze episode profanity for all filter levels at once, similar to movie analysis.
     * This provides comprehensive analysis and better consistency with movie behavior.
     */
    fun analyzeEpisodeProfanityAllLevels(episode: Episode) {
        analyzeEpisodeProfanityProgressive(episode, ProfanityFilterLevel.MILD)
    }
    
    /**
     * Progressive analysis - analyze priority filter level first for immediate results,
     * then analyze remaining levels in background. Optimized for TV hardware performance.
     */
    fun analyzeEpisodeProfanityProgressive(episode: Episode, priorityLevel: ProfanityFilterLevel = ProfanityFilterLevel.MILD) {
        val tvShow = _uiState.value.tvShow ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzingSubtitles = true,
                subtitleAnalysisError = null,
                subtitleAnalysisResult = null
            )
            
            try {
                android.util.Log.d("TvShowDetailsViewModel", "Starting progressive analysis for episode: ${episode.title}, priority level: $priorityLevel")
                
                // Use combined contentId format to match playback navigation
                val contentId = "${tvShow.ratingKey}_${episode.ratingKey}"
                
                // Check existing analyses
                val existingLevels = openSubtitlesRepository.getExistingFilterLevels(contentId)
                val allLevels = ProfanityFilterLevel.values().toList()
                val missingLevels = allLevels - existingLevels.toSet()
                
                if (missingLevels.isEmpty()) {
                    android.util.Log.d("TvShowDetailsViewModel", "All filter levels already analyzed for episode: ${episode.title}")
                    _uiState.value = _uiState.value.copy(
                        isAnalyzingSubtitles = false,
                        canAnalyzeProfanity = false
                    )
                    return@launch
                }
                
                // Process priority level first if missing
                val levelOrder = if (missingLevels.contains(priorityLevel)) {
                    listOf(priorityLevel) + (missingLevels - priorityLevel)
                } else {
                    missingLevels
                }
                
                android.util.Log.d("TvShowDetailsViewModel", "Will analyze ${levelOrder.size} missing levels for episode ${episode.title}: $levelOrder")
                
                // Extract IMDB ID for accurate search
                val imdbId = ImdbIdExtractor.extractImdbIdForTvShow(tvShow.guid, episode.guid)
                
                for ((index, filterLevel) in levelOrder.withIndex()) {
                    android.util.Log.d("TvShowDetailsViewModel", "Analyzing level $filterLevel (${index + 1}/${levelOrder.size}) for episode: ${episode.title}")
                    
                    val searchResult = openSubtitlesRepository.searchSubtitlesForEpisode(tvShow.title, episode.seasonNumber, episode.episodeNumber, tvShow.year, imdbId)
                    if (searchResult.isFailure) {
                        throw Exception("Failed to find episode subtitles: ${searchResult.exceptionOrNull()?.message}")
                    }
                    
                    val subtitles = searchResult.getOrThrow()
                    if (subtitles.isEmpty()) {
                        throw Exception("No subtitles available for '${tvShow.title}' S${episode.seasonNumber}E${episode.episodeNumber}")
                    }
                    
                    val bestSubtitle = subtitles.first()
                    val episodeInfo = "S${episode.seasonNumber.toString().padStart(2, '0')}E${episode.episodeNumber.toString().padStart(2, '0')}"
                    val analysisResult = openSubtitlesRepository.downloadAndAnalyzeSubtitlePersistent(
                        subtitleResult = bestSubtitle,
                        contentId = contentId,
                        contentType = "episode",
                        contentTitle = episode.title,
                        showTitle = tvShow.title,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        movieTitle = tvShow.title,
                        episodeInfo = episodeInfo,
                        filterLevel = filterLevel
                    )
                    
                    analysisResult.fold(
                        onSuccess = { analysis ->
                            android.util.Log.d("TvShowDetailsViewModel", "Analysis completed for level $filterLevel on episode: ${episode.title}")
                            
                            // Update UI after priority level (first analysis)
                            if (filterLevel == priorityLevel) {
                                _uiState.value = _uiState.value.copy(
                                    subtitleAnalysisResult = analysis
                                )
                            }
                        },
                        onFailure = { exception ->
                            android.util.Log.e("TvShowDetailsViewModel", "Analysis failed for level $filterLevel on episode: ${episode.title} - ${exception.message}")
                            if (filterLevel == priorityLevel) {
                                // If priority level fails, stop the whole process
                                throw exception
                            }
                            // For non-priority levels, log but continue
                        }
                    )
                }
                
                // Mark analysis as complete
                _uiState.value = _uiState.value.copy(
                    isAnalyzingSubtitles = false,
                    canAnalyzeProfanity = false
                )
                
                android.util.Log.d("TvShowDetailsViewModel", "Progressive analysis completed for episode: ${episode.title}")
                
            } catch (e: Exception) {
                android.util.Log.e("TvShowDetailsViewModel", "Error during progressive episode analysis: ${e.message}")
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
            } catch (e: Exception) {
                // Ignore errors when checking existing analysis
            }
        }
    }
    
    fun checkCanAnalyzeProfanity(episode: Episode, filterLevel: ProfanityFilterLevel) {
        viewModelScope.launch {
            val tvShow = _uiState.value.tvShow
            if (tvShow != null) {
                try {
                    // Use combined contentId format to match playback navigation and analysis storage
                    val contentId = "${tvShow.ratingKey}_${episode.ratingKey}"
                    android.util.Log.d("TvShowDetailsViewModel", "Checking analysis for episode: ${episode.title} with contentId: $contentId")
                    
                    // Get existing filter levels for this content (filter-agnostic)
                    val existingFilterLevels = openSubtitlesRepository.getExistingFilterLevels(contentId)
                    val hasAnyAnalysis = existingFilterLevels.isNotEmpty()
                    
                    android.util.Log.d("TvShowDetailsViewModel", "Existing filter levels: $existingFilterLevels, hasAnyAnalysis: $hasAnyAnalysis")
                    
                    // If any analysis exists, load it (same detected words regardless of filter level)
                    if (hasAnyAnalysis) {
                        // Load analysis from any available filter level (they should have same detected words)
                        val firstAvailableLevel = existingFilterLevels.first()
                        val existingAnalysis = openSubtitlesRepository.getExistingAnalysis(contentId, firstAvailableLevel)
                        
                        android.util.Log.d("TvShowDetailsViewModel", "Loaded existing analysis from $firstAvailableLevel level: ${existingAnalysis?.subtitleFileName} with ${existingAnalysis?.profanityWordsCount} profanity words")
                        
                        _uiState.value = _uiState.value.copy(
                            subtitleAnalysisResult = existingAnalysis,
                            subtitleAnalysisError = null,
                            canAnalyzeProfanity = false, // Disable button since analysis exists
                            existingFilterLevels = existingFilterLevels
                        )
                    } else {
                        android.util.Log.d("TvShowDetailsViewModel", "No existing analysis found, clearing results")
                        // Clear previous analysis results and allow analysis
                        _uiState.value = _uiState.value.copy(
                            subtitleAnalysisResult = null,
                            subtitleAnalysisError = null,
                            canAnalyzeProfanity = true, // Enable button since no analysis exists
                            existingFilterLevels = existingFilterLevels
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TvShowDetailsViewModel", "Error checking analysis: ${e.message}", e)
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
    
    
    fun loadEpisodeById(episodeId: String, maxRetries: Int = 3) {
        viewModelScope.launch {
            android.util.Log.d("TvShowDetailsViewModel", "Loading episode by ID: $episodeId")
            _uiState.value = _uiState.value.copy(isLoadingCurrentEpisode = true, currentEpisodeError = null)
            
            loadEpisodeByIdWithRetry(episodeId, maxRetries, 0)
        }
    }
    
    private suspend fun loadEpisodeByIdWithRetry(episodeId: String, maxRetries: Int, currentAttempt: Int) {
        try {
            val episodeResult = plexRepository.getEpisodeWithShowDetails(episodeId)
            episodeResult.fold(
                onSuccess = { (episode, tvShow) ->
                    android.util.Log.d("TvShowDetailsViewModel", "Successfully loaded episode: ${episode.title}")
                    android.util.Log.d("TvShowDetailsViewModel", "Show title: ${tvShow?.title ?: "Not found"}")

                    _uiState.value = _uiState.value.copy(
                        currentEpisode = episode,
                        isLoadingCurrentEpisode = false
                    )

                    // Set the TV show details if we got them
                    if (tvShow != null) {
                        _uiState.value = _uiState.value.copy(tvShow = tvShow)
                    } else if (_uiState.value.tvShow == null) {
                        // Fallback: create a basic show info from episode
                        loadTvShowFromEpisode(episode)
                    }

                    // Load progress for this episode
                    loadProgressForCurrentEpisode()
                },
                onFailure = { exception ->
                    val errorMessage = exception.message ?: "Unknown error"
                    android.util.Log.e("TvShowDetailsViewModel", "Failed to load episode: $errorMessage")
                    
                    // Check if it's a token-related error and we should retry
                    if (shouldRetryEpisodeLoad(errorMessage) && currentAttempt < maxRetries) {
                        val delayMs = (1000L * (currentAttempt + 1)) // 1s, 2s, 3s delays
                        android.util.Log.d("TvShowDetailsViewModel", "Retry attempt ${currentAttempt + 1}/$maxRetries for episode load after ${delayMs}ms delay")
                        delay(delayMs)
                        loadEpisodeByIdWithRetry(episodeId, maxRetries, currentAttempt + 1)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            currentEpisodeError = if (currentAttempt >= maxRetries) 
                                "Failed to load episode after $maxRetries attempts: $errorMessage" 
                            else 
                                "Failed to load episode: $errorMessage",
                            isLoadingCurrentEpisode = false
                        )
                    }
                }
            )
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error"
            android.util.Log.e("TvShowDetailsViewModel", "Exception loading episode", e)
            
            // Check if it's a token-related error and we should retry
            if (shouldRetryEpisodeLoad(errorMessage) && currentAttempt < maxRetries) {
                val delayMs = (1000L * (currentAttempt + 1)) // 1s, 2s, 3s delays
                android.util.Log.d("TvShowDetailsViewModel", "Retry attempt ${currentAttempt + 1}/$maxRetries for episode load after ${delayMs}ms delay (Exception: $errorMessage)")
                delay(delayMs)
                loadEpisodeByIdWithRetry(episodeId, maxRetries, currentAttempt + 1)
            } else {
                _uiState.value = _uiState.value.copy(
                    currentEpisodeError = if (currentAttempt >= maxRetries) 
                        "Failed to load episode after $maxRetries attempts: $errorMessage" 
                    else 
                        "Failed to load episode: $errorMessage",
                    isLoadingCurrentEpisode = false
                )
            }
        }
    }
    
    private fun shouldRetryEpisodeLoad(errorMessage: String): Boolean {
        val retryableErrors = listOf(
            "no token available",
            "token",
            "authentication",
            "unauthorized",
            "connection",
            "timeout",
            "network"
        )
        return retryableErrors.any { errorMessage.contains(it, ignoreCase = true) }
    }
    
    private fun loadTvShowFromEpisode(episode: Episode) {
        // This is a fallback to get basic show info for the episode details screen
        // Try to derive show name from episode key path if possible
        val showName = try {
            // Episode keys often follow patterns like "/library/metadata/123"
            // We can try to use a generic show name based on the episode's series info
            "TV Show" // Generic fallback - this shouldn't be used if parent info is available
        } catch (e: Exception) {
            "TV Show" // Final fallback
        }
        
        android.util.Log.w("TvShowDetailsViewModel", "Using fallback show name: $showName for episode: ${episode.title}")
        
        _uiState.value = _uiState.value.copy(
            tvShow = TvShow(
                ratingKey = "unknown",
                key = "unknown", 
                title = showName,
                summary = null,
                thumbUrl = episode.thumbUrl,
                artUrl = episode.thumbUrl,
                year = episode.year,
                rating = episode.rating,
                contentRating = null,
                studio = null,
                episodeCount = null,
                seasonCount = null
            )
        )
    }
    
    
    fun clearCurrentEpisodeError() {
        _uiState.value = _uiState.value.copy(currentEpisodeError = null)
    }

    // Initialize watch progress repository with context and profile
    fun initializeProgressTracking(context: android.content.Context, profileId: String) {
        watchProgressRepository = WatchProgressRepository(AppDatabase.getDatabase(context), context)
        currentProfileId = profileId
        // Load progress for current episodes if any exist
        if (_uiState.value.episodes.isNotEmpty()) {
            loadProgressForEpisodes()
        }
    }

    // Load watch progress for all currently loaded episodes
    fun loadProgressForEpisodes() {
        val repository = watchProgressRepository
        val profileId = currentProfileId

        if (repository == null || profileId.isEmpty()) {
            android.util.Log.w("TvShowDetailsViewModel", "Cannot load progress - repository or profileId not initialized")
            return
        }

        viewModelScope.launch {
            try {
                val progressMap = mutableMapOf<String, Float>()
                val positionMap = mutableMapOf<String, Long>()

                withContext(Dispatchers.IO) {
                    for (episode in _uiState.value.episodes) {
                        val progress = repository.getProgressPercentage(profileId, episode.ratingKey)
                        if (progress > 0f) {  // Include any progress
                            progressMap[episode.ratingKey] = progress
                            val position = repository.getProgressPosition(profileId, episode.ratingKey)
                            positionMap[episode.ratingKey] = position
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    episodeProgressMap = progressMap,
                    episodeProgressPositionMap = positionMap
                )
                android.util.Log.d("TvShowDetailsViewModel", "Loaded progress for ${progressMap.size} episodes")
            } catch (e: Exception) {
                android.util.Log.e("TvShowDetailsViewModel", "Error loading episode progress: ${e.message}", e)
            }
        }
    }

    fun refreshProgress() {
        loadProgressForEpisodes()
        loadProgressForCurrentEpisode()
    }

    /**
     * Clear all state - use when switching to demo mode or logging out
     */
    fun clearState() {
        _uiState.value = TvShowDetailsState()
        android.util.Log.d("TvShowDetailsViewModel", "State cleared - all data reset")
    }

    // Load watch progress for the current episode
    private fun loadProgressForCurrentEpisode() {
        val repository = watchProgressRepository
        val profileId = currentProfileId
        val episode = _uiState.value.currentEpisode

        if (repository == null || profileId.isEmpty() || episode == null) {
            android.util.Log.w("TvShowDetailsViewModel", "Cannot load progress for current episode - repository=$repository, profileId='$profileId', episode=$episode")
            return
        }

        viewModelScope.launch {
            try {
                val (progress, position) = withContext(Dispatchers.IO) {
                    val percentage = repository.getProgressPercentage(profileId, episode.ratingKey)
                    val pos = repository.getProgressPosition(profileId, episode.ratingKey)
                    percentage to pos
                }

                // Update the progress maps with this episode's data
                if (progress > 0f) {
                    val currentProgressMap = _uiState.value.episodeProgressMap.toMutableMap()
                    val currentPositionMap = _uiState.value.episodeProgressPositionMap.toMutableMap()

                    currentProgressMap[episode.ratingKey] = progress
                    currentPositionMap[episode.ratingKey] = position

                    _uiState.value = _uiState.value.copy(
                        episodeProgressMap = currentProgressMap,
                        episodeProgressPositionMap = currentPositionMap
                    )
                    android.util.Log.d("TvShowDetailsViewModel", "✓ Loaded progress for current episode: ${(progress * 100).toInt()}% at position ${position}ms")
                } else {
                    android.util.Log.d("TvShowDetailsViewModel", "✓ No progress found for current episode")
                }
            } catch (e: Exception) {
                android.util.Log.e("TvShowDetailsViewModel", "✗ Error loading progress for current episode: ${e.message}", e)
            }
        }
    }
}