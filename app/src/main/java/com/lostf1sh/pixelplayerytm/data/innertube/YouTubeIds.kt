package com.lostf1sh.pixelplayerytm.data.innertube

/** Well-known InnerTube browse ids. */
object BrowseId {
    const val HOME = "FEmusic_home"
    const val EXPLORE = "FEmusic_explore"
    const val MOODS_AND_GENRES = "FEmusic_moods_and_genres"
    const val MOODS_AND_GENRES_CATEGORY = "FEmusic_moods_and_genres_category"
    const val NEW_RELEASES = "FEmusic_new_releases"
    const val CHARTS = "FEmusic_charts"

    // Library (requires auth)
    const val LIBRARY_PLAYLISTS = "FEmusic_liked_playlists"
    const val LIBRARY_SONGS = "FEmusic_liked_videos"
    const val LIBRARY_ALBUMS = "FEmusic_liked_albums"
    const val LIBRARY_ARTISTS = "FEmusic_library_corpus_track_artists"

    /** Liked songs auto-playlist. */
    const val LIKED_SONGS_PLAYLIST = "VLLM"

    fun playlist(playlistId: String): String =
        if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
}

/**
 * Search filter params for music.youtube.com search. These are protobuf
 * blobs and drift occasionally; if a filter returns odd results, refresh
 * them from ytmusicapi.
 */
enum class SearchFilter(val params: String?) {
    ALL(null),
    SONGS("EgWKAQIIAWoMEA4QChADEAQQCRAF"),
    VIDEOS("EgWKAQIQAWoMEA4QChADEAQQCRAF"),
    ALBUMS("EgWKAQIYAWoMEA4QChADEAQQCRAF"),
    ARTISTS("EgWKAQIgAWoMEA4QChADEAQQCRAF"),
    PLAYLISTS("EgWKAQIoAWoMEA4QChADEAQQCRAF"),
}
