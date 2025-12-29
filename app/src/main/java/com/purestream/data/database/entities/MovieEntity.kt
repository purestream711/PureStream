package com.purestream.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_movies",
    indices = [
        Index(value = ["libraryId"]),
        Index(value = ["profileId"]),
        Index(value = ["ratingKey"], unique = true)
    ]
)
data class MovieEntity(
    @PrimaryKey val ratingKey: String,
    val key: String,
    val title: String,
    val sortTitle: String,
    val summary: String?,
    val thumbUrl: String?,
    val artUrl: String?,
    val year: Int?,
    val duration: Long?,
    val rating: Float?,
    val contentRating: String?,
    val studio: String?,
    val tagline: String?,

    // Serialized complex fields (JSON)
    val mediaJson: String?,
    val guidJson: String?,
    val collectionsJson: String?,

    // Profanity level
    val profanityLevel: String?,
    val hasSubtitles: Boolean,

    // Cache metadata
    val libraryId: String,
    val profileId: String,
    val cachedAt: Long,
    val serverUrl: String
)
