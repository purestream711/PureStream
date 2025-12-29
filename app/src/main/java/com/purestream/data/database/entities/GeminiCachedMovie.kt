package com.purestream.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gemini_cached_movies")
data class GeminiCachedMovie(
    @PrimaryKey val id: String,           // Composite: profileId_collectionId_movieId
    val profileId: String,
    val collectionId: String,             // "gemini_trending" or "gemini_top_rated"
    val movieRatingKey: String,           // Plex rating key
    val order: Int,                       // Order in collection (0, 1, 2...)
    val cachedAt: Long = System.currentTimeMillis()
)
