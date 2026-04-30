package com.liaobusi.stockman.monitor.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface SinaApi {
    @GET("quotes_service/api/json_v2.php/Market_Center.getHQNodeDataSimple")
    suspend fun getRealtimeStocks(
        @Query("page") page: Int,
        @Query("num") pageSize: Int,
        @QueryMap query: Map<String, String>
    ): List<SinaRealtimeStockDto>
}

data class SinaRealtimeStockDto(
    @SerializedName("code") val code: JsonElement?,
    @SerializedName("symbol") val symbol: JsonElement?,
    @SerializedName("name") val name: JsonElement?,
    @SerializedName("trade") val trade: JsonElement?,
    @SerializedName("settlement") val settlement: JsonElement?,
    @SerializedName("open") val open: JsonElement?,
    @SerializedName("high") val high: JsonElement?,
    @SerializedName("low") val low: JsonElement?,
    @SerializedName("volume") val volume: JsonElement?,
    @SerializedName("amount") val amount: JsonElement?,
    @SerializedName("changepercent") val changePercent: JsonElement?
)
