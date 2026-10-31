package app.marginalia.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

internal val storeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

/** Small JSON-file store. One file, rewritten on change. Plenty for a personal library. */
abstract class JsonFileStore<T>(private val file: File) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    protected abstract fun encode(value: T): String
    protected abstract fun decode(text: String): T

    protected fun readOrNull(): T? = runCatching { decode(file.readText()) }.getOrNull()

    protected fun persist(value: T) {
        scope.launch {
            lock.withLock {
                runCatching {
                    file.parentFile?.mkdirs()
                    val tmp = File(file.path + ".tmp")
                    tmp.writeText(encode(value))
                    tmp.renameTo(file)
                }
            }
        }
    }
}

class SettingsStore(context: Context) : JsonFileStore<AppSettings>(File(context.filesDir, "settings.json")) {
    private val _state = MutableStateFlow(migrate(readOrNull() ?: AppSettings()))
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val value: AppSettings get() = _state.value

    val isFirstRun: Boolean = readOrNull() == null

    private fun migrate(s0: AppSettings): AppSettings {
        var s = s0
        if (s.tabsVersion < 1) {
            s = s.copy(
                homeTabs = (s.homeTabs.filter { it == "add" || it == "library" } + listOf("add", "library", "playlists", "queue")).distinct()
                    .filter { it in setOf("add", "library", "playlists", "queue") },
            )
        }
        if (s.tabsVersion < 2 && s.vaultFolders.isNotEmpty()) {
            // Older versions copied vault names from imported templates and from clips into the list.
            // Keep the vaults that have a folder, and the one that is selected.
            val keep = s.vaults.filter { it in s.vaultFolders || it == s.vault }
            s = s.copy(vaults = keep, vault = if (s.vault in keep) s.vault else keep.firstOrNull().orEmpty())
        }
        if (s.tabsVersion < 3) s = s.copy(lockStyle = "page", lockBackground = "black")
        // Adding a video is a pop-up now, and playlists live inside the library.
        if (s.tabsVersion < 4) s = s.copy(homeTabs = listOf("library", "feed", "queue"))
        return s.copy(tabsVersion = 4)
    }

    override fun encode(value: AppSettings) = storeJson.encodeToString(AppSettings.serializer(), value)
    override fun decode(text: String) = storeJson.decodeFromString(AppSettings.serializer(), text)

    fun update(block: (AppSettings) -> AppSettings) {
        _state.update(block)
        persist(_state.value)
    }
}

class Library(private val context: Context) :
    JsonFileStore<List<SavedVideo>>(File(context.filesDir, "library.json")) {
    private val _state = MutableStateFlow(readOrNull().orEmpty())
    val videos: StateFlow<List<SavedVideo>> = _state.asStateFlow()

    override fun encode(value: List<SavedVideo>) = storeJson.encodeToString(ListSerializer(SavedVideo.serializer()), value)
    override fun decode(text: String) = storeJson.decodeFromString(ListSerializer(SavedVideo.serializer()), text)

    fun get(id: String): SavedVideo? = _state.value.firstOrNull { it.meta.id == id }

    fun upsert(video: SavedVideo) {
        _state.update { list ->
            val without = list.filterNot { it.meta.id == video.meta.id }
            listOf(video.copy(updatedAt = System.currentTimeMillis())) + without
        }
        persist(_state.value)
    }

    /** Change one video in place without moving it to the top. */
    fun edit(id: String, block: (SavedVideo) -> SavedVideo) {
        _state.update { list -> list.map { if (it.meta.id == id) block(it) else it } }
        persist(_state.value)
    }

    fun remove(id: String) {
        _state.update { list -> list.filterNot { it.meta.id == id } }
        persist(_state.value)
        File(transcriptDir(), "$id.json").delete()
    }

    private fun transcriptDir() = File(context.filesDir, "transcripts").also { it.mkdirs() }

    fun saveTranscript(id: String, data: TranscriptData) {
        runCatching {
            File(transcriptDir(), "$id.json").writeText(storeJson.encodeToString(TranscriptData.serializer(), data))
        }
    }

    fun loadTranscript(id: String): TranscriptData? = runCatching {
        storeJson.decodeFromString(TranscriptData.serializer(), File(transcriptDir(), "$id.json").readText())
    }.getOrNull()
}

/** Imported playlists. The videos live in [Library], marked with the playlist id. */
const val WATCH_LATER = "watch-later"

class Playlists(context: Context) : JsonFileStore<List<Playlist>>(File(context.filesDir, "playlists.json")) {
    private val _state = MutableStateFlow(readOrNull().orEmpty())
    val all: StateFlow<List<Playlist>> = _state.asStateFlow()

    override fun encode(value: List<Playlist>) = storeJson.encodeToString(ListSerializer(Playlist.serializer()), value)
    override fun decode(text: String) = storeJson.decodeFromString(ListSerializer(Playlist.serializer()), text)

    fun get(id: String): Playlist? = _state.value.firstOrNull { it.id == id }

    fun put(p: Playlist) {
        _state.update { list -> listOf(p) + list.filterNot { it.id == p.id } }
        persist(_state.value)
    }

    fun remove(id: String) {
        _state.update { list -> list.filterNot { it.id == id } }
        persist(_state.value)
    }

    /** A playlist you make yourself. */
    fun create(title: String, removeWhenWatched: Boolean = false): Playlist {
        val now = System.currentTimeMillis()
        val p = Playlist("my-" + now.toString(36), title.trim(), "", emptyList(), now, removeWhenWatched)
        put(p)
        return p
    }

    /** The built in "Watch later" playlist. Made the first time it is used. Videos leave it when watched. */
    fun watchLater(): Playlist = get(WATCH_LATER)
        ?: Playlist(WATCH_LATER, "Watch later", "", emptyList(), System.currentTimeMillis(), removeWhenWatched = true).also { put(it) }

    fun setRemoveWhenWatched(id: String, on: Boolean) {
        _state.update { list -> list.map { if (it.id == id) it.copy(removeWhenWatched = on) else it } }
        persist(_state.value)
    }

    fun addVideo(playlistId: String, videoId: String) {
        _state.update { list -> list.map { if (it.id == playlistId && videoId !in it.videoIds) it.copy(videoIds = it.videoIds + videoId) else it } }
        persist(_state.value)
    }

    fun removeVideo(playlistId: String, videoId: String) {
        _state.update { list -> list.map { if (it.id == playlistId) it.copy(videoIds = it.videoIds - videoId) else it } }
        persist(_state.value)
    }
}

/** The play queue. It is kept in the settings file so it survives a restart. */
class PlayQueue(private val settings: SettingsStore) {
    val ids: StateFlow<List<String>> get() = queueFlow
    private val queueFlow = MutableStateFlow(settings.value.queue)

    private fun set(next: List<String>) {
        queueFlow.value = next
        settings.update { it.copy(queue = next) }
    }

    fun add(vararg id: String) = set(queueFlow.value + id.filter { it !in queueFlow.value })
    fun playNext(id: String) = set(listOf(id) + queueFlow.value.filter { it != id })
    fun remove(id: String) = set(queueFlow.value - id)
    fun clear() = set(emptyList())

    /** Takes the first id off the queue. */
    fun pop(): String? {
        val first = queueFlow.value.firstOrNull() ?: return null
        set(queueFlow.value.drop(1))
        return first
    }
}
