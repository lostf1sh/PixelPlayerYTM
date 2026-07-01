package com.lostf1sh.pixelplayeross.data.innertube.parser

import com.lostf1sh.pixelplayeross.data.innertube.InnerTubeMapper
import com.lostf1sh.pixelplayeross.data.innertube.model.YouTubeArtistRef
import com.lostf1sh.pixelplayeross.data.innertube.model.YouTubeSong
import com.lostf1sh.pixelplayeross.data.model.BrowseKind
import com.lostf1sh.pixelplayeross.data.model.HomeShelf
import com.lostf1sh.pixelplayeross.data.model.ShelfItem
import com.lostf1sh.pixelplayeross.data.model.Song
import kotlinx.collections.immutable.toImmutableList
import org.json.JSONArray
import org.json.JSONObject

/** A parsed home/explore feed plus its paging token. */
data class HomeFeedPage(
    val shelves: List<HomeShelf>,
    val continuation: String?,
)

/** A parsed album/playlist/artist page. */
data class BrowsePage(
    val title: String,
    val subtitle: String?,
    val thumbnailUrl: String?,
    val songs: List<Song>,
    val shelves: List<HomeShelf>,
)

/**
 * Parses `browse` responses: the home feed, the moods & genres grid, and
 * album/playlist/artist detail pages. Produces domain models directly (shelves of
 * [ShelfItem]s) so the UI can render everything through one generic path.
 */
internal object BrowseParser {

    fun parseHomeFeed(root: JSONObject): HomeFeedPage {
        val sections = homeSections(root)
        val shelves = mutableListOf<HomeShelf>()
        sections.forEachObjectIndexed { index, section ->
            parseCarousel(section, index)?.let { shelves.add(it) }
        }
        return HomeFeedPage(shelves, extractSectionContinuation(root))
    }

    /** Moods & genres: a grid of colored category pills. */
    fun parseMoods(root: JSONObject): List<HomeShelf> {
        val sections = homeSections(root)
        val shelves = mutableListOf<HomeShelf>()
        sections.forEachObjectIndexed { index, section ->
            val grid = section.optJSONObject("gridRenderer")
                ?: section.optJSONObject("musicCarouselShelfRenderer")
                ?: return@forEachObjectIndexed
            val title = grid.optJSONObject("header")
                ?.optJSONObject("gridHeaderRenderer")
                ?.let { YouTubeParsingUtils.joinRuns(it.optJSONObject("title")) }
                ?.takeIf { it.isNotBlank() } ?: "Moods & genres"
            val items = grid.optJSONArray("items") ?: grid.optJSONArray("contents") ?: return@forEachObjectIndexed
            val moods = mutableListOf<ShelfItem>()
            items.forEachObject { item ->
                parseMoodButton(item)?.let { moods.add(it) }
            }
            if (moods.isNotEmpty()) {
                shelves.add(HomeShelf(id = "moods_$index", title = title, items = moods.toImmutableList()))
            }
        }
        return shelves
    }

    /** Library landing pages: a grid of playlist/album cards (e.g. saved playlists). */
    fun parseGridItems(root: JSONObject): List<ShelfItem> = parseLibraryItems(root)

    /**
     * Parse any library page (playlists / albums / artists / songs). Handles the three
     * shapes YTM uses: grid cards (`musicTwoRowItemRenderer`), shelf rows
     * (`musicResponsiveListItemRenderer` — songs OR artist/album/playlist rows), and carousels.
     */
    fun parseLibraryItems(root: JSONObject): List<ShelfItem> {
        val out = mutableListOf<ShelfItem>()
        val seen = HashSet<String>()
        fun addUnique(item: ShelfItem?) {
            if (item != null && seen.add(item.key)) out.add(item)
        }
        homeSections(root).forEachObject { section ->
            section.optJSONObject("gridRenderer")?.optJSONArray("items")?.forEachObject { addUnique(parseCarouselItem(it)) }
            section.optJSONObject("musicCarouselShelfRenderer")?.optJSONArray("contents")?.forEachObject { addUnique(parseCarouselItem(it)) }
            val shelf = section.optJSONObject("musicShelfRenderer")
                ?: section.optJSONObject("musicPlaylistShelfRenderer")
            shelf?.optJSONArray("contents")?.forEachObject { entry ->
                val mrlir = entry.optJSONObject("musicResponsiveListItemRenderer")
                if (mrlir != null) addUnique(parseLibraryRow(mrlir)) else addUnique(parseCarouselItem(entry))
            }
        }
        return out
    }

    /** A library shelf row: a song (has videoId) or an artist/album/playlist (has a browseId). */
    private fun parseLibraryRow(mrlir: JSONObject): ShelfItem? {
        YouTubeParsingUtils.parseSongRow(mrlir)?.let { return ShelfItem.SongItem(InnerTubeMapper.toSong(it)) }

        val title = YouTubeParsingUtils.joinRuns(YouTubeParsingUtils.flexColumnText(mrlir, 0))
            .takeIf { it.isNotBlank() } ?: return null
        val browse = mrlir.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
            ?: mrlir.optJSONArray("flexColumns")
                ?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
            ?: return null
        val browseId = browse.optString("browseId").takeIf { it.isNotBlank() } ?: return null
        val subtitle = YouTubeParsingUtils.joinRuns(YouTubeParsingUtils.flexColumnText(mrlir, 1)).takeIf { it.isNotBlank() }
        val thumb = YouTubeParsingUtils.bestThumbnail(mrlir.optJSONObject("thumbnail"))
        val pageType = browse.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType")
        return ShelfItem.BrowseItem(browseId, title, subtitle, thumb, pageType.toBrowseKind())
    }

    fun parseBrowsePage(root: JSONObject): BrowsePage {
        val title = headerTitle(root)
        val subtitle = headerSubtitle(root)
        val thumbnail = headerThumbnail(root)

        val songs = mutableListOf<Song>()
        val shelves = mutableListOf<HomeShelf>()
        val seen = HashSet<String>()

        homeSections(root).forEachObjectIndexed { index, section ->
            // Track lists (album/playlist bodies).
            val shelfContents = section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                ?: section.optJSONObject("musicPlaylistShelfRenderer")?.optJSONArray("contents")
            if (shelfContents != null) {
                shelfContents.forEachObject { row ->
                    val renderer = row.optJSONObject("musicResponsiveListItemRenderer") ?: return@forEachObject
                    val ytSong = YouTubeParsingUtils.parseSongRow(renderer) ?: return@forEachObject
                    if (seen.add(ytSong.videoId)) songs.add(InnerTubeMapper.toSong(ytSong))
                }
            }
            // Carousels (artist pages: albums, singles, related).
            parseCarousel(section, index)?.let { shelf ->
                // Absorb bare song carousels into the main song list; keep the rest as shelves.
                shelves.add(shelf)
            }
        }
        return BrowsePage(title, subtitle, thumbnail, songs, shelves)
    }

    // ─── Sections ──────────────────────────────────────────────────────

    private fun homeSections(root: JSONObject): JSONArray {
        root.optJSONObject("continuationContents")
            ?.optJSONObject("sectionListContinuation")
            ?.optJSONArray("contents")
            ?.let { return it }

        return root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?: JSONArray()
    }

    private fun extractSectionContinuation(root: JSONObject): String? {
        val sectionList = root.optJSONObject("continuationContents")
            ?.optJSONObject("sectionListContinuation")
            ?: root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
        return sectionList
            ?.optJSONArray("continuations")
            ?.optJSONObject(0)
            ?.optJSONObject("nextContinuationData")
            ?.optString("continuation")
            ?.takeIf { it.isNotBlank() }
    }

    // ─── Carousel shelf ────────────────────────────────────────────────

    private fun parseCarousel(section: JSONObject, index: Int): HomeShelf? {
        val carousel = section.optJSONObject("musicCarouselShelfRenderer")
            ?: section.optJSONObject("musicImmersiveCarouselShelfRenderer")
            ?: return null

        val header = carousel.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
        val title = YouTubeParsingUtils.joinRuns(header?.optJSONObject("title")).takeIf { it.isNotBlank() }
            ?: return null
        val strapline = header?.optJSONObject("strapline")
            ?.let { YouTubeParsingUtils.joinRuns(it) }?.takeIf { it.isNotBlank() }

        val contents = carousel.optJSONArray("contents") ?: return null
        val items = mutableListOf<ShelfItem>()
        contents.forEachObject { entry ->
            parseCarouselItem(entry)?.let { items.add(it) }
        }
        if (items.isEmpty()) return null
        return HomeShelf(id = "shelf_${index}_$title", title = title, strapline = strapline, items = items.toImmutableList())
    }

    private fun parseCarouselItem(entry: JSONObject): ShelfItem? {
        // Song row card.
        entry.optJSONObject("musicResponsiveListItemRenderer")?.let { row ->
            val ytSong = YouTubeParsingUtils.parseSongRow(row) ?: return null
            return ShelfItem.SongItem(InnerTubeMapper.toSong(ytSong))
        }

        val card = entry.optJSONObject("musicTwoRowItemRenderer") ?: return null
        val title = YouTubeParsingUtils.joinRuns(card.optJSONObject("title")).takeIf { it.isNotBlank() } ?: return null
        val subtitle = card.optJSONObject("subtitle")?.let { YouTubeParsingUtils.joinRuns(it) }?.takeIf { it.isNotBlank() }
        val thumbnail = YouTubeParsingUtils.bestThumbnail(card.optJSONObject("thumbnailRenderer"))

        val nav = card.optJSONObject("navigationEndpoint")
        // A watchEndpoint means this card plays a single track.
        nav?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }?.let { videoId ->
            val artists = listOfNotNull(subtitle?.let { YouTubeArtistRef(it, null) })
            return ShelfItem.SongItem(
                InnerTubeMapper.toSong(
                    YouTubeSong(
                        videoId = videoId,
                        title = title,
                        artists = artists,
                        album = null,
                        durationSeconds = 0,
                        thumbnailUrl = thumbnail,
                    )
                )
            )
        }

        val browse = nav?.optJSONObject("browseEndpoint") ?: return null
        val browseId = browse.optString("browseId").takeIf { it.isNotBlank() } ?: return null
        val pageType = browse.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType")
        return ShelfItem.BrowseItem(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnail,
            kind = pageType.toBrowseKind(),
        )
    }

    private fun parseMoodButton(item: JSONObject): ShelfItem? {
        val button = item.optJSONObject("musicNavigationButtonRenderer") ?: return null
        val title = YouTubeParsingUtils.joinRuns(button.optJSONObject("buttonText")).takeIf { it.isNotBlank() }
            ?: return null
        val browse = button.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint") ?: return null
        val browseId = browse.optString("browseId").takeIf { it.isNotBlank() } ?: return null
        val params = browse.optString("params").takeIf { it.isNotBlank() }
        return ShelfItem.MoodItem(browseId = browseId, params = params, title = title)
    }

    // ─── Header helpers ────────────────────────────────────────────────

    private fun header(root: JSONObject): JSONObject? =
        root.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")
            ?: root.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer")
            ?: root.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")

    private fun headerTitle(root: JSONObject): String =
        YouTubeParsingUtils.joinRuns(header(root)?.optJSONObject("title")).takeIf { it.isNotBlank() } ?: ""

    private fun headerSubtitle(root: JSONObject): String? =
        header(root)?.optJSONObject("subtitle")?.let { YouTubeParsingUtils.joinRuns(it) }?.takeIf { it.isNotBlank() }

    private fun headerThumbnail(root: JSONObject): String? {
        val h = header(root) ?: return null
        return YouTubeParsingUtils.bestThumbnail(
            h.optJSONObject("thumbnail")?.optJSONObject("croppedSquareThumbnailRenderer")
                ?: h.optJSONObject("thumbnail")
        )
    }

    private fun String?.toBrowseKind(): BrowseKind = when (this) {
        "MUSIC_PAGE_TYPE_ALBUM" -> BrowseKind.ALBUM
        "MUSIC_PAGE_TYPE_ARTIST" -> BrowseKind.ARTIST
        "MUSIC_PAGE_TYPE_PODCAST_SHOW" -> BrowseKind.PODCAST
        else -> BrowseKind.PLAYLIST
    }

    private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (i in 0 until length()) optJSONObject(i)?.let(action)
    }

    private inline fun JSONArray.forEachObjectIndexed(action: (Int, JSONObject) -> Unit) {
        for (i in 0 until length()) optJSONObject(i)?.let { action(i, it) }
    }
}
