package app.marginalia.player

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayerState(
    val videoId: String? = null,
    val title: String = "",
    val author: String = "",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val ready: Boolean = false,
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val speed: Float = 1f,
    val error: Int? = null,
)

/**
 * Plays the video with YouTube's own embedded player (the IFrame API). That is the
 * one surface YouTube promises to keep working, so playback does not need app updates.
 * The WebView lives here, not in the screen, so audio keeps going when the screen goes away.
 */
class PlayerHost(private val app: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val contextWrapper = MutableContextWrapper(app)
    private var web: WebView? = null
    private var controllerFuture: Any? = null

    val mediaPlayer: WebPlayer by lazy { WebPlayer(this) }

    // ---------- loading ----------

    fun load(id: String, title: String, author: String, startSec: Double) {
        val current = _state.value
        if (current.videoId == id && current.error == null) {
            // Already loaded: keep playing where we are, but remember the new title text.
            _state.update { it.copy(title = title, author = author) }
            mediaPlayer.refresh()
            return
        }
        _state.value = PlayerState(videoId = id, title = title, author = author, positionSec = startSec)
        val w = ensureWeb()
        w.loadDataWithBaseURL("https://www.youtube.com", shell(id, startSec.toInt()), "text/html", "utf-8", null)
        mediaPlayer.refresh()
    }

    fun connectService(context: Context) {
        if (controllerFuture != null) return
        runCatching {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(context.applicationContext, token).buildAsync()
        }
    }

    fun play() = js("if(window.player&&player.playVideo)player.playVideo()")
    fun pause() = js("if(window.player&&player.pauseVideo)player.pauseVideo()")
    fun toggle() = if (_state.value.playing) pause() else play()

    fun seekTo(sec: Double) {
        val s = sec.coerceAtLeast(0.0)
        js("if(window.player&&player.seekTo)player.seekTo($s,true)")
        _state.update { it.copy(positionSec = s) }
        mediaPlayer.refresh()
    }

    fun skip(deltaSec: Double) = seekTo(_state.value.positionSec + deltaSec)

    fun setSpeed(speed: Float) {
        js("if(window.player&&player.setPlaybackRate)player.setPlaybackRate($speed)")
        _state.update { it.copy(speed = speed) }
        mediaPlayer.refresh()
    }

    fun stop() {
        pause()
        _state.value = PlayerState()
        web?.loadUrl("about:blank")
        mediaPlayer.refresh()
    }

    private fun js(code: String) {
        main.post { web?.evaluateJavascript(code, null) }
    }

    // ---------- the view ----------

    fun attach(frame: FrameLayout) {
        val w = ensureWeb()
        (w.parent as? ViewGroup)?.removeView(w)
        contextWrapper.baseContext = frame.context
        frame.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun detach(frame: FrameLayout) {
        val w = web ?: return
        if (w.parent === frame) frame.removeView(w)
        contextWrapper.baseContext = app
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWeb(): WebView {
        web?.let { return it }
        val w = WebView(contextWrapper)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        w.setBackgroundColor(Color.BLACK)
        w.addJavascriptInterface(PlayerBridge(this), "Bridge")
        w.webViewClient = object : WebViewClient() {
            // Never let the player page navigate away to somewhere else.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = !request.url.host.orEmpty().let {
                it.endsWith("youtube.com") || it.endsWith("youtube-nocookie.com") || it.endsWith("google.com") || it.endsWith("googlevideo.com") || it.endsWith("ytimg.com")
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = Unit
        }
        web = w
        return w
    }

    private fun shell(id: String, start: Int) = """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;left:0;top:0;width:100%;height:100%}</style>
        </head><body><div id="p"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
        var player=null,timer=null;
        function onYouTubeIframeAPIReady(){
          player=new YT.Player('p',{
            width:'100%',height:'100%',videoId:'$id',
            playerVars:{playsinline:1,rel:0,fs:0,start:$start,enablejsapi:1,origin:'https://www.youtube.com',autoplay:0},
            events:{
              onReady:function(){Bridge.onReady(player.getDuration());timer=setInterval(tick,400);},
              onStateChange:function(e){Bridge.onState(e.data);},
              onError:function(e){Bridge.onError(e.data);}
            }
          });
        }
        function tick(){if(player&&player.getCurrentTime){Bridge.onTime(player.getCurrentTime(),player.getDuration());}}
        </script></body></html>
    """.trimIndent()

    // ---------- called by the JS bridge, on the main thread ----------

    internal fun bridgeReady(duration: Double) {
        _state.update { it.copy(ready = true, durationSec = duration.coerceAtLeast(0.0), error = null) }
        mediaPlayer.refresh()
    }

    internal fun bridgeState(code: Int) {
        _state.update {
            it.copy(
                playing = code == 1,
                buffering = code == 3,
                ended = code == 0,
                error = null,
            )
        }
        mediaPlayer.refresh()
    }

    internal fun bridgeTime(position: Double, duration: Double) {
        _state.update { it.copy(positionSec = position, durationSec = if (duration > 0) duration else it.durationSec) }
    }

    internal fun bridgeError(code: Int) {
        _state.update { it.copy(error = code, playing = false) }
        mediaPlayer.refresh()
    }

    internal fun post(block: () -> Unit) {
        main.post(block)
    }
}

class PlayerBridge(private val host: PlayerHost) {
    @JavascriptInterface
    fun onReady(duration: Double) = host.post { host.bridgeReady(duration) }

    @JavascriptInterface
    fun onState(code: Int) = host.post { host.bridgeState(code) }

    @JavascriptInterface
    fun onTime(position: Double, duration: Double) = host.post { host.bridgeTime(position, duration) }

    @JavascriptInterface
    fun onError(code: Int) = host.post { host.bridgeError(code) }
}
