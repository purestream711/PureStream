package com.purestream.data.model

import com.google.gson.annotations.SerializedName
import androidx.compose.runtime.Stable

@Stable
data class TvShow(
    @SerializedName("ratingKey")
    val ratingKey: String,
    @SerializedName("key")
    val key: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("summary")
    val summary: String?,
    @SerializedName("thumb")
    val thumbUrl: String?,
    @SerializedName("art")
    val artUrl: String?,
    @SerializedName("year")
    val year: Int?,
    @SerializedName("rating")
    val rating: Float?,
    @SerializedName("contentRating")
    val contentRating: String?,
    @SerializedName("studio")
    val studio: String?,
    @SerializedName("leafCount")
    val episodeCount: Int?,
    @SerializedName("childCount")
    val seasonCount: Int?,
    @SerializedName("Guid")
    val guid: List<GuidItem>? = emptyList(),
    @SerializedName("Image")
    val images: List<PlexImage>? = emptyList(),
    val logoUrl: String? = null,
    val profanityLevel: ProfanityLevel? = ProfanityLevel.UNKNOWN
) {
    // Additional computed properties for app functionality
    val id: String get() = ratingKey
    val posterUrl: String? get() = thumbUrl
    val sortTitle: String get() = title.removeArticles()
    
    // Helper function to remove articles for sorting
    private fun String.removeArticles(): String {
        val articles = listOf("The ", "A ", "An ")
        for (article in articles) {
            if (this.startsWith(article, ignoreCase = true)) {
                return this.substring(article.length)
            }
        }
        return this
    }
}

data class Season(
    @SerializedName("ratingKey")
    val ratingKey: String,
    @SerializedName("key")
    val key: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("summary")
    val summary: String?,
    @SerializedName("thumb")
    val thumbUrl: String?,
    @SerializedName("index")
    val seasonNumber: Int,
    @SerializedName("leafCount")
    val episodeCount: Int?
)

data class Episode(
    @SerializedName("ratingKey")
    val ratingKey: String,
    @SerializedName("key")
    val key: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("summary")
    val summary: String?,
    @SerializedName("thumb")
    val thumbUrl: String?,
    @SerializedName("index")
    val episodeNumber: Int,
    @SerializedName("parentIndex")
    val seasonNumber: Int,
    @SerializedName("parentRatingKey")
    val parentRatingKey: String?,
    @SerializedName("grandparentRatingKey")
    val grandparentRatingKey: String?,
    @SerializedName("parentTitle")
    val parentTitle: String?,
    @SerializedName("grandparentTitle")
    val grandparentTitle: String?,
    @SerializedName("grandparentYear")
    val grandparentYear: Int?,
    @SerializedName("duration")
    val duration: Long?,
    @SerializedName("rating")
    val rating: Float?,
    @SerializedName("year")
    val year: Int?,
    @SerializedName("originallyAvailableAt")
    val airDate: String?,
    @SerializedName("Media")
    val media: List<MediaItem>? = emptyList(),
    @SerializedName("Guid")
    val guid: List<GuidItem>? = emptyList(),
    @SerializedName("Image")
    val images: List<PlexImage>? = emptyList(),
    val logoUrl: String? = null,
    val hasSubtitles: Boolean = false,
    val profanityLevel: ProfanityLevel? = ProfanityLevel.UNKNOWN
) {
    // Get the actual video quality from stream info
    val videoQuality: String get() {
        return media?.firstOrNull()?.parts?.firstOrNull()?.streams
            ?.firstOrNull { it.streamType == 1 } // Video stream type
            ?.videoResolution?.let { "${it}p" } ?: "Unknown"
    }
}