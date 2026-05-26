package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube.SearchFilter
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import javax.inject.Inject

val QUICK_PICKS_CATEGORIES = listOf(
    "All", "Romance", "Love", "Pump", "Punjabi", "Bollywood",
    "Chill", "Party", "Sad", "Dance", "Hip Hop", "Pop", "Indie", "Rock"
)

@HiltViewModel
class QuickPicksViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _quickPicks = MutableStateFlow<List<Song>>(emptyList())
    val quickPicks: StateFlow<List<Song>> = _quickPicks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    init {
        loadQuickPicks("All")
    }

    fun setCategory(category: String) {
        if (_selectedCategory.value == category && !_isLoading.value) {
            return
        }
        _selectedCategory.value = category
        loadQuickPicks(category)
    }

    fun refresh() {
        loadQuickPicks(_selectedCategory.value)
    }

    private fun loadQuickPicks(category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _quickPicks.value = emptyList()
            try {
                val songs = withContext(Dispatchers.IO) {
                    fetchYoutubeSongs(category)
                }
                _quickPicks.value = songs
                Timber.tag("QuickPicks").d("Loaded ${songs.size} songs for category: $category")
            } catch (e: Exception) {
                Timber.tag("QuickPicks").e(e, "Error fetching quick picks for category: $category")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchYoutubeSongs(category: String): List<Song> {
        val songItems = mutableListOf<SongItem>()
        if (category == "All") {
            // Fetch from YouTube home sections
            val homeResult = YouTube.home().getOrNull()
            if (homeResult != null) {
                val songs = homeResult.sections
                    .flatMap { it.items }
                    .filterIsInstance<SongItem>()
                songItems.addAll(songs)
                
                var continuation = homeResult.continuation
                var attempts = 0
                while (songItems.distinctBy { it.id }.size < 50 && continuation != null && attempts < 5) {
                    val nextHome = YouTube.home(continuation = continuation).getOrNull()
                    if (nextHome != null) {
                        val nextSongs = nextHome.sections.flatMap { it.items }.filterIsInstance<SongItem>()
                        if (nextSongs.isEmpty()) break
                        songItems.addAll(nextSongs)
                        continuation = nextHome.continuation
                    } else {
                        break
                    }
                    attempts++
                }
            }

            val distinctSongs = songItems.distinctBy { it.id }
            if (distinctSongs.size >= 50) {
                return distinctSongs.take(50).map { it.toNativeSong() }
            }

            // Fallback / fill to 50: search "top songs"
            val searchResult = YouTube.search("top songs 2026", SearchFilter.FILTER_SONG).getOrNull()
            val fallbackSongs = searchResult?.items?.filterIsInstance<SongItem>().orEmpty()
            val combined = (distinctSongs + fallbackSongs).distinctBy { it.id }
            return combined.take(50).map { it.toNativeSong() }
        } else {
            // Category-specific YouTube search
            val query = when (category) {
                "Romance" -> "romantic songs hindi"
                "Love" -> "love songs hits"
                "Pump" -> "pump up gym workout music"
                "Punjabi" -> "punjabi songs latest hits"
                "Bollywood" -> "bollywood songs trending"
                "Chill" -> "chill lofi songs"
                "Party" -> "party songs hits"
                "Sad" -> "sad songs hindi"
                "Dance" -> "dance hits songs"
                "Hip Hop" -> "hip hop songs"
                "Pop" -> "pop songs hits"
                "Indie" -> "indie songs"
                "Rock" -> "rock songs hits"
                else -> "$category songs"
            }
            val searchResult = YouTube.search(query, SearchFilter.FILTER_SONG).getOrNull()
            searchResult?.items?.filterIsInstance<SongItem>()?.let { songItems.addAll(it) }
            
            var continuation = searchResult?.continuation
            var attempts = 0
            while (songItems.distinctBy { it.id }.size < 50 && continuation != null && attempts < 5) {
                val nextSearch = YouTube.searchContinuation(continuation).getOrNull()
                if (nextSearch != null) {
                    val nextSongs = nextSearch.items.filterIsInstance<SongItem>()
                    if (nextSongs.isEmpty()) break
                    songItems.addAll(nextSongs)
                    continuation = nextSearch.continuation
                } else {
                    break
                }
                attempts++
            }
            return songItems.distinctBy { it.id }.take(50).map { it.toNativeSong() }
        }
    }
}
