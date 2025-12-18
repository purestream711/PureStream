package com.purestream.data.model

import com.google.gson.annotations.SerializedName

data class OpenSubtitlesSearchRequest(
    @SerializedName("imdb_id")
    val imdbId: String? = null,
    @SerializedName("query")
    val query: String? = null,
    @SerializedName("season_number")
    val seasonNumber: Int? = null,
    @SerializedName("episode_number")
    val episodeNumber: Int? = null,
    @SerializedName("languages")
    val languages: String = "en",
    @SerializedName("moviehash")
    val movieHash: String? = null
)

// Flexible response wrapper that can handle both array and object responses
data class OpenSubtitlesSearchResponse(
    @SerializedName("total_pages")
    val totalPages: Int? = null,
    @SerializedName("total_count")
    val totalCount: Int? = null,
    @SerializedName("per_page")
    val perPage: Int? = null,
    @SerializedName("page")
    val page: Int? = null,
    @SerializedName("data")
    val data: List<SubtitleResult>? = null
)

// Wrapper class to handle flexible response
sealed class OpenSubtitlesResponse {
    data class Wrapped(val response: OpenSubtitlesSearchResponse) : OpenSubtitlesResponse()
    data class Direct(val data: List<SubtitleResult>) : OpenSubtitlesResponse()
}

data class SubtitleResult(
    @SerializedName("id")
    val id: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("attributes")
    val attributes: SubtitleAttributes
)

data class SubtitleAttributes(
    @SerializedName("subtitle_id")
    val subtitleId: String,
    @SerializedName("language")
    val language: String,
    @SerializedName("download_count")
    val downloadCount: Int = 0,
    @SerializedName("new_download_count")
    val newDownloadCount: Int,
    @SerializedName("hearing_impaired")
    val hearingImpaired: Boolean,
    @SerializedName("hd")
    val hd: Boolean,
    @SerializedName("format")
    val format: String?,
    @SerializedName("fps")
    val fps: Double?,
    @SerializedName("votes")
    val votes: Int = 0,
    @SerializedName("points")
    val points: Int = 0,
    @SerializedName("ratings")
    val ratings: Double = 0.0,
    @SerializedName("from_trusted")
    val fromTrusted: Boolean,
    @SerializedName("foreign_parts_only")
    val foreignPartsOnly: Boolean,
    @SerializedName("auto_translation")
    val autoTranslation: Boolean,
    @SerializedName("ai_translated")
    val aiTranslated: Boolean,
    @SerializedName("machine_translated")
    val machineTranslated: Boolean?,
    @SerializedName("upload_date")
    val uploadDate: String?,
    @SerializedName("release")
    val release: String?,
    @SerializedName("comments")
    val comments: String?,
    @SerializedName("legacy_subtitle_id")
    val legacySubtitleId: Int?,
    @SerializedName("uploader")
    val uploader: Uploader?,
    @SerializedName("feature_details")
    val featureDetails: FeatureDetails?,
    @SerializedName("url")
    val url: String,
    @SerializedName("related_links")
    val relatedLinks: Any?,
    @SerializedName("files")
    val files: List<SubtitleFile>
)

data class Uploader(
    @SerializedName("uploader_id")
    val uploaderId: Int?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("rank")
    val rank: String?
)

data class FeatureDetails(
    @SerializedName("feature_id")
    val featureId: Int,
    @SerializedName("feature_type")
    val featureType: String,
    @SerializedName("year")
    val year: Int?,
    @SerializedName("title")
    val title: String,
    @SerializedName("movie_name")
    val movieName: String,
    @SerializedName("imdb_id")
    val imdbId: Int?,
    @SerializedName("tmdb_id")
    val tmdbId: Int?,
    // Episode-specific fields
    @SerializedName("season_number")
    val seasonNumber: Int?,
    @SerializedName("episode_number")
    val episodeNumber: Int?,
    @SerializedName("parent_imdb_id")
    val parentImdbId: Int?,
    @SerializedName("parent_tmdb_id")
    val parentTmdbId: Int?,
    @SerializedName("parent_title")
    val parentTitle: String?,
    @SerializedName("parent_feature_id")
    val parentFeatureId: Int?
)

data class RelatedLinks(
    @SerializedName("label")
    val label: String?,
    @SerializedName("url")
    val url: String?,
    @SerializedName("img_url")
    val imgUrl: String?
)

data class SubtitleFile(
    @SerializedName("file_id")
    val fileId: Int,
    @SerializedName("cd_number")
    val cdNumber: Int,
    @SerializedName("file_name")
    val fileName: String
)

data class OpenSubtitlesDownloadRequest(
    @SerializedName("file_id")
    val fileId: Int,
    @SerializedName("sub_format")
    val subFormat: String = "srt"
)

data class OpenSubtitlesDownloadResponse(
    @SerializedName("link")
    val link: String,
    @SerializedName("file_name")
    val fileName: String,
    @SerializedName("requests")
    val requests: Int,
    @SerializedName("remaining")
    val remaining: Int,
    @SerializedName("message")
    val message: String?,
    @SerializedName("reset_time")
    val resetTime: String?,
    @SerializedName("reset_time_utc")
    val resetTimeUtc: String?
)

data class SubtitleAnalysisResult(
    val movieTitle: String,
    val episodeInfo: String? = null,
    val subtitleFileName: String,
    val profanityLevel: ProfanityLevel,
    val detectedWords: List<String>,
    val totalWordsCount: Int,
    val profanityWordsCount: Int,
    val profanityPercentage: Float,
    val analysisTimestamp: Long = System.currentTimeMillis()
)