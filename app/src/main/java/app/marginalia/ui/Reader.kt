package app.marginalia.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.marginalia.R
import app.marginalia.data.Chapter
import app.marginalia.data.Highlight
import app.marginalia.data.HighlightColors
import app.marginalia.data.Highlights
import app.marginalia.data.SavedVideo
import app.marginalia.data.TranscriptDoc
import app.marginalia.data.YouTube
import app.marginalia.obsidian.Template
import app.marginalia.player.PlayerHost
import app.marginalia.player.PlayerState
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Status { Loading, Ready, Failed }

@Composable
fun ReaderScreen(videoId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val view = LocalView.current
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
    var showAbout by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var headerHidden by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Start the audio right away. The transcript can arrive later.
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

    // The transcript as one block of text, built off the main thread.
    val doc by produceState<TranscriptDoc?>(null, transcript, settings.lineLength, settings.showTimestamps, video.chapters) {
        value = withContext(Dispatchers.Default) {
            transcript?.let { TranscriptDoc.build(it.cues, video.chapters, settings.lineLength, settings.showTimestamps) }
        }
    }
    val colors = remember(doc, video.highlights) { doc?.let { Highlights.colorByWord(it.words, video.highlights) } }
    val activeWord by remember(doc) { derivedStateOf { doc?.wordAtTime(ps.value.positionSec) ?: -1 } }
    val activeChapter by remember(video.chapters) {
        derivedStateOf { video.chapters.indexOfLast { it.start <= ps.value.positionSec + 0.5 } }
    }

    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val darkText = scheme.onSurface.toArgb()
    val style = ReaderStyle(
        textColor = darkText,
        mutedColor = scheme.onSurfaceVariant.toArgb(),
        accent = scheme.primary.toArgb(),
        sizeSp = settings.readerFontSize,
        lineMult = settings.lineHeight,
        typeface = when (settings.readerFont) {
            "sans" -> ResourcesCompat.getFont(ctx, R.font.atkinson_regular)
            "system" -> Typeface.DEFAULT
            "custom" -> customTypeface(settings.customFontPath) ?: ResourcesCompat.getFont(ctx, R.font.newsreader)
            else -> ResourcesCompat.getFont(ctx, R.font.newsreader)
        },
        highlightAlpha = if (scheme.background.luminanceApprox() < 0.5f) 0.42f else 0.55f,
        holdSelects = settings.holdSelects,
        maxWidthPx = with(density) { (settings.maxWidth * settings.readerFontSize).dp.roundToPx() },
        highlighterOn = settings.highlighterEnabled,
    )

    var tview by remember { mutableStateOf<TranscriptView?>(null) }
    LaunchedEffect(ps.value.error) { if (ps.value.error != null) app.updates.checkSoon() }
    var away by remember { mutableStateOf(false) }
    var editingId by remember(videoId) { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.autoScroll) { if (!settings.autoScroll) away = false }
    LaunchedEffect(activeWord, settings.autoScroll) {
        if (settings.autoScroll) tview?.follow(activeWord)
    }

    fun seek(sec: Double) {
        app.player.seekTo(sec)
        app.player.play()
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
                            text = { Text("About this video") },
                            leadingIcon = { Icon(Icons.Rounded.Description, null) },
                            onClick = { menu = false; showAbout = true },
                        )
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

            AnimatedVisibility(
                visible = settings.pinPlayer || !headerHidden,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    PlayerBlock(video, ps, app.player, ctx)
                    if (video.chapters.isNotEmpty()) {
                        ChapterChips(video.chapters, activeChapter) { c ->
                            seek(c.start)
                            doc?.let { d -> d.wordAtTime(c.start).takeIf { it >= 0 }?.let { tview?.scrollToWord(it) } }
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val d = doc
                if (d != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { c ->
                            TranscriptView(c).also { tv ->
                                tview = tv
                            }
                        },
                        update = { tv ->
                            tv.onSeek = { sec -> seek(sec) }
                            tv.onScrollDelta = { dy ->
                                if (dy > 6) headerHidden = true else if (dy < -6) headerHidden = false
                            }
                            tv.onAway = { away = it && settings.autoScroll }
                            tv.onTapWord = { w ->
                                val h = video.highlights.firstOrNull { it.w0 >= 0 && w >= it.w0 && w <= it.w1 }
                                if (h != null) { editingId = h.id; true } else false
                            }
                            tv.onPaint = { a, b, color ->
                                val next = Highlights.apply(video.highlights, d.words, d::plainText, a, b, color)
                                app.library.edit(videoId) { it.copy(highlights = next) }
                                app.settings.update { s -> s.copy(highlightColor = color) }
                                // open the note bar on the highlight that was just made
                                editingId = next.firstOrNull { it.w0 >= 0 && a >= it.w0 && a <= it.w1 }?.id
                            }
                            tv.onErase = { a, b ->
                                app.library.edit(videoId) { it.copy(highlights = Highlights.erase(it.highlights, d.words, d::plainText, a, b)) }
                            }
                            tv.bind(d, style)
                            colors?.let { tv.applyHighlights(it) }
                            tv.setActive(activeWord)
                        },
                    )
                } else if (status == Status.Failed) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("No transcript", style = MaterialTheme.typography.titleMedium)
                        Text(
                            failure ?: "YouTube did not give us captions for this video.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Button(onClick = { retry++ }, shapes = ButtonDefaults.shapes()) { Text("Try again") }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            }
        }

        // "Sync" pop-up: shown when follow-along is on and the reader has scrolled away.
        var syncing by remember { mutableStateOf(false) }
        val spin by androidx.compose.animation.core.animateFloatAsState(
            if (syncing) 360f else 0f, androidx.compose.animation.core.tween(480), label = "spin",
        )
        LaunchedEffect(syncing) { if (syncing) { kotlinx.coroutines.delay(520); syncing = false; away = false } }
        AnimatedVisibility(
            visible = away && settings.autoScroll && editingId == null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 86.dp),
            enter = androidx.compose.animation.slideInVertically(spring(0.6f, 420f)) { it } + androidx.compose.animation.scaleIn(spring(0.55f, 420f), initialScale = 0.6f) + fadeIn(),
            exit = androidx.compose.animation.scaleOut(spring(1f, 500f), targetScale = 0.7f) + fadeOut(),
        ) {
            Surface(
                onClick = { if (!syncing) { syncing = true; tview?.resync(activeWord) } },
                shape = RoundedCornerShape(30.dp),
                color = scheme.primaryContainer,
                shadowElevation = 8.dp,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.MyLocation, null, tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = spin },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sync to where it is", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
                }
            }
        }

        // Note bar for the highlight just made, or the one that was tapped.
        val editing = video.highlights.firstOrNull { it.id == editingId }
        AnimatedVisibility(
            visible = editing != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 86.dp),
            enter = androidx.compose.animation.slideInVertically(spring(0.7f, 420f)) { it / 2 } + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it / 2 } + fadeOut(),
        ) {
            var last by remember { mutableStateOf(editing) }
            if (editing != null) last = editing
            last?.let { h ->
                HighlightNoteBar(
                    h = h,
                    onNote = { text -> app.library.edit(videoId) { v -> v.copy(highlights = v.highlights.map { if (it.id == h.id) it.copy(note = text) else it }) } },
                    onColor = { c -> app.library.edit(videoId) { v -> v.copy(highlights = v.highlights.map { if (it.id == h.id) it.copy(color = c) else it }) } },
                    onDelete = {
                        editingId = null
                        app.library.edit(videoId) { v -> v.copy(highlights = v.highlights.filterNot { it.id == h.id }) }
                    },
                    onPlay = { seek(h.startSec) },
                    onClose = { editingId = null },
                )
            }
        }

        // Floating toolbar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 14.dp)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(40.dp),
            color = scheme.surfaceContainerHighest,
            shadowElevation = 10.dp,
            tonalElevation = 2.dp,
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showHighlights = true }) {
                    Icon(Icons.Rounded.BorderColor, null, Modifier.size(20.dp), tint = scheme.tertiary)
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
                    Icon(Icons.Rounded.MyLocation, "Follow along", tint = if (settings.autoScroll) scheme.primary else scheme.onSurfaceVariant)
                }
                IconToggleButton(
                    checked = settings.pinPlayer,
                    onCheckedChange = { v -> app.settings.update { it.copy(pinPlayer = v) } },
                ) {
                    Icon(Icons.Rounded.PushPin, "Pin player", tint = if (settings.pinPlayer) scheme.primary else scheme.onSurfaceVariant)
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
            lines = remember(transcript, settings.lineLength, video.chapters) {
                transcript?.let { YouTube.toLines(it.cues, settings.lineLength, video.chapters) }.orEmpty()
            },
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
                seek(h.startSec)
                doc?.let { d -> d.wordAtTime(h.startSec).takeIf { it >= 0 }?.let { tview?.scrollToWord(it) } }
            },
        )
    }
    if (showChapters) {
        val sheet = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showChapters = false }, sheetState = sheet) {
            Column(Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                Text("Chapters", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
                video.chapters.forEachIndexed { i, c ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            showChapters = false
                            seek(c.start)
                            doc?.let { d -> d.wordAtTime(c.start).takeIf { it >= 0 }?.let { tview?.scrollToWord(it) } }
                        }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            YouTube.formatTime(c.start),
                            Modifier.width(64.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (i == activeChapter) scheme.primary else scheme.onSurfaceVariant,
                        )
                        Text(c.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
    if (showAbout) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showAbout = false }, sheetState = sheet) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(video.meta.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(
                        video.meta.author.takeIf { it.isNotBlank() },
                        Template.formatDate(video.meta.published, "D MMM, YYYY"),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                )
                Text(video.meta.description.trim(), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun openInYouTube(ctx: android.content.Context, id: String) {
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(appIntent) }.onFailure { runCatching { ctx.startActivity(web) } }
}

// ---------- player area ----------

@Composable
private fun PlayerBlock(video: SavedVideo, ps: State<PlayerState>, host: PlayerHost, ctx: android.content.Context) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.Black)) {
            val s = ps.value
            if (s.videoOn) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        PlayerView(c).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = host.exo
                        }
                    },
                    onRelease = { it.player = null },
                )
            } else {
                AsyncImage(
                    model = video.meta.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.55f,
                )
            }
            if (s.loading || (s.buffering && !s.playing)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            if (s.error != null) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xE6000000)).padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.error, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { host.retry() }, shapes = ButtonDefaults.shapes()) { Text("Try again") }
                        FilledTonalButton(onClick = { openInYouTube(ctx, video.meta.id) }, shapes = ButtonDefaults.shapes()) { Text("Open in YouTube") }
                    }
                }
            }
            if (s.canWatch && s.error == null) {
                FilterChip(
                    selected = s.videoOn,
                    onClick = { host.setVideo(!s.videoOn) },
                    label = { Text(if (s.videoOn) "Listen" else "Watch") },
                    leadingIcon = { Icon(if (s.videoOn) Icons.Rounded.Headphones else Icons.Rounded.Videocam, null, Modifier.size(18.dp)) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                )
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
            FilterChip(selected = i == active, onClick = { onClick(c) }, label = { Text(c.title, maxLines = 1) })
        }
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
                    "Hold a word in the transcript. Drag the handles to take more, then pick a colour.",
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
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
                            HighlightColors.forEachIndexed { i, c ->
                                Box(
                                    Modifier.padding(horizontal = 3.dp).size(if (i == h.color) 26.dp else 20.dp).clip(CircleShape)
                                        .background(Color(c.ui))
                                        .clickable {
                                            app.library.edit(video.meta.id) { v -> v.copy(highlights = v.highlights.map { if (it.id == h.id) it.copy(color = i) else it }) }
                                        },
                                )
                            }
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

/** Quick note editor for one highlight: change its colour, write a note, play from it, or remove it. */
@Composable
private fun HighlightNoteBar(
    h: Highlight,
    onNote: (String) -> Unit,
    onColor: (Int) -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var text by remember(h.id) { mutableStateOf(h.note) }
    // save shortly after typing stops, and again when the bar goes away
    LaunchedEffect(text) { if (text != h.note) { kotlinx.coroutines.delay(450); onNote(text) } }
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHighest,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.text, Modifier.weight(1f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
                IconButton(onClick = { onNote(text); onClose() }) { Icon(Icons.Rounded.Check, "Done", tint = scheme.primary) }
            }
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add a note") },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HighlightColors.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(if (i == h.color) 32.dp else 26.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(c.ui.toInt()))
                            .then(if (i == h.color) Modifier.border(2.dp, scheme.onSurface, androidx.compose.foundation.shape.CircleShape) else Modifier)
                            .clickable { onColor(i) },
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, "Play from here") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Remove highlight") }
            }
        }
    }
}
