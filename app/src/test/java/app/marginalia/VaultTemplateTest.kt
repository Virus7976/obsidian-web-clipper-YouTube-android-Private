package app.marginalia

import app.marginalia.data.AppSettings
import app.marginalia.data.ObsidianText
import app.marginalia.data.SavedVideo
import app.marginalia.data.TemplateProperty
import app.marginalia.data.VideoMeta
import app.marginalia.obsidian.NoteBuilder
import app.marginalia.obsidian.PropertyRule
import app.marginalia.obsidian.VaultTemplate
import app.marginalia.obsidian.VideoData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultTemplateTest {
    private val clipping = """
        ---
        categories:
          - "[[Clippings]]"
        tags:
          - clippings
        author: []
        url: ""
        created: {{date}}
        published:
        topics: []
        ---

    """.trimIndent()
    private val types = mapOf("categories" to "multitext", "tags" to "tags", "created" to "date", "topics" to "multitext", "author" to "multitext", "published" to "date")
    private val video = SavedVideo(meta = VideoMeta("abc", "Time", "Some Channel", published = "2026-10-24T00:00:00+00:00", durationSec = 600))

    private fun prop(t: app.marginalia.data.ClipTemplate, name: String) = t.properties.first { it.name == name }

    @Test
    fun theClippingTemplateSetsItselfUp() {
        val t = VaultTemplate.read(clipping, "Templates/Clipping Template.md", types)
        assertEquals("Clipping Template", t.name)
        assertEquals(listOf("categories", "tags", "author", "url", "created", "published", "topics"), t.properties.map { it.name })
        assertEquals("[[Clippings]]", prop(t, "categories").value)
        assertEquals("multitext", prop(t, "tags").type)
        assertEquals("{{author}}", prop(t, "author").value)
        assertEquals("{{url}}", prop(t, "url").value)
        assertEquals("date", prop(t, "created").type)
        assertEquals("{{published}}", prop(t, "published").value)
        assertEquals("{{content}}", t.noteContentFormat)
        assertEquals(listOf("topics"), t.properties.filter { it.ask }.map { it.name })
    }

    @Test
    fun theFinishedNoteIsValidForObsidian() {
        val t = VaultTemplate.read(clipping, "Templates/Clipping Template.md", types)
        val topics = prop(t, "topics").id
        val md = NoteBuilder.build(t, video, emptyList(), AppSettings(), mapOf(topics to "[[Time]],physics")).markdown
        assertTrue(md, md.contains("categories:\n  - \"[[Clippings]]\"\n"))
        assertTrue(md, md.contains("author:\n  - \"Some Channel\"\n"))
        assertTrue(md, md.contains("url: \"https://www.youtube.com/watch?v=abc\"\n"))
        assertTrue(md, Regex("created: \\d{4}-\\d{2}-\\d{2}\n").containsMatchIn(md))
        assertTrue(md, md.contains("published: 2026-10-24\n"))
        assertTrue(md, md.contains("topics:\n  - \"[[Time]]\"\n  - \"physics\"\n"))
    }

    @Test
    fun aNoteClippedWithThisTemplateIsFoundAgainByItsUrl() {
        val t = VaultTemplate.read(clipping, "Templates/Clipping Template.md", types)
        val md = NoteBuilder.build(t, video, emptyList(), AppSettings()).markdown
        assertEquals("https://www.youtube.com/watch?v=abc", ObsidianText.parse(md).source)
    }

    @Test
    fun windowsLineEndingsReadTheSame() {
        val t = VaultTemplate.read(clipping.replace("\n", "\r\n"), "T.md", types)
        assertEquals(7, t.properties.size)
    }

    @Test
    fun readingTheNoteAgainKeepsYourChoices() {
        val first = VaultTemplate.read(clipping, "T.md", types)
        val mine = first.copy(
            name = "Mine",
            properties = first.properties.map { if (it.name == "author") it.copy(ask = true, value = "") else it },
        )
        val changed = clipping.replace("topics: []", "rating:")
        val again = VaultTemplate.read(changed, "T.md", types, mine)
        assertEquals(first.id, again.id)
        assertEquals("Mine", again.name)
        assertTrue(prop(again, "author").ask)
        assertTrue(prop(again, "rating").ask)
        assertFalse(again.properties.any { it.name == "topics" })
    }

    @Test
    fun obsidianDateTagsWork() {
        val t = VaultTemplate.read("---\nday: {{date:YYYY}}\nat: {{time}}\n---\nOn {{date}}", "T.md", emptyMap())
        val note = NoteBuilder.build(t, video, emptyList(), AppSettings())
        val year = java.time.LocalDate.now().year.toString()
        assertEquals("{{date|date:\"YYYY\"}}", prop(t, "day").value)
        assertTrue(note.markdown, note.markdown.contains("day: $year"))
        assertTrue(note.markdown, note.content.startsWith("On $year-"))
    }

    @Test
    fun aBodyWithoutClipTextStillGetsIt() {
        assertEquals("# Notes\n\n{{content}}", VaultTemplate.read("---\na: 1\n---\n# Notes\n", "T.md", emptyMap()).noteContentFormat)
        assertEquals("{{highlights}}", VaultTemplate.read("{{highlights}}", "T.md", emptyMap()).noteContentFormat)
    }

    @Test
    fun aNumberPropertyGetsMinutesNotAClock() {
        assertEquals("{{minutes}}", VaultTemplate.guess("runtime", "number"))
        assertEquals("{{duration}}", VaultTemplate.guess("duration", ""))
        assertNull(VaultTemplate.guess("topics", "multitext"))
    }

    @Test
    fun automaticFollowsTheValue() {
        assertEquals("checkbox", NoteBuilder.inferType("true"))
        assertEquals("date", NoteBuilder.inferType("2026-11-01"))
        assertEquals("datetime", NoteBuilder.inferType("2026-11-01T10:00:00"))
        assertEquals("number", NoteBuilder.inferType("12.5"))
        assertEquals("text", NoteBuilder.inferType("12:03"))
        assertEquals("text", NoteBuilder.inferType("1f"))
    }

    @Test
    fun theRuleReadsFromTheProperty() {
        fun rule(value: String, ask: Boolean = false) = VideoData.ruleOf(TemplateProperty("i", "n", value, "", ask))
        assertEquals(PropertyRule.Ask, rule("{{title}}", ask = true))
        assertEquals(PropertyRule.Empty, rule(""))
        assertEquals(PropertyRule.Data, rule("{{published|date:\"YYYY\"}}"))
        assertEquals(PropertyRule.Fixed, rule("{{title}} by {{author}}"))
        assertEquals(PropertyRule.Fixed, rule("[[Clippings]]"))
    }

    @Test
    fun eachTypeOffersOnlyDataThatFits() {
        assertTrue(VideoData.forType("checkbox").isEmpty())
        assertEquals(setOf("published", "date"), VideoData.forType("date").map { it.key }.toSet())
        assertTrue(VideoData.forType("number").any { it.key == "minutes" })
        assertFalse(VideoData.forType("number").any { it.key == "title" })
        assertEquals(VideoData.fields.size, VideoData.forType("").size)
    }

    @Test
    fun everyDataFieldHasAValue() {
        val vars = NoteBuilder.variables(video, emptyList(), AppSettings())
        VideoData.fields.forEach { assertTrue(it.key, vars.containsKey(it.key)) }
    }

    @Test
    fun vaultSettingsAreRead() {
        assertEquals("date", VaultTemplate.types("""{"types":{"created":"date"}}""")["created"])
        assertEquals("Templates", VaultTemplate.folder("""{"folder":"Templates/"}"""))
        assertTrue(VaultTemplate.types("not json").isEmpty())
    }
}
