package com.liaobusi.stockman.repo

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.liaobusi.stockman.Injector
import com.liaobusi.stockman.api.StockService
import com.liaobusi.stockman.api.StockTrend
import com.liaobusi.stockman.before
import com.liaobusi.stockman.compute
import com.liaobusi.stockman.db.*
import com.liaobusi.stockman.howDayShowZTFlag
import com.liaobusi.stockman.isShowHiddenStockAndBK
import com.liaobusi.stockman.writeLog
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

import java.lang.Integer.min
import java.lang.Math.max
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.roundToInt


data class Strategy1Param(
    val startMarketTime: Int,
    val endMarketTime: Int,
    val lowMarketValue: Double,
    val highMarketValue: Double,
    val ztRange: Int,
    val adjustTimeAfterZT: Int,
    val afterZTStockPriceLowRate: Double,
    val afterZTStockPriceHighRate: Double,
    val newHighestRangeBeforeZT: Int,
    val allowedZTBeforeZT: Int,
    val amplitudeAfterZT: Double,
    val endTime: Int,
    val bkList: List<String>? = null,
)

data class Strategy2Param(
    val startMarketTime: Int,
    val endMarketTime: Int,
    val lowMarketValue: Double,
    val highMarketValue: Double,
    val range: Int,
    val increaseLow: Double = 0.00,
    val increaseHigh: Double = 0.1,
    val adjustTimeAfterZT: Int,
    val ztCount: Int,
    val endTime: Int,
    val zbEnable: Boolean = false,
    val minAdjustTimeAfterZT: Int = 2,
    val bkList: List<String>? = null
)

data class Strategy3Param(
    val startMarketTime: Int,
    val endMarketTime: Int,
    val lowMarketValue: Double,
    val highMarketValue: Double,
    val range: Int,
    val endTime: Int,
    val allowedZTBeforeZT: Int = 0,
    val ztNextTurnoverRateLow: Double,
    val allowedZTRangeBeforeZT: Int,
    val decreaseTurnoverRateHigh: Double
)

data class Strategy4Param(
    val startMarketTime: Int,
    val endMarketTime: Int,
    val lowMarketValue: Double,
    val highMarketValue: Double,
    val range: Int,
    val averageDay: Int = 10,
    val divergeRate: Double = 0.03,
    val endTime: Int = 20220722,
    val allowBelowCount: Int = 10,
    //异常放量区间
    val abnormalRange: Int = 20,
    val abnormalRate: Double = 3.0,
    val bkList: List<String>? = null,
    val stockList: List<Stock>? = null
)

//板块强势
data class Strategy7Param(
    val range: Int,
    val averageDay: Int = 10,
    val divergeRate: Double = 0.03,
    val endTime: Int = 20220722,
    val allowBelowCount: Int = 10,
)

//涨停强势
data class Strategy8Param(
    val startMarketTime: Int,
    val endMarketTime: Int,
    val lowMarketValue: Double,
    val highMarketValue: Double,
    val ztRange: Int,
    val adjustTimeAfterZT: Int,
    val endTime: Int,
    val averageDay: Int = 5,
    val divergeRate: Double = 0.02,
    val allowBelowCount: Int = 1,
    val bkList: List<String>? = null,
)


object StockRepo {


    suspend fun refreshData() {
        getRealTimeStocks()
        getRealTimeBKs()
    }


    suspend fun filterStockByGDRS(stocks: List<StockResult>, count: Int): List<StockResult> =
        withContext(Dispatchers.IO) {
            val l = mutableListOf<StockResult>()
            val gdrsDao = Injector.appDatabase.gdrsDao()
            stocks.forEach {
                val list = gdrsDao.getGDRSByCode(it.stock.code)
                val m = kotlin.math.min(list.size - 1, count)
                for (i in list.indices) {
                    if (i == m) {
                        l.add(it)
                        break
                    }
                    if (list[i].totalNumRatio > 0 && i < m) {
                        break
                    }
                }
            }
            return@withContext l
        }

    suspend fun getHistoryGDRS() {

        val dao = Injector.appDatabase.stockDao()
        val api = Injector.retrofit.create(StockService::class.java)

        val gdrsDao = Injector.appDatabase.gdrsDao()
        val jobs = mutableListOf<Deferred<Any>>()
        dao.getAllStock().forEach {

            val j = GlobalScope.async {
                try {
                    val marhetCode = if (it.marketCode() == 0) "SZ" else "SH"
                    val f = "(SECUCODE=\"${it.code}.${marhetCode}\")"
                    val response = api.getGDRS(f)
                    if (response.success) {
                        val gdrss = response.result.data.mapIndexed { index, gdrsBean ->
                            val date = SimpleDateFormat("yyyy-MM-dd").parse(gdrsBean.END_DATE)
                            var s = gdrsBean.TOTAL_NUM_RATIO
                            if (index != response.result.data.size - 1) {
                                if (gdrsBean.TOTAL_NUM_RATIO == 0.0f && response.result.data[index + 1].HOLDER_TOTAL_NUM > 0) {
                                    s =
                                        ((gdrsBean.HOLDER_TOTAL_NUM - response.result.data[index + 1].HOLDER_TOTAL_NUM).toFloat() / response.result.data[index + 1].HOLDER_TOTAL_NUM)
                                }
                            }
                            GDRS(gdrsBean.SECURITY_CODE, date.time, s, gdrsBean.HOLDER_TOTAL_NUM)
                        }

                        gdrsDao.insert(gdrss)
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            jobs.add(j)
        }
        jobs.awaitAll()
    }


    //板块抗跌
    private fun bkKD(
        stockHistories: List<HistoryBK>
    ): Float {

        val historyBKDao = Injector.appDatabase.historyBKDao()
        val dpHistories = historyBKDao.getHistory("000001")
        if (dpHistories.isEmpty()) {
            return 0f
        }

        val stockMap = mutableMapOf<Int, HistoryBK>()
        stockHistories.forEach {
            stockMap[it.date] = it
        }

        var kd = 0f
        dpHistories.subList(0, min(5, dpHistories.size)).forEach {
            val s = stockMap[it.date]
            if (s != null) {
                kd += (s.chg - it.chg)
            }


//            if (s != null) {
//                if (it.chg < -2) {
//                    if (s.chg >= 0.00) {
//                        kd += 10
//                    } else if (s.chg >= -1) {
//                        kd += 5
//                    }
//
//                } else if (it.chg < -1) {
//                    if (s.chg >= 0.00) {
//                        kd += 8
//                    } else if (s.chg >= -1) {
//                        kd += 3
//                    }
//                } else if (it.chg < -0.5) {
//                    if (s.chg >= 0.00) {
//                        kd += 6
//                    } else if (s.chg >= -1) {
//                        kd += 2
//                    }
//                } else if (it.chg < 0) {
//                    if (s.chg >= 0.00) {
//                        kd += 1
//                    }
//                }
//            }
        }
        return kd
    }


    //抗跌
    private fun kd(
        stockHistories: List<HistoryStock>
    ): Int {

        val historyBKDao = Injector.appDatabase.historyBKDao()
        val dpHistories = historyBKDao.getHistory("000001")
        if (dpHistories.isEmpty()) {
            return 0
        }

        val stockMap = mutableMapOf<Int, HistoryStock>()
        stockHistories.forEach {
            stockMap[it.date] = it
        }

        var kd = 0
        dpHistories.subList(0, min(5, dpHistories.size)).forEach {
            val s = stockMap[it.date]
            if (s != null) {
                if (it.chg < -2) {
                    if (s.chg >= 0.00) {
                        kd += 10
                    } else if (s.chg >= -5) {
                        kd += 5
                    }

                } else if (it.chg < -1) {
                    if (s.chg >= 0.00) {
                        kd += 8
                    } else if (s.chg >= -5) {
                        kd += 3
                    }
                } else if (it.chg < -0.5) {
                    if (s.chg >= 0.00) {
                        kd += 6
                    } else if (s.chg >= -5) {
                        kd += 2
                    }
                } else if (it.chg < 0) {
                    if (s.chg >= 0.00) {
                        kd += 1
                    }
                }
            }
        }
        return kd
    }

    //牛回头
    private fun cowBack(histories: List<HistoryStock>): Boolean {

        //牛回头
        var cowBack = false
        var lowestClosePrice = Float.MAX_VALUE
        var zdIndex = -1
        val step = 10
        //找到step内的最低点
        kotlin.run run@{
            for (i in 0 until min(step, histories.size)) {
                if (histories[i].closePrice <= lowestClosePrice) {
                    lowestClosePrice = histories[i].closePrice
                    zdIndex = i
                }
            }
        }
        if (zdIndex >= 0) {
            //找到左侧最高
            var leftHighestClosePrice = Float.MIN_VALUE
            var leftHighestIndex = -1
            kotlin.run run@{
                for (i in min(zdIndex + 1, histories.size - 1) until min(
                    zdIndex + step,
                    histories.size
                )) {
                    if (histories[i].closePrice >= leftHighestClosePrice) {
                        leftHighestClosePrice = histories[i].closePrice
                        leftHighestIndex = i
                    }
                }
            }

            //找到右侧最高
            var rightHighestClosePrice = Float.MIN_VALUE
            var rightHighestIndex = -1
            kotlin.run run@{
                for (i in 0 until zdIndex) {
                    if (histories[i].closePrice >= leftHighestClosePrice) {
                        rightHighestClosePrice = histories[i].closePrice
                        rightHighestIndex = i
                    }
                }
            }
            if (leftHighestClosePrice > lowestClosePrice * 1.10 && rightHighestClosePrice < leftHighestClosePrice * 0.95) {
                //出现止跌信号
                if (histories[0].closePrice >= histories[zdIndex].closePrice * 1.02 && histories[0].closePrice < histories[zdIndex].closePrice * 1.10 && zdIndex >= 2) {
                    return true
                }
            }
        }


        return false
    }


    suspend fun getHistoryStocks(startDate: Int, endDate: Int) = withContext(Dispatchers.IO) {
        //                                 开盘价    收盘价     涨跌    涨跌幅   最低价   最高价                          换手率
        //[{"status":0,"hq":[["2022-08-02","13.31","13.82","1.26","10.03%","13.02","13.82","3294343","444015.31","20.89%"]],"code":"cn_000547"}]
        val dao = Injector.appDatabase.stockDao()
        val dao1 = Injector.appDatabase.historyStockDao()
        val allStock = dao.getAllStockByMarketTime(startDate).filter {
            return@filter true
        }
        var start = 0
        val step = 5
        var end = min(start + step, allStock.size)

        try {
            while (start <= allStock.size - 1) {
                val sub = allStock.subList(start, end)
                val codeBuild = StringBuilder()

                sub.forEach {
                    codeBuild.append("cn_" + it.code + ",")
                }
                try {
                    val response = Injector.retrofit.create(StockService::class.java)
                        .getStockHistory(
                            codeBuild.toString(),
                            startDate.toString(),
                            endDate.toString()
                        )

                    response.forEach {
                        val c = it.code.substring(3)
                        if (it.hq == null) {
                            Log.e("股票超人", "${c} 股票历史为空")
                        }
                        val ll = it.hq?.map {
                            val d = SimpleDateFormat("yyyy-MM-dd").parse(it[0])
                            val dd = SimpleDateFormat("yyyyMMdd").format(d).toInt()
                            val chg = it[4].substring(0, it[4].length - 1).toFloat()
                            val turnoverRate = it[9].substring(0, it[9].length - 1).toFloatOrNull()
                            return@map HistoryStock(
                                code = c,
                                date = dd,
                                openPrice = it[1].toFloat(),
                                chg = chg,
                                closePrice = it[2].toFloat(),
                                lowest = it[5].toFloat(),
                                highest = it[6].toFloat(),
                                turnoverRate = turnoverRate ?: 0f,
                                amplitude = 100f,
                                ztPrice = -1f,
                                dtPrice = -1f,
                                yesterdayClosePrice = -1f,
                                averagePrice = -1f
                            )
                        }
                        dao1.insertHistory(ll!!)
                    }

                } catch (e: Throwable) {
                    e.printStackTrace()
                }

                launch(Dispatchers.Main) {
                    Toast.makeText(
                        Injector.context,
                        "完成${(end * 100f / allStock.size).toInt()}/100",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                start = end
                end = min(start + step, allStock.size)
            }
        } catch (e: Throwable) {
            return@withContext false
        }
        return@withContext true
    }


    suspend fun getBKStocks() {
        val bkDao = Injector.appDatabase.bkDao()
        val api = Injector.retrofit.create(StockService::class.java)
        val bkStockDao = Injector.appDatabase.bkStockDao()
        val l = bkDao.getAllBK().filter { !it.specialBK && it.code != "000001" }.compute { bk ->
            val fs = "b:" + bk.code
            val response = api.getBKStocks(fs)
            if (response.data != null) {
                val list = response.data.diff.map {
                    return@map BKStock(bk.code, it.code)
                }
                Log.i("股票超人", "获取板块${bk.name}-${bk.code}股票列表数据,共${list.size}条")
                return@compute list
            }
            return@compute null
        }
        bkStockDao.insertAll(l.flatten())
    }

    private fun getHighestForBK(bk: BK, date: Int): Int {
        val historyStockDao = Injector.appDatabase.historyStockDao()
        val bkStockDao = Injector.appDatabase.bkStockDao()
        val stocks = bkStockDao.getStocksByBKCode(bk.code)

        var highestLianBanCount = 0

        stocks.forEach {
            //连板数
            var lianbanCount = 0
            val key = it.code + date

            val v = Injector.stockLianBanCountMap[key]
            if (v != null) {
                lianbanCount = v
            } else {
                val histories = historyStockDao.getHistoryBefore3(it.code, date, 30)
                var stop = false
                var i = 0
                while (!stop && i < histories.size) {
                    if (histories[i].ZT) {
                        lianbanCount++
                        i++
                    } else {
                        stop = true
                    }
                }
                Injector.stockLianBanCountMap[key] = lianbanCount
            }

            if (lianbanCount > highestLianBanCount) {
                highestLianBanCount = lianbanCount
            }
        }
        return highestLianBanCount
    }

    private fun getBKZTRate(bk: BK, date: Int, days: Int = 5): Pair<Int, Float> {
        val historyStockDao = Injector.appDatabase.historyStockDao()
        val bkStockDao = Injector.appDatabase.bkStockDao()
        var ztTotal = 0f
        val stocks = bkStockDao.getStocksByBKCode(bk.code)
        stocks.forEach {
            val key = it.code + date + days
            val value = Injector.bkZTCountMap[key]
            if (value != null) {
                ztTotal += value
            } else {
                val list = historyStockDao.getHistoryBefore3(it.code, date, days)
                val c = list.count { it.ZT }
                Injector.bkZTCountMap[key] = c
                ztTotal += c
                Log.i(
                    "股票超人",
                    "板块${bk.name}-${bk.code} 股票${it.name}-${it.code}--近${days}内有${c}次涨停"
                )
            }
        }
        Log.i(
            "股票超人",
            "板块${bk.name}-${bk.code}--${date}--近${days}内有${ztTotal}次涨停，板块内股票涨停概率${ztTotal / (days * stocks.size)}"
        )
        return Pair(ztTotal.toInt(), ztTotal / (days * stocks.size))
    }


    suspend fun getHistoryBks() = withContext(Dispatchers.IO) {

        val bkDao = Injector.appDatabase.bkDao()
        val historyDao = Injector.appDatabase.historyBKDao()
        val bks = bkDao.getAllBK()

        bks.forEach { bk ->
            Log.i("StockMan", "处理板块${bk.name}历史数据")
            val api = Injector.retrofit.create(StockService::class.java)
            val s = if (bk.code.startsWith("BK")) "90.${bk.code}" else "1.${bk.code}"
            val url =
                "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=${s}&klt=101&fqt=1&lmt=99&end=20500000&iscca=1&fields1=f1,f2,f3,f4,f5,f6,f7,f8&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64&forcect=1"
            val response = api.getBKHistory(url)
            if (response.data == null) {
                return@forEach
            }
            val list = response.data.klines.reversed().map {
                val arr = it.split(',')
                val date = arr[0].replace("-", "").toInt()
                val open = arr[1].toFloat()
                val close = arr[2].toFloat()
                val high = arr[3].toFloat()
                val low = arr[4].toFloat()
                val amplitude = arr[7].toFloat()
                val chg = arr[8].toFloat()
                val turnoverRate = arr[10].toFloat()
                return@map HistoryBK(
                    date = date,
                    openPrice = open,
                    closePrice = close,
                    lowest = low,
                    highest = high,
                    amplitude = amplitude,
                    chg = chg,
                    turnoverRate = turnoverRate,
                    code = bk.code,
                    yesterdayClosePrice = -1f
                )
            }
            historyDao.insertHistory(list)
        }

        return@withContext true
    }


    suspend fun fixData(code: String, fixedDate: Int) {

        val dao = Injector.appDatabase.historyStockDao()
        val api = Injector.retrofit.create(StockService::class.java)
        val marketCode =
            if (code.startsWith("000") || code.startsWith("300") || code.startsWith("002") || code.startsWith(
                    "001"
                ) || code.startsWith("003") || code.startsWith("301")
            ) 0 else 1

        val url =
            "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=$marketCode.$code&klt=101&fqt=1&lmt=100&end=20500000&iscca=1&fields1=f1,f2,f3,f4,f5,f6,f7,f8&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64,f350,f351,f352&forcect=1"
        val response2 = api.getBKHistory(url)
        if (response2.data == null) {
            return
        }
        val sh = response2.data.klines.reversed().map {
            val arr = it.split(',')
            val d = arr[0].replace("-", "").toInt()
            val open = arr[1].toFloat()
            val close = arr[2].toFloat()
            val high = arr[3].toFloat()
            val low = arr[4].toFloat()
            val amplitude = arr[7].toFloat()
            val chg = arr[8].toFloat()
            val turnoverRate = arr[10].toFloat()
            return@map HistoryStock(
                openPrice = open,
                closePrice = close,
                lowest = low,
                highest = high,
                amplitude = amplitude,
                chg = chg,
                code = response2.data.code,
                turnoverRate = turnoverRate,
                yesterdayClosePrice = -1f,
                date = d,
                averagePrice = -1f,
                ztPrice = -1f,
                dtPrice = -1f
            )
        }
        val errorHistory = sh.find { it.date == fixedDate }
        if (errorHistory != null) {
            val i = sh.indexOf(errorHistory)
            val pre = sh.getOrNull(i + 1)
            dao.insertHistory(
                listOf(
                    errorHistory.copy(
                        yesterdayClosePrice = pre?.closePrice ?: -1f
                    )
                )
            )
        }

    }

    suspend fun getRealTimeBKs() = withContext(Dispatchers.IO) {
        try {

            val bks = mutableListOf<BK>()
            val bkHistories = mutableListOf<HistoryBK>()

            val dao = Injector.appDatabase.bkDao()
            val dao1 = Injector.appDatabase.historyBKDao()
            val api = Injector.retrofit.create(StockService::class.java)
            val url =
                "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=1.000001&klt=101&fqt=1&lmt=1&end=20500000&iscca=1&fields1=f1,f2,f3,f4,f5,f6,f7,f8&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64&forcect=1"
            val response2 = api.getBKHistory(url)
            var d = -1
            val sh = response2.data?.klines?.reversed()?.map {
                val arr = it.split(',')
                d = arr[0].replace("-", "").toInt()
                val open = arr[1].toFloat()
                val close = arr[2].toFloat()
                val high = arr[3].toFloat()
                val low = arr[4].toFloat()
                val amplitude = arr[7].toFloat()
                val chg = arr[8].toFloat()
                val turnoverRate = arr[10].toFloat()
                return@map BK(
                    openPrice = open,
                    price = close,
                    lowest = low,
                    highest = high,
                    amplitude = amplitude,
                    chg = chg,
                    turnoverRate = turnoverRate,
                    code = "000001",
                    name = "上证指数",
                    yesterdayClosePrice = response2.data.prePrice.toFloat(),
                    circulationMarketValue = -1f,
                    type = 2
                )
            }
            val shHistory = sh?.map {
                return@map HistoryBK(
                    code = it.code,
                    date = d,
                    closePrice = it.price,
                    chg = it.chg,
                    amplitude = it.amplitude,
                    turnoverRate = it.turnoverRate,
                    highest = it.highest,
                    lowest = it.lowest,
                    openPrice = it.openPrice,
                    yesterdayClosePrice = it.yesterdayClosePrice,
                )
            }
            if (sh != null) {
                bks.addAll(sh)
            }
            if (shHistory != null) {
                bkHistories.addAll(shHistory)
            }

            listOf(0, 1).forEach { type ->
                val response =
                    if (type == 0) api.getRealTimeTradeBK() else api.getRealTimeConceptBK()
                if (response.data != null) {
                    val list = response.data.diff.map {
                        return@map BK(
                            code = it.code,
                            name = it.name,
                            turnoverRate = it.turnoverRate,
                            highest = it.highest,
                            lowest = it.lowest,
                            price = it.price,
                            chg = it.chg,
                            amplitude = it.amplitude,
                            openPrice = it.openPrice,
                            yesterdayClosePrice = it.yesterdayClosePrice,
                            circulationMarketValue = it.circulationMarketValue,
                            type = type
                        )
                    }
                    bks.addAll(list)
                    val date = response.data.diff.first().date
                    val histories = list.map {
                        return@map HistoryBK(
                            code = it.code,
                            date = date,
                            closePrice = it.price,
                            chg = it.chg,
                            amplitude = it.amplitude,
                            turnoverRate = it.turnoverRate,
                            highest = it.highest,
                            lowest = it.lowest,
                            openPrice = it.openPrice,
                            yesterdayClosePrice = it.yesterdayClosePrice,
                        )
                    }
                    bkHistories.addAll(histories)
                }
            }

            Log.i("股票超人", "从网络获取所有板块${bks.size}")
            dao.insertAll(bks)
            Log.i("股票超人", "插入板块信息数据库")
            dao1.insertHistory(bkHistories)
            Log.i("股票超人", "插入板块历史信息数据库")

        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    suspend fun getRealTimeStockData(code: String) = withContext(Dispatchers.IO) {
        val response = Injector.retrofit.create(StockService::class.java).getRealTimeStockData(code)
        val reader = BufferedReader(InputStreamReader(response.byteStream()))
        try {
            val results = StringBuilder()
            while (true) {
                val line = reader.readLine() ?: break

                if (line.isNullOrEmpty()) {
                    continue
                }

                Log.e("股票超人", "${code}实时数据-->  " + line)

                val s = line.indexOf('{')
                val ss = line.substring(s)
                val stockTrend = Gson().fromJson(ss, StockTrend::class.java)
                stockTrend.data.trends.lastOrNull()?.let {
                    //时间 开 收 最高 最低  成交额
                    val list = it.split(",")
                }

                results.append(line)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            reader.close()
        }
    }


    suspend fun getRealTimeStocks() = withContext(Dispatchers.IO) {
        try {
            val response = Injector.retrofit.create(StockService::class.java).getRealTimeStocks()
            if (response.data != null) {
                val list = response.data.diff.filter { it.price != 0f }.map {
                    return@map Stock(
                        code = it.code,
                        name = it.name,
                        turnoverRate = it.turnoverRate / 100,
                        highest = it.highest / 100,
                        lowest = it.lowest / 100,
                        price = it.price / 100,
                        chg = it.chg / 100,
                        amplitude = it.amplitude / 100,
                        openPrice = it.openPrice / 100,
                        yesterdayClosePrice = it.yesterdayClosePrice / 100,
                        toMarketTime = it.toMarketTime,
                        circulationMarketValue = it.circulationMarketValue,
                        ztPrice = it.ztPrice / 100,
                        dtPrice = it.dtPrice / 100,
                        averagePrice = it.averagePrice / 100,
                        bk = it.concept

                    )
                }
                Log.i("股票超人", "从网络获取所有股票${list.size}")
                val dao = Injector.appDatabase.stockDao()
                dao.insertAll(list)
                Log.i("股票超人", "插入股票信息数据库")

                val dao1 = Injector.appDatabase.historyStockDao()
                val today =
                    SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())).toInt()
                val date = response.data.diff.first().date
                val historyStocks = list.filter { it.price != 0f }.map {
                    return@map HistoryStock(
                        code = it.code,
                        date = date,
                        closePrice = it.price,
                        chg = it.chg,
                        amplitude = it.amplitude,
                        turnoverRate = it.turnoverRate,
                        highest = it.highest,
                        lowest = it.lowest,
                        openPrice = it.openPrice,
                        ztPrice = it.ztPrice,
                        dtPrice = it.dtPrice,
                        yesterdayClosePrice = it.yesterdayClosePrice,
                        averagePrice = it.averagePrice
                    )
                }
                dao1.insertHistory(historyStocks)
                Log.i("股票超人", "插入股票历史信息数据库")

            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }


    //涨停洗盘
    suspend fun strategy1(
        startMarketTime: Int = 19900102,
        endMarketTime: Int = 20180102,
        lowMarketValue: Double = 1000000000.0,
        highMarketValue: Double = 10000000000.0,
        //ztRange日内有涨停
        ztRange: Int = 8,
        //涨停之后的调整时间
        adjustTimeAfterZT: Int = 4,
        //涨停之后的调整区间
        afterZTStockPriceLowRate: Double = 0.97,
        afterZTStockPriceHighRate: Double = 1.03,
        //涨停后洗盘期间的区间振幅
        amplitudeAfterZT: Double = 0.1,
        newHighestRangeBeforeZT: Int = 60,
        //允许涨停之前newHighestRange日内存在涨停次数
        allowedZTBeforeZT: Int = 0,
        endTime: Int = 20220803,
        abnormalRange: Int = 10,
        bkList: List<String>? = null,
    ) = withContext(Dispatchers.IO) {
        val stockDao = Injector.appDatabase.stockDao()
        val historyDao = Injector.appDatabase.historyStockDao()
        val bkStockDao = Injector.appDatabase.bkStockDao()


        val stocks = if (bkList?.isNotEmpty() == true) {
            bkList.flatMap { bkCode ->
                return@flatMap bkStockDao.getStocksByBKCode2(
                    bkCode,
                    startTime = startMarketTime,
                    endTime = endMarketTime,
                    lowMarketValue = lowMarketValue,
                    highMarketValue = highMarketValue
                )
            }.distinctBy { it.code }
        } else {
            stockDao.getStock(
                startTime = startMarketTime,
                endTime = endMarketTime,
                lowMarketValue = lowMarketValue,
                highMarketValue = highMarketValue
            )
        }

        val follows = Injector.appDatabase.followDao().getFollowStocks()

//        val stocks=listOf( stockDao.getStockByCode("300898"))
        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())
        val result = stocks.compute {
            val histories =
                historyDao.getHistoryRange(it.code, endDay.before(ztRange), endTime)
            //找到涨停
            var ztStockHistory: HistoryStock? = null
            run run@{
                histories.forEach {
                    if (it.ZT) {
                        ztStockHistory = it
                        return@run
                    }
                }
            }

            if (ztStockHistory == null) {
                writeLog(it.code, "在${ztRange}日内未找到涨停")
                return@compute null
            }

            //涨停之后调整时间控制
            val i = histories.indexOf(ztStockHistory)
            if (i < adjustTimeAfterZT) {
                writeLog(it.code, "涨停后调整时间过短  $i < $adjustTimeAfterZT")
                return@compute null
            }


            //涨停后几日股价不低于涨停日收盘价*afterZTStockPriceRate
            var highestPriceAfterZT = 0f
            var lowestPriceAfterZT = 10000000000000f
            run run@{
                histories.forEach {
                    if (it == ztStockHistory) {
                        return@run
                    }
                    if (it.closePrice < ztStockHistory!!.closePrice * afterZTStockPriceLowRate || it.closePrice > ztStockHistory!!.closePrice * afterZTStockPriceHighRate) {
                        writeLog(
                            it.code,
                            "${it.date} 收盘价${it.closePrice}  允许最低${ztStockHistory!!.closePrice * afterZTStockPriceLowRate} 最高${ztStockHistory!!.closePrice * afterZTStockPriceHighRate}  "
                        )
                        return@compute null
                    }

                    if (it.highest > highestPriceAfterZT) {
                        highestPriceAfterZT = it.highest
                    }

                    if (it.lowest < lowestPriceAfterZT) {
                        lowestPriceAfterZT = it.lowest
                    }

                }
            }


            //调整期间的振幅，振幅小一般是控盘洗盘的表现
            val realAmplitudeAfterZT =
                ((highestPriceAfterZT - lowestPriceAfterZT) / ztStockHistory!!.closePrice)
            if (realAmplitudeAfterZT > amplitudeAfterZT) {
                writeLog(it.code, "振幅过大  ${realAmplitudeAfterZT} > ${amplitudeAfterZT}")
                return@compute null
            }

            val ztDate = SimpleDateFormat("yyyyMMdd").parse(ztStockHistory!!.date.toString())

            //涨停后到当前破newHighestRange新高
            val highestPriceBeforeZT = historyDao.getHistoryHighestPrice(
                it.code,
                ztDate.before(newHighestRangeBeforeZT),
                ztStockHistory!!.date
            )

            if (highestPriceAfterZT < highestPriceBeforeZT) {
                writeLog(it.code, "涨停之后未创新高  $highestPriceAfterZT < $highestPriceBeforeZT")
                return@compute null
            }
            val ztList = historyDao.getZTHistoryStock(
                it.code,
                ztDate.before(newHighestRangeBeforeZT),
                ztStockHistory!!.date
            )
            if (ztList.size > allowedZTBeforeZT) {
                writeLog(it.code, "涨停之前允许的涨停次数过多  ${ztList.size} < $allowedZTBeforeZT")
                return@compute null
            }


            //信号

            //大阳信号
            val dayang = histories[0].DY
            val zt = histories[0].ZT

            //炸板
            val friedPlate = histories[0].friedPlate

            //长上影
            val longUpShadow = histories[0].longUpShadow

            //过前高
            val highestClosePrice = historyDao.getHistoryHighestPrice(
                it.code,
                ztDate.before(newHighestRangeBeforeZT),
                histories[1].date
            )
            val overPreHigh = histories[0].highest >= highestClosePrice

            var totalTurnOverRate = 0.0
            for (i in 0 until min(abnormalRange, histories.size)) {
                totalTurnOverRate += histories[i].turnoverRate
            }
            val perTurnOverRate = totalTurnOverRate / min(abnormalRange, histories.size)

            //放量
            val highTurnOverRate = histories[0].turnoverRate > perTurnOverRate * 2


            //连阳
            var lianyangCount = 0
            while (lianyangCount < histories.size && histories[lianyangCount].chg >= 0) {
                lianyangCount += 1
            }


            //牛回头
            val cowBack = cowBack(histories)
            //抗跌
            val kd = kd(histories)

            val list = historyDao.getHistoryAfter3(it.code, endTime, 10)
            val hasZT = list.find { it.ZT } != null


            return@compute StockResult(
                it,
                highTurnOverRate = highTurnOverRate,
                friedPlate = friedPlate,
                dayang = if (zt) false else dayang,
                zt = zt,
                overPreHigh = overPreHigh,
                longUpShadow = longUpShadow,
                lianyangCount = lianyangCount,
                cowBack = cowBack,
                kd = kd,
                nextDayZT = hasZT,
                follow = follows.filter { f -> f.code == it.code }.isNotEmpty()
            )


        }
        Collections.sort(result, kotlin.Comparator
        { v0, v1 ->
            return@Comparator v1.signalCount - v0.signalCount
        })

        return@withContext result
    }


    //涨停强势
    suspend fun strategy8(
        startMarketTime: Int = 19900102,
        endMarketTime: Int = 20180102,
        lowMarketValue: Double = 1000000000.0,
        highMarketValue: Double = 10000000000.0,
        //ztRange个交易日内有涨停
        ztRange: Int = 10,
        //涨停之后的调整时间
        adjustTimeAfterZT: Int = 4,
        endTime: Int = 20221106,
        averageDay: Int = 5,
        divergeRate: Double = 0.02,
        allowBelowCount: Int = 1,
        bkList: List<String>? = null,
    ) = withContext(Dispatchers.IO) {
        val stockDao = Injector.appDatabase.stockDao()
        val historyDao = Injector.appDatabase.historyStockDao()
        val bkStockDao = Injector.appDatabase.bkStockDao()


        val stocks = if (bkList?.isNotEmpty() == true) {
            bkList.flatMap { bkCode ->
                return@flatMap bkStockDao.getStocksByBKCode2(
                    bkCode,
                    startTime = startMarketTime,
                    endTime = endMarketTime,
                    lowMarketValue = lowMarketValue,
                    highMarketValue = highMarketValue
                )
            }.distinctBy { it.code }
        } else {
            stockDao.getStock(
                startTime = startMarketTime,
                endTime = endMarketTime,
                lowMarketValue = lowMarketValue,
                highMarketValue = highMarketValue
            )
        }


        val follows = Injector.appDatabase.followDao().getFollowStocks()

//        val stocks=listOf( stockDao.getStockByCode("002771"))
        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())
        val result = mutableListOf<StockResult>()
        stocks.forEach stockList@{
            val histories =
                historyDao.getHistoryRange(it.code, endDay.before(ztRange * 2), endTime)

            if (histories.size < ztRange) {
                return@stockList
            }

            //找到涨停
            var ztStockHistory: HistoryStock? = null
            run run@{
                histories.subList(0, ztRange).forEach {
                    if (it.ZT) {
                        ztStockHistory = it
                        return@run
                    }
                }
                return@stockList
            }

            val ztTotalCount = histories.subList(0, ztRange).count {
                it.ZT
            }



            if (ztStockHistory == null) {
                writeLog(it.code, "在${ztRange}日内未找到涨停")
                return@stockList
            }
            val ztIndex = histories.indexOf(ztStockHistory)

            if (ztIndex < adjustTimeAfterZT) {
                writeLog(it.code, "涨停后调整时间过短  $ztIndex < $adjustTimeAfterZT")
                return@stockList
            }

            var totalTurnoverRate = 0f
            var totalAmplitude = 0f
            //调整期间均线强势
            if (histories.size < ztIndex + averageDay - 1) {
                return@stockList
            }
            var s = 0
            var currentAllowBelowCount = allowBelowCount
            var belowLine5Count = 0

            var totalAverageDiverge = 0.0
            while (s < ztIndex) {
                totalTurnoverRate += histories[s].turnoverRate
                totalAmplitude += histories[s].amplitude

                var total = 0.0

                for (i in s until s + averageDay) {
                    total += histories[i].closePrice
                }

                val averagePrice = total / averageDay

                totalAverageDiverge += (histories[s].closePrice - averagePrice) / averagePrice


                if (histories[s].closePrice < averagePrice * (1.0 - divergeRate)) {
                    writeLog(
                        it.code,
                        "${histories[s].date} ${averageDay}日线 $averagePrice 除去偏差值${averagePrice * (1.0 - divergeRate)}"
                    )
                    belowLine5Count++

                    if (currentAllowBelowCount <= 0) {
                        return@stockList
                    }
                    currentAllowBelowCount -= 1
                }
                s++
            }

            val list = historyDao.getHistoryAfter3(it.code, endTime)
            val hasZT = list.find { it.ZT } != null

            val h =
                (histories[0].closePrice - ztStockHistory!!.closePrice) * 100 / ztStockHistory!!.closePrice

            Log.e(
                "涨停强势",
                "${it.name}--${ztIndex}天平均换手率${totalTurnoverRate / ztIndex} 平均振幅${totalAmplitude / ztIndex}  均线偏差${totalAverageDiverge} ${h}"
            )

            //放量
            val perTurnOverRate = totalTurnoverRate / ztIndex
            val highTurnOverRate = histories[0].turnoverRate > perTurnOverRate * 1


            //过前高
            var overPreHigh = false
            if (histories.size >= 2) {
                val highestClosePrice = historyDao.getHistoryHighestPrice(
                    it.code,
                    histories[ztIndex].date,
                    histories[1].date
                )
                overPreHigh = histories[0].highest >= highestClosePrice
            }

            //大阳信号
            val dayang = histories[0].DY
            val zt = histories[0].ZT

            //炸板
            val friedPlate = histories[0].friedPlate

            //长上影
            val longUpShadow = histories[0].longUpShadow


            //触线
            var touchLine = false
            if (histories.size >= averageDay) {
                var line = 0f
                for (i in 0 until averageDay) {
                    line += histories[i].closePrice
                }

                if (line / averageDay >= histories[0].lowest && histories[0].closePrice > line / averageDay) {
                    //触10日线
                    touchLine = true
                }
            }



            result.add(
                StockResult(
                    it,
                    hyAfterZT = ((totalTurnoverRate * 0.4 / ztIndex) + totalAmplitude * 0.1 / ztIndex + 0.2 * h + totalAverageDiverge * 0.3 * 50).toInt(),
                    nextDayZT = hasZT,
                    ztCountInRange = ztTotalCount,
                    highTurnOverRate = highTurnOverRate,
                    overPreHigh = overPreHigh,
                    dayang = if (zt) false else dayang,
                    zt = zt,
                    longUpShadow = longUpShadow,
                    friedPlate = friedPlate,
                    touchLine = touchLine,
                    follow = follows.filter { f -> f.code == it.code }.isNotEmpty()
                )
            )
        }

        Collections.sort(result, kotlin.Comparator { v0, v1 ->
            return@Comparator v1.hyAfterZT - v0.hyAfterZT
        })

        return@withContext result
    }


    //涨停揉搓
    suspend fun strategy2(
        startMarketTime: Int = 19900102,
        endMarketTime: Int = 20180102,
        lowMarketValue: Double = 1000000000.0,
        highMarketValue: Double = 10000000000.0,
        range: Int = 6,
        adjustTimeAfterZT: Int = 4,
        increaseLow: Double = -0.00,
        increaseHigh: Double = -0.1,
        ztCount: Int = 2,
        endTime: Int = 20220803,
        zbEnable: Boolean = false,
        minAdjustTimeAfterZT: Int = 1,
        bkList: List<String>? = null
    ) = withContext(Dispatchers.IO) {
        val stockDao = Injector.appDatabase.stockDao()
        val historyDao = Injector.appDatabase.historyStockDao()
        val stocks = stockDao.getStock(
            startTime = startMarketTime,
            endTime = endMarketTime,
            lowMarketValue = lowMarketValue,
            highMarketValue = highMarketValue
        ).run {
            var r = this
            if (bkList?.isNotEmpty() == true) {
                r = this.filter { stock ->
                    var h = false
                    run run@{
                        bkList.forEach {
                            if (stock.bk.contains(it)) {
                                h = true
                                return@run
                            }
                        }
                    }
                    return@filter h
                }

            }
            return@run r
        }
//        val stocks=listOf( stockDao.getStockByCode("002579"))
        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())
        val result = mutableListOf<StockResult>()
        stocks.forEach stockList@{
            //range天内涨停
            val historyList = historyDao.getHistoryRange(
                it.code, endDay.before(range * 2), endTime
            ).run {
                return@run subList(0, min(range, this.size))
            }

            val ztList = mutableListOf<HistoryStock>()
            historyList.forEach {
                if (zbEnable) {
                    if (it.friedPlate) {
                        ztList.add(it)
                    }
                } else {
                    if (it.ZT) {
                        ztList.add(it)
                    }
                }
            }


            Log.e(
                "股票超人", "${endDay.before(range)}${range}天内涨停${ztList.size}"
            )
            if (ztList.size < ztCount) {
                return@stockList
            }

            var s = 0
            ztList.subList(0, ztCount).forEach {
                val i = historyList.indexOf(it)
                if (i - s > adjustTimeAfterZT || i - s < minAdjustTimeAfterZT) {
                    Log.e("股票超人", "调整时间${i - s}  i=${i} s=${s}")
                    return@stockList
                }

                //在涨停内调整
                val d = max(it.closePrice, it.highest)
                val h = historyList[s].closePrice - d
                if (h > increaseHigh * d || h < increaseLow * d) {
                    Log.e("股票超人", "区间涨幅${h} $increaseHigh $increaseLow")
                    return@stockList
                }

                s = i + 1
            }

            val list = historyDao.getHistoryAfter3(it.code, endTime)
            val hasZT = list.find { it.ZT } != null


            result.add(StockResult(it, nextDayZT = hasZT))
        }
        return@withContext result
    }


    suspend fun strategy3(
        startMarketTime: Int = 19900102,
        endMarketTime: Int = 20180102,
        lowMarketValue: Double = 1000000000.0,
        highMarketValue: Double = 10000000000.0,
        //ztRange日内有涨停
        range: Int = 4,
        ztNextTurnoverRateLow: Double = 3.0,
        decreaseTurnoverRateHigh: Double = 1.5,
        //允许涨停之前newHighestRange日内存在涨停次数
        allowedZTBeforeZT: Int = 0,
        allowedZTRangeBeforeZT: Int,
        endTime: Int = 20220803
    ) = withContext(Dispatchers.IO) {
        val stockDao = Injector.appDatabase.stockDao()
        val historyDao = Injector.appDatabase.historyStockDao()
        val stocks = stockDao.getStock(
            startTime = startMarketTime,
            endTime = endMarketTime,
            lowMarketValue = lowMarketValue,
            highMarketValue = highMarketValue
        )
        //val stocks=listOf( stockDao.getStockByCode("002672"))
        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())
        val result = mutableListOf<Stock>()
        stocks.forEach stockList@{
            val histories =
                historyDao.getHistoryRange(it.code, endDay.before(range - 1), endTime)
            //找到涨停
            var ztStockHistory: HistoryStock? = null
            if (histories.size < 3) {
                return@stockList
            }
            run run@{
                histories.forEach {
                    if (it.ZT) {
                        ztStockHistory = it
                        return@run
                    }
                }
                return@stockList
            }
            if (ztStockHistory == null) {
                return@stockList
            }
            val i = histories.indexOf(ztStockHistory)
            if (i < 1) {
                return@stockList
            }
            val ztNextHistoryStock = histories[i - 1]
            //涨停之后没有放量
            if (ztNextHistoryStock.turnoverRate / ztStockHistory!!.turnoverRate < ztNextTurnoverRateLow) {
                return@stockList
            }

            var minTurnOverRateAfterZT = 1000000f
            for (j in 0 until i - 1) {
                if (histories[j].turnoverRate < minTurnOverRateAfterZT) {
                    minTurnOverRateAfterZT = histories[j].turnoverRate
                }
            }
            //涨停第二天之后没有缩量
            if (minTurnOverRateAfterZT / ztStockHistory!!.turnoverRate > decreaseTurnoverRateHigh) {
                return@stockList
            }

            val ztList = historyDao.getZTHistoryStock(
                it.code,
                SimpleDateFormat("yyyyMMdd").parse(ztStockHistory!!.date.toString())
                    .before(allowedZTRangeBeforeZT),
                ztStockHistory!!.date
            )
            if (ztList.size > allowedZTBeforeZT) {
                return@stockList
            }

            result.add(it)

        }

        return@withContext result

    }

    //底部超跌爆量
    suspend fun strategy9(
        startMarketTime: Int = 19900102,
        endMarketTime: Int = 20230102,
        lowMarketValue: Double = 10000000.0,
        highMarketValue: Double = 10000000000.0,
        range: Int = 15,
        explosionTurnoverRateRadio: Float = 3f,
        explosionDays: Int = 4,
        afterBeforeRadio: Float = 1.5f,
        afterRadio: Float = 0.8f,
        minIncreaseAfterExplosion: Float = 0.05f,
        maxIncreaseAfterExplosion: Float = 0.15f,
        sampleDays: Int = 5,
        endTime: Int = 20230715,
        bkList: List<String>? = null
    ) = withContext(Dispatchers.IO) {
        val stockDao = Injector.appDatabase.stockDao()
        val historyDao = Injector.appDatabase.historyStockDao()
        val bkStockDao = Injector.appDatabase.bkStockDao()
        val stocks = if (bkList?.isNotEmpty() == true) {
            bkList.flatMap { bkCode ->
                return@flatMap bkStockDao.getStocksByBKCode2(
                    bkCode,
                    startTime = startMarketTime,
                    endTime = endMarketTime,
                    lowMarketValue = lowMarketValue,
                    highMarketValue = highMarketValue
                )
            }.distinctBy { it.code }
        } else {
            stockDao.getStock(
                startTime = startMarketTime,
                endTime = endMarketTime,
                lowMarketValue = lowMarketValue,
                highMarketValue = highMarketValue
            )
        }


        // val stocks = listOf(stockDao.getStockByCode("600243"))
        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())
        val follows = Injector.appDatabase.followDao().getFollowStocks()


        val result = stocks.compute {
            val histories = historyDao.getHistoryRange(
                it.code,
                endDay.before(range + 30),
                endTime
            )

            if (histories.size < range + sampleDays) {
                return@compute null
            }

            var explosionIndex = -1
            var explosionBeforeTurnoverRate = 0.0
            var activeRate = 0.0
            var r = 0.0
            run {
                histories.subList(0, range).forEachIndexed here@{ index, current ->

                    var total = 0.0
                    for (i in index + 1 until index + sampleDays + 1) {
                        total += histories[i].turnoverRate
                    }
                    val averageTurnoverRate = total / sampleDays
                    if (current.turnoverRate / averageTurnoverRate > explosionTurnoverRateRadio && current.chg > 0) {
                        explosionIndex = index
                        explosionBeforeTurnoverRate = averageTurnoverRate
                        r = current.turnoverRate / averageTurnoverRate
                    } else {
                        if (explosionIndex != -1) {
                            return@run
                        }
                    }
                }
            }

            if (explosionIndex < explosionDays) {
                return@compute null
            }

            Log.e("底部超跌横盘", "${it.name} 起爆时间:${histories[explosionIndex].date} ")

            var totalTurnoverRate = 0f
            for (i in 0 until explosionIndex) {
                totalTurnoverRate += histories[i].turnoverRate
            }
            val explosionAfterTurnoverRate = totalTurnoverRate / explosionIndex



            if (explosionAfterTurnoverRate / explosionBeforeTurnoverRate < afterBeforeRadio) {
                Log.e(
                    "底部超跌横盘",
                    "${it.name}起爆后缩量, 平均换手率是之前${explosionAfterTurnoverRate / explosionBeforeTurnoverRate}倍"
                )
                return@compute null
            }


            Log.e(
                "底部超跌横盘",
                "${it.name}起爆后平均换手率是之前${explosionAfterTurnoverRate / explosionBeforeTurnoverRate}倍"
            )


            if (explosionAfterTurnoverRate / histories[explosionIndex].turnoverRate < afterRadio) {
                Log.e(
                    "底部超跌横盘",
                    "${it.name}起爆后明显缩量 ${explosionAfterTurnoverRate / histories[explosionIndex].turnoverRate}"
                )
                return@compute null
            }

            Log.e(
                "底部超跌横盘",
                "${it.name}起爆后量能是起爆点 ${explosionAfterTurnoverRate / histories[explosionIndex].turnoverRate}倍"
            )

            activeRate =
                explosionAfterTurnoverRate / histories[explosionIndex].turnoverRate + explosionAfterTurnoverRate / explosionBeforeTurnoverRate + r


            val highestPrice = historyDao.getHistoryHighestPrice(
                it.code,
                histories[explosionIndex].date,
                histories[0].date
            )
            val lowestPrice = historyDao.getHistoryLowestPrice(
                it.code,
                histories[explosionIndex].date,
                histories[0].date
            )

            if (histories[explosionIndex].closePrice * (1 + minIncreaseAfterExplosion) > lowestPrice) {

                if (histories[explosionIndex].closePrice * (1 + minIncreaseAfterExplosion) > histories[0].closePrice) {
                    Log.e(
                        "底部超跌横盘",
                        "跌幅过大--${it.name}区间涨幅${(lowestPrice - histories[explosionIndex].closePrice) * 100 / histories[explosionIndex].closePrice}%"
                    )
                    return@compute null

                }

            }



            if (histories[explosionIndex].closePrice * (1 + maxIncreaseAfterExplosion) < highestPrice) {
                Log.e(
                    "底部超跌横盘",
                    "涨幅过大--${it.name}区间涨幅${(highestPrice - histories[explosionIndex].closePrice) * 100 / histories[explosionIndex].closePrice}%"
                )
                return@compute null
            }


            val list = historyDao.getHistoryAfter3(it.code, endTime)
            val hasZT = list.find { it.ZT } != null


            //爆量之后涨停数量
            val list2 = historyDao.getHistoryRange(it.code, histories[explosionIndex].date, endTime)
            val ztCountInRange = list2.count {
                it.ZT
            }


            return@compute StockResult(
                it,
                nextDayZT = hasZT,
                follow = follows.filter { f -> f.code == it.code }.isNotEmpty(),
                ztCountInRange = ztCountInRange,
                activeRate = activeRate.toFloat()
            )

        }


        Collections.sort(result, kotlin.Comparator
        { v0, v1 ->
            if (v1.activeRate - v0.activeRate == 0.0f) {
                return@Comparator 0
            } else if (v1.activeRate - v0.activeRate > 0) {
                return@Comparator 1
            }
            return@Comparator -1
        })

        return@withContext StrategyResult(result, stocks.size)
    }


    /**
     * 均线强势
     */
    suspend fun strategy4(
        startMarketTime: Int = 19900102,
        endMarketTime: Int = 20180102,
        lowMarketValue: Double = 1000000000.0,
        highMarketValue: Double = 10000000000.0,
        range: Int = 50,
        averageDay: Int = 10,
        divergeRate: Double = 0.03,
        endTime: Int = 20220722,
        allowBelowCount: Int = 10,
        //异常放量区间
        abnormalRange: Int = 20,
        abnormalRate: Double = 3.0,
        bkList: List<String>? = null,
        stockList: List<Stock>? = null
    ) = withContext(Dispatchers.IO) {
        val stockDao = Injector.appDatabase.stockDao()
        val historyDao = Injector.appDatabase.historyStockDao()
        val bkStockDao = Injector.appDatabase.bkStockDao()

        val list = stockList?.filter {
            (it.toMarketTime in startMarketTime..endMarketTime) && (it.circulationMarketValue in lowMarketValue..highMarketValue)
        } ?: listOf()

        val stocks = if (bkList?.isNotEmpty() == true) {
            (bkList.flatMap { bkCode ->
                return@flatMap bkStockDao.getStocksByBKCode2(
                    bkCode,
                    startTime = startMarketTime,
                    endTime = endMarketTime,
                    lowMarketValue = lowMarketValue,
                    highMarketValue = highMarketValue
                )
            } + list).distinctBy { it.code }
        } else {
            list.ifEmpty {
                stockDao.getStock(
                    startTime = startMarketTime,
                    endTime = endMarketTime,
                    lowMarketValue = lowMarketValue,
                    highMarketValue = highMarketValue
                )
            }
        }


        // val stocks=listOf( stockDao.getStockByCode("301056"))
        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())
        val follows = Injector.appDatabase.followDao().getFollowStocks()
        val result = stocks.compute(4) {
            val histories = historyDao.getHistoryRange(
                it.code,
                endDay.before(averageDay * 2 + range * 2 + 10),
                endTime
            )

            if (histories.size - averageDay < range) {
                writeLog(it.code, "${it.name}历史记录不足,共${histories.size}")
                return@compute null
            }
            var s = 0
            var currentAllowBelowCount = allowBelowCount
            var ztCount = 0f

            var totalTurnoverRate = 0f
            var totalZTCount = 0f

            val totalDays = range

            var totalAverageDiverge = 0.0
            while (s < totalDays) {

                var total = 0.0
                for (i in s until s + averageDay) {
                    total += histories[i].closePrice
                }
                val averagePrice = total / averageDay


                if (histories[s].ZT) {
                    totalZTCount += 1
                    ztCount += -((s - range * 2 / 3f)).pow(2) + (range / 2f).pow(2)
                }


                totalAverageDiverge += ((histories[s].closePrice - averagePrice) / averagePrice)


                totalTurnoverRate += histories[s].turnoverRate
                if (histories[s].closePrice < averagePrice * (1.0 - divergeRate)) {
                    writeLog(
                        it.code,
                        "${histories[s].date} ${averageDay}日线 $averagePrice 除去偏差值${averagePrice * (1.0 - divergeRate)}"
                    )
                    if (currentAllowBelowCount <= 0) {
                        return@compute null
                    }
                    currentAllowBelowCount -= 1
                }
                s++
            }


            val perTotalTurnOverRate = totalTurnoverRate / totalDays


            //发现异动
            //大阳线
            val dayang = histories[0].DY
            val zt = histories[0].ZT


            //连板数
            var lianbanCount = 0
            var stop = false
            var i = 0
            while (!stop && i < histories.size) {
                if (histories[i].ZT) {
                    lianbanCount++
                    i++
                } else {
                    stop = true
                }
            }


            var highest = 0.0f

            //区间涨停数
            var ztCount2 = 0
            histories.forEach {
                if (it.closePrice > highest) {
                    highest = it.closePrice
                }
                if (it.ZT) {
                    ztCount2++
                }
            }

            //过前高信号
            val overPreHigh = histories[0].highest > highest


            //炸板信号
            val friedPlate = histories[0].friedPlate
            //连阳
            var lianyangCount = 0
            while (lianyangCount < histories.size && histories[lianyangCount].chg >= 0) {
                lianyangCount += 1
            }


            val sampleCount = range//min(abnormalRange, histories.size)
            var totalSampleZTCount = 0f


            for (i in 0 until sampleCount) {
                if (histories[i].ZT) {
                    totalSampleZTCount++
                }
            }
            val perSampleZTRate = totalSampleZTCount / sampleCount

            //取样换手率
            var totalSampleTurnOverRate = 0.0f
            for (i in range until min(range * 2, histories.size)) {
                totalSampleTurnOverRate += histories[i].turnoverRate
            }
            val perSampleTurnOverRate = totalSampleTurnOverRate / sampleCount


            var chgDeviation = 0f
            if (bkList?.size == 1) {
                val bkCode = bkList.first()
                val sFirst = histories.first()

                val sLast = histories.last()
                val sChg = (sFirst.closePrice - sLast.closePrice) / sLast.closePrice
                val bkDao = Injector.appDatabase.historyBKDao()
                val bkLast = bkDao.getHistoryByDate(bkCode, sLast.date)
                val bkFirst = bkDao.getHistoryByDate(bkCode, sFirst.date)
                if (bkLast != null && bkFirst != null) {
                    val bkChg = (bkFirst.closePrice - bkLast.closePrice) / bkLast.closePrice
                    Log.e("股票超人", it.name + " 涨跌幅${sChg} ,板块涨跌幅${bkChg}")
                    chgDeviation = sChg - bkChg
                }
            }


            val kLineSlopeRate = kLineSlopeRate3(histories.subList(0, range - 1))

            var dd = 0f
            if (perTotalTurnOverRate >= perSampleTurnOverRate) {
                dd = perTotalTurnOverRate / perSampleTurnOverRate
            } else {
                dd = -perSampleTurnOverRate / perTotalTurnOverRate
            }


            val activeRate =
                (perTotalTurnOverRate / 100 * 0.2f + dd / 100 * 0.2f + kLineSlopeRate * 0.3f + perSampleZTRate * 0.1f + chgDeviation * 0.1f + totalAverageDiverge * 0.1f) * 1000 / range

            Log.i(
                "股票超人",
                "${it.name} ${histories.first().date}止${range}内平均换手率${perTotalTurnOverRate / 100} , 换手率变化率${dd / 100} ,涨跌幅${kLineSlopeRate}  , 均线偏差${totalAverageDiverge}, 平均涨停概率${perSampleZTRate}  ,  与板块偏差$chgDeviation ，活跃度$activeRate "
            )

            //放量异动
            val highTurnOverRate =
                histories[0].turnoverRate >= perSampleTurnOverRate * abnormalRate
            //长上影
            val longUpShadow = histories[0].longUpShadow


            //触线
            var touchLine = false
            if (histories.size >= averageDay) {
                var line = 0f
                for (i in 0 until averageDay) {
                    line += histories[i].closePrice
                }

                val averagePrice = line / averageDay

                if (averagePrice >= histories[0].lowest && histories[0].closePrice >= averagePrice) {
                    //触10日线
                    touchLine = true
                }
            }

            //******牛回头***********//
            //牛回头
            val cowBack = cowBack(histories)


            val list =
                historyDao.getHistoryAfter3(it.code, endTime, howDayShowZTFlag(Injector.context))
            val hasZT = list.find { it.ZT } != null
            val first = list.firstOrNull()
            var nextDayCry = false
            if (first != null) {
                nextDayCry = first.chg < -5
            }


            return@compute StockResult(
                it,
                friedPlate,
                overPreHigh,
                dayang = if (zt) false else dayang,
                zt = zt,
                highTurnOverRate = highTurnOverRate,
                lianyangCount = lianyangCount,
                longUpShadow = longUpShadow,
                cowBack = cowBack,
                nextDayZT = hasZT,
                nextDayCry = nextDayCry,
                touchLine = touchLine,
                activeRate = activeRate.toFloat(),
                follow = follows.filter { f -> f.code == it.code }.isNotEmpty(),
                ztCountInRange = ztCount2,
                lianbanCount = lianbanCount,
                chg = histories[0].chg
            )

        }




        Collections.sort(result, kotlin.Comparator
        { v0, v1 ->
            return@Comparator v1.signalCount - v0.signalCount
        })


        return@withContext StrategyResult(result, stocks.size)
    }


    /**
     * 板块强势
     */
    suspend fun strategy7(
        range: Int = 50,
        averageDay: Int = 10,
        divergeRate: Double = 0.03,
        endTime: Int = 20220722,
        allowBelowCount: Int = 10,
    ) = withContext(Dispatchers.IO) {
        val bkDao = Injector.appDatabase.bkDao()
        val historyDao = Injector.appDatabase.historyBKDao()

        val isShowHiddenStockAndBK = isShowHiddenStockAndBK(Injector.context)
        val bks = if (isShowHiddenStockAndBK) bkDao.getAllBK() else bkDao.getVisibleBKS()

        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())!!
        val follows = Injector.appDatabase.followDao().getFollowBks()

        val hides = Injector.appDatabase.hideDao().getHides()

        //&& (it.code == "BK0454" || it.code == "BK0474")
        val result = bks.filter { !it.specialBK }
            .compute(3) {

                if (!this.isActive) {
                    return@compute null
                }

                val histories = historyDao.getHistoryRange(
                    it.code,
                    endDay.before(averageDay * 2 + range * 2 + 10),
                    endTime
                )
                if (histories.size - averageDay < range) {
                    writeLog(
                        it.code,
                        "${it.name}历史记录不不足"
                    )
                    return@compute null
                }
                var s = 0
                var currentAllowBelowCount = allowBelowCount

                var belowCount = 0

                while (s < range) {
                    var total = 0.0

                    for (i in s until s + averageDay) {
                        total += histories[i].closePrice
                    }

                    if (histories[s].closePrice < total / averageDay * (1.0 - divergeRate)) {
                        belowCount++
                        writeLog(
                            it.code,
                            "${histories[s].date} ${averageDay}日线 ${total / averageDay} 除去偏差值${total / averageDay * (1.0 - divergeRate)}"
                        )
                        if (currentAllowBelowCount <= 0) {
                            return@compute null
                        }
                        currentAllowBelowCount -= 1
                    }
                    s++
                }

                val aboveRate =
                    (range - belowCount).toFloat() / range

                val highest = historyDao.getHistoryHighestPrice(
                    it.code, endDay.before(range * 4),
                    endTime
                )
                val overPreHigh = histories[0].highest >= highest

                val dayang = histories[0].DY

//              val kd = bkKD(histories)
//              Log.e("股票超人","抗跌${it.name} ${kd}")

                //连阳
                var lianyangCount = 0
                while (lianyangCount < histories.size && histories[lianyangCount].chg >= 0) {
                    lianyangCount += 1
                }


                //计算平均换手率
                var totalTurnOverRate = 0f
                for (j in 0 until range) {
                    val element = histories[j]
                    totalTurnOverRate += element.turnoverRate
                }
                val perTurnOverRate = totalTurnOverRate / (range)


                //放量异动
                val highTurnOverRate =
                    histories[0].turnoverRate >= perTurnOverRate * 1.2

                val zt = getBKZTRate(it, endTime, range)
                val ztOne = getBKZTRate(it, endTime, 1)
                val highestLianBanCount= getHighestForBK(it,endTime)


                val sampleList = histories.subList(range, min(range * 2, histories.size))
                val acc = sampleList.fold(0f) { acc, item ->
                    return@fold acc + item.turnoverRate
                }
                val samplePerTurnoverRate = acc / sampleList.size


                val kLineSlopeRate = kLineSlopeRate4(histories.subList(0, range))

                val bkFirst = histories.first()
                val bkLast = histories[range - 1]
                val bkChg = (bkFirst.closePrice - bkLast.closePrice) / bkLast.closePrice
                val dpLast = historyDao.getHistoryByDate("000001", bkLast.date)
                val dpFirst = historyDao.getHistoryByDate("000001", bkFirst.date)
                val dpChg = (dpFirst.closePrice - dpLast.closePrice) / dpLast.closePrice

                Log.i(
                    "股票超人",
                    it.name + " ${bkLast.date}-${bkFirst.date} 板块涨跌幅$bkChg  大盘涨跌幅$dpChg 区间涨幅${bkChg - dpChg}"
                )


                var dd = 0f
                if (perTurnOverRate >= samplePerTurnoverRate) {
                    dd = perTurnOverRate / samplePerTurnoverRate
                } else {
                    dd = -samplePerTurnoverRate / perTurnOverRate
                }

                Log.i(
                    "股票超人",
                    "${it.name} ${histories[0].date}止平均换手率${perTurnOverRate / 100},换手率变化率${dd / 100},涨跌幅${kLineSlopeRate},板块涨停率${zt},大盘差值${(bkChg - dpChg)}，在均线之上的概率${aboveRate}"
                )


                //触线
                var touchLine = false
                if (histories.size >= averageDay) {
                    var line = 0f
                    for (i in 0 until averageDay) {
                        line += histories[i].closePrice
                    }

                    val averagePrice = line / averageDay

                    if (averagePrice >= histories[0].lowest && histories[0].closePrice >= averagePrice) {
                        //触10日线
                        touchLine = true
                    }
                }

                return@compute BKResult(
                    it,
                    overPreHigh = overPreHigh,
                    dayang = dayang,
                    lianyangCount = lianyangCount,
                    highTurnOverRate = highTurnOverRate,
                    activeRate = (kLineSlopeRate * 0.3f + perTurnOverRate / 100 * 0.25f + dd / 100 * 0.2f + zt.second * 0.1f + (bkChg - dpChg) * 0.1f + aboveRate * 0.05f) * 1000 / range,
                    perZTRate = ztOne.second,
                    touchLine = touchLine,
                    follow = follows.filter { f -> f.code == it.code }.isNotEmpty(),
                    hide = if (isShowHiddenStockAndBK) hides.filter { f -> f.code == it.code }
                        .isNotEmpty() else false,
                    chg = histories[0].chg,
                    ztCount = ztOne.first,
                    highestLianBanCount = highestLianBanCount
                )

            }

        Collections.sort(result, kotlin.Comparator { v0, v1 ->
            return@Comparator v1.signalCount - v0.signalCount
        })


        return@withContext result
    }


    private fun kLineSlopeRate(histories: List<HistoryBK>): Float {
        val last = histories.last()
        var count = 0
        var total = 0f

        val ss = StringBuilder()
        histories.forEachIndexed { index, historyStock ->
            if (index != histories.size && index < histories.size / 2) {
                val r =
                    (historyStock.closePrice - last.closePrice) / last.closePrice * (histories.size - index)
                val arc = atan(r)
                ss.append("${historyStock.date} ${arc} |")
                total += arc
                count++
            }
        }
        return total / count
    }

    private fun kLineSlopeRate2(histories: List<HistoryStock>): Float {
        val last = histories.last()
        var count = 0
        var total = 0f

        histories.forEachIndexed { index, historyStock ->
            if (index != histories.size && index < histories.size / 2) {
                val r = (historyStock.closePrice - last.closePrice) / (histories.size - index)
                val arc = atan(r)
                total += arc
                count++
            }
        }
        return total / count
    }


    private fun kLineSlopeRate3(histories: List<HistoryStock>): Float {
        val r =
            (histories[0].closePrice - histories.last().closePrice) / histories.last().closePrice
        return r
    }

    private fun kLineSlopeRate4(histories: List<HistoryBK>): Float {
        val r =
            (histories[0].closePrice - histories.last().closePrice) / histories.last().closePrice
        return r
    }


    //活跃度选股
    suspend fun strategy5(
        startMarketTime: Int = 19900102,
        endMarketTime: Int = 20180102,
        lowMarketValue: Double = 1000000000.0,
        highMarketValue: Double = 10000000000.0,
        range: Int = 20,
        endTime: Int = 20220818,
        bkList: List<String>? = null
    ) = withContext(Dispatchers.IO) {

        val stockDao = Injector.appDatabase.stockDao()
        val historyDao = Injector.appDatabase.historyStockDao()
        val stocks = stockDao.getStock(
            startTime = startMarketTime,
            endTime = endMarketTime,
            lowMarketValue = lowMarketValue,
            highMarketValue = highMarketValue
        ).run {
            var r = this
            if (bkList?.isNotEmpty() == true) {
                r = this.filter { stock ->
                    var h = false
                    run run@{
                        bkList.forEach {
                            if (stock.bk.contains(it)) {
                                h = true
                                return@run
                            }
                        }
                    }
                    return@filter h
                }

            }
            return@run r
        }

        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())
        val result = mutableListOf<StockResult>()

        stocks.forEach {
            val histories =
                historyDao.getHistoryRange(it.code, endDay.before(range - 1), endTime)


            var totalTurnOverRate = 0f
            var totalAmplitude = 0f


            histories.forEach {
                totalTurnOverRate += it.turnoverRate
                totalAmplitude += it.amplitude
            }

            val perTurnOverRate = totalTurnOverRate / histories.size
            val perAmplitude = totalAmplitude / histories.size


            result.add(
                StockResult(
                    it,
                    activeCount = ((perTurnOverRate * 0.8 + perAmplitude * 0.2).toInt())
                )
            )

        }
        Collections.sort(result, kotlin.Comparator
        { v0, v1 ->
            return@Comparator v1.activeCount - v0.activeCount
        })

        return@withContext result.subList(0, min(200, result.size))
    }


    suspend fun strategy6(
        startMarketTime: Int = 19900102,
        endMarketTime: Int = 20180102,
        lowMarketValue: Double = 1000000000.0,
        highMarketValue: Double = 10000000000.0,
        range: Int = 120,
        //横盘时间
        hpRange: Int = 60,
        //横盘振幅
        hpAmplitude: Double = 0.2,
        //横盘之前的下跌幅度
        dAmplitude: Double = 0.4,
        endTime: Int = 20220909,
        bkList: List<String>? = null
    ) = withContext(Dispatchers.IO) {

        val stockDao = Injector.appDatabase.stockDao()
        val historyDao = Injector.appDatabase.historyStockDao()
        val stocks = stockDao.getStock(
            startTime = startMarketTime,
            endTime = endMarketTime,
            lowMarketValue = lowMarketValue,
            highMarketValue = highMarketValue
        ).run {
            var r = this
            if (bkList?.isNotEmpty() == true) {
                r = this.filter { stock ->
                    var h = false
                    run run@{
                        bkList.forEach {
                            if (stock.bk.contains(it)) {
                                h = true
                                return@run
                            }
                        }
                    }
                    return@filter h
                }

            }
            return@run r//.filter { return@filter it.code=="002282" }
        }

        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())
        val result = mutableListOf<StockResult>()


        stocks.forEach {


            val histories = historyDao.getHistoryRange(
                it.code,
                endDay.before(range * 7 / 5),
                endTime
            )
            var lowestHistory = histories[0]
            var highestHistory = histories[0]
            var hpHighestHistory = histories[0]
            var hpLowestHistory = histories[0]
            var hpMidClosePrice = histories[0].closePrice
            var activeCount = 0
            histories.forEachIndexed { index, item ->

                if (item.closePrice > highestHistory.closePrice) {
                    highestHistory = item
                }
                if (item.closePrice < lowestHistory.closePrice) {
                    lowestHistory = item
                }

                if (index == hpRange) {
                    hpHighestHistory = highestHistory
                    hpLowestHistory = lowestHistory
                    hpMidClosePrice = (hpHighestHistory.closePrice + hpLowestHistory.closePrice) / 2
                }

                if (index <= hpRange) {
                    //横盘震荡
                    if (highestHistory.closePrice - lowestHistory.closePrice > lowestHistory.closePrice * hpAmplitude) {
                        return@forEach
                    }
                    //横盘异动检查
                    if (item.friedPlate) {
                        activeCount++
                    }
                    if (item.ZT) {
                        activeCount++
                    }
                    if (item.longUpShadow) {
                        activeCount++
                    }

                } else {
                    //下跌趋势
                    if (highestHistory.closePrice - hpMidClosePrice > hpMidClosePrice * dAmplitude) {
                        //长上影
                        val longUpShadow = histories[0].longUpShadow
                        //连阳
                        var lianyangCount = 0
                        while (lianyangCount < histories.size && histories[lianyangCount].chg >= 0) {
                            lianyangCount += 1
                        }
                        //过前高
                        val overPreHigh = histories[0].highest > hpHighestHistory.closePrice

                        //炸板信号
                        val friedPlate = histories[0].friedPlate

                        //大阳线
                        val dayang = histories[0].DY
                        val zt = histories[0].ZT

                        result.add(
                            StockResult(
                                it,
                                activeCount = activeCount,
                                longUpShadow = longUpShadow,
                                lianyangCount = lianyangCount,
                                overPreHigh = overPreHigh,
                                friedPlate = friedPlate,
                                dayang = if (zt) false else dayang,
                                zt = zt,
                            )
                        )
                        return@forEach
                    }
                }
            }
            writeLog(
                it.code,
                "Strategy 6  最高-最低" + (highestHistory.closePrice - hpMidClosePrice) + "  横盘均值*跌幅" + hpMidClosePrice * dAmplitude
            )
        }

        Collections.sort(result, kotlin.Comparator { v0, v1 ->
            return@Comparator v1.activeCount + v1.signalCount - v0.activeCount - v0.signalCount
        })

        return@withContext result
    }

}

val StockResult.signalCount: Int
    get() {
        var c0 = 0

        if (zt) {
            c0 += 5
        }

        if (dayang) {
            c0 += 4
        }

        if (highTurnOverRate) {
            c0 += 4
        }

        if (friedPlate) {
            c0 += 3
        }


        if (overPreHigh) {
            c0 += 3
        }

        if (longUpShadow) {
            c0 += 2
        }


        if (lianyangCount >= 3) {
            c0 += 1
        }
        if (lianyangCount >= 6) {
            c0 += 1
        }

        if (lianyangCount >= 9) {
            c0 += 1
        }


        if (touchLine) {
            c0 += 1
        }

        return c0
    }

data class BKResult(
    val bk: BK,
    val overPreHigh: Boolean = false,
    var kd: Float = 0f,
    val dayang: Boolean = false,
    val highTurnOverRate: Boolean = false,
    val lianyangCount: Int = 0,
    //活跃度
    var activeRate: Float = 0f,
    //板块涨停率
    var perZTRate: Float = 0f,


    val touchLine: Boolean = false,
    var follow: Boolean = false,
    var hide: Boolean = false,
    val chg: Float,
    val ztCount: Int,
    val highestLianBanCount:Int
)

val BKResult.signalCount: Int
    get() {
        var c0 = 0
        if (dayang) {
            c0 += 3
        }
        if (highTurnOverRate) {
            c0 += 3
        }
        if (overPreHigh) {
            c0 += 2
        }

        if (lianyangCount > 3) {
            c0 += 1
        }
        if (lianyangCount >= 6) {
            c0 += 1
        }
        if (lianyangCount >= 8) {
            c0 += 1
        }

        if (touchLine) {
            c0 += 1
        }

        if (perZTRate > 0.03) {
            c0 += 1
        }

        if (perZTRate > 0.06) {
            c0 += 1
        }

        if (perZTRate > 0.1) {
            c0 += 1
        }

        return c0
    }

data class StockResult(
    val stock: Stock,
    val friedPlate: Boolean = false,
    val overPreHigh: Boolean = false,
    val dayang: Boolean = false,
    val highTurnOverRate: Boolean = false,
    val lianyangCount: Int = 0,
    val longUpShadow: Boolean = false,

    val zt: Boolean = false,
    val touchLine: Boolean = false,
    //异动
    var activeCount: Int = 0,
    //牛回头
    var cowBack: Boolean = false,
    var kd: Int = 0,
    //涨停后调整强势度
    val hyAfterZT: Int = 0,
    //之后有涨停
    val nextDayZT: Boolean = false,
    val nextDayCry: Boolean = false,
    //活跃度
    var activeRate: Float = 0f,
    var follow: Boolean = false,
    var ztCountInRange: Int = 0,
    val lianbanCount: Int = 0,
    val chg: Float = 0f
)

data class StrategyResult(
    val stockResults: List<StockResult>,
    val total: Int,

    )

fun BKResult.toFormatText(): String {

    val sb = StringBuilder()

    if (overPreHigh) {
        sb.append("过前高 ")
    }

    if (highTurnOverRate) {
        sb.append("放量 ")
    }

    if (lianyangCount > 3) {
        sb.append("${lianyangCount}连阳 ")
    }

    if (dayang) {
        sb.append("大阳 ")
    }

    if (perZTRate > 0.03) {
        sb.append("涨停率${(perZTRate * 100).roundToInt()} ")
    }

//    if (touchLine10 || touchLine20) {
//        sb.append("触")
//        if (touchLine10)
//            sb.append(10).append(',')
//        if (touchLine20)
//            sb.append(20).append(',')
//
//        sb.deleteCharAt(sb.length - 1)
//        sb.append("日线 ")
//    }

    if (touchLine) {
        sb.append("触线 ")
    }


//    if (activeRate > 1) {
//        sb.append("${activeRate.toInt()} ")
//    }

    return sb.toString()
}

fun StockResult.toFormatText(): String {
    val sb = StringBuilder()

    if (zt) {
        sb.append("涨停 ")
    }

    if (dayang) {
        sb.append("大阳 ")
    }

    if (friedPlate) {
        sb.append("炸板 ")
    }

    if (highTurnOverRate) {
        sb.append("放量 ")
    }

    if (overPreHigh) {
        sb.append("过前高 ")
    }

    if (longUpShadow) {
        sb.append("长上影 ")
    }

    if (lianyangCount > 3) {
        sb.append("${lianyangCount}连阳 ")
    }



    if (activeCount > 0) {
        sb.append("H$activeCount ")
    }

    if (touchLine) {
        sb.append("触线  ")
    }

//    if (activeRate > 2) {
//        val v = activeRate.toInt()
//        sb.append("${v} ")
//    }

    if (hyAfterZT > 2) {
        sb.append("$hyAfterZT ")
    }

    return sb.toString()
}


class UpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val d = Injector.appDatabase.stockDao()
        val h = Injector.appDatabase.historyStockDao()
        StockRepo.getRealTimeStocks()
        StockRepo.getRealTimeBKs()
        return Result.success()
    }

}





