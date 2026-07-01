package com.lostf1sh.pixelplayerytm.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lostf1sh.pixelplayerytm.ui.components.ErrorBox
import com.lostf1sh.pixelplayerytm.ui.components.ItemActions
import com.lostf1sh.pixelplayerytm.ui.components.LoadingBox
import com.lostf1sh.pixelplayerytm.ui.components.ShelfSection

@Composable
fun HomeScreen(
    actions: ItemActions,
    onSearchClick: () -> Unit,
    onAccountClick: () -> Unit,
    onMoreClick: (browseId: String, params: String?, title: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 160.dp),
    ) {
        item(key = "topbar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PixelPlayer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = onAccountClick) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Account")
                }
            }
        }

        when {
            state.isLoading -> item(key = "loading") {
                LoadingBox(Modifier.fillParentMaxSize())
            }

            state.error != null -> item(key = "error") {
                ErrorBox(
                    message = state.error ?: "Something went wrong",
                    modifier = Modifier.fillParentMaxSize(),
                    onRetry = viewModel::load,
                )
            }

            else -> {
                itemsIndexed(
                    state.shelves,
                    key = { index, shelf -> "$index/${shelf.title}" },
                ) { _, shelf ->
                    ShelfSection(
                        shelf = shelf,
                        actions = actions,
                        onMoreClick = shelf.moreBrowseId?.let { browseId ->
                            { onMoreClick(browseId, shelf.moreParams, shelf.title) }
                        },
                    )
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
