package com.purestream.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.purestream.data.database.entities.GeminiCachedMovie

@Dao
interface GeminiCachedMovieDao {
    @Query("SELECT * FROM gemini_cached_movies WHERE profileId = :profileId AND collectionId = :collectionId ORDER BY `order` ASC")
    suspend fun getMoviesForCollection(profileId: String, collectionId: String): List<GeminiCachedMovie>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<GeminiCachedMovie>)

    @Query("DELETE FROM gemini_cached_movies WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
