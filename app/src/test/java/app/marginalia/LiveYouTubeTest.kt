package app.marginalia

import app.marginalia.data.AppSettings
import app.marginalia.data.StreamFinder
import app.marginalia.data.TranscriptDoc
import app.marginalia.data.YouTube
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Talks to the real YouTube. Runs only when YT_LIVE=1, so normal builds stay offline. */
class LiveYouTubeTest {
    private val yt = YouTube { AppSettings(captionLang = "en") }

    @Test
    fun fetchesTranscriptAndMeta() {
        assumeTrue(System.getenv("YT_LIVE") == "1")
        val r = runBlocking { yt.fetch("dQw4w9WgXcQ") }
        println("TITLE=${r.meta.title} PUBLISHED=${r.meta.published}")
        val t = r.transcript!!
        println("TRANSCRIPT lang=${t.lang} auto=${t.auto} cues=${t.cues.size} withWordTimes=${t.cues.count { it.offs != null }}")
        val doc = TranscriptDoc.build(t.cues, r.chapters, 1, true)
        println("DOC words=${doc.words.size} paras=${doc.paras.size}")
        assertTrue(doc.words.size > 20)
    }

    /** Every fallback tier, one at a time, so a broken one shows up by name. */
    @Test
    fun everyStreamTierIsTriedAndAtLeastOneWorks() {
        assumeTrue(System.getenv("YT_LIVE") == "1")
        val finder = StreamFinder(yt) { AppSettings() }
        val found = runBlocking { finder.candidates("dQw4w9WgXcQ").take(6).toList() }
        println("TIERS THAT ANSWERED: " + found.joinToString { it.via + (if (it.videoUrl != null) "+video" else "") })
        assertTrue("no tier produced a playable link", found.isNotEmpty())
        // the first link really serves audio bytes
        val http = OkHttpClient()
        val first = found.first()
        val ua = app.marginalia.data.userAgentFor(first.audioUrl, AppSettings())
        http.newCall(Request.Builder().url(first.audioUrl).header("User-Agent", ua).header("Range", "bytes=0-65535").build()).execute().use {
            println("FIRST ${first.via} -> HTTP ${it.code}, ${it.body?.bytes()?.size} bytes")
            assertTrue(it.code == 200 || it.code == 206)
        }
    }
}
