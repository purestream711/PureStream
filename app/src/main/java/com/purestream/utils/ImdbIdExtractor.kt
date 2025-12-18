package com.purestream.utils

import com.purestream.data.model.GuidItem

object ImdbIdExtractor {
    
    /**
     * Extracts IMDB ID from Plex metadata Guid array
     * Handles format conversion from "imdb://tt31036941" to "tt31036941"
     * Preserves full IMDB ID format including leading zeroes for OpenSubtitles API
     */
    fun extractImdbId(guidList: List<GuidItem>?): String? {
        if (guidList.isNullOrEmpty()) return null
        
        // Look for IMDB guid in the format "imdb://tt1234567"
        val imdbGuid = guidList.find { guid ->
            guid.id.startsWith("imdb://tt", ignoreCase = true)
        }
        
        return imdbGuid?.let { guid ->
            // Extract the IMDB ID part after "imdb://" and return as-is
            guid.id.substringAfter("imdb://")
        }
    }
    
    /**
     * Extracts IMDB ID specifically for TV shows from grandparent or parent Guid
     * TV episodes may have IMDB IDs in the show's metadata
     */
    fun extractImdbIdForTvShow(
        showGuidList: List<GuidItem>?,
        episodeGuidList: List<GuidItem>? = null
    ): String? {
        // Try to get IMDB ID from the show metadata first
        val showImdbId = extractImdbId(showGuidList)
        if (showImdbId != null) return showImdbId
        
        // Fallback to episode metadata if show doesn't have IMDB ID
        return extractImdbId(episodeGuidList)
    }
}