package app.marginalia.data

import java.util.UUID

/** All the add/erase rules for highlights, kept apart from the screen so they are easy to reason about. */
object Highlights {
    private const val EPS = 0.05

    fun covers(h: Highlight, line: Line) = line.start >= h.startSec - EPS && line.start < h.endSec - EPS

    fun markedLines(lines: List<Line>, highlights: List<Highlight>): BooleanArray {
        val out = BooleanArray(lines.size)
        if (highlights.isEmpty()) return out
        for (i in lines.indices) out[i] = highlights.any { covers(it, lines[i]) }
        return out
    }

    private fun textOf(lines: List<Line>, from: Double, to: Double) =
        lines.filter { it.start >= from - EPS && it.start < to - EPS }.joinToString(" ") { it.text }

    /** Highlight lines lo..hi. Anything it overlaps is merged into one. */
    fun add(current: List<Highlight>, lines: List<Line>, lo: Int, hi: Int): List<Highlight> {
        if (lines.isEmpty()) return current
        val from = lines[lo.coerceIn(lines.indices)].start
        val to = lines[hi.coerceIn(lines.indices)].end
        val overlap = current.filter { it.startSec < to && it.endSec > from }
        val start = minOf(from, overlap.minOfOrNull { it.startSec } ?: from)
        val end = maxOf(to, overlap.maxOfOrNull { it.endSec } ?: to)
        val merged = Highlight(
            id = UUID.randomUUID().toString(),
            startSec = start,
            endSec = end,
            text = textOf(lines, start, end),
            note = overlap.map { it.note }.filter { it.isNotBlank() }.joinToString("\n"),
        )
        return ((current - overlap.toSet()) + merged).sortedBy { it.startSec }
    }

    /** Un-highlight lines lo..hi. A highlight that is only partly erased is split, not dropped. */
    fun erase(current: List<Highlight>, lines: List<Line>, lo: Int, hi: Int): List<Highlight> {
        if (lines.isEmpty()) return current
        val from = lines[lo.coerceIn(lines.indices)].start
        val to = lines[hi.coerceIn(lines.indices)].end
        val out = ArrayList<Highlight>()
        for (h in current) {
            if (!(h.startSec < to && h.endSec > from)) {
                out += h
                continue
            }
            val kept = lines.indices.filter { covers(h, lines[it]) && !(lines[it].start >= from - EPS && lines[it].start < to - EPS) }
            var runStart = -1
            var prev = -2
            var first = true
            fun flush(endIdx: Int) {
                if (runStart < 0) return
                val seg = lines.subList(runStart, endIdx + 1)
                out += Highlight(
                    id = UUID.randomUUID().toString(),
                    startSec = seg.first().start,
                    endSec = seg.last().end,
                    text = seg.joinToString(" ") { it.text },
                    note = if (first) h.note else "",
                )
                first = false
            }
            for (idx in kept) {
                if (idx != prev + 1) {
                    flush(prev)
                    runStart = idx
                }
                prev = idx
            }
            flush(prev)
        }
        return out.sortedBy { it.startSec }
    }
}
