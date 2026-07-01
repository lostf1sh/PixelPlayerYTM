package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.YtShelf
import com.lostf1sh.pixelplayeross.data.model.YtShelfEntry
import com.lostf1sh.pixelplayeross.data.network.youtube.YtSearchFilter
import com.lostf1sh.pixelplayeross.data.youtube.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the Search tab: debounced YTM search with filter chips, plus the Explore
 * moods & genres grid shown while the query is blank.
 */
@HiltViewModel
class YtSearchViewModel @Inject constructor(
    private val repository: YouTubeRepository,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val filter: YtSearchFilter = YtSearchFilter.ALL,
        val results: List<YtShelfEntry> = emptyList(),
        val isSearching: Boolean = false,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
        val moodShelves: List<YtShelf> = emptyList(),
        val isLoadingMoods: Boolean = true,
        val moodsError: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** (query, filter) pairs; collectLatest + delay = restartable debounce. */
    private val searchInput = MutableStateFlow("" to YtSearchFilter.ALL)

    private var continuation: String? = null

    init {
        loadMoods()
        viewModelScope.launch {
            searchInput.collectLatest { (query, filter) ->
                if (query.isBlank()) {
                    continuation = null
                    _uiState.update {
                        it.copy(results = emptyList(), isSearching = false, error = null)
                    }
                    return@collectLatest
                }
                delay(DEBOUNCE_MS)
                _uiState.update { it.copy(isSearching = true, error = null) }
                try {
                    val page = repository.search(query, filter)
                    continuation = page.continuation
                    _uiState.update { it.copy(results = page.entries, isSearching = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "search failed for %s", query)
                    _uiState.update {
                        it.copy(isSearching = false, error = e.message ?: "Search failed")
                    }
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchInput.value = query to _uiState.value.filter
    }

    fun onFilterChange(filter: YtSearchFilter) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter) }
        searchInput.value = _uiState.value.query to filter
    }

    fun loadMore() {
        val token = continuation ?: return
        val state = _uiState.value
        if (state.isSearching || state.isLoadingMore) return
        continuation = null
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val page = repository.searchMore(token)
                continuation = page.continuation
                _uiState.update { st ->
                    val known = st.results.mapTo(HashSet()) { it.key }
                    st.copy(
                        results = st.results + page.entries.filter { it.key !in known },
                        isLoadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "search continuation failed")
                continuation = token
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun loadMoods() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoods = true, moodsError = null) }
            try {
                val shelves = repository.moodsAndGenres()
                _uiState.update { it.copy(moodShelves = shelves, isLoadingMoods = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "moods & genres load failed")
                _uiState.update {
                    it.copy(isLoadingMoods = false, moodsError = e.message ?: "Couldn't load Explore")
                }
            }
        }
    }

    private companion object {
        const val TAG = "YtSearchViewModel"
        const val DEBOUNCE_MS = 350L
    }
}
