package com.purestream.data.api

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.purestream.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class GeminiMovieResult(
    val title: String,
    val year: Int
)

class GeminiApiService {

    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    /**
     * Fetches trending movies from Gemini with Google Search grounding
     * Returns structured JSON: [{title: "...", year: 2024}, ...]
     */
    suspend fun getTrendingMovies(limit: Int = 20): Result<List<GeminiMovieResult>> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Identify the top $limit trending movies for this week.
                Return ONLY a JSON array of objects.
                Each object must have: 'title' (string) and 'year' (integer).
                Do not include any conversational text or explanation.

                Example format:
                [{"title":"The Batman","year":2022},{"title":"Dune","year":2021}]
            """.trimIndent()

            val response = model.generateContent(content { text(prompt) })
            val responseText = response.text ?: return@withContext Result.failure(Exception("Empty response from Gemini"))

            // Parse JSON response
            val movies = parseMoviesFromJson(responseText)
            Result.success(movies)
        } catch (e: Exception) {
            android.util.Log.e("GeminiApiService", "Error fetching trending movies: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches top-rated movies from Gemini
     */
    suspend fun getTopRatedMovies(limit: Int = 20): Result<List<GeminiMovieResult>> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Identify the top $limit highest-rated movies of all time according to critics and audiences.
                Return ONLY a JSON array of objects.
                Each object must have: 'title' (string) and 'year' (integer).

                Example format:
                [{"title":"The Shawshank Redemption","year":1994},{"title":"The Godfather","year":1972}]
            """.trimIndent()

            val response = model.generateContent(content { text(prompt) })
            val responseText = response.text ?: return@withContext Result.failure(Exception("Empty response"))

            val movies = parseMoviesFromJson(responseText)
            Result.success(movies)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get top action movies
     * Focus on high-octane, adrenaline-pumping films
     */
    suspend fun getActionMovies(limit: Int = 20): Result<List<GeminiMovieResult>> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Identify the top $limit best action movies of all time.
                Focus on high-octane, adrenaline-pumping films with intense action sequences, chase scenes, and excitement.
                Include classic action films and modern blockbusters.

                Return ONLY a JSON array of objects.
                Each object must have exactly two fields:
                - 'title' (string): The exact title of the movie
                - 'year' (integer): The release year

                Example format:
                [{"title":"Mad Max: Fury Road","year":2015},{"title":"Die Hard","year":1988}]

                Do not include any explanation or additional text.
            """.trimIndent()

            val response = model.generateContent(content { text(prompt) })
            val responseText = response.text ?: return@withContext Result.failure(Exception("Empty response from Gemini"))

            val movies = parseMoviesFromJson(responseText)
            android.util.Log.d("GeminiApiService", "Action movies response: ${movies.size} movies")
            Result.success(movies)
        } catch (e: Exception) {
            android.util.Log.e("GeminiApiService", "Error fetching action movies: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Generate creative title for streaming collection (non-seasonal)
     */
    suspend fun generateStreamingTitle(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Create a creative, catchy title for a collection of popular streaming platform originals.
                The title should be engaging and modern.

                Examples of good titles:
                - "Streaming Sensations"
                - "Platform Premieres"
                - "Binge-Worthy Hits"
                - "Streaming Originals"
                - "Must-Watch Streaming"

                DO NOT include seasonal references (no "Summer", "Winter", "Fall", "Spring").
                Return only the title text, nothing else.
            """.trimIndent()

            val response = model.generateContent(content { text(prompt) })
            val title = response.text?.trim() ?: "Streaming Originals"

            android.util.Log.d("GeminiApiService", "Generated streaming title: '$title'")
            Result.success(title)
        } catch (e: Exception) {
            android.util.Log.e("GeminiApiService", "Error generating streaming title: ${e.message}", e)
            Result.success("Streaming Originals") // Fallback
        }
    }

    /**
     * Generates seasonal/time-aware collection title
     * E.g., "Hot Summer Flicks" in summer, "Cozy Winter Classics" in winter
     */
    suspend fun generateSeasonalTitle(collectionType: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentMonth = java.time.LocalDate.now().monthValue
            val season = when (currentMonth) {
                12, 1, 2 -> "winter"
                3, 4, 5 -> "spring"
                6, 7, 8 -> "summer"
                else -> "fall"
            }

            val prompt = """
                Generate a creative, catchy collection title for a $collectionType collection in $season.
                The title should be 2-4 words maximum and evoke the feeling of $season.
                Return ONLY the title, no explanation or quotes.

                Examples:
                - Summer: "Hot Summer Flicks"
                - Winter: "Cozy Winter Nights"
                - Fall: "Autumn Favorites"
                - Halloween (October): "Chilling Horror"
            """.trimIndent()

            val response = model.generateContent(content { text(prompt) })
            val title = response.text?.trim() ?: "$collectionType Movies"
            Result.success(title)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMoviesFromJson(jsonText: String): List<GeminiMovieResult> {
        val movies = mutableListOf<GeminiMovieResult>()
        try {
            // Extract JSON array from response (may have extra text)
            val jsonStart = jsonText.indexOf("[")
            val jsonEnd = jsonText.lastIndexOf("]") + 1
            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                throw Exception("No JSON array found in response")
            }

            val jsonArray = JSONArray(jsonText.substring(jsonStart, jsonEnd))
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                movies.add(GeminiMovieResult(
                    title = obj.getString("title"),
                    year = obj.getInt("year")
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiApiService", "Error parsing JSON: ${e.message}", e)
        }
        return movies
    }
}
