 package com.liaobusi.stockman.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar

class VerticalSeekBar : AppCompatSeekBar {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
    }

    @Synchronized
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.rotate(-90f)
        canvas.translate(-getHeight().toFloat(), 0f)
        super.onDraw(canvas)
    }

    override fun setProgress(progress: Int) {
        super.setProgress(progress)
        onSizeChanged(getWidth(), getHeight(), 0, 0)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled()) {
            return false
        }

        when (event.getAction()) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                setProgress(getMax() - (getMax() * event.getY() / getHeight()).toInt())
                onSizeChanged(getWidth(), getHeight(), 0, 0)
            }

            MotionEvent.ACTION_CANCEL -> {}
        }
        return true
    }
}