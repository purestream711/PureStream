package com.purestream.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.purestream.data.database.entities.SubtitleTimingPreference

@Dao
interface SubtitleTimingDao {
    
    @Query("SELECT timingOffsetMs FROM subtitle_timing_preferences WHERE contentId = :contentId")
    suspend fun getTimingPreference(contentId: String): Long?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTimingPreference(preference: SubtitleTimingPreference)
    
    @Query("DELETE FROM subtitle_timing_preferences WHERE contentId = :contentId")
    suspend fun deleteTimingPreference(contentId: String)
    
    @Query("SELECT * FROM subtitle_timing_preferences WHERE contentId = :contentId")
    suspend fun getTimingPreferenceEntity(contentId: String): SubtitleTimingPreference?
    
    @Query("SELECT COUNT(*) FROM subtitle_timing_preferences")
    suspend fun getPreferenceCount(): Int
    
    // Helper method to save timing preference with current timestamp
    suspend fun saveTimingOffset(contentId: String, offsetMs: Long) {
        val preference = SubtitleTimingPreference(
            contentId = contentId,
            timingOffsetMs = offsetMs,
            updatedAt = System.currentTimeMillis()
        )
        saveTimingPreference(preference)
    }
}