package com.liaobusi.stockman.monitor.web

import kotlinx.serialization.Serializable

@Serializable
data class StockTick(
    val code: String,
    val name: String,
    val price: Double,
    val chg: Double,
    val ztPrice: Double,
    val dtPrice: Double,
    val time: Long
)

@Serializable
data class AlertEvent(
    val code: String,
    val name: String,
    val title: String,
    val content: String,
    val chg: Double,
    val price: Double,
    val time: Long
)

@Serializable
data class ClientMessage(
    val type: String,
    val stocks: List<StockTick> = emptyList(),
    val alert: AlertEvent? = null
)
