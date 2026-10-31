package app.marginalia.data

enum class HitKind { Video, Highlight, Playlist, Channel }

/**
 * One row in the library search. A highlight hit carries the video it is in and the second it starts at,
 * so a tap can open the reader right there.
 */
data class Hit(
    val kind: HitKind,
    val title: String,
    val subtitle: String = "",
    /** Positions in [title] to draw bold. */
    val matched: List<Int> = emptyList(),
    val snippet: String? = null,
    val snippetMatch: IntRange? = null,
    val score: Double = 0.0,
    val videoId: String = "",
    val sec: Double = 0.0,
    /** Playlist id or channel address. */
    val ref: String = "",
    val color: Int = 0,
)

/**
 * Search over everything you saved, in the spirit of Obsidian's Omnisearch.
 * Type a few letters of a title, a channel, a tag, or a phrase you highlighted or wrote.
 * Skipped letters are forgiven in names. Several words must all match. `#word` looks only at tags and categories.
 */
object LibrarySearch {
    private const val MAX_HIGHLIGHTS = 40

    fun search(
        raw: String,
        videos: List<SavedVideo>,
        playlists: List<Playlist>,
        channels: List<Channel>,
        limit: Int = 90,
    ): List<Hit> {
        var q = raw.trim()
        if (q.isEmpty()) return emptyList()
        val tagsOnly = q.startsWith("#")
        if (tagsOnly) q = q.drop(1).trim()
        val tokens = q.split(' ', '\t', '\n').filter { it.isNotEmpty() }
        if (tokens.isEmpty() && !tagsOnly) return emptyList()
        val out = ArrayList<Hit>()

        // ---- videos
        for (v in videos) {
            var total = 0.0
            var ok = true
            var at: List<Int> = emptyList()
            for (tk in tokens) {
                val inTitle = Omni.fuzzy(v.meta.title, tk)
                val inAuthor = Omni.fuzzy(v.meta.author, tk)
                val inTag = v.tags.any { it.contains(tk, true) } || v.category.contains(tk, true)
                val best = when {
                    tagsOnly -> if (inTag) 120.0 else -1.0
                    inTitle != null -> { if (tokens.size == 1) at = inTitle.at; inTitle.score * 3 }
                    inAuthor != null -> inAuthor.score * 1.5
                    inTag -> 90.0
                    else -> -1.0
                }
                if (best < 0) { ok = false; break }
                total += best
            }
            if (tokens.isEmpty() && tagsOnly) total = if (v.tags.isNotEmpty() || v.category.isNotBlank()) 50.0 else { ok = false; 0.0 }
            if (!ok) continue
            if (v.pinned) total += 6
            out += Hit(
                HitKind.Video, v.meta.title,
                listOf(v.meta.author, v.category).filter { it.isNotBlank() }.joinToString(" · "),
                at, score = total, videoId = v.meta.id,
            )
        }
        if (tagsOnly) return out.sortedByDescending { it.score }.take(limit)

        // ---- highlights and notes
        val phrase = q.lowercase()
        val marks = ArrayList<Hit>()
        for (v in videos) for (h in v.highlights) {
            val text = h.text
            val note = h.note
            if (text.isBlank() && note.isBlank()) continue
            if (!tokens.all { text.contains(it, true) || note.contains(it, true) }) continue
            var s = 40.0
            if (text.lowercase().contains(phrase) || note.lowercase().contains(phrase)) s += 120.0
            if (note.isNotBlank() && tokens.any { note.contains(it, true) }) s += 12.0
            s += tokens.sumOf { tk -> minOf(count(text, tk) + count(note, tk), 4) * 3.0 }
            val from = if (text.isNotBlank() && tokens.any { text.contains(it, true) }) text else note
            val snip = snippetAround(from, tokens.first { from.contains(it, true) })
            marks += Hit(
                HitKind.Highlight, v.meta.title,
                YouTube.formatTime(h.startSec) + if (note.isNotBlank() && from === text) " · " + note.take(80) else "",
                snippet = snip?.first ?: from.take(140), snippetMatch = snip?.second, score = s,
                videoId = v.meta.id, sec = h.startSec, color = h.color,
            )
        }
        out += marks.sortedByDescending { it.score }.take(MAX_HIGHLIGHTS)

        // ---- playlists
        for (p in playlists) {
            var total = 0.0
            var ok = true
            for (tk in tokens) {
                val f = Omni.fuzzy(p.title, tk)
                if (f == null) { ok = false; break }
                total += f.score * 2.5
            }
            if (ok) out += Hit(HitKind.Playlist, p.title, "${p.videoIds.size} video${if (p.videoIds.size == 1) "" else "s"}", score = total, ref = p.id)
        }

        // ---- channels
        for (c in channels) {
            var total = 0.0
            var ok = true
            for (tk in tokens) {
                val f = Omni.fuzzy(c.name, tk)
                if (f == null) { ok = false; break }
                total += f.score * 2.5
            }
            if (ok) out += Hit(HitKind.Channel, c.name, "Following", score = total, ref = c.url)
        }

        return out.sortedWith(compareByDescending<Hit> { it.score }.thenBy { it.title.lowercase() }).take(limit)
    }

    private fun count(text: String, token: String): Int {
        var n = 0
        var i = text.indexOf(token, 0, ignoreCase = true)
        while (i >= 0) { n++; i = text.indexOf(token, i + token.length, ignoreCase = true) }
        return n
    }

    /** A short piece of [text] around the first [token], with the match marked by its range. */
    private fun snippetAround(text: String, token: String): Pair<String, IntRange>? {
        val i = text.indexOf(token, ignoreCase = true)
        if (i < 0) return null
        val from = (i - 50).coerceAtLeast(0)
        val to = (i + token.length + 100).coerceAtMost(text.length)
        val lead = if (from > 0) "…" else ""
        val s = lead + text.substring(from, to) + if (to < text.length) "…" else ""
        val start = lead.length + (i - from)
        return s to (start until start + token.length)
    }
}
