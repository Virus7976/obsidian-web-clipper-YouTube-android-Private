package app.marginalia.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marginalia.R
import app.marginalia.data.DueItem
import app.marginalia.data.HighlightColors
import app.marginalia.data.YouTube
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the last swipe did, so Undo can put it back. */
private class Undo(val index: Int, val id: String, val previous: app.marginalia.data.ReviewCard?, val what: String)

/**
 * Review mode. One old highlight at a time. Swipe right or press Keep to see it again later.
 * Swipe left or press Drop to stop seeing it here. A drop never deletes the highlight.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReviewScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val videos by app.library.videos.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val settings by app.settings.state.collectAsState()
    // 0 in settings means no limit.
    val limit = settings.reviewBatch.takeIf { it > 0 } ?: Int.MAX_VALUE
    var batch by remember { mutableStateOf<List<DueItem>>(app.reviews.due(videos).take(limit)) }
    var last by remember { mutableStateOf<Undo?>(null) }
    androidx.compose.runtime.LaunchedEffect(last) { if (last != null) { kotlinx.coroutines.delay(6000); last = null } }
    var at by remember { mutableIntStateOf(0) }
    var kept by remember { mutableIntStateOf(0) }
    var dropped by remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }
    val width = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    fun decide(keep: Boolean) {
        val item = batch.getOrNull(at) ?: return
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
        scope.launch {
            offset.animateTo(if (keep) width else -width)
            val prev = app.reviews.cards.value[item.highlight.id]
            if (keep) { app.reviews.keep(item.highlight.id); kept++ } else { app.reviews.drop(item.highlight.id); dropped++ }
            last = Undo(at, item.highlight.id, prev, if (keep) "Kept" else "Dropped")
            at++
            offset.snapTo(0f)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Review", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (batch.isNotEmpty() && at < batch.size) Text(
                "${at + 1} of ${batch.size}", Modifier.padding(end = 16.dp),
                style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
            )
        }
        if (batch.isNotEmpty()) LinearProgressIndicator(
            progress = { at.toFloat() / batch.size }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(4.dp),
        )

        val item = batch.getOrNull(at)
        if (item == null) {
            Box(Modifier.weight(1f).fillMaxWidth()) { Done(
                kept = kept, dropped = dropped, hadAny = batch.isNotEmpty(),
                more = remember(videos, at) { app.reviews.due(videos).size },
                size = limit,
                onMore = { batch = app.reviews.due(videos).take(limit); at = 0; kept = 0; dropped = 0; last = null },
                onBack = onBack,
            ) }
        } else {
            val h = item.highlight
            val bar = HighlightColors.getOrNull(h.color)?.ui?.let { Color(it) } ?: scheme.primary
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                // A quiet cue behind the card: a check on the left for keep, a cross on the right for drop.
                // It fades in as you drag and fills in when letting go would count.
                val pull = (abs(offset.value) / (width * 0.28f)).coerceIn(0f, 1f)
                val armed = pull >= 1f
                val keeping = offset.value > 0f
                androidx.compose.runtime.LaunchedEffect(armed) {
                    if (armed) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                }
                if (pull > 0.02f) Box(
                    Modifier.align(if (keeping) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 14.dp)
                        .graphicsLayer { alpha = pull; scaleX = 0.6f + 0.4f * pull; scaleY = 0.6f + 0.4f * pull }
                        .size(52.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (armed) (if (keeping) scheme.primary else scheme.onSurfaceVariant) else scheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (keeping) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(24.dp),
                        tint = if (armed) (if (keeping) scheme.onPrimary else scheme.surface) else scheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = offset.value; rotationZ = offset.value / 40f; alpha = 1f - 0.2f * pull }
                        .pointerInput(at) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val x = offset.value
                                    if (abs(x) > width * 0.28f) decide(x > 0) else scope.launch { offset.animateTo(0f) }
                                },
                                onDragCancel = { scope.launch { offset.animateTo(0f) } },
                                onHorizontalDrag = { change, dx -> change.consume(); scope.launch { offset.snapTo(offset.value + dx) } },
                            )
                        },
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.width(6.dp).fillMaxHeight().background(bar))
                        Column(Modifier.weight(1f).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (h.text.isNotBlank()) Text(
                                h.text,
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily(Font(R.font.newsreader)), lineHeight = 34.sp),
                                maxLines = 12, overflow = TextOverflow.Ellipsis,
                            )
                            if (h.note.isNotBlank()) Text(
                                h.note, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
                                maxLines = 8, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.video.meta.title + "  ·  " + YouTube.formatTime(h.startSec),
                                style = MaterialTheme.typography.labelLarge, color = scheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = {
                                app.library.edit(item.video.meta.id) { it.copy(positionSec = h.startSec) }
                                if (app.player.state.value.videoId == item.video.meta.id) app.player.seekTo(h.startSec)
                                app.pendingSeek.value = item.video.meta.id to h.startSec
                                nav.openVideo(item.video.meta.id)
                            }) {
                                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open where it came from")
                            }
                        }
                    }
                }
            }
            Text(
                "Swipe right to keep it, left to drop it", Modifier.fillMaxWidth().padding(bottom = 8.dp),
                style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { decide(false) }, modifier = Modifier.weight(1f).height(56.dp), shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Drop")
                }
                Button(onClick = { decide(true) }, modifier = Modifier.weight(1f).height(56.dp), shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Keep")
                }
            }
            TextButton(
                onClick = {
                    last = Undo(at, h.id, app.reviews.cards.value[h.id], "Skipped")
                    app.reviews.later(h.id)
                    at++
                },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            ) { Text("Not now, ask me tomorrow") }
        }

        // Undo stays for a few seconds after every swipe.
        androidx.compose.animation.AnimatedVisibility(
            visible = last != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
        ) {
            val u = last
            Surface(shape = RoundedCornerShape(50), color = scheme.inverseSurface) {
                Row(Modifier.padding(start = 20.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(u?.what.orEmpty(), color = scheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = {
                        u?.let { x ->
                            app.reviews.restore(x.id, x.previous)
                            at = x.index
                            when (x.what) { "Kept" -> kept = (kept - 1).coerceAtLeast(0); "Dropped" -> dropped = (dropped - 1).coerceAtLeast(0) }
                        }
                        last = null
                    }) { Text("Undo", color = scheme.inversePrimary) }
                }
            }
        }
    }
}

@Composable
private fun Done(kept: Int, dropped: Int, hadAny: Boolean, more: Int, size: Int, onMore: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(if (hadAny) "That is all for now" else "Nothing to review yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !hadAny -> "A highlight shows up here a day after you make it, then again after 3, 7, 16 and more days."
                kept + dropped == 0 -> "Skipped ones come back tomorrow."
                else -> "You kept $kept and dropped $dropped."
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (more > 0) Button(onClick = onMore, shapes = ButtonDefaults.shapes()) { Text("Review ${if (size == Int.MAX_VALUE) "all $more" else minOf(more, size).toString() + " more"}") }
        TextButton(onClick = onBack) { Text("Done") }
    }
}
