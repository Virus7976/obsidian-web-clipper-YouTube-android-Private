package app.marginalia.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marginalia.R
import app.marginalia.data.Highlight
import app.marginalia.data.HighlightColors
import app.marginalia.data.SavedVideo
import app.marginalia.data.YouTube
import kotlinx.coroutines.launch

/** One highlight with the video it belongs to. */
private class Entry(val video: SavedVideo, val h: Highlight) {
    /** Newer first. Highlights from before dates were kept fall back to when the video was added. */
    val stamp: Long get() = maxOf(h.createdAt, video.addedAt)
    val words: String get() = h.text.ifBlank { h.note }
}

/**
 * Every highlight you ever made, in one place. Search it, narrow it by colour or by notes,
 * and read it newest first or video by video. A tap opens the video at that spot.
 */
@Composable
fun HighlightsScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val videos by app.library.videos.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val intSet = androidx.compose.runtime.saveable.listSaver<Set<Int>, Int>({ it.toList() }, { it.toSet() })
    val textSet = androidx.compose.runtime.saveable.listSaver<Set<String>, String>({ it.toList() }, { it.toSet() })
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var byVideo by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var onlyNotes by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var colors by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = intSet) { mutableStateOf(setOf<Int>()) }
    var tagSel by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = textSet) { mutableStateOf(setOf<String>()) }

    val all = remember(videos) {
        videos.flatMap { v -> v.highlights.map { Entry(v, it) } }.filter { it.h.text.isNotBlank() || it.h.note.isNotBlank() }
    }
    val tokens = remember(query) { query.trim().split(' ').filter { it.isNotEmpty() } }
    val tagCounts = remember(all) { all.flatMap { it.h.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(12).map { it.key } }
    val shown = remember(all, tokens, onlyNotes, colors, tagSel) {
        all.filter { e ->
            (colors.isEmpty() || e.h.color in colors) &&
                (tagSel.isEmpty() || e.h.tags.any { t -> tagSel.any { it.equals(t, true) } }) &&
                (!onlyNotes || e.h.note.isNotBlank()) &&
                tokens.all { t ->
                    e.h.text.contains(t, true) || e.h.note.contains(t, true) ||
                        e.h.tags.any { g -> g.contains(t.removePrefix("#"), true) } ||
                        e.video.meta.title.contains(t, true) || e.video.meta.author.contains(t, true)
                }
        }
    }
    val groups = remember(shown, byVideo) {
        if (!byVideo) emptyList()
        else shown.groupBy { it.video.meta.id }.values
            .sortedByDescending { g -> g.maxOf { it.stamp } }
            .map { g -> g.sortedBy { it.h.startSec } }
    }
    val flat = remember(shown) { shown.sortedWith(compareByDescending<Entry> { it.stamp }.thenBy { it.h.startSec }) }

    // A new search or filter starts at the top.
    val list = rememberLazyListState()
    OnChanged("$query|$onlyNotes|$colors|$byVideo|$tagSel") { list.scrollToItem(0) }

    fun openAt(e: Entry) {
        app.library.edit(e.video.meta.id) { it.copy(positionSec = e.h.startSec) }
        if (app.player.state.value.videoId == e.video.meta.id) app.player.seekTo(e.h.startSec)
        app.pendingSeek.value = e.video.meta.id to e.h.startSec
        nav.openVideo(e.video.meta.id)
    }

    fun copy(e: Entry, asQuote: Boolean) {
        val body = if (asQuote) "> ${e.words}\n— ${e.video.meta.title}, ${YouTube.formatTime(e.h.startSec)}" else e.words
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("highlight", body))
        scope.launch { snackbar.showSnackbar("Copied.") }
    }

    fun delete(e: Entry) {
        app.library.edit(e.video.meta.id) { v -> v.copy(highlights = v.highlights.filterNot { it.id == e.h.id }) }
        scope.launch {
            val r = snackbar.showSnackbar("Highlight deleted", actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) app.library.edit(e.video.meta.id) { v -> v.copy(highlights = v.highlights + e.h) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Highlights", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when {
                            all.isEmpty() -> "Nothing yet"
                            shown.size == all.size -> "${all.size} in ${videoCount(videos.count { it.highlights.isNotEmpty() })}"
                            else -> "${shown.size} of ${all.size}"
                        },
                        style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
                    )
                }
            }
            SearchField(query, { query = it }, "Search words, notes and videos")

            // How to read, then what to keep. One line, scrolls sideways.
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item("n") { FilterChip(selected = !byVideo, onClick = { byVideo = false }, label = { Text("Newest") }) }
                item("v") { FilterChip(selected = byVideo, onClick = { byVideo = true }, label = { Text("By video") }) }
                item("notes") {
                    FilterChip(
                        selected = onlyNotes, onClick = { onlyNotes = !onlyNotes }, label = { Text("With notes") },
                        leadingIcon = { Icon(Icons.Rounded.Notes, null, Modifier.size(18.dp)) },
                    )
                }
                items(tagCounts, key = { "t$it" }) { t ->
                    FilterChip(
                        selected = t in tagSel, onClick = { tagSel = if (t in tagSel) tagSel - t else tagSel + t },
                        label = { Text("#$t") },
                    )
                }
                items(HighlightColors.indices.toList(), key = { "c$it" }) { i ->
                    val on = i in colors
                    val c = Color(HighlightColors[i].ui)
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable { colors = if (on) colors - i else colors + i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(if (on) 24.dp else 18.dp).clip(CircleShape).background(c)
                                .then(if (on) Modifier.border(2.dp, scheme.onSurface, CircleShape) else Modifier),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (shown.isEmpty()) {
                Text(
                    if (all.isEmpty()) "Hold a word in a transcript to highlight it. Everything you highlight or note shows up here."
                    else "Nothing matches. Clear the search or a filter.",
                    Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
                if (all.isNotEmpty() && (query.isNotBlank() || onlyNotes || colors.isNotEmpty() || tagSel.isNotEmpty())) TextButton(
                    onClick = { query = ""; onlyNotes = false; colors = emptySet(); tagSel = emptySet() }, modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Clear search and filters") }
            } else LazyColumn(
                Modifier.weight(1f), state = list,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (byVideo) groups.forEach { g ->
                    val v = g.first().video
                    item("g" + v.meta.id) {
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(v.meta.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(12.dp))
                            Text("${g.size}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                        }
                    }
                    items(g, key = { "h" + it.h.id }) { e ->
                        HighlightCard(e, tokens, showSource = false, onOpen = { openAt(e) }, onCopy = { copy(e, it) }, onDelete = { delete(e) })
                    }
                } else items(flat, key = { "h" + it.h.id }) { e ->
                    HighlightCard(e, tokens, showSource = true, onOpen = { openAt(e) }, onCopy = { copy(e, it) }, onDelete = { delete(e) })
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}

@Composable
private fun HighlightCard(
    e: Entry,
    tokens: List<String>,
    showSource: Boolean,
    onOpen: () -> Unit,
    onCopy: (asQuote: Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tone = HighlightColors.getOrNull(e.h.color)?.ui?.let { Color(it) } ?: scheme.primary
    var expanded by remember(e.h.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val long = e.h.text.length > 240
    Surface(
        onClick = onOpen, shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(tone))
            Column(Modifier.weight(1f).padding(start = 16.dp, top = 14.dp, end = 4.dp, bottom = 6.dp)) {
                if (e.h.text.isNotBlank()) Text(
                    found(e.h.text, tokens, scheme.primary),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily(Font(R.font.newsreader)), fontSize = 18.sp, lineHeight = 26.sp),
                    maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 12.dp),
                )
                if (long) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(32.dp)) {
                    Text(if (expanded) "Show less" else "Show more", style = MaterialTheme.typography.labelMedium)
                }
                if (e.h.note.isNotBlank()) Text(
                    found(e.h.note, tokens, scheme.primary),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = if (e.h.text.isNotBlank()) 8.dp else 0.dp, end = 12.dp),
                )
                if (e.h.tags.isNotEmpty()) Text(
                    e.h.tags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.labelMedium, color = scheme.primary,
                    modifier = Modifier.padding(top = 8.dp, end = 12.dp),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (showSource) e.video.meta.title + "  ·  " else "") + YouTube.formatTime(e.h.startSec),
                        Modifier.weight(1f).padding(vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Box {
                        IconButton(onClick = { menu = true }, Modifier.size(40.dp)) { Icon(Icons.Rounded.MoreVert, "More", tint = scheme.onSurfaceVariant) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Copy") }, onClick = { menu = false; onCopy(false) })
                            DropdownMenuItem(text = { Text("Copy as a quote") }, onClick = { menu = false; onCopy(true) })
                            DropdownMenuItem(text = { Text("Delete", color = scheme.error) }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
            }
        }
    }
}

/** [text] with every typed word marked, so you can see why it matched. */
private fun found(text: String, tokens: List<String>, color: Color): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        // [[Note links]] in your notes, in the accent colour.
        var open = text.indexOf("[[")
        while (open >= 0) {
            val close = text.indexOf("]]", open + 2)
            if (close < 0) break
            addStyle(SpanStyle(color = color), open, close + 2)
            open = text.indexOf("[[", close + 2)
        }
        for (t in tokens) {
            var i = text.indexOf(t, 0, ignoreCase = true)
            while (i >= 0) {
                addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold, background = color.copy(alpha = 0.16f)), i, i + t.length)
                i = text.indexOf(t, i + t.length, ignoreCase = true)
            }
        }
    }
}
