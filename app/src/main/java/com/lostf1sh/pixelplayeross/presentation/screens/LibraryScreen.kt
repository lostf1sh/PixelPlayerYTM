package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.model.Playlist
import com.lostf1sh.pixelplayeross.data.model.YtPageKind
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import com.lostf1sh.pixelplayeross.data.model.toSong
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistCover
import com.lostf1sh.pixelplayeross.presentation.components.resolveMainScreenBottomGradientHeight
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtErrorBox
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtLoadingBox
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtPageListRow
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtTrackOptionsSheetContent
import com.lostf1sh.pixelplayeross.presentation.components.youtube.ytSmoothShape
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlaylistViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YtLibraryViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YtRadioViewModel
import com.lostf1sh.pixelplayeross.utils.formatSongCount
import kotlinx.coroutines.launch

/** Kept for [com.lostf1sh.pixelplayeross.presentation.components.PlaylistContainer]. */
val PlayerSheetCollapsedCornerRadius = 32.dp

private enum class LibrarySection(val label: String) {
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    LIKED("Liked"),
}

/**
 * Library tab: the signed-in YTM library (playlists / albums / artists / liked songs)
 * plus the device's local files collapsed into a single synthetic "Local Songs"
 * playlist and the user's local playlists.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
) {
    val libraryViewModel: YtLibraryViewModel = hiltViewModel()
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val radioViewModel: YtRadioViewModel = hiltViewModel()

    val lib by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val localSongs by playerViewModel.allSongsFlow.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var section by rememberSaveable { mutableStateOf(LibrarySection.PLAYLISTS.name) }
    val currentSection = LibrarySection.entries.first { it.name == section }
    var trackForOptions by remember { mutableStateOf<YtTrack?>(null) }

    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode)
    val miniPlayerPadding = if (stablePlayerState.currentSong != null) MiniPlayerHeight else 0.dp
    val listBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() +
        94.dp + miniPlayerPadding

    val likedSongs = remember(lib.likedTracks) { lib.likedTracks.map { it.toSong() } }

    val colorScheme = MaterialTheme.colorScheme
    val bottomGradientBrush = remember(colorScheme.surfaceContainerLowest) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.2f to Color.Transparent,
                0.8f to colorScheme.surfaceContainerLowest,
                1.0f to colorScheme.surfaceContainerLowest
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = statusBarTopInset + 16.dp, end = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                FilledIconButton(
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    onClick = { navController.navigateSafely(Screen.YtLogin.route) }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = "YouTube Music account",
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibrarySection.entries.forEach { candidate ->
                    LibrarySectionChip(
                        label = candidate.label,
                        selected = candidate == currentSection,
                        onClick = { section = candidate.name },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (currentSection) {
                    LibrarySection.PLAYLISTS -> {
                        item(key = "local_songs", contentType = "local_songs") {
                            LocalSongsRow(
                                songCount = localSongs.size,
                                onClick = { navController.navigateSafely(Screen.LocalSongs.route) },
                            )
                        }
                        items(
                            count = playlistUiState.playlists.size,
                            key = { "local_pl_${playlistUiState.playlists[it].id}" },
                            contentType = { "local_playlist" },
                        ) { index ->
                            LocalPlaylistRow(
                                playlist = playlistUiState.playlists[index],
                                playerViewModel = playerViewModel,
                                onClick = {
                                    navController.navigateSafely(
                                        Screen.PlaylistDetail.createRoute(playlistUiState.playlists[index].id)
                                    )
                                },
                            )
                        }
                        ytSectionItems(
                            signedIn = lib.isSignedIn,
                            isLoading = lib.isLoading,
                            error = lib.error,
                            pages = lib.playlists,
                            emptyText = "No playlists in your YouTube Music library yet.",
                            headerText = "YouTube Music",
                            onRetry = libraryViewModel::refresh,
                            onSignIn = { navController.navigateSafely(Screen.YtLogin.route) },
                            onOpen = { entry ->
                                navController.navigateSafely(
                                    Screen.YtPage.createRoute(entry.kind, entry.browseId)
                                )
                            },
                        )
                    }

                    LibrarySection.ALBUMS -> ytSectionItems(
                        signedIn = lib.isSignedIn,
                        isLoading = lib.isLoading,
                        error = lib.error,
                        pages = lib.albums,
                        emptyText = "No saved albums yet.",
                        headerText = null,
                        onRetry = libraryViewModel::refresh,
                        onSignIn = { navController.navigateSafely(Screen.YtLogin.route) },
                        onOpen = { entry ->
                            navController.navigateSafely(
                                Screen.YtPage.createRoute(entry.kind, entry.browseId)
                            )
                        },
                    )

                    LibrarySection.ARTISTS -> ytSectionItems(
                        signedIn = lib.isSignedIn,
                        isLoading = lib.isLoading,
                        error = lib.error,
                        pages = lib.artists,
                        emptyText = "No artists in your library yet.",
                        headerText = null,
                        onRetry = libraryViewModel::refresh,
                        onSignIn = { navController.navigateSafely(Screen.YtLogin.route) },
                        onOpen = { entry ->
                            navController.navigateSafely(
                                Screen.YtPage.createRoute(entry.kind, entry.browseId)
                            )
                        },
                    )

                    LibrarySection.LIKED -> {
                        when {
                            !lib.isSignedIn -> item(key = "liked_sign_in") {
                                LibrarySignInCard(
                                    onClick = { navController.navigateSafely(Screen.YtLogin.route) }
                                )
                            }

                            lib.isLoading && lib.likedTracks.isEmpty() -> item(key = "liked_loading") {
                                YtLoadingBox()
                            }

                            lib.error != null && lib.likedTracks.isEmpty() -> item(key = "liked_error") {
                                YtErrorBox(message = lib.error!!, onRetry = libraryViewModel::refresh)
                            }

                            lib.likedTracks.isEmpty() -> item(key = "liked_empty") {
                                LibraryEmptyText("Songs you like on YouTube Music show up here.")
                            }

                            else -> {
                                item(key = "liked_header", contentType = "liked_header") {
                                    LikedHeader(
                                        songCount = likedSongs.size,
                                        onPlay = {
                                            playerViewModel.playSongs(likedSongs, likedSongs.first(), "Liked Songs")
                                        },
                                        onShuffle = {
                                            playerViewModel.playSongsShuffled(likedSongs, "Liked Songs", startAtZero = true)
                                        },
                                    )
                                }
                                items(
                                    count = lib.likedTracks.size,
                                    key = { "liked_${lib.likedTracks[it].videoId}" },
                                    contentType = { "liked_track" },
                                ) { index ->
                                    val track = lib.likedTracks[index]
                                    EnhancedSongListItem(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        song = likedSongs[index],
                                        isPlaying = stablePlayerState.isPlaying,
                                        isCurrentSong = stablePlayerState.currentSong?.id == track.videoId,
                                        onMoreOptionsClick = { trackForOptions = track },
                                        onClick = {
                                            playerViewModel.playSongs(likedSongs, likedSongs[index], "Liked Songs")
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(bottomGradientHeight)
                .background(brush = bottomGradientBrush)
        )
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

/** Adds one YTM library section's rows (sign-in gate → loading → error → empty → pages). */
private fun androidx.compose.foundation.lazy.LazyListScope.ytSectionItems(
    signedIn: Boolean,
    isLoading: Boolean,
    error: String?,
    pages: List<com.lostf1sh.pixelplayeross.data.model.YtShelfEntry.Page>,
    emptyText: String,
    headerText: String?,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onOpen: (com.lostf1sh.pixelplayeross.data.model.YtShelfEntry.Page) -> Unit,
) {
    when {
        !signedIn -> item(key = "yt_sign_in") {
            LibrarySignInCard(onClick = onSignIn)
        }

        isLoading && pages.isEmpty() -> item(key = "yt_loading") {
            YtLoadingBox()
        }

        error != null && pages.isEmpty() -> item(key = "yt_error") {
            YtErrorBox(message = error, onRetry = onRetry)
        }

        pages.isEmpty() -> item(key = "yt_empty") {
            LibraryEmptyText(emptyText)
        }

        else -> {
            if (headerText != null) {
                item(key = "yt_header", contentType = "yt_header") {
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            items(
                count = pages.size,
                key = { pages[it].key },
                contentType = { "yt_page_row" },
            ) { index ->
                YtPageListRow(
                    page = pages[index],
                    onClick = { onOpen(pages[index]) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun LocalSongsRow(
    songCount: Int,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            Surface(
                shape = remember { ytSmoothShape(12.dp) },
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
        headlineContent = {
            Text(
                text = "Local Songs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        supportingContent = {
            Text(
                text = formatSongCount(songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LocalPlaylistRow(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
) {
    val playlistSongs by remember(playlist.songIds, playerViewModel) {
        playerViewModel.observeSongs(playlist.songIds)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    ListItem(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = {
            PlaylistCover(
                playlist = playlist,
                playlistSongs = playlistSongs,
                size = 52.dp,
            )
        },
        headlineContent = {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = formatSongCount(playlist.songIds.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun LikedHeader(
    songCount: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatSongCount(songCount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Play")
            }
            FilledTonalButton(onClick = onShuffle) {
                Icon(
                    painter = painterResource(R.drawable.rounded_shuffle_24),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Shuffle")
            }
        }
    }
}

@Composable
private fun LibrarySignInCard(onClick: () -> Unit) {
    Surface(
        shape = remember { ytSmoothShape(24.dp) },
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "Sign in to see your library",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Playlists, albums, artists, and likes live on your account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun LibraryEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
    )
}

@Composable
private fun LibrarySectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        border = BorderStroke(width = 0.dp, color = Color.Transparent),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        leadingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(R.drawable.rounded_check_circle_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        }
    )
}
