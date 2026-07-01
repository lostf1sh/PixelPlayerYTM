package com.lostf1sh.pixelplayeross.data.model

import androidx.compose.runtime.Immutable

/** URI scheme carried by [Song.contentUriString] for YouTube Music tracks. */
const val YTM_URI_SCHEME = "ytm"

fun ytmUriFor(videoId: String): String = "$YTM_URI_SCHEME://$videoId"

fun Song.isYouTubeSong(): Boolean = contentUriString.startsWith("$YTM_URI_SCHEME://")

/** The 11-char video id of a YTM song, or null for local tracks. */
fun Song.youtubeVideoId(): String? =
    contentUriString.takeIf { it.startsWith("$YTM_URI_SCHEME://") }
        ?.removePrefix("$YTM_URI_SCHEME://")

/** An artist name as it appears on a YTM row, optionally linked to a `UC…` channel. */
@Immutable
data class YtArtistLink(
    val name: String,
    val channelId: String? = null,
)

/**
 * A playable YouTube Music track as parsed off the wire. Converted to the app-wide
 * [Song] via [toSong]; YTM tracks stay out of the Room `songs` table (string video ids
 * don't fit its Long keys) and live in-memory behind their `ytm://` URI.
 */
@Immutable
data class YtTrack(
    val videoId: String,
    val title: String,
    val artists: List<YtArtistLink>,
    val album: String? = null,
    val albumBrowseId: String? = null,
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    val explicit: Boolean = false,
)

fun YtTrack.toSong(): Song = Song(
    id = videoId,
    title = title,
    artist = artists.firstOrNull()?.name.orEmpty(),
    artistId = -1L,
    artists = artists.mapIndexed { i, a -> ArtistRef(id = -1L, name = a.name, isPrimary = i == 0) },
    album = album.orEmpty(),
    albumId = -1L,
    albumArtist = null,
    path = "",
    contentUriString = ytmUriFor(videoId),
    albumArtUriString = thumbnailUrl,
    duration = durationMs,
    genre = null,
    lyrics = null,
    isFavorite = false,
    trackNumber = 0,
    discNumber = null,
    year = 0,
    dateAdded = 0,
    dateModified = 0,
    // Left null so ExoPlayer sniffs whatever container (m4a/webm) the resolver picks.
    mimeType = null,
    bitrate = null,
    sampleRate = null,
)

/** What a non-track card links to. */
enum class YtPageKind { ALBUM, PLAYLIST, ARTIST, PODCAST }

/** One entry inside a [YtShelf]. */
@Immutable
sealed interface YtShelfEntry {
    /** Stable Compose list key. */
    val key: String

    @Immutable
    data class Track(val track: YtTrack) : YtShelfEntry {
        override val key: String get() = "t_${track.videoId}"
    }

    /** A card that opens an album/playlist/artist/podcast page. */
    @Immutable
    data class Page(
        val browseId: String,
        val kind: YtPageKind,
        val title: String,
        val subtitle: String? = null,
        val thumbnailUrl: String? = null,
    ) : YtShelfEntry {
        override val key: String get() = "p_$browseId"
    }

    /** A mood/genre chip from the Explore grid. */
    @Immutable
    data class Category(
        val browseId: String,
        val params: String?,
        val title: String,
    ) : YtShelfEntry {
        override val key: String get() = "c_${browseId}_${params.orEmpty()}"
    }
}

/** A titled horizontal (or grid) section of a server-driven page. */
@Immutable
data class YtShelf(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val entries: List<YtShelfEntry>,
)

/** A page of the home/explore feed plus the token for the next page. */
@Immutable
data class YtFeedPage(
    val shelves: List<YtShelf>,
    val continuation: String? = null,
)

/** A page of search results. */
@Immutable
data class YtSearchPage(
    val tracks: List<YtTrack>,
    val continuation: String? = null,
)

/** A page of a radio / up-next queue from the `next` endpoint. */
@Immutable
data class YtRadioPage(
    val tracks: List<YtTrack>,
    val continuation: String? = null,
)

/** An album/playlist/artist detail page. */
@Immutable
data class YtBrowsePage(
    val title: String,
    val subtitle: String? = null,
    val heroImageUrl: String? = null,
    val tracks: List<YtTrack> = emptyList(),
    /** Secondary sections (an artist's albums, related artists, …). */
    val shelves: List<YtShelf> = emptyList(),
)
