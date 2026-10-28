package app.marginalia.obsidian

import app.marginalia.data.AppSettings
import app.marginalia.data.ClipTemplate
import app.marginalia.data.TemplateProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject

/** Reads an Obsidian Web Clipper settings export and maps it onto this app's settings. */
object ClipperImport {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.b(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.f(k: String): Float? = (this[k] as? JsonPrimitive)?.floatOrNull
    private fun JsonObject.o(k: String): JsonObject? = this[k] as? JsonObject

    fun apply(text: String, base: AppSettings): AppSettings {
        val root = json.parseToJsonElement(text).jsonObject
        var out = base

        root.o("general_settings")?.let { g ->
            g.s("saveBehavior")?.let { out = out.copy(saveBehavior = it) }
        }
        root.o("highlighter_settings")?.let { h ->
            out = out.copy(
                highlighterEnabled = h.b("highlighterEnabled") ?: out.highlighterEnabled,
                alwaysShowHighlights = h.b("alwaysShowHighlights") ?: out.alwaysShowHighlights,
                highlightBehavior = h.s("highlightBehavior") ?: out.highlightBehavior,
            )
        }
        root.o("reader_settings")?.let { r ->
            out = out.copy(
                appearance = r.s("appearance") ?: out.appearance,
                autoScroll = r.b("autoScroll") ?: out.autoScroll,
                pinPlayer = r.b("pinPlayer") ?: out.pinPlayer,
                highlightActiveLine = r.b("highlightActiveLine") ?: out.highlightActiveLine,
                // The web clipper counts in desktop pixels. A phone reads comfortably about 1.4x larger.
                readerFontSize = r.f("fontSize")?.let { (it * 1.4f).coerceIn(14f, 28f) } ?: out.readerFontSize,
                lineHeight = r.f("lineHeight") ?: out.lineHeight,
                maxWidth = r.f("maxWidth") ?: out.maxWidth,
            )
        }

        val types = HashMap<String, String>()
        (root["property_types"] as? JsonArray)?.forEach { e ->
            val o = e as? JsonObject ?: return@forEach
            val name = o.s("name") ?: return@forEach
            types[name] = o.s("type") ?: "text"
        }
        if (types.isNotEmpty()) out = out.copy(propertyTypes = out.propertyTypes + types)

        // Vault names in an imported file are not added to the vault list. They belong to the other
        // device. Only vaults you add in Settings are yours.

        val order = (root["template_list"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val found = HashMap<String, ClipTemplate>()
        for ((key, value) in root) {
            if (!key.startsWith("template_") || key == "template_list") continue
            val o = value as? JsonObject ?: continue
            val id = o.s("id") ?: continue
            val props = (o["properties"] as? JsonArray)?.mapNotNull { pe ->
                val po = pe as? JsonObject ?: return@mapNotNull null
                TemplateProperty(
                    id = po.s("id") ?: java.util.UUID.randomUUID().toString(),
                    name = po.s("name") ?: return@mapNotNull null,
                    value = po.s("value") ?: "",
                    type = po.s("type") ?: "",
                )
            }.orEmpty()
            found[id] = ClipTemplate(
                id = id,
                name = o.s("name") ?: "Template",
                behavior = o.s("behavior") ?: "create",
                noteNameFormat = o.s("noteNameFormat") ?: "{{title}}",
                path = o.s("path") ?: "Clippings",
                noteContentFormat = o.s("noteContentFormat") ?: "{{content}}",
                vault = o.s("vault") ?: "",
                properties = props,
            )
        }
        val ordered = order.mapNotNull { found.remove(it) } + found.values
        if (ordered.isNotEmpty()) {
            val keep = out.templates.filter { t -> ordered.none { it.id == t.id } }
            val all = ordered + keep
            out = out.copy(
                templates = all,
                selectedTemplateId = out.selectedTemplateId.takeIf { id -> all.any { it.id == id } } ?: all.first().id,
            )
        }
        return out
    }
}
