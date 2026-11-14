package app.marginalia

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestTest {
    private val names = listOf("Learning", "Productivity", "Health")

    @Test fun emptyBoxShowsEverything() = assertEquals(names, suggestFor(names, ""))

    @Test fun typoStillFindsTheName() = assertEquals(listOf("Productivity"), suggestFor(names, "productit"))

    @Test fun exactNameIsLeftOut() = assertEquals(emptyList<String>(), suggestFor(listOf("Productivity"), "productivity"))

    @Test fun bestMatchComesFirst() = assertEquals("Health", suggestFor(listOf("Learning", "Health"), "hea").first())
}
