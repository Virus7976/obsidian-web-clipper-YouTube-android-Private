package app.marginalia

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import app.obsi.core.data.StoreFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** A phone that dies mid-save must never cost the library. */
class StoreFilesTest {
    private val dir = Files.createTempDirectory("store").toFile()
    private val file = File(dir, "library.json")
    private val backup = File(dir, "library.json.bak")
    private fun read() = StoreFiles.read(file, backup) { it }

    @Test
    fun aSaveIsReadBack() {
        StoreFiles.write(file, backup, "one")
        assertEquals("one", read())
    }

    @Test
    fun anEmptiedFileFallsBackToTheSaveBefore() {
        StoreFiles.write(file, backup, "one")
        StoreFiles.write(file, backup, "two")
        file.writeText("")
        assertEquals("one", read())
    }

    @Test
    fun aBrokenFileFallsBackToTheSaveBefore() {
        StoreFiles.write(file, backup, "one")
        StoreFiles.write(file, backup, "two")
        file.writeText("garbage")
        assertEquals("one", StoreFiles.read(file, backup) { require(it != "garbage"); it })
    }

    @Test
    fun anEmptyFileNeverReplacesTheGoodCopy() {
        StoreFiles.write(file, backup, "one")
        StoreFiles.write(file, backup, "two")
        file.writeText("")
        // The next save must keep the good copy, not copy the empty file over it.
        StoreFiles.write(file, backup, "three")
        assertEquals("one", backup.readText())
        assertEquals("three", read())
    }

    @Test
    fun nothingSavedIsNothing() {
        assertNull(read())
    }
}
