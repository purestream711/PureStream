package com.purestream.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.purestream.data.api.SearchResult
import com.purestream.data.repository.PlexRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class SearchUiState(
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val pageSize: Int = 50,
    val currentPage: Int = 0
)

@OptIn(FlowPreview::class)
class SearchViewModel(context: Context) : ViewModel() {

    private val plexRepository = PlexRepository(context)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var currentProfileSelectedLibraries: List<String> = emptyList()

    private val searchQueryFlow = MutableStateFlow("")

    // Store all search results for pagination
    private var allSearchResults: List<SearchResult> = emptyList()
    
    init {
        // Set up debounced search
        viewModelScope.launch {
            searchQueryFlow
                .debounce(500) // Wait 500ms after user stops typing
                .collect { query ->
                    if (query.isNotEmpty() && query.length >= 2) {
                        performSearch(query)
                    } else if (query.isEmpty()) {
                        clearSearch()
                    }
                }
        }
    }
    
    fun setPlexConnection(serverUrl: String, token: String) {
        com.purestream.utils.PlexConnectionHelper.setPlexConnection(
            viewModel = this,
            plexRepository = plexRepository,
            serverUrl = serverUrl,
            token = token
        )
        Log.d("SearchViewModel", "Plex connection set: $serverUrl")
    }
    
    fun setPlexConnectionWithAuth(token: String, selectedLibraries: List<String> = emptyList()) {
        currentProfileSelectedLibraries = selectedLibraries
        
        com.purestream.utils.PlexConnectionHelper.setPlexConnectionWithAuth(
            viewModel = this,
            plexRepository = plexRepository,
            authToken = token,
            selectedLibraries = selectedLibraries,
            onSuccess = { libraries ->
                Log.d("SearchViewModel", "Plex connection established with auth token, ${libraries.size} libraries found")
                Log.d("SearchViewModel", "Selected libraries for search: $selectedLibraries")
            },
            onError = { errorMessage ->
                Log.e("SearchViewModel", "Failed to establish Plex connection with auth: $errorMessage")
                _uiState.value = _uiState.value.copy(error = errorMessage)
            }
        )
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            error = null // Clear previous errors when starting new search
        )
        searchQueryFlow.value = query
    }
    
    fun performSearch(query: String) {
        if (query.isBlank() || query.length < 2) {
            return
        }
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isSearching = true,
                    error = null
                )
                
                Log.d("SearchViewModel", "Performing search for: '$query'")
                
                // Use the PlexApiService directly through the repository's serverApiService
                val searchResults = mutableListOf<SearchResult>()
                
                // Try to search using the Plex search endpoint
                try {
                    val searchResult = searchContent(query)
                    searchResult.fold(
                        onSuccess = { results ->
                            searchResults.addAll(results)
                            Log.d("SearchViewModel", "Search completed: ${results.size} results found")

                            // Store all results for pagination
                            allSearchResults = searchResults

                            // Return only the first page
                            val pageSize = _uiState.value.pageSize
                            val firstPage = searchResults.take(pageSize)

                            _uiState.value = _uiState.value.copy(
                                searchResults = firstPage,
                                isSearching = false,
                                error = null,
                                hasSearched = true,
                                currentPage = 0,
                                canLoadMore = searchResults.size > pageSize
                            )
                        },
                        onFailure = { exception ->
                            Log.e("SearchViewModel", "Search failed", exception)
                            _uiState.value = _uiState.value.copy(
                                isSearching = false,
                                error = "Search failed: ${exception.message}",
                                hasSearched = true
                            )
                            return@launch
                        }
                    )
                } catch (e: Exception) {
                    Log.e("SearchViewModel", "Exception during search", e)
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        error = "Search error: ${e.message}",
                        hasSearched = true
                    )
                    return@launch
                }
                
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Unexpected error during search", e)
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = "Unexpected error: ${e.message}",
                    hasSearched = true
                )
            }
        }
    }
    
    private suspend fun searchContent(query: String): Result<List<SearchResult>> {
        return try {
            // Create a custom search implementation using the PlexRepository
            // Since the repository's searchContent method returns ContentItem, we need to use the API directly
            
            // For now, let's create a simple search that looks through movies and TV shows
            val allResults = mutableListOf<SearchResult>()
            
            // Get selected libraries from current profile (if available)
            val selectedLibraries = currentProfileSelectedLibraries
            
            // Get libraries first
            val librariesResult = plexRepository.getLibraries()
            librariesResult.fold(
                onSuccess = { allLibraries ->
                    // Filter libraries based on profile selection
                    val filteredLibraries = if (selectedLibraries.isNotEmpty()) {
                        allLibraries.filter { library -> 
                            selectedLibraries.any { selectedLib -> 
                                library.key == selectedLib || library.title.equals(selectedLib, ignoreCase = true)
                            }
                        }
                    } else {
                        allLibraries
                    }
                    
                    // Search through movie libraries
                    val movieLibraries = filteredLibraries.filter { it.type == "movie" }
                    for (library in movieLibraries) {
                        val moviesResult = plexRepository.getMovies(library.key)
                        moviesResult.fold(
                            onSuccess = { movies ->
                                val matchingMovies = movies.filter { movie ->
                                    movie.title.contains(query, ignoreCase = true) ||
                                    movie.summary?.contains(query, ignoreCase = true) == true
                                }
                                
                                matchingMovies.forEach { movie ->
                                    allResults.add(
                                        SearchResult(
                                            ratingKey = movie.ratingKey,
                                            key = movie.key ?: "/library/metadata/${movie.ratingKey}",
                                            title = movie.title,
                                            type = "movie",
                                            summary = movie.summary,
                                            thumb = movie.thumbUrl,
                                            art = movie.artUrl,
                                            year = movie.year,
                                            rating = movie.rating,
                                            reason = "Title match",
                                            reasonTitle = movie.title,
                                            reasonID = movie.ratingKey
                                        )
                                    )
                                }
                            },
                            onFailure = { error ->
                                Log.w("SearchViewModel", "Failed to search movies in library ${library.title}: ${error.message}")
                            }
                        )
                    }
                    
                    // Search through TV show libraries
                    val showLibraries = filteredLibraries.filter { it.type == "show" }
                    for (library in showLibraries) {
                        val showsResult = plexRepository.getTvShows(library.key)
                        showsResult.fold(
                            onSuccess = { shows ->
                                val matchingShows = shows.filter { show ->
                                    show.title.contains(query, ignoreCase = true) ||
                                    show.summary?.contains(query, ignoreCase = true) == true
                                }
                                
                                matchingShows.forEach { show ->
                                    allResults.add(
                                        SearchResult(
                                            ratingKey = show.ratingKey,
                                            key = show.key ?: "/library/metadata/${show.ratingKey}",
                                            title = show.title,
                                            type = "show",
                                            summary = show.summary,
                                            thumb = show.thumbUrl,
                                            art = show.artUrl,
                                            year = show.year,
                                            rating = show.rating,
                                            reason = "Title match",
                                            reasonTitle = show.title,
                                            reasonID = show.ratingKey
                                        )
                                    )
                                }
                            },
                            onFailure = { error ->
                                Log.w("SearchViewModel", "Failed to search TV shows in library ${library.title}: ${error.message}")
                            }
                        )
                    }
                },
                onFailure = { error ->
                    Log.e("SearchViewModel", "Failed to get libraries for search: ${error.message}")
                    return Result.failure(error)
                }
            )
            
            // Sort results by relevance (exact matches first, then partial matches)
            val sortedResults = allResults.sortedWith(compareBy<SearchResult> { result ->
                when {
                    result.title.equals(query, ignoreCase = true) -> 0 // Exact match
                    result.title.startsWith(query, ignoreCase = true) -> 1 // Starts with query
                    else -> 2 // Contains query
                }
            }.thenBy { it.title })
            
            Result.success(sortedResults) // Return all results for pagination
            
        } catch (e: Exception) {
            Log.e("SearchViewModel", "Exception in searchContent", e)
            Result.failure(e)
        }
    }
    
    fun clearSearch() {
        _uiState.value = SearchUiState() // Reset to initial state
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun submitSearch(query: String) {
        updateSearchQuery(query)
        if (query.isNotEmpty() && query.length >= 2) {
            performSearch(query)
        }
    }

    fun loadMoreResults() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.canLoadMore) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)

            try {
                val nextPage = currentState.currentPage + 1
                val startIndex = nextPage * currentState.pageSize
                val endIndex = minOf(startIndex + currentState.pageSize, allSearchResults.size)

                if (startIndex < allSearchResults.size) {
                    val nextPageResults = allSearchResults.subList(startIndex, endIndex)
                    val updatedResults = currentState.searchResults + nextPageResults

                    _uiState.value = _uiState.value.copy(
                        searchResults = updatedResults,
                        isLoadingMore = false,
                        currentPage = nextPage,
                        canLoadMore = endIndex < allSearchResults.size
                    )

                    Log.d("SearchViewModel", "Loaded more results: page $nextPage, showing ${updatedResults.size} of ${allSearchResults.size}")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        canLoadMore = false
                    )
                }
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Error loading more results: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = "Failed to load more results: ${e.message}"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("SearchViewModel", "SearchViewModel cleared")
    }
}