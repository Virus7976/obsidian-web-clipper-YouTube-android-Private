package app.marginalia.ui

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
            CompositionLocalProvider(LocalNav provides nav) {
            // The daily reminder opens Review.
            val wantReview by app.openReview.collectAsState()
            androidx.compose.runtime.LaunchedEffect(wantReview) {
                if (wantReview) { app.openReview.value = false; nav.openReview() }
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
                    transitionSpec = {
                        val deeper = depth(targetState) >= depth(initialState)
                        if (deeper) {
                            (slideInHorizontally(spring(spring.damping, spring.stiffness)) { it / 5 } + fadeIn(spring(1f, 500f))) togetherWith
                                (fadeOut(spring(1f, 700f)))
                        } else {
                            (fadeIn(spring(1f, 500f)) + scaleIn(spring(spring.damping, spring.stiffness), initialScale = 0.94f)) togetherWith
                                (slideOutHorizontally(spring(spring.damping, spring.stiffness)) { it / 5 } + fadeOut(spring(1f, 700f)))
                        }
                    },
                    label = "screens",
                ) { screen ->
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

private fun depth(s: Screen) = when (s) {
    Screen.Home -> 0
    is Screen.Reader -> 1
    is Screen.Channel -> 1
    Screen.Settings -> 1
    Screen.Review -> 1
    Screen.Highlights -> 1
    is Screen.Template -> 2
}
