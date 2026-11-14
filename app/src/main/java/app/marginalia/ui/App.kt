package app.marginalia.ui

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.marginalia.MarginaliaApp
import app.marginalia.data.ChannelSource
import app.marginalia.data.YouTube
import kotlinx.coroutines.launch

val LocalApp = staticCompositionLocalOf<MarginaliaApp> { error("MarginaliaApp not provided") }

/** Ways to move around that any screen can use. */
class Nav(
    val openVideo: (String) -> Unit,
    val openChannel: (String) -> Unit,
    val openChannelOfVideo: (String) -> Unit,
    val openReview: () -> Unit = {},
    val openHighlights: () -> Unit = {},
)

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav not provided") }

sealed interface Screen {
    data object Home : Screen
    data class Reader(val id: String) : Screen
    data class Channel(val url: String) : Screen
    data object Settings : Screen
    data object Review : Screen
    data object Highlights : Screen
    data class Template(val id: String) : Screen
}

@Composable
fun AppRoot(app: MarginaliaApp, incomingLink: String?, onLinkConsumed: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    MarginaliaTheme(settings) {
        CompositionLocalProvider(LocalApp provides app) {
            val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
            // Home stays underneath. A second Back in the same frame must not take it away too.
            fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
            BackHandler(enabled = stack.size > 1) { pop() }
            // Each screen keeps its place while another one is on top: open tab, scroll, search box.
            val saved = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            fun toast(text: String) = android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()

            // The reader on top is replaced by the next video, so Back still goes home.
            fun pushReader(id: String) {
                val top = stack.last()
                if (top is Screen.Reader) { if (top.id != id) stack[stack.lastIndex] = Screen.Reader(id) }
                else stack.add(Screen.Reader(id))
            }
            fun pushChannel(url: String) {
                val top = stack.last()
                if (!(top is Screen.Channel && top.url == url)) stack.add(Screen.Channel(url))
            }
            val nav = remember {
                Nav(
                    openVideo = { id ->
                        if (app.library.get(id) != null) pushReader(id)
                        else scope.launch {
                            toast("Opening…")
                            if (app.addToLibrary(id)) pushReader(id) else toast("Could not load that video.")
                        }
                    },
                    openChannel = { url -> pushChannel(url) },
                    openReview = { if (stack.last() != Screen.Review) stack.add(Screen.Review) },
                    openHighlights = { if (stack.last() != Screen.Highlights) stack.add(Screen.Highlights) },
                    openChannelOfVideo = { id ->
                        scope.launch {
                            val url = runCatching { ChannelSource.channelOfVideo(app.youtube, id) }.getOrNull()
                            if (url == null) toast("Could not find the channel.") else pushChannel(url)
                        }
                    },
                )
            }

            // A link from the YouTube app. It is handled here, not on the home screen, so it works
            // whatever screen is open. A video goes straight to the reader.
            var addLink by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            androidx.compose.runtime.LaunchedEffect(incomingLink) {
                val link = incomingLink ?: return@LaunchedEffect
                onLinkConsumed()
                val id = YouTube.extractId(link)
                when {
                    id != null -> nav.openVideo(id)
                    ChannelSource.isChannelLink(link) -> nav.openChannel(link)
                    else -> { while (stack.size > 1) stack.removeAt(stack.lastIndex); addLink = link }
                }
            }
            // A video another Obsi app asked to open.
            val openItem by app.openItem.collectAsState()
            androidx.compose.runtime.LaunchedEffect(openItem) {
                val (id, at) = openItem ?: return@LaunchedEffect
                app.openItem.value = null
                if (at >= 0) app.pendingSeek.value = id to at
                nav.openVideo(id)
            }
            CompositionLocalProvider(LocalNav provides nav) {
            // The daily reminder opens Review.
            val wantReview by app.openReview.collectAsState()
            androidx.compose.runtime.LaunchedEffect(wantReview) {
                if (wantReview) { app.openReview.value = false; nav.openReview() }
            }
            // The media notification opens the video that is playing.
            val wantPlaying by app.openNowPlaying.collectAsState()
            androidx.compose.runtime.LaunchedEffect(wantPlaying) {
                if (wantPlaying) { app.openNowPlaying.value = false; app.player.state.value.videoId?.let(nav.openVideo) }
            }
            // Auto-save found a new note with empty fields: ask for them here, over any screen.
            val askSave by app.askSave.collectAsState()
            askSave?.let { id ->
                val video = app.library.get(id)
                if (video == null) app.askSave.value = null
                else {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val lines = androidx.compose.runtime.remember(id) {
                        app.library.loadTranscript(id)?.let {
                            YouTube.toLines(it.cues, app.settings.value.lineLength, video.chapters)
                        }.orEmpty()
                    }
                    SaveSheet(
                        video = video,
                        lines = lines,
                        onDismiss = { app.askSave.value = null },
                        onDone = { message, ok ->
                            app.askSave.value = null
                            if (ok) app.library.edit(id) { it.copy(clippedAt = System.currentTimeMillis()) }
                            android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_LONG).show()
                        },
                    )
                }
            }
            // When the queue starts the next video, the open reader follows it.
            androidx.compose.runtime.LaunchedEffect(Unit) {
                app.advanced.collect { id ->
                    if (stack.last() is Screen.Reader) stack[stack.lastIndex] = Screen.Reader(id)
                }
            }
            val spring = LocalSpring.current

            // Self-healing: when a new release is found (for example a playback fix), say so right away.
            val update by app.updates.state.collectAsState()
            var later by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            (update as? app.marginalia.update.UpdateState.Available)?.info?.let { info ->
                if (later != info.version) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { later = info.version },
                        title = { androidx.compose.material3.Text("Update ready: ${info.version}") },
                        text = {
                            androidx.compose.material3.Text(
                                info.notes.trim().ifBlank { "A new version is ready." }.take(500),
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { later = info.version; app.updates.installFlow(info) }) {
                                androidx.compose.material3.Text("Install")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { later = info.version }) {
                                androidx.compose.material3.Text("Later")
                            }
                        },
                    )
                }
            }

            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AnimatedContent(
                    targetState = stack.last(),
                    // Like an iPhone: a new screen slides in over the old one from the right edge, and the old one
                    // drifts a little to the left under it. Back is the same in reverse. Nothing scales and nothing
                    // fades out from under you, so the page you are looking at stays solid the whole way.
                    transitionSpec = {
                        val deeper = depth(targetState) >= depth(initialState)
                        val move = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(380, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f))
                        if (deeper) {
                            (slideInHorizontally(move) { it } togetherWith slideOutHorizontally(move) { -it / 4 })
                                .apply { targetContentZIndex = 1f }
                        } else {
                            (slideInHorizontally(move) { -it / 4 } togetherWith slideOutHorizontally(move) { it })
                                .apply { targetContentZIndex = -1f }
                        }
                    },
                    label = "screens",
                ) { screen ->
                    // Each screen is solid, so the one underneath never shows through while they slide.
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    saved.SaveableStateProvider(screen.toString()) {
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            incoming = addLink,
                            onConsumed = { addLink = null },
                            onOpen = nav.openVideo,
                            onSettings = { stack.add(Screen.Settings) },
                        )
                        // The reader also goes back by itself when its video is removed, so it checks that it is on top.
                        is Screen.Reader -> ReaderScreen(screen.id, onBack = { if (stack.last() == screen) pop() })
                        is Screen.Channel -> ChannelScreen(screen.url, onBack = { pop() })
                        Screen.Settings -> SettingsScreen(
                            onBack = { pop() },
                            onEditTemplate = { stack.add(Screen.Template(it)) },
                        )
                        is Screen.Template -> TemplateEditorScreen(screen.id, onBack = { pop() })
                        Screen.Review -> ReviewScreen(onBack = { pop() })
                        Screen.Highlights -> HighlightsScreen(onBack = { pop() })
                    }
                    }
                    }
                }
            }
            }
        }
    }
}

private fun depth(s: Screen) = when (s) {
    Screen.Home -> 0
    is Screen.Reader -> 1
    is Screen.Channel -> 1
    Screen.Settings -> 1
    Screen.Review -> 1
    Screen.Highlights -> 1
    is Screen.Template -> 2
}
