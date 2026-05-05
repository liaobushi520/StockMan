package com.liaobusi.stockman.db

import androidx.room.*
import com.liaobusi.stockman.Injector
import com.liaobusi.stockman.epochSecondsInCnTradingSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Dao
interface UnusualActionHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(unusualActionHistories: List<UnusualActionHistory>)

    @Query("select * from unusualactionhistory where time>=:start AND time<=:end order by time DESC")
    fun getHistories(start: Long, end: Long): List<UnusualActionHistory>

    @Query("select * from unusualactionhistory where time>=:start AND time<=:end AND type ==:type order by time DESC")
    fun getHistories(start: Long, end: Long, type: Int): List<UnusualActionHistory>

    @Delete
    fun delete(list: List<UnusualActionHistory>)

    /** stocks 为逗号分隔或为单码；用首尾逗号包裹避免子串误匹配（如 600 命中 600519） */
    @Query(
        "select * from unusualactionhistory where (',' || stocks || ',') like '%,' || :code || ',%' order by time desc"
    )
    fun listContainingStockCode(code: String): List<UnusualActionHistory>

    /** 统计关联股票用：限定时间段 + 仅异动源（1/2/3/4） */
    @Query(
        """
        select * from unusualactionhistory
        where time>=:start AND time<=:end
          AND type in (1,2,3,4)
          AND (',' || stocks || ',') like '%,' || :code || ',%'
        order by time desc
        """
    )
    fun listContainingStockCodeInRange(code: String, start: Long, end: Long): List<UnusualActionHistory>

    /** 两只股票在同一条异动中同时出现（限定时间段 + 仅异动源 1/2/3/4） */
    @Query(
        """
        select * from unusualactionhistory
        where time>=:start AND time<=:end
          AND type in (1,2,3,4)
          AND (',' || stocks || ',') like '%,' || :codeA || ',%'
          AND (',' || stocks || ',') like '%,' || :codeB || ',%'
        order by time desc
        """
    )
    fun listContainingTwoStockCodesInRange(
        codeA: String,
        codeB: String,
        start: Long,
        end: Long
    ): List<UnusualActionHistory>
}

@Dao
interface ExpectHotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(expectHots: List<ExpectHot>)

    @Query("select * from expecthot where date>=:date order by date ASC")
    fun getExpectHotList(date: Long): List<ExpectHot>


    @Query("select * from expecthot where bkCode==:bkCode AND date>=:date AND date<=:endTime order by date ASC  limit 5")
    fun getExpectHotListByCode(bkCode: String, date: Long, endTime: Long): List<ExpectHot>

}


@Dao
interface StockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(stock: List<Stock>)

    @Query("select * from stock where code in ( select code from follow where type=1) order by chg DESC")
    fun getFollowedStocks(): List<Stock>

    @Query("select * from stock where  chg>8.5 order by chg ASC")
    fun getWillZTStocks(): List<Stock>

    @Query("select * from stock where toMarketTime < :endTime AND toMarketTime >:startTime AND circulationMarketValue <= :highMarketValue AND circulationMarketValue >= :lowMarketValue AND name NOT LIKE '%退%'  order by circulationMarketValue desc")
    fun getStock(
        startTime: Int,
        endTime: Int,
        lowMarketValue: Double,
        highMarketValue: Double
    ): List<Stock>

    @Query("select * from stock where name not like '%ST%' and price>1")
    fun getAllStock(): List<Stock>

    @Query("select * from stock where name not like '%ST%' and price>1 and toMarketTime <=:startTime")
    fun getAllStockByMarketTimeNoST(startTime: Int): List<Stock>

    @Query("select * from stock where toMarketTime <=:startTime")
    fun getAllStockByMarketTime(startTime: Int): List<Stock>

    @Query("select * from stock where  code=:code")
    fun getStockByCode(code: String): Stock

    @Query("select * from stock where  code IN (:codes)")
    fun getStockByCodes(codes: List<String>): List<Stock>

    @Query("select * from stock where name  like '%ST%' ")
    fun getSTStock(): List<Stock>

}

@Dao
interface PopularityRankDao {

    @Query("select * from popularityrank where date=:date order by rank DESC ")
    fun getRanksByDate(date: Int): List<PopularityRank>

    @Query("select * from popularityrank where code = :code and date >= :minDate and date <= :maxDate")
    fun getByCodeAndDateRange(code: String, minDate: Int, maxDate: Int): List<PopularityRank>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(list: List<PopularityRank>)

    @Delete
    fun delete(list: List<PopularityRank>)


    @Transaction
    fun insertTransaction(date: Int, newList: List<PopularityRank>) {
        val list = getRanksByDate(date)
        delete(list)
        insert(newList)
    }


}

@Dao
interface DragonTigerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(list: List<DragonTigerRank>)

    @Delete
    fun delete(list: List<DragonTigerRank>)

    @Query("select * from dragontigerrank where date=:date")
    fun getDragonTigerByDate(date: Int): List<DragonTigerRank>


    @Transaction
    fun insertTransaction(date: Int, newList: List<DragonTigerRank>) {
        val list = getDragonTigerByDate(date)
        delete(list)
        insert(newList)
    }

}


@Dao
interface BKDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(stock: List<BK>)

    @Query("select * from bk")
    fun getAllBK(): List<BK>

    @Query("select * from bk where code in ( select code from follow where type=2) order by chg DESC")
    fun getFollowedBKS(): List<BK>

    @Query("select * from bk where code not in ( select code from hide where type=2) order by chg DESC")
    fun getVisibleBKS(): List<BK>


    @Query("select * from bk where code=:code")
    fun getBKByCode(code: String): BK?


    @Query("select * from bk where name=:name")
    fun getBKByName(name: String): BK?

    @Query("select * from bk where type=0")
    fun getTradeBKs(): List<BK>

    @Query("select * from bk where type=1")
    fun getConceptBKs(): List<BK>


}


@Dao
interface GDRSDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(list: List<GDRS>)


    @Query("select * from gdrs where code=:code order by endDate DESC")
    fun getGDRSByCode(code: String): List<GDRS>


    @Query("select * from gdrs")
    fun getAll(): List<GDRS>

}

@Dao
interface AnalysisBeanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(analysisBean: AnalysisBean)

    @Query("select * from analysisbean where date>=:startDate order by date ASC ")
    fun getAnalysisBeans(startDate: Int = 0): List<AnalysisBean>
}

@Dao
interface BKStockDao {
    @Query("select * from stock where code in ( select stockCode  from bkstock where bkCode=:bkCode )")
    fun getStocksByBKCode(bkCode: String): List<Stock>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(bkStocks: List<BKStock>)

    @Query("select * from bkstock")
    fun getAll(): List<BKStock>


    @Query("select * from stock where code in ( select stockCode  from bkstock where bkCode=:bkCode ) AND  toMarketTime < :endTime AND toMarketTime >:startTime AND circulationMarketValue <= :highMarketValue AND circulationMarketValue >= :lowMarketValue AND name NOT LIKE '%退%'")
    fun getStocksByBKCode2(
        bkCode: String,
        startTime: Int,
        endTime: Int,
        lowMarketValue: Double,
        highMarketValue: Double
    ): List<Stock>
}

@Dao
interface HistoryBKDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHistory(historyStocks: List<HistoryBK>)

    @Query("select * from historybk where code=:code AND date >= :start AND date <= :end order by date desc")
    fun getHistoryRange(code: String, start: Int, end: Int): List<HistoryBK>

    @Query("select * from historybk where code=:code order by date desc")
    fun getHistory(code: String): List<HistoryBK>

    @Query("select * from historybk where code=:code AND date>=:date   order by date asc limit 1")
    fun getHistoryByDate(code: String, date: Int): HistoryBK


    @Query("select * from historybk where code=:code AND date > :date order by date asc limit :limit")
    fun getHistoryAfter(code: String, date: Int, limit: Int = 5): List<HistoryBK>

    @Query("select * from historybk where code=:code AND date < :date order by date desc limit :limit")
    fun getHistoryBefore(code: String, date: Int, limit: Int = 5): List<HistoryBK>


    @Query("select * from historybk where code=:code AND date=:date   order by date asc limit 1")
    fun getHistoryByDate2(code: String, date: Int): HistoryBK?


    @Query("select * from historybk where code=:code AND date<=:date   order by date desc limit 1")
    fun getHistoryByDate3(code: String, date: Int): HistoryBK?

    @Query("select max(closePrice) from historybk where code=:code AND date <= :end AND date>= :start ")
    fun getHistoryHighestPrice(code: String, start: Int, end: Int): Float


}

@Dao
interface FollowDao {

    @Query("select * from follow where type=1")
    fun getFollowStocks(): List<Follow>

    @Query("select * from follow where type=2")
    fun getFollowBks(): List<Follow>

    @Query("select * from follow ")
    fun getFollows(): List<Follow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFollow(follow: Follow)

    @Delete
    fun deleteFollow(follow: Follow)

}

@Dao
interface HideDao {

    @Query("select * from hide where type=1")
    fun getHideStocks(): List<Hide>

    @Query("select * from hide where type=2")
    fun getHideBks(): List<Hide>

    @Query("select * from hide ")
    fun getHides(): List<Hide>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHide(hide: Hide)

    @Delete
    fun deleteHide(hide: Hide)

}

@Dao
interface DIYBkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(bean: DIYBk)

    @Query("select * from diybk")
    fun getDIYBks(): List<DIYBk>

    @Query("select * from diybk where code in (:codes)")
    fun getDIYBksByCodes(codes: List<String>): List<DIYBk>


    @Delete
    fun delete(item: DIYBk)


}


@Dao
interface HistoryStockDao {

    @Query("select * from historystock where code=:code AND date >= :date order by date desc")
    fun getHistoryAfter(code: String, date: Int): List<HistoryStock>

    @Query("select max(closePrice) from historystock where code=:code AND date <= :end AND date>= :start ")
    fun getHistoryHighestPrice(code: String, start: Int, end: Int): Float

    @Query("select min(closePrice) from historystock where code=:code AND date <= :end AND date>= :start ")
    fun getHistoryLowestPrice(code: String, start: Int, end: Int): Float

    @Query("select * from historystock where code=:code AND date <= :date order by date desc")
    fun getHistoryBefore(code: String, date: Int): List<HistoryStock>

    @Query("select * from historystock where code=:code AND date <= :date order by date desc limit :limit")
    fun getHistoryBefore3(code: String, date: Int, limit: Int = 5): List<HistoryStock>

    @Query("select * from historystock where date=:date AND chg>9.6")
    fun getZTHistoryByDate(date: Int): List<HistoryStock>

    @Query("select * from historystock where code=:code AND date > :date order by date asc limit :limit")
    fun getHistoryAfter3(code: String, date: Int, limit: Int = 5): List<HistoryStock>


    @Query("select * from historystock where chg>9.6 AND code=:code  AND date >= :start AND date<:end order by date desc")
    fun getZTHistoryStock(code: String, start: Int, end: Int): List<HistoryStock>

    @Query("select * from historystock where code=:code AND date >= :start AND date <= :end order by date desc")
    fun getHistoryRange(code: String, start: Int, end: Int): List<HistoryStock>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHistory(historyStocks: List<HistoryStock>)

    @Delete()
    fun deleteHistory(historyStocks: List<HistoryStock>)

    @Query("select * from historystock where  date >= :start AND date <= :end order by date desc")
    fun getHistoryByDate(start: Int, end: Int): List<HistoryStock>

    /**
     * 仅拉取指定代码在区间内的日 K，用于大范围扫描时跳过 ST 等无需参与的股票。
     * [codes] 单次不宜过长（建议分批），避免超出 SQLite 绑定上限。
     */
    @Query(
        "select * from historystock where date >= :start AND date <= :end AND code IN (:codes) order by date desc",
    )
    fun getHistoryByDateForCodes(start: Int, end: Int, codes: List<String>): List<HistoryStock>

    @Query("select * from historystock where code=:code AND date >= :start AND date <= :end order by date desc")
    fun getHistoryByDate2(code: String, start: Int, end: Int): List<HistoryStock>

    @Query("select * from historystock where code=:code AND date =:date")
    fun getHistoryByDate3(code: String, date: Int): HistoryStock

    @Query("select * from historystock where closePrice=0.0 or date<=20110504  or date>21001010")
    fun getErrorHistory(): List<HistoryStock>

    @Transaction
    fun deleteErrorHistory() {
        val errorHistoryList = getErrorHistory()
        deleteHistory(errorHistoryList)
    }


}


@Dao
interface ZTReplayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(list: List<ZTReplayBean>)

    @Query("select * from ztreplaybean where date=:date AND code=:code limit 1")
    fun getZTReplay(date: Int, code: String): ZTReplayBean?

    @Query("select * from ztreplaybean where code = :code order by date desc")
    fun listByCodeOrderByDateDesc(code: String): List<ZTReplayBean>

    @Query(
        """
        DELETE FROM ztreplaybean
        WHERE (expound IS NULL OR expound = '')
        AND (expound2 IS NULL OR expound2 = '')
        AND (expound3 IS NULL OR expound3 = '')
        AND (reason2 IS NULL OR reason2 = '')
        """
    )
    fun deleteWhereExpoundExpound2Reason2AllEmpty(): Int

}

@Dao
interface StockLinkageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(linkage: StockLinkage)

    @Query("select * from StockLinkage where code = :code limit 1")
    fun getByCode(code: String): StockLinkage?
}

/** 将异动 stocks 字符串中的多只股票两两写入关联（与已有记录合并） */
fun mergeStockLinkageFromStocksCsv(csv: String) {
    val dao = Injector.appDatabase.stockLinkageDao()
    val codes = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (codes.size < 2) return
    for (code in codes) {
        val others = codes.filter { it != code }.toSet()
        val row = dao.getByCode(code)
        val existing =
            row?.relatedCodes?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                ?: emptySet()
        val merged = (existing + others).sorted().joinToString(",")
        dao.upsert(StockLinkage(code, merged))
    }
}

/** 当前股 + 联动表中的关联码，供 Strategy4 板块入参；无关联则 null */
fun strongLinkCodesCsvForStrategy(stockCode: String): String? {
    val row = Injector.appDatabase.stockLinkageDao().getByCode(stockCode) ?: return null
    val rel = row.relatedCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (rel.isEmpty()) return null
    return (listOf(stockCode) + rel).distinct().joinToString(",")
}

/** UnusualActionHistory.time 为秒；日历日回溯窗口（与同页关联股票列表统计一致）。 */
internal fun unusualActionHistoryEpochRange(endDateYmd: Int, daysBack: Int): Pair<Long, Long> {
    val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val endDate: Date = runCatching { sdf.parse(endDateYmd.toString()) }.getOrNull() ?: Date()

    val calEnd = Calendar.getInstance().apply {
        time = endDate
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }

    val calStart = Calendar.getInstance().apply {
        time = endDate
        add(Calendar.DATE, -daysBack)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val startSec = calStart.timeInMillis / 1000
    val endSec = calEnd.timeInMillis / 1000
    return startSec to endSec
}

/**
 * 近 [daysBack] 天（至 [endDateYmd]）异动共现次数降序的关联股票代码（与关联股票页算法一致）。
 */
fun relatedStockCodesByCooccurrenceInRange(
    stockCode: String,
    endDateYmd: Int,
    daysBack: Int,
): List<String> {
    val (startSec, endSec) = unusualActionHistoryEpochRange(endDateYmd, daysBack)
    val histories = Injector.appDatabase.unusualActionHistoryDao()
        .listContainingStockCodeInRange(stockCode, startSec, endSec)
        .filter { epochSecondsInCnTradingSession(it.time) }
    val counts = mutableMapOf<String, Int>()
    histories.forEach { h ->
        val codes = h.stocks.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        codes.forEach { c ->
            if (c == stockCode) return@forEach
            counts[c] = (counts[c] ?: 0) + 1
        }
    }
    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }
}

/**
 * 在新加自选时：按近 [daysBack] 天关联度排序，继承第一个「已自选且标记色非 0」的关联股的 color；否则 0。
 */
fun linkedColorFromRecentRelatedFollows(
    stockCode: String,
    endDateYmd: Int,
    daysBack: Int,
): Int {
    val ordered = relatedStockCodesByCooccurrenceInRange(stockCode, endDateYmd, daysBack)
    if (ordered.isEmpty()) return 0
    val followMap = Injector.appDatabase.followDao().getFollowStocks().associateBy { it.code }
    return ordered.firstNotNullOfOrNull { c ->
        followMap[c]?.takeIf { it.color != 0 }?.color
    } ?: 0
}

