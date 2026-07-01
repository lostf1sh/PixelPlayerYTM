package com.lostf1sh.pixelplayeross.data.innertube.model

/**
 * Parser-layer DTO for a YouTube Music track, decoupled from the app's [com.lostf1sh.pixelplayeross.data.model.Song].
 * `InnerTubeMapper` converts this into a `Song` with a `ytm://<videoId>` content URI.
 */
data class YouTubeSong(
    val videoId: String,
    val title: String,
    val artists: List<YouTubeArtistRef>,
    val album: String?,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val explicit: Boolean = false,
) {
    val artistDisplay: String get() = artists.joinToString(", ") { it.name }
}

/** A named artist reference; [browseId] (a `UC…` channel id) is null for plain-text artists. */
data class YouTubeArtistRef(
    val name: String,
    val browseId: String?,
)
