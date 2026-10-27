package app.marginalia.data

import kotlin.math.ln
import kotlin.math.min

enum class OmniKind { Note, Folder, Tag, Create }

/** One row in the omni search. [matched] are the positions in [title] to draw bold. */
data class OmniItem(
    val kind: OmniKind,
    val title: String,
    val subtitle: String,
    val matched: List<Int> = emptyList(),
    val snippet: String? = null,
    val snippetMatch: IntRange? = null,
    val score: Double = 0.0,
    val count: Int = 0,
    /** Note path, folder path or tag name. For Create it is the thing to create. */
    val value: String = "",
    val createKind: OmniKind? = null,
)

/**
 * Search in the spirit of Obsidian's Omnisearch: type a few letters of anything.
 * It matches note names, folders, tags, headings and the text inside notes, forgives
 * skipped letters, and puts the best match first. Several words must all match.
 */
object Omni {
    class Fuzzy(val score: Double, val at: List<Int>)

    /** fzf-style subsequence match. Null when the letters are not all there in order. */
    fun fuzzy(text: String, pattern: String): Fuzzy? {
        if (pattern.isEmpty()) return Fuzzy(0.0, emptyList())
        val t = text.lowercase()
        val p = pattern.lowercase()
        // exact substring beats everything
        val sub = t.indexOf(p)
        if (sub >= 0) {
            val atWordStart = sub == 0 || !t[sub - 1].isLetterOrDigit()
            val score = 100.0 + p.length * 8 + (if (sub == 0) 40 else 0) + (if (atWordStart) 25 else 0) - sub * 0.15
            return Fuzzy(score, (sub until sub + p.length).toList())
        }
        val at = ArrayList<Int>(p.length)
        var ti = 0
        var score = 0.0
        var prev = -2
        for (ch in p) {
            var found = -1
            while (ti < t.length) {
                if (t[ti] == ch) {
                    found = ti
                    ti++
                    break
                }
                ti++
            }
            if (found < 0) return null
            val boundary = found == 0 || !t[found - 1].isLetterOrDigit() || (text[found].isUpperCase() && !text[found - 1].isUpperCase())
            score += 8.0
            if (found == prev + 1) score += 14.0
            if (boundary) score += 18.0
            score -= min(found - (if (prev >= 0) prev else 0), 6) * 0.8
            at += found
            prev = found
        }
        return Fuzzy(score, at)
    }

    private fun snippetAround(text: String, token: String): Pair<String, IntRange>? {
        val i = text.indexOf(token, ignoreCase = true)
        if (i < 0) return null
        val from = (i - 48).coerceAtLeast(0)
        val to = (i + token.length + 90).coerceAtMost(text.length)
        val lead = if (from > 0) "…" else ""
        val s = lead + text.substring(from, to) + if (to < text.length) "…" else ""
        val start = lead.length + (i - from)
        return s to (start until start + token.length)
    }

    fun search(
        index: VaultIndex?,
        raw: String,
        kinds: Set<OmniKind>,
        recentTags: List<String> = emptyList(),
        hide: Set<String> = emptySet(),
        limit: Int = 60,
    ): List<OmniItem> {
        if (index == null) return emptyList()
        var q = raw.trim()
        var only: OmniKind? = null
        when {
            q.startsWith("#") && OmniKind.Tag in kinds -> { only = OmniKind.Tag; q = q.drop(1).trim() }
            q.startsWith("/") && OmniKind.Folder in kinds -> { only = OmniKind.Folder; q = q.drop(1).trim() }
        }
        val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = ArrayList<OmniItem>()

        // ---- tags
        if ((only == null || only == OmniKind.Tag) && OmniKind.Tag in kinds) {
            for ((tag, count) in index.tags) {
                if (tag in hide) continue
                val f = if (tokens.isEmpty()) Fuzzy(0.0, emptyList()) else fuzzy(tag, q.replace(" ", "")) ?: continue
                val recent = recentTags.indexOf(tag).let { if (it < 0) 0.0 else 30.0 - it }
                out += OmniItem(OmniKind.Tag, "#$tag", "$count note${if (count == 1) "" else "s"}", f.at.map { it + 1 },
                    score = f.score + ln(1.0 + count) * 4 + recent + (if (only == OmniKind.Tag) 30 else 0), count = count, value = tag)
            }
        }
        // ---- folders
        if ((only == null || only == OmniKind.Folder) && OmniKind.Folder in kinds) {
            if (tokens.isEmpty()) {
                out += OmniItem(OmniKind.Folder, "Vault root", "Top of the vault", score = 1000.0, value = "")
            }
            for (folder in index.folders) {
                val f = if (tokens.isEmpty()) Fuzzy(0.0, emptyList()) else fuzzy(folder, q) ?: continue
                val depth = folder.count { it == '/' }
                val name = folder.substringAfterLast('/')
                val nameFuzzy = if (tokens.isEmpty()) null else fuzzy(name, q)
                val shown = if (nameFuzzy != null) nameFuzzy.score * 1.4 else f.score
                // the match list refers to the full path, so show the path as the title
                out += OmniItem(OmniKind.Folder, folder, "${index.notes.count { it.folder == folder }} notes",
                    f.at, score = shown - depth * 0.5, value = folder)
            }
        }
        // ---- notes
        if ((only == null) && OmniKind.Note in kinds) {
            for (n in index.notes) {
                if (n.path in hide) continue
                var total = 0.0
                var titleHit: Fuzzy? = null
                var snippet: Pair<String, IntRange>? = null
                var ok = true
                if (tokens.isEmpty()) {
                    total = n.mtime / 1e12
                } else {
                    for (tk in tokens) {
                        val inName = fuzzy(n.name, tk)
                        val inPath = if (inName == null) fuzzy(n.path, tk) else null
                        val inTag = n.tags.firstOrNull { it.contains(tk.removePrefix("#"), ignoreCase = true) }
                        val inHead = n.headings.firstOrNull { it.contains(tk, ignoreCase = true) }
                        val inText = n.text.contains(tk, ignoreCase = true)
                        val best = when {
                            inName != null -> { if (titleHit == null) titleHit = inName; inName.score * 3 }
                            inPath != null -> inPath.score * 1.1
                            inTag != null -> 90.0
                            inHead != null -> 70.0
                            inText -> {
                                if (snippet == null) snippet = snippetAround(n.text, tk)
                                30.0 + min(Regex(Regex.escape(tk), RegexOption.IGNORE_CASE).findAll(n.text).count(), 6) * 5
                            }
                            else -> { ok = false; 0.0 }
                        }
                        if (!ok) break
                        total += best
                    }
                }
                if (!ok) continue
                val name = n.name
                val matched = if (titleHit != null && tokens.size == 1) titleHit.at else emptyList()
                out += OmniItem(
                    OmniKind.Note, name, n.folder.ifEmpty { "Vault root" }, matched,
                    snippet?.first, snippet?.second, total, value = n.path,
                )
            }
        }
        out.sortByDescending { it.score }
        val result = out.take(limit).toMutableList()
        // offer to create what was typed, when it does not exist yet
        val typed = q.trim()
        if (typed.isNotEmpty() && OmniKind.Create in kinds) {
            if (OmniKind.Tag in kinds && (only == null || only == OmniKind.Tag) && !typed.contains(' ')) {
                val clean = typed.removePrefix("#")
                if (index.tags.keys.none { it.equals(clean, true) }) {
                    result.add(result.size,
                        OmniItem(OmniKind.Create, "Add #$clean", "New tag", value = clean, createKind = OmniKind.Tag))
                }
            }
            if (OmniKind.Folder in kinds && only != OmniKind.Tag && index.folders.none { it.equals(typed, true) }) {
                result += OmniItem(OmniKind.Create, "Create folder \"$typed\"", "New folder", value = typed, createKind = OmniKind.Folder)
            }
        }
        return result
    }
}
