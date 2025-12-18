package com.purestream.profanity

import com.purestream.data.model.FilteredSubtitleResult
import com.purestream.data.model.Movie
import com.purestream.data.model.TvShow
import com.purestream.data.model.Episode
import com.purestream.data.model.OpenSubtitlesDownloadRequest
import com.purestream.data.model.ParsedSubtitle
import com.purestream.data.model.ProfanityFilterLevel
import com.purestream.data.model.ProfanityStats
import com.purestream.data.model.SubtitleResult
import com.purestream.data.model.TimeRange
import com.purestream.data.model.SubtitleEntry as ModelSubtitleEntry
import com.purestream.data.repository.OpenSubtitlesRepository
import com.purestream.data.manager.SubtitleCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FilteredSubtitleManager(
    private val profanityFilter: ProfanityFilter,
    private val subtitleParser: SubtitleParser,
    private val openSubtitlesRepository: OpenSubtitlesRepository,
    private val subtitleCacheManager: SubtitleCacheManager = SubtitleCacheManager()
) {
    
    suspend fun processMovieSubtitles(
        movie: Movie,
        filterLevel: ProfanityFilterLevel
    ): Result<FilteredSubtitleResult> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cachedAnalysis = subtitleCacheManager.getCachedMovieAnalysis(movie)
            if (cachedAnalysis != null) {
                return@withContext convertAnalysisToFilteredResult(cachedAnalysis)
            }
            
            // Search for subtitles
            val subtitlesResult = openSubtitlesRepository.searchSubtitlesForMovie(
                movieTitle = movie.title,
                imdbId = null,
                year = movie.year
            )
            
            subtitlesResult.fold(
                onSuccess = { subtitlesList ->
                    if (subtitlesList.isNotEmpty()) {
                        val bestSubtitle = subtitlesList.first()
                        val result = processSubtitleFile(bestSubtitle, movie.title, null, filterLevel)
                        
                        // Cache the analysis result if successful
                        result.fold(
                            onSuccess = { filteredResult ->
                                // Convert back to analysis for caching
                                val analysis = convertFilteredResultToAnalysis(filteredResult, movie.title, null)
                                subtitleCacheManager.cacheMovieAnalysis(movie, analysis)
                            },
                            onFailure = { /* Don't cache failures */ }
                        )
                        
                        result
                    } else {
                        Result.failure(Exception("No subtitles found for movie: ${movie.title}"))
                    }
                },
                onFailure = { exception ->
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun processEpisodeSubtitles(
        tvShow: TvShow,
        episode: Episode,
        filterLevel: ProfanityFilterLevel
    ): Result<FilteredSubtitleResult> = withContext(Dispatchers.IO) {
        try {
            // Search for episode subtitles
            val subtitlesResult = openSubtitlesRepository.searchSubtitlesForEpisode(
                showTitle = tvShow.title,
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber,
                imdbId = null
            )
            
            subtitlesResult.fold(
                onSuccess = { subtitlesList ->
                    if (subtitlesList.isNotEmpty()) {
                        val bestSubtitle = subtitlesList.first()
                        val episodeInfo = "S${episode.seasonNumber}E${episode.episodeNumber} - ${episode.title}"
                        processSubtitleFile(bestSubtitle, tvShow.title, episodeInfo, filterLevel)
                    } else {
                        Result.failure(Exception("No subtitles found for ${tvShow.title} S${episode.seasonNumber}E${episode.episodeNumber}"))
                    }
                },
                onFailure = { exception ->
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun processSubtitleFile(
        subtitleResult: SubtitleResult,
        title: String,
        episodeInfo: String?,
        filterLevel: ProfanityFilterLevel
    ): Result<FilteredSubtitleResult> = withContext(Dispatchers.IO) {
        try {
            // Use the repository's downloadAndAnalyzeSubtitle method
            val analysisResult = openSubtitlesRepository.downloadAndAnalyzeSubtitle(
                subtitleResult = subtitleResult,
                movieTitle = title,
                episodeInfo = episodeInfo,
                filterLevel = filterLevel
            )
            
            analysisResult.fold(
                onSuccess = { analysis ->
                    // Load the actual filtered subtitle file content
                    val filteredSubtitleResult = loadFilteredSubtitleFromCache(title, episodeInfo, filterLevel, analysis)
                    filteredSubtitleResult
                },
                onFailure = { exception ->
                    Result.failure(exception)
                }
            )
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun loadFilteredSubtitleFromCache(
        title: String,
        episodeInfo: String?,
        filterLevel: ProfanityFilterLevel,
        analysis: com.purestream.data.model.SubtitleAnalysisResult
    ): Result<FilteredSubtitleResult> = withContext(Dispatchers.IO) {
        try {
            // Generate the expected cache file names
            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_")
            val originalFileName = "subtitle_${sanitizedTitle}.srt"
            val filteredFileName = "subtitle_${sanitizedTitle}_filtered.srt"
            
            // Try to load the filtered subtitle file from cache
            val cacheDir = File("/data/user/0/com.purestream/cache/")
            val filteredFile = File(cacheDir, filteredFileName)
            val originalFile = File(cacheDir, originalFileName)
            
            println("Looking for filtered subtitle file: ${filteredFile.absolutePath}")
            println("Looking for original subtitle file: ${originalFile.absolutePath}")
            
            val (originalSubtitle, filteredSubtitle) = if (filteredFile.exists() && originalFile.exists()) {
                println("Found cached subtitle files, loading content...")
                val originalContent = originalFile.readText()
                val filteredContent = filteredFile.readText()
                
                println("Original file size: ${originalContent.length} characters")
                println("Filtered file size: ${filteredContent.length} characters")
                
                val originalParsed = subtitleParser.parseSrt(originalContent)
                val filteredParsed = subtitleParser.parseSrt(filteredContent)
                
                println("Original parsed entries: ${originalParsed.entries.size}")
                println("Filtered parsed entries: ${filteredParsed.entries.size}")
                
                Pair(originalParsed, filteredParsed)
            } else {
                println("Cached subtitle files not found, creating empty subtitles")
                // Files not found, create empty subtitles with the analysis data
                val emptyOriginal = ParsedSubtitle(
                    entries = emptyList(),
                    totalProfanityEntries = analysis.profanityWordsCount,
                    profanityTimestamps = emptyList()
                )
                val emptyFiltered = ParsedSubtitle(
                    entries = emptyList(),
                    totalProfanityEntries = analysis.profanityWordsCount,
                    profanityTimestamps = emptyList()
                )
                Pair(emptyOriginal, emptyFiltered)
            }
            
            // Create muting timestamps based on profanity entries
            val mutingTimestamps = filteredSubtitle.entries
                .filter { it.hasProfanity }
                .map { TimeRange(it.startTime, it.endTime) }
            
            println("Created ${mutingTimestamps.size} muting timestamps")
            
            val profanityStats = ProfanityStats(
                totalEntries = analysis.totalWordsCount,
                profanityEntries = analysis.profanityWordsCount,
                profanityPercentage = analysis.profanityPercentage,
                detectedWords = analysis.detectedWords.associateWith { 1 },
                profanityLevel = analysis.profanityLevel
            )
            
            val result = FilteredSubtitleResult(
                originalSubtitle = originalSubtitle,
                filteredSubtitle = filteredSubtitle,
                mutingTimestamps = mutingTimestamps,
                profanityStats = profanityStats
            )
            
            println("Created FilteredSubtitleResult with ${result.filteredSubtitle.entries.size} subtitle entries")
            
            Result.success(result)
        } catch (e: Exception) {
            println("Error loading filtered subtitle from cache: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    fun saveFilteredSubtitleFile(
        filteredResult: FilteredSubtitleResult,
        outputPath: String
    ): Result<String> {
        return try {
            val filteredSrtContent = filteredResult.filteredSubtitle.toSrtString()
            val file = File(outputPath)
            file.writeText(filteredSrtContent)
            Result.success(outputPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getCurrentMutingStatus(
        mutingTimestamps: List<TimeRange>,
        currentPositionMs: Long
    ): Boolean {
        return mutingTimestamps.any { it.contains(currentPositionMs) }
    }
    
    fun getCurrentProfanitySubtitle(
        filteredSubtitle: ParsedSubtitle,
        currentPositionMs: Long
    ): ModelSubtitleEntry? {
        return filteredSubtitle.entries.find { entry ->
            entry.hasProfanity && currentPositionMs >= entry.startTime && currentPositionMs <= entry.endTime
        }
    }
    
    fun getCurrentSubtitleEntry(
        subtitle: ParsedSubtitle,
        currentPositionMs: Long
    ): ModelSubtitleEntry? {
        return subtitle.entries.find { entry ->
            currentPositionMs >= entry.startTime && currentPositionMs <= entry.endTime
        }
    }
    
    private suspend fun convertAnalysisToFilteredResult(analysis: com.purestream.data.model.SubtitleAnalysisResult): Result<FilteredSubtitleResult> {
        return try {
            // Try to load the cached filtered subtitle files
            val title = analysis.movieTitle ?: "Unknown"
            val episodeInfo = analysis.episodeInfo
            val filterLevel = ProfanityFilterLevel.MILD // Default, should be stored in analysis
            
            // Load from cache using the same method
            loadFilteredSubtitleFromCache(title, episodeInfo, filterLevel, analysis)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun convertFilteredResultToAnalysis(
        filteredResult: FilteredSubtitleResult,
        title: String,
        episodeInfo: String?
    ): com.purestream.data.model.SubtitleAnalysisResult {
        return com.purestream.data.model.SubtitleAnalysisResult(
            movieTitle = title,
            episodeInfo = episodeInfo,
            subtitleFileName = "cached_subtitle.srt",
            profanityLevel = filteredResult.profanityStats.profanityLevel,
            detectedWords = filteredResult.profanityStats.detectedWords.keys.toList(),
            totalWordsCount = filteredResult.profanityStats.totalEntries,
            profanityWordsCount = filteredResult.profanityStats.profanityEntries,
            profanityPercentage = filteredResult.profanityStats.profanityPercentage
        )
    }
}