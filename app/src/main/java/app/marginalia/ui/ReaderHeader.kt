package app.marginalia.ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.marginalia.data.SavedVideo
import app.marginalia.data.YouTube
import app.marginalia.player.PlayerHost
import app.marginalia.player.PlayerState
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private val TopRow = 56.dp
private val SmallRow = 64.dp
private val Controls = 96.dp
private val Speeds = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.75f)

private fun nextSpeed(now: Float) = Speeds[(Speeds.indexOf(now).let { if (it < 0) 0 else it } + 1) % Speeds.size]
private fun speedLabel(s: Float) = "${s}x".replace(".0x", "x")

/**
 * The top of the reader, in three shapes.
 *  - Watching: only the picture. Its buttons sit on it, appear when you tap it, and leave by themselves.
 *  - Listening: a slim top row and the controls.
 *  - Reading: one row with a small picture, the title, the time and play.
 * The same video view moves between them, so playback never restarts.
 */
@OptIn(UnstableApi::class)
@Composable
fun ReaderHeader(
    video: SavedVideo,
    ps: State<PlayerState>,
    host: PlayerHost,
    collapsed: Boolean,
    onExpand: () -> Unit,
    onBack: () -> Unit,
    searching: Boolean,
    onFind: () -> Unit,
    showSync: Boolean,
    onSync: () -> Unit,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    menuItems: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val picture by remember { derivedStateOf { ps.value.videoOn } }
    val playing by remember { derivedStateOf { ps.value.playing } }
    val waiting by remember { derivedStateOf { ps.value.error == null && (ps.value.loading || (ps.value.buffering && !ps.value.playing)) } }
    val t by animateFloatAsState(if (collapsed) 1f else 0f, spring(1f, 500f), label = "fold")

    // Over the picture the buttons come and go: a tap shows them, and they leave after a few seconds.
    // A paused video keeps them, because there is nothing to watch.
    var shown by remember { mutableStateOf(true) }
    var pokes by remember { mutableIntStateOf(0) }
    LaunchedEffect(collapsed) { if (!collapsed) shown = true }
    LaunchedEffect(shown, pokes, playing) { if (shown && playing) { delay(3500); shown = false } }

    BoxWithConstraints(Modifier.fillMaxWidth().background(scheme.background).clipToBounds()) {
        val width = maxWidth
        val screen = LocalConfiguration.current.screenHeightDp.dp
        // In landscape a full width picture would fill the screen, so it stops at 40 percent.
        val videoHeight = if (picture) min(width * 9f / 16f, screen * 0.4f) else 0.dp
        val openTop = if (picture) 0.dp else TopRow
        val open = if (picture) videoHeight else TopRow + Controls
        val tap = remember { MutableInteractionSource() }

        Box(Modifier.fillMaxWidth().height(lerp(open, SmallRow, t))) {
            if (!picture || t > 0f) Row(
                Modifier.fillMaxWidth().height(lerp(TopRow, SmallRow, t)).graphicsLayer { alpha = if (picture) t else 1f }.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.width(lerp(0.dp, 84.dp, t)))
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(end = 8.dp).clickable(tap, null, enabled = collapsed, onClickLabel = "Show the player", onClick = onExpand),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(video.meta.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t > 0f) SmallTime(ps, Modifier.graphicsLayer { alpha = t })
                }
                if (showSync) IconButton(onClick = onSync) { Icon(Icons.Rounded.MyLocation, "Sync to where it is", tint = scheme.primary) }
                if (collapsed) MorphingPlayButton(playing = ps.value.playing, onClick = { host.toggle() }, size = 40.dp)
                else IconButton(onClick = onFind) { Icon(Icons.Rounded.Search, "Find in this video", tint = if (searching) scheme.primary else scheme.onSurface) }
                Box {
                    IconButton(onClick = { onMenu(true) }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }, content = menuItems)
                }
            }

            // Open, it fills the width at the top. Folded, it sits after the back arrow.
            Box(
                Modifier.offset(lerp(0.dp, 56.dp, t), lerp(openTop, 11.dp, t))
                    .size(lerp(width, 74.dp, t), lerp(videoHeight, 42.dp, t))
                    .background(Color.Black).clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                if (picture) AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        PlayerView(c).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = host.exo
                        }
                    },
                    onRelease = { it.player = null },
                ) else if (t > 0f) AsyncImage(video.meta.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (picture && waiting) LoadingIndicator()
                Box(
                    Modifier.fillMaxSize().clickable(
                        remember { MutableInteractionSource() }, null,
                        onClickLabel = if (collapsed) "Show the player" else "Show the buttons",
                    ) { if (collapsed) onExpand() else shown = !shown },
                )
                if (picture && t < 0.5f) AnimatedVisibility(
                    visible = shown || !playing, enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.graphicsLayer { alpha = 1f - t * 2f },
                ) {
                    PictureButtons(
                        ps, host, onBack, onFind, menuOpen, onMenu, menuItems,
                        onHide = { shown = false }, onPoke = { pokes++ },
                    )
                }
            }

            if (!picture && t < 1f) Column(
                Modifier.offset(y = TopRow).height(Controls).graphicsLayer { alpha = (1f - t * 2f).coerceAtLeast(0f) }.padding(horizontal = 12.dp),
            ) { PlayerControls(ps, host, showBar = waiting) }
        }
        if (t > 0f) SmallProgress(ps, Modifier.align(Alignment.BottomStart).graphicsLayer { alpha = t })
    }
}

/** The buttons that sit on the picture: back and the small choices on top, play and skip in the middle, the seek bar below. */
@Composable
private fun PictureButtons(
    ps: State<PlayerState>,
    host: PlayerHost,
    onBack: () -> Unit,
    onFind: () -> Unit,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    menuItems: @Composable ColumnScope.() -> Unit,
    onHide: () -> Unit,
    onPoke: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000))
            .clickable(remember { MutableInteractionSource() }, null, onClickLabel = "Hide the buttons") { onHide() },
    ) {
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayButton("Back", onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(22.dp), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            OverlayButton("Speed", { host.setSpeed(nextSpeed(ps.value.speed)); onPoke() }) {
                Text(speedLabel(ps.value.speed), style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            if (ps.value.canWatch) OverlayButton("Hide the video", { host.setVideo(false) }) {
                Icon(Icons.Rounded.Headphones, null, Modifier.size(22.dp), tint = Color.White)
            }
            OverlayButton("Find in this video", { onFind(); onHide() }) { Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = Color.White) }
            Box {
                OverlayButton("More", { onMenu(true) }) { Icon(Icons.Rounded.MoreVert, null, Modifier.size(22.dp), tint = Color.White) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }, content = menuItems)
            }
        }
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { host.skip(-10.0); onPoke() }) { Icon(Icons.Rounded.Replay10, "Back 10 seconds", Modifier.size(32.dp), tint = Color.White) }
            MorphingPlayButton(playing = ps.value.playing, onClick = { host.toggle(); onPoke() }, size = 56.dp)
            IconButton(onClick = { host.skip(10.0); onPoke() }) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds", Modifier.size(32.dp), tint = Color.White) }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(YouTube.formatTime(ps.value.positionSec), style = MaterialTheme.typography.labelMedium, color = Color.White)
            WavySeekBar(
                ps.value.positionSec, ps.value.durationSec, ps.value.playing,
                onSeek = { host.seekTo(it); onPoke() }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(YouTube.formatTime(ps.value.durationSec), style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

/** A round button on the picture. The dark disc keeps it readable on any frame. */
@Composable
private fun OverlayButton(description: String, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center, content = content,
    )
}

/** The controls under a slim top row, for listening: the seek bar with its times, then the buttons, centred. */
@Composable
private fun PlayerControls(ps: State<PlayerState>, host: PlayerHost, showBar: Boolean) {
    val scheme = MaterialTheme.colorScheme
    var showLeft by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(YouTube.formatTime(ps.value.positionSec), Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        WavySeekBar(ps.value.positionSec, ps.value.durationSec, ps.value.playing, onSeek = { host.seekTo(it) }, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
        // Tap the time to see what is left.
        Text(
            if (showLeft) "-" + YouTube.formatTime((ps.value.durationSec - ps.value.positionSec).coerceAtLeast(0.0)) else YouTube.formatTime(ps.value.durationSec),
            Modifier.clickable(onClickLabel = "Switch between length and time left") { showLeft = !showLeft }.padding(horizontal = 4.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { host.setSpeed(nextSpeed(ps.value.speed)) }, modifier = Modifier.width(64.dp)) { Text(speedLabel(ps.value.speed)) }
        IconButton(onClick = { host.skip(-10.0) }) { Icon(Icons.Rounded.Replay10, "Back 10 seconds") }
        MorphingPlayButton(playing = ps.value.playing, onClick = { host.toggle() }, size = 48.dp)
        IconButton(onClick = { host.skip(10.0) }) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds") }
        Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            if (ps.value.canWatch && ps.value.error == null) IconButton(onClick = { host.setVideo(true) }) {
                Icon(Icons.Rounded.Videocam, "Show the video", tint = scheme.onSurfaceVariant)
            }
        }
    }
    if (showBar) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 4.dp))
}

@Composable
private fun SmallTime(ps: State<PlayerState>, modifier: Modifier) {
    Text(
        "${YouTube.formatTime(ps.value.positionSec)} / ${YouTube.formatTime(ps.value.durationSec)}", modifier,
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A thin line along the bottom of the folded row: how far along the video is. */
@Composable
private fun SmallProgress(ps: State<PlayerState>, modifier: Modifier) {
    val s = ps.value
    val frac = if (s.durationSec > 0) (s.positionSec / s.durationSec).toFloat().coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))) {
        Box(Modifier.fillMaxWidth(frac).height(2.dp).background(MaterialTheme.colorScheme.primary))
    }
}
