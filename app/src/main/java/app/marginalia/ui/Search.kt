package app.marginalia.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.marginalia.data.SavedVideo

/** Every word typed has to appear somewhere in the fields. Capitals do not matter. */
fun matchesQuery(query: String, vararg fields: String): Boolean {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val hay = fields.joinToString(" ").lowercase()
    return words.all { hay.contains(it) }
}

/** Looks in the title, channel, category, tags, and the words and notes you highlighted. */
fun SavedVideo.matchesQuery(query: String): Boolean =
    matchesQuery(
        query, meta.title, meta.author, category, tags.joinToString(" "),
        highlights.joinToString(" ") { it.text + " " + it.note },
    )

/** The one search box used on every list. */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query, onValueChange = onQuery,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine = true, shape = RoundedCornerShape(50),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}
