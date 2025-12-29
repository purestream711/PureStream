package com.purestream.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.purestream.data.database.Converters
import androidx.compose.runtime.Stable

@Entity(tableName = "profiles")
@TypeConverters(Converters::class)
@Stable
data class Profile(
    @PrimaryKey
    val id: String,
    val name: String,
    val avatarImage: String, // Drawable resource name (e.g., "avatar_1")
    val profileType: ProfileType,
    val profanityFilterLevel: ProfanityFilterLevel,
    val selectedLibraries: List<String>,
    val customFilteredWords: List<String> = emptyList(),
    val whitelistedWords: List<String> = emptyList(),
    val audioMuteDuration: Int = 2000, // milliseconds
    val dashboardCollections: List<DashboardCollection> = getDefaultCollections(),
    val createdAt: Long = System.currentTimeMillis(),
    val preferredMovieLibraryId: String? = null, // Preferred library for movies screen
    val preferredTvShowLibraryId: String? = null, // Preferred library for TV shows screen
    val preferredDashboardLibraryId: String? = null, // Preferred library for dashboard collections

    // AI Curation fields
    val aiCuratedEnabled: Boolean = false,
    val lastAiCurationTimestamp: Long = 0L,
    val aiFeaturedMovieRatingKey: String? = null,  // Stores rating key of AI-curated featured movie

    // Default profile setting
    val isDefaultProfile: Boolean = false,  // Automatically sign into this profile on startup

    // RPG-style level-up tracker fields
    val totalFilteredWordsCount: Int = 0,
    val currentLevel: Int = 1,
    val wordsFilteredThisLevel: Int = 0
) {
    companion object {
        fun getDefaultCollections(): List<DashboardCollection> {
            return listOf(
                DashboardCollection("trending", "Trending Now", CollectionType.HARDCODED, true, 0),
                DashboardCollection("popular", "Popular Movies", CollectionType.HARDCODED, true, 1),
                DashboardCollection("recommended", "Recommended for You", CollectionType.HARDCODED, true, 2)
            )
        }
    }
}

enum class ProfileType {
    ADULT, CHILD
}

enum class ProfanityFilterLevel {
    NONE, MILD, MODERATE, STRICT
}

enum class ProfanityLevel {
    NONE, LOW, MEDIUM, HIGH, UNKNOWN
}