package com.purestream.data.service

import android.content.Context
import com.purestream.data.api.GeminiApiService
import com.purestream.data.database.AppDatabase
import com.purestream.data.database.entities.GeminiCachedMovie
import com.purestream.data.model.CollectionType
import com.purestream.data.model.DashboardCollection
import com.purestream.data.model.Profile
import com.purestream.data.repository.PlexRepository
import com.purestream.data.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/**
 * Centralized service for AI curation using Gemini API
 * Can be called from Loading Screen or Settings Toggle
 */
class AiCurationService(private val context: Context) {

    private val geminiApiService = GeminiApiService()
    private val matchingService = GeminiMatchingService()
    private val plexRepository = PlexRepository(context)
    private val profileRepository = ProfileRepository(context)
    private val database = AppDatabase.getDatabase(context)
    private val geminiDao = database.geminiCachedMovieDao()

    /**
     * Process AI curation for a specific profile
     * Returns true if successful, false if failed
     */
    suspend fun processAiCuration(profile: Profile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("AiCurationService", "Starting AI curation for profile: ${profile.name}")

            // 1. Fetch trending movies from Gemini
            android.util.Log.d("AiCurationService", "Fetching trending movies from Gemini...")
            val trendingResult = geminiApiService.getTrendingMovies(limit = 20)
            if (trendingResult.isFailure) {
                val error = trendingResult.exceptionOrNull()?.message ?: "Unknown error"
                android.util.Log.e("AiCurationService", "Failed to fetch trending movies: $error")
                return@withContext Result.failure(Exception("Failed to fetch trending movies: $error"))
            }

            // 2. Fetch top-rated movies from Gemini
            android.util.Log.d("AiCurationService", "Fetching top-rated movies from Gemini...")
            val topRatedResult = geminiApiService.getTopRatedMovies(limit = 20)
            if (topRatedResult.isFailure) {
                val error = topRatedResult.exceptionOrNull()?.message ?: "Unknown error"
                android.util.Log.e("AiCurationService", "Failed to fetch top-rated movies: $error")
                return@withContext Result.failure(Exception("Failed to fetch top-rated movies: $error"))
            }

            // 3. Fetch action movies from Gemini
            android.util.Log.d("AiCurationService", "Fetching action movies from Gemini...")
            val actionResult = geminiApiService.getActionMovies(limit = 20)
            if (actionResult.isFailure) {
                val error = actionResult.exceptionOrNull()?.message ?: "Unknown error"
                android.util.Log.e("AiCurationService", "Failed to fetch action movies: $error")
                return@withContext Result.failure(Exception("Failed to fetch action movies: $error"))
            }

            val trendingMovies = trendingResult.getOrNull() ?: emptyList()
            val topRatedMovies = topRatedResult.getOrNull() ?: emptyList()
            val actionMovies = actionResult.getOrNull() ?: emptyList()

            android.util.Log.d("AiCurationService", "Gemini returned ${trendingMovies.size} trending, ${topRatedMovies.size} top-rated, ${actionMovies.size} action")

            // 4. Use fixed collection titles (no AI generation for better consistency)
            val trendingTitle = "What the World is Watching"
            val topRatedTitle = "All-Time Greats"
            val actionTitle = "Pulse-Pounding Action"

            android.util.Log.d("AiCurationService", "Titles: '$trendingTitle', '$topRatedTitle', '$actionTitle'")

            // 5. Get user's movies from all selected libraries
            android.util.Log.d("AiCurationService", "Fetching user's Plex movies from ${profile.selectedLibraries.size} libraries...")
            val userMovies = mutableListOf<com.purestream.data.model.Movie>()
            for (libraryId in profile.selectedLibraries) {
                val moviesResult = plexRepository.getMovies(libraryId = libraryId, profileId = profile.id)
                moviesResult.getOrNull()?.let { movies ->
                    userMovies.addAll(movies)
                    android.util.Log.d("AiCurationService", "  Library $libraryId: ${movies.size} movies")
                }
            }

            if (userMovies.isEmpty()) {
                android.util.Log.w("AiCurationService", "No movies found in user's Plex library")
                return@withContext Result.failure(Exception("No movies found in your Plex library"))
            }

            android.util.Log.d("AiCurationService", "Total Plex movies: ${userMovies.size}")

            // 6. Match Gemini results against user's library
            android.util.Log.d("AiCurationService", "Matching Gemini recommendations against Plex library...")
            val matchedTrending = matchingService.matchMovies(trendingMovies, userMovies)
            val matchedTopRated = matchingService.matchMovies(topRatedMovies, userMovies)
            val matchedAction = matchingService.matchMovies(actionMovies, userMovies)

            android.util.Log.d("AiCurationService", "Matched ${matchedTrending.size} trending, ${matchedTopRated.size} top-rated, ${matchedAction.size} action")

            // 7. Cache matched movies to database (shuffled for variety)
            android.util.Log.d("AiCurationService", "Shuffling collections for variety...")

            // Shuffle collections for variety on each refresh
            val shuffledTrending = matchedTrending.shuffled()
            val shuffledTopRated = matchedTopRated.shuffled()
            val shuffledAction = matchedAction.shuffled()

            val trendingCached = shuffledTrending.mapIndexed { index, movie ->
                GeminiCachedMovie(
                    id = "${profile.id}_gemini_trending_${movie.ratingKey}",
                    profileId = profile.id,
                    collectionId = "gemini_trending",
                    movieRatingKey = movie.ratingKey,
                    order = index
                )
            }

            val topRatedCached = shuffledTopRated.mapIndexed { index, movie ->
                GeminiCachedMovie(
                    id = "${profile.id}_gemini_top_rated_${movie.ratingKey}",
                    profileId = profile.id,
                    collectionId = "gemini_top_rated",
                    movieRatingKey = movie.ratingKey,
                    order = index
                )
            }

            val actionCached = shuffledAction.mapIndexed { index, movie ->
                GeminiCachedMovie(
                    id = "${profile.id}_gemini_action_${movie.ratingKey}",
                    profileId = profile.id,
                    collectionId = "gemini_action",
                    movieRatingKey = movie.ratingKey,
                    order = index
                )
            }

            // Clear old cache and insert new
            android.util.Log.d("AiCurationService", "Caching ${trendingCached.size + topRatedCached.size + actionCached.size} movies to database...")
            geminiDao.deleteForProfile(profile.id)
            geminiDao.insertAll(trendingCached + topRatedCached + actionCached)

            // 8. Create DashboardCollection objects with fixed engaging titles
            val aiCollections = listOf(
                DashboardCollection(
                    id = "gemini_trending",
                    title = trendingTitle, // "What the World is Watching"
                    type = CollectionType.GEMINI,
                    isEnabled = true,
                    order = 0,
                    itemCount = shuffledTrending.size
                ),
                DashboardCollection(
                    id = "gemini_top_rated",
                    title = topRatedTitle, // "All-Time Greats"
                    type = CollectionType.GEMINI,
                    isEnabled = true,
                    order = 1,
                    itemCount = shuffledTopRated.size
                ),
                DashboardCollection(
                    id = "gemini_action",
                    title = actionTitle, // "Pulse-Pounding Action"
                    type = CollectionType.GEMINI,
                    isEnabled = true,
                    order = 2,
                    itemCount = shuffledAction.size
                )
            ).filter { it.itemCount > 0 } // Only include collections with matches

            // 9. Select random featured movie from the AI-curated collections
            android.util.Log.d("AiCurationService", "Selecting random featured movie from AI collections...")
            val allMatchedMovies = shuffledTrending + shuffledTopRated + shuffledAction
            val featuredMovie = allMatchedMovies.randomOrNull()
            val featuredMovieRatingKey = featuredMovie?.ratingKey

            if (featuredMovie != null) {
                android.util.Log.d("AiCurationService", "Selected featured movie: '${featuredMovie.title}' (${featuredMovie.year})")
            } else {
                android.util.Log.w("AiCurationService", "No movies available for featured selection")
            }

            // 10. Update profile with AI collections and featured movie
            val existingCollections = profile.dashboardCollections.filter { it.type != CollectionType.GEMINI }
            val updatedCollections = (aiCollections + existingCollections).mapIndexed { index, collection ->
                collection.copy(order = index)
            }
            val updatedProfile = profile.copy(
                dashboardCollections = updatedCollections,
                lastAiCurationTimestamp = System.currentTimeMillis(),
                aiFeaturedMovieRatingKey = featuredMovieRatingKey
            )
            profileRepository.updateProfile(updatedProfile)

            android.util.Log.d("AiCurationService", "✓ AI curation completed successfully!")
            android.util.Log.d("AiCurationService", "  Created ${aiCollections.size} collections")

            Result.success(Unit)

        } catch (e: Exception) {
            android.util.Log.e("AiCurationService", "Error in AI curation: ${e.message}", e)
            Result.failure(e)
        }
    }
}
