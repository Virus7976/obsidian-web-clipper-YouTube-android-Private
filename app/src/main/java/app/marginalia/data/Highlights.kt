package app.marginalia.data

import java.util.UUID

/** All the add, recolour and erase rules for highlights, kept apart from the screen so they are easy to test. */
object Highlights {
    private const val EPS = 0.05

    private fun covers(h: Highlight, i: Int, w: Word) =
        if (h.w0 >= 0) i in h.w0..h.w1 else w.start >= h.startSec - EPS && w.start < h.endSec - EPS

    /** For every word, the colour index of the highlight on it, or -1. Later highlights win. */
    fun colorByWord(words: List<Word>, highlights: List<Highlight>): IntArray {
        val out = IntArray(words.size) { -1 }
        for (h in highlights) {
            if (h.w0 >= 0) {
                for (i in h.w0..minOf(h.w1, words.lastIndex)) out[i] = h.color
            } else {
                for (i in words.indices) if (covers(h, i, words[i])) out[i] = h.color
            }
        }
        return out
    }

    private fun wordsOf(h: Highlight, words: List<Word>): IntRange? {
        val idx = words.indices.filter { covers(h, it, words[it]) }
        return if (idx.isEmpty()) null else idx.first()..idx.last()
    }

    private fun make(words: List<Word>, text: (Int, Int) -> String, i0: Int, i1: Int, color: Int, note: String = "") = Highlight(
        id = UUID.randomUUID().toString(),
        startSec = words[i0].start,
        endSec = words.getOrNull(i1 + 1)?.start ?: (words[i1].start + 1.0),
        text = text(i0, i1),
        note = note,
        color = color,
        w0 = i0,
        w1 = i1,
    )

    /** Paint words i0..i1 in [color]. Whatever was there is replaced; a partly covered highlight is cut, not lost. */
    fun apply(current: List<Highlight>, words: List<Word>, text: (Int, Int) -> String, i0: Int, i1: Int, color: Int): List<Highlight> {
        if (words.isEmpty()) return current
        val lo = i0.coerceIn(words.indices)
        val hi = i1.coerceIn(words.indices)
        val cleared = erase(current, words, text, lo, hi)
        return (cleared + make(words, text, lo, hi, color)).sortedBy { it.startSec }
    }

    /** Take highlights off words i0..i1. A highlight that is only partly erased is split into pieces. */
    fun erase(current: List<Highlight>, words: List<Word>, text: (Int, Int) -> String, i0: Int, i1: Int): List<Highlight> {
        if (words.isEmpty()) return current
        val out = ArrayList<Highlight>()
        for (h in current) {
            val r = wordsOf(h, words)
            if (r == null || r.last < i0 || r.first > i1) {
                out += h
                continue
            }
            var first = true
            fun piece(a: Int, b: Int) {
                if (b < a) return
                out += make(words, text, a, b, h.color, if (first) h.note else "")
                first = false
            }
            piece(r.first, minOf(r.last, i0 - 1))
            piece(maxOf(r.first, i1 + 1), r.last)
        }
        return out.sortedBy { it.startSec }
    }

    /** Does any highlight touch words i0..i1? Decides whether a drag paints or erases. */
    fun touches(current: List<Highlight>, words: List<Word>, i0: Int, i1: Int): Boolean =
        colorByWord(words, current).let { c -> (i0..i1).any { c.getOrElse(it) { -1 } >= 0 } }
}
