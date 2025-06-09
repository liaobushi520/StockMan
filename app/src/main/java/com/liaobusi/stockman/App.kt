package com.liaobusi.stockman

import android.app.Application
import android.graphics.Color


val STOCK_GREEN = Color.argb(1f, 65f / 255, 161f / 255, 61f / 255)

val STOCK_RED = Color.argb(1f, 255f / 255, 0f / 255, 0f / 255)

val STOCK_RED2 = Color.argb(0.4f, 255f / 255, 0f / 255, 0f / 255)

val STOCK_GREEN2 = Color.argb(0.4f, 65f / 255, 161f / 255, 61f / 255)

val DIVIDER_COLOR = Color.parseColor("#66333333")

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Injector.inject(applicationContext)
    }


}