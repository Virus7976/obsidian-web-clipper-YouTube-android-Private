package app.marginalia

import app.marginalia.data.AppSettings
import app.marginalia.data.Cue
import app.marginalia.data.Highlights
import app.marginalia.data.SavedVideo
import app.marginalia.data.TranscriptDoc
import app.marginalia.data.VideoMeta
import app.marginalia.data.YouTube
import app.marginalia.obsidian.ClipperImport
import app.marginalia.obsidian.NoteBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTest {
    private val json = File("src/main/assets/default_clipper_settings.json").readText()
    private val settings = ClipperImport.apply(json, AppSettings())

    private val cues = listOf(
        Cue(0.0, 3.0, "Welcome back everybody."),
        Cue(3.0, 3.0, "Today we are talking about eugenics", listOf(0.0, 0.4, 0.8, 1.0, 1.4, 1.8)),
        Cue(6.0, 4.0, "and why it matters."),
        Cue(10.0, 4.0, "First point is genetics and poverty."),
        Cue(40.0, 3.0, "Now capitalism."),
        Cue(43.0, 3.0, "It is a system."),
    )
    private val desc = "Date: 4 Jun, 2021\n\n0:00 Intro\n0:10 Genetics and poverty\n0:40 Capitalism\n"
    private val chapters = YouTube.parseChapters(desc)
    private val lines = YouTube.toLines(cues, 1, chapters)
    private val doc = TranscriptDoc.build(cues, chapters, 1, true)
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
        assertEquals(listOf("Intro", "Genetics and poverty", "Capitalism"), chapters.map { it.title })
        assertTrue(lines.size >= 3)
    }

    @Test
    fun docKnowsEveryWord() {
        println(doc.text)
        assertEquals(25, doc.words.size)
        // word timing from the cue offsets is honoured
        val about = doc.words.first { doc.text.substring(it.charStart, it.charEnd) == "about" }
        assertEquals(3.0 + 1.4, about.start, 0.001)
        // a character in the middle of "eugenics" maps back to that word
        val c = doc.text.indexOf("eugenics") + 3
        assertEquals("eugenics", doc.text.substring(doc.words[doc.wordAtChar(c)].charStart, doc.words[doc.wordAtChar(c)].charEnd))
        // the spoken word at 3.5 seconds is "are"? no: offsets 3.0 Today, 3.4 we, 3.8 are, 4.0 talking
        val at = doc.wordAtTime(3.9)
        assertEquals("are", doc.text.substring(doc.words[at].charStart, doc.words[at].charEnd))
        // chapter headings and timestamps are in the text but are not words
        assertEquals(3, doc.headings.size)
        assertTrue(doc.stamps.isNotEmpty())
        assertTrue(doc.words.none { w -> doc.headings.any { w.charStart in it } || doc.stamps.any { w.charStart in it } })
    }

    @Test
    fun selectionSnapsToWords() {
        val a = doc.text.indexOf("talking") + 2
        val b = doc.text.indexOf("eugenics") + 3
        val r = doc.wordsInSelection(a, b)
        assertNotNull(r)
        assertEquals("talking about eugenics", doc.plainText(r!!.first, r.last))
        // a selection that only touches a heading holds no word
        val h = doc.headings.first()
        assertEquals(null, doc.wordsInSelection(h.first, h.first + 3))
    }

    @Test
    fun defaultTemplateFrontmatter() {
        val t = settings.templates.first { it.name == "Default" }
        val note = NoteBuilder.build(t, base, lines, settings)
        assertEquals("Clippings", note.folder)
        assertEquals("JF Stumps TheSerfs w Eugenics Argument EXTREMELY TRICKY DEBATE", note.name)
        assertTrue(note.markdown.contains("author:\n  - \"[[Destiny]]\""))
        assertTrue(note.markdown.contains("published: 2021-06-04\n"))
        assertTrue(note.markdown.contains("tags:\n  - \"clippings\""))
    }

    @Test
    fun highlightsUseObsidianSyntax() {
        val s = settings
        val word = { i: Int -> doc.text.substring(doc.words[i].charStart, doc.words[i].charEnd) }
        val yellow = Highlights.apply(emptyList(), doc.words, doc::plainText, 1, 3, 0)
        val both = Highlights.apply(yellow, doc.words, doc::plainText, 10, 12, 1)
        assertEquals(2, both.size)
        val t = s.templates.first { it.name == "Big Idea" }
        val note = NoteBuilder.build(t, base.copy(highlights = both), lines, s)
        println("=== BIG IDEA ===\n${note.markdown}")
        assertTrue(note.content.contains("> ==🟡${doc.plainText(1, 3)}=="))
        assertTrue(note.content.contains("==🔴${doc.plainText(10, 12)}=="))
        assertTrue(note.content.contains("&t="))
        assertTrue(word(1).isNotEmpty())
    }

    @Test
    fun highlightPaintRecolourAndErase() {
        val txt = doc::plainText
        val a = Highlights.apply(emptyList(), doc.words, txt, 2, 6, 0)
        assertEquals(1, a.size)
        // paint over the middle in red: the yellow one is cut in two, red sits between
        val b = Highlights.apply(a, doc.words, txt, 4, 4, 1)
        assertEquals(3, b.size)
        val colors = Highlights.colorByWord(doc.words, b)
        assertEquals(listOf(0, 0, 1, 0, 0), (2..6).map { colors[it] })
        // erase the red word and one yellow neighbour
        val c = Highlights.erase(b, doc.words, txt, 3, 4)
        val cc = Highlights.colorByWord(doc.words, c)
        assertEquals(listOf(0, -1, -1, 0), listOf(cc[2], cc[3], cc[4], cc[5]))
        assertTrue(Highlights.touches(c, doc.words, 2, 3))
        assertTrue(!Highlights.touches(c, doc.words, 7, 9))
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
