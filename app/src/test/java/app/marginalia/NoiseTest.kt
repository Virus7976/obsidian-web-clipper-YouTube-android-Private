package app.marginalia

import app.marginalia.data.Cue
import app.marginalia.data.TranscriptDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseTest {
    private val cues = listOf(
        Cue(0.0, 3.0, "Start with why. >> [music] >> In 1978, the"),
        Cue(3.0, 2.0, "[upbeat music] psychologists spoke."),
    )

    @Test fun marksAreFlaggedButKept() {
        val d = TranscriptDoc.build(cues, emptyList(), 1, false)
        val noisy = d.words.indices.filter { d.words[it].noise }.map { d.text.substring(d.words[it].charStart, d.words[it].charEnd) }
        assertEquals(listOf(">>", "[music]", ">>", "[upbeat", "music]"), noisy)
        // Every word is still there, so highlights saved earlier keep their place.
        assertEquals(13, d.words.size)
    }

    @Test fun hiddenRangesCoverTheMarks() {
        val d = TranscriptDoc.build(cues, emptyList(), 1, false)
        val shown = StringBuilder(d.text)
        d.hidden.sortedByDescending { it.first }.forEach { shown.delete(it.first, it.last + 1) }
        assertFalse(shown.contains("[music]"))
        assertFalse(shown.contains(">>"))
        assertTrue(shown.contains("In 1978"))
    }

    @Test fun savedHighlightTextSkipsTheMarks() {
        val d = TranscriptDoc.build(cues, emptyList(), 1, false)
        assertEquals("Start with why. In 1978, the", d.plainText(0, 8))
    }
}
