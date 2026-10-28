package app.marginalia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marginalia.data.AppSettings
import app.marginalia.data.Playlist
import app.marginalia.data.SavedVideo
import app.marginalia.obsidian.Sender
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

val LibrarySorts = listOf(
    "recent" to "Recent",
    "added" to "Date added",
    "title" to "Title",
    "author" to "Channel",
    "highlights" to "Most highlights",
)

fun List<SavedVideo>.sortedBy(sort: String): List<SavedVideo> = when (sort) {
    "title" -> sortedBy { it.meta.title.lowercase() }
    "author" -> sortedWith(compareBy({ it.meta.author.lowercase() }, { it.meta.title.lowercase() }))
    "highlights" -> sortedByDescending { it.highlights.size }
    "added" -> sortedByDescending { it.addedAt }
    else -> this
}

/** A filter picked in the library: a category or a tag. */
data class LibraryFilter(val category: String? = null, val tag: String? = null) {
    fun matches(v: SavedVideo) = (category == null || v.category.equals(category, true)) && (tag == null || v.tags.any { it.equals(tag, true) })
    val active get() = category != null || tag != null
}

/** Sort button and the category and tag chips above the library. */
@Composable
fun LibraryControls(
    videos: List<SavedVideo>,
    sort: String,
    onSort: (String) -> Unit,
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
) {
    val categories = videos.map { it.category }.filter { it.isNotBlank() }.groupingBy { it.lowercase() }.eachCount()
    val names = videos.map { it.category }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sortedByDescending { categories[it.lowercase()] ?: 0 }
    val tags = videos.flatMap { it.tags }.groupingBy { it.lowercase() }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    var menu by remember { mutableStateOf(false) }
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("sort") {
            androidx.compose.foundation.layout.Box {
                FilterChip(
                    selected = false, onClick = { menu = true },
                    label = { Text("Sort: " + (LibrarySorts.firstOrNull { it.first == sort }?.second ?: "Recent")) },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    LibrarySorts.forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { onSort(id); menu = false })
                    }
                }
            }
        }
        if (filter.active) item("clear") {
            FilterChip(selected = true, onClick = { onFilter(LibraryFilter()) }, label = { Text("Clear filter") },
                trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) })
        }
        items(names, key = { "cat" + it.lowercase() }) { c ->
            FilterChip(
                selected = filter.category.equals(c, true),
                onClick = { onFilter(filter.copy(category = if (filter.category.equals(c, true)) null else c)) },
                label = { Text(c) },
            )
        }
        items(tags, key = { "tag$it" }) { t ->
            FilterChip(
                selected = filter.tag.equals(t, true),
                onClick = { onFilter(filter.copy(tag = if (filter.tag.equals(t, true)) null else t)) },
                label = { Text("#$t") },
            )
        }
    }
}

/** Everything you can do to one video, in one sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionsSheet(videoId: String, inPlaylist: Boolean = false, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val videos by app.library.videos.collectAsState()
    val playlists by app.playlists.all.collectAsState()
    val saved by app.audio.saved.collectAsState()
    val progress by app.audio.progress.collectAsState()
    val failed by app.audio.failed.collectAsState()
    val queue by app.queue.ids.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val v = videos.firstOrNull { it.meta.id == videoId }
    if (v == null) { onDismiss(); return }
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // header: what this video is
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    v.meta.thumb, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(104.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(16.dp)),
                )
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(v.meta.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(v.meta.author, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // quick actions
            val isSaved = videoId in saved
            val busy = progress[videoId]
            val inQueue = videoId in queue
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(Icons.Rounded.PlayArrow, "Play next", false, Modifier.weight(1f)) { app.queue.playNext(videoId); onDismiss() }
                ActionTile(Icons.Rounded.QueueMusic, if (inQueue) "In queue" else "Queue", inQueue, Modifier.weight(1f)) {
                    if (inQueue) app.queue.remove(videoId) else app.queue.add(videoId)
                }
                ActionTile(
                    if (isSaved) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                    when {
                        busy != null -> if (busy < 0f) "Saving…" else "${(busy * 100).toInt()}%"
                        isSaved -> "Offline"
                        videoId in failed -> "Retry"
                        else -> "Save audio"
                    },
                    isSaved || busy != null, Modifier.weight(1f), busy = busy,
                ) {
                    when {
                        busy != null -> {}
                        isSaved -> {
                            app.audio.remove(videoId)
                            android.widget.Toast.makeText(ctx, "Removed the saved audio.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        else -> app.audio.saveAsync(videoId, v.meta.title)
                    }
                }
                ActionTile(Icons.Rounded.OpenInNew, "Obsidian", false, Modifier.weight(1f)) { if (openVideoNote(ctx, app, v)) onDismiss() }
            }
            if (isSaved) Text(
                "Audio is on this phone, ${"%.0f".format(app.audio.sizeMb(videoId))} MB. Tap Offline to remove it.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
            if (videoId in failed && busy == null) Text(
                "The last try failed. Check your connection, then tap Retry.",
                style = MaterialTheme.typography.bodySmall, color = scheme.error,
            )

            // organize
            SectionTitle("Category")
            val everyCategory = remember(videos) { videos.map { it.category }.filter { it.isNotBlank() }.distinctBy { it.lowercase() } }
            var cat by remember(videoId) { mutableStateOf(v.category) }
            OutlinedTextField(
                value = cat, onValueChange = { cat = it; app.library.edit(videoId) { x -> x.copy(category = it.trim()) } },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                placeholder = { Text("For example: Learning") },
            )
            val catSuggestions = everyCategory.filterNot { it.equals(cat.trim(), true) }.take(10)
            if (catSuggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(catSuggestions, key = { "sc$it" }) { c ->
                        FilterChip(selected = false, onClick = { cat = c; app.library.edit(videoId) { x -> x.copy(category = c) } }, label = { Text(c) })
                    }
                }
            }

            SectionTitle("Tags")
            if (v.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                v.tags.forEach { t ->
                    InputChip(
                        selected = true, onClick = { app.library.edit(videoId) { x -> x.copy(tags = x.tags - t) } },
                        label = { Text("#$t") }, trailingIcon = { Icon(Icons.Rounded.Close, "Remove $t", Modifier.size(16.dp)) },
                    )
                }
            }
            var newTag by remember(videoId) { mutableStateOf("") }
            fun addTag(raw: String) {
                val t = raw.trim().removePrefix("#").replace(' ', '-')
                if (t.isNotEmpty()) app.library.edit(videoId) { x -> x.copy(tags = (x.tags + t).distinctBy { it.lowercase() }) }
                newTag = ""
            }
            OutlinedTextField(
                value = newTag, onValueChange = { newTag = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                placeholder = { Text("Add a tag") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTag(newTag) }),
                trailingIcon = { if (newTag.isNotBlank()) IconButton(onClick = { addTag(newTag) }) { Icon(Icons.Rounded.Add, "Add tag") } },
            )
            val everyTag = remember(videos) { videos.flatMap { it.tags }.distinctBy { it.lowercase() } }
            val tagSuggestions = everyTag.filter { t -> v.tags.none { it.equals(t, true) } }.take(12)
            if (tagSuggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tagSuggestions, key = { "st$it" }) { t ->
                        FilterChip(selected = false, onClick = { addTag(t) }, label = { Text("#$t") })
                    }
                }
            }

            if (playlists.isNotEmpty()) {
                SectionTitle("Playlists")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    playlists.forEach { p ->
                        val inIt = videoId in p.videoIds
                        FilterChip(
                            selected = inIt,
                            onClick = { if (inIt) app.playlists.removeVideo(p.id, videoId) else app.playlists.addVideo(p.id, videoId) },
                            label = { Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }

            // footer
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { app.library.edit(videoId) { it.copy(pinned = !it.pinned) } }) {
                    Icon(Icons.Rounded.PushPin, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (v.pinned) "Unpin" else "Pin")
                }
                if (inPlaylist) TextButton(onClick = { app.library.edit(videoId) { it.copy(playlistId = "") }; onDismiss() }) { Text("Move to library") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmRemove = true }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = scheme.error)) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Remove")
                }
            }
        }
    }

    if (confirmRemove) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this video?") },
            text = { Text("Its highlights and saved audio on this phone are removed too. Your Obsidian note is not touched.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; removeVideo(app, videoId); onDismiss() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
}

/** One big, rounded button with an icon over a short label. Filled when it is switched on. */
@Composable
private fun ActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    busy: Float? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.material3.Surface(
        onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
            onClick()
        },
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = if (active) scheme.primaryContainer else scheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val tint = if (active) scheme.onPrimaryContainer else scheme.onSurface
            if (busy != null) {
                if (busy >= 0f) androidx.compose.material3.CircularProgressIndicator(progress = { busy }, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                else androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            } else Icon(icon, null, Modifier.size(24.dp), tint = tint)
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
        }
    }
}

private fun removeVideo(app: app.marginalia.MarginaliaApp, id: String) {
    app.queue.remove(id)
    app.audio.remove(id)
    app.playlists.all.value.filter { id in it.videoIds }.forEach { app.playlists.removeVideo(it.id, id) }
    app.library.remove(id)
}

/** The three-dot button that opens the sheet. */
@Composable
fun MoreButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Rounded.MoreVert, "More", tint = MaterialTheme.colorScheme.outline) }
}

// ---------- playlists tab ----------

fun LazyListScope.playlistsTab(
    playlists: List<Playlist>,
    videos: List<SavedVideo>,
    openId: String?,
    onOpenPlaylist: (String?) -> Unit,
    onOpenVideo: (String) -> Unit,
    onMore: (String) -> Unit,
) {
    val open = playlists.firstOrNull { it.id == openId }
    if (open == null) {
        if (playlists.isEmpty()) {
            item("pl-empty") {
                Text(
                    "No playlists yet. Paste a YouTube playlist link on the Add tab. It imports here, not into your library.",
                    Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(playlists, key = { "pl" + it.id }) { p -> PlaylistRow(p, videos) { onOpenPlaylist(p.id) } }
        return
    }
    item("pl-head") { PlaylistHeader(open, onBack = { onOpenPlaylist(null) }) }
    val inList = open.videoIds.mapNotNull { id -> videos.firstOrNull { it.meta.id == id } }
    items(inList, key = { "plv" + it.meta.id }) { v -> QueueRow(v, onClick = { onOpenVideo(v.meta.id) }, trailing = { MoreButton { onMore(v.meta.id) } }) }
}

@Composable
private fun PlaylistRow(p: Playlist, videos: List<SavedVideo>, onClick: () -> Unit) {
    val first = p.videoIds.firstNotNullOfOrNull { id -> videos.firstOrNull { it.meta.id == id } }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = first?.meta?.thumb, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(116.dp).androidxAspect().clip(RoundedCornerShape(16.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text("${p.videoIds.size} videos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Modifier.androidxAspect(): Modifier = this.aspectRatio(16 / 9f)

@Composable
private fun PlaylistHeader(p: Playlist, onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val videos by app.library.videos.collectAsState()
    val progress by app.audio.progress.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("‹ All playlists") }
        Text(p.title, style = MaterialTheme.typography.titleLarge)
        Text("${p.videoIds.size} videos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val ids = p.videoIds.filter { app.library.get(it) != null }
                if (ids.isNotEmpty()) {
                    app.queue.clear()
                    app.queue.add(*ids.drop(1).toTypedArray())
                    val v = app.library.get(ids.first())!!
                    app.player.load(v.meta.id, v.meta.title, v.meta.author, 0.0, force = true)
                }
            }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Play all")
            }
            FilledTonalButton(onClick = { app.queue.add(*p.videoIds.toTypedArray()) }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.QueueMusic, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add to queue")
            }
            FilledTonalButton(onClick = {
                p.videoIds.forEach { id -> app.library.get(id)?.let { app.audio.saveAsync(id, it.meta.title) } }
            }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                val busy = p.videoIds.count { it in progress }
                Text(if (busy > 0) "Saving…" else "Save audio")
            }
            TextButton(onClick = { confirm = true }) { Text("Delete playlist") }
        }
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete this playlist?") },
            text = { Text("Videos with highlights stay in your library. The others are removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    p.videoIds.forEach { id ->
                        val v = videos.firstOrNull { it.meta.id == id } ?: return@forEach
                        if (v.playlistId == p.id) {
                            if (v.highlights.isNotEmpty() || v.clippedAt > 0) app.library.edit(id) { it.copy(playlistId = "") }
                            else removeVideo(app, id)
                        }
                    }
                    app.playlists.remove(p.id)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } },
        )
    }
}

// ---------- queue tab ----------

fun LazyListScope.queueTab(queue: List<String>, videos: List<SavedVideo>, nowId: String?, onOpenVideo: (String) -> Unit, onClear: () -> Unit, onRemove: (String) -> Unit) {
    val now = nowId?.let { id -> videos.firstOrNull { it.meta.id == id } }
    if (now != null) {
        item("q-now") { Text("Playing now", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall) }
        item("q-now-row") { QueueRow(now, onClick = { onOpenVideo(now.meta.id) }) }
    }
    item("q-label") {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Up next", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (queue.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
        }
    }
    val rows = queue.mapNotNull { id -> videos.firstOrNull { it.meta.id == id } }
    if (rows.isEmpty()) {
        item("q-empty") {
            Text(
                "Nothing is waiting. Open the ⋮ menu on a video and choose Add to queue. When a video ends, the next one starts.",
                Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(rows, key = { "q" + it.meta.id }) { v ->
        androidx.compose.foundation.layout.Box(Modifier.animateItem()) { QueueRow(v, onClick = { onOpenVideo(v.meta.id) }, trailing = {
            IconButton(onClick = { onRemove(v.meta.id) }) { Icon(Icons.Rounded.Close, "Remove from queue", tint = MaterialTheme.colorScheme.outline) }
        }) }
    }
}

@Composable
fun QueueRow(v: SavedVideo, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val app = LocalApp.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = v.meta.thumb, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(96.dp).androidxAspect().clip(RoundedCornerShape(14.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(v.meta.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(v.meta.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OfflineBadge(v.meta.id)
        }
        trailing()
    }
}

/** Used by the settings search and the home screen to describe the sort in words. */
fun AppSettings.sortLabel() = LibrarySorts.firstOrNull { it.first == librarySort }?.second ?: "Recent"

/** A small, plain line that says where a video's audio stands: on the phone, saving, or failed. */
@Composable
fun OfflineBadge(id: String) {
    val app = LocalApp.current
    val saved by app.audio.saved.collectAsState()
    val progress by app.audio.progress.collectAsState()
    val failed by app.audio.failed.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val busy = progress[id]
    val (icon, text, color) = when {
        busy != null -> Triple(Icons.Rounded.Download, if (busy < 0f) "Saving…" else "Saving ${(busy * 100).toInt()}%", scheme.primary)
        id in saved -> Triple(Icons.Rounded.CheckCircle, "Offline", scheme.primary)
        id in failed -> Triple(Icons.Rounded.ErrorOutline, "Save failed", scheme.error)
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Opens the note for this video in Obsidian. Uses where the last clip went,
 * and falls back to finding the note by its source link in the vault index.
 * Returns false, after telling the user why, when there is nothing to open.
 */
fun openVideoNote(ctx: android.content.Context, application: app.marginalia.MarginaliaApp, v: SavedVideo): Boolean {
    val s = application.settings.value
    var vault = v.noteVault.ifBlank { s.vault }
    var path = v.notePath
    if (path.isBlank()) {
        val key = s.linkedVault(s.vault)
        val ref = key?.let { application.vaults.indexes.value[it] }?.noteForSource(v.meta.url)
        if (key != null && ref != null) { vault = key; path = ref.path }
    }
    if (path.isBlank() || vault.isBlank()) {
        android.widget.Toast.makeText(ctx, "No note yet. Clip this video first.", android.widget.Toast.LENGTH_SHORT).show()
        return false
    }
    val ok = Sender.openNote(ctx, vault, path)
    if (!ok) android.widget.Toast.makeText(ctx, "Obsidian is not installed.", android.widget.Toast.LENGTH_SHORT).show()
    return ok
}
