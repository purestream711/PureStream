package com.purestream.data.cache

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.purestream.data.database.entities.MovieEntity
import com.purestream.data.database.entities.TvShowEntity
import com.purestream.data.database.entities.LibraryEntity
import com.purestream.data.model.Movie
import com.purestream.data.model.TvShow
import com.purestream.data.model.MediaItem
import com.purestream.data.model.GuidItem
import com.purestream.data.model.CollectionTag
import com.purestream.data.model.ProfanityLevel

/**
 * Extension function to convert Movie to MovieEntity for caching
 */
fun Movie.toEntity(
    libraryId: String,
    profileId: String,
    cachedAt: Long,
    serverUrl: String
): MovieEntity {
    val gson = Gson()
    return MovieEntity(
        ratingKey = this.ratingKey,
        key = this.key,
        title = this.title,
        sortTitle = this.sortTitle,
        summary = this.summary,
        thumbUrl = this.thumbUrl,
        artUrl = this.artUrl,
        year = this.year,
        duration = this.duration,
        rating = this.rating,
        contentRating = this.contentRating,
        studio = this.studio,
        tagline = this.tagline,
        mediaJson = gson.toJson(this.media ?: emptyList<MediaItem>()),
        guidJson = gson.toJson(this.guid ?: emptyList<GuidItem>()),
        collectionsJson = gson.toJson(this.collections ?: emptyList<CollectionTag>()),
        profanityLevel = this.profanityLevel?.name,
        hasSubtitles = this.hasSubtitles,
        libraryId = libraryId,
        profileId = profileId,
        cachedAt = cachedAt,
        serverUrl = serverUrl
    )
}

/**
 * Extension function to convert MovieEntity back to Movie
 */
fun MovieEntity.toMovie(): Movie {
    val mediaList = try {
        Gson().fromJson(
            this.mediaJson,
            object : TypeToken<List<MediaItem>>() {}.type
        ) as? List<MediaItem> ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    val guidList = try {
        Gson().fromJson(
            this.guidJson,
            object : TypeToken<List<GuidItem>>() {}.type
        ) as? List<GuidItem> ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    val collectionsList = try {
        Gson().fromJson(
            this.collectionsJson,
            object : TypeToken<List<CollectionTag>>() {}.type
        ) as? List<CollectionTag> ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    val profanity = try {
        this.profanityLevel?.let { ProfanityLevel.valueOf(it) } ?: ProfanityLevel.UNKNOWN
    } catch (e: Exception) {
        ProfanityLevel.UNKNOWN
    }

    return Movie(
        ratingKey = this.ratingKey,
        key = this.key,
        title = this.title,
        summary = this.summary,
        thumbUrl = this.thumbUrl,
        artUrl = this.artUrl,
        year = this.year,
        duration = this.duration,
        rating = this.rating,
        contentRating = this.contentRating,
        studio = this.studio,
        tagline = this.tagline,
        media = mediaList,
        guid = guidList,
        collections = collectionsList,
        hasSubtitles = this.hasSubtitles,
        profanityLevel = profanity
    )
}

/**
 * Extension function to convert TvShow to TvShowEntity for caching
 */
fun TvShow.toEntity(
    libraryId: String,
    profileId: String,
    cachedAt: Long,
    serverUrl: String
): TvShowEntity {
    val gson = Gson()
    return TvShowEntity(
        ratingKey = this.ratingKey,
        key = this.key,
        title = this.title,
        sortTitle = this.sortTitle,
        summary = this.summary,
        thumbUrl = this.thumbUrl,
        artUrl = this.artUrl,
        year = this.year,
        rating = this.rating,
        contentRating = this.contentRating,
        studio = this.studio,
        episodeCount = this.episodeCount,
        seasonCount = this.seasonCount,
        guidJson = gson.toJson(this.guid ?: emptyList<GuidItem>()),
        profanityLevel = this.profanityLevel?.name,
        libraryId = libraryId,
        profileId = profileId,
        cachedAt = cachedAt,
        serverUrl = serverUrl
    )
}

/**
 * Extension function to convert TvShowEntity back to TvShow
 */
fun TvShowEntity.toTvShow(): TvShow {
    val guidList = try {
        Gson().fromJson(
            this.guidJson,
            object : TypeToken<List<GuidItem>>() {}.type
        ) as? List<GuidItem> ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    val profanity = try {
        this.profanityLevel?.let { ProfanityLevel.valueOf(it) } ?: ProfanityLevel.UNKNOWN
    } catch (e: Exception) {
        ProfanityLevel.UNKNOWN
    }

    return TvShow(
        ratingKey = this.ratingKey,
        key = this.key,
        title = this.title,
        summary = this.summary,
        thumbUrl = this.thumbUrl,
        artUrl = this.artUrl,
        year = this.year,
        rating = this.rating,
        contentRating = this.contentRating,
        studio = this.studio,
        episodeCount = this.episodeCount,
        seasonCount = this.seasonCount,
        guid = guidList,
        profanityLevel = profanity
    )
}