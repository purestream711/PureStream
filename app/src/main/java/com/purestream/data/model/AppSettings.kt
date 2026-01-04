package com.purestream.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.purestream.data.database.Converters

@Entity(tableName = "app_settings")
@TypeConverters(Converters::class)
data class AppSettings(
    @PrimaryKey
    val id: String = "app_settings",
    val plexServerUrl: String = "",
    val plexToken: String = "",
    val isPremium: Boolean = false,
    val currentProfileId: String? = null,
    val selectedLibraries: List<String> = emptyList(),
    val lastSync: Long = 0L,
    val voiceSearchEnabled: Boolean = true,
    val autoPlayEnabled: Boolean = true,
    val subtitleEnabled: Boolean = true,
    val darkThemeEnabled: Boolean = true
)

data class WatchHistoryItem(
    val id: String,
    val title: String,
    val type: ContentType,
    val thumbUrl: String?,
    val lastWatched: Long,
    val progress: Float = 0f, // 0.0 to 1.0
    val duration: Long? = null
)

enum class ContentType {
    MOVIE, TV_SHOW, EPISODE
}


data class ContentSection(
    val title: String,
    val items: List<ContentItem>
)

data class ContentItem(
    val id: String,
    val title: String,
    val type: ContentType,
    val thumbUrl: String?, // Movie poster/thumbnail
    val artUrl: String?, // Movie background art
    val logoUrl: String? = null, // Movie logo art
    val summary: String?,
    val year: Int?,
    val rating: Float?,
    val duration: Long?,
    val contentRating: String?, // Content rating like PG-13, TV-14, etc.
    val profanityLevel: ProfanityLevel,
    val hasSubtitles: Boolean
)