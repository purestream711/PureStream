package com.purestream.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.purestream.data.api.ApiClient
import com.purestream.data.api.PlexApiService
import com.purestream.data.api.PlexServer
import com.purestream.data.model.*
import com.purestream.data.database.AppDatabase
import com.purestream.data.database.dao.*
import com.purestream.data.database.entities.CacheMetadataEntity
import com.purestream.data.cache.CacheConfig
import com.purestream.data.cache.*
import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream

class PlexRepository(private val context: Context) {

    companion object {
        private const val TAG = "PlexRepository"
    }

    private val plexApiService = ApiClient.plexApiService
    private var serverApiService: PlexApiService? = null
    private var currentToken: String? = null
    private var currentServerUrl: String? = null

    // Auth repository to check stored token
    private val authRepository = PlexAuthRepository(context)

    // Database and DAOs for caching
    private val database = AppDatabase.getDatabase(context)
    private val movieCacheDao: MovieCacheDao = database.movieCacheDao()
    private val tvShowCacheDao: TvShowCacheDao = database.tvShowCacheDao()
    private val libraryCacheDao: LibraryCacheDao = database.libraryCacheDao()
    private val cacheMetadataDao: CacheMetadataDao = database.cacheMetadataDao()

    // Connection fallback management
    private var primaryConnection: ServerConnectionInfo? = null
    private var fallbackConnection: ServerConnectionInfo? = null
    private var isUsingFallback: Boolean = false

    fun setServerConnection(serverUrl: String, token: String) {
        currentServerUrl = serverUrl
        currentToken = token
        serverApiService = ApiClient.createServerApiService(serverUrl)

        // CRITICAL FIX: Save the real Plex token to SharedPreferences to overwrite any demo token
        // This ensures that after app restart, we don't fall back to demo mode
        authRepository.saveAuthToken(token)
        Log.d(TAG, "setServerConnection: Saved real Plex token (${token.take(20)}...)")
    }

    /**
     * Check if we have a valid Plex connection (or demo mode)
     */
    fun hasValidConnection(): Boolean {
        // Check for demo mode first
        val tokenToCheck = currentToken ?: authRepository.getAuthToken()
        if (com.purestream.data.demo.DemoData.isDemoToken(tokenToCheck)) {
            android.util.Log.d("PlexRepository", "hasValidConnection: Demo mode active - returning true")
            return true
        }

        return serverApiService != null && currentToken != null && currentServerUrl != null
    }

    /**
     * Ensure connection is available, restore from saved settings if needed
     * Used by background workers to restore connection in isolated context
     */
    private suspend fun ensureConnection(): Boolean {
        // If already connected, we're good
        if (hasValidConnection()) return true

        // Try to restore from saved AppSettings
        try {
            val appSettingsRepo = AppSettingsRepository(context)
            val settings = appSettingsRepo.getAppSettingsSync()

            if (!settings.plexServerUrl.isNullOrEmpty() && !settings.plexToken.isNullOrEmpty()) {
                android.util.Log.d(TAG, "Restoring Plex connection from saved settings: ${settings.plexServerUrl}")
                setServerConnection(settings.plexServerUrl, settings.plexToken)
                return true
            } else {
                android.util.Log.w(TAG, "No saved Plex connection found in AppSettings")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to restore Plex connection: ${e.message}", e)
        }

        return false
    }

    /**
     * Fix demo URIs to use correct package name (debug vs release builds)
     * This replaces hardcoded "com.purestream" with actual running package name
     */
    private fun fixDemoUris(movies: List<Movie>): List<Movie> {
        return movies.map { movie ->
            movie.copy(
                thumbUrl = fixUriPackage(movie.thumbUrl),
                artUrl = fixUriPackage(movie.artUrl),
                logoUrl = fixUriPackage(movie.logoUrl)
            )
        }
    }

    private fun fixDemoTvUris(shows: List<TvShow>): List<TvShow> {
        return shows.map { show ->
            show.copy(
                thumbUrl = fixUriPackage(show.thumbUrl),
                artUrl = fixUriPackage(show.artUrl)
            )
        }
    }

    private fun fixUriPackage(uri: String?): String? {
        if (uri == null) return null

        // If it's an android.resource URI with wrong package, fix it
        if (uri.startsWith("android.resource://")) {
            // Format: android.resource://package_name/resource_id
            val parts = uri.split("/")
            if (parts.size >= 4) {
                val resourceId = parts.last() // The ID number
                return "android.resource://${context.packageName}/$resourceId"
            }
        }

        // CRITICAL FIX: If it's a simple drawable name (like "demo_cyber_runner"), convert to URI
        // Demo drawable names don't contain "/" and don't start with "http"
        if (!uri.contains("/") && !uri.startsWith("http", ignoreCase = true)) {
            try {
                val resourceId = context.resources.getIdentifier(
                    uri,
                    "drawable",
                    context.packageName
                )
                if (resourceId != 0) {
                    val resourceUri = "android.resource://${context.packageName}/$resourceId"
                    Log.d(TAG, "fixUriPackage: Converted drawable '$uri' to URI: $resourceUri")
                    return resourceUri
                } else {
                    Log.w(TAG, "fixUriPackage: Could not find drawable resource: $uri")
                }
            } catch (e: Exception) {
                Log.e(TAG, "fixUriPackage: Failed to resolve drawable: $uri", e)
            }
        }

        return uri
    }

    /**
     * Retry an API call with automatic fallback to backup connection if available
     */
    private suspend fun <T> retryWithFallback(
        operation: suspend (PlexApiService, String) -> retrofit2.Response<T>
    ): retrofit2.Response<T>? {
        val currentService = serverApiService
        val currentToken = currentToken

        if (currentService == null || currentToken == null) {
            Log.e("PlexRepository", "No active connection available for retry")
            return null
        }

        try {
            val response = operation(currentService, currentToken)
            if (response.isSuccessful) {
                return response
            } else {
                Log.w("PlexRepository", "API call failed on ${if (isUsingFallback) "fallback" else "primary"} connection: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w("PlexRepository", "Exception on ${if (isUsingFallback) "fallback" else "primary"} connection: ${e.message}")
        }

        // If primary failed and we have a fallback, try it
        if (!isUsingFallback && fallbackConnection != null) {
            try {
                val fallbackResponse = operation(fallbackConnection!!.apiService, fallbackConnection!!.token)

                if (fallbackResponse.isSuccessful) {
                    // Switch to fallback connection
                    currentServerUrl = fallbackConnection!!.serverUrl
                    this.currentToken = fallbackConnection!!.token
                    serverApiService = fallbackConnection!!.apiService
                    isUsingFallback = true

                    return fallbackResponse
                } else {
                    Log.w("PlexRepository", "Fallback connection also failed: ${fallbackResponse.code()}")
                }
            } catch (e: Exception) {
                Log.w("PlexRepository", "Exception on fallback connection: ${e.message}")
            }
        }

        // If we're using fallback and it failed, try switching back to primary
        if (isUsingFallback && primaryConnection != null) {
            try {
                val primaryResponse = operation(primaryConnection!!.apiService, primaryConnection!!.token)

                if (primaryResponse.isSuccessful) {
                    // Switch back to primary connection
                    currentServerUrl = primaryConnection!!.serverUrl
                    this.currentToken = primaryConnection!!.token
                    serverApiService = primaryConnection!!.apiService
                    isUsingFallback = false

                    return primaryResponse
                } else {
                    Log.w("PlexRepository", "Primary connection still failing: ${primaryResponse.code()}")
                }
            } catch (e: Exception) {
                Log.w("PlexRepository", "Exception trying to restore primary connection: ${e.message}")
            }
        }

        Log.e("PlexRepository", "All connection attempts failed")
        return null
    }

    /**
     * Helper function to filter movie libraries based on selected libraries
     * Consolidates duplicate filtering logic across multiple methods
     */
    private suspend fun getFilteredMovieLibraries(selectedLibraries: List<String> = emptyList()): Result<List<PlexLibrary>> {
        // Check for Demo Mode first
        val tokenToCheck = currentToken ?: authRepository.getAuthToken()
        if (com.purestream.data.demo.DemoData.isDemoToken(tokenToCheck)) {
            return Result.success(com.purestream.data.demo.DemoData.DEMO_LIBRARIES.filter { it.type == "movie" })
        }

        val token = currentToken ?: return Result.failure(Exception("No token available"))
        val service = serverApiService ?: return Result.failure(Exception("No server connection"))

        val librariesResult = service.getLibraries(token)
        if (!librariesResult.isSuccessful) {
            return Result.failure(Exception("Failed to fetch libraries"))
        }

        val allMovieLibraries = librariesResult.body()?.mediaContainer?.directories?.filter { it.type == "movie" } ?: emptyList()

        // Filter by selected libraries if provided
        val movieLibraries = if (selectedLibraries.isNotEmpty()) {
            allMovieLibraries.filter { library ->
                selectedLibraries.any { selectedLib ->
                    library.key == selectedLib || library.title.equals(selectedLib, ignoreCase = true)
                }
            }
        } else {
            allMovieLibraries
        }

        return Result.success(movieLibraries)
    }

    suspend fun getLibrariesWithAuth(token: String): Result<List<PlexLibrary>> = withContext(Dispatchers.IO) {
        try {
            // Check for Demo Mode
            if (com.purestream.data.demo.DemoData.isDemoToken(token)) {
                Log.d("PlexRepository", "Demo Mode: Returning demo libraries")
                return@withContext Result.success(com.purestream.data.demo.DemoData.DEMO_LIBRARIES)
            }

            // First, try to get user's servers if we don't have a connection
            if (serverApiService == null) {
                val setupResult = setupServerConnection(token)
                if (setupResult.isFailure) {
                    return@withContext Result.failure(Exception("Failed to discover Plex servers. Please ensure your Plex Media Server is running and accessible from this device."))
                }
            }

            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            // Use the stored server-specific token if available, otherwise fall back to the passed token
            val authToken = currentToken ?: token
            Log.d("PlexRepository", "Getting libraries with token: ${authToken.take(10)}...")

            val response = service.getLibraries(authToken)
            if (response.isSuccessful) {
                val libraries = response.body()?.mediaContainer?.directories ?: emptyList()
                Log.d("PlexRepository", "Successfully retrieved ${libraries.size} libraries: ${libraries.map { it.title }}")
                Result.success(libraries)
            } else {
                Log.e("PlexRepository", "Failed to fetch libraries: ${response.code()}")
                Result.failure(Exception("Failed to fetch libraries: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun getLibraries(): Result<List<PlexLibrary>> = withContext(Dispatchers.IO) {
        try {
            // Check for Demo Mode - check stored token if currentToken is null
            val tokenToCheck = currentToken ?: authRepository.getAuthToken()
            if (com.purestream.data.demo.DemoData.isDemoToken(tokenToCheck)) {
                Log.d("PlexRepository", "Demo Mode: Returning demo libraries")
                return@withContext Result.success(com.purestream.data.demo.DemoData.DEMO_LIBRARIES)
            }

            val response = retryWithFallback { service, token ->
                service.getLibraries(token)
            }

            if (response?.isSuccessful == true) {
                val libraries = response.body()?.mediaContainer?.directories ?: emptyList()
                Result.success(libraries)
            } else {
                Result.failure(Exception("Failed to fetch libraries: ${response?.code() ?: "No response"}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun setupServerConnection(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check for Demo Mode
            if (com.purestream.data.demo.DemoData.isDemoToken(token)) {
                Log.d("PlexRepository", "Demo Mode: Skipping server setup")
                currentToken = token
                currentServerUrl = "https://demo.purestream.app"
                return@withContext Result.success(Unit)
            }

            Log.d("PlexRepository", "Attempting to discover Plex servers with token: ${token.take(10)}...")
            val response = plexApiService.getUserServers(token)
            Log.d("PlexRepository", "Server discovery response code: ${response.code()}")

            if (response.isSuccessful) {
                val responseBody = response.body()
                val xmlContent = responseBody?.string()

                if (xmlContent != null) {
                    val servers = parseServersXml(xmlContent)

                    if (servers.isNotEmpty()) {
                        // Group servers by machine identifier to get local and remote connections for same server
                        val serverGroups = servers.groupBy { it.machineIdentifier }

                        for ((machineId, serverConnections) in serverGroups) {

                            // Sort connections: local first, then remote
                            val localConnections = mutableListOf<PlexServer>()
                            val remoteConnections = mutableListOf<PlexServer>()

                            serverConnections.forEach { server ->
                                val isLocal = server.host.startsWith("192.168.") ||
                                             server.host.startsWith("10.") ||
                                             server.host.startsWith("172.") ||
                                             server.host == "localhost" ||
                                             server.host == "127.0.0.1"

                                if (isLocal) {
                                    localConnections.add(server)
                                } else {
                                    remoteConnections.add(server)
                                }
                            }

                            // Test and store both local and remote connections in parallel
                            val connectionsToTry = localConnections + remoteConnections
                            val workingConnections = coroutineScope {
                                connectionsToTry.map { server ->
                                    async {
                                        val serverUrl = if (server.host.startsWith("http")) {
                                            server.host
                                        } else {
                                            val protocol = if (server.host.contains(".app") || server.port == 443) "https" else "http"
                                            "$protocol://${server.host}:${server.port}"
                                        }

                                        val isLocal = server.host.startsWith("192.168.") ||
                                                     server.host.startsWith("10.") ||
                                                     server.host.startsWith("172.") ||
                                                     server.host == "localhost" ||
                                                     server.host == "127.0.0.1"

                                        val testToken = if (server.accessToken.isNotBlank()) server.accessToken else token

                                        try {
                                            Log.d("PlexRepository", "Testing connection: $serverUrl (${if (isLocal) "local" else "remote"})")
                                            val testApiService = ApiClient.createServerApiService(serverUrl, isLocal)
                                            val testResponse = testApiService.getLibraries(testToken)
                                            
                                            if (testResponse?.isSuccessful == true) {
                                                Log.d("PlexRepository", "Connection successful: $serverUrl")
                                                ServerConnectionInfo(
                                                    serverUrl = serverUrl,
                                                    token = testToken,
                                                    isLocal = isLocal,
                                                    apiService = testApiService
                                                )
                                            } else {
                                                Log.w("PlexRepository", "Connection failed ($serverUrl): ${testResponse?.code()}")
                                                null
                                            }
                                        } catch (e: Exception) {
                                            Log.w("PlexRepository", "Connection error ($serverUrl): ${e.message}")
                                            null
                                        }
                                    }
                                }.awaitAll().filterNotNull()
                            }

                            // Store primary and fallback connections
                            if (workingConnections.isNotEmpty()) {
                                // Prefer local connections as primary
                                val workingLocal = workingConnections.filter { it.isLocal }
                                val workingRemote = workingConnections.filter { !it.isLocal }

                                primaryConnection = workingLocal.firstOrNull() ?: workingRemote.first()
                                fallbackConnection = if (workingLocal.isNotEmpty() && workingRemote.isNotEmpty()) {
                                    workingRemote.first()
                                } else null

                                // Set up primary connection
                                currentServerUrl = primaryConnection!!.serverUrl
                                currentToken = primaryConnection!!.token
                                serverApiService = primaryConnection!!.apiService
                                isUsingFallback = false

                                // Save the successful connection details for background workers
                                try {
                                    val appSettingsRepo = AppSettingsRepository(context)
                                    appSettingsRepo.updatePlexConnection(primaryConnection!!.serverUrl, primaryConnection!!.token)
                                    Log.d(TAG, "Saved successful Plex connection to AppSettings for background use.")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to save Plex connection to AppSettings: ${e.message}")
                                }

                                Log.d("PlexRepository", "Primary connection: ${primaryConnection!!.serverUrl} (${if (primaryConnection!!.isLocal) "local" else "remote"})")
                                if (fallbackConnection != null) {
                                    Log.d("PlexRepository", "Fallback connection: ${fallbackConnection!!.serverUrl} (${if (fallbackConnection!!.isLocal) "local" else "remote"})")
                                } else {
                                    Log.d("PlexRepository", "No fallback connection available")
                                }

                                return@withContext Result.success(Unit)
                            }
                        }

                        // If we get here, no connections worked
                        Result.failure(Exception("Could not connect to any Plex server. Tried ${servers.size} connections."))
                    } else {
                        Result.failure(Exception("No Plex servers found in response"))
                    }
                } else {
                    Result.failure(Exception("Empty server response"))
                }
            } else {
                Log.e("PlexRepository", "Failed to get servers: ${response.code()} - ${response.message()}")
                val errorBody = response.errorBody()?.string()
                Log.e("PlexRepository", "Error response body: $errorBody")
                Result.failure(Exception("Failed to get servers: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("PlexRepository", "Exception during server discovery", e)
            Result.failure(e)
        }
    }

    private fun parseServersXml(xmlContent: String): List<PlexServer> {
        val servers = mutableListOf<PlexServer>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlContent.toByteArray()), null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "resource") {
                    val product = parser.getAttributeValue(null, "product")

                    // Only process Plex Media Server resources
                    if (product == "Plex Media Server") {
                        val name = parser.getAttributeValue(null, "name") ?: "Unknown Server"
                        val machineId = parser.getAttributeValue(null, "clientIdentifier") ?: ""
                        val version = parser.getAttributeValue(null, "productVersion") ?: ""
                        val accessToken = parser.getAttributeValue(null, "accessToken") ?: ""

                        // Look for connections within this resource
                        val connections = parseConnections(parser)
                        connections.forEach { connection ->
                            servers.add(PlexServer(name, connection.address, connection.port, machineId, version, accessToken))
                            Log.d("PlexRepository", "Found server: $name at ${connection.address}:${connection.port} (token: ${accessToken.take(10)}...)")
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("PlexRepository", "Error parsing servers XML", e)
        }
        return servers
    }

    private fun parseConnections(parser: XmlPullParser): List<ServerConnection> {
        val connections = mutableListOf<ServerConnection>()
        try {
            var depth = 1
            var eventType = parser.next()

            while (eventType != XmlPullParser.END_DOCUMENT && depth > 0) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        if (parser.name == "connection") {
                            val address = parser.getAttributeValue(null, "address") ?: ""
                            val portStr = parser.getAttributeValue(null, "port") ?: "32400"
                            val local = parser.getAttributeValue(null, "local") == "1"

                            val port = try {
                                portStr.toInt()
                            } catch (e: NumberFormatException) {
                                32400
                            }

                            if (address.isNotBlank()) {
                                connections.add(ServerConnection(address, port, local))
                                Log.d("PlexRepository", "Found connection: $address:$port (local: $local)")
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("PlexRepository", "Error parsing connections", e)
        }
        return connections
    }

    private data class ServerConnection(
        val address: String,
        val port: Int,
        val isLocal: Boolean
    )

    private data class ServerConnectionInfo(
        val serverUrl: String,
        val token: String,
        val isLocal: Boolean,
        val apiService: PlexApiService
    )

    suspend fun getMovies(
        libraryId: String,
        profileId: String = "",
        forceRefresh: Boolean = false
    ): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            // Check if libraryId indicates demo content regardless of token state
            if (libraryId.startsWith("demo_")) {
                Log.d(TAG, "Demo library ID detected ($libraryId): Returning demo movies with fixed URIs")
                return@withContext Result.success(fixDemoUris(com.purestream.data.demo.DemoData.DEMO_MOVIES))
            }

            // CRITICAL FIX: Try to restore connection FIRST before checking demo mode
            if (!hasValidConnection()) {
                ensureConnection()
            }

            // Check cache metadata if profileId is provided
            if (profileId.isNotEmpty() && !forceRefresh) {
                val cacheKey = "movies_${libraryId}_${profileId}"
                val metadata = cacheMetadataDao.getMetadata(cacheKey)

                // Use cache if valid
                val useCache = metadata != null &&
                              CacheConfig.isCacheValid(metadata.lastRefreshed, CacheConfig.MOVIE_TTL_MS)

                if (useCache) {
                    val cachedMovies = movieCacheDao.getMoviesForLibrary(profileId, libraryId)
                    if (cachedMovies.isNotEmpty()) {
                        Log.d(TAG, "Using cached movies for library $libraryId (${cachedMovies.size} items)")
                        return@withContext Result.success(cachedMovies.map { it.toMovie() })
                    }
                }
            }

            // Cache miss or expired - fetch from API
            Log.d(TAG, "Fetching fresh movies from API for library $libraryId")

            // Ensure connection is available (restores from saved settings if needed)
            if (!ensureConnection()) {
                Log.e(TAG, "Could not establish Plex connection for getMovies()")
                return@withContext Result.failure(Exception("Could not restore Plex connection"))
            }

            val response = retryWithFallback { service, token ->
                service.getMovies(libraryId, token)
            }

            if (response?.isSuccessful == true) {
                val rawMovies = response.body()?.mediaContainer?.metadata ?: emptyList()
                // Process thumbnail URLs to include server URL and token
                val moviesWithFullUrls = rawMovies.map { movie ->
                    // Extract logo URL from images list
                    val logoUrl = movie.images?.find { it.type == "clearLogo" }?.url
                    
                    movie.copy(
                        thumbUrl = buildImageUrl(movie.thumbUrl),
                        artUrl = buildImageUrl(movie.artUrl),
                        logoUrl = buildImageUrl(logoUrl),
                        profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN
                    )
                }

                // Cache the results if profileId is provided
                if (profileId.isNotEmpty()) {
                    cacheMovies(moviesWithFullUrls, libraryId, profileId)
                }

                Result.success(moviesWithFullUrls)
            } else {
                // On API failure, try to use stale cache if available
                if (profileId.isNotEmpty()) {
                    val staleCache = movieCacheDao.getMoviesForLibrary(profileId, libraryId)
                    if (staleCache.isNotEmpty()) {
                        Log.w(TAG, "API failed, using stale cache (${staleCache.size} items)")
                        return@withContext Result.success(staleCache.map { it.toMovie() })
                    }
                }
                Result.failure(Exception("Failed to fetch movies: ${response?.code() ?: "No response"}"))
            }
        } catch (e: Exception) {
            // On exception, try to use stale cache if available
            if (profileId.isNotEmpty()) {
                try {
                    val staleCache = movieCacheDao.getMoviesForLibrary(profileId, libraryId)
                    if (staleCache.isNotEmpty()) {
                        Log.w(TAG, "Exception occurred, using stale cache (${staleCache.size} items)")
                        return@withContext Result.success(staleCache.map { it.toMovie() })
                    }
                } catch (cacheE: Exception) {
                    Log.e(TAG, "Cache read failed: ${cacheE.message}")
                }
            }
            Result.failure(e)
        }
    }

    private suspend fun cacheMovies(movies: List<Movie>, libraryId: String, profileId: String) {
        try {
            val now = System.currentTimeMillis()
            val entities = movies.map { it.toEntity(libraryId, profileId, now, currentServerUrl ?: "") }

            movieCacheDao.insertMovies(entities)

            // Update metadata
            cacheMetadataDao.insertMetadata(
                CacheMetadataEntity(
                    key = "movies_${libraryId}_${profileId}",
                    cacheType = "movies",
                    profileId = profileId,
                    libraryId = libraryId,
                    lastRefreshed = now,
                    itemCount = movies.size,
                    isComplete = true
                )
            )

            Log.d(TAG, "Cached ${movies.size} movies for library $libraryId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache movies: ${e.message}")
        }
    }

    suspend fun getTvShows(
        libraryId: String,
        profileId: String = "",
        forceRefresh: Boolean = false
    ): Result<List<TvShow>> = withContext(Dispatchers.IO) {
        try {
            // Check if libraryId indicates demo content regardless of token state
            if (libraryId.startsWith("demo_")) {
                Log.d(TAG, "Demo library ID detected ($libraryId): Returning demo TV shows with fixed URIs")
                return@withContext Result.success(fixDemoTvUris(com.purestream.data.demo.DemoData.DEMO_TV_SHOWS))
            }

            // CRITICAL FIX: Try to restore connection FIRST before checking demo mode
            if (!hasValidConnection()) {
                ensureConnection()
            }

            // NOW check for Demo Mode after attempting to restore connection
            val tokenToCheck = currentToken ?: authRepository.getAuthToken()
            if (com.purestream.data.demo.DemoData.isDemoToken(tokenToCheck)) {
                Log.d(TAG, "Demo Mode: Returning demo TV shows with fixed URIs for package: ${context.packageName}")
                // Apply URI fix to handle debug vs release builds
                return@withContext Result.success(fixDemoTvUris(com.purestream.data.demo.DemoData.DEMO_TV_SHOWS))
            }

            // Check cache metadata if profileId is provided
            if (profileId.isNotEmpty() && !forceRefresh) {
                val cacheKey = "tvshows_${libraryId}_${profileId}"
                val metadata = cacheMetadataDao.getMetadata(cacheKey)

                // Use cache if valid
                val useCache = metadata != null &&
                              CacheConfig.isCacheValid(metadata.lastRefreshed, CacheConfig.TV_SHOW_TTL_MS)

                if (useCache) {
                    val cachedShows = tvShowCacheDao.getTvShowsForLibrary(profileId, libraryId)
                    if (cachedShows.isNotEmpty()) {
                        Log.d(TAG, "Using cached TV shows for library $libraryId (${cachedShows.size} items)")
                        return@withContext Result.success(cachedShows.map { it.toTvShow() })
                    }
                }
            }

            // Cache miss or expired - fetch from API
            Log.d(TAG, "Fetching fresh TV shows from API for library $libraryId")
            val response = retryWithFallback { service, token ->
                service.getTvShows(libraryId, token)
            }

            if (response?.isSuccessful == true) {
                val rawShows = response.body()?.mediaContainer?.metadata ?: emptyList()
                // Process thumbnail URLs to include server URL and token
                val showsWithFullUrls = rawShows.map { show ->
                    // Extract logo URL from images list
                    val logoUrl = show.images?.find { it.type == "clearLogo" }?.url
                    
                    show.copy(
                        thumbUrl = buildImageUrl(show.thumbUrl),
                        artUrl = buildImageUrl(show.artUrl),
                        logoUrl = buildImageUrl(logoUrl),
                        theme = buildImageUrl(show.theme),
                        profanityLevel = show.profanityLevel ?: ProfanityLevel.UNKNOWN
                    )
                }

                // Cache the results if profileId is provided
                if (profileId.isNotEmpty()) {
                    cacheTvShows(showsWithFullUrls, libraryId, profileId)
                }

                Result.success(showsWithFullUrls)
            } else {
                // On API failure, try to use stale cache if available
                if (profileId.isNotEmpty()) {
                    val staleCache = tvShowCacheDao.getTvShowsForLibrary(profileId, libraryId)
                    if (staleCache.isNotEmpty()) {
                        Log.w(TAG, "API failed, using stale cache (${staleCache.size} items)")
                        return@withContext Result.success(staleCache.map { it.toTvShow() })
                    }
                }
                Result.failure(Exception("Failed to fetch TV shows: ${response?.code() ?: "No response"}"))
            }
        } catch (e: Exception) {
            // On exception, try to use stale cache if available
            if (profileId.isNotEmpty()) {
                try {
                    val staleCache = tvShowCacheDao.getTvShowsForLibrary(profileId, libraryId)
                    if (staleCache.isNotEmpty()) {
                        Log.w(TAG, "Exception occurred, using stale cache (${staleCache.size} items)")
                        return@withContext Result.success(staleCache.map { it.toTvShow() })
                    }
                } catch (cacheE: Exception) {
                    Log.e(TAG, "Cache read failed: ${cacheE.message}")
                }
            }
            Result.failure(e)
        }
    }

    private suspend fun cacheTvShows(shows: List<TvShow>, libraryId: String, profileId: String) {
        try {
            val now = System.currentTimeMillis()
            val entities = shows.map { it.toEntity(libraryId, profileId, now, currentServerUrl ?: "") }

            tvShowCacheDao.insertTvShows(entities)

            // Update metadata
            cacheMetadataDao.insertMetadata(
                CacheMetadataEntity(
                    key = "tvshows_${libraryId}_${profileId}",
                    cacheType = "tv_shows",
                    profileId = profileId,
                    libraryId = libraryId,
                    lastRefreshed = now,
                    itemCount = shows.size,
                    isComplete = true
                )
            )

            Log.d(TAG, "Cached ${shows.size} TV shows for library $libraryId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache TV shows: ${e.message}")
        }
    }

    suspend fun getSeasons(showId: String): Result<List<Season>> = withContext(Dispatchers.IO) {
        try {
            // Check for demo mode - demo TV shows don't have seasons/episodes
            if (showId.startsWith("demo_")) {
                Log.d(TAG, "Demo show ID detected ($showId): Returning empty seasons list")
                return@withContext Result.success(emptyList())
            }

            // Check if currently in demo mode
            val tokenToCheck = currentToken ?: authRepository.getAuthToken()
            if (com.purestream.data.demo.DemoData.isDemoToken(tokenToCheck)) {
                Log.d(TAG, "Demo Mode active: Returning empty seasons list for show $showId")
                return@withContext Result.success(emptyList())
            }

            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            val response = service.getSeasons(showId, token)
            if (response.isSuccessful) {
                val rawSeasons = response.body()?.mediaContainer?.metadata ?: emptyList()
                // Process thumbnail URLs to include server URL and token
                val seasonsWithFullUrls = rawSeasons.map { season ->
                    season.copy(
                        thumbUrl = buildImageUrl(season.thumbUrl)
                    )
                }
                Result.success(seasonsWithFullUrls)
            } else {
                Result.failure(Exception("Failed to fetch seasons: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEpisodes(seasonId: String): Result<List<Episode>> = withContext(Dispatchers.IO) {
        try {
            // Check for demo mode - demo TV shows don't have seasons/episodes
            if (seasonId.startsWith("demo_")) {
                Log.d(TAG, "Demo season ID detected ($seasonId): Returning empty episodes list")
                return@withContext Result.success(emptyList())
            }

            // Check if currently in demo mode
            val tokenToCheck = currentToken ?: authRepository.getAuthToken()
            if (com.purestream.data.demo.DemoData.isDemoToken(tokenToCheck)) {
                Log.d(TAG, "Demo Mode active: Returning empty episodes list for season $seasonId")
                return@withContext Result.success(emptyList())
            }

            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            Log.d("PlexRepository", "Fetching episodes for season ID: $seasonId")
            val response = service.getEpisodes(seasonId, token)
            Log.d("PlexRepository", "Episodes response code: ${response.code()}")

            if (response.isSuccessful) {
                val rawEpisodes = response.body()?.mediaContainer?.metadata ?: emptyList()
                Log.d("PlexRepository", "Retrieved ${rawEpisodes.size} episodes for season $seasonId")

                // Process thumbnail URLs to include server URL and token
                val episodesWithFullUrls = rawEpisodes.map { episode ->
                    episode.copy(
                        thumbUrl = buildImageUrl(episode.thumbUrl)
                    )
                }

                // Log episode details for debugging
                episodesWithFullUrls.take(3).forEach { episode ->
                    Log.d("PlexRepository", "Episode: ${episode.title} (S${episode.seasonNumber}E${episode.episodeNumber})")
                }

                Result.success(episodesWithFullUrls)
            } else {
                Log.e("PlexRepository", "Failed to fetch episodes: ${response.code()} - ${response.message()}")
                Result.failure(Exception("Failed to fetch episodes: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("PlexRepository", "Exception fetching episodes for season $seasonId", e)
            Result.failure(e)
        }
    }

    suspend fun getEpisodeById(episodeId: String): Result<Episode> = withContext(Dispatchers.IO) {
        try {
            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            Log.d("PlexRepository", "Fetching episode details for ID: $episodeId")
            val response = service.getEpisodeDetails(episodeId, token)
            Log.d("PlexRepository", "Episode details response code: ${response.code()}")

            if (response.isSuccessful) {
                val episode = response.body()?.mediaContainer?.metadata?.firstOrNull()

                if (episode != null) {
                    Log.d("PlexRepository", "Episode found: ${episode.title} (S${episode.seasonNumber}E${episode.episodeNumber})")
                    val episodeWithFullUrls = episode.copy(
                        thumbUrl = buildImageUrl(episode.thumbUrl),
                        profanityLevel = episode.profanityLevel ?: ProfanityLevel.UNKNOWN
                    )
                    Result.success(episodeWithFullUrls)
                } else {
                    Log.e("PlexRepository", "Episode not found in response")
                    Result.failure(Exception("Episode not found"))
                }
            } else {
                Log.e("PlexRepository", "Failed to fetch episode details: ${response.code()} - ${response.message()}")
                Result.failure(Exception("Failed to fetch episode details: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("PlexRepository", "Exception fetching episode details for ID $episodeId", e)
            Result.failure(e)
        }
    }

    suspend fun getEpisodeWithShowDetails(episodeId: String): Result<Pair<Episode, TvShow?>> = withContext(Dispatchers.IO) {
        try {
            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            Log.d("PlexRepository", "Fetching episode with show details for ID: $episodeId")
            val response = service.getEpisodeDetails(episodeId, token)
            Log.d("PlexRepository", "Episode details response code: ${response.code()}")

            if (response.isSuccessful) {
                val responseBody = response.body()
                val episode = responseBody?.mediaContainer?.metadata?.firstOrNull()

                if (episode != null) {
                    Log.d("PlexRepository", "Episode found: ${episode.title} (S${episode.seasonNumber}E${episode.episodeNumber})")
                    Log.d("PlexRepository", "Episode grandparentTitle: ${episode.grandparentTitle}")
                    Log.d("PlexRepository", "Episode grandparentRatingKey: ${episode.grandparentRatingKey}")

                    val episodeWithFullUrls = episode.copy(
                        thumbUrl = buildImageUrl(episode.thumbUrl),
                        profanityLevel = episode.profanityLevel ?: ProfanityLevel.UNKNOWN
                    )

                    var tvShow: TvShow? = null

                    // First try to use episode's grandparent info (direct from episode metadata)
                    if (!episode.grandparentTitle.isNullOrBlank()) {
                        Log.d("PlexRepository", "Using episode grandparent info: ${episode.grandparentTitle}")
                        Log.d("PlexRepository", "Episode grandparentYear: ${episode.grandparentYear}")
                        tvShow = TvShow(
                            ratingKey = episode.grandparentRatingKey ?: "show_${episodeId}",
                            key = "/library/metadata/${episode.grandparentRatingKey}",
                            title = episode.grandparentTitle,
                            summary = null,
                            thumbUrl = episodeWithFullUrls.thumbUrl,
                            artUrl = episodeWithFullUrls.thumbUrl,
                            year = episode.grandparentYear,
                            rating = null,
                            contentRating = null,
                            studio = null,
                            episodeCount = null,
                            seasonCount = null
                        )
                        Log.d("PlexRepository", "Created TvShow with year: ${tvShow?.year}")
                    }
                    // If episode grandparent info isn't available, try MediaContainer info
                    else {
                        val containerParentTitle = responseBody?.mediaContainer?.parentTitle
                        val containerGrandparentTitle = responseBody?.mediaContainer?.grandparentTitle
                        val containerGrandparentYear = responseBody?.mediaContainer?.grandparentYear

                        val showTitle = containerGrandparentTitle ?: containerParentTitle

                        if (!showTitle.isNullOrBlank()) {
                            Log.d("PlexRepository", "Using MediaContainer grandparent info: $showTitle")
                            tvShow = TvShow(
                                ratingKey = "container_show_${episodeId}",
                                key = "container_key",
                                title = showTitle,
                                summary = null,
                                thumbUrl = episodeWithFullUrls.thumbUrl,
                                artUrl = episodeWithFullUrls.thumbUrl,
                                year = containerGrandparentYear,
                                rating = null,
                                contentRating = null,
                                studio = null,
                                episodeCount = null,
                                seasonCount = null
                            )
                        }
                    }

                    // If we still don't have show info and we have a grandparentRatingKey, fetch show details
                    // OR if we have show info but no year, try to fetch complete show details
                    if ((!episode.grandparentRatingKey.isNullOrBlank()) &&
                        (tvShow == null || tvShow?.year == null)) {
                        Log.d("PlexRepository", "Fetching complete show details using grandparentRatingKey: ${episode.grandparentRatingKey}")
                        try {
                            val showResult = getTvShowById(episode.grandparentRatingKey)
                            showResult.fold(
                                onSuccess = { fetchedShow ->
                                    if (fetchedShow != null) {
                                        tvShow = fetchedShow
                                        Log.d("PlexRepository", "Successfully fetched complete show: ${fetchedShow.title} (${fetchedShow.year})")
                                    }
                                },
                                onFailure = { showException ->
                                    Log.w("PlexRepository", "Failed to fetch show details: ${showException.message}")
                                }
                            )
                        } catch (showException: Exception) {
                            Log.w("PlexRepository", "Exception fetching show details: ${showException.message}")
                        }
                    }

                    Result.success(Pair(episodeWithFullUrls, tvShow))
                } else {
                    Log.e("PlexRepository", "Episode not found in response")
                    Result.failure(Exception("Episode not found"))
                }
            } else {
                Log.e("PlexRepository", "Failed to fetch episode details: ${response.code()} - ${response.message()}")
                Result.failure(Exception("Failed to fetch episode details: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("PlexRepository", "Exception fetching episode with show details for ID $episodeId", e)
            Result.failure(e)
        }
    }

    suspend fun getMovieDetails(movieId: String): Result<Movie> = withContext(Dispatchers.IO) {
        try {
            // Check for Demo Mode
            val tokenToCheck = currentToken ?: authRepository.getAuthToken()
            if (com.purestream.data.demo.DemoData.isDemoToken(tokenToCheck)) {
                Log.d("PlexRepository", "Demo Mode: Fetching demo movie details for ID: $movieId")
                // Find the demo movie by ratingKey and fix URIs
                val demoMovie = com.purestream.data.demo.DemoData.DEMO_MOVIES.find { it.ratingKey == movieId }
                return@withContext if (demoMovie != null) {
                    // CRITICAL FIX: Apply URI fix before returning
                    val fixedMovie = fixDemoUris(listOf(demoMovie)).first()
                    Result.success(fixedMovie)
                } else {
                    Result.failure(Exception("Demo movie not found with ID: $movieId"))
                }
            }

            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            Log.d("PlexRepository", "Fetching movie details for ID: $movieId")

            val response = service.getMovieDetails(movieId, token)
            if (response.isSuccessful) {
                Log.d("PlexRepository", "Movie details response received successfully")
                val movie = response.body()?.mediaContainer?.metadata?.firstOrNull()

                if (movie != null) {
                    Log.d("PlexRepository", "Movie found: ${movie.title}")
                    
                    // Extract logo URL from images list
                    val logoUrl = movie.images?.find { it.type == "clearLogo" }?.url
                    
                    val movieWithFullUrls = movie.copy(
                        thumbUrl = buildImageUrl(movie.thumbUrl),
                        artUrl = buildImageUrl(movie.artUrl),
                        logoUrl = buildImageUrl(logoUrl),
                        profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN
                    )
                    Result.success(movieWithFullUrls)
                } else {
                    Log.e("PlexRepository", "Movie not found in response")
                    Result.failure(Exception("Movie not found in response"))
                }
            } else {
                Log.e("PlexRepository", "Failed to fetch movie details: ${response.code()} - ${response.message()}")
                Result.failure(Exception("Failed to fetch movie details: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("PlexRepository", "Exception while fetching movie details", e)
            Result.failure(e)
        }
    }

    suspend fun getVideoStreamUrl(movie: Movie): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check for Demo Mode
            val tokenToCheck = currentToken ?: authRepository.getAuthToken()
            if (com.purestream.data.demo.DemoData.isDemoToken(tokenToCheck)) {
                Log.d("PlexRepository", "Demo Mode: Preparing demo video file")
                try {
                    val demoResId = com.purestream.data.demo.DemoData.DEMO_VIDEO_RESOURCE_ID
                    
                    // Try internal cache dir first, fallback to files dir
                    val cacheDir = context.cacheDir ?: context.filesDir
                    val demoFile = java.io.File(cacheDir, "demo_video.mp4")
                    
                    // Always overwrite to ensure we have a valid file
                    if (demoFile.exists()) {
                        demoFile.delete()
                    }
                    
                    try {
                        context.resources.openRawResource(demoResId).use { input ->
                            java.io.FileOutputStream(demoFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        // Verify file size
                        val fileSize = demoFile.length()
                        Log.d("PlexRepository", "Demo video copied to: ${demoFile.absolutePath}, size: $fileSize bytes")
                        
                        if (fileSize > 0) {
                            demoFile.setReadable(true, false)
                            return@withContext Result.success("file://${demoFile.absolutePath}")
                        } else {
                            Log.e("PlexRepository", "Demo video file is empty after copy")
                        }
                    } catch (e: Exception) {
                        Log.e("PlexRepository", "Error copying demo video resource", e)
                    }
                    
                    // Fallback: try to return resource URI if copy failed
                    Log.w("PlexRepository", "Falling back to resource URI")
                    val demoVideoUri = "android.resource://${context.packageName}/$demoResId"
                    return@withContext Result.success(demoVideoUri)
                    
                } catch (e: Exception) {
                    Log.e("PlexRepository", "Failed to prepare demo video file", e)
                    val demoResId = com.purestream.data.demo.DemoData.DEMO_VIDEO_RESOURCE_ID
                    val demoVideoUri = "android.resource://${context.packageName}/$demoResId"
                    return@withContext Result.success(demoVideoUri)
                }
            }

            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val serverUrl = currentServerUrl ?: return@withContext Result.failure(Exception("No server connection"))

            Log.d("PlexRepository", "Getting video URL for movie: ${movie.title} (ID: ${movie.ratingKey})")
            Log.d("PlexRepository", "Movie key: ${movie.key}")
            Log.d("PlexRepository", "Movie has ${movie.media?.size ?: 0} media items")

            // Handshake: Briefly ping server to wake it up/trigger session
            try {
                withTimeout(2000) {
                    serverApiService?.getItemDetails(movie.ratingKey, token)
                }
            } catch (e: Exception) {
                // Non-critical
            }

            // Check if movie has media parts with streaming keys
            val mediaPart = movie.media?.firstOrNull()?.parts?.firstOrNull()
            if (mediaPart?.key?.isNotBlank() == true) {
                val streamingKey = mediaPart.key
                val videoUrl = if (streamingKey.startsWith("http")) {
                    "$streamingKey?X-Plex-Token=$token"
                } else {
                    "$serverUrl$streamingKey?X-Plex-Token=$token"
                }
                Log.d("PlexRepository", "Generated video URL from media part: ${videoUrl.take(80)}...")
                return@withContext Result.success(videoUrl)
            }

            // If no media parts, construct streaming URL using Plex's video endpoint
            if (!movie.key.isNullOrBlank()) {
                // Use Plex's video streaming endpoint format
                val streamingPath = if (movie.key.startsWith("/library/metadata/")) {
                    movie.key
                } else {
                    "/library/metadata/${movie.ratingKey}"
                }

                // Build proper Plex streaming URL for LibVLC
                val videoUrl = "$serverUrl/video/:/transcode/universal/start.m3u8" +
                        "?hasMDE=1" +
                        "&path=${Uri.encode(streamingPath)}" +
                        "&mediaIndex=0" +
                        "&partIndex=0" +
                        "&protocol=hls" +
                        "&fastSeek=1" +
                        "&directPlay=0" +
                        "&directStream=1" +
                        "&subtitleSize=100" +
                        "&audioBoost=100" +
                        "&location=lan" +
                        "&addDebugOverlay=0" +
                        "&autoAdjustQuality=1" +
                        "&directStreamAudio=1" +
                        "&mediaBufferSize=102400" +
                        "&session=${java.util.UUID.randomUUID()}" +
                        "&X-Plex-Token=$token"

                Log.d("PlexRepository", "Generated Plex streaming URL: ${videoUrl.take(120)}...")
                return@withContext Result.success(videoUrl)
            }

            // If all else fails, return error
            Log.e("PlexRepository", "No streaming path found for movie: ${movie.title}")
            Result.failure(Exception("No streaming media found for this movie. Movie key: ${movie.key}, Media count: ${movie.media?.size}"))
        } catch (e: Exception) {
            Log.e("PlexRepository", "Error generating video URL for ${movie.title}", e)
            Result.failure(e)
        }
    }

    suspend fun getEpisodeVideoStreamUrl(episode: Episode): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val serverUrl = currentServerUrl ?: return@withContext Result.failure(Exception("No server connection"))

            Log.d("PlexRepository", "Getting video URL for episode: ${episode.title} (ID: ${episode.ratingKey})")
            Log.d("PlexRepository", "Episode key: ${episode.key}")
            Log.d("PlexRepository", "Episode has ${episode.media?.size ?: 0} media items")

            // Check if episode has media parts with streaming keys
            val mediaPart = episode.media?.firstOrNull()?.parts?.firstOrNull()
            if (mediaPart?.key?.isNotBlank() == true) {
                val streamingKey = mediaPart.key
                val videoUrl = if (streamingKey.startsWith("http")) {
                    "$streamingKey?X-Plex-Token=$token"
                } else {
                    "$serverUrl$streamingKey?X-Plex-Token=$token"
                }
                Log.d("PlexRepository", "Generated video URL from episode media part: ${videoUrl.take(80)}...")
                return@withContext Result.success(videoUrl)
            }

            // If no media parts, construct streaming URL using Plex's video endpoint
            if (!episode.key.isNullOrBlank()) {
                // Use Plex's video streaming endpoint format for episodes
                val streamingPath = if (episode.key.startsWith("/library/metadata/")) {
                    episode.key
                } else {
                    "/library/metadata/${episode.ratingKey}"
                }

                // Simplified parameters for LibVLC (handles all codecs)

                // Build proper Plex streaming URL for episode with codec-aware parameters
                val baseUrl = "$serverUrl/video/:/transcode/universal/start.m3u8"
                val params = mutableMapOf<String, String>()

                // Required Plex parameters
                params["hasMDE"] = "1"
                params["path"] = streamingPath
                params["mediaIndex"] = "0"
                params["partIndex"] = "0"
                params["fastSeek"] = "1"
                params["subtitleSize"] = "100"
                params["audioBoost"] = "100"
                params["location"] = "lan"
                params["addDebugOverlay"] = "0"
                params["session"] = java.util.UUID.randomUUID().toString()
                params["X-Plex-Token"] = token

                // LibVLC handles all codecs, simplified parameters
                params["directPlay"] = "1"
                params["directStream"] = "1"

                // Build URL with all parameters
                val videoUrl = baseUrl + "?" + params.map { "${it.key}=${Uri.encode(it.value)}" }.joinToString("&")

                Log.d("PlexRepository", "Generated Plex episode streaming URL: ${videoUrl.take(120)}...")
                return@withContext Result.success(videoUrl)
            }

            // If all else fails, return error
            Log.e("PlexRepository", "No streaming path found for episode: ${episode.title}")
            Result.failure(Exception("No streaming media found for this episode. Episode key: ${episode.key}, Media count: ${episode.media?.size}"))
        } catch (e: Exception) {
            Log.e("PlexRepository", "Error generating video URL for ${episode.title}", e)
            Result.failure(e)
        }
    }

    suspend fun searchContent(query: String): Result<List<ContentItem>> = withContext(Dispatchers.IO) {
        try {
            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            val response = service.performSearch(token, query)
            if (response.isSuccessful) {
                // Convert search results to ContentItem list
                val searchResults = response.body()?.mediaContainer?.metadata ?: emptyList<Any>()
                val contentItems = convertToContentItems(searchResults)
                Result.success(contentItems)
            } else {
                Result.failure(Exception("Search failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecentlyAdded(): Result<List<ContentItem>> = withContext(Dispatchers.IO) {
        try {
            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            val response = service.getRecentlyAdded(token)
            if (response.isSuccessful) {
                val recentItems = response.body()?.mediaContainer?.metadata ?: emptyList<Any>()
                val contentItems = convertToContentItems(recentItems)
                Result.success(contentItems)
            } else {
                Result.failure(Exception("Failed to fetch recently added: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // New methods for specific home dashboard sections
    suspend fun getTrendingNow(selectedLibraries: List<String> = emptyList()): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            val librariesResult = getFilteredMovieLibraries(selectedLibraries)
            if (librariesResult.isFailure) {
                return@withContext Result.failure(librariesResult.exceptionOrNull() ?: Exception("Failed to get libraries"))
            }

            val movieLibraries = librariesResult.getOrNull() ?: emptyList()
            if (movieLibraries.isEmpty()) {
                return@withContext Result.failure(Exception("No movie libraries available for trending content"))
            }

            // Use the first available movie library
            val movieLibrary = movieLibraries.first()

            val response = service.getNewestMovies(movieLibrary.key, token, limit = 15)
            if (response.isSuccessful) {
                val movies = response.body()?.mediaContainer?.metadata ?: emptyList()
                val moviesWithFullUrls = movies.map { movie ->
                    movie.copy(
                        thumbUrl = buildImageUrl(movie.thumbUrl),
                        artUrl = buildImageUrl(movie.artUrl)
                    )
                }
                Result.success(moviesWithFullUrls)
            } else {
                Result.failure(Exception("Failed to fetch trending movies: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPopularMovies(selectedLibraries: List<String> = emptyList()): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val response = retryWithFallback { service, token ->
                val librariesResult = getFilteredMovieLibraries(selectedLibraries)
                if (librariesResult.isFailure) {
                    throw Exception("Failed to get libraries")
                }

                val movieLibraries = librariesResult.getOrNull() ?: emptyList()
                if (movieLibraries.isEmpty()) {
                    throw Exception("No movie libraries available")
                }

                val movieLibrary = movieLibraries.first()
                service.getOnDeckMovies(movieLibrary.key, token, limit = 15)
            }

            if (response?.isSuccessful == true) {
                val movies = response.body()?.mediaContainer?.metadata ?: emptyList()
                val moviesWithFullUrls = movies.map { movie ->
                    movie.copy(
                        thumbUrl = buildImageUrl(movie.thumbUrl),
                        artUrl = buildImageUrl(movie.artUrl)
                    )
                }
                Result.success(moviesWithFullUrls)
            } else {
                Result.failure(Exception("Failed to fetch popular movies: ${response?.code() ?: "No response"}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecommendedMovies(selectedLibraries: List<String> = emptyList()): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val response = retryWithFallback { service, token ->
                val librariesResult = getFilteredMovieLibraries(selectedLibraries)
                if (librariesResult.isFailure) {
                    throw Exception("Failed to get libraries")
                }

                val movieLibraries = librariesResult.getOrNull() ?: emptyList()
                if (movieLibraries.isEmpty()) {
                    throw Exception("No movie libraries available")
                }

                val movieLibrary = movieLibraries.first()
                service.getRecentlyAddedMovies(movieLibrary.key, token, sort = "addedAt:desc", limit = 15)
            }

            if (response?.isSuccessful == true) {
                val movies = response.body()?.mediaContainer?.metadata ?: emptyList()
                val moviesWithFullUrls = movies.map { movie ->
                    movie.copy(
                        thumbUrl = buildImageUrl(movie.thumbUrl),
                        artUrl = buildImageUrl(movie.artUrl)
                    )
                }
                Result.success(moviesWithFullUrls)
            } else {
                Result.failure(Exception("Failed to fetch recommended movies: ${response?.code() ?: "No response"}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecentMovies(selectedLibraries: List<String> = emptyList()): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val response = retryWithFallback { service, token ->
                val librariesResult = getFilteredMovieLibraries(selectedLibraries)
                if (librariesResult.isFailure) {
                    throw Exception("Failed to get libraries")
                }

                val movieLibraries = librariesResult.getOrNull() ?: emptyList()
                if (movieLibraries.isEmpty()) {
                    throw Exception("No movie libraries available")
                }

                val movieLibrary = movieLibraries.first()
                service.getRecentlyAddedMovies(movieLibrary.key, token, sort = "addedAt:asc", limit = 15)
            }

            if (response?.isSuccessful == true) {
                val movies = response.body()?.mediaContainer?.metadata ?: emptyList()
                val moviesWithFullUrls = movies.map { movie ->
                    movie.copy(
                        thumbUrl = buildImageUrl(movie.thumbUrl),
                        artUrl = buildImageUrl(movie.artUrl)
                    )
                }
                Result.success(moviesWithFullUrls)
            } else {
                Result.failure(Exception("Failed to fetch recent movies: ${response?.code() ?: "No response"}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMovieById(movieId: String): Result<Movie?> = withContext(Dispatchers.IO) {
        try {
            // Check for Demo Mode
            val tokenToCheck = currentToken ?: authRepository.getAuthToken()
            if (com.purestream.data.demo.DemoData.isDemoToken(tokenToCheck)) {
                Log.d("PlexRepository", "Demo Mode: Fetching demo movie details for ID: $movieId")
                val demoMovie = com.purestream.data.demo.DemoData.DEMO_MOVIES.find { it.ratingKey == movieId }
                return@withContext if (demoMovie != null) {
                    val fixedMovie = fixDemoUris(listOf(demoMovie)).first()
                    Result.success(fixedMovie)
                } else {
                    Result.failure(Exception("Demo movie not found with ID: $movieId"))
                }
            }

            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            val response = service.getMovieDetails(movieId, token)
            if (response.isSuccessful) {
                val movie = response.body()?.mediaContainer?.metadata?.firstOrNull()?.let { movie ->
                    // Extract logo URL from images list
                    val logoUrl = movie.images?.find { it.type == "clearLogo" }?.url
                    
                    movie.copy(
                        thumbUrl = buildImageUrl(movie.thumbUrl),
                        artUrl = buildImageUrl(movie.artUrl),
                        logoUrl = buildImageUrl(logoUrl)
                    )
                }
                Result.success(movie)
            } else {
                Result.failure(Exception("Failed to fetch movie details: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTvShowById(showId: String): Result<TvShow?> = withContext(Dispatchers.IO) {
        try {
            // Check for Demo Mode
            val tokenToCheck = currentToken ?: authRepository.getAuthToken()
            if (com.purestream.data.demo.DemoData.isDemoToken(tokenToCheck)) {
                Log.d("PlexRepository", "Demo Mode: Fetching demo TV show details for ID: $showId")
                // Find the demo show by ratingKey and fix URIs
                val demoShow = com.purestream.data.demo.DemoData.DEMO_TV_SHOWS.find { it.ratingKey == showId }
                val fixedShow = demoShow?.let { fixDemoTvUris(listOf(it)).firstOrNull() }
                return@withContext Result.success(fixedShow)
            }

            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            val response = service.getTvShowDetails(showId, token)
            if (response.isSuccessful) {
                val tvShow = response.body()?.mediaContainer?.metadata?.firstOrNull()?.let { show ->
                    // Extract logo URL from images list
                    val logoUrl = show.images?.find { it.type == "clearLogo" }?.url
                    
                    show.copy(
                        thumbUrl = buildImageUrl(show.thumbUrl),
                        artUrl = buildImageUrl(show.artUrl),
                        logoUrl = buildImageUrl(logoUrl),
                        theme = buildImageUrl(show.theme)
                    )
                }
                Result.success(tvShow)
            } else {
                Result.failure(Exception("Failed to fetch TV show details: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMostRecentlyAddedMovie(selectedLibraries: List<String> = emptyList()): Result<Movie?> = withContext(Dispatchers.IO) {
        try {
            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            val librariesResult = service.getLibraries(token)
            if (!librariesResult.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch libraries"))
            }

            val movieLibrary = librariesResult.body()?.mediaContainer?.directories?.find { it.type == "movie" }
                ?: return@withContext Result.failure(Exception("No movie library found"))

            val response = service.getRecentlyAddedMovies(movieLibrary.key, token, sort = "addedAt:asc", limit = 1)
            if (response.isSuccessful) {
                val movies = response.body()?.mediaContainer?.metadata ?: emptyList()
                val movie = movies.firstOrNull()?.let { movie ->
                    movie.copy(
                        thumbUrl = buildImageUrl(movie.thumbUrl),
                        artUrl = buildImageUrl(movie.artUrl)
                    )
                }
                Result.success(movie)
            } else {
                Result.failure(Exception("Failed to fetch most recently added movie: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getThrillerGenreContent(selectedLibraries: List<String> = emptyList()): Result<List<ContentItem>> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("PlexRepository", "Starting Thriller genre search with libraries: $selectedLibraries")
            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            // Get all libraries first
            val librariesResult = service.getLibraries(token)
            if (!librariesResult.isSuccessful) {
                android.util.Log.e("PlexRepository", "Failed to fetch libraries: ${librariesResult.code()}")
                return@withContext Result.failure(Exception("Failed to fetch libraries"))
            }

            val allLibraries = librariesResult.body()?.mediaContainer?.directories ?: emptyList()
            android.util.Log.d("PlexRepository", "Found ${allLibraries.size} total libraries")

            // Filter by selected libraries if provided, otherwise use all
            val targetLibraries = if (selectedLibraries.isNotEmpty()) {
                allLibraries.filter { library ->
                    selectedLibraries.any { selectedLib ->
                        library.key == selectedLib || library.title.equals(selectedLib, ignoreCase = true)
                    }
                }
            } else {
                allLibraries
            }

            android.util.Log.d("PlexRepository", "Searching in ${targetLibraries.size} target libraries")
            if (targetLibraries.isEmpty()) {
                android.util.Log.w("PlexRepository", "No target libraries found for thriller content")
                return@withContext Result.failure(Exception("No libraries available for thriller content"))
            }

            val allThrillerContent = mutableListOf<ContentItem>()

            // Search for "Thriller" genre content in each library
            for (library in targetLibraries) {
                try {
                    android.util.Log.d("PlexRepository", "Searching for Thriller in library: ${library.title} (${library.key})")
                    val response = service.performSearch(token, "Thriller", sectionId = library.key)
                    if (response.isSuccessful) {
                        val searchResults = response.body()?.mediaContainer?.metadata ?: emptyList<Any>()
                        android.util.Log.d("PlexRepository", "Found ${searchResults.size} Thriller search results in ${library.title}")
                        val contentItems = convertToContentItems(searchResults)
                        // Filter to only include items that actually contain "Thriller" in genre
                        val thrillerItems = contentItems.filter { item ->
                            // For now, just include all search results as they should be thriller-related
                            // In a real implementation, you might want to check actual genre metadata
                            true
                        }
                        android.util.Log.d("PlexRepository", "Converted to ${thrillerItems.size} content items from ${library.title}")
                        allThrillerContent.addAll(thrillerItems)
                    } else {
                        android.util.Log.w("PlexRepository", "Search failed for library ${library.title}: ${response.code()}")
                    }
                } catch (e: Exception) {
                    // Continue with other libraries if one fails
                    android.util.Log.w("PlexRepository", "Failed to search Thriller content in library ${library.title}: ${e.message}")
                }
            }

            android.util.Log.d("PlexRepository", "Total Thriller content found: ${allThrillerContent.size}")
            // Take up to 15 items and return
            val result = allThrillerContent.take(15)
            android.util.Log.d("PlexRepository", "Returning ${result.size} Thriller content items")
            Result.success(result)

        } catch (e: Exception) {
            android.util.Log.e("PlexRepository", "Exception in getThrillerGenreContent: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getRecentTvShows(selectedLibraries: List<String> = emptyList()): Result<List<TvShow>> = withContext(Dispatchers.IO) {
        try {
            val token = currentToken ?: return@withContext Result.failure(Exception("No token available"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            val librariesResult = service.getLibraries(token)
            if (!librariesResult.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch libraries"))
            }

            val allTvLibraries = librariesResult.body()?.mediaContainer?.directories?.filter { it.type == "show" } ?: emptyList()

            // Filter by selected libraries if provided
            val tvLibraries = if (selectedLibraries.isNotEmpty()) {
                allTvLibraries.filter { library ->
                    selectedLibraries.any { selectedLib ->
                        library.key == selectedLib || library.title.equals(selectedLib, ignoreCase = true)
                    }
                }
            } else {
                allTvLibraries
            }

            if (tvLibraries.isEmpty()) {
                return@withContext Result.failure(Exception("No accessible TV show libraries found"))
            }

            // Use the first available TV show library for recent shows
            val showLibrary = tvLibraries.first()
            val response = service.getRecentlyAddedTvShows(showLibrary.key, token, sort = "addedAt:asc", limit = 15)
            if (response.isSuccessful) {
                val shows = response.body()?.mediaContainer?.metadata ?: emptyList()
                val showsWithFullUrls = shows.map { show ->
                    show.copy(
                        thumbUrl = buildImageUrl(show.thumbUrl),
                        artUrl = buildImageUrl(show.artUrl),
                        theme = buildImageUrl(show.theme)
                    )
                }
                Result.success(showsWithFullUrls)
            } else {
                Result.failure(Exception("Failed to fetch recent TV shows: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // New method that builds home dashboard sections using real Plex data
    suspend fun getHomeDashboardSections(
        selectedLibraries: List<String> = emptyList(),
        enabledCollections: List<DashboardCollection>? = null,
        profileId: String? = null
    ): Result<List<ContentSection>> = withContext(Dispatchers.IO) {
        try {
            // Check for Demo Mode
            val tokenToCheck = currentToken ?: authRepository.getAuthToken()
            if (com.purestream.data.demo.DemoData.isDemoToken(tokenToCheck)) {
                Log.d("PlexRepository", "Demo Mode: Returning demo home sections with fixed URIs for package: ${context.packageName}")

                // CRITICAL FIX: Apply URI fixes to all demo data
                val fixedMovies = fixDemoUris(com.purestream.data.demo.DemoData.DEMO_MOVIES)
                val fixedShows = fixDemoTvUris(com.purestream.data.demo.DemoData.DEMO_TV_SHOWS)
                val fixedFeatured = fixDemoUris(listOf(com.purestream.data.demo.DemoData.DEMO_FEATURED_MOVIE)).first()

                // Create demo sections with fixed URIs in specific order: Trending, Popular, Recommended
                val demoSections = listOf(
                    ContentSection(
                        title = "Trending Now",
                        items = fixedMovies.drop(1).take(6).map { movie ->
                            ContentItem(
                                id = movie.ratingKey,
                                title = movie.title,
                                type = ContentType.MOVIE,
                                thumbUrl = movie.thumbUrl,
                                artUrl = movie.artUrl,
                                logoUrl = movie.logoUrl,
                                summary = movie.summary,
                                year = movie.year,
                                rating = movie.rating,
                                duration = movie.duration,
                                contentRating = movie.contentRating,
                                profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN,
                                hasSubtitles = false
                            )
                        }
                    ),
                    ContentSection(
                        title = "Popular Movies",
                        items = fixedMovies.takeLast(6).map { movie ->
                            ContentItem(
                                id = movie.ratingKey,
                                title = movie.title,
                                type = ContentType.MOVIE,
                                thumbUrl = movie.thumbUrl,
                                artUrl = movie.artUrl,
                                logoUrl = movie.logoUrl,
                                summary = movie.summary,
                                year = movie.year,
                                rating = movie.rating,
                                duration = movie.duration,
                                contentRating = movie.contentRating,
                                profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN,
                                hasSubtitles = false
                            )
                        }
                    ),
                    ContentSection(
                        title = "Recommended for You",
                        items = listOf(
                            // Featured movie first
                            ContentItem(
                                id = fixedFeatured.ratingKey,
                                title = fixedFeatured.title,
                                type = ContentType.MOVIE,
                                thumbUrl = fixedFeatured.thumbUrl,
                                artUrl = fixedFeatured.artUrl,
                                logoUrl = fixedFeatured.logoUrl,
                                summary = fixedFeatured.summary,
                                year = fixedFeatured.year,
                                rating = fixedFeatured.rating,
                                duration = fixedFeatured.duration,
                                contentRating = fixedFeatured.contentRating,
                                profanityLevel = fixedFeatured.profanityLevel ?: ProfanityLevel.UNKNOWN,
                                hasSubtitles = false
                            )
                        ) + fixedMovies.take(5).map { movie ->
                            ContentItem(
                                id = movie.ratingKey,
                                title = movie.title,
                                type = ContentType.MOVIE,
                                thumbUrl = movie.thumbUrl,
                                artUrl = movie.artUrl,
                                logoUrl = movie.logoUrl,
                                summary = movie.summary,
                                year = movie.year,
                                rating = movie.rating,
                                duration = movie.duration,
                                contentRating = movie.contentRating,
                                profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN,
                                hasSubtitles = false
                            )
                        }
                    )
                )
                return@withContext Result.success(demoSections)
            }

            val sections = mutableListOf<ContentSection>()

            // If no custom collections provided, use defaults (all 3 hardcoded sections)
            val collectionsToUse = enabledCollections ?: Profile.getDefaultCollections()

            android.util.Log.d("PlexRepository", "=== GET HOME DASHBOARD SECTIONS ===")
            android.util.Log.d("PlexRepository", "Total collections to use: ${collectionsToUse.size}")

            // Filter for enabled collections and sort by order
            val orderedCollections = collectionsToUse
                .filter { it.isEnabled }
                .sortedBy { it.order }

            android.util.Log.d("PlexRepository", "Enabled collections (sorted by order): ${orderedCollections.size}")
            // Only log first 10 enabled collections to reduce verbosity
            orderedCollections.take(10).forEach { col ->
                android.util.Log.d("PlexRepository", "  [${col.order}] ${col.title} (${col.id}, type=${col.type})")
            }
            if (orderedCollections.size > 10) {
                android.util.Log.d("PlexRepository", "  ... and ${orderedCollections.size - 10} more enabled collections")
            }

            // Fetch all sections in parallel
            val sectionsList = coroutineScope {
                orderedCollections.map { collection ->
                    async {
                        android.util.Log.d("PlexRepository", "Processing collection in parallel: ${collection.title} (${collection.id})")
                        when (collection.id) {
                            "trending" -> {
                                // Trending Now - Newest movies
                                getTrendingNow(selectedLibraries).getOrNull()?.let { movies ->
                                    if (movies.isNotEmpty()) {
                                        val contentItems = movies.map { movie ->
                                            val token = currentToken
                                            val fullThumbUrl = movie.thumbUrl?.let { thumbPath ->
                                                if (thumbPath.startsWith("http")) {
                                                    if (token != null && !thumbPath.contains("X-Plex-Token")) "$thumbPath?X-Plex-Token=$token" else thumbPath
                                                } else if (token != null) "$currentServerUrl$thumbPath?X-Plex-Token=$token" else "$currentServerUrl$thumbPath"
                                            }
                                            val fullArtUrl = movie.artUrl?.let { artPath ->
                                                if (artPath.startsWith("http")) {
                                                    if (token != null && !artPath.contains("X-Plex-Token")) "$artPath?X-Plex-Token=$token" else artPath
                                                } else if (token != null) "$currentServerUrl$artPath?X-Plex-Token=$token" else "$currentServerUrl$artPath"
                                            }

                                            ContentItem(
                                                id = movie.ratingKey,
                                                title = movie.title,
                                                type = ContentType.MOVIE,
                                                thumbUrl = fullThumbUrl,
                                                artUrl = fullArtUrl,
                                                logoUrl = buildImageUrl(movie.images?.find { img -> img.type == "clearLogo" }?.url),
                                                summary = movie.summary,
                                                year = movie.year,
                                                rating = movie.rating,
                                                duration = movie.duration,
                                                contentRating = movie.contentRating,
                                                profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN,
                                                hasSubtitles = movie.hasSubtitles
                                            )
                                        }
                                        ContentSection("Trending Now", contentItems)
                                    } else null
                                }
                            }
                            "popular" -> {
                                // Popular Movies - On Deck
                                getPopularMovies(selectedLibraries).getOrNull()?.let { movies ->
                                    if (movies.isNotEmpty()) {
                                        val contentItems = movies.map { movie ->
                                            val token = currentToken
                                            val fullThumbUrl = movie.thumbUrl?.let { thumbPath ->
                                                if (thumbPath.startsWith("http")) {
                                                    if (token != null && !thumbPath.contains("X-Plex-Token")) "$thumbPath?X-Plex-Token=$token" else thumbPath
                                                } else if (token != null) "$currentServerUrl$thumbPath?X-Plex-Token=$token" else "$currentServerUrl$thumbPath"
                                            }
                                            val fullArtUrl = movie.artUrl?.let { artPath ->
                                                if (artPath.startsWith("http")) {
                                                    if (token != null && !artPath.contains("X-Plex-Token")) "$artPath?X-Plex-Token=$token" else artPath
                                                } else if (token != null) "$currentServerUrl$artPath?X-Plex-Token=$token" else "$currentServerUrl$artPath"
                                            }

                                            ContentItem(
                                                id = movie.ratingKey,
                                                title = movie.title,
                                                type = ContentType.MOVIE,
                                                thumbUrl = fullThumbUrl,
                                                artUrl = fullArtUrl,
                                                logoUrl = buildImageUrl(movie.images?.find { img -> img.type == "clearLogo" }?.url),
                                                summary = movie.summary,
                                                year = movie.year,
                                                rating = movie.rating,
                                                duration = movie.duration,
                                                contentRating = movie.contentRating,
                                                profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN,
                                                hasSubtitles = movie.hasSubtitles
                                            )
                                        }
                                        ContentSection("Popular Movies", contentItems)
                                    } else null
                                }
                            }
                            "recommended" -> {
                                // Recommended for You - Recently Added (desc)
                                getRecommendedMovies(selectedLibraries).getOrNull()?.let { movies ->
                                    if (movies.isNotEmpty()) {
                                        val contentItems = movies.map { movie ->
                                            val token = currentToken
                                            val fullThumbUrl = movie.thumbUrl?.let { thumbPath ->
                                                if (thumbPath.startsWith("http")) {
                                                    if (token != null && !thumbPath.contains("X-Plex-Token")) "$thumbPath?X-Plex-Token=$token" else thumbPath
                                                } else if (token != null) "$currentServerUrl$thumbPath?X-Plex-Token=$token" else "$currentServerUrl$thumbPath"
                                            }
                                            val fullArtUrl = movie.artUrl?.let { artPath ->
                                                if (artPath.startsWith("http")) {
                                                    if (token != null && !artPath.contains("X-Plex-Token")) "$artPath?X-Plex-Token=$token" else artPath
                                                } else if (token != null) "$currentServerUrl$artPath?X-Plex-Token=$token" else "$currentServerUrl$artPath"
                                            }

                                            ContentItem(
                                                id = movie.ratingKey,
                                                title = movie.title,
                                                type = ContentType.MOVIE,
                                                thumbUrl = fullThumbUrl,
                                                artUrl = fullArtUrl,
                                                logoUrl = buildImageUrl(movie.images?.find { img -> img.type == "clearLogo" }?.url),
                                                summary = movie.summary,
                                                year = movie.year,
                                                rating = movie.rating,
                                                duration = movie.duration,
                                                contentRating = movie.contentRating,
                                                profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN,
                                                hasSubtitles = movie.hasSubtitles
                                            )
                                        }
                                        ContentSection("Recommended for You", contentItems)
                                    } else null
                                }
                            }
                            else -> {
                                // Handle Plex and Gemini collections
                                when (collection.type) {
                                    CollectionType.PLEX -> {
                                        if (collection.id.startsWith("plex_")) {
                                            getPlexCollectionItems(collection, selectedLibraries).getOrNull()?.let { items ->
                                                if (items.isNotEmpty()) ContentSection(collection.title, items) else null
                                            }
                                        } else null
                                    }
                                    CollectionType.GEMINI -> {
                                        if (collection.id.startsWith("gemini_")) {
                                            getGeminiCollectionItems(collection, profileId).getOrNull()?.let { items ->
                                                if (items.isNotEmpty()) ContentSection(collection.title, items) else null
                                            }
                                        } else null
                                    }
                                    else -> null
                                }
                            }
                        }
                    }
                }
            }.awaitAll().filterNotNull()

            sections.addAll(sectionsList)

            android.util.Log.d("PlexRepository", "=== FINAL SECTIONS COUNT: ${sections.size} ===")
            sections.forEachIndexed { index, section ->
                android.util.Log.d("PlexRepository", "  Section ${index + 1}: ${section.title} (${section.items.size} items)")
            }

            Result.success(sections)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch items from a specific Plex collection
     * FIX: Treat Managed/Smart collections the same as standard collections to get their children properly.
     */
    private suspend fun getPlexCollectionItems(collection: DashboardCollection, selectedLibraries: List<String>): Result<List<ContentItem>> = withContext(Dispatchers.IO) {
        try {
            val token = currentToken ?: return@withContext Result.failure(Exception("No authentication token"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            android.util.Log.d("PlexRepository", "*** FETCHING COLLECTION ITEMS ***")
            android.util.Log.d("PlexRepository", "  Collection: ${collection.title}")
            android.util.Log.d("PlexRepository", "  ID: ${collection.id}")
            android.util.Log.d("PlexRepository", "  Type: ${collection.type}")

            // Extract the ratingKey from the collection ID (format: "plex_12345")
            val ratingKey = collection.id.removePrefix("plex_")

            // FIX: Treat Managed/Smart collections properly by using their 'key' property.
            // 1. Fetch collection details to get the correct content URL ('key')
            var collectionKey: String? = null
            
            try {
                val detailsResponse = retryWithFallback { service, token ->
                    service.getCollectionDetails(ratingKey, token)
                }
                
                if (detailsResponse?.isSuccessful == true) {
                    val details = detailsResponse.body()?.mediaContainer?.metadata?.firstOrNull()
                    collectionKey = details?.key
                    android.util.Log.d("PlexRepository", "  Fetched collection key: $collectionKey")
                }
            } catch (e: Exception) {
                android.util.Log.w("PlexRepository", "  Failed to fetch collection details: ${e.message}")
            }

            // 2. Fetch items using the key (if available) or fallback to children endpoint
            val response = if (collectionKey != null) {
                android.util.Log.d("PlexRepository", "  Using collection key URL: $collectionKey")
                retryWithFallback { service, token ->
                    service.getItemsFromUrl(collectionKey, token)
                }
            } else {
                android.util.Log.d("PlexRepository", "  Using standard children endpoint")
                retryWithFallback { service, token ->
                    service.getCollectionItems(ratingKey, token)
                }
            }

            android.util.Log.d("PlexRepository", "  Response code: ${response?.code()}")
            android.util.Log.d("PlexRepository", "  Response successful: ${response?.isSuccessful}")

            if (response == null || !response.isSuccessful) {
                android.util.Log.e("PlexRepository", "  ERROR: Failed to fetch collection items - HTTP ${response?.code()}: ${response?.message()}")
                return@withContext Result.success(emptyList())
            }

            if (response.body() == null) {
                android.util.Log.e("PlexRepository", "  ERROR: Response body is null")
                return@withContext Result.success(emptyList())
            }

            var mediaContainer = response.body()?.mediaContainer
            var movies = mediaContainer?.metadata ?: emptyList()

            android.util.Log.d("PlexRepository", "  MediaContainer size: ${mediaContainer?.size}")
            android.util.Log.d("PlexRepository", "  Metadata count: ${movies.size}")
            android.util.Log.d("PlexRepository", "  Standard endpoint returned ${movies.size} items")

            // FALLBACK: If standard endpoint returns 0 items, try searching by collection tag
            if (movies.isEmpty()) {
                android.util.Log.w("PlexRepository", "  ⚠️ Standard endpoint returned 0 items for '${collection.title}'")
                android.util.Log.w("PlexRepository", "  🔄 Trying fallback: Search by collection tag...")

                try {
                    // Search all movie libraries for items with this collection tag
                    val searchResults = mutableListOf<Movie>()

                    // Use retryWithFallback for library fetching
                    val librariesResult = retryWithFallback { service, token ->
                        service.getLibraries(token)
                    }

                    if (librariesResult?.isSuccessful == true) {
                        val libraries = librariesResult.body()?.mediaContainer?.directories?.filter { it.type == "movie" } ?: emptyList()

                        for (library in libraries) {
                            try {
                                // Use retryWithFallback for search
                                val searchResponse = retryWithFallback { service, token ->
                                    service.searchLibrary(
                                        sectionId = library.key,
                                        query = collection.title,
                                        token = token
                                    )
                                }

                                if (searchResponse?.isSuccessful == true) {
                                    val searchMovies = searchResponse.body()?.mediaContainer?.metadata ?: emptyList()
                                    // Filter to only movies that have this collection in their tags
                                    val matchingMovies = searchMovies.filter { movie ->
                                        movie.collections?.any { it.tag.equals(collection.title, ignoreCase = true) } == true
                                    }
                                    searchResults.addAll(matchingMovies)
                                    android.util.Log.d("PlexRepository", "    Found ${matchingMovies.size} matching items in library '${library.title}'")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("PlexRepository", "    Error searching library: ${e.message}")
                            }
                        }
                    }

                    if (searchResults.isNotEmpty()) {
                        movies = searchResults.distinctBy { it.ratingKey }
                        android.util.Log.d("PlexRepository", "  ✅ Fallback search found ${movies.size} items!")
                    } else {
                        android.util.Log.w("PlexRepository", "  ❌ Fallback search also returned 0 items")
                        android.util.Log.w("PlexRepository", "     Collection '${collection.title}' will NOT appear on home screen")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlexRepository", "  ❌ Fallback search failed: ${e.message}")
                }
            } else {
                android.util.Log.d("PlexRepository", "  ✓ Collection has ${movies.size} items")
                if (movies.isNotEmpty()) {
                    android.util.Log.d("PlexRepository", "  First item: ${movies.first().title}")
                }
            }

            // Convert movies to ContentItems
            val contentItems = movies.map { movie ->
                // Plex returns relative URLs - prepend server URL and add token for authentication
                val token = currentToken
                val fullThumbUrl = movie.thumbUrl?.let {
                    val fullUrl = if (it.startsWith("http")) {
                        if (token != null && !it.contains("X-Plex-Token")) {
                            "$it?X-Plex-Token=$token"
                        } else it
                    } else {
                        if (token != null) {
                            "$currentServerUrl$it?X-Plex-Token=$token"
                        } else {
                            "$currentServerUrl$it"
                        }
                    }
                    fullUrl
                }

                // Handle Art URL logic similarly...
                val fullArtUrl = movie.artUrl?.let {
                    if (it.startsWith("http")) {
                        if (token != null && !it.contains("X-Plex-Token")) {
                            "$it?X-Plex-Token=$token"
                        } else it
                    } else {
                        if (token != null) {
                            "$currentServerUrl$it?X-Plex-Token=$token"
                        } else {
                            "$currentServerUrl$it"
                        }
                    }
                }

                ContentItem(
                    id = movie.ratingKey,
                    title = movie.title,
                    type = ContentType.MOVIE,
                    thumbUrl = fullThumbUrl,
                    artUrl = fullArtUrl,
                    summary = movie.summary,
                    year = movie.year,
                    rating = movie.rating,
                    duration = movie.duration,
                    contentRating = movie.contentRating,
                    profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN,
                    hasSubtitles = movie.hasSubtitles
                )
            }

            Result.success(contentItems)
        } catch (e: Exception) {
            android.util.Log.e("PlexRepository", "*** EXCEPTION fetching collection items for ${collection.title} ***", e)
            Result.success(emptyList())
        }
    }

    /**
     * Fetch items from a Gemini AI-curated collection
     * Retrieves cached movie matches from the database and filters all movies by those rating keys
     */
    private suspend fun getGeminiCollectionItems(collection: DashboardCollection, profileId: String?): Result<List<ContentItem>> = withContext(Dispatchers.IO) {
        try {
            val token = currentToken ?: return@withContext Result.failure(Exception("No authentication token"))

            android.util.Log.d("PlexRepository", "*** FETCHING GEMINI COLLECTION ITEMS ***")
            android.util.Log.d("PlexRepository", "  Collection: ${collection.title}")
            android.util.Log.d("PlexRepository", "  ID: ${collection.id}")
            android.util.Log.d("PlexRepository", "  ProfileID: $profileId")

            // Check if profileId is available
            if (profileId == null) {
                android.util.Log.w("PlexRepository", "  ⚠️ No profileId provided - cannot fetch Gemini collection")
                return@withContext Result.success(emptyList())
            }

            // Get cached movie rating keys from database
            val database = AppDatabase.getDatabase(context)
            val geminiDao = database.geminiCachedMovieDao()
            val cachedMovies = geminiDao.getMoviesForCollection(profileId, collection.id)

            android.util.Log.d("PlexRepository", "  Found ${cachedMovies.size} cached movies for this collection")

            if (cachedMovies.isEmpty()) {
                android.util.Log.w("PlexRepository", "  ⚠️ No cached movies found")
                return@withContext Result.success(emptyList())
            }

            // Extract rating keys from cached movies
            val ratingKeys = cachedMovies.map { it.movieRatingKey }.toSet()
            android.util.Log.d("PlexRepository", "  Rating keys to fetch: ${ratingKeys.joinToString(", ")}")

            // Fetch the actual movie objects and convert to ContentItems
            val contentItems = mutableListOf<ContentItem>()

            for (ratingKey in ratingKeys) {
                try {
                    val movieResult = getMovieByRatingKey(ratingKey)
                    movieResult.getOrNull()?.let { movie ->
                        // Prepend server URL and add token for authentication
                        val fullThumbUrl = movie.thumbUrl?.let {
                            if (it.startsWith("http")) {
                                if (token != null && !it.contains("X-Plex-Token")) {
                                    "$it?X-Plex-Token=$token"
                                } else it
                            } else {
                                if (token != null) {
                                    "$currentServerUrl$it?X-Plex-Token=$token"
                                } else {
                                    "$currentServerUrl$it"
                                }
                            }
                        }
                        val fullArtUrl = movie.artUrl?.let {
                            if (it.startsWith("http")) {
                                if (token != null && !it.contains("X-Plex-Token")) {
                                    "$it?X-Plex-Token=$token"
                                } else it
                            } else {
                                if (token != null) {
                                    "$currentServerUrl$it?X-Plex-Token=$token"
                                } else {
                                    "$currentServerUrl$it"
                                }
                            }
                        }

                        contentItems.add(ContentItem(
                            id = movie.ratingKey,
                            title = movie.title,
                            type = ContentType.MOVIE,
                            thumbUrl = fullThumbUrl,
                            artUrl = fullArtUrl,
                            summary = movie.summary,
                            year = movie.year,
                            rating = movie.rating,
                            duration = movie.duration,
                            contentRating = movie.contentRating,
                            profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN,
                            hasSubtitles = movie.hasSubtitles
                        ))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlexRepository", "  Failed to fetch movie with ratingKey=$ratingKey: ${e.message}")
                }
            }

            // Sort by the original order from the cached list
            val sortedItems = contentItems.sortedBy { item ->
                cachedMovies.indexOfFirst { it.movieRatingKey == item.id }
            }

            android.util.Log.d("PlexRepository", "  ✓ Successfully fetched ${sortedItems.size} Gemini collection items")
            Result.success(sortedItems)

        } catch (e: Exception) {
            android.util.Log.e("PlexRepository", "*** EXCEPTION fetching Gemini collection items for ${collection.title} ***", e)
            Result.success(emptyList())
        }
    }

    /**
     * Fetch a single movie by its rating key
     */
    private suspend fun getMovieByRatingKey(ratingKey: String): Result<Movie> = withContext(Dispatchers.IO) {
        try {
            // Ensure connection is available (restores from saved settings if needed)
            if (!ensureConnection()) {
                return@withContext Result.failure(Exception("Could not restore Plex connection"))
            }

            val token = currentToken!!
            val service = serverApiService!!

            // Use the metadata endpoint to fetch a single item by rating key
            val response = retryWithFallback { service, token ->
                service.getMovieDetails(ratingKey, token)
            }

            if (response == null || !response.isSuccessful || response.body() == null) {
                return@withContext Result.failure(Exception("Failed to fetch movie with ratingKey=$ratingKey"))
            }

            val movie = response.body()?.mediaContainer?.metadata?.firstOrNull()
                ?: return@withContext Result.failure(Exception("No movie found with ratingKey=$ratingKey"))

            Result.success(movie)
        } catch (e: Exception) {
            android.util.Log.e("PlexRepository", "Error fetching movie by ratingKey=$ratingKey: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch Plex collections from all movie/TV libraries
     * Returns list of DashboardCollection objects (disabled by default)
     */
    suspend fun getPlexCollections(selectedLibraries: List<String> = emptyList()): Result<List<DashboardCollection>> = withContext(Dispatchers.IO) {
        try {
            val token = currentToken ?: return@withContext Result.failure(Exception("No authentication token"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            val collections = mutableListOf<DashboardCollection>()

            // Get all libraries
            val librariesResult = service.getLibraries(token)
            if (!librariesResult.isSuccessful || librariesResult.body() == null) {
                return@withContext Result.success(emptyList())
            }

            val libraries = librariesResult.body()?.mediaContainer?.directories ?: emptyList()

            android.util.Log.d("PlexRepository", "Total libraries available: ${libraries.size}")
            libraries.forEach { lib ->
                android.util.Log.d("PlexRepository", "  Library: '${lib.title}' (key='${lib.key}', type='${lib.type}')")
            }

            // Filter libraries: only include selected Movie libraries
            val filteredLibraries = if (selectedLibraries.isNotEmpty()) {
                android.util.Log.d("PlexRepository", "Filtering for selected libraries: $selectedLibraries")
                libraries.filter { library ->
                    val isMovie = library.type == "movie"
                    val isSelected = selectedLibraries.contains(library.key)
                    android.util.Log.d("PlexRepository", "  Checking '${library.title}': type=${library.type} (isMovie=$isMovie), key=${library.key} (isSelected=$isSelected)")
                    isMovie && isSelected
                }
            } else {
                android.util.Log.d("PlexRepository", "No libraries selected, fetching from all movie libraries")
                // If no libraries selected, fetch from all movie libraries
                libraries.filter { it.type == "movie" }
            }

            android.util.Log.d("PlexRepository", "Fetching collections from ${filteredLibraries.size} movie libraries (selected: $selectedLibraries)")
            filteredLibraries.forEach { lib ->
                android.util.Log.d("PlexRepository", "  Will fetch from: '${lib.title}' (${lib.key})")
            }

            // Fetch collections from each filtered movie library
            for (library in filteredLibraries) {
                try {
                    val collectionsResult = service.getCollections(library.key, token)
                    if (collectionsResult.isSuccessful) {
                        val plexCollections = collectionsResult.body()?.mediaContainer?.metadata ?: emptyList()

                        android.util.Log.d("PlexRepository", "Found ${plexCollections.size} collections in library '${library.title}'")

                        plexCollections.forEach { collection ->
                            val isSmart = collection.smart == true
                            val itemCount = collection.childCount ?: 0
                            android.util.Log.d("PlexRepository", "  Collection: '${collection.title}' (ratingKey=${collection.ratingKey}, smart=$isSmart, childCount=$itemCount)")

                            // Only include collections with items
                            if (itemCount > 0) {
                                collections.add(
                                    DashboardCollection(
                                        id = "plex_${collection.ratingKey}",
                                        title = collection.title,
                                        type = CollectionType.PLEX,
                                        isEnabled = false,  // Disabled by default
                                        order = Int.MAX_VALUE,  // Will be reordered when enabled
                                        itemCount = itemCount
                                    )
                                )
                                android.util.Log.d("PlexRepository", "    ✓ Added collection with $itemCount items")
                            } else {
                                android.util.Log.w("PlexRepository", "    ✗ Skipped collection with 0 items")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlexRepository", "Error fetching collections from library ${library.title}: ${e.message}")
                    // Continue with other libraries
                }
            }

            Result.success(collections.distinctBy { it.id }.sortedBy { it.title })
        } catch (e: Exception) {
            android.util.Log.e("PlexRepository", "Error fetching Plex collections: ${e.message}")
            Result.success(emptyList())  // Return empty list on error, don't fail
        }
    }

    suspend fun getCuratedSections(): Result<List<ContentSection>> = withContext(Dispatchers.IO) {
        try {
            val sections = mutableListOf<ContentSection>()

            // Get recently added content
            getRecentlyAdded().getOrNull()?.let { recentItems ->
                if (recentItems.isNotEmpty()) {
                    sections.add(ContentSection("Recently Added", recentItems.take(10)))
                }
            }

            // Get content from each library
            val token = currentToken ?: return@withContext Result.failure(Exception("No authentication token"))
            val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))

            try {
                val librariesResult = service.getLibraries(token)
                if (librariesResult.isSuccessful) {
                    val libraries = librariesResult.body()?.mediaContainer?.directories ?: emptyList()

                    for (library in libraries.take(4)) { // Limit to first 4 libraries
                        val content = when (library.type) {
                            "movie" -> {
                                val moviesResult = getMovies(library.key)
                                moviesResult.getOrNull()?.take(20)?.map { movie ->
                                    ContentItem(
                                        id = movie.ratingKey,
                                        title = movie.title,
                                        type = ContentType.MOVIE,
                                        thumbUrl = buildImageUrl(movie.thumbUrl),
                                        artUrl = buildImageUrl(movie.artUrl),
                                        summary = movie.summary,
                                        year = movie.year,
                                        rating = movie.rating,
                                        duration = movie.duration,
                                        contentRating = movie.contentRating,
                                        profanityLevel = movie.profanityLevel ?: ProfanityLevel.UNKNOWN,
                                        hasSubtitles = movie.hasSubtitles
                                    )
                                } ?: emptyList()
                            }
                            "show" -> {
                                val showsResult = getTvShows(library.key)
                                showsResult.getOrNull()?.take(20)?.map { show ->
                                    ContentItem(
                                        id = show.ratingKey,
                                        title = show.title,
                                        type = ContentType.TV_SHOW,
                                        thumbUrl = buildImageUrl(show.thumbUrl),
                                        artUrl = buildImageUrl(show.artUrl),
                                        summary = show.summary,
                                        year = show.year,
                                        rating = show.rating,
                                        duration = null,
                                        contentRating = show.contentRating,
                                        profanityLevel = ProfanityLevel.UNKNOWN,
                                        hasSubtitles = false
                                    )
                                } ?: emptyList()
                            }
                            else -> emptyList()
                        }

                        if (content.isNotEmpty()) {
                            sections.add(ContentSection(library.title, content))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlexRepository", "Error loading library content", e)
            }

            // Add seasonal/themed sections if we have space
            if (sections.size < 6) {
                sections.addAll(createSeasonalSections())
            }

            Result.success(sections)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildImageUrl(thumbPath: String?): String? {
        if (thumbPath.isNullOrEmpty()) return null

        // FIX: If it's already a full android resource or file URI, return it as-is
        if (thumbPath.startsWith("android.resource") || thumbPath.startsWith("file://")) {
            Log.d(TAG, "buildImageUrl: Already a resource URI: $thumbPath")
            return thumbPath
        }

        // FIX: If it's a simple drawable name (demo mode), convert to resource ID for Coil
        // Demo drawable names don't contain "/" and don't start with "http"
        if (!thumbPath.contains("/") && !thumbPath.startsWith("http", ignoreCase = true)) {
            try {
                val resourceId = context.resources.getIdentifier(
                    thumbPath,
                    "drawable",
                    context.packageName
                )
                if (resourceId != 0) {
                    // Coil can load android.resource URIs directly
                    val resourceUri = "android.resource://${context.packageName}/$resourceId"
                    Log.d(TAG, "buildImageUrl: Converted drawable '$thumbPath' to URI: $resourceUri (resourceId: $resourceId)")
                    return resourceUri
                } else {
                    Log.w(TAG, "buildImageUrl: Could not find drawable resource: $thumbPath")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve drawable resource: $thumbPath", e)
            }
        }

        // Regular Plex image URL
        val plexUrl = com.purestream.utils.PlexUrlBuilder.buildImageUrl(thumbPath, currentServerUrl, currentToken)
        Log.d(TAG, "buildImageUrl: Built Plex URL from '$thumbPath': $plexUrl")
        return plexUrl
    }

    private fun createSeasonalSections(): List<ContentSection> {
        // This would be enhanced with actual AI curation based on metadata and seasons
        val currentMonth = java.time.LocalDate.now().monthValue

        return when (currentMonth) {
            12, 1, 2 -> listOf(
                ContentSection("Winter Favorites", emptyList()),
                ContentSection("Holiday Classics", emptyList())
            )
            3, 4, 5 -> listOf(
                ContentSection("Spring Awakening", emptyList()),
                ContentSection("Feel-Good Films", emptyList())
            )
            6, 7, 8 -> listOf(
                ContentSection("Summer Blockbusters", emptyList()),
                ContentSection("Adventure Time", emptyList())
            )
            9, 10, 11 -> listOf(
                ContentSection("Halloween Thrills", emptyList()),
                ContentSection("Autumn Atmosphere", emptyList())
            )
            else -> listOf(
                ContentSection("Trending Now", emptyList()),
                ContentSection("Popular Picks", emptyList())
            )
        }
    }

    private fun convertToContentItems(items: List<Any>): List<ContentItem> {
        // Convert various Plex metadata types to ContentItem
        return items.mapNotNull { item ->
            when (item) {
                is Movie -> ContentItem(
                    id = item.ratingKey,
                    title = item.title,
                    type = ContentType.MOVIE,
                    thumbUrl = item.thumbUrl,
                    artUrl = item.artUrl,
                    summary = item.summary,
                    year = item.year,
                    rating = item.rating,
                    duration = item.duration,
                    contentRating = item.contentRating,
                    profanityLevel = item.profanityLevel ?: ProfanityLevel.UNKNOWN,
                    hasSubtitles = item.hasSubtitles
                )
                is Episode -> ContentItem(
                    id = item.ratingKey,
                    title = item.title,
                    type = ContentType.EPISODE,
                    thumbUrl = item.thumbUrl,
                    artUrl = null, // Episodes don't have artUrl property
                    summary = item.summary,
                    year = item.year,
                    rating = item.rating,
                    duration = item.duration,
                    contentRating = null, // Episodes typically don't have their own content rating
                    profanityLevel = item.profanityLevel ?: ProfanityLevel.UNKNOWN,
                    hasSubtitles = item.hasSubtitles
                )
                is TvShow -> ContentItem(
                    id = item.ratingKey,
                    title = item.title,
                    type = ContentType.TV_SHOW,
                    thumbUrl = item.thumbUrl,
                    artUrl = item.artUrl,
                    summary = item.summary,
                    year = item.year,
                    rating = item.rating,
                    duration = null,
                    contentRating = item.contentRating,
                    profanityLevel = ProfanityLevel.UNKNOWN,
                    hasSubtitles = false
                )
                else -> null
            }
        }
    }

    /**
     * Update timeline to report playback state to Plex server
     */
    suspend fun updateTimeline(
        ratingKey: String,
        key: String,
        state: String, // "playing", "paused", or "stopped"
        position: Long, // milliseconds
        duration: Long, // milliseconds
        deviceName: String = android.os.Build.MODEL ?: "Android TV"
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))
                val token = currentToken ?: return@withContext Result.failure(Exception("No auth token"))

                val clientId = "f7f96c82-17e5-4b11-a52f-74b1107bd0fb"

                val response = service.updateTimeline(
                    token = token,
                    ratingKey = ratingKey,
                    key = key,
                    state = state,
                    time = position,
                    duration = duration,
                    clientId = clientId,
                    deviceName = deviceName
                )

                if (response.isSuccessful) {
                    Log.d("PlexRepository", "Timeline updated: $ratingKey at $position/$duration ($state)")
                    Result.success(true)
                } else {
                    Log.w("PlexRepository", "Timeline update failed: ${response.code()}")
                    Result.failure(Exception("Timeline update failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e("PlexRepository", "Timeline update error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Mark content as watched (scrobble)
     */
    suspend fun scrobble(
        key: String,
        identifier: String = "com.plexapp.plugins.library"
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))
                val token = currentToken ?: return@withContext Result.failure(Exception("No auth token"))

                val clientId = "f7f96c82-17e5-4b11-a52f-74b1107bd0fb"

                val response = service.scrobble(
                    token = token,
                    key = key,
                    identifier = identifier,
                    clientId = clientId
                )

                if (response.isSuccessful) {
                    Log.d("PlexRepository", "Content scrobbled: $key")
                    Result.success(true)
                } else {
                    Log.w("PlexRepository", "Scrobble failed: ${response.code()}")
                    Result.failure(Exception("Scrobble failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e("PlexRepository", "Scrobble error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Unmark content as watched (unscrobble)
     */
    suspend fun unscrobble(
        key: String,
        identifier: String = "com.plexapp.plugins.library"
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val service = serverApiService ?: return@withContext Result.failure(Exception("No server connection"))
                val token = currentToken ?: return@withContext Result.failure(Exception("No auth token"))

                val clientId = "f7f96c82-17e5-4b11-a52f-74b1107bd0fb"

                val response = service.unscrobble(
                    token = token,
                    key = key,
                    identifier = identifier,
                    clientId = clientId
                )

                if (response.isSuccessful) {
                    Log.d("PlexRepository", "Content unscrobbled: $key")
                    Result.success(true)
                } else {
                    Log.w("PlexRepository", "Unscrobble failed: ${response.code()}")
                    Result.failure(Exception("Unscrobble failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e("PlexRepository", "Unscrobble error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Clear all connection data on logout
     */
    fun clearConnection() {
        Log.d("PlexRepository", "Clearing Plex connection")
        currentToken = null
        currentServerUrl = null
        serverApiService = null
        primaryConnection = null
        fallbackConnection = null
        isUsingFallback = false

        // CRITICAL FIX: Clear the persisted auth token to prevent demo mode leak
        // Without this, the demo token stays in SharedPreferences and hijacks normal profiles
        authRepository.clearAuthToken()
        Log.d(TAG, "Cleared auth token from storage to prevent demo mode leak")
    }
}