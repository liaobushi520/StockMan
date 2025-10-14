package com.liaobusi.stockman.db

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.room.*
import com.liaobusi.stockman.Injector
import com.liaobusi.stockman.STOCK_GREEN
import com.liaobusi.stockman.isFPSource
@Entity()
data class UnusualActionHistory(@PrimaryKey val time: Long, val comment: String, val stocks: String)


@Entity(primaryKeys = ["id"])
data class ExpectHot(val id: String,val bkCode: String, val summary: String, val date: Long, val expireTime: Long, val themeCode: String)


@Entity(primaryKeys = ["date"])
data class AnalysisBean(
    val date: Int,
    @ColumnInfo(defaultValue = "0") val ztCount: Int,
    @ColumnInfo(defaultValue = "0") val dtCount: Int,
    @ColumnInfo(defaultValue = "0") val highestLianBanCount: Int,
    @ColumnInfo(defaultValue = "0") val stZTCount: Int,
    @ColumnInfo(defaultValue = "0") val stDTCount: Int,
    @ColumnInfo(defaultValue = "0") val stHighestLianBanCount: Int
)


@Entity(primaryKeys = ["code", "endDate"])
data class GDRS(
    val code: String,
    val endDate: Long,
    val totalNumRatio: Float,
    @ColumnInfo(defaultValue = "0") val holderTotalNum: Int
)


@Entity(primaryKeys = ["code", "date"])
data class HistoryBK(
    val code: String,
    val date: Int,
    val closePrice: Float,
    val openPrice: Float,
    val highest: Float,
    val lowest: Float,
    val chg: Float,
    val amplitude: Float,
    val turnoverRate: Float,
    @ColumnInfo(defaultValue = "-1.0")
    val yesterdayClosePrice: Float,
)

val HistoryBK.color: Int
    get() {
        return if (chg > 0) {
            Color.RED
        } else if (chg < 0) {
            STOCK_GREEN
        } else {
            Color.GRAY
        }
    }


val HistoryBK.DY: Boolean
    get() {
        return this.chg >= 3
    }

@Entity
data class BK(
    @PrimaryKey val code: String,
    val name: String,
    val price: Float,
    val chg: Float,
    val amplitude: Float,
    val turnoverRate: Float,
    val highest: Float,
    val lowest: Float,
    val circulationMarketValue: Float,
    val openPrice: Float,
    val yesterdayClosePrice: Float,
    @ColumnInfo(defaultValue = "-1")
    val type: Int //0:行业  1:概念  2:上证指数
)

val BK.specialBK: Boolean
    get() {
        return code == "BK0612" || code == "BK0528" || code == "BK0804" || code == "BK0742" || code == "BK0498" || code == "BK1051" || code == "BK1050" || code == "BK0816" || code == "BK0707" || code == "BK0815" || code == "BK1053" || code == "BK0867" || code == "BK0500" || code == "BK0610" || code == "BK0636" || code == "BK0596"
    }


fun BK.openWeb(context: Context) {
    val market = if (code == "000001") 1 else 90
    val s = "dfcft://stock?market=${market}&code=${this.code}"
    val uri: Uri = Uri.parse(s)
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

@Entity(primaryKeys = ["bkCode", "stockCode"])
data class BKStock(val bkCode: String, val stockCode: String)

@Entity
data class Follow(
    @PrimaryKey val code: String,
    val type: Int,
    @ColumnInfo(defaultValue = "1") val stickyOnTop: Int = 1
)

@Entity
data class Hide(@PrimaryKey val code: String, val type: Int)


/***
 * @param name 名称
 * @param amplitude 振幅
 * @param turnoverRate 换手率
 * @param chg 涨跌幅
 * @param highest 当天最高价
 * @param lowest 当天最低价
 * @param circulationMarketValue 流通市值
 * @param toMarketTime 上市时间
 */
@Entity
data class Stock(
    @PrimaryKey val code: String,
    val name: String,
    val price: Float,
    val chg: Float,
    val amplitude: Float,
    val turnoverRate: Float,
    val highest: Float,
    val lowest: Float,
    val circulationMarketValue: Float,
    val toMarketTime: Int,
    val openPrice: Float,
    val yesterdayClosePrice: Float,


    @ColumnInfo(defaultValue = "-1.0")
    val ztPrice: Float,
    @ColumnInfo(defaultValue = "-1.0")
    val dtPrice: Float,
    @ColumnInfo(defaultValue = "-1.0")
    val averagePrice: Float,
    @ColumnInfo(defaultValue = "")
    val bk: String,

    )


fun Stock.openWeb(context: Context) {
    val s = "dfcf18://stock?market=${this.marketCode()}&code=${this.code}"
    val uri: Uri = Uri.parse(s)
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

fun Stock.openDragonTigerRank(context: Context) {
    val s =
        "amihexin://backwash_userid=ApUBS&backwash_source=wxhy&backwash_hxapp=gsc&url=https%253A%252F%252Fdata.10jqka.com.cn%252Fmobile%252Ftransaction%252Findex.html%253FreOpen%253DstockDetail%2526stockCode%253D${this.code}&live_app=app_0&backwash_id=open"
    val uri: Uri = Uri.parse(s)
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

//主板
fun Stock.isMainBoard(): Boolean {
    return this.code.startsWith(
        "600" +
                ""
    ) || this.code.startsWith("601") || this.code.startsWith("603") || this.code.startsWith("605") || this.code.startsWith(
        "000"
    ) || this.code.startsWith("002") || this.code.startsWith("001") || this.code.startsWith("003")
}

//创业板
fun Stock.isChiNext(): Boolean {
    return this.code.startsWith("300") || this.code.startsWith("301")
}

//科创板
fun Stock.isSTARMarket(): Boolean {
    return this.code.startsWith("688") || this.code.startsWith("689")
}

//北交所
fun Stock.isBJStockExchange(): Boolean {
    return this.code.startsWith("82") || this.code.startsWith("83") || this.code.startsWith("87") || this.code.startsWith(
        "88"
    ) || this.code.startsWith("43") || this.code.startsWith("92")
}

fun Stock.isST(): Boolean {
    return name.startsWith("ST") || name.startsWith("*")
}


fun Stock.marketCode(): Int {
    return if (this.code.startsWith("000") || this.code.startsWith("300") || this.code.startsWith("301") || this.code.startsWith(
            "002"
        ) || this.code.startsWith("001") || this.code.startsWith("003")
    ) 0 else if (this.isBJStockExchange()) 0 else 1
}

@Entity(primaryKeys = ["code", "date"])
data class HistoryStock(
    val code: String,
    val date: Int,
    val closePrice: Float,
    val openPrice: Float,
    val highest: Float,
    val lowest: Float,
    val chg: Float,
    val amplitude: Float,
    val turnoverRate: Float,

    @ColumnInfo(defaultValue = "-1.0")
    val ztPrice: Float,
    @ColumnInfo(defaultValue = "-1.0")
    val dtPrice: Float,
    @ColumnInfo(defaultValue = "-1.0")
    val yesterdayClosePrice: Float,
    @ColumnInfo(defaultValue = "-1.0")
    val averagePrice: Float,
)

val HistoryStock.color: Int
    get() {
        return if (chg > 0) {
            Color.RED
        } else if (chg < 0) {
            STOCK_GREEN
        } else {
            Color.GRAY
        }
    }

val HistoryStock.ZT: Boolean
    get() {
        if (this.ztPrice == -1f) {
            if (this.code.startsWith("300") || this.code.startsWith("688") || this.code.startsWith("301")) {
                return this.chg > 19
            } else {
                return this.chg > 9.65
            }
        }
        return this.ztPrice > 0 && this.closePrice == this.ztPrice &&this.chg>0
    }

val HistoryStock.DT: Boolean
    get() {
        if (this.dtPrice == -1f) {
            if (this.code.startsWith("300") || this.code.startsWith("688") || this.code.startsWith("301")) {
                return this.chg < -19
            } else {
                return this.chg < -9.6
            }
        }
        return this.closePrice == this.dtPrice&&chg<0
    }

val HistoryStock.DY: Boolean
    get() {
        if (this.code.startsWith("300") || this.code.startsWith("688") || this.code.startsWith("301")) {
            return this.chg >= 10
        } else {
            return this.chg >= 5
        }
    }


val HistoryStock.friedPlate: Boolean
    get() {
        if (this.ztPrice == -1f) {
            return false
        }
        return this.highest >= this.ztPrice && this.closePrice < this.highest
    }

val HistoryStock.longUpShadow: Boolean
    get() {
        if (chg <= 0 || yesterdayClosePrice < 0) {
            return false
        }
        val h = (highest - yesterdayClosePrice) * 100 / yesterdayClosePrice
        val c = (closePrice - yesterdayClosePrice) * 100 / yesterdayClosePrice
        val o = (openPrice - yesterdayClosePrice) * 100 / yesterdayClosePrice

        return h > 6.5 && h / c >= 2 && o <= c
    }


@Database(
    entities = [Stock::class, HistoryStock::class, BK::class, HistoryBK::class, BKStock::class, Follow::class, GDRS::class, Hide::class, AnalysisBean::class, ZTReplayBean::class, DIYBk::class, PopularityRank::class, DragonTigerRank::class, ExpectHot::class, UnusualActionHistory::class],
    version = 31,
    autoMigrations = [
        AutoMigration(from = 30, to = 31)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stockDao(): StockDao
    abstract fun historyStockDao(): HistoryStockDao
    abstract fun bkDao(): BKDao
    abstract fun historyBKDao(): HistoryBKDao
    abstract fun bkStockDao(): BKStockDao

    abstract fun followDao(): FollowDao

    abstract fun gdrsDao(): GDRSDao

    abstract fun hideDao(): HideDao

    abstract fun analysisBeanDao(): AnalysisBeanDao

    abstract fun ztReplayDao(): ZTReplayDao

    abstract fun diyBkDao(): DIYBkDao

    abstract fun popularityRankDao(): PopularityRankDao

    abstract fun dragonTigerDao(): DragonTigerDao

    abstract fun expectHotDao(): ExpectHotDao

    abstract fun unusualActionHistoryDao(): UnusualActionHistoryDao


//    @DeleteTable.Entries(value = [DeleteTable(tableName = "BK"),DeleteTable(tableName = "HistoryBK")])
//    class MyAutoMigration : AutoMigrationSpec


}

@Entity(primaryKeys = ["date", "code"])
data class ZTReplayBean(
    val date: Int,
    val code: String,
    val reason: String,
    val groupName: String,
    val expound: String,
    @ColumnInfo(defaultValue = "--:--:--") val time: String,
    @ColumnInfo(defaultValue = "") val groupName2: String = "",
    @ColumnInfo(defaultValue = "") val reason2: String = "",
    @ColumnInfo(defaultValue = "") val expound2: String = "",
)

val ZTReplayBean.isYiZIBan: Boolean
    get() {
        try {
            val s = time.replace(":", "").toInt()
            if (s <= 93000) {
                return true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return false
    }


val ZTReplayBean.groupNameV: String
    get() {
        if (isFPSource(Injector.context)) {
            if (groupName.isEmpty()){
                return  groupName2
            }
            return groupName
        } else {
            return if (groupName2.isEmpty()) {
                return groupName
            } else {
                groupName2
            }
        }
    }


val ZTReplayBean.reasonV: String
    get() {
        if (isFPSource(Injector.context)) {
            if (groupName.isEmpty()){
                return reason2
            }
            return reason
        } else {
            return if (groupName2.isEmpty()) {
                return reason
            } else {
                reason2
            }
        }
    }


val ZTReplayBean.expoundV: String
    get() {
        if (isFPSource(Injector.context)) {
            if (groupName.isEmpty()) {
                return expound2
            }
            return expound
        } else {
            return if (groupName2.isEmpty()) {
                return expound
            } else {
                expound2
            }
        }
    }


@Entity
data class DIYBk(
    @PrimaryKey val code: String,
    val name: String,
    val bkCodes: String,
    @ColumnInfo(defaultValue = "") val dsp: String,
    @ColumnInfo(defaultValue = "") val stockCodes: String=""
)


data class FPResponse(
    val data: List<Data2>,
    val errCode: String,
    val msg: String,
    val serverTime: Int
)

data class Data2(
    val action_field_id: String,
    val count: Int,
    val create_time: String,
    val date: String?,
    val delete_time: Any,
    val is_delete: String,
    val list: List<ArticleWrap>?,
    val name: String,
    val reason: String?=null,
    val sort_no: Int,
    val status: Int,
    val update_time: Any
)

data class ArticleWrap(
    val article: Article,
    val code: String,
    val name: String
)

data class Article(
    val action_info: ActionInfo,
    val article_id: String,
    val comment_count: Int,
    val create_time: String,
    val forward_count: Int,
    val is_like: Int,
    val is_step: Int,
    val like_count: Int,
    val step_count: Int,
    val title: String,
    val user: User,
    val user_id: String
)

data class ActionInfo(
    val action_field_id: String,
    val action_info_id: String,
    val article_id: String,
    val create_time: String,
    val day: Int,
    val delete_time: Any,
    val edition: Int,
    val expound: String,
    val is_crawl: Int,
    val is_delete: String,
    val is_recommend: Int,
    val num: String,
    val price: Int,
    val reason: Any,
    val shares_range: Double,
    val sort_no: Int,
    val stock_id: String,
    val time: String?,
    val update_time: Any
)

data class User(
    val avatar: String,
    val nickname: String,
    val user_id: String
)

@Entity(primaryKeys = ["date", "code"])
data class PopularityRank(
    val code: String,
    val date: Int,
    val rank: Int,
    @ColumnInfo(defaultValue = "-1") val thsRank: Int,
    @ColumnInfo(defaultValue = "-1") val tgbRank: Int,
    @ColumnInfo(defaultValue = "无") val explain: String,
    @ColumnInfo(defaultValue = "-1") val dzhRank: Int,
    @ColumnInfo(defaultValue = "-1") val clsRank: Int
)

@Entity(primaryKeys = ["date", "code"])
data class DragonTigerRank(
    val code: String,
    val date: Int,
    val explanation: String,
    @ColumnInfo(defaultValue = "") val tags: String = ""
)


data class THSFPResponse(
    val data: List<THSFPData>,
    val status_code: Int,
    val status_msg: String
)

data class THSFPData(
    val change: Double,
    val code: String,
    val continuous_plate_num: Int,
    val days: Int,
    val high: String,
    val high_num: Int,
    val limit_up_num: Int,
    val name: String,
    val stock_list: List<THSFPStock>
)

data class THSFPStock(
    val change_rate: Double,
    val change_tag: String,
    val code: String,
    val concept: String,
    val continue_num: Int,
    val first_limit_up_time: String,
    val high: String,
    val high_days: Int,
    val is_new: Int,
    val is_st: Int,
    val last_limit_up_time: String,
    val latest: Double,
    val market_id: Int,
    val market_type: String,
    val name: String,
    val reason_info: String,
    val reason_type: String
)


data class AllTabListResponse(
    val data: AllTabListData,
    val status_code: Int,
    val status_msg: String,
    val trace_id: String
)

data class AllTabListData(
    val tab_list: List<Tab>
)

data class Tab(
    val date: String,
    val tab_data: List<TabData>,
    val tab_name: String
)

data class TabData(
    val abnormal_reason: String?,
    val concept: String,
    val market: String,
    val reason: String,
    val stock_code: String,
    val stock_name: String,
    val ths_code: String
)