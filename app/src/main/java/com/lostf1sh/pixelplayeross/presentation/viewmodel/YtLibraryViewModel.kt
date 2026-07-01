package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.YtShelfEntry
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import com.lostf1sh.pixelplayeross.data.youtube.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the Library tab's YTM side: the signed-in user's playlists, saved albums,
 * followed artists, and liked songs. All four load in parallel and fail independently —
 * one broken endpoint shouldn't blank the whole library. Signed-out state clears
 * everything; the screen shows a sign-in prompt instead.
 */
@HiltViewModel
class YtLibraryViewModel @Inject constructor(
    private val repository: YouTubeRepository,
) : ViewModel() {

    data class UiState(
        val isSignedIn: Boolean = false,
        val playlists: List<YtShelfEntry.Page> = emptyList(),
        val albums: List<YtShelfEntry.Page> = emptyList(),
        val artists: List<YtShelfEntry.Page> = emptyList(),
        val likedTracks: List<YtTrack> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.isSignedIn.collect { signedIn ->
                if (signedIn) {
                    _uiState.update { it.copy(isSignedIn = true) }
                    refresh()
                } else {
                    loadJob?.cancel()
                    _uiState.value = UiState(isSignedIn = false)
                }
            }
        }
    }

    fun refresh() {
        if (!_uiState.value.isSignedIn) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            supervisorScope {
                val playlists = async { runCatching { repository.libraryPlaylists() } }
                val albums = async { runCatching { repository.libraryAlbums() } }
                val artists = async { runCatching { repository.libraryArtists() } }
                val liked = async { runCatching { repository.likedSongs() } }

                val results = listOf(
                    playlists.await(), albums.await(), artists.await(), liked.await(),
                )
                results.mapNotNull { it.exceptionOrNull() }.forEach { e ->
                    if (e is CancellationException) throw e
                    Timber.tag(TAG).w(e, "library section load failed")
                }
                val allFailed = results.all { it.isFailure }

                _uiState.update {
                    it.copy(
                        playlists = playlists.await().getOrDefault(it.playlists),
                        albums = albums.await().getOrDefault(it.albums),
                        artists = artists.await().getOrDefault(it.artists),
                        likedTracks = liked.await().getOrDefault(it.likedTracks),
                        isLoading = false,
                        error = if (allFailed) "Couldn't load your library" else null,
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "YtLibraryViewModel"
    }
}
