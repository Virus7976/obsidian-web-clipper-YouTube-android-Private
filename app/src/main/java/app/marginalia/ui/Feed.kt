package app.marginalia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marginalia.data.Channel
import app.marginalia.data.ChannelVideo
import app.marginalia.data.FeedState
import app.marginalia.data.SavedVideo
import app.marginalia.data.YouTube
import coil3.compose.AsyncImage

val FeedFilters = listOf("all" to "All", "new" to "New", "library" to "In library")

/** The newest videos from the channels you follow: your channels across the top, then a tight list. */
fun LazyListScope.feedTab(
    query: String,
    subs: List<Channel>,
    feed: FeedState,
    busy: Boolean,
    library: List<SavedVideo>,
    filter: String,
    onFilter: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOptions: (String) -> Unit,
    onQueue: (String) -> Unit,
    onFind: () -> Unit,
) {
    if (subs.isEmpty()) {
        item("feed-empty") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Follow a channel", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Its newest videos show up here, so you do not have to go back to YouTube. Search for a channel, or open a video's menu and choose Go to channel.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onFind, shapes = ButtonDefaults.shapes()) { Text("Find a channel") }
            }
        }
        return
    }
    item("feed-channels") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(subs, key = { it.url }) { c ->
                Column(
                    Modifier.width(84.dp).clip(RoundedCornerShape(18.dp)).clickable { onOpenChannel(c.url) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(c.avatar, 68)
                    Text(
                        c.name, Modifier.padding(top = 6.dp).fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    item("feed-filters") {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FeedFilters, key = { it.first }) { (id, label) ->
                FilterChip(selected = filter == id, onClick = { onFilter(id) }, label = { Text(label) })
            }
        }
    }
    item("feed-status") {
        Text(
            when {
                busy -> "Checking for new videos…"
                feed.updatedAt == 0L -> "Pull down to check for new videos."
                else -> "Updated ${ago(feed.updatedAt)}. Pull down to check again."
            },
            Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val byId = library.associateBy { it.meta.id }
    val shown = feed.items.filter { v ->
        matchesQuery(query, v.meta.title, v.channelName) && when (filter) {
            "new" -> v.meta.id !in byId
            "library" -> v.meta.id in byId
            else -> true
        }
    }
    if (shown.isEmpty() && !busy && feed.updatedAt != 0L) {
        item("feed-none") {
            Text(
                if (query.isNotBlank()) "Nothing in the feed matches \"$query\"." else "Nothing here. Pull down to check again.",
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(shown.take(150), key = { "f" + it.meta.id }) { v ->
        val mine = byId[v.meta.id]
        val frac = if (mine != null && v.meta.durationSec > 0) (mine.positionSec / v.meta.durationSec).toFloat().coerceIn(0f, 1f) else 0f
        ChannelVideoRow(
            v, showChannel = true, saved = mine != null,
            avatar = subs.firstOrNull { it.url == v.channelUrl }?.avatar.orEmpty(),
            progress = frac,
            onOptions = { onOptions(v.meta.id) }, onQueue = { onQueue(v.meta.id) },
        ) { onOpenVideo(v.meta.id) }
    }
}

/**
 * One video as a wide picture with the facts beside it. Pass [onOptions] and [onQueue] to get the
 * two buttons under the title.
 */
@Composable
fun ChannelVideoRow(
    v: ChannelVideo,
    showChannel: Boolean,
    saved: Boolean,
    avatar: String = "",
    progress: Float = 0f,
    onOptions: (() -> Unit)? = null,
    onQueue: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(168.dp)) {
            AsyncImage(
                v.meta.thumb, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().androidxAspect().clip(RoundedCornerShape(14.dp)),
            )
            if (v.meta.durationSec > 0) Text(
                YouTube.formatTime(v.meta.durationSec.toDouble()),
                Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xB3000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = Color.White,
            )
            if (progress > 0f) Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp).height(4.dp)
                    .clip(CircleShape).background(Color(0x66000000)),
            ) { Box(Modifier.fillMaxWidth(progress).height(4.dp).background(MaterialTheme.colorScheme.primary)) }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(v.meta.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            if (showChannel) Row(verticalAlignment = Alignment.CenterVertically) {
                if (avatar.isNotBlank()) { Avatar(avatar, 24); Spacer(Modifier.width(8.dp)) }
                Column {
                    if (v.channelName.isNotBlank()) Text(
                        v.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (v.ago.isNotBlank()) Text(v.ago, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (v.ago.isNotBlank()) Text(v.ago, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (saved && onOptions == null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("In your library", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (onOptions != null || onQueue != null) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onOptions != null) Pill(Icons.Rounded.Tune, "Options", onOptions)
                if (onQueue != null) Pill(Icons.Rounded.QueueMusic, null, onQueue)
                if (saved) Icon(Icons.Rounded.Check, "In your library", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, label ?: "Add to queue", Modifier.size(18.dp))
            if (label != null) Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** "3 min ago", "2 h ago". */
fun ago(whenMs: Long): String {
    val m = ((System.currentTimeMillis() - whenMs) / 60_000L).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} h ago"
        else -> "${m / (24 * 60)} d ago"
    }
}
