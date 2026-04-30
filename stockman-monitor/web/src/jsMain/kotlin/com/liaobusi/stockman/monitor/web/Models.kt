package com.liaobusi.stockman.monitor.web

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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

@Serializable
data class ReplayLiveResponse(
    @SerialName("List") val list: List<ReplayLiveItem> = emptyList(),
    val errcode: String? = null
)

@Serializable
data class ReplayLiveItem(
    @SerialName("ID") val id: String = "",
    @SerialName("Time") val time: Long = 0,
    @SerialName("Comment") val comment: String = "",
    @SerialName("PlateName") val plateName: String = "",
    @SerialName("PlateZDF") val plateChange: String = "",
    @SerialName("UserName") val userName: String = "",
    @SerialName("Image") val image: String = "",
    @SerialName("Stock") val stock: List<List<JsonElement>> = emptyList()
)
