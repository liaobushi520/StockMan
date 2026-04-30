package com.liaobusi.stockman.monitor.network

import com.google.gson.JsonArray
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface SseApi {
    @GET("v1/sh1/list/exchange/equity")
    suspend fun getEquities(
        @Query("begin") begin: Int,
        @Query("end") end: Int,
        @Query("_") timestamp: Long = System.currentTimeMillis(),
        @QueryMap query: Map<String, String>
    ): SseEquityResp
}

data class SseEquityResp(
    @SerializedName("date") val date: Int?,
    @SerializedName("total") val total: Int?,
    @SerializedName("list") val list: List<JsonArray>?
)
