package com.purestream.ui.viewmodel

import android.content.Context
import com.purestream.data.model.TvShow
import com.purestream.data.repository.PlexRepository

class TvShowsViewModel(
    context: Context,
    plexRepository: PlexRepository = PlexRepository(context)
) : BaseContentViewModel<TvShow>(plexRepository) {
    
    override val libraryType = "show"
    override val itemTypeName = "TV Show"
    override val itemTypeNamePlural = "TV shows"
    
    override suspend fun fetchItemsFromRepository(libraryId: String): Result<List<TvShow>> {
        return plexRepository.getTvShows(libraryId)
    }
    
    override fun getSortTitle(item: TvShow): String {
        return item.sortTitle
    }

    override fun getRatingKey(item: TvShow): String {
        return item.ratingKey
    }

    // Convenience methods that maintain the old API for UI compatibility
    fun loadTvShows(libraryId: String) = loadItems(libraryId)
    fun loadMoreTvShows() = loadMoreItems()
    fun refreshTvShows() = refreshItems()
    fun setLastFocusedTvShowId(tvShowId: String) = setLastFocusedItemId(tvShowId)
    fun clearLastFocusedTvShowId() = clearLastFocusedItemId()
    fun selectTvShowLibrary(libraryId: String) = selectLibrary(libraryId)

    // Convenience properties for UI compatibility
    val tvShows get() = uiState.value.items
    val lastFocusedTvShowId get() = lastFocusedItemId
}