package com.purestream.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subtitle_timing_preferences")
data class SubtitleTimingPreference(
    @PrimaryKey
    val contentId: String,                    // movie.ratingKey or episode.ratingKey
    val timingOffsetMs: Long,                 // Manual subtitle timing offset in milliseconds
    val updatedAt: Long = System.currentTimeMillis()  // When preference was last updated
)