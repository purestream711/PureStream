package com.purestream.data.database.dao

import androidx.room.*
import com.purestream.data.database.entities.SubtitleAnalysisEntity
import com.purestream.data.model.ProfanityFilterLevel
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleAnalysisDao {
    
    @Query("SELECT * FROM subtitle_analysis WHERE contentId = :contentId AND filterLevel = :filterLevel")
    suspend fun getAnalysisForContent(contentId: String, filterLevel: ProfanityFilterLevel): SubtitleAnalysisEntity?
    
    @Query("SELECT * FROM subtitle_analysis WHERE contentId = :contentId")
    suspend fun getAllAnalysesForContent(contentId: String): List<SubtitleAnalysisEntity>
    
    @Query("SELECT * FROM subtitle_analysis WHERE contentId = :contentId")
    fun getAllAnalysesForContentFlow(contentId: String): Flow<List<SubtitleAnalysisEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: SubtitleAnalysisEntity)
    
    @Update
    suspend fun updateAnalysis(analysis: SubtitleAnalysisEntity)
    
    @Delete
    suspend fun deleteAnalysis(analysis: SubtitleAnalysisEntity)
    
    @Query("DELETE FROM subtitle_analysis WHERE contentId = :contentId")
    suspend fun deleteAllAnalysesForContent(contentId: String)
    
    @Query("SELECT * FROM subtitle_analysis WHERE createdAt < :cutoffTime")
    suspend fun getOldAnalyses(cutoffTime: Long): List<SubtitleAnalysisEntity>

    @Query("DELETE FROM subtitle_analysis WHERE createdAt < :cutoffTime")
    suspend fun deleteOldAnalyses(cutoffTime: Long)
    
    @Query("SELECT COUNT(*) FROM subtitle_analysis WHERE contentId = :contentId")
    suspend fun getAnalysisCountForContent(contentId: String): Int
    
    @Query("SELECT DISTINCT filterLevel FROM subtitle_analysis WHERE contentId = :contentId")
    suspend fun getExistingFilterLevelsForContent(contentId: String): List<ProfanityFilterLevel>
    
    @Query("DELETE FROM subtitle_analysis")
    suspend fun clearAllAnalyses()
    
    @Query("SELECT COUNT(*) FROM subtitle_analysis")
    suspend fun getTotalAnalysisCount(): Int

    @Query("SELECT contentTitle FROM subtitle_analysis WHERE contentType = 'movie' ORDER BY profanityWordsCount DESC LIMIT 1")
    suspend fun getFilthiestMovieTitle(): String?
}