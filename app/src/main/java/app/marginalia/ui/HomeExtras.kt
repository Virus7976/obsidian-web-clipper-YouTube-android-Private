package app.marginalia.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.marginalia.data.NoteRef
import app.marginalia.data.SavedVideo
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme as M3
import androidx.compose.ui.text.style.TextOverflow

/** The tabs on the home screen. The order here is the order shown. */
val HomeTabs = linkedMapOf("add" to "Add", "library" to "Library", "vault" to "Vault")

val LibraryLayouts = listOf("list" to "List", "grid" to "Grid", "compact" to "Compact")

/** Pixel characters for pull-to-refresh. '#' is the body, 'o' is the eye. */
val PixelChars = listOf("ghost" to "Ghost", "heart" to "Heart", "star" to "Star", "invader" to "Invader", "off" to "None")

private val Sprites = mapOf(
    "ghost" to listOf("..####..", ".######.", "##o##o##", "########", "########", "########", "########", "#.##.##."),
    "heart" to listOf(".##..##.", "########", "########", "########", ".######.", "..####..", "...##...", "........"),
    "star" to listOf("...##...", "...##...", "########", ".######.", "..####..", ".##..##.", "##....##", "........"),
    "invader" to listOf("..#..#..", "...##...", "..####..", ".##oo##.", "########", "#.####.#", "#.#..#.#", "...##..."),
)

/** A small pixel character that grows as you pull and hops while it loads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.PixelIndicator(state: PullToRefreshState, refreshing: Boolean, char: String) {
    val sprite = Sprites[char] ?: return
    val fraction = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1.3f)
    val hop by rememberInfiniteTransition(label = "pixel").animateFloat(
        0f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "hop",
    )
    val body = M3.colorScheme.primary
    val eye = M3.colorScheme.onPrimary
    Canvas(
        Modifier
            .align(Alignment.TopCenter)
            .size(44.dp)
            .graphicsLayer {
                val rest = 12.dp.toPx()
                translationY = fraction.coerceAtMost(1f) * (rest + 20.dp.toPx()) - 44.dp.toPx() - (if (refreshing) hop * 8.dp.toPx() else 0f)
                alpha = fraction.coerceAtMost(1f)
                val s = 0.5f + 0.5f * fraction.coerceAtMost(1f)
                scaleX = s
                scaleY = s
            },
    ) {
        val cell = size.width / 8f
        sprite.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c != '.') {
                    drawRect(
                        if (c == 'o') eye else body,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}

/** Two cards per row. */
@Composable
fun VideoGridRow(pair: List<SavedVideo>, onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        pair.forEach { v ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { onOpen(v.meta.id) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AsyncImage(
                    model = v.meta.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(18.dp)),
                )
                Text(v.meta.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(v.meta.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
}

/** One line per video, for long libraries. */
@Composable
fun VideoCompactRow(v: SavedVideo, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(v.meta.id) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(v.meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(v.meta.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (v.highlights.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text("${v.highlights.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/** The Vault tab: a search button and the notes you changed last. */
fun LazyListScope.vaultTab(vault: String?, notes: List<NoteRef>, onSearch: () -> Unit, onOpen: (NoteRef) -> Unit) {
    if (vault == null) {
        item("vault-none") {
            Text(
                "Add a vault folder in Settings, under Obsidian. Then you can search your notes here.",
                Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    item("vault-search") {
        Button(onClick = onSearch, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Search $vault")
        }
    }
    item("vault-label") { Text("Changed lately", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall) }
    items(notes, key = { "n" + it.path }) { n ->
        Column(Modifier.fillMaxWidth().clickable { onOpen(n) }.padding(horizontal = 20.dp, vertical = 9.dp)) {
            Text(n.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(n.folder.ifEmpty { "Vault root" }, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
