package app.marginalia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.marginalia.data.YouTube
import app.obsi.core.ui.ReviewEntry
import app.obsi.core.ui.ReviewSession

/** Review mode. The screen itself is shared by every Obsi app, in obsi-core. */
@Composable
fun ReviewScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val items by app.library.videos.collectAsState()
    val settings by app.settings.state.collectAsState()
    ReviewSession(
        onBack = onBack,
        cards = app.reviews,
        batchSize = settings.reviewBatch,
        changes = items,
        due = { app.reviews.due(items).map { ReviewEntry(it.highlight, it.video.meta.id, it.video.meta.title, YouTube.formatTime(it.highlight.startSec)) } },
        onOpen = { e ->
            app.library.edit(e.itemId) { it.copy(positionSec = e.highlight.startSec) }
                if (app.player.state.value.videoId == e.itemId) app.player.seekTo(e.highlight.startSec)
            app.pendingSeek.value = e.itemId to e.highlight.startSec
            nav.openVideo(e.itemId)
        },
    )
}
