package com.liaobusi.stockman.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.ScrollView

interface OnCustomScrollListener {
    fun onCustomScroll(scrollView: DelayScrollView, height: Int, topInWindow: Int, top: Int);
}


class DelayScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ScrollView(context, attrs) {


    private val mListeners = mutableListOf<OnCustomScrollListener>()
    private val rect = intArrayOf(0, 0)

    private var firstLayout=true

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if(firstLayout){
            this.getLocationInWindow(rect)
            mListeners.forEach { listener->
                listener.onCustomScroll(this, this.height, rect[1], this.top)
            }
            firstLayout=false
        }


    }


    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        this.getLocationInWindow(rect)
        mListeners.forEach {
            it.onCustomScroll(this, height, rect[1], t)
        }
    }

    fun addCustomScrollListener(listener: OnCustomScrollListener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener)

        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mListeners.clear()
    }

    fun removeCustomScrollListener(listener: OnCustomScrollListener){
        if(mListeners.contains(listener)){
            mListeners.remove(listener)
        }
    }


}