package app.marginalia.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marginalia.data.Line
import app.marginalia.data.NoteRef
import app.marginalia.data.ClipMemo
import app.marginalia.data.OmniKind
import app.marginalia.data.SavedVideo
import app.marginalia.obsidian.NoteBuilder
import app.marginalia.obsidian.SendResult
import app.marginalia.obsidian.Sender
import app.marginalia.obsidian.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SaveSheet(
    video: SavedVideo,
    lines: List<Line>,
    onDismiss: () -> Unit,
    onDone: (message: String, ok: Boolean) -> Unit,
) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    val indexes by app.vaults.indexes.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        val templates = settings.templates
        if (templates.isEmpty()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No templates", style = MaterialTheme.typography.titleLarge)
                Text("Add one in Settings, or import your Web Clipper settings file.", style = MaterialTheme.typography.bodyMedium)
            }
            return@ModalBottomSheet
        }
        var templateId by remember {
            mutableStateOf(
                video.lastClip?.templateId?.takeIf { id -> templates.any { it.id == id } }
                    ?: settings.selectedTemplateId.takeIf { id -> templates.any { it.id == id } } ?: templates.first().id,
            )
        }
        val template = templates.first { it.id == templateId }

        val base = remember(templateId) { NoteBuilder.build(template, video, lines, settings) }
        // Clipping again brings back what you filled in last time, for the same template.
        val memo = remember(templateId) { video.lastClip?.takeIf { it.templateId == templateId } }
        var name by remember(templateId) { mutableStateOf(memo?.name?.ifBlank { null } ?: base.name) }
        var folder by remember(templateId) { mutableStateOf(memo?.folder?.takeIf { it.isNotBlank() } ?: base.folder) }
        var vault by remember(templateId) { mutableStateOf(memo?.vault?.takeIf { it in settings.vaults } ?: base.vault) }
        val overrides = remember(templateId) { mutableStateMapOf<String, String>().apply { memo?.values?.let { putAll(it) } } }
        val tags = remember(templateId) {
            mutableStateListOf<String>().apply {
                if (memo != null && memo.tags.isNotEmpty()) addAll(memo.tags)
                else base.properties.firstOrNull { it.isTags() }?.let { addAll(NoteBuilder.splitList(it.value).map { t -> t.removePrefix("#").trim('"') }) }
            }
        }
        var detailsOpen by remember { mutableStateOf(false) }
        var previewOpen by remember { mutableStateOf(false) }
        var pickFolder by remember { mutableStateOf(false) }
        var pickTags by remember { mutableStateOf(false) }
        var pickNote by remember { mutableStateOf<String?>(null) }
        var asking by remember { mutableStateOf<String?>(null) }

        // Read the vault in the background so the existing-note check and the pickers are fresh.
        val key = settings.linkedVault(vault)
        androidx.compose.runtime.LaunchedEffect(key) { key?.let { app.vaults.refresh(it) } }
        val index = key?.let { indexes[it] }
        val existing: NoteRef? = remember(index, video.meta.url) { index?.noteForSource(video.meta.url) }

        // The note that is already there, so the sheet can show what it has (Settings > Obsidian).
        val existingText by androidx.compose.runtime.produceState<String?>(null, existing?.uri, settings.prefillExisting) {
            value = if (settings.prefillExisting) existing?.uri?.let { u -> withContext(Dispatchers.IO) { Sender.readText(ctx, u) } } else null
        }
        var prefilled by remember(existing?.path, templateId) { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(existingText, templateId) {
            val text = existingText ?: return@LaunchedEffect
            if (prefilled) return@LaunchedEffect
            val have = Updater.readValues(text)
            base.properties.forEach { p ->
                if (!p.isTags()) have[p.property.name.lowercase()]?.takeIf { it.isNotBlank() }?.let { overrides[p.property.id] = it }
            }
            have["tags"]?.let { v ->
                NoteBuilder.splitList(v).map { it.removePrefix("#").trim('"') }
                    .filter { t -> t.isNotBlank() && tags.none { it.equals(t, true) } }
                    .forEach { tags.add(it) }
            }
            prefilled = true
        }

        val tagsProp = base.properties.firstOrNull { it.isTags() }
        val note = NoteBuilder.build(
            template, video, lines, settings,
            overrides.toMap() + (tagsProp?.let { mapOf(it.property.id to tags.joinToString(",")) } ?: emptyMap()),
            name, folder, vault,
        )
        // "Yours to fill" is decided by the template, not by what is typed now. Otherwise the box
        // jumps into Details after the first letter and the text is lost.
        val mineIds = base.properties.filter { !it.isTags() && it.value.isBlank() }.map { it.property.id }.toSet()
        val mine = note.properties.filter { it.property.id in mineIds }
        val details = note.properties.filter { !it.isTags() && it.property.id !in mineIds }
        val folderUri = key?.let { settings.vaultFolders[it] } ?: settings.vaultFolderUri

        fun remember() {
            app.library.edit(video.meta.id) {
                it.copy(lastClip = ClipMemo(templateId, name, folder, vault, overrides.toMap(), tags.toList()))
            }
            app.settings.update {
                it.copy(
                    selectedTemplateId = templateId,
                    // Only a vault you already added can become the default. A clip never adds one.
                    vault = if (vault in it.vaults) vault else it.vault,
                    recentTags = (tags.toList() + it.recentTags).distinct().take(40),
                )
            }
        }

        fun send(kind: String) {
            remember()
            scope.launch {
                val r: SendResult = when (kind) {
                    "saveFile" -> withContext(Dispatchers.IO) { Sender.saveFile(ctx, note, folderUri) }
                    "copyToClipboard" -> Sender.copyToClipboard(ctx, note)
                    "share" -> Sender.share(ctx, note)
                    else -> Sender.addToObsidian(ctx, note)
                }
                if (r.ok && (kind == "addToObsidian" || kind == "saveFile")) {
                    app.library.edit(video.meta.id) { it.copy(noteVault = note.vault, notePath = note.path) }
                }
                onDone(r.message, r.ok)
            }
        }

        fun update(target: NoteRef) {
            remember()
            scope.launch {
                val old = withContext(Dispatchers.IO) { Sender.readText(ctx, target.uri) }
                if (old == null) { onDone("Could not read ${target.name}. Add the vault folder in Settings again.", false); return@launch }
                val merged = Updater.merge(old, note, video, settings, mineIds)
                if (!merged.changed) { onDone("${target.name} already has all of this.", true); return@launch }
                val ok = withContext(Dispatchers.IO) { Sender.writeText(ctx, target.uri, merged.text) }
                if (ok) {
                    app.vaults.refresh(vault, force = true)
                    val bits = listOfNotNull(
                        merged.addedHighlights.takeIf { it > 0 }?.let { "$it highlight${if (it == 1) "" else "s"}" },
                        merged.addedProps.takeIf { it > 0 }?.let { "$it propert${if (it == 1) "y" else "ies"}" },
                    )
                    app.library.edit(video.meta.id) { it.copy(noteVault = vault, notePath = target.path) }
                    onDone("Updated ${target.name}: added ${bits.joinToString(" and ")}.", true)
                } else onDone("Could not write ${target.name}.", false)
            }
        }

        // Where a save would touch the vault, ask first when the video is already there.
        fun go(kind: String) {
            if ((kind == "addToObsidian" || kind == "saveFile") && existing != null) asking = kind else send(kind)
        }

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Save to Obsidian", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                coil3.compose.AsyncImage(
                    video.meta.thumb, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.width(88.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(14.dp)),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(video.meta.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (memo != null) "Filled in from your last clip." else video.meta.author,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }

            if (existing != null) {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("You already saved this video", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(
                            existing.path,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (prefilled) "What the note has is filled in below. Add to it, then choose Update it." else "When you save, I ask if you want to update it or make a new one.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            if (templates.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach { t ->
                        FilterChip(selected = t.id == templateId, onClick = { templateId = t.id }, label = { Text(t.name) })
                    }
                }
            }

            // ---- the things you fill in
            mine.forEach { p ->
                OutlinedTextField(
                    value = p.value,
                    onValueChange = { overrides[p.property.id] = it },
                    label = { Text(p.property.name) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (p.property.name.contains("idea", true)) 3 else 1,
                    maxLines = 8,
                    shape = MaterialTheme.shapes.large,
                )
            }

            if (tagsProp != null) {
                Text("Tags", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.toList().forEach { t ->
                        InputChip(selected = true, onClick = { tags.remove(t) }, label = { Text("#$t") })
                    }
                    AssistChip(
                        onClick = { pickTags = true },
                        label = { Text(if (tags.isEmpty()) "Add tags" else "Add") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                    )
                }
            }

            // ---- where it goes
            Text("Where it goes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (settings.vaults.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.vaults.forEach { v -> FilterChip(selected = v == vault, onClick = { vault = v }, label = { Text(v) }) }
                }
            } else {
                OutlinedTextField(
                    value = vault, onValueChange = { vault = it }, label = { Text("Vault") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
                )
            }
            OutlinedButton(
                onClick = { if ((key != null)) pickFolder = true },
                enabled = (key != null),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(folder.ifBlank { "Vault root" }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            if (!(key != null)) {
                OutlinedTextField(
                    value = folder, onValueChange = { folder = it }, label = { Text("Folder") },
                    supportingText = { Text("Add this vault's folder in Settings to search your folders.") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
                )
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Note name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
            )

            // ---- everything else
            if (details.isNotEmpty()) {
                Surface(
                    onClick = { detailsOpen = !detailsOpen },
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (detailsOpen) "Hide details" else "Details, ${details.size} filled in for you",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        )
                        if (detailsOpen) details.forEach { p ->
                            OutlinedTextField(
                                value = p.value,
                                onValueChange = { overrides[p.property.id] = it },
                                label = { Text(p.property.name + if (p.type != "text") "  ·  ${p.type}" else "") },
                                modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4, shape = RoundedCornerShape(18.dp),
                            )
                        }
                    }
                }
            }

            Surface(
                onClick = { previewOpen = !previewOpen },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().animateContentSize(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (previewOpen) "Hide note" else "Show the note", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        note.markdown,
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp,
                        maxLines = if (previewOpen) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val primary = settings.saveBehavior
            Button(onClick = { go(primary) }, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) {
                Icon(iconFor(primary), null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(labelFor(primary))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("addToObsidian", "saveFile", "copyToClipboard", "share").filter { it != primary }.forEach { k ->
                    OutlinedButton(onClick = { go(k) }) {
                        Icon(iconFor(k), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(labelFor(k))
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }

        if (pickFolder) {
            OmniSheet(
                vault = key ?: vault, title = "Choose a folder",
                kinds = setOf(OmniKind.Folder, OmniKind.Create),
                onPick = { item ->
                    folder = if (item.kind == OmniKind.Create) item.value.trim('/') else item.value
                    pickFolder = false
                },
                onDismiss = { pickFolder = false },
            )
        }
        if (pickTags) {
            OmniSheet(
                vault = key ?: vault, title = "Add tags",
                kinds = setOf(OmniKind.Tag, OmniKind.Create),
                multi = true, selected = tags.toSet(),
                onPick = { item ->
                    val t = item.value.removePrefix("#")
                    if (t.isNotBlank()) { if (t in tags) tags.remove(t) else tags.add(t) }
                },
                onDismiss = { pickTags = false },
            )
        }
        if (pickNote != null) {
            OmniSheet(
                vault = key ?: vault, title = "Update which note?",
                kinds = setOf(OmniKind.Note),
                onPick = { item ->
                    val ref = index?.notes?.firstOrNull { it.path == item.value }
                    pickNote = null
                    if (ref != null) update(ref)
                },
                onDismiss = { pickNote = null },
            )
        }
        val kind = asking
        if (kind != null && existing != null) {
            AlertDialog(
                onDismissRequest = { asking = null },
                title = { Text("This video is already in your vault") },
                text = { Text("${existing.path}\n\nUpdate it, or make a new note?") },
                confirmButton = {
                    TextButton(onClick = { asking = null; update(existing) }) { Text("Update it") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { asking = null; pickNote = "pick" }) { Text("Other note") }
                        TextButton(onClick = { asking = null; send(kind) }) { Text("New note") }
                    }
                },
            )
        }
    }
}

private fun app.marginalia.obsidian.BuiltProperty.isTags() =
    type == "multitext" && property.name.equals("tags", ignoreCase = true)

private fun labelFor(kind: String) = when (kind) {
    "saveFile" -> "Save file"
    "copyToClipboard" -> "Copy"
    "share" -> "Share"
    else -> "Add to Obsidian"
}

private fun iconFor(kind: String) = when (kind) {
    "saveFile" -> Icons.Rounded.Save
    "copyToClipboard" -> Icons.Rounded.ContentCopy
    "share" -> Icons.Rounded.Share
    else -> Icons.Rounded.Upload
}
