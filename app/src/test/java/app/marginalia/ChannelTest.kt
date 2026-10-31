package app.marginalia

import app.marginalia.data.AppSettings
import app.marginalia.data.ChannelSource
import app.marginalia.data.YouTube
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ChannelTest {
    @Test fun knowsChannelLinks() {
        assertTrue(ChannelSource.isChannelLink("https://www.youtube.com/@TEDx"))
        assertTrue(ChannelSource.isChannelLink("https://www.youtube.com/channel/UCsT0YIqwnpJCM-mx7-gSA4Q"))
        assertTrue(ChannelSource.isChannelLink("Check this https://youtube.com/c/Veritasium"))
    }

    @Test fun aVideoLinkIsNotAChannel() {
        assertFalse(ChannelSource.isChannelLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(ChannelSource.isChannelLink("how to slow down time"))
    }

    @Test fun findsTheLinkInSharedText() =
        assertEquals("https://youtube.com/@x", ChannelSource.firstUrl("Look at this https://youtube.com/@x now"))

    /** Real YouTube: open a channel, read its videos, search by name. Runs only when YT_LIVE=1. */
    @Test fun liveChannelAndSearch() {
        assumeTrue(System.getenv("YT_LIVE") == "1")
        val yt = YouTube { AppSettings(captionLang = "en") }
        val o = runBlocking { ChannelSource.open(yt, "https://www.youtube.com/@TEDx") }
        println("CHANNEL ${o.channel.name} url=${o.channel.url} subs=${o.subscribers} videos=${o.videos.size} avatar=${o.channel.avatar.isNotBlank()} more=${o.cursor.next != null}")
        o.videos.take(3).forEach { println("  ${it.meta.id} ${it.meta.title} | ${it.ago} | ${it.publishedMs}") }
        assertTrue(o.videos.isNotEmpty())
        val more = runBlocking { ChannelSource.more(yt, o.channel, o.cursor) }
        println("NEXT PAGE ${more.size}")
        val hits = runBlocking { ChannelSource.search(yt, "veritasium") }
        println("SEARCH " + hits.take(5).joinToString { (it as? ChannelSource.Hit.Chan)?.channel?.name ?: "video" })
        assertTrue(hits.any { it is ChannelSource.Hit.Chan })
        val ch = runBlocking { ChannelSource.channelOfVideo(yt, "dQw4w9WgXcQ") }
        println("CHANNEL OF VIDEO $ch")
        assertTrue(ch != null)
    }
}
