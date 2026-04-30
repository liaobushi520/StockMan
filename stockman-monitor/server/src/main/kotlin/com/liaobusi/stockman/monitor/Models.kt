package com.liaobusi.stockman.monitor

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

@Serializable
data class StockTick(
    val code: String,
    val name: String,
    val price: Double,
    val chg: Double,
    val ztPrice: Double,
    val dtPrice: Double,
    val time: Long = System.currentTimeMillis()
)

@Serializable
data class AlertEvent(
    val code: String,
    val name: String,
    val title: String,
    val content: String,
    val chg: Double,
    val price: Double,
    val time: Long = System.currentTimeMillis()
)

@Serializable
data class MonitorSnapshot(
    val stocks: List<StockTick>,
    val alerts: List<AlertEvent>
)

@Serializable
data class ClientMessage(
    val type: String,
    val stocks: List<StockTick> = emptyList(),
    val alert: AlertEvent? = null
)

@Serializable
data class ManualTickRequest(
    val code: String,
    val price: Double? = null,
    val chg: Double? = null
)

@Serializable
data class EastMoneyDebugResult(
    val code: String,
    val secId: String,
    val direct: Boolean,
    val url: String,
    val resolvedHosts: List<String>,
    val httpCode: Int? = null,
    val success: Boolean,
    val elapsedMs: Long,
    val contentLength: Long? = null,
    val bodyPreview: String? = null,
    val okHttpLog: String,
    val error: String? = null
)

data class StockSeed(
    val code: String,
    val name: String,
    val yesterdayClose: Double,
    val limitRate: Double = 0.1,
    val baseChg: Double = 0.0,
    val circulationMarketValue: Double = 0.0,
    val toMarketTime: Int = 20000101,
    val bk: String = ""
) {
    val ztPrice: Double = money(yesterdayClose * (1 + limitRate))
    val dtPrice: Double = money(yesterdayClose * (1 - limitRate))
}

fun StockSeed.tick(chg: Double): StockTick {
    val bounded = chg.coerceIn(-limitRate * 100, limitRate * 100)
    return StockTick(
        code = code,
        name = name,
        price = money(yesterdayClose * (1 + bounded / 100)),
        chg = percent(bounded),
        ztPrice = ztPrice,
        dtPrice = dtPrice
    )
}

fun StockSeed.tickByPrice(price: Double): StockTick {
    val chg = ((price - yesterdayClose) / yesterdayClose) * 100
    return StockTick(
        code = code,
        name = name,
        price = money(price),
        chg = percent(chg),
        ztPrice = ztPrice,
        dtPrice = dtPrice
    )
}

fun money(value: Double): Double = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()

fun percent(value: Double): Double = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()

fun samePrice(left: Double, right: Double): Boolean = abs(left - right) < 0.005

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
