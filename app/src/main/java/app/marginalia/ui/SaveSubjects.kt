package app.marginalia.ui

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import app.obsi.core.data.AppSettings
import app.obsi.core.data.ClipMemo
import app.obsi.core.data.ClipTemplate
import app.obsi.core.obsidian.BuiltNote
import app.obsi.core.obsidian.MergeResult
import app.obsi.core.obsidian.SaveSubject
import app.obsi.core.ui.SaveSheet as SharedSaveSheet
import app.marginalia.MarginaliaApp
import app.marginalia.data.SavedVideo
import app.obsi.core.data.Line
import app.marginalia.obsidian.NoteBuilder
import app.marginalia.obsidian.Updater

/** A video, for the shared save sheet. */
class VideoSubject(private val app: MarginaliaApp, private val article: SavedVideo, private val body: List<Line>) : SaveSubject {
    override val title get() = article.meta.title
    override val subtitle get() = article.meta.author
    override val sourceUrl get() = article.meta.url
    override val lastClip get() = article.lastClip

    override fun build(template: ClipTemplate, settings: AppSettings, overrides: Map<String, String>, name: String?, folder: String?, vault: String?): BuiltNote =
        NoteBuilder.build(template, article, body, settings, overrides, name, folder, vault)

    override fun variables(settings: AppSettings) = NoteBuilder.variables(article, body, settings)
    override fun readValues(text: String) = Updater.readValues(text)

    override fun merge(existing: String, note: BuiltNote, settings: AppSettings, replace: Set<String>): MergeResult =
        Updater.merge(existing, note, article, settings, replace).let { MergeResult(it.text, it.addedHighlights, it.addedProps) }

    override fun saveMemo(memo: ClipMemo) = app.library.edit(article.meta.id) { it.copy(lastClip = memo) }
    override fun savedTo(vault: String, path: String) = app.library.edit(article.meta.id) { it.copy(noteVault = vault, notePath = path) }

    @Composable
    override fun Thumb(modifier: Modifier) = coil3.compose.AsyncImage(article.meta.thumb, null, contentScale = ContentScale.Crop, modifier = modifier)
}

/** The shared save sheet, for one video. */
@Composable
fun SaveSheet(video: SavedVideo, lines: List<Line>, onDismiss: () -> Unit, onDone: (message: String, ok: Boolean) -> Unit) {
    val app = LocalApp.current
    val subject = remember(video, lines) { VideoSubject(app, video, lines) }
    SharedSaveSheet(subject, onDismiss, onDone)
}
