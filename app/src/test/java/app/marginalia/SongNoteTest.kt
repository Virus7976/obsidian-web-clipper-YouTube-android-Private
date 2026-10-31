package app.marginalia

import app.marginalia.data.AppSettings
import app.marginalia.data.Highlight
import app.marginalia.data.SavedVideo
import app.marginalia.data.VideoMeta
import app.marginalia.obsidian.NoteBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SongNoteTest {
    private val video = SavedVideo(VideoMeta("abc12345678", "A song", "A band"))

    @Test fun aNoteWithoutWordsIsATimedBullet() {
        val h = Highlight("1", 83.0, 83.0, "", "the bass drops here", 0)
        assertEquals("- [1:23](https://www.youtube.com/watch?v=abc12345678&t=83s) the bass drops here", NoteBuilder.highlightEntry(video, h, AppSettings()))
    }

    @Test fun aMarkWithNoTextIsJustTheTime() {
        val h = Highlight("2", 5.0, 5.0, "", "", 0)
        val out = NoteBuilder.highlightEntry(video, h, AppSettings())
        assertEquals("- [0:05](https://www.youtube.com/watch?v=abc12345678&t=5s)", out)
        assertFalse(out.contains("=="))
    }
}
