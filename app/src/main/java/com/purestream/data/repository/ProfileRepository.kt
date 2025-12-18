package com.purestream.data.repository

import com.purestream.data.database.AppDatabase
import com.purestream.data.database.ProfileDao
import com.purestream.data.model.Profile
import kotlinx.coroutines.flow.Flow
import android.content.Context

class ProfileRepository(context: Context) {
    
    private val profileDao: ProfileDao = AppDatabase.getDatabase(context).profileDao()
    
    fun getAllProfiles(): Flow<List<Profile>> = profileDao.getAllProfiles()
    
    suspend fun getProfileById(profileId: String): Profile? = profileDao.getProfileById(profileId)
    
    suspend fun insertProfile(profile: Profile) = profileDao.insertProfile(profile)
    
    suspend fun updateProfile(profile: Profile) = profileDao.updateProfile(profile)
    
    suspend fun deleteProfile(profile: Profile) = profileDao.deleteProfile(profile)
    
    suspend fun deleteProfileById(profileId: String) = profileDao.deleteProfileById(profileId)
    
    suspend fun getProfileCount(): Int = profileDao.getProfileCount()
}