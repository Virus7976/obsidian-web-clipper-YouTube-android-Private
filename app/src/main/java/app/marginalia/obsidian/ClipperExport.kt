package app.marginalia.obsidian

import app.marginalia.data.AppSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Writes the templates in the same shape as an Obsidian Web Clipper settings export,
 * so the file imports straight back into the browser clipper (Settings, Import).
 * Only the parts this app owns are written. The clipper fills in the rest with its defaults.
 */
object ClipperExport {
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun build(s: AppSettings): String {
        val root = buildJsonObject {
            put("general_settings", buildJsonObject { put("saveBehavior", s.saveBehavior) })
            put("highlighter_settings", buildJsonObject {
                put("alwaysShowHighlights", s.alwaysShowHighlights)
                put("highlightBehavior", s.highlightBehavior)
                put("highlighterEnabled", s.highlighterEnabled)
            })
            put("migrationVersion", 1)

            val names = s.templates.flatMap { t -> t.properties.map { it.name } }.distinct()
            put("property_types", buildJsonArray {
                names.forEach { n ->
                    add(buildJsonObject {
                        put("name", n)
                        put("type", s.propertyTypes[n] ?: s.templates.flatMap { it.properties }.firstOrNull { it.name == n && it.type.isNotBlank() }?.type ?: "text")
                    })
                }
            })
            put("reader_settings", buildJsonObject {
                put("appearance", s.appearance)
                put("autoScroll", s.autoScroll)
                put("highlightActiveLine", s.highlightActiveLine)
                put("pinPlayer", s.pinPlayer)
                put("lineHeight", s.lineHeight)
                put("maxWidth", s.maxWidth)
            })
            for (t in s.templates) {
                put("template_${t.id}", buildJsonObject {
                    put("id", t.id)
                    put("name", t.name)
                    put("behavior", t.behavior)
                    put("noteNameFormat", t.noteNameFormat)
                    put("path", t.path)
                    put("noteContentFormat", t.noteContentFormat)
                    put("context", "")
                    if (t.vault.isNotBlank()) put("vault", t.vault)
                    put("properties", buildJsonArray {
                        t.properties.forEach { p ->
                            add(buildJsonObject {
                                put("id", p.id)
                                put("name", p.name)
                                put("value", p.value)
                            })
                        }
                    })
                    put("triggers", JsonArray(emptyList()))
                })
            }
            put("template_list", JsonArray(s.templates.map { JsonPrimitive(it.id) }))
            put("vaults", JsonArray(s.vaults.map { JsonPrimitive(it) }))
        }
        return pretty.encodeToString(JsonObject.serializer(), root)
    }
}
