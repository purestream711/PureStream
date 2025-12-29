package com.purestream.data.cache

object CacheConfig {
    // TTL (Time To Live) values in milliseconds
    const val LIBRARY_TTL_MS = 24 * 60 * 60 * 1000L      // 24 hours
    const val MOVIE_TTL_MS = 6 * 60 * 60 * 1000L         // 6 hours
    const val TV_SHOW_TTL_MS = 6 * 60 * 60 * 1000L       // 6 hours
    const val DASHBOARD_TTL_MS = 1 * 60 * 60 * 1000L     // 1 hour

    // Cache size limits (per profile)
    const val MAX_CACHED_MOVIES = 5000
    const val MAX_CACHED_TV_SHOWS = 2000

    // Cache behavior
    const val ENABLE_BACKGROUND_REFRESH = true
    const val ENABLE_OFFLINE_MODE = true

    /**
     * Check if cached data is still valid based on TTL
     */
    fun isCacheValid(cachedAt: Long, ttlMs: Long): Boolean {
        val now = System.currentTimeMillis()
        return (now - cachedAt) < ttlMs
    }

    /**
     * Determine if cache should be refreshed
     */
    fun shouldRefreshCache(
        cachedAt: Long,
        ttlMs: Long,
        forceRefresh: Boolean = false
    ): Boolean {
        if (forceRefresh) return true
        return !isCacheValid(cachedAt, ttlMs)
    }
}
