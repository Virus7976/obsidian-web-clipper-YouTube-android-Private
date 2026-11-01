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
        val (fm, body) = ObsidianText.splitFrontmatter(text.replace("\r\n", "\n"))
        val props = fields(fm).map { f ->
            previous?.properties?.firstOrNull { it.name.equals(f.name, true) }?.let { return@map it }
            val value = clean(f.value)
            val type = typeOf(f.name, value, f.list, vaultTypes)
            val guessed = if (value.isBlank()) guess(f.name, type) else null
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
        return base.copy(source = path, noteContentFormat = content, properties = props)
    }

    private fun root(json: String): JsonObject? = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()

    /** Property name to type, from the vault's .obsidian/types.json. */
    fun types(json: String): Map<String, String> =
        (root(json)?.get("types") as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()

    /** The folder set in Obsidian's Templates plugin, from .obsidian/templates.json. */
    fun folder(json: String): String = (root(json)?.get("folder") as? JsonPrimitive)?.contentOrNull.orEmpty().trim('/')
}
