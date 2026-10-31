package app.marginalia.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marginalia.data.Hit
import app.marginalia.data.HighlightColors
import app.marginalia.data.HitKind
import app.marginalia.data.LibrarySearch
import app.marginalia.data.YouTube
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * One search box for everything you saved, like Obsidian's Omnisearch. It finds videos, the words you
 * highlighted, your timed notes, playlists and channels. Type `#` to look only at tags.
 * A highlight opens the reader at the place it came from.
 */
@Composable
fun LibrarySearchSheet(onDismiss: () -> Unit, onOpenPlaylist: (String) -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val settings by app.settings.state.collectAsState()
    val videos by app.library.videos.collectAsState()
    val playlists by app.playlists.all.collectAsState()
    val channels by app.subscriptions.all.collectAsState()
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<HitKind?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val all by produceState(emptyList<Hit>(), query, videos, playlists, channels) {
        delay(30)
        value = withContext(Dispatchers.Default) { LibrarySearch.search(query, videos, playlists, channels) }
    }
    val counts = all.groupingBy { it.kind }.eachCount()
    val shown = if (filter == null || counts[filter] == null) all else all.filter { it.kind == filter }

    fun open(h: Hit) {
        if (query.isNotBlank()) app.settings.update { it.copy(recentSearches = (listOf(query.trim()) + it.recentSearches).distinct().take(12)) }
        when (h.kind) {
            HitKind.Video -> nav.openVideo(h.videoId)
            HitKind.Highlight -> {
                // Start the reader at the highlight. A video that is already playing is moved there now.
                app.library.edit(h.videoId) { it.copy(positionSec = h.sec) }
                if (app.player.state.value.videoId == h.videoId) app.player.seekTo(h.sec)
                app.pendingSeek.value = h.videoId to h.sec
                nav.openVideo(h.videoId)
            }
            HitKind.Playlist -> onOpenPlaylist(h.ref)
            HitKind.Channel -> nav.openChannel(h.ref)
        }
        onDismiss()
    }

    SmoothSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it; filter = null },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(focus),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                placeholder = { Text("Search videos, highlights, notes", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { shown.firstOrNull()?.let { open(it) } }),
            )

            if (query.isNotBlank() && counts.size > 1) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All · ${all.size}") }) }
                    items(HitKind.entries.filter { counts[it] != null }, key = { it.name }) { k ->
                        FilterChip(
                            selected = filter == k, onClick = { filter = if (filter == k) null else k },
                            label = { Text(kindLabel(k) + " · " + counts[k]) },
                        )
                    }
                }
            }

            if (query.isBlank()) {
                EmptyState(
                    recent = settings.recentSearches,
                    onRecent = { query = it },
                    onClearRecent = { app.settings.update { s -> s.copy(recentSearches = emptyList()) } },
                    videosWithHighlights = videos,
                    onOpen = ::open,
                )
            } else if (shown.isEmpty()) {
                Text(
                    "Nothing matches \"${query.trim()}\". Try fewer letters, or a word from the note.",
                    Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(shown, key = { it.kind.name + "|" + it.videoId + "|" + it.sec + "|" + it.ref + "|" + it.title }) { h ->
                        HitRow(h) { open(h) }
                    }
                }
            }
        }
    }
}

private fun kindLabel(k: HitKind) = when (k) {
    HitKind.Video -> "Videos"
    HitKind.Highlight -> "Highlights"
    HitKind.Playlist -> "Playlists"
    HitKind.Channel -> "Channels"
}

@Composable
private fun EmptyState(
    recent: List<String>,
    onRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    videosWithHighlights: List<app.marginalia.data.SavedVideo>,
    onOpen: (Hit) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // The newest highlights, so the box is useful before you type anything.
    val latest = remember(videosWithHighlights) {
        videosWithHighlights.flatMap { v -> v.highlights.filter { it.text.isNotBlank() || it.note.isNotBlank() }.map { h -> v to h } }
            .sortedByDescending { (v, h) -> maxOf(h.createdAt, v.addedAt) }
            .take(6)
            .map { (v, h) ->
                Hit(
                    HitKind.Highlight, v.meta.title, YouTube.formatTime(h.startSec),
                    snippet = h.text.ifBlank { h.note }.take(140), videoId = v.meta.id, sec = h.startSec, color = h.color,
                )
            }
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        if (recent.isNotEmpty()) {
            item("rh") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearRecent) { Text("Clear") }
                }
            }
            item("rc") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent, key = { "r$it" }) { r -> FilterChip(selected = false, onClick = { onRecent(r) }, label = { Text(r, maxLines = 1) }) }
                }
            }
        }
        if (latest.isNotEmpty()) {
            item("lh") {
                Text(
                    "Latest highlights", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
                )
            }
            items(latest, key = { "l" + it.videoId + it.sec }) { h -> HitRow(h) { onOpen(h) } }
        } else if (recent.isEmpty()) {
            item("none") {
                Text(
                    "Type a few letters of a title, a channel, or a phrase you highlighted. Use # to look only at tags.",
                    Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HitRow(h: Hit, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (h.kind) {
            HitKind.Video -> AsyncImage(
                "https://i.ytimg.com/vi/${h.videoId}/mqdefault.jpg", null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 64.dp, height = 38.dp).clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainerHigh),
            )
            HitKind.Highlight -> {
                val c = HighlightColors.getOrNull(h.color)?.ui?.let { Color(it) } ?: scheme.primary
                // The colour you marked it in, the same bar as on the highlights page.
                Box(Modifier.padding(top = 3.dp).size(width = 4.dp, height = 34.dp).clip(RoundedCornerShape(2.dp)).background(c))
            }
            HitKind.Playlist -> Box(Modifier.size(38.dp).clip(CircleShape).background(scheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.QueueMusic, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
            }
            HitKind.Channel -> Box(Modifier.size(38.dp).clip(CircleShape).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (h.kind == HitKind.Highlight) {
                // The words come first, because they are what you were looking for.
                h.snippet?.let { s ->
                    Text(
                        buildAnnotatedString {
                            append(s)
                            h.snippetMatch?.let { r -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = scheme.primary), r.first, (r.last + 1).coerceAtMost(s.length)) }
                        },
                        style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    h.title + if (h.subtitle.isNotBlank()) "  ·  " + h.subtitle else "",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Text(marked(h.title, h.matched, scheme.primary), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (h.subtitle.isNotBlank()) Text(
                    h.subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
