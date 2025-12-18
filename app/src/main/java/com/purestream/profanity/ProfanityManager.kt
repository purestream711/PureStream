package com.purestream.profanity

import com.purestream.data.model.Profile
import com.purestream.data.model.ProfanityFilterLevel
import com.purestream.data.model.ProfanityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProfanityManager {
    
    private val profanityFilter = ProfanityFilter()
    private val subtitleAnalyzer = SubtitleAnalyzer(profanityFilter)
    
    private val _currentFilterLevel = MutableStateFlow(ProfanityFilterLevel.MILD)
    val currentFilterLevel: StateFlow<ProfanityFilterLevel> = _currentFilterLevel.asStateFlow()
    
    private val _audioMuteDuration = MutableStateFlow(2000) // 2 seconds default
    val audioMuteDuration: StateFlow<Int> = _audioMuteDuration.asStateFlow()
    
    private val _profanityEvents = MutableStateFlow<List<ProfanityEvent>>(emptyList())
    val profanityEvents: StateFlow<List<ProfanityEvent>> = _profanityEvents.asStateFlow()
    
    fun updateProfile(profile: Profile) {
        _currentFilterLevel.value = profile.profanityFilterLevel
        _audioMuteDuration.value = profile.audioMuteDuration
        
        // Update custom words
        profanityFilter.addCustomFilteredWords(profile.customFilteredWords)
        profanityFilter.addWhitelistedWords(profile.whitelistedWords)
    }
    
    fun setFilterLevel(level: ProfanityFilterLevel) {
        _currentFilterLevel.value = level
    }
    
    fun setAudioMuteDuration(durationMs: Int) {
        _audioMuteDuration.value = durationMs
    }
    
    suspend fun analyzeMediaSubtitles(
        subtitleContent: String,
        filterLevel: ProfanityFilterLevel = _currentFilterLevel.value
    ): SubtitleAnalysisResult {
        val result = subtitleAnalyzer.analyzeSubtitles(subtitleContent, filterLevel)
        _profanityEvents.value = result.profanityEvents
        return result
    }
    
    fun filterRealTimeText(text: String): FilterResult {
        return profanityFilter.filterText(text, _currentFilterLevel.value)
    }
    
    fun shouldMuteAudio(currentTimeMs: Long): Boolean {
        return _profanityEvents.value.any { event ->
            currentTimeMs >= event.startTime && currentTimeMs <= (event.endTime + _audioMuteDuration.value)
        }
    }
    
    fun getProfanityEventAt(timeMs: Long): ProfanityEvent? {
        return _profanityEvents.value.find { event ->
            timeMs >= event.startTime && timeMs <= event.endTime
        }
    }
    
    fun addCustomFilteredWord(word: String) {
        profanityFilter.addCustomFilteredWords(listOf(word))
    }
    
    fun removeCustomFilteredWord(word: String) {
        profanityFilter.removeCustomFilteredWords(listOf(word))
    }
    
    fun addWhitelistedWord(word: String) {
        profanityFilter.addWhitelistedWords(listOf(word))
    }
    
    fun removeWhitelistedWord(word: String) {
        profanityFilter.removeWhitelistedWords(listOf(word))
    }
    
    fun analyzeProfanityLevel(text: String): ProfanityLevel {
        val filterResult = profanityFilter.analyzeProfanityLevel(text)
        return when (filterResult) {
            ProfanityLevel.NONE -> ProfanityLevel.NONE
            ProfanityLevel.LOW -> ProfanityLevel.LOW
            ProfanityLevel.MEDIUM -> ProfanityLevel.MEDIUM
            ProfanityLevel.HIGH -> ProfanityLevel.HIGH
            ProfanityLevel.UNKNOWN -> ProfanityLevel.UNKNOWN
        }
    }
    
    fun generateFilteredSubtitleFile(analysisResult: SubtitleAnalysisResult): String {
        return subtitleAnalyzer.generateFilteredSubtitleFile(analysisResult)
    }
    
    fun getFilterLevelDescription(level: ProfanityFilterLevel): String {
        return when (level) {
            ProfanityFilterLevel.NONE -> "No filtering - all content shown as-is"
            ProfanityFilterLevel.MILD -> "Filters the strongest profanity and offensive language"
            ProfanityFilterLevel.MODERATE -> "Filters most profanity and inappropriate language"
            ProfanityFilterLevel.STRICT -> "Filters all known profanity including religious expressions"
        }
    }
    
    fun getFilterLevelColor(level: ProfanityFilterLevel): androidx.compose.ui.graphics.Color {
        return when (level) {
            ProfanityFilterLevel.NONE -> androidx.compose.ui.graphics.Color(0xFF10B981) // Green
            ProfanityFilterLevel.MILD -> androidx.compose.ui.graphics.Color(0xFFF59E0B) // Yellow
            ProfanityFilterLevel.MODERATE -> androidx.compose.ui.graphics.Color(0xFFEF4444) // Red
            ProfanityFilterLevel.STRICT -> androidx.compose.ui.graphics.Color(0xFF7C2D12) // Dark Red
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: ProfanityManager? = null
        
        fun getInstance(): ProfanityManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProfanityManager().also { INSTANCE = it }
            }
        }
    }
}