package app.marginalia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.marginalia.data.Omni
import app.marginalia.data.OmniItem
import app.marginalia.data.OmniKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Search over a vault, in the spirit of Obsidian's Omnisearch. Used to browse the vault, to pick
 * a folder, to pick tags and to pick a note to update.
 *
 * Type `#` to look only at tags, `/` to look only at folders.
 */
@Composable
fun OmniSheet(
    vault: String,
    title: String,
    kinds: Set<OmniKind>,
    onPick: (OmniItem) -> Unit,
    onDismiss: () -> Unit,
    multi: Boolean = false,
    selected: Set<String> = emptySet(),
    startWith: String = "",
) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val indexes by app.vaults.indexes.collectAsState()
    val busy by app.vaults.busy.collectAsState()
    val index = indexes[vault]
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf(startWith) }
    var filter by remember { mutableStateOf<OmniKind?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        app.vaults.refresh(vault)
        runCatching { focus.requestFocus() }
    }

    val shownKinds = if (filter != null) setOf(filter!!) + OmniKind.Create else kinds
    val results by produceState(emptyList<OmniItem>(), index, query, filter, selected) {
        delay(35)
        value = withContext(Dispatchers.Default) {
            Omni.search(index, query, shownKinds, settings.recentTags, hide = if (multi) selected else emptySet())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (multi) TextButton(onClick = onDismiss) { Text("Done") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).focusRequester(focus),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                placeholder = {
                    Text(
                        when {
                            (kinds - OmniKind.Create) == setOf(OmniKind.Tag) -> "Search your tags"
                            kinds.contains(OmniKind.Note) -> "Search notes, folders, #tags"
                            else -> "Search folders"
                        },
                    )
                },
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let(onPick) }),
            )

            if (multi && selected.isNotEmpty()) {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selected.forEach { t ->
                        AssistChip(
                            onClick = { onPick(OmniItem(OmniKind.Tag, "#$t", "", value = t)) },
                            label = { Text("#$t") },
                            trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }

            val realKinds = kinds - OmniKind.Create
            if (realKinds.size > 1) {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                    realKinds.forEach { k ->
                        FilterChip(
                            selected = filter == k,
                            onClick = { filter = if (filter == k) null else k },
                            label = { Text(when (k) { OmniKind.Note -> "Notes"; OmniKind.Folder -> "Folders"; else -> "Tags" }) },
                        )
                    }
                }
            }

            val reading = busy[vault]
            when {
                index == null && reading == null -> Text(
                    "Marginalia has not read this vault yet. Add its folder in Settings, under Obsidian, and your notes, folders and tags show up here.",
                    Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                index == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LoadingIndicator()
                        Text("Reading your vault, $reading notes so far", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> {
                    if (reading != null) {
                        Text(
                            "Updating the index, $reading notes read",
                            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (results.isEmpty() && query.isNotBlank()) {
                        Text("Nothing matches \"$query\"", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                        items(results, key = { it.kind.name + "|" + it.value + "|" + it.title }) { item ->
                            ResultRow(item, picked = item.kind == OmniKind.Tag && item.value in selected) { onPick(item) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(item: OmniItem, picked: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (icon, tint, container) = when (item.kind) {
        OmniKind.Note -> Triple(Icons.Rounded.Description, scheme.onSecondaryContainer, scheme.secondaryContainer)
        OmniKind.Folder -> Triple(Icons.Rounded.Folder, scheme.onTertiaryContainer, scheme.tertiaryContainer)
        OmniKind.Tag -> Triple(Icons.Rounded.Sell, scheme.onPrimaryContainer, scheme.primaryContainer)
        OmniKind.Create -> Triple(Icons.Rounded.Add, scheme.onPrimary, scheme.primary)
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                marked(item.title, item.matched, scheme.primary),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            item.snippet?.let { s ->
                Text(
                    buildAnnotatedString {
                        append(s)
                        item.snippetMatch?.let { r ->
                            addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = scheme.primary), r.first, (r.last + 1).coerceAtMost(s.length))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (picked) Icon(Icons.Rounded.Check, "Added", tint = scheme.primary)
    }
}

private fun marked(text: String, at: List<Int>, color: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    val set = at.toHashSet()
    text.forEachIndexed { i, c ->
        if (i in set) withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = color)) { append(c) } else append(c)
    }
}
