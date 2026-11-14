package app.marginalia

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import app.obsi.core.data.Highlight
import app.obsi.core.data.Highlights
import app.obsi.core.data.Word
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
    fun growingOverAnotherHighlightLeavesItAlone() {
        val other = Highlight("other", 8.0, 12.0, "x", color = 1, w0 = 8, w1 = 11)
        val out = Highlights.resize(listOf(h, other), words, text, "keep", 4, 9)
        assertEquals(listOf(4..9, 8..11), out.map { it.w0..it.w1 })
        assertEquals(listOf(3, 1), out.map { it.color })
    }

    @Test
    fun aHighlightInsideAnotherIsItsOwn() {
        val inner = Highlights.apply(listOf(h), words, text, 5, 5, 1)
        assertEquals(listOf(4..6, 5..5), inner.map { it.w0..it.w1 })
        assertEquals("my note", inner.first { it.id == "keep" }.note)
        // the inner one wins its word, and a tap there opens it, not the outer one
        assertEquals(listOf(3, 1, 3), Highlights.colorByWord(words, inner).slice(4..6))
        assertEquals(5..5, Highlights.at(inner, 5)!!.let { it.w0..it.w1 })
        assertEquals("keep", Highlights.at(inner, 4)!!.id)
        // drawn outer first, so the inner one sits on top
        assertEquals(listOf(4..6, 5..5), Highlights.runs(words, inner).map { it.words })
    }

    @Test
    fun theSameWordsAgainOnlyChangeColour() {
        val out = Highlights.apply(listOf(h), words, text, 4, 6, 2)
        assertEquals(1, out.size)
        assertEquals(listOf("keep", 2, "my note"), listOf(out[0].id, out[0].color, out[0].note))
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
