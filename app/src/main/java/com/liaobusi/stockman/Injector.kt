package com.liaobusi.stockman


import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.room.Room
import com.liaobusi.stockman.api.StockService
import com.liaobusi.stockman.api.TGBStock
import com.liaobusi.stockman.api.THSStock
import com.liaobusi.stockman.api.getOkHttpClientBuilder
import com.liaobusi.stockman.db.AppDatabase
import com.liaobusi.stockman.db.BK
import com.liaobusi.stockman.db.DragonTigerRank
import com.liaobusi.stockman.db.PopularityRank
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.marketCode
import com.liaobusi.stockman.db.openWeb
import com.liaobusi.stockman.db.specialBK
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

    lateinit var sp: SharedPreferences

    var activityActive = true

    val scope = MainScope()

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

        scope.launch(Dispatchers.IO) {
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
                    scope.launch(Dispatchers.IO) {
                        StockRepo.getRealTimeStocks()
                        StockRepo.getRealTimeBKs()
                        val l = StockRepo.fetchDragonTigerRank(today())
                        val ll = l.map {
                            return@map DragonTigerRank(
                                it.SECURITY_CODE,
                                today(),
                                it.EXPLANATION
                            )
                        }
                        appDatabase.dragonTigerDao().insert(ll)
                        appDatabase.dragonTigerDao().getDragonTigerByDate(today())
                    }

                    val serviceIntent =
                        Intent(applicationContext, com.liaobusi.stockman.StockService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(applicationContext, serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
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
                    activityActive = true
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
            autoRefreshJob = scope.launch(Dispatchers.IO) {
                while (true) {
                    val cal = Calendar.getInstance().apply {
                        time = Date()
                    }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    if (hour in 9..14) {
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


    suspend fun refreshPopularityRanking() {
        val list = StockRepo.fetchPopularityRanking()

        val thsList = StockRepo.fetchTHSPopularityRanking()

        val tgbList = StockRepo.fetchTGBPopularityRanking()

        val dzhList = StockRepo.fetchDZHPopularityRanking()

        val clsList = StockRepo.fetchCLSPopularityRanking()

        val dzhMap = mutableMapOf<String, Int>()
        dzhList.forEachIndexed { index, item ->
            val key = item.keys.first().removeRange(0, 2)
            dzhMap[key] = index + 1
        }

        val clsMap = mutableMapOf<String, Int>()
        clsList.forEachIndexed { index, item ->
            val key = item.stock.StockID.removeRange(0, 2)
            clsMap[key] = index + 1
        }


        val map = mutableMapOf<String, THSStock>()
        thsList.forEach {
            map[it.code] = it
        }

        val map2 = mutableMapOf<String, TGBStock>()
        tgbList.forEach {
            map2[it.fullCode.removeRange(0, 2)] = it
        }


        val newList = list.map {
            val tgb = map2[it.SECURITY_CODE]
            val explainSb = StringBuilder()

            explainSb.append("[东方财富] ${it.POPULARITY_RANK}\n\n")


            val ths = map[it.SECURITY_CODE]
            if (ths != null) {
                explainSb.append("[同花顺] ${ths.order}\n")
                ths.tag.concept_tag.forEach {
                    explainSb.append("${it}|")
                }
                if (explainSb.endsWith("|")) {
                    explainSb.deleteCharAt(explainSb.length - 1)
                }

                if (ths.tag.popularity_tag != null) {
                    explainSb.append("   ${ths.tag.popularity_tag} ")
                }


                if (ths.topic != null)
                    explainSb.append("\n${ths.topic.title} ")

                if (ths.analyse_title != null) {
                    explainSb.append("\n<${ths.analyse_title}>")
                }
                if (ths.analyse != null) {
                    explainSb.append("\n${ths.analyse}")
                }

                explainSb.append("\n\n")


            }

            val dzh = dzhMap[it.SECURITY_CODE]
            if (dzh != null) {
                explainSb.append("[大智慧] ${dzh}\n\n")
            }

            val cls = clsMap[it.SECURITY_CODE]
            if (cls != null) {
                explainSb.append("[财联社] ${cls}\n\n")
            }

            if (tgb != null) {
                explainSb.append("[淘股吧] ${tgb.ranking}\n")
                tgb.gnList.forEach {
                    explainSb.append("${it.gnName}|")
                }
                if (explainSb.endsWith("|")) {
                    explainSb.deleteCharAt(explainSb.length - 1)
                }
                explainSb.append("\n${tgb.remark}")
            }



            PopularityRank(
                it.SECURITY_CODE,
                today(),
                it.POPULARITY_RANK,
                map[it.SECURITY_CODE]?.order ?: -1,
                map2[it.SECURITY_CODE]?.ranking ?: -1,
                explainSb.trim().toString(),
                dzhMap[it.SECURITY_CODE] ?: -1,
                clsMap[it.SECURITY_CODE] ?: -1
            )
        }
        if (newList.isNotEmpty()) {
            appDatabase.popularityRankDao().insertTransaction(today(), newList)
        }
    }

    fun autoRefreshPopularityRanking() {
        autoRefreshPopularityRankingJob?.cancel()
        autoRefreshPopularityRankingJob = scope.launch(Dispatchers.IO) {
            while (true) {
                refreshPopularityRanking()
                if (!isActive) return@launch
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


    fun startTracking() {
        val cal = Calendar.getInstance().apply {
            time = Date()
        }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour in 9..14) {
            scope.launch(Dispatchers.IO) {
                val list =
                    appDatabase.popularityRankDao().getRanksByDate(today()).map { it.code }

                list.split(50).forEach {
                    launch(Dispatchers.Default) {
                        tracking(it)
                    }
                }


//                list.map { StockTracker(it) }.forEach {
//                    launch {
//                        it.startTrack()
//                    }
//                }
            }

        }
    }


}

data class StockRecord(val stock: Stock, val time: Long = System.currentTimeMillis())




suspend fun tracking(codes: List<String>) {
    val trackerMap = mutableMapOf<String, Tracker>()
    codes.forEach {
        trackerMap[it] = Tracker()
    }
    while (true) {
        val dao = Injector.appDatabase.stockDao()
        val stockList = dao.getStockByCodes(codes)
        stockList.forEach {
            trackerMap[it.code]?.update(it)
        }
        delay(1000)
    }
}

class  Tracker{
    private val cache = mutableListOf<StockRecord>()
    fun update(s: Stock) {
        val sb = StringBuilder()
        val last = if (cache.size >= 3) cache[cache.size - 2] else cache.lastOrNull()
        if (last != null) {
            val zf = (s.price - last.stock.price) / last.stock.price
            if (zf >= 0.01) {
                sb.append("${(System.currentTimeMillis() - last.time) / 1000}秒内涨幅${zf * 100}%")
            }

            if (last.stock.ztPrice == last.stock.price && s.price < s.ztPrice) {
                sb.append("[炸板]")
            }

            if (last.stock.dtPrice == last.stock.price && s.price > s.dtPrice) {
                sb.append("[翘板]")
            }
        }

        val mid = if (cache.size >= 28) cache[cache.size / 2] else null
        if (mid != null) {
            val zf = (s.price - mid.stock.price) / mid.stock.price
            if (zf >= 0.03) {
                sb.append("${(System.currentTimeMillis() - mid.time) / 1000}秒内涨幅${zf * 100}%")
            }
        }

        val first = cache.firstOrNull()
        if (first != null) {
            val zf = (s.price - first.stock.price) / first.stock.price
            if (zf >= 0.06) {
                sb.append("${(System.currentTimeMillis() - first.time) / 1000}秒内涨幅${zf * 100}%")
            }


        }

        if (sb.isNotEmpty()) {
            sendNotification(s, "${s.code}${s.name}异动,涨跌幅${s.chg}%", sb.toString())
        }

        cache.add(StockRecord(s))
        if (cache.size >= 30) {
            cache.removeAt(0)
        }
    }


    private fun sendNotification(stock: Stock, title: String, content: String) {
        val channel = NotificationChannel(
            "股票超人",
            "异动",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Channel description"
        val notificationManager: NotificationManager = Injector.context.getSystemService(
            NotificationManager::class.java
        )
        notificationManager.createNotificationChannel(channel)
        val s = "dfcf18://stock?market=${stock.marketCode()}&code=${stock.code}"
        val uri: Uri = Uri.parse(s)
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(Injector.context, "股票超人")
                .setSmallIcon(R.mipmap.ic_launcher) // 设置通知小图标
                .setContentTitle(title) // 设置通知标题
                .setContentText(content) // 设置通知内容
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 设置通知优先级
                .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000)) // 设置震动模式
                .setLights(Color.RED, 1000, 1000) // 设置呼吸灯效果
                .setContentIntent(
                    PendingIntent.getActivity(
                        Injector.context,
                        100,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )


        notificationManager.notify(stock.code.toInt(), builder.build())


    }
}


class StockTracker(val code: String) {


    private val cache = mutableListOf<StockRecord>()


    suspend fun startTrack() {

        val dao = Injector.appDatabase.stockDao()

        while (true) {
            val s = dao.getStockByCode(code)

            val sb = StringBuilder()
            val last = if (cache.size >= 3) cache[cache.size - 2] else cache.lastOrNull()
            if (last != null) {
                val zf = (s.price - last.stock.price) / last.stock.price
                if (zf >= 0.01) {
                    sb.append("${(System.currentTimeMillis() - last.time) / 1000}秒内涨幅${zf * 100}%")
                }
            }

            val mid = if (cache.size >= 28) cache[cache.size / 2] else null
            if (mid != null) {
                val zf = (s.price - mid.stock.price) / mid.stock.price
                if (zf >= 0.03) {
                    sb.append("${(System.currentTimeMillis() - mid.time) / 1000}秒内涨幅${zf * 100}%")
                }
            }

            val first = cache.firstOrNull()
            if (first != null) {
                val zf = (s.price - first.stock.price) / first.stock.price
                if (zf >= 0.06) {
                    sb.append("${(System.currentTimeMillis() - first.time) / 1000}秒内涨幅${zf * 100}%")
                }

                if (first.stock.ztPrice == first.stock.price && s.price < s.ztPrice) {
                    sb.append("[炸板]")
                }

                if (first.stock.dtPrice == first.stock.price && s.price > s.dtPrice) {
                    sb.append("[翘板]")
                }
            }

            if (sb.isNotEmpty()) {
                sendNotification(s, "${s.code}${s.name}异动,涨跌幅${s.chg}%", sb.toString())
            }

            cache.add(StockRecord(s))
            if (cache.size >= 30) {
                cache.removeAt(0)
            }
            delay(1200)
        }
    }


    private fun sendNotification(stock: Stock, title: String, content: String) {
        val channel = NotificationChannel(
            "股票超人",
            "异动",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Channel description"
        val notificationManager: NotificationManager = Injector.context.getSystemService(
            NotificationManager::class.java
        )
        notificationManager.createNotificationChannel(channel)
        val s = "dfcf18://stock?market=${stock.marketCode()}&code=${stock.code}"
        val uri: Uri = Uri.parse(s)
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(Injector.context, "股票超人")
                .setSmallIcon(R.mipmap.ic_launcher) // 设置通知小图标
                .setContentTitle(title) // 设置通知标题
                .setContentText(content) // 设置通知内容
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 设置通知优先级
                .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000)) // 设置震动模式
                .setLights(Color.RED, 1000, 1000) // 设置呼吸灯效果
                .setContentIntent(
                    PendingIntent.getActivity(
                        Injector.context,
                        100,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )


        notificationManager.notify(stock.code.toInt(), builder.build())


    }

}