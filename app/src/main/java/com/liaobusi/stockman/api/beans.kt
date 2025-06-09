package com.liaobusi.stockman.api

import com.google.gson.annotations.SerializedName


data class EMResponse(val data: EMData?)

data class EMData(val total: Int, val diff: List<EMDiffBean>)


data class GDRSResponse(
    val version: String,
    val success: Boolean,
    val message: String,
    val code: Int,
    val result: Result
)

data class Result(val pages: Int, val count: Int, val data: List<GDRSBean>)

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

data class HistoryBean(val status: Int, val code: String, val hq: List<List<String>>?)

data class DPDayKLineResponse(
    val data: Data?,
    val dlmkts: String,
    val full: Int,
    val lt: Int,
    val rc: Int,
    val rt: Int,
    val svr: Long
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

data class StockTrend(
    val data: StockTrendData,
    val dlmkts: String,
    val full: Int,
    val lt: Int,
    val rc: Int,
    val rt: Int,
    val svr: Long
)

data class StockTrendData(
    val trends: List<String>
)

data class FPRequest(val date: String, val pc: Int = 0)


data class PopularityRankingResponse(
    val code: Int,
    val message: String,
    val result: ResultX,
    val success: Boolean,
    val url: String,
    val version: Any
)

data class ResultX(
    val config: List<Any>,
    val count: Int,
    val currentpage: Int,
    val data: List<PopularityData>,
    val nextpage: Boolean
)

data class PopularityData(
    val CHANGE_RATE: Double,
    val DEAL_AMOUNT: Double,
    val MAX_TRADE_DATE: String,
    val POPULARITY_RANK: Int,
    val SECUCODE: String,
    val SECURITY_CODE: String,
    val SECURITY_NAME_ABBR: String,
    val VOLUME: Int,
    val VOLUME_RATIO: Double
)


data class DragonTigerRankResponse(
    val code: Int,
    val message: String,
    val result: DragonTigerRankResult,
    val success: Boolean,
    val version: String
)

data class DragonTigerRankResult(
    val count: Int,
    val data: List<DragonTigerRankData>,
    val pages: Int
)

data class DragonTigerRankData(
    val ACCUM_AMOUNT: Double,
    val BILLBOARD_BUY_AMT: Double,
    val BILLBOARD_DEAL_AMT: Double,
    val BILLBOARD_NET_AMT: Double,
    val BILLBOARD_SELL_AMT: Double,
    val CHANGE_RATE: Double,
    val CLOSE_PRICE: Double,
    val D10_CLOSE_ADJCHRATE: Any,
    val D1_CLOSE_ADJCHRATE: Double,
    val D2_CLOSE_ADJCHRATE: Double,
    val D5_CLOSE_ADJCHRATE: Double,
    val DEAL_AMOUNT_RATIO: Double,
    val DEAL_NET_RATIO: Double,
    val EXPLAIN: String,
    val EXPLANATION: String,
    val FREE_MARKET_CAP: Double,
    val SECUCODE: String,
    val SECURITY_CODE: String,
    val SECURITY_NAME_ABBR: String,
    val SECURITY_TYPE_CODE: String,
    val TRADE_DATE: String,
    val TURNOVERRATE: Double
)

data class THSHotRankingResponse(
    val data: THSData,
    val status_code: Int,
    val status_msg: String
)

data class THSData(
    val stock_list: List<THSStock>
)

data class THSStock(
    val analyse: String?,
    val analyse_title: String?,
    val code: String,
    val hot_rank_chg: Int,
    val market: Int,
    val name: String,
    val order: Int,
    val rate: String,
    val rise_and_fall: Double,
    val tag: Tag,
    val topic: Topic?
)

data class Tag(
    val concept_tag: List<String>?,
    val live_tag: LiveTag,
    val popularity_tag: String?
)

data class LiveTag(
    val anchor: String,
    val jump_url: String
)


data class TGBResponse(
    val _t: Long,
    val dto: List<TGBStock>,
    val errorCode: Int,
    val errorMessage: String,
    val status: Boolean
)

data class TGBStock(
    val continuenum: Int,
    val fullCode: String,
    val gnList: List<Gn>?,
    val implied: Implied,
    val linkingBoard: String,
    val popularValue: Int,
    val rankRate: Any,
    val ranking: Int,
    val remark: String,
    val stockGn: Any,
    val stockName: String
)

data class Gn(
    val gnName: String,
    val ztgnSeq: Int
)

data class Implied(
    val hangflag: String,
    val portrait: String,
    val stockCode: String,
    val subject: String,
    val topicId: Int,
    val userID: Int,
    val userName: String
)

data class Topic(
    val android_jump_url: String,
    val ios_jump_url: String,
    val title: String,
    val topic_code: String
)


data class SpecificDataBean(
    val code_selectors: CodeSelectors,
    val indexes: List<Indexe>,
    val page_info: PageInfo,
    val sort: List<Sort>
)

fun getDefaultSpecificDataBean(codes:List<String>): SpecificDataBean {
    return SpecificDataBean(
        sort = listOf(Sort()),
        page_info = PageInfo(),
        indexes = listOf(
            Indexe(index_id = "security_name"),
            Indexe(index_id = "last_price"),
            Indexe(index_id = "price_change_ratio_pct"),
            Indexe(index_id = "up_down_stock_limit_up_reason"),
            ),
        code_selectors = CodeSelectors(intersection = listOf(Intersection(values = codes)))
    )
}

data class CodeSelectors(
    val intersection: List<Intersection>
)

data class PageInfo(
    val page_begin: Int = 0,
    val page_size: Int = 100
)

data class Sort(
    val idx: Int = 2,
    val type: String = "DESC"
)

data class Intersection(
    val type: String = "stock_code",
    val values: List<String>
)

data class SpecificDataResponse(
    val data: SpecificData,
    val status_code: Int,
    val status_msg: String
)

data class SpecificData(
    val data: List<DataX>,
    val indexes: List<Indexe>,
    val total: Int
)

data class DataX(
    val code: String,
    val values: List<Value>
)

data class Indexe(
    val attribute: Attribute? = null,
    val index_id: String,
    val time_type: String? = null,
    val timestamp: String? = null,
    val value_type: String? = null
)

data class Value(
    val idx: Int,
    val value: String
)

class Attribute
data class DZHRankResponse(
    val result: List<Map<String,Double>>
)


data class CLSRankResponse(
    val data: List<CLSData>,
    val errno: Int
)

data class CLSData(
    val atype: Int,
    val config_id: Int,
    val ctype: Int,
    val data_id: Int,
    val ranking_change: Int,
    val stock: CLSStock,
    val title: String
)

data class CLSStock(
    val RiseRange: Double,
    val StockID: String,
    val is_stib: Boolean,
    val last: Double,
    val name: String,
    val rise_range_has_null: Any,
    val schema: String,
    val status: String
)

data class DraganTigerDataResponse(
    val data : DragonTiger2Data,
    val status_code: Int,
    val status_msg: String
)

data class DragonTiger2Data(
    val count: Int,
    val items: List<DragonTiger2Item>,
    val module: String,
    val page: Int,
    val size: Int,
    val stock_count: Int
)

data class DragonTiger2Item(
    val buy_value: Double,
    val change: Double,
    val concept_list: List<Concept>,
    val hot_money_net_value: Double,
    val hot_rank: Int,
    val limit_reason: String,
    val market_id: String,
    val net_rate: Double,
    val net_value: Double,
    val org_net_value: Double,
    val range_days: Int,
    val sell_value: Double,
    val stock_code: String,
    val stock_name: String,
    val tags: List<DragonTiger2Tag>
)

data class Concept(
    val code: String,
    val market_id: String,
    val name: String
)

data class DragonTiger2Tag(
    val color: String,
    val id: Any,
    val name: String,
    val type: String
)


data class BDStockResponse(
    val QueryID: String,
    val Result: ResultBD,
    val ResultCode: String
)

data class ResultBD(
    val QueryID: String,
    val Result: List<ResultBDX>,
    val ResultCode: String,
    val ResultNum: String
)

data class ResultBDX(
    val ClickNeed: String,
    val DisplayData: DisplayData,
    val OriginSrcID: String,
    val RecoverCacheTime: String,
    val ResultURL: String,
    val Sort: String,
    val SrcID: String,
    val SubResNum: String,
    val SubResult: List<Any?>,
    val Weight: String
)

data class DisplayData(
    val StdStg: String,
    val StdStl: String,
    val resultData: ResultData,
    val strategy: Strategy
)

data class ResultData(
    val extData: ExtData,
    val tplData: TplData
)

data class Strategy(
    val ctplOrPhp: String,
    val hilightWord: String,
    val precharge: String,
    val tempName: String
)

data class ExtData(
    val OriginQuery: String,
    val resourceid: String,
    val tplt: String
)

data class TplData(
    val ResultURL: String,
    val StdStg: String,
    val StdStl: String,
    val cardName: String,
    val card_order: String,
    val data_source: String,
    val disp_data_url_ex: DispDataUrlEx,
    val encoding: String,
    val normal_use: String,
    val pk: List<Any?>,
    val result: ResultXX,
    val sigma_use: String,
    val strong_use: String,
    val templateName: String,
    val title: String,
    val weak_use: String
)

data class DispDataUrlEx(
    val aesplitid: String
)

data class ResultXX(
    val async_url: String,
    val brief: List<String>,
    val rank: List<Rank>,
    val ratio: Ratio,
    val totalDeal: TotalDeal
)

data class Rank(
    val code: String,
    val exchange: String,
    val expire_date: String,
    val financeType: String,
    val follow_status: String,
    val is_warrants: String,
    val list: List<Item8>,
    val market: String,
    val name: String,
    val sf_url: String,
    val status: String
)

data class TempWrapper(  val price: Float,  val chg: Float ,val amplitude: Float,val highest: Float,val lowest: Float)


data class RankWrapper(  val price: Float,  val chg: Float, val turnoverRate: Float,val amplitude: Float,)

data class Ratio(
    val balance: String,
    val down: String,
    val up: String
)

data class TotalDeal(
    val price: String,
    val status: String,
    val title: String
)

data class Item8(
    val text: String,
    val value: String
)


data class StockResponse(val date:Int,val list:List<List<Any>>)

