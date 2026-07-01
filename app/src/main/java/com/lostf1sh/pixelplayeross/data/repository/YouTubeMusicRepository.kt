package com.lostf1sh.pixelplayeross.data.repository

import com.lostf1sh.pixelplayeross.data.innertube.parser.BrowsePage
import com.lostf1sh.pixelplayeross.data.innertube.parser.HomeFeedPage
import com.lostf1sh.pixelplayeross.data.innertube.parser.NextPage
import com.lostf1sh.pixelplayeross.data.model.HomeShelf
import com.lostf1sh.pixelplayeross.data.model.ShelfItem
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.youtube.stream.AudioQuality

/** A page of YTM search results already mapped to domain [Song]s. */
data class YouTubeSearchResult(
    val songs: List<Song>,
    val continuation: String?,
)

/**
 * Top-level facade for YouTube Music. Kept separate from [MusicRepository] (the local /
 * MediaStore facade) exactly as the Navidrome/Jellyfin repositories were — YTM is the new
 * primary source but its data flow (suspend/paged) is different from the Room-backed local one.
 */
interface YouTubeMusicRepository {
    suspend fun search(query: String, continuation: String? = null): YouTubeSearchResult

    suspend fun getSearchSuggestions(input: String): List<String>

    /** Resolve a directly-streamable audio URL for the proxy. Null if unplayable. */
    suspend fun resolveStreamUrl(videoId: String, quality: AudioQuality = AudioQuality.MEDIUM): String?

    /** The home feed (`FEmusic_home`), paginated via [continuation]. */
    suspend fun getHomeFeed(continuation: String? = null): HomeFeedPage

    /** Moods & genres grid. */
    suspend fun getMoodsAndGenres(): List<HomeShelf>

    /** A specific mood/genre category. */
    suspend fun getMoodCategory(browseId: String, params: String?): HomeFeedPage

    /** An album/playlist/artist page. */
    suspend fun getBrowsePage(browseId: String): BrowsePage?

    /** First page of a radio/up-next queue for [videoId]. */
    suspend fun getWatchQueue(
        videoId: String,
        playlistId: String? = null,
        continuation: String? = null,
    ): NextPage

    /** Liked songs (authenticated). */
    suspend fun getLikedSongs(): List<Song>

    /** Saved/created playlists (authenticated). */
    suspend fun getLibraryPlaylists(): List<ShelfItem.BrowseItem>

    /** Saved albums (authenticated). */
    suspend fun getLibraryAlbums(): List<ShelfItem.BrowseItem>

    /** Artists in your library (authenticated). */
    suspend fun getLibraryArtists(): List<ShelfItem.BrowseItem>

    /** Like or un-like a track (authenticated). */
    suspend fun setLike(videoId: String, liked: Boolean)
}
