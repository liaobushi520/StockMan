package com.liaobusi.stockman.monitor.sync.strategy

import com.liaobusi.stockman.monitor.StockRef
import com.liaobusi.stockman.monitor.SyncedHistoryStock
import com.liaobusi.stockman.monitor.RealtimeStockSync
import com.liaobusi.stockman.monitor.downLimitRate
import com.liaobusi.stockman.monitor.limitRate
import com.liaobusi.stockman.monitor.money
import com.liaobusi.stockman.monitor.network.EastMoneyApi
import com.liaobusi.stockman.monitor.network.SohuApi
import com.liaobusi.stockman.monitor.percent
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.math.roundToInt

private val historyStrategyLogger = LoggerFactory.getLogger("HistoryStockStrategies")

interface HistoryStockStrategy {
    val name: String
    suspend fun fetchHistory(stock: StockRef): HistoryStockFetchResult
}

data class HistoryStockFetchResult(
    val source: String,
    val histories: List<SyncedHistoryStock>,
    val message: String = ""
)

suspend fun fetchFirstSuccessfulHistory(
    strategies: List<HistoryStockStrategy>,
    stock: StockRef
): HistoryStockFetchResult {
    val errors = mutableListOf<String>()
    for (strategy in strategies) {
        val result = runCatching { strategy.fetchHistory(stock) }
        if (result.isSuccess) {
            val value = result.getOrThrow()
            if (value.histories.isNotEmpty()) return value
            errors.add("${strategy.name}: empty")
            continue
        }
        val message = result.exceptionOrNull()?.message ?: "unknown error"
        errors.add("${strategy.name}: $message")
        historyStrategyLogger.warn("{} history sync failed for {}: {}", strategy.name, stock.code, message)
    }
    historyStrategyLogger.warn("All history sync strategies failed for {}: {}", stock.code, errors.joinToString(" | "))
    return HistoryStockFetchResult("None", emptyList(), errors.joinToString(" | "))
}

class EastMoneyHistoryStockStrategy(private val api: EastMoneyApi) : HistoryStockStrategy {
    override val name: String = "EastMoneyKLine"

    override suspend fun fetchHistory(stock: StockRef): HistoryStockFetchResult {
        val response = api.getKLine(
            query = mapOf(
                "secid" to "${marketCode(stock.code)}.${stock.code}",
                "ut" to "fa5fd1943c7b386f172d6893dbfba10b",
                "klt" to "101",
                "fqt" to "1",
                "lmt" to (HISTORY_KLINE_SIZE + 1).toString(),
                "end" to "20500000",
                "iscca" to "1",
                "fields1" to "f1,f2,f3,f4,f5,f6,f7,f8",
                "fields2" to "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f62,f63,f64",
                "forcect" to "1"
            )
        )
        val data = response.data ?: return HistoryStockFetchResult(name, emptyList())
        val code = data.code ?: stock.code
        val histories = data.klines.orEmpty().mapNotNull { it.toEastMoneyHistoryStock(code, stock.name) }
        return HistoryStockFetchResult(name, histories)
    }
}

class SohuHistoryStockStrategy(private val api: SohuApi) : HistoryStockStrategy {
    override val name: String = "SohuHisHq"

    override suspend fun fetchHistory(stock: StockRef): HistoryStockFetchResult {
        val end = lastClosedTradeDate()
        val lookbackDays = (HISTORY_KLINE_SIZE * 2.4).roundToInt().coerceAtLeast(30)
        val start = LocalDate.now(RealtimeStockSync.CHINA_ZONE).minusDays(lookbackDays.toLong()).format(RealtimeStockSync.BASIC_DATE)
        val response = api.getStockHistory(
            code = "cn_${stock.code}",
            start = start,
            end = end
        )
        val rows = response.firstOrNull { it.code?.removePrefix("cn_") == stock.code }?.hq.orEmpty()
        val histories = rows.mapNotNull { it.toSohuHistoryStock(stock.code, stock.name) }
            .sortedBy { it.date }
            .takeLast(HISTORY_KLINE_SIZE)
        return HistoryStockFetchResult(name, histories)
    }
}

private const val HISTORY_KLINE_SIZE = 120

private fun lastClosedTradeDate(): String {
    return LocalDate.now(RealtimeStockSync.CHINA_ZONE)
        .minusDays(1)
        .format(RealtimeStockSync.BASIC_DATE)
}

private fun List<String>.toSohuHistoryStock(code: String, name: String): SyncedHistoryStock? {
    if (size < 10) return null
    val date = getOrNull(0)?.replace("-", "")?.toIntOrNull() ?: return null
    val open = getOrNull(1)?.toDoubleOrNull() ?: return null
    val close = getOrNull(2)?.toDoubleOrNull() ?: return null
    val chg = getOrNull(4)?.removeSuffix("%")?.toDoubleOrNull() ?: 0.0
    val low = getOrNull(5)?.toDoubleOrNull() ?: return null
    val high = getOrNull(6)?.toDoubleOrNull() ?: return null
    val turnoverRate = getOrNull(9)?.removeSuffix("%")?.toDoubleOrNull() ?: 0.0
    val yesterdayClose = if (chg != -100.0) money(close / (1 + chg / 100.0)) else -1.0
    val amplitude = if (yesterdayClose > 0.0) percent((high - low) / yesterdayClose * 100.0) else 0.0
    return SyncedHistoryStock(
        code = code,
        date = date,
        closePrice = money(close),
        openPrice = money(open),
        highest = money(high),
        lowest = money(low),
        chg = percent(chg),
        amplitude = amplitude,
        turnoverRate = percent(turnoverRate),
        ztPrice = yesterdayClose.takeIf { it > 0.0 }?.let { money(it * limitRate(code, name)) } ?: -1.0,
        dtPrice = yesterdayClose.takeIf { it > 0.0 }?.let { money(it * downLimitRate(code, name)) } ?: -1.0,
        yesterdayClosePrice = yesterdayClose,
        averagePrice = money((open + close + high + low) / 4.0)
    )
}

private fun String.toEastMoneyHistoryStock(code: String, name: String): SyncedHistoryStock? {
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
