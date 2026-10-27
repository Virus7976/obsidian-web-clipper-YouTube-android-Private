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

/** One raw caption cue straight from YouTube. */
@Serializable
data class Cue(val start: Double, val dur: Double, val text: String)

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

@Serializable
data class AppSettings(
    // look
    val themePreset: String = "ink",
    val appearance: String = "auto", // auto | light | dark
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
    val highlightColor: Long = 0xFFF2C14EL,
    val highlightFormat: String = "> {text}\n> [{time}]({link})",
    // obsidian
    val saveBehavior: String = "addToObsidian", // addToObsidian | saveFile | copyToClipboard | share
    val vaults: List<String> = emptyList(),
    val vault: String = "",
    val vaultFolderUri: String = "",
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
)
