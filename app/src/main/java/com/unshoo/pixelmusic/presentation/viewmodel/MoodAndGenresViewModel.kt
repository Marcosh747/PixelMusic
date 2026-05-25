package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.pages.MoodAndGenres
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.unshoo.pixelmusic.data.DailyMixManager
import com.unshoo.pixelmusic.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class MoodAndGenresViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val dailyMixManager: DailyMixManager
) : ViewModel() {

    private val _moodAndGenres = MutableStateFlow<List<MoodAndGenres.Item>?>(null)
    val moodAndGenres: StateFlow<List<MoodAndGenres.Item>?> = _moodAndGenres.asStateFlow()

    init {
        fetchMoodAndGenres()
    }

    private suspend fun sortMoodAndGenresByActivity(items: List<MoodAndGenres.Item>): List<MoodAndGenres.Item> {
        return try {
            val engagements = dailyMixManager.getAllEngagementStats()
            val allSongs = musicRepository.getAllSongsOnce()
            
            val genreWeights = mutableMapOf<String, Double>()
            allSongs.forEach { song ->
                val stats = engagements[song.id] ?: return@forEach
                val weight = stats.playCount.toDouble() + (stats.totalPlayDurationMs / 60000.0)
                if (weight > 0) {
                    song.genre?.lowercase()?.split(",")?.forEach { part ->
                        val cleanGenre = part.trim()
                        if (cleanGenre.isNotEmpty()) {
                            genreWeights.merge(cleanGenre, weight, Double::plus)
                        }
                    }
                }
            }
            
            if (genreWeights.isEmpty()) return items
            
            items.sortedByDescending { item ->
                val titleLower = item.title.lowercase()
                var score = 0.0
                genreWeights.forEach { (genre, weight) ->
                    if (titleLower.contains(genre) || genre.contains(titleLower)) {
                        score += weight
                    }
                }
                score
            }
        } catch (e: Exception) {
            Timber.e(e, "Error sorting mood and genres by activity")
            items
        }
    }

    private fun fetchMoodAndGenres() {
        viewModelScope.launch {
            YouTube.explore()
                .onSuccess { explorePage ->
                    viewModelScope.launch {
                        val sorted = withContext(Dispatchers.Default) {
                            sortMoodAndGenresByActivity(explorePage.moodAndGenres)
                        }
                        _moodAndGenres.value = sorted
                    }
                }
                .onFailure { error ->
                    Timber.w(error, "MoodAndGenresViewModel: Failed to fetch explore page")
                    // Fallback: try moodAndGenres endpoint directly
                    YouTube.moodAndGenres()
                        .onSuccess { items ->
                            viewModelScope.launch {
                                val flatItems = items.flatMap { it.items }
                                val sorted = withContext(Dispatchers.Default) {
                                    sortMoodAndGenresByActivity(flatItems)
                                }
                                _moodAndGenres.value = sorted
                            }
                        }
                        .onFailure { e ->
                            Timber.e(e, "MoodAndGenresViewModel: Fallback also failed")
                            _moodAndGenres.value = emptyList()
                        }
                }
        }
    }

    fun retry() {
        _moodAndGenres.value = null
        fetchMoodAndGenres()
    }
}
