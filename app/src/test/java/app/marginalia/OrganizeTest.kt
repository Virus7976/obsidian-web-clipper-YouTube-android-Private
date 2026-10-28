package app.marginalia

import app.marginalia.data.AppSettings
import app.marginalia.data.PlaylistImport
import app.marginalia.data.SavedVideo
import app.marginalia.data.VideoMeta
import app.marginalia.data.YouTube
import app.marginalia.ui.LibraryFilter
import app.marginalia.ui.sortedBy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class OrganizeTest {
    private fun v(id: String, title: String, author: String = "a", tags: List<String> = emptyList(), cat: String = "", added: Long = 0) =
        SavedVideo(VideoMeta(id, title, author), tags = tags, category = cat, addedAt = added)

    @Test fun sortsByTitleAndAdded() {
        val list = listOf(v("1", "Zebra", added = 5), v("2", "apple", added = 9), v("3", "Mango", added = 1))
        assertEquals(listOf("2", "3", "1"), list.sortedBy("title").map { it.meta.id })
        assertEquals(listOf("2", "1", "3"), list.sortedBy("added").map { it.meta.id })
        assertEquals(listOf("1", "2", "3"), list.sortedBy("recent").map { it.meta.id })
    }

    @Test fun filtersByCategoryAndTagIgnoringCase() {
        val a = v("1", "x", tags = listOf("Focus"), cat = "Learning")
        assertTrue(LibraryFilter(category = "learning").matches(a))
        assertTrue(LibraryFilter(tag = "focus").matches(a))
        assertFalse(LibraryFilter(category = "learning", tag = "other").matches(a))
        assertTrue(LibraryFilter().matches(a))
    }

    @Test fun readsPlaylistLinks() {
        val pl = "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
        assertEquals("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", PlaylistImport.listId(pl))
        assertTrue(PlaylistImport.isPlaylistLink(pl))
        // a video inside a playlist is a video link, not a playlist import
        assertFalse(PlaylistImport.isPlaylistLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"))
        assertNull(PlaylistImport.listId("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test fun livePlaylistImport() {
        assumeTrue(System.getenv("YT_LIVE") == "1")
        val yt = YouTube { AppSettings() }
        val r = runBlocking { PlaylistImport.fetch(yt, "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf") }
        println("PLAYLIST ${r.title}: ${r.videos.size} videos, first=${r.videos.first().title}")
        assertTrue(r.videos.isNotEmpty())
    }
}
