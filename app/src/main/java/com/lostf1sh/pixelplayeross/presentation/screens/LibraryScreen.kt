package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
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
import com.lostf1sh.pixelplayeross.data.model.YtShelfEntry
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import com.lostf1sh.pixelplayeross.data.model.toSong
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.PlaylistCover
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.components.resolveMainScreenBottomGradientHeight
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtErrorBox
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtLoadingBox
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

/** Where a row sits inside its card stack — drives the grouped-corner shape. */
private enum class GroupPosition { SINGLE, FIRST, MIDDLE, LAST }

private fun positionFor(index: Int, count: Int): GroupPosition = when {
    count == 1 -> GroupPosition.SINGLE
    index == 0 -> GroupPosition.FIRST
    index == count - 1 -> GroupPosition.LAST
    else -> GroupPosition.MIDDLE
}

private fun groupShape(position: GroupPosition): RoundedCornerShape {
    val big = 24.dp
    val small = 6.dp
    return when (position) {
        GroupPosition.SINGLE -> RoundedCornerShape(big)
        GroupPosition.FIRST -> RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small)
        GroupPosition.MIDDLE -> RoundedCornerShape(small)
        GroupPosition.LAST -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big)
    }
}

/**
 * Library tab: the signed-in YTM library (playlists / albums / artists / liked songs)
 * plus the device's local files collapsed into a single synthetic "Local Songs"
 * playlist and the user's local playlists.
 *
 * Visual language: gradient hero cards for the two entry points (Local Songs, Liked),
 * grouped "card stacks" (large outer corners, small inner corners) for browsable rows,
 * and a shape-morphing segmented selector instead of filter chips.
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
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
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

            LibrarySectionSelector(
                current = currentSection,
                onSelect = { section = it.name },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                when (currentSection) {
                    LibrarySection.PLAYLISTS -> {
                        item(key = "local_songs", contentType = "hero") {
                            LibraryHeroCard(
                                title = "Local Songs",
                                subtitle = formatSongCount(localSongs.size),
                                gradient = listOf(
                                    colorScheme.tertiaryContainer,
                                    colorScheme.secondaryContainer,
                                ),
                                contentColor = colorScheme.onTertiaryContainer,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(30.dp),
                                    )
                                },
                                onClick = { navController.navigateSafely(Screen.LocalSongs.route) },
                            )
                        }
                        if (playlistUiState.playlists.isNotEmpty()) {
                            item(key = "local_pl_header", contentType = "header") {
                                LibrarySectionHeader("Your playlists")
                            }
                            items(
                                count = playlistUiState.playlists.size,
                                key = { "local_pl_${playlistUiState.playlists[it].id}" },
                                contentType = { "local_playlist" },
                            ) { index ->
                                LocalPlaylistRow(
                                    playlist = playlistUiState.playlists[index],
                                    playerViewModel = playerViewModel,
                                    position = positionFor(index, playlistUiState.playlists.size),
                                    onClick = {
                                        navController.navigateSafely(
                                            Screen.PlaylistDetail.createRoute(playlistUiState.playlists[index].id)
                                        )
                                    },
                                )
                            }
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
                                item(key = "liked_header", contentType = "hero") {
                                    LikedHeroCard(
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

/**
 * M3-expressive segmented selector: equal-width segments in one pill container; the
 * selected segment fills with primary and morphs from pill towards a rounded square.
 */
@Composable
private fun LibrarySectionSelector(
    current: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LibrarySection.entries.forEach { candidate ->
            val selected = candidate == current
            val container by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "sectionContainer",
            )
            val label by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "sectionLabel",
            )
            val corner by animateDpAsState(
                targetValue = if (selected) 14.dp else 24.dp,
                animationSpec = spring(),
                label = "sectionCorner",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(corner))
                    .background(container)
                    .clickable { onSelect(candidate) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = candidate.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = label,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Small overline header introducing a card stack. */
@Composable
private fun LibrarySectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, end = 24.dp, top = 14.dp, bottom = 6.dp),
    )
}

/** Full-width gradient entry card (Local Songs). */
@Composable
private fun LibraryHeroCard(
    title: String,
    subtitle: String,
    gradient: List<Color>,
    contentColor: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        shape = remember { ytSmoothShape(28.dp) },
        color = Color.Transparent,
        contentColor = contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .background(Brush.linearGradient(gradient))
                .clickable(onClick = onClick)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.14f),
                contentColor = contentColor,
                modifier = Modifier.size(60.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.75f),
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
            )
        }
    }
}

/** Liked Songs hero: gradient banner with play/shuffle actions. */
@Composable
private fun LikedHeroCard(
    songCount: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = remember { ytSmoothShape(28.dp) },
        color = Color.Transparent,
        contentColor = colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(colorScheme.primaryContainer, colorScheme.tertiaryContainer)
                    )
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
                    contentColor = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(60.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Liked Songs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = formatSongCount(songCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
                FilledTonalButton(
                    onClick = onShuffle,
                    modifier = Modifier.weight(1f),
                ) {
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
}

/** One row of a grouped card stack: 60dp art, bold title, positional corners. */
@Composable
private fun LibraryStackRow(
    title: String,
    subtitle: String?,
    position: GroupPosition,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Surface(
        shape = groupShape(position),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/** Adds one YTM library section's rows (sign-in gate → loading → error → empty → pages). */
private fun androidx.compose.foundation.lazy.LazyListScope.ytSectionItems(
    signedIn: Boolean,
    isLoading: Boolean,
    error: String?,
    pages: List<YtShelfEntry.Page>,
    emptyText: String,
    headerText: String?,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onOpen: (YtShelfEntry.Page) -> Unit,
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
                item(key = "yt_header", contentType = "header") {
                    LibrarySectionHeader(headerText)
                }
            }
            items(
                count = pages.size,
                key = { pages[it].key },
                contentType = { "yt_page_row" },
            ) { index ->
                val page = pages[index]
                val isArtist = page.kind == YtPageKind.ARTIST
                val kindLabel = when (page.kind) {
                    YtPageKind.ALBUM -> "Album"
                    YtPageKind.PLAYLIST -> "Playlist"
                    YtPageKind.ARTIST -> "Artist"
                    YtPageKind.PODCAST -> "Podcast"
                }
                LibraryStackRow(
                    title = page.title,
                    subtitle = page.subtitle?.let { "$kindLabel • $it" } ?: kindLabel,
                    position = positionFor(index, pages.size),
                    onClick = { onOpen(page) },
                    leading = {
                        SmartImage(
                            model = page.thumbnailUrl,
                            contentDescription = page.title,
                            shape = if (isArtist) CircleShape else remember { ytSmoothShape(14.dp) },
                            modifier = Modifier.size(60.dp),
                        )
                    },
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LocalPlaylistRow(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    position: GroupPosition,
    onClick: () -> Unit,
) {
    val playlistSongs by remember(playlist.songIds, playerViewModel) {
        playerViewModel.observeSongs(playlist.songIds)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    LibraryStackRow(
        title = playlist.name,
        subtitle = formatSongCount(playlist.songIds.size),
        position = position,
        onClick = onClick,
        leading = {
            PlaylistCover(
                playlist = playlist,
                playlistSongs = playlistSongs,
                size = 60.dp,
            )
        },
    )
}

@Composable
private fun LibrarySignInCard(onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = remember { ytSmoothShape(28.dp) },
        color = Color.Transparent,
        contentColor = colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(colorScheme.secondaryContainer, colorScheme.tertiaryContainer)
                    )
                )
                .clickable(onClick = onClick)
                .padding(20.dp),
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
                    color = colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
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
