package com.lostf1sh.pixelplayerytm.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayerytm.data.repository.YouTubeRepository
import com.lostf1sh.pixelplayerytm.domain.model.AlbumPage
import com.lostf1sh.pixelplayerytm.domain.model.ArtistPage
import com.lostf1sh.pixelplayerytm.domain.model.PlaylistPage
import com.lostf1sh.pixelplayerytm.domain.model.Shelf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState<T>(
    val page: T? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: YouTubeRepository,
) : ViewModel() {
    private val browseId: String = checkNotNull(savedStateHandle["browseId"])

    private val _state = MutableStateFlow(DetailUiState<AlbumPage>())
    val state: StateFlow<DetailUiState<AlbumPage>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = DetailUiState(isLoading = true)
        viewModelScope.launch {
            repository.album(browseId)
                .onSuccess { _state.value = DetailUiState(page = it, isLoading = false) }
                .onFailure { _state.value = DetailUiState(isLoading = false, error = it.message) }
        }
    }
}

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: YouTubeRepository,
) : ViewModel() {
    private val browseId: String = checkNotNull(savedStateHandle["browseId"])

    private val _state = MutableStateFlow(DetailUiState<ArtistPage>())
    val state: StateFlow<DetailUiState<ArtistPage>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = DetailUiState(isLoading = true)
        viewModelScope.launch {
            repository.artist(browseId)
                .onSuccess { _state.value = DetailUiState(page = it, isLoading = false) }
                .onFailure { _state.value = DetailUiState(isLoading = false, error = it.message) }
        }
    }
}

data class PlaylistUiState(
    val page: PlaylistPage? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: YouTubeRepository,
) : ViewModel() {
    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])

    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = PlaylistUiState(isLoading = true)
        viewModelScope.launch {
            repository.playlist(playlistId)
                .onSuccess { _state.value = PlaylistUiState(page = it, isLoading = false) }
                .onFailure { _state.value = PlaylistUiState(isLoading = false, error = it.message) }
        }
    }

    fun loadMore() {
        val current = _state.value
        val page = current.page ?: return
        val token = page.continuation ?: return
        if (current.isLoadingMore) return
        _state.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            repository.playlistContinuation(token)
                .onSuccess { (songs, continuation) ->
                    _state.value = _state.value.copy(
                        page = _state.value.page?.let {
                            it.copy(songs = it.songs + songs, continuation = continuation)
                        },
                        isLoadingMore = false,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        page = _state.value.page?.copy(continuation = null),
                        isLoadingMore = false,
                    )
                }
        }
    }
}

data class BrowseUiState(
    val title: String = "",
    val shelves: List<Shelf> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** Generic shelf page: mood/genre categories, "More" targets, charts. */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: YouTubeRepository,
) : ViewModel() {
    private val browseId: String = checkNotNull(savedStateHandle["browseId"])
    private val params: String? =
        savedStateHandle.get<String>("params")?.takeIf { it.isNotEmpty() }
    val titleArg: String =
        savedStateHandle.get<String>("title")?.takeIf { it.isNotEmpty() }.orEmpty()

    private val _state = MutableStateFlow(BrowseUiState(title = titleArg))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = BrowseUiState(title = titleArg, isLoading = true)
        viewModelScope.launch {
            repository.browsePage(browseId, params)
                .onSuccess { page ->
                    _state.value = BrowseUiState(
                        title = titleArg,
                        shelves = page.shelves,
                        isLoading = false,
                    )
                }
                .onFailure {
                    _state.value = BrowseUiState(
                        title = titleArg,
                        isLoading = false,
                        error = it.message,
                    )
                }
        }
    }
}
