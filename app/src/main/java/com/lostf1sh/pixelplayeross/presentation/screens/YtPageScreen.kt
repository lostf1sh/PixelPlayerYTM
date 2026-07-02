package com.lostf1sh.pixelplayeross.presentation.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.YtPageKind
import com.lostf1sh.pixelplayeross.data.model.YtShelfEntry
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import com.lostf1sh.pixelplayeross.data.model.toSong
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtErrorBox
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtLoadingBox
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtShelfSection
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtTrackOptionsSheetContent
import com.lostf1sh.pixelplayeross.presentation.components.youtube.ytSmoothShape
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YtPageViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YtRadioViewModel
import kotlinx.coroutines.launch

/**
 * Generic YTM detail page: album, playlist, artist, or podcast. Hero header + track list
 * + secondary shelves (an artist's albums, related artists, …) — all four page kinds are
 * structurally the same browse response, so one screen serves them all.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtPageScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    viewModel: YtPageViewModel = hiltViewModel(),
    radioViewModel: YtRadioViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val page = uiState.page
    val pageSongs = remember(page) { page?.tracks?.map { it.toSong() }.orEmpty() }
    val queueName = page?.title?.ifBlank { null } ?: "YouTube Music"

    var trackForOptions by remember { mutableStateOf<YtTrack?>(null) }

    val bottomPadding = if (stablePlayerState.currentSong != null) MiniPlayerHeight else 0.dp
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = page?.title.orEmpty(),
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

            uiState.error != null || page == null -> Box(Modifier.padding(innerPadding).fillMaxSize()) {
                YtErrorBox(
                    message = uiState.error ?: "Couldn't load this page",
                    onRetry = viewModel::load,
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 38.dp + bottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "hero", contentType = "hero") {
                    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
                    PageHero(
                        title = page.title,
                        subtitle = page.subtitle,
                        heroImageUrl = page.heroImageUrl,
                        isArtist = viewModel.kind == YtPageKind.ARTIST,
                        hasTracks = pageSongs.isNotEmpty(),
                        subscribed = page.subscribed,
                        onToggleSubscribe = if (isSignedIn && page.channelId != null) {
                            viewModel::toggleSubscription
                        } else {
                            null
                        },
                        onPlay = {
                            pageSongs.firstOrNull()?.let { first ->
                                playerViewModel.playSongs(pageSongs, first, queueName)
                            }
                        },
                        onShuffle = {
                            playerViewModel.playSongsShuffled(pageSongs, queueName, startAtZero = true)
                        },
                    )
                }

                page.tracks.forEachIndexed { index, track ->
                    item(key = "track_${track.videoId}", contentType = "track") {
                        EnhancedSongListItem(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            song = pageSongs[index],
                            isPlaying = stablePlayerState.isPlaying,
                            isCurrentSong = stablePlayerState.currentSong?.id == track.videoId,
                            onLongPress = { trackForOptions = track },
                            onMoreOptionsClick = { trackForOptions = track },
                            onClick = {
                                playerViewModel.playSongs(pageSongs, pageSongs[index], queueName)
                            },
                        )
                    }
                }

                page.shelves.forEach { shelf ->
                    item(key = shelf.id, contentType = "shelf") {
                        Spacer(Modifier.height(16.dp))
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
    }

    trackForOptions?.let { track ->
        ModalBottomSheet(onDismissRequest = { trackForOptions = null }) {
            YtTrackOptionsSheetContent(
                track = track,
                onDismiss = { trackForOptions = null },
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

@Composable
private fun PageHero(
    title: String,
    subtitle: String?,
    heroImageUrl: String?,
    isArtist: Boolean,
    hasTracks: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    subscribed: Boolean = false,
    onToggleSubscribe: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SmartImage(
            model = heroImageUrl,
            contentDescription = title,
            shape = if (isArtist) CircleShape else ytSmoothShape(28.dp),
            modifier = Modifier.size(220.dp),
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
        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hasTracks) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPlay) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Play")
                }
                FilledTonalButton(onClick = onShuffle) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_shuffle_24),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle")
                }
            }
        }
        onToggleSubscribe?.let { toggle ->
            Spacer(Modifier.height(12.dp))
            if (subscribed) {
                FilledTonalButton(onClick = toggle) { Text("Subscribed") }
            } else {
                OutlinedButton(onClick = toggle) { Text("Subscribe") }
            }
        }
    }
}
