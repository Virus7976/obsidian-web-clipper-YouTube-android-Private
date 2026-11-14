package app.marginalia

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import app.obsi.core.data.Highlights
import app.obsi.core.data.Word
import org.junit.Assert.assertEquals
import org.junit.Test

/** The pull handles on an open highlight. They move one end only, and never leave it empty. */
class PullTest {
    // Words of four letters with one space between: word i is at characters 5i until 5i+4.
    private val words = List(10) { Word(start = it.toDouble(), charStart = it * 5, charEnd = it * 5 + 4) }
    private val r = 3..6

    @Test
    fun startHandleTakesTheWordUnderTheFinger() {
        assertEquals(1..6, Highlights.pull(words, r, start = true, off = 6))
        // in the gap after word 1, it takes word 2
        assertEquals(2..6, Highlights.pull(words, r, start = true, off = 9))
    }

    @Test
    fun endHandleTakesTheLastWordBeforeTheFinger() {
        assertEquals(3..8, Highlights.pull(words, r, start = false, off = 42))
        // right at the start of word 8, word 8 is not in yet
        assertEquals(3..7, Highlights.pull(words, r, start = false, off = 40))
    }

    @Test
    fun theEndsNeverPassEachOther() {
        // the start handle pulled far past the end stops on the last word
        assertEquals(6..6, Highlights.pull(words, r, start = true, off = 48))
        // the end handle pulled before the start stops on the first word
        assertEquals(3..3, Highlights.pull(words, r, start = false, off = 0))
    }

    @Test
    fun theOtherEndNeverMoves() {
        for (off in 0..60) {
            assertEquals(6, Highlights.pull(words, r, start = true, off = off).last)
            assertEquals(3, Highlights.pull(words, r, start = false, off = off).first)
        }
    }

    @Test
    fun pastTheLastWordIsTheLastWord() {
        assertEquals(3..9, Highlights.pull(words, r, start = false, off = 500))
        assertEquals(6..6, Highlights.pull(words, r, start = true, off = 500))
    }
}
