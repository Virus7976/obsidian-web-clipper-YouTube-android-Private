package app.marginalia.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.marginalia.data.AppSettings
import app.marginalia.data.StreamFinder
import app.marginalia.data.Streams
import app.marginalia.data.userAgentFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerState(
    val videoId: String? = null,
    val title: String = "",
    val author: String = "",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val loading: Boolean = false,
    val ended: Boolean = false,
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val speed: Float = 1f,
    val error: String? = null,
    val videoOn: Boolean = false,
    val canWatch: Boolean = false,
    /** Which fallback tier is playing right now. */
    val via: String = "",
)

/**
 * The one player for the whole app. It lives here, not in a screen, so audio keeps going when
 * the screen goes away. Sources come from [StreamFinder]; if one dies, the next one takes over
 * at the same spot.
 */
class PlayerHost(
    private val app: Application,
    private val finder: StreamFinder,
    private val settings: () -> AppSettings,
) {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val http = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    val exo: ExoPlayer by lazy {
        ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { it.addListener(listener) }
    }

    private var controllerFuture: Any? = null
    private var queue: ReceiveChannel<Streams>? = null
    private var current: Streams? = null
    private var job: Job? = null
    private var failures = 0

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    buffering = playbackState == Player.STATE_BUFFERING,
                    ended = playbackState == Player.STATE_ENDED,
                    durationSec = exo.duration.takeIf { d -> d != C.TIME_UNSET }?.div(1000.0) ?: it.durationSec,
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(playing = isPlaying) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            _state.update { it.copy(speed = playbackParameters.speed) }
        }

        override fun onPlayerError(error: PlaybackException) {
            // This source died. Move to the next one and carry on from the same second.
            next(exo.currentPosition / 1000.0, error.errorCodeName)
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (_state.value.videoId != null) {
                val pos = exo.currentPosition / 1000.0
                val dur = exo.duration.takeIf { it != C.TIME_UNSET }?.div(1000.0)
                _state.update { s ->
                    if (kotlin.math.abs(s.positionSec - pos) < 0.04 && (dur == null || dur == s.durationSec)) s
                    else s.copy(positionSec = pos, durationSec = dur ?: s.durationSec)
                }
            }
            main.postDelayed(this, 200)
        }
    }

    // ---------- loading ----------

    fun load(id: String, title: String, author: String, startSec: Double) {
        val cur = _state.value
        if (cur.videoId == id && cur.error == null && (cur.loading || exo.mediaItemCount > 0)) {
            _state.update { it.copy(title = title, author = author) }
            return
        }
        begin(id, title, author, startSec, settings().autoPlay)
    }

    fun retry() {
        val s = _state.value
        val id = s.videoId ?: return
        begin(id, s.title, s.author, s.positionSec, true)
    }

    private fun begin(id: String, title: String, author: String, startSec: Double, autoplay: Boolean) {
        job?.cancel()
        queue?.cancel()
        exo.stop()
        exo.clearMediaItems()
        current = null
        failures = 0
        _state.value = PlayerState(videoId = id, title = title, author = author, positionSec = startSec, loading = true, speed = _state.value.speed)
        main.removeCallbacks(poll)
        main.post(poll)

        // The flow is lazy. The next source is only looked up if the current one fails.
        val q = finder.candidates(id).produceIn(scope)
        queue = q
        job = scope.launch {
            val first = q.receiveCatching().getOrNull()
            if (first == null) fail("No source would play this video. Check your connection, then try again.")
            else start(first, startSec, autoplay)
        }
    }

    private fun next(resumeSec: Double, why: String) {
        val q = queue ?: return
        failures++
        _state.update { it.copy(loading = true, buffering = false) }
        job = scope.launch {
            val n = q.receiveCatching().getOrNull()
            if (n == null) fail("Every source failed ($why). Try again in a minute.")
            else start(n, resumeSec, true)
        }
    }

    private fun start(s: Streams, startSec: Double, autoplay: Boolean) {
        current = s
        val st = _state.value
        val useVideo = st.videoOn && s.videoUrl != null
        val url = if (useVideo) s.videoUrl!! else s.audioUrl
        http.setUserAgent(userAgentFor(url, settings()))
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId(st.videoId.orEmpty())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(st.title)
                    .setArtist(st.author)
                    .setArtworkUri(Uri.parse("https://i.ytimg.com/vi/${st.videoId}/hqdefault.jpg"))
                    .build(),
            )
            .build()
        exo.setMediaItem(item, (startSec * 1000).toLong())
        exo.prepare()
        exo.playWhenReady = autoplay
        _state.update { it.copy(loading = false, error = null, canWatch = s.videoUrl != null, via = s.via, videoOn = useVideo) }
    }

    private fun fail(message: String) {
        _state.update { it.copy(loading = false, buffering = false, playing = false, error = message) }
    }

    /** Switch between listening and watching without losing the spot. */
    fun setVideo(on: Boolean) {
        val s = current ?: return
        if (on && s.videoUrl == null) return
        _state.update { it.copy(videoOn = on) }
        start(s, exo.currentPosition / 1000.0, exo.playWhenReady)
    }

    fun connectService(context: Context) {
        if (controllerFuture != null) return
        runCatching {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(context.applicationContext, token).buildAsync()
        }
    }

    // ---------- controls ----------

    fun play() {
        if (_state.value.ended) exo.seekTo(0)
        exo.play()
    }

    fun pause() = exo.pause()
    fun toggle() = if (exo.isPlaying || exo.playWhenReady) pause() else play()

    fun seekTo(sec: Double) {
        val s = sec.coerceAtLeast(0.0)
        exo.seekTo((s * 1000).toLong())
        _state.update { it.copy(positionSec = s) }
    }

    fun skip(deltaSec: Double) = seekTo(exo.currentPosition / 1000.0 + deltaSec)

    fun setSpeed(speed: Float) = exo.setPlaybackSpeed(speed)

    fun stop() {
        job?.cancel()
        queue?.cancel()
        queue = null
        exo.stop()
        exo.clearMediaItems()
        main.removeCallbacks(poll)
        _state.value = PlayerState(speed = _state.value.speed)
    }
}
