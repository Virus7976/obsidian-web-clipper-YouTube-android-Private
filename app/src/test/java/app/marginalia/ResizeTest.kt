package app.marginalia

import app.marginalia.data.Highlight
import app.marginalia.data.Highlights
import app.marginalia.data.Word
import org.junit.Assert.assertEquals
import org.junit.Test

class ResizeTest {
    private val words = List(20) { Word(start = it.toDouble(), charStart = it * 5, charEnd = it * 5 + 4) }
    private val text = { a: Int, b: Int -> (a..b).joinToString(" ") { "w$it" } }
    private val h = Highlight("keep", 4.0, 7.0, "w4 w5 w6", note = "my note", color = 3, w0 = 4, w1 = 6, createdAt = 42L, tags = listOf("idea"))

    @Test
    fun longerKeepsTheNoteColourTagsAndId() {
        val out = Highlights.resize(listOf(h), words, text, "keep", 2, 9)
        assertEquals(1, out.size)
        val r = out[0]
        assertEquals(listOf("keep", "my note", 3, 2, 9, 42L, listOf("idea")), listOf(r.id, r.note, r.color, r.w0, r.w1, r.createdAt, r.tags))
        assertEquals("w2 w3 w4 w5 w6 w7 w8 w9", r.text)
        assertEquals(2.0, r.startSec, 0.0)
        assertEquals(10.0, r.endSec, 0.0)
    }

    @Test
    fun shorterDropsTheWordsLeftOut() {
        val r = Highlights.resize(listOf(h), words, text, "keep", 5, 5).single()
        assertEquals(listOf(5, 5, "w5"), listOf(r.w0, r.w1, r.text))
    }

    @Test
    fun growingOverAnotherHighlightCutsIt() {
        val other = Highlight("other", 8.0, 12.0, "x", color = 1, w0 = 8, w1 = 11)
        val out = Highlights.resize(listOf(h, other), words, text, "keep", 4, 9)
        assertEquals(listOf(4..9, 10..11), out.map { it.w0..it.w1 })
        assertEquals(listOf(3, 1), out.map { it.color })
    }

    @Test
    fun handlesDraggedPastEachOtherStillWork() {
        val r = Highlights.resize(listOf(h), words, text, "keep", 9, 3).single()
        assertEquals(3..9, r.w0..r.w1)
    }

    @Test
    fun anUnknownIdChangesNothing() {
        assertEquals(listOf(h), Highlights.resize(listOf(h), words, text, "gone", 0, 3))
    }
}
