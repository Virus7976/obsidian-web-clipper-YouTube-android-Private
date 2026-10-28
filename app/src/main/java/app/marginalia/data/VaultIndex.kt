package app.marginalia.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** One note in the vault, with just enough read from it to search and to find duplicates. */
@Serializable
data class NoteRef(
    val path: String, // relative to the vault, e.g. "Clippings/My note.md"
    val uri: String,
    val mtime: Long,
    val tags: List<String> = emptyList(),
    val source: String = "",
    val headings: List<String> = emptyList(),
    /** The first part of the note body, plain, for searching inside notes. */
    val text: String = "",
) {
    val name: String get() = path.substringAfterLast('/').removeSuffix(".md")
    val folder: String get() = path.substringBeforeLast('/', "")
}

@Serializable
data class VaultIndex(
    val vault: String,
    val scannedAt: Long = 0L,
    val notes: List<NoteRef> = emptyList(),
    val folders: List<String> = emptyList(),
) {
    @kotlinx.serialization.Transient
    private var tagCache: Map<String, Int>? = null

    /** Every tag in the vault with the number of notes that use it. */
    val tags: Map<String, Int>
        get() = tagCache ?: run {
            val m = LinkedHashMap<String, Int>()
            val casing = HashMap<String, String>()
            for (n in notes) for (t in n.tags) {
                val key = t.lowercase()
                val shown = casing.getOrPut(key) { t }
                m[shown] = (m[shown] ?: 0) + 1
            }
            tagCache = m
            m
        }

    fun noteForSource(url: String): NoteRef? =
        notes.firstOrNull { it.source.isNotBlank() && sameVideo(it.source, url) }

    private fun sameVideo(a: String, b: String): Boolean {
        val x = YouTube.extractId(a)
        val y = YouTube.extractId(b)
        return if (x != null && y != null) x == y else a.trim() == b.trim()
    }
}

/** Reads the parts of a markdown note that Obsidian users care about: properties, tags, headings. */
object ObsidianText {
    data class Parsed(
        val frontmatter: String,
        val body: String,
        val tags: List<String>,
        val source: String,
        val headings: List<String>,
    )

    private val inlineTag = Regex("""(?<![\p{L}\p{N}_/&#\[\]])#([\p{L}\p{N}_/-]*[\p{L}_/-][\p{L}\p{N}_/-]*)""")
    private val codeFence = Regex("```.*?```", RegexOption.DOT_MATCHES_ALL)
    private val inlineCode = Regex("`[^`\n]*`")

    fun splitFrontmatter(text: String): Pair<String, String> {
        val t = text.removePrefix("﻿")
        if (!t.startsWith("---")) return "" to t
        val end = Regex("\n---[ \t]*(\n|$)").find(t, 3) ?: return "" to t
        return t.substring(4.coerceAtMost(t.length), end.range.first + 1) to t.substring(end.range.last + 1).trimStart('\n')
    }

    fun parse(text: String): Parsed {
        val (fm, body) = splitFrontmatter(text)
        val tags = LinkedHashMap<String, String>()
        fun add(raw: String) {
            val t = raw.trim().trim('"', '\'').removePrefix("#").trim()
            if (t.isNotEmpty() && !t.all { it.isDigit() }) tags.putIfAbsent(t.lowercase(), t)
        }
        // frontmatter: tags: [a, b]   tags: a, b   tags:\n  - a
        val lines = fm.lines()
        var i = 0
        while (i < lines.size) {
            val m = Regex("^(?:tags|tag)\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE).find(lines[i])
            if (m != null) {
                val rest = m.groupValues[1].trim()
                if (rest.isNotEmpty()) {
                    rest.removePrefix("[").removeSuffix("]").split(',', ' ').forEach(::add)
                } else {
                    while (i + 1 < lines.size && Regex("^\\s*-\\s+.*").matches(lines[i + 1])) {
                        add(lines[i + 1].trim().removePrefix("-"))
                        i++
                    }
                }
            }
            i++
        }
        val source = Regex("^source\\s*:\\s*(.+)$", RegexOption.MULTILINE).find(fm)?.groupValues?.get(1)?.trim()?.trim('"', '\'') ?: ""

        val plain = body.replace(codeFence, " ").replace(inlineCode, " ")
        inlineTag.findAll(plain).forEach { add(it.groupValues[1]) }
        val headings = plain.lines().filter { it.startsWith("#") && it.getOrNull(it.takeWhile { c -> c == '#' }.length) == ' ' }
            .map { it.trimStart('#').trim() }.take(12)
        return Parsed(fm, body, tags.values.toList(), source, headings)
    }

    /** Body text with the markup taken out, for the search index. */
    fun plain(body: String, max: Int): String =
        body.replace(codeFence, " ")
            .replace(Regex("!?\\[\\[([^\\]|]*\\|)?([^\\]]*)]]")) { it.groupValues[2] }
            .replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)")) { it.groupValues[1] }
            .replace(Regex("[*_=>`#~|]"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(max)
}

object VaultScanner {
    private const val READ_BYTES = 40_000
    private const val TEXT_CHARS = 1_600

    private class Entry(val path: String, val uri: Uri, val mtime: Long)

    /**
     * Reads the vault. Phase one lists every folder (quick). Phase two reads the new or changed notes,
     * eight at a time. Throws when the folder cannot be read at all, so the caller can say so.
     */
    suspend fun scan(context: Context, vault: String, tree: Uri, previous: VaultIndex?, onProgress: (Int) -> Unit = {}): VaultIndex {
        val resolver = context.contentResolver
        val oldByPath = previous?.notes?.associateBy { it.path }.orEmpty()
        val entries = ArrayList<Entry>()
        val folders = ArrayList<String>()
        val queue = ArrayDeque<Pair<String, String>>() // document id, relative path
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        queue += rootId to ""
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (queue.isNotEmpty()) {
            val (docId, rel) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val listing = {
                resolver.query(children, cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        val name = c.getString(1) ?: continue
                        if (name.startsWith(".")) continue
                        val mime = c.getString(2)
                        val mtime = if (c.isNull(3)) 0L else c.getLong(3)
                        val path = if (rel.isEmpty()) name else "$rel/$name"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            folders += path
                            queue += id to path
                        } else if (name.endsWith(".md", ignoreCase = true)) {
                            entries += Entry(path, DocumentsContract.buildDocumentUriUsingTree(tree, id), mtime)
                        }
                    }
                } ?: error("Android gave no answer for the folder.")
            }
            if (rel.isEmpty()) {
                // the top folder must work, or the permission is gone
                runCatching { listing() }.getOrElse { throw IllegalStateException("Cannot read the folder. Add it again. (${it.message})") }
            } else {
                runCatching { listing() }
            }
        }
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val gate = kotlinx.coroutines.sync.Semaphore(8)
        val notes = kotlinx.coroutines.coroutineScope {
            entries.map { e ->
                async(Dispatchers.IO) {
                    gate.acquire()
                    try {
                        val old = oldByPath[e.path]
                        val ref = if (old != null && old.mtime == e.mtime && old.uri == e.uri.toString()) old
                        else readNote(context, e.uri, e.path, e.mtime)
                        val n = done.incrementAndGet()
                        if (n % 20 == 0) onProgress(n)
                        ref
                    } finally {
                        gate.release()
                    }
                }
            }.map { it.await() }
        }
        onProgress(notes.size)
        return VaultIndex(vault, System.currentTimeMillis(), notes, folders.sorted())
    }

    private fun readNote(context: Context, uri: Uri, path: String, mtime: Long): NoteRef {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { s ->
                val buf = ByteArray(READ_BYTES)
                var n = 0
                while (n < buf.size) {
                    val r = s.read(buf, n, buf.size - n)
                    if (r <= 0) break
                    n += r
                }
                String(buf, 0, n, Charsets.UTF_8)
            }
        }.getOrNull().orEmpty()
        val p = ObsidianText.parse(text)
        return NoteRef(path, uri.toString(), mtime, p.tags, p.source, p.headings, ObsidianText.plain(p.body, TEXT_CHARS))
    }
}

/** Keeps one index per vault, on disk and in memory, and rebuilds it in the background. */
class VaultStore(private val context: Context, private val settings: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val _indexes = MutableStateFlow<Map<String, VaultIndex>>(emptyMap())
    val indexes: StateFlow<Map<String, VaultIndex>> = _indexes.asStateFlow()
    private val _busy = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Vault name to the number of notes read so far, while it is being scanned. */
    val busy: StateFlow<Map<String, Int>> = _busy.asStateFlow()

    init {
        settings.value.vaultFolders.keys.forEach { name ->
            load(name)?.let { idx -> _indexes.update { it + (name to idx) } }
        }
    }

    private fun file(vault: String) = File(context.filesDir, "vault_${vault.hashCode().toUInt()}.json")
    private fun load(vault: String): VaultIndex? = runCatching { json.decodeFromString(VaultIndex.serializer(), file(vault).readText()) }.getOrNull()

    fun index(vault: String): VaultIndex? = _indexes.value[vault]

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Vault name to what went wrong the last time it was read. */
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()
    private val jobs = HashMap<String, kotlinx.coroutines.Job>()

    /**
     * Rebuilds the index. Unchanged notes are reused, so a second scan is quick.
     * A forced refresh (the Reindex button) stops a scan that is already running and starts over.
     */
    fun refresh(vault: String, force: Boolean = false) {
        val uri = settings.value.vaultFolders[vault] ?: return
        if (_busy.value.containsKey(vault)) {
            if (!force) return
            jobs.remove(vault)?.cancel()
        }
        val prev = _indexes.value[vault]
        if (!force && prev != null && System.currentTimeMillis() - prev.scannedAt < 60_000) return
        _busy.update { it + (vault to 0) }
        _errors.update { it - vault }
        jobs[vault] = scope.launch {
            try {
                val idx = VaultScanner.scan(context, vault, Uri.parse(uri), prev) { n -> _busy.update { it + (vault to n) } }
                _indexes.update { it + (vault to idx) }
                runCatching { file(vault).writeText(json.encodeToString(VaultIndex.serializer(), idx)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _errors.update { it + (vault to (e.message ?: "Could not read the vault.")) }
            } finally {
                _busy.update { it - vault }
            }
        }
    }

    fun forget(vault: String) {
        _indexes.update { it - vault }
        file(vault).delete()
    }
}
