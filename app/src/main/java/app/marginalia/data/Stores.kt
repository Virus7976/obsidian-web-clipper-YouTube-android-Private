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

private val storeJson = Json {
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
    private val _state = MutableStateFlow(readOrNull() ?: AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val value: AppSettings get() = _state.value

    val isFirstRun: Boolean = readOrNull() == null

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
