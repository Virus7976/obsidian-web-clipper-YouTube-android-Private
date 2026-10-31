package app.marginalia.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marginalia.data.ChannelSource
import app.marginalia.data.Playlist
import app.marginalia.data.PlaylistImport
import app.marginalia.data.SavedVideo
import app.marginalia.data.YouTube
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One pop-up for everything you can add: a video link, a playlist link, a channel link, or words to
 * search for. A video opens in the reader at once, so there is no second button to reach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(initial: String?, onDismiss: () -> Unit, onPlaylist: (String) -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initial.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var plPreview by remember { mutableStateOf<PlaylistImport.Result?>(null) }
    var plListId by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<ChannelSource.Hit>?>(null) }
    val focus = remember { FocusRequester() }

    fun go(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val link = ChannelSource.firstUrl(text)
        error = null
        hits = null
        plPreview = null
        val id = YouTube.extractId(text)
        when {
            id != null -> { nav.openVideo(id); onDismiss() }
            PlaylistImport.isPlaylistLink(text) -> {
                val listId = PlaylistImport.listId(text)!!
                busy = true
                scope.launch {
                    runCatching { PlaylistImport.fetch(app.youtube, listId) }
                        .onSuccess { plListId = listId; plPreview = it }
                        .onFailure { error = it.message ?: "Could not read that playlist." }
                    busy = false
                }
            }
            ChannelSource.isChannelLink(text) -> { nav.openChannel(link ?: text); onDismiss() }
            link != null -> error = "That is not a YouTube link."
            else -> {
                busy = true
                scope.launch {
                    runCatching { ChannelSource.search(app.youtube, text) }
                        .onSuccess { hits = it; if (it.isEmpty()) error = "Nothing found." }
                        .onFailure { error = "Search did not work. Check your connection." }
                    busy = false
                }
            }
        }
    }

    fun importPlaylist(r: PlaylistImport.Result) {
        val pid = "pl-$plListId"
        val now = System.currentTimeMillis()
        r.videos.forEach { m -> if (app.library.get(m.id) == null) app.library.upsert(SavedVideo(meta = m, playlistId = pid, addedAt = now)) }
        app.playlists.put(Playlist(pid, r.title, plListId, r.videos.map { it.id }, now))
        onPlaylist(pid)
        onDismiss()
    }

    LaunchedEffect(Unit) {
        if (!initial.isNullOrBlank()) go(initial)
        else { delay(250); runCatching { focus.requestFocus() } }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true, shape = RoundedCornerShape(50),
                placeholder = { Text("Paste a link, or search YouTube") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (input.isNotEmpty()) IconButton(onClick = { input = ""; error = null; hits = null; plPreview = null }) { Icon(Icons.Rounded.Close, "Clear") }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go(input) }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = {
                        val text = readClip(ctx)
                        if (text.isNullOrBlank()) error = "The clipboard is empty." else { input = text; go(text) }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Paste and go")
                }
                if (input.isNotBlank() && !busy) Button(onClick = { go(input) }, shapes = ButtonDefaults.shapes()) { Text("Go") }
            }
            if (busy) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            plPreview?.let { r ->
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${r.videos.size} videos. They go into a playlist. Your library stays clean.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { importPlaylist(r) }, shapes = ButtonDefaults.shapes()) { Text("Import playlist") }
                            TextButton(onClick = { plPreview = null }) { Text("Not now") }
                        }
                    }
                }
            }

            hits?.takeIf { it.isNotEmpty() }?.let { list ->
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(list, key = { h -> if (h is ChannelSource.Hit.Chan) "c" + h.channel.url else "v" + (h as ChannelSource.Hit.Vid).video.meta.id }) { h ->
                        when (h) {
                            is ChannelSource.Hit.Chan -> Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { nav.openChannel(h.channel.url); onDismiss() }.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(h.channel.avatar, 48)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(h.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (h.subscribers > 0) "Channel · ${compactCount(h.subscribers)} subscribers" else "Channel",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            is ChannelSource.Hit.Vid -> Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { nav.openVideo(h.video.meta.id); onDismiss() }.padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    h.video.meta.thumb, null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(96.dp).androidxAspect().clip(RoundedCornerShape(12.dp)),
                                )
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(h.video.meta.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(h.video.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A round picture, or a plain disc when there is none. */
@Composable
fun Avatar(url: String, size: Int) {
    if (url.isBlank()) Box(Modifier.size(size.dp).clip(CircleShape).then(Modifier), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape, modifier = Modifier.size(size.dp)) {}
    } else AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp).clip(CircleShape))
}

/** 1200 becomes 1.2K, 3400000 becomes 3.4M. */
fun compactCount(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1fB".format(n / 1e9).replace(".0", "")
    n >= 1_000_000 -> "%.1fM".format(n / 1e6).replace(".0", "")
    n >= 1_000 -> "%.1fK".format(n / 1e3).replace(".0", "")
    else -> n.toString()
}

private fun readClip(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim()
}
