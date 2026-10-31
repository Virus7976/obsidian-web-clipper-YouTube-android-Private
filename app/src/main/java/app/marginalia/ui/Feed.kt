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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marginalia.data.Channel
import app.marginalia.data.ChannelVideo
import app.marginalia.data.FeedState
import app.marginalia.data.YouTube
import coil3.compose.AsyncImage

/** The newest videos from the channels you follow. */
fun LazyListScope.feedTab(
    subs: List<Channel>,
    feed: FeedState,
    busy: Boolean,
    inLibrary: Set<String>,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(subs, key = { it.url }) { c ->
                Column(
                    Modifier.width(68.dp).clip(RoundedCornerShape(16.dp)).clickable { onOpenChannel(c.url) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(c.avatar, 56)
                    Text(c.name, Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                }
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
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (feed.items.isEmpty() && !busy && feed.updatedAt != 0L) {
        item("feed-none") { Text("No videos found. Pull down to try again.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    items(feed.items.take(150), key = { "f" + it.meta.id }) { v ->
        ChannelVideoRow(v, showChannel = true, saved = v.meta.id in inLibrary, onChannel = { onOpenChannel(v.channelUrl) }) { onOpenVideo(v.meta.id) }
    }
}

/** One video from a channel page or the feed. */
@Composable
fun ChannelVideoRow(v: ChannelVideo, showChannel: Boolean, saved: Boolean, onChannel: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                v.meta.thumb, null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(120.dp).androidxAspect().clip(RoundedCornerShape(16.dp)),
            )
            if (v.meta.durationSec > 0) Text(
                YouTube.formatTime(v.meta.durationSec.toDouble()),
                Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color(0xB3000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(v.meta.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            val line = listOfNotNull(v.channelName.takeIf { showChannel && it.isNotBlank() }, v.ago.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (line.isNotEmpty()) Text(
                line, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (onChannel != null && showChannel) Modifier.clickable(onClick = onChannel) else Modifier,
            )
            if (saved) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("In your library", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
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
