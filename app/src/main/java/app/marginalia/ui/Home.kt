package app.marginalia.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marginalia.data.Playlist
import app.marginalia.data.PlaylistImport
import app.marginalia.data.SavedVideo
import app.marginalia.data.VideoMeta
import app.marginalia.data.YouTube
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    incoming: String?,
    onConsumed: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    val videos by app.library.videos.collectAsState()
    val player by app.player.state.collectAsState()

    var addOpen by remember { mutableStateOf(false) }
    var addInitial by remember { mutableStateOf<String?>(null) }
    val subs by app.subscriptions.all.collectAsState()
    val feed by app.feed.state.collectAsState()
    val feedBusy by app.feed.busy.collectAsState()
    val nav = LocalNav.current

    var tabRequest by remember { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<String?>(null) }
    var actionsInPlaylist by remember { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(LibraryFilter()) }
    val playlists by app.playlists.all.collectAsState()
    val queue by app.queue.ids.collectAsState()

    // A playlist link that came from the YouTube app opens the Add pop-up with it filled in.
    LaunchedEffect(incoming) {
        if (incoming != null) {
            addInitial = incoming
            addOpen = true
            onConsumed()
        }
    }

    val visibleTabs = settings.homeTabs.filter { it in HomeTabs.keys }.ifEmpty { listOf("library") }
    var tab by remember { mutableStateOf(visibleTabs.first()) }
    if (tab !in visibleTabs) tab = visibleTabs.first()
    tabRequest?.let { if (it in visibleTabs) tab = it; tabRequest = null }
    val showLibrary = tab == "library" || visibleTabs.size == 1
    val showFeed = tab == "feed"
    val showQueue = tab == "queue"
    LaunchedEffect(tab, subs.size) { if (tab == "feed") app.feed.refresh() }
    var refreshing by remember { mutableStateOf(false) }
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

    Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                settings.vaultFolders.keys.forEach { app.vaults.refresh(it, force = true) }
                app.updates.check()
                app.feed.refresh(force = true)
                scope.launch { kotlinx.coroutines.delay(900); refreshing = false }
            },
            state = pullState,
            indicator = { PixelIndicator(pullState, refreshing, settings.pixelChar) },
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 120.dp,
            ),
        ) {
            item("head") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(30.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Marginalia", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { addInitial = null; addOpen = true }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add")
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                }
            }

            if (visibleTabs.size > 1) item("tabs") {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleTabs.forEach { id ->
                        androidx.compose.material3.FilterChip(selected = tab == id, onClick = { tab = id }, label = { Text(HomeTabs.getValue(id) + when (id) { "queue" -> queue.size.takeIf { it > 0 }?.let { " · $it" }; "feed" -> subs.size.takeIf { it > 0 }?.let { " · $it" }; else -> null }.orEmpty()) })
                    }
                }
            }

            if (showFeed) feedTab(
                subs = subs, feed = feed, busy = feedBusy, inLibrary = videos.map { it.meta.id }.toSet(),
                onOpenVideo = onOpen, onOpenChannel = nav.openChannel,
                onFind = { addInitial = null; addOpen = true },
            )
            if (showQueue) queueTab(
                queue = queue, videos = videos, nowId = player.videoId, onOpenVideo = onOpen,
                onClear = { app.queue.clear() }, onRemove = { app.queue.remove(it) },
            )

            val libraryVideos = videos.filter { it.playlistId.isEmpty() }
            val inPlaylist = showLibrary && openPlaylist != null && playlists.any { it.id == openPlaylist }
            if (inPlaylist) playlistsTab(
                playlists = playlists, videos = videos, openId = openPlaylist,
                onOpenPlaylist = { openPlaylist = it }, onOpenVideo = onOpen,
                onMore = { actionsInPlaylist = true; actionsFor = it },
            )
            val resume = libraryVideos.filter { it.positionSec > 5 && (it.meta.durationSec == 0 || it.positionSec < it.meta.durationSec - 10) }.take(10)
            if (showLibrary && !inPlaylist && playlists.isNotEmpty()) {
                item("pl-label") { SectionLabel("Playlists", trailing = "${playlists.size}") }
                item("pl-row") {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(playlists, key = { "pc" + it.id }) { p -> PlaylistCard(p, videos) { openPlaylist = p.id } }
                    }
                }
            }
            if (showLibrary && !inPlaylist && resume.isNotEmpty()) {
                item("resume-label") { SectionLabel("Pick up where you left off") }
                item("resume") {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(resume, key = { "r" + it.meta.id }) { v -> ResumeCard(v, { actionsInPlaylist = false; actionsFor = it }) { onOpen(v.meta.id) } }
                    }
                }
            }

            val pinned = libraryVideos.filter { it.pinned }
            if (showLibrary && !inPlaylist && pinned.isNotEmpty()) {
                item("pinned-label") { SectionLabel("Pinned") }
                items(pinned, key = { "p" + it.meta.id }) { v -> VideoRow(v, { actionsInPlaylist = false; actionsFor = it }) { onOpen(v.meta.id) } }
            }

            if (showLibrary && !inPlaylist) {
                item("lib-label") {
                    SectionLabel("Library", trailing = if (libraryVideos.isEmpty()) null else "${libraryVideos.size} video${if (libraryVideos.size == 1) "" else "s"}")
                }
                if (libraryVideos.isNotEmpty()) item("lib-controls") {
                    LibraryControls(
                        libraryVideos, settings.librarySort,
                        onSort = { s -> app.settings.update { it.copy(librarySort = s) } },
                        filter = filter, onFilter = { filter = it },
                    )
                }
                val shown = libraryVideos.filter { filter.matches(it) }.sortedBy(settings.librarySort)
                val more: (String) -> Unit = { actionsInPlaylist = false; actionsFor = it }
                if (libraryVideos.isEmpty()) {
                    item("empty") { EmptyShelf() }
                } else if (shown.isEmpty()) {
                    item("none") { Text("Nothing matches this filter.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else when (settings.libraryLayout) {
                    "grid" -> items(shown.chunked(2), key = { "g" + it.first().meta.id }) { pair -> VideoGridRow(pair, onOpen, more) }
                    "compact" -> items(shown, key = { "c" + it.meta.id }) { v -> VideoCompactRow(v, onOpen, more) }
                    else -> items(shown, key = { "v" + it.meta.id }) { v -> androidx.compose.foundation.layout.Box(Modifier.animateItem()) { VideoRow(v, more) { onOpen(v.meta.id) } } }
                }
            }
        }
        }

        if (addOpen) AddSheet(
            initial = addInitial,
            onDismiss = { addOpen = false; addInitial = null },
            onPlaylist = { openPlaylist = it; tabRequest = "library" },
        )

        actionsFor?.let { id -> VideoActionsSheet(id, inPlaylist = actionsInPlaylist, onDismiss = { actionsFor = null }) }

        // Now playing pill
        val nowId = player.videoId
        AnimatedVisibility(
            visible = nowId != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = expandVertically(spring(0.6f, 380f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clickable { nowId?.let(onOpen) },
                shape = RoundedCornerShape(36.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp,
            ) {
                val frac = if (player.durationSec > 0) (player.positionSec / player.durationSec).toFloat().coerceIn(0f, 1f) else 0f
                val track = MaterialTheme.colorScheme.onPrimaryContainer
                Row(
                    Modifier.drawBehind {
                        // A thin line along the bottom edge shows how far along the video is.
                        val h = 3.dp.toPx()
                        drawRect(track.copy(alpha = 0.12f), Offset(0f, size.height - h), Size(size.width, h))
                        drawRect(track.copy(alpha = 0.55f), Offset(0f, size.height - h), Size(size.width * frac, h))
                    }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = nowId?.let { "https://i.ytimg.com/vi/$it/default.jpg" },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(CircleShape),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(player.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(player.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                    }
                    MorphingPlayButton(playing = player.playing, onClick = { app.player.toggle() }, size = 52.dp)
                    IconButton(onClick = { app.player.stop() }) { Icon(Icons.Rounded.Close, "Stop", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
            }
        }
    }
}

private fun readClipboard(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim()
}

@Composable
private fun PreviewCard(
    meta: VideoMeta,
    pinPlayer: Boolean,
    highlighting: Boolean,
    onPin: (Boolean) -> Unit,
    onHighlighting: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Box {
                AsyncImage(
                    model = meta.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(MaterialTheme.shapes.extraLarge),
                )
                if (meta.durationSec > 0) {
                    Text(
                        YouTube.formatTime(meta.durationSec.toDouble()),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color_scrim).padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(meta.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(meta.author, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pinPlayer,
                        onClick = { onPin(!pinPlayer) },
                        label = { Text("Show video") },
                        leadingIcon = { Icon(Icons.Rounded.Videocam, null, Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = highlighting,
                        onClick = { onHighlighting(!highlighting) },
                        label = { Text("Highlighting") },
                        leadingIcon = { Icon(Icons.Rounded.Highlight, null, Modifier.size(18.dp)) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onOpen, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open reader")
                    }
                    TextButton(onClick = onDismiss) { Text("Not now") }
                }
            }
        }
    }
}

private val Color_scrim = androidx.compose.ui.graphics.Color(0xB3000000)

@Composable
private fun ResumeCard(v: SavedVideo, onMore: (String) -> Unit, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val radius = pressedRadius(source, 28.dp, 12.dp)
    Column(
        Modifier.width(220.dp).springPress(source).combinedClickable(source, null, onLongClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onMore(v.meta.id)
        }, onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = v.meta.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(radius)),
            )
            if (v.meta.durationSec > 0) {
                val frac = (v.positionSec / v.meta.durationSec).toFloat().coerceIn(0f, 1f)
                Box(
                    Modifier.align(Alignment.BottomStart).padding(12.dp).fillMaxWidth().height(5.dp)
                        .clip(CircleShape).background(androidx.compose.ui.graphics.Color(0x66000000)),
                ) {
                    Box(Modifier.fillMaxWidth(frac).height(5.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Text(v.meta.title, Modifier.padding(top = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        Text(v.meta.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VideoRow(v: SavedVideo, onMore: (String) -> Unit, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val app = LocalApp.current
    val source = remember { MutableInteractionSource() }
    val radius = pressedRadius(source, 22.dp, 8.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .springPress(source, 0.98f)
            .clip(RoundedCornerShape(26.dp))
            .combinedClickable(source, null, onLongClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onMore(v.meta.id)
            }, onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = v.meta.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(116.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(radius)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(v.meta.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(v.meta.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (v.highlights.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Highlight, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text("${v.highlights.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                }
                OfflineBadge(v.meta.id)
                if (v.clippedAt > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Clipped", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        IconButton(onClick = { app.library.edit(v.meta.id) { it.copy(pinned = !it.pinned) } }) {
            Icon(
                Icons.Rounded.PushPin, if (v.pinned) "Unpin" else "Pin",
                tint = if (v.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
        MoreButton { onMore(v.meta.id) }
    }
}

@Composable
private fun EmptyShelf() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer))
            Box(Modifier.size(64.dp).clip(MaterialShapes.Clover4Leaf.toShape()).background(MaterialTheme.colorScheme.tertiaryContainer))
            Box(Modifier.size(36.dp).clip(MaterialShapes.Pill.toShape()).background(MaterialTheme.colorScheme.secondaryContainer))
        }
        Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap Add to paste a link or search YouTube. Or share a video to Marginalia from the YouTube app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistCard(p: Playlist, videos: List<SavedVideo>, onClick: () -> Unit) {
    val first = p.videoIds.firstNotNullOfOrNull { id -> videos.firstOrNull { it.meta.id == id } }
    Column(Modifier.width(170.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        AsyncImage(
            model = first?.meta?.thumb, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(20.dp)),
        )
        Text(p.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        Text("${p.videoIds.size} videos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
