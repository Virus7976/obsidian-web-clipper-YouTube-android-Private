package app.marginalia.obsidian

import app.marginalia.data.TemplateProperty

/** One thing the app knows about a video, and the property types it can go into. */
class DataField(val key: String, val label: String, val types: Set<String>)

enum class PropertyRule { Ask, Data, Fixed, Empty }

object VideoData {
    private val words = setOf("text", "multitext")
    private val dates = words + setOf("date", "datetime")
    private val numbers = words + "number"

    val fields = listOf(
        DataField("title", "Title", words),
        DataField("author", "Channel", words),
        DataField("url", "Link", words),
        DataField("published", "Date published", dates),
        DataField("date", "Date you clip it", dates),
        DataField("description", "Description, first line", words),
        DataField("fullDescription", "Description, all of it", words),
        DataField("image", "Thumbnail", words),
        DataField("duration", "Length", words),
        DataField("minutes", "Length in minutes", numbers),
        DataField("seconds", "Length in seconds", numbers),
        DataField("highlights", "Highlights", words),
        DataField("highlightCount", "Number of highlights", numbers),
        DataField("transcript", "Transcript", words),
        DataField("words", "Word count", numbers),
        DataField("chapters", "Chapters", words),
        DataField("tags", "Video tags", words),
        DataField("category", "Category", words),
        DataField("videoId", "Video ID", words),
        DataField("embed", "Video embed", words),
        DataField("site", "Site", words),
    )

    private val single = Regex("^\\{\\{\\s*([A-Za-z]+)\\s*(\\|[^{}]*)?\\}\\}$")

    /** Automatic takes anything. A checkbox takes no video data. */
    fun forType(type: String): List<DataField> =
        if (type.isBlank()) fields else fields.filter { type in it.types }

    /** The field behind a value that is one video data tag and nothing else. */
    fun fieldOf(value: String): DataField? =
        single.find(value.trim())?.groupValues?.get(1)?.let { key -> fields.firstOrNull { it.key == key } }

    /** What to store for a field. A date in a text box reads better as a plain day. */
    fun valueFor(field: DataField, type: String): String =
        if (field.key in setOf("date", "published") && type != "date" && type != "datetime") "{{${field.key}|date:\"YYYY-MM-DD\"}}"
        else "{{${field.key}}}"

    fun ruleOf(p: TemplateProperty): PropertyRule = when {
        p.ask -> PropertyRule.Ask
        p.value.isBlank() -> PropertyRule.Empty
        fieldOf(p.value) != null -> PropertyRule.Data
        else -> PropertyRule.Fixed
    }
}
