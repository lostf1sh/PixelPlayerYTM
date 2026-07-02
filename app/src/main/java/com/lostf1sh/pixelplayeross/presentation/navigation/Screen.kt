package com.lostf1sh.pixelplayeross.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.lostf1sh.pixelplayeross.data.model.YtPageKind


@Immutable
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Settings : Screen("settings")
    object SettingsCategory : Screen("settings_category/{categoryId}") {
        fun createRoute(categoryId: String) = "settings_category/$categoryId"
    }
    object PaletteStyle : Screen("palette_style_settings")
    object Experimental : Screen("experimental_settings")
    object NavBarCrRad : Screen("nav_bar_corner_radius")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
    }

    object  DailyMixScreen : Screen("daily_mix")
    object RecentlyPlayed : Screen("recently_played")
    object Stats : Screen("stats")
    object GenreDetail : Screen("genre_detail/{genreId}") { // New screen
        fun createRoute(genreId: String) = "genre_detail/$genreId"
    }
    object DJSpace : Screen("dj_space")
    // The base route is "album_detail". The full route with the argument is defined in AppNavigation.
    object AlbumDetail : Screen("album_detail/{albumId}") {
        // Helper function to build the navigation route with the album ID.
        fun createRoute(albumId: Long) = "album_detail/$albumId"
    }

    object ArtistDetail : Screen("artist_detail/{artistId}") {
        fun createRoute(artistId: Long) = "artist_detail/$artistId"
    }

    object EditTransition : Screen("edit_transition?playlistId={playlistId}") {
        fun createRoute(playlistId: String?) =
            if (playlistId != null) "edit_transition?playlistId=$playlistId" else "edit_transition"
    }

    /** Generic YTM detail page — album, playlist, artist, or podcast by browseId. */
    object YtPage : Screen("yt_page/{kind}/{browseId}") {
        fun createRoute(kind: YtPageKind, browseId: String) =
            "yt_page/${kind.name}/${Uri.encode(browseId)}"
    }

    /** Shelf feed behind one Explore mood/genre chip. */
    object YtMood : Screen("yt_mood/{browseId}?params={params}&title={title}") {
        fun createRoute(browseId: String, params: String?, title: String) =
            "yt_mood/${Uri.encode(browseId)}?params=${Uri.encode(params.orEmpty())}&title=${Uri.encode(title)}"
    }

    /** Google sign-in via the TV device-code flow. */
    object YtLogin : Screen("yt_login")

    /** The synthetic "Local Songs" playlist — all on-device audio as one flat list. */
    object LocalSongs : Screen("local_songs")

    /** Offline YTM downloads. */
    object YtDownloads : Screen("yt_downloads")

    object About : Screen("about")
    object EasterEgg : Screen("easter_egg")

    object ArtistSettings : Screen("artist_settings")
    object DelimiterConfig : Screen("delimiter_config")
    object WordDelimiterConfig : Screen("word_delimiter_config")
    object Equalizer : Screen("equalizer")
    object DeviceCapabilities : Screen("device_capabilities")
}
