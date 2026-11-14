package app.marginalia.ui
import app.obsi.core.R as CoreR

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBarsPadding
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
import app.obsi.core.data.Chapter
import app.obsi.core.data.Highlight
import app.obsi.core.data.HighlightColors
import app.obsi.core.data.Highlights
import app.marginalia.data.SavedVideo
import app.obsi.core.data.TranscriptDoc
import app.marginalia.data.YouTube
import app.obsi.core.obsidian.Template
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
    var showActions by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var headerHidden by remember { mutableStateOf(false) }
    // The player folds into one row while you read, so the text gets the screen.
    var collapsed by remember(videoId) { mutableStateOf(false) }
    // Pinned: the big picture stays while you scroll.
    var pinned by remember(videoId) { mutableStateOf(false) }
    val pulled = remember { intArrayOf(0) }
    val foldAfter = with(LocalDensity.current) { 48.dp.roundToPx() }
    // Full screen reading: no top row, player, bottom bar, status bar or navigation bar.
    var immersive by remember { mutableStateOf(false) }
    // Full screen watching: the phone turns sideways and the picture takes everything.
    var watchFull by remember(videoId) { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = immersive) { immersive = false }
    androidx.activity.compose.BackHandler(enabled = watchFull) { watchFull = false }
    val activity = remember(ctx) {
        var c: android.content.Context = ctx
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        c as? android.app.Activity
    }
    DisposableEffect(watchFull) {
        if (watchFull) activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val barsOff = immersive || watchFull
    androidx.compose.runtime.DisposableEffect(barsOff) {
        val window = activity?.window
        val controller = window?.let { androidx.core.view.WindowInsetsControllerCompat(it, it.decorView) }
        if (barsOff) {
            controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Start the audio right away. The transcript can arrive later.
    LaunchedEffect(videoId) {
        // A highlight picked in search starts at that highlight, even near the end.
        val start = app.pendingSeek.value?.takeIf { it.first == videoId }?.second ?: video.resumeSec
        app.player.load(videoId, video.meta.title, video.meta.author, start)
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

    // Playback keeps its own place while it runs. This catches a seek made while paused.
    DisposableEffect(videoId) { onDispose { app.player.savePosition() } }

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
    val noted = remember(doc, video.highlights) { doc?.let { Highlights.notedRuns(it.words, video.highlights) } }
    val runs = remember(doc, video.highlights) { doc?.let { Highlights.runs(it.words, video.highlights) } }
    val activeWord by remember(doc) { derivedStateOf { doc?.wordAtTime(ps.value.positionSec) ?: -1 } }
    val playerError by remember { derivedStateOf { ps.value.error } }
    val canWatch by remember { derivedStateOf { ps.value.canWatch } }
    val activeChapter by remember(video.chapters) {
        derivedStateOf { video.chapters.indexOfLast { it.start <= ps.value.positionSec + 0.5 } }
    }

    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val darkText = scheme.onSurface.toArgb()
    val readerTypeface = remember(settings.readerFont, settings.customFontPath) {
        when (settings.readerFont) {
            "sans" -> ResourcesCompat.getFont(ctx, CoreR.font.atkinson_regular)
            "system" -> Typeface.DEFAULT
            "custom" -> customTypeface(settings.customFontPath) ?: ResourcesCompat.getFont(ctx, CoreR.font.newsreader)
            else -> ResourcesCompat.getFont(ctx, CoreR.font.newsreader)
        }
    }
    val style = ReaderStyle(
        textColor = darkText,
        mutedColor = scheme.onSurfaceVariant.toArgb(),
        accent = scheme.primary.toArgb(),
        sizeSp = settings.readerFontSize,
        lineMult = settings.lineHeight,
        typeface = readerTypeface,
        highlightAlpha = if (scheme.background.luminanceApprox() < 0.5f) 0.30f else 0.55f,
        holdSelects = settings.holdSelects,
        maxWidthPx = with(density) { (settings.maxWidth * settings.readerFontSize).dp.roundToPx() },
        highlighterOn = settings.highlighterEnabled,
    )

    var tview by remember { mutableStateOf<TranscriptView?>(null) }
    LaunchedEffect(playerError) { if (playerError != null) app.updates.checkSoon() }
    // "Show the video" is a setting. Otherwise the reader is listen-only and has no picture.
    var videoAsked by remember(videoId) { mutableStateOf(false) }
    // Coming back to a video that already plays must not restart it just to show the picture.
    val alreadyPlaying = remember(videoId) { app.player.state.value.videoId == videoId && app.player.exo.mediaItemCount > 0 }
    LaunchedEffect(canWatch) {
        if (!videoAsked && canWatch) {
            videoAsked = true
            if (!alreadyPlaying && settings.pinPlayer && !ps.value.videoOn) app.player.setVideo(true)
        }
    }
    // Find in this video. Searches only this transcript, nothing else.
    var searching by remember(videoId) { mutableStateOf(false) }
    var query by remember(videoId) { mutableStateOf("") }
    var matchCount by remember(videoId) { mutableIntStateOf(0) }
    var matchAt by remember(videoId) { mutableIntStateOf(-1) }
    LaunchedEffect(searching) {
        tview?.searching = searching
        if (!searching) { tview?.clearSearch(); query = ""; matchCount = 0; matchAt = -1 }
    }
    var away by remember { mutableStateOf(false) }
    var editingId by remember(videoId) { mutableStateOf<String?>(null) }
    var noteFocused by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(settings.autoScroll) { if (!settings.autoScroll) away = false }
    // The spoken word changes several times a second. It goes straight to the text view, so this
    // whole screen does not redraw for it.
    LaunchedEffect(tview, doc, settings.autoScroll) {
        androidx.compose.runtime.snapshotFlow { activeWord }.collect { w ->
            tview?.setActive(w)
            if (settings.autoScroll) tview?.follow(w)
        }
    }
    androidx.activity.compose.BackHandler(enabled = searching) { searching = false }
    androidx.activity.compose.BackHandler(enabled = editingId != null) { editingId = null }
    // Working on a highlight: stay where you are, and offer the Sync button.
    LaunchedEffect(editingId) {
        if (editingId != null) tview?.pauseFollow()
        else { noteFocused = false; tview?.stopResize() }
    }
    // The note bar takes room from the text. Keep the marked words in view above it.
    LaunchedEffect(editingId) {
        val id = editingId ?: return@LaunchedEffect
        val w = video.highlights.firstOrNull { it.id == id }?.w0 ?: return@LaunchedEffect
        // The bar and the keyboard take a moment to settle, so look again until the marked words are clear of them.
        repeat(3) {
            delay(380)
            val tv = tview ?: return@LaunchedEffect
            if (w >= 0 && !tv.isComfortable(w)) { tv.scrollToWord(w); tv.pauseFollow() }
        }
    }
    // Playback waits only while you are in the note box, not every time a highlight is made.
    var pausedForNote by remember { mutableStateOf(false) }
    val typing = editingId != null && noteFocused
    LaunchedEffect(searching) { if (searching) collapsed = true }
    LaunchedEffect(typing) {
        if (typing) {
            collapsed = true
            if (settings.pauseWhileNoting && app.player.state.value.playing) { app.player.pause(); pausedForNote = true }
        } else if (pausedForNote) {
            pausedForNote = false
            app.player.play()
        }
    }
    DisposableEffect(Unit) { onDispose { if (pausedForNote) app.player.play() } }

    // A tap on a far word loses your place. For a few seconds there is a way back.
    var jumpedFrom by remember(videoId) { mutableStateOf<Double?>(null) }
    LaunchedEffect(jumpedFrom) { if (jumpedFrom != null) { delay(7000); jumpedFrom = null } }
    fun noteJump(to: Double) {
        val now = app.player.state.value.positionSec
        if (kotlin.math.abs(to - now) > 30) jumpedFrom = now
    }

    fun seek(sec: Double) {
        noteJump(sec)
        app.player.seekTo(sec)
        app.player.play()
    }

    val pictureOn by remember { derivedStateOf { ps.value.videoOn } }
    LaunchedEffect(pictureOn) { if (!pictureOn) watchFull = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(if (immersive) 28.dp else if (watchFull) 0.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
            val noPicture by remember { derivedStateOf { ps.value.noPicture } }
            LaunchedEffect(noPicture) { if (noPicture) snackbar.showSnackbar("This video has sound only.") }
            if (!immersive) ReaderHeader(
                video = video, ps = ps, host = app.player,
                collapsed = collapsed, onExpand = { collapsed = false }, onBack = { if (watchFull) watchFull = false else onBack() },
                searching = searching, onFind = { searching = !searching },
                pinned = pinned, onPin = { pinned = !pinned; if (pinned) collapsed = false },
                chapters = video.chapters,
                onJump = { noteJump(it); app.player.seekTo(it) },
                full = watchFull, onFull = { collapsed = false; editingId = null; searching = false; watchFull = !watchFull },
                menuOpen = menu, onMenu = { menu = it },
            ) {
                // Four switches for how you read, as tiles. Then what is about the video, as a short list.
                val watching = ps.value.videoOn
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuTile("Find", Icons.Rounded.Search, on = searching) { menu = false; searching = true }
                    MenuTile("Follow", Icons.Rounded.MyLocation, on = settings.autoScroll) { app.settings.update { it.copy(autoScroll = !it.autoScroll) } }
                    MenuTile("Text only", Icons.Rounded.Fullscreen, on = false) { menu = false; immersive = true }
                    MenuTile(
                        if (watching) "Listen" else "Video", if (watching) Icons.Rounded.Headphones else Icons.Rounded.Videocam,
                        on = false, enabled = watching || !ps.value.noPicture,
                    ) { menu = false; app.player.setVideo(!watching) }
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp), color = scheme.outlineVariant.copy(alpha = 0.4f))
                DropdownMenuItem(
                    text = { Text("Mark as done") },
                    leadingIcon = { Icon(Icons.Rounded.TaskAlt, null) },
                    trailingIcon = { if (video.done) Icon(Icons.Rounded.Check, "On", tint = scheme.primary) },
                    onClick = { app.setDone(videoId, !video.done) },
                )
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
                    text = { Text("Tags, queue and note") },
                    leadingIcon = { Icon(Icons.Rounded.Sell, null) },
                    onClick = { menu = false; showActions = true },
                )
            }

            if (searching && !immersive) {
                FindBar(
                    query = query,
                    count = matchCount,
                    at = matchAt,
                    onQuery = {
                        query = it
                        matchCount = tview?.search(it) ?: 0
                        matchAt = if (matchCount > 0) 0 else -1
                    },
                    onStep = { d -> tview?.goToMatch(matchAt + d)?.let { matchAt = it } },
                    onClose = { searching = false },
                )
            }

            playerError?.let { e ->
                if (!immersive) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(e, color = scheme.error, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { app.player.retry() }, shapes = ButtonDefaults.shapes()) { Text("Try again") }
                        FilledTonalButton(onClick = { openInYouTube(ctx, video.meta.id) }, shapes = ButtonDefaults.shapes()) { Text("Open in YouTube") }
                    }
                }
            }

            // The long transcript is laid out only after the screen has slid in. Doing both at once made it stutter.
            var settled by remember(videoId) { mutableStateOf(false) }
            LaunchedEffect(videoId) { delay(380); settled = true }
            val fade by androidx.compose.animation.core.animateFloatAsState(if (settled) 1f else 0f, androidx.compose.animation.core.tween(220), label = "transcriptFade")
            // The text stops above the phone's own buttons, so nothing is read through them.
            Box(Modifier.weight(1f).fillMaxWidth().then(if (barsOff || editingId != null) Modifier else Modifier.navigationBarsPadding()).clipToBounds().graphicsLayer { alpha = fade }) {
                val d = doc
                if (d != null && settled) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { c ->
                            TranscriptView(c).also { tv ->
                                tview = tv
                            }
                        },
                        update = { tv ->
                            tv.onSeek = { sec -> seek(sec) }
                            // Only your own finger folds the player and hides the bar. Follow-along scrolling leaves them alone.
                            tv.onUserScroll = { dy, atTop ->
                                if (dy > 6) headerHidden = true else if (dy < -6) headerHidden = false
                                if (atTop) { collapsed = false; pulled[0] = 0 }
                                else if (dy > 0 && !pinned) { pulled[0] += dy; if (pulled[0] > foldAfter) collapsed = true }
                                else pulled[0] = 0
                            }
                            tv.currentColor = settings.highlightColor
                            tv.onAway = { away = it && settings.autoScroll }
                            tv.onTapWord = { w ->
                                val h = Highlights.at(video.highlights, w)
                                when {
                                    // Tapping a highlight opens it with handles on its ends, ready to drag.
                                    h != null -> { editingId = h.id; tv.startResize(h.id, h.w0..h.w1); true }
                                    // A tap beside it puts it away.
                                    editingId != null -> { editingId = null; true }
                                    // In full screen a stray tap must not jump the audio.
                                    else -> immersive
                                }
                            }
                            tv.onPaint = { a, b, color ->
                                val next = Highlights.apply(video.highlights, d.words, d::plainText, a, b, color)
                                app.library.edit(videoId) { it.copy(highlights = next) }
                                app.settings.update { s -> s.copy(highlightColor = color) }
                                // open the note bar on the highlight that was just made, with handles on it
                                val made = next.firstOrNull { it.w0 == minOf(a, b) && it.w1 == maxOf(a, b) }
                                editingId = made?.id
                                if (made != null) tv.post { tv.startResize(made.id, made.w0..made.w1) }
                            }
                            tv.onResize = { id, a, b ->
                                app.library.edit(videoId) { it.copy(highlights = Highlights.resize(it.highlights, d.words, d::plainText, id, a, b)) }
                            }
                            tv.onErase = { a, b ->
                                app.library.edit(videoId) { it.copy(highlights = Highlights.erase(it.highlights, d.words, d::plainText, a, b)) }
                            }
                            tv.bind(d, style)
                            colors?.let { tv.applyHighlights(it, runs.orEmpty()) }
                            noted?.let { tv.applyNotes(it) }
                            tv.setActive(androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { activeWord })
                        },
                    )
                } else if (status == Status.Failed) {
                    SongNotes(video, failure, now = { ps.value.positionSec }, onSeek = { seek(it) }, onRetry = { retry++ })
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            }
            // Note bar for the highlight just made, or the one that was tapped.
            val editing = video.highlights.firstOrNull { it.id == editingId }
            // Tags and links come from the vault you save to.
            val noteVault = settings.linkedVault(settings.vault) ?: settings.vaultFolders.keys.firstOrNull()
            val tagPool = remember(editingId, noteVault) {
                app.library.videos.value.flatMap { v -> v.highlights.flatMap { it.tags } }
                    .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } +
                    (noteVault?.let { app.vaults.indexes.value[it]?.tags?.keys }.orEmpty())
            }
            AnimatedVisibility(
                visible = editing != null,
                enter = androidx.compose.animation.expandVertically(spring(1f, 500f)) + fadeIn(),
                exit = androidx.compose.animation.shrinkVertically(spring(1f, 500f)) + fadeOut(),
            ) {
                var last by remember { mutableStateOf(editing) }
                if (editing != null) last = editing
                last?.let { h ->
                    HighlightNoteBar(
                        h = h,
                        onNote = { text -> app.library.edit(videoId) { v -> v.copy(highlights = v.highlights.map { if (it.id == h.id) it.copy(note = text) else it }) } },
                        tags = h.tags,
                        onTags = { t -> app.library.edit(videoId) { v -> v.copy(highlights = v.highlights.map { if (it.id == h.id) it.copy(tags = t) else it }) } },
                        vault = noteVault,
                        suggestTags = tagPool,
                        onTyping = { noteFocused = it },
                        onColor = { c ->
                            app.library.edit(videoId) { v -> v.copy(highlights = v.highlights.map { if (it.id == h.id) it.copy(color = c) else it }) }
                            app.settings.update { it.copy(highlightColor = c) } // the next highlight starts with this colour
                        },
                        onDelete = {
                            editingId = null
                            app.library.edit(videoId) { v -> v.copy(highlights = v.highlights.filterNot { it.id == h.id }) }
                            scope.launch {
                                val r = snackbar.showSnackbar("Highlight deleted", actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Short)
                                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) app.library.edit(videoId) { v -> v.copy(highlights = v.highlights + h) }
                            }
                        },
                        onPlay = { seek(h.startSec) },
                        onClose = { editingId = null },
                    )
                }
            }
        }

        // Bottom bar: only what you need while reading. It slides away when you scroll down
        // and comes back when you scroll up. The chapter you are in is its middle button.
        AnimatedVisibility(
            visible = !headerHidden && !barsOff && editingId == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.slideInVertically(spring(1f, 500f)) { it },
            exit = androidx.compose.animation.slideOutVertically(spring(1f, 500f)) { it },
        ) {
            Surface(modifier = Modifier.fillMaxWidth(), color = scheme.surfaceContainer) {
                Row(
                    Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showHighlights = true }) {
                        Icon(Icons.Rounded.BorderColor, "Highlights", Modifier.size(20.dp), tint = scheme.tertiary)
                        if (video.highlights.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text("${video.highlights.size}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                        }
                    }
                    val chapter = video.chapters.getOrNull(activeChapter)
                    if (video.chapters.isNotEmpty()) TextButton(onClick = { showChapters = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.FormatListBulleted, "Chapters", Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            chapter?.title ?: "Chapters", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
                        )
                    } else Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    // The loud button means one thing: you marked something that is not in Obsidian yet.
                    val clipped = video.clippedAt > 0
                    val unsaved = video.highlights.count { it.createdAt > video.clippedAt }
                    val label = when { !clipped -> "Clip"; unsaved > 0 -> "Clip $unsaved new"; else -> "Clipped" }
                    val icon = if (clipped && unsaved == 0) Icons.Rounded.Check else Icons.Rounded.Upload
                    if (unsaved > 0) Button(onClick = { showSave = true }, shapes = ButtonDefaults.shapes()) {
                        Icon(icon, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, maxLines = 1)
                    } else FilledTonalButton(onClick = { showSave = true }, shapes = ButtonDefaults.shapes()) {
                        Icon(icon, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, maxLines = 1)
                    }
                }
            }
        }

        // Shows whenever you have scrolled away from the spot that is being read.
        val bottomGap by androidx.compose.animation.core.animateDpAsState(
            if (!headerHidden && !barsOff && editingId == null) 76.dp else 16.dp, spring(1f, 500f), label = "syncGap",
        )
        // It only shows when the spoken word is out of sight. Scrolled away, but still looking at it, there is nothing to go back to.
        var wordOffScreen by remember { mutableStateOf(true) }
        LaunchedEffect(away) {
            while (away) {
                wordOffScreen = tview?.isVisible(activeWord)?.not() ?: true
                delay(250)
            }
        }
        val offerNow = away && wordOffScreen && settings.autoScroll && editingId == null && !watchFull
        AnimatedVisibility(
            visible = offerNow,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = bottomGap),
            enter = fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f),
            exit = fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.85f),
        ) {
            FloatingPill(Icons.Rounded.MyLocation, "Back to now") { tview?.resync(activeWord); away = false }
        }
        var lastJump by remember { mutableStateOf(0.0) }
        jumpedFrom?.let { lastJump = it }
        AnimatedVisibility(
            visible = jumpedFrom != null && !offerNow && editingId == null && !watchFull,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = bottomGap),
            enter = fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f),
            exit = fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.85f),
        ) {
            FloatingPill(Icons.Rounded.Replay, "Back to ${YouTube.formatTime(lastJump)}") {
                val to = lastJump
                jumpedFrom = null
                app.player.seekTo(to)
                doc?.wordAtTime(to)?.takeIf { it >= 0 }?.let { tview?.resync(it) }
            }
        }

        // The only control left in full screen. It is faint, so it does not pull the eye from the text.
        AnimatedVisibility(
            visible = immersive,
            modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.displayCutout).padding(top = 6.dp, end = 6.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            IconButton(onClick = { immersive = false }) {
                Icon(Icons.Rounded.FullscreenExit, "Leave full screen", tint = scheme.onSurface.copy(alpha = 0.35f))
            }
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 72.dp),
        )
    }

    // A highlight picked in search: show the place it came from.
    val pending by app.pendingSeek.collectAsState()
    LaunchedEffect(pending, videoId, doc, tview) {
        val p = pending ?: return@LaunchedEffect
        if (p.first != videoId) return@LaunchedEffect
        val d = doc
        val tv = tview
        if (transcript != null && (d == null || tv == null)) return@LaunchedEffect
        settleText()
        d?.wordAtTime(p.second)?.takeIf { it >= 0 }?.let { tv?.scrollToWord(it) }
        app.pendingSeek.value = null
    }
    // Coming back to a video: the page opens where you are, not at the first line.
    var placed by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(videoId, doc, tview) {
        val d = doc ?: return@LaunchedEffect
        val tv = tview ?: return@LaunchedEffect
        if (placed) return@LaunchedEffect
        placed = true
        if (app.pendingSeek.value?.first == videoId) return@LaunchedEffect
        settleText()
        d.wordAtTime(app.player.state.value.positionSec).takeIf { it > 0 }?.let { tv.scrollToWord(it) }
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
        SmoothSheet(onDismissRequest = { showChapters = false }, sheetState = sheet) {
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
    if (showActions) VideoActionsSheet(videoId, onDismiss = { showActions = false })
    if (showAbout) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        SmoothSheet(onDismissRequest = { showAbout = false }, sheetState = sheet) {
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

/** A small button that floats over the text. It casts a shadow, so it never reads as part of the page. */
@Composable
private fun FloatingPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun openInYouTube(ctx: android.content.Context, id: String) {
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(appIntent) }.onFailure { runCatching { ctx.startActivity(web) } }
}

// ---------- highlights sheet ----------

@Composable
private fun HighlightsSheet(video: SavedVideo, onDismiss: () -> Unit, onJump: (Highlight) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbar = remember { SnackbarHostState() }
    var find by remember { mutableStateOf("") }
    val ordered = remember(video.highlights, find) { video.highlights.sortedBy { it.startSec }.filter { matchesQuery(find, it.text, it.note, it.tags.joinToString(" ")) } }
    fun change(h: Highlight, block: (Highlight) -> Highlight) =
        app.library.edit(video.meta.id) { v -> v.copy(highlights = v.highlights.map { if (it.id == h.id) block(it) else it }) }
    SmoothSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Box {
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
                // A long list is hard to scan. The box finds a word in what you highlighted or wrote.
                if (video.highlights.size >= 5) SearchField(find, { find = it }, "Search these highlights")
                if (find.isNotBlank() && ordered.isEmpty()) Text(
                    "No highlight matches \"$find\".", Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    itemsIndexed(ordered, key = { _, h -> h.id }) { _, h ->
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
                                ColorDots(h.color) { i -> change(h) { it.copy(color = i) } }
                                IconButton(onClick = {
                                    app.library.edit(video.meta.id) { v -> v.copy(highlights = v.highlights.filterNot { it.id == h.id }) }
                                    scope.launch {
                                        val r = snackbar.showSnackbar("Highlight deleted", actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Short)
                                        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) app.library.edit(video.meta.id) { v -> v.copy(highlights = v.highlights + h) }
                                    }
                                }) { Icon(Icons.Rounded.Close, "Remove highlight") }
                            }
                            if (h.text.isNotBlank()) Text(h.text, style = MaterialTheme.typography.bodyMedium)
                            var note by remember(h.id) { mutableStateOf(h.note) }
                            SaveWhenIdle(note, h.note) { n -> change(h) { it.copy(note = n) } }
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Add a note") },
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                            if (h.tags.isNotEmpty()) Text(
                                h.tags.joinToString(" ") { "#$it" }, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
    }
}

/** The text view lays its words out a frame or two after it gets them. Before that it has no line positions. */
private suspend fun settleText() = repeat(3) { androidx.compose.runtime.withFrameNanos { } }

/** Saves typed text shortly after typing stops, and once more when the box goes away. */
@Composable
private fun SaveWhenIdle(text: String, saved: String, onSave: (String) -> Unit) {
    LaunchedEffect(text) { if (text != saved) { delay(450); onSave(text) } }
    val latest by androidx.compose.runtime.rememberUpdatedState(text)
    val stored by androidx.compose.runtime.rememberUpdatedState(saved)
    val save by androidx.compose.runtime.rememberUpdatedState(onSave)
    DisposableEffect(Unit) { onDispose { if (latest != stored) save(latest) } }
}

/**
 * Note editor for one highlight. It sits on the keyboard and pushes the text up, so the words you marked stay in view.
 * One slim field, then one row of buttons. The colours open only when you ask.
 */
@Composable
private fun HighlightNoteBar(
    h: Highlight,
    onNote: (String) -> Unit,
    tags: List<String>,
    onTags: (List<String>) -> Unit,
    vault: String?,
    suggestTags: List<String>,
    onTyping: (Boolean) -> Unit,
    onColor: (Int) -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var text by remember(h.id) { mutableStateOf(h.note) }
    var tagMode by remember(h.id) { mutableStateOf(false) }
    var colorOpen by remember(h.id) { mutableStateOf(false) }
    var newTag by remember(h.id) { mutableStateOf("") }
    var linking by remember(h.id) { mutableStateOf(false) }
    var noteFocus by remember(h.id) { mutableStateOf(false) }
    // Playback stays paused for the whole note: the box, the tag entry and the link picker. Only leaving all of them lets it go on.
    LaunchedEffect(noteFocus, tagMode, linking) { onTyping(noteFocus || tagMode || linking) }
    // True when the picker was opened by typing [[, so the typed brackets are replaced and not doubled.
    var linkTyped by remember(h.id) { mutableStateOf(false) }
    fun addTag(raw: String) {
        val t = raw.trim().removePrefix("#").replace(' ', '-')
        if (t.isNotEmpty() && tags.none { it.equals(t, true) }) onTags(tags + t)
        newTag = ""
    }
    androidx.compose.runtime.key(h.id) { SaveWhenIdle(text, h.note, onNote) }
    val tone = HighlightColors.getOrNull(h.color)?.ui?.let { Color(it) } ?: scheme.primary
    val circle = androidx.compose.foundation.shape.CircleShape
    // Like a notes app: roomy by default. With the keyboard up the box still leaves a few lines of the text above it.
    val screenDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val imeUp = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val fieldMax = (screenDp * (if (imeUp) 0.2f else 0.36f)).dp

    Column(
        Modifier.fillMaxWidth().background(scheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        androidx.compose.material3.HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
        Column(Modifier.padding(horizontal = 12.dp).padding(top = 2.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(start = 4.dp).width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(tone))
                Text(
                    h.text.ifBlank { "Note at ${YouTube.formatTime(h.startSec)}" },
                    Modifier.weight(1f).padding(horizontal = 10.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
                IconButton(onClick = { onNote(text); onClose() }) { Icon(Icons.Rounded.Check, "Done", tint = scheme.primary) }
            }
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { v ->
                    // Typing [[ opens the note picker, like in Obsidian.
                    if (vault != null && v.endsWith("[[") && !text.endsWith("[[")) { linkTyped = true; linking = true }
                    text = v
                },
                modifier = Modifier.fillMaxWidth().heightIn(max = fieldMax).onFocusChanged { noteFocus = it.isFocused },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary),
                minLines = 3,
                maxLines = 12,
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().background(scheme.surfaceContainerHighest, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (text.isEmpty()) Text(
                            if (vault != null) "Add a note. Type [[ to link" else "Add a note",
                            style = MaterialTheme.typography.bodyLarge, color = scheme.outline,
                        )
                        inner()
                    }
                },
            )
            if (tags.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tags.forEach { t ->
                    androidx.compose.material3.InputChip(
                        selected = false, onClick = { onTags(tags - t) },
                        label = { Text("#$t") }, trailingIcon = { Icon(Icons.Rounded.Close, "Remove $t", Modifier.size(16.dp)) },
                    )
                }
            }
            if (tagMode) {
                PillField(
                    newTag, { newTag = it }, "Add a tag", onDone = { addTag(newTag) },
                    trailing = { if (newTag.isNotBlank()) IconButton(onClick = { addTag(newTag) }) { Icon(Icons.Rounded.Check, "Add tag") } },
                )
                val near = suggestTags.filter { s -> tags.none { it.equals(s, true) } && (newTag.isBlank() || s.contains(newTag.removePrefix("#"), true)) }.take(8)
                if (near.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(near, key = { "nt$it" }) { s -> androidx.compose.material3.FilterChip(selected = false, onClick = { addTag(s) }, label = { Text("#$s") }) }
                }
            }
            if (colorOpen) ColorDots(h.color, Modifier.fillMaxWidth()) { onColor(it); colorOpen = false }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(circle).clickable(onClickLabel = "Change the colour") { colorOpen = !colorOpen },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(22.dp).clip(circle).background(tone).border(2.dp, scheme.onSurface.copy(alpha = if (colorOpen) 0.9f else 0.35f), circle))
                }
                IconButton(onClick = { tagMode = !tagMode }, Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Sell, "Tags", tint = if (tagMode || tags.isNotEmpty()) scheme.primary else scheme.onSurfaceVariant)
                }
                if (vault != null) IconButton(onClick = { linkTyped = false; linking = true }, Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Link, "Link a note", tint = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPlay, Modifier.size(40.dp)) { Icon(Icons.Rounded.PlayArrow, "Play from here", tint = scheme.onSurfaceVariant) }
                IconButton(onClick = onDelete, Modifier.size(40.dp)) { Icon(Icons.Rounded.Delete, "Remove highlight", tint = scheme.onSurfaceVariant) }
            }
        }
    }

    if (linking && vault != null) OmniSheet(
        vault = vault, title = "Link a note",
        kinds = setOf(app.obsi.core.data.OmniKind.Note),
        onPick = { item ->
            val name = item.title
            text = (if (linkTyped && text.endsWith("[[")) text.removeSuffix("[[") else text.trimEnd() + (if (text.isBlank()) "" else " ")) + "[[$name]]"
            linking = false
        },
        onDismiss = { linking = false },
    )
}

/** Search box for the transcript in front of you. Shows how many matches and steps between them. */
@Composable
private fun FindBar(query: String, count: Int, at: Int, onQuery: (String) -> Unit, onStep: (Int) -> Unit, onClose: () -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            placeholder = { Text("Find in this video") },
            shape = RoundedCornerShape(50),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onStep(1) }),
        )
        Text(
            if (query.trim().length < 2) "" else if (count == 0) "None" else "${at + 1}/$count",
            Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = { onStep(-1) }, enabled = count > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous match") }
        IconButton(onClick = { onStep(1) }, enabled = count > 0) { Icon(Icons.Rounded.KeyboardArrowDown, "Next match") }
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close search") }
    }
}

/** A square switch in the reader's menu: an icon over a short name, filled while it is on. */
@Composable
private fun MenuTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.size(width = 68.dp, height = 64.dp).clip(RoundedCornerShape(16.dp))
            .background(if (on) scheme.primaryContainer else scheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        val tint = when {
            !enabled -> scheme.outline
            on -> scheme.onPrimaryContainer
            else -> scheme.onSurface
        }
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}
