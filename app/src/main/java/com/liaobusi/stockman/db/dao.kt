package com.liaobusi.stockman.db

import androidx.room.*

@Dao
interface StockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(stock: List<Stock>)

    @Query("select * from stock where code in ( select code from follow where type=1) order by chg DESC")
    fun getFollowedStocks(): List<Stock>

    @Query("select * from stock where  chg>8.5 order by chg ASC")
    fun getWillZTStocks(): List<Stock>

    @Query("select * from stock where toMarketTime < :endTime AND toMarketTime >:startTime AND circulationMarketValue <= :highMarketValue AND circulationMarketValue >= :lowMarketValue  order by circulationMarketValue desc")
    fun getStock(
        startTime: Int,
        endTime: Int,
        lowMarketValue: Double,
        highMarketValue: Double
    ): List<Stock>

    @Query("select * from stock where name not like '%ST%' and price>1")
    fun getAllStock(): List<Stock>

    @Query("select * from stock where name not like '%ST%' and price>1 and toMarketTime <=:startTime")
    fun getAllStockByMarketTime(startTime: Int): List<Stock>

    @Query("select * from stock where  code=:code")
    fun getStockByCode(code: String): Stock

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


    @Query("select * from stock where code in ( select stockCode  from bkstock where bkCode=:bkCode ) AND  toMarketTime < :endTime AND toMarketTime >:startTime AND circulationMarketValue <= :highMarketValue AND circulationMarketValue >= :lowMarketValue ")
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
    fun getHistoryByDate3(code: String, date: Int): HistoryBK

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
    fun getHides(): List<Follow>

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
    fun getDIYBks():List<DIYBk>
    @Delete
    fun delete(item:DIYBk)


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

    @Query("select * from historystock where code=:code AND date >= :start AND date <= :end order by date desc")
    fun getHistoryByDate2(code: String, start: Int, end: Int): List<HistoryStock>

    @Query("select * from historystock where code=:code AND date =:date")
    fun getHistoryByDate3(code: String, date: Int ): HistoryStock

    @Query("select * from historystock where closePrice=0.0")
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
    fun getZTReplay(date: Int, code: String): ZTReplayBean

}

