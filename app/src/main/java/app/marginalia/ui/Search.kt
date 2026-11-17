package app.marginalia.ui

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.obsi.core.data.Omni
import app.marginalia.data.SavedVideo

/**
 * Every word typed has to match somewhere in the fields. Capitals do not matter.
 * Short fields such as a title forgive skipped letters, so "mmil" finds "Marcia Milligan".
 * Long text, such as your highlights, must contain the word as typed.
 */
fun matchesQuery(query: String, vararg fields: String): Boolean {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    return words.all { w ->
        fields.any { f -> f.contains(w, ignoreCase = true) || (f.length <= 120 && w.length >= 2 && Omni.fuzzy(f, w) != null) }
    }
}

/** Looks in the title, channel, category, tags, and the words and notes you highlighted. */
fun SavedVideo.matchesQuery(query: String): Boolean =
    matchesQuery(
        query, meta.title, meta.author, category, tags.joinToString(" "),
        highlights.joinToString(" ") { it.text + " " + it.note },
    )

/** The one search box used on every list. A slim pill, one line, with room for the text. */
