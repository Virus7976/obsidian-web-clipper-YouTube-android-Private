package app.marginalia.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

/** Reads a YouTube playlist link with NewPipe Extractor. */
object PlaylistImport {
    private const val MAX = 500

    fun listId(input: String): String? = Regex("[?&]list=([A-Za-z0-9_-]{10,})").find(input)?.groupValues?.get(1)

    /** A playlist page, with no single video in the link. */
    fun isPlaylistLink(input: String): Boolean = listId(input) != null && YouTube.extractId(input) == null

    data class Result(val title: String, val videos: List<VideoMeta>)

    suspend fun fetch(yt: YouTube, listId: String): Result = withContext(Dispatchers.IO) {
        NewPipeBridge.ensureInit(yt.http)
        val service = ServiceList.YouTube
        val url = "https://www.youtube.com/playlist?list=$listId"
        val info = PlaylistInfo.getInfo(service, url)
        val out = ArrayList<VideoMeta>()
        fun take(items: List<org.schabi.newpipe.extractor.stream.StreamInfoItem>) {
            for (i in items) {
                val id = YouTube.extractId(i.url) ?: continue
                if (out.none { it.id == id }) out += VideoMeta(id, i.name, i.uploaderName.orEmpty(), durationSec = i.duration.coerceAtLeast(0).toInt())
            }
        }
        take(info.relatedItems)
        var page = info.nextPage
        while (page != null && out.size < MAX) {
            val more = PlaylistInfo.getMoreItems(service, url, page)
            take(more.items)
            page = more.nextPage
        }
        if (out.isEmpty()) throw YouTubeException("That playlist has no videos I can read. It may be private.")
        Result(info.name.ifBlank { "Playlist" }, out.take(MAX))
    }
}
