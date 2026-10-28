package app.marginalia

import app.marginalia.data.NoteName
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteNameTest {
    private val day = LocalDate.of(2026, 10, 28)

    @Test fun buildsYourSchema() =
        assertEquals("2026-10-28_productivity_thesis-how-to-slow-time-down_v1", NoteName.build(day, "Productivity", "thesis how to slow time down", 1))

    @Test fun cleansTitles() =
        assertEquals("how-to-slow-down-the-passing-of-time-jedidiah-jenkins-tedx", NoteName.slug("How to Slow Down the Passing of Time | Jedidiah Jenkins | TEDx!"))

    @Test fun emptyPartsGetDefaults() =
        assertEquals("2026-10-28_clipping_untitled_v2", NoteName.build(day, "", "???", 2))

    @Test fun versionCountsUp() {
        val p = NoteName.prefix(day, "clipping", "a")
        assertEquals(1, NoteName.nextVersion(emptyList(), p))
        assertEquals(3, NoteName.nextVersion(listOf(p + "1", p + "2", "other"), p))
    }
}
