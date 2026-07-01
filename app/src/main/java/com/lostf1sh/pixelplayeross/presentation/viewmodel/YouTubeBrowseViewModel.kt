package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.HomeShelf
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.repository.YouTubeMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [YouTubeBrowseScreen][com.lostf1sh.pixelplayeross.presentation.screens.YouTubeBrowseScreen]:
 * loads an album/playlist/artist page (tracks + related shelves) or a mood/genre category
 * (shelves only). One generic screen covers all YTM browse targets.
 */
@HiltViewModel
class YouTubeBrowseViewModel @Inject constructor(
    private val repository: YouTubeMusicRepository,
) : ViewModel() {

    data class BrowseUiState(
        val isLoading: Boolean = true,
        val title: String = "",
        val subtitle: String? = null,
        val thumbnailUrl: String? = null,
        val songs: ImmutableList<Song> = persistentListOf(),
        val shelves: ImmutableList<HomeShelf> = persistentListOf(),
        val isError: Boolean = false,
    )

    private val _state = MutableStateFlow(BrowseUiState())
    val state = _state.asStateFlow()

    private var loadedKey: String? = null

    fun loadBrowse(browseId: String) {
        if (loadedKey == browseId) return
        loadedKey = browseId
        _state.value = BrowseUiState(isLoading = true)
        viewModelScope.launch {
            val page = repository.getBrowsePage(browseId)
            if (page == null) {
                _state.value = BrowseUiState(isLoading = false, isError = true)
                return@launch
            }
            _state.value = BrowseUiState(
                isLoading = false,
                title = page.title,
                subtitle = page.subtitle,
                thumbnailUrl = page.thumbnailUrl,
                songs = page.songs.toImmutableList(),
                shelves = page.shelves.toImmutableList(),
            )
        }
    }

    fun loadMood(browseId: String, params: String?, title: String) {
        val key = "mood:$browseId:$params"
        if (loadedKey == key) return
        loadedKey = key
        _state.value = BrowseUiState(isLoading = true, title = title)
        viewModelScope.launch {
            val page = repository.getMoodCategory(browseId, params)
            _state.value = BrowseUiState(
                isLoading = false,
                title = title,
                shelves = page.shelves.toImmutableList(),
                isError = page.shelves.isEmpty(),
            )
        }
    }
}
