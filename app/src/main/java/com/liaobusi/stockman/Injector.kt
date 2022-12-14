package com.liaobusi.stockman

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.room.Room
import com.liaobusi.stockman.api.getOkHttpClientBuilder
import com.liaobusi.stockman.db.AppDatabase
import com.liaobusi.stockman.db.BK
import com.liaobusi.stockman.db.specialBK
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val log = StringBuilder()

const val ENABLE_LOG = true

fun writeLog(code: String, msg: String) {

    if (ENABLE_LOG) {
        Log.i("股票超人", "$code --- $msg")
        if (log.length > 1000) {
            log.clear()
        }
        log.append(code).append("----").append(msg).append("\n")
    }

}


@SuppressLint("StaticFieldLeak")
object Injector {

    lateinit var appDatabase: AppDatabase
    lateinit var context: Context
    lateinit var retrofit: Retrofit

    lateinit var conceptBks: List<BK>
    lateinit var tradeBks: List<BK>


    fun inject(applicationContext: Context) {
        context = applicationContext
        appDatabase = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "stock_man"
        ).build()
        retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(getOkHttpClientBuilder().build())
            .build()

        GlobalScope.launch {
            tradeBks = appDatabase.bkDao().getTradeBKs()
            conceptBks = appDatabase.bkDao().getConceptBKs().filter { !it.specialBK }
        }

    }


}