package com.liaobusi.stockman.monitor

import com.liaobusi.stockman.monitor.network.MarketApiFactory
import com.liaobusi.stockman.monitor.sync.strategy.EastMoneyRealtimeStockStrategy
import com.liaobusi.stockman.monitor.sync.strategy.RealtimeStockStrategy
import com.liaobusi.stockman.monitor.sync.strategy.SinaRealtimeStockStrategy
import com.liaobusi.stockman.monitor.sync.strategy.fetchFirstSuccessfulSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RealtimeStockSync(private val database: StockDatabase) {
    private val eastMoneyApi = MarketApiFactory.eastMoneyRealtimeApi()
    private val sinaApi = MarketApiFactory.sinaApi()

    private val snapshotStrategies: List<RealtimeStockStrategy> = listOf(
        SinaRealtimeStockStrategy(sinaApi),
        EastMoneyRealtimeStockStrategy(eastMoneyApi)
    )

    suspend fun refreshRealtimeStocks(retry: Boolean = true): SyncStatus = withContext(Dispatchers.IO) {
        val snapshot = if (retry) {
            retryWithExponentialBackoff { fetchFirstSuccessfulSnapshot(snapshotStrategies) }
        } else {
            fetchFirstSuccessfulSnapshot(snapshotStrategies)
        }
        val stocks = snapshot.stocks.distinctBy { it.code }
        check(stocks.size > FULL_MARKET_STOCK_MIN_COUNT) { "realtime stock snapshot is incomplete: ${stocks.size}" }
        database.replaceStocksFromSync(
            stocks = stocks,
            date = snapshot.date,
            clearBeforeInsert = true,
            source = snapshot.source
        )
        database.syncStatus()
    }

    suspend fun refreshRealtimeStocksIfScheduled(): SyncStatus? {
        val now = LocalDateTime.now(CHINA_ZONE)
        if (!now.isTradingWeekday()) return null

        val today = now.toLocalDate().format(BASIC_DATE).toInt()
        val slot = currentSlot(now.toLocalTime()) ?: return null
        val status = database.syncStatus()
        val slotKey = "$today-$slot"
        if (status.lastStockSyncSlot == slotKey && status.lastStockSyncCount != null && status.lastStockSyncCount > FULL_MARKET_STOCK_MIN_COUNT) {
            return null
        }
        return refreshRealtimeStocks().also { database.markDailyStockSlot(slotKey) }
    }

    suspend fun initializeRealtimeStocksIfEmpty(): SyncStatus? {
        return if (database.stockCount() < 1000) refreshRealtimeStocks(retry = true) else null
    }

    private fun LocalDateTime.isTradingWeekday(): Boolean {
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
    }

    private fun currentSlot(time: LocalTime): String? {
        return REALTIME_SYNC_TIMES.lastOrNull { time >= it }?.format(SLOT_FORMATTER)
    }

    companion object {
        val CHINA_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val REALTIME_SYNC_TIMES: List<LocalTime> = listOf(LocalTime.of(9, 25), LocalTime.of(12, 10), LocalTime.of(15, 10))
        val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        private val SLOT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmm")
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
