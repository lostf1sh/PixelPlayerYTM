package com.lostf1sh.pixelplayerytm.data.innertube.parser

import com.lostf1sh.pixelplayerytm.data.innertube.model.response.Badge
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.MusicCarouselShelfRenderer
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.MusicNavigationButtonRenderer
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.MusicResponsiveListItemRenderer
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.MusicTwoRowItemRenderer
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.Run
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.Thumbnail
import com.lostf1sh.pixelplayerytm.domain.model.AlbumItem
import com.lostf1sh.pixelplayerytm.domain.model.AlbumRef
import com.lostf1sh.pixelplayerytm.domain.model.ArtistItem
import com.lostf1sh.pixelplayerytm.domain.model.ArtistRef
import com.lostf1sh.pixelplayerytm.domain.model.MoodItem
import com.lostf1sh.pixelplayerytm.domain.model.PlaylistItem
import com.lostf1sh.pixelplayerytm.domain.model.SongItem
import com.lostf1sh.pixelplayerytm.domain.model.YtItem

object PageType {
    const val ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
    const val AUDIOBOOK = "MUSIC_PAGE_TYPE_AUDIOBOOK"
    const val ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
    const val USER_CHANNEL = "MUSIC_PAGE_TYPE_USER_CHANNEL"
    const val PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
}

private val DURATION_REGEX = Regex("""^\d+:\d{2}(:\d{2})?$""")

internal fun List<Thumbnail>.bestUrl(): String? = maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url

internal fun List<Badge>?.isExplicit(): Boolean =
    this?.any { it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE" } == true

private fun Run.artistRefOrNull(): ArtistRef? {
    val browse = navigationEndpoint?.browseEndpoint ?: return null
    val pageType = browse.pageType
    return if (pageType == PageType.ARTIST || pageType == PageType.USER_CHANNEL ||
        (pageType == null && browse.browseId?.startsWith("UC") == true)
    ) {
        ArtistRef(text, browse.browseId)
    } else {
        null
    }
}

/**
 * Extracts artists from subtitle-style runs. Runs are separated by " • " and
 * artist names may themselves be split by " & ", ", " etc. without endpoints.
 */
internal fun parseArtistRuns(runs: List<Run>): List<ArtistRef> {
    val linked = runs.mapNotNull { it.artistRefOrNull() }
    if (linked.isNotEmpty()) return linked
    // Fallback: take the segment before the first bullet that isn't a known type label.
    return emptyList()
}

object ItemParser {

    /** Song/row items in lists (search results, album/playlist tracks, library rows). */
    fun parseListItem(
        renderer: MusicResponsiveListItemRenderer,
        fallbackAlbum: AlbumRef? = null,
        fallbackThumbnail: String? = null,
    ): YtItem? {
        val browseEndpoint = renderer.navigationEndpoint?.browseEndpoint
        if (browseEndpoint != null) {
            // Row that navigates to a page: artist / album / playlist row.
            return parseBrowseListItem(renderer, browseEndpoint.browseId ?: return null, browseEndpoint.pageType)
        }
        return parseSongListItem(renderer, fallbackAlbum, fallbackThumbnail)
    }

    private fun parseBrowseListItem(
        renderer: MusicResponsiveListItemRenderer,
        browseId: String,
        pageType: String?,
    ): YtItem? {
        val title = renderer.flexColumns?.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.text.orEmpty()
        if (title.isEmpty()) return null
        val subtitle = renderer.flexColumns?.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.text
        val thumbnail = renderer.thumbnail?.urls?.bestUrl()
        return when (pageType) {
            PageType.ARTIST, PageType.USER_CHANNEL -> ArtistItem(
                browseId = browseId,
                title = title,
                thumbnailUrl = thumbnail,
                subtitle = subtitle,
            )

            PageType.ALBUM, PageType.AUDIOBOOK -> AlbumItem(
                browseId = browseId,
                title = title,
                artists = parseArtistRuns(
                    renderer.flexColumns?.getOrNull(1)
                        ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs.orEmpty(),
                ),
                thumbnailUrl = thumbnail,
                explicit = renderer.badges.isExplicit(),
                subtitle = subtitle,
            )

            PageType.PLAYLIST -> PlaylistItem(
                browseId = browseId,
                title = title,
                author = subtitle,
                thumbnailUrl = thumbnail,
            )

            else -> when {
                browseId.startsWith("UC") -> ArtistItem(browseId, title, thumbnail, subtitle)
                browseId.startsWith("MPRE") -> AlbumItem(
                    browseId = browseId,
                    title = title,
                    thumbnailUrl = thumbnail,
                    subtitle = subtitle,
                )

                browseId.startsWith("VL") || browseId.startsWith("PL") -> PlaylistItem(
                    browseId = browseId,
                    title = title,
                    author = subtitle,
                    thumbnailUrl = thumbnail,
                )

                else -> null
            }
        }
    }

    private fun parseSongListItem(
        renderer: MusicResponsiveListItemRenderer,
        fallbackAlbum: AlbumRef?,
        fallbackThumbnail: String?,
    ): SongItem? {
        val videoId = renderer.playlistItemData?.videoId
            ?: renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId
            ?: renderer.flexColumns?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                ?.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId
            ?: return null

        val titleRuns = renderer.flexColumns?.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer?.text
        val title = titleRuns?.text.orEmpty()
        if (title.isEmpty()) return null

        val secondaryRuns = renderer.flexColumns?.drop(1)
            ?.mapNotNull { it.musicResponsiveListItemFlexColumnRenderer?.text?.runs }
            ?.flatten()
            .orEmpty()

        val artists = parseArtistRuns(secondaryRuns)
        val album = secondaryRuns.firstNotNullOfOrNull { run ->
            val browse = run.navigationEndpoint?.browseEndpoint
            if (browse?.pageType == PageType.ALBUM ||
                (browse?.pageType == null && browse?.browseId?.startsWith("MPRE") == true)
            ) {
                AlbumRef(run.text, browse.browseId)
            } else {
                null
            }
        } ?: fallbackAlbum

        val duration = renderer.fixedColumns?.firstOrNull()
            ?.musicResponsiveListItemFixedColumnRenderer?.text?.text
            ?.takeIf { DURATION_REGEX.matches(it) }
            ?: secondaryRuns.lastOrNull()?.text?.takeIf { DURATION_REGEX.matches(it) }

        val musicVideoType = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
            ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint
            ?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType

        return SongItem(
            videoId = videoId,
            title = title,
            artists = artists,
            album = album,
            durationText = duration,
            thumbnailUrl = renderer.thumbnail?.urls?.bestUrl() ?: fallbackThumbnail,
            explicit = renderer.badges.isExplicit(),
            isVideo = musicVideoType != null && musicVideoType != "MUSIC_VIDEO_TYPE_ATV",
        )
    }

    /** Card items in carousels/grids. */
    fun parseTwoRowItem(renderer: MusicTwoRowItemRenderer): YtItem? {
        val title = renderer.title?.text.orEmpty()
        if (title.isEmpty()) return null
        val thumbnail = renderer.thumbnailRenderer?.urls?.bestUrl()
        val subtitleRuns = renderer.subtitle?.runs.orEmpty()
        val subtitleText = renderer.subtitle?.text

        renderer.navigationEndpoint?.watchEndpoint?.let { watch ->
            val videoId = watch.videoId ?: return null
            val videoType = watch.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig?.musicVideoType
            return SongItem(
                videoId = videoId,
                title = title,
                artists = parseArtistRuns(subtitleRuns),
                thumbnailUrl = thumbnail,
                explicit = renderer.subtitleBadges.isExplicit(),
                isVideo = videoType != null && videoType != "MUSIC_VIDEO_TYPE_ATV",
            )
        }

        renderer.navigationEndpoint?.watchPlaylistEndpoint?.let { watchPlaylist ->
            val playlistId = watchPlaylist.playlistId ?: return null
            return PlaylistItem(
                browseId = "VL$playlistId",
                title = title,
                author = subtitleText,
                thumbnailUrl = thumbnail,
            )
        }

        val browse = renderer.navigationEndpoint?.browseEndpoint ?: return null
        val browseId = browse.browseId ?: return null
        return when (browse.pageType) {
            PageType.ALBUM, PageType.AUDIOBOOK -> AlbumItem(
                browseId = browseId,
                title = title,
                artists = parseArtistRuns(subtitleRuns),
                year = subtitleRuns.lastOrNull()?.text?.takeIf { it.length == 4 && it.all(Char::isDigit) },
                playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                    ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchPlaylistEndpoint?.playlistId,
                thumbnailUrl = thumbnail,
                explicit = renderer.subtitleBadges.isExplicit(),
                subtitle = subtitleText,
            )

            PageType.ARTIST, PageType.USER_CHANNEL -> ArtistItem(
                browseId = browseId,
                title = title,
                thumbnailUrl = thumbnail,
                subtitle = subtitleText,
            )

            PageType.PLAYLIST -> PlaylistItem(
                browseId = browseId,
                title = title,
                author = subtitleText,
                thumbnailUrl = thumbnail,
            )

            else -> when {
                browseId.startsWith("MPRE") -> AlbumItem(
                    browseId = browseId, title = title, thumbnailUrl = thumbnail, subtitle = subtitleText,
                )
                browseId.startsWith("UC") -> ArtistItem(browseId, title, thumbnail, subtitleText)
                browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RDCL") ->
                    PlaylistItem(browseId = browseId, title = title, author = subtitleText, thumbnailUrl = thumbnail)
                else -> null
            }
        }
    }

    fun parseMoodButton(renderer: MusicNavigationButtonRenderer): MoodItem? {
        val browse = renderer.clickCommand?.browseEndpoint ?: return null
        return MoodItem(
            title = renderer.buttonText?.text.orEmpty().ifEmpty { return null },
            browseId = browse.browseId ?: return null,
            params = browse.params,
            stripeColor = renderer.solid?.leftStripeColor,
        )
    }

    fun parseCarouselContent(content: MusicCarouselShelfRenderer.Content): YtItem? = when {
        content.musicTwoRowItemRenderer != null -> parseTwoRowItem(content.musicTwoRowItemRenderer)
        content.musicResponsiveListItemRenderer != null -> parseListItem(content.musicResponsiveListItemRenderer)
        content.musicNavigationButtonRenderer != null -> parseMoodButton(content.musicNavigationButtonRenderer)
        else -> null
    }
}
