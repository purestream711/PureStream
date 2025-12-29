package com.purestream.data.model

import androidx.compose.runtime.Stable

/**
 * Represents a collection/section that can appear on the home screen dashboard.
 * Can be either a hardcoded section (Trending, Popular, Recommended) or a Plex collection.
 */
@Stable
data class DashboardCollection(
    val id: String,
    val title: String,
    val type: CollectionType,
    val isEnabled: Boolean,
    val order: Int,
    val itemCount: Int = 0  // Number of items in this collection (0 means unknown or empty)
)

enum class CollectionType {
    HARDCODED,  // Built-in sections like Trending Now, Popular Movies, etc.
    PLEX,       // Dynamic collections fetched from Plex server
    GEMINI      // AI-curated collections from Gemini API
}
