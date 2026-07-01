package com.lostf1sh.pixelplayerytm.data.innertube.parser

import com.lostf1sh.pixelplayerytm.data.innertube.model.response.GetSearchSuggestionsResponse
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.SearchResponse
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.SectionListRenderer
import com.lostf1sh.pixelplayerytm.domain.model.SearchResultPage
import com.lostf1sh.pixelplayerytm.domain.model.SearchSuggestions
import com.lostf1sh.pixelplayerytm.domain.model.SearchSummaryPage
import com.lostf1sh.pixelplayerytm.domain.model.Shelf
import com.lostf1sh.pixelplayerytm.domain.model.YtItem

object SearchParser {

    private fun SearchResponse.sectionList(): SectionListRenderer? =
        contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer

    /** Filtered search: a single musicShelf of results with a continuation. */
    fun parseResults(response: SearchResponse): SearchResultPage {
        val shelf = response.sectionList()?.contents.orEmpty()
            .firstNotNullOfOrNull { it.musicShelfRenderer }
        val items = shelf?.contents.orEmpty()
            .mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { ItemParser.parseListItem(it) }
        return SearchResultPage(
            items = items,
            continuation = shelf?.continuations?.firstNotNullOfOrNull { it.token }
                ?: shelf?.contents?.firstNotNullOfOrNull { it.continuationItemRenderer?.token },
        )
    }

    fun parseContinuation(response: SearchResponse): SearchResultPage {
        val shelf = response.continuationContents?.musicShelfContinuation
        val items = shelf?.contents.orEmpty()
            .mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { ItemParser.parseListItem(it) }
        return SearchResultPage(
            items = items,
            continuation = shelf?.continuations?.firstNotNullOfOrNull { it.token },
        )
    }

    /** Unfiltered search: top-result card + category shelves. */
    fun parseSummary(response: SearchResponse): SearchSummaryPage {
        val shelves = response.sectionList()?.contents.orEmpty().mapNotNull { content ->
            content.musicCardShelfRenderer?.let { card ->
                val topItem = parseTopResultCard(card)
                val extra = card.contents.orEmpty()
                    .mapNotNull { it.musicResponsiveListItemRenderer }
                    .mapNotNull { ItemParser.parseListItem(it) }
                val items = listOfNotNull(topItem) + extra
                if (items.isEmpty()) return@mapNotNull null
                return@mapNotNull Shelf(
                    title = "Top result",
                    items = items,
                    isVerticalList = true,
                )
            }
            content.musicShelfRenderer?.let { shelf ->
                val items = shelf.contents.orEmpty()
                    .mapNotNull { it.musicResponsiveListItemRenderer }
                    .mapNotNull { ItemParser.parseListItem(it) }
                if (items.isEmpty()) return@mapNotNull null
                return@mapNotNull Shelf(
                    title = shelf.title?.text.orEmpty(),
                    items = items,
                    isVerticalList = true,
                )
            }
            null
        }
        return SearchSummaryPage(shelves)
    }

    private fun parseTopResultCard(
        card: com.lostf1sh.pixelplayerytm.data.innertube.model.response.MusicCardShelfRenderer,
    ): YtItem? {
        val title = card.title?.text.orEmpty()
        if (title.isEmpty()) return null
        val thumbnail = card.thumbnail?.urls?.bestUrl()
        val subtitleRuns = card.subtitle?.runs.orEmpty()

        card.onTap?.watchEndpoint?.videoId?.let { videoId ->
            return com.lostf1sh.pixelplayerytm.domain.model.SongItem(
                videoId = videoId,
                title = title,
                artists = parseArtistRuns(subtitleRuns),
                thumbnailUrl = thumbnail,
            )
        }
        val browse = card.onTap?.browseEndpoint ?: return null
        val browseId = browse.browseId ?: return null
        return when (browse.pageType) {
            PageType.ARTIST, PageType.USER_CHANNEL ->
                com.lostf1sh.pixelplayerytm.domain.model.ArtistItem(
                    browseId = browseId,
                    title = title,
                    thumbnailUrl = thumbnail,
                    subtitle = card.subtitle?.text,
                )

            PageType.ALBUM, PageType.AUDIOBOOK ->
                com.lostf1sh.pixelplayerytm.domain.model.AlbumItem(
                    browseId = browseId,
                    title = title,
                    artists = parseArtistRuns(subtitleRuns),
                    thumbnailUrl = thumbnail,
                    subtitle = card.subtitle?.text,
                )

            PageType.PLAYLIST ->
                com.lostf1sh.pixelplayerytm.domain.model.PlaylistItem(
                    browseId = browseId,
                    title = title,
                    author = card.subtitle?.text,
                    thumbnailUrl = thumbnail,
                )

            else -> null
        }
    }

    fun parseSuggestions(response: GetSearchSuggestionsResponse): SearchSuggestions {
        val queries = mutableListOf<String>()
        val items = mutableListOf<YtItem>()
        response.contents.orEmpty()
            .flatMap { it.searchSuggestionsSectionRenderer?.contents.orEmpty() }
            .forEach { suggestion ->
                suggestion.searchSuggestionRenderer?.suggestion?.text
                    ?.takeIf { it.isNotBlank() }
                    ?.let { queries += it }
                suggestion.musicResponsiveListItemRenderer
                    ?.let { ItemParser.parseListItem(it) }
                    ?.let { items += it }
            }
        return SearchSuggestions(queries = queries, items = items)
    }
}
