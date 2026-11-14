package app.marginalia

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import app.marginalia.data.SavedVideo
import app.marginalia.data.VideoMeta
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoneTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun aLibraryFromBeforeThisFeatureLoadsAsNotDone() {
        val old = json.encodeToString(SavedVideo.serializer(), SavedVideo(meta = VideoMeta("a", "t", "c")))
            .replace(Regex(",?\"doneAt\":\\d+"), "")
        assertFalse(old.contains("doneAt"))
        assertFalse(json.decodeFromString(SavedVideo.serializer(), old).done)
    }

    @Test
    fun doneKeepsYourPlaceSoItCanBeUndone() {
        val v = SavedVideo(meta = VideoMeta("a", "t", "c", durationSec = 600), positionSec = 120.0, doneAt = 5L)
        assertTrue(v.done)
        assertEquals(120.0, v.resumeSec, 0.0)
    }
}
