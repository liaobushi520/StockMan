package com.liaobusi.stockman
import com.liaobusi.stockman5.R

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat

/**
 * 沿圆角矩形边框绘制的跑动描边，叠在查询按钮之上作为加载态。
 */
class RoundedRectBorderLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val path = Path()
    private val rect = RectF()
    private val pathMeasure = PathMeasure()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var phase = 0f
    private var animator: ValueAnimator? = null
    private var wantsLoading = false

    init {
        paint.strokeWidth = dp(2.5f)
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val sw = paint.strokeWidth
        val inset = sw / 2f
        rect.set(inset, inset, w - inset, h - inset)
        path.reset()
        path.addRoundRect(rect, cornerPx, cornerPx, Path.Direction.CW)
        pathMeasure.setPath(path, false)
        if (wantsLoading) {
            restartAnimatorIfNeeded()
        }
    }

    fun setLoading(active: Boolean) {
        wantsLoading = active
        if (!active) {
            animator?.cancel()
            animator = null
            visibility = GONE
            phase = 0f
            invalidate()
            return
        }
        visibility = VISIBLE
        paint.color = ContextCompat.getColor(context, R.color.fit_cta_text)
        paint.strokeWidth = dp(2.5f)
        if (width > 0 && height > 0) {
            restartAnimatorIfNeeded()
        }
        invalidate()
    }

    private fun restartAnimatorIfNeeded() {
        val len = pathMeasure.length
        if (len <= 1f) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, len).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!wantsLoading || visibility != VISIBLE) return
        val len = pathMeasure.length
        if (len <= 1f) return
        val dash = len * 0.28f
        val gap = len - dash
        paint.pathEffect = DashPathEffect(floatArrayOf(dash, gap), phase)
        canvas.drawPath(path, paint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private val cornerPx: Float get() = dp(10f)
}
