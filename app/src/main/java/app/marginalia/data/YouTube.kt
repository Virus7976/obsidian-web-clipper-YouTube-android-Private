package app.marginalia.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class YouTubeException(message: String) : Exception(message)

data class FetchResult(
    val meta: VideoMeta,
    val chapters: List<Chapter>,
    val transcript: TranscriptData?,
    val transcriptError: String?,
)

/**
 * Talks to YouTube the same way the official mobile apps do. Every step has a
 * fallback so a change on YouTube's side degrades the app instead of breaking it:
 *   metadata: player API  ->  oEmbed  ->  watch page
 *   captions: Android client -> iOS client -> watch page
 */
class YouTube(private val settings: () -> AppSettings) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---------- public ----------

    /** Fast: title, author, thumbnail. Used for the paste preview. */
    suspend fun preview(id: String): VideoMeta = coroutineScope {
        val player = async { runCatching { playerResponse(id, "ANDROID") }.getOrNull() }
        val oembed = async { runCatching { oembed(id) }.getOrNull() }
        val p = player.await()
        val o = oembed.await()
        val details = p?.get("videoDetails") as? JsonObject
        val title = details?.str("title") ?: o?.first
        val author = details?.str("author") ?: o?.second
        if (title == null) throw YouTubeException("Could not read that video. Check the link and your connection.")
        VideoMeta(
            id = id,
            title = title,
            author = author ?: "",
            description = details?.str("shortDescription") ?: "",
            durationSec = details?.str("lengthSeconds")?.toIntOrNull() ?: 0,
        )
    }

    /** Everything: metadata, chapters, transcript. */
    suspend fun fetch(id: String): FetchResult = coroutineScope {
        val dateJob = async { runCatching { watchPageDate(id) }.getOrNull() }
        var meta: VideoMeta? = null
        var tracks: List<Track> = emptyList()
        var playerError: String? = null
        for (client in listOf("ANDROID", "IOS")) {
            val resp = runCatching { playerResponse(id, client) }.getOrNull() ?: continue
            val details = resp["videoDetails"] as? JsonObject
            if (meta == null && details != null) {
                meta = VideoMeta(
                    id = id,
                    title = details.str("title") ?: "",
                    author = details.str("author") ?: "",
                    description = details.str("shortDescription") ?: "",
                    durationSec = details.str("lengthSeconds")?.toIntOrNull() ?: 0,
                )
            }
            val found = captionTracks(resp)
            if (found.isNotEmpty()) {
                tracks = found
                break
            }
            playerError = (resp["playabilityStatus"] as? JsonObject)?.str("reason")
        }
        if (meta == null) {
            val o = runCatching { oembed(id) }.getOrNull()
                ?: throw YouTubeException("Could not read that video. Check the link and your connection.")
            meta = VideoMeta(id = id, title = o.first, author = o.second)
        }
        val date = dateJob.await()
        val finalMeta = if (date != null) meta.copy(published = date) else meta

        var transcript: TranscriptData? = null
        var error: String? = null
        if (tracks.isEmpty()) {
            error = playerError ?: "This video has no captions."
        } else {
            val track = pickTrack(tracks, settings().captionLang)
            transcript = runCatching { downloadTrack(track) }.getOrNull()
            if (transcript == null || transcript.cues.isEmpty()) {
                error = "YouTube did not hand over the captions. Try again in a moment."
                transcript = null
            }
        }
        FetchResult(finalMeta, parseChapters(finalMeta.description), transcript, error)
    }

    // ---------- player API ----------

    private data class Track(val url: String, val lang: String, val auto: Boolean, val name: String)

    private fun clientBody(client: String, id: String): Pair<String, Map<String, String>> {
        val s = settings()
        return if (client == "ANDROID") {
            val v = s.androidClientVersion.ifBlank { "20.10.38" }
            """{"context":{"client":{"clientName":"ANDROID","clientVersion":"$v","androidSdkVersion":34,"osName":"Android","osVersion":"14","hl":"en","gl":"US"}},"videoId":"$id","contentCheckOk":true,"racyCheckOk":true}""" to
                mapOf("User-Agent" to "com.google.android.youtube/$v (Linux; U; Android 14) gzip")
        } else {
            val v = s.iosClientVersion.ifBlank { "20.10.4" }
            """{"context":{"client":{"clientName":"IOS","clientVersion":"$v","deviceMake":"Apple","deviceModel":"iPhone16,2","osName":"iPhone","osVersion":"18.3.2.22D82","hl":"en","gl":"US"}},"videoId":"$id","contentCheckOk":true,"racyCheckOk":true}""" to
                mapOf("User-Agent" to "com.google.ios.youtube/$v (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)")
        }
    }

    private suspend fun playerResponse(id: String, client: String): JsonObject = withContext(Dispatchers.IO) {
        val (body, headers) = clientBody(client, id)
        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw YouTubeException("HTTP ${r.code}")
            json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
        }
    }

    private fun captionTracks(resp: JsonObject): List<Track> {
        val list = resp["captions"]?.jsonObject
            ?.get("playerCaptionsTracklistRenderer")?.jsonObject
            ?.get("captionTracks") as? JsonArray ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val url = o.str("baseUrl") ?: return@mapNotNull null
            val nameObj = o["name"] as? JsonObject
            val name = nameObj?.str("simpleText")
                ?: (nameObj?.get("runs") as? JsonArray)?.firstOrNull()?.jsonObject?.str("text") ?: ""
            Track(url, o.str("languageCode") ?: "", o.str("kind") == "asr", name)
        }
    }

    private fun pickTrack(tracks: List<Track>, want: String): Track {
        val lang = want.ifBlank { java.util.Locale.getDefault().language.ifBlank { "en" } }
        fun same(t: Track) = t.lang.equals(lang, true) || t.lang.startsWith("$lang-", true)
        return tracks.firstOrNull { same(it) && !it.auto }
            ?: tracks.firstOrNull { same(it) }
            ?: tracks.firstOrNull { it.lang.startsWith("en", true) && !it.auto }
            ?: tracks.firstOrNull { !it.auto }
            ?: tracks.first()
    }

    private suspend fun downloadTrack(t: Track): TranscriptData = withContext(Dispatchers.IO) {
        val ua = "com.google.android.youtube/${settings().androidClientVersion.ifBlank { "20.10.38" }} (Linux; U; Android 14) gzip"
        val req = Request.Builder().url(t.url).header("User-Agent", ua).build()
        val text = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        val cues = if (text.trimStart().startsWith("{")) parseJson3(text) else parseXml(text)
        TranscriptData(t.lang, t.auto, cues)
    }

    // ---------- caption parsing ----------

    private fun parseXml(xml: String): List<Cue> {
        val out = ArrayList<Cue>()
        val p = Regex("<p\\s+([^>]*)>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        val tAttr = Regex("""\bt="(\d+)"""")
        val dAttr = Regex("""\bd="(\d+)"""")
        for (m in p.findAll(xml)) {
            val attrs = m.groupValues[1]
            val t = tAttr.find(attrs)?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val d = dAttr.find(attrs)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val raw = m.groupValues[2].replace(Regex("<[^>]+>"), "")
            val text = unescape(raw).replace(Regex("\\s+"), " ").trim()
            if (text.isNotEmpty()) out += Cue(t / 1000.0, d / 1000.0, text)
        }
        return out
    }

    private fun parseJson3(text: String): List<Cue> {
        val events = json.parseToJsonElement(text).jsonObject["events"] as? JsonArray ?: return emptyList()
        val out = ArrayList<Cue>()
        for (e in events) {
            val o = e as? JsonObject ?: continue
            val segs = o["segs"] as? JsonArray ?: continue
            val t = o["tStartMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
            val d = o["dDurationMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            val s = segs.joinToString("") { it.jsonObject.str("utf8") ?: "" }
            val clean = s.replace(Regex("\\s+"), " ").trim()
            if (clean.isNotEmpty()) out += Cue(t / 1000.0, d / 1000.0, clean)
        }
        return out
    }

    private fun unescape(s: String): String {
        var r = s
        repeat(2) {
            r = r.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&nbsp;", " ")
            r = Regex("&#(\\d+);").replace(r) { m -> m.groupValues[1].toIntOrNull()?.let { c -> String(Character.toChars(c)) } ?: m.value }
        }
        return r
    }

    // ---------- fallbacks ----------

    private suspend fun oembed(id: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val u = "https://www.youtube.com/oembed?format=json&url=" + java.net.URLEncoder.encode("https://www.youtube.com/watch?v=$id", "UTF-8")
        http.newCall(Request.Builder().url(u).build()).execute().use { r ->
            if (!r.isSuccessful) throw YouTubeException("oembed ${r.code}")
            val o = json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            (o.str("title") ?: throw YouTubeException("no title")) to (o.str("author_name") ?: "")
        }
    }

    /** The publish date is not in the app API, so read it off the watch page. Best effort. */
    private suspend fun watchPageDate(id: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://www.youtube.com/watch?v=$id")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "CONSENT=YES+1; SOCS=CAI")
            .build()
        val html = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        Regex("\"publishDate\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: Regex("itemprop=\"datePublished\"\\s+content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    companion object {
        private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")

        /** Pulls a video id out of any normal YouTube link, or a bare id. */
        fun extractId(input: String): String? {
            val s = input.trim()
            if (idPattern.matches(s)) return s
            val m = Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|live/|embed/|v/)|youtube-nocookie\.com/embed/)([A-Za-z0-9_-]{11})""")
                .find(s)
            return m?.groupValues?.get(1)
        }

        /** Chapters from description timestamps, the same rule YouTube uses: must start at 0:00, need 3+. */
        fun parseChapters(description: String): List<Chapter> {
            val rx = Regex("""^\s*[\[(]?((?:\d{1,2}:)?\d{1,2}:\d{2})[\])]?\s*[-–—:|]?\s*(.+?)\s*$""")
            val found = description.lines().mapNotNull { line ->
                val m = rx.find(line) ?: return@mapNotNull null
                val secs = m.groupValues[1].split(":").map { it.toInt() }.fold(0) { a, b -> a * 60 + b }
                Chapter(secs.toDouble(), m.groupValues[2])
            }
            if (found.size < 3 || found.first().start != 0.0) return emptyList()
            return found.sortedBy { it.start }
        }

        /** Glue short caption cues into readable lines. */
        fun toLines(cues: List<Cue>, lengthSetting: Int, chapters: List<Chapter>): List<Line> {
            val (target, max) = when (lengthSetting) {
                0 -> 80 to 140
                2 -> 280 to 460
                else -> 160 to 260
            }
            val out = ArrayList<Line>()
            val sb = StringBuilder()
            var start = 0.0
            var end = 0.0
            var chapterIdx = -1
            fun flush() {
                if (sb.isNotEmpty()) {
                    out += Line(start, end, sb.toString().trim())
                    sb.clear()
                }
            }
            for (c in cues) {
                val ci = chapters.indexOfLast { it.start <= c.start + 0.5 }
                if (ci != chapterIdx) {
                    flush()
                    chapterIdx = ci
                }
                if (sb.isNotEmpty() && c.start - end > 2.5) flush()
                if (sb.isEmpty()) start = c.start else sb.append(' ')
                sb.append(c.text)
                end = c.start + c.dur
                val done = Regex("[.?!][\"')\\]]?$").containsMatchIn(sb)
                if ((sb.length >= target && done) || sb.length >= max) flush()
            }
            flush()
            return out
        }

        fun formatTime(sec: Double): String {
            val t = sec.toInt().coerceAtLeast(0)
            val h = t / 3600
            val m = (t % 3600) / 60
            val s = t % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
