package app.marginalia.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.marginalia.MarginaliaApp
import app.marginalia.data.YouTube
import app.marginalia.player.QuickHighlight
import coil3.compose.AsyncImage

private fun argb(c: Color, alpha: Float = c.alpha): Int =
    android.graphics.Color.argb((alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

/** The player shown over the keyguard. Every part of it follows the lock screen settings. */
@Composable
fun LockScreen(app: MarginaliaApp, onUnlock: () -> Unit, onClose: () -> Unit) {
    val s by app.settings.state.collectAsState()
    val ps by app.player.state.collectAsState()
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val id = ps.videoId
    val art = id?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

    // Nothing loaded: there is nothing to show.
    LaunchedEffect(id) { if (id == null) onClose() }

    // The words being spoken right now, from the saved transcript.
    val cues = remember(id) { id?.let { app.library.loadTranscript(it)?.cues }.orEmpty() }
    val line = remember(cues, (ps.positionSec * 2).toInt()) {
        val i = cues.indexOfLast { it.start <= ps.positionSec }
        if (i >= 0 && ps.positionSec <= cues[i].start + cues[i].dur + 2.0) cues[i].text.replace('\n', ' ').trim() else ""
    }

    val onDark = s.lockBackground != "accent"
    val fg = if (onDark) Color.White else scheme.onPrimaryContainer
    val soft = fg.copy(alpha = 0.72f)

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if (total < -220f) onUnlock() },
                onVerticalDrag = { _, dy -> total += dy },
            )
        },
    ) {
        // background
        when (s.lockBackground) {
            "black" -> Box(Modifier.fillMaxSize().background(Color.Black))
            "accent" -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.background))))
            else -> {
                Box(Modifier.fillMaxSize().background(Color.Black))
                AsyncImage(art, null, Modifier.fillMaxSize().blur(36.dp), contentScale = ContentScale.Crop, alpha = 0.9f)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color(0xCC000000)))))
            }
        }

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // clock
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (s.lockClock) {
                    Column(Modifier.weight(1f)) {
                        AndroidView(
                            factory = { c ->
                                android.widget.TextClock(c).apply {
                                    format12Hour = "h:mm"; format24Hour = "H:mm"
                                    textSize = 56f
                                    typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                                }
                            },
                            update = { it.setTextColor(argb(fg)) },
                        )
                        AndroidView(
                            factory = { c ->
                                android.widget.TextClock(c).apply {
                                    format12Hour = "EEEE, MMM d"; format24Hour = "EEEE, MMM d"
                                    textSize = 15f
                                }
                            },
                            update = { it.setTextColor(argb(fg, 0.72f)) },
                        )
                    }
                } else Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close", tint = soft) }
            }

            Spacer(Modifier.height(12.dp))

            // middle: depends on the style
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                when (s.lockStyle) {
                    "minimal" -> {
                        Text(ps.title, color = fg, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Text(ps.author, color = soft, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        if (s.lockTranscript) Spoken(line, fg, large = false)
                    }
                    "transcript" -> {
                        Text(ps.title, color = soft, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(20.dp))
                        Spoken(line.ifBlank { "…" }, fg, large = true)
                    }
                    else -> {
                        AsyncImage(
                            art, null, Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(28.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(ps.title, color = fg, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(ps.author, color = soft, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        if (s.lockTranscript) Spoken(line, fg, large = false)
                    }
                }
            }

            // seek bar
            var drag by remember { mutableFloatStateOf(-1f) }
            val dur = ps.durationSec.toFloat().coerceAtLeast(1f)
            Slider(
                value = if (drag >= 0f) drag else ps.positionSec.toFloat().coerceIn(0f, dur),
                onValueChange = { drag = it },
                onValueChangeFinished = { if (drag >= 0f) app.player.seekTo(drag.toDouble()); drag = -1f },
                valueRange = 0f..dur,
                colors = SliderDefaults.colors(thumbColor = fg, activeTrackColor = fg, inactiveTrackColor = fg.copy(alpha = 0.25f)),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(YouTube.formatTime(if (drag >= 0f) drag.toDouble() else ps.positionSec), color = soft, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(YouTube.formatTime(ps.durationSec), color = soft, style = MaterialTheme.typography.labelMedium)
            }

            // controls
            Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                IconButton(onClick = { app.player.skip(-10.0) }, Modifier.size(56.dp)) { Icon(Icons.Rounded.Replay10, "Back 10 seconds", Modifier.size(36.dp), tint = fg) }
                MorphingPlayButton(playing = ps.playing, onClick = { app.player.toggle() }, size = 80.dp)
                IconButton(onClick = { app.player.skip(10.0) }, Modifier.size(56.dp)) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds", Modifier.size(36.dp), tint = fg) }
            }

            if (s.lockHighlight) {
                FilledTonalButton(onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    Toast.makeText(ctx, QuickHighlight.run(app), Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Rounded.Highlight, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Highlight this")
                }
            }

            // unlock
            Column(Modifier.fillMaxWidth().clickable(onClick = onUnlock).padding(top = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.KeyboardArrowUp, null, tint = soft)
                Text("Swipe up to unlock", color = soft, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** The line being spoken, which fades between lines. */
@Composable
private fun Spoken(line: String, color: Color, large: Boolean) {
    AnimatedContent(
        targetState = line,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = Modifier.padding(top = if (large) 0.dp else 16.dp),
        label = "spoken",
    ) { text ->
        Text(
            text, color = color.copy(alpha = if (large) 1f else 0.85f),
            fontSize = if (large) 26.sp else 16.sp, lineHeight = if (large) 36.sp else 22.sp,
            fontWeight = if (large) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center, maxLines = if (large) 8 else 3, overflow = TextOverflow.Ellipsis,
        )
    }
}
