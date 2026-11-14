package app.marginalia.ui

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.obsi.core.data.ClipTemplate
import app.obsi.core.data.TemplateProperty
import app.marginalia.data.YouTube
import app.marginalia.obsidian.NoteBuilder
import app.marginalia.obsidian.TemplateSync
import app.obsi.core.obsidian.VideoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** What happens when the note is already there, in plain words. */
private val behaviors = listOf(
    "create" to "Make a new note",
    "overwrite" to "Replace the note",
    "append-specific" to "Add to the end",
    "prepend-specific" to "Add to the top",
)

private val variablesHelp =
    "Video data: " + (VideoData.fields.map { it.key } + "content").joinToString(" ") { "{{$it}}" } +
        "\n\nFilters: split join wikilink link date:\"YYYY-MM-DD\" lower upper capitalize title trim kebab snake camel " +
        "safe_name blockquote list first last default:\"x\" replace:\"a\":\"b\""

/**
 * One template, as three cards: what goes in the note's properties, where the note is saved, and the
 * note's text. Every row shows its value, and a tap opens the one thing that changes it.
 */
@Composable
fun TemplateEditorScreen(id: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val s by app.settings.state.collectAsState()
    val t = s.templates.firstOrNull { it.id == id }
    if (t == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    // A vault template opens with what is in Obsidian now, and leaves with what changed here.
    LaunchedEffect(id) { TemplateSync.pull(app, id) }
    androidx.compose.runtime.DisposableEffect(id) {
        onDispose { kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { TemplateSync.push(app, id) } }
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
    // Which text dialog is open: "name", "noteName", "folder", "vault" or "content".
    var dialog by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    var behaviorMenu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val isDefault = t.id == s.selectedTemplateId

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 40.dp,
        ),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                // The name is the title. Tap it to rename.
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Rename") { dialog = "name" }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { more = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(more, { more = false }, shape = RoundedCornerShape(20.dp), containerColor = scheme.surfaceContainerHigh) {
                        if (!isDefault) DropdownMenuItem(
                            text = { Text("Use as default") }, leadingIcon = { Icon(Icons.Rounded.Star, null) },
                            onClick = { more = false; app.settings.update { it.copy(selectedTemplateId = id) } },
                        )
                        if (s.templates.size > 1) DropdownMenuItem(
                            text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = {
                                more = false
                                app.settings.update { st ->
                                    val rest = st.templates.filterNot { it.id == id }
                                    st.copy(templates = rest, selectedTemplateId = if (st.selectedTemplateId == id) rest.first().id else st.selectedTemplateId)
                                }
                                onBack()
                            },
                        )
                    }
                }
            }
            val about = listOfNotNull(
                if (isDefault) "Default template" else null,
                if (t.source.isNotBlank()) "Synced with ${t.source}" else null,
            ).joinToString("  ·  ")
            if (about.isNotEmpty()) Text(
                about, Modifier.padding(start = 64.dp, end = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        item {
            Section("Properties", footer = sample?.let { "Examples from ${it.meta.title}" }) {
                t.properties.forEach { p -> PropertyRow(p, vars) { editing = p.id } }
                AddRow("Add a property") {
                    val pid = UUID.randomUUID().toString()
                    edit { it.copy(properties = it.properties + TemplateProperty(pid, "new property", "", "")) }
                    editing = pid
                }
            }
        }
        item {
            Section("Saving") {
                ListRow("Note name", t.noteNameFormat.ifBlank { "Not set" }, mono = true, onClick = { dialog = "noteName" })
                ListRow("Folder", t.path.ifBlank { "Vault root" }, onClick = { dialog = "folder" })
                ListRow("Vault", t.vault.ifBlank { "Default vault" }, onClick = { dialog = "vault" })
                Box {
                    ListRow(
                        "If the note is already there", behaviors.firstOrNull { it.first == t.behavior }?.second ?: "Make a new note",
                        onClick = { behaviorMenu = true },
                    )
                    DropdownMenu(behaviorMenu, { behaviorMenu = false }, shape = RoundedCornerShape(20.dp), containerColor = scheme.surfaceContainerHigh) {
                        behaviors.forEach { (k, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                trailingIcon = { if (t.behavior == k) Icon(Icons.Rounded.Check, "Chosen", tint = scheme.primary) },
                                onClick = { behaviorMenu = false; edit { it.copy(behavior = k) } },
                            )
                        }
                    }
                }
            }
        }
        item {
            Section("Note text") {
                ListRow(
                    "What goes under the properties", t.noteContentFormat.ifBlank { "Nothing" }.replace('\n', ' '), mono = true,
                    onClick = { dialog = "content" },
                ) { Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant) }
            }
        }
    }
    when (dialog) {
        "name" -> TextDialog("Template name", t.name, { v -> edit { it.copy(name = v.trim().ifBlank { it.name }) } }, { dialog = null })
        "noteName" -> TextDialog("Note name", t.noteNameFormat, { v -> edit { it.copy(noteNameFormat = v) } }, { dialog = null }, help = "For example {{title}} or {{date|date:\"YYYY-MM-DD\"}} {{title}}", mono = true)
        "folder" -> TextDialog("Folder", t.path, { v -> edit { it.copy(path = v.trim().trim('/')) } }, { dialog = null }, help = "Blank saves to the vault's top folder.")
        "vault" -> TextDialog("Vault", t.vault, { v -> edit { it.copy(vault = v.trim()) } }, { dialog = null }, help = "Blank uses your default vault.")
        "content" -> TextDialog("Note text", t.noteContentFormat, { v -> edit { it.copy(noteContentFormat = v) } }, { dialog = null }, help = variablesHelp, mono = true, lines = 6)
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
