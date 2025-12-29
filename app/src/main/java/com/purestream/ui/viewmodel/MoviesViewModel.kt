package com.purestream.ui.viewmodel

import android.content.Context
import com.purestream.data.model.Movie
import com.purestream.data.repository.PlexRepository

class MoviesViewModel(
    context: Context,
    plexRepository: PlexRepository = PlexRepository(context)
) : BaseContentViewModel<Movie>(plexRepository) {
    
    override val libraryType = "movie"
    override val itemTypeName = "Movie"
    override val itemTypeNamePlural = "movies"
    
    override suspend fun fetchItemsFromRepository(libraryId: String): Result<List<Movie>> {
        return plexRepository.getMovies(libraryId)
    }
    
    override fun getSortTitle(item: Movie): String {
        return item.sortTitle
    }

    override fun getRatingKey(item: Movie): String {
        return item.ratingKey
    }

    // Convenience methods that maintain the old API for UI compatibility
    fun loadMovies(libraryId: String) = loadItems(libraryId)
    fun loadMoreMovies() = loadMoreItems()
    fun refreshMovies() = refreshItems()
    fun setLastFocusedMovieId(movieId: String) = setLastFocusedItemId(movieId)
    fun clearLastFocusedMovieId() = clearLastFocusedItemId()
    fun selectMovieLibrary(libraryId: String) = selectLibrary(libraryId)

    // Convenience properties for UI compatibility
    val movies get() = uiState.value.items
    val lastFocusedMovieId get() = lastFocusedItemId
}