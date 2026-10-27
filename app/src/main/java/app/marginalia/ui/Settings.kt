package app.marginalia.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marginalia.data.ClipTemplate
import app.marginalia.data.TemplateProperty
import app.marginalia.obsidian.ClipperImport
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SettingsScreen(onBack: () -> Unit, onEditTemplate: (String) -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by app.settings.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    fun set(block: (app.marginalia.data.AppSettings) -> app.marginalia.data.AppSettings) = app.settings.update(block)
    fun say(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                set { ClipperImport.apply(text, it) }
            }.onSuccess { say("Imported your clipper settings.") }.onFailure { say("That file is not a clipper settings export.") }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            set { it.copy(vaultFolderUri = uri.toString()) }
            say("Vault folder set.")
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                }
            }

            // ---------- look ----------
            item { Group("Look") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                    ) {
                        items(Palettes.size) { i ->
                            val p = Palettes[i]
                            PaletteCard(p, selected = s.themePreset == p.id) { set { it.copy(themePreset = p.id, accentOverride = 0L) } }
                        }
                    }
                    Label("Appearance")
                    Choices(listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark"), s.appearance) { v -> set { it.copy(appearance = v) } }
                    Label("Accent")
                    Swatches(
                        colors = listOf(0xFFFF6A47, 0xFFF2A93B, 0xFFB9D84A, 0xFF3DDC97, 0xFFFF5C93, 0xFFE2554F),
                        selected = s.accentOverride,
                        allowReset = true,
                        onPick = { c -> set { it.copy(accentOverride = c) } },
                    )
                    Label("Motion")
                    Choices(listOf("calm" to "Calm", "lively" to "Lively", "playful" to "Playful"), s.motion) { v -> set { it.copy(motion = v) } }
                    SliderRow("Corner roundness", s.roundness, 0.4f..1.6f, "%.1fx".format(s.roundness)) { v -> set { it.copy(roundness = v) } }
                }
            } }

            // ---------- reading ----------
            item { Group("Reading") {
                Label("Font")
                Choices(listOf("serif" to "Serif", "sans" to "Sans", "system" to "System"), s.readerFont) { v -> set { it.copy(readerFont = v) } }
                SliderRow("Text size", s.readerFontSize, 14f..30f, "${s.readerFontSize.toInt()} sp") { v -> set { it.copy(readerFontSize = v) } }
                SliderRow("Line height", s.lineHeight, 1.2f..2.2f, "%.2f".format(s.lineHeight)) { v -> set { it.copy(lineHeight = v) } }
                SliderRow("Reading width", s.maxWidth, 24f..60f, "${s.maxWidth.toInt()}") { v -> set { it.copy(maxWidth = v) } }
                Label("Line length")
                Choices(listOf("0" to "Short", "1" to "Medium", "2" to "Long"), s.lineLength.toString()) { v -> set { it.copy(lineLength = v.toInt()) } }
                SwitchRow("Pin the player", "Keep the video at the top while you read.", s.pinPlayer) { v -> set { it.copy(pinPlayer = v) } }
                SwitchRow("Mark the current line", "A red bar follows the spoken line.", s.highlightActiveLine) { v -> set { it.copy(highlightActiveLine = v) } }
                SwitchRow("Follow along", "Scroll the transcript as the video plays.", s.autoScroll) { v -> set { it.copy(autoScroll = v) } }
                SwitchRow("Show timestamps", null, s.showTimestamps) { v -> set { it.copy(showTimestamps = v) } }
            } }

            // ---------- highlights ----------
            item { Group("Highlights") {
                SwitchRow("Highlighter", "Hold a line, drag to take more.", s.highlighterEnabled) { v -> set { it.copy(highlighterEnabled = v) } }
                SwitchRow("Always show highlights", null, s.alwaysShowHighlights) { v -> set { it.copy(alwaysShowHighlights = v) } }
                SwitchRow(
                    "Highlights replace the content",
                    "When on, the note body is your highlights. When off, it is the transcript.",
                    s.highlightBehavior == "replace-content",
                ) { v -> set { it.copy(highlightBehavior = if (v) "replace-content" else "keep-content") } }
                Label("Marker colour")
                Swatches(
                    colors = listOf(0xFFF2C14E, 0xFFFF8A65, 0xFF9CCC65, 0xFF4DD0E1, 0xFFF48FB1, 0xFFFFFFFF),
                    selected = s.highlightColor,
                    allowReset = false,
                    onPick = { c -> set { it.copy(highlightColor = c) } },
                )
                Label("How a highlight is written")
                OutlinedTextField(
                    value = s.highlightFormat,
                    onValueChange = { v -> set { it.copy(highlightFormat = v) } },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Use {text}, {time} and {link}") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            } }

            // ---------- obsidian ----------
            item { Group("Obsidian") {
                Label("When I tap Clip")
                Choices(
                    listOf("addToObsidian" to "Open Obsidian", "saveFile" to "Save file", "copyToClipboard" to "Copy", "share" to "Share"),
                    s.saveBehavior,
                ) { v -> set { it.copy(saveBehavior = v) } }

                Label("Vaults")
                s.vaults.forEach { v ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = v == s.vault, onClick = { set { it.copy(vault = v) } }, label = { Text(v) })
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { set { st -> st.copy(vaults = st.vaults - v, vault = if (st.vault == v) st.vaults.firstOrNull { it != v }.orEmpty() else st.vault) } }) {
                            Icon(Icons.Rounded.Close, "Remove vault")
                        }
                    }
                }
                var newVault by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newVault, onValueChange = { newVault = it }, singleLine = true,
                        label = { Text("Vault name") }, modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = {
                        val n = newVault.trim()
                        if (n.isNotEmpty()) {
                            set { it.copy(vaults = (it.vaults + n).distinct(), vault = it.vault.ifBlank { n }) }
                            newVault = ""
                        }
                    }, shapes = ButtonDefaults.shapes()) { Icon(Icons.Rounded.Add, "Add vault") }
                }

                Label("Vault folder, for Save file")
                Text(
                    if (s.vaultFolderUri.isBlank()) "Not set. Pick the folder that holds your vault." else android.net.Uri.decode(s.vaultFolderUri).substringAfterLast(':'),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                FilledTonalButton(onClick = { folderPicker.launch(null) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Choose folder")
                }

                Label("Templates")
                s.templates.forEach { t ->
                    Surface(
                        onClick = { onEditTemplate(t.id) },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${t.path.ifBlank { "Vault root" }}  ·  ${t.properties.size} properties",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (t.id == s.selectedTemplateId) Icon(Icons.Rounded.Check, "Default", tint = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Rounded.ChevronRight, null)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        val id = UUID.randomUUID().toString().take(12)
                        set { it.copy(templates = it.templates + ClipTemplate(id = id, name = "New template", properties = listOf(
                            TemplateProperty(UUID.randomUUID().toString(), "title", "{{title}}"),
                            TemplateProperty(UUID.randomUUID().toString(), "source", "{{url}}"),
                        )), selectedTemplateId = it.selectedTemplateId.ifBlank { id }) }
                        onEditTemplate(id)
                    }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("New template")
                    }
                    FilledTonalButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Import settings")
                    }
                }
                TextButton(onClick = {
                    runCatching {
                        val text = ctx.assets.open("default_clipper_settings.json").bufferedReader().use { it.readText() }
                        set { ClipperImport.apply(text, it.copy(templates = emptyList())) }
                    }.onSuccess { say("Templates reset to the bundled ones.") }
                }) { Text("Reset templates to the bundled set") }
            } }

            // ---------- playback ----------
            item { Group("Playback and captions") {
                SwitchRow("Keep playing in the background", "Audio continues with the screen off, with a notification.", s.backgroundPlay) { v -> set { it.copy(backgroundPlay = v) } }
                SwitchRow("Keep the screen on", "Only while a video is playing.", s.keepScreenOn) { v -> set { it.copy(keepScreenOn = v) } }
                OutlinedTextField(
                    value = s.captionLang,
                    onValueChange = { v -> set { it.copy(captionLang = v.trim()) } },
                    label = { Text("Caption language code") },
                    placeholder = { Text("Blank uses your phone language") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } }

            // ---------- advanced ----------
            item { Group("Advanced") {
                Text(
                    "Marginalia reads captions the way YouTube's own phone apps do. If captions ever stop loading, a newer client version below usually fixes it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = s.androidClientVersion, onValueChange = { v -> set { it.copy(androidClientVersion = v.trim()) } },
                    label = { Text("Android client version") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.iosClientVersion, onValueChange = { v -> set { it.copy(iosClientVersion = v.trim()) } },
                    label = { Text("iOS client version") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            } }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

// ---------- building blocks ----------

@Composable
private fun Group(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, Modifier.padding(start = 6.dp, bottom = 8.dp, top = 12.dp), style = MaterialTheme.typography.titleLarge)
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(display, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun Choices(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            FilterChip(selected = key == selected, onClick = { onSelect(key) }, label = { Text(label) })
        }
    }
}

@Composable
private fun Swatches(colors: List<Long>, selected: Long, allowReset: Boolean, onPick: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (allowReset) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .border(2.dp, if (selected == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onPick(0L) },
                contentAlignment = Alignment.Center,
            ) { Text("Auto", fontSize = 10.sp, style = MaterialTheme.typography.labelSmall) }
        }
        colors.forEach { c ->
            val on = selected == c
            val scale by animateFloatAsState(if (on) 1.12f else 1f, LocalSpring.current.spec(), label = "swatch")
            Box(
                Modifier.size(40.dp).scale(scale).clip(CircleShape).background(Color(c.toInt()))
                    .border(if (on) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun PaletteCard(p: Palette, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, LocalSpring.current.spec(), label = "palette")
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "pborder")
    Column(
        Modifier.width(164.dp).scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(p.bgDark)
            .border(BorderStroke(2.5.dp, border), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(p.accent))
            Box(Modifier.height(6.dp).width(46.dp).clip(CircleShape).background(Color(0xFFF2C14E)))
        }
        Text("Aa", fontFamily = SerifFont, fontSize = 30.sp, color = p.textDark)
        Text(p.label, style = MaterialTheme.typography.titleSmall, color = p.textDark)
        Text(p.blurb, style = MaterialTheme.typography.bodySmall, color = p.textDark.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
