package com.vibecoding.aireading.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class SoundWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD4AF37.toInt()
        style = Paint.Style.FILL
    }

    private val rectF = RectF()
    private var animator: ValueAnimator? = null
    private var animProgress = 0f

    var waveColor: Int
        get() = paint.color
        set(value) {
            paint.color = value
            invalidate()
        }

    var isPlaying: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    startAnimation()
                } else {
                    stopAnimation()
                }
            }
        }

    init {
        // Default start idle
    }

    private fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
        animProgress = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val barCount = 4
        val barWidth = (w / (barCount * 2 - 1)).coerceAtLeast(4f)
        val cornerRadius = barWidth / 2f
        val maxHeight = h * 0.85f
        val minHeight = h * 0.2f

        val phaseOffsets = floatArrayOf(0f, 1.8f, 3.2f, 4.7f)

        for (i in 0 until barCount) {
            val left = i * barWidth * 2f
            val right = left + barWidth

            val barHeight = if (isPlaying) {
                val wave = ((sin(animProgress + phaseOffsets[i]) + 1.0) / 2.0).toFloat()
                minHeight + (maxHeight - minHeight) * wave
            } else {
                minHeight
            }

            val top = (h - barHeight) / 2f
            val bottom = top + barHeight

            rectF.set(left, top, right, bottom)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying) startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
