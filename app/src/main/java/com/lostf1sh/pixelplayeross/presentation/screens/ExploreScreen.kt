package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lostf1sh.pixelplayeross.data.model.ShelfItem
import com.lostf1sh.pixelplayeross.presentation.components.home.ShelfRenderer
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.viewmodel.HomeFeedViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel

/** Explore: moods & genres (and any discovery shelves) from YouTube Music. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExploreScreen(
    navController: NavHostController,
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel,
    viewModel: HomeFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.explore.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadExploreIfNeeded() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "explore_header") {
                Text(
                    text = "Explore",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp),
                )
            }

            items(state.shelves, key = { it.id }, contentType = { "shelf" }) { shelf ->
                val shelfSongs = shelf.items
                    .filterIsInstance<ShelfItem.SongItem>()
                    .map { it.song }
                ShelfRenderer(
                    shelf = shelf,
                    onSongClick = { item ->
                        playerViewModel.showAndPlaySong(item.song, shelfSongs, shelf.title)
                    },
                    onBrowseClick = { item ->
                        navController.navigateSafely(Screen.YouTubeBrowse.createRoute(item.browseId))
                    },
                    onMoodClick = { item ->
                        navController.navigateSafely(Screen.YouTubeMood.createRoute(item.browseId, item.params))
                    },
                )
            }
        }

        when {
            state.isLoading && state.shelves.isEmpty() -> LoadingIndicator(
                modifier = Modifier.align(Alignment.Center),
            )

            state.isError && state.shelves.isEmpty() -> TextButton(
                onClick = { viewModel.loadExploreIfNeeded() },
                modifier = Modifier.align(Alignment.Center),
            ) { Text("Retry") }
        }
    }
}
