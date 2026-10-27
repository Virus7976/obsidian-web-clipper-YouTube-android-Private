package app.marginalia.player

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Shows the embedded YouTube player to Android as a normal media player, so the
 * lock screen, the notification and headset buttons all control it.
 */
class WebPlayer(private val host: PlayerHost) : SimpleBasePlayer(Looper.getMainLooper()) {

    fun refresh() = invalidateState()

    override fun getState(): State {
        val s = host.state.value
        val commands = Player.Commands.Builder()
            .addAll(
                COMMAND_PLAY_PAUSE,
                COMMAND_PREPARE,
                COMMAND_STOP,
                COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                COMMAND_SEEK_BACK,
                COMMAND_SEEK_FORWARD,
                COMMAND_GET_CURRENT_MEDIA_ITEM,
                COMMAND_GET_METADATA,
                COMMAND_GET_TIMELINE,
                COMMAND_SET_SPEED_AND_PITCH,
            ).build()
        val id = s.videoId ?: return State.Builder()
            .setAvailableCommands(commands)
            .setPlaybackState(STATE_IDLE)
            .build()

        val item = MediaItemData.Builder(id)
            .setMediaItem(
                MediaItem.Builder()
                    .setMediaId(id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.author)
                            .setArtworkUri(Uri.parse("https://i.ytimg.com/vi/$id/hqdefault.jpg"))
                            .build(),
                    ).build(),
            )
            .setDurationUs(if (s.durationSec > 0) (s.durationSec * 1_000_000).toLong() else C.TIME_UNSET)
            .setIsSeekable(true)
            .build()

        val positionMs = (s.positionSec * 1000).toLong()
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            .setPlaybackState(
                when {
                    s.ended -> STATE_ENDED
                    !s.ready || s.buffering -> STATE_BUFFERING
                    else -> STATE_READY
                },
            )
            .setPlayWhenReady(s.playing, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs(
                if (s.playing) PositionSupplier.getExtrapolating(positionMs, s.speed) else PositionSupplier.getConstant(positionMs),
            )
            .setPlaybackParameters(PlaybackParameters(s.speed))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) host.play() else host.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        host.seekTo(positionMs / 1000.0)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        host.setSpeed(playbackParameters.speed)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        host.pause()
        return Futures.immediateVoidFuture()
    }
}
