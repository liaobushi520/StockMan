package com.liaobusi.stockman.monitor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.QueryMap
import retrofit2.http.Url
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.math.ceil

class DailyStockSync(private val database: StockDatabase) {
    private val marketApi = Retrofit.Builder()
        .baseUrl("https://example.com/")
        .client(trustAllOkHttpClient())
        .build()
        .create(MarketDataApi::class.java)

    private val strategy: StockSyncStrategy = FallbackStockSyncStrategy(
        listOf(
            EastMoneyStockStrategy(marketApi),
            SseStockStrategy(marketApi),
        )
    )

    suspend fun sync(): SyncStatus = withContext(Dispatchers.IO) {
        val result = retryWithExponentialBackoff { strategy.fetch() }
        val stocks = result.stocks.distinctBy { it.code }
        check(stocks.size > FULL_MARKET_STOCK_MIN_COUNT) { "daily stock snapshot is incomplete: ${stocks.size}" }
        database.replaceStocksFromSync(
            stocks = stocks,
            date = result.date,
            clearBeforeInsert = true,
            source = result.source
        )
        database.syncStatus()
    }

    suspend fun syncIfNeededBySchedule(): SyncStatus? {
        val now = LocalDateTime.now(CHINA_ZONE)
        if (!now.isTradingWeekday()) return null

        val today = now.toLocalDate().format(BASIC_DATE).toInt()
        val slot = currentSlot(now.toLocalTime()) ?: return null
        val status = database.syncStatus()
        val slotKey = "$today-$slot"
        if (status.lastStockSyncSlot == slotKey && status.lastStockSyncCount != null && status.lastStockSyncCount > FULL_MARKET_STOCK_MIN_COUNT) {
            return null
        }
        return sync().also { database.markDailyStockSlot(slotKey) }
    }

    suspend fun init(): SyncStatus? {
        return if (database.stockCount() < 1000) sync() else null
    }

    private fun LocalDateTime.isTradingWeekday(): Boolean {
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
    }

    private fun currentSlot(time: LocalTime): String? {
        return DAILY_SYNC_TIMES.lastOrNull { time >= it }?.format(SLOT_FORMATTER)
    }

    private fun trustAllOkHttpClient(): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        val CHINA_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val DAILY_SYNC_TIMES: List<LocalTime> = listOf(LocalTime.of(9, 25), LocalTime.of(12, 10), LocalTime.of(15, 10))
        val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        private val SLOT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmm")
    }
}

interface MarketDataApi {
    @GET
    suspend fun get(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @QueryMap query: Map<String, String>
    ): ResponseBody

    @GET
    suspend fun getEncoded(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @QueryMap(encoded = true) query: Map<String, String>
    ): ResponseBody

}

data class PagedRequestConfig(
    val name: String,
    val baseUrl: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val fixedQuery: Map<String, String> = emptyMap(),
    val pageParam: String = "page",
    val pageSizeParam: String = "pageSize",
    val pageStart: Int = 1,
    val pageSize: Int = 200,
    val preservePlusInQueryValues: Boolean = false
) {
    val url: String get() = baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    fun query(page: Int, pageSize: Int = this.pageSize): Map<String, String> {
        return fixedQuery + mapOf(pageParam to page.toString(), pageSizeParam to pageSize.toString())
    }
}

data class RangeRequestConfig(
    val name: String,
    val baseUrl: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val fixedQuery: Map<String, String> = emptyMap(),
    val beginParam: String = "begin",
    val endParam: String = "end",
    val pageSize: Int = 2500
) {
    val url: String get() = baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    fun query(begin: Int, end: Int): Map<String, String> {
        return fixedQuery + mapOf(beginParam to begin.toString(), endParam to end.toString())
    }
}

interface StockSyncStrategy {
    val name: String
    suspend fun fetch(): SyncResult
}

data class SyncResult(
    val source: String,
    val date: Int,
    val stocks: List<SyncedStock>
)

class FallbackStockSyncStrategy(private val strategies: List<StockSyncStrategy>) : StockSyncStrategy {
    override val name: String = strategies.joinToString(" -> ") { it.name }

    override suspend fun fetch(): SyncResult {
        val errors = mutableListOf<String>()
        for (strategy in strategies) {
            val result = runCatching { strategy.fetch() }
            if (result.isSuccess) return result.getOrThrow()
            val message = result.exceptionOrNull()?.message ?: "unknown error"
            errors.add("${strategy.name}: $message")
            println("${strategy.name} sync failed: $message")
        }
        error("All stock sync strategies failed: ${errors.joinToString(" | ")}")
    }
}


class EastMoneyStockStrategy(private val api: MarketDataApi) : StockSyncStrategy {
    override val name: String = "EastMoney"
    private val request = PagedRequestConfig(
        name = name,
        baseUrl = "http://20.push2.eastmoney.com",
        path = "/api/qt/clist/get",
        headers = mapOf(
            "Referer" to "https://quote.eastmoney.com/center/gridlist.html",
            "User-Agent" to "Mozilla/5.0"
        ),
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

    override suspend fun fetch(): SyncResult {
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

        check(all.size > FULL_MARKET_MIN_COUNT) { "$name returned too few stocks: ${all.size}" }
        return SyncResult(name, date = date.safeSyncDate(), stocks = all)
    }

    private suspend fun fetchPage(page: Int, pageSize: Int): EastMoneyPage {
        val root = api.getJsonObject(
            url = request.url,
            headers = request.headers,
            query = request.query(page, pageSize)
                .encodeValuesPreservingPlusIfNeeded(request.preservePlusInQueryValues),
            encodedQuery = request.preservePlusInQueryValues
        )
        val data = root.obj("data") ?: return EastMoneyPage(total = 0, date = 0, stocks = emptyList())
        val total = data.int("total")
        val rows = data.array("diff").elements()
        var date = 0
        val stocks = rows.mapNotNull { element ->
            val row = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val stock = row.eastMoneyStock() ?: return@mapNotNull null
            val rowDate = row.int("f297")
            if (rowDate > date) date = rowDate
            stock
        }
        return EastMoneyPage(total = total, date = date, stocks = stocks)
    }

    private fun JsonObject.eastMoneyStock(): SyncedStock? {
        val code = text("f12")
        val name = text("f14")
        val price = scaled("f2")
        if (code.isBlank() || name.isBlank() || price <= 0.0) return null
        return SyncedStock(
            code = code,
            name = name,
            price = price,
            chg = scaled("f3"),
            amplitude = scaled("f7"),
            turnoverRate = scaled("f8"),
            highest = scaled("f15"),
            lowest = scaled("f16"),
            openPrice = scaled("f17"),
            yesterdayClosePrice = scaled("f18"),
            circulationMarketValue = double("f21"),
            toMarketTime = int("f26"),
            ztPrice = scaled("f350"),
            dtPrice = scaled("f351"),
            averagePrice = scaled("f352"),
            bk = text("f383")
        )
    }

    private data class EastMoneyPage(
        val total: Int,
        val date: Int,
        val stocks: List<SyncedStock>
    )
}

class SseStockStrategy(private val api: MarketDataApi) : StockSyncStrategy {
    override val name: String = "SSE"
    private val request = RangeRequestConfig(
        name = name,
        baseUrl = "https://yunhq.sse.com.cn:32042",
        path = "/v1/sh1/list/exchange/equity",
        pageSize = 2500,
        fixedQuery = mapOf(
            "select" to "code,name,open,high,low,last,prev_close,chg_rate,volume,amount,tradephase,change,amp_rate,cpxxsubtype,cpxxprodusta,",
            "order" to ""
        )
    )

    override suspend fun fetch(): SyncResult {
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
        return SyncResult(name, first.date.safeSyncDate(), all)
    }

    private suspend fun fetchRange(begin: Int, end: Int): SsePage {
        val root = api.getJsonObject(
            url = request.url,
            headers = request.headers,
            query = request.query(begin, end) + ("_" to System.currentTimeMillis().toString())
        )
        val date = root.int("date")
        val total = root.int("total")
        val rows = root.array("list").elements()
        return SsePage(
            date = date,
            total = total,
            stocks = rows.mapNotNull { it.asJsonArrayOrNull()?.sseStock() }
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

class CaixinStockStrategy(private val api: MarketDataApi) : StockSyncStrategy {
    override val name: String = "Caixin"
    private val request = PagedRequestConfig(
        name = name,
        baseUrl = "https://stock.caixin.com",
        path = "/cgi/StockRankEx",
        pageParam = "page",
        pageSizeParam = "size",
        pageSize = 6000,
        fixedQuery = mapOf(
            "mar" to "all",
            "type" to "changeRate",
            "isAsc" to "false"
        )
    )

    override suspend fun fetch(): SyncResult {
        val root = api.getJsonObject(request.url, request.headers, request.query(1, request.pageSize))
        check(root.text("status") == "true") { "$name returned status=false" }
        val data = root.obj("data") ?: error("$name missing data")
        val timestamp = data.double("timestamp").toLong()
        val date = if (timestamp > 0) {
            Instant.ofEpochMilli(timestamp).atZone(DailyStockSync.CHINA_ZONE).format(DailyStockSync.BASIC_DATE).toInt()
        } else {
            LocalDate.now(DailyStockSync.CHINA_ZONE).format(DailyStockSync.BASIC_DATE).toInt()
        }
        val stocks = data.array("list").elements().mapNotNull { element ->
            val row = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val code = row.text("stkCode").removeSuffix(".SZ").removeSuffix(".SH").removeSuffix(".BJ")
            val name = row.text("stkShortName")
            val price = row.double("curPrice")
            if (code.isBlank() || name.isBlank() || price <= 0.0) return@mapNotNull null
            SyncedStock(
                code = code,
                name = name,
                price = money(price),
                chg = percent(row.double("changeRate")),
                amplitude = percent(row.double("amplitude")),
                turnoverRate = percent(row.double("turnoverRate")),
                highest = money(row.double("highPrice")),
                lowest = money(row.double("lowPrice")),
                openPrice = money(row.double("openPrice")),
                yesterdayClosePrice = money(row.double("preClosePrice")),
                circulationMarketValue = row.double("floatValue").takeIf { it > 0.0 } ?: row.double("totValue"),
                toMarketTime = 0,
                ztPrice = money(row.double("upLimit")),
                dtPrice = money(row.double("downLimit")),
                averagePrice = money(row.double("avgPrice").takeIf { it > 0.0 } ?: price),
                bk = ""
            )
        }
        check(stocks.size > FULL_MARKET_MIN_COUNT) { "$name returned too few stocks: ${stocks.size}" }
        return SyncResult(name, date, stocks)
    }
}

class BaiduStockStrategy(private val api: MarketDataApi) : StockSyncStrategy {
    override val name: String = "Baidu"
    private val request = PagedRequestConfig(
        name = name,
        baseUrl = "https://finance.pae.baidu.com",
        path = "/selfselect/getmarketrank",
        pageParam = "pn",
        pageSizeParam = "rn",
        pageStart = 0,
        pageSize = 200,
        fixedQuery = mapOf(
            "group" to "ranklist",
            "type" to "ab",
            "finClientType" to "pc"
        )
    )

    override suspend fun fetch(): SyncResult {
        val pageSize = request.pageSize
        val all = mutableListOf<SyncedStock>()
        for (page in 0 until 40) {
            val rows = parseRows(api.get(request.url, request.headers, request.query(page * pageSize, pageSize)).string())
            if (rows.isEmpty()) break
            all.addAll(rows)
            if (rows.size < pageSize) break
        }
        check(all.size > FULL_MARKET_MIN_COUNT) { "$name returned too few stocks: ${all.size}" }
        return SyncResult(name, LocalDate.now(DailyStockSync.CHINA_ZONE).format(DailyStockSync.BASIC_DATE).toInt(), all)
    }

    private fun parseRows(body: String): List<SyncedStock> {
        val root = body.toJsonObject()
        if (root.text("ResultCode") != "0") return emptyList()
        val result = root.obj("Result")
            ?.array("Result")?.firstOrNull()?.asJsonObjectOrNull()
            ?.obj("DisplayData")
            ?.obj("resultData")
            ?.obj("tplData")
            ?.obj("result")
        val ranks = result?.array("rank").elements()
        return ranks.mapNotNull { element ->
            val row = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val values = row.array("list").elements().mapNotNull {
                val item = it.asJsonObjectOrNull() ?: return@mapNotNull null
                item.text("text").takeIf { text -> text.isNotBlank() }?.let { text -> text to item.text("value") }
            }.toMap()
            val code = row.text("code")
            val name = row.text("name")
            val price = values["最新价"].toPlainDouble()
            if (code.isBlank() || name.isBlank() || price <= 0.0) return@mapNotNull null
            val chg = values["涨跌幅"].toPlainDouble()
            val amplitude = values["振幅"].toPlainDouble()
            val turnover = values["换手率"].toPlainDouble()
            val totalMarketValue = values["总市值"].toMarketValue()
            val previousClose = if (chg != -100.0) money(price / (1 + chg / 100.0)) else price
            val high = if (amplitude > 0.0) money(price * (1 + amplitude / 200.0)) else price
            val low = if (amplitude > 0.0) money(price * (1 - amplitude / 200.0)) else price
            SyncedStock(
                code = code,
                name = name,
                price = money(price),
                chg = percent(chg),
                amplitude = percent(amplitude),
                turnoverRate = percent(turnover),
                highest = high,
                lowest = low,
                openPrice = price,
                yesterdayClosePrice = previousClose,
                circulationMarketValue = totalMarketValue,
                toMarketTime = 0,
                ztPrice = money(previousClose * limitRate(code, name)),
                dtPrice = money(previousClose * downLimitRate(code, name)),
                averagePrice = price,
                bk = ""
            )
        }
    }
}

private suspend fun MarketDataApi.getJsonObject(
    url: String,
    headers: Map<String, String>,
    query: Map<String, String>,
    encodedQuery: Boolean = false
): JsonObject {
    val body = if (encodedQuery) {
        getEncoded(url, headers, query)
    } else {
        get(url, headers, query)
    }
    return body.string().toJsonObject()
}

private fun Map<String, String>.encodeValuesPreservingPlusIfNeeded(enabled: Boolean): Map<String, String> {
    if (!enabled) return this
    return mapValues { (_, value) -> URLEncoder.encode(value, Charsets.UTF_8).replace("%2B", "+") }
}

private fun String.toJsonObject(): JsonObject = JsonParser.parseString(this).asJsonObject

private fun JsonObject.obj(key: String): JsonObject? = get(key)?.asJsonObjectOrNull()

private fun JsonObject.array(key: String): JsonArray? = get(key)?.asJsonArrayOrNull()

private fun JsonObject.text(key: String): String = get(key)?.stringOrNull().orEmpty()

private fun JsonObject.int(key: String): Int = get(key)?.stringOrNull()?.toIntOrNull() ?: 0

private fun JsonObject.double(key: String): Double = get(key)?.stringOrNull()?.toDoubleOrNull() ?: 0.0

private fun JsonObject.scaled(key: String): Double = money(double(key) / 100.0)

private fun JsonArray.textAt(index: Int): String = elementAtOrNull(index)?.stringOrNull().orEmpty()

private fun JsonArray.doubleAt(index: Int): Double = elementAtOrNull(index)?.stringOrNull()?.toDoubleOrNull() ?: 0.0

private fun JsonArray.elementAtOrNull(index: Int) = if (index in 0 until size()) get(index) else null

private fun JsonArray?.elements() = this?.let { array -> (0 until array.size()).map { array.get(it) } }.orEmpty()

private fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? {
    return if (!isJsonNull && isJsonObject) asJsonObject else null
}

private fun com.google.gson.JsonElement.asJsonArrayOrNull(): JsonArray? {
    return if (!isJsonNull && isJsonArray) asJsonArray else null
}

private fun com.google.gson.JsonElement.stringOrNull(): String? {
    return if (!isJsonNull && isJsonPrimitive) asJsonPrimitive.asString else null
}

private fun String?.toPlainDouble(): Double {
    if (isNullOrBlank() || this == "--") return 0.0
    return replace("%", "")
        .replace("+", "")
        .replace(",", "")
        .toDoubleOrNull() ?: 0.0
}

private fun String?.toMarketValue(): Double {
    if (isNullOrBlank() || this == "--") return 0.0
    val value = replace("+", "").replace(",", "")
    return when {
        value.endsWith("万亿") -> (value.removeSuffix("万亿").toDoubleOrNull() ?: 0.0) * 1_000_000_000_000
        value.endsWith("亿") -> (value.removeSuffix("亿").toDoubleOrNull() ?: 0.0) * 100_000_000
        value.endsWith("万") -> (value.removeSuffix("万").toDoubleOrNull() ?: 0.0) * 10_000
        else -> value.toDoubleOrNull() ?: 0.0
    }
}

private fun Int.safeSyncDate(): Int {
    return takeIf { it > 0 } ?: LocalDate.now(DailyStockSync.CHINA_ZONE).format(DailyStockSync.BASIC_DATE).toInt()
}

fun limitRate(code: String, name: String): Double {
    return when {
        name.startsWith("ST") || name.startsWith("*") -> 1.05
        code.startsWith("300") || code.startsWith("301") || code.startsWith("688") || code.startsWith("689") -> 1.2
        code.startsWith("82") || code.startsWith("83") || code.startsWith("87") || code.startsWith("88") || code.startsWith("43") || code.startsWith("92") -> 1.3
        else -> 1.1
    }
}

fun downLimitRate(code: String, name: String): Double {
    return when {
        name.startsWith("ST") || name.startsWith("*") -> 0.95
        code.startsWith("300") || code.startsWith("301") || code.startsWith("688") || code.startsWith("689") -> 0.8
        code.startsWith("82") || code.startsWith("83") || code.startsWith("87") || code.startsWith("88") || code.startsWith("43") || code.startsWith("92") -> 0.7
        else -> 0.9
    }
}

private const val FULL_MARKET_MIN_COUNT = FULL_MARKET_STOCK_MIN_COUNT

suspend fun <T> retryWithExponentialBackoff(
    attempts: Int = 5,
    initialDelayMillis: Long = 1_000,
    block: suspend () -> T
): T {
    var nextDelay = initialDelayMillis
    var lastError: Throwable? = null
    repeat(attempts) { index ->
        val result = runCatching { block() }
        if (result.isSuccess) return result.getOrThrow()
        lastError = result.exceptionOrNull()
        if (index < attempts - 1) {
            delay(nextDelay)
            nextDelay *= 2
        }
    }
    throw lastError ?: IllegalStateException("retry failed")
}
