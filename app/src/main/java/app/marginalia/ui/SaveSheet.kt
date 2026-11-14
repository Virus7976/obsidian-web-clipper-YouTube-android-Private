package app.marginalia.ui

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import app.obsi.core.data.NoteName
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
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
import app.obsi.core.data.Line
import app.obsi.core.data.NoteRef
import app.obsi.core.data.ClipMemo
import app.obsi.core.data.OmniKind
import app.marginalia.data.SavedVideo
import app.marginalia.obsidian.NoteBuilder
import app.obsi.core.obsidian.SendResult
import app.obsi.core.obsidian.Sender
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

    SmoothSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        val templates = settings.templates
        if (templates.isEmpty()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No templates", style = MaterialTheme.typography.titleLarge)
                Text("Add one in Settings, or import your Web Clipper settings file.", style = MaterialTheme.typography.bodyMedium)
            }
            return@SmoothSheet
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
        var askLink by remember { mutableStateOf(false) }
        var sendAfterLink by remember { mutableStateOf(false) }
        var askFor by remember { mutableStateOf<String?>(null) }
        var askDone by remember { mutableStateOf<String?>(null) }
        var askEmpty by remember { mutableStateOf<Set<String>>(emptySet()) }
        val vars = remember(templateId) { NoteBuilder.variables(video, lines, settings) }

        // Read the vault in the background so the existing-note check and the pickers are fresh.
        val key = settings.linkedVault(vault)
        androidx.compose.runtime.LaunchedEffect(key) { key?.let { app.vaults.refresh(it) } }
        val index = key?.let { indexes[it] }
        val existing: NoteRef? = remember(index, video.meta.url) { index?.noteForSource(video.meta.url) }

        // The date_subject_description_v# name, when it is switched on in Settings > Obsidian.
        val schema = settings.nameSchema
        var subject by remember { mutableStateOf(settings.nameSubject.ifBlank { "clipping" }) }
        var desc by remember { mutableStateOf(video.meta.title) }
        var versionPick by remember { mutableStateOf<Int?>(null) }
        val today = remember { java.time.LocalDate.now() }
        val namePrefix = NoteName.prefix(today, subject, desc)
        val autoVersion = remember(index, folder, namePrefix) {
            val dir = folder.trim('/')
            NoteName.nextVersion(
                index?.notes.orEmpty().filter { it.path.substringBeforeLast('/', "") == dir }.map { it.name }, namePrefix,
            )
        }
        val version = versionPick ?: autoVersion
        val finalName = if (schema) NoteName.build(today, subject, desc, version) else name

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
            finalName, folder, vault,
        )
        // "Yours to fill" is decided by the template, not by what is typed now. Otherwise the box
        // jumps into Details after the first letter and the text is lost.
        val askIds = base.properties.filter { it.property.ask }.map { it.property.id }.toSet()
        val mineIds = base.properties.filter { !it.isTags() && it.value.isBlank() }.map { it.property.id }.toSet() + askIds
        val mine = note.properties.filter { it.property.id in mineIds && it.property.id !in askIds }
        val details = note.properties.filter { !it.isTags() && it.property.id !in mineIds }
        val folderUri = key?.let { settings.vaultFolders[it] } ?: settings.vaultFolderUri

        fun remember() {
            app.library.edit(video.meta.id) {
                it.copy(lastClip = ClipMemo(templateId, finalName, folder, vault, overrides.toMap(), tags.toList()))
            }
            app.settings.update {
                it.copy(
                    recentSubjects = if (schema) (listOf(subject.trim()) + it.recentSubjects).filter { s -> s.isNotBlank() }.distinct().take(20) else it.recentSubjects,
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
                    // Obsidian drops an obsidian://new link when it must load the vault first, so
                    // the note is lost. With the vault folder linked, write the file here. Then open only the
                    // vault: opening the new note by name makes Obsidian create an empty note over it.
                    else -> if (key != null && folderUri.isNotBlank()) {
                        val saved = withContext(Dispatchers.IO) { Sender.saveFile(ctx, note, folderUri) }
                        if (saved.ok) {
                            app.vaults.refresh(key, force = true)
                            Sender.openVault(ctx, note.vault)
                            SendResult(true, "Saved to ${saved.path.ifBlank { note.path }}.md", saved.path)
                        } else saved
                    } else Sender.addToObsidian(ctx, note)
                }
                if (r.ok && (kind == "addToObsidian" || kind == "saveFile")) {
                    app.library.edit(video.meta.id) { it.copy(noteVault = note.vault, notePath = r.path.ifBlank { note.path }) }
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
                    app.vaults.refresh(key ?: vault, force = true)
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
        fun finish(kind: String) {
            when {
                // Without the vault folder, the note can only go by link, and Obsidian often drops it.
                kind == "addToObsidian" && key == null -> askLink = true
                (kind == "addToObsidian" || kind == "saveFile") && existing != null -> asking = kind
                else -> send(kind)
            }
        }
        val linkPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val name = vault.ifBlank { android.net.Uri.decode(uri.toString()).substringAfterLast(':').substringAfterLast('/').ifBlank { "Vault" } }
            app.settings.update {
                it.copy(
                    vaults = (it.vaults + name).distinct(),
                    vault = it.vault.ifBlank { name },
                    vaultFolders = it.vaultFolders + (name to uri.toString()),
                    vaultFolderUri = uri.toString(),
                )
            }
            vault = name
            sendAfterLink = true
        }
        // Save once the new folder is in the settings, so the note goes straight into it.
        androidx.compose.runtime.LaunchedEffect(key, sendAfterLink) {
            if (sendAfterLink && key != null) { sendAfterLink = false; send("addToObsidian") }
        }

        fun setValue(p: app.obsi.core.obsidian.BuiltProperty, v: String) {
            if (p.isTags()) {
                tags.clear()
                tags.addAll(NoteBuilder.splitList(v).map { it.removePrefix("#").trim('"') })
            } else overrides[p.property.id] = v
        }

        // The boxes are on the sheet. The popup is only for one still empty at save time.
        fun go(kind: String) {
            // Every field you fill by hand, and the tags, is asked for while still empty.
            // A note saved without them is hard to find again.
            askEmpty = note.properties.filter { (it.property.id in mineIds || it.isTags()) && it.value.isBlank() }.map { it.property.id }.toSet()
            if (askEmpty.isNotEmpty()) askFor = kind else finish(kind)
        }

        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
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
            note.properties.filter { it.property.id in askIds }.forEach { p ->
                TypedField(p.property.name, p.type, p.value, { setValue(p, it) }, vars, key, insert = { f -> vars[f.key].orEmpty() })
            }
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

            if (tagsProp != null && tagsProp.property.id !in askIds) {
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
            if (key != null) OutlinedButton(
                onClick = { pickFolder = true },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(folder.ifBlank { "Vault root" }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else {
                OutlinedTextField(
                    value = folder, onValueChange = { folder = it }, label = { Text("Folder") },
                    supportingText = { Text("Add this vault's folder in Settings to search your folders.") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
                )
            }
            if (schema) {
                OutlinedTextField(
                    value = subject, onValueChange = { subject = it }, label = { Text("Subject") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
                )
                val subjects = suggestFor((settings.recentSubjects + settings.nameSubject).filter { it.isNotBlank() }.distinctBy { it.lowercase() }, subject)
                if (subjects.isNotEmpty()) Row(
                    Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    subjects.forEach { s -> androidx.compose.material3.FilterChip(selected = false, onClick = { subject = s }, label = { Text(s) }) }
                }
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it }, label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Version", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { versionPick = (version - 1).coerceAtLeast(1) }) { Text("−") }
                    Text("v$version", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { versionPick = version + 1 }) { Text("+") }
                }
                Text(finalName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            } else OutlinedTextField(
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
                            if (detailsOpen) "Hide details"
                            else "Details: " + details.take(3).joinToString(", ") { it.property.name } + if (details.size > 3) " and ${details.size - 3} more" else "",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        )
                        if (detailsOpen) details.forEach { p ->
                            TypedField(p.property.name, p.type, p.value, { setValue(p, it) }, vars, key, insert = { f -> vars[f.key].orEmpty() }, dataButton = false)
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("addToObsidian", "saveFile", "copyToClipboard", "share").filter { it != primary }.forEach { k ->
                    OutlinedButton(onClick = { go(k) }) {
                        Icon(iconFor(k), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(labelFor(k))
                    }
                }
            }
        }
        // Always in reach, however long the form is. Swipe the sheet down to cancel.
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
            val primary = settings.saveBehavior
            Button(
                onClick = { go(primary) },
                modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).height(54.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(iconFor(primary), null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(labelFor(primary))
            }
        }

        askFor?.let { kind ->
            AskSheet(
                fields = note.properties.filter { it.property.id in askEmpty },
                action = labelFor(kind), vars = vars, vault = key,
                onValue = { p, v -> setValue(p, v) },
                onSave = { askDone = kind; askFor = null },
                onDismiss = { askFor = null },
            )
        }
        // After the next composition, so the note holds what was typed last.
        askDone?.let { kind ->
            androidx.compose.runtime.LaunchedEffect(kind) { askDone = null; finish(kind) }
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
        if (askLink) {
            AlertDialog(
                onDismissRequest = { askLink = false },
                title = { Text("Link your vault folder") },
                text = { Text("Pick the folder for ${vault.ifBlank { "your vault" }} once. Then ObsiTube saves notes straight into it.") },
                confirmButton = {
                    TextButton(onClick = { askLink = false; linkPicker.launch(null) }) { Text("Pick folder") }
                },
                dismissButton = {
                    TextButton(onClick = { askLink = false; send("addToObsidian") }) { Text("Not now") }
                },
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

private fun app.obsi.core.obsidian.BuiltProperty.isTags() =
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
