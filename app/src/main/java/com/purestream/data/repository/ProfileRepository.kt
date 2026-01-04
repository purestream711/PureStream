package com.purestream.data.repository

import com.purestream.data.database.AppDatabase
import com.purestream.data.database.ProfileDao
import com.purestream.data.model.Profile
import kotlinx.coroutines.flow.Flow
import android.content.Context

class ProfileRepository(context: Context) {
    
    private val profileDao: ProfileDao = AppDatabase.getDatabase(context).profileDao()
    
    fun getAllProfiles(): Flow<List<Profile>> = profileDao.getAllProfiles()

    suspend fun getAllProfilesList(): List<Profile> = profileDao.getAllProfilesList()
    
    suspend fun getProfileById(profileId: String): Profile? = profileDao.getProfileById(profileId)
    
    suspend fun insertProfile(profile: Profile) = profileDao.insertProfile(profile)
    
    suspend fun updateProfile(profile: Profile) = profileDao.updateProfile(profile)
    
    suspend fun deleteProfile(profile: Profile) = profileDao.deleteProfile(profile)
    
    suspend fun deleteProfileById(profileId: String) = profileDao.deleteProfileById(profileId)

    suspend fun getProfileCount(): Int = profileDao.getProfileCount()

    suspend fun updatePreferredMovieLibrary(profileId: String, libraryId: String?) {
        val profile = getProfileById(profileId)
        profile?.let {
            updateProfile(it.copy(preferredMovieLibraryId = libraryId))
        }
    }

    suspend fun updatePreferredTvShowLibrary(profileId: String, libraryId: String?) {
        val profile = getProfileById(profileId)
        profile?.let {
            updateProfile(it.copy(preferredTvShowLibraryId = libraryId))
        }
    }

    suspend fun setDefaultProfile(profileId: String) {
        // First, unset all profiles as default
        val allProfiles = profileDao.getAllProfilesList()
        allProfiles.forEach { profile ->
            if (profile.isDefaultProfile) {
                updateProfile(profile.copy(isDefaultProfile = false))
            }
        }

        // Then set the specified profile as default
        val profile = getProfileById(profileId)
        profile?.let {
            updateProfile(it.copy(isDefaultProfile = true))
        }
    }

    suspend fun getDefaultProfile(): Profile? {
        val allProfiles = profileDao.getAllProfilesList()
        return allProfiles.firstOrNull { it.isDefaultProfile }
    }

    suspend fun deleteAllProfiles() {
        val allProfiles = profileDao.getAllProfilesList()
        allProfiles.forEach { profile ->
            deleteProfile(profile)
        }
    }
}