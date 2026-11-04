package app.marginalia.data

import java.util.UUID

/** All the add, recolour and erase rules for highlights, kept apart from the screen so they are easy to test. */
object Highlights {
    private const val EPS = 0.05

    /** Earlier first. At the same start the longer one comes first, so one inside another is later and wins. */
    private val order = compareBy<Highlight>({ it.startSec }, { -it.endSec })

    private fun covers(h: Highlight, i: Int, w: Word) =
        if (h.w0 >= 0) i in h.w0..h.w1 else w.start >= h.startSec - EPS && w.start < h.endSec - EPS

    /** For every word, the colour index of the highlight on it, or -1. Where highlights sit inside each other, the inner one wins. */
    fun colorByWord(words: List<Word>, highlights: List<Highlight>): IntArray {
        val out = IntArray(words.size) { -1 }
        for (h in highlights.sortedByDescending { it.endSec - it.startSec }) {
            if (h.w0 >= 0) {
                for (i in h.w0..minOf(h.w1, words.lastIndex)) out[i] = h.color
            } else {
                for (i in words.indices) if (covers(h, i, words[i])) out[i] = h.color
            }
        }
        return out
    }

    /** The words of each highlight that has a note, with its colour. */
    fun notedRuns(words: List<Word>, highlights: List<Highlight>): List<Pair<IntRange, Int>> =
        highlights.filter { it.note.isNotBlank() }.mapNotNull { h ->
            val run = if (h.w0 >= 0) h.w0..minOf(h.w1, words.lastIndex) else wordsOf(h, words)
            run?.let { it to h.color }
        }

    /** Each highlight's words and colour, longest first, so one inside another is drawn on top of it. */
    fun runs(words: List<Word>, highlights: List<Highlight>): List<Pair<IntRange, Int>> =
        highlights.mapNotNull { h ->
            val r = if (h.w0 >= 0) h.w0..minOf(h.w1, words.lastIndex) else wordsOf(h, words)
            r?.takeIf { !it.isEmpty() }?.let { it to h.color }
        }.sortedByDescending { it.first.last - it.first.first }

    /** The highlight to open when word [i] is tapped: the shortest one on it, so one inside another can be reached. */
    fun at(highlights: List<Highlight>, i: Int): Highlight? =
        highlights.filter { it.w0 >= 0 && i in it.w0..it.w1 }.minByOrNull { it.w1 - it.w0 }

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
        createdAt = System.currentTimeMillis(),
    )

    /**
     * Highlight words i0..i1 in [color]. A highlight made inside or across another one is its own highlight,
     * and the other one is left as it was. The exact same words highlighted again only change colour.
     */
    fun apply(current: List<Highlight>, words: List<Word>, text: (Int, Int) -> String, i0: Int, i1: Int, color: Int): List<Highlight> {
        if (words.isEmpty()) return current
        val lo = minOf(i0, i1).coerceIn(words.indices)
        val hi = maxOf(i0, i1).coerceIn(words.indices)
        val same = current.firstOrNull { (if (it.w0 >= 0) it.w0..it.w1 else wordsOf(it, words)) == lo..hi }
        if (same != null) return current.map { if (it.id == same.id) it.copy(color = color) else it }
        return (current + make(words, text, lo, hi, color)).sortedWith(order)
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
        return out.sortedWith(order)
    }

    /** Move the ends of highlight [id] to words i0..i1. It keeps its id, colour, note and tags. Other highlights stay as they are. */
    fun resize(current: List<Highlight>, words: List<Word>, text: (Int, Int) -> String, id: String, i0: Int, i1: Int): List<Highlight> {
        val h = current.firstOrNull { it.id == id } ?: return current
        if (words.isEmpty()) return current
        val lo = minOf(i0, i1).coerceIn(words.indices)
        val hi = maxOf(i0, i1).coerceIn(words.indices)
        val moved = make(words, text, lo, hi, h.color, h.note).copy(id = h.id, createdAt = h.createdAt, tags = h.tags)
        return current.map { if (it.id == id) moved else it }.sortedWith(order)
    }

    /** Does any highlight touch words i0..i1? Decides whether a drag paints or erases. */
    fun touches(current: List<Highlight>, words: List<Word>, i0: Int, i1: Int): Boolean =
        colorByWord(words, current).let { c -> (i0..i1).any { c.getOrElse(it) { -1 } >= 0 } }
}
