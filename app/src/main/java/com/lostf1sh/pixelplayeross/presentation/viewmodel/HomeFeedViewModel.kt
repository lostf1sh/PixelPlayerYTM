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
 * Drives the YouTube Music home feed and the Explore (moods & genres) grid, plus the
 * "start radio" affordance. Playback itself stays in [PlayerViewModel]; this VM only
 * fetches data and hands resolved song lists back to the caller.
 */
@HiltViewModel
class HomeFeedViewModel @Inject constructor(
    private val repository: YouTubeMusicRepository,
) : ViewModel() {

    data class FeedUiState(
        val isLoading: Boolean = true,
        val shelves: ImmutableList<HomeShelf> = persistentListOf(),
        val isError: Boolean = false,
    )

    private val _home = MutableStateFlow(FeedUiState())
    val home = _home.asStateFlow()

    private val _explore = MutableStateFlow(FeedUiState())
    val explore = _explore.asStateFlow()

    private var homeContinuation: String? = null
    private var homeLoaded = false
    private var exploreLoaded = false

    fun loadHomeIfNeeded() {
        if (homeLoaded) return
        homeLoaded = true
        refreshHome()
    }

    fun refreshHome() {
        _home.value = _home.value.copy(isLoading = true, isError = false)
        viewModelScope.launch {
            val page = repository.getHomeFeed()
            homeContinuation = page.continuation
            _home.value = FeedUiState(
                isLoading = false,
                shelves = page.shelves.toImmutableList(),
                isError = page.shelves.isEmpty(),
            )
        }
    }

    fun loadMoreHome() {
        val token = homeContinuation ?: return
        homeContinuation = null
        viewModelScope.launch {
            val page = repository.getHomeFeed(token)
            homeContinuation = page.continuation
            val merged = (_home.value.shelves + page.shelves).toImmutableList()
            _home.value = _home.value.copy(shelves = merged)
        }
    }

    fun loadExploreIfNeeded() {
        if (exploreLoaded) return
        exploreLoaded = true
        _explore.value = _explore.value.copy(isLoading = true, isError = false)
        viewModelScope.launch {
            val shelves = repository.getMoodsAndGenres()
            _explore.value = FeedUiState(
                isLoading = false,
                shelves = shelves.toImmutableList(),
                isError = shelves.isEmpty(),
            )
        }
    }

    /** Fetch a radio/up-next queue seeded by [song]; delivers the full play list to [onReady]. */
    fun startRadio(song: Song, onReady: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val page = repository.getWatchQueue(song.id)
            val queue = if (page.songs.any { it.id == song.id }) page.songs else listOf(song) + page.songs
            onReady(queue.ifEmpty { listOf(song) })
        }
    }
}
