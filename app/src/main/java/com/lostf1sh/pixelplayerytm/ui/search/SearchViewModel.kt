package com.lostf1sh.pixelplayerytm.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayerytm.data.innertube.SearchFilter
import com.lostf1sh.pixelplayerytm.data.repository.YouTubeRepository
import com.lostf1sh.pixelplayerytm.domain.model.SearchSummaryPage
import com.lostf1sh.pixelplayerytm.domain.model.YtItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val filter: SearchFilter = SearchFilter.ALL,
    val summary: SearchSummaryPage? = null,
    val results: List<YtItem> = emptyList(),
    val continuation: String? = null,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: YouTubeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(250)
                .distinctUntilChanged()
                .collect { input ->
                    if (input.length >= 2 && !_state.value.hasSearched) {
                        repository.searchSuggestions(input).onSuccess { suggestions ->
                            _state.value = _state.value.copy(suggestions = suggestions.queries)
                        }
                    } else if (input.isEmpty()) {
                        _state.value = _state.value.copy(suggestions = emptyList())
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query, hasSearched = false)
        queryFlow.value = query
    }

    fun search(query: String = _state.value.query) {
        if (query.isBlank()) return
        searchJob?.cancel()
        _state.value = _state.value.copy(
            query = query,
            isSearching = true,
            hasSearched = true,
            suggestions = emptyList(),
            error = null,
        )
        searchJob = viewModelScope.launch {
            val filter = _state.value.filter
            if (filter == SearchFilter.ALL) {
                repository.searchSummary(query)
                    .onSuccess { summary ->
                        _state.value = _state.value.copy(
                            summary = summary,
                            results = emptyList(),
                            continuation = null,
                            isSearching = false,
                        )
                    }
                    .onFailure { e ->
                        _state.value = _state.value.copy(isSearching = false, error = e.message)
                    }
            } else {
                repository.search(query, filter)
                    .onSuccess { page ->
                        _state.value = _state.value.copy(
                            summary = null,
                            results = page.items,
                            continuation = page.continuation,
                            isSearching = false,
                        )
                    }
                    .onFailure { e ->
                        _state.value = _state.value.copy(isSearching = false, error = e.message)
                    }
            }
        }
    }

    fun setFilter(filter: SearchFilter) {
        if (_state.value.filter == filter) return
        _state.value = _state.value.copy(filter = filter)
        if (_state.value.hasSearched) search()
    }

    fun loadMore() {
        val current = _state.value
        val token = current.continuation ?: return
        if (current.isLoadingMore) return
        _state.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            repository.searchContinuation(token)
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        results = _state.value.results + page.items,
                        continuation = page.continuation,
                        isLoadingMore = false,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoadingMore = false, continuation = null)
                }
        }
    }
}
