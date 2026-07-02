package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.YtShelfEntry
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import com.lostf1sh.pixelplayeross.data.youtube.YouTubeRepository
import com.lostf1sh.pixelplayeross.data.youtube.YtDownloadEntry
import com.lostf1sh.pixelplayeross.data.youtube.YtDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the per-track actions of the options sheet that need their own data/IO:
 * the "Add to playlist" pane (library playlists + the add / create-and-add writes)
 * and offline downloads.
 */
@HiltViewModel
class YtTrackActionsViewModel @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val downloadManager: YtDownloadManager,
) : ViewModel() {

    val isSignedIn: StateFlow<Boolean> get() = youTubeRepository.isSignedIn

    private val _playlists = MutableStateFlow<List<YtShelfEntry.Page>?>(null)

    /** Editable library playlists; null while loading. */
    val playlists: StateFlow<List<YtShelfEntry.Page>?> = _playlists.asStateFlow()

    fun loadPlaylists() {
        _playlists.value = null
        viewModelScope.launch {
            _playlists.value = runCatching { youTubeRepository.libraryPlaylists() }
                .getOrDefault(emptyList())
                // Liked Music (LM) and Episodes for later (SE) are system playlists
                // edit_playlist can't append to.
                .filter { it.browseId.removePrefix("VL") !in setOf("LM", "SE") }
        }
    }

    /** Add [videoId] to [playlistBrowseId]; reports success/failure via [onResult]. */
    fun addToPlaylist(playlistBrowseId: String, videoId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                youTubeRepository.addToPlaylist(playlistBrowseId, videoId)
            }.getOrDefault(false)
            onResult(ok)
        }
    }

    /** Create a new private playlist named [title] containing [videoId]. */
    fun createPlaylistWith(title: String, videoId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                youTubeRepository.createPlaylist(title, listOf(videoId)) != null
            }.getOrDefault(false)
            onResult(ok)
        }
    }

    // ─────────────────────────── Downloads ───────────────────────────

    val downloads: StateFlow<Map<String, YtDownloadEntry>> = downloadManager.downloads

    /** In-flight downloads: videoId → progress in 0..1. */
    val downloadProgress: StateFlow<Map<String, Float>> = downloadManager.inProgress

    fun download(track: YtTrack) = downloadManager.download(track)

    fun removeDownload(videoId: String) = downloadManager.delete(videoId)

    fun songOf(entry: YtDownloadEntry) = downloadManager.songFor(entry)

    fun trackOf(entry: YtDownloadEntry) = downloadManager.trackFor(entry)
}
