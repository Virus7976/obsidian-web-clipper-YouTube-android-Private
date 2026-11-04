package app.marginalia.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
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

/** Takes no room and draws nothing. Used to hide caption marks such as ">>" and "[music]". */
private class HiddenSpan : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = 0
    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {}
}

/** A run of text with a colour behind it. Search matches and highlights are both these. */
private class Mark(val start: Int, val end: Int, val color: Int)

/**
 * The text of the transcript. Marks are drawn here and not with text spans, for two reasons. A span fills the
 * full line height, so a highlight touches the line above it. And a span changes the text, which costs a
 * redraw of the whole block several times a second for the spoken word.
 */
private class PageText(context: Context) : TextView(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dp = resources.displayMetrics.density
    private var built: android.text.Layout? = null
    private var builtHeight = -1
    private var rects: List<Pair<android.graphics.RectF, Int>> = emptyList()
    private var ticks: List<Pair<android.graphics.RectF, Int>> = emptyList()

    /** The spoken word, moving from where it was to where it is. */
    private var spoken: android.graphics.RectF? = null
    private var spokenWas: android.graphics.RectF? = null
    private var spokenMove = 1f
    private var spokenColor = 0
    private var spokenChars: IntRange? = null
    private val mover = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 150
        interpolator = android.view.animation.DecelerateInterpolator()
        addUpdateListener { spokenMove = it.animatedValue as Float; invalidate() }
    }

    var highlights: List<Mark> = emptyList()
        set(v) { field = v; refresh() }
    var matches: List<Mark> = emptyList()
        set(v) { field = v; refresh() }

    /** Words with a note on them. A tick in the margin shows there is something to read. */
    var noted: List<Mark> = emptyList()
        set(v) { field = v; refresh() }

    /** Called when the selection moves. Nullable, because TextView can move it before this class is set up. */
    var onSelectionMoved: (() -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionMoved?.invoke()
    }

    fun refresh() {
        built = null
        invalidate()
    }

    fun setSpoken(chars: IntRange?, color: Int) {
        spokenColor = color
        spokenChars = chars
        val l = layout
        val next = if (chars == null || l == null) null else lineRects(l, chars.first, chars.last + 1).firstOrNull()
        val was = spoken
        spoken = next
        mover.cancel()
        // Along a line it glides. To a new line it jumps, because a slide would cross the text.
        if (next != null && was != null && was.top == next.top && was != next) {
            spokenWas = was
            spokenMove = 0f
            mover.start()
        } else {
            spokenWas = null
            spokenMove = 1f
            invalidate()
        }
    }

    /** The tight box around the letters on each line of a run, not the full line height. */
    private fun lineRects(l: android.text.Layout, start: Int, end: Int): List<android.graphics.RectF> {
        val out = ArrayList<android.graphics.RectF>()
        val size = textSize
        for (line in l.getLineForOffset(start)..l.getLineForOffset((end - 1).coerceAtLeast(start))) {
            val visibleEnd = l.getLineVisibleEnd(line)
            val a = maxOf(start, l.getLineStart(line))
            val b = minOf(end, visibleEnd)
            if (b <= a) continue
            val x0 = l.getPrimaryHorizontal(a)
            val x1 = if (b >= visibleEnd) l.getLineRight(line) else l.getPrimaryHorizontal(b)
            if (x1 <= x0) continue
            val base = l.getLineBaseline(line).toFloat()
            out += android.graphics.RectF(x0 - 2 * dp, base - size * 0.84f, x1 + 2 * dp, base + size * 0.3f)
        }
        return out
    }

    private fun rebuild(l: android.text.Layout) {
        built = l
        builtHeight = l.height
        rects = (highlights + matches).flatMap { m -> lineRects(l, m.start, m.end).map { it to m.color } }
        ticks = noted.mapNotNull { m ->
            val all = lineRects(l, m.start, m.end)
            if (all.isEmpty()) null else android.graphics.RectF(0f, all.first().top, 0f, all.last().bottom) to m.color
        }
        spokenChars?.let { c -> spoken = lineRects(l, c.first, c.last + 1).firstOrNull() }
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout
        if (l != null) {
            if (l !== built || l.height != builtHeight) rebuild(l)
            val left = totalPaddingLeft.toFloat()
            val top = totalPaddingTop.toFloat()
            val r = 3.5f * dp
            ticks.forEach { (t, color) ->
                ink.color = color
                val x = width - paddingRight + 7 * dp
                canvas.drawRoundRect(x, t.top + top, x + 3 * dp, t.bottom + top, 1.5f * dp, 1.5f * dp, ink)
            }
            canvas.save()
            canvas.translate(left, top)
            rects.forEach { (rect, color) ->
                ink.color = color
                canvas.drawRoundRect(rect, r, r, ink)
            }
            spoken?.let { to ->
                val from = spokenWas
                ink.color = spokenColor
                if (from == null || spokenMove >= 1f) canvas.drawRoundRect(to, r, r, ink)
                else canvas.drawRoundRect(
                    from.left + (to.left - from.left) * spokenMove, to.top,
                    from.right + (to.right - from.right) * spokenMove, to.bottom, r, r, ink,
                )
            }
            canvas.restore()
        }
        super.onDraw(canvas)
    }
}

/**
 * The whole transcript in one selectable TextView, so the phone's own selection handles, magnifier
 * and floating toolbar work, and a selection can start and end on any word.
 */
@SuppressLint("ClickableViewAccessibility")
class TranscriptView(context: Context) : ScrollView(context) {
    private val tv = PageText(context)
    private var doc: TranscriptDoc? = null
    private var style: ReaderStyle? = null
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

    /** The ends of highlight [id] were moved to words [first, last]. Called while the handles are dragged. */
    var onResize: (id: String, first: Int, last: Int) -> Unit = { _, _, _ -> }

    /** The highlight with handles on its ends, and the words it covers now. */
    private var resizing: Pair<String, IntRange>? = null

    /** True until the handles are on the highlight. Before that a selection change is the long press, not a drag. */
    private var resizeFresh = false
    private var resizeMode: ActionMode? = null

    /** True while this view sends its own long press. */
    private var pressing = false

    private val saveResize = Runnable {
        val (id, was) = resizing ?: return@Runnable
        val r = doc?.wordsInSelection(tv.selectionStart.coerceAtLeast(0), tv.selectionEnd.coerceAtLeast(0)) ?: return@Runnable
        if (r != was) {
            resizing = id to r
            onResize(id, r.first, r.last)
        }
    }

    /**
     * Put the selection handles on the ends of highlight [id], so the reader can drag them. Each drag saves by itself.
     * A real long press is the only way to get the handles, so one is sent to the first word.
     */
    fun startResize(id: String, words: IntRange) {
        val d = doc ?: return
        if (words.first !in d.words.indices) return
        if (resizing?.first == id && resizeMode != null) return
        resizeMode?.finish()
        resizing = id to words
        resizeFresh = true
        val wait = if (isVisible(words.first)) 0L else { scrollToWord(words.first); 450L }
        postDelayed({
            if (resizing?.first != id) return@postDelayed
            val layout = tv.layout ?: return@postDelayed
            val w = d.words[words.first]
            val line = layout.getLineForOffset(w.charStart)
            val x = (layout.getPrimaryHorizontal(w.charStart) + layout.getPrimaryHorizontal(w.charEnd.coerceAtMost(layout.getLineEnd(line)))) / 2f + tv.paddingLeft
            val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f + tv.paddingTop
            val t0 = SystemClock.uptimeMillis()
            pressing = true
            MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x, y, 0).also { it.source = android.view.InputDevice.SOURCE_TOUCHSCREEN; tv.dispatchTouchEvent(it); it.recycle() }
            postDelayed({
                val t1 = SystemClock.uptimeMillis()
                MotionEvent.obtain(t0, t1, MotionEvent.ACTION_UP, x, y, 0).also { it.source = android.view.InputDevice.SOURCE_TOUCHSCREEN; tv.dispatchTouchEvent(it); it.recycle() }
                pressing = false
            }, android.view.ViewConfiguration.getLongPressTimeout() + 150L)
        }, wait)
    }

    /** Take the handles off. Every drag is already saved. */
    fun stopResize() {
        val m = resizeMode
        if (m != null) { m.finish(); return }
        if (resizing == null) return
        resizing = null
        (tv.text as? Spannable)?.let { Selection.removeSelection(it) }
    }

    init {
        isFillViewport = true
        overScrollMode = OVER_SCROLL_NEVER
        // Text fades out at the edges of the page, so no line is ever cut in half against a bar.
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(26))
        tv.setTextIsSelectable(true)
        tv.customSelectionActionModeCallback = selectionCallback()
        tv.setPadding(dp(18), dp(10), dp(18), dp(160))
        tv.onSelectionMoved = {
            if (resizing != null && !resizeFresh) {
                removeCallbacks(saveResize)
                postDelayed(saveResize, 120)
            }
        }
        tv.setOnTouchListener { _, ev ->
            // Our own long press is not the reader's finger.
            if (pressing) return@setOnTouchListener false
            lastTouch = SystemClock.uptimeMillis()
            // A finger on the page beats a glide that is still running.
            if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) glide?.cancel()
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
            for (r in newDoc.hidden) {
                sp.setSpan(HiddenSpan(), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
            tv.matches = emptyList()
            matches = emptyList()
            current = -1
            tv.refresh()
            applySidePadding(width)
            post { scrollTo(0, keepY) }
            if (colors.isNotEmpty()) ownRuns?.let { applyHighlights(colors, it) } ?: applyHighlights(colors)
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrollDelta(t - oldt)
        // The reader dragged the text away from the spoken word: stop following and offer to sync.
        val now = SystemClock.uptimeMillis()
        if (now >= glidingUntil && now - lastTouch < 1500 && activeIdx >= 0) setPaused(true)
        if (now >= glidingUntil && now - lastTouch < 1500) onUserScroll(t - oldt, t == 0)
    }

    /** Called when a finger moved the text: by how much, and whether it is now at the very top. */
    var onUserScroll: (Int, Boolean) -> Unit = { _, _ -> }

    /** Our own scrolling (a glide back, or following along) is not the reader scrolling away. */
    private var glidingUntil = 0L

    /** Stops following, so the Sync button shows and the text stays where the reader put it. */
    fun pauseFollow() = setPaused(true)

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

    /** True when the word sits in the upper part of the view, clear of whatever is docked under the text. */
    fun isComfortable(word: Int): Boolean {
        val y = yOfWord(word) ?: return true
        return y >= scrollY && y <= scrollY + height * 0.55f
    }

    /** Scroll back to the spoken word with an eased glide, and start following again. */
    fun resync(word: Int) {
        setPaused(false)
        val y = yOfWord(word) ?: return
        glideTo((y - height * 0.3f).toInt().coerceAtLeast(0))
    }

    // ---------- find in this video ----------

    private var matches: List<IntRange> = emptyList()
    private var current = -1

    /** While true, follow-along does not scroll, so it does not fight the search. */
    var searching = false

    private fun drawMatches() {
        tv.matches = matches.mapIndexed { i, r -> Mark(r.first, r.last + 1, if (i == current) MATCH_NOW else MATCH) }
    }

    /** Marks every match of [query] in this transcript. Returns how many there are and jumps to the first. */
    fun search(query: String): Int {
        clearSearch()
        val text = tv.text?.toString() ?: return 0
        val q = query.trim()
        if (q.length < 2) return 0
        val found = ArrayList<IntRange>()
        var i = text.indexOf(q, 0, ignoreCase = true)
        while (i >= 0 && found.size < 500) {
            found += i until i + q.length
            i = text.indexOf(q, i + q.length, ignoreCase = true)
        }
        matches = found
        if (found.isNotEmpty()) goToMatch(0)
        return found.size
    }

    /** Jumps to match [i], wrapping around. Returns the match number now shown, or -1 when there are none. */
    fun goToMatch(i: Int): Int {
        if (matches.isEmpty()) return -1
        val idx = ((i % matches.size) + matches.size) % matches.size
        current = idx
        drawMatches()
        val r = matches[idx]
        tv.layout?.let { l ->
            val y = l.getLineTop(l.getLineForOffset(r.first)) + tv.paddingTop
            glideTo((y - height * 0.3f).toInt().coerceAtLeast(0))
        }
        return idx
    }

    fun clearSearch() {
        matches = emptyList()
        current = -1
        tv.matches = emptyList()
    }

    private var glide: android.animation.ObjectAnimator? = null

    private fun glideTo(target: Int) {
        glidingUntil = SystemClock.uptimeMillis() + 700
        // Only one glide at a time. Two running together pull the page in two directions.
        glide?.cancel()
        glide = android.animation.ObjectAnimator.ofInt(this, "scrollY", scrollY, target).apply {
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

    /**
     * [byWord] holds a colour index per word, or -1. [runs] is each highlight's words and colour, outer ones first.
     * Each is drawn on its own, so a highlight inside another one shows darker on top of it.
     */
    fun applyHighlights(byWord: IntArray, runs: List<Pair<IntRange, Int>>) {
        colors = byWord
        ownRuns = runs
        val d = doc ?: return
        val s = style ?: return
        val out = ArrayList<Mark>()
        for ((r, c) in runs) {
            var i = r.first.coerceAtLeast(0)
            val last = r.last.coerceAtMost(d.words.lastIndex)
            while (i <= last) {
                var j = i
                // A run stops at the end of a paragraph, so the time stamp on the next one stays clean.
                while (j < last && !breakBetween(d, j)) j++
                out += Mark(d.words[i].charStart, d.words[j].charEnd, tint(c, s))
                i = j + 1
            }
        }
        tv.highlights = out
    }

    private var ownRuns: List<Pair<IntRange, Int>>? = null

    /** [byWord] holds a colour index per word, or -1. */
    fun applyHighlights(byWord: IntArray) {
        ownRuns = null
        colors = byWord
        val d = doc ?: return
        val s = style ?: return
        val runs = ArrayList<Mark>()
        var i = 0
        while (i < byWord.size && i < d.words.size) {
            val c = byWord[i]
            if (c < 0) {
                i++
                continue
            }
            var j = i
            // A run stops at the end of a paragraph, so the time stamp on the next one stays clean.
            while (j + 1 < byWord.size && j + 1 < d.words.size && byWord[j + 1] == c && !breakBetween(d, j)) j++
            runs += Mark(d.words[i].charStart, d.words[j].charEnd, tint(c, s))
            i = j + 1
        }
        tv.highlights = runs
    }

    private fun breakBetween(d: TranscriptDoc, word: Int): Boolean {
        val nl = d.text.indexOf('\n', d.words[word].charEnd)
        return nl in 0 until d.words[word + 1].charStart
    }

    private fun tint(index: Int, s: ReaderStyle): Int {
        val ui = HighlightColors[index.coerceIn(HighlightColors.indices)].ui.toInt()
        return (ui and 0x00FFFFFF) or ((s.highlightAlpha * 255).toInt() shl 24)
    }

    /** Each entry is a run of words with a note, and the colour of that highlight. */
    fun applyNotes(runs: List<Pair<IntRange, Int>>) {
        val d = doc ?: return
        tv.noted = runs.mapNotNull { (w, c) ->
            val a = d.words.getOrNull(w.first) ?: return@mapNotNull null
            val b = d.words.getOrNull(w.last) ?: return@mapNotNull null
            Mark(a.charStart, b.charEnd, HighlightColors[c.coerceIn(HighlightColors.indices)].ui.toInt())
        }
    }

    fun setActive(word: Int) {
        val d = doc ?: return
        val s = style ?: return
        activeIdx = word
        val w = d.words.getOrNull(word)
        tv.setSpoken(w?.let { it.charStart until it.charEnd }, (s.accent and 0x00FFFFFF) or 0x66000000)
    }

    /** Scroll to keep [word] in view. Backs off while the reader is touching or selecting. */
    fun follow(word: Int) {
        val d = doc ?: return
        if (word !in d.words.indices) return
        if (searching) return
        if (tv.hasSelection()) { setPaused(true); return }
        if (paused) {
            // Follow again only if the reader scrolled so the spoken word sits where it would be put anyway.
            val wy = yOfWord(word)
            val comfy = wy != null && wy >= scrollY + height * 0.12f && wy <= scrollY + height * 0.62f
            if (SystemClock.uptimeMillis() - lastTouch > 1500 && comfy) setPaused(false)
            return
        }
        if (SystemClock.uptimeMillis() - lastTouch < 1500) return
        val layout = tv.layout ?: return
        val line = layout.getLineForOffset(d.words[word].charStart)
        val y = layout.getLineTop(line) + tv.paddingTop
        if (y < scrollY + height * 0.12f || y > scrollY + height * 0.62f) {
            glidingUntil = SystemClock.uptimeMillis() + 900
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
            // With handles on a highlight, a tap still opens another highlight or puts these away.
            if (tv.hasSelection() && resizing == null) return false
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
            resizing?.let { (_, r) ->
                val d = doc
                val t = tv.text as? Spannable
                // Only the first time: later the handles hold whatever the reader dragged them to.
                if (resizeFresh && d != null && t != null && r.first in d.words.indices) {
                    Selection.setSelection(t, d.words[r.first].charStart, d.words[r.last.coerceAtMost(d.words.lastIndex)].charEnd)
                }
                resizeFresh = false
                resizeMode = mode
                // The highlight's own colour shows while its ends move, not the selection colour on top of it.
                tv.highlightColor = Color.TRANSPARENT
                menu.clear()
                return true
            }
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
            // Only when there is a highlight here to take off.
            val here = doc?.wordsInSelection(tv.selectionStart.coerceAtLeast(0), tv.selectionEnd.coerceAtLeast(0))
            if (here != null && here.any { colors.getOrElse(it) { -1 } >= 0 }) menu.add(Menu.NONE, ID_ERASE, 4, "Remove highlight")
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            // While the ends of a highlight move there is nothing to pick. The note bar has the rest.
            if (resizing == null || menu.findItem(android.R.id.selectAll) == null) return false
            menu.removeItem(android.R.id.selectAll)
            return true
        }

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
            if (mode === resizeMode) {
                // Save the last drag now, then put the normal selection colour back.
                removeCallbacks(saveResize)
                saveResize.run()
                resizeMode = null
                resizing = null
                style?.let { tv.highlightColor = (it.accent and 0x00FFFFFF) or 0x55000000 }
            }
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
        const val MATCH = 0x66FFD54F
        const val MATCH_NOW = 0xCCFF9800.toInt()
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
