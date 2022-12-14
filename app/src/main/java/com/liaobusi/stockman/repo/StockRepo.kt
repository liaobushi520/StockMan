package com.liaobusi.stockman.repo

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.liaobusi.stockman.Injector
import com.liaobusi.stockman.api.StockService
import com.liaobusi.stockman.before
import com.liaobusi.stockman.compute
import com.liaobusi.stockman.db.*
import com.liaobusi.stockman.writeLog
import kotlinx.coroutines.*

import java.lang.Integer.min
import java.lang.Math.max
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.atan
import kotlin.math.pow


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
    //??????????????????
    val abnormalRange: Int = 20,
    val abnormalRate: Double = 3.0,
    val bkList: List<String>? = null
)

//????????????
data class Strategy7Param(
    val range: Int,
    val averageDay: Int = 10,
    val divergeRate: Double = 0.03,
    val endTime: Int = 20220722,
    val allowBelowCount: Int = 10,
)

//????????????
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

    //????????????
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


    //??????
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

    //?????????
    private fun cowBack(histories: List<HistoryStock>): Boolean {

        //?????????
        var cowBack = false
        var lowestClosePrice = Float.MAX_VALUE
        var zdIndex = -1
        val step = 10
        //??????step???????????????
        kotlin.run run@{
            for (i in 0 until min(step, histories.size)) {
                if (histories[i].closePrice <= lowestClosePrice) {
                    lowestClosePrice = histories[i].closePrice
                    zdIndex = i
                }
            }
        }
        if (zdIndex >= 0) {
            //??????????????????
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

            //??????????????????
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
                //??????????????????
                if (histories[0].closePrice >= histories[zdIndex].closePrice * 1.02 && histories[0].closePrice < histories[zdIndex].closePrice * 1.10 && zdIndex >= 2) {
                    return true
                }
            }
        }


        return false
    }


    suspend fun getHistoryStocks(startDate: Int, endDate: Int) = withContext(Dispatchers.IO) {
        //                                 ?????????    ?????????     ??????    ?????????   ?????????   ?????????                          ?????????
        //[{"status":0,"hq":[["2022-08-02","13.31","13.82","1.26","10.03%","13.02","13.82","3294343","444015.31","20.89%"]],"code":"cn_000547"}]
        val dao = Injector.appDatabase.stockDao()
        val dao1 = Injector.appDatabase.historyStockDao()
        // val allStock = dao.getAllStock()
        val allStock = dao.getAllStockByMarketTime(startDate).filter {
            return@filter true
        }
        var start = 0
        val step = 50
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
                            Log.e("????????????", "${c} ??????????????????")
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
                        "??????${(end * 100f / allStock.size).toInt()}/100",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                start = end + 1
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
                Log.i("????????????", "????????????${bk.name}-${bk.code}??????????????????,???${list.size}???")
                return@compute list
            }
            return@compute null
        }
        bkStockDao.insertAll(l.flatten())
    }


    private fun getBKZTRate(bk: BK, date: Int, days: Int = 5): Float {
        val historyStockDao = Injector.appDatabase.historyStockDao()
        val bkStockDao = Injector.appDatabase.bkStockDao()
        var ztTotal = 0f
        val stocks = bkStockDao.getStocksByBKCode(bk.code)
        stocks.forEach {
            val list = historyStockDao.getHistoryAfter2(it.code, date, days)
            val c = list.count { it.ZT }
            ztTotal += c
            Log.i("????????????", "??????${bk.name}-${bk.code} ??????${it.name}-${it.code}???${days}??????${c}?????????")

        }
        Log.i(
            "????????????",
            "??????${bk.name}-${bk.code}--${date}--???${days}??????${ztTotal}???????????????????????????????????????${ztTotal * 100f / (days * stocks.size)}/100"
        )
        return ztTotal * 100 / (days * stocks.size)
    }


    suspend fun getHistoryBks() = withContext(Dispatchers.IO) {

        val bkDao = Injector.appDatabase.bkDao()
        val historyDao = Injector.appDatabase.historyBKDao()
        val bks = bkDao.getAllBK()

        bks.forEach { bk ->
            Log.i("StockMan", "????????????${bk.name}????????????")
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
                    name = "????????????",
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

            Log.i("????????????", "???????????????????????????${bks.size}")
            dao.insertAll(bks)
            Log.i("????????????", "???????????????????????????")
            dao1.insertHistory(bkHistories)
            Log.i("????????????", "?????????????????????????????????")

        } catch (e: Throwable) {
            e.printStackTrace()
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
                Log.i("????????????", "???????????????????????????${list.size}")
                val dao = Injector.appDatabase.stockDao()
                dao.insertAll(list)
                Log.i("????????????", "???????????????????????????")

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
                Log.i("????????????", "?????????????????????????????????")

            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }


    //????????????
    suspend fun strategy1(
        startMarketTime: Int = 19900102,
        endMarketTime: Int = 20180102,
        lowMarketValue: Double = 1000000000.0,
        highMarketValue: Double = 10000000000.0,
        //ztRange???????????????
        ztRange: Int = 8,
        //???????????????????????????
        adjustTimeAfterZT: Int = 4,
        //???????????????????????????
        afterZTStockPriceLowRate: Double = 0.97,
        afterZTStockPriceHighRate: Double = 1.03,
        //????????????????????????????????????
        amplitudeAfterZT: Double = 0.1,
        newHighestRangeBeforeZT: Int = 60,
        //??????????????????newHighestRange????????????????????????
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
            }
        } else {
            stockDao.getStock(
                startTime = startMarketTime,
                endTime = endMarketTime,
                lowMarketValue = lowMarketValue,
                highMarketValue = highMarketValue
            )
        }


//        val stocks=listOf( stockDao.getStockByCode("300898"))
        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())
        val result = stocks.compute {
            val histories =
                historyDao.getHistoryRange(it.code, endDay.before(ztRange), endTime)
            //????????????
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
                writeLog(it.code, "???${ztRange}?????????????????????")
                return@compute null
            }

            //??????????????????????????????
            val i = histories.indexOf(ztStockHistory)
            if (i < adjustTimeAfterZT) {
                writeLog(it.code, "???????????????????????????  $i < $adjustTimeAfterZT")
                return@compute null
            }


            //????????????????????????????????????????????????*afterZTStockPriceRate
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
                            "${it.date} ?????????${it.closePrice}  ????????????${ztStockHistory!!.closePrice * afterZTStockPriceLowRate} ??????${ztStockHistory!!.closePrice * afterZTStockPriceHighRate}  "
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


            //???????????????????????????????????????????????????????????????
            val realAmplitudeAfterZT =
                ((highestPriceAfterZT - lowestPriceAfterZT) / ztStockHistory!!.closePrice)
            if (realAmplitudeAfterZT > amplitudeAfterZT) {
                writeLog(it.code, "????????????  ${realAmplitudeAfterZT} > ${amplitudeAfterZT}")
                return@compute null
            }

            val ztDate = SimpleDateFormat("yyyyMMdd").parse(ztStockHistory!!.date.toString())

            //?????????????????????newHighestRange??????
            val highestPriceBeforeZT = historyDao.getHistoryHighestPrice(
                it.code,
                ztDate.before(newHighestRangeBeforeZT),
                ztStockHistory!!.date
            )

            if (highestPriceAfterZT < highestPriceBeforeZT) {
                writeLog(it.code, "????????????????????????  $highestPriceAfterZT < $highestPriceBeforeZT")
                return@compute null
            }
            val ztList = historyDao.getZTHistoryStock(
                it.code,
                ztDate.before(newHighestRangeBeforeZT),
                ztStockHistory!!.date
            )
            if (ztList.size > allowedZTBeforeZT) {
                writeLog(it.code, "???????????????????????????????????????  ${ztList.size} < $allowedZTBeforeZT")
                return@compute null
            }


            //??????
            //????????????
            val dayang = histories[0].DY
            //??????
            val friedPlate = histories[0].friedPlate

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

            //??????
            val highTurnOverRate = histories[0].turnoverRate > perTurnOverRate * 2


            //?????????
            val longUpShadow = histories[0].longUpShadow

            //??????
            var lianyangCount = 0
            while (lianyangCount < histories.size && histories[lianyangCount].chg >= 0) {
                lianyangCount += 1
            }

            //??????
            var touchLine10 = false
            var touchLine20 = false
            if (histories.size >= 20) {
                var line10 = 0f
                var line20 = 0f
                for (i in 0 until 20) {
                    if (i <= 9) {
                        line10 += histories[i].closePrice
                    }
                    if (i <= 19) {
                        line20 += histories[i].closePrice
                    }
                }
                if (line10 / 10 >= histories[0].lowest && histories[0].closePrice > line10 / 10) {
                    //???10??????
                    touchLine10 = true
                }
                if (line20 / 20 >= histories[0].lowest && histories[0].closePrice > line20 / 20) {
                    //???20??????
                    touchLine20 = true
                }
            }

            //?????????
            val cowBack = cowBack(histories)
            //??????
            val kd = kd(histories)

            val list = historyDao.getHistoryBefore2(it.code, endTime, 10)
            val hasZT = list.find { it.ZT } != null


            return@compute StockResult(
                it,
                highTurnOverRate = highTurnOverRate,
                friedPlate = friedPlate,
                dayang = dayang,
                overPreHigh = overPreHigh,
                longUpShadow = longUpShadow,
                lianyangCount = lianyangCount,
                touchLine10 = touchLine10,
                touchLine20 = touchLine20,
                cowBack = cowBack,
                kd = kd,
                nextDayZT = hasZT
            )


        }
        Collections.sort(result, kotlin.Comparator
        { v0, v1 ->
            return@Comparator v1.signalCount - v0.signalCount
        })

        return@withContext result
    }


    //????????????
    suspend fun strategy8(
        startMarketTime: Int = 19900102,
        endMarketTime: Int = 20180102,
        lowMarketValue: Double = 1000000000.0,
        highMarketValue: Double = 10000000000.0,
        //ztRange????????????????????????
        ztRange: Int = 10,
        //???????????????????????????
        adjustTimeAfterZT: Int = 4,
        endTime: Int = 20221106,
        averageDay: Int = 5,
        divergeRate: Double = 0.02,
        allowBelowCount: Int = 1,
        bkList: List<String>? = null,
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

//        val stocks=listOf( stockDao.getStockByCode("002771"))
        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())
        val result = mutableListOf<StockResult>()
        stocks.forEach stockList@{
            val histories =
                historyDao.getHistoryRange(it.code, endDay.before(ztRange * 2), endTime)

            if (histories.size < ztRange) {
                return@stockList
            }

            //????????????
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

            if (ztStockHistory == null) {
                writeLog(it.code, "???${ztRange}?????????????????????")
                return@stockList
            }
            val ztIndex = histories.indexOf(ztStockHistory)

            if (ztIndex < adjustTimeAfterZT) {
                writeLog(it.code, "???????????????????????????  $ztIndex < $adjustTimeAfterZT")
                return@stockList
            }

            var totalTurnoverRate = 0f
            var totalAmplitude = 0f
            //????????????????????????
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
                        "${histories[s].date} ${averageDay}?????? $averagePrice ???????????????${averagePrice * (1.0 - divergeRate)}"
                    )
                    belowLine5Count++

                    if (currentAllowBelowCount <= 0) {
                        return@stockList
                    }
                    currentAllowBelowCount -= 1
                }
                s++
            }

            val list = historyDao.getHistoryBefore2(it.code, endTime)
            val hasZT = list.find { it.ZT } != null

            val h =
                (histories[0].closePrice - ztStockHistory!!.closePrice) * 100 / ztStockHistory!!.closePrice

            Log.e(
                "????????????",
                "${it.name}--${ztIndex}??????????????????${totalTurnoverRate / ztIndex} ????????????${totalAmplitude / ztIndex}  ????????????${totalAverageDiverge} ${h}"
            )


            result.add(
                StockResult(
                    it,
                    hyAfterZT = ((totalTurnoverRate * 0.4 / ztIndex) + totalAmplitude * 0.1 / ztIndex + 0.2 * h + totalAverageDiverge * 0.3 * 50).toInt(),
                    nextDayZT = hasZT
                )
            )
        }

        Collections.sort(result, kotlin.Comparator { v0, v1 ->
            return@Comparator v1.hyAfterZT - v0.hyAfterZT
        })

        return@withContext result
    }


    //????????????
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
            //range????????????
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
                "????????????", "${endDay.before(range)}${range}????????????${ztList.size}"
            )
            if (ztList.size < ztCount) {
                return@stockList
            }

            var s = 0
            ztList.subList(0, ztCount).forEach {
                val i = historyList.indexOf(it)
                if (i - s > adjustTimeAfterZT || i - s < minAdjustTimeAfterZT) {
                    Log.e("????????????", "????????????${i - s}  i=${i} s=${s}")
                    return@stockList
                }

                //??????????????????
                val d = max(it.closePrice, it.highest)
                val h = historyList[s].closePrice - d
                if (h > increaseHigh * d || h < increaseLow * d) {
                    Log.e("????????????", "????????????${h} $increaseHigh $increaseLow")
                    return@stockList
                }

                s = i + 1
            }

            val list = historyDao.getHistoryBefore2(it.code, endTime)
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
        //ztRange???????????????
        range: Int = 4,
        ztNextTurnoverRateLow: Double = 3.0,
        decreaseTurnoverRateHigh: Double = 1.5,
        //??????????????????newHighestRange????????????????????????
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
            //????????????
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
            //????????????????????????
            if (ztNextHistoryStock.turnoverRate / ztStockHistory!!.turnoverRate < ztNextTurnoverRateLow) {
                return@stockList
            }

            var minTurnOverRateAfterZT = 1000000f
            for (j in 0 until i - 1) {
                if (histories[j].turnoverRate < minTurnOverRateAfterZT) {
                    minTurnOverRateAfterZT = histories[j].turnoverRate
                }
            }
            //?????????????????????????????????
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

    /**
     * ????????????
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
        //??????????????????
        abnormalRange: Int = 20,
        abnormalRate: Double = 3.0,
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
            }
        } else {
            stockDao.getStock(
                startTime = startMarketTime,
                endTime = endMarketTime,
                lowMarketValue = lowMarketValue,
                highMarketValue = highMarketValue
            )
        }


//       val stocks=listOf( stockDao.getStockByCode("000088"))
        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())
        val result = stocks.compute {
            val histories = historyDao.getHistoryRange(
                it.code,
                endDay.before(averageDay * 2 + range - 1),
                endTime
            )
            if (histories.size - averageDay <= 0) {
                return@compute null
            }
            var s = 0
            var currentAllowBelowCount = allowBelowCount
            var highest = 0.0f
            var ztCount = 0f

            var totalTurnoverRate = 0f
            var totalZTCount = 0f

            val totalDays = histories.size - averageDay + 1

            var totalAverageDiverge = 0.0
            while (s < totalDays) {
                var total = 0.0
                if (highest < histories[s].closePrice) {
                    highest = histories[s].closePrice
                }
                for (i in s until s + averageDay) {
                    total += histories[i].closePrice
                }

                if (histories[s].ZT) {
                    totalZTCount += 1
                    ztCount += -((s - range * 2 / 3f)).pow(2) + (range / 2f).pow(2)
                }

                val averagePrice = total / averageDay

                if (s < min(abnormalRange, histories.size)) {
                    totalAverageDiverge += ((histories[s].closePrice - averagePrice) / averagePrice)
                }


                totalTurnoverRate += histories[s].turnoverRate
                if (histories[s].closePrice < averagePrice * (1.0 - divergeRate)) {
                    writeLog(
                        it.code,
                        "${histories[s].date} ${averageDay}?????? $averagePrice ???????????????${averagePrice * (1.0 - divergeRate)}"
                    )
                    if (currentAllowBelowCount <= 0) {
                        return@compute null
                    }
                    currentAllowBelowCount -= 1
                }
                s++
            }


            val perTotalTurnOverRate = totalTurnoverRate / totalDays


            //????????????
            //?????????
            val dayang = histories[0].DY
            //???????????????
            val overPreHigh = histories[0].highest > highest

            //????????????
            val friedPlate = histories[0].friedPlate
            //??????
            var lianyangCount = 0
            while (lianyangCount < histories.size && histories[lianyangCount].chg >= 0) {
                lianyangCount += 1
            }

            val sampleCount = min(abnormalRange, histories.size)
            var totalSampleTurnOverRate = 0.0f
            var totalSampleZTCount = 0f
            for (i in 0 until sampleCount) {
                totalSampleTurnOverRate += histories[i].turnoverRate
                if (histories[i].ZT) {
                    totalSampleZTCount++
                }
            }
            val perSampleZTRate = totalSampleZTCount / sampleCount
            //?????????????????????
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
                val bkChg = (bkFirst.closePrice - bkLast.closePrice) / bkLast.closePrice
                chgDeviation = sChg - bkChg
            }


            val kLineSlopeRate = kLineSlopeRate2(histories)
            val activeRate =
                (perSampleTurnOverRate * 0.2f / 10 + perSampleTurnOverRate * 0.2f / perTotalTurnOverRate + perSampleZTRate * 0.2f + kLineSlopeRate * 0.1f + chgDeviation * 0.1f + totalAverageDiverge / sampleCount * 10 * 0.2f) * 10

            Log.i(
                "????????????",
                "${it.name}${sampleCount}??????????????????${perSampleTurnOverRate},????????????${totalAverageDiverge / sampleCount},??????????????????${perSampleZTRate},???????????????${perSampleTurnOverRate / perTotalTurnOverRate},k?????????${kLineSlopeRate}  ???????????????$chgDeviation $activeRate  ${histories.last().date}"
            )

            //????????????
            val highTurnOverRate =
                histories[0].turnoverRate >= perSampleTurnOverRate * abnormalRate
            //?????????
            val longUpShadow = histories[0].longUpShadow

            //******?????????***********//
            //?????????
            val cowBack = cowBack(histories)


            val list = historyDao.getHistoryBefore2(it.code, endTime)
            val hasZT = list.find { it.ZT } != null

            return@compute StockResult(
                it,
                friedPlate,
                overPreHigh,
                dayang = dayang,
                highTurnOverRate = highTurnOverRate,
                lianyangCount = lianyangCount,
                longUpShadow = longUpShadow,
                cowBack = cowBack,
                nextDayZT = hasZT,
                activeRate = activeRate.toFloat()
            )

        }

        Collections.sort(result, kotlin.Comparator
        { v0, v1 ->
            return@Comparator v1.signalCount - v0.signalCount
        })

        return@withContext StrategyResult(result, stocks.size)
    }


    /**
     * ????????????
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
        val bks = bkDao.getAllBK()
        val endDay = SimpleDateFormat("yyyyMMdd").parse(endTime.toString())!!

        //&& (it.code == "BK0454" || it.code == "BK0474")
        val result = bks.filter { !it.specialBK }
            .compute(4) {

                val histories = historyDao.getHistoryRange(
                    it.code,
                    endDay.before(averageDay * 2 + range - 1),
                    endTime
                )
                if (histories.size - averageDay <= 0) {
                    return@compute null
                }
                var s = 0
                var currentAllowBelowCount = allowBelowCount

                var belowCount = 0

                while (s <= histories.size - averageDay) {
                    var total = 0.0

                    for (i in s until s + averageDay) {
                        total += histories[i].closePrice
                    }

                    if (histories[s].closePrice < total / averageDay * (1.0 - divergeRate)) {
                        belowCount++
                        writeLog(
                            it.code,
                            "${histories[s].date} ${averageDay}?????? ${total / averageDay} ???????????????${total / averageDay * (1.0 - divergeRate)}"
                        )
                        if (currentAllowBelowCount <= 0) {
                            return@compute null
                        }
                        currentAllowBelowCount -= 1
                    }
                    s++
                }

                val belowRate =
                    (histories.size - averageDay - belowCount + 1f) / (histories.size - averageDay + 1f)

                val highest = historyDao.getHistoryHighestPrice(
                    it.code, endDay.before(range * 4),
                    endTime
                )
                val overPreHigh = histories[0].highest >= highest

                val dayang = histories[0].DY

//              val kd = bkKD(histories)
//              Log.e("????????????","??????${it.name} ${kd}")

                //??????
                var lianyangCount = 0
                while (lianyangCount < histories.size && histories[lianyangCount].chg >= 0) {
                    lianyangCount += 1
                }

                var totalTurnOverRate = 0f
                for (element in histories) {
                    totalTurnOverRate += element.turnoverRate
                }
                //?????????????????????
                val perTurnOverRate = totalTurnOverRate / (histories.size)
                //????????????
                val highTurnOverRate =
                    histories[0].turnoverRate >= perTurnOverRate * 1.3

                val zt = getBKZTRate(it, endTime)

                val ztOne = getBKZTRate(it, endTime, 1)

                val sampleCount = kotlin.math.max(1, averageDay / 2)
                val acc = histories.subList(0, sampleCount).fold(0f) { acc, item ->
                    return@fold acc + item.turnoverRate
                }

                val kLineSlopeRate = kLineSlopeRate(histories)

                val bkFirst = histories.first()
                val bkLast = histories.last()
                val bkChg = (bkFirst.closePrice - bkLast.closePrice) / bkLast.closePrice
                val dpLast = historyDao.getHistoryByDate("000001", bkLast.date)
                val dpFirst = historyDao.getHistoryByDate("000001", bkFirst.date)
                val dpChg = (dpFirst.closePrice - dpLast.closePrice) / dpLast.closePrice

                Log.i(
                    "????????????",
                    it.name + " ${bkLast.date}-${bkFirst.date} ${bkChg}  ${dpChg} ????????????${bkChg - dpChg}"
                )
                Log.i(
                    "????????????",
                    it.name + " ????????????????????????${belowRate}    ?????????${(acc / (perTurnOverRate * sampleCount))}  K?????????${kLineSlopeRate} ???????????????${zt}  ????????????${(bkChg - dpChg) * 10}  " +
                            " ${histories.size}??????????????????${perTurnOverRate}  ???${sampleCount}??????????????????" + (acc / sampleCount)
                )


                //??????
                var touchLine10 = false
                var touchLine20 = false
                if (histories.size >= 20) {
                    var line10 = 0f
                    var line20 = 0f
                    for (i in 0 until 20) {
                        if (i <= 9) {
                            line10 += histories[i].closePrice
                        }
                        line20 += histories[i].closePrice
                    }

                    val line10Price = line10 / 10
                    val line20Price = line20 / 10
                    if (line10 / 10 >= histories[0].lowest && histories[0].closePrice > line10Price) {
                        //???10??????
                        touchLine10 = true
                    }
                    if (line20 / 20 >= histories[0].lowest && histories[0].closePrice > line20Price) {
                        //???20??????
                        touchLine20 = true
                    }
                }

                return@compute BKResult(
                    it,
                    overPreHigh = overPreHigh,
                    dayang = dayang,
                    lianyangCount = lianyangCount,
                    highTurnOverRate = highTurnOverRate,
                    activeRate = (kLineSlopeRate * 0.1f + (acc / (perTurnOverRate * sampleCount)) * 0.3f + zt / 10 * 0.2f + (bkChg - dpChg) * 10 * 0.2f + belowRate * 0.2f) * 10,
                    perZTRate = ztOne,
                    touchLine10 = touchLine10,
                    touchLine20 = touchLine20
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


    //???????????????
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
        //????????????
        hpRange: Int = 60,
        //????????????
        hpAmplitude: Double = 0.2,
        //???????????????????????????
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
                    //????????????
                    if (highestHistory.closePrice - lowestHistory.closePrice > lowestHistory.closePrice * hpAmplitude) {
                        return@forEach
                    }
                    //??????????????????
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
                    //????????????
                    if (highestHistory.closePrice - hpMidClosePrice > hpMidClosePrice * dAmplitude) {
                        //?????????
                        val longUpShadow = histories[0].longUpShadow
                        //??????
                        var lianyangCount = 0
                        while (lianyangCount < histories.size && histories[lianyangCount].chg >= 0) {
                            lianyangCount += 1
                        }
                        //?????????
                        val overPreHigh = histories[0].highest > hpHighestHistory.closePrice

                        //????????????
                        val friedPlate = histories[0].friedPlate

                        //?????????
                        val dayang = histories[0].DY

                        result.add(
                            StockResult(
                                it,
                                activeCount = activeCount,
                                longUpShadow = longUpShadow,
                                lianyangCount = lianyangCount,
                                overPreHigh = overPreHigh,
                                friedPlate = friedPlate,
                                dayang = dayang
                            )
                        )
                        return@forEach
                    }
                }
            }
            writeLog(
                it.code,
                "Strategy 6  ??????-??????" + (highestHistory.closePrice - hpMidClosePrice) + "  ????????????*??????" + hpMidClosePrice * dAmplitude
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
        if (dayang) {
            c0 += 2
        }
        if (highTurnOverRate) {
            c0 += 2
        }
        if (overPreHigh) {
            c0 += 1
        }
        if (lianyangCount >=3) {
            c0 += 1
        }
        if (lianyangCount >=6) {
            c0 += 1
        }

        if (lianyangCount >=9) {
            c0 += 1
        }

        if (friedPlate) {
            c0 += 2
        }
        if (longUpShadow) {
            c0 += 1
        }

        if (touchLine10 || touchLine20) {
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
    //?????????
    var activeRate: Float = 0f,
    //???????????????
    var perZTRate: Float = 0f,
    val touchLine10: Boolean = false,
    val touchLine20: Boolean = false,
)

val BKResult.signalCount: Int
    get() {
        var c0 = 0
        if (dayang) {
            c0 += 2
        }
        if (highTurnOverRate) {
            c0 += 2
        }
        if (overPreHigh) {
            c0 += 1
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

        if (touchLine10 || touchLine20) {
            c0 += 1
        }

        if (perZTRate > 3) {
            c0 += 1
        }

        if (perZTRate > 6) {
            c0 += 1
        }

        if (perZTRate > 10) {
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
    val touchLine5: Boolean = false,
    val touchLine10: Boolean = false,
    val touchLine20: Boolean = false,
    //??????
    var activeCount: Int = 0,
    //?????????
    var cowBack: Boolean = false,
    var kd: Int = 0,
    //????????????????????????
    val hyAfterZT: Int = 0,
    //???????????????
    val nextDayZT: Boolean = false,
    //?????????
    var activeRate: Float = 0f
)

data class StrategyResult(val stockResults: List<StockResult>, val total: Int)

fun BKResult.toFormatText(): String {

    val sb = StringBuilder()

    if (overPreHigh) {
        sb.append("????????? ")
    }

    if (highTurnOverRate) {
        sb.append("?????? ")
    }

    if (lianyangCount > 3) {
        sb.append("${lianyangCount}?????? ")
    }

    if (dayang) {
        sb.append("?????? ")
    }

    if (perZTRate > 3) {
        sb.append("?????????${perZTRate.toInt()} ")
    }

    if (touchLine10 || touchLine20) {
        sb.append("???")
        if (touchLine10)
            sb.append(10).append(',')
        if (touchLine20)
            sb.append(20).append(',')

        sb.deleteCharAt(sb.length - 1)
        sb.append("?????? ")
    }


//    if (activeRate > 1) {
//        sb.append("${activeRate.toInt()} ")
//    }

    return sb.toString()
}

fun StockResult.toFormatText(): String {
    val sb = StringBuilder()

    if (longUpShadow) {
        sb.append("????????? ")
    }

    if (overPreHigh) {
        sb.append("????????? ")
    }

    if (friedPlate) {
        sb.append("?????? ")
    }

    if (dayang) {
        sb.append("?????? ")
    }

    if (highTurnOverRate) {
        sb.append("?????? ")
    }

    if (lianyangCount > 3) {
        sb.append("${lianyangCount}?????? ")
    }

    if (touchLine10 || touchLine20) {
        sb.append("???")
//        if (touchLine5)
//            sb.append(5).append(',')
        if (touchLine10)
            sb.append(10).append(',')
        if (touchLine20)
            sb.append(20).append(',')
        sb.deleteCharAt(sb.length - 1)
        sb.append("?????? ")
    }

    if (activeCount > 0) {
        sb.append("H$activeCount ")
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





