package com.liaobusi.stockman.monitor.sync.strategy

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.liaobusi.stockman.monitor.ChinaMarketCalendar
import com.liaobusi.stockman.monitor.FULL_MARKET_STOCK_MIN_COUNT
import com.liaobusi.stockman.monitor.SyncedStock
import com.liaobusi.stockman.monitor.downLimitRate
import com.liaobusi.stockman.monitor.limitRate
import com.liaobusi.stockman.monitor.money
import com.liaobusi.stockman.monitor.percent
import com.liaobusi.stockman.monitor.network.EastMoneyApi
import com.liaobusi.stockman.monitor.network.EastMoneyRealtimeStockDto
import com.liaobusi.stockman.monitor.network.SinaApi
import com.liaobusi.stockman.monitor.network.SinaRealtimeStockDto
import com.liaobusi.stockman.monitor.network.SseApi
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlinx.coroutines.delay

private val realtimeStrategyLogger = LoggerFactory.getLogger("RealtimeStockStrategies")

interface RealtimeStockStrategy {
    val name: String
    suspend fun fetchSnapshot(): RealtimeStockSnapshot
}

data class RealtimeStockSnapshot(
    val source: String,
    val date: Int,
    val stocks: List<SyncedStock>
)

data class PagedRequestConfig(
    val name: String,
    val fixedQuery: Map<String, String> = emptyMap(),
    val pageParam: String = "page",
    val pageSizeParam: String = "pageSize",
    val pageStart: Int = 1,
    val pageSize: Int = 200,
    val preservePlusInQueryValues: Boolean = false
) {
    fun query(page: Int, pageSize: Int = this.pageSize): Map<String, String> {
        return fixedQuery + mapOf(pageParam to page.toString(), pageSizeParam to pageSize.toString())
    }
}

data class RangeRequestConfig(
    val name: String,
    val fixedQuery: Map<String, String> = emptyMap(),
    val beginParam: String = "begin",
    val endParam: String = "end",
    val pageSize: Int = 2500
) {
    fun query(begin: Int, end: Int): Map<String, String> {
        return fixedQuery + mapOf(beginParam to begin.toString(), endParam to end.toString())
    }
}

suspend fun fetchFirstSuccessfulSnapshot(strategies: List<RealtimeStockStrategy>): RealtimeStockSnapshot {
    val errors = mutableListOf<String>()
    for (strategy in strategies) {
        val result = runCatching { strategy.fetchSnapshot() }
        if (result.isSuccess) return result.getOrThrow()
        val message = result.exceptionOrNull()?.message ?: "unknown error"
        errors.add("${strategy.name}: $message")
        realtimeStrategyLogger.warn("{} sync failed: {}", strategy.name, message)
    }
    error("All stock sync strategies failed: ${errors.joinToString(" | ")}")
}

class EastMoneyRealtimeStockStrategy(private val api: EastMoneyApi) : RealtimeStockStrategy {
    override val name: String = "EastMoney"
    private val request = PagedRequestConfig(
        name = name,
        pageParam = "pn",
        pageSizeParam = "pz",
        pageSize = 200,
        preservePlusInQueryValues = true,
        fixedQuery = mapOf(
            "np" to "1",
            "fid" to "f3",
            "fields" to "f2,f3,f7,f8,f12,f14,f15,f16,f17,f18,f21,f26,f297,f350,f351,f352,f383",
            "fs" to "m:1+t:2,m:0+t:6,m:0+t:13,m:0+t:80,m:1+t:23,t:81+s:2048"
        )
    )

    override suspend fun fetchSnapshot(): RealtimeStockSnapshot {
        val pageSize = request.pageSize
        val first = fetchPage(page = 1, pageSize = pageSize)
        val total = first.total
        val effectivePageSize = first.stocks.size.takeIf { it > 0 } ?: pageSize
        val pages = ceil(total / effectivePageSize.toDouble()).toInt().coerceAtLeast(1)
        val all = first.stocks.toMutableList()
        var date = first.date

        for (page in 2..pages) {
            delay(250)
            val next = fetchPage(page = page, pageSize = pageSize)
            all.addAll(next.stocks)
            if (next.date > 0) date = next.date
        }

        check(all.size > FULL_MARKET_STOCK_MIN_COUNT) { "$name returned too few stocks: ${all.size}" }
        return RealtimeStockSnapshot(name, date = date.safeSyncDate(), stocks = all)
    }

    private suspend fun fetchPage(page: Int, pageSize: Int): EastMoneyPage {
        val response = api.getRealtimeStocks(
            page = page,
            pageSize = pageSize,
            query = request.fixedQuery.encodeValuesPreservingPlusIfNeeded(request.preservePlusInQueryValues)
        )
        val data = response.data ?: return EastMoneyPage(total = 0, date = 0, stocks = emptyList())
        val total = data.total ?: 0
        val rows = data.diff.orEmpty()
        var date = 0
        val stocks = rows.mapNotNull { row ->
            val stock = row.eastMoneyStock() ?: return@mapNotNull null
            val rowDate = row.date.intOrZero()
            if (rowDate > date) date = rowDate
            stock
        }
        return EastMoneyPage(total = total, date = date, stocks = stocks)
    }

    private fun EastMoneyRealtimeStockDto.eastMoneyStock(): SyncedStock? {
        val code = code.text()
        val name = name.text()
        val price = price.scaled()
        if (code.isBlank() || name.isBlank() || price <= 0.0) return null
        return SyncedStock(
            code = code,
            name = name,
            price = price,
            chg = chg.scaled(),
            amplitude = amplitude.scaled(),
            turnoverRate = turnoverRate.scaled(),
            highest = highest.scaled(),
            lowest = lowest.scaled(),
            openPrice = openPrice.scaled(),
            yesterdayClosePrice = yesterdayClosePrice.scaled(),
            circulationMarketValue = circulationMarketValue.doubleOrZero(),
            toMarketTime = toMarketTime.intOrZero(),
            ztPrice = ztPrice.scaled(),
            dtPrice = dtPrice.scaled(),
            averagePrice = averagePrice.scaled(),
            bk = bk.text()
        )
    }

    private data class EastMoneyPage(
        val total: Int,
        val date: Int,
        val stocks: List<SyncedStock>
    )
}

class SinaRealtimeStockStrategy(private val api: SinaApi) : RealtimeStockStrategy {
    override val name: String = "Sina"
    private val request = PagedRequestConfig(
        name = name,
        pageParam = "page",
        pageSizeParam = "num",
        pageStart = 1,
        pageSize = 3000,
        fixedQuery = mapOf(
            "sort" to "symbol",
            "asc" to "1",
            "node" to "hs_a",
            "_s_r_a" to "page"
        )
    )

    override suspend fun fetchSnapshot(): RealtimeStockSnapshot {
        val all = mutableListOf<SyncedStock>()
        var page = request.pageStart
        while (true) {
            val rows = fetchPage(page)
            if (rows.rawCount == 0) break
            all.addAll(rows.stocks)
            if (rows.rawCount < request.pageSize) break
            page += 1
            delay(250)
        }

        val stocks = all.distinctBy { it.code }
        check(stocks.size > FULL_MARKET_STOCK_MIN_COUNT) { "$name returned too few stocks: ${stocks.size}" }
        return RealtimeStockSnapshot(
            source = name,
            date = 0,
            stocks = stocks
        )
    }

    private suspend fun fetchPage(page: Int): SinaPage {
        val rows = api.getRealtimeStocks(
            page = page,
            pageSize = request.pageSize,
            query = request.fixedQuery
        )
        return SinaPage(
            rawCount = rows.size,
            stocks = rows.mapNotNull { it.sinaStock() }
        )
    }

    private fun SinaRealtimeStockDto.sinaStock(): SyncedStock? {
        val code = sinaCode()
        val name = name.text()
        val price = trade.doubleOrZero()
        val previousClose = settlement.doubleOrZero()
        val open = open.doubleOrZero()
        val high = high.doubleOrZero()
        val low = low.doubleOrZero()
        if (code.isBlank() || name.isBlank() || price <= 0.0 || previousClose <= 0.0) return null
        val volume = volume.doubleOrZero()
        val amount = amount.doubleOrZero()
        val average = amount.takeIf { it > 0.0 && volume > 0.0 }?.let { money(it / volume / 100.0) } ?: price
        return SyncedStock(
            code = code,
            name = name,
            price = money(price),
            chg = percent(changePercent.doubleOrZero()),
            amplitude = percent(if (previousClose > 0.0) (high - low) / previousClose * 100.0 else 0.0),
            turnoverRate = 0.0,
            highest = money(high),
            lowest = money(low),
            circulationMarketValue = 0.0,
            toMarketTime = 0,
            openPrice = money(open),
            yesterdayClosePrice = money(previousClose),
            ztPrice = money(previousClose * limitRate(code, name)),
            dtPrice = money(previousClose * downLimitRate(code, name)),
            averagePrice = average,
            bk = ""
        )
    }

    private fun SinaRealtimeStockDto.sinaCode(): String {
        val rawCode = code.text()
        if (rawCode.isNotBlank()) return rawCode
        return symbol.text()
            .removePrefix("sh")
            .removePrefix("sz")
            .removePrefix("bj")
    }

    private data class SinaPage(
        val rawCount: Int,
        val stocks: List<SyncedStock>
    )
}

/**
 * 上海交易所，只有沪A数据。保留为后续局部数据源，不参与 stock 全量实时快照策略链。
 */
class SseRealtimeStockStrategy(private val api: SseApi) : RealtimeStockStrategy {
    override val name: String = "SSE"
    private val request = RangeRequestConfig(
        name = name,
        pageSize = 2500,
        fixedQuery = mapOf(
            "select" to "code,name,open,high,low,last,prev_close,chg_rate,volume,amount,tradephase,change,amp_rate,cpxxsubtype,cpxxprodusta,",
            "order" to ""
        )
    )

    override suspend fun fetchSnapshot(): RealtimeStockSnapshot {
        val pageSize = request.pageSize
        val first = fetchRange(begin = 0, end = pageSize)
        val all = first.stocks.toMutableList()
        var begin = pageSize
        while (begin < first.total) {
            delay(250)
            val next = fetchRange(begin = begin, end = begin + pageSize)
            all.addAll(next.stocks)
            begin += pageSize
        }
        check(all.size > 1000) { "$name returned too few stocks: ${all.size}" }
        return RealtimeStockSnapshot(name, first.date.safeSyncDate(), all)
    }

    private suspend fun fetchRange(begin: Int, end: Int): SsePage {
        val response = api.getEquities(
            begin = begin,
            end = end,
            query = request.fixedQuery
        )
        return SsePage(
            date = response.date ?: 0,
            total = response.total ?: 0,
            stocks = response.list.orEmpty().mapNotNull { it.sseStock() }
        )
    }

    private fun JsonArray.sseStock(): SyncedStock? {
        val code = textAt(0)
        val name = textAt(1)
        val open = doubleAt(2)
        val high = doubleAt(3)
        val low = doubleAt(4)
        val last = doubleAt(5)
        val previousClose = doubleAt(6)
        if (code.isBlank() || name.isBlank() || last <= 0.0) return null
        return SyncedStock(
            code = code,
            name = name,
            price = money(last),
            chg = percent(doubleAt(7)),
            amplitude = percent(doubleAt(12)),
            turnoverRate = 0.0,
            highest = money(high),
            lowest = money(low),
            circulationMarketValue = 0.0,
            toMarketTime = 0,
            openPrice = money(open),
            yesterdayClosePrice = money(previousClose),
            ztPrice = money(previousClose * limitRate(code, name)),
            dtPrice = money(previousClose * downLimitRate(code, name)),
            averagePrice = money(last),
            bk = textAt(13)
        )
    }

    private data class SsePage(
        val date: Int,
        val total: Int,
        val stocks: List<SyncedStock>
    )
}

private fun Map<String, String>.encodeValuesPreservingPlusIfNeeded(enabled: Boolean): Map<String, String> {
    if (!enabled) return this
    return mapValues { (_, value) -> URLEncoder.encode(value, Charsets.UTF_8).replace("%2B", "+") }
}

private fun JsonArray.textAt(index: Int): String = elementAtOrNull(index)?.stringOrNull().orEmpty()

private fun JsonArray.doubleAt(index: Int): Double = elementAtOrNull(index)?.stringOrNull()?.toDoubleOrNull() ?: 0.0

private fun JsonArray.elementAtOrNull(index: Int) = if (index in 0 until size()) get(index) else null

private fun JsonElement?.stringOrNull(): String? {
    return if (this != null && !isJsonNull && isJsonPrimitive) asJsonPrimitive.asString else null
}

private fun JsonElement?.text(): String = stringOrNull().orEmpty()

private fun JsonElement?.intOrZero(): Int = stringOrNull()?.toIntOrNull() ?: 0

private fun JsonElement?.doubleOrZero(): Double = stringOrNull()?.toDoubleOrNull() ?: 0.0

private fun JsonElement?.scaled(): Double = money(doubleOrZero() / 100.0)

private fun Int.safeSyncDate(): Int {
    return takeIf { it > 0 }
        ?.let { ChinaMarketCalendar.normalizeTradingDate(it) }
        ?: ChinaMarketCalendar.toBasicInt(ChinaMarketCalendar.latestTradingDateOnOrBefore(LocalDate.now(CHINA_ZONE)))
}

private val CHINA_ZONE: ZoneId = ChinaMarketCalendar.zone
private val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
