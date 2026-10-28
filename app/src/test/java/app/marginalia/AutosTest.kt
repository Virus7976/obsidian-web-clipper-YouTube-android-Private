package app.marginalia

import app.marginalia.data.AppSettings
import app.marginalia.data.TemplateProperty
import app.marginalia.obsidian.NoteBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class AutosTest {
    @Test fun addsTagsAndJoinsExisting() {
        val base = listOf(TemplateProperty("1", "tags", "clippings,#youtube", "multitext"))
        val out = NoteBuilder.withAutos(base, listOf("#read", "youtube"), emptyList())
        assertEquals("clippings,youtube,read", out.single().value)
    }

    @Test fun addsTagsPropertyWhenMissing() {
        val out = NoteBuilder.withAutos(emptyList(), listOf("read"), emptyList())
        assertEquals("tags", out.single().name)
        assertEquals("read", out.single().value)
    }

    @Test fun autoPropFillsBlankButKeepsFilled() {
        val base = listOf(TemplateProperty("1", "status", ""), TemplateProperty("2", "type", "video"))
        val autos = listOf(TemplateProperty("a", "Status", "inbox"), TemplateProperty("b", "type", "x"), TemplateProperty("c", "area", "learn"))
        val out = NoteBuilder.withAutos(base, emptyList(), autos)
        assertEquals("inbox", out[0].value)
        assertEquals("video", out[1].value)
        assertEquals("area", out[2].name)
    }

    @Test fun linkedVaultFallsBackToTheOnlyVault() {
        val s = AppSettings(vaultFolders = mapOf("Liam's Git Vault" to "uri"))
        assertEquals("Liam's Git Vault", s.linkedVault("other"))
        assertEquals("Liam's Git Vault", s.linkedVault("Liam's Git Vault"))
    }
}
