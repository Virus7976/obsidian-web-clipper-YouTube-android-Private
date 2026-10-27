package app.marginalia.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import app.marginalia.data.SavedVideo
import app.marginalia.obsidian.NoteBuilder
import app.marginalia.obsidian.SendResult
import app.marginalia.obsidian.Sender
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
        var templateId by remember { mutableStateOf(settings.selectedTemplateId.takeIf { id -> templates.any { it.id == id } } ?: templates.first().id) }
        val template = templates.first { it.id == templateId }

        val base = remember(templateId) { NoteBuilder.build(template, video, lines, settings) }
        var name by remember(templateId) { mutableStateOf(base.name) }
        var folder by remember(templateId) { mutableStateOf(base.folder) }
        var vault by remember(templateId) { mutableStateOf(base.vault) }
        val overrides = remember(templateId) { mutableStateMapOf<String, String>() }
        var previewOpen by remember { mutableStateOf(false) }

        val note = NoteBuilder.build(template, video, lines, settings, overrides.toMap(), name, folder, vault)

        fun send(kind: String) {
            app.settings.update { it.copy(selectedTemplateId = templateId, vault = vault.ifBlank { it.vault }, vaults = if (vault.isNotBlank()) (it.vaults + vault).distinct() else it.vaults) }
            scope.launch {
                val r: SendResult = when (kind) {
                    "saveFile" -> withContext(Dispatchers.IO) { Sender.saveFile(ctx, note, settings.vaultFolderUri) }
                    "copyToClipboard" -> Sender.copyToClipboard(ctx, note)
                    "share" -> Sender.share(ctx, note)
                    else -> Sender.addToObsidian(ctx, note)
                }
                onDone(r.message, r.ok)
            }
        }

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Clip this video", style = MaterialTheme.typography.headlineSmall)

            if (templates.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach { t ->
                        FilterChip(selected = t.id == templateId, onClick = { templateId = t.id }, label = { Text(t.name) })
                    }
                }
            }

            if (settings.vaults.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.vaults.forEach { v -> FilterChip(selected = v == vault, onClick = { vault = v }, label = { Text(v) }) }
                }
            } else {
                OutlinedTextField(
                    value = vault, onValueChange = { vault = it }, label = { Text("Vault") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }

            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Note name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                value = folder, onValueChange = { folder = it }, label = { Text("Folder") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )

            if (note.properties.isNotEmpty()) {
                Text("Properties", style = MaterialTheme.typography.titleMedium)
                note.properties.forEach { p ->
                    OutlinedTextField(
                        value = p.value,
                        onValueChange = { overrides[p.property.id] = it },
                        label = { Text(p.property.name + if (p.type != "text") "  ·  ${p.type}" else "") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 4,
                    )
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
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = if (previewOpen) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val primary = settings.saveBehavior
            Button(onClick = { send(primary) }, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) {
                Icon(iconFor(primary), null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(labelFor(primary))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("addToObsidian", "saveFile", "copyToClipboard", "share").filter { it != primary }.forEach { k ->
                    OutlinedButton(onClick = { send(k) }) {
                        Icon(iconFor(k), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(labelFor(k))
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

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
