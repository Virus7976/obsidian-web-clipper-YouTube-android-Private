package app.marginalia.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

/** One playable source. [via] says which fallback tier found it, for the error text and the logs. */
data class Streams(
    val audioUrl: String,
    val videoUrl: String?,
    val via: String,
)

private const val UA_FIREFOX = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

/**
 * googlevideo checks that the User-Agent matches the client that made the link. The link says
 * which client that was in its c= parameter, the same trick NewPipe uses.
 */
fun userAgentFor(url: String, settings: AppSettings): String {
    val client = Regex("[?&]c=([A-Z_0-9]+)").find(url)?.groupValues?.get(1)
    return when (client) {
        "ANDROID_VR" -> "com.google.android.apps.youtube.vr.oculus/${settings.vrClientVersion.ifBlank { "1.62.27" }} (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        "ANDROID" -> "com.google.android.youtube/${settings.androidClientVersion.ifBlank { "20.10.38" }} (Linux; U; Android 14) gzip"
        "IOS" -> "com.google.ios.youtube/${settings.iosClientVersion.ifBlank { "20.10.4" }} (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)"
        else -> UA_FIREFOX
    }
}

/**
 * Finds a way to play a video. It tries sources one after another and hands back the first
 * ones that really answer. Playback walks down the same list if a source dies mid-song.
 *
 *   1. YouTube's own phone-app clients (VR, Android, iOS). Fast, no third party.
 *   2. NewPipe Extractor. Maintained by the NewPipe team, a separate code path.
 *   3. Piped servers. Community run, found from the public list at run time.
 *   4. Invidious servers. Community run, found from the public list at run time.
 */
class StreamFinder(
    private val yt: YouTube,
    private val settings: () -> AppSettings,
) {
    private val http: OkHttpClient = yt.http.newBuilder().callTimeout(25, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun candidates(id: String): Flow<Streams> = flow {
        for (client in listOf("ANDROID_VR", "ANDROID", "IOS")) {
            val s = runCatching { fromClient(id, client) }.getOrNull()
            if (s != null && works(s.audioUrl)) emit(s)
        }
        val np = runCatching { fromNewPipe(id) }.getOrNull()
        if (np != null && works(np.audioUrl)) emit(np)

        for (base in runCatching { pipedInstances() }.getOrDefault(emptyList()).take(4)) {
            val s = runCatching { fromPiped(id, base) }.getOrNull()
            if (s != null && works(s.audioUrl)) emit(s)
        }
        for (base in runCatching { invidiousInstances() }.getOrDefault(emptyList()).take(4)) {
            val s = runCatching { fromInvidious(id, base) }.getOrNull()
            if (s != null && works(s.audioUrl)) emit(s)
        }
    }.flowOn(Dispatchers.IO).buffer(Channel.RENDEZVOUS)

    /** Asks for the first two bytes. A dead or blocked link fails here, not after the user taps play. */
    private fun works(url: String): Boolean = runCatching {
        val req = Request.Builder().url(url)
            .header("User-Agent", userAgentFor(url, settings()))
            .header("Range", "bytes=0-1")
            .build()
        http.newCall(req).execute().use { it.code == 200 || it.code == 206 }
    }.getOrDefault(false)

    // ---------- 1. YouTube's own clients ----------

    private suspend fun fromClient(id: String, client: String): Streams? {
        val resp = yt.playerResponse(id, client)
        val sd = resp["streamingData"] as? JsonObject ?: return null
        val adaptive = sd["adaptiveFormats"] as? JsonArray ?: JsonArray(emptyList())
        val muxed = sd["formats"] as? JsonArray ?: JsonArray(emptyList())

        val audio = adaptive.mapNotNull { it as? JsonObject }
            .filter { (it.str("mimeType") ?: "").startsWith("audio/") && it.str("url") != null }
            .sortedWith(
                compareByDescending<JsonObject> { isOriginalTrack(it) }
                    .thenByDescending { (it.str("mimeType") ?: "").startsWith("audio/mp4") }
                    .thenByDescending { if ((it.num("bitrate") ?: 0.0) <= 200_000) it.num("bitrate") ?: 0.0 else 0.0 },
            )
            .firstOrNull() ?: return null

        val video = muxed.mapNotNull { it as? JsonObject }
            .filter { (it.str("mimeType") ?: "").startsWith("video/mp4") && it.str("url") != null }
            .maxByOrNull { it.num("height") ?: 0.0 }
        return Streams(audio.str("url")!!, video?.str("url"), client.lowercase())
    }

    // ---------- 2. NewPipe Extractor ----------

    private fun fromNewPipe(id: String): Streams? {
        NewPipeBridge.ensureInit(http)
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val audio = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> {
                    it.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL
                }
                .thenByDescending { it.format?.suffix == "m4a" }
                .thenByDescending { if (it.averageBitrate in 1..200) it.averageBitrate else 0 })
            .firstOrNull() ?: return null
        val video = info.videoStreams
            .filter { !it.isVideoOnly && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .maxByOrNull { it.height }
        return Streams(audio.content, video?.content, "newpipe")
    }

    // ---------- 3. Piped ----------

    private fun pipedInstances(): List<String> {
        val body = get("https://piped-instances.kavin.rocks/") ?: return emptyList()
        return json.parseToJsonElement(body).jsonArray.mapNotNull { (it as? JsonObject)?.str("api_url") }
    }

    private fun fromPiped(id: String, base: String): Streams? {
        val body = get("${base.trimEnd('/')}/streams/$id") ?: return null
        val o = json.parseToJsonElement(body).jsonObject
        val audio = (o["audioStreams"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { it.str("url") != null }
            .sortedWith(compareByDescending<JsonObject> { (it.str("mimeType") ?: "").contains("mp4") }
                .thenByDescending { if ((it.num("bitrate") ?: 0.0) <= 200_000) it.num("bitrate") ?: 0.0 else 0.0 })
            .firstOrNull() ?: return null
        val video = (o["videoStreams"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { it["videoOnly"]?.let { v -> (v as? JsonPrimitive)?.booleanOrNull } == false && it.str("url") != null }
            .maxByOrNull { it.num("height") ?: 0.0 }
        return Streams(audio.str("url")!!, video?.str("url"), "piped")
    }

    // ---------- 4. Invidious ----------

    private fun invidiousInstances(): List<String> {
        val body = get("https://api.invidious.io/instances.json?sort_by=type,health") ?: return emptyList()
        return json.parseToJsonElement(body).jsonArray.mapNotNull { e ->
            val pair = e as? JsonArray ?: return@mapNotNull null
            val info = pair.getOrNull(1) as? JsonObject ?: return@mapNotNull null
            val api = info["api"]?.let { (it as? JsonPrimitive)?.booleanOrNull } == true
            val uri = info.str("uri")
            if (api && info.str("type") == "https" && uri != null) uri else null
        }
    }

    private fun fromInvidious(id: String, base: String): Streams? {
        val body = get("${base.trimEnd('/')}/api/v1/videos/$id?local=true") ?: return null
        val o = json.parseToJsonElement(body).jsonObject
        val audio = (o["adaptiveFormats"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { (it.str("type") ?: "").startsWith("audio/") && it.str("url") != null }
            .sortedWith(compareByDescending<JsonObject> { (it.str("type") ?: "").startsWith("audio/mp4") }
                .thenByDescending { it.str("bitrate")?.toDoubleOrNull() ?: 0.0 })
            .firstOrNull() ?: return null
        val video = (o["formatStreams"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { it.str("url") != null }
            .lastOrNull()
        return Streams(audio.str("url")!!, video?.str("url"), "invidious")
    }

    private fun get(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", UA_FIREFOX).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.getOrNull()

    /**
     * YouTube can offer the same audio dubbed into other languages. The original track is marked
     * "original" in its name. A track with no name at all is the only one, so it counts as original.
     */
    private fun isOriginalTrack(o: JsonObject): Boolean {
        val track = o["audioTrack"] as? JsonObject ?: return true
        val name = track.str("displayName").orEmpty()
        return name.contains("original", true) || (name.isEmpty() && track["audioIsDefault"]?.let { (it as? JsonPrimitive)?.booleanOrNull } == true)
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
}

/** Starts NewPipe Extractor once, using our own OkHttp for its network calls. */
object NewPipeBridge {
    @Volatile private var started = false

    @Synchronized
    fun ensureInit(http: OkHttpClient) {
        if (started) return
        NewPipe.init(OkDownloader(http), Localization("en", "US"))
        started = true
    }

    private class OkDownloader(private val http: OkHttpClient) : Downloader() {
        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
            val body = request.dataToSend()?.let { okhttp3.RequestBody.create(null, it) }
            val b = Request.Builder().url(request.url())
                .method(request.httpMethod(), body ?: if (request.httpMethod() == "POST") okhttp3.RequestBody.create(null, ByteArray(0)) else null)
                .header("User-Agent", UA_FIREFOX)
            request.headers().forEach { (name, values) ->
                b.removeHeader(name)
                values.forEach { v -> b.addHeader(name, v) }
            }
            http.newCall(b.build()).execute().use { r ->
                if (r.code == 429) throw org.schabi.newpipe.extractor.exceptions.ReCaptchaException("rate limited", request.url())
                val headers = r.headers.toMultimap()
                return Response(r.code, r.message, headers, r.body?.string().orEmpty(), r.request.url.toString())
            }
        }
    }
}
