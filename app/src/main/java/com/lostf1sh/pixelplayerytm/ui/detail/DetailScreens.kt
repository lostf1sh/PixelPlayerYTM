package com.lostf1sh.pixelplayerytm.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lostf1sh.pixelplayerytm.domain.model.SongItem
import com.lostf1sh.pixelplayerytm.ui.components.Artwork
import com.lostf1sh.pixelplayerytm.ui.components.ErrorBox
import com.lostf1sh.pixelplayerytm.ui.components.ItemActions
import com.lostf1sh.pixelplayerytm.ui.components.LoadingBox
import com.lostf1sh.pixelplayerytm.ui.components.ShelfSection
import com.lostf1sh.pixelplayerytm.ui.components.SongRow

@Composable
private fun DetailHeader(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    isArtist: Boolean = false,
    onBack: () -> Unit,
    primaryAction: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(
                url = thumbnailUrl,
                modifier = Modifier.size(200.dp),
                shape = if (isArtist) CircleShape else RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                primaryAction?.invoke()
                secondaryAction?.invoke()
            }
        }
    }
}

@Composable
fun AlbumScreen(
    actions: ItemActions,
    onBack: () -> Unit,
    onPlayAll: (songs: List<SongItem>, startIndex: Int, shuffle: Boolean) -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when {
        state.isLoading -> LoadingBox()
        state.error != null -> ErrorBox(
            message = state.error ?: "Failed to load album",
            onRetry = viewModel::load,
        )

        else -> {
            val page = state.page ?: return
            LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                item(key = "header") {
                    DetailHeader(
                        title = page.album.title,
                        subtitle = listOfNotNull(
                            page.album.artists.joinToString(", ") { it.name }.ifEmpty { null },
                            page.yearAndCount,
                        ).joinToString(" • "),
                        thumbnailUrl = page.album.thumbnailUrl,
                        onBack = onBack,
                        primaryAction = {
                            Button(onClick = { onPlayAll(page.songs, 0, false) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Play")
                            }
                        },
                        secondaryAction = {
                            OutlinedButton(onClick = { onPlayAll(page.songs, 0, true) }) {
                                Icon(Icons.Default.Shuffle, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Shuffle")
                            }
                        },
                    )
                }
                itemsIndexed(page.songs, key = { i, s -> "$i/${s.videoId}" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlayAll(page.songs, index, false) },
                        trailingText = song.durationText,
                    )
                }
                if (page.otherVersions.isNotEmpty()) {
                    item(key = "other-versions") {
                        ShelfSection(
                            shelf = com.lostf1sh.pixelplayerytm.domain.model.Shelf(
                                title = "Other versions",
                                items = page.otherVersions,
                            ),
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(
    actions: ItemActions,
    onBack: () -> Unit,
    onPlayRadio: (playlistId: String) -> Unit,
    onShufflePlay: (playlistId: String) -> Unit,
    onMoreClick: (browseId: String, params: String?, title: String) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when {
        state.isLoading -> LoadingBox()
        state.error != null -> ErrorBox(
            message = state.error ?: "Failed to load artist",
            onRetry = viewModel::load,
        )

        else -> {
            val page = state.page ?: return
            LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                item(key = "header") {
                    DetailHeader(
                        title = page.artist.title,
                        subtitle = null,
                        thumbnailUrl = page.artist.thumbnailUrl,
                        isArtist = true,
                        onBack = onBack,
                        primaryAction = page.shuffplaylistId?.let { playlistId ->
                            {
                                Button(onClick = { onShufflePlay(playlistId) }) {
                                    Icon(Icons.Default.Shuffle, contentDescription = null)
                                    Spacer(Modifier.size(4.dp))
                                    Text("Shuffle")
                                }
                            }
                        },
                        secondaryAction = page.radioPlaylistId?.let { playlistId ->
                            {
                                OutlinedButton(onClick = { onPlayRadio(playlistId) }) {
                                    Icon(Icons.Default.Radio, contentDescription = null)
                                    Spacer(Modifier.size(4.dp))
                                    Text("Radio")
                                }
                            }
                        },
                    )
                }
                itemsIndexed(page.sections, key = { i, s -> "$i/${s.title}" }) { _, shelf ->
                    ShelfSection(
                        shelf = shelf,
                        actions = actions,
                        onMoreClick = shelf.moreBrowseId?.let { browseId ->
                            { onMoreClick(browseId, shelf.moreParams, shelf.title) }
                        },
                    )
                }
                if (!page.description.isNullOrBlank()) {
                    item(key = "about") {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = "About",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = page.description.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    onPlayAll: (songs: List<SongItem>, startIndex: Int, shuffle: Boolean) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
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

    when {
        state.isLoading -> LoadingBox()
        state.error != null -> ErrorBox(
            message = state.error ?: "Failed to load playlist",
            onRetry = viewModel::load,
        )

        else -> {
            val page = state.page ?: return
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 160.dp),
            ) {
                item(key = "header") {
                    DetailHeader(
                        title = page.playlist.title,
                        subtitle = listOfNotNull(page.subtitle, page.secondSubtitle)
                            .distinct()
                            .joinToString(" • ")
                            .ifEmpty { null },
                        thumbnailUrl = page.playlist.thumbnailUrl,
                        onBack = onBack,
                        primaryAction = {
                            Button(onClick = { onPlayAll(page.songs, 0, false) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Play")
                            }
                        },
                        secondaryAction = {
                            OutlinedButton(onClick = { onPlayAll(page.songs, 0, true) }) {
                                Icon(Icons.Default.Shuffle, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Shuffle")
                            }
                        },
                    )
                }
                itemsIndexed(page.songs, key = { i, s -> "$i/${s.videoId}" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onPlayAll(page.songs, index, false) },
                        trailingText = song.durationText,
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

@Composable
fun BrowseScreen(
    actions: ItemActions,
    onBack: () -> Unit,
    onMoreClick: (browseId: String, params: String?, title: String) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            state.isLoading -> LoadingBox()
            state.error != null -> ErrorBox(
                message = state.error ?: "Failed to load",
                onRetry = viewModel::load,
            )

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                itemsIndexed(state.shelves, key = { i, s -> "$i/${s.title}" }) { _, shelf ->
                    ShelfSection(
                        shelf = shelf,
                        actions = actions,
                        onMoreClick = shelf.moreBrowseId?.let { browseId ->
                            { onMoreClick(browseId, shelf.moreParams, shelf.title) }
                        },
                    )
                }
            }
        }
    }
}
