package com.purestream.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.purestream.data.database.entities.TvShowEntity

@Dao
interface TvShowCacheDao {

    @Query("SELECT * FROM cached_tv_shows WHERE profileId = :profileId AND libraryId = :libraryId ORDER BY sortTitle ASC")
    suspend fun getTvShowsForLibrary(profileId: String, libraryId: String): List<TvShowEntity>

    @Query("SELECT * FROM cached_tv_shows WHERE profileId = :profileId ORDER BY sortTitle ASC")
    suspend fun getAllTvShowsForProfile(profileId: String): List<TvShowEntity>

    @Query("SELECT * FROM cached_tv_shows WHERE ratingKey = :ratingKey")
    suspend fun getTvShowByRatingKey(ratingKey: String): TvShowEntity?

    @Query("""
        SELECT * FROM cached_tv_shows
        WHERE profileId = :profileId
        AND (title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%')
        ORDER BY
            CASE
                WHEN title LIKE :query || '%' THEN 0
                WHEN title LIKE '%' || :query THEN 1
                ELSE 2
            END,
            sortTitle ASC
        LIMIT :limit
    """)
    suspend fun searchTvShows(profileId: String, query: String, limit: Int = 50): List<TvShowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTvShows(shows: List<TvShowEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTvShow(show: TvShowEntity)

    @Query("DELETE FROM cached_tv_shows WHERE profileId = :profileId AND libraryId = :libraryId")
    suspend fun clearLibraryCache(profileId: String, libraryId: String)

    @Query("DELETE FROM cached_tv_shows WHERE profileId = :profileId")
    suspend fun clearAllTvShowsForProfile(profileId: String)

    @Query("DELETE FROM cached_tv_shows WHERE cachedAt < :expirationTime")
    suspend fun deleteExpiredTvShows(expirationTime: Long)

    @Query("SELECT COUNT(*) FROM cached_tv_shows WHERE profileId = :profileId AND libraryId = :libraryId")
    suspend fun getTvShowCount(profileId: String, libraryId: String): Int

    @Query("SELECT COUNT(*) FROM cached_tv_shows WHERE profileId = :profileId")
    suspend fun getTotalTvShowCount(profileId: String): Int
}
