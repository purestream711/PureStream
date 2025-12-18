package com.purestream.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a Plex collection (custom grouping of content)
 */
data class PlexCollection(
    @SerializedName("ratingKey")
    val ratingKey: String,

    @SerializedName("key")
    val key: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("type")
    val type: String = "collection",

    @SerializedName("summary")
    val summary: String? = null,

    @SerializedName("thumb")
    val thumbUrl: String? = null,

    @SerializedName("art")
    val artUrl: String? = null,

    @SerializedName("childCount")
    val childCount: Int? = null,

    @SerializedName("addedAt")
    val addedAt: Long? = null,

    @SerializedName("smart")
    val smart: Boolean? = null,

    @SerializedName("collectionMode")
    val collectionMode: String? = null
)
