package app.marginalia

import app.marginalia.data.AppSettings
import app.marginalia.data.YouTube
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Talks to the real YouTube. Runs only when YT_LIVE=1, so normal builds stay offline. */
class LiveYouTubeTest {
    @Test
    fun fetchesTranscriptAndMeta() {
        assumeTrue(System.getenv("YT_LIVE") == "1")
        val yt = YouTube { AppSettings(captionLang = "en") }
        val r = runBlocking { yt.fetch("dQw4w9WgXcQ") }
        println("TITLE=${r.meta.title} AUTHOR=${r.meta.author} PUBLISHED=${r.meta.published} DUR=${r.meta.durationSec}")
        println("TRANSCRIPT lang=${r.transcript?.lang} auto=${r.transcript?.auto} cues=${r.transcript?.cues?.size} err=${r.transcriptError}")
        val lines = YouTube.toLines(r.transcript!!.cues, 1, r.chapters)
        println("LINES=${lines.size} FIRST=${lines.first()}")
        assertTrue(r.meta.title.isNotBlank())
        assertTrue(r.transcript!!.cues.size > 10)
        val p = runBlocking { yt.preview("dQw4w9WgXcQ") }
        assertTrue(p.title.isNotBlank())
    }
}
