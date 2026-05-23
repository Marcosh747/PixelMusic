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

@HiltViewModel
class MoodAndGenresViewModel @Inject constructor() : ViewModel() {

    private val _moodAndGenres = MutableStateFlow<List<MoodAndGenres.Item>?>(null)
    val moodAndGenres: StateFlow<List<MoodAndGenres.Item>?> = _moodAndGenres.asStateFlow()

    init {
        fetchMoodAndGenres()
    }

    private fun fetchMoodAndGenres() {
        viewModelScope.launch {
            YouTube.explore()
                .onSuccess { explorePage ->
                    _moodAndGenres.value = explorePage.moodAndGenres
                }
                .onFailure { error ->
                    Timber.w(error, "MoodAndGenresViewModel: Failed to fetch explore page")
                    // Fallback: try moodAndGenres endpoint directly
                    YouTube.moodAndGenres()
                        .onSuccess { items ->
                            _moodAndGenres.value = items.flatMap { it.items }
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
