package com.purestream.data.repository

import com.purestream.data.database.AppDatabase
import com.purestream.data.database.dao.SubtitleAnalysisDao
import com.purestream.data.database.entities.SubtitleAnalysisEntity
import com.purestream.data.model.ProfanityFilterLevel
import com.purestream.data.model.ProfanityLevel
import com.purestream.profanity.SubtitleAnalysisResult
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import java.io.File
import android.content.Context

class SubtitleAnalysisRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    private val dao: SubtitleAnalysisDao = database.subtitleAnalysisDao()
    private val gson = Gson()
    
    suspend fun getAnalysisForContent(contentId: String, filterLevel: ProfanityFilterLevel): SubtitleAnalysisEntity? {
        val result = dao.getAnalysisForContent(contentId, filterLevel)
        android.util.Log.d("SubtitleAnalysisRepository", "getAnalysisForContent($contentId, $filterLevel) = ${result?.id}")
        return result
    }
    
    suspend fun getAllAnalysesForContent(contentId: String): List<SubtitleAnalysisEntity> {
        return dao.getAllAnalysesForContent(contentId)
    }
    
    fun getAllAnalysesForContentFlow(contentId: String): Flow<List<SubtitleAnalysisEntity>> {
        return dao.getAllAnalysesForContentFlow(contentId)
    }
    
    suspend fun getExistingFilterLevelsForContent(contentId: String): List<ProfanityFilterLevel> {
        val allLevels = dao.getExistingFilterLevelsForContent(contentId)

        // Filter to only include levels where the file actually exists on this device
        return allLevels.filter { filterLevel ->
            val analysis = dao.getAnalysisForContent(contentId, filterLevel)
            val filePath = analysis?.filteredSubtitlePath
            val fileExists = filePath?.let { File(it).exists() } ?: false

            if (!fileExists && filePath != null) {
                android.util.Log.w("SubtitleAnalysisRepository",
                    "Database entry for $filterLevel exists but file missing: $filePath")
            }

            fileExists
        }
    }
    
    suspend fun hasAnalysisForFilterLevel(contentId: String, filterLevel: ProfanityFilterLevel): Boolean {
        val analysis = dao.getAnalysisForContent(contentId, filterLevel)

        // Check if analysis exists AND the file is actually present on this device
        val result = if (analysis != null) {
            val filePath = analysis.filteredSubtitlePath
            val fileExists = filePath?.let { File(it).exists() } ?: false

            if (!fileExists && filePath != null) {
                android.util.Log.w("SubtitleAnalysisRepository",
                    "Database entry exists but file missing (likely synced from another device): $filePath")
            }

            fileExists
        } else {
            false
        }

        android.util.Log.d("SubtitleAnalysisRepository", "hasAnalysisForFilterLevel($contentId, $filterLevel) = $result")
        return result
    }
    
    suspend fun saveAnalysisResult(
        contentId: String,
        contentType: String,
        contentTitle: String,
        showTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        filterLevel: ProfanityFilterLevel,
        analysisResult: SubtitleAnalysisResult,
        subtitleFileName: String,
        filteredSubtitleContent: String
    ): String? {
        try {
            android.util.Log.d("SubtitleAnalysisRepository", "Saving analysis for contentId: $contentId, filterLevel: $filterLevel")
            android.util.Log.d("SubtitleAnalysisRepository", "Content: $contentTitle, Analysis events: ${analysisResult.profanityEvents.size}")
            // Save filtered subtitles to persistent storage
            val filteredSubtitlePath = saveFilteredSubtitle(
                contentId = contentId,
                filterLevel = filterLevel,
                filteredContent = filteredSubtitleContent,
                originalFileName = subtitleFileName
            )
            
            // Calculate profanity level based on analysis
            val profanityLevel = calculateProfanityLevel(analysisResult)
            
            // Create analysis entity
            val analysisEntity = SubtitleAnalysisEntity(
                id = "${contentId}_${filterLevel.name}",
                contentId = contentId,
                contentType = contentType,
                contentTitle = contentTitle,
                showTitle = showTitle,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                filterLevel = filterLevel,
                profanityLevel = profanityLevel,
                detectedWords = gson.toJson(analysisResult.profanityEvents.flatMap { it.detectedWords }.distinct()),
                totalWordsCount = analysisResult.filteredSubtitles.sumOf { countWords(it.text) },
                profanityWordsCount = analysisResult.profanityEvents.sumOf { it.detectedWords.size },
                profanityPercentage = calculateProfanityPercentage(analysisResult),
                subtitleFileName = subtitleFileName,
                filteredSubtitlePath = filteredSubtitlePath,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            // Save to database
            dao.insertAnalysis(analysisEntity)
            android.util.Log.d("SubtitleAnalysisRepository", "Successfully saved analysis to database with ID: ${analysisEntity.id}")
            
            return filteredSubtitlePath
        } catch (e: Exception) {
            android.util.Log.e("SubtitleAnalysisRepository", "Failed to save analysis: ${e.message}", e)
            e.printStackTrace()
            return null
        }
    }
    
    private fun saveFilteredSubtitle(
        contentId: String,
        filterLevel: ProfanityFilterLevel,
        filteredContent: String,
        originalFileName: String
    ): String {
        // Use Android's internal storage directory
        val appDir = File(context.filesDir, "filtered_subtitles")
        appDir.mkdirs()
        
        android.util.Log.d("SubtitleAnalysisRepository", "Saving filtered subtitle to: ${appDir.absolutePath}")
        
        // Create unique filename for this content and filter level (Settings screen looks for _filtered.srt)
        val baseName = originalFileName.removeSuffix(".srt")
        val fileName = "${contentId}_${filterLevel.name}_${baseName}_filtered.srt"
        val file = File(appDir, fileName)
        
        // Write filtered subtitle content
        file.writeText(filteredContent)
        android.util.Log.d("SubtitleAnalysisRepository", "Successfully saved filtered subtitle: ${file.absolutePath}")
        
        return file.absolutePath
    }
    
    private fun calculateProfanityLevel(analysisResult: SubtitleAnalysisResult): ProfanityLevel {
        val totalEvents = analysisResult.profanityEvents.size
        val totalSubtitles = analysisResult.filteredSubtitles.size
        
        if (totalEvents == 0 || totalSubtitles == 0) return ProfanityLevel.NONE
        
        val profanityRatio = totalEvents.toFloat() / totalSubtitles.toFloat()
        
        return when {
            profanityRatio >= 0.15f -> ProfanityLevel.HIGH    // 15%+ profanity events
            profanityRatio >= 0.08f -> ProfanityLevel.MEDIUM  // 8-15% profanity events
            profanityRatio >= 0.02f -> ProfanityLevel.LOW     // 2-8% profanity events
            else -> ProfanityLevel.NONE
        }
    }
    
    private fun calculateProfanityPercentage(analysisResult: SubtitleAnalysisResult): Float {
        val totalEvents = analysisResult.profanityEvents.size
        val totalSubtitles = analysisResult.filteredSubtitles.size
        
        return if (totalSubtitles > 0) {
            (totalEvents.toFloat() / totalSubtitles.toFloat()) * 100f
        } else {
            0f
        }
    }
    
    private fun countWords(text: String): Int {
        return text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
    }
    
    suspend fun getFilteredSubtitleContent(contentId: String, filterLevel: ProfanityFilterLevel): String? {
        android.util.Log.d("SubtitleAnalysisRepository", "getFilteredSubtitleContent called for contentId: $contentId, filterLevel: $filterLevel")
        
        val analysis = dao.getAnalysisForContent(contentId, filterLevel)
        android.util.Log.d("SubtitleAnalysisRepository", "Found analysis: ${analysis?.id}, path: ${analysis?.filteredSubtitlePath}")
        
        return analysis?.filteredSubtitlePath?.let { path ->
            try {
                android.util.Log.d("SubtitleAnalysisRepository", "Attempting to read file at path: $path")
                val file = File(path)
                android.util.Log.d("SubtitleAnalysisRepository", "File exists: ${file.exists()}, readable: ${file.canRead()}, size: ${if (file.exists()) file.length() else "N/A"}")
                
                if (!file.exists()) {
                    android.util.Log.w("SubtitleAnalysisRepository", "Filtered subtitle file does not exist: $path")
                    return@let null
                }
                
                if (!file.canRead()) {
                    android.util.Log.w("SubtitleAnalysisRepository", "Cannot read filtered subtitle file: $path")
                    return@let null
                }
                
                val content = file.readText()
                android.util.Log.d("SubtitleAnalysisRepository", "Successfully read ${content.length} characters from file")
                content
            } catch (e: Exception) {
                android.util.Log.e("SubtitleAnalysisRepository", "Error reading filtered subtitle file: ${e.message}", e)
                null
            }
        } ?: run {
            android.util.Log.w("SubtitleAnalysisRepository", "No analysis found or no path stored for contentId: $contentId, filterLevel: $filterLevel")
            null
        }
    }
    
    suspend fun deleteAnalysisForContent(contentId: String) {
        // Get all analyses for this content to clean up files
        val analyses = dao.getAllAnalysesForContent(contentId)
        
        // Delete associated filtered subtitle files
        analyses.forEach { analysis ->
            analysis.filteredSubtitlePath?.let { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    // Ignore file deletion errors
                }
            }
        }
        
        // Delete from database
        dao.deleteAllAnalysesForContent(contentId)
    }
    
    suspend fun deleteAnalysis(contentId: String, filterLevel: ProfanityFilterLevel) {
        val analysis = dao.getAnalysisForContent(contentId, filterLevel)
        analysis?.let {
            // Delete associated filtered subtitle file
            it.filteredSubtitlePath?.let { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    // Ignore file deletion errors
                }
            }
            
            // Delete from database
            dao.deleteAnalysis(it)
        }
    }
    
    suspend fun cleanupOldAnalyses(olderThanDays: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
        
        // Get old analyses to clean up files
        val oldAnalyses = dao.getOldAnalyses(cutoffTime)
        android.util.Log.d("SubtitleAnalysisRepository", "Found ${oldAnalyses.size} old subtitle analyses to clean up")
        
        // Delete associated filtered subtitle files
        oldAnalyses.forEach { analysis ->
            analysis.filteredSubtitlePath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists() && file.delete()) {
                        android.util.Log.d("SubtitleAnalysisRepository", "Deleted old filtered subtitle file: $path")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SubtitleAnalysisRepository", "Failed to delete old file $path: ${e.message}")
                }
            }
        }
        
        // Delete from database
        dao.deleteOldAnalyses(cutoffTime)
    }
    
    suspend fun clearAllSubtitleData(): Int {
        try {
            android.util.Log.d("SubtitleAnalysisRepository", "Starting complete subtitle data cleanup...")
            
            // Get total count before clearing
            val totalCount = dao.getTotalAnalysisCount()
            android.util.Log.d("SubtitleAnalysisRepository", "Found $totalCount subtitle analysis entries to clear")
            
            // Clear all filtered subtitle files
            clearAllFilteredSubtitleFiles()
            
            // Clear all database entries
            dao.clearAllAnalyses()
            android.util.Log.d("SubtitleAnalysisRepository", "Cleared all subtitle analysis entries from database")
            
            // Clear temporary subtitle cache
            clearSubtitleCache()
            
            android.util.Log.d("SubtitleAnalysisRepository", "Successfully cleared all subtitle data!")
            return totalCount
            
        } catch (e: Exception) {
            android.util.Log.e("SubtitleAnalysisRepository", "Error during subtitle data cleanup: ${e.message}", e)
            throw e
        }
    }
    
    private fun clearAllFilteredSubtitleFiles() {
        try {
            val appDir = File(context.filesDir, "filtered_subtitles")
            if (appDir.exists()) {
                val filesCleared = appDir.listFiles()?.size ?: 0
                android.util.Log.d("SubtitleAnalysisRepository", "Clearing $filesCleared filtered subtitle files...")
                
                appDir.deleteRecursively()
                android.util.Log.d("SubtitleAnalysisRepository", "Cleared filtered subtitle files directory")
            } else {
                android.util.Log.d("SubtitleAnalysisRepository", "No filtered subtitle files directory found")
            }
        } catch (e: Exception) {
            android.util.Log.e("SubtitleAnalysisRepository", "Error clearing filtered subtitle files: ${e.message}", e)
        }
    }
    
    private fun clearSubtitleCache() {
        try {
            // Clear temporary subtitle cache (usually in system temp directory)
            val tempDir = File(System.getProperty("java.io.tmpdir") ?: context.cacheDir.absolutePath)
            val subtitleCacheFiles = tempDir.listFiles { _, name -> 
                name.startsWith("subtitle_") && name.endsWith(".srt")
            }
            
            val cacheFilesCleared = subtitleCacheFiles?.size ?: 0
            android.util.Log.d("SubtitleAnalysisRepository", "Found $cacheFilesCleared subtitle cache files to clear")
            
            subtitleCacheFiles?.forEach { file ->
                try {
                    if (file.delete()) {
                        android.util.Log.d("SubtitleAnalysisRepository", "Deleted cache file: ${file.name}")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SubtitleAnalysisRepository", "Failed to delete cache file ${file.name}: ${e.message}")
                }
            }
            
            android.util.Log.d("SubtitleAnalysisRepository", "Subtitle cache cleanup completed")
            
        } catch (e: Exception) {
            android.util.Log.e("SubtitleAnalysisRepository", "Error clearing subtitle cache: ${e.message}", e)
        }
    }
    
    suspend fun getSubtitleDataStats(): SubtitleDataStats {
        return try {
            val totalAnalyses = dao.getTotalAnalysisCount()
            
            val filteredFilesDir = File(context.filesDir, "filtered_subtitles")
            val filteredFilesCount = if (filteredFilesDir.exists()) {
                filteredFilesDir.listFiles()?.size ?: 0
            } else {
                0
            }
            
            val tempDir = File(System.getProperty("java.io.tmpdir") ?: context.cacheDir.absolutePath)
            val cacheFilesCount = tempDir.listFiles { _, name -> 
                name.startsWith("subtitle_") && name.endsWith(".srt")
            }?.size ?: 0
            
            SubtitleDataStats(
                databaseEntries = totalAnalyses,
                filteredSubtitleFiles = filteredFilesCount,
                cacheFiles = cacheFilesCount
            )
        } catch (e: Exception) {
            android.util.Log.e("SubtitleAnalysisRepository", "Error getting subtitle data stats: ${e.message}", e)
            SubtitleDataStats(0, 0, 0)
        }
    }
    
    data class SubtitleDataStats(
        val databaseEntries: Int,
        val filteredSubtitleFiles: Int,
        val cacheFiles: Int
    )

    suspend fun getFilthiestMovieTitle(): String? {
        return dao.getFilthiestMovieTitle()
    }
}