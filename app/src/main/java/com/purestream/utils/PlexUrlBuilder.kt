package com.purestream.utils

/**
 * Utility class for building Plex server URLs with proper authentication
 * Consolidates image URL construction logic that was duplicated across multiple methods
 */
object PlexUrlBuilder {
    
    /**
     * Builds a complete image URL for Plex media thumbnails and artwork
     * 
     * @param thumbPath The relative path from Plex API response, or null/blank
     * @param serverUrl The base Plex server URL (e.g., "http://192.168.1.100:32400")
     * @param token The authentication token for the server
     * @return Complete URL with authentication, or null if inputs are invalid
     */
    fun buildImageUrl(thumbPath: String?, serverUrl: String?, token: String?): String? {
        if (thumbPath.isNullOrBlank() || serverUrl.isNullOrBlank() || token.isNullOrBlank()) {
            return null
        }
        
        return if (thumbPath.startsWith("http")) {
            // Path is already a complete URL
            thumbPath
        } else {
            // Ensure proper slash separator
            val cleanServerUrl = serverUrl.trimEnd('/')
            val cleanThumbPath = if (thumbPath.startsWith("/")) thumbPath else "/$thumbPath"
            
            // Build complete URL with server base and token authentication
            "${cleanServerUrl}${cleanThumbPath}?X-Plex-Token=${token}"
        }
    }
    
    /**
     * Builds a complete media stream URL for Plex content
     * 
     * @param mediaPath The relative media path from Plex API
     * @param serverUrl The base Plex server URL
     * @param token The authentication token
     * @return Complete streaming URL with authentication, or null if inputs are invalid
     */
    fun buildStreamUrl(mediaPath: String?, serverUrl: String?, token: String?): String? {
        if (mediaPath.isNullOrBlank() || serverUrl.isNullOrBlank() || token.isNullOrBlank()) {
            return null
        }
        
        return "${serverUrl}${mediaPath}?X-Plex-Token=${token}"
    }
}