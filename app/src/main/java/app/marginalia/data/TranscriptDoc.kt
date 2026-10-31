package app.marginalia.data

/** [noise] marks caption marks that are not speech, such as ">>" and "[music]". They stay in the word list so saved highlights keep their place, but the reader hides them. */
data class Word(val start: Double, val charStart: Int, val charEnd: Int, val noise: Boolean = false)

/** A block of speech. [charStart] is where its timestamp begins, [firstWord] is its first spoken word. */
data class Para(val charStart: Int, val charEnd: Int, val startSec: Double, val firstWord: Int, val lastWord: Int)

/**
 * The transcript as one piece of text, with a map from every character to the word and the
 * time it belongs to. The reader shows [text] in a single selectable view, so a selection can
 * start and end on any word, across lines and chapters.
 */
class TranscriptDoc(
    val text: String,
    val words: List<Word>,
    val paras: List<Para>,
    /** Character ranges of chapter headings. */
    val headings: List<IntRange>,
    /** Character ranges of the small timestamps. */
    val stamps: List<IntRange>,
) {
    /** The word that contains [c], or the next one after it. -1 if there is none. */
    fun wordAtChar(c: Int): Int {
        if (words.isEmpty()) return -1
        var lo = 0
        var hi = words.lastIndex
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid].charEnd > c) {
                ans = mid
                hi = mid - 1
            } else lo = mid + 1
        }
        return ans
    }

    /** The word being spoken at [t] seconds. -1 before the first word. */
    fun wordAtTime(t: Double): Int {
        var lo = 0
        var hi = words.lastIndex
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid].start <= t + 0.05) {
                ans = mid
                lo = mid + 1
            } else hi = mid - 1
        }
        return ans
    }

    /** Words touched by the character selection [a, b). Null if the selection holds no spoken word. */
    fun wordsInSelection(a: Int, b: Int): IntRange? {
        val first = wordAtChar(a)
        if (first < 0) return null
        var last = wordAtChar(b - 1).let { if (it < 0) words.lastIndex else it }
        if (words[last].charStart >= b) last--
        return if (last >= first && words[first].charStart < b) first..last else null
    }

    /** Character ranges the reader draws as nothing: caption marks, and a paragraph that holds only marks. */
    val hidden: List<IntRange> by lazy {
        val out = ArrayList<IntRange>()
        for (p in paras) {
            if (p.firstWord in words.indices && p.lastWord in words.indices && (p.firstWord..p.lastWord).all { words[it].noise }) {
                out += p.charStart until p.charEnd
            }
        }
        for (w in words) {
            if (!w.noise) continue
            val end = if (w.charEnd < text.length && text[w.charEnd] == ' ') w.charEnd + 1 else w.charEnd
            if (end > w.charStart) out += w.charStart until end
        }
        out
    }

    fun paraOfWord(i: Int): Int = paras.indexOfLast { it.firstWord <= i }

    fun plainText(i0: Int, i1: Int): String {
        val sb = StringBuilder()
        for (i in i0..i1) {
            if (words[i].noise) continue
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(text, words[i].charStart, words[i].charEnd)
        }
        return sb.toString()
    }

    companion object {
        private val sentenceEnd = Regex("[.?!][\"')\\]]?$")
        private val spaces = Regex("\\s+")

        fun build(cues: List<Cue>, chapters: List<Chapter>, lengthSetting: Int, showStamps: Boolean): TranscriptDoc {
            val (target, max) = when (lengthSetting) {
                0 -> 90 to 150
                2 -> 300 to 480
                else -> 170 to 280
            }
            // 1. every word with its time
            class W(val text: String, val t: Double, val noise: Boolean = false)
            val all = ArrayList<W>(cues.size * 6)
            for (c in cues) {
                val tokens = c.text.split(spaces).filter { it.isNotEmpty() }
                if (tokens.isEmpty()) continue
                // ">>" and tags like [music] are marks, not speech. [upbeat music] is two tokens.
                var inTag = false
                val noise = BooleanArray(tokens.size) { i ->
                    val tk = tokens[i]
                    if (inTag || tk.startsWith("[")) { inTag = !tk.contains("]"); true }
                    else tk.all { it == '>' }
                }
                val offs = c.offs?.takeIf { it.size == tokens.size }
                if (offs != null) {
                    tokens.forEachIndexed { i, tk -> all += W(tk, c.start + offs[i], noise[i]) }
                } else {
                    val total = tokens.sumOf { it.length + 1 }.toDouble()
                    var cum = 0
                    val span = if (c.dur > 0) c.dur else tokens.size * 0.35
                    tokens.forEachIndexed { i, tk ->
                        all += W(tk, c.start + span * (cum / total), noise[i])
                        cum += tk.length + 1
                    }
                }
            }

            // 2. paragraphs, headings, timestamps
            val sb = StringBuilder()
            val words = ArrayList<Word>(all.size)
            val paras = ArrayList<Para>()
            val headings = ArrayList<IntRange>()
            val stamps = ArrayList<IntRange>()
            var chapter = -1
            var paraStartChar = -1
            var paraFirst = -1
            var paraStartSec = 0.0
            var paraLen = 0
            var lastT = -100.0
            var lastText = ""

            fun closePara() {
                if (paraFirst >= 0 && words.size > paraFirst) {
                    paras += Para(paraStartChar, sb.length, paraStartSec, paraFirst, words.lastIndex)
                    sb.append("\n\n")
                }
                paraFirst = -1
                paraLen = 0
            }

            for (w in all) {
                val ci = chapters.indexOfLast { it.start <= w.t + 0.5 }
                val newChapter = ci != chapter && ci >= 0
                val silence = paraFirst >= 0 && w.t - lastT > 4.0
                val long = paraFirst >= 0 && ((paraLen >= target && sentenceEnd.containsMatchIn(lastText)) || paraLen >= max)
                if (paraFirst >= 0 && (newChapter || silence || long)) closePara()
                if (newChapter) {
                    chapter = ci
                    val h0 = sb.length
                    sb.append("— ").append(chapters[ci].title)
                    headings += h0 until sb.length
                    sb.append("\n")
                }
                if (paraFirst < 0) {
                    paraStartChar = sb.length
                    paraStartSec = w.t
                    if (showStamps) {
                        val s0 = sb.length
                        sb.append(YouTube.formatTime(w.t))
                        stamps += s0 until sb.length
                        sb.append("  ")
                    }
                    paraFirst = words.size
                } else sb.append(' ')
                val c0 = sb.length
                sb.append(w.text)
                words += Word(w.t, c0, sb.length, w.noise)
                paraLen += w.text.length + 1
                lastT = w.t
                lastText = w.text
            }
            closePara()
            val text = sb.toString().trimEnd()
            return TranscriptDoc(text, words, paras, headings, stamps)
        }
    }
}
