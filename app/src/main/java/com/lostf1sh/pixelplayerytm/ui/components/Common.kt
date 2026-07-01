package com.lostf1sh.pixelplayerytm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lostf1sh.pixelplayerytm.domain.model.AlbumItem
import com.lostf1sh.pixelplayerytm.domain.model.ArtistItem
import com.lostf1sh.pixelplayerytm.domain.model.MoodItem
import com.lostf1sh.pixelplayerytm.domain.model.PlaylistItem
import com.lostf1sh.pixelplayerytm.domain.model.Shelf
import com.lostf1sh.pixelplayerytm.domain.model.SongItem
import com.lostf1sh.pixelplayerytm.domain.model.YtItem

/** Click routing for any YouTube item. */
data class ItemActions(
    val onSong: (SongItem) -> Unit,
    val onAlbum: (AlbumItem) -> Unit,
    val onArtist: (ArtistItem) -> Unit,
    val onPlaylist: (PlaylistItem) -> Unit,
    val onMood: (MoodItem) -> Unit = {},
    val onSongMenu: ((SongItem) -> Unit)? = null,
)

fun ItemActions.onClick(item: YtItem) {
    when (item) {
        is SongItem -> onSong(item)
        is AlbumItem -> onAlbum(item)
        is ArtistItem -> onArtist(item)
        is PlaylistItem -> onPlaylist(item)
        is MoodItem -> onMood(item)
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorBox(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier.clip(shape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Compact list row for a song (search results, album tracks, queue). */
@Composable
fun SongRow(
    song: SongItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null,
    trailingText: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.thumbnailUrl, Modifier.size(48.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.explicit) {
                    Icon(
                        Icons.Default.Explicit,
                        contentDescription = "Explicit",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = listOfNotNull(
                        song.artistNames.ifEmpty { null },
                        song.album?.name,
                        song.durationText,
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onMenuClick != null) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Generic list row for albums/artists/playlists in vertical lists. */
@Composable
fun ItemRow(
    item: YtItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = when (item) {
        is AlbumItem -> item.subtitle ?: item.artists.joinToString(", ") { it.name }
        is ArtistItem -> item.subtitle.orEmpty()
        is PlaylistItem -> item.author.orEmpty()
        is SongItem -> item.artistNames
        is MoodItem -> ""
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = item.thumbnailUrl,
            modifier = Modifier.size(48.dp),
            shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(8.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Square card for carousels and grids. */
@Composable
fun ItemCard(
    item: YtItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: androidx.compose.ui.unit.Dp = 144.dp,
) {
    val subtitle = when (item) {
        is AlbumItem -> item.subtitle ?: item.artists.joinToString(", ") { it.name }
        is ArtistItem -> item.subtitle.orEmpty()
        is PlaylistItem -> item.author.orEmpty()
        is SongItem -> item.artistNames
        is MoodItem -> ""
    }
    Column(
        modifier = modifier
            .width(cardWidth)
            .clickable(onClick = onClick),
    ) {
        Artwork(
            url = item.thumbnailUrl,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardWidth),
            shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun MoodChip(
    mood: MoodItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    mood.stripeColor?.let { Color(it.toInt()) }
                        ?: MaterialTheme.colorScheme.primary,
                ),
        )
        Text(
            text = mood.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A titled horizontal carousel of items. */
@Composable
fun ShelfSection(
    shelf: Shelf,
    actions: ItemActions,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shelf.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onMoreClick != null) {
                TextButton(onClick = onMoreClick) { Text("More") }
            }
        }
        if (shelf.isVerticalList) {
            Column {
                shelf.items.forEach { item ->
                    if (item is SongItem) {
                        SongRow(song = item, onClick = { actions.onSong(item) })
                    } else {
                        ItemRow(item = item, onClick = { actions.onClick(item) })
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shelf.items, key = { it.id + it.title }) { item ->
                    if (item is MoodItem) {
                        MoodChip(mood = item, onClick = { actions.onMood(item) })
                    } else {
                        ItemCard(item = item, onClick = { actions.onClick(item) })
                    }
                }
            }
        }
    }
}
