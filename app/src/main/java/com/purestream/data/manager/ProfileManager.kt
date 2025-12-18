package com.purestream.data.manager

import android.content.Context
import android.content.SharedPreferences
import com.purestream.data.model.Profile
import com.purestream.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class ProfileManager private constructor(context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: ProfileManager? = null
        
        fun getInstance(context: Context): ProfileManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ProfileManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    private val profileRepository = ProfileRepository(context)
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
    
    private val _currentProfile = MutableStateFlow<Profile?>(null)
    val currentProfile: StateFlow<Profile?> = _currentProfile.asStateFlow()
    
    suspend fun setCurrentProfile(profile: Profile) {
        _currentProfile.value = profile
        // Store the current profile ID in SharedPreferences
        sharedPreferences.edit()
            .putString("current_profile_id", profile.id)
            .apply()
    }
    
    suspend fun loadCurrentProfile(): Profile? {
        val savedProfileId = sharedPreferences.getString("current_profile_id", null)
        return if (savedProfileId != null) {
            try {
                val profile = profileRepository.getProfileById(savedProfileId)
                _currentProfile.value = profile
                profile
            } catch (e: Exception) {
                null
            }
        } else {
            // Try to get the first available profile
            val profiles = profileRepository.getAllProfiles().first()
            val firstProfile = profiles.firstOrNull()
            if (firstProfile != null) {
                setCurrentProfile(firstProfile)
            }
            firstProfile
        }
    }
    
    fun clearCurrentProfile() {
        _currentProfile.value = null
        sharedPreferences.edit()
            .remove("current_profile_id")
            .commit()  // Use commit() for synchronous clearing on logout
    }
}