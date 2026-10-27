package app.marginalia.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.marginalia.MarginaliaApp

/** Keeps playback alive in the background and draws the media notification. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as MarginaliaApp
        session = MediaSession.Builder(this, app.player.mediaPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val app = application as MarginaliaApp
        // Swiping the app away stops playback unless the user turned on background play.
        if (!app.settings.value.backgroundPlay || !app.player.state.value.playing) {
            app.player.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
