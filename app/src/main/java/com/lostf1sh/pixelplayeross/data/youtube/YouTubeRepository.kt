package com.lostf1sh.pixelplayeross.data.youtube

import com.lostf1sh.pixelplayeross.data.model.Lyrics
import com.lostf1sh.pixelplayeross.data.model.SyncedLine
import com.lostf1sh.pixelplayeross.data.model.YtAccountInfo
import com.lostf1sh.pixelplayeross.data.model.YtBrowsePage
import com.lostf1sh.pixelplayeross.data.model.YtFeedPage
import com.lostf1sh.pixelplayeross.data.model.YtRadioPage
import com.lostf1sh.pixelplayeross.data.model.YtSearchPage
import com.lostf1sh.pixelplayeross.data.model.YtShelf
import com.lostf1sh.pixelplayeross.data.model.YtShelfEntry
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import com.lostf1sh.pixelplayeross.data.network.youtube.InnerTubeClientId
import com.lostf1sh.pixelplayeross.data.network.youtube.InnerTubeService
import com.lostf1sh.pixelplayeross.data.network.youtube.YouTubeResponseParser
import com.lostf1sh.pixelplayeross.data.network.youtube.YtBrowseIds
import com.lostf1sh.pixelplayeross.data.network.youtube.YtSearchFilter
import com.lostf1sh.pixelplayeross.data.network.youtube.auth.YtAccountStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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

    /** Who's signed in (name/handle/avatar), as last cached by [refreshAccountInfo]. */
    val accountInfo: StateFlow<YtAccountInfo?> get() = accountStore.accountInfo

    /** Fetch and cache the signed-in account's identity for the settings/account UI. */
    suspend fun refreshAccountInfo(): YtAccountInfo? {
        // Backfill the active identity id (brand-account correctness) for sessions that
        // signed in before it was captured. One HTML fetch, then cached in prefs.
        if (accountStore.dataSyncId == null) {
            innerTube.fetchDataSyncId()?.let(accountStore::saveDataSyncId)
        }
        return YouTubeResponseParser.accountInfo(innerTube.call("account/account_menu"))
            ?.also(accountStore::saveAccountInfo)
    }

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

    // ─────────────────────────── Lyrics ───────────────────────────

    /**
     * YTM's own lyrics for a track: time-synced when available (only mobile clients are
     * served `timedLyricsModel`), otherwise the plain text the web client shows. Null
     * when the track has no lyrics on YTM.
     */
    suspend fun lyrics(videoId: String): Lyrics? {
        val browseId = YouTubeResponseParser.lyricsBrowseId(
            innerTube.call("next") { put("videoId", videoId) }
        ) ?: return null

        val synced = runCatching {
            YouTubeResponseParser.timedLyricsLines(
                innerTube.call("browse", InnerTubeClientId.ANDROID_MUSIC) {
                    put("browseId", browseId)
                }
            )
        }.getOrNull()
        if (!synced.isNullOrEmpty()) {
            return Lyrics(
                synced = synced.map { (timeMs, line) -> SyncedLine(time = timeMs, line = line) },
                areFromRemote = true,
            )
        }

        val plain = runCatching {
            YouTubeResponseParser.plainLyrics(
                innerTube.call("browse") { put("browseId", browseId) }
            )
        }.getOrNull() ?: return null
        return Lyrics(plain = plain.lines(), areFromRemote = true)
    }

    // ─────────────────────────── Library ───────────────────────────

    suspend fun libraryPlaylists(): List<YtShelfEntry.Page> = libraryPages(YtBrowseIds.LIBRARY_PLAYLISTS)

    suspend fun libraryAlbums(): List<YtShelfEntry.Page> = libraryPages(YtBrowseIds.LIBRARY_ALBUMS)

    suspend fun libraryArtists(): List<YtShelfEntry.Page> = libraryPages(YtBrowseIds.LIBRARY_ARTISTS)

    /** First page of liked songs plus the continuation token for [likedSongsMore]. */
    suspend fun likedSongs(): Pair<List<YtTrack>, String?> =
        YouTubeResponseParser.likedSongsPage(
            innerTube.call("browse") { put("browseId", YtBrowseIds.LIBRARY_SONGS) }
        )

    suspend fun likedSongsMore(continuation: String): Pair<List<YtTrack>, String?> =
        YouTubeResponseParser.likedSongsPage(
            innerTube.call("browse") { put("continuation", continuation) }
        )

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

    /** Create a private playlist (optionally seeded with tracks); returns its playlistId. */
    suspend fun createPlaylist(title: String, videoIds: List<String> = emptyList()): String? =
        innerTube.call("playlist/create") {
            put("title", title)
            put("privacyStatus", "PRIVATE")
            if (videoIds.isNotEmpty()) {
                putJsonArray("videoIds") { videoIds.forEach(::add) }
            }
        }["playlistId"]?.jsonPrimitive?.contentOrNull

    /** Append a track to one of the user's playlists. True when YT confirms the edit. */
    suspend fun addToPlaylist(playlistId: String, videoId: String): Boolean {
        val root = innerTube.call("browse/edit_playlist") {
            // Library rows carry the browse form (`VL<id>`); edits want the bare id.
            put("playlistId", playlistId.removePrefix("VL"))
            putJsonArray("actions") {
                addJsonObject {
                    put("action", "ACTION_ADD_VIDEO")
                    put("addedVideoId", videoId)
                }
            }
        }
        return root["status"]?.jsonPrimitive?.contentOrNull == "STATUS_SUCCEEDED"
    }

    suspend fun setArtistSubscribed(channelId: String, subscribed: Boolean) {
        innerTube.call(if (subscribed) "subscription/subscribe" else "subscription/unsubscribe") {
            putJsonArray("channelIds") { add(channelId) }
        }
    }

    private companion object {
        /** `RDAMVM<videoId>` is the auto-generated radio playlist for a video. */
        const val RADIO_PLAYLIST_PREFIX = "RDAMVM"

        /** Params InnerTube expects alongside a radio `next` call. */
        const val RADIO_PARAMS = "wAEB"
    }
}
