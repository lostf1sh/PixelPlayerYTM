package com.lostf1sh.pixelplayerytm.domain.model

/** A horizontal shelf (carousel) or vertical list section. */
data class Shelf(
    val title: String,
    val items: List<YtItem>,
    /** browseId+params to open the full list, if the shelf has a "More" button. */
    val moreBrowseId: String? = null,
    val moreParams: String? = null,
    /** Vertical list (musicShelf) vs horizontal carousel. */
    val isVerticalList: Boolean = false,
)

data class HomePage(
    val shelves: List<Shelf>,
    val continuation: String? = null,
)

data class AlbumPage(
    val album: AlbumItem,
    val songs: List<SongItem>,
    val description: String? = null,
    val yearAndCount: String? = null,
    val otherVersions: List<AlbumItem> = emptyList(),
)

data class ArtistPage(
    val artist: ArtistItem,
    val description: String? = null,
    val shuffplaylistId: String? = null,
    val radioPlaylistId: String? = null,
    val sections: List<Shelf>,
)

data class PlaylistPage(
    val playlist: PlaylistItem,
    val songs: List<SongItem>,
    val subtitle: String? = null,
    val secondSubtitle: String? = null,
    val continuation: String? = null,
)

data class SearchResultPage(
    val items: List<YtItem>,
    val continuation: String? = null,
)

/** Unfiltered ("all") search: top result card + per-category shelves. */
data class SearchSummaryPage(
    val shelves: List<Shelf>,
)

data class SearchSuggestions(
    val queries: List<String>,
    val items: List<YtItem> = emptyList(),
)

/** Result of the `next` endpoint: watch queue / radio. */
data class NextPage(
    val items: List<SongItem>,
    val currentIndex: Int? = null,
    val playlistId: String? = null,
    val params: String? = null,
    val continuation: String? = null,
    /** Endpoint for automix/radio continuation when starting radio from a song. */
    val radioPlaylistId: String? = null,
    val radioParams: String? = null,
)

data class MoodsPage(
    val sections: List<MoodSection>,
) {
    data class MoodSection(
        val title: String,
        val items: List<MoodItem>,
    )
}

data class AccountInfo(
    val name: String,
    val email: String? = null,
    val photoUrl: String? = null,
)
