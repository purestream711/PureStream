package com.purestream.data.api

import com.purestream.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface PlexApiService {
    
    @GET("resources.xml")
    suspend fun getUserServers(@Query("X-Plex-Token") token: String): Response<okhttp3.ResponseBody>
    
    @GET("library/sections")
    suspend fun getLibraries(
        @Header("X-Plex-Token") token: String
    ): Response<PlexResponse<PlexLibrary>>
    
    @GET("library/sections/{libraryId}/all")
    suspend fun getMovies(
        @Path("libraryId") libraryId: String,
        @Header("X-Plex-Token") token: String,
        @Query("type") type: Int = 1,
        @Query("includeExtra") includeExtra: Int = 1
    ): Response<PlexResponse<Movie>>
    
    @GET("library/sections/{libraryId}/all")
    suspend fun getTvShows(
        @Path("libraryId") libraryId: String,
        @Header("X-Plex-Token") token: String,
        @Query("type") type: Int = 2
    ): Response<PlexResponse<TvShow>>
    
    @GET("library/metadata/{showId}/children")
    suspend fun getSeasons(
        @Path("showId") showId: String,
        @Header("X-Plex-Token") token: String
    ): Response<PlexResponse<Season>>
    
    @GET("library/metadata/{seasonId}/children")
    suspend fun getEpisodes(
        @Path("seasonId") seasonId: String,
        @Header("X-Plex-Token") token: String
    ): Response<PlexResponse<Episode>>
    
    @GET("library/metadata/{itemId}")
    suspend fun getItemDetails(
        @Path("itemId") itemId: String,
        @Header("X-Plex-Token") token: String
    ): Response<PlexResponse<Any>>
    
    @GET("library/metadata/{itemId}")
    suspend fun getMovieDetails(
        @Path("itemId") itemId: String,
        @Header("X-Plex-Token") token: String
    ): Response<PlexResponse<Movie>>
    
    @GET("library/metadata/{itemId}")
    suspend fun getTvShowDetails(
        @Path("itemId") itemId: String,
        @Header("X-Plex-Token") token: String
    ): Response<PlexResponse<TvShow>>
    
    @GET("library/metadata/{episodeId}")
    suspend fun getEpisodeDetails(
        @Path("episodeId") episodeId: String,
        @Header("X-Plex-Token") token: String
    ): Response<PlexResponse<Episode>>
    
    @GET("hubs/search")
    suspend fun performSearch(
        @Header("X-Plex-Token") token: String,
        @Query("query") query: String,
        @Query("limit") limit: Int = 20,
        @Query("sectionId") sectionId: String? = null
    ): Response<PlexResponse<SearchResult>>
    
    
    @GET("library/recentlyAdded")
    suspend fun getRecentlyAdded(
        @Header("X-Plex-Token") token: String,
        @Query("limit") limit: Int = 20
    ): Response<PlexResponse<Any>>
    
    // Home Dashboard Endpoints
    @GET("library/sections/{libraryId}/newest")
    suspend fun getNewestMovies(
        @Path("libraryId") libraryId: String,
        @Header("X-Plex-Token") token: String,
        @Query("type") type: Int = 1, // 1 = movies
        @Query("limit") limit: Int = 20
    ): Response<PlexResponse<Movie>>
    
    @GET("library/sections/{libraryId}/onDeck")
    suspend fun getOnDeckMovies(
        @Path("libraryId") libraryId: String,
        @Header("X-Plex-Token") token: String,
        @Query("type") type: Int = 1, // 1 = movies
        @Query("limit") limit: Int = 20
    ): Response<PlexResponse<Movie>>
    
    @GET("library/sections/{libraryId}/recentlyAdded")
    suspend fun getRecentlyAddedMovies(
        @Path("libraryId") libraryId: String,
        @Header("X-Plex-Token") token: String,
        @Query("type") type: Int = 1, // 1 = movies
        @Query("sort") sort: String = "addedAt:desc", // desc for "Recommended for You", asc for "Recently Added"
        @Query("limit") limit: Int = 20
    ): Response<PlexResponse<Movie>>
    
    @GET("library/sections/{libraryId}/recentlyAdded")
    suspend fun getRecentlyAddedTvShows(
        @Path("libraryId") libraryId: String,
        @Header("X-Plex-Token") token: String,
        @Query("type") type: Int = 2, // 2 = tv shows
        @Query("sort") sort: String = "addedAt:asc", // ascending order
        @Query("limit") limit: Int = 20
    ): Response<PlexResponse<TvShow>>

    // Collections Endpoint
    @GET("library/sections/{libraryId}/collections")
    suspend fun getCollections(
        @Path("libraryId") libraryId: String,
        @Header("X-Plex-Token") token: String
    ): Response<PlexResponse<PlexCollection>>

    @GET("library/metadata/{collectionId}/children")
    suspend fun getCollectionItems(
        @Path("collectionId") collectionId: String,
        @Header("X-Plex-Token") token: String
    ): Response<PlexResponse<Movie>>

    @GET("library/sections/{sectionId}/all")
    suspend fun searchLibrary(
        @Path("sectionId") sectionId: String,
        @Query("title") query: String,
        @Header("X-Plex-Token") token: String
    ): Response<PlexResponse<Movie>>

    // Timeline/Progress Tracking Endpoints
    @GET(":/timeline")
    suspend fun updateTimeline(
        @Header("X-Plex-Token") token: String,
        @Query("ratingKey") ratingKey: String,
        @Query("key") key: String,
        @Query("state") state: String, // "playing", "paused", or "stopped"
        @Query("time") time: Long, // Current position in milliseconds
        @Query("duration") duration: Long, // Total duration in milliseconds
        @Query("X-Plex-Client-Identifier") clientId: String,
        @Query("X-Plex-Product") product: String = "Pure Stream",
        @Query("X-Plex-Version") version: String = "1.0.0",
        @Query("X-Plex-Platform") platform: String = "Android TV",
        @Query("X-Plex-Device") device: String = "Android TV",
        @Query("X-Plex-Device-Name") deviceName: String
    ): Response<okhttp3.ResponseBody>

    @GET(":/progress")
    suspend fun updateProgress(
        @Header("X-Plex-Token") token: String,
        @Query("key") key: String,
        @Query("identifier") identifier: String, // Type of identifier (e.g., "com.plexapp.plugins.library")
        @Query("time") time: Long, // Current position in milliseconds
        @Query("X-Plex-Client-Identifier") clientId: String
    ): Response<okhttp3.ResponseBody>

    @GET(":/scrobble")
    suspend fun scrobble(
        @Header("X-Plex-Token") token: String,
        @Query("key") key: String,
        @Query("identifier") identifier: String, // Type of identifier
        @Query("X-Plex-Client-Identifier") clientId: String
    ): Response<okhttp3.ResponseBody>

    @GET(":/unscrobble")
    suspend fun unscrobble(
        @Header("X-Plex-Token") token: String,
        @Query("key") key: String,
        @Query("identifier") identifier: String,
        @Query("X-Plex-Client-Identifier") clientId: String
    ): Response<okhttp3.ResponseBody>
}

data class PlexServer(
    val name: String,
    val host: String,
    val port: Int,
    val machineIdentifier: String,
    val version: String,
    val accessToken: String = ""
)


data class SearchResult(
    val ratingKey: String,
    val key: String,
    val title: String,
    val type: String, // "movie", "show", "episode"
    val summary: String?,
    val thumb: String?,
    val art: String?,
    val year: Int?,
    val rating: Float?,
    val reason: String?, // Why this item was returned
    val reasonTitle: String?, // Context for the result
    val reasonID: String? // Identifier for the matching item
)