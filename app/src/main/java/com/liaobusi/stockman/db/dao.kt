package com.liaobusi.stockman.db

import androidx.room.*

@Dao
interface StockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(stock: List<Stock>)

    @Query("select * from stock where toMarketTime < :endTime AND toMarketTime >:startTime AND circulationMarketValue <= :highMarketValue AND circulationMarketValue >= :lowMarketValue AND name not like '%ST%'  order by circulationMarketValue desc")
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

    @Query("select * from bk where code=:code")
    fun getBKByCode(code: String): BK

    @Query("select * from bk where type=0")
    fun getTradeBKs(): List<BK>

    @Query("select * from bk where type=1")
    fun getConceptBKs(): List<BK>


}

@Dao
interface BKStockDao {
    @Query("select * from stock where code in ( select stockCode  from bkstock where bkCode=:bkCode )")
    fun getStocksByBKCode(bkCode: String): List<Stock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(bkStocks: List<BKStock>)

    @Query("select * from bkstock")
    fun getAll(): List<BKStock>


    @Query("select * from stock where code in ( select stockCode  from bkstock where bkCode=:bkCode ) AND  toMarketTime < :endTime AND toMarketTime >:startTime AND circulationMarketValue <= :highMarketValue AND circulationMarketValue >= :lowMarketValue AND name not like '%ST%' ")
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


    @Query("select max(closePrice) from historybk where code=:code AND date <= :end AND date>= :start ")
    fun getHistoryHighestPrice(code: String, start: Int, end: Int): Float


}

@Dao
interface FollowDao{

    @Query("select * from follow where type=1")
    fun getFollowStocks():List<Follow>

    @Query("select * from follow where type=2")
    fun getFollowBks():List<Follow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFollow(follow: Follow)

    @Delete
    fun deleteFollow(follow: Follow)

}


@Dao
interface HistoryStockDao {

    @Query("select * from historystock where code=:code AND date <= :date order by date desc")
    fun getHistoryAfter(code: String, date: Int): List<HistoryStock>

    @Query("select max(closePrice) from historystock where code=:code AND date <= :end AND date>= :start ")
    fun getHistoryHighestPrice(code: String, start: Int, end: Int): Float

    @Query("select min(closePrice) from historystock where code=:code AND date <= :end AND date>= :start ")
    fun getHistoryLowestPrice(code: String, start: Int, end: Int): Float

    @Query("select * from historystock where code=:code AND date >= :date order by date desc")
    fun getHistoryBefore(code: String, date: Int): List<HistoryStock>

    @Query("select * from historystock where code=:code AND date > :date order by date asc limit :limit")
    fun getHistoryBefore2(code: String, date: Int, limit: Int = 5): List<HistoryStock>

    @Query("select * from historystock where code=:code AND date <=:date order by date desc limit :limit")
    fun getHistoryAfter2(code: String, date: Int, limit: Int = 10): List<HistoryStock>


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

    @Query("select * from historystock where closePrice=0.0")
    fun getErrorHistory(): List<HistoryStock>

    @Transaction
    fun deleteErrorHistory() {
        val errorHistoryList = getErrorHistory()
        deleteHistory(errorHistoryList)
    }


}

