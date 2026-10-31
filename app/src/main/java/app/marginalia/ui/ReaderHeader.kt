package app.marginalia.ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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

/** True while a source is loading or the player is catching up, and there is no error to show instead. */
private val PlayerState.waiting get() = error == null && (loading || (buffering && !playing))

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
    pinned: Boolean,
    onPin: () -> Unit,
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
        val fullHeight = min(width * 9f / 16f, screen * 0.4f)
        val videoHeight by animateDpAsState(if (picture) fullHeight else 0.dp, spring(1f, 450f), label = "picture")
        val openTop = 0.dp
        // The 3dp under the picture is the thin progress line, so you can see how much is left with the buttons hidden.
        val openTarget = if (picture) fullHeight + 3.dp else TopRow + Controls
        val open by animateDpAsState(openTarget, spring(1f, 450f), label = "open")
        val tap = remember { MutableInteractionSource() }

        Box(Modifier.fillMaxWidth().height(lerp(open, SmallRow, t))) {
            val rowAlpha by animateFloatAsState(if (!picture) 1f else t, tween(220), label = "rowAlpha")
            if (rowAlpha > 0f) Row(
                Modifier.fillMaxWidth().height(lerp(TopRow, SmallRow, t)).graphicsLayer { alpha = rowAlpha }.padding(horizontal = 4.dp),
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
                if (picture || videoHeight > 1.dp) AndroidView(
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
                if (picture && t < 0.5f) {
                    val on = shown || !playing
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - t * 2f }) {
                        AnimatedVisibility(visible = on, enter = fadeIn(tween(200)), exit = fadeOut(tween(300))) {
                            Box(Modifier.fillMaxSize().background(Color(0x55000000)).clickable(remember { MutableInteractionSource() }, null, onClickLabel = "Hide the buttons") { shown = false })
                        }
                        // Always there, so the menu never leaves while you use it.
                        PictureTopButtons(ps, host, onBack, onFind, pinned, onPin, menuOpen, onMenu, menuItems, onPoke = { pokes++ })
                        AnimatedVisibility(visible = on, enter = fadeIn(tween(200)), exit = fadeOut(tween(300)), modifier = Modifier.fillMaxSize()) {
                            PictureCentre(ps, host, onPoke = { pokes++ })
                        }
                    }
                }
            }

            val ctlAlpha by animateFloatAsState(if (picture) 0f else 1f, tween(220), label = "ctlAlpha")
            if (ctlAlpha > 0f && t < 1f) Column(
                Modifier.offset(y = TopRow).height(Controls).graphicsLayer { alpha = (ctlAlpha * (1f - t * 2f)).coerceAtLeast(0f) }.padding(horizontal = 12.dp),
            ) { PlayerControls(ps, host) }
        }
        // One bar at a time: while the buttons are on the picture they have the seek bar, so this line waits.
        if ((picture && !(shown || !playing)) || t > 0f) SmallProgress(ps, Modifier.align(Alignment.BottomStart), if (collapsed) 2.dp else 3.dp)
    }
}

/** Back and the small choices, along the top of the picture. They stay while the rest hides. */
@Composable
private fun PictureTopButtons(
    ps: State<PlayerState>,
    host: PlayerHost,
    onBack: () -> Unit,
    onFind: () -> Unit,
    pinned: Boolean,
    onPin: () -> Unit,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    menuItems: @Composable ColumnScope.() -> Unit,
    onPoke: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayButton("Back", onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(22.dp), tint = Color.White) }
        Spacer(Modifier.weight(1f))
        OverlayButton("Speed", { host.setSpeed(nextSpeed(ps.value.speed)); onPoke() }) {
            Text(speedLabel(ps.value.speed), style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        OverlayButton(if (pinned) "Let the video fold when you scroll" else "Keep the video big when you scroll", onPin) {
            Icon(if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, null, Modifier.size(22.dp), tint = if (pinned) MaterialTheme.colorScheme.primary else Color.White)
        }
        if (ps.value.canWatch) OverlayButton("Hide the video", { host.setVideo(false) }) {
            Icon(Icons.Rounded.Headphones, null, Modifier.size(22.dp), tint = Color.White)
        }
        OverlayButton("Find in this video", { onFind() }) { Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = Color.White) }
        Box {
            OverlayButton("More", { onMenu(true) }) { Icon(Icons.Rounded.MoreVert, null, Modifier.size(22.dp), tint = Color.White) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }, content = menuItems)
        }
    }
}

/** Play, skip and the seek bar. They fade out by themselves while the video plays. */
@Composable
private fun PictureCentre(ps: State<PlayerState>, host: PlayerHost, onPoke: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
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
            SlimSeekBar(
                ps.value.positionSec, ps.value.durationSec,
                onSeek = { host.seekTo(it); onPoke() }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                loading = ps.value.waiting, onLight = true,
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
private fun PlayerControls(ps: State<PlayerState>, host: PlayerHost) {
    val scheme = MaterialTheme.colorScheme
    var showLeft by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(YouTube.formatTime(ps.value.positionSec), Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        SlimSeekBar(
            ps.value.positionSec, ps.value.durationSec, onSeek = { host.seekTo(it) },
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp), loading = ps.value.waiting,
        )
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
}

@Composable
private fun SmallTime(ps: State<PlayerState>, modifier: Modifier) {
    Text(
        "${YouTube.formatTime(ps.value.positionSec)} / ${YouTube.formatTime(ps.value.durationSec)}", modifier,
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A thin line along the bottom edge of the player: how far along the video is. */
@Composable
private fun SmallProgress(ps: State<PlayerState>, modifier: Modifier, thick: androidx.compose.ui.unit.Dp = 2.dp) {
    val s = ps.value
    val frac = if (s.durationSec > 0) (s.positionSec / s.durationSec).toFloat().coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth().height(thick).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
        Box(Modifier.fillMaxWidth(frac).height(thick).background(MaterialTheme.colorScheme.primary))
    }
}

/** One slim seek bar, the same line that shows under the picture. Tap or drag to move. */
@Composable
fun SlimSeekBar(
    positionSec: Double,
    durationSec: Double,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onLight: Boolean = false,
) {
    var width by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val frac = if (durationSec > 0) (positionSec / durationSec).toFloat().coerceIn(0f, 1f) else 0f
    val shown = dragging ?: frac
    val scheme = MaterialTheme.colorScheme
    val pulse by rememberInfiniteTransition(label = "wait").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "waitAlpha",
    )
    val track = if (onLight) Color.White.copy(alpha = 0.3f) else scheme.outlineVariant.copy(alpha = 0.6f)
    Box(
        modifier.fillMaxWidth().height(32.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(durationSec) { detectTapGestures { o -> if (durationSec > 0) onSeek(o.x / width * durationSec) } }
            .pointerInput(durationSec) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = (o.x / width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging?.let { if (durationSec > 0) onSeek(it * durationSec) }; dragging = null },
                    onDragCancel = { dragging = null },
                    onHorizontalDrag = { change, _ -> dragging = (change.position.x / width).coerceIn(0f, 1f) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(track)) {
            Box(Modifier.fillMaxWidth(shown).height(3.dp).graphicsLayer { alpha = if (loading) pulse else 1f }.background(scheme.primary))
        }
        if (dragging != null) Box(Modifier.offset { androidx.compose.ui.unit.IntOffset((shown * width).toInt() - 6.dp.roundToPx(), 0) }.size(12.dp).clip(CircleShape).background(scheme.primary))
    }
}
