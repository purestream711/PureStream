package com.purestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.purestream.data.model.*
import com.purestream.data.repository.PlexRepository
import com.purestream.data.repository.PlexAuthRepository
import com.purestream.data.repository.ProfileRepository
import com.purestream.data.manager.ProfileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

data class ProfileCreationState(
    val name: String = "",
    val selectedAvatarImage: String = "bear_avatar", // Default to bear avatar (leftmost)
    val profileType: ProfileType = ProfileType.ADULT,
    val profanityFilterLevel: ProfanityFilterLevel = ProfanityFilterLevel.MILD,
    val selectedLibraries: List<String> = emptyList(),
    val availableLibraries: List<PlexLibrary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCreating: Boolean = false
)

class ProfileViewModel(
    private val plexRepository: PlexRepository = PlexRepository(),
    private val context: android.content.Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProfileCreationState())
    val uiState: StateFlow<ProfileCreationState> = _uiState.asStateFlow()
    
    private val authRepository = PlexAuthRepository(context)
    private val profileRepository = ProfileRepository(context)
    private val profileManager = ProfileManager.getInstance(context)
    
    // Predefined animal avatar images for profile selection (14 total)
    val avatarImages = listOf(
        "bear_avatar",
        "beaver_avatar",
        "cat_avatar",
        "cheetah_avatar",
        "deer_avatar",
        "dog_avatar",
        "elephant_avatar",
        "fox_avatar",
        "giraffe_avatar",
        "hedgehog_avatar",
        "koala_avatar",
        "lion_avatar",
        "monkey_avatar",
        "raccoon_avatar"
    )
    
    init {
        loadAvailableLibraries()
    }
    
    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }
    
    fun updateSelectedAvatarImage(avatarImage: String) {
        _uiState.value = _uiState.value.copy(selectedAvatarImage = avatarImage)
    }
    
    fun updateProfileType(profileType: ProfileType) {
        val updatedState = _uiState.value.copy(profileType = profileType)
        
        // Automatically set STRICT filter for child profiles
        val finalState = if (profileType == ProfileType.CHILD) {
            updatedState.copy(profanityFilterLevel = ProfanityFilterLevel.STRICT)
        } else {
            updatedState
        }
        
        _uiState.value = finalState
    }
    
    fun updateProfanityFilterLevel(level: ProfanityFilterLevel) {
        _uiState.value = _uiState.value.copy(profanityFilterLevel = level)
    }
    
    fun toggleLibrarySelection(libraryKey: String) {
        val currentSelection = _uiState.value.selectedLibraries
        val newSelection = if (currentSelection.contains(libraryKey)) {
            currentSelection - libraryKey
        } else {
            currentSelection + libraryKey
        }
        _uiState.value = _uiState.value.copy(selectedLibraries = newSelection)
    }
    
    fun selectAllLibraries() {
        val allLibraryKeys = _uiState.value.availableLibraries.map { it.key }
        _uiState.value = _uiState.value.copy(selectedLibraries = allLibraryKeys)
    }
    
    fun clearLibrarySelection() {
        _uiState.value = _uiState.value.copy(selectedLibraries = emptyList())
    }
    
    private fun loadAvailableLibraries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Get authentication token
                val token = authRepository.getAuthToken()
                if (token == null) {
                    _uiState.value = _uiState.value.copy(
                        availableLibraries = emptyList(),
                        error = "No Plex authentication found. Please connect to your Plex server first.",
                        isLoading = false
                    )
                    return@launch
                }
                
                val result = plexRepository.getLibrariesWithAuth(token)
                result.fold(
                    onSuccess = { libraries ->
                        _uiState.value = _uiState.value.copy(
                            availableLibraries = libraries,
                            isLoading = false
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            availableLibraries = emptyList(),
                            error = "Failed to load Plex libraries: ${exception.message}",
                            isLoading = false
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    availableLibraries = emptyList(),
                    error = "Error loading Plex libraries: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    fun createProfile(onSuccess: (Profile) -> Unit, onError: (String) -> Unit) {
        val currentState = _uiState.value
        
        // Validate input
        if (currentState.name.isBlank()) {
            onError("Profile name cannot be empty")
            return
        }
        
        if (currentState.selectedLibraries.isEmpty()) {
            onError("Please select at least one library")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            
            try {
                val profile = Profile(
                    id = UUID.randomUUID().toString(),
                    name = currentState.name.trim(),
                    avatarImage = currentState.selectedAvatarImage,
                    profileType = currentState.profileType,
                    profanityFilterLevel = currentState.profanityFilterLevel,
                    selectedLibraries = currentState.selectedLibraries
                )
                
                // Save profile to database
                android.util.Log.d("ProfileViewModel", "Attempting to save profile: ${profile.name}")
                profileRepository.insertProfile(profile)
                android.util.Log.d("ProfileViewModel", "Profile saved successfully: ${profile.id}")
                
                // Set as current profile in ProfileManager
                profileManager.setCurrentProfile(profile)
                
                _uiState.value = _uiState.value.copy(isCreating = false)
                onSuccess(profile)
                
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Failed to create profile", e)
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = "Failed to create profile: ${e.message}"
                )
                onError("Failed to create profile: ${e.message}")
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun resetForm() {
        _uiState.value = ProfileCreationState(availableLibraries = _uiState.value.availableLibraries)
    }
}