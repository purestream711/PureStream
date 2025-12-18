package com.purestream.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.purestream.data.repository.PlexRepository
import kotlinx.coroutines.launch

/**
 * Utility class to centralize Plex connection setup across ViewModels
 * Reduces duplicate connection logic and standardizes error handling
 */
object PlexConnectionHelper {
    
    /**
     * Sets up basic Plex connection with server URL and token
     * @param viewModel The ViewModel requesting the connection
     * @param plexRepository The repository to configure
     * @param serverUrl The Plex server URL
     * @param token The authentication token
     * @param onConnectionSet Optional callback after connection is set
     */
    fun setPlexConnection(
        viewModel: ViewModel,
        plexRepository: PlexRepository,
        serverUrl: String,
        token: String,
        onConnectionSet: (() -> Unit)? = null
    ) {
        plexRepository.setServerConnection(serverUrl, token)
        onConnectionSet?.invoke()
    }
    
    /**
     * Sets up Plex connection with authentication token and library filtering
     * Handles server discovery and provides standardized error handling
     * 
     * @param viewModel The ViewModel requesting the connection
     * @param plexRepository The repository to configure  
     * @param authToken The authentication token
     * @param selectedLibraries Optional list of selected library identifiers
     * @param onSuccess Callback with discovered libraries on success
     * @param onError Callback with error message on failure
     */
    fun setPlexConnectionWithAuth(
        viewModel: ViewModel,
        plexRepository: PlexRepository,
        authToken: String,
        selectedLibraries: List<String> = emptyList(),
        onSuccess: ((libraries: List<com.purestream.data.model.PlexLibrary>) -> Unit)? = null,
        onError: ((errorMessage: String) -> Unit)? = null
    ) {
        viewModel.viewModelScope.launch {
            try {
                val librariesResult = plexRepository.getLibrariesWithAuth(authToken)
                librariesResult.fold(
                    onSuccess = { libraries ->
                        onSuccess?.invoke(libraries)
                    },
                    onFailure = { exception ->
                        val errorMessage = "Failed to connect to Plex server. Please check your connection and try again. Error: ${exception.message}"
                        onError?.invoke(errorMessage)
                    }
                )
            } catch (e: Exception) {
                val errorMessage = "Failed to connect to Plex server. Please check your connection and try again. Error: ${e.message}"
                onError?.invoke(errorMessage)
            }
        }
    }
    
    /**
     * Simplified version of setPlexConnectionWithAuth for ViewModels that only need basic connection
     * @param viewModel The ViewModel requesting the connection
     * @param plexRepository The repository to configure
     * @param authToken The authentication token
     * @param onError Optional callback with error message on failure
     */
    fun setPlexConnectionWithAuthSimple(
        viewModel: ViewModel,
        plexRepository: PlexRepository,
        authToken: String,
        onError: ((errorMessage: String) -> Unit)? = null
    ) {
        setPlexConnectionWithAuth(
            viewModel = viewModel,
            plexRepository = plexRepository,
            authToken = authToken,
            selectedLibraries = emptyList(),
            onSuccess = null,
            onError = onError
        )
    }
}