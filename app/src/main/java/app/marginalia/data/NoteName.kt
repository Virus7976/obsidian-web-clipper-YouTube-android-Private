package app.marginalia.data

import java.time.LocalDate

/** The file name style date_subject_description_v#, for people who file notes that way. */
object NoteName {
    /** Lower case, words joined by hyphens, nothing a file name or a link cannot hold. */
    fun slug(text: String, max: Int = 60): String =
        text.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ').filter { it.isNotEmpty() }
            .joinToString("-")
            .take(max).trim('-')

    fun prefix(date: LocalDate, subject: String, description: String): String =
        "${date}_${slug(subject).ifBlank { "clipping" }}_${slug(description).ifBlank { "untitled" }}_v"

    fun build(date: LocalDate, subject: String, description: String, version: Int): String =
        prefix(date, subject, description) + version.coerceAtLeast(1)

    /** One more than the highest version already filed under this prefix. 1 when there is none. */
    fun nextVersion(existingNames: List<String>, prefix: String): Int =
        (existingNames.filter { it.startsWith(prefix) }.mapNotNull { it.removePrefix(prefix).toIntOrNull() }.maxOrNull() ?: 0) + 1
}
