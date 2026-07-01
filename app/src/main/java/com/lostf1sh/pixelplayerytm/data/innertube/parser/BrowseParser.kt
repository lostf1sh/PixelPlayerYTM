package com.lostf1sh.pixelplayerytm.data.innertube.parser

import com.lostf1sh.pixelplayerytm.data.innertube.model.response.BrowseResponse
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.MusicPlaylistShelfRenderer
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.MusicShelfRenderer
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.SectionListRenderer
import com.lostf1sh.pixelplayerytm.domain.model.AlbumItem
import com.lostf1sh.pixelplayerytm.domain.model.AlbumPage
import com.lostf1sh.pixelplayerytm.domain.model.AlbumRef
import com.lostf1sh.pixelplayerytm.domain.model.ArtistItem
import com.lostf1sh.pixelplayerytm.domain.model.ArtistPage
import com.lostf1sh.pixelplayerytm.domain.model.ArtistRef
import com.lostf1sh.pixelplayerytm.domain.model.HomePage
import com.lostf1sh.pixelplayerytm.domain.model.MoodsPage
import com.lostf1sh.pixelplayerytm.domain.model.PlaylistItem
import com.lostf1sh.pixelplayerytm.domain.model.PlaylistPage
import com.lostf1sh.pixelplayerytm.domain.model.Shelf
import com.lostf1sh.pixelplayerytm.domain.model.SongItem
import com.lostf1sh.pixelplayerytm.domain.model.YtItem

object BrowseParser {

    // ---------- Generic section parsing ----------

    private fun BrowseResponse.primarySectionList(): SectionListRenderer? =
        contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer
            ?: contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer
            ?: contents?.sectionListRenderer

    private fun BrowseResponse.secondarySectionList(): SectionListRenderer? =
        contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer

    fun parseShelf(content: SectionListRenderer.Content): Shelf? {
        content.musicCarouselShelfRenderer?.let { carousel ->
            val header = carousel.header?.musicCarouselShelfBasicHeaderRenderer
            val items = carousel.contents.orEmpty().mapNotNull(ItemParser::parseCarouselContent)
            if (items.isEmpty()) return null
            val more = header?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint
            return Shelf(
                title = header?.title?.text.orEmpty(),
                items = items,
                moreBrowseId = more?.browseId,
                moreParams = more?.params,
            )
        }
        content.musicShelfRenderer?.let { shelf ->
            val items = shelf.contents.orEmpty()
                .mapNotNull { it.musicResponsiveListItemRenderer }
                .mapNotNull { ItemParser.parseListItem(it) }
            if (items.isEmpty()) return null
            val more = shelf.bottomEndpoint?.browseEndpoint
            return Shelf(
                title = shelf.title?.text.orEmpty(),
                items = items,
                moreBrowseId = more?.browseId,
                moreParams = more?.params,
                isVerticalList = true,
            )
        }
        content.gridRenderer?.let { grid ->
            val items = grid.items.orEmpty().mapNotNull { item ->
                item.musicTwoRowItemRenderer?.let(ItemParser::parseTwoRowItem)
                    ?: item.musicNavigationButtonRenderer?.let(ItemParser::parseMoodButton)
            }
            if (items.isEmpty()) return null
            return Shelf(
                title = grid.header?.gridHeaderRenderer?.title?.text.orEmpty(),
                items = items,
            )
        }
        return null
    }

    private fun SectionListRenderer?.shelves(): List<Shelf> =
        this?.contents.orEmpty().mapNotNull(::parseShelf)

    private fun SectionListRenderer?.continuationToken(): String? =
        this?.continuations?.firstNotNullOfOrNull { it.token }
            ?: this?.contents?.firstNotNullOfOrNull { it.continuationItemRenderer?.token }

    // ---------- Home / Explore ----------

    fun parseHome(response: BrowseResponse): HomePage {
        val sectionList = response.primarySectionList()
        return HomePage(
            shelves = sectionList.shelves(),
            continuation = sectionList.continuationToken(),
        )
    }

    fun parseHomeContinuation(response: BrowseResponse): HomePage {
        response.continuationContents?.sectionListContinuation?.let { continuation ->
            return HomePage(
                shelves = continuation.contents.orEmpty().mapNotNull(::parseShelf),
                continuation = continuation.continuations?.firstNotNullOfOrNull { it.token },
            )
        }
        val appended = response.onResponseReceivedActions.orEmpty()
            .flatMap { it.appendContinuationItemsAction?.continuationItems.orEmpty() }
        return HomePage(
            shelves = appended.mapNotNull(::parseShelf),
            continuation = appended.firstNotNullOfOrNull { it.continuationItemRenderer?.token },
        )
    }

    // ---------- Moods & genres ----------

    fun parseMoods(response: BrowseResponse): MoodsPage {
        val sections = response.primarySectionList()?.contents.orEmpty().mapNotNull { content ->
            val grid = content.gridRenderer ?: return@mapNotNull null
            val items = grid.items.orEmpty()
                .mapNotNull { it.musicNavigationButtonRenderer }
                .mapNotNull(ItemParser::parseMoodButton)
            if (items.isEmpty()) return@mapNotNull null
            MoodsPage.MoodSection(
                title = grid.header?.gridHeaderRenderer?.title?.text.orEmpty(),
                items = items,
            )
        }
        return MoodsPage(sections)
    }

    // ---------- Album ----------

    fun parseAlbum(browseId: String, response: BrowseResponse): AlbumPage? {
        // Newer two-column layout: header inside primary sectionList.
        val responsiveHeader = response.primarySectionList()?.contents
            ?.firstNotNullOfOrNull { it.musicResponsiveHeaderRenderer }
            ?: response.header?.musicResponsiveHeaderRenderer
        val detailHeader = response.header?.musicDetailHeaderRenderer

        val title: String
        val subtitle: String?
        val secondSubtitle: String?
        val thumbnail: String?
        val artists: List<ArtistRef>
        val description: String?

        when {
            responsiveHeader != null -> {
                title = responsiveHeader.title?.text.orEmpty()
                subtitle = responsiveHeader.subtitle?.text
                secondSubtitle = responsiveHeader.secondSubtitle?.text
                thumbnail = responsiveHeader.thumbnail?.urls?.bestUrl()
                artists = parseArtistRuns(responsiveHeader.straplineTextOne?.runs.orEmpty())
                description = responsiveHeader.description
                    ?.musicDescriptionShelfRenderer?.description?.text
            }

            detailHeader != null -> {
                title = detailHeader.title?.text.orEmpty()
                subtitle = detailHeader.subtitle?.text
                secondSubtitle = detailHeader.secondSubtitle?.text
                thumbnail = detailHeader.thumbnail?.urls?.bestUrl()
                artists = parseArtistRuns(detailHeader.subtitle?.runs.orEmpty())
                description = detailHeader.description?.text
            }

            else -> return null
        }

        val shelfContents = response.secondarySectionList()?.contents
            ?: response.primarySectionList()?.contents
        val songShelf = shelfContents
            ?.firstNotNullOfOrNull { it.musicShelfRenderer ?: it.musicPlaylistShelfRenderer?.asMusicShelf() }

        val albumRef = AlbumRef(title, browseId)
        val songs = songShelf?.contents.orEmpty()
            .mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull {
                ItemParser.parseListItem(it, fallbackAlbum = albumRef, fallbackThumbnail = thumbnail) as? SongItem
            }
            .map { song -> if (song.artists.isEmpty()) song.copy(artists = artists) else song }

        val otherVersions = shelfContents.orEmpty()
            .firstNotNullOfOrNull { it.musicCarouselShelfRenderer }
            ?.contents.orEmpty()
            .mapNotNull { it.musicTwoRowItemRenderer?.let(ItemParser::parseTwoRowItem) as? AlbumItem }

        return AlbumPage(
            album = AlbumItem(
                browseId = browseId,
                title = title,
                artists = artists,
                year = subtitle?.split("•")?.lastOrNull()?.trim()?.takeIf { s -> s.all(Char::isDigit) },
                thumbnailUrl = thumbnail,
                subtitle = subtitle,
            ),
            songs = songs,
            description = description,
            yearAndCount = secondSubtitle,
            otherVersions = otherVersions,
        )
    }

    private fun MusicPlaylistShelfRenderer.asMusicShelf() = MusicShelfRenderer(
        title = null,
        contents = contents,
        continuations = continuations,
    )

    // ---------- Artist ----------

    fun parseArtist(browseId: String, response: BrowseResponse): ArtistPage? {
        val header = response.header?.musicImmersiveHeaderRenderer
        val visualHeader = response.header?.musicVisualHeaderRenderer
        val title = header?.title?.text ?: visualHeader?.title?.text ?: ""
        if (title.isEmpty()) return null

        val thumbnail = header?.thumbnail?.urls?.bestUrl()
            ?: visualHeader?.foregroundThumbnail?.urls?.bestUrl()
            ?: visualHeader?.thumbnail?.urls?.bestUrl()

        val shufflePlaylistId = header?.playButton?.buttonRenderer
            ?.navigationEndpoint?.watchPlaylistEndpoint?.playlistId
            ?: header?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint?.playlistId
        val radioPlaylistId = header?.startRadioButton?.buttonRenderer
            ?.navigationEndpoint?.watchPlaylistEndpoint?.playlistId
            ?: header?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint?.playlistId

        val sections = response.primarySectionList()?.contents.orEmpty().mapNotNull { content ->
            content.musicDescriptionShelfRenderer?.let { return@mapNotNull null }
            parseShelf(content)
        }

        val description = response.primarySectionList()?.contents.orEmpty()
            .firstNotNullOfOrNull { it.musicDescriptionShelfRenderer?.description?.text }
            ?: header?.description?.text

        return ArtistPage(
            artist = ArtistItem(
                browseId = browseId,
                title = title,
                thumbnailUrl = thumbnail,
            ),
            description = description,
            shuffplaylistId = shufflePlaylistId,
            radioPlaylistId = radioPlaylistId,
            sections = sections,
        )
    }

    // ---------- Playlist ----------

    fun parsePlaylist(browseId: String, response: BrowseResponse): PlaylistPage? {
        val header = response.header?.musicDetailHeaderRenderer
            ?: response.header?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicDetailHeaderRenderer
        val responsiveHeader = response.primarySectionList()?.contents
            ?.firstNotNullOfOrNull { it.musicResponsiveHeaderRenderer }
            ?: response.header?.musicResponsiveHeaderRenderer

        val title = header?.title?.text
            ?: responsiveHeader?.title?.text
            ?: ""

        val thumbnail = header?.thumbnail?.urls?.bestUrl()
            ?: responsiveHeader?.thumbnail?.urls?.bestUrl()

        val subtitle = header?.subtitle?.text ?: responsiveHeader?.subtitle?.text
        val secondSubtitle = header?.secondSubtitle?.text ?: responsiveHeader?.secondSubtitle?.text

        val shelf = response.secondarySectionList()?.contents
            ?.firstNotNullOfOrNull { it.musicPlaylistShelfRenderer ?: it.musicShelfRenderer?.asPlaylistShelf() }
            ?: response.primarySectionList()?.contents
                ?.firstNotNullOfOrNull { it.musicPlaylistShelfRenderer ?: it.musicShelfRenderer?.asPlaylistShelf() }

        val songs = shelf?.contents.orEmpty()
            .mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { ItemParser.parseListItem(it) as? SongItem }

        val continuation = shelf?.continuations?.firstNotNullOfOrNull { it.token }
            ?: shelf?.contents?.firstNotNullOfOrNull { it.continuationItemRenderer?.token }

        return PlaylistPage(
            playlist = PlaylistItem(
                browseId = browseId,
                title = title,
                author = subtitle,
                songCountText = secondSubtitle,
                thumbnailUrl = thumbnail,
            ),
            songs = songs,
            subtitle = subtitle,
            secondSubtitle = secondSubtitle,
            continuation = continuation,
        )
    }

    private fun MusicShelfRenderer.asPlaylistShelf() = MusicPlaylistShelfRenderer(
        playlistId = null,
        contents = contents,
        continuations = continuations,
    )

    fun parsePlaylistContinuation(response: BrowseResponse): Pair<List<SongItem>, String?> {
        val shelf = response.continuationContents?.musicPlaylistShelfContinuation
            ?: response.continuationContents?.musicShelfContinuation?.asPlaylistShelf()
        if (shelf != null) {
            val songs = shelf.contents.orEmpty()
                .mapNotNull { it.musicResponsiveListItemRenderer }
                .mapNotNull { ItemParser.parseListItem(it) as? SongItem }
            return songs to shelf.continuations?.firstNotNullOfOrNull { it.token }
        }
        val appended = response.onResponseReceivedActions.orEmpty()
            .flatMap { it.appendContinuationItemsAction?.continuationItems.orEmpty() }
        // Continuation items arrive as raw list contents in the two-column layout.
        val songs = appended
            .flatMap { it.musicPlaylistShelfRenderer?.contents ?: it.musicShelfRenderer?.contents ?: emptyList() }
            .mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { ItemParser.parseListItem(it) as? SongItem }
        return songs to null
    }

    // ---------- Library ----------

    fun parseLibraryItems(response: BrowseResponse): Pair<List<YtItem>, String?> {
        val sectionList = response.primarySectionList()
        if (sectionList != null) {
            val items = mutableListOf<YtItem>()
            var continuation: String? = null
            sectionList.contents.orEmpty().forEach { content ->
                content.gridRenderer?.let { grid ->
                    items += grid.items.orEmpty().mapNotNull { item ->
                        item.musicTwoRowItemRenderer?.let(ItemParser::parseTwoRowItem)
                    }
                    continuation = continuation ?: grid.continuations?.firstNotNullOfOrNull { it.token }
                        ?: grid.items?.firstNotNullOfOrNull { it.continuationItemRenderer?.token }
                }
                content.musicShelfRenderer?.let { shelf ->
                    items += shelf.contents.orEmpty()
                        .mapNotNull { it.musicResponsiveListItemRenderer }
                        .mapNotNull { ItemParser.parseListItem(it) }
                    continuation = continuation ?: shelf.continuations?.firstNotNullOfOrNull { it.token }
                        ?: shelf.contents?.firstNotNullOfOrNull { it.continuationItemRenderer?.token }
                }
                content.musicCarouselShelfRenderer?.let { carousel ->
                    items += carousel.contents.orEmpty().mapNotNull(ItemParser::parseCarouselContent)
                }
            }
            return items.toList() to continuation
        }
        return parseLibraryContinuation(response)
    }

    fun parseLibraryContinuation(response: BrowseResponse): Pair<List<YtItem>, String?> {
        response.continuationContents?.gridContinuation?.let { grid ->
            val items = grid.items.orEmpty().mapNotNull { it.musicTwoRowItemRenderer?.let(ItemParser::parseTwoRowItem) }
            return items to grid.continuations?.firstNotNullOfOrNull { it.token }
        }
        response.continuationContents?.musicShelfContinuation?.let { shelf ->
            val items = shelf.contents.orEmpty()
                .mapNotNull { it.musicResponsiveListItemRenderer }
                .mapNotNull { ItemParser.parseListItem(it) }
            return items to shelf.continuations?.firstNotNullOfOrNull { it.token }
        }
        return emptyList<YtItem>() to null
    }
}
