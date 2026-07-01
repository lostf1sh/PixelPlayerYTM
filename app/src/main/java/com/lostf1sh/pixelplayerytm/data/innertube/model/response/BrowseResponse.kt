package com.lostf1sh.pixelplayerytm.data.innertube.model.response

import kotlinx.serialization.Serializable

@Serializable
data class BrowseResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
    val header: Header? = null,
    val background: ThumbnailRenderer? = null,
    val onResponseReceivedActions: List<ResponseAction>? = null,
    val responseContext: ResponseContext? = null,
) {
    @Serializable
    data class Contents(
        val singleColumnBrowseResultsRenderer: SingleColumnBrowseResultsRenderer? = null,
        val twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRenderer? = null,
        val sectionListRenderer: SectionListRenderer? = null,
    )

    @Serializable
    data class SingleColumnBrowseResultsRenderer(
        val tabs: List<Tabs.Tab>? = null,
    )

    @Serializable
    data class TwoColumnBrowseResultsRenderer(
        val tabs: List<Tabs.Tab>? = null,
        val secondaryContents: SecondaryContents? = null,
    ) {
        @Serializable
        data class SecondaryContents(
            val sectionListRenderer: SectionListRenderer? = null,
        )
    }

    @Serializable
    data class ContinuationContents(
        val sectionListContinuation: SectionListRenderer? = null,
        val musicShelfContinuation: MusicShelfRenderer? = null,
        val musicPlaylistShelfContinuation: MusicPlaylistShelfRenderer? = null,
        val gridContinuation: GridRenderer? = null,
        val playlistPanelContinuation: PlaylistPanelRenderer? = null,
    )

    @Serializable
    data class ResponseAction(
        val appendContinuationItemsAction: AppendContinuationItemsAction? = null,
    ) {
        @Serializable
        data class AppendContinuationItemsAction(
            val continuationItems: List<SectionListRenderer.Content>? = null,
        )
    }

    @Serializable
    data class Header(
        val musicImmersiveHeaderRenderer: MusicImmersiveHeaderRenderer? = null,
        val musicDetailHeaderRenderer: MusicDetailHeaderRenderer? = null,
        val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRenderer? = null,
        val musicVisualHeaderRenderer: MusicVisualHeaderRenderer? = null,
        val musicHeaderRenderer: MusicHeaderRenderer? = null,
        val musicEditablePlaylistDetailHeaderRenderer: MusicEditablePlaylistDetailHeaderRenderer? = null,
    )

    /** Artist page header. */
    @Serializable
    data class MusicImmersiveHeaderRenderer(
        val title: Runs? = null,
        val description: Runs? = null,
        val thumbnail: ThumbnailRenderer? = null,
        val playButton: Button? = null,
        val startRadioButton: Button? = null,
    )

    /** Classic album/playlist header. */
    @Serializable
    data class MusicDetailHeaderRenderer(
        val title: Runs? = null,
        val subtitle: Runs? = null,
        val secondSubtitle: Runs? = null,
        val description: Runs? = null,
        val thumbnail: ThumbnailRenderer? = null,
        val menu: Menu? = null,
    )

    /** Newer two-column album/playlist header. */
    @Serializable
    data class MusicResponsiveHeaderRenderer(
        val title: Runs? = null,
        val subtitle: Runs? = null,
        val secondSubtitle: Runs? = null,
        val straplineTextOne: Runs? = null,
        val straplineThumbnail: ThumbnailRenderer? = null,
        val description: Description? = null,
        val thumbnail: ThumbnailRenderer? = null,
        val buttons: List<Button>? = null,
    ) {
        @Serializable
        data class Description(
            val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer? = null,
        )
    }

    @Serializable
    data class MusicVisualHeaderRenderer(
        val title: Runs? = null,
        val foregroundThumbnail: ThumbnailRenderer? = null,
        val thumbnail: ThumbnailRenderer? = null,
    )

    @Serializable
    data class MusicHeaderRenderer(
        val title: Runs? = null,
    )

    @Serializable
    data class MusicEditablePlaylistDetailHeaderRenderer(
        val header: Header? = null,
    )
}
