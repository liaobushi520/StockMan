package com.liaobusi.stockman

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.liaobusi.stockman.repo.UpdateWorker
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Injector.inject(applicationContext)
        val r = PeriodicWorkRequestBuilder<UpdateWorker>(8, TimeUnit.HOURS).build()

        WorkManager
            .getInstance(this).apply {
                val work= getWorkInfosForUniqueWork("update").apply {}
              if( work.get().firstOrNull()==null){
                   enqueueUniquePeriodicWork("update",ExistingPeriodicWorkPolicy.KEEP, r)
              }

            }


    }
}