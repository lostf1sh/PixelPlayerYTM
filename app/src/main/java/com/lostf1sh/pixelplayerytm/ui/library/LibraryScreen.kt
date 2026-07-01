package com.lostf1sh.pixelplayerytm.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayerytm.data.repository.YouTubeRepository
import com.lostf1sh.pixelplayerytm.data.youtube.auth.AuthState
import com.lostf1sh.pixelplayerytm.domain.model.SongItem
import com.lostf1sh.pixelplayerytm.domain.model.YtItem
import com.lostf1sh.pixelplayerytm.ui.components.ErrorBox
import com.lostf1sh.pixelplayerytm.ui.components.ItemActions
import com.lostf1sh.pixelplayerytm.ui.components.ItemRow
import com.lostf1sh.pixelplayerytm.ui.components.SongRow
import com.lostf1sh.pixelplayerytm.ui.components.onClick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab(val label: String) {
    PLAYLISTS("Playlists"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
}

data class LibraryTabState(
    val items: List<YtItem> = emptyList(),
    val continuation: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.PLAYLISTS,
    val tabs: Map<LibraryTab, LibraryTabState> = LibraryTab.entries.associateWith { LibraryTabState() },
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    authState: AuthState,
) : ViewModel() {

    val loggedIn = authState.loggedIn

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun selectTab(tab: LibraryTab) {
        _state.value = _state.value.copy(selectedTab = tab)
        ensureLoaded(tab)
    }

    fun ensureLoaded(tab: LibraryTab = _state.value.selectedTab, force: Boolean = false) {
        val tabState = _state.value.tabs.getValue(tab)
        if (!force && (tabState.loaded || tabState.isLoading)) return
        update(tab) { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = when (tab) {
                LibraryTab.PLAYLISTS -> repository.libraryPlaylists()
                LibraryTab.SONGS -> repository.librarySongs()
                LibraryTab.ALBUMS -> repository.libraryAlbums()
                LibraryTab.ARTISTS -> repository.libraryArtists()
            }
            result
                .onSuccess { (items, continuation) ->
                    update(tab) {
                        LibraryTabState(
                            items = items,
                            continuation = continuation,
                            loaded = true,
                        )
                    }
                }
                .onFailure { e ->
                    update(tab) { it.copy(isLoading = false, error = e.message, loaded = true) }
                }
        }
    }

    fun loadMore(tab: LibraryTab = _state.value.selectedTab) {
        val tabState = _state.value.tabs.getValue(tab)
        val token = tabState.continuation ?: return
        if (tabState.isLoadingMore) return
        update(tab) { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            repository.libraryContinuation(token)
                .onSuccess { (items, continuation) ->
                    update(tab) {
                        it.copy(
                            items = it.items + items,
                            continuation = continuation,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure {
                    update(tab) { it.copy(isLoadingMore = false, continuation = null) }
                }
        }
    }

    private fun update(tab: LibraryTab, transform: (LibraryTabState) -> LibraryTabState) {
        val tabs = _state.value.tabs.toMutableMap()
        tabs[tab] = transform(tabs.getValue(tab))
        _state.value = _state.value.copy(tabs = tabs)
    }
}

@Composable
fun LibraryScreen(
    actions: ItemActions,
    onLoginClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val loggedIn by viewModel.loggedIn.collectAsState()
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        if (!loggedIn) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    text = "Sign in with your Google account to see your playlists, liked songs, albums and artists.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onLoginClick,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Sign in")
                }
            }
            return
        }

        LaunchedEffect(loggedIn) {
            if (loggedIn) viewModel.ensureLoaded()
        }

        PrimaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(tab.label) },
                )
            }
        }

        val tabState = state.tabs.getValue(state.selectedTab)
        val listState = rememberLazyListState()
        val shouldLoadMore by remember(state.selectedTab) {
            derivedStateOf {
                val info = listState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5
            }
        }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) viewModel.loadMore()
        }

        when {
            tabState.isLoading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            tabState.error != null -> ErrorBox(
                message = tabState.error ?: "Failed to load",
                onRetry = { viewModel.ensureLoaded(force = true) },
            )

            tabState.items.isEmpty() && tabState.loaded -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing here yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 160.dp),
            ) {
                items(tabState.items, key = { it.id + it.title }) { item ->
                    if (item is SongItem) {
                        SongRow(song = item, onClick = { actions.onSong(item) })
                    } else {
                        ItemRow(item = item, onClick = { actions.onClick(item) })
                    }
                }
                if (tabState.isLoadingMore) {
                    item(key = "loadingMore") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
