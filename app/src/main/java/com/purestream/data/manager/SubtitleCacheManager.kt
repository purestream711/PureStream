package com.purestream.data.manager

import com.purestream.data.model.SubtitleAnalysisResult
import com.purestream.data.model.FilteredSubtitleResult
import com.purestream.data.model.Movie
import com.purestream.data.model.TvShow
import com.purestream.data.model.Episode
import java.util.concurrent.ConcurrentHashMap

class SubtitleCacheManager {
    
    private val movieCache = ConcurrentHashMap<String, SubtitleAnalysisResult>()
    private val episodeCache = ConcurrentHashMap<String, SubtitleAnalysisResult>()
    
    fun getCachedMovieAnalysis(movie: Movie): SubtitleAnalysisResult? {
        return movieCache[getMovieKey(movie)]
    }
    
    fun cacheMovieAnalysis(movie: Movie, analysis: SubtitleAnalysisResult) {
        movieCache[getMovieKey(movie)] = analysis
    }
    
    fun getCachedEpisodeAnalysis(tvShow: TvShow, episode: Episode): SubtitleAnalysisResult? {
        return episodeCache[getEpisodeKey(tvShow, episode)]
    }
    
    fun cacheEpisodeAnalysis(tvShow: TvShow, episode: Episode, analysis: SubtitleAnalysisResult) {
        episodeCache[getEpisodeKey(tvShow, episode)] = analysis
    }
    
    fun clearCache() {
        movieCache.clear()
        episodeCache.clear()
    }
    
    fun clearMovieCache() {
        movieCache.clear()
    }
    
    fun clearEpisodeCache() {
        episodeCache.clear()
    }
    
    private fun getMovieKey(movie: Movie): String {
        return "movie_${movie.title}_${movie.year ?: "unknown"}"
    }
    
    private fun getEpisodeKey(tvShow: TvShow, episode: Episode): String {
        return "episode_${tvShow.title}_S${episode.seasonNumber}E${episode.episodeNumber}"
    }
    
    fun getCacheSize(): Pair<Int, Int> {
        return Pair(movieCache.size, episodeCache.size)
    }
}