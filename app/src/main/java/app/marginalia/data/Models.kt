package app.marginalia.data

import kotlinx.serialization.Serializable

@Serializable
data class VideoMeta(
    val id: String,
    val title: String,
    val author: String,
    val description: String = "",
    val published: String = "",
    val durationSec: Int = 0,
) {
    val url: String get() = "https://www.youtube.com/watch?v=$id"
    val thumb: String get() = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
}

/**
 * One raw caption cue straight from YouTube.
 * [offs] holds the start of each spoken word, in seconds from [start]. Auto captions have it;
 * hand-made captions do not, and then the words are spread over the cue by their length.
 */
@Serializable
data class Cue(val start: Double, val dur: Double, val text: String, val offs: List<Double>? = null)

/** A readable line: several cues glued together so a sentence reads as one block. */
data class Line(val start: Double, val end: Double, val text: String)

@Serializable
data class Chapter(val start: Double, val title: String)

@Serializable
data class TranscriptData(
    val lang: String = "",
    val auto: Boolean = false,
    val cues: List<Cue> = emptyList(),
)

/**
 * A highlight is stored by time range, not by line number, so changing the
 * line length setting never breaks existing highlights.
 */
@Serializable
data class Highlight(
    val id: String,
    val startSec: Double,
    val endSec: Double,
    val text: String,
    val note: String = "",
    /** Index into [HighlightColors]. */
    val color: Int = 0,
    /** First and last word, by position in the transcript. -1 on highlights saved by version 1.0, which only know the time. */
    val w0: Int = -1,
    val w1: Int = -1,
)

/** The five marker colours. Yellow is Obsidian's own ==highlight==. The rest use Highlightr's <mark> style. */
data class HighlightColor(val name: String, val ui: Long, val obsidian: String?)

val HighlightColors = listOf(
    HighlightColor("Yellow", 0xFFF2C14E, null),
    HighlightColor("Red", 0xFFFF6B6B, "#FF5582A6"),
    HighlightColor("Green", 0xFF5BD98C, "#BBFABBA6"),
    HighlightColor("Blue", 0xFF6AA8FF, "#ADCCFFA6"),
    HighlightColor("Purple", 0xFFB58CFF, "#D2B3FFA6"),
)

@Serializable
data class SavedVideo(
    val meta: VideoMeta,
    val chapters: List<Chapter> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val positionSec: Double = 0.0,
    val pinned: Boolean = false,
    val updatedAt: Long = 0L,
    val clippedAt: Long = 0L,
)

@Serializable
data class TemplateProperty(
    val id: String,
    val name: String,
    val value: String = "",
    val type: String = "",
)

@Serializable
data class ClipTemplate(
    val id: String,
    val name: String,
    val behavior: String = "create",
    val noteNameFormat: String = "{{title}}",
    val path: String = "Clippings",
    val noteContentFormat: String = "{{content}}",
    val vault: String = "",
    val properties: List<TemplateProperty> = emptyList(),
)

/** A palette made in the palette maker. */
@Serializable
data class CustomPalette(val id: String, val name: String, val seed: Long, val style: String = "tonal")

@Serializable
data class AppSettings(
    // look
    val themePreset: String = "ink",
    val customPalettes: List<CustomPalette> = emptyList(),
    /** Home tabs that are shown: add, library, vault. */
    val homeTabs: List<String> = listOf("add", "library", "vault"),
    val libraryLayout: String = "list", // list | grid | compact
    val pixelChar: String = "ghost", // ghost | heart | star | invader | off
    val customFontPath: String = "",
    val customFontName: String = "",
    val uiFontCustom: Boolean = false,
    val appearance: String = "auto", // auto | light | dusk | dark | oled
    val accentOverride: Long = 0L, // 0 = use the preset accent
    val motion: String = "lively", // calm | lively | playful
    val roundness: Float = 1f,
    // reading
    val readerFont: String = "serif", // serif | sans | system
    val readerFontSize: Float = 18f,
    val lineHeight: Float = 1.6f,
    val maxWidth: Float = 38f,
    val lineLength: Int = 1, // 0 short, 1 medium, 2 long
    val pinPlayer: Boolean = true,
    val highlightActiveLine: Boolean = true,
    val autoScroll: Boolean = false,
    val showTimestamps: Boolean = true,
    // highlighting
    val highlighterEnabled: Boolean = true,
    val alwaysShowHighlights: Boolean = true,
    val highlightBehavior: String = "replace-content", // replace-content | keep-content
    val highlightColor: Int = 0, // the colour new highlights start with
    val holdSelects: String = "word", // word | sentence | paragraph
    val highlightSyntax: String = "obsidian", // obsidian | plain
    val highlightFormat: String = "> {text}\n> [{time}]({link})",
    val autoPlay: Boolean = true,
    // obsidian
    val saveBehavior: String = "addToObsidian", // addToObsidian | saveFile | copyToClipboard | share
    val vaults: List<String> = emptyList(),
    val vault: String = "",
    val vaultFolderUri: String = "",
    /** Vault name -> folder the user picked, so the app can read tags, folders and notes. */
    val vaultFolders: Map<String, String> = emptyMap(),
    val recentTags: List<String> = emptyList(),
    val templates: List<ClipTemplate> = emptyList(),
    val selectedTemplateId: String = "",
    val propertyTypes: Map<String, String> = emptyMap(),
    // playback + transcript
    val backgroundPlay: Boolean = true,
    val keepScreenOn: Boolean = true,
    val captionLang: String = "",
    // advanced: client versions can be changed here if YouTube ever rejects the built-in ones
    val androidClientVersion: String = "20.10.38",
    val iosClientVersion: String = "20.10.4",
    val vrClientVersion: String = "1.62.27",
    // updates
    val autoCheckUpdates: Boolean = true,
    val updateRepo: String = "Virus7976/obsidian-web-clipper-YouTube-android-Private",
    val updateToken: String = "",
    val updateMirror: String = "",
    val ignoredVersion: String = "",
    val installMethod: String = "system", // system | shizuku | adb
    val lastUpdateCheck: Long = 0L,
)
