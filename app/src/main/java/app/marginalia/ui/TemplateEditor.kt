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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marginalia.data.ClipTemplate
import app.marginalia.data.TemplateProperty
import java.util.UUID

private val propertyTypes = listOf("text", "multitext", "date", "datetime", "number", "checkbox")
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
            Text("Properties", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.titleLarge)
        }
        itemsIndexed(t.properties, key = { _, p -> p.id }) { _, p ->
            Surface(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(p.name, { v -> editProp(p.id) { it.copy(name = v) } }, Modifier.weight(1f), label = { Text("Name") }, singleLine = true)
                        IconButton(onClick = { edit { it.copy(properties = it.properties.filterNot { x -> x.id == p.id }) } }) {
                            Icon(Icons.Rounded.Close, "Remove property")
                        }
                    }
                    OutlinedTextField(p.value, { v -> editProp(p.id) { it.copy(value = v) } }, Modifier.fillMaxWidth(), label = { Text("Value") })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val current = p.type.ifBlank { s.propertyTypes[p.name] ?: "text" }
                        propertyTypes.forEach { ty ->
                            FilterChip(selected = current == ty, onClick = { editProp(p.id) { it.copy(type = ty) } }, label = { Text(ty) })
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = {
                    edit { it.copy(properties = it.properties + TemplateProperty(UUID.randomUUID().toString(), "new property", "", "text")) }
                }) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add property")
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
                        "{{title}} {{url}} {{author}} {{channel}} {{published}} {{date}} {{description}} {{fullDescription}} " +
                            "{{content}} {{highlights}} {{transcript}} {{image}} {{duration}} {{videoId}} {{embed}} {{site}}",
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
}
