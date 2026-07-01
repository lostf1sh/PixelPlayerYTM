package com.lostf1sh.pixelplayeross.data.innertube

import com.lostf1sh.pixelplayeross.data.innertube.model.YouTubeClient
import com.lostf1sh.pixelplayeross.data.innertube.model.response.PlayerResponse
import com.lostf1sh.pixelplayeross.data.innertube.parser.BrowseParser
import com.lostf1sh.pixelplayeross.data.innertube.parser.BrowsePage
import com.lostf1sh.pixelplayeross.data.innertube.parser.HomeFeedPage
import com.lostf1sh.pixelplayeross.data.innertube.parser.NextPage
import com.lostf1sh.pixelplayeross.data.innertube.parser.NextParser
import com.lostf1sh.pixelplayeross.data.innertube.parser.PlayerParser
import com.lostf1sh.pixelplayeross.data.innertube.parser.SearchPage
import com.lostf1sh.pixelplayeross.data.innertube.parser.SearchParser
import com.lostf1sh.pixelplayeross.data.youtube.stream.AudioQuality
import com.lostf1sh.pixelplayeross.data.youtube.stream.YouTubeCipherManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONObject
import timber.log.Timber

/**
 * High-level YouTube Music endpoint methods. Owns which [YouTubeClient] each call uses and
 * turns raw InnerTube JSON into parsed domain results via the parser layer.
 */
class YouTubeMusicApi(
    private val client: InnerTubeClient,
    private val json: Json,
    private val cipherManager: YouTubeCipherManager,
) {
    /** Full-text search. Phase 1 returns playable tracks from all shelves. */
    suspend fun search(query: String, continuation: String? = null): SearchPage {
        val raw = client.post(endpoint = "search", client = YouTubeClient.WEB_REMIX) {
            if (continuation != null) {
                put("continuation", continuation)
            } else {
                put("query", query)
            }
        }
        return SearchParser.parse(JSONObject(raw))
    }

    /** The YouTube Music home feed (`FEmusic_home`), paginated via [continuation]. */
    suspend fun getHomeFeed(continuation: String? = null): HomeFeedPage {
        val raw = client.post(endpoint = "browse", client = YouTubeClient.WEB_REMIX) {
            if (continuation != null) put("continuation", continuation) else put("browseId", "FEmusic_home")
        }
        return BrowseParser.parseHomeFeed(JSONObject(raw))
    }

    /** Moods & genres grid (`FEmusic_moods_and_genres`). */
    suspend fun getMoodsAndGenres(): List<com.lostf1sh.pixelplayeross.data.model.HomeShelf> {
        val raw = client.post(endpoint = "browse", client = YouTubeClient.WEB_REMIX) {
            put("browseId", "FEmusic_moods_and_genres")
        }
        return BrowseParser.parseMoods(JSONObject(raw))
    }

    /** A specific mood/genre category page (from a [ShelfItem.MoodItem]). */
    suspend fun getMoodCategory(browseId: String, params: String?): HomeFeedPage {
        val raw = client.post(endpoint = "browse", client = YouTubeClient.WEB_REMIX) {
            put("browseId", browseId)
            if (params != null) put("params", params)
        }
        return BrowseParser.parseHomeFeed(JSONObject(raw))
    }

    /** An album/playlist/artist page. */
    suspend fun getBrowsePage(browseId: String): BrowsePage {
        // Playlist ids need the "VL" browse prefix; album (MPREb…)/artist (UC…) ids don't.
        val normalizedId = if (browseId.startsWith("PL")) "VL$browseId" else browseId
        val raw = client.post(endpoint = "browse", client = YouTubeClient.WEB_REMIX) {
            put("browseId", normalizedId)
        }
        return BrowseParser.parseBrowsePage(JSONObject(raw))
    }

    /** Radio / up-next queue for a track (`next`). */
    suspend fun getWatchQueue(
        videoId: String,
        playlistId: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): NextPage {
        val raw = client.post(endpoint = "next", client = YouTubeClient.WEB_REMIX) {
            if (continuation != null) {
                put("continuation", continuation)
            } else {
                put("videoId", videoId)
                playlistId?.let { put("playlistId", it) }
                params?.let { put("params", it) }
                put("isAudioOnly", true)
            }
        }
        return NextParser.parse(JSONObject(raw))
    }

    /** Liked songs ("Your Likes"). Requires an authenticated session. */
    suspend fun getLikedSongs(): List<com.lostf1sh.pixelplayeross.data.model.Song> {
        val raw = browse("FEmusic_liked_videos")
        val root = JSONObject(raw)
        val songs = BrowseParser.parseBrowsePage(root).songs
        return songs.ifEmpty {
            BrowseParser.parseLibraryItems(root)
                .filterIsInstance<com.lostf1sh.pixelplayeross.data.model.ShelfItem.SongItem>()
                .map { it.song }
        }
    }

    /** Saved / created playlists. Requires an authenticated session. */
    suspend fun getLibraryPlaylists() = libraryBrowseItems("FEmusic_liked_playlists")

    /** Saved albums. Requires an authenticated session. */
    suspend fun getLibraryAlbums() = libraryBrowseItems("FEmusic_liked_albums")

    /** Artists in your library. Requires an authenticated session. */
    suspend fun getLibraryArtists() = libraryBrowseItems("FEmusic_library_corpus_track_artists")

    private suspend fun libraryBrowseItems(browseId: String): List<com.lostf1sh.pixelplayeross.data.model.ShelfItem.BrowseItem> {
        val raw = browse(browseId)
        return BrowseParser.parseLibraryItems(JSONObject(raw))
            .filterIsInstance<com.lostf1sh.pixelplayeross.data.model.ShelfItem.BrowseItem>()
    }

    private suspend fun browse(browseId: String): String =
        client.post(endpoint = "browse", client = YouTubeClient.WEB_REMIX) {
            put("browseId", browseId)
        }

    /** Like or remove-like a track. Requires an authenticated session. */
    suspend fun setLike(videoId: String, liked: Boolean) {
        val endpoint = if (liked) "like/like" else "like/removelike"
        client.post(endpoint = endpoint, client = YouTubeClient.WEB_REMIX) {
            put("target", kotlinx.serialization.json.buildJsonObject { put("videoId", videoId) })
        }
    }

    /** Search-as-you-type suggestions. */
    suspend fun searchSuggestions(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val raw = client.post(endpoint = "music/get_search_suggestions", client = YouTubeClient.WEB_REMIX) {
            put("input", input)
        }
        return parseSuggestions(raw)
    }

    /**
     * Resolve a directly-streamable audio URL for [videoId]. Tries the Android Music client
     * first (plain URLs, no cipher); falls back to the embedded TV client for gated content.
     */
    suspend fun resolveStreamUrl(videoId: String, quality: AudioQuality): String? {
        for (playerClient in listOf(YouTubeClient.ANDROID_MUSIC, YouTubeClient.WEB_REMIX, YouTubeClient.TVHTML5)) {
            val response = runCatching { requestPlayer(videoId, playerClient) }
                .onFailure { Timber.w(it, "player call failed for %s via %s", videoId, playerClient.clientName) }
                .getOrNull() ?: continue

            val selected = PlayerParser.selectAudioFormat(response, quality) ?: run {
                val reason = response.playabilityStatus?.reason ?: response.playabilityStatus?.status
                Timber.w("No audio format for %s via %s (status: %s)", videoId, playerClient.clientName, reason)
                continue
            }

            val playable = cipherManager.buildPlayableUrl(selected.url, selected.signatureCipher)
            if (playable != null) {
                Timber.d("Resolved %s via %s: itag=%d %s", videoId, playerClient.clientName, selected.itag, selected.mimeType)
                return playable
            }
        }
        return null
    }

    private suspend fun requestPlayer(videoId: String, playerClient: YouTubeClient): PlayerResponse {
        val raw = client.post(endpoint = "player", client = playerClient) {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        return json.decodeFromString(PlayerResponse.serializer(), raw)
    }

    private fun parseSuggestions(raw: String): List<String> = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        val sections = root["contents"]?.jsonArray ?: return emptyList()
        buildList {
            sections.forEach { section ->
                val contents = section.jsonObject["searchSuggestionsSectionRenderer"]
                    ?.jsonObject?.get("contents")?.jsonArray ?: return@forEach
                contents.forEach { item ->
                    val runs = item.jsonObject["searchSuggestionRenderer"]
                        ?.jsonObject?.get("suggestion")
                        ?.jsonObject?.get("runs")?.jsonArray ?: return@forEach
                    val text = runs.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
                    if (text.isNotBlank()) add(text)
                }
            }
        }
    }.getOrElse {
        Timber.w(it, "Failed to parse search suggestions")
        emptyList()
    }
}
