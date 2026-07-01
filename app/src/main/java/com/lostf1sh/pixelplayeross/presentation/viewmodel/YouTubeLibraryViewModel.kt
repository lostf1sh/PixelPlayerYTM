package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.ShelfItem
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.repository.YouTubeMusicRepository
import com.lostf1sh.pixelplayeross.data.youtube.auth.YouTubeAccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The YouTube Music library tabs (Local is handled separately in the UI). */
enum class YouTubeLibraryTab(val label: String) {
    PLAYLISTS("Playlists"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
}

/**
 * Backs the tabbed YouTube Music library. Each remote tab loads lazily the first time it's
 * shown and exposes an explicit loading/loaded state so the UI never renders a blank screen.
 */
@HiltViewModel
class YouTubeLibraryViewModel @Inject constructor(
    private val repository: YouTubeMusicRepository,
    private val accountManager: YouTubeAccountManager,
) : ViewModel() {

    data class TabState(
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        val songs: ImmutableList<Song> = persistentListOf(),
        val items: ImmutableList<ShelfItem.BrowseItem> = persistentListOf(),
    ) {
        val isEmpty: Boolean get() = loaded && songs.isEmpty() && items.isEmpty()
    }

    val isLoggedIn: StateFlow<Boolean> = accountManager.isLoggedIn

    private val tabStates: Map<YouTubeLibraryTab, MutableStateFlow<TabState>> =
        YouTubeLibraryTab.entries.associateWith { MutableStateFlow(TabState()) }

    fun tabState(tab: YouTubeLibraryTab): StateFlow<TabState> = tabStates.getValue(tab).asStateFlow()

    fun loadTab(tab: YouTubeLibraryTab, force: Boolean = false) {
        if (!accountManager.isLoggedIn.value) return
        val flow = tabStates.getValue(tab)
        if (!force && (flow.value.loaded || flow.value.isLoading)) return

        flow.value = flow.value.copy(isLoading = true)
        viewModelScope.launch {
            when (tab) {
                YouTubeLibraryTab.SONGS -> {
                    val songs = repository.getLikedSongs()
                    flow.value = TabState(loaded = true, songs = songs.toImmutableList())
                }
                YouTubeLibraryTab.PLAYLISTS -> {
                    val items = repository.getLibraryPlaylists()
                    flow.value = TabState(loaded = true, items = items.toImmutableList())
                }
                YouTubeLibraryTab.ALBUMS -> {
                    val items = repository.getLibraryAlbums()
                    flow.value = TabState(loaded = true, items = items.toImmutableList())
                }
                YouTubeLibraryTab.ARTISTS -> {
                    val items = repository.getLibraryArtists()
                    flow.value = TabState(loaded = true, items = items.toImmutableList())
                }
            }
        }
    }

    fun refreshAll() {
        YouTubeLibraryTab.entries.forEach { loadTab(it, force = true) }
    }

    fun signOut() {
        accountManager.signOut()
        tabStates.values.forEach { it.value = TabState() }
    }
}
