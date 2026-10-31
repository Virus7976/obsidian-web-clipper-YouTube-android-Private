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
import androidx.compose.material.icons.rounded.QueueMusic
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    // Only which video plays. The position changes five times a second and must not redraw this list.
    val nowId by remember { app.player.state.map { it.videoId }.distinctUntilChanged() }.collectAsState(app.player.state.value.videoId)

    var addOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var actionsMeta by remember { mutableStateOf<VideoMeta?>(null) }
    var newPlaylistOpen by remember { mutableStateOf(false) }
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var feedFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("all") }
    var addInitial by remember { mutableStateOf<String?>(null) }
    val subs by app.subscriptions.all.collectAsState()
    val feed by app.feed.state.collectAsState()
    val feedBusy by app.feed.busy.collectAsState()
    val nav = LocalNav.current

    var tabRequest by remember { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<String?>(null) }
    var actionsInPlaylist by remember { mutableStateOf(false) }
    var openPlaylist by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var filter by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = LibraryFilterSaver) { mutableStateOf(LibraryFilter()) }
    val playlists by app.playlists.all.collectAsState()
    val queue by app.queue.ids.collectAsState()
    val reviewCards by app.reviews.cards.collectAsState()
    val dueCount = remember(videos, reviewCards) { app.reviews.due(videos).size }

    // A playlist link that came from the YouTube app opens the Add pop-up with it filled in.
    LaunchedEffect(incoming) {
        if (incoming != null) {
            addInitial = incoming
            addOpen = true
            onConsumed()
        }
    }

    val visibleTabs = settings.homeTabs.filter { it in HomeTabs.keys }.ifEmpty { listOf("library") }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(visibleTabs.first()) }
    if (tab !in visibleTabs) tab = visibleTabs.first()
    tabRequest?.let { if (it in visibleTabs) tab = it; tabRequest = null }
    val showLibrary = tab == "library" || visibleTabs.size == 1
    val showFeed = tab == "feed"
    val showQueue = tab == "queue"
    LaunchedEffect(tab, subs.size) { if (tab == "feed") app.feed.refresh() }
    OnChanged(tab) { query = ""; page = null }
    androidx.activity.compose.BackHandler(enabled = query.isNotEmpty() || page != null || openPlaylist != null) {
        when {
            query.isNotEmpty() -> query = ""
            openPlaylist != null -> openPlaylist = null
            else -> page = null
        }
    }
    // A new search starts at the top, where the search box is, not wherever the list was scrolled to.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    OnChanged("$tab|$query") { listState.scrollToItem(0) }
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
            state = listState,
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
                    IconButton(onClick = { searchOpen = true }) { Icon(Icons.Rounded.Search, "Search everything") }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                }
            }

            if (visibleTabs.size > 1) item("tabs") {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleTabs.forEach { id ->
                        androidx.compose.material3.FilterChip(selected = tab == id, onClick = {
                            if (tab != id) tab = id
                            else if (openPlaylist != null || page != null) { openPlaylist = null; page = null }
                            else scope.launch { listState.animateScrollToItem(0) }
                        }, label = { Text(HomeTabs.getValue(id) + when (id) { "queue" -> queue.size.takeIf { it > 0 }?.let { " · $it" }; "feed" -> subs.size.takeIf { it > 0 }?.let { " · $it" }; else -> null }.orEmpty()) })
                    }
                }
            }

            // Highlights that are ready to look at again.
            // One card for everything about highlights: browse them, or go through the ones that are due.
            val savedCount = videos.sumOf { v -> v.highlights.count { it.text.isNotBlank() || it.note.isNotBlank() } }
            if (tab == "library" && query.isBlank() && openPlaylist == null && page == null && savedCount > 0) item("highlights") {
                androidx.compose.material3.Surface(
                    onClick = nav.openHighlights, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Highlight, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Highlights", style = MaterialTheme.typography.titleSmall)
                            Text("$savedCount saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (dueCount > 0) androidx.compose.material3.FilledTonalButton(
                            onClick = nav.openReview, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        ) { Text("Review $dueCount") }
                    }
                }
            }

            item("search") {
                SearchField(
                    query, { query = it },
                    when {
                        showFeed -> "Search the feed"
                        showQueue -> "Search the queue"
                        openPlaylist != null -> "Search this playlist"
                        else -> "Search your library"
                    },
                )
            }

            if (showFeed) feedTab(
                query = query,
                subs = subs, feed = feed, busy = feedBusy, library = videos,
                filter = feedFilter, onFilter = { feedFilter = it },
                onOptions = { id -> actionsInPlaylist = false; actionsMeta = feed.items.firstOrNull { it.meta.id == id }?.meta; actionsFor = id },
                onQueue = { id -> scope.launch {
                    if (app.addToLibrary(id)) {
                        app.queue.add(id)
                        android.widget.Toast.makeText(ctx, "Added to the queue.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } },
                onOpenVideo = onOpen, onOpenChannel = nav.openChannel,
                onFind = { addInitial = null; addOpen = true },
            )
            if (showQueue) queueTab(
                queue = if (query.isBlank()) queue else queue.filter { id -> videos.firstOrNull { it.meta.id == id }?.matchesQuery(query) == true },
                videos = videos, nowId = if (query.isBlank()) nowId else null, onOpenVideo = onOpen,
                onClear = { app.queue.clear() }, onRemove = { app.queue.remove(it) },
                onMore = { actionsInPlaylist = false; actionsFor = it },
            )

            val libraryVideos = videos.filter { it.playlistId.isEmpty() }
            val inPlaylist = showLibrary && openPlaylist != null && playlists.any { it.id == openPlaylist }
            if (inPlaylist) playlistsTab(
                playlists = playlists, videos = if (query.isBlank()) videos else videos.filter { it.matchesQuery(query) }, openId = openPlaylist,
                onOpenPlaylist = { openPlaylist = it }, onOpenVideo = onOpen,
                onMore = { actionsInPlaylist = true; actionsFor = it },
            )
            val searching = query.isNotBlank()
            val browsing = showLibrary && !inPlaylist && !searching && page == null
            val resumeAll = libraryVideos.filter { it.positionSec > 5 && it.resumeSec > 0.0 }
            val pinned = libraryVideos.filter { it.pinned }
            val onMoreVideo: (String) -> Unit = { actionsInPlaylist = false; actionsFor = it }
            val rowPad = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
            if (browsing && playlists.isNotEmpty()) {
                item("pl-label") { SectionHeader("Playlists", playlists.size, always = true) { page = "playlists" } }
                item("pl-row") {
                    LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(playlists.take(12), key = { "pc" + it.id }) { p -> PlaylistCard(p, videos) { openPlaylist = p.id } }
                    }
                }
            }
            if (browsing && resumeAll.isNotEmpty()) {
                item("resume-label") { SectionHeader("Pick up where you left off", resumeAll.size) { page = "resume" } }
                item("resume") {
                    LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(resumeAll.take(12), key = { "r" + it.meta.id }) { v -> VideoCard(v, onMoreVideo, showProgress = true) { onOpen(v.meta.id) } }
                    }
                }
            }
            if (browsing && pinned.isNotEmpty()) {
                item("pinned-label") { SectionHeader("Pinned", pinned.size) { page = "pinned" } }
                item("pinned") {
                    LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(pinned.take(12), key = { "pn" + it.meta.id }) { v -> VideoCard(v, onMoreVideo, pinned = true) { onOpen(v.meta.id) } }
                    }
                }
            }
            if (showLibrary && !inPlaylist && page != null) {
                val pageTitle = when (page) { "playlists" -> "Playlists"; "resume" -> "Pick up where you left off"; else -> "Pinned" }
                val pageCount = when (page) { "playlists" -> playlists.size; "resume" -> resumeAll.size; else -> pinned.size }
                item("page-head") { BackRow(pageTitle, { page = null }, "$pageCount") }
                if (page == "playlists") {
                    item("pl-new") {
                        TextButton(onClick = { newPlaylistOpen = true }, modifier = Modifier.padding(horizontal = 10.dp)) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New playlist")
                        }
                    }
                    playlistsTab(
                        playlists = playlists.filter { matchesQuery(query, it.title) }, videos = videos, openId = null,
                        onOpenPlaylist = { openPlaylist = it }, onOpenVideo = onOpen, onMore = { actionsInPlaylist = true; actionsFor = it },
                    )
                } else {
                    val list = (if (page == "pinned") pinned else resumeAll).filter { it.matchesQuery(query) }
                    if (list.isEmpty()) item("page-none") { Text("Nothing here matches.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(list, key = { "pg" + it.meta.id }) { v -> VideoRow(v, onMoreVideo) { onOpen(v.meta.id) } }
                }
            }

            if (showLibrary && !inPlaylist && page == null) {
                item("lib-label") {
                    SectionLabel("Library", trailing = if (libraryVideos.isEmpty()) null else "${libraryVideos.size} video${if (libraryVideos.size == 1) "" else "s"}")
                }
                if (libraryVideos.isNotEmpty()) item("lib-controls") {
                    LibraryControls(
                        libraryVideos, settings.librarySort, settings.librarySortReversed,
                        // The sort that is on reverses. A new one starts the normal way round.
                        onSort = { s -> app.settings.update { it.copy(librarySort = s, librarySortReversed = s == it.librarySort && !it.librarySortReversed) } },
                        filter = filter, onFilter = { filter = it },
                    )
                }
                val everything = searching || filter.category != null || filter.tag != null
                val shown = libraryVideos.filter { (everything || !it.pinned) && filter.matches(it) && it.matchesQuery(query) }.sortedBy(settings.librarySort, settings.librarySortReversed)
                val more: (String) -> Unit = { actionsInPlaylist = false; actionsFor = it }
                if (libraryVideos.isEmpty()) {
                    item("empty") { EmptyShelf() }
                } else if (shown.isEmpty() && !searching && libraryVideos.all { it.pinned } && filter.category == null && filter.tag == null) {
                    item("all-pinned") { Text("Everything here is pinned. Tap See all above.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else if (shown.isEmpty()) {
                    item("none") { Text(if (searching) "Nothing matches \"$query\"." else "Nothing matches this filter.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else when (settings.libraryLayout) {
                    "grid" -> items(shown.chunked(2), key = { "g" + it.first().meta.id }) { pair -> VideoGridRow(pair, onOpen, more) }
                    "compact" -> items(shown, key = { "c" + it.meta.id }) { v -> VideoCompactRow(v, onOpen, more) }
                    else -> items(shown, key = { "v" + it.meta.id }) { v -> androidx.compose.foundation.layout.Box(Modifier.animateItem()) { VideoRow(v, more) { onOpen(v.meta.id) } } }
                }
            }
        }
        }

        if (newPlaylistOpen) NewPlaylistDialog(
            onDismiss = { newPlaylistOpen = false },
            onCreate = { name -> app.playlists.create(name); newPlaylistOpen = false },
        )

        if (searchOpen) LibrarySearchSheet(
            onDismiss = { searchOpen = false },
            onOpenPlaylist = { id -> query = ""; page = null; tab = "library"; openPlaylist = id },
        )

        if (addOpen) AddSheet(
            initial = addInitial,
            onDismiss = { addOpen = false; addInitial = null },
            onPlaylist = { openPlaylist = it; tabRequest = "library" },
        )

        actionsFor?.let { id -> VideoActionsSheet(id, inPlaylist = actionsInPlaylist, pending = actionsMeta, onDismiss = { actionsFor = null }) }

        StatusBarScrim()
        NowPlayingBar(Modifier.align(Alignment.BottomCenter), onOpen)
    }
}

/** The bar along the bottom while something plays. It reads the position itself, so only this bar redraws. */
@Composable
private fun NowPlayingBar(modifier: Modifier, onOpen: (String) -> Unit) {
    val app = LocalApp.current
    val player by app.player.state.collectAsState()
    val nowId = player.videoId
    AnimatedVisibility(
        visible = nowId != null,
        modifier = modifier,
        enter = expandVertically(spring(0.6f, 380f)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            onClick = { nowId?.let(onOpen) },
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
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
                    model = nowId?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" },
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
private fun VideoRow(v: SavedVideo, onMore: (String) -> Unit, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
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
                if (v.pinned) Icon(Icons.Rounded.PushPin, "Pinned", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                OfflineBadge(v.meta.id)
                if (v.clippedAt > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Clipped", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        MoreButton { onMore(v.meta.id) }
    }
}

@Composable
private fun EmptyShelf() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap Add to paste a link or search YouTube. You can also share a video here from the YouTube app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistCard(p: Playlist, videos: List<SavedVideo>, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Column(Modifier.width(168.dp).springPress(source).clickable(source, null, onClick = onClick)) {
        PlaylistThumb(p, videos, Modifier.fillMaxWidth())
        Text(p.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        Text(if (p.videoIds.isEmpty()) "Empty" else videoCount(p.videoIds.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One video as a card. Every sideways row uses it, so everything is one size. */
@Composable
private fun VideoCard(v: SavedVideo, onMore: (String) -> Unit, pinned: Boolean = false, showProgress: Boolean = false, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    Column(
        Modifier.width(168.dp).springPress(source).combinedClickable(source, null, onLongClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onMore(v.meta.id)
        }, onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = v.meta.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(18.dp)),
            )
            if (pinned) Icon(
                Icons.Rounded.PushPin, null, Modifier.align(Alignment.TopStart).padding(8.dp).size(16.dp),
                tint = androidx.compose.ui.graphics.Color.White,
            )
            if (showProgress && v.meta.durationSec > 0) {
                val frac = (v.positionSec / v.meta.durationSec).toFloat().coerceIn(0f, 1f)
                Box(
                    Modifier.align(Alignment.BottomStart).padding(10.dp).fillMaxWidth().height(4.dp)
                        .clip(CircleShape).background(androidx.compose.ui.graphics.Color(0x66000000)),
                ) { Box(Modifier.fillMaxWidth(frac).height(4.dp).background(MaterialTheme.colorScheme.primary)) }
            }
        }
        Text(v.meta.title, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        Text(v.meta.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A section title with a See all button that opens that section on its own page. */
@Composable
private fun SectionHeader(title: String, count: Int, always: Boolean = false, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // A row already shows two. See all earns its place only when there is more to see.
        if (always || count > 2) TextButton(onClick = onSeeAll) { Text("See all $count") }
        else Spacer(Modifier.height(48.dp))
    }
}
