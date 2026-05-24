package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.model.Genre
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem as YtSongItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.BrowseResult
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong

// --- Model Types for Sectioned Display ---

enum class SortOption { ARTIST, ALBUM, TITLE }

data class AlbumData(
    val name: String,
    val artUri: String?,
    val songs: List<Song>
)

sealed class SectionData {
    abstract val id: String

    data class ArtistSection(
        override val id: String,
        val artistName: String,
        val albums: List<AlbumData>
    ) : SectionData()

    data class AlbumSection(
        override val id: String,
        val album: AlbumData
    ) : SectionData()

    data class FlatList(
        val songs: List<Song>
    ) : SectionData() {
        override val id = "flat_list"
    }
}

/**
 * Representa un elemento visual en la lista aplanada para mejorar el rendimiento de LazyColumn.
 */
sealed class GenreDetailListItem {
    abstract val key: String

    data class ArtistHeader(
        override val key: String,
        val artistName: String,
        val artistImageUrl: String? = null
    ) : GenreDetailListItem()

    data class AlbumHeader(
        override val key: String,
        val album: AlbumData,
        val useArtistStyle: Boolean
    ) : GenreDetailListItem()

    data class SongItem(
        override val key: String,
        val song: Song,
        val isFirstInAlbum: Boolean,
        val isLastInAlbum: Boolean,
        val isLastAlbumInSection: Boolean,
        val useArtistStyle: Boolean
    ) : GenreDetailListItem()

    data class Spacer(
        override val key: String,
        val heightDp: Int,
        val useSurfaceBackground: Boolean = false
    ) : GenreDetailListItem()

    data class Divider(
        override val key: String
    ) : GenreDetailListItem()

    // --- Online Specific List Item Classes ---
    data class OnlineSectionHeader(
        override val key: String,
        val title: String
    ) : GenreDetailListItem()

    data class OnlinePlaylistsRow(
        override val key: String,
        val playlists: List<PlaylistItem>
    ) : GenreDetailListItem()

    data class OnlineAlbumsRow(
        override val key: String,
        val albums: List<AlbumItem>
    ) : GenreDetailListItem()

    data class OnlineArtistsRow(
        override val key: String,
        val artists: List<ArtistItem>
    ) : GenreDetailListItem()

    data class OnlineSongItem(
        override val key: String,
        val songItem: YtSongItem,
        val index: Int
    ) : GenreDetailListItem()
}

data class GenreDetailUiState(
    val genre: Genre? = null,
    val songs: List<Song> = emptyList(),
    val sortedSongs: List<Song> = emptyList(), // 为播放逻辑预留的已排序副本
    val displaySections: List<SectionData> = emptyList(),
    val flattenedItems: List<GenreDetailListItem> = emptyList(),
    val sortOption: SortOption = SortOption.ARTIST,
    val isLoadingGenreName: Boolean = false,
    val isLoadingSongs: Boolean = false,
    val error: String? = null,
    val isOnline: Boolean = false,
    val onlineTitle: String? = null,
    val onlineThumbnail: String? = null,
    val onlineSections: List<BrowseResult.Item> = emptyList(),
    val playEndpoint: WatchEndpoint? = null,
    val shuffleEndpoint: WatchEndpoint? = null,
    val radioEndpoint: WatchEndpoint? = null
)

@HiltViewModel
class GenreDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenreDetailUiState())
    val uiState: StateFlow<GenreDetailUiState> = _uiState.asStateFlow()

    private var artistMap: Map<String, String?> = emptyMap()

    init {
        savedStateHandle.get<String>("genreId")?.let { genreId ->
            val decodedGenreId = java.net.URLDecoder.decode(genreId, "UTF-8")
            loadGenreDetails(decodedGenreId)
        } ?: run {
            _uiState.value = _uiState.value.copy(error = "Genre ID not found", isLoadingGenreName = false, isLoadingSongs = false)
        }
    }

    private data class ProcessingResult(
        val genre: Genre,
        val songs: List<Song>,
        val sortedSongs: List<Song>,
        val sections: List<SectionData>,
        val flattened: List<GenreDetailListItem>
    )

    private fun loadGenreDetails(genreId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingGenreName = true, isLoadingSongs = true, error = null)

            try {
                val parts = genreId.split("?p=")
                val actualBrowseId = parts[0]
                val params = if (parts.size > 1) parts[1] else null

                if (actualBrowseId.startsWith("FEmusic_")) {
                    // Online Category Loading Mode (ArchiveTune dynamic browse)
                    val browseResult = withContext(Dispatchers.IO) {
                        YouTube.browse(actualBrowseId, params).getOrNull()
                    }

                    if (browseResult != null) {
                        val initialDisplayName = actualBrowseId
                            .replace("FEmusic_moods_and_genres_category_", "")
                            .replace("_", " ")
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                        val genreModel = Genre(
                            id = genreId,
                            name = browseResult.title ?: initialDisplayName,
                            lightColorHex = "#9E9E9E", onLightColorHex = "#000000",
                            darkColorHex = "#616161", onDarkColorHex = "#FFFFFF"
                        )

                        // Compile all songs inside sections to support shuffle / play flow
                        val onlineSongs = browseResult.items.flatMap { it.items }
                            .filterIsInstance<YtSongItem>()
                            .map { it.toNativeSong() }

                        val flattened = flattenOnlineSections(browseResult.items)

                        _uiState.value = _uiState.value.copy(
                            genre = genreModel,
                            isOnline = true,
                            onlineTitle = browseResult.title ?: initialDisplayName,
                            onlineThumbnail = browseResult.thumbnail,
                            onlineSections = browseResult.items,
                            playEndpoint = browseResult.playEndpoint,
                            shuffleEndpoint = browseResult.shuffleEndpoint,
                            radioEndpoint = browseResult.radioEndpoint,
                            flattenedItems = flattened,
                            songs = onlineSongs,
                            sortedSongs = onlineSongs,
                            isLoadingGenreName = false,
                            isLoadingSongs = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to load online genre categories",
                            isLoadingGenreName = false,
                            isLoadingSongs = false
                        )
                    }
                } else {
                    // Local / Offline Mode fallback (if any)
                    // Step 1: Fast load of the Genre object to stabilize the UI theme as early as possible.
                    // This prevents a major recomposition (theme switch) mid-animation.
                    val initialGenre = withContext(Dispatchers.Default) {
                        val genres = musicRepository.getGenres().first()
                        genres.find { it.id.equals(genreId, ignoreCase = true) }
                    }
                    
                    if (initialGenre != null) {
                        _uiState.value = _uiState.value.copy(genre = initialGenre, isLoadingGenreName = false)
                    }

                    // Step 2: Heavy data processing for songs and sections
                    val result = withContext(Dispatchers.Default) {
                        val genres = musicRepository.getGenres().first()
                        val genre = initialGenre ?: genres.find { it.id.equals(genreId, ignoreCase = true) }
                            ?: Genre(
                                id = genreId,
                                name = genreId.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, 
                                lightColorHex = "#9E9E9E", onLightColorHex = "#000000",
                                darkColorHex = "#616161", onDarkColorHex = "#FFFFFF"
                            )

                        val songs = musicRepository.getMusicByGenre(genre.name).first()
                        val artists = musicRepository.getArtists().first()
                        artistMap = artists.associate { it.name.trim().lowercase() to it.imageUrl }

                        val sections = buildDisplaySections(songs, SortOption.ARTIST)
                        val flattened = flattenSections(sections, artistMap)
                        val sorted = songs.sortedBy { it.artist ?: "Unknown Artist" }
                        
                        ProcessingResult(genre, songs, sorted, sections, flattened)
                    }

                    _uiState.value = _uiState.value.copy(
                        genre = result.genre,
                        songs = result.songs,
                        sortedSongs = result.sortedSongs,
                        displaySections = result.sections,
                        flattenedItems = result.flattened,
                        isOnline = false,
                        isLoadingGenreName = false,
                        isLoadingSongs = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load genre details: ${e.message}",
                    isLoadingGenreName = false,
                    isLoadingSongs = false
                )
            }
        }
    }

    private fun flattenOnlineSections(sections: List<BrowseResult.Item>): List<GenreDetailListItem> {
        val items = mutableListOf<GenreDetailListItem>()
        sections.forEachIndexed { sectionIndex, section ->
            val sectionTitle = section.title ?: ""
            val sectionItems = section.items
            if (sectionItems.isEmpty()) return@forEachIndexed

            // Renders the section header
            items.add(GenreDetailListItem.OnlineSectionHeader(
                key = "online_section_header_${sectionIndex}_${sectionTitle.hashCode()}",
                title = sectionTitle
            ))

            val songs = sectionItems.filterIsInstance<YtSongItem>()
            val albums = sectionItems.filterIsInstance<AlbumItem>()
            val playlists = sectionItems.filterIsInstance<PlaylistItem>()
            val artists = sectionItems.filterIsInstance<ArtistItem>()

            if (songs.isNotEmpty()) {
                songs.forEachIndexed { songIndex, songItem ->
                    items.add(GenreDetailListItem.OnlineSongItem(
                        key = "online_song_${sectionIndex}_${songItem.id}_$songIndex",
                        songItem = songItem,
                        index = songIndex
                    ))
                }
            }

            if (playlists.isNotEmpty()) {
                items.add(GenreDetailListItem.OnlinePlaylistsRow(
                    key = "online_playlists_${sectionIndex}_${playlists.hashCode()}",
                    playlists = playlists
                ))
            }

            if (albums.isNotEmpty()) {
                items.add(GenreDetailListItem.OnlineAlbumsRow(
                    key = "online_albums_${sectionIndex}_${albums.hashCode()}",
                    albums = albums
                ))
            }

            if (artists.isNotEmpty()) {
                items.add(GenreDetailListItem.OnlineArtistsRow(
                    key = "online_artists_${sectionIndex}_${artists.hashCode()}",
                    artists = artists
                ))
            }

            items.add(GenreDetailListItem.Spacer(
                key = "online_section_spacer_$sectionIndex",
                heightDp = 24
            ))
        }
        return items
    }

    /**
     * Updates the sort option and re-groups the songs.
     */
    fun updateSortOption(newSort: SortOption) {
        val currentState = _uiState.value
        if (currentState.sortOption == newSort) return

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoadingSongs = true)
            val (updatedSections, updatedFlattened, updatedSorted) = withContext(Dispatchers.Default) {
                val sections = buildDisplaySections(currentState.songs, newSort)
                val flattened = flattenSections(sections, artistMap)
                val sorted = when (newSort) {
                    SortOption.ARTIST -> currentState.songs.sortedBy { it.artist ?: "Unknown Artist" }
                    SortOption.ALBUM -> currentState.songs.sortedBy { it.album ?: "Unknown Album" }
                    SortOption.TITLE -> currentState.songs.sortedBy { it.title }
                }
                Triple(sections, flattened, sorted)
            }
            _uiState.value = currentState.copy(
                sortOption = newSort,
                displaySections = updatedSections,
                flattenedItems = updatedFlattened,
                sortedSongs = updatedSorted,
                isLoadingSongs = false
            )
        }
    }

    private fun flattenSections(sections: List<SectionData>, artistMap: Map<String, String?> = emptyMap()): List<GenreDetailListItem> {
        val items = mutableListOf<GenreDetailListItem>()
        sections.forEach { section ->
            when (section) {
                is SectionData.ArtistSection -> {
                    items.add(GenreDetailListItem.ArtistHeader(
                        key = "header_${section.id}", 
                        artistName = section.artistName,
                        artistImageUrl = artistMap[section.artistName.trim().lowercase()]
                    ))
                    section.albums.forEachIndexed { albumIndex, album ->
                        if (albumIndex > 0) {
                            items.add(GenreDetailListItem.Divider("divider_${section.id}_$albumIndex"))
                        }
                        
                        items.add(GenreDetailListItem.AlbumHeader("${section.id}_album_header_${album.name}", album, true))
                        items.add(GenreDetailListItem.Spacer("${section.id}_album_spacer_${album.name}", 10, true))
                        
                        album.songs.forEachIndexed { songIndex, song ->
                            items.add(GenreDetailListItem.SongItem(
                                key = "${section.id}_${album.name}_${song.id}",
                                song = song,
                                isFirstInAlbum = songIndex == 0,
                                isLastInAlbum = songIndex == album.songs.lastIndex,
                                isLastAlbumInSection = albumIndex == section.albums.lastIndex,
                                useArtistStyle = true
                            ))
                        }
                    }
                }
                is SectionData.AlbumSection -> {
                    val album = section.album
                    items.add(GenreDetailListItem.AlbumHeader("${section.id}_album_header_${album.name}", album, false))
                    items.add(GenreDetailListItem.Spacer("${section.id}_album_spacer_${album.name}", 10, true))
                    
                    album.songs.forEachIndexed { songIndex, song ->
                        items.add(GenreDetailListItem.SongItem(
                            key = "${section.id}_${song.id}",
                            song = song,
                            isFirstInAlbum = songIndex == 0,
                            isLastInAlbum = songIndex == album.songs.lastIndex,
                            isLastAlbumInSection = true,
                            useArtistStyle = false
                        ))
                    }
                }
                is SectionData.FlatList -> {
                    section.songs.forEach { song ->
                        items.add(GenreDetailListItem.SongItem(
                            key = "flat_${song.id}",
                            song = song,
                            isFirstInAlbum = false,
                            isLastInAlbum = false,
                            isLastAlbumInSection = false,
                            useArtistStyle = false
                        ))
                    }
                }
            }
            items.add(GenreDetailListItem.Spacer("section_spacer_${section.id}", 16))
        }
        return items
    }

    private fun buildDisplaySections(songs: List<Song>, sort: SortOption): List<SectionData> {
        return when (sort) {
            SortOption.ARTIST -> {
                val sorted = songs.sortedBy { it.artist ?: "Unknown Artist" }
                val grouped = sorted.groupBy { it.artist ?: "Unknown Artist" }
                grouped.map { (artist, artistSongs) ->
                    val albums = artistSongs.groupBy { it.album ?: "Unknown Album" }.map { (albumName, albumSongs) ->
                        val sortedAlbumSongs = albumSongs.sortedWith(
                            compareBy<Song> { it.discNumber ?: 1 }
                                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                .thenBy { it.title.lowercase() }
                        )
                        AlbumData(albumName, sortedAlbumSongs.firstOrNull()?.albumArtUriString, sortedAlbumSongs)
                    }
                    SectionData.ArtistSection("artist_$artist", artist, albums)
                }
            }
            SortOption.ALBUM -> {
                val sorted = songs.sortedBy { it.album ?: "Unknown Album" }
                val grouped = sorted.groupBy { it.album ?: "Unknown Album" }
                grouped.map { (album, albumSongs) ->
                    val sortedAlbumSongs = albumSongs.sortedWith(
                        compareBy<Song> { it.discNumber ?: 1 }
                            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                            .thenBy { it.title.lowercase() }
                    )
                    SectionData.AlbumSection(
                        "album_$album",
                        AlbumData(album, sortedAlbumSongs.firstOrNull()?.albumArtUriString, sortedAlbumSongs)
                    )
                }
            }
            SortOption.TITLE -> {
                listOf(SectionData.FlatList(songs.sortedBy { it.title }))
            }
        }
    }
}

