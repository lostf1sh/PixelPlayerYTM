package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.YtShelfEntry
import com.lostf1sh.pixelplayeross.data.youtube.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "Add to playlist" pane of the track options sheet: the signed-in user's
 * playlists to pick from, plus the add / create-and-add write actions.
 */
@HiltViewModel
class YtPlaylistActionsViewModel @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
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
}
