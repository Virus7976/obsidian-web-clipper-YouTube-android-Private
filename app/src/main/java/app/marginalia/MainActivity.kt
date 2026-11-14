package app.marginalia

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.marginalia.data.ChannelSource
import app.marginalia.data.PlaylistImport
import app.marginalia.data.YouTube
import app.marginalia.ui.AppRoot

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MarginaliaApp
        app.player.connectService(this)
        incoming = linkFrom(intent)
        if (intent?.getBooleanExtra(ReviewReminder.EXTRA_REVIEW, false) == true) app.openReview.value = true
        if (intent?.getBooleanExtra(EXTRA_NOW_PLAYING, false) == true) app.openNowPlaying.value = true
        intent?.getStringExtra(SaveAsk.EXTRA_SAVE)?.let { app.askSave.value = it }
        setContent {
            AppRoot(app, incoming) { incoming = null }
        }
    }

    override fun onResume() {
        super.onResume()
        // The notification says this: open the app to unlock.
        (application as MarginaliaApp).player.phoneUnlocked()
    }

    override fun onStart() {
        super.onStart()
        val app = application as MarginaliaApp
        app.inForeground = true
        // The sheet is about to show, so the notification has done its job.
        if (app.askSave.value != null) SaveAsk.clear(app)
    }

    override fun onStop() {
        super.onStop()
        (application as MarginaliaApp).inForeground = false
        (application as MarginaliaApp).player.savePosition()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(ReviewReminder.EXTRA_REVIEW, false)) (application as MarginaliaApp).openReview.value = true
        if (intent.getBooleanExtra(EXTRA_NOW_PLAYING, false)) (application as MarginaliaApp).openNowPlaying.value = true
        intent.getStringExtra(SaveAsk.EXTRA_SAVE)?.let { (application as MarginaliaApp).askSave.value = it }
        linkFrom(intent)?.let { incoming = it }
    }

    companion object {
        const val EXTRA_NOW_PLAYING = "app.marginalia.NOW_PLAYING"
    }

    /** A link shared from the YouTube app, or tapped anywhere on the phone. */
    private fun linkFrom(intent: Intent?): String? {
        intent ?: return null
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return null
        YouTube.extractId(text)?.let { return "https://www.youtube.com/watch?v=$it" }
        // A playlist or a channel shared from YouTube.
        val url = ChannelSource.firstUrl(text) ?: text
        return if (PlaylistImport.listId(text) != null || ChannelSource.isChannelLink(text)) url else null
    }
}
