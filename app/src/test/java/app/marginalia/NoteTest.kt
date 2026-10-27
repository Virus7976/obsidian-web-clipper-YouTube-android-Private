package app.marginalia

import app.marginalia.data.AppSettings
import app.marginalia.data.Cue
import app.marginalia.data.Highlights
import app.marginalia.data.SavedVideo
import app.marginalia.data.VideoMeta
import app.marginalia.data.YouTube
import app.marginalia.obsidian.ClipperImport
import app.marginalia.obsidian.NoteBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTest {
    private val json = File("src/main/assets/default_clipper_settings.json").readText()
    private val settings = ClipperImport.apply(json, AppSettings())

    private val cues = listOf(
        Cue(0.0, 3.0, "Welcome back everybody."),
        Cue(3.0, 3.0, "Today we are talking about eugenics"),
        Cue(6.0, 4.0, "and why it matters."),
        Cue(10.0, 4.0, "First point is genetics and poverty."),
        Cue(40.0, 3.0, "Now capitalism."),
        Cue(43.0, 3.0, "It is a system."),
    )
    private val desc = "Date: 4 Jun, 2021\n\n0:00 Intro\n0:10 Genetics and poverty\n0:40 Capitalism\n"
    private val chapters = YouTube.parseChapters(desc)
    private val lines = YouTube.toLines(cues, 1, chapters)
    private val base = SavedVideo(
        meta = VideoMeta("abc12345678", "JF Stumps TheSerfs w/ Eugenics Argument | EXTREMELY TRICKY DEBATE", "Destiny", desc, "2021-06-04T10:00:00-07:00", 7678),
        chapters = chapters,
    )

    @Test
    fun importReadsTemplatesInOrder() {
        assertEquals(listOf("Big Idea", "Default"), settings.templates.map { it.name })
        assertEquals(listOf("Liam's Git Vault"), settings.vaults)
        assertEquals("addToObsidian", settings.saveBehavior)
        assertEquals("multitext", settings.propertyTypes["author"])
    }

    @Test
    fun chaptersAndLines() {
        assertEquals(3, chapters.size)
        assertEquals(listOf("Intro", "Genetics and poverty", "Capitalism"), chapters.map { it.title })
        assertTrue(lines.size >= 3)
        // no line crosses a chapter boundary
        lines.forEach { l -> val a = chapters.indexOfLast { it.start <= l.start + 0.5 }; assertTrue(a >= 0) }
    }

    @Test
    fun defaultTemplateFrontmatter() {
        val t = settings.templates.first { it.name == "Default" }
        val note = NoteBuilder.build(t, base, lines, settings)
        println("=== DEFAULT ===\n${note.path}\n${note.markdown}")
        assertEquals("Clippings", note.folder)
        assertEquals("JF Stumps TheSerfs w Eugenics Argument EXTREMELY TRICKY DEBATE", note.name)
        assertTrue(note.markdown.contains("author:\n  - \"[[Destiny]]\""))
        assertTrue(note.markdown.contains("published: 2021-06-04\n"))
        assertTrue(note.markdown.contains("tags:\n  - \"clippings\""))
        assertTrue(note.markdown.contains("source: \"https://www.youtube.com/watch?v=abc12345678\""))
    }

    @Test
    fun bigIdeaWithHighlights() {
        val t = settings.templates.first { it.name == "Big Idea" }
        val hl = Highlights.add(emptyList(), lines, 1, 1)
        val video = base.copy(highlights = hl)
        val note = NoteBuilder.build(t, video, lines, settings)
        println("=== BIG IDEA ===\n${note.path}\n${note.markdown}")
        assertTrue(note.markdown.contains("Big Idea:\n"))
        assertTrue(note.markdown.contains("> "))
        assertTrue(Regex("\\[\\[\\d{4}-\\d{2}-\\d{2}]]\\s*$").containsMatchIn(note.markdown))
        assertTrue(note.content.contains("&t="))
    }

    @Test
    fun highlightAddMergeErase() {
        val a = Highlights.add(emptyList(), lines, 0, 1)
        val b = Highlights.add(a, lines, 1, 2)
        assertEquals(1, b.size)
        val marked = Highlights.markedLines(lines, b)
        assertTrue(marked[0] && marked[1] && marked[2])
        val c = Highlights.erase(b, lines, 1, 1)
        val m2 = Highlights.markedLines(lines, c)
        assertTrue(m2[0]); assertTrue(!m2[1]); assertTrue(m2[2])
        assertEquals(2, c.size)
    }

    @Test
    fun urlParsing() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://youtu.be/dQw4w9WgXcQ?t=5",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ", "https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
            "dQw4w9WgXcQ",
        ).forEach { assertEquals(it, "dQw4w9WgXcQ", YouTube.extractId(it)) }
        assertEquals(null, YouTube.extractId("https://example.com"))
    }
}
