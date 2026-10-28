package app.marginalia.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UpdateAppearance
import android.util.TypedValue
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import app.marginalia.data.HighlightColors
import app.marginalia.data.TranscriptDoc

data class ReaderStyle(
    val textColor: Int,
    val mutedColor: Int,
    val accent: Int,
    val sizeSp: Float,
    val lineMult: Float,
    val typeface: Typeface?,
    val highlightAlpha: Float,
    val holdSelects: String,
    val maxWidthPx: Int,
    val highlighterOn: Boolean,
)

/** Paints the word being spoken. Added last, so it sits on top of any highlight colour. */
private class ActiveWordSpan(private val bg: Int, private val fg: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) {
        tp.bgColor = bg
        tp.color = fg
    }
}

/**
 * The whole transcript in one selectable TextView, so the phone's own selection handles, magnifier
 * and floating toolbar work, and a selection can start and end on any word.
 */
@SuppressLint("ClickableViewAccessibility")
class TranscriptView(context: Context) : ScrollView(context) {
    private val tv = TextView(context)
    private var doc: TranscriptDoc? = null
    private var style: ReaderStyle? = null
    private var activeSpan: ActiveWordSpan? = null
    private val highlightSpans = ArrayList<Any>()
    private var lastTouch = 0L
    private var colors: IntArray = IntArray(0)

    /** The colour the Highlight button uses. Changed in the note bar after a highlight. */
    var currentColor: Int = 0

    /** Scroll movement in pixels, positive when the text moves up. */
    var onScrollDelta: (Int) -> Unit = {}

    /** Seek to this many seconds. */
    var onSeek: (Double) -> Unit = {}

    /** Paint words [first, last] in colour [color]. */
    var onPaint: (first: Int, last: Int, color: Int) -> Unit = { _, _, _ -> }

    /** Take highlights off words [first, last]. */
    var onErase: (first: Int, last: Int) -> Unit = { _, _ -> }

    init {
        isFillViewport = true
        overScrollMode = OVER_SCROLL_NEVER
        tv.setTextIsSelectable(true)
        tv.customSelectionActionModeCallback = selectionCallback()
        tv.setPadding(dp(18), dp(10), dp(18), dp(160))
        tv.setOnTouchListener { _, ev ->
            lastTouch = SystemClock.uptimeMillis()
            tapDetector.onTouchEvent(ev)
            false
        }
        addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- content ----------

    fun bind(newDoc: TranscriptDoc, s: ReaderStyle) {
        val docChanged = newDoc !== doc
        val styleChanged = s != style
        doc = newDoc
        style = s
        if (docChanged || styleChanged) {
            val keepY = scrollY
            val sp = SpannableStringBuilder(newDoc.text)
            for (r in newDoc.headings) {
                sp.setSpan(StyleSpan(Typeface.BOLD), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(RelativeSizeSpan(1.18f), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(ForegroundColorSpan(s.accent), r.first, r.first + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            for (r in newDoc.stamps) {
                sp.setSpan(RelativeSizeSpan(0.68f), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(ForegroundColorSpan(s.mutedColor), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            // Line height goes above each line, so the selection handles and magnifier sit on the text.
            tv.setText(sp, TextView.BufferType.SPANNABLE)
            tv.setTextColor(s.textColor)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, s.sizeSp)
            tv.setLineSpacing(0f, 1f)
            tv.typeface = s.typeface ?: Typeface.DEFAULT
            // One fixed line height, with the spare room above the text. Absolute, so it never adds up.
            val m = tv.paint.fontMetricsInt
            (tv.text as Spannable).setSpan(
                SpaceAbove(Math.round(s.lineMult * (m.descent - m.ascent))), 0, tv.text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
            tv.highlightColor = (s.accent and 0x00FFFFFF) or 0x55000000
            tv.setLinkTextColor(s.accent)
            highlightSpans.clear()
            searchSpans.clear()
            matches = emptyList()
            current = -1
            activeSpan = null
            applySidePadding(width)
            post { scrollTo(0, keepY) }
            if (colors.isNotEmpty()) applyHighlights(colors)
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrollDelta(t - oldt)
        // The reader dragged the text away from the spoken word: stop following and offer to sync.
        if (SystemClock.uptimeMillis() - lastTouch < 1500 && activeIdx >= 0) setPaused(!isVisible(activeIdx))
    }

    /** True while follow-along is paused because the reader scrolled away. */
    var paused = false
        private set
    private var activeIdx = -1

    /** Called when follow-along pauses (true) or resumes (false). */
    var onAway: (Boolean) -> Unit = {}

    /** Called when a word is tapped. Return true to say it was handled, so the view does not seek. */
    var onTapWord: (Int) -> Boolean = { false }

    private fun setPaused(v: Boolean) {
        if (paused != v) { paused = v; onAway(v) }
    }

    private fun yOfWord(word: Int): Int? {
        val d = doc ?: return null
        val layout = tv.layout ?: return null
        if (word !in d.words.indices) return null
        return layout.getLineTop(layout.getLineForOffset(d.words[word].charStart)) + tv.paddingTop
    }

    fun isVisible(word: Int): Boolean {
        val y = yOfWord(word) ?: return true
        return y >= scrollY - dp(20) && y <= scrollY + height - dp(60)
    }

    /** Scroll back to the spoken word with an eased glide, and start following again. */
    fun resync(word: Int) {
        setPaused(false)
        val y = yOfWord(word) ?: return
        glideTo((y - height * 0.3f).toInt().coerceAtLeast(0))
    }

    // ---------- find in this video ----------

    private val searchSpans = ArrayList<Any>()
    private var matches: List<IntRange> = emptyList()
    private var current = -1

    /** While true, follow-along does not scroll, so it does not fight the search. */
    var searching = false

    /** Marks every match of [query] in this transcript. Returns how many there are and jumps to the first. */
    fun search(query: String): Int {
        clearSearch()
        val sp = tv.text as? Spannable ?: return 0
        val q = query.trim()
        if (q.length < 2) return 0
        val text = sp.toString()
        val found = ArrayList<IntRange>()
        var i = text.indexOf(q, 0, ignoreCase = true)
        while (i >= 0 && found.size < 500) {
            found += i until i + q.length
            i = text.indexOf(q, i + q.length, ignoreCase = true)
        }
        matches = found
        found.forEach { r ->
            val span = android.text.style.BackgroundColorSpan(0x66FFD54F)
            sp.setSpan(span, r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            searchSpans += span
        }
        if (found.isNotEmpty()) goToMatch(0)
        return found.size
    }

    /** Jumps to match [i], wrapping around. Returns the match number now shown, or -1 when there are none. */
    fun goToMatch(i: Int): Int {
        val sp = tv.text as? Spannable ?: return -1
        if (matches.isEmpty()) return -1
        val idx = ((i % matches.size) + matches.size) % matches.size
        if (current in searchSpans.indices) {
            val r = matches[current]
            sp.removeSpan(searchSpans[current])
            val soft = android.text.style.BackgroundColorSpan(0x66FFD54F)
            sp.setSpan(soft, r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            searchSpans[current] = soft
        }
        val r = matches[idx]
        sp.removeSpan(searchSpans[idx])
        val strong = android.text.style.BackgroundColorSpan(0xCCFF9800.toInt())
        sp.setSpan(strong, r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        searchSpans[idx] = strong
        current = idx
        tv.layout?.let { l ->
            val y = l.getLineTop(l.getLineForOffset(r.first)) + tv.paddingTop
            glideTo((y - height * 0.3f).toInt().coerceAtLeast(0))
        }
        return idx
    }

    fun clearSearch() {
        val sp = tv.text as? Spannable
        searchSpans.forEach { sp?.removeSpan(it) }
        searchSpans.clear()
        matches = emptyList()
        current = -1
    }

    private fun glideTo(target: Int) {
        android.animation.ObjectAnimator.ofInt(this, "scrollY", scrollY, target).apply {
            duration = 480
            interpolator = android.view.animation.DecelerateInterpolator(1.7f)
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applySidePadding(w)
    }

    private fun applySidePadding(w: Int) {
        val max = style?.maxWidthPx ?: return
        val base = dp(18)
        val side = if (w > max + base * 2) (w - max) / 2 else base
        if (tv.paddingLeft != side) tv.setPadding(side, tv.paddingTop, side, tv.paddingBottom)
    }

    // ---------- highlights and the active word ----------

    /** [byWord] holds a colour index per word, or -1. */
    fun applyHighlights(byWord: IntArray) {
        colors = byWord
        val d = doc ?: return
        val s = style ?: return
        val sp = tv.text as? Spannable ?: return
        highlightSpans.forEach { sp.removeSpan(it) }
        highlightSpans.clear()
        var i = 0
        while (i < byWord.size && i < d.words.size) {
            val c = byWord[i]
            if (c < 0) {
                i++
                continue
            }
            var j = i
            while (j + 1 < byWord.size && j + 1 < d.words.size && byWord[j + 1] == c) j++
            val ui = HighlightColors[c.coerceIn(HighlightColors.indices)].ui.toInt()
            val bg = (ui and 0x00FFFFFF) or ((s.highlightAlpha * 255).toInt() shl 24)
            val span = BackgroundColorSpan(bg)
            sp.setSpan(span, d.words[i].charStart, d.words[j].charEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            highlightSpans += span
            i = j + 1
        }
        // keep the spoken word on top
        activeSpan?.let { a ->
            val st = sp.getSpanStart(a)
            val en = sp.getSpanEnd(a)
            if (st >= 0) {
                sp.removeSpan(a)
                sp.setSpan(a, st, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    fun setActive(word: Int) {
        val d = doc ?: return
        val s = style ?: return
        activeIdx = word
        val sp = tv.text as? Spannable ?: return
        activeSpan?.let { sp.removeSpan(it) }
        activeSpan = null
        if (word in d.words.indices) {
            val bg = (s.accent and 0x00FFFFFF) or 0x66000000
            val a = ActiveWordSpan(bg, s.textColor)
            sp.setSpan(a, d.words[word].charStart, d.words[word].charEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            activeSpan = a
        }
    }

    /** Scroll to keep [word] in view. Backs off while the reader is touching or selecting. */
    fun follow(word: Int) {
        val d = doc ?: return
        if (word !in d.words.indices) return
        if (searching) return
        if (paused) {
            // the reader may have scrolled back to the spoken word on their own
            if (SystemClock.uptimeMillis() - lastTouch > 1500 && isVisible(word)) setPaused(false)
            return
        }
        if (SystemClock.uptimeMillis() - lastTouch < 1500 || tv.hasSelection()) return
        val layout = tv.layout ?: return
        val line = layout.getLineForOffset(d.words[word].charStart)
        val y = layout.getLineTop(line) + tv.paddingTop
        if (y < scrollY + height * 0.12f || y > scrollY + height * 0.62f) {
            smoothScrollTo(0, (y - height * 0.3f).toInt().coerceAtLeast(0))
        }
    }

    fun scrollToWord(word: Int) {
        val d = doc ?: return
        if (word !in d.words.indices) return
        val layout = tv.layout ?: return
        val y = layout.getLineTop(layout.getLineForOffset(d.words[word].charStart)) + tv.paddingTop
        setPaused(false)
        glideTo((y - height * 0.25f).toInt().coerceAtLeast(0))
    }

    // ---------- tap to seek ----------

    private val tapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (tv.hasSelection()) return false
            val d = doc ?: return false
            val off = tv.getOffsetForPosition(e.x, e.y)
            val w = d.wordAtChar(off)
            if (w >= 0 && !onTapWord(w)) onSeek(d.words[w].start)
            return false
        }
    })

    // ---------- selection ----------

    private fun dot(color: Long) = ShapeDrawable(OvalShape()).apply {
        intrinsicWidth = dp(26)
        intrinsicHeight = dp(26)
        paint.color = color.toInt()
    }

    private fun selectionCallback() = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            if (style?.highlighterOn == false) return false
            expand(style?.holdSelects ?: "word")
            menu.clear()
            // One button to highlight. The colour is picked afterwards, in the note bar.
            menu.add(Menu.NONE, ID_COLOR + currentColor, 0, "Highlight")
                .setIcon(dot(HighlightColors.getOrNull(currentColor)?.ui ?: HighlightColors.first().ui))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
            menu.add(Menu.NONE, ID_SENTENCE, 1, "Sentence").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(Menu.NONE, ID_PARAGRAPH, 2, "Paragraph").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(Menu.NONE, ID_COPY, 3, "Copy")
            menu.add(Menu.NONE, ID_ERASE, 4, "Remove highlight")
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val d = doc ?: return false
            val range = d.wordsInSelection(tv.selectionStart.coerceAtLeast(0), tv.selectionEnd.coerceAtLeast(0))
            when (item.itemId) {
                ID_SENTENCE -> { expand("sentence"); mode.invalidateContentRect(); return true }
                ID_PARAGRAPH -> { expand("paragraph"); mode.invalidateContentRect(); return true }
                ID_COPY -> {
                    if (range != null) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Transcript", d.plainText(range.first, range.last)))
                    }
                }
                ID_ERASE -> range?.let { onErase(it.first, it.last) }
                else -> if (item.itemId >= ID_COLOR && item.itemId < ID_COLOR + HighlightColors.size && range != null) {
                    tv.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    onPaint(range.first, range.last, item.itemId - ID_COLOR)
                }
            }
            mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            val t = tv.text
            if (t is Spannable) Selection.removeSelection(t)
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
            super.onGetContentRect(mode, view, outRect)
        }
    }

    /** Grow the selection to a whole sentence or paragraph. */
    private fun expand(kind: String) {
        val t = tv.text as? Spannable ?: return
        var a = tv.selectionStart
        var b = tv.selectionEnd
        if (a < 0 || b < 0) return
        if (a > b) { val x = a; a = b; b = x }
        when (kind) {
            "paragraph" -> {
                a = t.lastIndexOf('\n', (a - 1).coerceAtLeast(0)).let { if (it < 0 || a == 0) 0 else it + 1 }
                b = t.indexOf('\n', b).let { if (it < 0) t.length else it }
            }
            "sentence" -> {
                var s = a
                while (s > 0) {
                    val c = t[s - 1]
                    if (c == '\n') break
                    if ((c == '.' || c == '?' || c == '!') && s < t.length && t[s].isWhitespace()) break
                    s--
                }
                while (s < t.length && t[s].isWhitespace()) s++
                var e = maxOf(b, a)
                while (e < t.length) {
                    val c = t[e]
                    if (c == '\n') break
                    e++
                    if (c == '.' || c == '?' || c == '!') break
                }
                a = s
                b = e
            }
            else -> return
        }
        if (b > a) Selection.setSelection(t, a, b)
    }

    private companion object {
        const val ID_COLOR = 100
        const val ID_ERASE = 200
        const val ID_COPY = 201
        const val ID_SENTENCE = 202
        const val ID_PARAGRAPH = 203
    }
}

/** Makes every line [total] pixels tall, with the spare room above the text instead of below it. */
private class SpaceAbove(private val total: Int) : android.text.style.LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: android.graphics.Paint.FontMetricsInt) {
        val want = fm.descent - total
        if (want < fm.ascent) {
            fm.top -= fm.ascent - want
            fm.ascent = want
        }
    }
}
