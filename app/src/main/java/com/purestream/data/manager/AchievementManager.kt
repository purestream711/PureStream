package com.purestream.data.manager

import android.content.Context
import com.purestream.data.model.Achievement
import com.purestream.data.model.Profile
import com.purestream.data.model.ProfanityFilterLevel
import com.purestream.data.repository.ProfileRepository
import com.purestream.data.repository.WatchProgressRepository
import com.purestream.utils.SoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.purestream.data.database.AppDatabase

class AchievementManager(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val database: AppDatabase
) {
    // Check Filter Master: Filter 100 words in a single movie
    suspend fun checkFilterMaster(profileId: String, filteredCount: Int) {
        if (filteredCount >= 100) {
            unlockAchievement(profileId, Achievement.FILTER_MASTER)
        }
    }

    // Check Marathon Runner: Watch 10 movies in a week
    suspend fun checkMarathonRunner(profileId: String) {
        withContext(Dispatchers.IO) {
            val progressList = watchProgressRepository.getAllProgressForProfile(profileId)
            
            val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            
            val watchedMoviesInLastWeek = progressList.count { 
                it.completed && 
                it.contentType == "movie" && 
                it.lastWatched >= oneWeekAgo 
            }

            if (watchedMoviesInLastWeek >= 10) {
                unlockAchievement(profileId, Achievement.MARATHON_RUNNER)
            }
        }
    }

    // Check Clean Sweep: Use Strict filter for 30 days
    suspend fun checkCleanSweep(profile: Profile) {
        if (profile.profanityFilterLevel == ProfanityFilterLevel.STRICT) {
            val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000L
            val age = System.currentTimeMillis() - profile.createdAt
            
            if (age >= thirtyDaysMillis) {
                unlockAchievement(profile.id, Achievement.CLEAN_SWEEP)
            }
        }
    }

    // Check Family Protector: Watch 5 movies with R or PG-13 rating
    suspend fun checkFamilyProtector(profileId: String) {
        withContext(Dispatchers.IO) {
            val progressList = watchProgressRepository.getAllProgressForProfile(profileId)
            val watchedMovies = progressList.filter { it.completed && it.contentType == "movie" }
            
            if (watchedMovies.isEmpty()) return@withContext

            var protectedCount = 0
            val movieCacheDao = database.movieCacheDao()

            for (progress in watchedMovies) {
                val movie = movieCacheDao.getMovieByRatingKey(progress.ratingKey)
                if (movie != null) {
                    val rating = movie.contentRating
                    if (rating == "R" || rating == "PG-13") {
                        protectedCount++
                    }
                }
            }

            if (protectedCount >= 5) {
                unlockAchievement(profileId, Achievement.FAMILY_PROTECTOR)
            }
        }
    }

    // Check Power User: Become a Pro subscriber
    suspend fun checkPowerUser(profileId: String) {
        unlockAchievement(profileId, Achievement.POWER_USER)
    }

    // Check Maxed Out: Reach level 30
    suspend fun checkMaxedOut(profileId: String, currentLevel: Int) {
        if (currentLevel >= 30) {
            unlockAchievement(profileId, Achievement.MAXED_OUT)
        }
    }

    // Check Completionist: Unlock all other achievements
    private suspend fun checkCompletionist(profileId: String, unlockedAchievements: List<String>) {
        val allOtherAchievements = Achievement.values()
            .filter { it != Achievement.COMPLETIONIST }
            .map { it.name }
        
        if (unlockedAchievements.containsAll(allOtherAchievements)) {
            unlockAchievement(profileId, Achievement.COMPLETIONIST)
        }
    }

    suspend fun unlockAchievement(profileId: String, achievement: Achievement) {
        withContext(Dispatchers.IO) {
            val profile = profileRepository.getProfileById(profileId) ?: return@withContext
            
            if (!profile.unlockedAchievements.contains(achievement.name)) {
                val newAchievements = profile.unlockedAchievements + achievement.name
                val updatedProfile = profile.copy(unlockedAchievements = newAchievements)
                
                profileRepository.updateProfile(updatedProfile)
                
                android.util.Log.i("AchievementManager", "Unlocked achievement: ${achievement.title} for ${profile.name}")

                // Check for Completionist
                if (achievement != Achievement.COMPLETIONIST) {
                    checkCompletionist(profileId, newAchievements)
                }
            }
        }
    }
}
