package app.marginalia

import app.marginalia.data.AppSettings
import app.marginalia.data.Highlight
import app.marginalia.data.NoteRef
import app.marginalia.data.ObsidianText
import app.marginalia.data.Omni
import app.marginalia.data.OmniKind
import app.marginalia.data.SavedVideo
import app.marginalia.data.VaultIndex
import app.marginalia.data.VideoMeta
import app.marginalia.obsidian.ClipperExport
import app.marginalia.obsidian.ClipperImport
import app.marginalia.obsidian.NoteBuilder
import app.marginalia.obsidian.Updater
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultTest {
    private val settings = ClipperImport.apply(File("src/main/assets/default_clipper_settings.json").readText(), AppSettings())

    private val index = VaultIndex(
        "V", 0L,
        notes = listOf(
            NoteRef("Clippings/Eugenics debate.md", "u1", 5, listOf("history", "debate"), "https://www.youtube.com/watch?v=abc12345678", listOf("Intro"), "genetics and poverty talk"),
            NoteRef("Projects/Kitchen remodel.md", "u2", 9, listOf("home"), "", emptyList(), "cabinet order and tile"),
            NoteRef("Daily/2026-01-01.md", "u3", 1, emptyList(), "", emptyList(), "went for a walk"),
        ),
        folders = listOf("Clippings", "Projects", "Daily"),
    )

    @Test
    fun parsesTagsSourceAndHeadings() {
        val p = ObsidianText.parse("---\ntags:\n  - alpha\n  - Beta\nsource: \"https://x.test/a\"\n---\n\n# Title\nSome #inline text and a `#code` bit.\n")
        assertEquals(setOf("alpha", "Beta", "inline"), p.tags.toSet())
        assertEquals("https://x.test/a", p.source)
        assertEquals(listOf("Title"), p.headings)
    }

    @Test
    fun findsTheNoteForAVideoByItsId() {
        assertEquals("Clippings/Eugenics debate.md", index.noteForSource("https://youtu.be/abc12345678")?.path)
        assertEquals(null, index.noteForSource("https://youtu.be/zzzzzzzzzzz"))
    }

    @Test
    fun searchForgivesSkippedLettersAndFindsBodyText() {
        val kinds = setOf(OmniKind.Note, OmniKind.Folder, OmniKind.Tag)
        assertEquals("Kitchen remodel", Omni.search(index, "kchn rmdl", kinds).first().title)
        val body = Omni.search(index, "tile", kinds).first()
        assertEquals("Kitchen remodel", body.title)
        assertTrue(body.snippet != null)
        assertEquals(OmniKind.Tag, Omni.search(index, "#hist", kinds).first().kind)
        assertEquals(OmniKind.Folder, Omni.search(index, "/proj", kinds).first().kind)
        assertTrue(Omni.search(index, "nothingmatchesthis", setOf(OmniKind.Note)).isEmpty())
    }

    @Test
    fun offersToCreateAMissingTag() {
        val r = Omni.search(index, "#brandnew", setOf(OmniKind.Tag, OmniKind.Create))
        assertTrue(r.any { it.kind == OmniKind.Create && it.value == "brandnew" })
    }

    @Test
    fun updateAddsOnlyWhatIsMissing() {
        val video = SavedVideo(
            VideoMeta("abc12345678", "Eugenics debate", "Destiny", "", "2021-06-04", 100),
            highlights = listOf(
                Highlight("a", 3.0, 4.0, "genetics and poverty"),
                Highlight("b", 9.0, 10.0, "a brand new thought", color = 2),
            ),
        )
        val note = NoteBuilder.build(settings.templates.first(), video, emptyList(), settings)
        val old = "---\ntitle: \"Eugenics debate\"\ntags:\n  - history\n---\n\nMy own words.\n\n> ==genetics and poverty==\n"
        val m = Updater.merge(old, note, video, settings)
        assertTrue(m.text.contains("My own words."))
        assertEquals(1, m.addedHighlights)
        assertTrue(m.text.contains("a brand new thought"))
        assertEquals(1, Regex("genetics and poverty").findAll(m.text).count())
        // a second pass changes nothing
        assertFalse(Updater.merge(m.text, note, video, settings).changed)
    }

    @Test
    fun exportImportsBackTheSame() {
        val text = ClipperExport.build(settings)
        val back = ClipperImport.apply(text, AppSettings())
        assertEquals(settings.templates.map { it.name }, back.templates.map { it.name })
        assertEquals(settings.templates.map { it.properties.map { p -> p.name to p.value } }, back.templates.map { it.properties.map { p -> p.name to p.value } })
        assertEquals(settings.vaults, back.vaults)
        assertTrue(text.contains("\"template_list\""))
    }
}
