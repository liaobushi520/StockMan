package com.liaobusi.stockman.monitor

import com.liaobusi.stockman.monitor.network.MarketApiFactory
import com.liaobusi.stockman.monitor.sync.strategy.EastMoneyRealtimeStockStrategy
import com.liaobusi.stockman.monitor.sync.strategy.RealtimeStockStrategy
import com.liaobusi.stockman.monitor.sync.strategy.SinaRealtimeStockStrategy
import com.liaobusi.stockman.monitor.sync.strategy.fetchFirstSuccessfulSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RealtimeStockSync(private val database: StockDatabase) {
    private val eastMoneyApi = MarketApiFactory.eastMoneyRealtimeApi()
    private val sinaApi = MarketApiFactory.sinaApi()

    private val realtimeSnapshotStrategies: List<RealtimeStockStrategy> = listOf(
        SinaRealtimeStockStrategy(sinaApi)
    )
    private val eastMoneyCloseSnapshotStrategy = EastMoneyRealtimeStockStrategy(eastMoneyApi)

    suspend fun refreshRealtimeStocks(source: RealtimeRefreshSource, retry: Boolean = false): SyncStatus {
        return when (source) {
            RealtimeRefreshSource.SINA -> refreshRealtimeStocks(retry)
            RealtimeRefreshSource.EAST_MONEY -> refreshEastMoneyRealtimeStocks(updateBk = true, retry = retry)
        }
    }

    suspend fun refreshRealtimeStocks(retry: Boolean = true): SyncStatus = withContext(Dispatchers.IO) {
        val snapshot = if (retry) {
            retryWithExponentialBackoff { fetchFirstSuccessfulSnapshot(realtimeSnapshotStrategies) }
        } else {
            fetchFirstSuccessfulSnapshot(realtimeSnapshotStrategies)
        }
        val stocks = snapshot.stocks.distinctBy { it.code }
        check(stocks.size > FULL_MARKET_STOCK_MIN_COUNT) { "realtime stock snapshot is incomplete: ${stocks.size}" }
        val syncDate = resolveRealtimeTradingDate(snapshot.date)
        database.replaceStocksFromSync(
            stocks = stocks,
            date = syncDate,
            clearBeforeInsert = false,
            source = snapshot.source,
            preserveExistingBk = true
        )
        database.syncStatus()
    }

    suspend fun refreshRealtimeStocksIfScheduled(): SyncStatus? {
        val now = LocalDateTime.now(CHINA_ZONE)
        if (!ChinaMarketCalendar.isTradingDay(now.toLocalDate())) return null

        val today = now.toLocalDate().format(BASIC_DATE).toInt()
        val slot = currentSlot(now.toLocalTime()) ?: return null
        val status = database.syncStatus()
        val slotKey = "$today-$slot"
        if (status.lastStockSyncSlot == slotKey && status.lastStockSyncCount != null && status.lastStockSyncCount > FULL_MARKET_STOCK_MIN_COUNT) {
            return null
        }
        return refreshRealtimeStocks().also { database.markDailyStockSlot(slotKey) }
    }

    suspend fun refreshEastMoneyRealtimeStocksIfScheduled(): SyncStatus? {
        val now = LocalDateTime.now(CHINA_ZONE)
        if (!ChinaMarketCalendar.isTradingDay(now.toLocalDate())) return null

        val today = now.toLocalDate().format(BASIC_DATE).toInt()
        val slot = currentEastMoneySlot(now.toLocalTime()) ?: return null
        val slotKey = "$today-eastmoney-$slot"
        if (database.hasEastMoneyRealtimeSlot(slotKey)) return null

        return refreshEastMoneyRealtimeStocks(updateBk = false, retry = false).also {
            database.markEastMoneyRealtimeSlot(slotKey)
        }
    }

    suspend fun initializeRealtimeStocksIfEmpty(): SyncStatus? {
        return if (database.stockCount() < 1000) refreshRealtimeStocks(retry = true) else null
    }

    suspend fun refreshEastMoneyRealtimeStocks(updateBk: Boolean, retry: Boolean = true): SyncStatus = withContext(Dispatchers.IO) {
        val snapshot = if (retry) {
            retryWithExponentialBackoff { eastMoneyCloseSnapshotStrategy.fetchSnapshot() }
        } else {
            eastMoneyCloseSnapshotStrategy.fetchSnapshot()
        }
        val stocks = snapshot.stocks.distinctBy { it.code }
        check(stocks.size > FULL_MARKET_STOCK_MIN_COUNT) { "eastmoney close snapshot is incomplete: ${stocks.size}" }
        database.replaceStocksFromSync(
            stocks = stocks,
            date = resolveRealtimeTradingDate(snapshot.date),
            clearBeforeInsert = updateBk,
            source = snapshot.source,
            preserveExistingBk = !updateBk
        )
        database.syncStatus()
    }

    private fun currentSlot(time: LocalTime): String? {
        return REALTIME_SYNC_TIMES.lastOrNull { time >= it }?.format(SLOT_FORMATTER)
    }

    private fun currentEastMoneySlot(time: LocalTime): String? {
        return EAST_MONEY_SYNC_TIMES
            .firstOrNull { slot -> !time.isBefore(slot) && time.isBefore(slot.plusMinutes(EAST_MONEY_SYNC_WINDOW_MINUTES)) }
            ?.format(SLOT_FORMATTER)
    }

    private fun resolveRealtimeTradingDate(sourceDate: Int): Int {
        if (sourceDate > 0) return ChinaMarketCalendar.normalizeTradingDate(sourceDate)
        return ChinaMarketCalendar.currentRealtimeSnapshotDate(LocalDateTime.now(CHINA_ZONE))
    }

    companion object {
        val CHINA_ZONE = ChinaMarketCalendar.zone
        val REALTIME_SYNC_TIMES: List<LocalTime> = listOf(LocalTime.of(9, 25), LocalTime.of(12, 10), LocalTime.of(15, 10))
        val EAST_MONEY_SYNC_TIMES: List<LocalTime> = listOf(LocalTime.of(9, 25), LocalTime.of(14, 55))
        private const val EAST_MONEY_SYNC_WINDOW_MINUTES: Long = 15
        val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        private val SLOT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmm")
    }
}

enum class RealtimeRefreshSource {
    SINA,
    EAST_MONEY;

    companion object {
        fun from(value: String): RealtimeRefreshSource {
            return when (value.trim().lowercase()) {
                "eastmoney", "east_money", "em", "dfcf" -> EAST_MONEY
                else -> SINA
            }
        }
    }
}

suspend fun <T> retryWithExponentialBackoff(
    attempts: Int = 1,
    initialDelayMillis: Long = 1_000,
    block: suspend () -> T
): T {
    var nextDelay = initialDelayMillis
    var lastError: Throwable? = null
    repeat(attempts) { index ->
        val result = runCatching { block() }
        if (result.isSuccess) return result.getOrThrow()
        lastError = result.exceptionOrNull()
        if (index < attempts - 1) {
            delay(nextDelay)
            nextDelay *= 2
        }
    }
    throw lastError ?: IllegalStateException("retry failed")
}
