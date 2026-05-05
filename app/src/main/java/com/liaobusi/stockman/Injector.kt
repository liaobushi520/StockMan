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
import androidx.core.content.edit
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.GsonBuilder
import com.liaobusi.stockman.api.StockService
import com.liaobusi.stockman.api.TGBStock
import com.liaobusi.stockman.api.THSStock
import com.liaobusi.stockman.api.getOkHttpClientBuilder
import com.liaobusi.stockman.db.AppDatabase
import com.liaobusi.stockman.db.BK
import com.liaobusi.stockman.db.PopularityRank
import com.liaobusi.stockman.db.Stock
import com.liaobusi.stockman.db.marketCode
import com.liaobusi.stockman.db.specialBK
import com.liaobusi.stockman.repo.StockRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale



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

    private val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE Follow ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `StockLinkage` (`code` TEXT NOT NULL, `relatedCodes` TEXT NOT NULL, PRIMARY KEY(`code`))"
            )
        }
    }

    lateinit var appDatabase: AppDatabase
    lateinit var context: Context
    lateinit var retrofit: Retrofit
    lateinit var apiService: StockService

    lateinit var conceptBks: List<BK>
    lateinit var tradeBks: List<BK>


    lateinit var sp: SharedPreferences


    val realTimeStockMap = mutableMapOf<String, Stock>()

    val scope = MainScope()

    var trackerType = false

    val client = getOkHttpClientBuilder().build()

    private fun createAppDatabase(applicationContext: Context) =
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "stock_man"
        ).addMigrations(MIGRATION_34_35, MIGRATION_35_36)
            .build()

    fun closeAppDatabaseForRestore() {
        if (::appDatabase.isInitialized) {
            try {
                appDatabase.close()
            } catch (_: Throwable) {
            }
        }
    }

    fun reopenAppDatabaseIfNeeded(applicationContext: Context) {
        if (!::appDatabase.isInitialized || !appDatabase.isOpen) {
            appDatabase = createAppDatabase(applicationContext)
        }
    }

    fun inject(applicationContext: Context) {
        context = applicationContext

        appDatabase = createAppDatabase(applicationContext)
        retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder()
                        .setLenient() // 允许非严格 JSON
                        .create()
                )
            )
            .client(client)
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

        trackerType = trackingType(context)


        val serviceIntent =
            Intent(applicationContext, com.liaobusi.stockman.StockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(applicationContext, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        (applicationContext as Application).registerActivityLifecycleCallbacks(object :
            ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is HomeActivity) {
                    scope.launch(Dispatchers.IO) {
                        StockRepo.getRealTimeIndexByCode("1.000001")
                        StockRepo.getRealTimeIndexByCode("2.932000")
                        StockRepo.getRealTimeIndexByCode("1.000905")
                        StockRepo.getRealTimeStocksDFCF()
                        StockRepo.getRealTimeBKs()

                        StockRepo.fetchDragonTigerRank(today())
                        StockRepo.getKPLLive()
                        StockRepo.getCLSLive()
                        StockRepo.getTHSLive(today())
                        StockRepo.getLimitUpPool(today())
                        StockRepo.getLimitDownPool(today())

                        val sp = activity.getSharedPreferences("app", Context.MODE_PRIVATE)
                        if (System.currentTimeMillis() - sp.getLong(
                                "fetch_bk_stocks_time",
                                0
                            ) > 1 * 12 * 60 * 60 * 1000
                        ) {
                            StockRepo.getBKStocks()
                            sp.edit {
                                putLong("fetch_bk_stocks_time", System.currentTimeMillis())
                            }
                        }


                        if (System.currentTimeMillis() - sp.getLong(
                                "fetch_gdrs_time",
                                0
                            ) > 5 * 24 * 60 * 60 * 1000
                        ) {
                            StockRepo.getHistoryGDRS()
                            sp.edit { putLong("fetch_gdrs_time", System.currentTimeMillis()) }
                        }

                    }
                }

            }

            override fun onActivityStarted(activity: Activity) {

            }

            override fun onActivityResumed(activity: Activity) {
            }

            override fun onActivityPaused(activity: Activity) {
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
                async {
                    while (true) {
                        while (true) {
                            if (isTradingTime()) {
                                StockRepo.getRealTimeBKs()
                                delay(30 * 1000)
                            } else {
                                delay(1000 * 60 * 6)
                            }
                        }
                    }
                }

                if (isTradingTime() && isRealTimeDataSource(context)) {
                    repeat(3) {
                        launch(Dispatchers.IO) {
                            StockRepo.requestStocks(it + 1)
                        }
                    }
                }

                if (!isRealTimeDataSource(context)) {

//                    async {
//                        while (true) {
//                            if (isTradingTime()) {
//                                StockRepo.getRealTimeStocksBD()
//                                delay(2000)
//                            } else {
//                                delay(1000 * 60 * 6)
//                            }
//                        }
//                    }


//                    async {
//                        StockRepo.getRealTimeStocksBD2()
//                    }

                    async {
                        while (true) {
                            if (isTradingTime()) {
                                delay(1000)
                                StockRepo.getRealTimeSinaSZ()
                            } else {
                                delay(1000 * 60 * 6)
                            }
                        }
                    }

                    async {
                        while (true) {
                            if (isTradingTime()) {
                                StockRepo.getRealTimeStocksSH()
                                delay(1000)
                            } else {
                                delay(1000 * 60 * 6)
                            }
                        }
                    }

//                    async {
//                        while (true) {
//                            if (isTradingTime()) {
//                                StockRepo.getRealTimeStocksCX()
//                                delay(3000)
//                            } else {
//                                delay(1000 * 60 * 6)
//                            }
//                        }
//                    }

                    async {
                        while (true) {
                            if (isCallAuctionTime()) {
                                StockRepo.getCallWarnType()
                                delay(3 * 60 * 1000)
                            } else {
                                delay(30 * 60 * 1000)
                            }
                        }
                    }

                    async {
                        while (true) {
                            if (isCallAuctionTime()) {
                                StockRepo.getRealTimeStocksDFCF()
                                delay(3000)
                            } else {
                                delay(30 * 60 * 1000)
                            }
                        }
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

        val codeSB = StringBuilder()
        list.forEach {
            if (it.SECUCODE.contains("SZ")) {
                codeSB.append("SZ${it.SECURITY_CODE},")
            } else if (it.SECUCODE.contains("SH")) {
                codeSB.append("SH${it.SECURITY_CODE},")
            }
        }
        val hotTopicMap = StockRepo.fetchDFCFHotTopic(codeSB.removeSuffix(",").toString())

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

            explainSb.append("[东方财富] ${it.POPULARITY_RANK}\n")
            val hotTopics = hotTopicMap?.get(it.SECURITY_CODE)
            hotTopics?.forEach {
                explainSb.append("${it.name}\n${it.summary}\n")
            }
            explainSb.append("\n")


            val ths = map[it.SECURITY_CODE]
            if (ths != null) {
                explainSb.append("[同花顺] ${ths.order}\n")
                ths.tag?.concept_tag?.forEach {
                    explainSb.append("${it}|")
                }
                if (explainSb.endsWith("|")) {
                    explainSb.deleteCharAt(explainSb.length - 1)
                }

                if (ths.tag?.popularity_tag != null) {
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
                tgb.gnList?.forEach {
                    explainSb.append("${it.gnName}|")
                }
                if (explainSb.endsWith("|")) {
                    explainSb.deleteCharAt(explainSb.length - 1)
                }
                if (tgb.remark != null)
                    explainSb.append("\n ${tgb.remark}")
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
            try {
                while (true) {
                    refreshPopularityRanking()
                    if (!isActive) return@launch
                    delay(1000 * 60 * 60)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
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


    val trackerMap = mutableMapOf<String, Tracker>()
    private fun startTracking2() {
        scope.launch(Dispatchers.IO) {
            val list = if (isFocusLB(context)) {
                appDatabase.historyStockDao().getZTHistoryByDate(preTradingDay(today()))
                    .map { it.code }
            } else {
                appDatabase.popularityRankDao().getRanksByDate(today()).map { it.code }
            }
            Log.e(
                "股票超人",
                "开始跟踪股票异动方式2 聚焦昨日涨停${isFocusLB(context)} 数量${list.size}"
            )
            list.forEach {
                trackerMap[it] = Tracker()
            }

        }
    }


    private var trackingJob: Job? = null

    fun startTracking() {
        trackingJob?.cancel()
        trackerMap.clear()
        if (trackerType) {
            startTracking1()
        } else {
            startTracking2()
        }
    }

    private fun startTracking1() {
        if (isTradingTime()) {
            trackingJob = scope.launch(Dispatchers.IO) {
                val list = if (isFocusLB(context)) {
                    appDatabase.historyStockDao().getZTHistoryByDate(preTradingDay(today()))
                        .map { it.code }
                } else {
                    appDatabase.popularityRankDao().getRanksByDate(today()).map { it.code }
                }
                Log.e(
                    "股票超人",
                    "开始跟踪股票异动方式1 聚焦昨日涨停${isFocusLB(context)} 数量${list.size}"
                )
                list.split(10).forEachIndexed { index, codes ->
                    launch(Dispatchers.Default) {
                        tracking(codes, "track${index}")
                    }
                }
            }

        }
    }


    data class StockRecord(val stock: Stock, val time: Long = System.currentTimeMillis())


    private suspend fun tracking(codes: List<String>, tag: String) {
        val trackerMap = mutableMapOf<String, Tracker>()
        codes.forEach {
            trackerMap[it] = Tracker()
        }
        while (true) {
            codes.forEach {
                val stock = realTimeStockMap[it]
                if (stock != null)
                    trackerMap[it]?.update(stock)
            }
            delay(1000)
        }
    }


    class Tracker {
        private val cache = mutableListOf<StockRecord>()
        /**
         * [cache] 中 [Stock.chg] 最小的下标；用于「相对窗口最低涨跌幅不足 1%」时
         * 跳过按 chg 推算的时间窗涨幅检测。
         */
        private var minChgIdx = -1

        fun update(s: Stock) {
            val sb = StringBuilder()
            appendBoardEvents(sb, s, cache.lastOrNull())

            val skipChgWindows =
                cache.isNotEmpty() && minChgIdx in cache.indices &&
                    s.chg - cache[minChgIdx].stock.chg < 1f
            if (!skipChgWindows) {
                val nowMs = System.currentTimeMillis()
                val chg = s.chg
                val fifthBack = if (cache.size >= 5) cache[cache.size - 5] else cache.lastOrNull()
                appendChgSpike(sb, chg, fifthBack, nowMs, minDeltaChg = 1f, secRange = SEC_0_THROUGH_15)
                val n = cache.size
                if (n >= 3) {
                    appendChgSpike(sb, chg, cache[n * 2 / 3], nowMs, 2f, SEC_15_THROUGH_60)
                    appendChgSpike(sb, chg, cache[n / 3], nowMs, 3f, SEC_61_THROUGH_90)
                }
                cache.firstOrNull()?.let {
                    appendChgSpike(sb, chg, it, nowMs, 4f, SEC_91_THROUGH_180)
                }
            }

            if (sb.isNotEmpty()) {
                sendNotification(s, "${s.code}${s.name}异动,涨跌幅${s.chg}%", sb.toString())
            }
            pushCache(s)
        }

        private fun appendBoardEvents(sb: StringBuilder, s: Stock, last: StockRecord?) {
            val lp = last?.stock ?: return
            if (s.price == s.ztPrice && lp.price != lp.ztPrice) sb.append("[涨停]")
            if (lp.ztPrice == lp.price && s.price < s.ztPrice) sb.append("[炸板]")
            if (s.price == s.dtPrice && lp.dtPrice != lp.price) sb.append("[跌停]")
            if (lp.dtPrice == lp.price && s.price > s.dtPrice) sb.append("[翘板]")
        }

        /** 在时间窗 [secRange]（秒，闭区间）内相对 anchor 涨幅 ≥ minDeltaChg 则追加文案 */
        private fun appendChgSpike(
            sb: StringBuilder,
            curChg: Float,
            anchor: StockRecord?,
            nowMs: Long,
            minDeltaChg: Float,
            secRange: IntRange,
        ) {
            val a = anchor ?: return
            val elapsed = ((nowMs - a.time) / 1000L).toInt().coerceAtLeast(0)
            if (elapsed !in secRange) return
            val delta = curChg - a.stock.chg
            if (delta < minDeltaChg) return
            sb.append("${elapsed}秒内涨幅${String.format(Locale.getDefault(), "%.2f", delta)}% ")
        }

        private fun indexOfMinimumChg(): Int {
            if (cache.isEmpty()) return -1
            var bestI = 0
            var bestChg = cache[0].stock.chg
            for (i in 1 until cache.size) {
                val c = cache[i].stock.chg
                if (c < bestChg) {
                    bestChg = c
                    bestI = i
                }
            }
            return bestI
        }

        private fun pushCache(s: Stock) {
            val prevMinChg =
                if (minChgIdx >= 0 && minChgIdx < cache.size) cache[minChgIdx].stock.chg
                else Float.POSITIVE_INFINITY
            cache.add(StockRecord(s))
            val newIdx = cache.lastIndex
            minChgIdx = when {
                cache.size == 1 -> 0
                s.chg < prevMinChg -> newIdx
                minChgIdx < 0 || minChgIdx > cache.lastIndex -> indexOfMinimumChg()
                else -> minChgIdx
            }
            if (cache.size >= CACHE_SIZE) {
                if (minChgIdx == 0) {
                    cache.removeAt(0)
                    minChgIdx = indexOfMinimumChg()
                } else {
                    cache.removeAt(0)
                    minChgIdx--
                }
            }
        }

        private fun sendNotification(stock: Stock, title: String, content: String) {
            val nm = context.getSystemService(NotificationManager::class.java)
            ensureNotificationChannelOnce(nm)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(
                "dfcf18://stock?market=${stock.marketCode()}&code=${stock.code}",
            ))
            val pending = PendingIntent.getActivity(
                Injector.context,
                100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            nm.notify(
                stock.code.toInt(),
                NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
                    .setLights(Color.RED, 1000, 1000)
                    .setContentIntent(pending)
                    .build(),
            )
        }

        companion object {
            private const val CACHE_SIZE = 80
            private const val NOTIFICATION_CHANNEL_ID = "股票超人"

            private val SEC_0_THROUGH_15 = 0..15
            private val SEC_15_THROUGH_60 = 15..60
            private val SEC_61_THROUGH_90 = 61..90
            private val SEC_91_THROUGH_180 = 91..180

            private val channelLock = Any()
            @Volatile
            private var notificationChannelEnsured = false

            private fun ensureNotificationChannelOnce(nm: NotificationManager) {
                if (notificationChannelEnsured) return
                synchronized(channelLock) {
                    if (notificationChannelEnsured) return
                    nm.createNotificationChannel(
                        NotificationChannel(
                            NOTIFICATION_CHANNEL_ID,
                            "异动",
                            NotificationManager.IMPORTANCE_HIGH,
                        ).apply { description = "Channel description" },
                    )
                    notificationChannelEnsured = true
                }
            }
        }
    }
}

