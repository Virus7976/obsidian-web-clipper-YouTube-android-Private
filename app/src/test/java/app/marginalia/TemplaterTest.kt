package app.marginalia

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import app.obsi.core.data.TemplateProperty
import app.obsi.core.obsidian.Templater
import app.obsi.core.obsidian.VaultTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Templates written for the Templater plugin. The script is read, never run. */
class TemplaterTest {
    /** Jake's own Source template, exactly as it is in his vault. */
    private val source = """
<%*
const pickDate = (title) => new Promise(resolve => {
  const modal = new tp.obsidian.Modal(tp.app);
  let submitted = false;
  modal.titleEl.setText(title);

  const input = modal.contentEl.createEl("input", { type: "date" });
  input.style.width = "100%";

  const btn = modal.contentEl.createEl("button", { text: "Submit", cls: "mod-cta" });
  btn.style.marginTop = "12px";

  const submit = () => { submitted = true; modal.close(); };
  btn.addEventListener("click", submit);
  input.addEventListener("keydown", e => { if (e.key === "Enter") submit(); });

  modal.onClose = () => {
    const value = submitted ? input.value : "";
    modal.contentEl.empty();
    resolve(value);
  };

  modal.open();
  input.focus();
});

const rawName = ((await tp.system.prompt("Source title")) || "").trim();
const author = ((await tp.system.prompt("Author")) || "").trim();
const url = ((await tp.system.prompt("URL (optional)")) || "").trim();
const published = await pickDate("Date published");
const topicInput = await tp.system.prompt("Topics (comma separated)");
const topics = (topicInput || "")
  .split(",")
  .map(t => t.trim().toLowerCase())
  .filter(Boolean);
const bigIdea = ((await tp.system.prompt("Big idea (optional)")) || "").trim();

const fileName = rawName.replace(/[\\/:*?"<>|#^\[\]]/g, "").trim();
if (fileName) await tp.file.rename(fileName);
-%>
---
type: Source
author: <% JSON.stringify(author) %>
url: <% JSON.stringify(url) %>
published: <% published %>
dateCreated: <% tp.date.now("YYYY-MM-DD") %>
topics:<% topics.length ? "\n" + topics.map(t => `  - "${'$'}{t}"`).join("\n") : " []" %>
big_idea: <% JSON.stringify(bigIdea) %>
---
""".trimStart()

    private fun read(text: String = source) = VaultTemplate.read(text, "Templates/Source.md", emptyMap())
    private fun prop(name: String) = read().properties.first { it.name == name }

    @Test
    fun everyPropertyIsFound() {
        assertEquals(listOf("type", "author", "url", "published", "dateCreated", "topics", "big_idea"), read().properties.map { it.name })
        assertTrue(read().templater)
    }

    @Test
    fun questionsTheVideoCanAnswerFillThemselvesIn() {
        assertEquals("{{author}}", prop("author").value)
        assertEquals("{{url}}", prop("url").value)
        assertEquals("{{published}}", prop("published").value)
        assertFalse(prop("author").ask || prop("url").ask || prop("published").ask)
    }

    @Test
    fun theDatePickerMakesADateProperty() {
        assertEquals("date", prop("published").type)
    }

    @Test
    fun todaysDateKeepsItsFormat() {
        assertEquals("{{date|date:\"YYYY-MM-DD\"}}", prop("dateCreated").value)
        assertEquals("date", prop("dateCreated").type)
    }

    @Test
    fun plainTextStaysTheSameEveryTime() {
        val p = prop("type")
        assertEquals("Source", p.value)
        assertFalse(p.ask)
    }

    @Test
    fun questionsTheVideoCannotAnswerAreAskedAtSave() {
        assertTrue(prop("big_idea").ask)
        assertTrue(prop("topics").ask)
        assertEquals("multitext", prop("topics").type)
    }

    @Test
    fun theRenameBecomesTheNoteName() {
        // The file is renamed to the answer to "Source title", and the video knows its title.
        assertEquals("{{title}}", read().noteNameFormat)
    }

    @Test
    fun theScriptNeverReachesTheNote() {
        val t = read()
        assertEquals("{{content}}", t.noteContentFormat)
        assertTrue(t.properties.none { it.value.contains("<%") || it.value.contains("tp.") })
    }

    @Test
    fun questionWordingIsMatchedByItsLastWord() {
        assertEquals("{{title}}", Templater.fromLabel("Source title"))
        assertEquals("{{published}}", Templater.fromLabel("Date published"))
        assertEquals("{{url}}", Templater.fromLabel("URL (optional)"))
        assertEquals("{{author}}", Templater.fromLabel("Channel"))
        assertNull(Templater.fromLabel("Big idea (optional)"))
    }

    @Test
    fun writingBackNeverTouchesTheScript() {
        val t = read()
        // Nothing changed: the note is left alone.
        assertNull(VaultTemplate.writeBack(source, t, t))
        // A changed plain value is the only line that changes.
        val edited = t.copy(properties = t.properties.map { if (it.name == "type") it.copy(value = "Article") else it })
        val out = VaultTemplate.writeBack(source, edited, t)!!
        assertEquals(source.replace("type: Source", "type: \"Article\""), out)
    }

    @Test
    fun appChoicesForTagLinesAreNeverWrittenOverTheTags() {
        val t = read()
        val edited = t.copy(properties = t.properties.map { if (it.name == "author") it.copy(value = "Someone") else it })
        // "author" holds a Templater tag in the note, so it stays as written.
        assertNull(VaultTemplate.writeBack(source, edited, t))
    }

    @Test
    fun otherTemplaterCallsAreUnderstood() {
        val text = """
<%* const kind = await tp.system.suggester(["Book", "Talk"], ["book", "talk"]); await tp.file.move("Sources/" + tp.file.title) %>
---
title: <% tp.file.title %>
created: <% tp.file.creation_date("YYYY-MM-DD HH:mm") %>
kind: <% kind %>
---
<% tp.file.cursor() %>
""".trimStart()
        val t = read(text)
        assertEquals("{{title}}", t.properties.first { it.name == "title" }.value)
        assertEquals("{{date|date:\"YYYY-MM-DD HH:mm\"}}", t.properties.first { it.name == "created" }.value)
        assertTrue(t.properties.first { it.name == "kind" }.ask)
        assertEquals("Sources", t.path)
        assertEquals("{{content}}", t.noteContentFormat)
    }

    @Test
    fun plainTemplatesAreNotTemplater() {
        val t = read("---\ntype: Source\nauthor:\n---\n")
        assertFalse(t.templater)
        assertEquals(listOf(TemplateProperty::class.java), t.properties.map { it::class.java }.distinct())
    }
}
