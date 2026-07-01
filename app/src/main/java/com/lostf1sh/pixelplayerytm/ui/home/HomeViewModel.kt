package com.lostf1sh.pixelplayerytm.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayerytm.data.repository.YouTubeRepository
import com.lostf1sh.pixelplayerytm.domain.model.Shelf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val shelves: List<Shelf> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val continuation: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YouTubeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = HomeUiState(isLoading = true)
        viewModelScope.launch {
            repository.home()
                .onSuccess { page ->
                    _state.value = HomeUiState(
                        shelves = page.shelves,
                        isLoading = false,
                        continuation = page.continuation,
                    )
                }
                .onFailure { e ->
                    _state.value = HomeUiState(isLoading = false, error = e.message)
                }
        }
    }

    fun loadMore() {
        val current = _state.value
        val token = current.continuation ?: return
        if (current.isLoadingMore) return
        _state.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            repository.homeContinuation(token)
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        shelves = _state.value.shelves + page.shelves,
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
