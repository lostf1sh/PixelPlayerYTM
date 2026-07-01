package com.lostf1sh.pixelplayerytm.data.innertube.model.response

import kotlinx.serialization.Serializable

@Serializable
data class NextResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
) {
    @Serializable
    data class Contents(
        val singleColumnMusicWatchNextResultsRenderer: SingleColumnMusicWatchNextResultsRenderer? = null,
    ) {
        @Serializable
        data class SingleColumnMusicWatchNextResultsRenderer(
            val tabbedRenderer: TabbedRenderer? = null,
        ) {
            @Serializable
            data class TabbedRenderer(
                val watchNextTabbedResultsRenderer: WatchNextTabbedResultsRenderer? = null,
            ) {
                @Serializable
                data class WatchNextTabbedResultsRenderer(
                    val tabs: List<Tabs.Tab>? = null,
                )
            }
        }
    }

    @Serializable
    data class ContinuationContents(
        val playlistPanelContinuation: PlaylistPanelRenderer? = null,
    )
}
