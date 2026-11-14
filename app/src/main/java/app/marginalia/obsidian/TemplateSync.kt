package app.marginalia.obsidian

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import android.net.Uri
import android.widget.Toast
import app.marginalia.MarginaliaApp
import app.obsi.core.data.ClipTemplate
import app.obsi.core.data.NoteRef
import app.obsi.core.data.VaultScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps a template made from a vault note and that note the same, in both directions. */
object TemplateSync {
    private class Link(val vault: String, val tree: Uri, val note: NoteRef)

    private fun link(app: MarginaliaApp, t: ClipTemplate): Link? {
        if (t.source.isBlank()) return null
        val s = app.settings.value
        val vault = s.linkedVault(t.vault.ifBlank { s.vault }) ?: s.vaultFolders.keys.firstOrNull() ?: return null
        val notes = app.vaults.index(vault)?.notes.orEmpty()
        val file = t.source.substringAfterLast('/')
        val note = notes.firstOrNull { it.path == t.source }
            ?: notes.firstOrNull { it.path.substringAfterLast('/') == file } ?: return null
        return Link(vault, Uri.parse(s.vaultFolders[vault] ?: return null), note)
    }

    private fun types(app: MarginaliaApp, l: Link) =
        VaultScanner.config(app, l.tree, "types.json")?.let(VaultTemplate::types).orEmpty()

    /** Brings in what changed in Obsidian. Your choices for each property stay. */
    suspend fun pull(app: MarginaliaApp, id: String) = withContext(Dispatchers.IO) {
        val t = app.settings.value.templates.firstOrNull { it.id == id } ?: return@withContext
        val l = link(app, t) ?: return@withContext
        val text = Sender.readText(app, l.note.uri) ?: return@withContext
        val fresh = VaultTemplate.read(text, t.source, types(app, l), t)
        if (fresh != t) app.settings.update { s -> s.copy(templates = s.templates.map { if (it.id == id) fresh else it }) }
    }

    /** Writes the template's changes into the vault, and says so. */
    suspend fun push(app: MarginaliaApp, id: String) = withContext(Dispatchers.IO) {
        val t = app.settings.value.templates.firstOrNull { it.id == id } ?: return@withContext
        val l = link(app, t) ?: return@withContext
        val text = Sender.readText(app, l.note.uri) ?: return@withContext
        val updated = VaultTemplate.writeBack(text, t, VaultTemplate.read(text, t.source, types(app, l)))
        val wrote = updated != null && Sender.writeText(app, l.note.uri, updated)
        val typesJson = VaultScanner.config(app, l.tree, "types.json")
        val typed = typesJson?.let { VaultTemplate.writeTypes(it, t) }?.let { VaultScanner.writeConfig(app, l.tree, "types.json", it) } == true
        if (wrote) app.vaults.refresh(l.vault, force = true)
        if (wrote || typed) withContext(Dispatchers.Main) {
            Toast.makeText(app, "Updated ${l.note.name} in your vault.", Toast.LENGTH_SHORT).show()
        }
    }
}
