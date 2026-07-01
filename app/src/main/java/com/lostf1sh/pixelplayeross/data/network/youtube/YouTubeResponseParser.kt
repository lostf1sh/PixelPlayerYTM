package com.lostf1sh.pixelplayeross.data.network.youtube

import com.lostf1sh.pixelplayeross.data.model.YtArtistLink
import com.lostf1sh.pixelplayeross.data.model.YtBrowsePage
import com.lostf1sh.pixelplayeross.data.model.YtFeedPage
import com.lostf1sh.pixelplayeross.data.model.YtPageKind
import com.lostf1sh.pixelplayeross.data.model.YtRadioPage
import com.lostf1sh.pixelplayeross.data.model.YtSearchPage
import com.lostf1sh.pixelplayeross.data.model.YtShelf
import com.lostf1sh.pixelplayeross.data.model.YtShelfEntry
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Turns InnerTube's renderer trees into the app's Yt* models. InnerTube has no stable
 * schema — shapes differ per surface and drift with server experiments — so everything
 * here is written to degrade to "skip this item", never to throw.
 *
 * The renderer names ("musicResponsiveListItemRenderer", "musicTwoRowItemRenderer", …)
 * and nesting paths are protocol facts shared with every other InnerTube client
 * (ytmusicapi, InnerTune).
 */
internal object YouTubeResponseParser {

    // ─────────────────────────── Search ───────────────────────────

    fun searchPage(root: JsonObject): YtSearchPage {
        val tracks = LinkedHashMap<String, YtTrack>()
        var continuation: String? = null

        val shelves: List<JsonObject> =
            root.obj("continuationContents").obj("musicShelfContinuation")?.let(::listOf)
                ?: root.obj("contents").obj("tabbedSearchResultsRenderer")
                    .arr("tabs").objAt(0).obj("tabRenderer").obj("content")
                    .obj("sectionListRenderer").arr("contents")
                    .objects()
                    .mapNotNull { it.obj("musicShelfRenderer") }
                    .toList()

        for (shelf in shelves) {
            if (continuation == null) continuation = shelfContinuation(shelf)
            shelf.arr("contents")?.objects()?.forEach { row ->
                row.obj("musicResponsiveListItemRenderer")
                    ?.let(::trackFromListRow)
                    ?.let { tracks.putIfAbsent(it.videoId, it) }
            }
        }
        return YtSearchPage(tracks.values.toList(), continuation)
    }

    fun searchSuggestions(root: JsonObject): List<String> =
        root.arr("contents")?.objects().orEmpty()
            .mapNotNull { it.obj("searchSuggestionsSectionRenderer") }
            .flatMap { it.arr("contents")?.objects().orEmpty() }
            .mapNotNull { it.obj("searchSuggestionRenderer").obj("suggestion")?.runsText() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    // ──────────────────────── Home / Explore ────────────────────────

    fun feedPage(root: JsonObject): YtFeedPage {
        val shelves = mutableListOf<YtShelf>()
        browseSections(root).forEachIndexed { index, section ->
            carouselShelf(section, index)?.let(shelves::add)
            moodGridShelf(section, index)?.let(shelves::add)
        }
        return YtFeedPage(shelves, sectionListContinuation(root))
    }

    /** Just the mood/genre chip shelves of FEmusic_moods_and_genres. */
    fun moodShelves(root: JsonObject): List<YtShelf> =
        browseSections(root).mapIndexedNotNull { index, section -> moodGridShelf(section, index) }

    // ───────────────────── Album / playlist / artist ─────────────────────

    fun browsePage(root: JsonObject): YtBrowsePage {
        val header = pageHeader(root)
        val tracks = LinkedHashMap<String, YtTrack>()
        val shelves = mutableListOf<YtShelf>()

        browseSections(root).forEachIndexed { index, section ->
            val body = section.obj("musicShelfRenderer") ?: section.obj("musicPlaylistShelfRenderer")
            body.arr("contents")?.objects()?.forEach { row ->
                row.obj("musicResponsiveListItemRenderer")
                    ?.let(::trackFromListRow)
                    ?.let { tracks.putIfAbsent(it.videoId, it) }
            }
            carouselShelf(section, index)?.let(shelves::add)
        }

        return YtBrowsePage(
            title = header?.obj("title")?.runsText().orEmpty(),
            subtitle = header?.obj("subtitle")?.runsText()?.ifBlank { null },
            heroImageUrl = headerThumbnail(header),
            tracks = tracks.values.toList(),
            shelves = shelves,
        )
    }

    /** Rows/cards of a library page: saved playlists, albums, artists or songs. */
    fun libraryEntries(root: JsonObject): List<YtShelfEntry> {
        val entries = LinkedHashMap<String, YtShelfEntry>()
        fun add(entry: YtShelfEntry?) {
            if (entry != null) entries.putIfAbsent(entry.key, entry)
        }
        browseSections(root).forEach { section ->
            section.obj("gridRenderer").arr("items")?.objects()?.forEach { add(shelfEntry(it)) }
            section.obj("musicCarouselShelfRenderer").arr("contents")?.objects()?.forEach { add(shelfEntry(it)) }
            val shelf = section.obj("musicShelfRenderer") ?: section.obj("musicPlaylistShelfRenderer")
            shelf.arr("contents")?.objects()?.forEach { row ->
                val listRow = row.obj("musicResponsiveListItemRenderer")
                if (listRow != null) add(listRowEntry(listRow)) else add(shelfEntry(row))
            }
        }
        return entries.values.toList()
    }

    // ───────────────────────── Radio / next ─────────────────────────

    fun radioPage(root: JsonObject): YtRadioPage {
        val panel = root.obj("continuationContents").obj("playlistPanelContinuation")
            ?: root.obj("contents").obj("singleColumnMusicWatchNextResultsRenderer")
                .obj("tabbedRenderer").obj("watchNextTabbedResultsRenderer")
                .arr("tabs").objAt(0).obj("tabRenderer").obj("content")
                .obj("musicQueueRenderer").obj("content").obj("playlistPanelRenderer")
            ?: return YtRadioPage(emptyList())

        val tracks = LinkedHashMap<String, YtTrack>()
        panel.arr("contents")?.objects()?.forEach { row ->
            row.obj("playlistPanelVideoRenderer")
                ?.let(::trackFromQueuePanel)
                ?.let { tracks.putIfAbsent(it.videoId, it) }
        }

        val continuation = panel.arr("continuations").objAt(0)?.let {
            it.obj("nextRadioContinuationData") ?: it.obj("nextContinuationData")
        }.str("continuation")

        return YtRadioPage(tracks.values.toList(), continuation)
    }

    // ────────────────────────── Track rows ──────────────────────────

    /** `musicResponsiveListItemRenderer` → track (search results, page track lists, library songs). */
    private fun trackFromListRow(row: JsonObject): YtTrack? {
        val videoId = rowVideoId(row) ?: return null
        val title = flexColumnText(row, 0)?.runsText()?.ifBlank { null } ?: return null
        val byline = flexColumnText(row, 1)

        return YtTrack(
            videoId = videoId,
            title = title,
            artists = artistsFrom(byline),
            album = flexColumnText(row, 2)?.runsText()?.ifBlank { null },
            albumBrowseId = flexColumnText(row, 2).arr("runs").objAt(0)
                .obj("navigationEndpoint").obj("browseEndpoint").str("browseId"),
            durationMs = bylineDurationMs(byline)
                ?: fixedColumnDurationMs(row)
                ?: 0L,
            thumbnailUrl = thumbnailUrl(row.obj("thumbnail")),
        )
    }

    /** `playlistPanelVideoRenderer` → track (radio / up-next queue rows). */
    private fun trackFromQueuePanel(row: JsonObject): YtTrack? {
        val videoId = row.str("videoId") ?: return null
        val title = row.obj("title")?.runsText()?.ifBlank { null } ?: return null
        val byline = row.obj("longBylineText") ?: row.obj("shortBylineText")
        return YtTrack(
            videoId = videoId,
            title = title,
            artists = artistsFrom(byline),
            durationMs = durationTextMs(row.obj("lengthText")?.runsText()) ?: 0L,
            thumbnailUrl = thumbnailUrl(row.obj("thumbnail")),
        )
    }

    // ─────────────────────────── Shelves ───────────────────────────

    private fun carouselShelf(section: JsonObject, index: Int): YtShelf? {
        val carousel = section.obj("musicCarouselShelfRenderer")
            ?: section.obj("musicImmersiveCarouselShelfRenderer")
            ?: return null
        val header = carousel.obj("header").obj("musicCarouselShelfBasicHeaderRenderer")
        val title = header.obj("title")?.runsText()?.ifBlank { null } ?: return null

        val entries = carousel.arr("contents")?.objects().orEmpty()
            .mapNotNull(::shelfEntry)
            .toList()
        if (entries.isEmpty()) return null

        return YtShelf(
            id = "shelf_${index}_$title",
            title = title,
            subtitle = header.obj("strapline")?.runsText()?.ifBlank { null },
            entries = entries,
        )
    }

    private fun moodGridShelf(section: JsonObject, index: Int): YtShelf? {
        val grid = section.obj("gridRenderer") ?: return null
        val chips = grid.arr("items")?.objects().orEmpty()
            .mapNotNull(::categoryEntry)
            .toList()
        if (chips.isEmpty()) return null
        val title = grid.obj("header").obj("gridHeaderRenderer").obj("title")?.runsText()
            ?.ifBlank { null } ?: "Moods & genres"
        return YtShelf(id = "moods_$index", title = title, entries = chips)
    }

    /** Any carousel/grid cell → shelf entry (track card, page card, or mood chip). */
    private fun shelfEntry(cell: JsonObject): YtShelfEntry? {
        cell.obj("musicResponsiveListItemRenderer")?.let { row ->
            return trackFromListRow(row)?.let { YtShelfEntry.Track(it) } ?: listRowEntry(row)
        }
        categoryEntry(cell)?.let { return it }

        val card = cell.obj("musicTwoRowItemRenderer") ?: return null
        val title = card.obj("title")?.runsText()?.ifBlank { null } ?: return null
        val subtitle = card.obj("subtitle")?.runsText()?.ifBlank { null }
        val thumb = thumbnailUrl(card.obj("thumbnailRenderer"))

        val nav = card.obj("navigationEndpoint")
        nav.obj("watchEndpoint").str("videoId")?.let { videoId ->
            return YtShelfEntry.Track(
                YtTrack(
                    videoId = videoId,
                    title = title,
                    artists = listOfNotNull(subtitle?.let { YtArtistLink(it) }),
                    thumbnailUrl = thumb,
                )
            )
        }

        val browse = nav.obj("browseEndpoint") ?: return null
        val browseId = browse.str("browseId") ?: return null
        return YtShelfEntry.Page(
            browseId = browseId,
            kind = pageKind(browse),
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumb,
        )
    }

    /** A library list row that is a link (artist/album/playlist) rather than a track. */
    private fun listRowEntry(row: JsonObject): YtShelfEntry? {
        val title = flexColumnText(row, 0)?.runsText()?.ifBlank { null } ?: return null
        val browse = row.obj("navigationEndpoint").obj("browseEndpoint")
            ?: flexColumnText(row, 0).arr("runs").objAt(0)
                .obj("navigationEndpoint").obj("browseEndpoint")
            ?: return null
        val browseId = browse.str("browseId") ?: return null
        return YtShelfEntry.Page(
            browseId = browseId,
            kind = pageKind(browse),
            title = title,
            subtitle = flexColumnText(row, 1)?.runsText()?.ifBlank { null },
            thumbnailUrl = thumbnailUrl(row.obj("thumbnail")),
        )
    }

    private fun categoryEntry(cell: JsonObject): YtShelfEntry.Category? {
        val button = cell.obj("musicNavigationButtonRenderer") ?: return null
        val title = button.obj("buttonText")?.runsText()?.ifBlank { null } ?: return null
        val browse = button.obj("clickCommand").obj("browseEndpoint") ?: return null
        val browseId = browse.str("browseId") ?: return null
        return YtShelfEntry.Category(browseId, browse.str("params"), title)
    }

    // ───────────────────────── Page plumbing ─────────────────────────

    /** Sections of a browse page, for both first responses and continuations. */
    private fun browseSections(root: JsonObject): List<JsonObject> =
        (root.obj("continuationContents").obj("sectionListContinuation").arr("contents")
            ?: root.obj("contents").obj("singleColumnBrowseResultsRenderer")
                .arr("tabs").objAt(0).obj("tabRenderer").obj("content")
                .obj("sectionListRenderer").arr("contents"))
            ?.objects()?.toList().orEmpty()

    private fun sectionListContinuation(root: JsonObject): String? =
        (root.obj("continuationContents").obj("sectionListContinuation")
            ?: root.obj("contents").obj("singleColumnBrowseResultsRenderer")
                .arr("tabs").objAt(0).obj("tabRenderer").obj("content")
                .obj("sectionListRenderer"))
            .arr("continuations").objAt(0).obj("nextContinuationData").str("continuation")

    private fun shelfContinuation(shelf: JsonObject): String? =
        shelf.arr("continuations").objAt(0).obj("nextContinuationData").str("continuation")

    private fun pageHeader(root: JsonObject): JsonObject? {
        val header = root.obj("header") ?: return null
        return header.obj("musicDetailHeaderRenderer")
            ?: header.obj("musicImmersiveHeaderRenderer")
            ?: header.obj("musicResponsiveHeaderRenderer")
    }

    private fun headerThumbnail(header: JsonObject?): String? =
        thumbnailUrl(header.obj("thumbnail").obj("croppedSquareThumbnailRenderer") ?: header.obj("thumbnail"))

    private fun pageKind(browseEndpoint: JsonObject): YtPageKind =
        when (
            browseEndpoint.obj("browseEndpointContextSupportedConfigs")
                .obj("browseEndpointContextMusicConfig").str("pageType")
        ) {
            "MUSIC_PAGE_TYPE_ALBUM" -> YtPageKind.ALBUM
            "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" -> YtPageKind.ARTIST
            "MUSIC_PAGE_TYPE_PODCAST_SHOW" -> YtPageKind.PODCAST
            else -> YtPageKind.PLAYLIST
        }

    // ───────────────────────── Row helpers ─────────────────────────

    /** A row's videoId can hide in three places depending on the surface. */
    private fun rowVideoId(row: JsonObject): String? {
        row.obj("playlistItemData").str("videoId")?.let { return it }

        row.obj("overlay").obj("musicItemThumbnailOverlayRenderer").obj("content")
            .obj("musicPlayButtonRenderer").obj("playNavigationEndpoint")
            .obj("watchEndpoint").str("videoId")?.let { return it }

        row.arr("flexColumns")?.objects()?.forEach { col ->
            col.obj("musicResponsiveListItemFlexColumnRenderer").obj("text")
                .arr("runs")?.objects()?.forEach { run ->
                    run.obj("navigationEndpoint").obj("watchEndpoint").str("videoId")
                        ?.let { return it }
                }
        }
        return null
    }

    private fun flexColumnText(row: JsonObject, index: Int): JsonObject? =
        row.arr("flexColumns").objAt(index)
            .obj("musicResponsiveListItemFlexColumnRenderer").obj("text")

    private fun artistsFrom(byline: JsonObject?): List<YtArtistLink> {
        val linked = byline.arr("runs")?.objects().orEmpty()
            .mapNotNull { run ->
                val channelId = run.obj("navigationEndpoint").obj("browseEndpoint")
                    .str("browseId")?.takeIf { it.startsWith("UC") }
                    ?: return@mapNotNull null
                YtArtistLink(run.str("text").orEmpty(), channelId)
            }
            .filter { it.name.isNotBlank() }
            .toList()
        if (linked.isNotEmpty()) return linked

        val plain = byline?.runsText()?.substringBefore(" • ")?.trim().orEmpty()
        return if (plain.isNotEmpty()) listOf(YtArtistLink(plain)) else emptyList()
    }

    /** The last run of a byline that looks like "3:45" / "1:02:33". */
    private fun bylineDurationMs(byline: JsonObject?): Long? =
        byline.arr("runs")?.objects().orEmpty()
            .mapNotNull { it.str("text") }
            .lastOrNull { DURATION_TEXT.matches(it.trim()) }
            ?.let(::durationTextMs)

    private fun fixedColumnDurationMs(row: JsonObject): Long? =
        row.arr("fixedColumns").objAt(0)
            .obj("musicResponsiveListItemFixedColumnRenderer").obj("text")?.runsText()
            ?.let(::durationTextMs)

    private fun durationTextMs(text: String?): Long? {
        val parts = text?.trim()?.split(':')?.map { it.toLongOrNull() ?: return null } ?: return null
        if (parts.isEmpty()) return null
        return parts.fold(0L) { acc, p -> acc * 60 + p } * 1000L
    }

    private val DURATION_TEXT = Regex("""\d{1,2}(:\d{2})+""")

    // ─────────────────────────── Thumbnails ───────────────────────────

    /**
     * Highest-resolution thumbnail of a renderer, upscaled: YTM encodes the size in the
     * URL (`=w120-h120-…`), so requesting a bigger square is just a string edit.
     */
    private fun thumbnailUrl(container: JsonObject?, targetSize: Int = 544): String? {
        val list = container.obj("musicThumbnailRenderer").obj("thumbnail").arr("thumbnails")
            ?: container.obj("thumbnail").arr("thumbnails")
            ?: container.arr("thumbnails")
            ?: return null
        val url = list.objAt(list.size - 1).str("url") ?: return null
        return when {
            QUERY_SIZE.containsMatchIn(url) -> url.replace(QUERY_SIZE, "=w$targetSize-h$targetSize")
            PATH_SIZE.containsMatchIn(url) -> url.replace(PATH_SIZE, "w$targetSize-h$targetSize")
            else -> url
        }
    }

    private val QUERY_SIZE = Regex("""=w\d+-h\d+""")
    private val PATH_SIZE = Regex("""w\d+-h\d+""")

    // ───────────────────── JsonElement navigation ─────────────────────

    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

    private fun JsonObject?.arr(key: String): JsonArray? = this?.get(key) as? JsonArray

    private fun JsonObject?.str(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonArray?.objAt(index: Int): JsonObject? =
        this?.getOrNull(index) as? JsonObject

    private fun JsonArray?.objects(): Sequence<JsonObject> =
        this?.asSequence()?.filterIsInstance<JsonObject>() ?: emptySequence()

    /** Concatenated `runs[].text`, or `simpleText` for plain text objects. */
    private fun JsonObject.runsText(): String {
        val runs = arr("runs") ?: return str("simpleText").orEmpty()
        return runs.objects().joinToString("") { it.str("text").orEmpty() }
    }
}
