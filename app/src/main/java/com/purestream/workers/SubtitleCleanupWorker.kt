package com.purestream.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.purestream.data.database.AppDatabase
import com.purestream.data.repository.SubtitleAnalysisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SubtitleCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("SubtitleCleanupWorker", "Starting subtitle cleanup worker")

            // Initialize repository
            val database = AppDatabase.getDatabase(applicationContext)
            val repository = SubtitleAnalysisRepository(database, applicationContext)

            // Cleanup subtitles older than 30 days
            // Note: This matches the "Auto remove filtered subtitles after 30 days" requirement
            repository.cleanupOldAnalyses(olderThanDays = 30)

            android.util.Log.d("SubtitleCleanupWorker", "Subtitle cleanup worker completed successfully")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SubtitleCleanupWorker", "Error in subtitle cleanup worker: ${e.message}", e)
            Result.retry()
        }
    }
}
