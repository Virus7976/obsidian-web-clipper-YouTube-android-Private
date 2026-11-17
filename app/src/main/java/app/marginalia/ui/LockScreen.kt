package app.marginalia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.obsi.core.data.Cue
import app.obsi.core.data.Highlight
import app.obsi.core.ui.LockItem
import app.obsi.core.ui.LockPlaying
import app.obsi.core.ui.LockScreenHost
import kotlinx.coroutines.flow.map
import app.marginalia.player.PlayerState
import app.marginalia.player.QuickHighlight
import app.marginalia.MarginaliaApp
import app.obsi.core.ui.LockScreen as SharedLockScreen

/** Ties the app's player and library to the shared lock screen in obsi-core. */
class AppLockHost(private val app: MarginaliaApp) : LockScreenHost {
    override val settings get() = app.settings
    override val locked get() = app.player.locked
    override fun setLocked(on: Boolean) = app.player.setLocked(on)
    override val playing = app.player.state.map { it.toLock() }
    override val playingNow get() = app.player.state.value.toLock()
    override fun skip(deltaSec: Double) { app.player.skip(deltaSec) }
    override fun seekTo(sec: Double) = app.player.seekTo(sec)
    override fun toggle() { app.player.toggle() }
    override val library get() = app.library.videos
    override fun item(id: String) = app.library.get(id)?.let { LockItem(it.meta.title, it.meta.author, it.chapters, it.highlights) }
    override fun cues(id: String): List<Cue>? = app.library.loadTranscript(id)?.cues
    override fun editHighlights(id: String, change: (List<Highlight>) -> List<Highlight>) =
        app.library.edit(id) { it.copy(highlights = change(it.highlights)) }
    override fun art(id: String): String? = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    override fun quickHighlight(): String = QuickHighlight.run(app)

    private fun PlayerState.toLock() = LockPlaying(videoId, title, author, playing, positionSec, durationSec)
}

/** The lock screen. The screen itself is shared by every Obsi app with a player, in obsi-core. */
@Composable
fun LockScreen(app: MarginaliaApp, onUnlock: () -> Unit, onClose: () -> Unit) {
    val host = remember(app) { AppLockHost(app) }
    SharedLockScreen(host, onUnlock, onClose)
}
