package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.YtShelf
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

/** Loads the shelf feed behind one Explore mood/genre chip (e.g. "Chill", "Rock"). */
@HiltViewModel
class YtMoodViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val shelves: List<YtShelf> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val browseId: String = checkNotNull(savedStateHandle.get<String>("browseId"))
    private val params: String? = savedStateHandle.get<String>("params")?.takeIf { it.isNotBlank() }

    val title: String = savedStateHandle.get<String>("title")?.takeIf { it.isNotBlank() } ?: "Explore"

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val page = repository.moodCategory(browseId, params)
                _uiState.update { it.copy(shelves = page.shelves, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "mood page load failed for %s", browseId)
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Couldn't load this page")
                }
            }
        }
    }

    private companion object {
        const val TAG = "YtMoodViewModel"
    }
}
