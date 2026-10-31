package app.marginalia.ui

import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.withTimeout
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import app.marginalia.MarginaliaApp
import app.marginalia.R
import app.marginalia.data.Highlights
import app.marginalia.data.TranscriptDoc
import app.marginalia.data.YouTube
import app.marginalia.player.QuickHighlight
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun argb(c: Color, alpha: Float = c.alpha): Int =
    android.graphics.Color.argb((alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

/**
 * The player shown over the keyguard. Every part follows Settings > Lock screen:
 * the layout (page, cover or minimal), the background, text size, clock, accent colour and the controls.
 * The default is pure black, so an OLED screen lights almost nothing, with the transcript as a page you can highlight.
 */
@Composable
fun LockScreen(app: MarginaliaApp, onUnlock: () -> Unit, onClose: () -> Unit) {
    val s by app.settings.state.collectAsState()
    val ps by app.player.state.collectAsState()
    val locked by app.player.locked.collectAsState()
    val videos by app.library.videos.collectAsState()
    val ctx = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val accent = if (s.lockAccent == "white") Color.White else MaterialTheme.colorScheme.primary
    val id = ps.videoId
    val video = videos.firstOrNull { it.meta.id == id }
    // The layout can be flipped from the lock screen itself without changing the setting.
    var layout by remember(s.lockStyle) { mutableStateOf(s.lockStyle) }
    val showText = layout != "minimal"

    // Nothing loaded: there is nothing to show.
    LaunchedEffect(id) { if (id == null) onClose() }
    DisposableEffect(s.lockKeepOn) {
        view.keepScreenOn = s.lockKeepOn
        onDispose { view.keepScreenOn = false }
    }

    val doc by produceState<TranscriptDoc?>(null, id, showText, s.lineLength, s.showTimestamps, video?.chapters) {
        value = if (!showText) null else withContext(Dispatchers.Default) {
            id?.let { app.library.loadTranscript(it) }?.let { t ->
                TranscriptDoc.build(t.cues, video?.chapters.orEmpty(), s.lineLength, s.showTimestamps)
            }
        }
    }
    val colors = remember(doc, video?.highlights) { doc?.let { d -> Highlights.colorByWord(d.words, video?.highlights.orEmpty()) } }
    val activeWord by remember(doc) { derivedStateOf { doc?.wordAtTime(ps.positionSec) ?: -1 } }

    val onBlack = s.lockBackground != "accent"
    val textColor = if (onBlack) Color(0xFFE6E6E6) else MaterialTheme.colorScheme.onPrimaryContainer
    val muted = textColor.copy(alpha = 0.6f)
    val style = ReaderStyle(
        textColor = argb(textColor),
        mutedColor = argb(muted),
        accent = argb(accent),
        sizeSp = s.lockTextSize,
        lineMult = s.lineHeight,
        typeface = if (s.readerFont == "system") Typeface.DEFAULT else ResourcesCompat.getFont(ctx, R.font.newsreader),
        highlightAlpha = 0.42f,
        holdSelects = s.holdSelects,
        maxWidthPx = with(density) { (s.maxWidth * s.lockTextSize).dp.roundToPx() },
        highlighterOn = true,
    )

    var controls by remember { mutableStateOf(!s.lockHideControls) }
    var tview by remember { mutableStateOf<TranscriptView?>(null) }
    var away by remember { mutableStateOf(false) }
    LaunchedEffect(activeWord, s.lockFollow) { if (s.lockFollow && activeWord >= 0) tview?.follow(activeWord) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // background
        when (s.lockBackground) {
            "art" -> {
                AsyncImage(id?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }, null, Modifier.fillMaxSize().blur(36.dp), contentScale = ContentScale.Crop, alpha = 0.55f)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0xDD000000)))))
            }
            "accent" -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, Color.Black))))
        }

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // header
            if (layout == "minimal") {
                // Centred clock and date, with a button for the words in the corner.
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    if (s.lockClock) {
                        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                            AndroidView(
                                factory = { c ->
                                    android.widget.TextClock(c).apply {
                                        format12Hour = "h:mm"; format24Hour = "H:mm"; textSize = 64f
                                        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                                    }
                                },
                                update = { it.setTextColor(argb(textColor)) },
                            )
                            AndroidView(
                                factory = { c -> android.widget.TextClock(c).apply { format12Hour = "MMM d, EEE"; format24Hour = "MMM d, EEE"; textSize = 15f } },
                                update = { it.setTextColor(argb(muted)) },
                            )
                        }
                    }
                    IconButton(onClick = { layout = "page" }, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Rounded.Subtitles, "Show the words", tint = muted) }
                }
            } else if (s.lockClock || s.lockTitle) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (s.lockClock) {
                        val big = s.lockClockSize == "large"
                        Column {
                            AndroidView(
                                factory = { c ->
                                    android.widget.TextClock(c).apply {
                                        format12Hour = "h:mm"; format24Hour = "H:mm"
                                        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                                    }
                                },
                                update = { it.textSize = if (big) 64f else 26f; it.setTextColor(argb(textColor)) },
                            )
                            if (big) AndroidView(
                                factory = { c -> android.widget.TextClock(c).apply { format12Hour = "EEEE, MMM d"; format24Hour = "EEEE, MMM d"; textSize = 15f } },
                                update = { it.setTextColor(argb(muted)) },
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                    }
                    if (s.lockTitle) {
                        Text(
                            video?.meta?.title ?: ps.title,
                            Modifier.weight(1f), color = muted, style = MaterialTheme.typography.labelLarge,
                            maxLines = if (s.lockClockSize == "large") 3 else 1, overflow = TextOverflow.Ellipsis,
                        )
                    } else Spacer(Modifier.weight(1f))
                    IconButton(onClick = { layout = "minimal" }) { Icon(Icons.Rounded.UnfoldLess, "Hide the words", tint = muted) }
                }
            }

            // cover layout: the artwork on top of the page
            if (layout == "cover") {
                AsyncImage(
                    id?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }, null,
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(150.dp).clip(RoundedCornerShape(22.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            if (layout == "minimal") {
                // Like a music app's lock screen: the title in the middle, with skip buttons on both sides.
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { app.player.skip(-10.0) }) { Icon(Icons.Rounded.Replay10, "Back 10 seconds", Modifier.size(30.dp), tint = muted) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(video?.meta?.title ?: ps.title, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Text(video?.meta?.author ?: ps.author, color = muted, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                        }
                        IconButton(onClick = { app.player.skip(10.0) }) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds", Modifier.size(30.dp), tint = muted) }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                // The page. This is the same text view as the reader, so you can select and highlight here.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val d = doc
                    if (d != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { c -> TranscriptView(c).also { tview = it } },
                            update = { tv ->
                                // A stray tap must not jump the audio on a locked phone.
                                tv.onTapWord = { true }
                                tv.onAway = { away = it && s.lockFollow }
                                tv.onPaint = { a, b, color ->
                                    val cur = id?.let { app.library.get(it) }
                                    if (cur != null) {
                                        val next = Highlights.apply(cur.highlights, d.words, d::plainText, a, b, color)
                                        app.library.edit(cur.meta.id) { it.copy(highlights = next) }
                                        app.settings.update { st -> st.copy(highlightColor = color) }
                                    }
                                }
                                tv.onErase = { a, b ->
                                    if (id != null) {
                                        app.library.edit(id) { it.copy(highlights = Highlights.erase(it.highlights, d.words, d::plainText, a, b)) }
                                    }
                                }
                                tv.currentColor = s.highlightColor
                                tv.bind(d, style)
                                colors?.let { tv.applyHighlights(it) }
                                tv.setActive(activeWord)
                            },
                        )
                    } else if (id != null && app.library.loadTranscript(id) == null) {
                        Text("No transcript for this video.", Modifier.align(Alignment.Center), color = muted)
                    }

                    // Shown only after you scrolled away from the spoken word.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = away,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                        enter = fadeIn(), exit = fadeOut(),
                    ) {
                        Surface(onClick = { tview?.resync(activeWord); away = false }, shape = RoundedCornerShape(24.dp), color = Color(0xFF1B1B1B)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp), tint = accent)
                                Spacer(Modifier.width(8.dp))
                                Text("Sync", color = Color(0xFFE6E6E6), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // The handle: drag it down to hide the controls and read, drag it up to bring them back.
            var acc by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier.fillMaxWidth().height(28.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { acc = 0f },
                            onDragEnd = { if (acc > 24f) controls = false else if (acc < -24f) controls = true },
                            onVerticalDrag = { _, dy -> acc += dy },
                        )
                    }
                    .clickable { controls = !controls },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(44.dp).height(4.dp).background(muted.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
            }

            AnimatedVisibility(visible = controls, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp)) {
                    var drag by remember { mutableFloatStateOf(-1f) }
                    val dur = ps.durationSec.toFloat().coerceAtLeast(1f)
                    if (s.lockSeekBar) {
                        Slider(
                            value = if (drag >= 0f) drag else ps.positionSec.toFloat().coerceIn(0f, dur),
                            onValueChange = { drag = it },
                            onValueChangeFinished = { if (drag >= 0f) app.player.seekTo(drag.toDouble()); drag = -1f },
                            valueRange = 0f..dur,
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = textColor.copy(alpha = 0.18f)),
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(YouTube.formatTime(if (drag >= 0f) drag.toDouble() else ps.positionSec), color = muted, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.weight(1f))
                            Text(YouTube.formatTime(ps.durationSec), color = muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = onUnlock, Modifier.size(48.dp)) { Icon(Icons.Rounded.LockOpen, "Unlock the phone", tint = muted) }
                        IconButton(onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            app.player.setLocked(true)
                        }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Lock, "Lock the controls", tint = muted) }
                        IconButton(onClick = { app.player.skip(-10.0) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Replay10, "Back 10 seconds", Modifier.size(32.dp), tint = textColor) }
                        MorphingPlayButton(playing = ps.playing, onClick = { app.player.toggle() }, size = 64.dp, container = accent, content = if (s.lockAccent == "white") Color.Black else MaterialTheme.colorScheme.onPrimary)
                        IconButton(onClick = { app.player.skip(10.0) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds", Modifier.size(32.dp), tint = textColor) }
                        if (s.lockHighlight) {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                Toast.makeText(ctx, QuickHighlight.run(app), Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Rounded.Highlight, "Highlight this sentence", tint = accent) }
                        } else Spacer(Modifier.size(48.dp))
                    }
                }
            }
        }

        if (locked) TouchLock(muted) { app.player.setLocked(false) }
    }
}

/**
 * Covers the whole lock screen and swallows every touch, so a phone in a pocket cannot skip, pause or
 * highlight. Only holding a finger on the pill for a moment gets out.
 */
@Composable
private fun TouchLock(muted: Color, onUnlock: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var pill by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(
        Modifier.fillMaxSize().background(Color(0xF2000000))
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                // One handler does it all, so no other layer can steal the touch.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var drain = true
                    if (pill.contains(down.position + origin)) {
                        val timedOut = try {
                            withTimeout(700) { waitForUpOrCancellation() }
                            false
                        } catch (_: PointerEventTimeoutCancellationException) { true }
                        if (timedOut) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onUnlock()
                        } else drain = false
                    }
                    while (drain) {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                        if (e.changes.none { it.pressed }) break
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(40.dp), tint = muted)
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(24.dp), color = Color(0xFF1B1B1B),
                modifier = Modifier.onGloballyPositioned { pill = it.boundsInRoot() },
            ) {
                Text("Hold to unlock", Modifier.padding(horizontal = 24.dp, vertical = 16.dp), color = Color(0xFFE6E6E6), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
