package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.YtShelf
import com.lostf1sh.pixelplayeross.data.youtube.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the Home tab: the server-curated YTM home feed ("Listen again", "Quick picks",
 * mixes, …) with continuation paging. The feed reloads whenever sign-in state flips so it
 * switches between the anonymous and the personalized feed without an app restart.
 */
@HiltViewModel
class YtHomeViewModel @Inject constructor(
    private val repository: YouTubeRepository,
) : ViewModel() {

    data class UiState(
        val shelves: List<YtShelf> = emptyList(),
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
        val isSignedIn: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var continuation: String? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.isSignedIn.collect { signedIn ->
                _uiState.update { it.copy(isSignedIn = signedIn) }
                refresh()
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val page = repository.homeFeed()
                continuation = page.continuation
                _uiState.update { it.copy(shelves = page.shelves, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "home feed load failed")
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Couldn't load your feed")
                }
            }
        }
    }

    fun loadMore() {
        val token = continuation ?: return
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore) return
        continuation = null
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val page = repository.homeFeed(token)
                continuation = page.continuation
                _uiState.update { st ->
                    val known = st.shelves.mapTo(HashSet()) { it.id }
                    st.copy(
                        shelves = st.shelves + page.shelves.filter { it.id !in known },
                        isLoadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "home feed continuation failed")
                continuation = token
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private companion object {
        const val TAG = "YtHomeViewModel"
    }
}
