package com.lostf1sh.pixelplayeross.data.youtube

import com.lostf1sh.pixelplayeross.data.model.YtBrowsePage
import com.lostf1sh.pixelplayeross.data.model.YtFeedPage
import com.lostf1sh.pixelplayeross.data.model.YtRadioPage
import com.lostf1sh.pixelplayeross.data.model.YtSearchPage
import com.lostf1sh.pixelplayeross.data.model.YtShelf
import com.lostf1sh.pixelplayeross.data.model.YtShelfEntry
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import com.lostf1sh.pixelplayeross.data.network.youtube.InnerTubeService
import com.lostf1sh.pixelplayeross.data.network.youtube.YouTubeResponseParser
import com.lostf1sh.pixelplayeross.data.network.youtube.YtBrowseIds
import com.lostf1sh.pixelplayeross.data.network.youtube.YtSearchFilter
import com.lostf1sh.pixelplayeross.data.network.youtube.auth.YtAccountStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's YouTube Music data source: search, home/explore feed, album/playlist/artist
 * pages, radio, and the signed-in user's library. Methods perform network I/O and throw
 * ([java.io.IOException] / [InnerTubeService.YtApiException]) on failure — callers wrap
 * with `runCatching` and decide presentation.
 *
 * Stream-URL resolution intentionally lives elsewhere (data/stream, M3): this class is
 * about catalog data, not playback.
 */
@Singleton
class YouTubeRepository @Inject constructor(
    private val innerTube: InnerTubeService,
    private val accountStore: YtAccountStore,
) {

    /** Whether library endpoints will return the user's data rather than a sign-in wall. */
    val isSignedIn: StateFlow<Boolean> get() = accountStore.isSignedIn

    // ─────────────────────────── Search ───────────────────────────

    suspend fun search(query: String, filter: YtSearchFilter = YtSearchFilter.SONGS): YtSearchPage =
        YouTubeResponseParser.searchPage(
            innerTube.call("search") {
                put("query", query)
                filter.params?.let { put("params", it) }
            }
        )

    suspend fun searchMore(continuation: String): YtSearchPage =
        YouTubeResponseParser.searchPage(
            innerTube.call("search") { put("continuation", continuation) }
        )

    suspend fun searchSuggestions(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        return YouTubeResponseParser.searchSuggestions(
            innerTube.call("music/get_search_suggestions") { put("input", input) }
        )
    }

    // ──────────────────────── Home / Explore ────────────────────────

    suspend fun homeFeed(continuation: String? = null): YtFeedPage =
        YouTubeResponseParser.feedPage(
            innerTube.call("browse") {
                if (continuation != null) put("continuation", continuation)
                else put("browseId", YtBrowseIds.HOME)
            }
        )

    suspend fun moodsAndGenres(): List<YtShelf> =
        YouTubeResponseParser.moodShelves(
            innerTube.call("browse") { put("browseId", YtBrowseIds.MOODS_AND_GENRES) }
        )

    /** The shelf page behind one mood/genre chip. */
    suspend fun moodCategory(browseId: String, params: String?): YtFeedPage =
        YouTubeResponseParser.feedPage(
            innerTube.call("browse") {
                put("browseId", browseId)
                params?.let { put("params", it) }
            }
        )

    // ───────────────────── Album / playlist / artist ─────────────────────

    /** Album, artist, or podcast page by browseId. For playlists prefer [playlistPage]. */
    suspend fun page(browseId: String): YtBrowsePage =
        YouTubeResponseParser.browsePage(
            innerTube.call("browse") { put("browseId", browseId) }
        )

    suspend fun playlistPage(playlistId: String): YtBrowsePage =
        page(YtBrowseIds.forPlaylist(playlistId))

    // ─────────────────────────── Radio ───────────────────────────

    /** Start an endless radio seeded by [videoId]. */
    suspend fun radioFor(videoId: String): YtRadioPage =
        YouTubeResponseParser.radioPage(
            innerTube.call("next") {
                put("videoId", videoId)
                put("playlistId", "$RADIO_PLAYLIST_PREFIX$videoId")
                put("params", RADIO_PARAMS)
            }
        )

    suspend fun radioMore(continuation: String): YtRadioPage =
        YouTubeResponseParser.radioPage(
            innerTube.call("next") { put("continuation", continuation) }
        )

    // ─────────────────────────── Library ───────────────────────────

    suspend fun libraryPlaylists(): List<YtShelfEntry.Page> = libraryPages(YtBrowseIds.LIBRARY_PLAYLISTS)

    suspend fun libraryAlbums(): List<YtShelfEntry.Page> = libraryPages(YtBrowseIds.LIBRARY_ALBUMS)

    suspend fun libraryArtists(): List<YtShelfEntry.Page> = libraryPages(YtBrowseIds.LIBRARY_ARTISTS)

    suspend fun likedSongs(): List<YtTrack> =
        YouTubeResponseParser.libraryEntries(
            innerTube.call("browse") { put("browseId", YtBrowseIds.LIBRARY_SONGS) }
        ).filterIsInstance<YtShelfEntry.Track>().map { it.track }

    private suspend fun libraryPages(browseId: String): List<YtShelfEntry.Page> =
        YouTubeResponseParser.libraryEntries(
            innerTube.call("browse") { put("browseId", browseId) }
        ).filterIsInstance<YtShelfEntry.Page>()

    // ─────────────────────────── Actions ───────────────────────────

    suspend fun setLiked(videoId: String, liked: Boolean) {
        innerTube.call(if (liked) "like/like" else "like/removelike") {
            putJsonObject("target") { put("videoId", videoId) }
        }
    }

    private companion object {
        /** `RDAMVM<videoId>` is the auto-generated radio playlist for a video. */
        const val RADIO_PLAYLIST_PREFIX = "RDAMVM"

        /** Params InnerTube expects alongside a radio `next` call. */
        const val RADIO_PARAMS = "wAEB"
    }
}
