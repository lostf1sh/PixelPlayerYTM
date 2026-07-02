package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.lostf1sh.pixelplayeross.data.network.youtube.YtSearchFilter
import com.lostf1sh.pixelplayeross.presentation.components.MiniPlayerHeight
import com.lostf1sh.pixelplayeross.presentation.components.resolveMainScreenBottomGradientHeight
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtErrorBox
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtLoadingBox
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtPageListRow
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtShelfSection
import com.lostf1sh.pixelplayeross.presentation.components.youtube.YtTrackOptionsSheetContent
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YtRadioViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YtSearchViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search tab: debounced YouTube Music search with filter chips. While the query is
 * blank it doubles as Explore — the YTM moods & genres chip grid.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit = {}
) {
    val searchViewModel: YtSearchViewModel = hiltViewModel()
    val radioViewModel: YtRadioViewModel = hiltViewModel()
    val uiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode)
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchInputFocusRequester = remember { FocusRequester() }

    var trackForOptions by remember { mutableStateOf<YtTrack?>(null) }

    LaunchedEffect(Unit) {
        onSearchBarActiveChange(false)
    }

    LaunchedEffect(playerViewModel, keyboardController) {
        playerViewModel.searchNavDoubleTapEvents.collect {
            delay(40L)
            searchInputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onSearchBarActiveChange(false)
        }
    }

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
                    .padding(start = 24.dp, top = statusBarTopInset + 12.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val searchBarInputFieldColors = SearchBarDefaults.inputFieldColors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )

                Box(
                    Modifier
                        .weight(1f)
                        .background(color = Color.Transparent)
                ) {
                    DockedSearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                modifier = Modifier.focusRequester(searchInputFocusRequester),
                                query = uiState.query,
                                onQueryChange = searchViewModel::onQueryChange,
                                onSearch = { keyboardController?.hide() },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = {
                                    Text(
                                        stringResource(R.string.search_placeholder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = stringResource(R.string.cd_search_icon),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (uiState.query.isNotBlank()) {
                                        IconButton(
                                            onClick = { searchViewModel.onQueryChange("") },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.cd_clear_search_query),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                colors = searchBarInputFieldColors
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier.clip(RoundedCornerShape(28.dp)),
                        colors = SearchBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            inputFieldColors = searchBarInputFieldColors
                        ),
                        content = {}
                    )
                }

                FilledIconButton(
                    modifier = Modifier.padding(bottom = 2.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    onClick = { navController.navigateSafely(Screen.Settings.route) }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_settings_24),
                        contentDescription = stringResource(R.string.presentation_batch_d_open_settings_cd)
                    )
                }
            }

            val showExplore by remember { derivedStateOf { uiState.query.isBlank() } }
            AnimatedContent(
                targetState = showExplore,
                transitionSpec = {
                    val switchingToExplore = targetState
                    val enter = fadeIn(animationSpec = tween(durationMillis = 320, delayMillis = 70)) +
                        slideInVertically(animationSpec = tween(durationMillis = 320)) { fullHeight ->
                            if (switchingToExplore) -fullHeight / 10 else fullHeight / 10
                        }
                    val exit = fadeOut(animationSpec = tween(durationMillis = 220)) +
                        slideOutVertically(animationSpec = tween(durationMillis = 220)) { fullHeight ->
                            if (switchingToExplore) fullHeight / 12 else -fullHeight / 12
                        }
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "search_mode_transition"
            ) { isExploreMode ->
                if (isExploreMode) {
                    ExploreMoods(
                        uiState = uiState,
                        onRetry = searchViewModel::loadMoods,
                        onCategoryClick = { category ->
                            navController.navigateSafely(
                                Screen.YtMood.createRoute(category.browseId, category.params, category.title)
                            )
                        },
                        currentSongId = stablePlayerState.currentSong?.id,
                        isPlaying = stablePlayerState.isPlaying,
                        bottomPadding = paddingValues.calculateBottomPadding(),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            YtSearchFilter.entries.forEach { filter ->
                                YtSearchFilterChip(
                                    filter = filter,
                                    selected = filter == uiState.filter,
                                    onClick = { searchViewModel.onFilterChange(filter) },
                                )
                            }
                        }
                        SearchResultsContent(
                            uiState = uiState,
                            onLoadMore = searchViewModel::loadMore,
                            playerViewModel = playerViewModel,
                            navController = navController,
                            onTrackMoreOptions = { trackForOptions = it },
                            currentSongId = stablePlayerState.currentSong?.id,
                            isPlaying = stablePlayerState.isPlaying,
                        )
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

@Composable
private fun ExploreMoods(
    uiState: YtSearchViewModel.UiState,
    onRetry: () -> Unit,
    onCategoryClick: (YtShelfEntry.Category) -> Unit,
    currentSongId: String?,
    isPlaying: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    when {
        uiState.isLoadingMoods -> YtLoadingBox(modifier = Modifier.padding(top = 24.dp))

        uiState.moodsError != null -> YtErrorBox(
            message = uiState.moodsError,
            onRetry = onRetry,
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 20.dp,
                bottom = bottomPadding + MiniPlayerHeight + 38.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(
                count = uiState.moodShelves.size,
                key = { uiState.moodShelves[it].id },
                contentType = { "mood_shelf" },
            ) { index ->
                YtShelfSection(
                    shelf = uiState.moodShelves[index],
                    onTrackClick = { _, _ -> },
                    onPageClick = {},
                    onCategoryClick = onCategoryClick,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SearchResultsContent(
    uiState: YtSearchViewModel.UiState,
    onLoadMore: () -> Unit,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    onTrackMoreOptions: (YtTrack) -> Unit,
    currentSongId: String?,
    isPlaying: Boolean,
) {
    val localDensity = LocalDensity.current
    val results = uiState.results

    // One queue of every track in the result list, so tapping a song keeps playing
    // through the rest of the results.
    val resultSongs = remember(results) {
        results.filterIsInstance<YtShelfEntry.Track>().map { it.track.toSong() }
    }
    val queueName = remember(uiState.query) {
        uiState.query.trim().takeIf { it.isNotEmpty() }?.let { "Search: $it" } ?: "Search Results"
    }

    val listState: LazyListState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    when {
        uiState.isSearching && results.isEmpty() -> YtLoadingBox(modifier = Modifier.padding(top = 24.dp))

        uiState.error != null && results.isEmpty() -> Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = uiState.error,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(32.dp),
            )
        }

        results.isEmpty() -> EmptySearchResults(uiState.query)

        else -> {
            val imePadding = WindowInsets.ime.getBottom(localDensity).dp
            val systemBarPaddingBottom =
                WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = if (imePadding <= 8.dp) (MiniPlayerHeight + systemBarPaddingBottom) else imePadding
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = results.size,
                    key = { results[it].key },
                    contentType = { if (results[it] is YtShelfEntry.Track) "yt_track" else "yt_page" },
                ) { index ->
                    when (val entry = results[index]) {
                        is YtShelfEntry.Track -> {
                            val song = remember(entry) { entry.track.toSong() }
                            EnhancedSongListItem(
                                song = song,
                                isPlaying = isPlaying,
                                isCurrentSong = currentSongId == entry.track.videoId,
                                onLongPress = { onTrackMoreOptions(entry.track) },
                                onMoreOptionsClick = { onTrackMoreOptions(entry.track) },
                                onClick = {
                                    val queue = resultSongs.ifEmpty { listOf(song) }
                                    val start = queue.firstOrNull { it.id == song.id } ?: song
                                    playerViewModel.playSongs(queue, start, queueName)
                                }
                            )
                        }

                        is YtShelfEntry.Page -> YtPageListRow(
                            page = entry,
                            onClick = {
                                navController.navigateSafely(
                                    Screen.YtPage.createRoute(entry.kind, entry.browseId)
                                )
                            },
                        )

                        is YtShelfEntry.Category -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchResults(searchQuery: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.cd_no_search_results),
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Text(
            text = if (searchQuery.isNotBlank()) {
                stringResource(R.string.search_no_results_for_query, searchQuery)
            } else {
                stringResource(R.string.search_nothing_found)
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.search_try_different_or_filters),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun YtSearchFilterChip(
    filter: YtSearchFilter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(filter.name.lowercase().replaceFirstChar { it.titlecase() }) },
        modifier = modifier,
        shape = CircleShape,
        border = BorderStroke(width = 0.dp, color = Color.Transparent),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        leadingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(R.drawable.rounded_check_circle_24),
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        }
    )
}
