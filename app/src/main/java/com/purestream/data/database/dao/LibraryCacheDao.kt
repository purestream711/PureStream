package com.purestream.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.purestream.data.database.entities.LibraryEntity

@Dao
interface LibraryCacheDao {

    @Query("SELECT * FROM cached_libraries WHERE profileId = :profileId ORDER BY title ASC")
    suspend fun getLibrariesForProfile(profileId: String): List<LibraryEntity>

    @Query("SELECT * FROM cached_libraries WHERE key = :libraryKey")
    suspend fun getLibraryByKey(libraryKey: String): LibraryEntity?

    @Query("SELECT * FROM cached_libraries WHERE profileId = :profileId AND type = :type ORDER BY title ASC")
    suspend fun getLibrariesByType(profileId: String, type: String): List<LibraryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraries(libraries: List<LibraryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibrary(library: LibraryEntity)

    @Query("DELETE FROM cached_libraries WHERE profileId = :profileId")
    suspend fun clearLibrariesForProfile(profileId: String)

    @Query("DELETE FROM cached_libraries WHERE cachedAt < :expirationTime")
    suspend fun deleteExpiredLibraries(expirationTime: Long)

    @Query("SELECT COUNT(*) FROM cached_libraries WHERE profileId = :profileId")
    suspend fun getLibraryCount(profileId: String): Int
}
