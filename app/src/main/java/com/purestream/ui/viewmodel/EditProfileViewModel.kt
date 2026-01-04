package com.purestream.ui.viewmodel
import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.purestream.data.model.*
import com.purestream.data.repository.PlexRepository
import com.purestream.data.repository.PlexAuthRepository
import com.purestream.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditProfileState(
    val profile: Profile? = null,
    val name: String = "",
    val selectedAvatarImage: String = "cat_avatar",
    val profileType: ProfileType = ProfileType.ADULT,
    val profanityFilterLevel: ProfanityFilterLevel = ProfanityFilterLevel.MILD,
    val selectedLibraries: List<String> = emptyList(),
    val availableLibraries: List<PlexLibrary> = emptyList(),
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false,
    val error: String? = null,
    val isDefaultProfile: Boolean = false
)

class EditProfileViewModel(
    private val context: android.content.Context,
    private val profileId: String,
    private val plexRepository: PlexRepository = PlexRepository(context),
    private val profileRepository: ProfileRepository = ProfileRepository(context)
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EditProfileState())
    val uiState: StateFlow<EditProfileState> = _uiState.asStateFlow()
    
    private val authRepository = PlexAuthRepository(context)
    
    // Same avatar images as ProfileViewModel
    val avatarImages = AvatarConstants.AVATAR_IMAGES
    
    init {
        loadProfile()
        loadAvailableLibraries()
    }
    
    private fun loadProfile() {
        viewModelScope.launch {
            try {
                val profile = profileRepository.getProfileById(profileId)
                if (profile != null) {
                    _uiState.value = _uiState.value.copy(
                        profile = profile,
                        name = profile.name,
                        selectedAvatarImage = profile.avatarImage,
                        profileType = profile.profileType,
                        profanityFilterLevel = profile.profanityFilterLevel,
                        selectedLibraries = profile.selectedLibraries,
                        isDefaultProfile = profile.isDefaultProfile,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Profile not found",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load profile: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    private fun loadAvailableLibraries() {
        viewModelScope.launch {
            try {
                val token = authRepository.getAuthToken()
                if (token == null) {
                    _uiState.value = _uiState.value.copy(
                        availableLibraries = emptyList(),
                        error = "No Plex authentication found. Please connect to your Plex server first."
                    )
                    return@launch
                }
                
                val result = plexRepository.getLibrariesWithAuth(token)
                result.fold(
                    onSuccess = { libraries ->
                        // Filter out "Artist" libraries (Music) as they are not supported/needed
                        val filteredLibraries = libraries.filter { it.type.lowercase() != "artist" }
                        _uiState.value = _uiState.value.copy(availableLibraries = filteredLibraries)
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            availableLibraries = emptyList(),
                            error = "Failed to load Plex libraries: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    availableLibraries = emptyList(),
                    error = "Error loading Plex libraries: ${e.message}"
                )
            }
        }
    }
    
    
    fun updateName(name: String) {
        val currentProfile = _uiState.value.profile
        _uiState.value = _uiState.value.copy(
            name = name,
            profile = currentProfile?.copy(name = name)
        )
    }
    
    fun updateSelectedAvatarImage(avatarImage: String) {
        val currentProfile = _uiState.value.profile
        _uiState.value = _uiState.value.copy(
            selectedAvatarImage = avatarImage,
            profile = currentProfile?.copy(avatarImage = avatarImage)
        )
    }
    
    fun updateProfileType(profileType: ProfileType) {
        val currentProfile = _uiState.value.profile
        
        // Automatically set STRICT filter for child profiles
        val filterLevel = if (profileType == ProfileType.CHILD) {
            ProfanityFilterLevel.STRICT
        } else {
            _uiState.value.profanityFilterLevel
        }
        
        _uiState.value = _uiState.value.copy(
            profileType = profileType,
            profanityFilterLevel = filterLevel,
            profile = currentProfile?.copy(
                profileType = profileType,
                profanityFilterLevel = filterLevel
            )
        )
    }
    
    fun updateProfanityFilterLevel(level: ProfanityFilterLevel) {
        val currentProfile = _uiState.value.profile
        _uiState.value = _uiState.value.copy(
            profanityFilterLevel = level,
            profile = currentProfile?.copy(profanityFilterLevel = level)
        )
    }

    fun updateIsDefaultProfile(isDefault: Boolean) {
        val currentProfile = _uiState.value.profile
        _uiState.value = _uiState.value.copy(
            isDefaultProfile = isDefault,
            profile = currentProfile?.copy(isDefaultProfile = isDefault)
        )
    }

    fun toggleLibrarySelection(libraryKey: String) {
        val currentSelection = _uiState.value.selectedLibraries
        val newSelection = if (currentSelection.contains(libraryKey)) {
            currentSelection - libraryKey
        } else {
            currentSelection + libraryKey
        }
        val currentProfile = _uiState.value.profile
        _uiState.value = _uiState.value.copy(
            selectedLibraries = newSelection,
            profile = currentProfile?.copy(selectedLibraries = newSelection)
        )
    }
    
    fun selectAllLibraries() {
        val allLibraryKeys = _uiState.value.availableLibraries.map { it.key }
        val currentProfile = _uiState.value.profile
        _uiState.value = _uiState.value.copy(
            selectedLibraries = allLibraryKeys,
            profile = currentProfile?.copy(selectedLibraries = allLibraryKeys)
        )
    }
    
    fun clearLibrarySelection() {
        val currentProfile = _uiState.value.profile
        _uiState.value = _uiState.value.copy(
            selectedLibraries = emptyList(),
            profile = currentProfile?.copy(selectedLibraries = emptyList())
        )
    }
    
    fun updateProfile(onSuccess: (Profile) -> Unit, onError: (String) -> Unit) {
        val currentState = _uiState.value
        val originalProfile = currentState.profile ?: return
        
        if (currentState.name.isBlank()) {
            onError("Profile name cannot be empty")
            return
        }
        
        if (currentState.selectedLibraries.isEmpty()) {
            onError("Please select at least one library")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, error = null)
            
            try {
                val updatedProfile = originalProfile.copy(
                    name = currentState.name.trim(),
                    avatarImage = currentState.selectedAvatarImage,
                    profileType = currentState.profileType,
                    profanityFilterLevel = currentState.profanityFilterLevel,
                    selectedLibraries = currentState.selectedLibraries,
                    isDefaultProfile = currentState.isDefaultProfile
                )

                profileRepository.updateProfile(updatedProfile)

                // If this is set as default profile, ensure only one profile is default
                if (currentState.isDefaultProfile) {
                    profileRepository.setDefaultProfile(updatedProfile.id)
                }

                _uiState.value = _uiState.value.copy(
                    profile = updatedProfile,
                    isUpdating = false
                )
                onSuccess(updatedProfile)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    error = "Failed to update profile: ${e.message}"
                )
                onError("Failed to update profile: ${e.message}")
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}