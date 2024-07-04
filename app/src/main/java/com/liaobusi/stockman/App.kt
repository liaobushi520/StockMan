package com.liaobusi.stockman

import android.app.Application
import android.graphics.Color


  val STOCK_GREEN=Color.argb(1f,65f/255,161f/255,61f/255)

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Injector.inject(applicationContext)
    }


}