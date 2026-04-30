package com.liaobusi.stockman.monitor.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface EastMoneyApi {
    @Headers(
        "Referer: https://quote.eastmoney.com/center/gridlist.html",
        "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
    )
    @GET("api/qt/clist/get")
    suspend fun getRealtimeStocks(
        @Query("pn") page: Int,
        @Query("pz") pageSize: Int,
        @QueryMap(encoded = true) query: Map<String, String>
    ): EastMoneyRealtimeResp

    @Headers(
        "Referer: https://quote.eastmoney.com/",
    )
    @GET("api/qt/stock/kline/get")
    suspend fun getKLine(
        @QueryMap query: Map<String, String>
    ): EastMoneyKLineResp
}

data class EastMoneyRealtimeResp(
    @SerializedName("data") val data: EastMoneyRealtimeData?
)

data class EastMoneyRealtimeData(
    @SerializedName("total") val total: Int?,
    @SerializedName("diff") val diff: List<EastMoneyRealtimeStockDto>?
)

data class EastMoneyRealtimeStockDto(
    @SerializedName("f2") val price: JsonElement?,
    @SerializedName("f3") val chg: JsonElement?,
    @SerializedName("f7") val amplitude: JsonElement?,
    @SerializedName("f8") val turnoverRate: JsonElement?,
    @SerializedName("f12") val code: JsonElement?,
    @SerializedName("f14") val name: JsonElement?,
    @SerializedName("f15") val highest: JsonElement?,
    @SerializedName("f16") val lowest: JsonElement?,
    @SerializedName("f17") val openPrice: JsonElement?,
    @SerializedName("f18") val yesterdayClosePrice: JsonElement?,
    @SerializedName("f21") val circulationMarketValue: JsonElement?,
    @SerializedName("f26") val toMarketTime: JsonElement?,
    @SerializedName("f297") val date: JsonElement?,
    @SerializedName("f350") val ztPrice: JsonElement?,
    @SerializedName("f351") val dtPrice: JsonElement?,
    @SerializedName("f352") val averagePrice: JsonElement?,
    @SerializedName("f383") val bk: JsonElement?
)

data class EastMoneyKLineResp(
    @SerializedName("data") val data: EastMoneyKLineData?
)

data class EastMoneyKLineData(
    @SerializedName("code") val code: String?,
    @SerializedName("klines") val klines: List<String>?
)
