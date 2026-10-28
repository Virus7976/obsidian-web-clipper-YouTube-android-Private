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
import androidx.compose.material.icons.rounded.Search
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
import app.marginalia.obsidian.ClipperExport
import app.marginalia.update.AdbInstaller
import app.marginalia.update.ShizukuInstaller
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
    var pickFor by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    // every word typed must appear in the section's keyword list
    fun hit(words: String) = query.isBlank() ||
        query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.all { words.contains(it) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val name = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "font.ttf"
                val dir = java.io.File(ctx.filesDir, "fonts").apply { deleteRecursively(); mkdirs() }
                val file = java.io.File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                ctx.contentResolver.openInputStream(uri)!!.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
                // a file that is not a font would silently fall back to the default, so check it loads
                val tf = android.graphics.Typeface.createFromFile(file)
                require(tf != null && file.length() > 1000)
                set { it.copy(customFontPath = file.absolutePath, customFontName = name.substringBeforeLast('.'), readerFont = "custom") }
            }.onSuccess { say("Font added.") }.onFailure { say("That file is not a font. Pick a .ttf or .otf file.") }
        }
    }
    val exporter =rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(ClipperExport.build(app.settings.value)) }
            }.onSuccess { say("Saved your presets.") }.onFailure { say("Could not save the file.") }
        }
    }
    val folderPicker =rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            // A vault name with no folder yet (for example the one in your template) gets this folder.
            val cur = app.settings.value
            val name = pickFor
                ?: cur.vaults.firstOrNull { !cur.vaultFolders.containsKey(it) }
                ?: android.net.Uri.decode(uri.toString()).substringAfterLast(':').substringAfterLast('/').ifBlank { "Vault" }
            pickFor = null
            set { it.copy(
                vaults = (it.vaults + name).distinct(),
                vault = it.vault.ifBlank { name },
                vaultFolders = it.vaultFolders + (name to uri.toString()),
                vaultFolderUri = uri.toString(),
            ) }
            app.vaults.refresh(name, force = true)
            say("Added $name. Reading your notes now.")
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
            item("search") {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    singleLine = true,
                    placeholder = { Text("Search settings") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                    shape = RoundedCornerShape(50),
                )
                if (query.isNotBlank() && !listOf(
                        SettingsKeys.look, SettingsKeys.reading, SettingsKeys.highlights, SettingsKeys.updates,
                        SettingsKeys.obsidian, SettingsKeys.playback, SettingsKeys.advanced,
                    ).any { hit(it) }
                ) {
                    Text("Nothing matches \"$query\"", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---------- look ----------
            if (hit(SettingsKeys.look)) item { Group("Look") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                    ) {
                        val all = allPalettes(s.customPalettes)
                        items(all.size) { i ->
                            val p = all[i]
                            PaletteCard(p, selected = s.themePreset == p.id) { set { it.copy(themePreset = p.id, accentOverride = 0L) } }
                        }
                    }
                    var making by remember { mutableStateOf(false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { making = true }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Make a palette")
                        }
                        if (s.customPalettes.any { it.id == s.themePreset }) {
                            TextButton(onClick = {
                                set { st -> st.copy(customPalettes = st.customPalettes.filterNot { it.id == st.themePreset }, themePreset = "ink") }
                            }) { Text("Delete this palette") }
                        }
                    }
                    if (making) PaletteMaker(
                        onDismiss = { making = false },
                        onSave = { cp -> set { it.copy(customPalettes = it.customPalettes + cp, themePreset = cp.id, accentOverride = 0L) }; making = false },
                    )
                    Label("Appearance")
                    Choices(listOf("auto" to "Auto") + Modes, s.appearance) { v -> set { it.copy(appearance = v) } }
                    Text(
                        "Dusk is a softer dark. OLED is true black and saves battery on OLED screens.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Label("Accent")
                    Swatches(
                        colors = listOf(0xFFFF6A47, 0xFFF2A93B, 0xFFB9D84A, 0xFF3DDC97, 0xFFFF5C93, 0xFFE2554F),
                        selected = s.accentOverride,
                        allowReset = true,
                        onPick = { c -> set { it.copy(accentOverride = c) } },
                    )
                    Label("Home tabs")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeTabs.forEach { (id, label) ->
                            val on = id in s.homeTabs
                            FilterChip(
                                selected = on,
                                onClick = {
                                    // keep at least one tab
                                    val next = if (on) s.homeTabs - id else HomeTabs.keys.filter { it in s.homeTabs || it == id }
                                    if (next.isNotEmpty()) set { it.copy(homeTabs = next) }
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    Label("Library layout")
                    Choices(LibraryLayouts, s.libraryLayout) { v -> set { it.copy(libraryLayout = v) } }
                    Label("Pull to refresh")
                    Choices(PixelChars, s.pixelChar) { v -> set { it.copy(pixelChar = v) } }
                    Label("Motion")
                    Choices(listOf("calm" to "Calm", "lively" to "Lively", "playful" to "Playful"), s.motion) { v -> set { it.copy(motion = v) } }
                    SliderRow("Corner roundness", s.roundness, 0.4f..1.6f, "%.1fx".format(s.roundness)) { v -> set { it.copy(roundness = v) } }
                }
            } }

            // ---------- reading ----------
            if (hit(SettingsKeys.reading)) item { Group("Reading") {
                Label("Font")
                Choices(
                    listOf("serif" to "Serif", "sans" to "Sans", "system" to "System") + (if (s.customFontPath.isNotBlank()) listOf("custom" to s.customFontName.ifBlank { "Mine" }) else emptyList()),
                    s.readerFont,
                ) { v -> set { it.copy(readerFont = v) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*")) }, shapes = ButtonDefaults.shapes()) {
                        Text(if (s.customFontPath.isBlank()) "Use my own font" else "Change my font")
                    }
                    if (s.customFontPath.isNotBlank()) TextButton(onClick = {
                        set { it.copy(customFontPath = "", customFontName = "", uiFontCustom = false, readerFont = if (it.readerFont == "custom") "serif" else it.readerFont) }
                    }) { Text("Remove") }
                }
                if (s.customFontPath.isNotBlank()) {
                    SwitchRow("Use it everywhere", "Titles and menus too, not only the transcript.", s.uiFontCustom) { v -> set { it.copy(uiFontCustom = v) } }
                }
                SliderRow("Text size", s.readerFontSize, 14f..30f, "${s.readerFontSize.toInt()} sp") { v -> set { it.copy(readerFontSize = v) } }
                SliderRow("Line height", s.lineHeight, 1.2f..2.2f, "%.2f".format(s.lineHeight)) { v -> set { it.copy(lineHeight = v) } }
                SliderRow("Reading width", s.maxWidth, 24f..60f, "${s.maxWidth.toInt()}") { v -> set { it.copy(maxWidth = v) } }
                Label("Line length")
                Choices(listOf("0" to "Short", "1" to "Medium", "2" to "Long"), s.lineLength.toString()) { v -> set { it.copy(lineLength = v.toInt()) } }
                SwitchRow("Show the video", "Off means listen only. You can switch it on in the reader.", s.pinPlayer) { v -> set { it.copy(pinPlayer = v) } }
                SwitchRow("Mark the current line", "A red bar follows the spoken line.", s.highlightActiveLine) { v -> set { it.copy(highlightActiveLine = v) } }
                SwitchRow("Follow along", "Scroll the transcript as the video plays.", s.autoScroll) { v -> set { it.copy(autoScroll = v) } }
                SwitchRow("Show timestamps", null, s.showTimestamps) { v -> set { it.copy(showTimestamps = v) } }
            } }

            // ---------- highlights ----------
            if (hit(SettingsKeys.highlights)) item { Group("Highlights") {
                SwitchRow("Highlighter", "Hold a line, drag to take more.", s.highlighterEnabled) { v -> set { it.copy(highlighterEnabled = v) } }
                SwitchRow("Always show highlights", null, s.alwaysShowHighlights) { v -> set { it.copy(alwaysShowHighlights = v) } }
                SwitchRow(
                    "Highlights replace the content",
                    "When on, the note body is your highlights. When off, it is the transcript.",
                    s.highlightBehavior == "replace-content",
                ) { v -> set { it.copy(highlightBehavior = if (v) "replace-content" else "keep-content") } }
                Label("Holding a word selects")
                Choices(listOf("word" to "Word", "sentence" to "Sentence", "paragraph" to "Paragraph"), s.holdSelects) { v -> set { it.copy(holdSelects = v) } }
                Text(
                    "Drag the two handles to take exactly the text you want. The toolbar also has Sentence and Paragraph buttons.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Label("In the note")
                Choices(listOf("obsidian" to "Obsidian highlight", "plain" to "Plain text"), s.highlightSyntax) { v -> set { it.copy(highlightSyntax = v) } }
                Text(
                    "Yellow is written as ==text==. Other colours use <mark style=\"background: …\">, the style the Highlightr plugin uses.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Label("Layout of each highlight")
                Choices(
                    listOf(
                        "> {text}\n> [{time}]({link})" to "Quote",
                        "> [!quote] [{time}]({link})\n> {text}" to "Callout",
                        "- {text} [{time}]({link})" to "Bullet",
                        "{text} [{time}]({link})" to "Inline",
                    ),
                    s.highlightFormat,
                ) { v -> set { it.copy(highlightFormat = v) } }
                OutlinedTextField(
                    value = s.highlightFormat,
                    onValueChange = { v -> set { it.copy(highlightFormat = v) } },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Use {text}, {time} and {link}") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            } }

            if (hit(SettingsKeys.updates)) item { UpdateGroup(s, ::set) }

            // ---------- obsidian ----------
            if (hit(SettingsKeys.obsidian)) item { Group("Obsidian") {
                Label("When I tap Clip")
                Choices(
                    listOf("addToObsidian" to "Open Obsidian", "saveFile" to "Save file", "copyToClipboard" to "Copy", "share" to "Share"),
                    s.saveBehavior,
                ) { v -> set { it.copy(saveBehavior = v) } }

                Label("Vaults")
                val indexes by app.vaults.indexes.collectAsState()
                val busy by app.vaults.busy.collectAsState()
                val problems by app.vaults.errors.collectAsState()
                s.vaults.forEach { v ->
                    val linked = s.vaultFolders.containsKey(v)
                    // a name with no folder of its own uses the only folder there is
                    val key = if (linked) v else s.linkedVault(v)
                    val idx = key?.let { indexes[it] }
                    val problem = key?.let { problems[it] }
                    val reading = key?.let { busy[it] }
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(selected = v == s.vault, onClick = { set { it.copy(vault = v) } }, label = { Text(v) })
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = {
                                    app.vaults.forget(v)
                                    set { st -> st.copy(
                                        vaults = st.vaults - v,
                                        vaultFolders = st.vaultFolders - v,
                                        vault = if (st.vault == v) st.vaults.firstOrNull { it != v }.orEmpty() else st.vault,
                                    ) }
                                }) { Icon(Icons.Rounded.Close, "Remove vault") }
                            }
                            Text(
                                when {
                                    reading != null -> "Reading your notes… $reading so far"
                                    problem != null -> problem
                                    idx != null -> "${idx.notes.size} notes, ${idx.tags.size} tags, ${idx.folders.size} folders"
                                    key != null -> "Not read yet. Tap Reindex."
                                    else -> "No folder yet. Choose the folder that holds this vault."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (problem != null && reading == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (key != null) {
                                FilledTonalButton(onClick = { app.vaults.refresh(key, force = true) }, shapes = ButtonDefaults.shapes()) {
                                    Text(if (reading != null) "Start again" else "Reindex")
                                }
                            } else {
                                FilledTonalButton(onClick = { pickFor = v; folderPicker.launch(null) }, shapes = ButtonDefaults.shapes()) {
                                    Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Choose folder")
                                }
                            }
                        }
                    }
                }
                Text(
                    "Pick the folder that holds a vault. Marginalia reads your notes, folders and tags so you can pick them when you save a clip. It only reads. It writes only when you save a clip.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = { pickFor = null; folderPicker.launch(null) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add a vault folder")
                }
                var newVault by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newVault, onValueChange = { newVault = it }, singleLine = true,
                        label = { Text("Or add a name only") }, modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = {
                        val n = newVault.trim()
                        if (n.isNotEmpty()) {
                            set { it.copy(vaults = (it.vaults + n).distinct(), vault = it.vault.ifBlank { n }) }
                            newVault = ""
                        }
                    }, shapes = ButtonDefaults.shapes()) { Icon(Icons.Rounded.Add, "Add vault") }
                }

                Label("Added to every clip")
                Text(
                    "These go on every clip, whatever the template says. You can still remove one while you save.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.autoTags.forEach { t ->
                        androidx.compose.material3.InputChip(
                            selected = true, onClick = { set { it.copy(autoTags = it.autoTags - t) } },
                            label = { Text("#$t") },
                            trailingIcon = { Icon(Icons.Rounded.Close, "Remove $t", Modifier.size(16.dp)) },
                        )
                    }
                }
                var newTag by remember { mutableStateOf("") }
                fun addTag() {
                    val t = newTag.trim().removePrefix("#").replace(' ', '-')
                    if (t.isNotEmpty()) set { it.copy(autoTags = (it.autoTags + t).distinct()) }
                    newTag = ""
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTag, onValueChange = { newTag = it }, singleLine = true,
                        label = { Text("Add a tag") }, modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { addTag() }),
                    )
                    FilledTonalButton(onClick = ::addTag, shapes = ButtonDefaults.shapes()) { Icon(Icons.Rounded.Add, "Add tag") }
                }
                s.autoProps.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            Text(p.value.ifBlank { "(empty)" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { set { it.copy(autoProps = it.autoProps - p) } }) { Icon(Icons.Rounded.Delete, "Remove ${p.name}") }
                    }
                }
                var propName by remember { mutableStateOf("") }
                var propValue by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = propName, onValueChange = { propName = it }, singleLine = true,
                        label = { Text("Property") }, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = propValue, onValueChange = { propValue = it }, singleLine = true,
                        label = { Text("Value") }, modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = {
                        val n = propName.trim()
                        if (n.isNotEmpty() && !n.equals("tags", true)) {
                            set { it.copy(autoProps = it.autoProps.filterNot { x -> x.name.equals(n, true) } + TemplateProperty("auto:$n", n, propValue.trim(), if (propValue.contains("{{date")) "date" else "")) }
                            propName = ""; propValue = ""
                        }
                    }, shapes = ButtonDefaults.shapes()) { Icon(Icons.Rounded.Add, "Add property") }
                }
                androidx.compose.material3.TextButton(onClick = {
                    propName = if (propName.isBlank()) "date added" else propName
                    propValue = "{{date|date:\"YYYY-MM-DD\"}}"
                }) { Text("Fill in today's date") }

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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Import")
                    }
                    FilledTonalButton(onClick = { exporter.launch("obsidian-web-clipper-settings.json") }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Export")
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
            if (hit(SettingsKeys.playback)) item { Group("Playback and captions") {
                SwitchRow("Start playing when I open a video", null, s.autoPlay) { v -> set { it.copy(autoPlay = v) } }
                SwitchRow("Keep playing in the background","Audio continues with the screen off, with a notification.", s.backgroundPlay) { v -> set { it.copy(backgroundPlay = v) } }
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
            if (hit(SettingsKeys.advanced)) item { Group("Advanced") {
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

/** Every setting name in each section, so the search box can find a section by what is inside it. */
private object SettingsKeys {
    const val look = "look theme palette palettes make colour color appearance auto light dusk dark oled accent home tabs add library layout list grid compact pull refresh pixel ghost heart star invader motion calm lively playful corner roundness"
    const val reading = "reading font serif sans system custom own text size line height width length short medium long pin player mark current line follow along scroll timestamps"
    const val highlights = "highlights highlight highlighter always show replace content holding word selects sentence paragraph in the note obsidian plain text syntax layout each quote callout bullet inline"
    const val updates = "updates update version install installer android shizuku wireless adb pair pairing code port check on its own sources github repo token mirror skip release"
    const val obsidian = "obsidian when i tap clip open save file copy share vaults vault folder reindex index tags notes search always every automatic auto properties property templates template new import export presets clipper settings reset"
    const val playback = "playback captions start playing open a video background audio screen off notification keep screen on"
    const val advanced = "advanced client version android ios vr youtube innertube"
}

// ---------- palette maker ----------

@Composable
private fun PaletteMaker(onDismiss: () -> Unit, onSave: (app.marginalia.data.CustomPalette) -> Unit) {
    var name by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf(0xFF3DDC97L) }
    var hex by remember { mutableStateOf("3DDC97") }
    var style by remember { mutableStateOf("tonal") }
    val palette = makePalette("preview", name.ifBlank { "Mine" }, Color(seed.toInt()), style)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make a palette") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(20) }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Label("Pick one colour")
                Swatches(
                    colors = listOf(0xFFFF6A47, 0xFFF2A93B, 0xFFFFD84D, 0xFFB9D84A, 0xFF3DDC97, 0xFF4FC3F7, 0xFF8FB3D9, 0xFFE08AC8, 0xFFFF5C93, 0xFFE2554F),
                    selected = seed, allowReset = false,
                    onPick = { c -> seed = c; hex = "%06X".format(c and 0xFFFFFF) },
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { v ->
                        hex = v.filter { it.isLetterOrDigit() }.take(6).uppercase()
                        if (hex.length == 6) hex.toLongOrNull(16)?.let { seed = 0xFF000000L or it }
                    },
                    singleLine = true, label = { Text("Or type a hex code") }, modifier = Modifier.fillMaxWidth(),
                )
                Label("Style")
                Choices(PaletteStyles, style) { style = it }
                Label("How it looks")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Modes.forEach { (m, label) ->
                        val bg = backgroundFor(palette, m)
                        val fg = if (m == "light") palette.textLight else palette.textDark
                        val accent = if (m == "light") palette.accentLight else palette.accent
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(bg).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(accent))
                            Text("Aa", color = fg, fontSize = 18.sp)
                            Text(label, color = fg.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(app.marginalia.data.CustomPalette(UUID.randomUUID().toString().take(8), name.ifBlank { "Mine" }, seed, style))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------- updates ----------

@Composable
private fun UpdateGroup(s: app.marginalia.data.AppSettings, set: ((app.marginalia.data.AppSettings) -> app.marginalia.data.AppSettings) -> Unit) {
    val app = LocalApp.current
    val up = app.updates
    val state by up.state.collectAsState()
    var advanced by remember { mutableStateOf(false) }
    Group("Updates") {
        Text("Version ${up.currentVersion}", style = MaterialTheme.typography.titleSmall)
        when (val u = state) {
            is app.marginalia.update.UpdateState.Idle, is app.marginalia.update.UpdateState.UpToDate -> {
                Text(
                    if (u is app.marginalia.update.UpdateState.UpToDate) "You have the newest version." else "Not checked yet.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = { up.check() }, shapes = ButtonDefaults.shapes()) { Text("Check for updates") }
            }
            is app.marginalia.update.UpdateState.Checking -> Text("Checking…", style = MaterialTheme.typography.bodyMedium)
            is app.marginalia.update.UpdateState.Available -> {
                Text("Version ${u.info.version} is ready", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (u.info.notes.isNotBlank()) Text(u.info.notes.trim().take(600), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { up.installFlow(u.info) }, shapes = ButtonDefaults.shapes()) { Text("Install") }
                    TextButton(onClick = { up.ignore(u.info) }) { Text("Skip this version") }
                }
                Text(
                    "Tap once. Android asks for your OK, the download runs in the app, and the install follows.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is app.marginalia.update.UpdateState.Downloading -> {
                val frac = if (u.total > 0) (u.done.toFloat() / u.total).coerceIn(0f, 1f) else 0f
                Text(
                    "Downloading ${u.info.version}  ·  ${u.done / 1_000_000} of ${if (u.total > 0) "${u.total / 1_000_000}" else "?"} MB",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (u.total > 0) androidx.compose.material3.LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                else androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is app.marginalia.update.UpdateState.Ready -> {
                Text("Downloaded ${u.info.version}.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { up.install(u) }, shapes = ButtonDefaults.shapes()) { Text("Install now") }
            }
            is app.marginalia.update.UpdateState.Installing -> {
                Text("Installing ${u.info.version}… Approve the prompt if Android shows one.", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is app.marginalia.update.UpdateState.Failed -> {
                Text(u.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                FilledTonalButton(onClick = { up.check() }, shapes = ButtonDefaults.shapes()) { Text("Try again") }
            }
        }
        if (!up.canInstall()) {
            Text(
                "Android must allow Marginalia to install apps. You are asked once, when you tap Install.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val notice by up.notice.collectAsState()
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Label("Install with")
        Choices(listOf("system" to "Android", "shizuku" to "Shizuku", "adb" to "Wireless ADB"), s.installMethod) { v -> set { it.copy(installMethod = v) } }
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        if (s.installMethod == "shizuku") {
            val running = ShizukuInstaller.running()
            Text(
                when {
                    !running -> "Shizuku is not running. Start it, then come back."
                    ShizukuInstaller.allowed() -> "Shizuku is ready. Updates install without a prompt."
                    else -> "Shizuku is running. Allow Marginalia once."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (running && !ShizukuInstaller.allowed()) {
                FilledTonalButton(onClick = { ShizukuInstaller.askPermission() }, shapes = ButtonDefaults.shapes()) { Text("Allow in Shizuku") }
            }
        }
        if (s.installMethod == "adb") {
            val adb = remember { AdbInstaller.get(ctx) }
            var paired by remember { mutableStateOf(adb.paired()) }
            var port by remember { mutableStateOf("") }
            var code by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var msg by remember { mutableStateOf<String?>(null) }
            Text(
                if (paired) "Paired. Updates install on their own through wireless debugging."
                else "Set up once. Turn on Developer options and Wireless debugging. Tap Pair device with pairing code. Type the port and the code here. Keep that Android dialog open while you tap Pair.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!paired) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, singleLine = true,
                        label = { Text("Port") }, modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                        label = { Text("Code") }, modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    )
                }
                FilledTonalButton(
                    enabled = !busy && port.isNotEmpty() && code.length == 6,
                    onClick = {
                        busy = true; msg = null
                        scope.launch {
                            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { adb.pair(port.toInt(), code) }
                            busy = false
                            if (r == null) { paired = true; code = "" } else msg = r
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(if (busy) "Pairing…" else "Pair") }
                msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = { java.io.File(ctx.filesDir, "adb_paired").delete(); paired = false }) { Text("Pair again") }
            }
        }
        SwitchRow("Check on its own", "About twice a day, when you open the app.", s.autoCheckUpdates) { v -> set { it.copy(autoCheckUpdates = v) } }
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide sources" else "Update sources") }
        if (advanced) {
            OutlinedTextField(
                value = s.updateRepo, onValueChange = { v -> set { it.copy(updateRepo = v.trim()) } }, singleLine = true,
                label = { Text("GitHub repo (owner/name)") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.updateToken, onValueChange = { v -> set { it.copy(updateToken = v.trim()) } }, singleLine = true,
                label = { Text("Access token, only for a private repo") },
                supportingText = { Text("Needs read access to releases. It stays on this phone.") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.updateMirror, onValueChange = { v -> set { it.copy(updateMirror = v.trim()) } }, singleLine = true,
                label = { Text("Mirror JSON link, optional") },
                supportingText = { Text("Tried first. Shape: version, notes, url, size.") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
