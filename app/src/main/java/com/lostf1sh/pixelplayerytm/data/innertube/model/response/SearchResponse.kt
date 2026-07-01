package com.lostf1sh.pixelplayerytm.data.innertube.model.response

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
) {
    @Serializable
    data class Contents(
        val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer? = null,
    ) {
        @Serializable
        data class TabbedSearchResultsRenderer(
            val tabs: List<Tabs.Tab>? = null,
        )
    }

    @Serializable
    data class ContinuationContents(
        val musicShelfContinuation: MusicShelfRenderer? = null,
    )
}

@Serializable
data class GetSearchSuggestionsResponse(
    val contents: List<Content>? = null,
) {
    @Serializable
    data class Content(
        val searchSuggestionsSectionRenderer: SearchSuggestionsSectionRenderer? = null,
    ) {
        @Serializable
        data class SearchSuggestionsSectionRenderer(
            val contents: List<Suggestion>? = null,
        ) {
            @Serializable
            data class Suggestion(
                val searchSuggestionRenderer: SearchSuggestionRenderer? = null,
                val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
            ) {
                @Serializable
                data class SearchSuggestionRenderer(
                    val suggestion: Runs? = null,
                    val navigationEndpoint: NavigationEndpoint? = null,
                )
            }
        }
    }
}
