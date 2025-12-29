package com.purestream.data.service

import com.purestream.data.api.GeminiMovieResult
import com.purestream.data.model.Movie

class GeminiMatchingService {

    /**
     * Matches Gemini results against Plex library using fuzzy matching
     * Returns only movies that exist in the user's Plex library
     */
    fun matchMovies(
        geminiResults: List<GeminiMovieResult>,
        plexMovies: List<Movie>
    ): List<Movie> {
        val matchedMovies = mutableListOf<Movie>()

        for (geminiMovie in geminiResults) {
            val match = plexMovies.find { plexMovie ->
                isMovieMatch(geminiMovie.title, geminiMovie.year, plexMovie)
            }

            if (match != null) {
                matchedMovies.add(match)
                android.util.Log.d("GeminiMatching", "✓ Matched: ${geminiMovie.title} (${geminiMovie.year}) → ${match.title}")
            } else {
                android.util.Log.d("GeminiMatching", "✗ No match: ${geminiMovie.title} (${geminiMovie.year})")
            }
        }

        return matchedMovies
    }

    /**
     * Fuzzy matching logic to compare titles
     * Handles variations like "The Batman" vs "Batman", punctuation differences, etc.
     */
    private fun isMovieMatch(
        geminiTitle: String,
        geminiYear: Int,
        plexMovie: Movie
    ): Boolean {
        // 1. Normalize both strings (lowercase, remove punctuation)
        val cleanGemini = geminiTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanPlex = plexMovie.title.lowercase().replace(Regex("[^a-z0-9]"), "")

        // 2. Direct match check
        if (cleanGemini == cleanPlex) {
            return yearMatches(geminiYear, plexMovie.year)
        }

        // 3. Simple "Contains" check (handles "The Batman" vs "Batman")
        if (cleanGemini.contains(cleanPlex) || cleanPlex.contains(cleanGemini)) {
            return yearMatches(geminiYear, plexMovie.year)
        }

        // 4. Levenshtein distance check (for typos/minor differences)
        val distance = levenshteinDistance(cleanGemini, cleanPlex)
        val maxLength = maxOf(cleanGemini.length, cleanPlex.length)
        val similarity = 1.0 - (distance.toDouble() / maxLength)

        // If 80%+ similar and year matches, consider it a match
        if (similarity >= 0.8 && yearMatches(geminiYear, plexMovie.year)) {
            return true
        }

        return false
    }

    /**
     * Check if years match (allow ±1 year for release date variations)
     */
    private fun yearMatches(geminiYear: Int, plexYear: Int?): Boolean {
        if (plexYear == null) return false
        return kotlin.math.abs(geminiYear - plexYear) <= 1
    }

    /**
     * Levenshtein distance algorithm for string similarity
     * Returns the minimum number of edits needed to transform one string to another
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[m][n]
    }
}
