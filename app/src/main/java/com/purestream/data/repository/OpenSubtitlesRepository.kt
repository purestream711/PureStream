package com.purestream.data.repository

import com.purestream.data.model.*
import com.purestream.profanity.ProfanityFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import kotlin.math.min
import kotlin.math.max

class OpenSubtitlesRepository(
    private val profanityFilter: ProfanityFilter,
    private val subtitleAnalysisRepository: SubtitleAnalysisRepository? = null
) {
    private val apiKey = "qQp93kDwHyfJfW1ZkwKfcHhELEvSnMJI"
    private val client = createOkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://api.opensubtitles.com/api/v1/"
    private val cacheExpirationHours = 24L
    private val maxRetryAttempts = 3
    private val baseRetryDelayMs = 1000L
    
    private fun createOkHttpClient(): OkHttpClient {
        return try {
            // Create a trust manager that accepts all certificates
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        } catch (e: Exception) {
            println("Failed to create SSL-bypassed client, using default: ${e.message}")
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
    
    private fun getCacheFileName(movieTitle: String, episodeInfo: String?): String {
        val cleanTitle = movieTitle.replace("[^a-zA-Z0-9]".toRegex(), "_")
        return if (episodeInfo != null) {
            val cleanEpisode = episodeInfo.replace("[^a-zA-Z0-9]".toRegex(), "_")
            "subtitle_${cleanTitle}_${cleanEpisode}.srt"
        } else {
            "subtitle_${cleanTitle}.srt"
        }
    }
    
    private fun getCachedSubtitleFile(movieTitle: String, episodeInfo: String?): java.io.File? {
        return try {
            val cacheDir = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp")
            val cacheFile = java.io.File(cacheDir, getCacheFileName(movieTitle, episodeInfo))
            
            if (cacheFile.exists()) {
                val ageHours = (System.currentTimeMillis() - cacheFile.lastModified()) / (1000 * 60 * 60)
                if (ageHours < cacheExpirationHours) {
                    println("Found cached subtitle file: ${cacheFile.absolutePath} (${ageHours}h old)")
                    cacheFile
                } else {
                    println("Cached subtitle file expired (${ageHours}h old), deleting...")
                    cacheFile.delete()
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error checking cache: ${e.message}")
            null
        }
    }
    
    private fun saveSubtitleToCache(subtitleContent: String, movieTitle: String, episodeInfo: String?): java.io.File? {
        return try {
            val cacheDir = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp")
            cacheDir.mkdirs()
            val cacheFile = java.io.File(cacheDir, getCacheFileName(movieTitle, episodeInfo))
            cacheFile.writeText(subtitleContent)
            println("Subtitle saved to cache: ${cacheFile.absolutePath}")
            cacheFile
        } catch (e: Exception) {
            println("Failed to save subtitle to cache: ${e.message}")
            null
        }
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                if (s1[i - 1] == s2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    dp[i][j] = 1 + min(dp[i - 1][j], min(dp[i][j - 1], dp[i - 1][j - 1]))
                }
            }
        }
        
        return dp[s1.length][s2.length]
    }
    
    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        
        val maxLength = max(s1.length, s2.length)
        val distance = levenshteinDistance(s1.lowercase(), s2.lowercase())
        return 1.0 - (distance.toDouble() / maxLength)
    }
    
    private fun normalizeTitle(title: String): String {
        return title
            .lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\b(the|a|an)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    private fun generateSearchVariations(title: String, year: Int? = null): List<String> {
        val variations = mutableListOf<String>()
        val cleanTitle = normalizeTitle(title)
        val dotTitle = title.replace(Regex("[\\s:]+"), ".").lowercase()
        
        variations.add(dotTitle)
        variations.add(cleanTitle)
        variations.add(title.lowercase())
        
        if (year != null) {
            variations.add("$dotTitle.$year")
            variations.add("$cleanTitle $year")
        }
        
        return variations.distinct()
    }
    
    private fun calculateSubtitleQualityScore(subtitle: SubtitleResult): Double {
        var score = 0.0
        
        score += subtitle.attributes.downloadCount * 0.4
        score += subtitle.attributes.ratings * 2.0
        score += subtitle.attributes.points * 1.5
        
        if (subtitle.attributes.hearingImpaired == false) score += 10.0
        if (subtitle.attributes.aiTranslated == false) score += 15.0
        if (subtitle.attributes.machineTranslated == false) score += 10.0
        
        val uploaderReliability = when {
            subtitle.attributes.uploader?.name?.contains("admin", ignoreCase = true) == true -> 20.0
            subtitle.attributes.uploader?.name?.contains("trusted", ignoreCase = true) == true -> 15.0
            else -> 0.0
        }
        score += uploaderReliability
        
        return score
    }
    
    private suspend fun <T> executeWithRetry(
        operation: suspend () -> T,
        maxRetries: Int = maxRetryAttempts
    ): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    val delayMs = baseRetryDelayMs * (1L shl attempt)
                    delay(delayMs)
                }
            }
        }
        throw lastException!!
    }
    
    private fun parseFlexibleResponse(jsonString: String): List<SubtitleResult> {
        return try {
            // Log response for debugging
            println("OpenSubtitles API Response: ${jsonString.take(1000)}")
            
            val jsonElement = JsonParser.parseString(jsonString)
            
            // Check if it's a direct array response (most common format)
            if (jsonElement.isJsonArray) {
                val listType = object : TypeToken<List<SubtitleResult>>() {}.type
                return gson.fromJson(jsonString, listType)
            } 
            
            // Check if it's an object with a "data" array
            if (jsonElement.isJsonObject) {
                val jsonObject = jsonElement.asJsonObject
                if (jsonObject.has("data") && jsonObject.get("data").isJsonArray) {
                    val wrappedResponse = gson.fromJson(jsonString, OpenSubtitlesSearchResponse::class.java)
                    return wrappedResponse.data ?: emptyList()
                }
                // Single object response - wrap in list
                return try {
                    val singleResult = gson.fromJson(jsonString, SubtitleResult::class.java)
                    listOf(singleResult)
                } catch (e: Exception) {
                    // If single object parsing fails, return empty list
                    emptyList()
                }
            }
            
            emptyList()
        } catch (e: Exception) {
            // Include more detailed error information
            throw Exception("Failed to parse API response: ${e.message}\nResponse snippet: ${jsonString.take(500)}", e)
        }
    }
    
    suspend fun searchSubtitlesForMovie(
        movieTitle: String,
        imdbId: String? = null,
        year: Int? = null
    ): Result<List<SubtitleResult>> = withContext(Dispatchers.IO) {
        try {
            executeWithRetry(operation = {
                searchSubtitlesForMovieInternal(movieTitle, imdbId, year)
            })
        } catch (e: Exception) {
            val detailedMessage = when {
                e.message?.contains("chain validation failed") == true -> 
                    "SSL certificate validation failed. Check your network connection or try connecting to a different network."
                e.message?.contains("SocketTimeoutException") == true -> 
                    "Request timed out. Check your internet connection and try again."
                e.message?.contains("UnknownHostException") == true -> 
                    "Cannot reach OpenSubtitles API. Check your internet connection."
                e.message?.contains("ConnectException") == true -> 
                    "Cannot connect to OpenSubtitles API. The service may be temporarily unavailable."
                else -> "Network error: ${e.message}"
            }
            println("OpenSubtitles search error: $detailedMessage")
            println("Full exception: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            Result.failure(Exception("Failed to search for subtitles: $detailedMessage", e))
        }
    }
    
    private suspend fun searchSubtitlesForMovieInternal(
        movieTitle: String,
        imdbId: String? = null,
        year: Int? = null
    ): Result<List<SubtitleResult>> {
        // Prioritize IMDB ID search if available
        if (imdbId != null) {
            try {
                val imdbResult = searchMovieByImdbId(imdbId, movieTitle)
                if (imdbResult.isSuccess && imdbResult.getOrThrow().isNotEmpty()) {
                    println("Found ${imdbResult.getOrThrow().size} subtitles using IMDB ID: $imdbId")
                    return imdbResult
                } else {
                    println("IMDB ID search returned no results, falling back to name/year search")
                }
            } catch (e: Exception) {
                println("IMDB ID search failed: ${e.message}, falling back to name/year search")
            }
        }
        
        // Fallback to original query-based search
        val searchVariations = generateSearchVariations(movieTitle, year)
        var bestResults: List<SubtitleResult> = emptyList()
        var highestMatchScore = 0.0
        
        for (queryString in searchVariations) {
            try {
                val urlBuilder = "${baseUrl}subtitles".toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("query", queryString)
                    ?.addQueryParameter("languages", "en")
                    ?.addQueryParameter("ai_translated", "include")
                    ?.addQueryParameter("order_by", "download_count")
                
                if (year != null) {
                    urlBuilder?.addQueryParameter("year", year.toString())
                }
                
                val url = urlBuilder?.build()?.toString()
                    ?: throw Exception("Failed to build request URL")
                
                println("OpenSubtitles API Request:")
                println("  URL: $url")
                println("  Query variation: $queryString")
                println("  Movie: $movieTitle")
                println("  Year: $year")
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("User-Agent", "PureStream/1.0")
                    .addHeader("Api-Key", apiKey)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val subtitlesList = parseFlexibleResponse(responseBody)
                        
                        if (subtitlesList.isNotEmpty()) {
                            val qualityFilteredSubtitles = subtitlesList.filter { 
                                it.attributes.downloadCount >= 10 
                            }
                            
                            val normalizedMovieTitle = normalizeTitle(movieTitle)
                            val resultsWithScores = qualityFilteredSubtitles.map { subtitle ->
                                val titleSimilarity = calculateSimilarity(
                                    normalizedMovieTitle,
                                    normalizeTitle(subtitle.attributes.release ?: "")
                                )
                                val qualityScore = calculateSubtitleQualityScore(subtitle)
                                val combinedScore = titleSimilarity * 50 + qualityScore
                                Pair(subtitle, combinedScore)
                            }
                            
                            val avgScore = resultsWithScores.map { it.second }.average()
                            if (avgScore > highestMatchScore) {
                                highestMatchScore = avgScore
                                bestResults = resultsWithScores
                                    .sortedByDescending { it.second }
                                    .map { it.first }
                            }
                        }
                    }
                }
                
                delay(100)
            } catch (e: Exception) {
                println("Search variation failed for '$queryString': ${e.message}")
                continue
            }
        }
        
        return if (bestResults.isNotEmpty()) {
            println("Found ${bestResults.size} quality subtitles for '$movieTitle' (match score: $highestMatchScore)")
            Result.success(bestResults)
        } else {
            Result.failure(Exception("No suitable subtitles found for movie: $movieTitle"))
        }
    }
    
    suspend fun searchSubtitlesForEpisode(
        showTitle: String,
        seasonNumber: Int,
        episodeNumber: Int,
        showYear: Int? = null,
        imdbId: String? = null
    ): Result<List<SubtitleResult>> = withContext(Dispatchers.IO) {
        try {
            executeWithRetry(operation = {
                searchSubtitlesForEpisodeInternal(showTitle, seasonNumber, episodeNumber, showYear, imdbId)
            })
        } catch (e: Exception) {
            val detailedMessage = when {
                e.message?.contains("chain validation failed") == true -> 
                    "SSL certificate validation failed. Check your network connection or try connecting to a different network."
                e.message?.contains("SocketTimeoutException") == true -> 
                    "Request timed out. Check your internet connection and try again."
                e.message?.contains("UnknownHostException") == true -> 
                    "Cannot reach OpenSubtitles API. Check your internet connection."
                e.message?.contains("ConnectException") == true -> 
                    "Cannot connect to OpenSubtitles API. The service may be temporarily unavailable."
                else -> "Network error: ${e.message}"
            }
            println("OpenSubtitles search error: $detailedMessage")
            println("Full exception: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            Result.failure(Exception("Failed to search for subtitles: $detailedMessage", e))
        }
    }
    
    private suspend fun searchSubtitlesForEpisodeInternal(
        showTitle: String,
        seasonNumber: Int,
        episodeNumber: Int,
        showYear: Int? = null,
        imdbId: String? = null
    ): Result<List<SubtitleResult>> {
        // Prioritize IMDB ID search if available
        if (imdbId != null) {
            try {
                val imdbResult = searchEpisodeByImdbId(imdbId, seasonNumber, episodeNumber, showTitle)
                if (imdbResult.isSuccess && imdbResult.getOrThrow().isNotEmpty()) {
                    println("Found ${imdbResult.getOrThrow().size} subtitles using IMDB ID: $imdbId")
                    return imdbResult
                } else {
                    println("IMDB ID episode search returned no results, falling back to query search")
                }
            } catch (e: Exception) {
                println("IMDB ID episode search failed: ${e.message}, falling back to query search")
            }
        }
        
        // Fallback to original query-based search
        val formattedSeason = "s${seasonNumber.toString().padStart(2, '0')}"
        val formattedEpisode = "e${episodeNumber.toString().padStart(2, '0')}"
        val episodeTag = "$formattedSeason$formattedEpisode"
        
        val searchVariations = generateEpisodeSearchVariations(showTitle, episodeTag, showYear)
        var bestResults: List<SubtitleResult> = emptyList()
        var highestMatchScore = 0.0
        
        for (queryString in searchVariations) {
            try {
                val urlBuilder = "${baseUrl}subtitles".toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("query", queryString)
                    ?.addQueryParameter("languages", "en")
                    ?.addQueryParameter("ai_translated", "include")
                    ?.addQueryParameter("order_by", "download_count")
                
                if (showYear != null) {
                    urlBuilder?.addQueryParameter("year", showYear.toString())
                }
                
                val url = urlBuilder?.build()?.toString()
                    ?: throw Exception("Failed to build request URL")
                
                println("OpenSubtitles TV Episode API Request:")
                println("  URL: $url")
                println("  Query variation: $queryString")
                println("  Show: $showTitle")
                println("  Season: $seasonNumber, Episode: $episodeNumber")
                println("  Year: $showYear")
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("User-Agent", "PureStream/1.0")
                    .addHeader("Api-Key", apiKey)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val subtitlesList = parseFlexibleResponse(responseBody)
                        
                        if (subtitlesList.isNotEmpty()) {
                            val qualityFilteredSubtitles = subtitlesList.filter { 
                                it.attributes.downloadCount >= 5
                            }
                            
                            val normalizedShowTitle = normalizeTitle(showTitle)
                            val resultsWithScores = qualityFilteredSubtitles.map { subtitle ->
                                val releaseName = subtitle.attributes.release ?: ""
                                val titleSimilarity = calculateSimilarity(
                                    normalizedShowTitle,
                                    normalizeTitle(releaseName.substringBefore(episodeTag))
                                )
                                val episodeMatch = if (releaseName.contains(episodeTag, ignoreCase = true)) 1.0 else 0.0
                                val qualityScore = calculateSubtitleQualityScore(subtitle)
                                val combinedScore = titleSimilarity * 30 + episodeMatch * 70 + qualityScore
                                Pair(subtitle, combinedScore)
                            }
                            
                            val avgScore = resultsWithScores.map { it.second }.average()
                            if (avgScore > highestMatchScore) {
                                highestMatchScore = avgScore
                                bestResults = resultsWithScores
                                    .sortedByDescending { it.second }
                                    .map { it.first }
                            }
                        }
                    }
                }
                
                delay(100)
            } catch (e: Exception) {
                println("Episode search variation failed for '$queryString': ${e.message}")
                continue
            }
        }
        
        return if (bestResults.isNotEmpty()) {
            println("Found ${bestResults.size} quality subtitles for '$showTitle' $episodeTag (match score: $highestMatchScore)")
            Result.success(bestResults)
        } else {
            Result.failure(Exception("No suitable subtitles found for $showTitle $episodeTag"))
        }
    }
    
    private suspend fun searchMovieByImdbId(imdbId: String, movieTitle: String): Result<List<SubtitleResult>> {
        try {
            val urlBuilder = "${baseUrl}subtitles".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("imdb_id", imdbId)
                ?.addQueryParameter("languages", "en")
                ?.addQueryParameter("ai_translated", "include")
                ?.addQueryParameter("order_by", "download_count")
            
            val url = urlBuilder?.build()?.toString()
                ?: throw Exception("Failed to build IMDB request URL")
            
            println("OpenSubtitles IMDB API Request:")
            println("  URL: $url")
            println("  IMDB ID: $imdbId")
            println("  Movie: $movieTitle")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "PureStream/1.0")
                .addHeader("Api-Key", apiKey)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val subtitlesList = parseFlexibleResponse(responseBody)
                    
                    if (subtitlesList.isNotEmpty()) {
                        // For IMDB-based searches, we can be less strict on quality filters
                        // since the match is already precise
                        val qualityFilteredSubtitles = subtitlesList.filter { 
                            it.attributes.downloadCount >= 5 
                        }
                        
                        val sortedResults = qualityFilteredSubtitles.sortedByDescending { subtitle ->
                            calculateSubtitleQualityScore(subtitle)
                        }
                        
                        return Result.success(sortedResults)
                    }
                }
            } else {
                throw Exception("IMDB search failed: ${response.code} - ${response.message}")
            }
            
            return Result.success(emptyList())
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    private suspend fun searchEpisodeByImdbId(
        imdbId: String, 
        seasonNumber: Int, 
        episodeNumber: Int, 
        showTitle: String
    ): Result<List<SubtitleResult>> {
        try {
            val urlBuilder = "${baseUrl}subtitles".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("imdb_id", imdbId)
                ?.addQueryParameter("season_number", seasonNumber.toString())
                ?.addQueryParameter("episode_number", episodeNumber.toString())
                ?.addQueryParameter("languages", "en")
                ?.addQueryParameter("ai_translated", "include")
                ?.addQueryParameter("order_by", "download_count")
            
            val url = urlBuilder?.build()?.toString()
                ?: throw Exception("Failed to build IMDB episode request URL")
            
            println("OpenSubtitles IMDB Episode API Request:")
            println("  URL: $url")
            println("  IMDB ID: $imdbId")
            println("  Show: $showTitle")
            println("  Season: $seasonNumber, Episode: $episodeNumber")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "PureStream/1.0")
                .addHeader("Api-Key", apiKey)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val subtitlesList = parseFlexibleResponse(responseBody)
                    
                    if (subtitlesList.isNotEmpty()) {
                        // For IMDB-based searches, we can be less strict on quality filters
                        // since the match is already precise
                        val qualityFilteredSubtitles = subtitlesList.filter { 
                            it.attributes.downloadCount >= 3 
                        }
                        
                        val sortedResults = qualityFilteredSubtitles.sortedByDescending { subtitle ->
                            calculateSubtitleQualityScore(subtitle)
                        }
                        
                        return Result.success(sortedResults)
                    }
                }
            } else {
                throw Exception("IMDB episode search failed: ${response.code} - ${response.message}")
            }
            
            return Result.success(emptyList())
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    private fun generateEpisodeSearchVariations(showTitle: String, episodeTag: String, year: Int? = null): List<String> {
        val variations = mutableListOf<String>()
        val cleanTitle = normalizeTitle(showTitle)
        val dotTitle = showTitle.replace(Regex("[\\s:]+"), ".").lowercase()
        
        variations.add("$dotTitle.$episodeTag")
        variations.add("$cleanTitle $episodeTag")
        variations.add("${showTitle.lowercase()}.$episodeTag")
        
        if (year != null) {
            variations.add("$dotTitle.$year.$episodeTag")
            variations.add("$cleanTitle $year $episodeTag")
        }
        
        return variations.distinct()
    }
    
    suspend fun searchAndAnalyzeWithFallback(
        movieTitle: String,
        imdbId: String? = null,
        year: Int? = null,
        filterLevel: ProfanityFilterLevel,
        fallbackToLowerQuality: Boolean = true
    ): Result<SubtitleAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            val searchResult = searchSubtitlesForMovie(movieTitle, imdbId, year)
            
            if (searchResult.isFailure) {
                return@withContext Result.failure(searchResult.exceptionOrNull() ?: Exception("Search failed"))
            }
            
            val subtitles = searchResult.getOrThrow()
            
            if (subtitles.isEmpty()) {
                return@withContext Result.failure(Exception("No subtitles available for '$movieTitle'"))
            }
            
            var bestSubtitle = subtitles.first()
            if (fallbackToLowerQuality && calculateSubtitleQualityScore(bestSubtitle) < 10.0) {
                val alternativeResults = searchWithRelaxedCriteria(movieTitle, year)
                if (alternativeResults.isSuccess && alternativeResults.getOrThrow().isNotEmpty()) {
                    bestSubtitle = alternativeResults.getOrThrow().first()
                }
            }
            
            return@withContext downloadAndAnalyzeSubtitle(bestSubtitle, movieTitle, null, filterLevel)
            
        } catch (e: Exception) {
            Result.failure(Exception("Failed to search and analyze subtitles for '$movieTitle': ${e.message}", e))
        }
    }
    
    suspend fun searchEpisodeWithFallback(
        showTitle: String,
        seasonNumber: Int,
        episodeNumber: Int,
        showYear: Int? = null,
        filterLevel: ProfanityFilterLevel,
        fallbackToLowerQuality: Boolean = true
    ): Result<SubtitleAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            val searchResult = searchSubtitlesForEpisode(showTitle, seasonNumber, episodeNumber, showYear)
            
            if (searchResult.isFailure) {
                return@withContext Result.failure(searchResult.exceptionOrNull() ?: Exception("Episode search failed"))
            }
            
            val subtitles = searchResult.getOrThrow()
            
            if (subtitles.isEmpty()) {
                return@withContext Result.failure(Exception("No subtitles available for '$showTitle' S${seasonNumber}E${episodeNumber}"))
            }
            
            var bestSubtitle = subtitles.first()
            val episodeInfo = "S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')}"
            
            if (fallbackToLowerQuality && calculateSubtitleQualityScore(bestSubtitle) < 5.0) {
                val alternativeResults = searchEpisodeWithRelaxedCriteria(showTitle, seasonNumber, episodeNumber, showYear)
                if (alternativeResults.isSuccess && alternativeResults.getOrThrow().isNotEmpty()) {
                    bestSubtitle = alternativeResults.getOrThrow().first()
                }
            }
            
            return@withContext downloadAndAnalyzeSubtitle(bestSubtitle, showTitle, episodeInfo, filterLevel)
            
        } catch (e: Exception) {
            Result.failure(Exception("Failed to search and analyze episode subtitles: ${e.message}", e))
        }
    }
    
    private suspend fun searchWithRelaxedCriteria(
        movieTitle: String,
        year: Int? = null
    ): Result<List<SubtitleResult>> {
        val relaxedVariations = listOf(
            movieTitle.split(" ").joinToString(".").lowercase(),
            movieTitle.replace(Regex("[^a-zA-Z0-9\\s]"), "").lowercase(),
            normalizeTitle(movieTitle)
        ).distinct()
        
        for (query in relaxedVariations) {
            try {
                val urlBuilder = "${baseUrl}subtitles".toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("query", query)
                    ?.addQueryParameter("languages", "en")
                    ?.addQueryParameter("ai_translated", "include")
                
                val response = client.newCall(
                    Request.Builder()
                        .url(urlBuilder?.build()?.toString() ?: continue)
                        .addHeader("User-Agent", "PureStream/1.0")
                        .addHeader("Api-Key", apiKey)
                        .build()
                ).execute()
                
                if (response.isSuccessful) {
                    val subtitles = parseFlexibleResponse(response.body?.string() ?: "")
                    if (subtitles.isNotEmpty()) {
                        return Result.success(subtitles.filter { it.attributes.downloadCount >= 1 })
                    }
                }
                
                delay(100)
            } catch (e: Exception) {
                continue
            }
        }
        
        return Result.failure(Exception("No subtitles found with relaxed criteria"))
    }
    
    private suspend fun searchEpisodeWithRelaxedCriteria(
        showTitle: String,
        seasonNumber: Int,
        episodeNumber: Int,
        showYear: Int? = null
    ): Result<List<SubtitleResult>> {
        val episodeTag = "s${seasonNumber.toString().padStart(2, '0')}e${episodeNumber.toString().padStart(2, '0')}"
        val relaxedVariations = listOf(
            "${showTitle.lowercase().replace(Regex("\\s+"), ".")}.$episodeTag",
            "${normalizeTitle(showTitle)} $episodeTag",
            "${showTitle.split(" ").first().lowercase()}.$episodeTag"
        ).distinct()
        
        for (query in relaxedVariations) {
            try {
                val urlBuilder = "${baseUrl}subtitles".toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("query", query)
                    ?.addQueryParameter("languages", "en")
                
                val response = client.newCall(
                    Request.Builder()
                        .url(urlBuilder?.build()?.toString() ?: continue)
                        .addHeader("User-Agent", "PureStream/1.0")
                        .addHeader("Api-Key", apiKey)
                        .build()
                ).execute()
                
                if (response.isSuccessful) {
                    val subtitles = parseFlexibleResponse(response.body?.string() ?: "")
                    if (subtitles.isNotEmpty()) {
                        return Result.success(subtitles.filter { it.attributes.downloadCount >= 1 })
                    }
                }
                
                delay(100)
            } catch (e: Exception) {
                continue
            }
        }
        
        return Result.failure(Exception("No episode subtitles found with relaxed criteria"))
    }
    
    suspend fun downloadAndAnalyzeSubtitle(
        subtitleResult: SubtitleResult,
        movieTitle: String,
        episodeInfo: String? = null,
        filterLevel: ProfanityFilterLevel
    ): Result<SubtitleAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cachedFile = getCachedSubtitleFile(movieTitle, episodeInfo)
            if (cachedFile != null) {
                println("Using cached subtitle file for analysis")
                val cachedContent = cachedFile.readText()
                val analysisResult = analyzeSubtitleContent(
                    subtitleContent = cachedContent,
                    movieTitle = movieTitle,
                    episodeInfo = episodeInfo,
                    fileName = cachedFile.name,
                    filterLevel = filterLevel
                )
                return@withContext Result.success(analysisResult)
            }
            
            // Try each available subtitle file until one works
            val subtitleFiles = subtitleResult.attributes.files
            if (subtitleFiles.isEmpty()) {
                return@withContext Result.failure(Exception("No subtitle files available"))
            }
            
            var lastException: Exception? = null
            
            for (subtitleFile in subtitleFiles) {
                try {
                    val result = downloadSingleSubtitleFile(subtitleFile, movieTitle, episodeInfo, filterLevel)
                    if (result.isSuccess) {
                        return@withContext result
                    } else {
                        lastException = result.exceptionOrNull() as? Exception
                    }
                } catch (e: Exception) {
                    lastException = e
                    continue // Try next file
                }
            }
            
            return@withContext Result.failure(lastException ?: Exception("All subtitle files failed to download"))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun downloadSingleSubtitleFile(
        subtitleFile: com.purestream.data.model.SubtitleFile,
        movieTitle: String,
        episodeInfo: String?,
        filterLevel: ProfanityFilterLevel
    ): Result<SubtitleAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            // Get download link with retry mechanism
            val downloadInfo = executeWithRetry(operation = {
                getDownloadLinkWithRetry(subtitleFile.fileId)
            })
            
            // Download the actual subtitle file with retry mechanism
            val subtitleContent = executeWithRetry(operation = {
                downloadSubtitleFileWithRetry(downloadInfo.link)
            })
            
            // Log subtitle content info
            println("Downloaded subtitle content length: ${subtitleContent.length}")
            println("First 500 characters of subtitle: ${subtitleContent.take(500)}")
            
            // Save to cache for 24-hour reuse
            saveSubtitleToCache(subtitleContent, movieTitle, episodeInfo)
            
            // Parse and analyze the subtitle content
            val analysisResult = analyzeSubtitleContent(
                subtitleContent = subtitleContent,
                movieTitle = movieTitle,
                episodeInfo = episodeInfo,
                fileName = subtitleFile.fileName,
                filterLevel = filterLevel
            )
            
            Result.success(analysisResult)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Helper function to get download link with retryable network call
     */
    private suspend fun getDownloadLinkWithRetry(fileId: Int): OpenSubtitlesDownloadResponse {
        val downloadRequestJson = "{\n  \"file_id\": $fileId\n}"
        
        val downloadRequest = Request.Builder()
            .url("${baseUrl}download")
            .post(downloadRequestJson.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", "PureStream/1.0")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Api-Key", apiKey)
            .build()
        
        val downloadResponse = client.newCall(downloadRequest).execute()
        
        if (!downloadResponse.isSuccessful) {
            val errorBody = downloadResponse.body?.string() ?: "No error details"
            throw Exception("Failed to get download link: ${downloadResponse.code} - ${downloadResponse.message}. Error: $errorBody")
        }
        
        val downloadResponseBody = downloadResponse.body?.string()
            ?: throw Exception("Empty download response")
        
        return gson.fromJson(downloadResponseBody, OpenSubtitlesDownloadResponse::class.java)
    }
    
    /**
     * Helper function to download subtitle file content with retryable network call
     */
    private suspend fun downloadSubtitleFileWithRetry(downloadUrl: String): String {
        val fileRequest = Request.Builder()
            .url(downloadUrl)
            .get()
            .addHeader("User-Agent", "PureStream/1.0")
            .build()
            
        val fileResponse = client.newCall(fileRequest).execute()
        
        if (!fileResponse.isSuccessful) {
            throw Exception("Failed to download subtitle file: ${fileResponse.code} - ${fileResponse.message}")
        }
        
        return fileResponse.body?.string() ?: throw Exception("Empty subtitle content")
    }
    
    private fun analyzeSubtitleContent(
        subtitleContent: String,
        movieTitle: String,
        episodeInfo: String?,
        fileName: String,
        filterLevel: ProfanityFilterLevel
    ): SubtitleAnalysisResult {
        try {
            println("Starting subtitle analysis for: $movieTitle")
            
            // Parse SRT content to extract dialogue text
            println("Parsing SRT content...")
            val dialogueText = parseSrtContent(subtitleContent)
            println("Parsed dialogue text length: ${dialogueText.length}")
            println("First 200 characters of dialogue: ${dialogueText.take(200)}")
            
            // Analyze profanity in the dialogue
            println("Analyzing profanity...")
            val filterResult = profanityFilter.filterText(dialogueText, filterLevel)
            val profanityLevel = profanityFilter.analyzeProfanityLevel(dialogueText)
            
            // Create filtered subtitle file if profanity was found
            if (filterResult.hasProfanity && filterLevel != ProfanityFilterLevel.NONE) {
                println("Creating filtered subtitle file...")
                val filteredSubtitleContent = createFilteredSubtitle(subtitleContent, filterLevel)
                saveFilteredSubtitleToCache(filteredSubtitleContent, movieTitle, episodeInfo)
            }
            
            // Count total words for statistics
            println("Counting words...")
            val totalWords = if (dialogueText.isBlank()) {
                0
            } else {
                dialogueText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
            }
            val profanityWords = filterResult.detectedWords.size
            val profanityPercentage = if (totalWords > 0) {
                (profanityWords.toFloat() / totalWords.toFloat()) * 100f
            } else {
                0f
            }
            
            println("Analysis complete: $totalWords total words, $profanityWords profanity words")
            
            return SubtitleAnalysisResult(
                movieTitle = movieTitle,
                episodeInfo = episodeInfo,
                subtitleFileName = fileName,
                profanityLevel = profanityLevel,
                detectedWords = filterResult.detectedWords,
                totalWordsCount = totalWords,
                profanityWordsCount = profanityWords,
                profanityPercentage = profanityPercentage
            )
        } catch (e: Exception) {
            println("Error during subtitle analysis: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    private fun parseSrtContent(srtContent: String): String {
        try {
            println("Starting SRT parsing...")
            val dialogue = StringBuilder()
            val reader = BufferedReader(StringReader(srtContent))
            var line: String?
            var lineCount = 0
            
            while (reader.readLine().also { line = it } != null) {
                lineCount++
                line?.let { currentLine ->
                    try {
                        when {
                            // Skip subtitle numbers (just digits)
                            currentLine.matches(Regex("^\\d+$")) -> {
                                // Just digits - subtitle number
                            }
                            // Skip timestamp lines
                            currentLine.contains("-->") -> {
                                // Timestamp line
                            }
                            // Empty line indicates end of subtitle block
                            currentLine.trim().isEmpty() -> {
                                if (dialogue.isNotEmpty() && !dialogue.endsWith(" ")) {
                                    dialogue.append(" ")
                                }
                            }
                            // This is dialogue text
                            else -> {
                                // Remove HTML tags and formatting safely
                                var cleanLine = currentLine
                                
                                // Remove HTML tags - simple approach
                                while (cleanLine.contains("<") && cleanLine.contains(">")) {
                                    val start = cleanLine.indexOf("<")
                                    val end = cleanLine.indexOf(">", start)
                                    if (end > start) {
                                        cleanLine = cleanLine.substring(0, start) + cleanLine.substring(end + 1)
                                    } else {
                                        break
                                    }
                                }
                                
                                // Remove curly braces content - simple approach
                                while (cleanLine.contains("{") && cleanLine.contains("}")) {
                                    val start = cleanLine.indexOf("{")
                                    val end = cleanLine.indexOf("}", start)
                                    if (end > start) {
                                        cleanLine = cleanLine.substring(0, start) + cleanLine.substring(end + 1)
                                    } else {
                                        break
                                    }
                                }
                                cleanLine = cleanLine.trim()
                                
                                if (cleanLine.isNotEmpty()) {
                                    dialogue.append(cleanLine).append(" ")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("Error processing line $lineCount: '$currentLine' - ${e.message}")
                        // Continue processing other lines
                    }
                }
            }
            
            val result = dialogue.toString().trim()
            println("SRT parsing complete. Extracted ${result.length} characters of dialogue")
            return result
            
        } catch (e: Exception) {
            println("Error during SRT parsing: ${e.message}")
            e.printStackTrace()
            return ""
        }
    }
    
    private fun createFilteredSubtitle(originalContent: String, filterLevel: ProfanityFilterLevel): String {
        try {
            println("Creating filtered subtitle with level: $filterLevel")
            val lines = originalContent.lines()
            val filteredLines = mutableListOf<String>()
            
            for (line in lines) {
                // Check if this line contains dialogue (not numbers or timestamps)
                if (line.trim().isNotEmpty() && 
                    !line.matches(Regex("^\\d+$")) && 
                    !line.contains("-->")) {
                    
                    // Filter the dialogue line
                    val filterResult = profanityFilter.filterText(line, filterLevel)
                    filteredLines.add(filterResult.filteredText)
                } else {
                    // Keep numbers, timestamps, and empty lines as-is
                    filteredLines.add(line)
                }
            }
            
            val result = filteredLines.joinToString("\n")
            println("Filtered subtitle created, length: ${result.length}")
            return result
            
        } catch (e: Exception) {
            println("Error creating filtered subtitle: ${e.message}")
            return originalContent // Return original if filtering fails
        }
    }
    
    private fun saveFilteredSubtitleToCache(filteredContent: String, movieTitle: String, episodeInfo: String?): java.io.File? {
        return try {
            val cacheDir = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp")
            cacheDir.mkdirs()
            val fileName = getCacheFileName(movieTitle, episodeInfo).replace(".srt", "_filtered.srt")
            val cacheFile = java.io.File(cacheDir, fileName)
            cacheFile.writeText(filteredContent)
            println("Filtered subtitle saved to cache: ${cacheFile.absolutePath}")
            cacheFile
        } catch (e: Exception) {
            println("Failed to save filtered subtitle to cache: ${e.message}")
            null
        }
    }
    
    // New methods for persistent analysis
    suspend fun hasAnalysisForContent(contentId: String, filterLevel: ProfanityFilterLevel): Boolean {
        return subtitleAnalysisRepository?.hasAnalysisForFilterLevel(contentId, filterLevel) ?: false
    }
    
    suspend fun getExistingAnalysis(contentId: String, filterLevel: ProfanityFilterLevel): SubtitleAnalysisResult? {
        val analysisEntity = subtitleAnalysisRepository?.getAnalysisForContent(contentId, filterLevel)
            ?: return null

        // Verify the subtitle file actually exists on this device
        // (database entry might be synced from another device via Android Auto Backup)
        val filePath = analysisEntity.filteredSubtitlePath
        if (filePath != null) {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                android.util.Log.w("OpenSubtitlesRepository",
                    "Analysis exists in database but file missing (likely synced from another device): $filePath")
                return null
            }
        }

        // Convert database entity back to analysis result using real stored data
        val detectedWords = try {
            gson.fromJson(analysisEntity.detectedWords, Array<String>::class.java).toList()
        } catch (e: Exception) {
            emptyList<String>()
        }

        return SubtitleAnalysisResult(
            movieTitle = analysisEntity.contentTitle,
            episodeInfo = analysisEntity.showTitle?.let { showTitle ->
                "${showTitle} S${analysisEntity.seasonNumber}E${analysisEntity.episodeNumber}"
            },
            subtitleFileName = analysisEntity.subtitleFileName,
            profanityLevel = analysisEntity.profanityLevel,
            detectedWords = detectedWords,
            totalWordsCount = analysisEntity.totalWordsCount,
            profanityWordsCount = analysisEntity.profanityWordsCount,
            profanityPercentage = analysisEntity.profanityPercentage
        )
    }
    
    suspend fun getExistingFilterLevels(contentId: String): List<ProfanityFilterLevel> {
        return subtitleAnalysisRepository?.getExistingFilterLevelsForContent(contentId) ?: emptyList()
    }
    
    suspend fun getFilteredSubtitleContent(contentId: String, filterLevel: ProfanityFilterLevel): String? {
        return subtitleAnalysisRepository?.getFilteredSubtitleContent(contentId, filterLevel)
    }
    
    suspend fun downloadAndAnalyzeSubtitlePersistent(
        subtitleResult: SubtitleResult,
        contentId: String,
        contentType: String,
        contentTitle: String,
        showTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        movieTitle: String,
        episodeInfo: String? = null,
        filterLevel: ProfanityFilterLevel
    ): Result<SubtitleAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("OpenSubtitlesRepository", "Starting persistent analysis for contentId: $contentId, filterLevel: $filterLevel")
            
            // Check if analysis already exists for this filter level
            if (hasAnalysisForContent(contentId, filterLevel)) {
                android.util.Log.d("OpenSubtitlesRepository", "Found existing analysis, returning it")
                val existingAnalysis = getExistingAnalysis(contentId, filterLevel)
                if (existingAnalysis != null) {
                    return@withContext Result.success(existingAnalysis)
                }
            }
            
            android.util.Log.d("OpenSubtitlesRepository", "No existing analysis, proceeding with download and analysis")
            
            // Perform the standard download and analysis first
            val standardResult = downloadAndAnalyzeSubtitle(subtitleResult, movieTitle, episodeInfo, filterLevel)
            
            if (standardResult.isSuccess && subtitleAnalysisRepository != null) {
                android.util.Log.d("OpenSubtitlesRepository", "Standard analysis successful, saving to persistent storage")
                val analysis = standardResult.getOrThrow()
                
                // Get the actual downloaded subtitle content for detailed analysis
                val cachedFile = getCachedSubtitleFile(movieTitle, episodeInfo)
                val originalContent = cachedFile?.readText() ?: ""
                
                if (originalContent.isNotEmpty()) {
                    // Create detailed analysis using the SubtitleAnalyzer with synchronization
                    val subtitleAnalyzer = com.purestream.profanity.SubtitleAnalyzer(profanityFilter)
                    
                    // Apply basic synchronization and validation
                    val syncOptions = com.purestream.profanity.SubtitleAnalyzer.SyncOptions(
                        validateTimings = true,
                        repairMalformed = true
                    )
                    
                    val detailedAnalysis = subtitleAnalyzer.analyzeSubtitles(originalContent, filterLevel, syncOptions)
                    val filteredContent = subtitleAnalyzer.generateFilteredSubtitleFile(detailedAnalysis)
                    
                    // Save to persistent storage using the detailed analysis data
                    val savedPath = subtitleAnalysisRepository.saveAnalysisResult(
                        contentId = contentId,
                        contentType = contentType,
                        contentTitle = contentTitle,
                        showTitle = showTitle,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        filterLevel = filterLevel,
                        analysisResult = detailedAnalysis,
                        subtitleFileName = cachedFile?.name ?: "subtitle.srt",
                        filteredSubtitleContent = filteredContent
                    )
                    
                    android.util.Log.d("OpenSubtitlesRepository", "Persistent storage result: ${if (savedPath != null) "SUCCESS" else "FAILED"}")
                } else {
                    android.util.Log.w("OpenSubtitlesRepository", "No subtitle content found for detailed analysis")
                }
                
                // Return the standard analysis result for UI compatibility
                return@withContext standardResult
            } else {
                android.util.Log.e("OpenSubtitlesRepository", "Standard analysis failed: ${standardResult.exceptionOrNull()?.message}")
                return@withContext standardResult
            }
        } catch (e: Exception) {
            android.util.Log.e("OpenSubtitlesRepository", "Exception in persistent analysis: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private fun convertNewToOldAnalysisResult(
        newResult: com.purestream.profanity.SubtitleAnalysisResult,
        contentTitle: String,
        episodeInfo: String?,
        fileName: String
    ): SubtitleAnalysisResult {
        // Convert from the detailed SubtitleAnalyzer result to the old OpenSubtitles format
        val allDetectedWords = newResult.profanityEvents.flatMap { it.detectedWords }.distinct()
        val totalWords = newResult.filteredSubtitles.sumOf { entry ->
            entry.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        }
        val profanityPercentage = if (totalWords > 0) {
            (newResult.totalProfanityCount.toFloat() / totalWords.toFloat()) * 100f
        } else {
            0f
        }
        
        // Determine profanity level based on the analysis
        val profanityLevel = when {
            profanityPercentage >= 15f -> ProfanityLevel.HIGH
            profanityPercentage >= 8f -> ProfanityLevel.MEDIUM
            profanityPercentage >= 2f -> ProfanityLevel.LOW
            else -> ProfanityLevel.NONE
        }
        
        return SubtitleAnalysisResult(
            movieTitle = contentTitle,
            episodeInfo = episodeInfo,
            subtitleFileName = fileName,
            profanityLevel = profanityLevel,
            detectedWords = allDetectedWords,
            totalWordsCount = totalWords,
            profanityWordsCount = newResult.totalProfanityCount,
            profanityPercentage = profanityPercentage
        )
    }
    
    private fun convertToNewAnalysisResult(oldResult: SubtitleAnalysisResult): com.purestream.profanity.SubtitleAnalysisResult {
        // This method is deprecated - we now use real analysis data from SubtitleAnalyzer
        // Keeping for backward compatibility only
        return com.purestream.profanity.SubtitleAnalysisResult(
            filteredSubtitles = emptyList(),
            profanityEvents = emptyList(),
            totalProfanityCount = oldResult.profanityWordsCount
        )
    }
    
    /**
     * Downloads subtitle content once and processes it for all specified filter levels.
     * This ensures consistent detected words across all filter levels.
     */
    suspend fun downloadAndAnalyzeSubtitleForAllLevels(
        subtitleResult: SubtitleResult,
        contentId: String,
        contentType: String,
        contentTitle: String,
        showTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        movieTitle: String,
        episodeInfo: String? = null,
        filterLevels: List<ProfanityFilterLevel>
    ): Result<Map<ProfanityFilterLevel, SubtitleAnalysisResult>> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("OpenSubtitlesRepository", "Starting multi-level analysis for contentId: $contentId, filterLevels: $filterLevels")
            
            // Check if we already have all requested analyses
            val existingAnalyses = mutableMapOf<ProfanityFilterLevel, SubtitleAnalysisResult>()
            val missingLevels = mutableListOf<ProfanityFilterLevel>()
            
            for (filterLevel in filterLevels) {
                if (hasAnalysisForContent(contentId, filterLevel)) {
                    val existingAnalysis = getExistingAnalysis(contentId, filterLevel)
                    if (existingAnalysis != null) {
                        existingAnalyses[filterLevel] = existingAnalysis
                        android.util.Log.d("OpenSubtitlesRepository", "Found existing analysis for $filterLevel")
                    } else {
                        missingLevels.add(filterLevel)
                    }
                } else {
                    missingLevels.add(filterLevel)
                }
            }
            
            if (missingLevels.isEmpty()) {
                android.util.Log.d("OpenSubtitlesRepository", "All filter levels already analyzed, returning existing analyses")
                return@withContext Result.success(existingAnalyses)
            }
            
            android.util.Log.d("OpenSubtitlesRepository", "Need to analyze ${missingLevels.size} missing filter levels: $missingLevels")
            
            // Download subtitle content once
            val subtitleContent = downloadSubtitleContent(subtitleResult)
            if (subtitleContent == null) {
                return@withContext Result.failure(Exception("Failed to download subtitle content"))
            }
            
            android.util.Log.d("OpenSubtitlesRepository", "Successfully downloaded subtitle content (${subtitleContent.length} characters)")
            
            // Cache the subtitle content for future use
            val cacheFile = cacheSubtitleContent(movieTitle, episodeInfo, subtitleContent)
            val fileName = cacheFile?.name ?: "unknown.srt"
            
            // Process the same subtitle content for all missing filter levels
            val newAnalyses = mutableMapOf<ProfanityFilterLevel, SubtitleAnalysisResult>()
            
            for (filterLevel in missingLevels) {
                android.util.Log.d("OpenSubtitlesRepository", "Processing subtitle for filter level: $filterLevel")
                
                try {
                    val analysisResult = analyzeSubtitleContent(
                        subtitleContent = subtitleContent,
                        movieTitle = movieTitle,
                        episodeInfo = episodeInfo,
                        fileName = fileName,
                        filterLevel = filterLevel
                    )
                    
                    newAnalyses[filterLevel] = analysisResult
                    android.util.Log.d("OpenSubtitlesRepository", "Successfully analyzed $filterLevel: ${analysisResult.profanityWordsCount} profanity words")
                    
                    // Save to persistent storage using the detailed analysis pipeline
                    try {
                        if (subtitleAnalysisRepository != null) {
                            val subtitleAnalyzer = com.purestream.profanity.SubtitleAnalyzer(profanityFilter)
                            val syncOptions = com.purestream.profanity.SubtitleAnalyzer.SyncOptions(
                                validateTimings = true,
                                repairMalformed = true
                            )
                            
                            val detailedAnalysis = subtitleAnalyzer.analyzeSubtitles(subtitleContent, filterLevel, syncOptions)
                            val filteredContent = subtitleAnalyzer.generateFilteredSubtitleFile(detailedAnalysis)
                            
                            val savedPath = subtitleAnalysisRepository.saveAnalysisResult(
                                contentId = contentId,
                                contentType = contentType,
                                contentTitle = contentTitle,
                                showTitle = showTitle,
                                seasonNumber = seasonNumber,
                                episodeNumber = episodeNumber,
                                filterLevel = filterLevel,
                                analysisResult = detailedAnalysis,
                                subtitleFileName = fileName,
                                filteredSubtitleContent = filteredContent
                            )
                            
                            android.util.Log.d("OpenSubtitlesRepository", "Successfully saved analysis for $filterLevel to database: $savedPath")
                        } else {
                            android.util.Log.w("OpenSubtitlesRepository", "SubtitleAnalysisRepository is null, cannot save to database")
                        }
                    } catch (saveException: Exception) {
                        android.util.Log.e("OpenSubtitlesRepository", "Failed to save analysis for $filterLevel: ${saveException.message}", saveException)
                        // Continue with other filter levels even if saving fails
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("OpenSubtitlesRepository", "Failed to analyze filter level $filterLevel: ${e.message}", e)
                    // Continue with other filter levels
                }
            }
            
            // Combine existing and new analyses
            val allAnalyses = existingAnalyses + newAnalyses
            
            android.util.Log.d("OpenSubtitlesRepository", "Multi-level analysis complete. Processed ${allAnalyses.size} filter levels")
            
            if (allAnalyses.isEmpty()) {
                return@withContext Result.failure(Exception("No successful analyses for any filter level"))
            }
            
            return@withContext Result.success(allAnalyses)
            
        } catch (e: Exception) {
            android.util.Log.e("OpenSubtitlesRepository", "Error in multi-level subtitle analysis", e)
            Result.failure(Exception("Failed to analyze subtitles for multiple filter levels: ${e.message}", e))
        }
    }
    
    /**
     * Downloads the raw subtitle content from OpenSubtitles API
     */
    private suspend fun downloadSubtitleContent(subtitleResult: SubtitleResult): String? {
        try {
            val subtitleFiles = subtitleResult.attributes.files
            if (subtitleFiles.isEmpty()) {
                android.util.Log.w("OpenSubtitlesRepository", "No subtitle files available")
                return null
            }
            
            // Try each subtitle file until one works
            for (subtitleFile in subtitleFiles) {
                try {
                    android.util.Log.d("OpenSubtitlesRepository", "Attempting to download subtitle file: ${subtitleFile.fileId}")
                    
                    // Get download link with retry mechanism
                    val downloadInfo = executeWithRetry(operation = {
                        getDownloadLinkWithRetry(subtitleFile.fileId)
                    })
                    
                    android.util.Log.d("OpenSubtitlesRepository", "Got download link, downloading subtitle content...")
                    
                    // Download the actual subtitle file with retry mechanism
                    val subtitleContent = executeWithRetry(operation = {
                        downloadSubtitleFileWithRetry(downloadInfo.link)
                    })
                    
                    if (subtitleContent.isNotEmpty()) {
                        android.util.Log.d("OpenSubtitlesRepository", "Successfully downloaded subtitle content (${subtitleContent.length} characters)")
                        return subtitleContent
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.w("OpenSubtitlesRepository", "Error downloading subtitle file ${subtitleFile.fileId} with retries: ${e.message}")
                    continue // Try next file
                }
            }
            
            android.util.Log.e("OpenSubtitlesRepository", "Failed to download content from any subtitle file")
            return null
            
        } catch (e: Exception) {
            android.util.Log.e("OpenSubtitlesRepository", "Error downloading subtitle content", e)
            return null
        }
    }
    
    /**
     * Caches subtitle content to disk for reuse
     */
    private suspend fun cacheSubtitleContent(movieTitle: String, episodeInfo: String?, content: String): java.io.File? {
        return try {
            val cacheDir = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp")
            val cacheFile = java.io.File(cacheDir, getCacheFileName(movieTitle, episodeInfo))
            cacheFile.writeText(content)
            android.util.Log.d("OpenSubtitlesRepository", "Cached subtitle content to: ${cacheFile.absolutePath}")
            cacheFile
        } catch (e: Exception) {
            android.util.Log.e("OpenSubtitlesRepository", "Error caching subtitle content", e)
            null
        }
    }
}