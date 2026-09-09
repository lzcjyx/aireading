package com.vibecoding.aireading.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.view.GestureDetectorCompat
import com.vibecoding.aireading.model.Chapter
import com.vibecoding.aireading.model.Sentence

class ReaderCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class TextLine(
        val text: String,
        val startChar: Int,
        val endChar: Int,
        var y: Float = 0f
    )

    data class Page(
        val index: Int,
        val lines: List<TextLine>,
        val startChar: Int,
        val endChar: Int
    )

    var onSentenceClicked: ((Int) -> Unit)? = null
    var onCenterClicked: (() -> Unit)? = null
    var onPageChanged: ((Int, Int) -> Unit)? = null

    private var currentChapter: Chapter? = null
    private var pages = ArrayList<Page>()
    var currentPageIndex: Int = 0
        private set

    // Animation transition variables
    private var isAnimating = false
    private var animOffsetX = 0f
    private var oldPageIndex = -1
    private var flipAnimator: ValueAnimator? = null

    private var activeSentenceIndex: Int = -1

    // Styling & Theme
    private var fontSizeSp: Float = 20f
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val headerFooterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var themeBgColor: Int = 0xFFF6F1E7.toInt()
        set(value) { field = value; invalidate() }
    var themeTextColor: Int = 0xFF2B2520.toInt()
        set(value) { field = value; textPaint.color = value; invalidate() }
    var themeHighlightColor: Int = 0x35F59E0B.toInt()
        set(value) { field = value; highlightPaint.color = value; invalidate() }

    private val lineSpacingMultiplier = 1.6f
    private val paddingHorizontal = 52f
    private val paddingTop = 84f
    private val paddingBottom = 68f

    var onNextChapterRequested: (() -> Unit)? = null
    var onPrevChapterRequested: (() -> Unit)? = null
    private val gestureDetector: GestureDetectorCompat

    init {
        isClickable = true
        isFocusable = true

        val density = resources.displayMetrics.scaledDensity
        textPaint.textSize = fontSizeSp * density
        textPaint.color = themeTextColor

        headerFooterPaint.textSize = 12f * density
        headerFooterPaint.color = 0x88757575.toInt()

        highlightPaint.color = themeHighlightColor
        highlightPaint.style = Paint.Style.FILL

        gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isAnimating) return true
                val x = e.x
                val w = width.toFloat()

                // Standard reading zones:
                // Left 30%: Previous page
                // Right 30%: Next page
                // Center 40%: Toggle menu
                when {
                    x < w * 0.30f -> prevPage()
                    x > w * 0.70f -> nextPage()
                    else -> onCenterClicked?.invoke()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val clickedSentence = findSentenceAt(e.x, e.y)
                if (clickedSentence != null) {
                    onSentenceClicked?.invoke(clickedSentence.index)
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                val clickedSentence = findSentenceAt(e.x, e.y)
                if (clickedSentence != null) {
                    onSentenceClicked?.invoke(clickedSentence.index)
                }
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (isAnimating || e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 40) {
                    if (diffX < 0) {
                        nextPage()
                    } else {
                        prevPage()
                    }
                    return true
                }
                return false
            }
        })
    }

    fun setFontSize(sizeSp: Float) {
        if (sizeSp in 14f..36f) {
            fontSizeSp = sizeSp
            val density = resources.displayMetrics.scaledDensity
            textPaint.textSize = fontSizeSp * density
            paginate()
            invalidate()
        }
    }

    fun getFontSize(): Float = fontSizeSp

    fun setChapter(chapter: Chapter, initialSentenceIndex: Int = 0) {
        currentChapter = chapter
        activeSentenceIndex = initialSentenceIndex
        paginate()

        val sent = chapter.sentences.getOrNull(initialSentenceIndex)
        if (sent != null) {
            val pageIdx = pages.indexOfFirst { it.startChar <= sent.startChar && it.endChar >= sent.startChar }
            if (pageIdx >= 0) {
                currentPageIndex = pageIdx
            }
        } else {
            currentPageIndex = 0
        }
        invalidate()
        notifyPageChanged()
    }

    fun setActiveSentence(sentenceIdx: Int) {
        activeSentenceIndex = sentenceIdx
        val chapter = currentChapter ?: return
        val sent = chapter.sentences.getOrNull(sentenceIdx) ?: return

        // Auto Page Turning: If active sentence is on a different page, trigger smooth animated transition!
        val targetPageIdx = pages.indexOfFirst { it.startChar <= sent.startChar && it.endChar >= sent.startChar }
        if (targetPageIdx >= 0 && targetPageIdx != currentPageIndex) {
            animateToPage(targetPageIdx)
        } else {
            invalidate()
        }
    }

    fun nextPage() {
        if (isAnimating) return
        if (currentPageIndex + 1 < pages.size) {
            animateToPage(currentPageIndex + 1)
        } else {
            onNextChapterRequested?.invoke()
        }
    }

    fun prevPage() {
        if (isAnimating) return
        if (currentPageIndex > 0) {
            animateToPage(currentPageIndex - 1)
        } else {
            onPrevChapterRequested?.invoke()
        }
    }

    private fun animateToPage(targetIndex: Int) {
        if (width <= 0 || targetIndex == currentPageIndex) return
        val forward = targetIndex > currentPageIndex
        oldPageIndex = currentPageIndex
        currentPageIndex = targetIndex
        notifyPageChanged()

        val startX = if (forward) width.toFloat() else -width.toFloat()
        flipAnimator?.cancel()
        isAnimating = true
        animOffsetX = startX

        flipAnimator = ValueAnimator.ofFloat(startX, 0f).apply {
            duration = 240L
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                animOffsetX = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                    oldPageIndex = -1
                    animOffsetX = 0f
                    invalidate()
                }
            })
            start()
        }
    }

    private fun notifyPageChanged() {
        onPageChanged?.invoke(currentPageIndex + 1, maxOf(1, pages.size))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paginate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val gestureHandled = gestureDetector.onTouchEvent(event)
        return gestureHandled || super.onTouchEvent(event) || true
    }

    private fun paginate() {
        pages.clear()
        val chapter = currentChapter ?: return
        val text = chapter.content
        if (text.isEmpty() || width <= 0 || height <= 0) return

        val availWidth = width - paddingHorizontal * 2
        val availHeight = height - paddingTop - paddingBottom
        val fontMetrics = textPaint.fontMetrics
        val lineHeight = (fontMetrics.descent - fontMetrics.ascent) * lineSpacingMultiplier
        val linesPerPage = (availHeight / lineHeight).toInt().coerceAtLeast(1)

        val paragraphs = text.split("\n")
        var currentLines = ArrayList<TextLine>()
        var globalCharOffset = 0

        for (paragraph in paragraphs) {
            val pTrim = paragraph.trim()
            if (pTrim.isEmpty()) {
                globalCharOffset += paragraph.length + 1
                continue
            }

            val indentedText = "    $pTrim"
            var lineStart = 0
            val pLen = indentedText.length

            while (lineStart < pLen) {
                val charsFit = textPaint.breakText(indentedText, lineStart, pLen, true, availWidth, null)
                val lineEnd = lineStart + charsFit
                val lineSub = indentedText.substring(lineStart, lineEnd)

                val rawStart = (globalCharOffset + lineStart - 4).coerceAtLeast(globalCharOffset)
                val rawEnd = (globalCharOffset + lineEnd - 4).coerceAtLeast(rawStart)

                currentLines.add(TextLine(lineSub, rawStart, rawEnd))

                if (currentLines.size >= linesPerPage) {
                    pages.add(Page(pages.size, currentLines, currentLines.first().startChar, currentLines.last().endChar))
                    currentLines = ArrayList()
                }

                lineStart = lineEnd
            }
            globalCharOffset += paragraph.length + 1
        }

        if (currentLines.isNotEmpty()) {
            pages.add(Page(pages.size, currentLines, currentLines.first().startChar, currentLines.last().endChar))
        }

        if (currentPageIndex >= pages.size) {
            currentPageIndex = maxOf(0, pages.size - 1)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(themeBgColor)

        val chapter = currentChapter ?: return
        if (pages.isEmpty()) return

        if (isAnimating && oldPageIndex in pages.indices) {
            // Draw outgoing old page
            val oldShift = if (animOffsetX > 0) animOffsetX - width else animOffsetX + width
            canvas.save()
            canvas.translate(oldShift, 0f)
            drawPageContent(canvas, chapter, pages[oldPageIndex], isCurrent = false)
            canvas.restore()

            // Draw incoming new page
            canvas.save()
            canvas.translate(animOffsetX, 0f)
            drawPageContent(canvas, chapter, pages[currentPageIndex], isCurrent = true)
            canvas.restore()
        } else {
            val page = pages.getOrNull(currentPageIndex) ?: return
            drawPageContent(canvas, chapter, page, isCurrent = true)
        }
    }

    private fun drawPageContent(canvas: Canvas, chapter: Chapter, page: Page, isCurrent: Boolean) {
        val fontMetrics = textPaint.fontMetrics
        val lineHeight = (fontMetrics.descent - fontMetrics.ascent) * lineSpacingMultiplier

        // 1. Chapter Title Header
        canvas.drawText(chapter.title, paddingHorizontal, paddingTop * 0.65f, headerFooterPaint)

        // 2. Lines & Sentence Highlight
        val activeSent = if (isCurrent) chapter.sentences.getOrNull(activeSentenceIndex) else null
        var curY = paddingTop + (-fontMetrics.ascent)

        val rect = RectF()
        for (line in page.lines) {
            line.y = curY
            if (activeSent != null && line.endChar >= activeSent.startChar && line.startChar <= activeSent.endChar) {
                val highlightStartChar = maxOf(line.startChar, activeSent.startChar)
                val highlightEndChar = minOf(line.endChar, activeSent.endChar)

                val lineOffsetStart = (highlightStartChar - line.startChar).coerceIn(0, line.text.length)
                val lineOffsetEnd = (highlightEndChar - line.startChar).coerceIn(lineOffsetStart, line.text.length)

                val startX = paddingHorizontal + textPaint.measureText(line.text, 0, lineOffsetStart)
                val endX = paddingHorizontal + textPaint.measureText(line.text, 0, lineOffsetEnd)

                rect.set(
                    startX - 6f,
                    curY + fontMetrics.ascent - 3f,
                    endX + 6f,
                    curY + fontMetrics.descent + 5f
                )
                canvas.drawRoundRect(rect, 8f, 8f, highlightPaint)
            }

            canvas.drawText(line.text, paddingHorizontal, curY, textPaint)
            curY += lineHeight
        }

        // 3. Page Number Footer
        val footerText = "${page.index + 1} / ${pages.size}"
        val footerWidth = headerFooterPaint.measureText(footerText)
        canvas.drawText(footerText, width - paddingHorizontal - footerWidth, height - paddingBottom * 0.45f, headerFooterPaint)
    }

    private fun findSentenceAt(x: Float, y: Float): Sentence? {
        val chapter = currentChapter ?: return null
        val page = pages.getOrNull(currentPageIndex) ?: return null
        val fontMetrics = textPaint.fontMetrics

        for (line in page.lines) {
            val top = line.y + fontMetrics.ascent
            val bottom = line.y + fontMetrics.descent
            if (y in top..bottom && x in paddingHorizontal..(width - paddingHorizontal)) {
                return chapter.sentences.find { it.startChar <= line.endChar && it.endChar >= line.startChar }
            }
        }
        return null
    }

    fun getActiveSentenceIndex(): Int = activeSentenceIndex

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        flipAnimator?.cancel()
    }
}
