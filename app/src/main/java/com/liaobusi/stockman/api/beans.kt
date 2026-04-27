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
    val tag: Tag?,
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
    val remark: String?,
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

fun getDefaultSpecificDataBean(codes: List<String>): SpecificDataBean {
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
    val result: List<Map<String, Double>>
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
    val data: DragonTiger2Data,
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

data class TempWrapper(
    val price: Float,
    val chg: Float,
    val amplitude: Float,
    val highest: Float,
    val lowest: Float
)


data class RankWrapper(
    val price: Float,
    val chg: Float,
    val turnoverRate: Float,
    val amplitude: Float,
)

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


data class StockResponse(val date: Int, val list: List<List<Any>>)

data class ExpectHotResponse(
    val code: Int,
    val costTime: Int,
    val data: List<ExpectHotData>,
    val message: String,
    val requestId: String,
    val reserve: Any
)

data class ExpectHotData(
    val date: Long,
    val expireTime: Long,
    val isNew: Int,
    val summary: String,
    val theme: List<Theme>
)

data class Theme(
    val code: String,
    val f3: Double,
    val name: String
)

data class ExpectHotParam(
    val client: String = "web",
    val clientType: String = "cfw",
    val clientVersion: String = "8.3",
    val randomCode: String = "XyuKF8DSJNhfne3N",
    val timestamp: Long = System.currentTimeMillis()
)


data class StockEventData(val total: Int, val diff: Map<String, EMDiffBean>)

data class StockEventBean(
    val data: StockEventData?,
    val dlmkts: String,
    val full: Int,
    val lt: Int,
    val rc: Int,
    val rt: Int,
    val svr: Int
)


data class HotTopicParam(
    val pageUrl: String="https://vipmoney.eastmoney.com/collect/app_ranking/ranking/app.html?hashcode=_1760158430337&market=&appfenxiang=1#/stock",
    val parm: String="{\"deviceid\":\"C1C1C88D75DF3F58430A09741E1681C5\",\"version\":\"180\",\"product\":\"EastMoney\",\"plat\":\"Android\",\"gubaCode\":\"SZ300260,SH600111,SH601606,SH603690,SH688585,SZ002549,SZ000981,SH603011,SZ002208,SH601727,SZ000969,SZ300059,SZ000021,SH600635,SH601611,SH601212,SH600362,SZ301013,SZ002156,SH603300,SH600519,SZ000006,SH600105,SH688981,SZ002941,SH601899,SH600021,SH601126,SZ301120,SZ300450,SZ002050,SZ000063,SH601011,SH600089,SZ002338,SZ002366,SH600268,SZ002460,SH601127,SH600490,SH603156,SH600010,SZ002475,SZ002600,SZ000831,SH600810,SZ000977,SH601992,SZ002084,SZ002241,SZ002910,SH601138,SZ002298,SZ002291,SH603530,SZ002074,SH600167,SH601669,SZ000045,SZ300750,SZ300748,SH600382,SH603363,SH600078,SZ000685,SH600895,SZ002493,SZ920509,SH603993,SH603799,SH600545,SH600801,SZ002631,SZ002409,SZ002029,SZ002709,SZ000878,SH688256,SH600960,SZ002052,SH600580,SZ300274,SH600403,SZ000559,SZ000058,SH601218,SH603889,SH603986,SH600584,SH601279,SZ002734,SZ002402,SZ300250,SZ000737,SH605169,SH600376,SH601868,SZ000819,SZ002594,SZ300014\"}",
    val path: String="newtopic/api/Topic/GubaCodeHotTopicNewRead",
    val track: String="tanzhen_sys_1760159354320"
)

data class HotTopicParamBean(val gubaCode: String,val deviceid: String="C1C1C88D75DF3F58430A09741E1681C5",val version: String="180",val product: String="EastMoney",val plat: String="Android")

data class HotTopicResponse(val RData: String)

data class RDataBean(val re: Map<String,List<RDataInnerBean>>)

data class RDataInnerBean(val name: String,val summary: String)

data class ZhiBoResponse(
    val JHJJYD: List<Any>,
    val List: List<Item0>,
    val Notice: String,
    val Status: Int,
    val Time: Int,
    val date: String,
    val errcode: String,
    val ttag: Double
)

data class Item0(
    val BoomReason: String,
    val Comment: String,
    val DisStock: List<List<String>>,
    val ID: String,
    val Image: String,
    val Interpretation: String,
    val IsChart: String,
    val PlateCode: String,
    val PlateJE: String,
    val PlateName: String,
    val PlateZDF: String,
    val ShareData: ShareData,
    val Stock: List<List<Any>>,
    val ThemeClassInfo: List<Any>,
    val ThemeInfo: List<Any>,
    val Time: Int,
    val Type: String,
    val UID: String,
    val UserName: String,
    val styleIndex: List<StyleIndex>
)

class ShareData

data class StyleIndex(
    val type: String,
    val typeName: String,
    val typeNum: String
)

data class CLSLiveResponse(
    val data: List<CLSLiveData>,
    val errno: Int,
    val msg: String
)

data class CLSLiveData(
    val article_id: Int,
    val audio: String,
    val brief: String,
    val comment_num: Int,
    val ctime: Int,
    val float: String,
    val gray_share: Int,
    val guide_text: String,
    val images: List<String>,
    val img: String,
    val plate_change: Double,
    val plate_code: String,
    val plate_name: String,
    val reading_num: Int,
    val rec_vip_article_title: String,
    val rec_vip_icon: String,
    val rec_vip_id: Int,
    val rec_vip_type: Int,
    val share_img: String,
    val share_num: Int,
    val share_url: String,
    val stock_list: List<CLSStock>,
    val title: String,
    val type: Int
)
data class LimitUpPoolResponse(
    val data: LimitUpPoolData?,
    val status_code: Int,
    val status_msg: String
)

data class LimitUpPoolData(
    val date: String,
    val info: List<Info>,
    val limit_down_count: LimitDownCount,
    val limit_up_count: LimitUpCount,
    val msg: Any,
    val page: Page,
    val trade_status: TradeStatus
)

data class Info(
    val change_rate: Double,
    val change_tag: String,
    val code: String,
    val currency_value: Double,
    val first_limit_up_time: String,
    val high_days: String,
    val high_days_value: Int,
    val is_again_limit: Int,
    val is_new: Int,
    val last_limit_up_time: String,
    val latest: Double,
    val limit_up_suc_rate: Double,
    val limit_up_type: String,
    val market_id: Int,
    val market_type: String,
    val name: String,
    val open_num: Int,
    val order_amount: Double,
    val order_volume: Double,
    val reason_type: String,
    val time_preview: List<Double>,
    val turnover_rate: Double
)

data class LimitDownCount(
    val today: Today,
    val yesterday: Yesterday
)

data class LimitUpCount(
    val today: Today,
    val yesterday: Yesterday
)

data class Page(
    val count: Int,
    val limit: Int,
    val page: Int,
    val total: Int
)

data class TradeStatus(
    val end_time: String,
    val id: String,
    val name: String,
    val start_time: String
)

data class Today(
    val history_num: Int,
    val num: Int,
    val open_num: Int,
    val rate: Double
)

data class Yesterday(
    val history_num: Int,
    val num: Int,
    val open_num: Int,
    val rate: Double
)

data class LimitDownPoolResponse(
    val data: LimitDownPoolData?,
    val status_code: Int,
    val status_msg: String
)

data class LimitDownPoolData(
    val date: String,
    val info: List<LimitDownPoolInfo>,
    val limit_down_count: LimitDownCount,
    val limit_up_count: LimitUpCount,
    val msg: Any,
    val page: Page,
    val trade_status: TradeStatus
)

data class LimitDownPoolInfo(
    val change_rate: Double,
    val change_tag: String,
    val code: String,
    val currency_value: Double,
    val first_limit_down_time: String,
    val high_days_value: Any,
    val is_again_limit: Int,
    val is_new: Int,
    val last_limit_down_time: String,
    val latest: Double,
    val market_id: Int,
    val market_type: String,
    val name: String,
    val turnover_rate: Double
)


data class CXStockDataResponse(
    val data : CXData,
    val status: Boolean
)

data class CXData(
    val list: List<CXItem>,
    val page: Int,
    val pages: Int,
    val timestamp: Long
)

data class CXItem(
    val amplitude: Double,
    val avgPrice: Double,
    val changeRate: Float,
    val curPrice: Float,
    val downLimit: Double,
    val floatValue: Double,
    val hasCollected: Boolean,
    val highPrice: Double,
    val isDelisted: Boolean,
    val logoUrl: String,
    val lowPrice: Double,
    val openPrice: Double,
    val preClosePrice: Double,
    val priceBookRatio: Double,
    val priceEarningRatio: Double,
    val priceUpdown1: Double,
    val sortIndex: Int,
    val stkCode: String,
    val stkShortName: String,
    val stkUniCode: Int,
    val todayIsTrade: Boolean,
    val totValue: Double,
    val tradeAmut: Double,
    val tradeDate: Long,
    val tradeVol: Int,
    val turnoverRate: Double,
    val upLimit: Double,
    val volRatio: Double
)







data class BDResponse(
    val QueryID: String,
    val Result: BDResult,
    val ResultCode: Int
)

data class BDResult(
    val list: BDList
)

data class BDList(
    val body: List<BDBody>,
    val headers: List<BDHeader>
)

data class BDBody(
    val amount: String,
    val amplitude: String,
    val code: String,
    val exchange: String,
    val financeType: String,
    val followStatus: Int,
    val lastPx: String,
    val logo: Logo,
    val market: String,
    val marketValue: String,
    val name: String,
    val pxChange: String,
    val pxChangeRate: String,
    val rawData: RawData,
    val turnoverRatio: String,
    val volume: String
)

data class BDHeader(
    val canSort: Int,
    val key: String,
    val name: String
)

data class Logo(
    val logo: String,
    val type: String
)

data class RawData(
    val amount: Long,
    val amplitude: Double,
    val lastPx: Float,
    val marketValue: Long,
    val pxChange: Double,
    val pxChangeRate: Float,
    val turnoverRatio: Double,
    val volume: Double
)





data class CallWarnParam(
    val filter: Filter = Filter(),
    val page_num: Int = 1,
    val page_size: Int = 20,
    val sort: Int = 0,
    val sort_field: String = ""
)

data class Filter(
    val market: String = "1",
    val optionals: Any?=null,
    val st: Int = 0,
    val warn_types: String = ""
)

data class CallWarnResponse(
    val data : CallWarnData,
    val status_code: Int,
    val status_msg: String
)

data class CallWarnData(
    val current_page: Int,
    val data_list: List<List<Any>>,
    val header_list: List<Header>,
    val total: Int
)

data class Header(
    val code: String,
    val name: String,
    val type: String
)


class THSYDResponse : ArrayList<THSYDResponseItem>()

data class THSYDResponseItem(
    val ctime: Int,
    val info: List<THSInfo>,
    val isdraw: Int
)

data class THSInfo(
    val analysisContent: String?=null,
    val analysisUrl: String,
    val bkname: String,
    val id: String,
    val importantPoint: ImportantPoint,
    val isdraw: Int,
    val newsurl: Any,
    val sentiment: Int,
    val seq: String,
    val stocklist: List<Stocklist>,
    val time: Int,
    val title: String
)

data class ImportantPoint(
    val capital: Long,
    val cate: String,
    val change: Double,
    val isShow: Int,
    val limitCnt: Int
)

data class Stocklist(
    val capitalFlow: String,
    val dzf: String,
    val marketId: String,
    val recdzf: String,
    val stockCode: String,
    val stockName: String
)







