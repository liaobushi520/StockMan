package com.liaobusi.stockman.db

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.*




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
    val type:Int //0:行业  1:概念  2:上证指数
)

val BK.specialBK: Boolean
    get() {
        return code == "BK1051" || code == "BK1050" || code=="BK0816" ||code=="BK0815"  || code=="BK1053" || code=="BK0867" ||code=="BK0500" ||code=="BK0610" || code=="BK0636"
    }


fun BK.openWeb(context: Context) {
    val market=if(code=="000001") 1 else 90
    val  s= "dfcft://stock?market=${market}&code=${this.code}"
    val uri: Uri = Uri.parse(s)
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

@Entity(primaryKeys = ["bkCode","stockCode"])
data class BKStock(  val bkCode:String,  val stockCode:String)

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
    val  s="dfcft://stock?market=${this.marketCode()}&code=${this.code}"
    val uri: Uri = Uri.parse(s)
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

fun Stock.marketCode():Int{
    return if( this.code.startsWith("000")|| this.code.startsWith("300")|| this.code.startsWith("301") ||this.code.startsWith("002")|| this.code.startsWith("001")|| this.code.startsWith("003")) 0 else 1
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

val HistoryStock.ZT: Boolean
    get() {
        if (this.ztPrice == -1f) {
            if (this.code.startsWith("300") || this.code.startsWith("688")) {
                return this.chg > 19
            } else {
                return this.chg > 9.6
            }
        }
        return this.ztPrice>0&&this.closePrice == this.ztPrice
    }

val HistoryStock.DT: Boolean
    get() {
        if (this.dtPrice == -1f) {
            if (this.code.startsWith("300") || this.code.startsWith("688")) {
                return this.chg < -19
            } else {
                return this.chg < -9.6
            }
        }
        return this.closePrice == this.dtPrice
    }

val HistoryStock.DY: Boolean
    get() {
        if (this.code.startsWith("300") || this.code.startsWith("688")) {
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
    entities = [Stock::class, HistoryStock::class, BK::class, HistoryBK::class,BKStock::class], version = 10,
    autoMigrations = [
        AutoMigration(from = 9, to = 10)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stockDao(): StockDao
    abstract fun historyStockDao(): HistoryStockDao
    abstract fun bkDao(): BKDao
    abstract fun historyBKDao(): HistoryBKDao
    abstract fun bkStockDao():BKStockDao


//    @DeleteTable.Entries(value = [DeleteTable(tableName = "BK"),DeleteTable(tableName = "HistoryBK")])
//    class MyAutoMigration : AutoMigrationSpec


}

