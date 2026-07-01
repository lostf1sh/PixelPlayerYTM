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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.lostf1sh.pixelplayeross.data.model.BrowseKind
import com.lostf1sh.pixelplayeross.data.model.ShelfItem
import com.lostf1sh.pixelplayeross.presentation.navigation.Screen
import com.lostf1sh.pixelplayeross.presentation.navigation.navigateSafely
import com.lostf1sh.pixelplayeross.presentation.viewmodel.PlayerViewModel
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YouTubeLibraryTab
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YouTubeLibraryViewModel

/**
 * The main Library tab, customised for YouTube Music: Playlists / Songs / Albums / Artists
 * pulled from the signed-in account, plus a Local tab that hosts the on-device library.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YouTubeMusicLibraryScreen(
    navController: NavHostController,
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel,
    viewModel: YouTubeLibraryViewModel = hiltViewModel(),
) {
    val loggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val remoteTabs = remember { YouTubeLibraryTab.entries.toList() }
    val tabCount = remoteTabs.size + 1 // + Local
    val localTabIndex = remoteTabs.size

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Load the selected remote tab when it becomes visible / login changes.
    LaunchedEffect(selectedTab, loggedIn) {
        if (loggedIn && selectedTab < remoteTabs.size) {
            viewModel.loadTab(remoteTabs[selectedTab])
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = paddingValues.calculateTopPadding()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (loggedIn) {
                TextButton(onClick = { viewModel.signOut() }) { Text("Sign out") }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp,
        ) {
            remoteTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(tab.label) },
                )
            }
            Tab(
                selected = selectedTab == localTabIndex,
                onClick = { selectedTab = localTabIndex },
                text = { Text("Local") },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                selectedTab == localTabIndex -> {
                    // On-device library.
                    LibraryScreen(navController = navController, playerViewModel = playerViewModel)
                }
                !loggedIn -> SignInPrompt(onSignIn = { navController.navigateSafely(Screen.YouTubeLogin.route) })
                else -> RemoteTabContent(
                    tab = remoteTabs[selectedTab],
                    viewModel = viewModel,
                    playerViewModel = playerViewModel,
                    navController = navController,
                    bottomInset = paddingValues.calculateBottomPadding(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoteTabContent(
    tab: YouTubeLibraryTab,
    viewModel: YouTubeLibraryViewModel,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val state by viewModel.tabState(tab).collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && !state.loaded -> LoadingIndicator(modifier = Modifier.align(Alignment.Center))
            state.isEmpty -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Nothing here yet", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { viewModel.loadTab(tab, force = true) }) { Text("Refresh") }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.songs, key = { "s_${it.id}" }) { song ->
                    LibraryEntryRow(
                        thumbnailUrl = song.albumArtUriString,
                        title = song.title,
                        subtitle = song.displayArtist,
                        circular = false,
                        onClick = { playerViewModel.showAndPlaySong(song, state.songs, tab.label) },
                    )
                }
                items(state.items, key = { "b_${it.browseId}" }) { item ->
                    LibraryEntryRow(
                        thumbnailUrl = item.thumbnailUrl,
                        title = item.title,
                        subtitle = item.subtitle,
                        circular = item.kind == BrowseKind.ARTIST,
                        onClick = { navController.navigateSafely(Screen.YouTubeBrowse.createRoute(item.browseId)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInPrompt(onSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sign in to YouTube Music",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "See your playlists, liked songs, albums and artists.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSignIn) { Text("Sign in with Google") }
    }
}

@Composable
private fun LibraryEntryRow(
    thumbnailUrl: String?,
    title: String,
    subtitle: String?,
    circular: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = title,
            modifier = Modifier
                .size(52.dp)
                .clip(if (circular) CircleShape else RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
