package app.marginalia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marginalia.data.ClipTemplate
import app.marginalia.data.TemplateProperty
import app.marginalia.data.YouTube
import app.marginalia.obsidian.NoteBuilder
import app.marginalia.obsidian.VideoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private val behaviors = listOf(
    "create" to "Create",
    "overwrite" to "Overwrite",
    "append-specific" to "Append",
    "prepend-specific" to "Prepend",
)

@Composable
fun TemplateEditorScreen(id: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val s by app.settings.state.collectAsState()
    val t = s.templates.firstOrNull { it.id == id }
    if (t == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    fun edit(block: (ClipTemplate) -> ClipTemplate) {
        app.settings.update { st -> st.copy(templates = st.templates.map { if (it.id == id) block(it) else it }) }
    }
    fun editProp(pid: String, block: (TemplateProperty) -> TemplateProperty) =
        edit { it.copy(properties = it.properties.map { p -> if (p.id == pid) block(p) else p }) }

    // Your last video, so each kind of data shows what it really holds.
    val videos by app.library.videos.collectAsState()
    val sample = remember(videos) { videos.maxByOrNull { maxOf(it.playedAt, it.updatedAt) } }
    val vars by produceState(emptyMap<String, String>(), sample?.meta?.id) {
        val v = sample ?: return@produceState
        value = withContext(Dispatchers.IO) {
            val lines = app.library.loadTranscript(v.meta.id)?.let { YouTube.toLines(it.cues, s.lineLength, v.chapters) }.orEmpty()
            NoteBuilder.variables(v, lines, s)
        }
    }
    var editing by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Template", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                if (s.templates.size > 1) {
                    IconButton(onClick = {
                        app.settings.update { st ->
                            val rest = st.templates.filterNot { it.id == id }
                            st.copy(templates = rest, selectedTemplateId = if (st.selectedTemplateId == id) rest.first().id else st.selectedTemplateId)
                        }
                        onBack()
                    }) { Icon(Icons.Rounded.Delete, "Delete template") }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(t.name, { v -> edit { it.copy(name = v) } }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                if (t.source.isNotBlank()) {
                    Text("From your vault: ${t.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("Properties", style = MaterialTheme.typography.titleLarge)
                Text("Tap one to choose what goes in it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                sample?.let {
                    Text(
                        "Examples from ${it.meta.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
        itemsIndexed(t.properties, key = { _, p -> p.id }) { _, p ->
            Column(Modifier.padding(horizontal = 16.dp)) { PropertyRow(p, vars) { editing = p.id } }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = {
                    val pid = UUID.randomUUID().toString()
                    edit { it.copy(properties = it.properties + TemplateProperty(pid, "new property", "", "")) }
                    editing = pid
                }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add property")
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("When saving", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    behaviors.forEach { (k, label) ->
                        FilterChip(selected = t.behavior == k, onClick = { edit { it.copy(behavior = k) } }, label = { Text(label) })
                    }
                }
                OutlinedTextField(t.noteNameFormat, { v -> edit { it.copy(noteNameFormat = v) } }, Modifier.fillMaxWidth(), label = { Text("Note name") }, singleLine = true)
                OutlinedTextField(t.path, { v -> edit { it.copy(path = v) } }, Modifier.fillMaxWidth(), label = { Text("Folder") }, singleLine = true)
                OutlinedTextField(
                    t.vault, { v -> edit { it.copy(vault = v) } }, Modifier.fillMaxWidth(),
                    label = { Text("Vault, blank uses the default") }, singleLine = true,
                )
                OutlinedTextField(
                    t.noteContentFormat, { v -> edit { it.copy(noteContentFormat = v) } }, Modifier.fillMaxWidth(),
                    label = { Text("Note content") }, minLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                if (t.id != s.selectedTemplateId) {
                    FilledTonalButton(onClick = { app.settings.update { it.copy(selectedTemplateId = id) } }, shapes = ButtonDefaults.shapes()) {
                        Text("Use as the default template")
                    }
                } else {
                    Text("This is the default template.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Surface(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Variables", style = MaterialTheme.typography.titleSmall)
                    Text(
                        (VideoData.fields.map { it.key } + "content").joinToString(" ") { "{{$it}}" },
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp,
                    )
                    Text("Filters", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        "split:\", \"  join  wikilink  link  date:\"YYYY-MM-DD\"  lower  upper  capitalize  title  trim  " +
                            "kebab  snake  camel  safe_name  blockquote  list  first  last  default:\"x\"  replace:\"a\":\"b\"",
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp,
                    )
                }
            }
        }
    }
    t.properties.firstOrNull { it.id == editing }?.let { p ->
        PropertyRuleSheet(
            p = p, vars = vars, vault = s.linkedVault(s.vault) ?: s.vaultFolders.keys.firstOrNull(),
            onChange = { block -> editProp(p.id, block) },
            onRemove = {
                editing = null
                edit { it.copy(properties = it.properties.filterNot { x -> x.id == p.id }) }
            },
            onDismiss = { editing = null },
        )
    }
}
