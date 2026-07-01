package com.lostf1sh.pixelplayerytm.data.innertube.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
    val context: Context,
    val query: String,
    val params: String? = null,
)

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String? = null,
    val params: String? = null,
    val continuation: String? = null,
)

@Serializable
data class NextBody(
    val context: Context,
    val videoId: String? = null,
    val playlistId: String? = null,
    val playlistSetVideoId: String? = null,
    val index: Int? = null,
    val params: String? = null,
    val continuation: String? = null,
)

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String? = null,
    val playbackContext: PlaybackContext? = null,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true,
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext,
    ) {
        @Serializable
        data class ContentPlaybackContext(
            val signatureTimestamp: Int,
        )
    }
}

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)

@Serializable
data class LikeBody(
    val context: Context,
    val target: Target,
) {
    @Serializable
    data class Target(
        val videoId: String? = null,
        val playlistId: String? = null,
    )
}

@Serializable
data class AccountMenuBody(
    val context: Context,
    val deviceTheme: String = "DEVICE_THEME_SELECTED",
    val userInterfaceTheme: String = "USER_INTERFACE_THEME_DARK",
)
