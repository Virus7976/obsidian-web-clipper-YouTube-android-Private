package app.marginalia

import app.marginalia.data.Highlight
import app.marginalia.data.Highlights
import app.marginalia.data.Word
import org.junit.Assert.assertEquals
import org.junit.Test

class NotedRunsTest {
    private val words = List(10) { Word(start = it.toDouble(), charStart = it * 5, charEnd = it * 5 + 4) }

    @Test
    fun onlyHighlightsWithANoteGetATick() {
        val runs = Highlights.notedRuns(
            words,
            listOf(
                Highlight("a", 2.0, 3.0, "x", note = "why this matters", color = 2, w0 = 2, w1 = 3),
                Highlight("b", 5.0, 6.0, "y", note = "   ", w0 = 5, w1 = 6),
                Highlight("c", 7.0, 8.0, "z", w0 = 7, w1 = 8),
            ),
        )
        assertEquals(listOf((2..3) to 2), runs)
    }

    @Test
    fun aRunNeverPassesTheLastWord() {
        val runs = Highlights.notedRuns(words, listOf(Highlight("a", 8.0, 12.0, "x", note = "n", w0 = 8, w1 = 40)))
        assertEquals(listOf((8..9) to 0), runs)
    }
}
