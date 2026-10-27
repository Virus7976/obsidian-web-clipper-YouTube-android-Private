package app.marginalia.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.marginalia.data.Chapter
import app.marginalia.data.Highlight
import app.marginalia.data.Highlights
import app.marginalia.data.Line
import app.marginalia.data.SavedVideo
import app.marginalia.data.YouTube
import app.marginalia.player.PlayerHost
import app.marginalia.player.PlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Entry {
    val key: String
}

private data object DescEntry : Entry {
    override val key = "desc"
}

private data class ChapterEntry(val index: Int, val chapter: Chapter) : Entry {
    override val key = "c$index"
}

private data class LineEntry(val index: Int) : Entry {
    override val key = "l$index"
}

private enum class Status { Loading, Ready, Failed }

private fun activeIndexFor(lines: List<Line>, pos: Double): Int {
    if (lines.isEmpty()) return -1
    var lo = 0
    var hi = lines.lastIndex
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].start <= pos + 0.35) {
            ans = mid
            lo = mid + 1
        } else hi = mid - 1
    }
    return ans
}

@Composable
fun ReaderScreen(videoId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    val videos by app.library.videos.collectAsState()
    val video = videos.firstOrNull { it.meta.id == videoId }
    if (video == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val ps = app.player.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var transcript by remember(videoId) { mutableStateOf(app.library.loadTranscript(videoId)) }
    var status by remember(videoId) { mutableStateOf(if (transcript == null) Status.Loading else Status.Ready) }
    var failure by remember(videoId) { mutableStateOf<String?>(null) }
    var retry by remember(videoId) { mutableIntStateOf(0) }

    var showSave by remember { mutableStateOf(false) }
    var showHighlights by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    // Ask once for the notification permission so the playback controls can show.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Start the player right away. The transcript can arrive later.
    LaunchedEffect(videoId) {
        app.player.load(videoId, video.meta.title, video.meta.author, video.positionSec)
    }

    // Fetch transcript, chapters and the publish date.
    LaunchedEffect(videoId, retry) {
        if (transcript != null && video.meta.published.isNotBlank()) return@LaunchedEffect
        if (transcript == null) status = Status.Loading
        runCatching { app.youtube.fetch(videoId) }
            .onSuccess { r ->
                app.library.edit(videoId) {
                    it.copy(
                        meta = r.meta.copy(
                            title = r.meta.title.ifBlank { it.meta.title },
                            author = r.meta.author.ifBlank { it.meta.author },
                            description = r.meta.description.ifBlank { it.meta.description },
                            durationSec = if (r.meta.durationSec > 0) r.meta.durationSec else it.meta.durationSec,
                        ),
                        chapters = if (r.chapters.isNotEmpty()) r.chapters else it.chapters,
                    )
                }
                if (r.transcript != null) {
                    app.library.saveTranscript(videoId, r.transcript)
                    transcript = r.transcript
                    status = Status.Ready
                } else if (transcript == null) {
                    failure = r.transcriptError
                    status = Status.Failed
                }
            }
            .onFailure {
                if (transcript == null) {
                    failure = it.message
                    status = Status.Failed
                }
            }
    }

    // Remember where the reader stopped.
    LaunchedEffect(videoId) {
        while (true) {
            delay(5000)
            val p = app.player.state.value
            if (p.videoId == videoId && p.positionSec > 1) app.library.edit(videoId) { it.copy(positionSec = p.positionSec) }
        }
    }
    DisposableEffect(videoId) {
        onDispose {
            val p = app.player.state.value
            if (p.videoId == videoId && p.positionSec > 1) app.library.edit(videoId) { it.copy(positionSec = p.positionSec) }
        }
    }

    val playing by remember { derivedStateOf { ps.value.playing } }
    DisposableEffect(settings.keepScreenOn, playing) {
        view.keepScreenOn = settings.keepScreenOn && playing
        onDispose { view.keepScreenOn = false }
    }

    val lines = remember(transcript, settings.lineLength, video.chapters) {
        transcript?.let { YouTube.toLines(it.cues, settings.lineLength, video.chapters) }.orEmpty()
    }
    val marked = remember(lines, video.highlights) { Highlights.markedLines(lines, video.highlights) }
    val entries = remember(lines, video.chapters) {
        buildList<Entry> {
            add(DescEntry)
            var last = -1
            lines.forEachIndexed { i, l ->
                val ci = video.chapters.indexOfLast { it.start <= l.start + 0.5 }
                if (ci != last && ci >= 0) {
                    add(ChapterEntry(ci, video.chapters[ci]))
                    last = ci
                }
                add(LineEntry(i))
            }
        }
    }
    val active by remember(lines) { derivedStateOf { activeIndexFor(lines, ps.value.positionSec) } }
    val activeChapter by remember(video.chapters) {
        derivedStateOf { video.chapters.indexOfLast { it.start <= ps.value.positionSec + 0.5 } }
    }

    val listState = rememberLazyListState()

    // Follow along while playing.
    LaunchedEffect(active, settings.autoScroll) {
        if (settings.autoScroll && active >= 0 && !listState.isScrollInProgress) {
            val at = entries.indexOfFirst { it is LineEntry && it.index == active }
            if (at >= 0) listState.animateScrollToItem(at, scrollOffset = -220)
        }
    }

    // Hold-and-drag selection.
    var anchor by remember { mutableIntStateOf(-1) }
    var cursor by remember { mutableIntStateOf(-1) }
    var erasing by remember { mutableStateOf(false) }
    val selLo = if (anchor >= 0) minOf(anchor, cursor) else -1
    val selHi = if (anchor >= 0) maxOf(anchor, cursor) else -1

    fun lineAt(y: Float): Int? {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size } ?: return null
        val k = item.key as? String ?: return null
        return if (k.startsWith("l")) k.substring(1).toIntOrNull() else null
    }

    fun scrollToLine(index: Int) {
        val at = entries.indexOfFirst { it is LineEntry && it.index == index }
        if (at >= 0) scope.launch { listState.animateScrollToItem(at, scrollOffset = -40) }
    }

    fun seekAndScroll(sec: Double) {
        app.player.seekTo(sec)
        app.player.play()
        val i = activeIndexFor(lines, sec)
        if (i >= 0) scrollToLine(i)
    }

    val header: @Composable () -> Unit = {
        Column {
            PlayerBlock(video, ps, app.player, ctx)
            if (video.chapters.isNotEmpty()) {
                ChapterChips(video.chapters, activeChapter) { seekAndScroll(it.start) }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text(
                    video.meta.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Open in YouTube") },
                            leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                            onClick = { menu = false; openInYouTube(ctx, videoId) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (video.pinned) "Unpin from library" else "Pin to library") },
                            leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                            onClick = { menu = false; app.library.edit(videoId) { it.copy(pinned = !it.pinned) } },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove from library") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; app.library.remove(videoId); onBack() },
                        )
                    }
                }
            }

            if (settings.pinPlayer) header()

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(lines, settings.highlighterEnabled) {
                            if (!settings.highlighterEnabled) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = { o ->
                                    lineAt(o.y)?.let { i ->
                                        anchor = i
                                        cursor = i
                                        erasing = marked.getOrElse(i) { false }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val y = change.position.y
                                    lineAt(y)?.let { i ->
                                        if (i != cursor) {
                                            cursor = i
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    val h = listState.layoutInfo.viewportSize.height
                                    if (y < 90f) scope.launch { listState.scrollBy(-28f) }
                                    else if (y > h - 90f) scope.launch { listState.scrollBy(28f) }
                                },
                                onDragEnd = {
                                    if (anchor >= 0) {
                                        val lo = minOf(anchor, cursor)
                                        val hi = maxOf(anchor, cursor)
                                        val wasErase = erasing
                                        app.library.edit(videoId) {
                                            it.copy(highlights = if (wasErase) Highlights.erase(it.highlights, lines, lo, hi) else Highlights.add(it.highlights, lines, lo, hi))
                                        }
                                    }
                                    anchor = -1
                                    cursor = -1
                                },
                                onDragCancel = {
                                    anchor = -1
                                    cursor = -1
                                },
                            )
                        },
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!settings.pinPlayer) item("header") { header() }
                    itemsIndexed(entries, key = { _, e -> e.key }) { _, e ->
                        Box(Modifier.widthIn(max = (settings.maxWidth * settings.readerFontSize).dp)) {
                            when (e) {
                                DescEntry -> DescriptionCard(video)
                                is ChapterEntry -> ChapterHeading(e.chapter, e.index == activeChapter)
                                is LineEntry -> {
                                    val i = e.index
                                    LineRow(
                                        line = lines[i],
                                        active = i == active,
                                        highlighted = marked.getOrElse(i) { false },
                                        selected = i in selLo..selHi && selLo >= 0,
                                        erasing = erasing,
                                        onTap = { seekAndScroll(lines[i].start) },
                                    )
                                }
                            }
                        }
                    }
                    when (status) {
                        Status.Loading -> item("loading") {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                        }
                        Status.Failed -> item("failed") {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("No transcript", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    failure ?: "YouTube did not give us captions for this video.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = { retry++ }, shapes = ButtonDefaults.shapes()) { Text("Try again") }
                            }
                        }
                        Status.Ready -> Unit
                    }
                }
            }
        }

        // Floating toolbar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 14.dp)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 10.dp,
            tonalElevation = 2.dp,
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showHighlights = true }) {
                    Icon(Icons.Rounded.BorderColor, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(6.dp))
                    Text("${video.highlights.size}", style = MaterialTheme.typography.labelLarge)
                }
                if (video.chapters.isNotEmpty()) {
                    IconButton(onClick = { showChapters = true }) { Icon(Icons.Rounded.FormatListBulleted, "Chapters") }
                }
                IconToggleButton(
                    checked = settings.autoScroll,
                    onCheckedChange = { v -> app.settings.update { it.copy(autoScroll = v) } },
                ) {
                    Icon(
                        Icons.Rounded.MyLocation, "Follow along",
                        tint = if (settings.autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconToggleButton(
                    checked = settings.pinPlayer,
                    onCheckedChange = { v -> app.settings.update { it.copy(pinPlayer = v) } },
                ) {
                    Icon(
                        Icons.Rounded.PushPin, "Pin player",
                        tint = if (settings.pinPlayer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { showSave = true }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Upload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clip")
                }
            }
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 90.dp),
        )
    }

    if (showSave) {
        SaveSheet(
            video = video,
            lines = lines,
            onDismiss = { showSave = false },
            onDone = { message, ok ->
                showSave = false
                if (ok) app.library.edit(videoId) { it.copy(clippedAt = System.currentTimeMillis()) }
                scope.launch { snackbar.showSnackbar(message) }
            },
        )
    }
    if (showHighlights) {
        HighlightsSheet(
            video = video,
            onDismiss = { showHighlights = false },
            onJump = { h ->
                showHighlights = false
                seekAndScroll(h.startSec)
            },
        )
    }
    if (showChapters) {
        val sheet = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showChapters = false }, sheetState = sheet) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("Chapters", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
                video.chapters.forEachIndexed { i, c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { showChapters = false; seekAndScroll(c.start) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            YouTube.formatTime(c.start),
                            Modifier.width(64.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (i == activeChapter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(c.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

private fun openInYouTube(ctx: android.content.Context, id: String) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(app) }.onFailure { runCatching { ctx.startActivity(web) } }
}

// ---------- player ----------

@Composable
private fun PlayerSurface(host: PlayerHost, modifier: Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { c -> FrameLayout(c).also { host.attach(it) } },
        onRelease = { host.detach(it) },
    )
}

@Composable
private fun PlayerBlock(video: SavedVideo, ps: State<PlayerState>, host: PlayerHost, ctx: android.content.Context) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.Black)) {
            PlayerSurface(host, Modifier.fillMaxSize())
            val err = ps.value.error
            if (err != null) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xE6000000)).padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (err == 101 || err == 150 || err == 153) "The owner does not allow this video to play inside other apps."
                        else "This video could not play here.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { openInYouTube(ctx, video.meta.id) }, shapes = ButtonDefaults.shapes()) { Text("Open in YouTube") }
                }
            }
        }
        TransportRow(ps, host)
    }
}

@Composable
private fun TransportRow(ps: State<PlayerState>, host: PlayerHost) {
    val speeds = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.75f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { host.skip(-10.0) }) { Icon(Icons.Rounded.Replay10, "Back 10 seconds") }
            MorphingPlayButton(playing = ps.value.playing, onClick = { host.toggle() }, size = 52.dp)
            IconButton(onClick = { host.skip(10.0) }) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds") }
            Spacer(Modifier.weight(1f))
            Text(
                "${YouTube.formatTime(ps.value.positionSec)} / ${YouTube.formatTime(ps.value.durationSec)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                val next = speeds[(speeds.indexOf(ps.value.speed).let { if (it < 0) 0 else it } + 1) % speeds.size]
                host.setSpeed(next)
            }) { Text("${ps.value.speed}x".replace(".0x", "x")) }
        }
        WavySeekBar(ps.value.positionSec, ps.value.durationSec, ps.value.playing, onSeek = { host.seekTo(it) })
    }
}

@Composable
private fun ChapterChips(chapters: List<Chapter>, active: Int, onClick: (Chapter) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) state.animateScrollToItem(active) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        itemsIndexed(chapters, key = { i, _ -> "chip$i" }) { i, c ->
            FilterChip(
                selected = i == active,
                onClick = { onClick(c) },
                label = { Text(c.title, maxLines = 1) },
            )
        }
    }
}

// ---------- transcript ----------

@Composable
private fun DescriptionCard(video: SavedVideo) {
    var open by remember { mutableStateOf(false) }
    if (video.meta.description.isBlank()) return
    Surface(
        modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = { open = !open },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (video.meta.published.isNotBlank()) {
                Text(
                    app.marginalia.obsidian.Template.formatDate(video.meta.published, "D MMM, YYYY") ?: video.meta.published,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                video.meta.description.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (open) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(if (open) "Show less" else "Show more", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ChapterHeading(c: Chapter, active: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val w by animateFloatAsState(if (active) 26f else 14f, LocalSpring.current.spec(), label = "dash")
        Box(Modifier.width(w.dp).height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Text(c.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(YouTube.formatTime(c.start), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LineRow(
    line: Line,
    active: Boolean,
    highlighted: Boolean,
    selected: Boolean,
    erasing: Boolean,
    onTap: () -> Unit,
) {
    val s = LocalSettings.current
    val scheme = MaterialTheme.colorScheme
    val mark = Color(s.highlightColor)
    val bgTarget = when {
        selected && erasing -> scheme.error.copy(alpha = 0.25f)
        selected -> mark.copy(alpha = 0.55f)
        highlighted && s.alwaysShowHighlights -> mark.copy(alpha = 0.32f)
        else -> Color.Transparent
    }
    val bg by animateColorAsState(bgTarget, LocalSpring.current.spec(), label = "mark")
    val bar by animateFloatAsState(if (active && s.highlightActiveLine) 1f else 0f, LocalSpring.current.spec(), label = "bar")
    val textColor = if (!s.highlightActiveLine || active) scheme.onSurface else scheme.onSurface.copy(alpha = 0.74f)
    val fontSize = s.readerFontSize

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape((10 * s.roundness).dp))
            .background(bg)
            .drawBehind {
                if (bar > 0.01f) {
                    drawRoundRect(
                        color = scheme.primary.copy(alpha = bar),
                        topLeft = Offset(0f, 6.dp.toPx()),
                        size = Size(3.dp.toPx(), size.height - 12.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
            .clickable(onClick = onTap)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        if (s.showTimestamps) {
            Text(
                YouTube.formatTime(line.start),
                Modifier.width(46.dp).padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (active) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        Text(
            line.text,
            Modifier.weight(1f),
            color = textColor,
            fontFamily = readerFamily(s.readerFont),
            fontSize = fontSize.sp,
            lineHeight = (fontSize * s.lineHeight).sp,
        )
    }
}

// ---------- highlights sheet ----------

@Composable
private fun HighlightsSheet(video: SavedVideo, onDismiss: () -> Unit, onJump: (Highlight) -> Unit) {
    val app = LocalApp.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                if (video.highlights.isEmpty()) "No highlights yet" else "${video.highlights.size} highlights",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            if (video.highlights.isEmpty()) {
                Text(
                    "Hold a line in the transcript. Keep your finger down and drag to take more lines.",
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false)) {
                itemsIndexed(video.highlights, key = { _, h -> h.id }) { _, h ->
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onJump(h) }) { Text(YouTube.formatTime(h.startSec)) }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { app.library.edit(video.meta.id) { v -> v.copy(highlights = v.highlights.filterNot { it.id == h.id }) } }) {
                                Icon(Icons.Rounded.Close, "Remove highlight")
                            }
                        }
                        Text(h.text, style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = h.note,
                            onValueChange = { n -> app.library.edit(video.meta.id) { v -> v.copy(highlights = v.highlights.map { if (it.id == h.id) it.copy(note = n) else it }) } },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Add a note") },
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
