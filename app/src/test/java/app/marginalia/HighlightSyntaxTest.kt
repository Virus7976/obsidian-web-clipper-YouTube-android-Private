package app.marginalia

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import app.obsi.core.data.AppSettings
import app.obsi.core.data.Highlight
import app.obsi.core.data.HighlightColors
import app.marginalia.obsidian.NoteBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightSyntaxTest {
    private fun h(color: Int) = Highlight(id = "x", startSec = 0.0, endSec = 1.0, text = "sdf", color = color)

    @Test fun redIsACircleRightAfterTheMarks() {
        val red = HighlightColors.indexOfFirst { it.name == "Red" }
        assertEquals("==🔴sdf==", NoteBuilder.styled(h(red), AppSettings()))
    }

    @Test fun everyColourHasItsOwnCircle() {
        val circles = HighlightColors.map { it.emoji }
        assertEquals(circles.size, circles.toSet().size)
        assertEquals(listOf("Yellow", "Red", "Green", "Blue", "Purple", "Orange"), HighlightColors.map { it.name })
    }

    @Test fun plainSyntaxKeepsJustTheText() {
        assertEquals("sdf", NoteBuilder.styled(h(1), AppSettings(highlightSyntax = "plain")))
    }
}
