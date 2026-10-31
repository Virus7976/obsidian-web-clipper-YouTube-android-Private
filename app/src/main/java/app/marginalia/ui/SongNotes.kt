package app.marginalia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.marginalia.data.Highlight
import app.marginalia.data.SavedVideo
import app.marginalia.data.YouTube
import java.util.UUID

/**
 * For a song, or anything with no captions: write notes while it plays. Each note keeps the time
 * it was started, and goes into the Obsidian note with the other highlights.
 */
@Composable
fun SongNotes(video: SavedVideo, failure: String?, nowSec: Double, onSeek: (Double) -> Unit, onRetry: () -> Unit) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    var text by remember(video.meta.id) { mutableStateOf("") }
    // The note belongs to the moment you started writing, not the moment you press Add.
    var stamp by remember(video.meta.id) { mutableStateOf<Double?>(null) }
    val at = stamp ?: nowSec

    fun add(note: String, t: Double) {
        val h = Highlight(UUID.randomUUID().toString(), t, t, "", note.trim(), settings.highlightColor)
        app.library.edit(video.meta.id) { it.copy(highlights = it.highlights + h) }
        text = ""
        stamp = null
    }

    val notes = video.highlights.sortedBy { it.startSec }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("intro") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("No captions for this one", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Write what you notice as it plays. Each note keeps its time, and goes into your Obsidian note when you save.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("compose") {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v ->
                            if (text.isEmpty() && v.isNotEmpty()) stamp = nowSec
                            if (v.isEmpty()) stamp = null
                            text = v
                        },
                        label = { Text("Note at ${YouTube.formatTime(at)}") },
                        minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = text.isNotBlank(), onClick = { add(text, at) }, shapes = ButtonDefaults.shapes()) { Text("Add note") }
                        FilledTonalButton(onClick = { add("", nowSec) }, shapes = ButtonDefaults.shapes()) { Text("Mark ${YouTube.formatTime(nowSec)}") }
                    }
                }
            }
        }
        items(notes, key = { "n" + it.id }) { h ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onSeek(h.startSec) }.padding(vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.Top,
            ) {
                Text(
                    YouTube.formatTime(h.startSec), Modifier.padding(end = 12.dp, top = 2.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    h.note.ifBlank { "Marked" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    color = if (h.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = { app.library.edit(video.meta.id) { v -> v.copy(highlights = v.highlights.filterNot { it.id == h.id }) } }, Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, "Delete note", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
        item("retry") {
            TextButton(onClick = onRetry) { Text("Try captions again") }
            if (!failure.isNullOrBlank() && failure != "This video has no captions.") Text(
                failure, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
