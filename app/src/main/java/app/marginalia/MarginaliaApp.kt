package app.marginalia

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import android.app.Application
import app.marginalia.data.AudioCache
import app.marginalia.data.Library
import app.obsi.core.data.PlayQueue
import app.obsi.core.data.Playlists
import app.obsi.core.data.SettingsStore
import app.marginalia.data.StreamFinder
import app.obsi.core.data.VaultStore
import app.obsi.core.update.SelfUpdate
import app.marginalia.data.YouTube
import app.obsi.core.obsidian.ClipperImport
import app.marginalia.player.PlayerHost
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MarginaliaApp : Application(), app.obsi.core.ObsiHost {
    override lateinit var settings: SettingsStore
        private set
    lateinit var library: Library
        private set
    lateinit var youtube: YouTube
        private set
    lateinit var player: PlayerHost
        private set
    override lateinit var updates: SelfUpdate
        private set
    override lateinit var vaults: VaultStore
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

    override val recentTags: kotlinx.coroutines.flow.StateFlow<List<String>> by lazy {
        settings.state.map { it.recentTags }.stateIn(appScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, settings.value.recentTags)
    }

    /** A video another Obsi app asked to open, and the second to start at. The app opens it and clears it. */
    val openItem = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, Double>?>(null)

    /** A highlight picked in search: the reader scrolls to it when it opens. Video id and second. */
    val pendingSeek = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, Double>?>(null)

    /** Set when the review reminder was tapped. The app opens Review and clears it. */
    val openReview = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Set when the media notification was tapped. The app opens the playing video and clears it. */
    val openNowPlaying = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** A video whose new note needs your details before it saves. The app shows the save sheet for it. */
    val askSave = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** True while the app is on screen. */
    @Volatile var inForeground = false

    /** Fires with the video id when the queue starts the next video by itself. */
    val advanced = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)

    override fun onCreate() {
        super.onCreate()
        app.obsi.core.Brand.init(app.obsi.core.ObsiApp.Tube)
        app.obsi.core.share.SharedHighlights.source = ::sharedHighlights
        app.obsi.core.share.SharedSetup.source = { app.obsi.core.share.SharedSetup.of(settings.value) }
        app.obsi.core.share.SharedReview.due = { reviews.due(library.videos.value).map { shared(it.video, it.highlight) } }
        app.obsi.core.share.SharedReview.act = { key, action -> reviews.act(key, action) }
        settings = SettingsStore(this)
        vaults = VaultStore(this) { settings.value.vaultFolders }
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
        player.onPosition = { id, sec -> library.edit(id) { it.copy(positionSec = sec, playedAt = System.currentTimeMillis()) } }
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
                // A new note with empty fields waits for you: the save sheet opens, or a notification asks.
                if (message == app.marginalia.obsidian.AutoClip.ASK) {
                    askSave.value = id
                    if (!inForeground) SaveAsk.post(this@MarginaliaApp, id)
                    return@withContext
                }
                android.widget.Toast.makeText(this@MarginaliaApp, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private val autoClipped = HashMap<String, List<app.obsi.core.data.Highlight>>()

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

    /** Done by hand. It works like watching to the end, and a finished video has no place in the queue. */
    fun setDone(id: String, done: Boolean) {
        if (done) { markWatched(id); queue.remove(id) }
        else library.edit(id) { it.copy(doneAt = 0L) }
    }

    /** A video was watched to the end. It counts as done, and leaves every playlist that is set to drop watched videos. */
    fun markWatched(id: String) {
        library.edit(id) { if (it.done) it else it.copy(doneAt = System.currentTimeMillis()) }
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
    override fun sampleVariables(s: app.obsi.core.data.AppSettings): Map<String, String> {
        val v = library.videos.value.maxByOrNull { maxOf(it.playedAt, it.updatedAt) } ?: return emptyMap()
        val lines = library.loadTranscript(v.meta.id)?.let { YouTube.toLines(it.cues, s.lineLength, v.chapters) }.orEmpty()
        return app.marginalia.obsidian.NoteBuilder.variables(v, lines, s)
    }

    override fun previewLockScreen(context: android.content.Context) = LockScreenActivity.launch(context)

    @androidx.compose.runtime.Composable
    override fun IconPicker() = app.marginalia.ui.AppIconPicker()

    /** Every highlight, for the other Obsi apps to show. */
    private fun sharedHighlights(): List<app.obsi.core.share.SharedHighlight> =
        library.videos.value.flatMap { v -> v.highlights.map { h -> shared(v, h) } }

    private fun shared(v: app.marginalia.data.SavedVideo, h: app.obsi.core.data.Highlight) = app.obsi.core.share.SharedHighlight(
        app = app.obsi.core.ObsiApp.Tube, itemId = v.meta.id, itemTitle = v.meta.title, text = h.text, note = h.note,
        color = HighlightColors.getOrNull(h.color)?.ui ?: 0xFFF2C14E, at = h.startSec.toString(),
        place = YouTube.formatTime(h.startSec), created = h.createdAt.takeIf { it > 0 } ?: v.addedAt, tags = h.tags, key = h.id,
    )

    private fun seedFromBundledClipperSettings() {
        if (settings.value.templates.isNotEmpty()) return
        runCatching {
            val text = assets.open("default_clipper_settings.json").bufferedReader().use { it.readText() }
            settings.update { ClipperImport.apply(text, it) }
        }
    }
}
