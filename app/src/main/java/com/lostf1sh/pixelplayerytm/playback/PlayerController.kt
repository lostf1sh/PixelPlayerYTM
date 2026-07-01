package com.lostf1sh.pixelplayerytm.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lostf1sh.pixelplayerytm.data.repository.YouTubeRepository
import com.lostf1sh.pixelplayerytm.domain.model.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-side handle to the playback session: owns the [MediaController], exposes
 * player state as flows for Compose, and implements queue semantics —
 * play-song-with-radio, endless radio queue extension, shuffle-play, etc.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: YouTubeRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _currentSong = MutableStateFlow<SongItem?>(null)
    val currentSong: StateFlow<SongItem?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _queue = MutableStateFlow<List<SongItem>>(emptyList())
    val queue: StateFlow<List<SongItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _positionMillis = MutableStateFlow(0L)
    val positionMillis: StateFlow<Long> = _positionMillis.asStateFlow()

    private val _durationMillis = MutableStateFlow(0L)
    val durationMillis: StateFlow<Long> = _durationMillis.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    /** Continuation token for extending an active radio queue. */
    private var radioContinuation: String? = null
    private var radioPlaylistId: String? = null
    private var extendingQueue = false

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync().also { future ->
            future.addListener({
                runCatching { future.get() }.getOrNull()?.let { onControllerReady(it) }
            }, MoreExecutors.directExecutor())
        }
    }

    private fun onControllerReady(mediaController: MediaController) {
        controller = mediaController
        mediaController.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentSong.value = mediaItem?.toSongItem()
                _currentIndex.value = mediaController.currentMediaItemIndex
                maybeExtendRadioQueue()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    _durationMillis.value = mediaController.duration.coerceAtLeast(0)
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                syncQueue()
            }
        })
        syncQueue()
        _currentSong.value = mediaController.currentMediaItem?.toSongItem()
        _isPlaying.value = mediaController.isPlaying

        scope.launch {
            while (isActive) {
                controller?.let {
                    _positionMillis.value = it.currentPosition.coerceAtLeast(0)
                    if (it.duration > 0) _durationMillis.value = it.duration
                }
                delay(500)
            }
        }
    }

    private fun syncQueue() {
        val c = controller ?: return
        _queue.value = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSongItem() }
        _currentIndex.value = c.currentMediaItemIndex
    }

    // ---------- Commands ----------

    /** Plays [songs] starting at [startIndex], replacing the queue. */
    fun playQueue(songs: List<SongItem>, startIndex: Int = 0) {
        val c = controller ?: return
        clearRadio()
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0)
        c.prepare()
        c.play()
    }

    /** Plays a single song and builds a radio queue behind it. */
    fun playSongWithRadio(song: SongItem) {
        val c = controller ?: return
        clearRadio()
        c.setMediaItems(listOf(song.toMediaItem()), 0, 0)
        c.prepare()
        c.play()
        scope.launch {
            repository.radio(song.videoId).onSuccess { page ->
                radioContinuation = page.continuation
                radioPlaylistId = page.radioPlaylistId ?: "RDAMVM${song.videoId}"
                val upNext = page.items.filter { it.videoId != song.videoId }
                if (upNext.isNotEmpty()) {
                    controller?.addMediaItems(upNext.map { it.toMediaItem() })
                }
            }
        }
    }

    /** Starts radio for a playlist/song id via the next endpoint. */
    fun playRadio(playlistId: String, params: String? = null) {
        val c = controller ?: return
        clearRadio()
        scope.launch {
            repository.next(playlistId = playlistId, params = params).onSuccess { page ->
                if (page.items.isEmpty()) return@onSuccess
                radioContinuation = page.continuation
                radioPlaylistId = playlistId
                c.setMediaItems(page.items.map { it.toMediaItem() }, page.currentIndex ?: 0, 0)
                c.prepare()
                c.play()
            }
        }
    }

    fun addToQueue(song: SongItem) {
        controller?.addMediaItem(song.toMediaItem())
    }

    fun playNext(song: SongItem) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playSongWithRadio(song)
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun seekTo(positionMillis: Long) {
        controller?.seekTo(positionMillis)
        _positionMillis.value = positionMillis
    }

    fun skipToNext() = controller?.seekToNext() ?: Unit

    fun skipToPrevious() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPrevious()
    }

    fun skipToIndex(index: Int) {
        controller?.seekToDefaultPosition(index)
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun setRepeatMode(mode: Int) {
        controller?.repeatMode = mode
    }

    // ---------- Radio queue extension ----------

    private fun clearRadio() {
        radioContinuation = null
        radioPlaylistId = null
    }

    private fun maybeExtendRadioQueue() {
        val c = controller ?: return
        val continuation = radioContinuation ?: return
        if (extendingQueue) return
        if (c.currentMediaItemIndex < c.mediaItemCount - 3) return

        extendingQueue = true
        scope.launch {
            repository.next(
                playlistId = radioPlaylistId,
                continuation = continuation,
            ).onSuccess { page ->
                radioContinuation = page.continuation
                val existing = (0 until (controller?.mediaItemCount ?: 0))
                    .map { controller?.getMediaItemAt(it)?.mediaId }
                    .toSet()
                val fresh = page.items.filter { it.videoId !in existing }
                if (fresh.isNotEmpty()) {
                    controller?.addMediaItems(fresh.map { it.toMediaItem() })
                }
            }
            extendingQueue = false
        }
    }
}
