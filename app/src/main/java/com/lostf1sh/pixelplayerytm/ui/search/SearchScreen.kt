package com.lostf1sh.pixelplayerytm.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lostf1sh.pixelplayerytm.data.innertube.SearchFilter
import com.lostf1sh.pixelplayerytm.domain.model.SongItem
import com.lostf1sh.pixelplayerytm.ui.components.ErrorBox
import com.lostf1sh.pixelplayerytm.ui.components.ItemActions
import com.lostf1sh.pixelplayerytm.ui.components.ItemRow
import com.lostf1sh.pixelplayerytm.ui.components.ShelfSection
import com.lostf1sh.pixelplayerytm.ui.components.SongRow
import com.lostf1sh.pixelplayerytm.ui.components.onClick

private val FILTERS = listOf(
    SearchFilter.ALL to "All",
    SearchFilter.SONGS to "Songs",
    SearchFilter.VIDEOS to "Videos",
    SearchFilter.ALBUMS to "Albums",
    SearchFilter.ARTISTS to "Artists",
    SearchFilter.PLAYLISTS to "Playlists",
)

@Composable
fun SearchScreen(
    actions: ItemActions,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    LaunchedEffect(Unit) {
        if (!state.hasSearched) focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            TextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search songs, albums, artists…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
            )
        }

        if (state.hasSearched) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(FILTERS, key = { it.second }) { (filter, label) ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(label) },
                    )
                }
            }
        }

        when {
            !state.hasSearched && state.suggestions.isNotEmpty() -> {
                LazyColumn {
                    items(state.suggestions, key = { it }) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.search(suggestion) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = suggestion,
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            state.isSearching -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.error != null -> ErrorBox(
                message = state.error ?: "Search failed",
                onRetry = { viewModel.search() },
            )

            state.summary != null -> LazyColumn(
                contentPadding = PaddingValues(bottom = 160.dp),
            ) {
                itemsIndexed(
                    state.summary?.shelves.orEmpty(),
                    key = { index, shelf -> "$index/${shelf.title}" },
                ) { _, shelf ->
                    ShelfSection(shelf = shelf, actions = actions)
                }
            }

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 160.dp),
            ) {
                items(state.results, key = { it.id + it.title }) { item ->
                    if (item is SongItem) {
                        SongRow(song = item, onClick = { actions.onSong(item) })
                    } else {
                        ItemRow(item = item, onClick = { actions.onClick(item) })
                    }
                }
                if (state.isLoadingMore) {
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
