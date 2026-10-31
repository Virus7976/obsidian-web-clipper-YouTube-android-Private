package app.marginalia

import android.app.Application
import app.marginalia.data.AudioCache
import app.marginalia.data.Library
import app.marginalia.data.PlayQueue
import app.marginalia.data.Playlists
import app.marginalia.data.SettingsStore
import app.marginalia.data.StreamFinder
import app.marginalia.data.VaultStore
import app.marginalia.update.SelfUpdate
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
    lateinit var updates: SelfUpdate
        private set
    lateinit var vaults: VaultStore
        private set
    lateinit var playlists: Playlists
        private set
    lateinit var queue: PlayQueue
        private set
    lateinit var subscriptions: app.marginalia.data.Subscriptions
        private set
    lateinit var feed: app.marginalia.data.FeedStore
        private set
    lateinit var audio: AudioCache
        private set

    /** Fires with the video id when the queue starts the next video by itself. */
    val advanced = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        vaults = VaultStore(this, settings)
        updates = SelfUpdate(this, settings)
        library = Library(this)
        playlists = Playlists(this)
        queue = PlayQueue(settings)
        youtube = YouTube { settings.value }
        subscriptions = app.marginalia.data.Subscriptions(this)
        feed = app.marginalia.data.FeedStore(this, subscriptions, youtube)
        val finder = StreamFinder(youtube) { settings.value }
        audio = AudioCache(this, youtube, finder) { settings.value }
        player = PlayerHost(this, finder) { settings.value }
        player.cache = audio
        player.onEnded = { player.state.value.videoId?.let(::markWatched); playNextInQueue() }
        player.onWatched = ::markWatched
        seedFromBundledClipperSettings()
        updates.checkIfDue()
    }

    /** A video was watched to the end. It leaves every playlist that is set to drop watched videos. */
    fun markWatched(id: String) {
        playlists.all.value.filter { it.removeWhenWatched && id in it.videoIds }.forEach { p ->
            playlists.removeVideo(p.id, id)
            // An imported playlist hides its videos from the library. Let this one go there.
            if (library.get(id)?.playlistId == p.id) library.edit(id) { it.copy(playlistId = "") }
        }
    }

    /** Puts a video in the library if it is not there yet, reading its details first. False when it cannot be read. */
    suspend fun addToLibrary(id: String): Boolean {
        if (library.get(id) != null) return true
        val meta = runCatching { youtube.preview(id) }.getOrNull() ?: return false
        library.upsert(app.marginalia.data.SavedVideo(meta = meta, addedAt = System.currentTimeMillis()))
        return true
    }

    /** Starts the next queued video. Skips ids that are no longer in the library. */
    fun playNextInQueue() {
        while (true) {
            val id = queue.pop() ?: return
            val v = library.get(id) ?: continue
            player.load(id, v.meta.title, v.meta.author, 0.0, force = true)
            advanced.tryEmit(id)
            return
        }
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
