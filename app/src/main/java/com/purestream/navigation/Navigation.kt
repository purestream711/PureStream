package com.purestream.navigation

object Destinations {
    const val GET_STARTED = "get_started"
    const val FEATURE_SHOWCASE = "feature_showcase"
    const val CONNECT_PLEX = "connect_plex"
    const val PLEX_WEB_AUTH = "plex_web_auth"
    const val PLEX_PIN = "plex_pin/{pinId}/{pinCode}"
    const val PROFILE_SELECTION = "profile_selection"
    const val PROFILE_CREATE = "profile_create"
    const val PROFILE_EDIT = "profile_edit/{profileId}"
    const val LOADING = "loading?workRequestId={workRequestId}"
    const val UPGRADE = "upgrade"
    const val HOME = "home"
    const val MOVIES = "movies"
    const val TV_SHOWS = "tv_shows"
    const val MOVIE_DETAILS = "movie_details/{movieId}"
    const val TV_SHOW_DETAILS = "tv_show_details/{showId}"
    const val EPISODE_DETAILS = "episode_details/{episodeId}"
    const val MEDIA_PLAYER = "media_player/{contentId}/{contentType}"
    const val MEDIA_PLAYER_WITH_URL = "media_player_with_url/{videoUrl}/{title}/{contentId}"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val LEVEL_UP_STATS = "level_up_stats"

    fun loading(workRequestId: String? = null): String {
        return if (workRequestId != null) {
            "loading?workRequestId=$workRequestId"
        } else {
            "loading"
        }
    }
    
    fun movieDetails(movieId: String) = "movie_details/$movieId"
    fun tvShowDetails(showId: String) = "tv_show_details/$showId"
    fun episodeDetails(episodeId: String) = "episode_details/$episodeId"
    fun mediaPlayer(contentId: String, contentType: String) = "media_player/$contentId/$contentType"
    fun mediaPlayerWithUrl(videoUrl: String, title: String, contentId: String) = "media_player_with_url/$videoUrl/$title/$contentId"
    fun profileEdit(profileId: String) = "profile_edit/$profileId"
    fun plexPin(pinId: String, pinCode: String) = "plex_pin/$pinId/$pinCode"
}