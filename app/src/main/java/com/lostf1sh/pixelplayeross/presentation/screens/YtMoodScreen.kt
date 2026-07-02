package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.lostf1sh.pixelplayeross.data.model.YtPageKind
import com.lostf1sh.pixelplayeross.data.model.YtShelfEntry
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import com.lostf1sh.pixelplayeross.data.model.toSong
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtErrorBox
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtLoadingBox
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtShelfSection
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtTrackOptionsSheetContent
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YtMoodViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YtRadioViewModel
import kotlinx.coroutines.launch

/** The shelf feed behind one Explore mood/genre chip ("Chill", "Rock", "Commute", …). */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtMoodScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    viewModel: YtMoodViewModel = hiltViewModel(),
    radioViewModel: YtRadioViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var trackForOptions by remember { mutableStateOf<YtTrack?>(null) }

    val bottomPadding = if (stablePlayerState.currentSong != null) MiniPlayerHeight else 0.dp
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = viewModel.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(Modifier.padding(innerPadding).fillMaxSize()) {
                YtLoadingBox()
            }

            uiState.error != null -> Box(Modifier.padding(innerPadding).fillMaxSize()) {
                YtErrorBox(
                    message = uiState.error ?: "Couldn't load this page",
                    onRetry = viewModel::load,
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 38.dp + bottomPadding),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(uiState.shelves, key = { it.id }) { shelf ->
                    YtShelfSection(
                        shelf = shelf,
                        onTrackClick = { track, fromShelf ->
                            val shelfSongs = fromShelf.entries
                                .filterIsInstance<YtShelfEntry.Track>()
                                .map { it.track.toSong() }
                            val start = shelfSongs.firstOrNull { it.id == track.videoId }
                            if (start != null && shelfSongs.size > 1) {
                                playerViewModel.playSongs(shelfSongs, start, fromShelf.title)
                            } else {
                                scope.launch {
                                    val radio = radioViewModel.radioSongsFor(track)
                                    playerViewModel.playSongs(radio, radio.first(), "${track.title} Radio")
                                }
                            }
                        },
                        onPageClick = { entry ->
                            navController.navigateSafely(
                                Screen.YtPage.createRoute(entry.kind, entry.browseId)
                            )
                        },
                        onCategoryClick = { category ->
                            navController.navigateSafely(
                                Screen.YtMood.createRoute(category.browseId, category.params, category.title)
                            )
                        },
                        currentSongId = stablePlayerState.currentSong?.id,
                        isPlaying = stablePlayerState.isPlaying,
                        onTrackLongPress = { trackForOptions = it },
                    )
                }
            }
        }
    }

    trackForOptions?.let { track ->
        ModalBottomSheet(onDismissRequest = { trackForOptions = null }) {
            YtTrackOptionsSheetContent(
                track = track,
                onPlayNext = {
                    playerViewModel.addSongNextToQueue(track.toSong())
                    trackForOptions = null
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(track.toSong())
                    trackForOptions = null
                },
                onStartRadio = {
                    trackForOptions = null
                    scope.launch {
                        val radio = radioViewModel.radioSongsFor(track)
                        playerViewModel.playSongs(radio, radio.first(), "${track.title} Radio")
                    }
                },
                onGoToAlbum = track.albumBrowseId?.let { albumId ->
                    {
                        trackForOptions = null
                        navController.navigateSafely(
                            Screen.YtPage.createRoute(YtPageKind.ALBUM, albumId)
                        )
                    }
                },
                onGoToArtist = track.artists.firstOrNull { it.channelId != null }?.channelId?.let { channelId ->
                    {
                        trackForOptions = null
                        navController.navigateSafely(
                            Screen.YtPage.createRoute(YtPageKind.ARTIST, channelId)
                        )
                    }
                },
            )
        }
    }
}
