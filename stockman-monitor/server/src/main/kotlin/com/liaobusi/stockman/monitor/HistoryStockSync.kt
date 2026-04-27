package com.liaobusi.stockman.monitor

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.QueryMap
import retrofit2.http.Url
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class HistoryStockSync(private val database: StockDatabase) {
    private val api = Retrofit.Builder()
        .baseUrl("https://example.com/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .build()
        .create(HistoryKLineApi::class.java)

    suspend fun syncRecentKLines(
        limit: Int = DEFAULT_LIMIT,
        stockLimit: Int? = null,
        codes: List<String> = emptyList()
    ): HistorySyncStatus = withContext(Dispatchers.IO) {
        val stocks = if (codes.isNotEmpty()) database.stockRefsByCodes(codes) else database.stockRefs(stockLimit)
        val semaphore = Semaphore(CONCURRENCY)
        val histories = coroutineScope {
            stocks.map { stock ->
                async {
                    semaphore.withPermit {
                        runCatching { retryWithExponentialBackoff { fetchStockHistory(stock, limit) } }
                            .onFailure { println("History sync failed for ${stock.code}: ${it.message}") }
                            .getOrDefault(emptyList())
                    }
                }
            }.awaitAll().flatten()
        }
        database.upsertHistoryFromSync(
            histories = histories,
            source = "EastMoneyKLine",
            requestedStocks = stocks.size
        )
    }

    suspend fun syncTodayIfNeededAfterClose(): HistorySyncStatus? = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now(DailyStockSync.CHINA_ZONE)
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return@withContext null
        if (now.toLocalTime().isBefore(DAILY_HISTORY_SYNC_TIME)) return@withContext null

        val today = now.toLocalDate().format(DailyStockSync.BASIC_DATE).toInt()
        val status = database.historySyncStatus()
        if (status.lastHistorySyncDate == today && status.lastHistorySyncCount != null && status.lastHistorySyncCount > 1000) {
            return@withContext null
        }
        retryWithExponentialBackoff { syncRecentKLines(limit = 1) }
    }

    private suspend fun fetchStockHistory(stock: StockRef, limit: Int): List<SyncedHistoryStock> {
        val response = api.get(
            url = "https://push2his.eastmoney.com/api/qt/stock/kline/get",
            headers = mapOf(
                "Referer" to "https://quote.eastmoney.com/",
                "User-Agent" to "Mozilla/5.0"
            ),
            query = mapOf(
                "secid" to "${marketCode(stock.code)}.${stock.code}",
                "klt" to "101",
                "fqt" to "1",
                "lmt" to limit.coerceIn(1, 500).toString(),
                "end" to "20500000",
                "iscca" to "1",
                "fields1" to "f1,f2,f3,f4,f5,f6,f7,f8",
                "fields2" to "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64,f350,f351,f352",
                "forcect" to "1"
            )
        )
        val root = JsonParser.parseString(response.string()).asJsonObject
        val dataElement = root.get("data")
        if (dataElement == null || dataElement.isJsonNull || !dataElement.isJsonObject) return emptyList()
        val data = dataElement.asJsonObject
        val code = data.get("code")?.asString ?: stock.code
        val rows = data.getAsJsonArray("klines") ?: return emptyList()
        return (0 until rows.size()).mapNotNull { index ->
            rows.get(index).asString.toHistoryStock(code, stock.name)
        }
    }

    private fun String.toHistoryStock(code: String, name: String): SyncedHistoryStock? {
        val arr = split(',')
        if (arr.size < 11) return null
        val date = arr[0].replace("-", "").toIntOrNull() ?: return null
        val open = arr[1].toDoubleOrNull() ?: return null
        val close = arr[2].toDoubleOrNull() ?: return null
        val high = arr[3].toDoubleOrNull() ?: return null
        val low = arr[4].toDoubleOrNull() ?: return null
        val volume = arr[5].toDoubleOrNull() ?: 0.0
        val amount = arr[6].toDoubleOrNull() ?: 0.0
        val amplitude = arr[7].toDoubleOrNull() ?: 0.0
        val chg = arr[8].toDoubleOrNull() ?: 0.0
        val turnoverRate = arr[10].toDoubleOrNull() ?: 0.0
        val yesterdayClose = if (chg != -100.0) money(close / (1 + chg / 100.0)) else -1.0
        val ztPrice = arr.getOrNull(14)?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: yesterdayClose.takeIf { it > 0.0 }?.let { money(it * limitRate(code, name)) }
            ?: -1.0
        val dtPrice = arr.getOrNull(15)?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: yesterdayClose.takeIf { it > 0.0 }?.let { money(it * downLimitRate(code, name)) }
            ?: -1.0
        val averagePrice = arr.getOrNull(16)?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: amount.takeIf { it > 0.0 && volume > 0.0 }?.let { money(it / volume / 100.0) }
            ?: money((open + close + high + low) / 4.0)
        return SyncedHistoryStock(
            code = code,
            date = date,
            closePrice = money(close),
            openPrice = money(open),
            highest = money(high),
            lowest = money(low),
            chg = percent(chg),
            amplitude = percent(amplitude),
            turnoverRate = percent(turnoverRate),
            ztPrice = moneyOrMissing(ztPrice),
            dtPrice = moneyOrMissing(dtPrice),
            yesterdayClosePrice = yesterdayClose,
            averagePrice = moneyOrMissing(averagePrice)
        )
    }

    private fun marketCode(code: String): Int {
        return if (
            code.startsWith("000") ||
            code.startsWith("001") ||
            code.startsWith("002") ||
            code.startsWith("003") ||
            code.startsWith("30") ||
            code.startsWith("43") ||
            code.startsWith("82") ||
            code.startsWith("83") ||
            code.startsWith("87") ||
            code.startsWith("88") ||
            code.startsWith("92")
        ) 0 else 1
    }

    private fun moneyOrMissing(value: Double): Double = if (value > 0) money(value) else -1.0

    companion object {
        private const val DEFAULT_LIMIT = 120
        private const val CONCURRENCY = 8
        private val DAILY_HISTORY_SYNC_TIME: LocalTime = LocalTime.of(15, 10)
    }
}

interface HistoryKLineApi {
    @GET
    suspend fun get(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @QueryMap query: Map<String, String>
    ): okhttp3.ResponseBody
}
