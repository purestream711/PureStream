package com.purestream.data.database.dao

import androidx.room.*
import com.purestream.data.database.entities.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND ratingKey = :ratingKey")
    suspend fun getProgress(profileId: String, ratingKey: String): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND ratingKey = :ratingKey")
    fun getProgressFlow(profileId: String, ratingKey: String): Flow<WatchProgressEntity?>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId ORDER BY lastWatched DESC")
    suspend fun getAllProgressForProfile(profileId: String): List<WatchProgressEntity>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId ORDER BY lastWatched DESC")
    fun getAllProgressForProfileFlow(profileId: String): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND completed = 0 ORDER BY lastWatched DESC LIMIT :limit")
    suspend fun getContinueWatching(profileId: String, limit: Int = 20): List<WatchProgressEntity>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND completed = 0 ORDER BY lastWatched DESC LIMIT :limit")
    fun getContinueWatchingFlow(profileId: String, limit: Int = 20): Flow<List<WatchProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: WatchProgressEntity)

    @Update
    suspend fun updateProgress(progress: WatchProgressEntity)

    @Delete
    suspend fun deleteProgress(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId")
    suspend fun deleteAllProgressForProfile(profileId: String)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND ratingKey = :ratingKey")
    suspend fun deleteProgressForContent(profileId: String, ratingKey: String)

    @Query("DELETE FROM watch_progress WHERE lastWatched < :cutoffTime")
    suspend fun deleteOldProgress(cutoffTime: Long)

    @Query("DELETE FROM watch_progress")
    suspend fun clearAllProgress()

    @Query("SELECT COUNT(*) FROM watch_progress WHERE profileId = :profileId")
    suspend fun getProgressCountForProfile(profileId: String): Int

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND contentType = :contentType ORDER BY lastWatched DESC")
    suspend fun getProgressByType(profileId: String, contentType: String): List<WatchProgressEntity>
}
