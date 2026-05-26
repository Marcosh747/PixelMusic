package com.unshoo.pixelmusic.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.MoodAndGenresSection
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.navigation.navigateSafelyReplacing
import com.unshoo.pixelmusic.presentation.viewmodel.ExploreUiState
import com.unshoo.pixelmusic.presentation.viewmodel.ExploreViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    paddingValuesParent: PaddingValues,
    exploreViewModel: ExploreViewModel = hiltViewModel()
) {
    val uiState by exploreViewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isPlaying by remember(stablePlayerState) { mutableStateOf(stablePlayerState.isPlaying) }
    val currentSongId = stablePlayerState.currentSong?.id

    var selectedTab by remember { mutableStateOf("All") }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullToRefreshState()

    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundBrush = remember(surfaceColor, primaryColor) {
        Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.15f),
                surfaceColor.copy(alpha = 0.6f),
                surfaceColor
            ),
            endY = 1000f
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ExploreTopBar(
                onSettingsClick = {
                    navController.navigateSafely(Screen.Settings.route)
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                exploreViewModel.loadData()
                scope.launch {
                    delay(1500)
                    isRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
            ) {
                when (val state = uiState) {
                    is ExploreUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is ExploreUiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = { exploreViewModel.loadData() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Retry")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    }
                    is ExploreUiState.Success -> {
                        val bottomPadding = if (currentSongId != null) MiniPlayerHeight else 0.dp
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = innerPadding.calculateTopPadding(),
                                bottom = paddingValuesParent.calculateBottomPadding() + 24.dp + bottomPadding
                            ),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Category Filter Chips
                            item(key = "explore_filters") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val categories = listOf("All", "New Releases", "Charts", "Moods & Genres")
                                    categories.forEach { category ->
                                        FilterChip(
                                            selected = selectedTab == category,
                                            onClick = { selectedTab = category },
                                            label = { Text(category) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            shape = RoundedCornerShape(16.dp),
                                            border = null
                                        )
                                    }
                                }
                            }

                            // 1) New Releases Section
                            if ((selectedTab == "All" || selectedTab == "New Releases") &&
                                state.explorePage != null && state.explorePage.newReleaseAlbums.isNotEmpty()
                            ) {
                                item(key = "new_releases_header") {
                                    SectionHeader(title = "New Releases")
                                }
                                item(key = "new_releases_carousel") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(state.explorePage.newReleaseAlbums) { album ->
                                            AlbumCarouselItem(
                                                album = album,
                                                onClick = {
                                                    navController.navigateSafelyReplacing(
                                                        route = Screen.AlbumDetail.createRoute(album.browseId),
                                                        patternToPop = Screen.AlbumDetail.route
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // 2) Charts Sections
                            if ((selectedTab == "All" || selectedTab == "Charts") &&
                                state.chartsPage != null && state.chartsPage.sections.isNotEmpty()
                            ) {
                                state.chartsPage.sections.forEach { chartSection ->
                                    item(key = "chart_${chartSection.title}_header") {
                                        SectionHeader(title = chartSection.title)
                                    }

                                    val songItems = chartSection.items.filterIsInstance<SongItem>()
                                    if (songItems.isNotEmpty()) {
                                        val songListNative = songItems.map { it.toNativeSong() }
                                        items(songItems.size) { index ->
                                            val songItem = songItems[index]
                                            val songNative = songListNative[index]
                                            EnhancedSongListItem(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                song = songNative,
                                                isPlaying = isPlaying && currentSongId == songNative.id,
                                                isCurrentSong = currentSongId == songNative.id,
                                                onClick = {
                                                    playerViewModel.showAndPlaySong(
                                                        songNative,
                                                        songListNative,
                                                        chartSection.title
                                                    )
                                                },
                                                onMoreOptionsClick = {
                                                    playerViewModel.selectSongForInfo(songNative)
                                                    // In a real app we might open the info sheet, but we have showAndPlaySong wired.
                                                }
                                            )
                                        }
                                    } else {
                                        // Fallback rendering for generic non-song chart items (like artists or playlists)
                                        item(key = "chart_${chartSection.title}_list") {
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                items(chartSection.items) { item ->
                                                    when (item) {
                                                        is AlbumItem -> {
                                                            AlbumCarouselItem(
                                                                album = item,
                                                                onClick = {
                                                                    navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                                                                }
                                                            )
                                                        }
                                                        is ArtistItem -> {
                                                            ArtistCardItem(
                                                                artist = item,
                                                                onClick = {
                                                                    navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                                                                }
                                                            )
                                                        }
                                                        is PlaylistItem -> {
                                                            PlaylistCardItem(
                                                                playlist = item,
                                                                onClick = {
                                                                    navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                                                                }
                                                            )
                                                        }
                                                        else -> {}
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3) Moods & Genres Section
                            if ((selectedTab == "All" || selectedTab == "Moods & Genres") &&
                                state.explorePage != null && state.explorePage.moodAndGenres.isNotEmpty()
                            ) {
                                item(key = "mood_genres_header") {
                                    SectionHeader(title = "Moods & Genres")
                                }
                                item(key = "mood_genres_grid") {
                                    MoodAndGenresSection(
                                        items = state.explorePage.moodAndGenres,
                                        onItemClick = { moodItem ->
                                            val browseId = moodItem.endpoint.browseId
                                            val params = moodItem.endpoint.params
                                            val route = Screen.GenreDetail.createRoute(
                                                java.net.URLEncoder.encode(
                                                    browseId + (if (params != null) "?p=$params" else ""),
                                                    "UTF-8"
                                                )
                                            )
                                            navController.navigateSafely(route)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreTopBar(
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Explore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "Explore",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansRounded
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        FilledIconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_settings_24),
                contentDescription = stringResource(R.string.settings_top_bar_title),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontFamily = GoogleSansRounded
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
fun AlbumCarouselItem(
    album: AlbumItem,
    onClick: () -> Unit
) {
    val shape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 24.dp,
            cornerRadiusTR = 24.dp,
            cornerRadiusBR = 24.dp,
            cornerRadiusBL = 24.dp,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentTR = 60,
            smoothnessAsPercentBR = 60,
            smoothnessAsPercentBL = 60
        )
    }

    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            SmartImage(
                model = album.thumbnail,
                contentDescription = album.title,
                modifier = Modifier
                    .size(140.dp)
                    .clip(shape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = album.artists?.joinToString { it.name } ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ArtistCardItem(
    artist: ArtistItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SmartImage(
                model = artist.thumbnail,
                contentDescription = artist.title,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = artist.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun PlaylistCardItem(
    playlist: PlaylistItem,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            SmartImage(
                model = playlist.thumbnail,
                contentDescription = playlist.title,
                modifier = Modifier
                    .size(140.dp)
                    .clip(shape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = playlist.author?.name ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
