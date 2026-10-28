package app.marginalia.player

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.marginalia.MarginaliaApp
import app.marginalia.R
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Keeps playback alive in the background and draws the media notification. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as MarginaliaApp
        val highlight = SessionCommand(ACTION_HIGHLIGHT, Bundle.EMPTY)

        // Back 10, Highlight, Forward 10. Play, pause and the seek bar are drawn by Android itself.
        val buttons = ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                .setDisplayName("Back 10 seconds")
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.ic_highlight)
                .setDisplayName("Highlight")
                .setSessionCommand(highlight)
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
                .setDisplayName("Forward 10 seconds")
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .build(),
        )

        session = MediaSession.Builder(this, app.player.exo)
            .setCustomLayout(buttons)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(highlight).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == ACTION_HIGHLIGHT) {
                        val message = QuickHighlight.run(app)
                        Handler(Looper.getMainLooper()).post { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
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

    companion object {
        const val ACTION_HIGHLIGHT = "app.marginalia.HIGHLIGHT"
    }
}
