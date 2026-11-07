package app.marginalia.obsidian

import app.marginalia.data.ClipTemplate
import app.marginalia.data.ObsidianText
import app.marginalia.data.TemplateProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/**
 * Turns a note from the vault into a clip template. A property with a value keeps it. An empty one
 * is matched to video data by its name, and if nothing fits it is asked for at every save.
 */
object VaultTemplate {
    private class Field(val name: String, val value: String, val list: Boolean)

    private val blockMarks = setOf("|", ">", "|-", ">-")
    private val head = Regex("^([^\\s#-][^:]*?)\\s*:(?:\\s+(.*))?$")
    private val datedTag = Regex("\\{\\{\\s*(date|time)\\s*:\\s*([^}|]+?)\\s*\\}\\}")
    private val bodyVars = Regex("\\{\\{\\s*(content|highlights|transcript)\\b")

    private fun unquote(v: String): String {
        val t = v.trim()
        return if (t.length >= 2 && (t.first() == '"' || t.first() == '\'') && t.last() == t.first()) t.substring(1, t.length - 1) else t
    }

    private fun fields(frontmatter: String): List<Field> {
        val out = ArrayList<Field>()
        var key: String? = null
        var list = false
        var block = false
        val items = ArrayList<String>()
        fun flush() {
            key?.let { out += Field(it, items.filter { i -> i.isNotBlank() }.joinToString(if (block) " " else ","), list) }
            items.clear()
            list = false
            block = false
        }
        for (line in frontmatter.lines()) {
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val m = head.find(line)
            when {
                m != null -> {
                    flush()
                    key = unquote(m.groupValues[1])
                    val v = m.groupValues[2].trim()
                    when {
                        v == "[]" -> list = true
                        v in blockMarks -> block = true
                        v.startsWith("[") && !v.startsWith("[[") -> {
                            list = true
                            items += NoteBuilder.splitList(v.removeSurrounding("[", "]")).map(::unquote)
                        }
                        v.isNotEmpty() -> items += unquote(v)
                    }
                }
                key != null && block -> items += line.trim()
                key != null && line.trim().startsWith("-") -> {
                    list = true
                    items += unquote(line.trim().removePrefix("-"))
                }
            }
        }
        flush()
        return out
    }

    /** A note's short property values, by lower-case name. Long text is not worth offering again. */
    fun values(frontmatter: String): Map<String, List<String>> = fields(frontmatter.replace("\r\n", "\n"))
        .mapNotNull { f ->
            val list = (if (f.list) NoteBuilder.splitList(f.value) else listOf(f.value.trim()))
                .filter { it.isNotBlank() && it.length <= 60 && !it.contains("{{") }.take(20)
            if (list.isEmpty()) null else f.name.lowercase() to list
        }.toMap()

    /** Obsidian's own {{date}}, {{time}} and {{date:FORMAT}} in the form the clip template language reads. */
    fun clean(s: String): String = s
        .replace(datedTag) { "{{date|date:\"" + it.groupValues[2] + "\"}}" }
        .replace(Regex("\\{\\{\\s*date\\s*\\}\\}"), Regex.escapeReplacement("{{date|date:\"YYYY-MM-DD\"}}"))
        .replace(Regex("\\{\\{\\s*time\\s*\\}\\}"), Regex.escapeReplacement("{{date|date:\"HH:mm\"}}"))

    private fun typeOf(name: String, value: String, list: Boolean, vaultTypes: Map<String, String>): String {
        when (val set = vaultTypes.entries.firstOrNull { it.key.equals(name, true) }?.value) {
            "tags", "aliases", "multitext" -> return "multitext"
            "text", "number", "date", "datetime", "checkbox" -> return set
        }
        return when {
            list -> "multitext"
            value.contains("{{date|date:\"HH:mm") -> "datetime"
            value.startsWith("{{date") -> "date"
            else -> ""
        }
    }

    /** The video data an empty property most likely wants, going by its name. */
    fun guess(name: String, type: String): String? {
        val n = name.lowercase().replace(Regex("[^a-z0-9]"), "")
        return when (n) {
            "url", "source", "link", "sourceurl", "videourl", "youtube" -> "{{url}}"
            "title", "videotitle" -> "{{title}}"
            "author", "authors", "channel", "creator", "speaker", "host" -> "{{author}}"
            "published", "publishdate", "datepublished", "uploaded", "posted", "released" -> "{{published}}"
            "created", "clipped", "saved", "added", "date", "dateadded", "dateclipped" -> "{{date}}"
            "description", "summary", "about" -> "{{description}}"
            "image", "thumbnail", "cover", "banner", "poster" -> "{{image}}"
            "duration", "length", "runtime" -> if (type == "number") "{{minutes}}" else "{{duration}}"
            "site", "domain", "platform" -> "{{site}}"
            "videoid" -> "{{videoId}}"
            "highlights" -> "{{highlights}}"
            "transcript" -> "{{transcript}}"
            else -> null
        }
    }

    /**
     * [previous] is the template made from this same note earlier. Your choices for each property
     * stay, new properties are set up, and properties the note no longer has go away.
     */
    fun read(text: String, path: String, vaultTypes: Map<String, String>, previous: ClipTemplate? = null): ClipTemplate {
        // A Templater template is read as the plain template its script and tags stand for.
        val tp = if (Templater.isTemplater(text)) Templater.prepare(text) else null
        val (fm, body) = ObsidianText.splitFrontmatter((tp?.text ?: text).replace("\r\n", "\n"))
        val props = fields(fm).map { f ->
            previous?.properties?.firstOrNull { it.name.equals(f.name, true) }?.let { return@map it }
            val value = clean(f.value)
            val type = typeOf(f.name, value, f.list, vaultTypes).ifBlank { tp?.types?.get(f.name).orEmpty() }
            // By the property's name first, then by the wording of the Templater question behind it.
            val guessed = if (value.isBlank()) guess(f.name, type) ?: tp?.questions?.get(f.name)?.let(Templater::fromLabel) else null
            TemplateProperty(
                UUID.randomUUID().toString(), f.name, guessed ?: value, type,
                ask = value.isBlank() && guessed == null && !f.name.equals("tags", true),
            )
        }
        val written = clean(body.trim())
        // A clip without the highlights in it would lose the point of the clip.
        val content = when {
            written.isBlank() -> "{{content}}"
            bodyVars.containsMatchIn(written) -> written
            else -> "$written\n\n{{content}}"
        }
        val base = previous ?: ClipTemplate(
            id = UUID.randomUUID().toString().take(12),
            name = path.substringAfterLast('/').removeSuffix(".md"),
        )
        // The script's file name and folder only set up a new template. After that they are your choice.
        val named = if (previous == null && tp != null) base.copy(
            noteNameFormat = tp.noteName ?: base.noteNameFormat,
            path = tp.folder ?: base.path,
        ) else base
        return named.copy(source = path, noteContentFormat = content, properties = props, templater = tp != null)
    }

    private fun block(name: String, value: String, type: String): String {
        val key = if (Regex("^[A-Za-z0-9_ -]+$").matches(name)) name else "\"$name\""
        fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        return when {
            type == "multitext" && value.isBlank() -> "$key: []"
            type == "multitext" -> "$key:\n" + NoteBuilder.splitList(value).joinToString("\n") { "  - " + q(it) }
            value.isBlank() -> "$key:"
            value.startsWith("{{") || type in setOf("date", "datetime", "number", "checkbox") -> "$key: $value"
            else -> "$key: " + q(value)
        }
    }

    /**
     * The vault note with this template's changes in it, or null when it already matches.
     * Video data and Ask me stay in the app, so those properties sit empty in the note, and a
     * line that still means the same is left exactly as it was written.
     */
    fun writeBack(text: String, t: ClipTemplate, read: ClipTemplate): String? {
        if (t.templater || Templater.isTemplater(text)) return writeBackTemplater(text, t)
        val (fm, body) = ObsidianText.splitFrontmatter(text.replace("\r\n", "\n"))
        val blocks = ArrayList<Pair<String, ArrayList<String>>>()
        for (line in fm.lines()) {
            val m = if (line.isBlank() || line.trimStart().startsWith("#")) null else head.find(line)
            if (m != null) blocks += unquote(m.groupValues[1]) to arrayListOf(line)
            else if (blocks.isNotEmpty() && line.isNotBlank()) blocks.last().second += line
        }
        val have = fields(fm).associateBy { it.name.lowercase() }
        val lines = t.properties.joinToString("\n") { p ->
            val old = have[p.name.lowercase()]
            val raw = blocks.firstOrNull { it.first.equals(p.name, true) }?.second?.joinToString("\n")
            val fixed = VideoData.ruleOf(p) == PropertyRule.Fixed && !p.ask
            val same = old != null && clean(old.value) == p.value
            when {
                raw != null && (same || (old?.value.orEmpty().isBlank() && !fixed)) -> raw
                else -> block(p.name, if (fixed) p.value else "", p.type)
            }
        }
        val newBody = if (t.noteContentFormat == read.noteContentFormat) body else t.noteContentFormat
        if (lines.trim() == fm.trim() && newBody == body) return null
        val top = if (t.properties.isEmpty()) "" else "---\n$lines\n---\n"
        return top + (if (newBody.isBlank()) "\n" else "\n$newBody")
    }

    /**
     * A Templater note is never rewritten whole, because that would lose its script. Only a property
     * that holds plain text in the note, and is set to the same text every time in the app, is changed,
     * one line at a time. Lines with a Templater tag are left exactly as they are.
     */
    private fun writeBackTemplater(text: String, t: ClipTemplate): String? {
        val lines = text.replace("\r\n", "\n").lines().toMutableList()
        var inScript = false
        var start = -1
        var end = -1
        for ((i, line) in lines.withIndex()) {
            if (line.contains("<%*")) inScript = true
            if (!inScript && line.trim() == "---") {
                if (start < 0) start = i else { end = i; break }
            }
            if (inScript && line.contains("%>")) inScript = false
        }
        if (start < 0 || end < 0) return null
        var changed = false
        for (i in start + 1 until end) {
            val line = lines[i]
            if (line.contains("<%")) continue
            val m = head.find(line) ?: continue
            val p = t.properties.firstOrNull { it.name.equals(unquote(m.groupValues[1]), true) } ?: continue
            if (p.ask || p.type == "multitext" || VideoData.ruleOf(p) != PropertyRule.Fixed) continue
            if (unquote(m.groupValues[2]) == p.value) continue
            lines[i] = block(p.name, p.value, p.type)
            changed = true
        }
        return if (changed) lines.joinToString("\n") else null
    }

    /** The vault's types.json with the types set in the app, or null when nothing changed. */
    fun writeTypes(json: String, t: ClipTemplate): String? {
        val all = root(json) ?: return null
        val old = (all["types"] as? JsonObject)?.toMutableMap() ?: return null
        var changed = false
        for (p in t.properties) {
            if (p.type.isBlank() || p.name.equals("tags", true) || p.name.equals("aliases", true)) continue
            val key = old.keys.firstOrNull { it.equals(p.name, true) } ?: p.name
            if ((old[key] as? JsonPrimitive)?.contentOrNull != p.type) { old[key] = JsonPrimitive(p.type); changed = true }
        }
        if (!changed) return null
        val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(all + ("types" to JsonObject(old))))
    }

    private fun root(json: String): JsonObject? = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()

    /** Property name to type, from the vault's .obsidian/types.json. */
    fun types(json: String): Map<String, String> =
        (root(json)?.get("types") as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()

    /** The folder set in Obsidian's Templates plugin, from .obsidian/templates.json. */
    fun folder(json: String): String = (root(json)?.get("folder") as? JsonPrimitive)?.contentOrNull.orEmpty().trim('/')
}
