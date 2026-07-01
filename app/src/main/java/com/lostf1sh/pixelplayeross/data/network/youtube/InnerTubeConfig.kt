package com.lostf1sh.pixelplayeross.data.network.youtube

/**
 * The InnerTube client identity a request pretends to be. YouTube keys every
 * `youtubei/v1` call to a client, and different clients unlock different behaviour:
 * the YT Music web client is the canonical browse/search/library source, while the
 * Android Music and TV clients matter for `player` calls (M3) because they return
 * formats that need little or no cipher work.
 *
 * All values below are public, widely-known constants. They drift: when InnerTube
 * starts answering 400 or with empty bodies, refresh [version] (and the API keys)
 * from ytmusicapi / InnerTune.
 */
enum class InnerTubeClientId(
    val clientName: String,
    val version: String,
    val apiKey: String,
    /** Numeric id for the `X-YouTube-Client-Name` header. */
    val headerId: String,
    val userAgent: String,
    val referer: String?,
    val androidSdkVersion: Int?,
) {
    WEB_REMIX(
        clientName = "WEB_REMIX",
        version = "1.20241023.01.00",
        apiKey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
        headerId = "67",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        referer = "https://music.youtube.com/",
        androidSdkVersion = null,
    ),
    ANDROID_MUSIC(
        clientName = "ANDROID_MUSIC",
        version = "7.27.52",
        apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
        headerId = "21",
        userAgent = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip",
        referer = null,
        androidSdkVersion = 30,
    ),
    TVHTML5(
        clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
        version = "2.0",
        apiKey = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
        headerId = "85",
        userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/16.0 Safari/605.1.15",
        referer = "https://www.youtube.com/",
        androidSdkVersion = null,
    ),
}

/** Well-known `browse` endpoint ids. */
object YtBrowseIds {
    const val HOME = "FEmusic_home"
    const val MOODS_AND_GENRES = "FEmusic_moods_and_genres"
    const val NEW_RELEASES = "FEmusic_new_releases"
    const val CHARTS = "FEmusic_charts"

    // Library pages (require a signed-in request).
    const val LIBRARY_PLAYLISTS = "FEmusic_liked_playlists"
    const val LIBRARY_SONGS = "FEmusic_liked_videos"
    const val LIBRARY_ALBUMS = "FEmusic_liked_albums"
    const val LIBRARY_ARTISTS = "FEmusic_library_corpus_track_artists"

    /**
     * Playlist pages are browsed with a `VL` prefix on the plain `PL…`/`LM`/`RD…` id.
     */
    fun forPlaylist(playlistId: String): String =
        if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
}

/**
 * Search filter blobs (`params` on the `search` endpoint). Protobuf-encoded and
 * occasionally rotated by Google — refresh from ytmusicapi when a filter misbehaves.
 */
enum class YtSearchFilter(val params: String?) {
    ALL(null),
    SONGS("EgWKAQIIAWoMEA4QChADEAQQCRAF"),
    VIDEOS("EgWKAQIQAWoMEA4QChADEAQQCRAF"),
    ALBUMS("EgWKAQIYAWoMEA4QChADEAQQCRAF"),
    ARTISTS("EgWKAQIgAWoMEA4QChADEAQQCRAF"),
    PLAYLISTS("EgWKAQIoAWoMEA4QChADEAQQCRAF"),
}
