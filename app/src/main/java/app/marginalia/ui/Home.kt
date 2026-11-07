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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.BorderColor
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
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    // Every way of marking a video lands here, so each one can be undone the same way.
    fun toggleDone(id: String) {
        val was = videos.firstOrNull { it.meta.id == id }?.done ?: return
        app.setDone(id, !was)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(if (was) "Marked as not done" else "Marked as done", actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Short)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) app.setDone(id, was)
        }
    }

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
        // Changing tab: the new page fades in and slides a little from the side of the tab you came from.
        val tabIndex = visibleTabs.indexOf(tab).coerceAtLeast(0)
        // The new page starts hidden in the very frame the tab changes, so the old value never flashes it in.
        val lastTab = remember { intArrayOf(tabIndex) }
        val tabWay = remember(tabIndex) { if (tabIndex >= lastTab[0]) 1 else -1 }
        val tabIn = remember(tabIndex) { androidx.compose.animation.core.Animatable(if (tabIndex == lastTab[0]) 1f else 0f) }
        LaunchedEffect(tabIndex) {
            lastTab[0] = tabIndex
            tabIn.animateTo(1f, androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f)))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = tabIn.value
                translationX = (1f - tabIn.value) * tabWay * 28.dp.toPx()
            },
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + if (visibleTabs.size > 1) 160.dp else 100.dp,
            ),
        ) {
            // The page's name, large, like a title in Apple's apps. The tabs that change it are at the bottom.
            item("head") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (visibleTabs.size > 1) HomeTabs.getValue(tab.takeIf { it in visibleTabs } ?: visibleTabs.first()) else "ObsiTube",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        maxLines = 1,
                    )
                    // Small round buttons, like Apple's. Search sits by the tabs when there are tabs.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(end = 12.dp)) {
                        RoundButton(Icons.Rounded.Add, "Add a video") { addInitial = null; addOpen = true }
                        if (visibleTabs.size <= 1) RoundButton(Icons.Rounded.Search, "Search everything") { searchOpen = true }
                        RoundButton(Icons.Rounded.Settings, "Settings", onSettings)
                    }
                }
            }

            val libraryVideos = videos.filter { it.playlistId.isEmpty() }
            val inPlaylist = showLibrary && openPlaylist != null && playlists.any { it.id == openPlaylist }
            val searching = query.isNotBlank()
            val browsing = showLibrary && !inPlaylist && !searching && page == null
            // What is playing now is in the bar at the bottom already.
            val resumeAll = libraryVideos.filter { !it.done && it.positionSec > 5 && it.resumeSec > 0.0 && it.meta.id != nowId }.sortedByDescending { maxOf(it.playedAt, it.updatedAt) }
            val pinned = libraryVideos.filter { it.pinned }
            val onMoreVideo: (String) -> Unit = { actionsInPlaylist = false; actionsFor = it }
            val rowPad = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)

            // First the thing you most likely came back for. One list, one shape: three rows you can scan, and the rest behind See all.
            if (browsing && resumeAll.isNotEmpty()) {
                item("resume-label") { SectionHeader("Pick up where you left off", resumeAll.size, limit = 3) { page = "resume" } }
                items(resumeAll.take(3), key = { "r" + it.meta.id }) { v ->
                    androidx.compose.foundation.layout.Box(Modifier.animateItem()) { ContinueRow(v, onMoreVideo, ::toggleDone) { onOpen(v.meta.id) } }
                }
            }

            // One card for everything about highlights: browse them, or go through the ones that are due.
            val savedCount = videos.sumOf { v -> v.highlights.count { it.text.isNotBlank() || it.note.isNotBlank() } }
            if (browsing && savedCount > 0) item("highlights") {
                androidx.compose.material3.Surface(
                    onClick = nav.openHighlights, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 4.dp),
                ) {
                    Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BorderColor, null, tint = MaterialTheme.colorScheme.tertiary)
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

            // The search icon covers everything you saved. A box here is only for a long list of this one kind.
            val listSize = when {
                showFeed -> feed.items.size
                showQueue -> queue.size
                inPlaylist -> playlists.firstOrNull { it.id == openPlaylist }?.videoIds?.size ?: 0
                page == "playlists" -> playlists.size
                page == "resume" -> resumeAll.size
                page == "pinned" -> pinned.size
                else -> 0
            }
            if (listSize >= 8 || query.isNotEmpty()) item("search") {
                SearchField(
                    query, { query = it },
                    when {
                        showFeed -> "Search the feed"
                        showQueue -> "Search the queue"
                        openPlaylist != null -> "Search this playlist"
                        else -> "Search this list"
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

            if (inPlaylist) playlistsTab(
                playlists = playlists, videos = if (query.isBlank()) videos else videos.filter { it.matchesQuery(query) }, openId = openPlaylist,
                onOpenPlaylist = { openPlaylist = it }, onOpenVideo = onOpen,
                onMore = { actionsInPlaylist = true; actionsFor = it },
            )
            // The card above already shows this video, so it is not repeated here.
            val pinnedRow = pinned.filter { it.meta.id != resumeAll.firstOrNull()?.meta?.id }
            if (browsing && pinnedRow.isNotEmpty()) {
                item("pinned-label") { SectionHeader("Pinned", pinned.size) { page = "pinned" } }
                // One video in a sideways row leaves half the screen empty, so it gets a whole line.
                if (pinnedRow.size == 1) item("pinned") { VideoRow(pinnedRow.first(), onMoreVideo) { onOpen(pinnedRow.first().meta.id) } }
                else item("pinned") {
                    LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(pinnedRow.take(12), key = { "pn" + it.meta.id }) { v -> VideoCard(v, onMoreVideo, pinned = true) { onOpen(v.meta.id) } }
                    }
                }
            }
            if (browsing && playlists.isNotEmpty()) {
                item("pl-label") { SectionHeader("Playlists", playlists.size, always = true) { page = "playlists" } }
                item("pl-row") {
                    LazyRow(contentPadding = rowPad, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(playlists.take(12), key = { "pc" + it.id }) { p -> PlaylistCard(p, videos) { openPlaylist = p.id } }
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
                    SectionLabel("Videos", trailing = if (libraryVideos.isEmpty()) null else "${libraryVideos.size} video${if (libraryVideos.size == 1) "" else "s"}")
                }
                if (libraryVideos.isNotEmpty()) item("lib-controls") {
                    LibraryControls(
                        libraryVideos, settings.librarySort, settings.librarySortReversed,
                        // The sort that is on reverses. A new one starts the normal way round.
                        onSort = { s -> app.settings.update { it.copy(librarySort = s, librarySortReversed = s == it.librarySort && !it.librarySortReversed) } },
                        filter = filter, onFilter = { filter = it },
                        hideDone = settings.hideDone, onHideDone = { on -> app.settings.update { it.copy(hideDone = on) } },
                    )
                }
                val everything = searching || filter.category != null || filter.tag != null
                val shown = libraryVideos.filter { (everything || !it.pinned) && (!settings.hideDone || !it.done) && filter.matches(it) && it.matchesQuery(query) }.sortedBy(settings.librarySort, settings.librarySortReversed)
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
                    else -> items(shown, key = { "v" + it.meta.id }) { v -> androidx.compose.foundation.layout.Box(Modifier.animateItem()) { SwipeDone(v.done, { toggleDone(v.meta.id) }) { VideoRow(v, more) { onOpen(v.meta.id) } } } }
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
        // The mini player rests on the tab bar, like Apple Music: what plays, then where you can go.
        val tabbed = visibleTabs.size > 1
        Column(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(bottom = 8.dp)) {
            NowPlayingBar(Modifier, onOpen)
            if (tabbed) Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    BottomTabs(visibleTabs, tab.takeIf { it in visibleTabs } ?: visibleTabs.first(), queue.size) { id ->
                        if (tab != id) tab = id
                        else if (openPlaylist != null || page != null) { openPlaylist = null; page = null }
                        else scope.launch { listState.animateScrollToItem(0) }
                    }
                }
                // Search is its own circle beside the tabs, as in Apple Music.
                Surface(
                    onClick = { searchOpen = true }, modifier = Modifier.size(60.dp),
                    shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 8.dp,
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, "Search everything", Modifier.size(26.dp)) } }
            }
        }
        androidx.compose.material3.SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars)
                .padding(bottom = (if (nowId != null) 64.dp else 8.dp) + (if (tabbed) 72.dp else 4.dp)),
        )
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
        // A slim capsule resting on the tabs, like Apple Music's: picture, title, play, close.
        Surface(
            onClick = { nowId?.let(onOpen) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            Row(Modifier.padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // A video is a wide picture. A circle would cut most of it away.
                AsyncImage(
                    model = nowId?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.height(40.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(20.dp)),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(player.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    Text(player.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { app.player.toggle() }) {
                    Icon(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.playing) "Pause" else "Play", Modifier.size(28.dp))
                }
                IconButton(onClick = { app.player.stop() }) { Icon(Icons.Rounded.Close, "Stop", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** A small round button at the top of a page. */
@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(40.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(22.dp)) }
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
                        leadingIcon = { Icon(Icons.Rounded.BorderColor, null, Modifier.size(18.dp)) },
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
            alpha = if (v.done) 0.5f else 1f,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(v.meta.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(v.meta.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (v.highlights.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.BorderColor, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text("${v.highlights.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                }
                if (v.done) DoneBadge()
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
            "Tap + to paste a link or search YouTube. You can also share a video here from the YouTube app.",
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


/**
 * The tabs, as a floating bar at the bottom, the way Apple's own apps do it: an icon with a small name under
 * it, and a soft shape behind the chosen one that slides when you change tabs. The thumb reaches it, and the
 * top of the page is left for the page's name.
 */
@Composable
private fun BottomTabs(ids: List<String>, selected: String, queueCount: Int, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bounds = remember(ids) { androidx.compose.runtime.mutableStateListOf(*Array(ids.size) { 0f to 0f }) }
    val index = ids.indexOf(selected).coerceAtLeast(0)
    val (x0, w0) = bounds.getOrElse(index) { 0f to 0f }
    val x by androidx.compose.animation.core.animateFloatAsState(x0, androidx.compose.animation.core.spring(0.75f, 450f), label = "tabX")
    val w by androidx.compose.animation.core.animateFloatAsState(w0, androidx.compose.animation.core.spring(0.75f, 450f), label = "tabW")
    Surface(
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(30.dp),
        color = scheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Box(Modifier.padding(5.dp)) {
            if (w > 0f) Box(
                Modifier.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), 0) }
                    .width(with(density) { w.toDp() }).fillMaxHeight()
                    .clip(RoundedCornerShape(25.dp)).background(scheme.surfaceContainerHighest),
            )
            Row(Modifier.fillMaxSize()) {
                ids.forEachIndexed { i, id ->
                    val on = id == selected
                    val tone by androidx.compose.animation.animateColorAsState(if (on) scheme.primary else scheme.onSurfaceVariant, label = "tabTone")
                    val label = HomeTabs.getValue(id)
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .onGloballyPositioned { c -> bounds[i] = c.positionInParent().x to c.size.width.toFloat() }
                            .clip(RoundedCornerShape(25.dp))
                            .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClickLabel = label) { onSelect(id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box {
                            Icon(tabIcon(id), null, Modifier.size(24.dp), tint = tone)
                            if (id == "queue" && queueCount > 0) Text(
                                queueCount.toString(),
                                Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape).background(scheme.primary)
                                    .padding(horizontal = 5.dp),
                                style = MaterialTheme.typography.labelSmall, color = scheme.onPrimary,
                            )
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = tone, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun tabIcon(id: String) = when (id) {
    "feed" -> Icons.Rounded.Subscriptions
    "queue" -> Icons.Rounded.QueueMusic
    else -> Icons.Rounded.VideoLibrary
}

/** A video you started: how far along it is, how much is left, and a button to call it finished. */
@Composable
private fun ContinueRow(v: SavedVideo, onMore: (String) -> Unit, onDone: (String) -> Unit, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val total = v.meta.durationSec.toDouble()
    val frac = if (total > 0) (v.positionSec / total).toFloat().coerceIn(0f, 1f) else 0f
    val minutes = kotlin.math.ceil((total - v.positionSec).coerceAtLeast(0.0) / 60).toInt()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).springPress(source, 0.98f)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(source, null, onLongClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onMore(v.meta.id)
            }, onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(116.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(16.dp))) {
            AsyncImage(model = v.meta.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // The line sits on the picture, like YouTube, so the row stays one clean shape.
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(androidx.compose.ui.graphics.Color(0x66FFFFFF))) {
                Box(Modifier.fillMaxWidth(frac).height(3.dp).background(scheme.primary))
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(v.meta.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(if (minutes <= 1) "Almost done" else "$minutes min left", v.meta.author).filter { it.isNotBlank() }.joinToString("  ·  "),
                Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
        }
        // A tick in a circle, the same as a to-do list. Tapping the row plays.
        IconButton(onClick = { onDone(v.meta.id) }) { Icon(Icons.Outlined.CheckCircle, "Mark as done", tint = scheme.onSurfaceVariant) }
    }
}

/** Swipe a row to the right to mark it done, or not done. The row comes back to its place. */
@Composable
private fun SwipeDone(done: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = false,
        onDismiss = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onToggle()
            scope.launch { state.snapTo(androidx.compose.material3.SwipeToDismissBoxValue.Settled) }
        },
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(26.dp)).background(scheme.surfaceContainerHigh).padding(start = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Check, null, tint = scheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(if (done) "Not done" else "Done", style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() } }
}

/** A section title with a See all button that opens that section on its own page. */
@Composable
private fun SectionHeader(title: String, count: Int, always: Boolean = false, limit: Int = 2, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // A row already shows two. See all earns its place only when there is more to see.
        if (always || count > limit) TextButton(onClick = onSeeAll) { Text("See all $count", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else Spacer(Modifier.height(48.dp))
    }
}
