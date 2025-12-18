package com.purestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.purestream.data.model.Profile
import com.purestream.data.repository.ProfileRepository
import com.purestream.data.manager.ProfileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ProfileSelectionState(
    val profiles: List<Profile> = emptyList(),
    val selectedProfile: Profile? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ProfileSelectionViewModel(
    private val profileRepository: ProfileRepository,
    private val context: android.content.Context
) : ViewModel() {
    
    private val profileManager = ProfileManager.getInstance(context)
    
    private val _uiState = MutableStateFlow(ProfileSelectionState())
    val uiState: StateFlow<ProfileSelectionState> = _uiState.asStateFlow()
    
    init {
        loadProfiles()
    }
    
    fun loadProfiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            profileRepository.getAllProfiles()
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load profiles: ${exception.message}"
                    )
                }
                .collect { profiles ->
                    _uiState.value = _uiState.value.copy(
                        profiles = profiles,
                        isLoading = false,
                        error = null
                    )
                }
        }
    }
    
    fun selectProfile(profile: Profile) {
        _uiState.value = _uiState.value.copy(selectedProfile = profile)
        // Also set in ProfileManager
        viewModelScope.launch {
            profileManager.setCurrentProfile(profile)
        }
    }
    
    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            try {
                profileRepository.deleteProfile(profile)
                // Profiles will be automatically updated through the Flow
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete profile: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}