package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ExplorePage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ChartsPage
import javax.inject.Inject

sealed interface ExploreUiState {
    object Loading : ExploreUiState
    data class Success(
        val homePage: HomePage?,
        val explorePage: ExplorePage?,
        val chartsPage: ChartsPage?
    ) : ExploreUiState
    data class Error(val message: String) : ExploreUiState
}

@HiltViewModel
class ExploreViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ExploreUiState>(ExploreUiState.Loading)
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = ExploreUiState.Loading
            try {
                val data = withContext(Dispatchers.IO) {
                    val home = YouTube.home().getOrNull()
                    val explore = YouTube.explore().getOrNull()
                    val charts = YouTube.getChartsPage().getOrNull()
                    Triple(home, explore, charts)
                }

                if (data.first == null && data.second == null && data.third == null) {
                    _uiState.value = ExploreUiState.Error("Failed to fetch explore data from YouTube Music. Please check your connection.")
                } else {
                    _uiState.value = ExploreUiState.Success(
                        homePage = data.first,
                        explorePage = data.second,
                        chartsPage = data.third
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading Explore screen data")
                _uiState.value = ExploreUiState.Error(e.localizedMessage ?: "Unknown error occurred")
            }
        }
    }
}
