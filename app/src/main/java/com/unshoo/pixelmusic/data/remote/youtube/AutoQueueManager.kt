package com.unshoo.pixelmusic.data.remote.youtube

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.unshoo.pixelmusic.data.remote.youtube.UmihiHelper.printe
import com.unshoo.pixelmusic.data.remote.youtube.UmihiHelper.printd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.RelatedSongMap
import com.unshoo.pixelmusic.data.database.SongEntity
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.data.database.ArtistEntity
import com.unshoo.pixelmusic.data.database.SongArtistCrossRef
import com.unshoo.pixelmusic.data.database.SourceType
import com.unshoo.pixelmusic.data.model.ArtistRef
import com.unshoo.pixelmusic.utils.MediaItemBuilder

/**
 * AutoQueueManager — Radio Mode
 *
 * Watches the player queue and automatically appends YouTube-suggested songs
 * using YouTube.next() when there are ≤2 songs remaining.
 */
object AutoQueueManager {

    private const val TRIGGER_REMAINING = 2
    private const val MAX_HISTORY = 50

    private var fetchJob: Job? = null
    private var lastFetchedVideoId: String? = null
    private var continuationToken: String? = null
    private var currentWatchEndpoint: WatchEndpoint? = null
    private val addedVideoIds = mutableSetOf<String>()
    
    private var scope: CoroutineScope? = null
    private var datastoreRepository: DatastoreRepository? = null
    private var playerRef: Player? = null
    private var musicDaoRef: MusicDao? = null

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
        musicDao: MusicDao
    ) {
        scope = coroutineScope
        datastoreRepository = datastoreRepo
        playerRef = player
        musicDaoRef = musicDao
        player.addListener(playerListener)
        printd("AutoQueueManager attached")
    }

    fun detach(player: Player?) {
        player?.removeListener(playerListener)
        playerRef = null
        fetchJob?.cancel()
        scope = null
        datastoreRepository = null
        musicDaoRef = null
    }

    fun reset() {
        lastFetchedVideoId = null
        continuationToken = null
        currentWatchEndpoint = null
        addedVideoIds.clear()
        fetchJob?.cancel()
    }

    private fun checkAndRefillQueue() {
        forceRefill(forceRefresh = false)
    }

    fun forceRefill(forceRefresh: Boolean) {
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
            if (currentId == null) return@launch
            
            // Clean/remove prefix if present
            val rawVideoId = if (currentId.startsWith("youtube_")) currentId.substringAfter("youtube_") else currentId
            
            if (forceRefresh) {
                lastFetchedVideoId = rawVideoId
                continuationToken = null
                currentWatchEndpoint = null
                addedVideoIds.clear()
                addedVideoIds.add(rawVideoId)
            } else {
                if (remaining > TRIGGER_REMAINING) return@launch
                
                // If the active track has changed, reset continuation to start a new radio session
                if (rawVideoId != lastFetchedVideoId) {
                    lastFetchedVideoId = rawVideoId
                    continuationToken = null
                    currentWatchEndpoint = null
                    addedVideoIds.clear()
                    addedVideoIds.add(rawVideoId)
                }
            }

            printd("AutoQueueManager: Fetching related for $rawVideoId (forceRefresh=$forceRefresh)")
            
            if (fetchJob?.isActive == true) return@launch
            
            fetchJob = launch(Dispatchers.IO) {
                fetchAndAppend(rawVideoId, player)
            }
        }
    }

    private suspend fun fetchAndAppend(videoId: String, player: Player) {
        try {
            val endpoint = currentWatchEndpoint ?: WatchEndpoint(videoId = videoId)
            val result = YouTube.next(endpoint = endpoint, continuation = continuationToken, followAutomixPreview = true)
            
            result.onSuccess { nextResult ->
                continuationToken = nextResult.continuation
                currentWatchEndpoint = nextResult.endpoint
                
                val addedVideoIdsLocal = addedVideoIds
                val filteredItems = nextResult.items
                    .filter { it.id !in addedVideoIdsLocal }
                    .take(5)

                if (filteredItems.isEmpty()) {
                    printd("AutoQueueManager: No new related songs found from YouTube")
                    return
                }

                // Add to history
                filteredItems.forEach { addedVideoIds.add(it.id) }
                if (addedVideoIds.size > MAX_HISTORY) {
                    val excess = addedVideoIds.size - MAX_HISTORY
                    val toRemove = addedVideoIds.take(excess)
                    addedVideoIds.removeAll(toRemove.toSet())
                }

                // Map to native Songs and MediaItems
                val nativeSongs = filteredItems.map { it.toNativeSong() }
                val mediaItems = nativeSongs.map { MediaItemBuilder.build(it) }

                // Insert into the local database to preserve related maps and enable Quick Picks
                saveRelatedSongsToDb(videoId, nativeSongs, player)

                withContext(Dispatchers.Main) {
                    player.addMediaItems(mediaItems)
                }
                
                printd("AutoQueueManager: Appended ${mediaItems.size} online related songs to queue")
            }.onFailure { e ->
                printe("AutoQueueManager: Failed to fetch related: ${e.message}")
            }
        } catch (e: Exception) {
            printe("AutoQueueManager: Exception fetching related songs: ${e.message}")
        }
    }

    private suspend fun saveRelatedSongsToDb(sourceVideoId: String, relatedSongs: List<Song>, player: Player) {
        val dao = musicDaoRef ?: return

        try {
            val sourceLongId = getDatabaseIdForYoutubeId(sourceVideoId)
            
            val songEntities = mutableListOf<SongEntity>()
            val albumEntities = mutableListOf<AlbumEntity>()
            val artistEntities = mutableListOf<ArtistEntity>()
            val crossRefs = mutableListOf<SongArtistCrossRef>()
            val relatedMaps = mutableListOf<RelatedSongMap>()

            withContext(Dispatchers.IO) {
                // Check if source song exists in DB, if not, insert it first!
                val exists = dao.getSongByIdOnce(sourceLongId) != null
                if (!exists) {
                    // Get source song details from player currentMediaItem
                    val currentMediaItem = withContext(Dispatchers.Main) {
                        player.currentMediaItem
                    }
                    if (currentMediaItem != null) {
                        val title = currentMediaItem.mediaMetadata.title?.toString() ?: ""
                        val artist = currentMediaItem.mediaMetadata.artist?.toString() ?: ""
                        val artistLongId = artist.hashCode().toLong()
                        val album = currentMediaItem.mediaMetadata.albumTitle?.toString() ?: "YouTube Music"
                        val albumLongId = album.hashCode().toLong()
                        
                        val sourceArtist = ArtistEntity(id = artistLongId, name = artist, trackCount = 1, imageUrl = null)
                        val sourceAlbum = AlbumEntity(
                            id = albumLongId,
                            title = album,
                            artistName = artist,
                            artistId = artistLongId,
                            albumArtUriString = currentMediaItem.mediaMetadata.artworkUri?.toString(),
                            songCount = 1,
                            dateAdded = System.currentTimeMillis(),
                            year = 0,
                            albumArtist = artist
                        )
                        val sourceSong = SongEntity(
                            id = sourceLongId,
                            title = title,
                            artistName = artist,
                            artistId = artistLongId,
                            albumArtist = artist,
                            albumName = album,
                            albumId = albumLongId,
                            contentUriString = "youtube://$sourceVideoId",
                            albumArtUriString = currentMediaItem.mediaMetadata.artworkUri?.toString(),
                            duration = player.duration.coerceAtLeast(0L),
                            genre = "YouTube",
                            filePath = "",
                            parentDirectoryPath = "/Cloud/YouTube",
                            isFavorite = false,
                            lyrics = null,
                            trackNumber = 0,
                            discNumber = null,
                            year = 0,
                            dateAdded = System.currentTimeMillis(),
                            mimeType = "audio/mpeg",
                            bitrate = 128,
                            sampleRate = 44100,
                            sourceType = SourceType.YOUTUBE
                        )
                        val sourceCrossRef = SongArtistCrossRef(songId = sourceLongId, artistId = artistLongId, isPrimary = true)
                        
                        dao.insertArtists(listOf(sourceArtist))
                        dao.insertAlbums(listOf(sourceAlbum))
                        dao.insertSongs(listOf(sourceSong))
                        dao.insertSongArtistCrossRefs(listOf(sourceCrossRef))
                    }
                }

                relatedSongs.forEach { song ->
                    val songLongId = song.id.substringAfter("youtube_").let(::getDatabaseIdForYoutubeId)
                    val artistLongId = song.artistId
                    val albumLongId = song.albumId

                    artistEntities.add(
                        ArtistEntity(
                            id = artistLongId,
                            name = song.artist,
                            trackCount = 1,
                            imageUrl = null
                        )
                    )

                    albumEntities.add(
                        AlbumEntity(
                            id = albumLongId,
                            title = song.album,
                            artistName = song.artist,
                            artistId = artistLongId,
                            albumArtUriString = song.albumArtUriString,
                            songCount = 1,
                            dateAdded = System.currentTimeMillis(),
                            year = 0,
                            albumArtist = song.artist
                        )
                    )

                    songEntities.add(
                        SongEntity(
                            id = songLongId,
                            title = song.title,
                            artistName = song.artist,
                            artistId = artistLongId,
                            albumArtist = song.artist,
                            albumName = song.album,
                            albumId = albumLongId,
                            contentUriString = song.contentUriString,
                            albumArtUriString = song.albumArtUriString,
                            duration = song.duration,
                            genre = song.genre,
                            filePath = "",
                            parentDirectoryPath = "/Cloud/YouTube",
                            isFavorite = false,
                            lyrics = null,
                            trackNumber = 0,
                            discNumber = null,
                            year = 0,
                            dateAdded = System.currentTimeMillis(),
                            mimeType = "audio/mpeg",
                            bitrate = 128,
                            sampleRate = 44100,
                            sourceType = SourceType.YOUTUBE
                        )
                    )

                    crossRefs.add(
                        SongArtistCrossRef(
                            songId = songLongId,
                            artistId = artistLongId,
                            isPrimary = true
                        )
                    )

                    relatedMaps.add(
                        RelatedSongMap(
                            songId = sourceLongId,
                            relatedSongId = songLongId
                        )
                    )
                }

                dao.insertArtists(artistEntities)
                dao.insertAlbums(albumEntities)
                dao.insertSongs(songEntities)
                dao.insertSongArtistCrossRefs(crossRefs)
                dao.insertRelatedSongMaps(relatedMaps)
            }
        } catch (e: Exception) {
            printe("AutoQueueManager: Error saving related songs to DB: ${e.message}")
        }
    }

    private fun getDatabaseIdForYoutubeId(youtubeId: String): Long {
        val YOUTUBE_SONG_ID_OFFSET = 15_000_000_000_000L
        return -(YOUTUBE_SONG_ID_OFFSET + youtubeId.hashCode().toLong().absoluteValue)
    }
}
