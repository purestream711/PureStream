package com.purestream.data.repository

import android.content.Context
import com.purestream.data.database.AppDatabase
import com.purestream.data.database.AppSettingsDao
import com.purestream.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AppSettingsRepository(context: Context) {
    
    private val appSettingsDao: AppSettingsDao = AppDatabase.getDatabase(context).appSettingsDao()
    
    fun getAppSettings(): Flow<AppSettings?> {
        return appSettingsDao.getAppSettings()
    }
    
    suspend fun getAppSettingsSync(): AppSettings {
        return appSettingsDao.getAppSettingsSync() ?: AppSettings()
    }
    
    suspend fun updatePremiumStatus(isPremium: Boolean) {
        // Ensure settings exist first
        val existingSettings = appSettingsDao.getAppSettingsSync()
        if (existingSettings == null) {
            // Create default settings with premium status
            appSettingsDao.insertOrUpdateAppSettings(AppSettings(isPremium = isPremium))
        } else {
            // Update existing settings
            appSettingsDao.updatePremiumStatus(isPremium)
        }
    }
    
    suspend fun updatePlexConnection(serverUrl: String, token: String) {
        val existingSettings = appSettingsDao.getAppSettingsSync()
        if (existingSettings == null) {
            appSettingsDao.insertOrUpdateAppSettings(
                AppSettings(plexServerUrl = serverUrl, plexToken = token)
            )
        } else {
            appSettingsDao.updatePlexConnection(serverUrl, token)
        }
    }
    
    suspend fun updateCurrentProfile(profileId: String?) {
        val existingSettings = appSettingsDao.getAppSettingsSync()
        if (existingSettings == null) {
            appSettingsDao.insertOrUpdateAppSettings(AppSettings(currentProfileId = profileId))
        } else {
            appSettingsDao.updateCurrentProfile(profileId)
        }
    }
    
    suspend fun insertOrUpdateAppSettings(appSettings: AppSettings) {
        appSettingsDao.insertOrUpdateAppSettings(appSettings)
    }
}