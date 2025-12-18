package com.purestream.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey
    val id: String, // Composite key: profileId + ratingKey (e.g., "profile1_12345")
    val profileId: String, // Profile ID who watched this content
    val ratingKey: String, // Plex media ID (unique identifier for movie/episode)
    val contentType: String, // "movie" or "episode"
    val contentTitle: String, // Movie title or episode title
    val showTitle: String? = null, // TV show title (for episodes only)
    val seasonNumber: Int? = null, // Season number (for episodes only)
    val episodeNumber: Int? = null, // Episode number (for episodes only)
    val position: Long, // Current playback position in milliseconds
    val duration: Long, // Total duration in milliseconds
    val completed: Boolean = false, // True if watched >90%
    val lastWatched: Long = System.currentTimeMillis(), // Timestamp of last update
    val updatedAt: Long = System.currentTimeMillis()
)
