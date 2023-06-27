package com.liaobusi.stockman.api

import com.google.gson.annotations.SerializedName


data class EMResponse(val data: EMData?)

data class EMData(val total: Int, val diff: List<EMDiffBean>)


data class GDRSResponse(val version:String,val success:Boolean,val message:String,val code:Int,val result: Result)

data class Result(val pages:Int,val count:Int,val data:List<GDRSBean>)

data class GDRSBean(
    val AVG_FREESHARES_RATIO: Double,
    val AVG_FREE_SHARES: Int,
    val AVG_HOLD_AMT: Double,
    val A_MARK: String,
    val B_MARK: String,
    val END_DATE: String,
    val FREEHOLD_RATIO_TOTAL: Double,
    val HOLDER_ANUM_RATIO: Double,
    val HOLDER_A_NUM: Int,
    val HOLDER_BNUM_RATIO: Any,
    val HOLDER_B_NUM: Any,
    val HOLDER_HNUM_RATIO: Any,
    val HOLDER_H_NUM: Any,
    val HOLDER_TOTAL_NUM: Int,
    val HOLD_FOCUS: String,
    val HOLD_RATIO_TOTAL: Double,
    val H_MARK: String,
    val ORG_CODE: String,
    val PRICE: Double,
    val SECUCODE: String,
    val SECURITY_CODE: String,
    val TOTAL_NUM_RATIO: Float
)



//f104涨 f105跌 f106平
data class EMDiffBean(
    val f1: Int,
    @SerializedName("f2") val price: Float,
    @SerializedName("f3") val chg: Float,
    @SerializedName("f7") val amplitude: Float,
    @SerializedName("f8") val turnoverRate: Float,
    @SerializedName("f12") val code: String,
    @SerializedName("f14") val name: String,
    @SerializedName("f15") val highest: Float,
    @SerializedName("f16") val lowest: Float,
    @SerializedName("f17") val openPrice: Float,
    @SerializedName("f18") val yesterdayClosePrice: Float,
    @SerializedName("f21") val circulationMarketValue: Float,
    @SerializedName("f26") val toMarketTime: Int,
    @SerializedName("f297") val date: Int,
    @SerializedName("f350") val ztPrice: Float,
    @SerializedName("f351") val dtPrice: Float,
    @SerializedName("f352") val averagePrice: Float,
    //个股概念
    @SerializedName("f383") val concept: String,
)

data class HistoryBean(val status:Int,val code:String,val hq:List<List<String>>?)

data class DPDayKLineResponse(
    val data: Data?,
    val dlmkts: String,
    val full: Int,
    val lt: Int,
    val rc: Int,
    val rt: Int,
    val svr: Int
)

data class Data(
    val code: String,
    val decimal: Int,
    val dktotal: Int,
    val klines: List<String>,
    val market: Int,
    val name: String,
    val preKPrice: Double,
    val prePrice: Double,
    val qtMiscType: Int
)



