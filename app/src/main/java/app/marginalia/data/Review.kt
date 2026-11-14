package app.marginalia.data

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** A highlight that is ready to look at again, with the video it came from. */
data class DueItem(val video: SavedVideo, val highlight: Highlight)

/** The shared review schedule, with this app's own list of what is due. */
class Reviews(context: Context) : ReviewCards(context) {
    /** The highlights ready now, the longest waiting first. */
    fun due(videos: List<SavedVideo>, now: Long = System.currentTimeMillis()): List<DueItem> = ready(videos, cards.value, now)

    companion object {
        const val DAY = ReviewCards.DAY
        val DAYS = ReviewCards.DAYS

        /** Pure, so it can be tested. */
        fun ready(videos: List<SavedVideo>, cards: Map<String, ReviewCard>, now: Long): List<DueItem> {
            val out = ArrayList<Pair<Long, DueItem>>()
            for (v in videos) for (h in v.highlights) {
                if (h.text.isBlank() && h.note.isBlank()) continue
                val c = cards[h.id]
                if (c?.dropped == true) continue
                // A new highlight waits one day, counted from when it was made.
                val due = c?.due ?: (maxOf(h.createdAt, v.addedAt) + DAY)
                if (due <= now) out += due to DueItem(v, h)
            }
            return out.sortedBy { it.first }.map { it.second }
        }

    }
}
