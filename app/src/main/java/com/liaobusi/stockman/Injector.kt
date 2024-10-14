package com.liaobusi.stockman

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.room.Room
import com.liaobusi.stockman.api.PopularityData
import com.liaobusi.stockman.api.StockService
import com.liaobusi.stockman.api.getOkHttpClientBuilder
import com.liaobusi.stockman.db.AppDatabase
import com.liaobusi.stockman.db.BK
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.specialBK
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDateTime
import java.util.Calendar
import java.util.Date

val log = StringBuilder()

const val ENABLE_LOG = false

fun writeLog(code: String, msg: String) {

    if (ENABLE_LOG) {
        Log.i("股票超人", "$code --- $msg")
        if (log.length > 1000) {
            log.clear()
        }
//        log.append(code).append("----").append(msg).append("\n")
    }

}


@SuppressLint("StaticFieldLeak")
object Injector {

    lateinit var appDatabase: AppDatabase
    lateinit var context: Context
    lateinit var retrofit: Retrofit
    lateinit var apiService: StockService

    lateinit var conceptBks: List<BK>
    lateinit var tradeBks: List<BK>

    var popularityRanking = mapOf<String, PopularityData>()


    lateinit var sp: SharedPreferences

    var activityActive = false

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
        apiService = retrofit.create(StockService::class.java)

        GlobalScope.launch {
            tradeBks = appDatabase.bkDao().getTradeBKs()
            conceptBks = appDatabase.bkDao().getConceptBKs().filter { !it.specialBK }
        }
        sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)

        if (!sp.contains("diy_bk_code")) {
            sp.edit().putInt("diy_bk_code", 100000).apply()
        }

        (applicationContext as Application).registerActivityLifecycleCallbacks(object :
            ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is HomeActivity) {
                    GlobalScope.launch(Dispatchers.IO) {
                        StockRepo.getRealTimeStocks()
                        StockRepo.getRealTimeBKs()
                    }
                    autoRefreshPopularityRanking()
                    startAutoRefresh()
                }

            }

            override fun onActivityStarted(activity: Activity) {

            }

            override fun onActivityResumed(activity: Activity) {
                if (activity is Strategy4Activity || activity is BKStrategyActivity) {

                    activityActive = true
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (activity is Strategy4Activity || activity is BKStrategyActivity) {
                    activityActive = false
                }
            }

            override fun onActivityStopped(activity: Activity) {

            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

            }

            override fun onActivityDestroyed(activity: Activity) {

            }

        })


    }

    private var autoRefreshJob: Job? = null
    private var autoRefreshPopularityRankingJob: Job? = null

    fun startAutoRefresh() {
        val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        autoRefresh(sp.getBoolean("auto_refresh", false))
    }

    fun autoRefresh(enable: Boolean) {
        autoRefreshJob?.cancel()
        if (enable) {
            autoRefreshJob = GlobalScope.launch {
                while (true) {
                    val cal = Calendar.getInstance().apply {
                        time = Date()
                    }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    if (hour in 9..15) {
                        if (activityActive) {
                            StockRepo.getRealTimeBKs()
                            StockRepo.getRealTimeStocks()
                        }
                        delay(2000)
                    } else {
                        delay(1000 * 60 * 6)
                    }
                }
            }
        }
    }

    fun autoRefreshPopularityRanking() {
        autoRefreshPopularityRankingJob?.cancel()
        autoRefreshPopularityRankingJob = GlobalScope.launch {
            while (true) {
                val map = mutableMapOf<String, PopularityData>()
                StockRepo.fetchPopularityRanking().forEach {
                    map[it.SECURITY_CODE] = it
                }
                if (!isActive) return@launch
                popularityRanking = map
                delay(1000 * 60 * 10)
            }
        }

    }


    private val snapshotList = mutableListOf<Stock>()
    fun getSnapshot(): List<Stock> {
        return snapshotList
    }

    fun deleteSnapshot() {
        snapshotList.clear()
    }

    fun takeSnapshot(stocks: List<Stock>) {
        snapshotList.clear()
        snapshotList.addAll(stocks)
    }

    val bkZTCountMap = mutableMapOf<String, Int>()


    val stockLianBanCountMap = mutableMapOf<String, Int>()


}