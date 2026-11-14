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

/** Where one highlight is in its review schedule. A highlight with no card yet is new. */
@Serializable
data class ReviewCard(
    val id: String,
    val due: Long = 0L,
    /** How many times you kept it. The wait grows with each one. */
    val step: Int = 0,
    val dropped: Boolean = false,
)

/** A highlight that is ready to look at again, with the video it came from. */
data class DueItem(val video: SavedVideo, val highlight: Highlight)

/**
 * Review mode. A highlight comes back after a day, then 3, 7, 16, 35, 90 and 180 days each time you keep it.
 * Dropping it only stops it coming back here. The highlight stays in the video and in your note.
 */
class Reviews(context: Context) : JsonFileStore<List<ReviewCard>>(File(context.filesDir, "review.json")) {
    private val _state = MutableStateFlow(readOrNull().orEmpty().associateBy { it.id })
    val cards: StateFlow<Map<String, ReviewCard>> = _state.asStateFlow()

    override fun encode(value: List<ReviewCard>) = storeJson.encodeToString(ListSerializer(ReviewCard.serializer()), value)
    override fun decode(text: String) = storeJson.decodeFromString(ListSerializer(ReviewCard.serializer()), text)

    private fun save() = persist(_state.value.values.toList())

    private fun put(card: ReviewCard) {
        _state.update { it + (card.id to card) }
        save()
    }

    /** The highlights ready now, the longest waiting first. */
    fun due(videos: List<SavedVideo>, now: Long = System.currentTimeMillis()): List<DueItem> = ready(videos, _state.value, now)

    fun keep(id: String, now: Long = System.currentTimeMillis()) {
        val step = (_state.value[id]?.step ?: 0)
        put(ReviewCard(id, now + DAYS[step.coerceAtMost(DAYS.lastIndex)] * DAY, step + 1))
    }

    /** Puts a card back the way it was. For Undo. A null [previous] means it had no card yet. */
    fun restore(id: String, previous: ReviewCard?) {
        _state.update { if (previous == null) it - id else it + (id to previous) }
        save()
    }

    /** Not now. Comes back tomorrow without counting as kept. */
    fun later(id: String, now: Long = System.currentTimeMillis()) {
        val c = _state.value[id]
        put(ReviewCard(id, now + DAY, c?.step ?: 0, c?.dropped ?: false))
    }

    fun drop(id: String) {
        val c = _state.value[id]
        put(ReviewCard(id, c?.due ?: 0L, c?.step ?: 0, dropped = true))
    }

    companion object {
        const val DAY = 24L * 60 * 60 * 1000
        val DAYS = longArrayOf(1, 3, 7, 16, 35, 90, 180)

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
