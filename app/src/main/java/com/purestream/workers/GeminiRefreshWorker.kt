package com.purestream.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.purestream.data.repository.ProfileRepository
import com.purestream.data.service.AiCurationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class GeminiRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val profileRepository = ProfileRepository(applicationContext)
    private val aiCurationService = AiCurationService(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("GeminiRefreshWorker", "Starting AI curation worker")

            // Get all profiles with AI curation enabled
            val allProfiles = profileRepository.getAllProfiles().first()
            val aiEnabledProfiles = allProfiles.filter { it.aiCuratedEnabled }

            if (aiEnabledProfiles.isEmpty()) {
                android.util.Log.d("GeminiRefreshWorker", "No profiles have AI curation enabled, skipping")
                return@withContext Result.success()
            }

            // Process AI curation for each profile
            var lastError: String? = null
            for (profile in aiEnabledProfiles) {
                val result = aiCurationService.processAiCuration(profile)
                if (result.isFailure) {
                    val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                    android.util.Log.e("GeminiRefreshWorker", "AI curation failed for profile ${profile.name}: $errorMessage")
                    lastError = errorMessage

                    // Return failure immediately so user sees the error
                    return@withContext Result.failure(
                        androidx.work.workDataOf("error" to errorMessage)
                    )
                }
            }

            android.util.Log.d("GeminiRefreshWorker", "AI curation worker completed successfully")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("GeminiRefreshWorker", "Error in AI curation worker: ${e.message}", e)
            Result.retry()
        }
    }
}
