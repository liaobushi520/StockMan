package com.liaobusi.stockman.db

import androidx.room.*
@Dao
interface ExpectHotDao{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun  insertAll(expectHots:List<ExpectHot>)

    @Query("select * from expecthot where date>=:date order by date ASC")
    fun getExpectHotList(date: Long):List<ExpectHot>


    @Query("select * from expecthot where bkCode==:bkCode AND date>=:date AND date<=:endTime order by date ASC  limit 5")
    fun getExpectHotListByCode(bkCode: String,date: Long,endTime: Long):List<ExpectHot>

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
interface PopularityRankDao{

    @Query("select * from popularityrank where date=:date order by rank DESC ")
    fun getRanksByDate(date: Int):List<PopularityRank>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(list:List<PopularityRank>)

    @Delete
    fun delete(list:List<PopularityRank>)


    @Transaction
    fun insertTransaction(date: Int,newList:List<PopularityRank>) {
        val list = getRanksByDate(date)
        delete(list)
        insert(newList)
    }


}
@Dao
interface DragonTigerDao{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(list:List<DragonTigerRank>)

    @Delete
    fun delete(list:List<DragonTigerRank>)

    @Query("select * from dragontigerrank where date=:date")
    fun getDragonTigerByDate(date: Int):List<DragonTigerRank>


    @Transaction
    fun insertTransaction(date: Int,newList:List<DragonTigerRank>) {
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
    fun getDIYBks():List<DIYBk>

    @Query("select * from diybk where code in (:codes)")
    fun getDIYBksByCodes(codes:List<String>):List<DIYBk>


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

    @Query("select * from historystock where date=:date AND chg>9.6")
    fun getZTHistoryByDate(date: Int):List<HistoryStock>

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

    @Query("select * from historystock where closePrice=0.0 or date<=20110504")
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

}

