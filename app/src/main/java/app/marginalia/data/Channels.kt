package app.marginalia.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.File

/** A YouTube channel you follow. [url] is the stable channel/UC... address. */
@Serializable
data class Channel(val url: String, val name: String, val avatar: String = "", val subscribedAt: Long = 0L)

/** One video as listed on a channel page or in the feed. Not in the library until you open it. */
@Serializable
data class ChannelVideo(
    val meta: VideoMeta,
    val channelUrl: String = "",
    val channelName: String = "",
    val publishedMs: Long = 0L,
    val ago: String = "",
)

@Serializable
data class FeedState(val items: List<ChannelVideo> = emptyList(), val updatedAt: Long = 0L)

class Subscriptions(context: Context) : JsonFileStore<List<Channel>>(File(context.filesDir, "subscriptions.json")) {
    private val _state = MutableStateFlow(readOrNull().orEmpty())
    val all: StateFlow<List<Channel>> = _state.asStateFlow()

    override fun encode(value: List<Channel>) = storeJson.encodeToString(ListSerializer(Channel.serializer()), value)
    override fun decode(text: String) = storeJson.decodeFromString(ListSerializer(Channel.serializer()), text)

    fun has(url: String) = _state.value.any { it.url == url }

    fun add(c: Channel) {
        _state.update { list -> if (list.any { it.url == c.url }) list else listOf(c.copy(subscribedAt = System.currentTimeMillis())) + list }
        persist(_state.value)
    }

    fun remove(url: String) {
        _state.update { list -> list.filterNot { it.url == url } }
        persist(_state.value)
    }
}

/** The newest videos from every channel you follow, newest first. Kept on disk so it opens at once. */
class FeedStore(context: Context, private val subs: Subscriptions, private val yt: YouTube) :
    JsonFileStore<FeedState>(File(context.filesDir, "feed.json")) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(readOrNull() ?: FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    override fun encode(value: FeedState) = storeJson.encodeToString(FeedState.serializer(), value)
    override fun decode(text: String) = storeJson.decodeFromString(FeedState.serializer(), text)

    /** Fetches again when the feed is older than [maxAgeMs], or always when [force]. */
    fun refresh(force: Boolean = false, maxAgeMs: Long = 30 * 60_000L) {
        val channels = subs.all.value
        if (_busy.value) return
        if (channels.isEmpty()) { _state.value = FeedState(); persist(_state.value); return }
        if (!force && System.currentTimeMillis() - _state.value.updatedAt < maxAgeMs) return
        _busy.value = true
        scope.launch {
            val gate = Semaphore(4)
            val fetched = withContext(Dispatchers.IO) {
                channels.map { c ->
                    async { gate.withPermit { runCatching { ChannelSource.latest(yt, c, 12) }.getOrNull() } }
                }.awaitAll()
            }
            // A channel that failed keeps what the feed already had for it.
            val old = _state.value.items
            val merged = channels.flatMapIndexed { i, c ->
                fetched[i] ?: old.filter { it.channelUrl == c.url }
            }
            val sorted = merged.distinctBy { it.meta.id }.sortedByDescending { it.publishedMs }
            _state.value = FeedState(sorted, System.currentTimeMillis())
            persist(_state.value)
            _busy.value = false
        }
    }

    /** Drops a channel's videos from the feed right away. */
    fun forget(channelUrl: String) {
        _state.update { it.copy(items = it.items.filterNot { v -> v.channelUrl == channelUrl }) }
        persist(_state.value)
    }
}

/** Reads channels, their videos and YouTube search with NewPipe Extractor. Always call off the main thread. */
object ChannelSource {
    /** The first page of a channel's Videos tab, and how to get the next one. */
    class Cursor(val tab: ListLinkHandler?, var next: Page?)

    class Opened(val channel: Channel, val subscribers: Long, val description: String, val videos: List<ChannelVideo>, val cursor: Cursor)

    sealed interface Hit {
        data class Chan(val channel: Channel, val subscribers: Long) : Hit
        data class Vid(val video: ChannelVideo) : Hit
    }

    private val channelLink = Regex("youtube\\.com/(channel/UC[A-Za-z0-9_-]+|@[A-Za-z0-9_.-]+|c/[^/?#\\s]+|user/[^/?#\\s]+)", RegexOption.IGNORE_CASE)

    fun isChannelLink(text: String): Boolean = YouTube.extractId(text) == null && channelLink.containsMatchIn(text)

    /** The first link in a block of text, as YouTube's Share gives it ("Title https://..."). */
    fun firstUrl(text: String): String? = Regex("https?://[^\\s]+").find(text)?.value

    private fun avatarOf(images: List<org.schabi.newpipe.extractor.Image>?): String =
        images.orEmpty().maxByOrNull { it.width.coerceAtLeast(0) }?.url ?: images?.firstOrNull()?.url.orEmpty()

    private fun normal(url: String) = url.trim().trimEnd('/')

    private fun video(i: StreamInfoItem, fallbackChannel: Channel? = null): ChannelVideo? {
        val id = YouTube.extractId(i.url) ?: return null
        if (i.streamType != StreamType.VIDEO_STREAM && i.streamType != StreamType.AUDIO_STREAM) return null
        if (i.isShortFormContent) return null
        val whenMs = i.uploadDate?.instant?.toEpochMilli() ?: 0L
        return ChannelVideo(
            meta = VideoMeta(id, i.name, i.uploaderName?.takeIf { it.isNotBlank() } ?: fallbackChannel?.name.orEmpty(), durationSec = i.duration.coerceAtLeast(0).toInt()),
            channelUrl = i.uploaderUrl?.let(::normal) ?: fallbackChannel?.url.orEmpty(),
            channelName = i.uploaderName?.takeIf { it.isNotBlank() } ?: fallbackChannel?.name.orEmpty(),
            publishedMs = whenMs,
            ago = i.textualUploadDate.orEmpty(),
        )
    }

    private fun videosOf(items: List<InfoItem>, channel: Channel?): List<ChannelVideo> =
        items.filterIsInstance<StreamInfoItem>().mapNotNull { video(it, channel) }

    private fun videosTab(info: ChannelInfo): ListLinkHandler? =
        info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters } ?: info.tabs.firstOrNull()

    suspend fun open(yt: YouTube, url: String): Opened = withContext(Dispatchers.IO) {
        NewPipeBridge.ensureInit(yt.http)
        val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
        val channel = Channel(normal(info.url), info.name.orEmpty(), avatarOf(info.avatars))
        val tab = videosTab(info)
        val page = tab?.let { ChannelTabInfo.getInfo(ServiceList.YouTube, it) }
        Opened(
            channel, info.subscriberCount, info.description.orEmpty(),
            videosOf(page?.relatedItems.orEmpty(), channel), Cursor(tab, page?.nextPage),
        )
    }

    suspend fun more(yt: YouTube, channel: Channel, cursor: Cursor): List<ChannelVideo> = withContext(Dispatchers.IO) {
        val tab = cursor.tab ?: return@withContext emptyList()
        val next = cursor.next ?: return@withContext emptyList()
        NewPipeBridge.ensureInit(yt.http)
        val page = ChannelTabInfo.getMoreItems(ServiceList.YouTube, tab, next)
        cursor.next = page.nextPage
        videosOf(page.items, channel)
    }

    /** The newest [count] videos of a channel, for the feed. */
    suspend fun latest(yt: YouTube, channel: Channel, count: Int): List<ChannelVideo> {
        val o = open(yt, channel.url)
        return o.videos.take(count).map { it.copy(channelUrl = channel.url, channelName = channel.name) }
    }

    /** The channel a video belongs to. */
    suspend fun channelOfVideo(yt: YouTube, videoId: String): String? = withContext(Dispatchers.IO) {
        NewPipeBridge.ensureInit(yt.http)
        StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId").uploaderUrl?.let(::normal)
    }

    suspend fun search(yt: YouTube, query: String): List<Hit> = withContext(Dispatchers.IO) {
        NewPipeBridge.ensureInit(yt.http)
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query, listOf("all"), "")
        SearchInfo.getInfo(service, handler).relatedItems.mapNotNull { item ->
            when (item) {
                is ChannelInfoItem -> Hit.Chan(Channel(normal(item.url), item.name.orEmpty(), avatarOf(item.thumbnails)), item.subscriberCount)
                is StreamInfoItem -> video(item)?.let { Hit.Vid(it) }
                else -> null
            }
        }
    }
}
