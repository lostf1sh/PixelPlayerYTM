package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.lostf1sh.pixelplayeross.data.model.SortOption
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.components.LibrarySortBottomSheet
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.utils.formatSongCount
import com.lostf1sh.pixelplayeross.utils.formatTotalDuration
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * The synthetic "Local Songs" playlist: every audio file on the device as one flat,
 * playable list. This is the entire local-library surface of the app — the YTM catalog
 * is the primary source.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LocalSongsScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
) {
    val allSongs by playerViewModel.allSongsFlow.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    var sortOption by remember { mutableStateOf<SortOption>(SortOption.SongTitleAZ) }
    var showSortSheet by remember { mutableStateOf(false) }
    val songs = remember(allSongs, sortOption) { sortLocalSongs(allSongs, sortOption) }

    val bottomPadding = if (stablePlayerState.currentSong != null) MiniPlayerHeight else 0.dp
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Local Songs",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    if (songs.isNotEmpty()) {
                        Text(
                            text = "${formatSongCount(songs.size)} • ${formatTotalDuration(songs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = "Sort songs",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 38.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (songs.isNotEmpty()) {
                item(key = "actions", contentType = "actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                playerViewModel.playSongs(songs, songs.first(), "Local Songs")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            shape = AbsoluteSmoothCornerShape(
                                cornerRadiusTL = 60.dp,
                                smoothnessAsPercentTR = 60,
                                cornerRadiusTR = 14.dp,
                                smoothnessAsPercentTL = 60,
                                cornerRadiusBL = 60.dp,
                                smoothnessAsPercentBR = 60,
                                cornerRadiusBR = 14.dp,
                                smoothnessAsPercentBL = 60
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Play")
                        }
                        FilledTonalButton(
                            onClick = {
                                playerViewModel.playSongsShuffled(songs, "Local Songs", startAtZero = true)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            shape = AbsoluteSmoothCornerShape(
                                cornerRadiusTL = 14.dp,
                                smoothnessAsPercentTR = 60,
                                cornerRadiusTR = 60.dp,
                                smoothnessAsPercentTL = 60,
                                cornerRadiusBL = 14.dp,
                                smoothnessAsPercentBR = 60,
                                cornerRadiusBR = 60.dp,
                                smoothnessAsPercentBL = 60
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Shuffle")
                        }
                    }
                }
            }

            if (songs.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No audio files found on this device.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                    )
                }
            } else {
                items(
                    count = songs.size,
                    key = { "local_${songs[it].id}" },
                    contentType = { "local_song" },
                ) { index ->
                    val song = songs[index]
                    EnhancedSongListItem(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        song = song,
                        isPlaying = stablePlayerState.isPlaying,
                        isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                        showMoreOptionsButton = false,
                        onMoreOptionsClick = {},
                        onClick = {
                            playerViewModel.playSongs(songs, song, "Local Songs")
                        },
                    )
                }
            }
        }
    }

    if (showSortSheet) {
        LibrarySortBottomSheet(
            title = "Sort songs",
            options = LOCAL_SONG_SORT_OPTIONS,
            selectedOption = sortOption,
            onDismiss = { showSortSheet = false },
            onOptionSelected = { option ->
                sortOption = option
                showSortSheet = false
            },
            onDirectionToggle = { option -> sortOption = option },
        )
    }
}

private val LOCAL_SONG_SORT_OPTIONS = listOf(
    SortOption.SongTitleAZ,
    SortOption.SongTitleZA,
    SortOption.SongArtist,
    SortOption.SongArtistDesc,
    SortOption.SongAlbum,
    SortOption.SongAlbumDesc,
    SortOption.SongDateAdded,
    SortOption.SongDateAddedAsc,
    SortOption.SongDuration,
    SortOption.SongDurationAsc,
)

private fun sortLocalSongs(songs: List<Song>, sortOption: SortOption): List<Song> = when (sortOption) {
    SortOption.SongTitleZA -> songs.sortedWith(
        compareByDescending<Song> { it.title.lowercase() }.thenBy { it.artist.lowercase() }.thenBy { it.id }
    )
    SortOption.SongArtist -> songs.sortedWith(
        compareBy<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() }.thenBy { it.id }
    )
    SortOption.SongArtistDesc -> songs.sortedWith(
        compareByDescending<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() }.thenBy { it.id }
    )
    SortOption.SongAlbum -> songs.sortedWith(
        compareBy<Song> { it.album.lowercase() }.thenBy { it.trackNumber }.thenBy { it.id }
    )
    SortOption.SongAlbumDesc -> songs.sortedWith(
        compareByDescending<Song> { it.album.lowercase() }.thenBy { it.trackNumber }.thenBy { it.id }
    )
    SortOption.SongDateAdded -> songs.sortedWith(
        compareByDescending<Song> { it.dateAdded }.thenBy { it.title.lowercase() }.thenBy { it.id }
    )
    SortOption.SongDateAddedAsc -> songs.sortedWith(
        compareBy<Song> { it.dateAdded }.thenBy { it.title.lowercase() }.thenBy { it.id }
    )
    SortOption.SongDuration -> songs.sortedWith(
        compareByDescending<Song> { it.duration }.thenBy { it.title.lowercase() }.thenBy { it.id }
    )
    SortOption.SongDurationAsc -> songs.sortedWith(
        compareBy<Song> { it.duration }.thenBy { it.title.lowercase() }.thenBy { it.id }
    )
    else -> songs.sortedWith(
        compareBy<Song> { it.title.lowercase() }.thenBy { it.artist.lowercase() }.thenBy { it.id }
    )
}
