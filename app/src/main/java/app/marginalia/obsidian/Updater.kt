package app.marginalia.obsidian

import app.marginalia.data.AppSettings
import app.marginalia.data.ObsidianText
import app.marginalia.data.SavedVideo

/**
 * Updates a note that already exists, the careful way. Nothing the user wrote is changed or
 * removed: missing properties are added, new tags are added to the tag list, and highlights
 * that are not in the note yet are added at the end.
 */
object Updater {
    data class Merged(val text: String, val addedHighlights: Int, val addedProps: Int) {
        val changed: Boolean get() = addedHighlights > 0 || addedProps > 0
    }

    private fun norm(s: String) = s.lowercase()
        .replace(Regex("<[^>]+>"), " ")
        .replace("==", " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun blockRegex(key: String) = Regex("(?m)^" + Regex.escape(key) + "[ \\t]*:[^\\n]*(?:\\n[ \\t]+-[^\\n]*)*")

    private fun existingItems(block: String): List<String> {
        val first = block.lines().first().substringAfter(':').trim()
        val inline = if (first.isNotEmpty()) first.removePrefix("[").removeSuffix("]").split(',') else emptyList()
        val listed = block.lines().drop(1).map { it.trim().removePrefix("-").trim() }
        return (inline + listed).map { it.trim().trim('"', '\'') }.filter { it.isNotEmpty() }
    }

    /** The frontmatter as plain text values, by lower-case property name. A list is joined with commas. */
    fun readValues(text: String): Map<String, String> {
        val fm = ObsidianText.splitFrontmatter(text).first
        val out = LinkedHashMap<String, String>()
        var key: String? = null
        val items = ArrayList<String>()
        fun unquote(v: String) = v.trim().trim('"', '\'')
        fun flush() {
            val k = key ?: return
            if (items.isNotEmpty()) out[k] = items.joinToString(",")
            items.clear()
        }
        val head = Regex("^([^\\s#-][^:]*?)\\s*:\\s*(.*)$")
        for (line in fm.lines()) {
            val m = head.find(line)
            if (m != null) {
                flush()
                key = unquote(m.groupValues[1]).lowercase()
                val inline = m.groupValues[2].trim()
                if (inline.isNotEmpty()) {
                    out[key] = inline.removePrefix("[").removeSuffix("]").split(',').joinToString(",") { unquote(it) }
                    key = null
                }
            } else if (key != null && line.trim().startsWith("-")) {
                items += unquote(line.trim().removePrefix("-"))
            }
        }
        flush()
        return out
    }

    /** [replace] lists property ids whose new value should replace the one already in the note. */
    fun merge(existing: String, note: BuiltNote, video: SavedVideo, settings: AppSettings, replace: Set<String> = emptySet()): Merged {
        val (fm0, body0) = ObsidianText.splitFrontmatter(existing)
        var fm = fm0
        var addedProps = 0

        for (p in note.properties) {
            val value = p.value.trim()
            if (value.isEmpty()) continue
            val key = p.property.name
            val block = blockRegex(key).find(fm)
            if (block == null) {
                fm = fm.trimEnd('\n') + (if (fm.isBlank()) "" else "\n") + NoteBuilder.yaml(p).trimEnd('\n')
                addedProps++
            } else if (p.type == "multitext") {
                val have = existingItems(block.value)
                val fresh = NoteBuilder.splitList(value).filter { n -> have.none { it.equals(n.trim('"'), true) } }
                if (fresh.isNotEmpty()) {
                    val merged = (have + fresh).joinToString(",")
                    val rebuilt = NoteBuilder.yaml(p.copy(value = merged)).trimEnd('\n')
                    fm = fm.replaceRange(block.range, rebuilt)
                    addedProps++
                }
            } else if (p.property.id in replace) {
                // A box you filled in on the sheet. If you changed what the note had, the note changes too.
                val cur = readValues("---\n" + block.value + "\n---\n")[key.lowercase()].orEmpty()
                if (cur.trim() != value) {
                    fm = fm.replaceRange(block.range, NoteBuilder.yaml(p).trimEnd('\n'))
                    addedProps++
                }
            }
        }

        val bodyNorm = norm(body0)
        val fresh = video.highlights.sortedBy { it.startSec }
            .filter { h -> norm(h.text).let { it.isNotEmpty() && !bodyNorm.contains(it) } }
            .map { NoteBuilder.highlightEntry(video, it, settings) }

        if (addedProps == 0 && fresh.isEmpty()) return Merged(existing, 0, 0)

        val newBody = if (fresh.isEmpty()) body0 else body0.trimEnd() + "\n\n" + fresh.joinToString("\n\n") + "\n"
        val text = buildString {
            if (fm.isNotBlank()) append("---\n").append(fm.trimEnd('\n')).append("\n---\n\n")
            append(newBody)
        }
        return Merged(text, fresh.size, addedProps)
    }
}
