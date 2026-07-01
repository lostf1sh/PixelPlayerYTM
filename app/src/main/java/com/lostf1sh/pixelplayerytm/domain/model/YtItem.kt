package com.lostf1sh.pixelplayerytm.domain.model

/** Reference to an artist inside another item's metadata. */
data class ArtistRef(
    val name: String,
    val browseId: String? = null,
)

/** Reference to an album inside another item's metadata. */
data class AlbumRef(
    val name: String,
    val browseId: String? = null,
)

sealed interface YtItem {
    val id: String
    val title: String
    val thumbnailUrl: String?
}

data class SongItem(
    val videoId: String,
    override val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val album: AlbumRef? = null,
    val durationText: String? = null,
    override val thumbnailUrl: String? = null,
    val explicit: Boolean = false,
    val isVideo: Boolean = false,
) : YtItem {
    override val id: String get() = videoId

    val artistNames: String get() = artists.joinToString(", ") { it.name }
}

data class AlbumItem(
    val browseId: String,
    override val title: String,
    val artists: List<ArtistRef> = emptyList(),
    val year: String? = null,
    val playlistId: String? = null,
    override val thumbnailUrl: String? = null,
    val explicit: Boolean = false,
    val subtitle: String? = null,
) : YtItem {
    override val id: String get() = browseId
}

data class ArtistItem(
    val browseId: String,
    override val title: String,
    override val thumbnailUrl: String? = null,
    val subtitle: String? = null,
) : YtItem {
    override val id: String get() = browseId
}

data class PlaylistItem(
    val browseId: String,
    override val title: String,
    val author: String? = null,
    val songCountText: String? = null,
    override val thumbnailUrl: String? = null,
) : YtItem {
    override val id: String get() = browseId

    /** Raw playlist id without the VL browse prefix. */
    val playlistId: String get() = browseId.removePrefix("VL")
}

/** Colored mood/genre chip (moods & genres page, explore). */
data class MoodItem(
    override val title: String,
    val browseId: String,
    val params: String? = null,
    val stripeColor: Long? = null,
) : YtItem {
    override val id: String get() = "$browseId/$params"
    override val thumbnailUrl: String? get() = null
}
