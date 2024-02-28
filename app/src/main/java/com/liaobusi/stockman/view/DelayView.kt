package com.liaobusi.stockman.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewStub
import android.widget.FrameLayout
import androidx.core.view.children

class DelayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), OnCustomScrollListener {

    private fun findDelayScrollView(): DelayScrollView? {
        var p = this.parent
        while (p != null && p !is DelayScrollView) {
            p = p.parent
        }

        if (p == null) {
            return null
        }

        return p as DelayScrollView

    }

    override fun onAttachedToWindow() {

        super.onAttachedToWindow()
        val p = findDelayScrollView()
        p?.addCustomScrollListener(this)
    }

    val rect = intArrayOf(0, 0)

    override fun onCustomScroll(
        scrollView: DelayScrollView,
        height: Int,
        topInWindow: Int,
        top: Int,
        ) {

        this.getLocationInWindow(rect)
        if (height + topInWindow + 100 > rect[1]) {
            val v = getChildAt(0)
            if (v != null && v is ViewStub) {
                v.inflate()
                findDelayScrollView()?.removeCustomScrollListener(this)
            }
        }
    }


}