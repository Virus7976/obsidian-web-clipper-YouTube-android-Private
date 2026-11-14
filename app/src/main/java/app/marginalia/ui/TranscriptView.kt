package app.marginalia.ui

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

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
import app.obsi.core.data.HighlightColors
import app.obsi.core.data.HighlightRun
import app.obsi.core.data.Highlights
import app.obsi.core.data.TranscriptDoc

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

    /** The two ends of the highlight being changed, as character offsets. -1 when no highlight is open. */
    private var pinFrom = -1
    private var pinTo = -1
    private var pinColor = 0

    fun setPins(from: Int, to: Int, color: Int) {
        if (from == pinFrom && to == pinTo && color == pinColor) return
        pinFrom = from
        pinTo = to
        pinColor = color
        invalidate()
    }

    /**
     * Where the pull handles sit, in this view's own coordinates: for each end the x of its line, the top and
     * bottom of the text box on that line, and the centre of its knob. The start knob is above the text and
     * the end knob below, so neither covers a word and they never sit on the same spot.
     */
    class Pin(val x: Float, val top: Float, val bottom: Float, val knobY: Float)

    fun pins(): Pair<Pin, Pin>? {
        val l = layout ?: return null
        if (pinFrom < 0 || pinTo <= pinFrom) return null
        val size = textSize
        val left = totalPaddingLeft
        val top = totalPaddingTop
        val r = KNOB * dp
        val l0 = l.getLineForOffset(pinFrom)
        val b0 = l.getLineBaseline(l0).toFloat()
        val x0 = l.getPrimaryHorizontal(pinFrom) - 2 * dp + left
        val t0 = b0 - size * 0.84f + top
        val start = Pin(x0, t0, b0 + size * 0.3f + top, t0 - r)
        val l1 = l.getLineForOffset(pinTo - 1)
        val b1 = l.getLineBaseline(l1).toFloat()
        val x1 = (if (pinTo >= l.getLineVisibleEnd(l1)) l.getLineRight(l1) else l.getPrimaryHorizontal(pinTo)) + 2 * dp + left
        val e1 = b1 + size * 0.3f + top
        val end = Pin(x1, b1 - size * 0.84f + top, e1, e1 + r)
        return start to end
    }

    private fun drawPin(canvas: Canvas, p: Pin, knobAbove: Boolean) {
        val r = KNOB * dp
        // A dark ring keeps a light colour readable on the page, and the stem joins the knob to the text.
        ink.color = 0xCC000000.toInt()
        canvas.drawRect(p.x - 2f * dp, p.top, p.x + 2f * dp, p.bottom, ink)
        canvas.drawCircle(p.x, p.knobY, r + 1.5f * dp, ink)
        ink.color = pinColor
        canvas.drawRect(p.x - 1.25f * dp, if (knobAbove) p.knobY else p.top, p.x + 1.25f * dp, if (knobAbove) p.bottom else p.knobY, ink)
        canvas.drawCircle(p.x, p.knobY, r, ink)
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
        // On top of the text, so the stem is never hidden by a letter.
        pins()?.let { (a, b) ->
            drawPin(canvas, a, knobAbove = true)
            drawPin(canvas, b, knobAbove = false)
        }
    }

    companion object {
        /** Radius of a pull handle's knob, in dp. */
        const val KNOB = 9f
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

    /** The ends of highlight [id] were moved to words [first, last]. Called when a pull handle is let go. */
    var onResize: (id: String, first: Int, last: Int) -> Unit = { _, _, _ -> }

    // ---------- pull handles on an open highlight ----------
    // These are drawn by this view, not the phone's text selection. A selection can collapse to one point
    // when a tap lands, and saving that would shrink the highlight to nothing. A handle can only move its
    // own end, word by word, and the two ends can never pass each other.

    /** The open highlight, and the words it covers. Null when none is open. */
    private var pinned: String? = null
    private var pinWords: IntRange? = null

    /** 0 when no handle is held, -1 for the start one, 1 for the end one. */
    private var dragging = 0
    private var dragFrom: IntRange? = null
    private var grabDx = 0f
    private var grabDy = 0f
    private var fingerX = 0f
    private var fingerY = 0f
    private var magnifier: android.widget.Magnifier? = null

    /** Put pull handles on the ends of highlight [id]. */
    fun startResize(id: String, words: IntRange) {
        if (dragging != 0) endDrag()
        pinned = id
        pinWords = words
        if (!isVisible(words.first)) scrollToWord(words.first)
        drawRuns()
    }

    /** Take the handles off. A drag in progress is kept. */
    fun stopResize() {
        if (dragging != 0) endDrag()
        if (pinned == null) return
        pinned = null
        pinWords = null
        drawRuns()
        tv.setPins(-1, -1, 0)
    }

    private fun showPins() {
        val d = doc
        val r = pinWords
        val run = ownRuns?.firstOrNull { it.id == pinned }
        val a = r?.let { d?.words?.getOrNull(it.first) }
        val b = r?.let { d?.words?.getOrNull(it.last) }
        if (run == null || a == null || b == null) { tv.setPins(-1, -1, 0); return }
        tv.setPins(a.charStart, b.charEnd, HighlightColors[run.color.coerceIn(HighlightColors.indices)].ui.toInt())
    }

    /** Which handle is under the finger at ([x], [y]) on screen: -1 start, 1 end, 0 none. The nearer one wins. */
    private fun pinAt(x: Float, y: Float): Int {
        val (a, b) = tv.pins() ?: return 0
        val px = x + scrollX - tv.left
        val py = y + scrollY - tv.top
        val reach = dp(30).toFloat()
        fun dist(p: PageText.Pin): Float {
            // The knob, and the stem down to the text, are both something to grab.
            val dy = when {
                py < minOf(p.knobY, p.top) -> minOf(p.knobY, p.top) - py
                py > maxOf(p.knobY, p.bottom) -> py - maxOf(p.knobY, p.bottom)
                else -> 0f
            }
            return kotlin.math.hypot(px - p.x, dy)
        }
        val da = dist(a)
        val db = dist(b)
        return when {
            da > reach && db > reach -> 0
            da <= db -> -1
            else -> 1
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && !tv.hasSelection()) {
            val which = pinAt(ev.x, ev.y)
            if (which != 0) {
                beginDrag(which, ev)
                return true
            }
        }
        return dragging != 0 || super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (dragging == 0) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { fingerX = ev.x; fingerY = ev.y; dragStep() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
        }
        return true
    }

    private fun beginDrag(which: Int, ev: MotionEvent) {
        val (a, b) = tv.pins() ?: return
        val p = if (which < 0) a else b
        dragging = which
        dragFrom = pinWords
        fingerX = ev.x
        fingerY = ev.y
        // Remember where on the handle the finger landed, so the end does not jump to the finger.
        grabDx = p.x - (ev.x + scrollX - tv.left)
        grabDy = (p.top + p.bottom) / 2f - (ev.y + scrollY - tv.top)
        parent?.requestDisallowInterceptTouchEvent(true)
        setPaused(true)
        if (android.os.Build.VERSION.SDK_INT >= 28) magnifier = android.widget.Magnifier(tv)
        tv.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Move the held end to the word under the finger. It stops at the other end, so at least one word stays. */
    private fun dragStep() {
        val d = doc ?: return
        val l = tv.layout ?: return
        val r = pinWords ?: return
        if (d.words.isEmpty()) return
        val tx = fingerX + scrollX - tv.left + grabDx
        val ty = fingerY + scrollY - tv.top + grabDy
        val line = l.getLineForVertical((ty - tv.totalPaddingTop).toInt().coerceIn(0, l.height - 1))
        val off = l.getOffsetForHorizontal(line, tx - tv.totalPaddingLeft)
        val next = Highlights.pull(d.words, r, dragging < 0, off)
        if (next != r) {
            pinWords = next
            tv.performHapticFeedback(
                if (android.os.Build.VERSION.SDK_INT >= 27) android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE
                else android.view.HapticFeedbackConstants.CLOCK_TICK,
            )
            drawRuns()
        }
        tv.pins()?.let { (a, b) ->
            val p = if (dragging < 0) a else b
            if (android.os.Build.VERSION.SDK_INT >= 28) magnifier?.show(p.x, (p.top + p.bottom) / 2f)
        }
        // Near the top or bottom edge, the page scrolls so a highlight can grow past what is on screen.
        val edge = dp(56)
        val speed = when {
            fingerY < edge -> -((edge - fingerY) / edge * dp(18)).toInt().coerceAtLeast(1)
            fingerY > height - edge -> ((fingerY - (height - edge)) / edge * dp(18)).toInt().coerceAtLeast(1)
            else -> 0
        }
        removeCallbacks(edgeScroll)
        if (speed != 0) {
            edgeSpeed = speed
            postOnAnimation(edgeScroll)
        }
    }

    private var edgeSpeed = 0
    private val edgeScroll = Runnable {
        if (dragging == 0) return@Runnable
        glidingUntil = SystemClock.uptimeMillis() + 300
        scrollBy(0, edgeSpeed)
        dragStep()
    }

    private fun endDrag() {
        val id = pinned
        val r = pinWords
        val was = dragFrom
        dragging = 0
        dragFrom = null
        removeCallbacks(edgeScroll)
        if (android.os.Build.VERSION.SDK_INT >= 28) magnifier?.dismiss()
        magnifier = null
        lastTouch = SystemClock.uptimeMillis()
        if (id != null && r != null && r != was) onResize(id, r.first, r.last)
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
        tv.setOnTouchListener { _, ev ->
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
    fun applyHighlights(byWord: IntArray, runs: List<HighlightRun>) {
        colors = byWord
        ownRuns = runs
        if (dragging == 0) {
            val open = runs.firstOrNull { it.id == pinned }
            if (pinned != null && open == null) stopResize() else if (open != null) pinWords = open.words
        }
        drawRuns()
    }

    private fun drawRuns() {
        val runs = ownRuns ?: return
        val d = doc ?: return
        val s = style ?: return
        val out = ArrayList<Mark>()
        for (run in runs) {
            val r = if (run.id == pinned) pinWords ?: run.words else run.words
            val c = run.color
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
        showPins()
    }

    private var ownRuns: List<HighlightRun>? = null

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
            // Only when there is a highlight here to take off.
            val here = doc?.wordsInSelection(tv.selectionStart.coerceAtLeast(0), tv.selectionEnd.coerceAtLeast(0))
            if (here != null && here.any { colors.getOrElse(it) { -1 } >= 0 }) menu.add(Menu.NONE, ID_ERASE, 4, "Remove highlight")
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
