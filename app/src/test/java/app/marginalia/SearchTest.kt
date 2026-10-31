package app.marginalia

import app.marginalia.data.Highlight
import app.marginalia.data.SavedVideo
import app.marginalia.data.VideoMeta
import app.marginalia.ui.matchesQuery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test fun everyWordMustMatch() {
        assertTrue(matchesQuery("slow time", "How to Slow Down the Passing of Time"))
        assertFalse(matchesQuery("slow money", "How to Slow Down the Passing of Time"))
    }

    @Test fun emptyQueryMatchesAll() = assertTrue(matchesQuery("  ", "anything"))

    @Test fun searchesHighlightsAndTags() {
        val v = SavedVideo(
            VideoMeta("abc12345678", "A talk", "Someone"),
            tags = listOf("productivity"),
            highlights = listOf(Highlight("h1", 0.0, 1.0, "the only thing that matters")),
        )
        assertTrue(v.matchesQuery("only thing"))
        assertTrue(v.matchesQuery("productivity someone"))
        assertFalse(v.matchesQuery("banana"))
    }
}
