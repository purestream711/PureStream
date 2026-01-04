package com.purestream.data.model

import com.google.gson.annotations.SerializedName
import androidx.compose.runtime.Stable

@Stable
data class Movie(
    @SerializedName("ratingKey")
    val ratingKey: String,
    @SerializedName("key")
    val key: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("summary")
    val summary: String? = null,
    @SerializedName("thumb")
    val thumbUrl: String? = null,
    @SerializedName("art")
    val artUrl: String? = null,
    @SerializedName("year")
    val year: Int? = null,
    @SerializedName("duration")
    val duration: Long? = null,
    @SerializedName("rating")
    val rating: Float? = null,
    @SerializedName("contentRating")
    val contentRating: String? = null,
    @SerializedName("studio")
    val studio: String? = null,
    @SerializedName("tagline")
    val tagline: String? = null,
    @SerializedName("Media")
    val media: List<MediaItem>? = emptyList(),
    @SerializedName("Guid")
    val guid: List<GuidItem>? = emptyList(),
    @SerializedName("Collection")
    val collections: List<CollectionTag>? = emptyList(),
    @SerializedName("Genre")
    val genres: List<CollectionTag>? = emptyList(),
    @SerializedName("Image")
    val images: List<PlexImage>? = emptyList(),
    val logoUrl: String? = null,
    val hasSubtitles: Boolean = false,
    val profanityLevel: ProfanityLevel? = ProfanityLevel.UNKNOWN
) {
    // Additional computed properties for app functionality
    val id: String get() = ratingKey
    val posterUrl: String? get() = thumbUrl
    val sortTitle: String get() = title.removeArticles()
    
    // Get the actual video quality from stream info
    val videoQuality: String get() {
        return media?.firstOrNull()?.parts?.firstOrNull()?.streams
            ?.firstOrNull { it.streamType == 1 } // Video stream type
            ?.videoResolution?.let { "${it}p" } ?: "Unknown"
    }
    
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

data class MediaItem(
    @SerializedName("id")
    val id: String,
    @SerializedName("duration")
    val duration: Long?,
    @SerializedName("Part")
    val parts: List<MediaPart>? = emptyList()
)

data class CollectionTag(
    @SerializedName("tag")
    val tag: String
)

data class MediaPart(
    @SerializedName("id")
    val id: String,
    @SerializedName("key")
    val key: String,
    @SerializedName("duration")
    val duration: Long?,
    @SerializedName("file")
    val file: String?,
    @SerializedName("size")
    val size: Long?,
    @SerializedName("Stream")
    val streams: List<StreamInfo>? = emptyList()
)

data class StreamInfo(
    @SerializedName("id")
    val id: String,
    @SerializedName("streamType")
    val streamType: Int,
    @SerializedName("codec")
    val codec: String?,
    @SerializedName("language")
    val language: String?,
    @SerializedName("languageCode")
    val languageCode: String?,
    @SerializedName("videoResolution")
    val videoResolution: String?,
    @SerializedName("width")
    val width: Int?,
    @SerializedName("height")
    val height: Int?
)

data class GuidItem(
    @SerializedName("id")
    val id: String
)

data class PlexImage(
    @SerializedName("type")
    val type: String,
    @SerializedName("url")
    val url: String
)

