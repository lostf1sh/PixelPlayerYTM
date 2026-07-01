package com.lostf1sh.pixelplayeross.data.innertube

import com.lostf1sh.pixelplayeross.data.innertube.model.YouTubeSong
import com.lostf1sh.pixelplayeross.data.model.ArtistRef
import com.lostf1sh.pixelplayeross.data.model.Song

/**
 * Converts parser-layer YouTube DTOs into the app's domain [Song].
 *
 * YTM tracks intentionally never touch the Room `songs` table (their ids are 11-char
 * strings, not the Long PKs the local library uses). They live purely in-memory: the
 * `ytm://<videoId>` content URI is all the playback pipeline needs, and `mimeType = null`
 * lets ExoPlayer sniff the container (opus/webm vs m4a) returned by the proxy.
 */
object InnerTubeMapper {

    const val URI_SCHEME = "ytm"

    fun videoIdToUri(videoId: String): String = "$URI_SCHEME://$videoId"

    fun toSong(source: YouTubeSong): Song {
        val artistRefs = source.artists.mapIndexed { index, ref ->
            ArtistRef(id = -1L, name = ref.name, isPrimary = index == 0)
        }
        return Song(
            id = source.videoId,
            title = source.title,
            artist = source.artistDisplay.ifBlank { source.artists.firstOrNull()?.name.orEmpty() },
            artistId = -1L,
            artists = artistRefs,
            album = source.album.orEmpty(),
            albumId = -1L,
            albumArtist = null,
            path = "",
            contentUriString = videoIdToUri(source.videoId),
            albumArtUriString = source.thumbnailUrl,
            duration = source.durationSeconds * 1000L,
            genre = null,
            lyrics = null,
            isFavorite = false,
            trackNumber = 0,
            discNumber = null,
            year = 0,
            dateAdded = 0,
            dateModified = 0,
            mimeType = null,
            bitrate = null,
            sampleRate = null,
        )
    }
}
