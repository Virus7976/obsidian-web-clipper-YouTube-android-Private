package app.marginalia.obsidian

import app.marginalia.MarginaliaApp
import app.marginalia.data.ClipMemo
import app.marginalia.data.NoteName
import app.marginalia.data.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Saves a video's highlights to Obsidian by itself when it ends (Settings > Obsidian).
 * It uses the same template, folder and tags as your last clip of that video.
 * If the video already has a note, it adds only what is missing. Nothing opens, nothing is asked.
 */
object AutoClip {
    /** A short sentence to show, or null when there was nothing to do. */
    suspend fun run(app: MarginaliaApp, id: String): String? = withContext(Dispatchers.IO) {
        val ctx = app.applicationContext
        val s = app.settings.value
        val video = app.library.get(id) ?: return@withContext null
        // Nothing highlighted, nothing to lose.
        if (video.highlights.isEmpty()) return@withContext null
        val template = s.templates.firstOrNull { it.id == video.lastClip?.templateId }
            ?: s.templates.firstOrNull { it.id == s.selectedTemplateId }
            ?: s.templates.firstOrNull()
            ?: return@withContext "Auto-save skipped: add a template in Settings."
        val memo = video.lastClip?.takeIf { it.templateId == template.id }
        val lines = app.library.loadTranscript(id)?.let { YouTube.toLines(it.cues, s.lineLength, video.chapters) }.orEmpty()
        val base = NoteBuilder.build(template, video, lines, s)

        val vault = memo?.vault?.takeIf { it in s.vaults } ?: base.vault
        val key = s.linkedVault(vault) ?: return@withContext "Auto-save skipped: pick your vault folder in Settings."
        val folderUri = s.vaultFolders[key] ?: s.vaultFolderUri
        if (folderUri.isBlank()) return@withContext "Auto-save skipped: pick your vault folder in Settings."

        // Know what is already in the vault, so a second run adds to the note and never makes a copy.
        app.vaults.refresh(key)
        var waited = 0
        while (app.vaults.indexes.value[key] == null && waited < 40) { delay(500); waited++ }
        while (app.vaults.busy.value.containsKey(key) && waited < 60) { delay(500); waited++ }
        val index = app.vaults.indexes.value[key]
        val existing = index?.noteForSource(video.meta.url)
        // Without the index there is no way to tell, and a copy is worse than a late save.
        if (index == null) return@withContext "Auto-save skipped: could not read your vault."

        val tagsProp = base.properties.firstOrNull { it.type == "multitext" && it.property.name.equals("tags", true) }
        val tags = memo?.tags?.takeIf { it.isNotEmpty() }
            ?: tagsProp?.let { NoteBuilder.splitList(it.value).map { t -> t.removePrefix("#").trim('"') } }.orEmpty()
        val overrides = (memo?.values.orEmpty()) + (tagsProp?.let { mapOf(it.property.id to tags.joinToString(",")) } ?: emptyMap())
        val folder = memo?.folder?.takeIf { it.isNotBlank() } ?: base.folder
        val name = if (s.nameSchema) {
            val today = java.time.LocalDate.now()
            val subject = s.nameSubject.ifBlank { "clipping" }
            val prefix = NoteName.prefix(today, subject, video.meta.title)
            val dir = folder.trim('/')
            val v = NoteName.nextVersion(index.notes.filter { it.path.substringBeforeLast('/', "") == dir }.map { it.name }, prefix)
            NoteName.build(today, subject, video.meta.title, v)
        } else memo?.name?.ifBlank { null } ?: base.name
        val note = NoteBuilder.build(template, video, lines, s, overrides, name, folder, vault)

        val notePath: String
        val message: String
        if (existing != null) {
            val old = Sender.readText(ctx, existing.uri) ?: return@withContext "Auto-save skipped: could not read ${existing.name}."
            val merged = Updater.merge(old, note, video, s, emptySet())
            if (!merged.changed) return@withContext null
            if (!Sender.writeText(ctx, existing.uri, merged.text)) return@withContext "Auto-save failed: could not write ${existing.name}."
            notePath = existing.path
            message = "Saved to ${existing.name}."
        } else {
            val r = Sender.saveFile(ctx, note, folderUri)
            if (!r.ok) return@withContext "Auto-save failed. " + r.message
            notePath = note.path
            message = "Saved ${note.name} to Obsidian."
        }
        app.vaults.refresh(key, force = true)
        app.library.edit(id) {
            it.copy(noteVault = vault, notePath = notePath, clippedAt = System.currentTimeMillis(), lastClip = ClipMemo(template.id, name, folder, vault, overrides, tags))
        }
        message
    }
}
