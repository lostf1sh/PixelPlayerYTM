package com.lostf1sh.pixelplayerytm.ui.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val LOGIN = "login"
    const val SETTINGS = "settings"

    const val ALBUM = "album/{browseId}"
    const val ARTIST = "artist/{browseId}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val BROWSE = "browse/{browseId}?params={params}&title={title}"

    fun album(browseId: String) = "album/${Uri.encode(browseId)}"
    fun artist(browseId: String) = "artist/${Uri.encode(browseId)}"
    fun playlist(playlistId: String) = "playlist/${Uri.encode(playlistId)}"
    fun browse(browseId: String, params: String? = null, title: String? = null) =
        "browse/${Uri.encode(browseId)}?params=${Uri.encode(params.orEmpty())}&title=${Uri.encode(title.orEmpty())}"
}
