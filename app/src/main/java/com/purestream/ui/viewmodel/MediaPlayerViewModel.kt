package com.purestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.purestream.data.model.*
import com.purestream.profanity.FilteredSubtitleManager
import com.purestream.profanity.ProfanityFilter
import com.purestream.profanity.SubtitleParser
import com.purestream.data.repository.OpenSubtitlesRepository
import com.purestream.data.repository.SubtitleAnalysisRepository
import com.purestream.data.repository.WatchProgressRepository
import com.purestream.data.repository.PlexRepository
import com.purestream.data.manager.SubtitleCacheManager
import com.purestream.data.database.AppDatabase
import com.purestream.data.database.dao.SubtitleTimingDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import android.content.Context
import android.os.PowerManager
import kotlin.math.abs

enum class SeekDirection {
    FORWARD, BACKWARD
}

data class SeekState(
    val isSeeking: Boolean = false,
    val seekDirection: SeekDirection? = null,
    val seekStartTime: Long = 0L,
    val currentSeekIncrement: Long = 2000L,
    val targetPosition: Long? = null,
    val lastSeekTime: Long = 0L,
    val seekRetryCount: Int = 0,
    val seekError: String? = null
)

data class MediaPlayerState(
    val isLoading: Boolean = false,
    val filteredSubtitleResult: FilteredSubtitleResult? = null,
    val currentPosition: Long = 0L,
    val isAudioMuted: Boolean = false,
    val currentSubtitle: String? = null,
    val showProfanityOverlay: Boolean = false,
    val subtitlesEnabled: Boolean = false,
    val subtitleTimingOffsetMs: Long = 0L, // Manual timing offset in milliseconds
    val error: String? = null,
    val hasAnalysis: Boolean = false, // Live analysis state for padlock
    val startPosition: Long = 0L, // Position to start playback from (for Resume functionality)
    val seekState: SeekState = SeekState(), // State for seek retry mechanism
    val showSeekTimeline: Boolean = false, // Timeline visibility during scrubbing
    val seekPreviewPosition: Long = 0L, // Preview position during seek
    val isSeekBuffering: Boolean = false // Distinguish seek buffering from initial loading
)

// Seek Configuration Constants
private const val SEEK_DEBOUNCE_WINDOW_MS = 500L  // Increased to give VLC time to process seeks
private const val SEEK_INCREMENT_TIER_1 = 5000L    // 0-1s hold: 5s per tick
private const val SEEK_INCREMENT_TIER_2 = 10000L   // 1-3s hold: 10s per tick
private const val SEEK_INCREMENT_TIER_3 = 30000L   // 3-5s hold: 30s per tick
private const val SEEK_INCREMENT_TIER_4 = 60000L   // 5-10s hold: 1min per tick
private const val SEEK_INCREMENT_TIER_5 = 120000L  // 10-15s hold: 2min per tick
private const val SEEK_INCREMENT_TIER_6 = 300000L  // 15s+ hold: 5min per tick
private const val SEEK_TIER_1_THRESHOLD_MS = 1000L
private const val SEEK_TIER_2_THRESHOLD_MS = 3000L
private const val SEEK_TIER_3_THRESHOLD_MS = 5000L
private const val SEEK_TIER_4_THRESHOLD_MS = 10000L
private const val SEEK_TIER_5_THRESHOLD_MS = 15000L
private const val SEEK_MAX_RETRIES = 3
private const val SEEK_RETRY_DELAY_MS = 500L
private const val TIMELINE_HIDE_DELAY_MS = 1500L

class MediaPlayerViewModel(
    private val context: android.content.Context? = null,
    private val plexRepository: PlexRepository = PlexRepository()
) : ViewModel() {

    private val profanityFilter = ProfanityFilter()

    // Coroutine job for continuous seeking
    private var seekJob: Job? = null

    // Database access for subtitle timing preferences
    private val subtitleTimingDao: SubtitleTimingDao? = context?.let {
        AppDatabase.getDatabase(it).subtitleTimingDao()
    }

    // Watch progress tracking
    private val watchProgressRepository: WatchProgressRepository? = context?.let { ctx ->
        WatchProgressRepository(AppDatabase.getDatabase(ctx), ctx)
    }

    // Progress tracking state
    private var currentMediaRatingKey: String? = null
    private var currentMediaKey: String? = null
    private var currentMediaDuration: Long = 0L
    private var currentProfileId: String = ""
    private var progressUpdateJob: Job? = null
    private var lastProgressUpdateTime: Long = 0L
    private val PROGRESS_UPDATE_INTERVAL_MS = 15000L // Update every 15 seconds

    // Store media objects for tracking (set before navigation)
    private var storedMovie: Movie? = null
    private var storedEpisode: Episode? = null
    private var storedTvShow: TvShow? = null

    // Wake lock to prevent screensaver during video playbook
    private var wakeLock: PowerManager.WakeLock? = null
    private val subtitleParser = SubtitleParser()

    // Analysis repository for monitoring analysis state
    private val subtitleAnalysisRepository: SubtitleAnalysisRepository? = context?.let { ctx ->
        SubtitleAnalysisRepository(AppDatabase.getDatabase(ctx), ctx)
    }

    private val openSubtitlesRepository = OpenSubtitlesRepository(profanityFilter, subtitleAnalysisRepository)
    private val subtitleCacheManager = SubtitleCacheManager()
    private val filteredSubtitleManager = FilteredSubtitleManager(
        profanityFilter = profanityFilter,
        subtitleParser = subtitleParser,
        openSubtitlesRepository = openSubtitlesRepository,
        subtitleCacheManager = subtitleCacheManager
    )

    private val _uiState = MutableStateFlow(MediaPlayerState())
    val uiState: StateFlow<MediaPlayerState> = _uiState.asStateFlow()
    
    fun loadFilteredSubtitles(
        movie: Movie? = null,
        tvShow: TvShow? = null,
        episode: Episode? = null,
        filterLevel: ProfanityFilterLevel
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // First, try to load from persistent analysis storage (generated by "Analyze Profanity")
                val contentId = movie?.ratingKey ?: "${tvShow?.ratingKey}_${episode?.ratingKey}"
                val persistentResult = if (contentId.isNotEmpty()) {
                    loadFromPersistentAnalysis(contentId, filterLevel)
                } else {
                    null
                }
                
                if (persistentResult != null) {
                    android.util.Log.d("MediaPlayerViewModel", "Successfully loaded from persistent analysis for filter level: $filterLevel")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        filteredSubtitleResult = persistentResult
                    )
                    return@launch
                }
                
                // Fallback to existing logic
                val result = if (movie != null) {
                    android.util.Log.d("MediaPlayerViewModel", "Processing movie subtitles for: ${movie.title}")
                    filteredSubtitleManager.processMovieSubtitles(movie, filterLevel)
                } else if (tvShow != null && episode != null) {
                    android.util.Log.d("MediaPlayerViewModel", "Processing episode subtitles for: ${episode.title}")
                    filteredSubtitleManager.processEpisodeSubtitles(tvShow, episode, filterLevel)
                } else {
                    android.util.Log.w("MediaPlayerViewModel", "No media provided for subtitle processing")
                    Result.failure(Exception("No media information available for subtitle processing"))
                }
                
                result.fold(
                    onSuccess = { filteredResult ->
                        android.util.Log.d("MediaPlayerViewModel", "Successfully loaded filtered subtitles with ${filteredResult.filteredSubtitle.entries.size} entries")
                        
                        if (filteredResult.filteredSubtitle.entries.isNotEmpty()) {
                            android.util.Log.d("MediaPlayerViewModel", "Using real subtitle data")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                filteredSubtitleResult = filteredResult
                            )
                        } else {
                            android.util.Log.d("MediaPlayerViewModel", "No subtitle entries found")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                filteredSubtitleResult = null,
                                error = "Subtitles unavailable for this content"
                            )
                        }
                    },
                    onFailure = { exception ->
                        android.util.Log.e("MediaPlayerViewModel", "Failed to load subtitles: ${exception.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            filteredSubtitleResult = null,
                            error = "Subtitles unavailable: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Unexpected error loading subtitles", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    filteredSubtitleResult = null,
                    error = "Subtitles unavailable: ${e.message}"
                )
            }
        }
    }
    
    
    private suspend fun loadFromPersistentAnalysis(
        contentId: String,
        filterLevel: ProfanityFilterLevel
    ): FilteredSubtitleResult? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("MediaPlayerViewModel", "Loading from persistent analysis: contentId=$contentId, filterLevel=$filterLevel")
            
            // Try to get filtered subtitle content directly from the database
            val filteredContent = openSubtitlesRepository.getFilteredSubtitleContent(contentId, filterLevel)
            if (filteredContent != null) {
                android.util.Log.d("MediaPlayerViewModel", "Found filtered content in database, length: ${filteredContent.length}")
                
                // Parse the filtered content
                val filteredParsed = subtitleParser.parseSrt(filteredContent)
                
                // For persistent analysis, we need to reconstruct the original subtitle
                // We can do this by removing filtering markers from the filtered version
                val originalEntries = filteredParsed.entries.map { entry ->
                    val hasUnicodeMarkers = entry.originalText.contains("\u200B") && entry.originalText.contains("\u200C")
                    val cleanedText = if (hasUnicodeMarkers) {
                        // Extract the original text from Unicode markers
                        val unicodePattern = Regex("\u200B([^\u200C]+)\u200C")
                        val matches = unicodePattern.findAll(entry.originalText)
                        var cleanText = entry.originalText
                        matches.forEach { match ->
                            // Replace filtered word with a placeholder to reconstruct original
                            val filteredWord = match.groupValues[1]
                            cleanText = cleanText.replace(match.value, "***") // Placeholder
                        }
                        cleanUnicodeMarkers(cleanText)
                    } else {
                        entry.originalText
                    }
                    
                    entry.copy(
                        originalText = cleanedText,
                        filteredText = cleanUnicodeMarkers(entry.originalText),
                        hasProfanity = hasUnicodeMarkers,
                        detectedWords = if (hasUnicodeMarkers) extractWordsFromUnicodeMarkers(entry.originalText) else emptyList()
                    )
                }
                
                val originalParsed = ParsedSubtitle(
                    entries = originalEntries.map { it.copy(filteredText = null) },
                    totalProfanityEntries = originalEntries.count { it.hasProfanity },
                    profanityTimestamps = originalEntries.filter { it.hasProfanity }
                        .map { TimeRange(it.startTime, it.endTime) }
                )
                
                val enhancedFilteredParsed = ParsedSubtitle(
                    entries = originalEntries,
                    totalProfanityEntries = originalEntries.count { it.hasProfanity },
                    profanityTimestamps = originalEntries.filter { it.hasProfanity }
                        .map { TimeRange(it.startTime, it.endTime) }
                )
                
                // Create muting timestamps
                val mutingTimestamps = originalEntries
                    .filter { it.hasProfanity }
                    .map { TimeRange(it.startTime, it.endTime) }
                
                val profanityCount = originalEntries.count { it.hasProfanity }
                val totalEntries = originalEntries.size
                val profanityPercentage = if (totalEntries > 0) (profanityCount.toFloat() / totalEntries) * 100f else 0f
                
                val profanityStats = ProfanityStats(
                    totalEntries = totalEntries,
                    profanityEntries = profanityCount,
                    profanityPercentage = profanityPercentage,
                    detectedWords = emptyMap(),
                    profanityLevel = when {
                        profanityCount == 0 -> ProfanityLevel.NONE
                        profanityPercentage < 5f -> ProfanityLevel.LOW
                        profanityPercentage < 15f -> ProfanityLevel.MEDIUM
                        else -> ProfanityLevel.HIGH
                    }
                )
                
                val result = FilteredSubtitleResult(
                    originalSubtitle = originalParsed,
                    filteredSubtitle = enhancedFilteredParsed,
                    mutingTimestamps = mutingTimestamps,
                    profanityStats = profanityStats
                )
                
                android.util.Log.d("MediaPlayerViewModel", "Successfully created FilteredSubtitleResult from persistent analysis:")
                android.util.Log.d("MediaPlayerViewModel", "  Entries: ${result.filteredSubtitle.entries.size}")
                android.util.Log.d("MediaPlayerViewModel", "  Profanity entries: $profanityCount")
                android.util.Log.d("MediaPlayerViewModel", "  Muting timestamps: ${mutingTimestamps.size}")
                
                return@withContext result
            } else {
                android.util.Log.d("MediaPlayerViewModel", "No filtered content found in persistent analysis for $contentId with filter level $filterLevel")
                return@withContext null
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaPlayerViewModel", "Error loading from persistent analysis: ${e.message}", e)
            return@withContext null
        }
    }
    
    
    
    
    
    
    private fun extractWordsFromUnicodeMarkers(text: String): List<String> {
        // Extract words between zero-width Unicode markers
        // \u200B = Zero Width Space (start), \u200C = Zero Width Non-Joiner (end)
        val unicodePattern = Regex("\u200B([^\u200C]+)\u200C")
        return unicodePattern.findAll(text)
            .map { it.groupValues[1] }
            .toList()
    }
    
    private fun cleanUnicodeMarkers(text: String): String {
        // Remove zero-width Unicode markers from text for clean display
        // This ensures users don't see any artifacts in the subtitles
        return text.replace("\u200B", "").replace("\u200C", "")
    }
    
    fun regenerateSubtitlesWithNewLanguage(
        movie: Movie? = null,
        tvShow: TvShow? = null,
        episode: Episode? = null,
        filterLevel: ProfanityFilterLevel,
        newLanguage: String
    ) {
        viewModelScope.launch {
            android.util.Log.d("MediaPlayerViewModel", "Regenerating subtitles with new language: $newLanguage")
            
            // Clear current subtitle result first
            _uiState.value = _uiState.value.copy(
                filteredSubtitleResult = null,
                isLoading = true,
                error = null
            )
            
            try {
                // Language-specific subtitle loading not yet implemented
                // Current implementation reloads with existing language system
                // Future implementation would:
                // 1. Delete the current filtered subtitle file
                // 2. Download subtitles in the new language
                // 3. Process them with the profanity filter
                // 4. Update the UI state
                
                if (movie != null) {
                    loadFilteredSubtitles(movie = movie, filterLevel = filterLevel)
                } else if (tvShow != null && episode != null) {
                    loadFilteredSubtitles(tvShow = tvShow, episode = episode, filterLevel = filterLevel)
                }
                
                android.util.Log.d("MediaPlayerViewModel", "Subtitle regeneration completed for language: $newLanguage")
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Error regenerating subtitles: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to regenerate subtitles: ${e.message}"
                )
            }
        }
    }
    
    
    
    
    
    fun updatePlayerPosition(positionMs: Long) {
        val currentState = _uiState.value
        val filteredResult = currentState.filteredSubtitleResult
        
        if (filteredResult != null) {
            // Note: VLC now handles subtitle timing natively, so we use the raw position
            // The timing offset is applied by VLC internally via setSpuDelay()
            
            // Check if audio should be muted based on bracket detection in current subtitle
            val currentEntry = filteredResult.filteredSubtitle.entries.find { entry ->
                positionMs >= entry.startTime && positionMs <= entry.endTime
            }
            val shouldMute = currentEntry?.displayText?.let { text ->
                text.contains("[") && text.contains("]")
            } ?: false
            
            // Handle subtitle display logic
            val (subtitleText, showOverlay) = if (currentState.subtitlesEnabled) {
                // Show all subtitles when enabled
                val currentEntry = filteredResult.filteredSubtitle.entries.find { entry ->
                    positionMs >= entry.startTime && positionMs <= entry.endTime
                }
                android.util.Log.d("MediaPlayerViewModel", "Position: ${positionMs}ms, Current subtitle entry: ${currentEntry?.displayText}")
                
                // Check if the subtitle text contains Unicode markers (filtered content)
                val rawDisplayText = currentEntry?.displayText
                val hasUnicodeMarkers = rawDisplayText?.contains("\u200B") == true && rawDisplayText.contains("\u200C")
                
                // Clean the display text for user viewing (remove invisible markers)
                val cleanDisplayText = rawDisplayText?.let { cleanUnicodeMarkers(it) }
                
                Pair(cleanDisplayText, hasUnicodeMarkers)
            } else {
                // Only show subtitles during profanity when subtitles are disabled
                val profanityEntry = filteredResult.filteredSubtitle.entries.find { entry ->
                    entry.hasProfanity && positionMs >= entry.startTime && positionMs <= entry.endTime
                }
                val rawDisplayText = profanityEntry?.displayText
                val hasUnicodeMarkers = rawDisplayText?.contains("\u200B") == true && rawDisplayText.contains("\u200C")
                
                // Clean the display text for user viewing (remove invisible markers)
                val cleanDisplayText = rawDisplayText?.let { cleanUnicodeMarkers(it) }
                
                Pair(cleanDisplayText, hasUnicodeMarkers)
            }
            
            _uiState.value = currentState.copy(
                currentPosition = positionMs,
                isAudioMuted = shouldMute,
                currentSubtitle = subtitleText,
                showProfanityOverlay = showOverlay
            )
        } else {
            _uiState.value = currentState.copy(currentPosition = positionMs)
        }
    }
    
    fun toggleSubtitles() {
        _uiState.value = _uiState.value.copy(
            subtitlesEnabled = !_uiState.value.subtitlesEnabled
        )
    }
    
    fun setSubtitlesEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(subtitlesEnabled = enabled)
    }
    
    fun adjustSubtitleTiming(contentId: String, offsetMs: Long) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val newOffsetMs = currentState.subtitleTimingOffsetMs + offsetMs
                
                android.util.Log.d("MediaPlayerViewModel", "Adjusting subtitle timing by ${offsetMs}ms, new total offset: ${newOffsetMs}ms")
                
                // Update UI state immediately for instant effect
                _uiState.value = currentState.copy(subtitleTimingOffsetMs = newOffsetMs)
                
                // Save to database for persistence across sessions/filter changes
                subtitleTimingDao?.saveTimingOffset(contentId, newOffsetMs)
                
                android.util.Log.d("MediaPlayerViewModel", "Subtitle timing preference saved for content: $contentId")
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Error adjusting subtitle timing: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = "Failed to adjust subtitle timing: ${e.message}")
            }
        }
    }
    
    fun loadSubtitleTimingPreference(contentId: String) {
        viewModelScope.launch {
            try {
                val savedOffset = subtitleTimingDao?.getTimingPreference(contentId) ?: 0L
                android.util.Log.d("MediaPlayerViewModel", "Loaded subtitle timing preference for $contentId: ${savedOffset}ms")
                
                _uiState.value = _uiState.value.copy(subtitleTimingOffsetMs = savedOffset)
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Error loading subtitle timing preference: ${e.message}", e)
                // Default to 0 offset if loading fails
                _uiState.value = _uiState.value.copy(subtitleTimingOffsetMs = 0L)
            }
        }
    }
    
    fun resetSubtitleTiming(contentId: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("MediaPlayerViewModel", "Resetting subtitle timing for content: $contentId")

                // Reset to 0 offset
                _uiState.value = _uiState.value.copy(subtitleTimingOffsetMs = 0L)

                // Delete from database
                subtitleTimingDao?.deleteTimingPreference(contentId)

                android.util.Log.d("MediaPlayerViewModel", "Subtitle timing reset and preference deleted")
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Error resetting subtitle timing: ${e.message}", e)
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // Seek functionality for double-tap gestures
    private var currentPlayer: org.videolan.libvlc.MediaPlayer? = null

    fun setMediaPlayer(player: org.videolan.libvlc.MediaPlayer) {
        currentPlayer = player
    }

    fun seekForward(seconds: Int = 10) {
        currentPlayer?.let { player ->
            try {
                val currentPos = player.time
                val newPos = currentPos + (seconds * 1000)
                player.time = newPos
                android.util.Log.d("MediaPlayerViewModel", "Seek forward: ${seconds}s (${currentPos}ms -> ${newPos}ms)")
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Error seeking forward: ${e.message}")
            }
        }
    }

    fun seekBackward(seconds: Int = 10) {
        currentPlayer?.let { player ->
            try {
                val currentPos = player.time
                val newPos = maxOf(0L, currentPos - (seconds * 1000))
                player.time = newPos
                android.util.Log.d("MediaPlayerViewModel", "Seek backward: ${seconds}s (${currentPos}ms -> ${newPos}ms)")
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Error seeking backward: ${e.message}")
            }
        }
    }

    // Continuous seeking methods for D-pad hold
    fun startContinuousSeeking(direction: SeekDirection) {
        // Guard clause: If already seeking in this direction, ignore repeated KeyDown events
        val currentSeekState = _uiState.value.seekState
        if (currentSeekState.isSeeking && currentSeekState.seekDirection == direction) {
            android.util.Log.d("MediaPlayerViewModel", "Already seeking $direction - ignoring repeated KeyDown")
            return
        }

        // Cancel any existing seek (e.g., switching from left to right)
        seekJob?.cancel()

        val currentPos = currentPlayer?.time ?: _uiState.value.currentPosition
        android.util.Log.d("MediaPlayerViewModel", "Starting continuous seeking: $direction at position $currentPos")

        // Update state to show seeking started
        _uiState.value = _uiState.value.copy(
            seekState = SeekState(
                isSeeking = true,
                seekDirection = direction,
                seekStartTime = System.currentTimeMillis(),
                currentSeekIncrement = SEEK_INCREMENT_TIER_1
            ),
            showSeekTimeline = true,
            seekPreviewPosition = currentPos
        )

        android.util.Log.d("MediaPlayerViewModel", "showSeekTimeline set to: ${_uiState.value.showSeekTimeline}")

        // Start continuous seek coroutine
        seekJob = viewModelScope.launch {
            performContinuousSeek(direction)
        }
    }

    fun stopContinuousSeeking() {
        android.util.Log.d("MediaPlayerViewModel", "Stopping continuous seeking")

        seekJob?.cancel()
        seekJob = null

        // Hide timeline after delay
        viewModelScope.launch {
            delay(TIMELINE_HIDE_DELAY_MS)
            _uiState.value = _uiState.value.copy(
                showSeekTimeline = false,
                seekState = SeekState()
            )
        }
    }

    private suspend fun performContinuousSeek(direction: SeekDirection) {
        // Track our intended position to avoid lag from VLC
        var targetPosition = currentPlayer?.time ?: 0L

        while (coroutineContext.isActive) {
            val currentState = _uiState.value.seekState
            val holdDuration = System.currentTimeMillis() - currentState.seekStartTime

            // Calculate increment based on acceleration curve
            val increment = calculateSeekIncrement(holdDuration)

            // Calculate new target position
            currentPlayer?.let { player ->
                val duration = player.length
                targetPosition = when (direction) {
                    SeekDirection.FORWARD -> minOf(duration, targetPosition + increment)
                    SeekDirection.BACKWARD -> maxOf(0L, targetPosition - increment)
                }

                android.util.Log.d("MediaPlayerViewModel", "Continuous seek loop: direction=$direction, increment=$increment, target=$targetPosition, holdDuration=${holdDuration}ms")

                // Update increment and preview position in state for UI display
                _uiState.value = _uiState.value.copy(
                    seekState = currentState.copy(currentSeekIncrement = increment),
                    seekPreviewPosition = targetPosition
                )

                // Perform the seek
                performSeek(targetPosition)
            }

            // Wait before next seek
            delay(SEEK_DEBOUNCE_WINDOW_MS)
        }

        android.util.Log.d("MediaPlayerViewModel", "Continuous seek loop ended")
    }

    private fun calculateSeekIncrement(holdDurationMs: Long): Long {
        return when {
            holdDurationMs < SEEK_TIER_1_THRESHOLD_MS -> SEEK_INCREMENT_TIER_1   // 0-1s: 5s per tick
            holdDurationMs < SEEK_TIER_2_THRESHOLD_MS -> SEEK_INCREMENT_TIER_2   // 1-3s: 10s per tick
            holdDurationMs < SEEK_TIER_3_THRESHOLD_MS -> SEEK_INCREMENT_TIER_3   // 3-5s: 30s per tick
            holdDurationMs < SEEK_TIER_4_THRESHOLD_MS -> SEEK_INCREMENT_TIER_4   // 5-10s: 1min per tick
            holdDurationMs < SEEK_TIER_5_THRESHOLD_MS -> SEEK_INCREMENT_TIER_5   // 10-15s: 2min per tick
            else -> SEEK_INCREMENT_TIER_6                                         // 15s+: 5min per tick
        }
    }

    private suspend fun performSeek(targetPosition: Long) {
        withContext(Dispatchers.Main) {
            currentPlayer?.let { player ->
                try {
                    player.time = targetPosition
                    android.util.Log.d("MediaPlayerViewModel", "Seeking to: ${targetPosition}ms")
                } catch (e: Exception) {
                    android.util.Log.e("MediaPlayerViewModel", "Seek error: ${e.message}")
                    handleSeekError(e)
                }
            }
        }
    }

    private suspend fun performDebouncedSeek(direction: SeekDirection, incrementMs: Long) {
        withContext(Dispatchers.Main) {
            currentPlayer?.let { player ->
                try {
                    val currentPos = player.time
                    val duration = player.length
                    val newPos = when (direction) {
                        SeekDirection.FORWARD -> minOf(duration, currentPos + incrementMs)
                        SeekDirection.BACKWARD -> maxOf(0L, currentPos - incrementMs)
                    }
                    player.time = newPos
                    android.util.Log.d("MediaPlayerViewModel",
                        "Seek ${direction.name}: +${incrementMs}ms (${currentPos}ms -> ${newPos}ms)")
                } catch (e: Exception) {
                    android.util.Log.e("MediaPlayerViewModel", "Seek error: ${e.message}")
                    handleSeekError(e)
                }
            }
        }
    }

    private fun handleSeekError(error: Exception) {
        val currentSeekState = _uiState.value.seekState
        _uiState.value = _uiState.value.copy(
            seekState = currentSeekState.copy(
                seekError = error.message,
                seekRetryCount = currentSeekState.seekRetryCount + 1
            )
        )

        // Retry if under max retries
        if (currentSeekState.seekRetryCount < SEEK_MAX_RETRIES) {
            viewModelScope.launch {
                delay(SEEK_RETRY_DELAY_MS)
                retryFailedSeek()
            }
        }
    }

    private suspend fun retryFailedSeek() {
        val seekState = _uiState.value.seekState

        if (seekState.seekRetryCount >= SEEK_MAX_RETRIES) {
            android.util.Log.e("MediaPlayerViewModel", "Max seek retries exceeded")
            _uiState.value = _uiState.value.copy(
                seekState = seekState.copy(
                    isSeeking = false,
                    seekError = "Seek failed after ${SEEK_MAX_RETRIES} attempts"
                )
            )
            return
        }

        android.util.Log.d("MediaPlayerViewModel", "Retrying seek (attempt ${seekState.seekRetryCount + 1})")

        seekState.targetPosition?.let { target ->
            withContext(Dispatchers.Main) {
                currentPlayer?.let { player ->
                    try {
                        player.time = target
                        android.util.Log.d("MediaPlayerViewModel", "Seek retry successful")
                        _uiState.value = _uiState.value.copy(
                            seekState = seekState.copy(
                                seekError = null,
                                seekRetryCount = 0
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("MediaPlayerViewModel", "Seek retry failed: ${e.message}")
                        handleSeekError(e)
                    }
                }
            }
        }
    }

    fun getFilteredSrtContent(): String? {
        return _uiState.value.filteredSubtitleResult?.filteredSubtitle?.toSrtString()
    }
    
    fun saveFilteredSubtitleFile(outputPath: String): Result<String> {
        val filteredResult = _uiState.value.filteredSubtitleResult
            ?: return Result.failure(Exception("No filtered subtitles available"))
        
        return try {
            val srtContent = filteredResult.filteredSubtitle.toSrtString()
            // Here you would normally save to file, for now just return success
            Result.success("Subtitles saved to $outputPath")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Acquires a stronger wake lock to prevent the TV from turning off during video playback
     * Uses SCREEN_BRIGHT_WAKE_LOCK for maximum power management override on TV devices
     */
    fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true && context != null) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                
                // Use SCREEN_BRIGHT_WAKE_LOCK for stronger TV power management override
                wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "PureStream::MediaPlayerWakeLock"
                )
                wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hour max for longer movies
                android.util.Log.d("MediaPlayerViewModel", "Enhanced wake lock acquired - TV will stay on during playback")
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaPlayerViewModel", "Failed to acquire wake lock: ${e.message}")
        }
    }
    
    /**
     * Releases the wake lock to allow normal screen timeout behavior
     */
    fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    android.util.Log.d("MediaPlayerViewModel", "Wake lock released - screen timeout restored")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            android.util.Log.e("MediaPlayerViewModel", "Failed to release wake lock: ${e.message}")
        }
    }
    
    /**
     * Called when playback state changes to manage wake lock appropriately
     */
    fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (isPlaying) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }
    
    /**
     * Check if analysis exists for the current content and update state
     */
    fun checkAnalysisStatus(
        contentId: String,
        filterLevel: ProfanityFilterLevel
    ) {
        viewModelScope.launch {
            try {
                if (contentId.isNotEmpty() && contentId != "unknown") {
                    val hasAnalysis = subtitleAnalysisRepository?.hasAnalysisForFilterLevel(contentId, filterLevel) ?: false
                    _uiState.value = _uiState.value.copy(hasAnalysis = hasAnalysis)
                    android.util.Log.d("MediaPlayerViewModel", "Analysis status check: contentId=$contentId, filterLevel=$filterLevel, hasAnalysis=$hasAnalysis")
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Error checking analysis status: ${e.message}", e)
            }
        }
    }
    
    /**
     * Monitor analysis status changes for the current content
     */
    fun monitorAnalysisStatus(
        contentId: String,
        filterLevel: ProfanityFilterLevel
    ) {
        viewModelScope.launch {
            try {
                if (contentId.isNotEmpty() && contentId != "unknown") {
                    // Check periodically for analysis completion
                    while (true) {
                        val hasAnalysis = subtitleAnalysisRepository?.hasAnalysisForFilterLevel(contentId, filterLevel) ?: false
                        val currentHasAnalysis = _uiState.value.hasAnalysis
                        
                        if (hasAnalysis != currentHasAnalysis) {
                            _uiState.value = _uiState.value.copy(hasAnalysis = hasAnalysis)
                            android.util.Log.d("MediaPlayerViewModel", "Analysis status changed: contentId=$contentId, hasAnalysis=$hasAnalysis")
                        }
                        
                        // Check every 2 seconds
                        kotlinx.coroutines.delay(2000)
                        
                        // Stop monitoring if analysis is found
                        if (hasAnalysis) break
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Error monitoring analysis status: ${e.message}", e)
            }
        }
    }

    /**
     * Load filtered subtitles directly using contentId (for navigation-based scenarios)
     */
    fun loadFilteredSubtitlesByContentId(
        contentId: String,
        filterLevel: ProfanityFilterLevel
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                android.util.Log.d("MediaPlayerViewModel", "Loading filtered subtitles by contentId: $contentId, filterLevel: $filterLevel")
                
                if (contentId.isNotEmpty() && contentId != "unknown") {
                    val persistentResult = loadFromPersistentAnalysis(contentId, filterLevel)
                    
                    if (persistentResult != null) {
                        android.util.Log.d("MediaPlayerViewModel", "Successfully loaded from persistent analysis for contentId: $contentId")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            filteredSubtitleResult = persistentResult
                        )
                    } else {
                        android.util.Log.d("MediaPlayerViewModel", "No persistent analysis found for contentId: $contentId")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            filteredSubtitleResult = null,
                            error = "No analyzed subtitles available for this content"
                        )
                    }
                } else {
                    android.util.Log.w("MediaPlayerViewModel", "Invalid contentId provided: $contentId")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        filteredSubtitleResult = null,
                        error = "Content identifier unavailable"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Error loading filtered subtitles by contentId: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    filteredSubtitleResult = null,
                    error = "Failed to load subtitles: ${e.message}"
                )
            }
        }
    }

    /**
     * Store media objects before navigation to MediaPlayerScreen
     * This allows the media objects to be retrieved and used for tracking
     */
    fun setCurrentMedia(
        movie: Movie? = null,
        episode: Episode? = null,
        tvShow: TvShow? = null
    ) {
        storedMovie = movie
        storedEpisode = episode
        storedTvShow = tvShow
        android.util.Log.d("MediaPlayerViewModel", "📦 Stored media for navigation: movie=${movie?.title}, episode=${episode?.title}, tvShow=${tvShow?.title}")
    }

    /**
     * Set the start position for playback (used for Resume functionality)
     */
    fun setStartPosition(position: Long) {
        _uiState.value = _uiState.value.copy(startPosition = position)
        android.util.Log.d("MediaPlayerViewModel", "⏩ Set start position: ${position}ms")
    }

    /**
     * Retrieve stored movie object (used in MediaPlayerScreen after navigation)
     */
    fun getStoredMovie(): Movie? = storedMovie

    /**
     * Retrieve stored episode object (used in MediaPlayerScreen after navigation)
     */
    fun getStoredEpisode(): Episode? = storedEpisode

    /**
     * Retrieve stored TV show object (used in MediaPlayerScreen after navigation)
     */
    fun getStoredTvShow(): TvShow? = storedTvShow

    /**
     * Clear stored media objects after they've been used
     */
    fun clearStoredMedia() {
        storedMovie = null
        storedEpisode = null
        storedTvShow = null
        android.util.Log.d("MediaPlayerViewModel", "🗑️ Cleared stored media")
    }

    /**
     * Set up Plex connection with authentication token
     */
    fun setPlexConnectionWithAuth(authToken: String) {
        android.util.Log.d("MediaPlayerViewModel", "Setting up Plex connection with auth token")

        com.purestream.utils.PlexConnectionHelper.setPlexConnectionWithAuthSimple(
            viewModel = this,
            plexRepository = plexRepository,
            authToken = authToken,
            onError = { errorMessage ->
                android.util.Log.e("MediaPlayerViewModel", "Failed to set Plex connection: $errorMessage")
            }
        )
    }

    /**
     * Start tracking playback progress for a movie or episode
     */
    fun startProgressTracking(
        movie: Movie? = null,
        tvShow: TvShow? = null,
        episode: Episode? = null,
        profileId: String,
        duration: Long
    ) {
        val ratingKey = movie?.ratingKey ?: episode?.ratingKey ?: return
        val key = movie?.key ?: episode?.key ?: return

        currentMediaRatingKey = ratingKey
        currentMediaKey = key
        currentMediaDuration = duration
        currentProfileId = profileId

        android.util.Log.d("MediaPlayerViewModel", "========================================")
        android.util.Log.d("MediaPlayerViewModel", "▶ Starting progress tracking")
        android.util.Log.d("MediaPlayerViewModel", "  Profile ID: $profileId")
        android.util.Log.d("MediaPlayerViewModel", "  Rating Key: $ratingKey")
        android.util.Log.d("MediaPlayerViewModel", "  Content: ${movie?.title ?: episode?.title}")
        android.util.Log.d("MediaPlayerViewModel", "  Duration: ${duration}ms (${duration / 1000 / 60} minutes)")
        android.util.Log.d("MediaPlayerViewModel", "  Repository: ${if (watchProgressRepository != null) "✓ Ready" else "✗ NULL"}")
        android.util.Log.d("MediaPlayerViewModel", "========================================")

        // Report to Plex that playback has started
        reportPlaybackState("playing")

        // Start periodic progress updates
        startPeriodicProgressUpdates(movie, tvShow, episode)
    }

    /**
     * Start periodic progress updates (every 15 seconds)
     */
    private fun startPeriodicProgressUpdates(
        movie: Movie? = null,
        tvShow: TvShow? = null,
        episode: Episode? = null
    ) {
        // Cancel any existing job
        progressUpdateJob?.cancel()

        android.util.Log.d("MediaPlayerViewModel", "⏰ Starting periodic progress updates (every 15 seconds)")

        progressUpdateJob = viewModelScope.launch {
            while (true) {
                delay(PROGRESS_UPDATE_INTERVAL_MS)

                val currentPosition = _uiState.value.currentPosition
                android.util.Log.d("MediaPlayerViewModel", "📊 Progress update cycle - Position: ${currentPosition}ms")

                // Save progress locally
                saveProgressLocally(movie, tvShow, episode, currentPosition)

                // Report to Plex timeline
                reportTimelineUpdate(currentPosition, "playing")
            }
        }
    }

    /**
     * Save progress locally to database
     */
    private suspend fun saveProgressLocally(
        movie: Movie?,
        tvShow: TvShow?,
        episode: Episode?,
        position: Long
    ) {
        val ratingKey = currentMediaRatingKey ?: return
        val profileId = currentProfileId
        val duration = currentMediaDuration

        // Check if profileId is valid
        if (profileId.isEmpty()) {
            android.util.Log.w("MediaPlayerViewModel", "Cannot save progress - profileId is empty")
            return
        }

        // Check if repository exists
        if (watchProgressRepository == null) {
            android.util.Log.w("MediaPlayerViewModel", "Cannot save progress - watchProgressRepository is null")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                watchProgressRepository?.saveProgress(
                    profileId = profileId,
                    ratingKey = ratingKey,
                    contentType = if (movie != null) "movie" else "episode",
                    contentTitle = movie?.title ?: episode?.title ?: "Unknown",
                    position = position,
                    duration = duration,
                    showTitle = tvShow?.title,
                    seasonNumber = episode?.seasonNumber,
                    episodeNumber = episode?.episodeNumber
                )

                android.util.Log.d("MediaPlayerViewModel", "✓ Saved progress for profile $profileId: ratingKey=$ratingKey, position=$position/$duration (${(position.toFloat() / duration * 100).toInt()}%)")

                // Check if content should be marked as watched (>90%)
                val progressPercentage = if (duration > 0) {
                    position.toFloat() / duration.toFloat()
                } else {
                    0f
                }

                if (progressPercentage >= 0.90f) {
                    scrobbleContent()
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Error saving progress: ${e.message}", e)
            }
            Unit
        }
    }

    /**
     * Report timeline update to Plex server
     */
    private fun reportTimelineUpdate(position: Long, state: String) {
        val ratingKey = currentMediaRatingKey ?: run {
            android.util.Log.w("MediaPlayerViewModel", "Cannot report timeline - ratingKey is null")
            return
        }
        val key = currentMediaKey ?: run {
            android.util.Log.w("MediaPlayerViewModel", "Cannot report timeline - key is null")
            return
        }
        val duration = currentMediaDuration

        android.util.Log.d("MediaPlayerViewModel", "📡 Reporting to Plex: state=$state, position=${position}ms, duration=${duration}ms, ratingKey=$ratingKey")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = plexRepository.updateTimeline(
                    ratingKey = ratingKey,
                    key = key,
                    state = state,
                    position = position,
                    duration = duration
                )

                result.fold(
                    onSuccess = {
                        android.util.Log.d("MediaPlayerViewModel", "✓ Plex timeline updated successfully: $state at $position/$duration")
                    },
                    onFailure = { error ->
                        android.util.Log.e("MediaPlayerViewModel", "✗ Plex timeline update failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "✗ Error reporting timeline: ${e.message}", e)
            }
        }
    }

    /**
     * Report playback state change (playing, paused, stopped)
     */
    fun reportPlaybackState(state: String) {
        val position = _uiState.value.currentPosition
        reportTimelineUpdate(position, state)
    }

    /**
     * Mark content as watched on Plex server
     */
    private fun scrobbleContent() {
        val key = currentMediaKey ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                plexRepository.scrobble(key)
                android.util.Log.d("MediaPlayerViewModel", "Content scrobbled: $key")
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Error scrobbling: ${e.message}", e)
            }
        }
    }

    /**
     * Stop progress tracking
     */
    fun stopProgressTracking(movie: Movie? = null, tvShow: TvShow? = null, episode: Episode? = null) {
        android.util.Log.d("MediaPlayerViewModel", "Stopping progress tracking")

        // Cancel periodic updates
        progressUpdateJob?.cancel()
        progressUpdateJob = null

        // Save final progress
        val currentPosition = _uiState.value.currentPosition
        viewModelScope.launch {
            saveProgressLocally(movie, tvShow, episode, currentPosition)
        }

        // Report stopped state to Plex
        reportPlaybackState("stopped")

        // Clear tracking state
        currentMediaRatingKey = null
        currentMediaKey = null
        currentMediaDuration = 0L
    }

    /**
     * Pause progress tracking (but don't clear state)
     */
    fun pauseProgressTracking(movie: Movie? = null, tvShow: TvShow? = null, episode: Episode? = null) {
        android.util.Log.d("MediaPlayerViewModel", "Pausing progress tracking")

        // Cancel periodic updates
        progressUpdateJob?.cancel()
        progressUpdateJob = null

        // Save current progress
        val currentPosition = _uiState.value.currentPosition
        viewModelScope.launch {
            saveProgressLocally(movie, tvShow, episode, currentPosition)
        }

        // Report paused state to Plex
        reportPlaybackState("paused")
    }

    /**
     * Resume progress tracking after pause
     */
    fun resumeProgressTracking(movie: Movie? = null, tvShow: TvShow? = null, episode: Episode? = null) {
        // Only resume if tracking has been started
        if (currentMediaRatingKey == null) {
            android.util.Log.d("MediaPlayerViewModel", "Skipping resume - tracking not started yet")
            return
        }

        android.util.Log.d("MediaPlayerViewModel", "Resuming progress tracking")

        // Report playing state to Plex
        reportPlaybackState("playing")

        // Restart periodic updates
        startPeriodicProgressUpdates(movie, tvShow, episode)
    }

    /**
     * Get saved progress for content (for resume functionality)
     */
    suspend fun getSavedProgress(profileId: String, ratingKey: String): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val progress = watchProgressRepository?.getProgress(profileId, ratingKey)
                progress?.position
            } catch (e: Exception) {
                android.util.Log.e("MediaPlayerViewModel", "Error getting saved progress: ${e.message}", e)
                null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel progress tracking
        progressUpdateJob?.cancel()
        // Ensure wake lock is released when ViewModel is destroyed
        releaseWakeLock()
        android.util.Log.d("MediaPlayerViewModel", "ViewModel cleared - wake lock cleanup completed")
    }
}