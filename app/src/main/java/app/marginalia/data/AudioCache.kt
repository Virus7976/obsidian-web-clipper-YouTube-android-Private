package app.marginalia.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Keeps the audio of chosen videos on the phone, so they play with no network.
 * The file is fetched in 4 MB pieces, because YouTube slows one long request down.
 */
class AudioCache(
    private val context: Context,
    private val yt: YouTube,
    private val finder: StreamFinder,
    private val settings: () -> AppSettings,
) {
    private val dir get() = File(context.filesDir, "audio").also { it.mkdirs() }
    private val http = yt.http.newBuilder().callTimeout(0, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()

    private val _saved = MutableStateFlow(scan())
    /** Ids that are on the phone. */
    val saved: StateFlow<Set<String>> = _saved.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    /** Ids being fetched now, with progress 0..1. A negative number means the size is not known yet. */
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private fun file(id: String) = File(dir, "$id.audio")
    private fun scan(): Set<String> = dir.listFiles().orEmpty().filter { it.name.endsWith(".audio") && it.length() > 0 }.map { it.name.removeSuffix(".audio") }.toSet()

    fun has(id: String) = id in _saved.value

    fun localStreams(id: String): Streams? =
        file(id).takeIf { has(id) && it.exists() }?.let { Streams(android.net.Uri.fromFile(it).toString(), null, "saved") }

    fun sizeMb(id: String): Double = file(id).length() / 1_048_576.0

    fun remove(id: String) {
        file(id).delete()
        _saved.update { it - id }
    }

    /** Returns true when the audio is on the phone. */
    suspend fun save(id: String): Boolean = withContext(Dispatchers.IO) {
        if (has(id)) return@withContext true
        if (id in _progress.value) return@withContext false
        _progress.update { it + (id to -1f) }
        try {
            val ok = finder.candidates(id).firstOrNull { s -> runCatching { fetch(id, s) }.getOrDefault(false) } != null
            if (ok) _saved.update { it + id }
            ok
        } finally {
            _progress.update { it - id }
            File(dir, "$id.part").delete()
        }
    }

    private fun fetch(id: String, s: Streams): Boolean {
        val part = File(dir, "$id.part")
        val ua = userAgentFor(s.audioUrl, settings())
        var total = -1L
        var pos = 0L
        part.outputStream().use { out ->
            while (total < 0 || pos < total) {
                val req = Request.Builder().url(s.audioUrl)
                    .header("User-Agent", ua)
                    .header("Range", "bytes=$pos-${pos + CHUNK - 1}")
                    .build()
                http.newCall(req).execute().use { r ->
                    if (r.code != 200 && r.code != 206) error("HTTP ${r.code}")
                    val body = r.body ?: error("no body")
                    if (r.code == 200) {
                        // The server ignored the range and sent everything. Take it all and stop.
                        if (pos != 0L) error("server ignored the range")
                        total = body.contentLength().takeIf { it > 0 } ?: Long.MAX_VALUE
                    } else if (total < 0) {
                        total = r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: error("no size")
                    }
                    val buf = ByteArray(64 * 1024)
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            pos += n
                            if (total in 1 until Long.MAX_VALUE) _progress.update { it + (id to (pos.toFloat() / total).coerceIn(0f, 1f)) }
                        }
                    }
                    if (r.code == 200) total = pos
                }
            }
        }
        if (pos <= 0) return false
        val dest = file(id)
        dest.delete()
        return part.renameTo(dest)
    }

    private companion object {
        const val CHUNK = 4L * 1024 * 1024
    }
}
