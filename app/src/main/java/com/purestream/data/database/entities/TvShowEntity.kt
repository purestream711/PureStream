package com.purestream.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_tv_shows",
    indices = [
        Index(value = ["libraryId"]),
        Index(value = ["profileId"]),
        Index(value = ["ratingKey"], unique = true)
    ]
)
data class TvShowEntity(
    @PrimaryKey val ratingKey: String,
    val key: String,
    val title: String,
    val sortTitle: String,
    val summary: String?,
    val thumbUrl: String?,
    val artUrl: String?,
    val theme: String?,
    val year: Int?,
    val rating: Float?,
    val contentRating: String?,
    val studio: String?,
    val episodeCount: Int?,
    val seasonCount: Int?,

    // Serialized complex fields (JSON)
    val guidJson: String?,

    // Profanity level
    val profanityLevel: String?,

    // Cache metadata
    val libraryId: String,
    val profileId: String,
    val cachedAt: Long,
    val serverUrl: String
)
