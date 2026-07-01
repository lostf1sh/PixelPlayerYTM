package com.lostf1sh.pixelplayerytm.data.repository

import com.lostf1sh.pixelplayerytm.data.innertube.BrowseId
import com.lostf1sh.pixelplayerytm.data.innertube.InnerTube
import com.lostf1sh.pixelplayerytm.data.innertube.SearchFilter
import com.lostf1sh.pixelplayerytm.data.innertube.parser.BrowseParser
import com.lostf1sh.pixelplayerytm.data.innertube.parser.NextParser
import com.lostf1sh.pixelplayerytm.data.innertube.parser.SearchParser
import com.lostf1sh.pixelplayerytm.domain.model.AccountInfo
import com.lostf1sh.pixelplayerytm.domain.model.AlbumPage
import com.lostf1sh.pixelplayerytm.domain.model.ArtistPage
import com.lostf1sh.pixelplayerytm.domain.model.HomePage
import com.lostf1sh.pixelplayerytm.domain.model.MoodsPage
import com.lostf1sh.pixelplayerytm.domain.model.NextPage
import com.lostf1sh.pixelplayerytm.domain.model.PlaylistPage
import com.lostf1sh.pixelplayerytm.domain.model.SearchResultPage
import com.lostf1sh.pixelplayerytm.domain.model.SearchSuggestions
import com.lostf1sh.pixelplayerytm.domain.model.SearchSummaryPage
import com.lostf1sh.pixelplayerytm.domain.model.SongItem
import com.lostf1sh.pixelplayerytm.domain.model.YtItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level YouTube Music data access: raw InnerTube responses in,
 * parsed domain pages out. All calls are network-backed and suspend.
 */
@Singleton
class YouTubeRepository @Inject constructor(
    private val innerTube: InnerTube,
) {
    // ---------- Search ----------

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> = runCatching {
        SearchParser.parseSummary(innerTube.search(query))
    }

    suspend fun search(query: String, filter: SearchFilter): Result<SearchResultPage> = runCatching {
        SearchParser.parseResults(innerTube.search(query, filter.params))
    }

    suspend fun searchContinuation(token: String): Result<SearchResultPage> = runCatching {
        SearchParser.parseContinuation(innerTube.searchContinuation(token))
    }

    suspend fun searchSuggestions(input: String): Result<SearchSuggestions> = runCatching {
        SearchParser.parseSuggestions(innerTube.searchSuggestions(input))
    }

    // ---------- Home / Explore ----------

    suspend fun home(): Result<HomePage> = runCatching {
        BrowseParser.parseHome(innerTube.browse(BrowseId.HOME))
    }

    suspend fun homeContinuation(token: String): Result<HomePage> = runCatching {
        BrowseParser.parseHomeContinuation(innerTube.browse(continuation = token))
    }

    suspend fun explore(): Result<HomePage> = runCatching {
        BrowseParser.parseHome(innerTube.browse(BrowseId.EXPLORE))
    }

    suspend fun newReleases(): Result<HomePage> = runCatching {
        BrowseParser.parseHome(innerTube.browse(BrowseId.NEW_RELEASES))
    }

    suspend fun charts(): Result<HomePage> = runCatching {
        BrowseParser.parseHome(innerTube.browse(BrowseId.CHARTS))
    }

    suspend fun moods(): Result<MoodsPage> = runCatching {
        BrowseParser.parseMoods(innerTube.browse(BrowseId.MOODS_AND_GENRES))
    }

    /** A mood/genre category page, or any browse page rendered as shelves. */
    suspend fun browsePage(browseId: String, params: String? = null): Result<HomePage> = runCatching {
        BrowseParser.parseHome(innerTube.browse(browseId, params))
    }

    // ---------- Detail pages ----------

    suspend fun album(browseId: String): Result<AlbumPage> = runCatching {
        BrowseParser.parseAlbum(browseId, innerTube.browse(browseId))
            ?: error("Album page could not be parsed: $browseId")
    }

    suspend fun artist(browseId: String): Result<ArtistPage> = runCatching {
        BrowseParser.parseArtist(browseId, innerTube.browse(browseId))
            ?: error("Artist page could not be parsed: $browseId")
    }

    suspend fun playlist(playlistId: String): Result<PlaylistPage> = runCatching {
        val browseId = BrowseId.playlist(playlistId)
        BrowseParser.parsePlaylist(browseId, innerTube.browse(browseId))
            ?: error("Playlist page could not be parsed: $browseId")
    }

    suspend fun playlistContinuation(token: String): Result<Pair<List<SongItem>, String?>> = runCatching {
        BrowseParser.parsePlaylistContinuation(innerTube.browse(continuation = token))
    }

    // ---------- Queue / radio ----------

    suspend fun next(
        videoId: String? = null,
        playlistId: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): Result<NextPage> = runCatching {
        NextParser.parse(
            innerTube.next(
                videoId = videoId,
                playlistId = playlistId,
                params = params,
                continuation = continuation,
            ),
        )
    }

    /** Start radio for a song: RDAMVM playlist based on the videoId. */
    suspend fun radio(videoId: String): Result<NextPage> = runCatching {
        NextParser.parse(
            innerTube.next(videoId = videoId, playlistId = "RDAMVM$videoId"),
        )
    }

    // ---------- Library (requires auth) ----------

    suspend fun libraryPlaylists(): Result<Pair<List<YtItem>, String?>> = runCatching {
        BrowseParser.parseLibraryItems(innerTube.browse(BrowseId.LIBRARY_PLAYLISTS))
    }

    suspend fun librarySongs(): Result<Pair<List<YtItem>, String?>> = runCatching {
        BrowseParser.parseLibraryItems(innerTube.browse(BrowseId.LIBRARY_SONGS))
    }

    suspend fun libraryAlbums(): Result<Pair<List<YtItem>, String?>> = runCatching {
        BrowseParser.parseLibraryItems(innerTube.browse(BrowseId.LIBRARY_ALBUMS))
    }

    suspend fun libraryArtists(): Result<Pair<List<YtItem>, String?>> = runCatching {
        BrowseParser.parseLibraryItems(innerTube.browse(BrowseId.LIBRARY_ARTISTS))
    }

    suspend fun libraryContinuation(token: String): Result<Pair<List<YtItem>, String?>> = runCatching {
        BrowseParser.parseLibraryContinuation(innerTube.browse(continuation = token))
    }

    // ---------- Actions ----------

    suspend fun likeSong(videoId: String): Result<Boolean> = runCatching {
        innerTube.setLike(videoId, "like/like")
    }

    suspend fun unlikeSong(videoId: String): Result<Boolean> = runCatching {
        innerTube.setLike(videoId, "like/removelike")
    }

    suspend fun accountInfo(): Result<AccountInfo> = runCatching {
        val header = innerTube.accountMenu().header ?: error("No account header")
        AccountInfo(
            name = header.accountName?.text.orEmpty(),
            email = header.email?.text,
            photoUrl = header.accountPhoto?.thumbnails?.lastOrNull()?.url,
        )
    }
}
