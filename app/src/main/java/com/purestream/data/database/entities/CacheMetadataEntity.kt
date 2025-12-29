package com.purestream.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache_metadata")
data class CacheMetadataEntity(
    @PrimaryKey val key: String,  // e.g., "movies_lib123_profile456"
    val cacheType: String,         // "movies", "tv_shows", "libraries"
    val profileId: String,
    val libraryId: String?,
    val lastRefreshed: Long,
    val itemCount: Int,
    val isComplete: Boolean
)
