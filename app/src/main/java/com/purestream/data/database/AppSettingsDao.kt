package com.purestream.data.database

import androidx.room.*
import com.purestream.data.model.AppSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {
    
    @Query("SELECT * FROM app_settings WHERE id = 'app_settings' LIMIT 1")
    fun getAppSettings(): Flow<AppSettings?>
    
    @Query("SELECT * FROM app_settings WHERE id = 'app_settings' LIMIT 1")
    suspend fun getAppSettingsSync(): AppSettings?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAppSettings(appSettings: AppSettings)
    
    @Query("UPDATE app_settings SET isPremium = :isPremium WHERE id = 'app_settings'")
    suspend fun updatePremiumStatus(isPremium: Boolean)
    
    @Query("UPDATE app_settings SET plexServerUrl = :serverUrl, plexToken = :token WHERE id = 'app_settings'")
    suspend fun updatePlexConnection(serverUrl: String, token: String)
    
    @Query("UPDATE app_settings SET currentProfileId = :profileId WHERE id = 'app_settings'")
    suspend fun updateCurrentProfile(profileId: String?)
}