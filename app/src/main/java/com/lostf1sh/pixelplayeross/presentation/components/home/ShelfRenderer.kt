package com.lostf1sh.pixelplayeross.presentation.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lostf1sh.pixelplayeross.data.model.BrowseKind
import com.lostf1sh.pixelplayeross.data.model.HomeShelf
import com.lostf1sh.pixelplayeross.data.model.ShelfItem

/**
 * Renders a single [HomeShelf] as a titled horizontal carousel. Song cards play on tap,
 * browse cards open a [browse detail][com.lostf1sh.pixelplayeross.presentation.screens.YouTubeBrowseScreen],
 * and mood pills open a category. Deliberately layout-simple so any YTM section renders
 * without a bespoke composable.
 */
@Composable
fun ShelfRenderer(
    shelf: HomeShelf,
    onSongClick: (ShelfItem.SongItem) -> Unit,
    onBrowseClick: (ShelfItem.BrowseItem) -> Unit,
    onMoodClick: (ShelfItem.MoodItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        shelf.strapline?.let {
            Text(
                text = it.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Text(
            text = shelf.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shelf.items, key = { it.key }) { item ->
                when (item) {
                    is ShelfItem.SongItem -> ShelfCard(
                        thumbnailUrl = item.song.albumArtUriString,
                        title = item.song.title,
                        subtitle = item.song.displayArtist,
                        circular = false,
                        onClick = { onSongClick(item) },
                    )

                    is ShelfItem.BrowseItem -> ShelfCard(
                        thumbnailUrl = item.thumbnailUrl,
                        title = item.title,
                        subtitle = item.subtitle,
                        circular = item.kind == BrowseKind.ARTIST,
                        onClick = { onBrowseClick(item) },
                    )

                    is ShelfItem.MoodItem -> AssistChip(
                        onClick = { onMoodClick(item) },
                        label = { Text(item.title) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfCard(
    thumbnailUrl: String?,
    title: String,
    subtitle: String?,
    circular: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = title,
            modifier = Modifier
                .size(150.dp)
                .clip(if (circular) CircleShape else RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
