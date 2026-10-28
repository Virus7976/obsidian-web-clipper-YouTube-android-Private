package app.marginalia

import app.marginalia.data.AppSettings
import app.marginalia.data.ClipTemplate
import app.marginalia.data.SavedVideo
import app.marginalia.data.VideoMeta
import app.marginalia.obsidian.NoteBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultChoiceTest {
    private val video = SavedVideo(VideoMeta("abc12345678", "T", "A"))
    private val template = ClipTemplate(id = "t", name = "Big Idea", vault = "OtherDeviceVault")

    @Test fun settingsVaultBeatsTheTemplateVault() {
        val s = AppSettings(vault = "Liam's Git Vault", vaults = listOf("Liam's Git Vault"))
        assertEquals("Liam's Git Vault", NoteBuilder.build(template, video, emptyList(), s).vault)
    }

    @Test fun templateVaultIsOnlyAFallback() {
        assertEquals("OtherDeviceVault", NoteBuilder.build(template, video, emptyList(), AppSettings()).vault)
    }
}
