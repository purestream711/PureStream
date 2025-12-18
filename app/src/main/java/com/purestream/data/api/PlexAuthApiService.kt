package com.purestream.data.api

import com.purestream.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface PlexAuthApiService {
    
    @Headers(
        "X-Plex-Client-Name: Pure Stream",
        "X-Plex-Client-Version: 1.0.0",
        "X-Plex-Client-Identifier: pure-stream-tv",
        "X-Plex-Platform: Android TV",
        "X-Plex-Device-Name: Pure Stream TV"
    )
    @FormUrlEncoded
    @POST("users/sign_in.json")
    suspend fun signIn(
        @Field("user[login]") login: String,
        @Field("user[password]") password: String
    ): Response<PlexAuthResponse>
    
    @Headers("Accept: application/json")
    @POST("api/v2/pins")
    suspend fun createPin(
        @Query("strong") strong: Boolean = true,
        @Header("X-Plex-Product") product: String,
        @Header("X-Plex-Version") version: String,
        @Header("X-Plex-Client-Identifier") clientId: String,
        @Header("X-Plex-Platform") platform: String,
        @Header("X-Plex-Platform-Version") platformVersion: String,
        @Header("X-Plex-Device") device: String,
        @Header("X-Plex-Device-Name") deviceName: String,
        @Header("X-Plex-Model") model: String,
        @Header("X-Plex-Screen-Resolution") screenResolution: String,
        @Header("X-Plex-Device-Screen-Resolution") deviceScreenResolution: String
    ): Response<PlexPinResponse>

    @Headers(
        "X-Plex-Client-Name: Pure Stream",
        "X-Plex-Client-Version: 1.0.0",
        "X-Plex-Client-Identifier: pure-stream-tv",
        "X-Plex-Platform: Android TV",
        "X-Plex-Device-Name: Pure Stream TV"
    )
    @FormUrlEncoded
    @POST("pins.json")
    suspend fun createPinV1(
        @Field("strong") strong: Boolean = true
    ): Response<PlexPinApiResponse>

    @Headers(
        "X-Plex-Client-Name: Pure Stream",
        "X-Plex-Client-Version: 1.0.0",
        "X-Plex-Client-Identifier: pure-stream-tv",
        "X-Plex-Platform: Android TV",
        "X-Plex-Device-Name: Pure Stream TV"
    )
    @GET("pins/{pinId}.json")
    suspend fun checkPin(
        @Path("pinId") pinId: String
    ): Response<PlexPinApiResponse>
}