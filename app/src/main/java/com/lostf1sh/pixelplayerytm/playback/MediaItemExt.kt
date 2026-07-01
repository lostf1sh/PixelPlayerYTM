package com.lostf1sh.pixelplayerytm.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lostf1sh.pixelplayerytm.domain.model.ArtistRef
import com.lostf1sh.pixelplayerytm.domain.model.SongItem

/** Custom scheme resolved at play time by [StreamResolvingDataSource]. */
const val YTM_SCHEME = "ytm"

private const val EXTRA_ARTIST_BROWSE_ID = "artistBrowseId"
private const val EXTRA_ALBUM_BROWSE_ID = "albumBrowseId"

fun SongItem.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        artists.firstOrNull()?.browseId?.let { putString(EXTRA_ARTIST_BROWSE_ID, it) }
        album?.browseId?.let { putString(EXTRA_ALBUM_BROWSE_ID, it) }
    }
    return MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(Uri.Builder().scheme(YTM_SCHEME).authority(videoId).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistNames.ifEmpty { null })
                .setAlbumTitle(album?.name)
                .setArtworkUri(thumbnailUrl?.toUri())
                .setExtras(extras)
                .build(),
        )
        .build()
}

fun MediaItem.toSongItem(): SongItem = SongItem(
    videoId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artists = mediaMetadata.artist?.toString()
        ?.split(", ")
        ?.map { ArtistRef(it, mediaMetadata.extras?.getString(EXTRA_ARTIST_BROWSE_ID)) }
        .orEmpty(),
    album = mediaMetadata.albumTitle?.toString()?.let {
        com.lostf1sh.pixelplayerytm.domain.model.AlbumRef(
            it,
            mediaMetadata.extras?.getString(EXTRA_ALBUM_BROWSE_ID),
        )
    },
    thumbnailUrl = mediaMetadata.artworkUri?.toString(),
)
