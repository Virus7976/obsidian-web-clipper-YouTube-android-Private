package app.marginalia.obsidian

import app.marginalia.data.AppSettings
import app.marginalia.data.ClipTemplate
import app.marginalia.data.Line
import app.marginalia.data.SavedVideo
import app.marginalia.data.TemplateProperty
import app.marginalia.data.YouTube
import java.time.ZonedDateTime

data class BuiltProperty(val property: TemplateProperty, val type: String, val value: String)

data class BuiltNote(
    val vault: String,
    val folder: String,
    val name: String,
    val behavior: String,
    val properties: List<BuiltProperty>,
    val content: String,
    val markdown: String,
) {
    val path: String get() = if (folder.isBlank()) name else "${folder.trim('/')}/$name"
}

object NoteBuilder {
    fun timeLink(id: String, sec: Double) = "https://www.youtube.com/watch?v=$id&t=${sec.toInt()}s"

    /** The colour is a coloured circle right after the opening marks, then the text. */
    fun styled(h: app.marginalia.data.Highlight, settings: AppSettings): String {
        val text = h.text.replace(Regex("\\s*\\n\\s*"), " ").trim()
        if (settings.highlightSyntax != "obsidian") return text
        val emoji = app.marginalia.data.HighlightColors.getOrNull(h.color)?.emoji.orEmpty()
        return "==$emoji$text=="
    }

    fun highlightEntry(video: SavedVideo, h: app.marginalia.data.Highlight, settings: AppSettings): String {
        val body = settings.highlightFormat
            .replace("{text}", styled(h, settings))
            .replace("{time}", YouTube.formatTime(h.startSec))
            .replace("{link}", timeLink(video.meta.id, h.startSec))
        return if (h.note.isBlank()) body else body + "\n\n" + h.note.trim()
    }

    fun highlightsMarkdown(video: SavedVideo, settings: AppSettings): String =
        video.highlights.sortedBy { it.startSec }.joinToString("\n\n") { highlightEntry(video, it, settings) }

    fun transcriptMarkdown(video: SavedVideo, lines: List<Line>): String {
        val sb = StringBuilder()
        var chapterIdx = -1
        for (l in lines) {
            val ci = video.chapters.indexOfLast { it.start <= l.start + 0.5 }
            if (ci != chapterIdx && ci >= 0) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append("## ").append(video.chapters[ci].title)
                chapterIdx = ci
            }
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append("[").append(YouTube.formatTime(l.start)).append("](")
                .append(timeLink(video.meta.id, l.start)).append(") ").append(l.text)
        }
        return sb.toString()
    }

    fun variables(video: SavedVideo, lines: List<Line>, settings: AppSettings, now: ZonedDateTime = ZonedDateTime.now()): Map<String, String> {
        val m = video.meta
        val highlights = highlightsMarkdown(video, settings)
        val transcript = transcriptMarkdown(video, lines)
        val content = when {
            highlights.isNotBlank() && settings.highlightBehavior == "replace-content" -> highlights
            transcript.isNotBlank() -> transcript
            else -> m.description
        }
        return mapOf(
            "title" to m.title,
            "url" to m.url,
            "videoId" to m.id,
            "author" to m.author,
            "channel" to m.author,
            "published" to m.published,
            "date" to now.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "time" to now.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "description" to m.description.lines().firstOrNull { it.isNotBlank() }.orEmpty().take(300),
            "fullDescription" to m.description,
            "content" to content,
            "highlights" to highlights,
            "transcript" to transcript,
            "image" to m.thumb,
            "thumbnail" to m.thumb,
            "duration" to YouTube.formatTime(m.durationSec.toDouble()),
            "embed" to "![](${m.url})",
            "domain" to "youtube.com",
            "site" to "YouTube",
            "words" to content.split(Regex("\\s+")).count { it.isNotBlank() }.toString(),
        )
    }

    /**
     * Adds the always-on tags and properties to a template's properties.
     * A template value that is already filled in is kept. Tags are joined, never replaced.
     */
    fun withAutos(base: List<TemplateProperty>, autoTags: List<String>, autoProps: List<TemplateProperty>): List<TemplateProperty> {
        val out = base.toMutableList()
        fun clean(t: String) = t.trim().removePrefix("#").trim('"')
        val tagsToAdd = autoTags.map(::clean).filter { it.isNotBlank() }
        if (tagsToAdd.isNotEmpty()) {
            val i = out.indexOfFirst { it.name.equals("tags", ignoreCase = true) }
            if (i < 0) {
                out += TemplateProperty("auto:tags", "tags", tagsToAdd.joinToString(","), "multitext")
            } else {
                val have = splitList(out[i].value).map(::clean).filter { it.isNotBlank() }
                out[i] = out[i].copy(value = (have + tagsToAdd).distinct().joinToString(","))
            }
        }
        for (a in autoProps) {
            if (a.name.isBlank() || a.name.equals("tags", ignoreCase = true)) continue
            val i = out.indexOfFirst { it.name.equals(a.name, ignoreCase = true) }
            if (i < 0) out += a.copy(id = "auto:" + a.name)
            else if (out[i].value.isBlank()) out[i] = out[i].copy(value = a.value)
        }
        return out
    }

    fun build(
        template: ClipTemplate,
        video: SavedVideo,
        lines: List<Line>,
        settings: AppSettings,
        valueOverrides: Map<String, String> = emptyMap(),
        nameOverride: String? = null,
        folderOverride: String? = null,
        vaultOverride: String? = null,
    ): BuiltNote {
        val vars = variables(video, lines, settings)
        val props = withAutos(template.properties, settings.autoTags, settings.autoProps).map { p ->
            val raw = valueOverrides[p.id] ?: Template.render(p.value, vars)
            val type = p.type.ifBlank { settings.propertyTypes[p.name] ?: "text" }
            BuiltProperty(p, type, raw)
        }
        val content = Template.render(template.noteContentFormat, vars)
        val name = Template.safeName(nameOverride ?: Template.render(template.noteNameFormat, vars)).ifBlank { video.meta.id }
        val folder = (folderOverride ?: Template.render(template.path, vars)).trim().trim('/')
        // The vault chosen in Settings wins. A vault name inside an imported template is only a fallback.
        val vault = vaultOverride ?: settings.vault.ifBlank { template.vault }
        val md = buildString {
            if (props.isNotEmpty()) {
                append("---\n")
                props.forEach { append(yaml(it)) }
                append("---\n\n")
            }
            append(content)
        }
        return BuiltNote(vault, folder, name, template.behavior, props, content, md)
    }

    // ---------- YAML properties ----------

    private fun quote(s: String) =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace(Regex("\\s*\\n\\s*"), " ").trim() + "\""

    fun splitList(value: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var depth = 0
        for (c in value) {
            when {
                c == '[' -> { depth++; cur.append(c) }
                c == ']' -> { depth = (depth - 1).coerceAtLeast(0); cur.append(c) }
                (c == ',' || c == '\n') && depth == 0 -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
        }
        out += cur.toString()
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun yaml(p: BuiltProperty): String {
        val key = if (Regex("^[A-Za-z0-9_ -]+$").matches(p.property.name)) p.property.name else quote(p.property.name)
        val v = p.value.trim()
        return when (p.type) {
            "multitext" -> {
                val items = splitList(v)
                if (items.isEmpty()) "$key:\n" else "$key:\n" + items.joinToString("") { "  - ${quote(it)}\n" }
            }
            "date" -> {
                val f = Template.formatDate(v, "YYYY-MM-DD")
                if (v.isEmpty()) "$key:\n" else "$key: ${f ?: v}\n"
            }
            "datetime" -> {
                val f = Template.formatDate(v, "YYYY-MM-DD[T]HH:mm:ss")
                if (v.isEmpty()) "$key:\n" else "$key: ${f ?: v}\n"
            }
            "number" -> if (v.toDoubleOrNull() != null) "$key: $v\n" else "$key:\n"
            "checkbox" -> "$key: ${v.equals("true", true) || v == "1" || v.equals("yes", true)}\n"
            else -> if (v.isEmpty()) "$key:\n" else "$key: ${quote(v)}\n"
        }
    }
}
