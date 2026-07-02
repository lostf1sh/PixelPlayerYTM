package com.lostf1sh.pixelplayeross.presentation.components.youtube

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YtTrackActionsViewModel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.data.model.YtPageKind
import com.lostf1sh.pixelplayeross.data.model.YtShelf
import com.lostf1sh.pixelplayeross.data.model.YtShelfEntry
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import com.lostf1sh.pixelplayeross.presentation.components.SmartImage
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.PlayingEqIcon
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Renderers for the server-driven YTM surfaces (home feed, explore, detail-page shelves).
 * One [YtShelfSection] per [YtShelf]; entries dispatch to track cards, page cards, or
 * mood chips. Playback and navigation stay with the caller via the on* lambdas.
 */

private val CardWidth = 148.dp

fun ytSmoothShape(radius: androidx.compose.ui.unit.Dp): Shape = AbsoluteSmoothCornerShape(
    cornerRadiusTL = radius, smoothnessAsPercentTL = 60,
    cornerRadiusTR = radius, smoothnessAsPercentTR = 60,
    cornerRadiusBR = radius, smoothnessAsPercentBR = 60,
    cornerRadiusBL = radius, smoothnessAsPercentBL = 60,
)

@Composable
fun YtShelfSection(
    shelf: YtShelf,
    onTrackClick: (YtTrack, YtShelf) -> Unit,
    onPageClick: (YtShelfEntry.Page) -> Unit,
    onCategoryClick: (YtShelfEntry.Category) -> Unit,
    currentSongId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onTrackLongPress: (YtTrack) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        shelf.subtitle?.let { strapline ->
            Text(
                text = strapline.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Text(
            text = shelf.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))

        val categories = remember(shelf) {
            shelf.entries.filterIsInstance<YtShelfEntry.Category>()
        }
        val tracks = remember(shelf) {
            shelf.entries.filterIsInstance<YtShelfEntry.Track>().map { it.track }
        }
        when {
            categories.size == shelf.entries.size && categories.isNotEmpty() -> FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    YtCategoryChip(category = category, onClick = { onCategoryClick(category) })
                }
            }

            // All-track shelves (Quick picks & friends) read best as the og-YTM layout:
            // a horizontal pager of 4-row columns — denser, and each row is one tap away.
            tracks.size == shelf.entries.size && tracks.size >= TRACK_GRID_ROWS -> YtTrackPagedGrid(
                tracks = tracks,
                currentSongId = currentSongId,
                isPlaying = isPlaying,
                onTrackClick = { onTrackClick(it, shelf) },
                onTrackLongPress = onTrackLongPress,
            )

            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shelf.entries, key = { it.key }) { entry ->
                    when (entry) {
                        is YtShelfEntry.Track -> YtTrackCard(
                            track = entry.track,
                            isCurrentSong = entry.track.videoId == currentSongId,
                            isPlaying = isPlaying,
                            onClick = { onTrackClick(entry.track, shelf) },
                            onLongClick = { onTrackLongPress(entry.track) },
                        )
                        is YtShelfEntry.Page -> YtPageCard(
                            page = entry,
                            onClick = { onPageClick(entry) },
                        )
                        is YtShelfEntry.Category -> YtCategoryChip(
                            category = entry,
                            onClick = { onCategoryClick(entry) },
                        )
                    }
                }
            }
        }
    }
}

private const val TRACK_GRID_ROWS = 4

/** og-YTM "Quick picks" layout: pages of [TRACK_GRID_ROWS] compact rows with a peek of the next page. */
@Composable
private fun YtTrackPagedGrid(
    tracks: List<YtTrack>,
    currentSongId: String?,
    isPlaying: Boolean,
    onTrackClick: (YtTrack) -> Unit,
    onTrackLongPress: (YtTrack) -> Unit,
) {
    val pages = remember(tracks) { tracks.chunked(TRACK_GRID_ROWS) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(start = 20.dp, end = 48.dp),
        pageSpacing = 12.dp,
        verticalAlignment = Alignment.Top,
    ) { pageIndex ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            pages[pageIndex].forEach { track ->
                YtTrackGridRow(
                    track = track,
                    isCurrentSong = track.videoId == currentSongId,
                    isPlaying = isPlaying,
                    onClick = { onTrackClick(track) },
                    onLongClick = { onTrackLongPress(track) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YtTrackGridRow(
    track: YtTrack,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = remember { ytSmoothShape(12.dp) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(remember { ytSmoothShape(16.dp) })
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        SmartImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            shape = shape,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val byline = remember(track) {
                track.artists.joinToString(", ") { it.name }.ifBlank { track.album.orEmpty() }
            }
            if (byline.isNotBlank()) {
                Text(
                    text = byline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isCurrentSong) {
            Spacer(Modifier.width(8.dp))
            PlayingEqIcon(
                modifier = Modifier.size(width = 18.dp, height = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                isPlaying = isPlaying,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YtTrackCard(
    track: YtTrack,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
) {
    val shape = remember { ytSmoothShape(20.dp) }
    Column(
        modifier = modifier
            .width(CardWidth)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            SmartImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                shape = shape,
                modifier = Modifier.size(CardWidth),
            )
            if (isCurrentSong) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) {
                    PlayingEqIcon(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(width = 18.dp, height = 16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        isPlaying = isPlaying,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val byline = remember(track) {
            track.artists.joinToString(", ") { it.name }.ifBlank { track.album.orEmpty() }
        }
        if (byline.isNotBlank()) {
            Text(
                text = byline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun YtPageCard(
    page: YtShelfEntry.Page,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isArtist = page.kind == YtPageKind.ARTIST
    val shape = if (isArtist) CircleShape else remember { ytSmoothShape(20.dp) }
    Column(
        modifier = modifier
            .width(CardWidth)
            .clickable(onClick = onClick),
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        SmartImage(
            model = page.thumbnailUrl,
            contentDescription = page.title,
            shape = shape,
            modifier = Modifier.size(CardWidth),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        )
        page.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

@Composable
fun YtCategoryChip(
    category: YtShelfEntry.Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** Row-style entry for search results and library lists that link to a page. */
@Composable
fun YtPageListRow(
    page: YtShelfEntry.Page,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isArtist = page.kind == YtPageKind.ARTIST
    val imageShape = if (isArtist) CircleShape else remember { ytSmoothShape(12.dp) }
    val kindLabel = when (page.kind) {
        YtPageKind.ALBUM -> "Album"
        YtPageKind.PLAYLIST -> "Playlist"
        YtPageKind.ARTIST -> "Artist"
        YtPageKind.PODCAST -> "Podcast"
    }
    ListItem(
        modifier = modifier
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = {
            SmartImage(
                model = page.thumbnailUrl,
                contentDescription = page.title,
                shape = imageShape,
                modifier = Modifier.size(52.dp),
            )
        },
        headlineContent = {
            Text(
                text = page.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = page.subtitle?.let { "$kindLabel • $it" } ?: kindLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YtLoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(
            modifier = Modifier.size(96.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun YtErrorBox(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onRetry) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

/** Options sheet content for a YTM track (queueing, radio, page navigation). */
@Composable
fun YtTrackOptionsSheetContent(
    track: YtTrack,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartRadio: () -> Unit,
    onGoToAlbum: (() -> Unit)?,
    onGoToArtist: (() -> Unit)?,
    onDismiss: () -> Unit = {},
) {
    val playlistActions: YtTrackActionsViewModel = hiltViewModel()
    val isSignedIn by playlistActions.isSignedIn.collectAsStateWithLifecycle()
    var pickingPlaylist by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmartImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (pickingPlaylist) {
            YtPlaylistPickerPane(
                track = track,
                viewModel = playlistActions,
                onDone = onDismiss,
            )
        } else {
            YtSheetAction("Play next", onPlayNext)
            YtSheetAction("Add to queue", onAddToQueue)
            YtSheetAction("Start radio", onStartRadio)
            if (isSignedIn) {
                YtSheetAction("Add to playlist") { pickingPlaylist = true }
            }
            YtDownloadAction(
                track = track,
                viewModel = playlistActions,
                onDismiss = onDismiss,
            )
            onGoToAlbum?.let { YtSheetAction("Go to album", it) }
            onGoToArtist?.let { YtSheetAction("Go to artist", it) }
        }
    }
}

/** Download / progress / remove-download row, driven by the download manager's flows. */
@Composable
private fun YtDownloadAction(
    track: YtTrack,
    viewModel: YtTrackActionsViewModel,
    onDismiss: () -> Unit,
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val progress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val inFlight = progress[track.videoId]
    when {
        inFlight != null -> YtSheetAction("Downloading… ${(inFlight * 100).toInt()}%") {}
        track.videoId in downloads -> YtSheetAction("Remove download") {
            viewModel.removeDownload(track.videoId)
            onDismiss()
        }
        else -> YtSheetAction("Download") {
            viewModel.download(track)
            onDismiss()
        }
    }
}

/**
 * Second pane of the options sheet: pick one of the account's playlists (or create a
 * new one) to add [track] to. Writes go through [YtTrackActionsViewModel]; the result
 * lands as a toast after [onDone] has closed the sheet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YtPlaylistPickerPane(
    track: YtTrack,
    viewModel: YtTrackActionsViewModel,
    onDone: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.loadPlaylists() }

    fun toastResult(ok: Boolean) {
        Toast.makeText(
            context,
            if (ok) "Added to playlist" else "Couldn't add to playlist",
            Toast.LENGTH_SHORT,
        ).show()
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Add to playlist",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        YtSheetAction("New playlist…") { showCreateDialog = true }
        when {
            playlists == null -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) { LoadingIndicator() }

            playlists.orEmpty().isEmpty() -> Text(
                text = "No playlists in your library yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )

            else -> playlists.orEmpty().forEach { playlist ->
                YtSheetAction(playlist.title) {
                    viewModel.addToPlaylist(playlist.browseId, track.videoId, ::toastResult)
                    onDone()
                }
            }
        }
    }

    if (showCreateDialog) {
        var title by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Playlist name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        viewModel.createPlaylistWith(title.trim(), track.videoId, ::toastResult)
                        showCreateDialog = false
                        onDone()
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun YtSheetAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    )
}
