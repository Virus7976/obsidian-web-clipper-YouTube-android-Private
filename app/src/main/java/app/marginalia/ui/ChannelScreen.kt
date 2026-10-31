package app.marginalia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marginalia.data.ChannelSource
import app.marginalia.data.ChannelVideo
import kotlinx.coroutines.launch

/** A channel's page: who it is, a Follow button, and its videos. Tap one to read it. */
@Composable
fun ChannelScreen(url: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val subs by app.subscriptions.all.collectAsState()
    val library by app.library.videos.collectAsState()
    var opened by remember(url) { mutableStateOf<ChannelSource.Opened?>(null) }
    var videos by remember(url) { mutableStateOf<List<ChannelVideo>>(emptyList()) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var more by remember(url) { mutableStateOf(false) }
    var loadingMore by remember(url) { mutableStateOf(false) }
    var query by remember(url) { mutableStateOf("") }

    LaunchedEffect(url) {
        runCatching { ChannelSource.open(app.youtube, url) }
            .onSuccess { opened = it; videos = it.videos; more = it.cursor.next != null }
            .onFailure { error = "Could not open that channel. Check your connection." }
    }

    val inLibrary = remember(library) { library.map { it.meta.id }.toSet() }
    val o = opened
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 40.dp,
            ),
        ) {
            item("bar") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Text(o?.channel?.name ?: "Channel", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (o != null) {
                item("head") {
                    val following = subs.any { it.url == o.channel.url }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(o.channel.avatar, 72)
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(o.channel.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (o.subscribers > 0) Text(
                                    "${compactCount(o.subscribers)} subscribers",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (following) FilledTonalButton(onClick = {
                            app.subscriptions.remove(o.channel.url)
                            app.feed.forget(o.channel.url)
                        }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Check, null, Modifier.padding(end = 6.dp)); Text("Following")
                        } else Button(onClick = {
                            app.subscriptions.add(o.channel)
                            app.feed.refresh(force = true)
                        }, shapes = ButtonDefaults.shapes()) { Text("Follow") }
                        if (o.description.isNotBlank()) Text(
                            o.description.trim(), maxLines = 3, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item("search") { SearchField(query, { query = it }, "Search ${o.channel.name}") }
                val shown = if (query.isBlank()) videos else videos.filter { matchesQuery(query, it.meta.title) }
                if (query.isNotBlank() && more) item("searching") {
                    Text(
                        "Looking through the rest of the channel…", Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(shown, key = { "cv" + it.meta.id }) { v ->
                    ChannelVideoRow(v, showChannel = false, saved = v.meta.id in inLibrary) { nav.openVideo(v.meta.id) }
                }
                if (shown.isEmpty() && !more) item("none") {
                    Text(if (query.isBlank()) "No videos found on this channel." else "No video matches \"$query\".", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (more) item("more") {
                    // Reaching the end of the list asks for the next page.
                    LaunchedEffect(videos.size) {
                        if (!loadingMore) {
                            loadingMore = true
                            val next = runCatching { ChannelSource.more(app.youtube, o.channel, o.cursor) }.getOrDefault(emptyList())
                            videos = (videos + next).distinctBy { it.meta.id }
                            more = o.cursor.next != null && next.isNotEmpty()
                            loadingMore = false
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            } else item("wait") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) else LoadingIndicator()
                }
            }
        }
    }
}
