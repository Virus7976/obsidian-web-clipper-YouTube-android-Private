package app.marginalia

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import app.marginalia.data.Channel
import app.obsi.core.data.Highlight
import app.marginalia.data.HitKind
import app.marginalia.data.LibrarySearch
import app.obsi.core.data.Playlist
import app.obsi.core.data.ReviewCard
import app.marginalia.data.Reviews
import app.marginalia.data.SavedVideo
import app.marginalia.data.VideoMeta
import app.marginalia.ui.matchesQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {
    private val day = Reviews.DAY
    private val stoic = SavedVideo(
        VideoMeta("aaaaaaaaaaa", "Marcus Aurelius on Stoic Habits", "Bygone Philosophy"),
        highlights = listOf(
            Highlight("h1", 60.0, 70.0, "You have power over your mind, not outside events", "my favourite line", 0),
            Highlight("h2", 90.0, 95.0, "", "the bass drops here", 1),
        ),
        tags = listOf("productivity"), category = "Learning",
    )
    private val other = SavedVideo(VideoMeta("bbbbbbbbbbb", "How to cook rice", "Kitchen"))
    private val videos = listOf(stoic, other)

    @Test fun skippedLettersStillFindATitle() {
        val hits = LibrarySearch.search("mrcs stc", videos, emptyList(), emptyList())
        assertEquals("Marcus Aurelius on Stoic Habits", hits.first { it.kind == HitKind.Video }.title)
    }

    @Test fun aPhraseYouHighlightedIsFoundAndOpensAtItsSecond() {
        val hit = LibrarySearch.search("power over your mind", videos, emptyList(), emptyList()).first()
        assertEquals(HitKind.Highlight, hit.kind)
        assertEquals(60.0, hit.sec, 0.0)
        assertEquals("aaaaaaaaaaa", hit.videoId)
    }

    @Test fun aTimedNoteWithNoWordsIsFoundByItsNote() {
        val hit = LibrarySearch.search("bass", videos, emptyList(), emptyList()).first { it.kind == HitKind.Highlight }
        assertEquals(90.0, hit.sec, 0.0)
        assertEquals("the bass drops here", hit.snippet)
    }

    @Test fun everyWordMustMatch() {
        assertTrue(LibrarySearch.search("power rice", videos, emptyList(), emptyList()).none { it.kind == HitKind.Highlight })
    }

    @Test fun hashLooksOnlyAtTags() {
        val hits = LibrarySearch.search("#product", videos, emptyList(), emptyList())
        assertEquals(listOf(HitKind.Video), hits.map { it.kind })
        assertEquals("Marcus Aurelius on Stoic Habits", hits.single().title)
        assertTrue(LibrarySearch.search("#rice", videos, emptyList(), emptyList()).isEmpty())
    }

    @Test fun playlistsAndChannelsAreFound() {
        val p = Playlist("p1", "Morning stoics", "", listOf("aaaaaaaaaaa"), 0L)
        val c = Channel("https://www.youtube.com/@bygone", "Bygone Philosophy")
        val kinds = LibrarySearch.search("stoic", videos, listOf(p), listOf(c)).map { it.kind }.toSet()
        assertTrue(HitKind.Playlist in kinds)
        assertTrue(HitKind.Video in kinds)
        assertTrue(LibrarySearch.search("bygone", videos, listOf(p), listOf(c)).any { it.kind == HitKind.Channel })
    }

    @Test fun anEmptyBoxFindsNothing() {
        assertTrue(LibrarySearch.search("   ", videos, emptyList(), emptyList()).isEmpty())
    }

    // The search box on every list now forgives skipped letters in a title, but not in long highlight text.
    @Test fun listFilterForgivesTitlesButNotLongText() {
        assertTrue(stoic.let { matchesQuery("mrcs", it.meta.title) })
        assertFalse(matchesQuery("pwr", "x".repeat(200) + " power over"))
        assertTrue(matchesQuery("power", "x".repeat(200) + " power over"))
    }

    // ---------- review ----------

    @Test fun aNewHighlightWaitsADay() {
        val v = SavedVideo(VideoMeta("ccccccccccc", "t", "a"), highlights = listOf(Highlight("n1", 1.0, 2.0, "text", createdAt = 1_000_000L)), addedAt = 0L)
        assertTrue(Reviews.ready(listOf(v), emptyMap(), 1_000_000L + day - 1).isEmpty())
        assertEquals(1, Reviews.ready(listOf(v), emptyMap(), 1_000_000L + day).size)
    }

    @Test fun aDroppedHighlightNeverComesBack() {
        val v = SavedVideo(VideoMeta("ccccccccccc", "t", "a"), highlights = listOf(Highlight("n1", 1.0, 2.0, "text")), addedAt = 0L)
        val cards = mapOf("n1" to ReviewCard("n1", 0L, 0, dropped = true))
        assertTrue(Reviews.ready(listOf(v), cards, 10 * day).isEmpty())
    }

    @Test fun aKeptHighlightWaitsForItsNextDate() {
        val v = SavedVideo(VideoMeta("ccccccccccc", "t", "a"), highlights = listOf(Highlight("n1", 1.0, 2.0, "text")), addedAt = 0L)
        val cards = mapOf("n1" to ReviewCard("n1", due = 5 * day, step = 1))
        assertTrue(Reviews.ready(listOf(v), cards, 4 * day).isEmpty())
        assertEquals(1, Reviews.ready(listOf(v), cards, 5 * day).size)
    }

    @Test fun aMarkWithNoWordsOrNoteIsNotReviewed() {
        val v = SavedVideo(VideoMeta("ccccccccccc", "t", "a"), highlights = listOf(Highlight("m", 1.0, 1.0, "", "")), addedAt = 0L)
        assertTrue(Reviews.ready(listOf(v), emptyMap(), 100 * day).isEmpty())
    }

    @Test fun theLongestWaitingComesFirst() {
        val a = SavedVideo(VideoMeta("aaaaaaaaaaa", "a", "x"), highlights = listOf(Highlight("old", 1.0, 2.0, "old text")), addedAt = 0L)
        val b = SavedVideo(VideoMeta("bbbbbbbbbbb", "b", "x"), highlights = listOf(Highlight("new", 1.0, 2.0, "new text")), addedAt = 3 * day)
        assertEquals(listOf("old", "new"), Reviews.ready(listOf(b, a), emptyMap(), 10 * day).map { it.highlight.id })
    }
}

class HighlightTagTest {
    private val video = SavedVideo(VideoMeta("abc12345678", "A talk", "A speaker"))

    @Test fun tagsGoAfterTheNoteAsObsidianTags() {
        val h = Highlight("1", 83.0, 90.0, "words here", "see [[Memory palace]]", 0, tags = listOf("poem", "memorize"))
        val out = app.marginalia.obsidian.NoteBuilder.highlightEntry(video, h, app.obsi.core.data.AppSettings())
        assertTrue(out, out.endsWith("see [[Memory palace]] #poem #memorize"))
    }

    @Test fun noTagsAddsNothing() {
        val h = Highlight("1", 83.0, 90.0, "words here", "", 0)
        val out = app.marginalia.obsidian.NoteBuilder.highlightEntry(video, h, app.obsi.core.data.AppSettings())
        assertFalse(out.contains("#"))
    }

    @Test fun aTimedNoteKeepsItsTags() {
        val h = Highlight("1", 5.0, 5.0, "", "the drop", 0, tags = listOf("song"))
        val out = app.marginalia.obsidian.NoteBuilder.highlightEntry(video, h, app.obsi.core.data.AppSettings())
        assertTrue(out, out.endsWith("the drop #song"))
    }

    @Test fun searchFindsAHighlightByItsTag() {
        val v = video.copy(highlights = listOf(Highlight("1", 83.0, 90.0, "words here", "", 0, tags = listOf("poem"))))
        val hits = LibrarySearch.search("#poem", listOf(v), emptyList(), emptyList())
        assertTrue(hits.any { it.kind == HitKind.Highlight && it.sec == 83.0 })
        assertTrue(LibrarySearch.search("poem", listOf(v), emptyList(), emptyList()).any { it.kind == HitKind.Highlight })
    }
}
