package app.marginalia.player

import app.marginalia.MarginaliaApp
import app.marginalia.data.Highlight
import app.marginalia.data.Highlights
import app.marginalia.data.YouTube
import app.marginalia.data.TranscriptDoc

/**
 * Highlights the sentence being spoken right now, without opening the app.
 * Used by the Highlight button on the media notification and the lock screen.
 */
object QuickHighlight {
    /** Returns a short message for the user. */
    fun run(app: MarginaliaApp): String {
        val ps = app.player.state.value
        val id = ps.videoId ?: return "Nothing is playing."
        val video = app.library.get(id) ?: return "This video is not in your library."
        val transcript = app.library.loadTranscript(id)
        if (transcript == null) {
            // No captions, for example a song. Keep the moment, so a note can be added to it later.
            val t = ps.positionSec
            val mark = Highlight(java.util.UUID.randomUUID().toString(), t, t, "", "", app.settings.value.highlightColor)
            app.library.edit(id) { it.copy(highlights = it.highlights + mark) }
            return "Marked " + YouTube.formatTime(t) + "."
        }
        val s = app.settings.value
        val doc = TranscriptDoc.build(transcript.cues, video.chapters, s.lineLength, s.showTimestamps)
        val w = doc.wordAtTime(ps.positionSec)
        if (w < 0) return "No words here yet."

        fun endsSentence(i: Int): Boolean {
            val from = doc.words[i].charStart
            val to = if (i < doc.words.lastIndex) doc.words[i + 1].charStart else doc.text.length
            return doc.text.substring(from, to).trimEnd().trimEnd('"', '\'', ')', '”').let { it.endsWith('.') || it.endsWith('?') || it.endsWith('!') }
        }
        var a = w
        while (a > 0 && w - a < 40 && !endsSentence(a - 1)) a--
        var b = w
        while (b < doc.words.lastIndex && b - w < 40 && !endsSentence(b)) b++

        val next = Highlights.apply(video.highlights, doc.words, doc::plainText, a, b, s.highlightColor)
        app.library.edit(id) { it.copy(highlights = next) }
        return "Highlighted: " + doc.plainText(a, b).take(60)
    }
}
