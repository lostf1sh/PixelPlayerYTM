package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.YtBrowsePage
import com.lostf1sh.pixelplayeross.data.model.YtPageKind
import com.lostf1sh.pixelplayeross.data.youtube.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Loads one YTM detail page — album, playlist, artist, or podcast — for the generic
 * [com.lostf1sh.pixelplayeross.presentation.screens.YtPageScreen]. The browseId and kind
 * arrive as navigation arguments.
 */
@HiltViewModel
class YtPageViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val page: YtBrowsePage? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val browseId: String = checkNotNull(savedStateHandle.get<String>("browseId"))

    val kind: YtPageKind = savedStateHandle.get<String>("kind")
        ?.let { name -> YtPageKind.entries.firstOrNull { it.name == name } }
        ?: YtPageKind.PLAYLIST

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Gates the subscribe button — anonymous sessions can't subscribe. */
    val isSignedIn: kotlinx.coroutines.flow.StateFlow<Boolean> get() = repository.isSignedIn

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val page = if (kind == YtPageKind.PLAYLIST) {
                    repository.playlistPage(browseId)
                } else {
                    repository.page(browseId)
                }
                _uiState.update { it.copy(page = page, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "browse page load failed for %s", browseId)
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Couldn't load this page")
                }
            }
        }
    }

    /** Optimistic subscribe/unsubscribe for artist pages; reverts if the call fails. */
    fun toggleSubscription() {
        val page = _uiState.value.page ?: return
        val channelId = page.channelId ?: return
        val target = !page.subscribed
        _uiState.update { it.copy(page = page.copy(subscribed = target)) }
        viewModelScope.launch {
            runCatching { repository.setArtistSubscribed(channelId, target) }
                .onFailure { e ->
                    Timber.tag(TAG).w(e, "subscribe toggle failed for %s", channelId)
                    _uiState.update { s -> s.copy(page = s.page?.copy(subscribed = !target)) }
                }
        }
    }

    private companion object {
        const val TAG = "YtPageViewModel"
    }
}
