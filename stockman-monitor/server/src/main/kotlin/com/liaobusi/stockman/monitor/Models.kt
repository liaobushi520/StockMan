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
