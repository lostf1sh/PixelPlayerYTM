package com.lostf1sh.pixelplayerytm.data.innertube.model.response

import kotlinx.serialization.Serializable

// ---------- Text ----------

@Serializable
data class Runs(
    val runs: List<Run>? = null,
) {
    val text: String
        get() = runs?.joinToString("") { it.text } ?: ""
}

@Serializable
data class Run(
    val text: String,
    val navigationEndpoint: NavigationEndpoint? = null,
)

// ---------- Thumbnails ----------

@Serializable
data class Thumbnail(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class Thumbnails(
    val thumbnails: List<Thumbnail>? = null,
)

@Serializable
data class MusicThumbnailRenderer(
    val thumbnail: Thumbnails? = null,
)

@Serializable
data class ThumbnailRenderer(
    val musicThumbnailRenderer: MusicThumbnailRenderer? = null,
    val croppedSquareThumbnailRenderer: MusicThumbnailRenderer? = null,
) {
    val urls: List<Thumbnail>
        get() = musicThumbnailRenderer?.thumbnail?.thumbnails
            ?: croppedSquareThumbnailRenderer?.thumbnail?.thumbnails
            ?: emptyList()
}

// ---------- Endpoints ----------

@Serializable
data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint? = null,
    val browseEndpoint: BrowseEndpoint? = null,
    val watchPlaylistEndpoint: WatchPlaylistEndpoint? = null,
    val searchEndpoint: SearchEndpoint? = null,
)

@Serializable
data class WatchEndpoint(
    val videoId: String? = null,
    val playlistId: String? = null,
    val playlistSetVideoId: String? = null,
    val params: String? = null,
    val index: Int? = null,
    val watchEndpointMusicSupportedConfigs: WatchEndpointMusicSupportedConfigs? = null,
) {
    @Serializable
    data class WatchEndpointMusicSupportedConfigs(
        val watchEndpointMusicConfig: WatchEndpointMusicConfig? = null,
    ) {
        @Serializable
        data class WatchEndpointMusicConfig(
            val musicVideoType: String? = null,
        )
    }
}

@Serializable
data class BrowseEndpoint(
    val browseId: String? = null,
    val params: String? = null,
    val browseEndpointContextSupportedConfigs: BrowseEndpointContextSupportedConfigs? = null,
) {
    val pageType: String?
        get() = browseEndpointContextSupportedConfigs
            ?.browseEndpointContextMusicConfig?.pageType

    @Serializable
    data class BrowseEndpointContextSupportedConfigs(
        val browseEndpointContextMusicConfig: BrowseEndpointContextMusicConfig? = null,
    ) {
        @Serializable
        data class BrowseEndpointContextMusicConfig(
            val pageType: String? = null,
        )
    }
}

@Serializable
data class WatchPlaylistEndpoint(
    val playlistId: String? = null,
    val params: String? = null,
)

@Serializable
data class SearchEndpoint(
    val query: String? = null,
    val params: String? = null,
)

// ---------- Buttons / menu ----------

@Serializable
data class Button(
    val buttonRenderer: ButtonRenderer? = null,
)

@Serializable
data class ButtonRenderer(
    val text: Runs? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
    val command: NavigationEndpoint? = null,
    val icon: Icon? = null,
)

@Serializable
data class Icon(
    val iconType: String? = null,
)

@Serializable
data class Badge(
    val musicInlineBadgeRenderer: MusicInlineBadgeRenderer? = null,
) {
    @Serializable
    data class MusicInlineBadgeRenderer(
        val icon: Icon? = null,
    )
}

@Serializable
data class Menu(
    val menuRenderer: MenuRenderer? = null,
) {
    @Serializable
    data class MenuRenderer(
        val items: List<Item>? = null,
        val topLevelButtons: List<TopLevelButton>? = null,
    ) {
        @Serializable
        data class Item(
            val menuNavigationItemRenderer: MenuNavigationItemRenderer? = null,
            val menuServiceItemRenderer: MenuServiceItemRenderer? = null,
            val toggleMenuServiceItemRenderer: ToggleMenuServiceItemRenderer? = null,
        )

        @Serializable
        data class MenuNavigationItemRenderer(
            val text: Runs? = null,
            val icon: Icon? = null,
            val navigationEndpoint: NavigationEndpoint? = null,
        )

        @Serializable
        data class MenuServiceItemRenderer(
            val text: Runs? = null,
            val icon: Icon? = null,
            val serviceEndpoint: ServiceEndpoint? = null,
        )

        @Serializable
        data class ToggleMenuServiceItemRenderer(
            val defaultText: Runs? = null,
            val defaultIcon: Icon? = null,
            val defaultServiceEndpoint: ServiceEndpoint? = null,
            val toggledText: Runs? = null,
            val toggledIcon: Icon? = null,
            val toggledServiceEndpoint: ServiceEndpoint? = null,
        )

        @Serializable
        data class TopLevelButton(
            val likeButtonRenderer: LikeButtonRenderer? = null,
            val buttonRenderer: ButtonRenderer? = null,
        ) {
            @Serializable
            data class LikeButtonRenderer(
                val likeStatus: String? = null,
                val likesAllowed: Boolean? = null,
            )
        }
    }
}

@Serializable
data class ServiceEndpoint(
    val queueAddEndpoint: QueueAddEndpoint? = null,
    val likeEndpoint: LikeEndpoint? = null,
    val feedbackEndpoint: FeedbackEndpoint? = null,
) {
    @Serializable
    data class QueueAddEndpoint(
        val queueTarget: QueueTarget? = null,
    ) {
        @Serializable
        data class QueueTarget(
            val videoId: String? = null,
            val playlistId: String? = null,
        )
    }

    @Serializable
    data class LikeEndpoint(
        val status: String? = null,
        val target: Target? = null,
    ) {
        @Serializable
        data class Target(
            val videoId: String? = null,
            val playlistId: String? = null,
        )
    }

    @Serializable
    data class FeedbackEndpoint(
        val feedbackToken: String? = null,
    )
}

// ---------- Item renderers ----------

@Serializable
data class PlaylistItemData(
    val videoId: String? = null,
    val playlistSetVideoId: String? = null,
)

@Serializable
data class Overlay(
    val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer? = null,
) {
    @Serializable
    data class MusicItemThumbnailOverlayRenderer(
        val content: Content? = null,
    ) {
        @Serializable
        data class Content(
            val musicPlayButtonRenderer: MusicPlayButtonRenderer? = null,
        ) {
            @Serializable
            data class MusicPlayButtonRenderer(
                val playNavigationEndpoint: NavigationEndpoint? = null,
            )
        }
    }
}

@Serializable
data class MusicResponsiveListItemRenderer(
    val flexColumns: List<FlexColumn>? = null,
    val fixedColumns: List<FixedColumn>? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
    val playlistItemData: PlaylistItemData? = null,
    val overlay: Overlay? = null,
    val menu: Menu? = null,
    val badges: List<Badge>? = null,
    val index: Runs? = null,
) {
    @Serializable
    data class FlexColumn(
        val musicResponsiveListItemFlexColumnRenderer: ColumnRenderer? = null,
    )

    @Serializable
    data class FixedColumn(
        val musicResponsiveListItemFixedColumnRenderer: ColumnRenderer? = null,
    )

    @Serializable
    data class ColumnRenderer(
        val text: Runs? = null,
    )
}

@Serializable
data class MusicTwoRowItemRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val thumbnailRenderer: ThumbnailRenderer? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
    val menu: Menu? = null,
    val thumbnailOverlay: Overlay? = null,
    val subtitleBadges: List<Badge>? = null,
)

/** Colored mood/genre chip button. */
@Serializable
data class MusicNavigationButtonRenderer(
    val buttonText: Runs? = null,
    val solid: Solid? = null,
    val clickCommand: NavigationEndpoint? = null,
) {
    @Serializable
    data class Solid(
        val leftStripeColor: Long? = null,
    )
}

// ---------- Shelves / sections ----------

@Serializable
data class ContinuationItemRenderer(
    val continuationEndpoint: ContinuationEndpoint? = null,
) {
    val token: String?
        get() = continuationEndpoint?.continuationCommand?.token

    @Serializable
    data class ContinuationEndpoint(
        val continuationCommand: ContinuationCommand? = null,
    ) {
        @Serializable
        data class ContinuationCommand(
            val token: String? = null,
        )
    }
}

@Serializable
data class Continuation(
    val nextContinuationData: Data? = null,
    val reloadContinuationData: Data? = null,
) {
    val token: String?
        get() = nextContinuationData?.continuation ?: reloadContinuationData?.continuation

    @Serializable
    data class Data(
        val continuation: String? = null,
    )
}

@Serializable
data class MusicShelfRenderer(
    val title: Runs? = null,
    val contents: List<Content>? = null,
    val continuations: List<Continuation>? = null,
    val bottomEndpoint: NavigationEndpoint? = null,
) {
    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    )
}

@Serializable
data class MusicPlaylistShelfRenderer(
    val playlistId: String? = null,
    val contents: List<MusicShelfRenderer.Content>? = null,
    val continuations: List<Continuation>? = null,
    val collapsedItemCount: Int? = null,
)

@Serializable
data class MusicCarouselShelfRenderer(
    val header: Header? = null,
    val contents: List<Content>? = null,
) {
    @Serializable
    data class Header(
        val musicCarouselShelfBasicHeaderRenderer: BasicHeader? = null,
    ) {
        @Serializable
        data class BasicHeader(
            val title: Runs? = null,
            val strapline: Runs? = null,
            val thumbnail: ThumbnailRenderer? = null,
            val moreContentButton: Button? = null,
        )
    }

    @Serializable
    data class Content(
        val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null,
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
        val musicNavigationButtonRenderer: MusicNavigationButtonRenderer? = null,
    )
}

/** Top result card in search. */
@Serializable
data class MusicCardShelfRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val onTap: NavigationEndpoint? = null,
    val contents: List<MusicShelfRenderer.Content>? = null,
    val menu: Menu? = null,
)

@Serializable
data class MusicDescriptionShelfRenderer(
    val header: Runs? = null,
    val description: Runs? = null,
)

@Serializable
data class GridRenderer(
    val header: Header? = null,
    val items: List<Item>? = null,
    val continuations: List<Continuation>? = null,
) {
    @Serializable
    data class Header(
        val gridHeaderRenderer: GridHeaderRenderer? = null,
    ) {
        @Serializable
        data class GridHeaderRenderer(
            val title: Runs? = null,
        )
    }

    @Serializable
    data class Item(
        val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null,
        val musicNavigationButtonRenderer: MusicNavigationButtonRenderer? = null,
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    )
}

@Serializable
data class SectionListRenderer(
    val contents: List<Content>? = null,
    val continuations: List<Continuation>? = null,
    val header: Header? = null,
) {
    @Serializable
    data class Content(
        val musicShelfRenderer: MusicShelfRenderer? = null,
        val musicCarouselShelfRenderer: MusicCarouselShelfRenderer? = null,
        val musicCardShelfRenderer: MusicCardShelfRenderer? = null,
        val musicPlaylistShelfRenderer: MusicPlaylistShelfRenderer? = null,
        val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer? = null,
        val musicResponsiveHeaderRenderer: BrowseResponse.MusicResponsiveHeaderRenderer? = null,
        val gridRenderer: GridRenderer? = null,
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    )

    @Serializable
    data class Header(
        val chipCloudRenderer: ChipCloudRenderer? = null,
    ) {
        @Serializable
        data class ChipCloudRenderer(
            val chips: List<Chip>? = null,
        ) {
            @Serializable
            data class Chip(
                val chipCloudChipRenderer: ChipCloudChipRenderer? = null,
            ) {
                @Serializable
                data class ChipCloudChipRenderer(
                    val text: Runs? = null,
                    val navigationEndpoint: NavigationEndpoint? = null,
                    val isSelected: Boolean? = null,
                )
            }
        }
    }
}

// ---------- Tabs ----------

@Serializable
data class Tabs(
    val tabs: List<Tab>? = null,
) {
    @Serializable
    data class Tab(
        val tabRenderer: TabRenderer? = null,
    ) {
        @Serializable
        data class TabRenderer(
            val title: String? = null,
            val content: Content? = null,
            val endpoint: NavigationEndpoint? = null,
        ) {
            @Serializable
            data class Content(
                val sectionListRenderer: SectionListRenderer? = null,
                val musicQueueRenderer: MusicQueueRenderer? = null,
            )
        }
    }
}

@Serializable
data class MusicQueueRenderer(
    val content: Content? = null,
) {
    @Serializable
    data class Content(
        val playlistPanelRenderer: PlaylistPanelRenderer? = null,
    )
}

@Serializable
data class PlaylistPanelRenderer(
    val title: String? = null,
    val contents: List<Content>? = null,
    val continuations: List<Continuation>? = null,
    val playlistId: String? = null,
) {
    @Serializable
    data class Content(
        val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer? = null,
        val playlistPanelVideoWrapperRenderer: PlaylistPanelVideoWrapperRenderer? = null,
        val automixPreviewVideoRenderer: AutomixPreviewVideoRenderer? = null,
    )
}

@Serializable
data class PlaylistPanelVideoRenderer(
    val videoId: String? = null,
    val title: Runs? = null,
    val longBylineText: Runs? = null,
    val shortBylineText: Runs? = null,
    val lengthText: Runs? = null,
    val thumbnail: Thumbnails? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
    val selected: Boolean? = null,
)

@Serializable
data class PlaylistPanelVideoWrapperRenderer(
    val primaryRenderer: Primary? = null,
) {
    @Serializable
    data class Primary(
        val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer? = null,
    )
}

@Serializable
data class AutomixPreviewVideoRenderer(
    val content: Content? = null,
) {
    @Serializable
    data class Content(
        val automixPlaylistVideoRenderer: AutomixPlaylistVideoRenderer? = null,
    ) {
        @Serializable
        data class AutomixPlaylistVideoRenderer(
            val navigationEndpoint: NavigationEndpoint? = null,
        )
    }
}

// ---------- Response context ----------

@Serializable
data class ResponseContext(
    val visitorData: String? = null,
)
