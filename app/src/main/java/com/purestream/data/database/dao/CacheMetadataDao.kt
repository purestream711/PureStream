package com.purestream.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.purestream.data.database.entities.CacheMetadataEntity

@Dao
interface CacheMetadataDao {

    @Query("SELECT * FROM cache_metadata WHERE key = :key")
    suspend fun getMetadata(key: String): CacheMetadataEntity?

    @Query("SELECT * FROM cache_metadata WHERE profileId = :profileId")
    suspend fun getAllMetadataForProfile(profileId: String): List<CacheMetadataEntity>

    @Query("SELECT * FROM cache_metadata WHERE profileId = :profileId AND cacheType = :type")
    suspend fun getMetadataByType(profileId: String, type: String): List<CacheMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: CacheMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadataList(metadataList: List<CacheMetadataEntity>)

    @Query("DELETE FROM cache_metadata WHERE key = :key")
    suspend fun deleteMetadata(key: String)

    @Query("DELETE FROM cache_metadata WHERE profileId = :profileId")
    suspend fun clearMetadataForProfile(profileId: String)

    @Query("DELETE FROM cache_metadata WHERE profileId = :profileId AND cacheType = :type")
    suspend fun clearMetadataByType(profileId: String, type: String)
}
