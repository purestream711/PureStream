package com.purestream.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_libraries",
    indices = [Index(value = ["profileId"])]
)
data class LibraryEntity(
    @PrimaryKey val key: String,
    val title: String,
    val type: String,  // "movie" or "show"
    val agent: String?,
    val scanner: String?,
    val language: String?,
    val uuid: String?,

    // Cache metadata
    val profileId: String,
    val cachedAt: Long
)
