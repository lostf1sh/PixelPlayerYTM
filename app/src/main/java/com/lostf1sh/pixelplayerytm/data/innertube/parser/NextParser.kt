package com.lostf1sh.pixelplayerytm.data.innertube.parser

import com.lostf1sh.pixelplayerytm.data.innertube.model.response.NextResponse
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.PlaylistPanelRenderer
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.PlaylistPanelVideoRenderer
import com.lostf1sh.pixelplayerytm.domain.model.AlbumRef
import com.lostf1sh.pixelplayerytm.domain.model.NextPage
import com.lostf1sh.pixelplayerytm.domain.model.SongItem

object NextParser {

    fun parse(response: NextResponse): NextPage {
        val panel = response.contents?.singleColumnMusicWatchNextResultsRenderer
            ?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.musicQueueRenderer?.content?.playlistPanelRenderer
            ?: response.continuationContents?.playlistPanelContinuation
            ?: return NextPage(items = emptyList())
        return parsePanel(panel)
    }

    private fun parsePanel(panel: PlaylistPanelRenderer): NextPage {
        val items = mutableListOf<SongItem>()
        var currentIndex: Int? = null
        var radioPlaylistId: String? = null
        var radioParams: String? = null

        panel.contents.orEmpty().forEach { content ->
            val renderer = content.playlistPanelVideoRenderer
                ?: content.playlistPanelVideoWrapperRenderer?.primaryRenderer?.playlistPanelVideoRenderer
            if (renderer != null) {
                parseSong(renderer)?.let { song ->
                    if (renderer.selected == true) currentIndex = items.size
                    items += song
                }
                return@forEach
            }
            content.automixPreviewVideoRenderer?.content?.automixPlaylistVideoRenderer
                ?.navigationEndpoint?.watchPlaylistEndpoint?.let { automix ->
                    radioPlaylistId = automix.playlistId
                    radioParams = automix.params
                }
        }

        return NextPage(
            items = items,
            currentIndex = currentIndex,
            playlistId = panel.playlistId,
            continuation = panel.continuations?.firstNotNullOfOrNull { it.token },
            radioPlaylistId = radioPlaylistId,
            radioParams = radioParams,
        )
    }

    private fun parseSong(renderer: PlaylistPanelVideoRenderer): SongItem? {
        val videoId = renderer.videoId
            ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
            ?: return null
        val title = renderer.title?.text.orEmpty()
        if (title.isEmpty()) return null

        val bylineRuns = renderer.longBylineText?.runs
            ?: renderer.shortBylineText?.runs
            .orEmpty()
        val artists = parseArtistRuns(bylineRuns.orEmpty())
        val album = bylineRuns.orEmpty().firstNotNullOfOrNull { run ->
            val browse = run.navigationEndpoint?.browseEndpoint
            if (browse?.pageType == PageType.ALBUM) AlbumRef(run.text, browse.browseId) else null
        }

        // Fallback: byline without endpoints — "Artist • Album • 3:21"
        val fallbackArtists = if (artists.isEmpty()) {
            renderer.longBylineText?.text?.split(" • ")?.firstOrNull()
                ?.let { listOf(com.lostf1sh.pixelplayerytm.domain.model.ArtistRef(it)) }
                .orEmpty()
        } else {
            artists
        }

        return SongItem(
            videoId = videoId,
            title = title,
            artists = fallbackArtists,
            album = album,
            durationText = renderer.lengthText?.text,
            thumbnailUrl = renderer.thumbnail?.thumbnails?.bestUrl(),
        )
    }
}
