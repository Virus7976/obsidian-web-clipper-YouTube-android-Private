package app.marginalia.obsidian

/**
 * Reads a template written for Obsidian's Templater plugin. The script at the top is not run. It is read
 * to find out what each `<% %>` tag puts in a property: a question, today's date, the note's title. A
 * question is then matched to video data by its wording, so "Author" fills itself in from the video.
 */
object Templater {
    /**
     * [text] is the note as a plain template: the script is gone, and every tag is replaced with what
     * the clip template language can fill in, or nothing. [questions] is the wording of the question
     * behind each property. [types] holds a type a tag makes clear, like a date picker. [noteName]
     * is what the file is renamed to, when the script renames it. [folder] is where tp.file.move puts it.
     */
    class Prepared(
        val text: String,
        val questions: Map<String, String>,
        val types: Map<String, String>,
        val noteName: String?,
        val folder: String? = null,
    )

    private sealed interface Source {
        data class Ask(val label: String, val type: String) : Source
        data class Date(val format: String) : Source
        object Title : Source
        data class From(val name: String, val list: Boolean) : Source
    }

    private val run = Regex("<%[*][\\s\\S]*?%>\\n?")
    private val tag = Regex("<%[-_]?(?![*])([\\s\\S]*?)[-_]?%>")
    private val assign = Regex("^(?:const|let|var)\\s+(\\w+)\\s*=\\s*([\\s\\S]*)$")
    private val prompt = Regex("tp\\.system\\.prompt\\(\\s*([\"'`])(.*?)\\1")
    private val suggester = Regex("tp\\.system\\.suggester\\(")
    private val today = Regex("tp\\.date\\.(?:now|tomorrow|yesterday|weekday)\\(\\s*(?:([\"'`])(.*?)\\1)?")
    private val created = Regex("tp\\.file\\.(?:creation_date|last_modified_date)\\(\\s*(?:([\"'`])(.*?)\\1)?")
    private val move = Regex("tp\\.file\\.move\\(\\s*([\"'`])([^\"'`]*?)/?\\1\\s*\\+")
    private val called = Regex("\\b(\\w+)\\(\\s*([\"'`])(.*?)\\2")
    private val rename = Regex("tp\\.file\\.rename\\(\\s*(\\w+)")
    private val word = Regex("(?<![.\\w])([A-Za-z_]\\w*)")
    private val helper = Regex("^\\s*(?:async\\s+)?(?:function\\b|\\([^)]*\\)\\s*=>|\\w+\\s*=>)")
    private val sameLine = Regex(";[ \\t]*(?=(?:const|let|var|await|if)\\b)")
    private val keyLine =Regex("^([^\\s#-][^:]*?)\\s*:(.*)$")

    fun isTemplater(text: String) = text.contains("<%")

    fun prepare(raw: String): Prepared {
        val text = raw.replace("\r\n", "\n").removePrefix("﻿")
        val script = run.findAll(text).joinToString("\n") { it.value.removePrefix("<%*").removeSuffix("\n").removeSuffix("%>").removeSuffix("-") }

        // What each variable in the script holds.
        val vars = LinkedHashMap<String, Source>()
        val functions = HashMap<String, String>()
        for (st in statements(script)) {
            val m = assign.find(st) ?: continue
            val name = m.groupValues[1]
            val expr = m.groupValues[2]
            // Only a value that IS a function is a helper. "x.map(t => ...)" is a value that uses one.
            if (helper.containsMatchIn(expr)) { functions[name] = expr; continue }
            vars[name] = sourceOf(expr, vars, functions)
                ?: if (suggester.containsMatchIn(expr)) Source.Ask(name, "") else continue
        }

        fun resolve(s: Source?, depth: Int = 0): Source? = when {
            s is Source.From && depth < 8 -> resolve(vars[s.name], depth + 1).let { r ->
                if (r is Source.Ask && s.list) r.copy(type = "multitext") else r
            }
            else -> s
        }

        val noteName = rename.find(script)?.groupValues?.get(1)?.let { v ->
            when (val r = resolve(vars[v])) {
                is Source.Ask -> fromLabel(r.label)
                Source.Title -> "{{title}}"
                is Source.Date -> clip(r.format)
                else -> null
            }
        }

        // Replace the tags. A property line keeps its key, so the normal reader sees it.
        val questions = LinkedHashMap<String, String>()
        val types = LinkedHashMap<String, String>()
        val plain = text.replace(run, "").trimStart('\n')
        val out = plain.lines().joinToString("\n") { line ->
            if (!line.contains("<%")) return@joinToString line
            val key = keyLine.find(line)?.groupValues?.get(1)?.trim()
            val filled = line.replace(tag) { t ->
                val expr = t.groupValues[1]
                val s = resolve(sourceOf(expr, vars, functions))
                val listy = expr.contains(".map(") && (expr.contains("\\n") || expr.contains("- "))
                when (s) {
                    is Source.Date -> clip(s.format).also { if (key != null) types[key] = "date" }
                    Source.Title -> "{{title}}"
                    is Source.Ask -> {
                        if (key != null) {
                            questions[key] = s.label
                            val type = if (listy) "multitext" else s.type
                            if (type.isNotEmpty()) types[key] = type
                        }
                        ""
                    }
                    else -> ""
                }
            }
            // "key:<% x %>" has no space after the colon once the tag is gone.
            if (key != null) keyLine.find(filled)?.let { m -> "${m.groupValues[1]}: ${m.groupValues[2].trim()}".trimEnd() } ?: filled
            else filled
        }
        val folder = move.find(script)?.groupValues?.get(2)?.trim('/')?.takeIf { it.isNotBlank() }
        return Prepared(out, questions, types, noteName, folder)
    }

    /**
     * The script cut into top-level statements. A statement goes on until the next line that starts at
     * the left edge, so a helper written over many lines, like pickDate, stays in one piece.
     */
    private fun statements(script: String): List<String> {
        val out = ArrayList<StringBuilder>()
        for (line in script.lines()) {
            val starts = line.isNotEmpty() && !line[0].isWhitespace() && line[0] !in ")}].,+?:|&"
            if (out.isEmpty() || starts) out += StringBuilder()
            out.last().append(line).append('\n')
        }
        // Two statements on one line, like "const a = x; await b()", are two statements.
        return out.flatMap { it.toString().split(sameLine) }.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun sourceOf(expr: String, vars: Map<String, Source>, functions: Map<String, String>): Source? {
        prompt.find(expr)?.let { return Source.Ask(it.groupValues[2], "") }
        today.find(expr)?.let { return Source.Date(it.groupValues[2].ifBlank { "YYYY-MM-DD" }) }
        created.find(expr)?.let { return Source.Date(it.groupValues[2].ifBlank { "YYYY-MM-DD" }) }
        if (expr.contains("tp.file.title")) return Source.Title
        // A helper of the script's own, like pickDate("Date published"), asks a question too.
        called.findAll(expr).firstOrNull { it.groupValues[1] in functions }?.let { c ->
            val body = functions[c.groupValues[1]].orEmpty()
            val type = when {
                Regex("type\\s*:\\s*[\"']datetime-local").containsMatchIn(body) -> "datetime"
                Regex("type\\s*:\\s*[\"']date[\"']").containsMatchIn(body) -> "date"
                Regex("type\\s*:\\s*[\"']number").containsMatchIn(body) -> "number"
                else -> ""
            }
            return Source.Ask(c.groupValues[3], type)
        }
        word.findAll(expr).map { it.groupValues[1] }.firstOrNull { it in vars }?.let {
            return Source.From(it, expr.contains(".split("))
        }
        return null
    }

    /** Templater's date format in the clip template language. Both use moment's tokens. */
    private fun clip(format: String) = "{{date|date:\"$format\"}}"

    /**
     * Video data for a question, going by its wording. The last word counts most, so
     * "Source title" is the title and "Date published" is the publish date.
     */
    fun fromLabel(label: String): String? {
        val clean = label.replace(Regex("\\(.*?\\)"), " ").trim()
        VaultTemplate.guess(clean, "")?.let { return it }
        return clean.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }.reversed()
            .firstNotNullOfOrNull { VaultTemplate.guess(it, "") }
    }
}
