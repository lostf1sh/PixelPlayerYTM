package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.lostf1sh.pixelplayeross.data.model.ShelfItem
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.components.home.ShelfRenderer
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YouTubeBrowseViewModel

/** Album / playlist / artist detail page from YouTube Music. */
@Composable
fun YouTubeBrowseScreen(
    navController: NavHostController,
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel,
    browseId: String,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    LaunchedEffect(browseId) { viewModel.loadBrowse(browseId) }
    BrowseFeedContent(navController, paddingValues, playerViewModel, viewModel)
}

/** Mood / genre category page from YouTube Music. */
@Composable
fun YouTubeMoodScreen(
    navController: NavHostController,
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel,
    browseId: String,
    params: String?,
    title: String,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    LaunchedEffect(browseId, params) { viewModel.loadMood(browseId, params, title) }
    BrowseFeedContent(navController, paddingValues, playerViewModel, viewModel)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BrowseFeedContent(
    navController: NavHostController,
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel,
    viewModel: YouTubeBrowseViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.title.isNotBlank() || state.thumbnailUrl != null) {
                item(key = "browse_header") {
                    BrowseHeader(
                        title = state.title,
                        subtitle = state.subtitle,
                        thumbnailUrl = state.thumbnailUrl,
                        canPlay = state.songs.isNotEmpty(),
                        onPlay = {
                            state.songs.firstOrNull()?.let {
                                playerViewModel.showAndPlaySong(it, state.songs, state.title)
                            }
                        },
                        onRadio = {
                            state.songs.firstOrNull()?.let { playerViewModel.startYouTubeRadio(it) }
                        },
                    )
                }
            }

            items(state.songs, key = { "s_${it.id}" }, contentType = { "song" }) { song ->
                BrowseSongRow(
                    song = song,
                    onClick = { playerViewModel.showAndPlaySong(song, state.songs, state.title) },
                )
            }

            items(state.shelves, key = { it.id }, contentType = { "shelf" }) { shelf ->
                val shelfSongs = shelf.items.filterIsInstance<ShelfItem.SongItem>().map { it.song }
                ShelfRenderer(
                    shelf = shelf,
                    onSongClick = { item -> playerViewModel.showAndPlaySong(item.song, shelfSongs, shelf.title) },
                    onBrowseClick = { item -> navController.navigateSafely(Screen.YouTubeBrowse.createRoute(item.browseId)) },
                    onMoodClick = { item -> navController.navigateSafely(Screen.YouTubeMood.createRoute(item.browseId, item.params)) },
                )
            }
        }

        if (state.isLoading) {
            LoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun BrowseHeader(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onRadio: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        thumbnailUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = title,
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canPlay) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onPlay) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play")
                }
                OutlinedButton(onClick = onRadio) {
                    Icon(Icons.Rounded.Radio, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Radio")
                }
            }
        }
    }
}

@Composable
private fun BrowseSongRow(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.albumArtUriString,
            contentDescription = song.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
