package com.purestream.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.purestream.data.database.entities.MovieEntity

@Dao
interface MovieCacheDao {

    @Query("SELECT * FROM cached_movies WHERE profileId = :profileId AND libraryId = :libraryId ORDER BY sortTitle ASC")
    suspend fun getMoviesForLibrary(profileId: String, libraryId: String): List<MovieEntity>

    @Query("SELECT * FROM cached_movies WHERE profileId = :profileId ORDER BY sortTitle ASC")
    suspend fun getAllMoviesForProfile(profileId: String): List<MovieEntity>

    @Query("SELECT * FROM cached_movies WHERE ratingKey = :ratingKey")
    suspend fun getMovieByRatingKey(ratingKey: String): MovieEntity?

    @Query("""
        SELECT * FROM cached_movies
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
    suspend fun searchMovies(profileId: String, query: String, limit: Int = 50): List<MovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<MovieEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: MovieEntity)

    @Query("DELETE FROM cached_movies WHERE profileId = :profileId AND libraryId = :libraryId")
    suspend fun clearLibraryCache(profileId: String, libraryId: String)

    @Query("DELETE FROM cached_movies WHERE profileId = :profileId")
    suspend fun clearAllMoviesForProfile(profileId: String)

    @Query("DELETE FROM cached_movies WHERE cachedAt < :expirationTime")
    suspend fun deleteExpiredMovies(expirationTime: Long)

    @Query("SELECT COUNT(*) FROM cached_movies WHERE profileId = :profileId AND libraryId = :libraryId")
    suspend fun getMovieCount(profileId: String, libraryId: String): Int

    @Query("SELECT COUNT(*) FROM cached_movies WHERE profileId = :profileId")
    suspend fun getTotalMovieCount(profileId: String): Int
}
