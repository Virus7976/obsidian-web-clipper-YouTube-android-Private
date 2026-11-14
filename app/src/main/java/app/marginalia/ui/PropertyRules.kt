package app.marginalia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marginalia.data.Omni
import app.marginalia.data.OmniItem
import app.marginalia.data.OmniKind
import app.marginalia.data.TemplateProperty
import app.marginalia.obsidian.BuiltProperty
import app.marginalia.obsidian.DataField
import app.marginalia.obsidian.NoteBuilder
import app.marginalia.obsidian.PropertyRule
import app.marginalia.obsidian.Template
import app.marginalia.obsidian.VideoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class PropertyType(val id: String, val label: String, val icon: ImageVector)

/** The same seven types Obsidian has, in the same order. */
val PropertyTypes = listOf(
    PropertyType("", "Automatic", Icons.Rounded.AutoAwesome),
    PropertyType("checkbox", "Checkbox", Icons.Outlined.CheckBox),
    PropertyType("date", "Date", Icons.Rounded.CalendarToday),
    PropertyType("datetime", "Date & time", Icons.Rounded.Schedule),
    PropertyType("multitext", "List", Icons.AutoMirrored.Rounded.List),
    PropertyType("number", "Number", Icons.Rounded.Numbers),
    PropertyType("text", "Text", Icons.AutoMirrored.Rounded.Notes),
)

private fun oneLine(s: String) = s.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().take(90)

private fun ruleLine(p: TemplateProperty, vars: Map<String, String>): String = when (VideoData.ruleOf(p)) {
    PropertyRule.Ask -> "Ask me every time"
    PropertyRule.Empty -> "Leave empty"
    PropertyRule.Data -> {
        val field = VideoData.fieldOf(p.value)!!
        val sample = oneLine(Template.render(VideoData.valueFor(field, ""), vars))
        field.label + if (sample.isEmpty()) "" else ": $sample"
    }
    PropertyRule.Fixed -> oneLine(Template.render(p.value, vars)).ifEmpty { p.value }
}

/** One property in the template, with what fills it. */
@Composable
fun PropertyRow(p: TemplateProperty, vars: Map<String, String>, onClick: () -> Unit) {
    // A flat row, so a template's properties sit together in one card.
    val scheme = MaterialTheme.colorScheme
    ListRow(
        p.name, ruleLine(p, vars),
        icon = PropertyTypes.firstOrNull { it.id == p.type }?.icon ?: Icons.Rounded.AutoAwesome,
        subtitleColor = if (p.ask) scheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
        onClick = onClick,
    ) { Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant) }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DataRow(title: String, sample: String, picked: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sample.isNotEmpty()) {
                Text(sample, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (picked) Icon(Icons.Rounded.Check, "Chosen", tint = scheme.primary)
    }
}

/** The list of video data, each row showing what it holds for a real video. */
@Composable
private fun DataList(type: String, vars: Map<String, String>, value: String, onPick: (DataField) -> Unit) {
    val current = VideoData.fieldOf(value)
    VideoData.forType(type).forEach { f ->
        DataRow(f.label, oneLine(Template.render(VideoData.valueFor(f, ""), vars)), f == current) { onPick(f) }
    }
}

/** What goes into one property, and what type it is. */
@Composable
fun PropertyRuleSheet(
    p: TemplateProperty,
    vars: Map<String, String>,
    vault: String?,
    onChange: ((TemplateProperty) -> TemplateProperty) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rule by remember(p.id) { mutableStateOf(VideoData.ruleOf(p)) }
    SmoothSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                p.name, { v -> onChange { it.copy(name = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Property") }, singleLine = true, shape = MaterialTheme.shapes.large,
            )
            SheetLabel("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PropertyTypes.forEach { t ->
                    FilterChip(
                        selected = p.type == t.id,
                        onClick = {
                            if (t.id == "checkbox" && rule == PropertyRule.Data) {
                                rule = PropertyRule.Empty
                                onChange { it.copy(type = t.id, value = "") }
                            } else onChange { it.copy(type = t.id) }
                        },
                        label = { Text(t.label) },
                        leadingIcon = { Icon(t.icon, null, Modifier.size(18.dp)) },
                    )
                }
            }
            SheetLabel("What goes here")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PropertyRule.Ask to "Ask me",
                    PropertyRule.Data to "Video data",
                    PropertyRule.Fixed to "Always the same",
                    PropertyRule.Empty to "Leave empty",
                ).filter { it.first != PropertyRule.Data || p.type != "checkbox" }.forEach { (r, label) ->
                    FilterChip(
                        selected = rule == r,
                        onClick = {
                            rule = r
                            onChange {
                                when (r) {
                                    PropertyRule.Ask -> it.copy(ask = true)
                                    PropertyRule.Data -> it.copy(ask = false)
                                    PropertyRule.Fixed -> it.copy(ask = false, value = if (VideoData.fieldOf(it.value) != null) "" else it.value)
                                    PropertyRule.Empty -> it.copy(ask = false, value = "")
                                }
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }
            when (rule) {
                PropertyRule.Ask -> {
                    Text("A box opens before the note saves.", style = MaterialTheme.typography.bodyMedium)
                    if (p.type != "checkbox") {
                        SheetLabel("Start the box with")
                        Column {
                            DataRow("Nothing", "", p.value.isBlank()) { onChange { it.copy(value = "") } }
                            DataList(p.type, vars, p.value) { f -> onChange { it.copy(value = VideoData.valueFor(f, p.type)) } }
                        }
                    }
                }
                PropertyRule.Data -> Column {
                    DataList(p.type, vars, p.value) { f -> onChange { it.copy(value = VideoData.valueFor(f, p.type)) } }
                }
                PropertyRule.Fixed -> TypedField(
                    p.name, p.type, p.value, { v -> onChange { it.copy(value = v) } },
                    vars = vars, vault = vault, insert = { f -> VideoData.valueFor(f, p.type) },
                )
                PropertyRule.Empty -> Unit
            }
            TextButton(onClick = onRemove) { Text("Remove property", color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** What you already use for this property in your vault, most used first, narrowed by what is typed. */
@Composable
private fun usedValues(vault: String?, property: String, typed: String, shown: Boolean, hide: Collection<String>): List<String> {
    val app = LocalApp.current
    val indexes by app.vaults.indexes.collectAsState()
    val index = vault?.let { indexes[it] }
    val found by produceState(emptyList<String>(), index, property, typed, shown, hide.toSet()) {
        this.value = if (index == null || !shown) emptyList() else withContext(Dispatchers.Default) {
            index.values(property).entries.filter { it.key !in hide }
                .mapNotNull { e -> Omni.fuzzy(e.key, typed.trim())?.let { f -> e to f.score } }
                .sortedWith(compareByDescending<Pair<Map.Entry<String, Int>, Double>> { it.second }.thenByDescending { it.first.value })
                .take(12).map { it.first.key }
        }
    }
    return found
}

@Composable
private fun SuggestionRow(items: List<String>, onPick: (String) -> Unit) {
    if (items.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { v ->
            AssistChip(onClick = { onPick(v) }, label = { Text(v, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}

/** A text box that offers the values this property already has in the vault. */
@Composable
private fun TextValueField(label: String, type: String, value: String, vault: String?, dataIcon: (@Composable () -> Unit)?, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val used = usedValues(vault, label, value, focused && type != "number", listOf(value))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value, onChange, Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            label = { Text(label) }, maxLines = 6, shape = MaterialTheme.shapes.large, trailingIcon = dataIcon,
            keyboardOptions = if (type == "number") KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        )
        SuggestionRow(used, onChange)
    }
}

/** The right box for a property type. [insert] says what a piece of video data puts in the box. */
@Composable
fun TypedField(
    label: String,
    type: String,
    value: String,
    onChange: (String) -> Unit,
    vars: Map<String, String>,
    vault: String?,
    insert: (DataField) -> String,
    dataButton: Boolean = true,
) {
    var pickData by remember { mutableStateOf(false) }
    val dataIcon: (@Composable () -> Unit)? = if (dataButton && VideoData.forType(type).isNotEmpty()) {
        { IconButton(onClick = { pickData = true }) { Icon(Icons.Rounded.SmartDisplay, "Use video data") } }
    } else null
    Column {
        when (type) {
            "checkbox" -> Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = value.equals("true", true) || value == "1" || value.equals("yes", true), onCheckedChange = { onChange(it.toString()) })
            }
            "date", "datetime" -> DateField(label, value, withTime = type == "datetime", dataIcon, onChange)
            "multitext" -> ListField(label, value, vault, dataIcon, onChange)
            else -> TextValueField(label, type, value, vault, dataIcon, onChange)
        }
    }
    if (pickData) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        SmoothSheet(onDismissRequest = { pickData = false }, sheetState = sheet) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            ) {
                Text("Video data", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                DataList(type, vars, "") { f ->
                    val piece = insert(f)
                    onChange(
                        when {
                            type == "multitext" -> (NoteBuilder.splitList(value) + piece).joinToString(",")
                            type == "text" || type.isBlank() -> if (value.isBlank()) piece else "$value $piece"
                            else -> piece
                        },
                    )
                    pickData = false
                }
            }
        }
    }
}

@Composable
private fun DateField(label: String, value: String, withTime: Boolean, dataIcon: (@Composable () -> Unit)?, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var pickDay by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    val parsed = Template.parseDate(value)
    Surface(onClick = { pickDay = true }, shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (withTime) Icons.Rounded.Schedule else Icons.Rounded.CalendarToday, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(
                    if (value.isBlank()) "Pick a date" else Template.formatDate(value, if (withTime) "D MMM YYYY, HH:mm" else "D MMM YYYY") ?: value,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            dataIcon?.invoke()
            if (value.isNotBlank()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Clear $label") }
        }
    }
    if (pickDay) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (parsed?.toLocalDate() ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDay = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (withTime) day = d else onChange(d.toString())
                    }
                    pickDay = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDay = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    day?.let { d ->
        val at = parsed?.toLocalTime() ?: LocalTime.now()
        val state = rememberTimePickerState(at.hour, at.minute, android.text.format.DateFormat.is24HourFormat(ctx))
        AlertDialog(
            onDismissRequest = { day = null },
            confirmButton = {
                TextButton(onClick = {
                    onChange(d.atTime(state.hour, state.minute).toString() + ":00")
                    day = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { day = null }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun ListField(label: String, value: String, vault: String?, dataIcon: (@Composable () -> Unit)?, onChange: (String) -> Unit) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val indexes by app.vaults.indexes.collectAsState()
    val index = vault?.let { indexes[it] }
    val isTags = label.equals("tags", true)
    val kinds = if (isTags) setOf(OmniKind.Tag) else setOf(OmniKind.Note, OmniKind.Tag)
    val items = NoteBuilder.splitList(value)
    var draft by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    // Your own tags as you type. A tags box shows the ones you use most before you type at all.
    val matches by produceState(emptyList<OmniItem>(), index, draft, focused, value) {
        if (index == null || !focused || (draft.isBlank() && !isTags)) { this.value = emptyList(); return@produceState }
        delay(35)
        this.value = withContext(Dispatchers.Default) {
            Omni.search(index, draft.removePrefix("#"), kinds, settings.recentTags, hide = items.toSet(), limit = 12)
        }
    }
    val used = usedValues(vault, label, draft, focused, items)
    fun add(item: String) {
        val t = item.trim().removePrefix("#")
        if (t.isNotEmpty() && t !in items) onChange((items + t).joinToString(","))
        draft = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            draft,
            { v -> if (v.endsWith(",")) add(v.dropLast(1)) else draft = v },
            // Typed words count even when the next tap is Save.
            Modifier.fillMaxWidth().onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused && draft.isNotBlank()) add(draft)
            },
            label = { Text(label) }, singleLine = true, shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(draft) }),
            trailingIcon = {
                Row {
                    dataIcon?.invoke()
                    if (vault != null) IconButton(onClick = { pick = true }) { Icon(Icons.Rounded.Search, "Search your vault") }
                }
            },
        )
        val fromVault = matches.map { if (it.kind == OmniKind.Note) "[[${it.title}]]" else it.value }
        SuggestionRow((used + fromVault).distinct().take(14), ::add)
        if (items.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                InputChip(
                    selected = true,
                    onClick = { onChange((items - item).joinToString(",")) },
                    label = { Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = { Icon(Icons.Rounded.Close, "Remove $item", Modifier.size(16.dp)) },
                )
            }
        }
    }
    if (pick && vault != null) {
        OmniSheet(
            vault = vault, title = "Add to $label", kinds = kinds,
            onPick = { o -> add(if (o.kind == OmniKind.Note) "[[${o.title}]]" else o.value); pick = false },
            onDismiss = { pick = false },
        )
    }
}

/** Opens after the save button, for the fields still empty that you fill by hand. Swiping it away keeps what you typed. */
@Composable
fun AskSheet(
    fields: List<BuiltProperty>,
    action: String,
    vars: Map<String, String>,
    vault: String?,
    onValue: (BuiltProperty, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = LocalFocusManager.current
    SmoothSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Before it saves", style = MaterialTheme.typography.headlineSmall)
            fields.forEach { p ->
                TypedField(p.property.name, p.type, p.value, { onValue(p, it) }, vars, vault, insert = { f -> vars[f.key].orEmpty() })
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
            Button(
                onClick = { focus.clearFocus(); onSave() },
                modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).height(54.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(action)
            }
        }
    }
}
