package com.purestream.data.model

import com.google.gson.annotations.SerializedName

data class PlexLibrary(
    @SerializedName("key")
    val key: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("type")
    val type: String, // "movie" or "show"
    @SerializedName("agent")
    val agent: String?,
    @SerializedName("scanner")
    val scanner: String?,
    @SerializedName("language")
    val language: String?,
    @SerializedName("uuid")
    val uuid: String?,
    @SerializedName("updatedAt")
    val updatedAt: Long?,
    @SerializedName("createdAt")
    val createdAt: Long?
)

data class PlexResponse<T>(
    @SerializedName("MediaContainer")
    val mediaContainer: MediaContainer<T>
)

data class MediaContainer<T>(
    @SerializedName("size")
    val size: Int,
    @SerializedName("allowSync")
    val allowSync: Boolean?,
    @SerializedName("identifier")
    val identifier: String?,
    @SerializedName("mediaTagPrefix")
    val mediaTagPrefix: String?,
    @SerializedName("mediaTagVersion")
    val mediaTagVersion: Long?,
    @SerializedName("parentTitle")
    val parentTitle: String?,
    @SerializedName("parentYear")
    val parentYear: Int?,
    @SerializedName("grandparentTitle")
    val grandparentTitle: String?,
    @SerializedName("grandparentYear")
    val grandparentYear: Int?,
    @SerializedName("Metadata")
    val metadata: List<T>? = emptyList(),
    @SerializedName("Directory")
    val directories: List<PlexLibrary>? = emptyList()
)