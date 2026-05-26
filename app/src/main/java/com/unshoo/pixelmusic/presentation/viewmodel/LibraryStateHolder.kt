package com.unshoo.pixelmusic.presentation.viewmodel

import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.MusicFolder
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

private const val ENABLE_FOLDERS_STORAGE_FILTER = false

/**
 * Library state stub — all local library data loading has been removed.
 * The library section is now backed exclusively by YouTube/streaming.
 * Flows emit empty lists; the UI shows empty-state placeholders.
 */
@Singleton
class LibraryStateHolder @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository
) {

    // --- State (always empty — local library removed) ---
    private val _allSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val allSongs = _allSongs.asStateFlow()

    private val _allSongsById = MutableStateFlow<Map<String, Song>>(emptyMap())
    val allSongsById = _allSongsById.asStateFlow()

    private val _albums = MutableStateFlow<ImmutableList<Album>>(persistentListOf())
    val albums = _albums.asStateFlow()

    private val _artists = MutableStateFlow<ImmutableList<Artist>>(persistentListOf())
    val artists = _artists.asStateFlow()

    private val _musicFolders = MutableStateFlow<ImmutableList<MusicFolder>>(persistentListOf())
    val musicFolders = _musicFolders.asStateFlow()

    private val _isLoadingLibrary = MutableStateFlow(false)
    val isLoadingLibrary = _isLoadingLibrary.asStateFlow()

    private val _isLoadingCategories = MutableStateFlow(false)
    val isLoadingCategories = _isLoadingCategories.asStateFlow()

    private val _currentSongSortOption = MutableStateFlow<SortOption>(SortOption.SongDefaultOrder)
    val currentSongSortOption = _currentSongSortOption.asStateFlow()

    private val _currentStorageFilter = MutableStateFlow(com.unshoo.pixelmusic.data.model.StorageFilter.ALL)
    val currentStorageFilter = _currentStorageFilter.asStateFlow()

    private val _currentAlbumSortOption = MutableStateFlow<SortOption>(SortOption.AlbumTitleAZ)
    val currentAlbumSortOption = _currentAlbumSortOption.asStateFlow()

    private val _currentArtistSortOption = MutableStateFlow<SortOption>(SortOption.ArtistNameAZ)
    val currentArtistSortOption = _currentArtistSortOption.asStateFlow()

    private val _currentFolderSortOption = MutableStateFlow<SortOption>(SortOption.FolderNameAZ)
    val currentFolderSortOption = _currentFolderSortOption.asStateFlow()

    private val _currentFavoriteSortOption = MutableStateFlow<SortOption>(SortOption.LikedSongDateLiked)
    val currentFavoriteSortOption = _currentFavoriteSortOption.asStateFlow()

    // Paging flows all return empty (local library removed)
    val songsPagingFlow: Flow<androidx.paging.PagingData<Song>> =
        flowOf(androidx.paging.PagingData.empty())

    val albumsPagingFlow: Flow<androidx.paging.PagingData<Album>> =
        flowOf(androidx.paging.PagingData.empty())

    val artistsPagingFlow: Flow<androidx.paging.PagingData<Artist>> =
        flowOf(androidx.paging.PagingData.empty())

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoritesPagingFlow: Flow<androidx.paging.PagingData<Song>> = combine(
        _currentFavoriteSortOption,
        _currentStorageFilter
    ) { sortOption, storageFilter ->
        sortOption to storageFilter
    }.flatMapLatest { (sortOption, storageFilter) ->
        musicRepository.getPaginatedFavoriteSongs(sortOption, storageFilter)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteSongCountFlow: Flow<Int> = _currentStorageFilter.flatMapLatest { storageFilter ->
        musicRepository.getFavoriteSongCountFlow(storageFilter)
    }

    val genres: Flow<ImmutableList<com.unshoo.pixelmusic.data.model.Genre>> =
        flowOf(persistentListOf())

    fun initialize(scope: CoroutineScope) {
        // No-op: local library loading removed
    }

    fun onCleared() {}

    // --- No-op data loaders ---
    fun startObservingLibraryData() {}
    fun loadSongsFromRepository() {}
    fun loadAlbumsFromRepository() {}
    fun loadArtistsFromRepository() {}
    fun loadFoldersFromRepository() {}
    fun loadSongsIfNeeded() {}
    fun loadAlbumsIfNeeded() {}
    fun loadArtistsIfNeeded() {}

    // Sort stubs (keep so callers compile)
    fun sortSongs(sortOption: SortOption, persist: Boolean = true) {
        _currentSongSortOption.value = sortOption
    }

    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) {
        _currentAlbumSortOption.value = sortOption
    }

    fun sortArtists(sortOption: SortOption, persist: Boolean = true) {
        _currentArtistSortOption.value = sortOption
    }

    fun sortFolders(sortOption: SortOption, persist: Boolean = true) {
        _currentFolderSortOption.value = sortOption
    }

    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) {
        _currentFavoriteSortOption.value = sortOption
    }

    fun updateSong(updatedSong: Song) {
        _allSongs.update { currentList ->
            currentList.map { if (it.id == updatedSong.id) updatedSong else it }.toImmutableList()
        }
    }

    fun removeSong(songId: String) {
        _allSongs.update { it.filter { s -> s.id != songId }.toImmutableList() }
    }

    fun setStorageFilter(filter: com.unshoo.pixelmusic.data.model.StorageFilter) {
        _currentStorageFilter.value = filter
    }

    fun trimMemory(level: Int) {
        // No-op: no local data to trim
    }

    fun restoreAfterTrimIfNeeded() {
        // No-op
    }
}
