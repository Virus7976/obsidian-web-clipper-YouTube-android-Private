package app.marginalia.obsidian

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class SendResult(val ok: Boolean, val message: String)

/** The four ways the web clipper can finish a clip. */
object Sender {
    fun addToObsidian(context: Context, note: BuiltNote): SendResult {
        if (note.vault.isBlank()) return SendResult(false, "Pick a vault in Settings first.")
        val file = note.path
        val mode = when (note.behavior) {
            "overwrite" -> "&overwrite=true"
            "append-specific", "append-daily" -> "&append=true"
            "prepend-specific", "prepend-daily" -> "&prepend=true"
            else -> ""
        }
        val base = "obsidian://new?vault=${Uri.encode(note.vault)}&file=${Uri.encode(file)}$mode"
        val withContent = "$base&content=${Uri.encode(note.markdown)}"
        val uri: String
        if (withContent.length <= 40_000) {
            uri = withContent
        } else {
            // Long notes go through the clipboard, like the desktop clipper does.
            copy(context, note.markdown)
            uri = "$base&clipboard=true"
        }
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SendResult(true, "Sent to Obsidian.")
        } catch (e: ActivityNotFoundException) {
            copy(context, note.markdown)
            SendResult(false, "Obsidian is not installed. The note was copied instead.")
        }
    }

    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Marginalia note", text))
    }

    fun copyToClipboard(context: Context, note: BuiltNote): SendResult {
        copy(context, note.markdown)
        return SendResult(true, "Copied the note.")
    }

    fun share(context: Context, note: BuiltNote): SendResult {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.name)
            putExtra(Intent.EXTRA_TEXT, note.markdown)
        }
        context.startActivity(Intent.createChooser(send, "Share note").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return SendResult(true, "Shared.")
    }

    fun readText(context: Context, uri: String): String? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    fun writeText(context: Context, uri: String, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(Uri.parse(uri), "wt")!!.bufferedWriter().use { it.write(text) }
        true
    }.getOrDefault(false)

    /** Opens a note in Obsidian. */
    fun openNote(context: Context, vault: String, path: String): Boolean = open(
        context, "obsidian://open?vault=${Uri.encode(vault)}&file=${Uri.encode(path.removeSuffix(".md"))}",
    )

    /** Opens Obsidian's own search with a query filled in. */
    fun openSearch(context: Context, vault: String, query: String): Boolean = open(
        context, "obsidian://search?vault=${Uri.encode(vault)}&query=${Uri.encode(query)}",
    )

    private fun open(context: Context, uri: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    /** Writes the .md file straight into the vault folder the user picked. */
    fun saveFile(context: Context, note: BuiltNote, treeUri: String): SendResult {
        if (treeUri.isBlank()) return SendResult(false, "Pick your vault folder in Settings first.")
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: return SendResult(false, "Cannot open the vault folder. Pick it again.")
        if (!root.canWrite()) return SendResult(false, "No write access to the vault folder. Pick it again.")
        var dir: DocumentFile = root
        for (part in note.folder.split("/").filter { it.isNotBlank() }) {
            dir = dir.findFile(part)?.takeIf { it.isDirectory } ?: dir.createDirectory(part)
                ?: return SendResult(false, "Could not create the folder \"$part\".")
        }
        val existing = dir.findFile("${note.name}.md")
        val text: String
        val target: DocumentFile
        when {
            existing != null && note.behavior == "overwrite" -> { target = existing; text = note.markdown }
            existing != null && (note.behavior.startsWith("append") || note.behavior.startsWith("prepend")) -> {
                val old = context.contentResolver.openInputStream(existing.uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                target = existing
                text = if (note.behavior.startsWith("append")) old + "\n\n" + note.content else note.content + "\n\n" + old
            }
            existing != null -> {
                var n = 1
                while (dir.findFile("${note.name} $n.md") != null) n++
                target = dir.createFile("application/octet-stream", "${note.name} $n.md") ?: return SendResult(false, "Could not create the file.")
                text = note.markdown
            }
            else -> {
                target = dir.createFile("application/octet-stream", "${note.name}.md") ?: return SendResult(false, "Could not create the file.")
                text = note.markdown
            }
        }
        return runCatching {
            context.contentResolver.openOutputStream(target.uri, "wt")!!.bufferedWriter().use { it.write(text) }
            SendResult(true, "Saved ${target.name}.")
        }.getOrElse { SendResult(false, "Could not write the file.") }
    }
}
