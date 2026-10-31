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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    lateinit var reviews: app.marginalia.data.Reviews
        private set

    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    /** A highlight picked in search: the reader scrolls to it when it opens. Video id and second. */
    val pendingSeek = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, Double>?>(null)

    /** Set when the review reminder was tapped. The app opens Review and clears it. */
    val openReview = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Fires with the video id when the queue starts the next video by itself. */
    val advanced = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        vaults = VaultStore(this, settings)
        updates = SelfUpdate(this, settings)
        library = Library(this)
        reviews = app.marginalia.data.Reviews(this)
        playlists = Playlists(this)
        queue = PlayQueue(settings)
        youtube = YouTube { settings.value }
        subscriptions = app.marginalia.data.Subscriptions(this)
        feed = app.marginalia.data.FeedStore(this, subscriptions, youtube)
        val finder = StreamFinder(youtube) { settings.value }
        audio = AudioCache(this, youtube, finder) { settings.value }
        player = PlayerHost(this, finder) { settings.value }
        player.cache = audio
        player.onEnded = { player.state.value.videoId?.let { markWatched(it); autoClip(it) }; playNextInQueue() }
        player.onWatched = { markWatched(it); autoClip(it) }
        player.onPosition = { id, sec -> library.edit(id) { it.copy(positionSec = sec) } }
        // The reminder alarm follows the settings.
        appScope.launch {
            settings.state.map { it.reviewReminder to it.reviewHour }.distinctUntilChanged().collect { (on, hour) ->
                ReviewReminder.schedule(this@MarginaliaApp, on, hour)
            }
        }
        seedFromBundledClipperSettings()
        updates.checkIfDue()
    }

    /** Saves the highlights to Obsidian when a video ends, if that is switched on. */
    fun autoClip(id: String) {
        if (!settings.value.autoClip) return
        // It runs near the end and again at the end. The second run has work only if something changed.
        val now = library.get(id)?.highlights ?: return
        if (autoClipped[id] == now) return
        autoClipped[id] = now
        appScope.launch {
            val message = runCatching { app.marginalia.obsidian.AutoClip.run(this@MarginaliaApp, id) }.getOrNull() ?: return@launch
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(this@MarginaliaApp, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private val autoClipped = HashMap<String, List<app.marginalia.data.Highlight>>()

    /** A channel page that was just read, so coming back from a video does not start it over. */
    class ChannelPage(val opened: app.marginalia.data.ChannelSource.Opened, var videos: List<app.marginalia.data.ChannelVideo>, var more: Boolean, val at: Long)
    val channelPages = HashMap<String, ChannelPage>()

    /** Takes a video off this phone: playback, queue, saved audio, playlists and library. */
    fun removeVideo(id: String) {
        if (player.state.value.videoId == id) player.stop()
        queue.remove(id)
        audio.remove(id)
        playlists.all.value.filter { id in it.videoIds }.forEach { playlists.removeVideo(it.id, id) }
        library.remove(id)
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
