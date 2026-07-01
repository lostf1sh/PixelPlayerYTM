package com.lostf1sh.pixelplayerytm.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lostf1sh.pixelplayerytm.domain.model.SongItem
import com.lostf1sh.pixelplayerytm.ui.components.ItemActions
import com.lostf1sh.pixelplayerytm.ui.detail.AlbumScreen
import com.lostf1sh.pixelplayerytm.ui.detail.ArtistScreen
import com.lostf1sh.pixelplayerytm.ui.detail.BrowseScreen
import com.lostf1sh.pixelplayerytm.ui.detail.PlaylistScreen
import com.lostf1sh.pixelplayerytm.ui.explore.ExploreScreen
import com.lostf1sh.pixelplayerytm.ui.home.HomeScreen
import com.lostf1sh.pixelplayerytm.ui.library.LibraryScreen
import com.lostf1sh.pixelplayerytm.ui.login.LoginScreen
import com.lostf1sh.pixelplayerytm.ui.navigation.Routes
import com.lostf1sh.pixelplayerytm.ui.player.MiniPlayer
import com.lostf1sh.pixelplayerytm.ui.player.NowPlayingSheet
import com.lostf1sh.pixelplayerytm.ui.player.PlayerViewModel
import com.lostf1sh.pixelplayerytm.ui.search.SearchScreen
import com.lostf1sh.pixelplayerytm.ui.settings.SettingsScreen

private data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Routes.EXPLORE, "Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
    BottomNavItem(Routes.LIBRARY, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
)

@Composable
fun AppRoot(playerViewModel: PlayerViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    var showNowPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        playerViewModel.player.connect()
    }

    val actions = rememberItemActions(navController, playerViewModel)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                Column {
                    MiniPlayer(
                        viewModel = playerViewModel,
                        onExpand = { showNowPlaying = true },
                    )
                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                    )
                                },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp),
        ) {
            AppNavHost(
                navController = navController,
                actions = actions,
                playerViewModel = playerViewModel,
            )
            if (!showBottomBar) {
                MiniPlayer(
                    viewModel = playerViewModel,
                    onExpand = { showNowPlaying = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                )
            }
        }
    }

    if (showNowPlaying) {
        NowPlayingSheet(
            viewModel = playerViewModel,
            onDismiss = { showNowPlaying = false },
        )
    }
}

@Composable
private fun rememberItemActions(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
): ItemActions = remember(navController, playerViewModel) {
    ItemActions(
        onSong = { playerViewModel.player.playSongWithRadio(it) },
        onAlbum = { navController.navigate(Routes.album(it.browseId)) },
        onArtist = { navController.navigate(Routes.artist(it.browseId)) },
        onPlaylist = { navController.navigate(Routes.playlist(it.playlistId)) },
        onMood = { navController.navigate(Routes.browse(it.browseId, it.params, it.title)) },
    )
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    actions: ItemActions,
    playerViewModel: PlayerViewModel,
) {
    val onMoreClick: (String, String?, String) -> Unit = { browseId, params, title ->
        navController.navigate(Routes.browse(browseId, params, title))
    }
    val playAll: (List<SongItem>, Int, Boolean) -> Unit = { songs, startIndex, shuffle ->
        playerViewModel.player.playQueue(songs, startIndex)
        playerViewModel.player.setShuffle(shuffle)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                actions = actions,
                onSearchClick = { navController.navigate(Routes.SEARCH) },
                onAccountClick = { navController.navigate(Routes.SETTINGS) },
                onMoreClick = onMoreClick,
            )
        }
        composable(Routes.EXPLORE) {
            ExploreScreen(actions = actions, onMoreClick = onMoreClick)
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                actions = actions,
                onLoginClick = { navController.navigate(Routes.LOGIN) },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(actions = actions, onBack = { navController.popBackStack() })
        }
        composable(Routes.ALBUM) {
            AlbumScreen(
                actions = actions,
                onBack = { navController.popBackStack() },
                onPlayAll = playAll,
            )
        }
        composable(Routes.ARTIST) {
            ArtistScreen(
                actions = actions,
                onBack = { navController.popBackStack() },
                onPlayRadio = { playerViewModel.player.playRadio(it) },
                onShufflePlay = { playerViewModel.player.playRadio(it) },
                onMoreClick = onMoreClick,
            )
        }
        composable(Routes.PLAYLIST) {
            PlaylistScreen(
                onBack = { navController.popBackStack() },
                onPlayAll = playAll,
            )
        }
        composable(Routes.BROWSE) {
            BrowseScreen(
                actions = actions,
                onBack = { navController.popBackStack() },
                onMoreClick = onMoreClick,
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onLoggedIn = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLoginClick = { navController.navigate(Routes.LOGIN) },
            )
        }
    }
}
