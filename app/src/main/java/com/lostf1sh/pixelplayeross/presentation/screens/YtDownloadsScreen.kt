package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.runtime.rememberCoroutineScope
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import com.lostf1sh.pixelplayeross.data.model.toSong
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtTrackOptionsSheetContent
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YtRadioViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YtTrackActionsViewModel
import kotlinx.coroutines.launch

/**
 * The user's offline YTM downloads, newest first. Rows play through the normal `ytm://`
 * pipeline — the player engine swaps in the on-disk file, so everything here works with
 * no connection.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtDownloadsScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    viewModel: YtTrackActionsViewModel = hiltViewModel(),
    radioViewModel: YtRadioViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var trackForOptions by remember { mutableStateOf<YtTrack?>(null) }

    val entries = remember(downloads) {
        downloads.values.sortedByDescending { it.downloadedAtMs }
    }
    val songs = remember(entries) { entries.map(viewModel::songOf) }
    val bottomPadding = if (stablePlayerState.currentSong != null) MiniPlayerHeight else 0.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing downloaded yet. Long-press any song and pick Download to keep it offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 38.dp + bottomPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                entries.forEachIndexed { index, entry ->
                    item(key = entry.videoId, contentType = "track") {
                        EnhancedSongListItem(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            song = songs[index],
                            isPlaying = stablePlayerState.isPlaying,
                            isCurrentSong = stablePlayerState.currentSong?.id == entry.videoId,
                            onLongPress = { trackForOptions = viewModel.trackOf(entry) },
                            onMoreOptionsClick = { trackForOptions = viewModel.trackOf(entry) },
                            onClick = {
                                playerViewModel.playSongs(songs, songs[index], "Downloads")
                            },
                        )
                    }
                }
            }
        }
    }

    trackForOptions?.let { track ->
        ModalBottomSheet(onDismissRequest = { trackForOptions = null }) {
            YtTrackOptionsSheetContent(
                track = track,
                onDismiss = { trackForOptions = null },
                onPlayNext = {
                    playerViewModel.addSongNextToQueue(track.toSongFromDownloads(viewModel))
                    trackForOptions = null
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(track.toSongFromDownloads(viewModel))
                    trackForOptions = null
                },
                onStartRadio = {
                    trackForOptions = null
                    scope.launch {
                        val radio = radioViewModel.radioSongsFor(track)
                        radio.firstOrNull()?.let {
                            playerViewModel.playSongs(radio, it, "${track.title} Radio")
                        }
                    }
                },
                onGoToAlbum = null,
                onGoToArtist = null,
            )
        }
    }
}

/** Prefer the download entry's Song (local artwork) when it still exists. */
private fun YtTrack.toSongFromDownloads(viewModel: YtTrackActionsViewModel) =
    viewModel.downloads.value[videoId]?.let(viewModel::songOf) ?: toSong()
