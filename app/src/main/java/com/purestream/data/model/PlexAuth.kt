package com.purestream.data.model

import com.google.gson.annotations.SerializedName

// PlexAuthRequest removed - now using form fields directly

data class PlexAuthResponse(
    @SerializedName("user")
    val user: AuthenticatedUser?
)

data class AuthenticatedUser(
    @SerializedName("id")
    val id: String,
    @SerializedName("uuid")
    val uuid: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("joined_at")
    val joinedAt: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("thumb")
    val thumb: String?,
    @SerializedName("authentication_token")
    val authToken: String
)

// PlexPinRequest removed - now using form fields directly

data class PlexPinApiResponse(
    @SerializedName("pin")
    val pin: PlexPinResponse
)

data class PlexPinResponse(
    @SerializedName("id")
    val id: Long,
    @SerializedName("code")
    val code: String,
    @SerializedName("expires_at")
    val expiresAt: String,
    @SerializedName("user_id")
    val userId: String?,
    @SerializedName("client_identifier")
    val clientIdentifier: String,
    @SerializedName("trusted")
    val trusted: Boolean,
    @SerializedName("auth_token")
    val authToken: String?
)

data class PlexPinLocation(
    @SerializedName("code")
    val code: String,
    @SerializedName("european_union_member")
    val europeanUnionMember: Boolean,
    @SerializedName("continent_code")
    val continentCode: String,
    @SerializedName("country")
    val country: String,
    @SerializedName("time_zone")
    val timeZone: String
)

enum class AuthenticationStatus {
    IDLE,
    LOADING,
    SUCCESS,
    ERROR,
    WAITING_FOR_PIN
}