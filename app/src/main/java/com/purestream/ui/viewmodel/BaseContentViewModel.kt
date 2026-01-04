package com.purestream.ui.viewmodel

import android.content.Context
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.purestream.data.model.PlexLibrary
import com.purestream.data.repository.PlexRepository
import com.purestream.data.repository.WatchProgressRepository
import com.purestream.data.database.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContentState<T>(
    val items: List<T> = emptyList(),
    val libraries: List<PlexLibrary> = emptyList(),
    val selectedLibraryId: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
    val pageSize: Int = 50,
    val currentPage: Int = 0,
    val progressMap: Map<String, Float> = emptyMap() // ratingKey -> progress percentage (0.0-1.0)
)

abstract class BaseContentViewModel<T>(
    protected val plexRepository: PlexRepository
) : ViewModel() {

    protected val _uiState = MutableStateFlow(ContentState<T>())
    val uiState: StateFlow<ContentState<T>> = _uiState.asStateFlow()

    // Persistent grid state to maintain scroll position
    val gridState = LazyGridState()

    // Focus tracking to restore focus when returning from details screen
    private val _lastFocusedItemId = MutableStateFlow<String?>(null)
    val lastFocusedItemId: StateFlow<String?> = _lastFocusedItemId.asStateFlow()

    // Store all sorted items for pagination
    private var allSortedItems: List<T> = emptyList()

    // Watch progress tracking
    protected var watchProgressRepository: WatchProgressRepository? = null
    protected var currentProfileId: String = ""

    // Profile tracking for library preferences
    private var currentProfile: com.purestream.data.model.Profile? = null

    // Abstract methods for type-specific operations
    protected abstract val libraryType: String
    protected abstract suspend fun fetchItemsFromRepository(libraryId: String): Result<List<T>>
    protected abstract fun getSortTitle(item: T): String
    protected abstract val itemTypeName: String
    protected abstract val itemTypeNamePlural: String
    protected abstract fun getRatingKey(item: T): String  // New: get ratingKey from item
    
    fun loadLibraries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val librariesResult = plexRepository.getLibraries()
                librariesResult.fold(
                    onSuccess = { allLibraries ->
                        // Filter for libraries of the specific type
                        val typeLibraries = allLibraries.filter { it.type == libraryType }
                        
                        // Further filter by user's selected libraries
                        val filteredLibraries = if (currentProfile != null) {
                            val selectedKeys = currentProfile!!.selectedLibraries
                            typeLibraries.filter { library ->
                                selectedKeys.any { key ->
                                    library.key == key || library.title.equals(key, ignoreCase = true)
                                }
                            }
                        } else {
                            typeLibraries
                        }

                        _uiState.value = _uiState.value.copy(
                            libraries = filteredLibraries,
                            isLoading = false
                        )
                        
                        // Automatically load items from the first library of this type
                        if (filteredLibraries.isNotEmpty() && _uiState.value.selectedLibraryId == null) {
                            // Try to use preferred library if available
                            val preferredLibraryId = when (libraryType) {
                                "movie" -> currentProfile?.preferredMovieLibraryId
                                "show" -> currentProfile?.preferredTvShowLibraryId
                                else -> null
                            }

                            val libraryToLoad = if (preferredLibraryId != null &&
                                                    filteredLibraries.any { lib -> lib.key == preferredLibraryId }) {
                                preferredLibraryId
                            } else {
                                filteredLibraries.first().key
                            }
                            loadItems(libraryToLoad!!)
                        }
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to load libraries: ${exception.message}",
                            isLoading = false
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load libraries: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    fun loadItems(libraryId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, 
                error = null,
                selectedLibraryId = libraryId,
                items = emptyList(),
                currentPage = 0,
                canLoadMore = true
            )
            
            try {
                val itemsResult = fetchItemsFromRepository(libraryId)
                itemsResult.fold(
                    onSuccess = { allItems ->
                        // Sort items alphabetically by title (excluding articles)
                        val sortedItems = allItems.sortedBy { getSortTitle(it) }
                        val pageSize = _uiState.value.pageSize
                        val firstPage = sortedItems.take(pageSize)
                        
                        _uiState.value = _uiState.value.copy(
                            items = firstPage,
                            isLoading = false,
                            canLoadMore = sortedItems.size > pageSize,
                            currentPage = 0
                        )

                        // Store all items for pagination
                        allSortedItems = sortedItems

                        // Load watch progress for items
                        loadProgressForItems()
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to load $itemTypeNamePlural: ${exception.message}",
                            isLoading = false
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load $itemTypeNamePlural: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    fun loadMoreItems() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.canLoadMore) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            
            try {
                val nextPage = currentState.currentPage + 1
                val startIndex = nextPage * currentState.pageSize
                val endIndex = minOf(startIndex + currentState.pageSize, allSortedItems.size)
                
                if (startIndex < allSortedItems.size) {
                    val nextPageItems = allSortedItems.subList(startIndex, endIndex)
                    val updatedItems = currentState.items + nextPageItems
                    
                    _uiState.value = _uiState.value.copy(
                        items = updatedItems,
                        isLoadingMore = false,
                        currentPage = nextPage,
                        canLoadMore = endIndex < allSortedItems.size
                    )

                    // Load watch progress for new items
                    loadProgressForItems()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        canLoadMore = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = "Failed to load more $itemTypeNamePlural: ${e.message}"
                )
            }
        }
    }
    
    fun refreshItems() {
        val currentLibraryId = _uiState.value.selectedLibraryId
        if (currentLibraryId != null) {
            loadItems(currentLibraryId)
        } else {
            loadLibraries()
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun setPlexConnection(serverUrl: String, token: String) {
        com.purestream.utils.PlexConnectionHelper.setPlexConnection(
            viewModel = this,
            plexRepository = plexRepository,
            serverUrl = serverUrl,
            token = token,
            onConnectionSet = { loadLibraries() }
        )
    }
    
    fun setPlexConnectionWithAuth(authToken: String, selectedLibraries: List<String> = emptyList()) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        com.purestream.utils.PlexConnectionHelper.setPlexConnectionWithAuth(
            viewModel = this,
            plexRepository = plexRepository,
            authToken = authToken,
            selectedLibraries = selectedLibraries,
            onSuccess = { allLibraries ->
                // Filter for libraries of the specific type
                val typeLibraries = allLibraries.filter { it.type == libraryType }
                
                android.util.Log.d(this::class.simpleName, "Filtering libraries for type '$libraryType'")
                android.util.Log.d(this::class.simpleName, "Available type libraries: ${typeLibraries.map { "${it.title} (${it.key})" }}")
                
                // Further filter by user's selected libraries (mandatory if profile exists)
                val librariesToFilterBy = if (selectedLibraries.isNotEmpty()) {
                    selectedLibraries
                } else {
                    currentProfile?.selectedLibraries ?: emptyList()
                }
                
                android.util.Log.d(this::class.simpleName, "Filtering by: $librariesToFilterBy")
                
                val filteredLibraries = if (librariesToFilterBy.isNotEmpty() || currentProfile != null) {
                    typeLibraries.filter { library -> 
                        librariesToFilterBy.any { selectedLib -> 
                            library.key == selectedLib || library.title.equals(selectedLib, ignoreCase = true)
                        }
                    }
                } else {
                    typeLibraries
                }
                
                android.util.Log.d(this::class.simpleName, "Final filtered libraries: ${filteredLibraries.map { "${it.title} (${it.key})" }}")
                
                _uiState.value = _uiState.value.copy(
                    libraries = filteredLibraries,
                    isLoading = false
                )
                
                // Check if user has any libraries of this type selected
                if (selectedLibraries.isNotEmpty() && filteredLibraries.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        error = "No $itemTypeName Library Available - This profile has no $itemTypeName libraries selected. Please select $itemTypeName libraries in your profile settings.",
                        isLoading = false
                    )
                } else if (filteredLibraries.isNotEmpty() && _uiState.value.selectedLibraryId == null) {
                    // Try to use preferred library if available and valid
                    val preferredLibraryId = when (libraryType) {
                        "movie" -> currentProfile?.preferredMovieLibraryId
                        "show" -> currentProfile?.preferredTvShowLibraryId
                        else -> null
                    }

                    val libraryToLoad = if (preferredLibraryId != null &&
                                            filteredLibraries.any { it.key == preferredLibraryId }) {
                        android.util.Log.d(this::class.simpleName, "Using preferred library: $preferredLibraryId")
                        preferredLibraryId
                    } else {
                        // Fallback to first available library
                        android.util.Log.d(this::class.simpleName, "Using first available library: ${filteredLibraries.first().key}")
                        filteredLibraries.first().key
                    }

                    loadItems(libraryToLoad)
                } else if (typeLibraries.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        error = "No $itemTypeName libraries found on Plex server",
                        isLoading = false
                    )
                }
            },
            onError = { errorMessage ->
                _uiState.value = _uiState.value.copy(
                    error = errorMessage,
                    isLoading = false
                )
            }
        )
    }

    fun setCurrentProfile(profile: com.purestream.data.model.Profile) {
        currentProfile = profile
    }

    fun getCurrentProfile(): com.purestream.data.model.Profile? {
        return currentProfile
    }

    fun selectLibrary(libraryId: String) {
        loadItems(libraryId)
    }

    fun setPreferredLibrary(libraryId: String, profileRepository: com.purestream.data.repository.ProfileRepository, profileId: String) {
        viewModelScope.launch {
            try {
                val profile = profileRepository.getProfileById(profileId)
                profile?.let { currentProfile ->
                    val updatedProfile = when (libraryType) {
                        "movie" -> currentProfile.copy(preferredMovieLibraryId = libraryId)
                        "show" -> currentProfile.copy(preferredTvShowLibraryId = libraryId)
                        else -> currentProfile
                    }
                    profileRepository.updateProfile(updatedProfile)
                    android.util.Log.d(this::class.simpleName, "Saved preferred library: $libraryId for type: $libraryType")
                }
            } catch (e: Exception) {
                android.util.Log.e(this::class.simpleName, "Failed to save preferred library: ${e.message}")
            }
        }
    }

    fun setLastFocusedItemId(itemId: String) {
        _lastFocusedItemId.value = itemId
    }

    fun clearLastFocusedItemId() {
        _lastFocusedItemId.value = null
    }

    // Initialize watch progress repository with context and profile
    fun initializeProgressTracking(context: android.content.Context, profileId: String) {
        watchProgressRepository = WatchProgressRepository(AppDatabase.getDatabase(context), context)
        currentProfileId = profileId
        // Load progress for current items if any exist
        if (_uiState.value.items.isNotEmpty()) {
            loadProgressForItems()
        }
    }

    // Load watch progress for all currently loaded items
    fun loadProgressForItems() {
        val repository = watchProgressRepository
        val profileId = currentProfileId

        if (repository == null || profileId.isEmpty()) {
            android.util.Log.w(this::class.simpleName, "Cannot load progress - repository=${repository != null}, profileId='$profileId'")
            return
        }

        android.util.Log.d(this::class.simpleName, "📂 Loading progress for ${_uiState.value.items.size} items, profileId=$profileId")

        viewModelScope.launch {
            try {
                val progressMap = mutableMapOf<String, Float>()

                withContext(Dispatchers.IO) {
                    for (item in _uiState.value.items) {
                        val ratingKey = getRatingKey(item)
                        val progress = repository.getProgressPercentage(profileId, ratingKey)
                        if (progress > 0.01f) {  // Only include items with meaningful progress
                            progressMap[ratingKey] = progress
                            android.util.Log.d(this::class.simpleName, "  ✓ Found progress: $ratingKey = ${(progress * 100).toInt()}%")
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(progressMap = progressMap)
                android.util.Log.d(this::class.simpleName, "✓ Loaded progress for ${progressMap.size} items with data")
            } catch (e: Exception) {
                android.util.Log.e(this::class.simpleName, "✗ Error loading progress: ${e.message}", e)
            }
        }
    }

    open fun reset() {
        _uiState.value = ContentState()
        allSortedItems = emptyList()
        _lastFocusedItemId.value = null
        currentProfile = null
    }
}