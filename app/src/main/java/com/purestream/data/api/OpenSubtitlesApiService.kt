package com.purestream.data.api

import com.purestream.data.model.OpenSubtitlesDownloadRequest
import com.purestream.data.model.OpenSubtitlesDownloadResponse
import com.purestream.data.model.OpenSubtitlesSearchResponse
import com.purestream.data.model.SubtitleResult
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface OpenSubtitlesApiService {
    
    @GET("subtitles")
    suspend fun searchSubtitles(
        @Header("Api-Key") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("User-Agent") userAgent: String = "PureStream v1.0",
        @Query("imdb_id") imdbId: String? = null,
        @Query("query") query: String? = null,
        @Query("season_number") seasonNumber: Int? = null,
        @Query("episode_number") episodeNumber: Int? = null,
        @Query("languages") languages: String = "en",
        @Query("moviehash") movieHash: String? = null
    ): Response<ResponseBody>
    
    @POST("download")
    suspend fun downloadSubtitle(
        @Header("Api-Key") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("User-Agent") userAgent: String = "PureStream v1.0",
        @Body request: OpenSubtitlesDownloadRequest
    ): Response<OpenSubtitlesDownloadResponse>
    
    @GET
    suspend fun downloadSubtitleFile(
        @Url url: String
    ): Response<ResponseBody>
}