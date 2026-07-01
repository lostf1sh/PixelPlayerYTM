package com.lostf1sh.pixelplayeross.data.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * Server-driven home/explore feed: an ordered list of horizontal shelves. Rendered
 * generically by the UI so new YTM section types need no bespoke composables.
 */
@Immutable
data class HomeFeed(
    val shelves: ImmutableList<HomeShelf>,
)

@Immutable
data class HomeShelf(
    val id: String,
    val title: String,
    val strapline: String? = null,
    val items: ImmutableList<ShelfItem>,
)

/** What kind of page a [ShelfItem.BrowseItem] opens. */
enum class BrowseKind { ALBUM, PLAYLIST, ARTIST, PODCAST }

/**
 * One card in a shelf. [SongItem]s play directly; [BrowseItem]s open a browse page;
 * [MoodItem]s open a mood/genre category. The [key] is used for stable Compose list keys.
 */
@Immutable
sealed interface ShelfItem {
    val key: String

    data class SongItem(val song: Song) : ShelfItem {
        override val key: String get() = "song_${song.id}"
    }

    data class BrowseItem(
        val browseId: String,
        val title: String,
        val subtitle: String?,
        val thumbnailUrl: String?,
        val kind: BrowseKind,
    ) : ShelfItem {
        override val key: String get() = "browse_${browseId}"
    }

    data class MoodItem(
        val browseId: String,
        val params: String?,
        val title: String,
    ) : ShelfItem {
        override val key: String get() = "mood_${browseId}_${params ?: ""}"
    }
}
