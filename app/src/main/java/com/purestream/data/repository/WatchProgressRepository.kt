package com.purestream.data.repository

import com.purestream.data.database.AppDatabase
import com.purestream.data.database.dao.WatchProgressDao
import com.purestream.data.database.entities.WatchProgressEntity
import kotlinx.coroutines.flow.Flow
import android.content.Context

class WatchProgressRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    private val dao: WatchProgressDao = database.watchProgressDao()

    /**
     * Get watch progress for a specific content
     */
    suspend fun getProgress(profileId: String, ratingKey: String): WatchProgressEntity? {
        return dao.getProgress(profileId, ratingKey)
    }

    /**
     * Get watch progress as Flow for real-time updates
     */
    fun getProgressFlow(profileId: String, ratingKey: String): Flow<WatchProgressEntity?> {
        return dao.getProgressFlow(profileId, ratingKey)
    }

    /**
     * Get all watch progress for a profile
     */
    suspend fun getAllProgressForProfile(profileId: String): List<WatchProgressEntity> {
        return dao.getAllProgressForProfile(profileId)
    }

    /**
     * Get continue watching list (incomplete content, sorted by recent)
     */
    suspend fun getContinueWatching(profileId: String, limit: Int = 20): List<WatchProgressEntity> {
        return dao.getContinueWatching(profileId, limit)
    }

    /**
     * Get continue watching as Flow
     */
    fun getContinueWatchingFlow(profileId: String, limit: Int = 20): Flow<List<WatchProgressEntity>> {
        return dao.getContinueWatchingFlow(profileId, limit)
    }

    /**
     * Save or update watch progress
     */
    suspend fun saveProgress(
        profileId: String,
        ratingKey: String,
        contentType: String,
        contentTitle: String,
        position: Long,
        duration: Long,
        showTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ) {
        val progressPercentage = if (duration > 0) (position.toFloat() / duration.toFloat()) else 0f
        val completed = progressPercentage >= 0.90f

        val progress = WatchProgressEntity(
            id = "${profileId}_$ratingKey",
            profileId = profileId,
            ratingKey = ratingKey,
            contentType = contentType,
            contentTitle = contentTitle,
            showTitle = showTitle,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            position = position,
            duration = duration,
            completed = completed,
            lastWatched = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        dao.insertProgress(progress)
        android.util.Log.d("WatchProgressRepository", "Saved progress: $ratingKey at $position/$duration (${(progressPercentage * 100).toInt()}%)")
    }

    /**
     * Update progress position
     */
    suspend fun updateProgress(
        profileId: String,
        ratingKey: String,
        position: Long,
        duration: Long
    ) {
        val existing = dao.getProgress(profileId, ratingKey)
        if (existing != null) {
            val progressPercentage = if (duration > 0) (position.toFloat() / duration.toFloat()) else 0f
            val completed = progressPercentage >= 0.90f

            val updated = existing.copy(
                position = position,
                duration = duration,
                completed = completed,
                lastWatched = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            dao.updateProgress(updated)
        }
    }

    /**
     * Mark content as completed (watched >90%)
     */
    suspend fun markCompleted(profileId: String, ratingKey: String) {
        val existing = dao.getProgress(profileId, ratingKey)
        if (existing != null) {
            val updated = existing.copy(
                completed = true,
                position = existing.duration, // Set to end
                lastWatched = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            dao.updateProgress(updated)
        }
    }

    /**
     * Delete progress for specific content
     */
    suspend fun deleteProgress(profileId: String, ratingKey: String) {
        dao.deleteProgressForContent(profileId, ratingKey)
    }

    /**
     * Delete all progress for a profile
     */
    suspend fun deleteAllProgressForProfile(profileId: String) {
        dao.deleteAllProgressForProfile(profileId)
    }

    /**
     * Clean up old watch progress (e.g., older than 90 days)
     */
    suspend fun cleanupOldProgress(daysOld: Int = 90) {
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        dao.deleteOldProgress(cutoffTime)
    }

    /**
     * Get progress count for profile
     */
    suspend fun getProgressCount(profileId: String): Int {
        return dao.getProgressCountForProfile(profileId)
    }

    /**
     * Check if content has been started
     */
    suspend fun hasProgress(profileId: String, ratingKey: String): Boolean {
        return dao.getProgress(profileId, ratingKey) != null
    }

    /**
     * Get watch progress percentage (0.0 to 1.0)
     */
    suspend fun getProgressPercentage(profileId: String, ratingKey: String): Float {
        val progress = dao.getProgress(profileId, ratingKey)
        return if (progress != null && progress.duration > 0) {
            (progress.position.toFloat() / progress.duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /**
     * Get watch progress position in milliseconds
     */
    suspend fun getProgressPosition(profileId: String, ratingKey: String): Long {
        val progress = dao.getProgress(profileId, ratingKey)
        return progress?.position ?: 0L
    }
}
