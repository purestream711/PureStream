package com.purestream.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.purestream.data.model.ProfanityFilterLevel
import com.purestream.data.model.ProfanityLevel

@Entity(tableName = "subtitle_analysis")
data class SubtitleAnalysisEntity(
    @PrimaryKey
    val id: String, // Composite key: contentId + filterLevel (e.g., "12345_STRICT")
    val contentId: String, // Movie/Episode rating key from Plex
    val contentType: String, // "movie" or "episode"
    val contentTitle: String, // Movie title or episode title
    val showTitle: String? = null, // TV show title (for episodes only)
    val seasonNumber: Int? = null, // Season number (for episodes only)
    val episodeNumber: Int? = null, // Episode number (for episodes only)
    val filterLevel: ProfanityFilterLevel,
    val profanityLevel: ProfanityLevel,
    val detectedWords: String, // JSON array of detected profanity words
    val totalWordsCount: Int,
    val profanityWordsCount: Int,
    val profanityPercentage: Float,
    val subtitleFileName: String,
    val filteredSubtitlePath: String? = null, // Path to filtered subtitle file
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)