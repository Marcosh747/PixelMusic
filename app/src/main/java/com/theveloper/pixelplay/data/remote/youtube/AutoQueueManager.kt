package com.theveloper.pixelplay.data.remote.youtube

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.theveloper.pixelplay.data.remote.youtube.UmihiHelper.printe
import com.theveloper.pixelplay.data.remote.youtube.UmihiHelper.printd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/**
 * AutoQueueManager — Radio Mode
 *
 * Watches the player queue and automatically appends YouTube-suggested songs
 * when there are ≤2 songs remaining after the current one.
 *
 * Call [attach] from your playback service's onCreate() and [detach] from onDestroy().
 */
object AutoQueueManager {

    private const val TRIGGER_REMAINING = 2
    private const val MAX_HISTORY = 30

    private var fetchJob: Job? = null
    private var lastFetchedVideoId: String? = null
    private val addedVideoIds = mutableSetOf<String>()
    private var scope: CoroutineScope? = null
    private var datastoreRepository: DatastoreRepository? = null
    private val songRepository = SongRepository()
    private var playerRef: Player? = null
    private var musicRepository: com.theveloper.pixelplay.data.repository.MusicRepository? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            checkAndRefillQueue()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                checkAndRefillQueue()
            }
        }
    }

    fun attach(
        player: Player,
        datastoreRepo: DatastoreRepository,
        coroutineScope: CoroutineScope,
        musicRepo: com.theveloper.pixelplay.data.repository.MusicRepository
    ) {
        scope = coroutineScope
        datastoreRepository = datastoreRepo
        playerRef = player
        musicRepository = musicRepo
        player.addListener(playerListener)
        printd("AutoQueueManager attached")
    }

    fun detach(player: Player?) {
        player?.removeListener(playerListener)
        playerRef = null
        fetchJob?.cancel()
        scope = null
        datastoreRepository = null
        musicRepository = null
    }

    private fun checkAndRefillQueue() {
        val currentScope = scope ?: return
        val player = playerRef ?: return

        currentScope.launch(Dispatchers.IO) {
            val settings = datastoreRepository?.settings?.first() ?: return@launch
            if (!settings.autoQueueEnabled) return@launch

            val playerState = withContext(Dispatchers.Main) {
                if (playerRef == null) null
                else {
                    val currentIndex = player.currentMediaItemIndex
                    val totalCount = player.mediaItemCount
                    val remaining = totalCount - currentIndex - 1
                    val currentId = player.currentMediaItem?.mediaId
                    Triple(remaining, currentId, totalCount)
                }
            } ?: return@launch

            val (remaining, currentId, _) = playerState
            if (remaining > TRIGGER_REMAINING) return@launch
            if (currentId == null || currentId == lastFetchedVideoId) return@launch
            lastFetchedVideoId = currentId

            printd("AutoQueueManager: Only $remaining songs remaining. Fetching related for $currentId")
            fetchAndAppend(currentId, player)
        }
    }

    private suspend fun fetchAndAppend(videoId: String, player: Player) {
        try {
            val musicRepo = musicRepository
            val addedVideoIdsLocal = addedVideoIds

            val candidates = mutableListOf<MediaItem>()

            if (musicRepo != null) {
                // 1. Resolve current song
                val unifiedIdStr = videoId.toLongOrNull()?.toString() ?: (-(15000000000000L + videoId.hashCode().toLong().absoluteValue)).toString()
                val currentSong = musicRepo.getSong(unifiedIdStr).first()
                if (currentSong != null) {
                    val genre = currentSong.genre
                    val artistName = currentSong.artist

                    // 1. Same genre from local DB
                    if (!genre.isNullOrBlank() && genre != "YouTube" && genre != "YouTube Music") {
                        val genreSongs = musicRepo.getSongsByGenre(genre, excludeId = currentSong.id.toLongOrNull() ?: 0L, limit = 10)
                        val genreItems = genreSongs
                            .filter { it.youtubeId == null || it.youtubeId !in addedVideoIdsLocal }
                            .map { com.theveloper.pixelplay.utils.MediaItemBuilder.build(it) }
                        candidates.addAll(genreItems)
                    }

                    // 2. Same artist / similar artists from local DB
                    if (!artistName.isNullOrBlank()) {
                        val artistSongs = musicRepo.getSongsByArtistName(artistName, limit = 5)
                        val artistItems = artistSongs
                            .filter { it.youtubeId == null || it.youtubeId !in addedVideoIdsLocal }
                            .map { com.theveloper.pixelplay.utils.MediaItemBuilder.build(it) }
                        candidates.addAll(artistItems)
                    }
                }
            }

            // Shuffle and limit local DB candidates
            val selectedItems = candidates.distinctBy { it.mediaId }.shuffled().take(5)

            if (selectedItems.isNotEmpty()) {
                printd("AutoQueueManager: Found ${selectedItems.size} local DB candidates for genre/artist")
                withContext(Dispatchers.Main) {
                    player.addMediaItems(selectedItems)
                }
                selectedItems.forEach { item ->
                    addedVideoIds.add(item.mediaId)
                }

                // Trim history to avoid unbounded growth
                if (addedVideoIds.size > MAX_HISTORY) {
                    val excess = addedVideoIds.size - MAX_HISTORY
                    val toRemove = addedVideoIds.take(excess)
                    addedVideoIds.removeAll(toRemove.toSet())
                }
                printd("AutoQueueManager: Appended ${selectedItems.size} local DB songs to queue")
                return
            }

            // 3. Online YT related songs (fallback only if candidates are empty)
            printd("AutoQueueManager: Local candidates empty. Falling back to YouTube related API.")
            songRepository.getRelatedSongs(videoId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        val newSongs = result.data
                            .filter { it.youtubeId !in addedVideoIdsLocal }
                            .take(5)

                        if (newSongs.isEmpty()) {
                            printd("AutoQueueManager: No new related songs found from YouTube")
                            return@collect
                        }

                        val mediaItems = newSongs.map { it.mediaItem }

                        withContext(Dispatchers.Main) {
                            player.addMediaItems(mediaItems)
                        }

                        newSongs.forEach { addedVideoIds.add(it.youtubeId) }

                        // Trim history to avoid unbounded growth
                        if (addedVideoIds.size > MAX_HISTORY) {
                            val excess = addedVideoIds.size - MAX_HISTORY
                            val toRemove = addedVideoIds.take(excess)
                            addedVideoIds.removeAll(toRemove.toSet())
                        }

                        printd("AutoQueueManager: Appended ${newSongs.size} online related songs to queue")
                    }
                    is ApiResult.Error -> {
                        printe("AutoQueueManager: Failed to fetch online related songs: ${result.exception.message}")
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            printe("AutoQueueManager: Exception fetching related songs: ${e.message}")
        }
    }

    /** Call to reset when the user manually clears the queue */
    fun reset() {
        lastFetchedVideoId = null
        addedVideoIds.clear()
        fetchJob?.cancel()
    }
}
