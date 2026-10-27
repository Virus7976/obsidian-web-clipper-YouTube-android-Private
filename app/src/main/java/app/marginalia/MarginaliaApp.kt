package app.marginalia

import android.app.Application
import app.marginalia.data.Library
import app.marginalia.data.SettingsStore
import app.marginalia.data.YouTube
import app.marginalia.obsidian.ClipperImport
import app.marginalia.player.PlayerHost

class MarginaliaApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var library: Library
        private set
    lateinit var youtube: YouTube
        private set
    lateinit var player: PlayerHost
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        library = Library(this)
        youtube = YouTube { settings.value }
        player = PlayerHost(this)
        seedFromBundledClipperSettings()
    }

    /** First launch: load the clipper templates that ship inside the app. */
    private fun seedFromBundledClipperSettings() {
        if (settings.value.templates.isNotEmpty()) return
        runCatching {
            val text = assets.open("default_clipper_settings.json").bufferedReader().use { it.readText() }
            settings.update { ClipperImport.apply(text, it) }
        }
    }
}
