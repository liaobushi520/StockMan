package com.liaobusi.stockman.monitor

import com.liaobusi.stockman.monitor.network.MarketApiFactory
import com.liaobusi.stockman.monitor.sync.strategy.EastMoneyHistoryStockStrategy
import com.liaobusi.stockman.monitor.sync.strategy.HistoryStockFetchResult
import com.liaobusi.stockman.monitor.sync.strategy.HistoryStockStrategy
import com.liaobusi.stockman.monitor.sync.strategy.SohuHistoryStockStrategy
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.random.Random

class HistoryStockSync(private val database: StockDatabase) {
    private val logger = LoggerFactory.getLogger(HistoryStockSync::class.java)
    private val historyStrategies: List<HistoryStockStrategy> = listOf(
        SohuHistoryStockStrategy(MarketApiFactory.sohuApi()),
        EastMoneyHistoryStockStrategy(MarketApiFactory.eastMoneyHistoryApi())
    )
    private val sohuHistoryStrategy = historyStrategies.first { it.name == SOHU_HISTORY_SOURCE }
    private val eastMoneyHistoryStrategy = historyStrategies.first { it.name == EAST_MONEY_HISTORY_SOURCE }

    suspend fun syncRecentKLines(
        codes: List<String> = emptyList(),
        retry: Boolean = true
    ): HistorySyncStatus = withContext(Dispatchers.IO) {
        val endDate = historyEndDate()
        val stocks = if (codes.isNotEmpty()) {
            database.stockRefsByCodes(codes)
        } else {
            database.stockRefsPendingHistorySync(endDate, HISTORY_TARGET_ROWS)
        }
        logger.info(
            "History sync requested: stocks={}, codes={}, retry={}, endDate={}",
            stocks.size,
            codes.size,
            retry,
            endDate
        )
        syncStocks(
            stocks = stocks,
            source = "HistoryStrategy",
            retry = retry,
            endDate = endDate
        )
    }

    suspend fun syncRecentKLinesIncremental(
        codes: List<String> = emptyList(),
        retry: Boolean = true,
        full: Boolean = false,
        onProgress: suspend (HistorySyncProgress) -> Unit,
        shouldStop: () -> Boolean
    ): HistorySyncStatus = withContext(Dispatchers.IO) {
        val endDate = historyEndDate()
        val stocks = if (codes.isNotEmpty()) {
            database.stockRefsByCodes(codes)
        } else if (full) {
            database.stockRefs()
        } else {
            database.stockRefsPendingHistorySync(endDate, HISTORY_TARGET_ROWS)
        }
        val syncMode = if (full && codes.isEmpty()) "全量" else "增量"
        val total = stocks.size
        val completed = AtomicInteger(0)
        val success = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val scheduler = AdaptiveHistoryStrategyScheduler()
        val taskStartedAt = System.currentTimeMillis()
        onProgress(
            HistorySyncProgress(
                running = true,
                total = total,
                startedAt = taskStartedAt,
                lastMessage = "历史${syncMode}同步启动，串行同步，搜狐为主，东财每 ${EAST_MONEY_ASSIGNMENT_INTERVAL} 只试一次，连续失败会冷却"
            )
        )
        logger.info("Background history sync started: syncMode={}, stocks={}, retry={}, endDate={}, mode=serial", syncMode, total, retry, endDate)
        for ((stockIndex, stock) in stocks.withIndex()) {
            if (shouldStop()) {
                onProgress(
                    HistorySyncProgress(
                        running = false,
                        stopRequested = true,
                        total = total,
                        completed = completed.get(),
                        success = success.get(),
                        failed = failed.get(),
                        startedAt = taskStartedAt,
                        endedAt = System.currentTimeMillis(),
                        lastMessage = "已停止，成功 ${success.get()}，失败 ${failed.get()}，完成 ${completed.get()}/$total"
                    )
                )
                break
            }
            var finalResult = HistoryStockFetchResult("None", emptyList(), "not requested")
            var finalMessage = ""
            onProgress(
                HistorySyncProgress(
                    running = true,
                    total = total,
                    completed = completed.get(),
                    success = success.get(),
                    failed = failed.get(),
                    currentCode = stock.code,
                    currentName = stock.name,
                    startedAt = taskStartedAt,
                    lastMessage = "请求 ${stock.code} ${stock.name}"
                )
            )
            finalResult = if (shouldStop()) {
                finalResult
            } else {
                fetchHistoryByAdaptiveStrategies(
                    stock = stock,
                    stockIndex = stockIndex,
                    endDate = endDate,
                    retry = retry,
                    scheduler = scheduler,
                    shouldStop = shouldStop,
                    onProgressMessage = { source, message ->
                        finalMessage = message
                        onProgress(
                            HistorySyncProgress(
                                running = true,
                                total = total,
                                completed = completed.get(),
                                success = success.get(),
                                failed = failed.get(),
                                currentCode = stock.code,
                                currentName = stock.name,
                                currentSource = source,
                                startedAt = taskStartedAt,
                                lastMessage = message
                            )
                        )
                    }
                )
            }
            val codeSuccess = finalResult.histories.isNotEmpty()
            if (codeSuccess) {
                database.upsertHistoryForCode(finalResult.histories, finalResult.source, total)
                success.incrementAndGet()
            } else {
                failed.incrementAndGet()
            }
            database.upsertHistorySyncResult(
                HistoryCodeSyncResult(
                    code = stock.code,
                    name = stock.name,
                    status = if (codeSuccess) HistoryCodeSyncStatus.SUCCESS else HistoryCodeSyncStatus.FAILED,
                    source = finalResult.source,
                    rowCount = finalResult.histories.size,
                    message = if (codeSuccess) "" else finalResult.message.ifBlank { finalMessage.ifBlank { "empty history" } },
                    startDate = finalResult.histories.minOfOrNull { it.date } ?: 0,
                    endDate = finalResult.histories.maxOfOrNull { it.date } ?: endDate
                )
            )
            val done = completed.incrementAndGet()
            onProgress(
                HistorySyncProgress(
                    running = !shouldStop(),
                    stopRequested = shouldStop(),
                    total = total,
                    completed = done,
                    success = success.get(),
                    failed = failed.get(),
                    currentCode = stock.code,
                    currentName = stock.name,
                    currentSource = finalResult.source,
                    startedAt = taskStartedAt,
                    endedAt = if (done == total || shouldStop()) System.currentTimeMillis() else null,
                    lastMessage = "${stock.code} ${stock.name} ${if (codeSuccess) "成功" else "失败"}，${finalResult.source}，${finalResult.histories.size} 条"
                )
            )
            if (done % PROGRESS_LOG_INTERVAL == 0 || done == total) {
                logger.info("Background history sync progress: syncMode={}, completed={}/{}, success={}, failed={}", syncMode, done, total, success.get(), failed.get())
            }
        }
        logger.info("Background history sync finished: syncMode={}, total={}, completed={}, success={}, failed={}", syncMode, total, completed.get(), success.get(), failed.get())
        database.historySyncStatus()
    }

    private suspend fun syncStocks(
        stocks: List<StockRef>,
        source: String,
        retry: Boolean,
        endDate: Int
    ): HistorySyncStatus {
        val startedAt = System.currentTimeMillis()
        logger.info(
            "History sync started: source={}, stocks={}, retry={}, endDate={}",
            source,
            stocks.size,
            retry,
            endDate
        )
        val results = fetchHistoriesByStrategyChain(stocks = stocks, source = source, endDate = endDate, retry = retry)
        val histories = results
            .flatMap { it.histories }
            .distinctBy { "${it.code}-${it.date}" }
        logger.info(
            "History sync fetched: source={}, requestedStocks={}, rows={}, distinctCodes={}, endDate={}, elapsedMs={}",
            source,
            stocks.size,
            histories.size,
            histories.asSequence().map { it.code }.distinct().count(),
            endDate,
            System.currentTimeMillis() - startedAt
        )
        val sourceSummary = results
            .filter { it.histories.isNotEmpty() }
            .groupingBy { it.source }
            .eachCount()
            .entries
            .joinToString(",") { "${it.key}:${it.value}" }
            .ifBlank { source }
        return database.upsertHistoryFromSync(
            histories = histories,
            source = sourceSummary,
            requestedStocks = stocks.size
        ).also {
            logger.info(
                "History sync saved: sourceSummary={}, historyCount={}, lastDate={}, lastRows={}, lastStocks={}, requestedStocks={}",
                sourceSummary,
                it.historyCount,
                it.lastHistorySyncDate,
                it.lastHistorySyncCount,
                it.lastHistorySyncStockCount,
                it.lastHistorySyncRequestedStockCount
            )
        }
    }

    private suspend fun fetchHistoriesByStrategyChain(
        stocks: List<StockRef>,
        source: String,
        endDate: Int,
        retry: Boolean
    ): List<HistoryStockFetchResult> {
        val scheduler = AdaptiveHistoryStrategyScheduler()
        val results = mutableListOf<HistoryStockFetchResult>()
        stocks.forEachIndexed { index, stock ->
            val result = fetchHistoryByAdaptiveStrategies(
                stock = stock,
                stockIndex = index,
                endDate = endDate,
                retry = retry,
                scheduler = scheduler,
                shouldStop = { false },
                onProgressMessage = null
            )
            database.upsertHistorySyncResult(
                HistoryCodeSyncResult(
                    code = stock.code,
                    name = stock.name,
                    status = if (result.histories.isNotEmpty()) HistoryCodeSyncStatus.SUCCESS else HistoryCodeSyncStatus.FAILED,
                    source = result.source,
                    rowCount = result.histories.size,
                    message = if (result.histories.isNotEmpty()) "" else result.message.ifBlank { "empty history" },
                    startDate = result.histories.minOfOrNull { it.date } ?: 0,
                    endDate = result.histories.maxOfOrNull { it.date } ?: endDate
                )
            )
            if ((index + 1) % PROGRESS_LOG_INTERVAL == 0 || index + 1 == stocks.size) {
                logger.info(
                    "History sync progress: attempted={}/{}, source={}, eastMoneyCoolingDown={}",
                    index + 1,
                    stocks.size,
                    source,
                    scheduler.isEastMoneyCoolingDown()
                )
            }
            results.add(result)
        }
        return results
    }

    private suspend fun fetchHistoryByAdaptiveStrategies(
        stock: StockRef,
        stockIndex: Int,
        endDate: Int,
        retry: Boolean,
        scheduler: AdaptiveHistoryStrategyScheduler,
        shouldStop: () -> Boolean,
        onProgressMessage: (suspend (String, String) -> Unit)?
    ): HistoryStockFetchResult {
        val messages = mutableListOf<String>()
        for (strategy in scheduler.strategiesFor(stockIndex)) {
            if (shouldStop()) break
            onProgressMessage?.invoke(strategy.name, "${strategy.name} 请求 ${stock.code} ${stock.name}")
            val result = runCatching {
                delayBeforeHistoryRequest(strategy)
                fetchHistoryWithTransientRetry(
                    strategy = strategy,
                    stock = stock,
                    retry = retry && strategy.name != EAST_MONEY_HISTORY_SOURCE,
                    shouldStop = shouldStop
                )
            }
                .onFailure { error ->
                    logger.warn(
                        "History strategy failed: strategy={}, code={}, error={}",
                        strategy.name,
                        stock.code,
                        error.message
                    )
                }
                .fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        HistoryStockFetchResult(strategy.name, emptyList(), error.message ?: "unknown error")
                    }
                )
                .let { result ->
                    val validHistories = result.histories.filter { it.date <= endDate }
                    result.copy(histories = validHistories)
                }
            scheduler.recordResult(strategy, result)
            val message = if (result.histories.isNotEmpty()) {
                "${strategy.name} 成功 ${result.histories.size} 条，最新 ${result.histories.maxOfOrNull { it.date }}"
            } else {
                "${strategy.name} 失败/空数据: ${result.message.ifBlank { "empty history" }}"
            }
            messages += message
            onProgressMessage?.invoke(strategy.name, message)
            if (result.histories.isNotEmpty()) {
                return result
            }
        }
        return HistoryStockFetchResult("None", emptyList(), messages.joinToString(" | "))
    }

    private inner class AdaptiveHistoryStrategyScheduler {
        private var eastMoneyConsecutiveFailures = 0
        private var eastMoneyCooldownUntil = 0L

        fun strategiesFor(stockIndex: Int): List<HistoryStockStrategy> {
            val eastMoneyAvailable = !isEastMoneyCoolingDown()
            return when {
                eastMoneyAvailable && stockIndex % EAST_MONEY_ASSIGNMENT_INTERVAL == 0 -> {
                    listOf(eastMoneyHistoryStrategy, sohuHistoryStrategy)
                }
                eastMoneyAvailable -> {
                    listOf(sohuHistoryStrategy, eastMoneyHistoryStrategy)
                }
                else -> {
                    listOf(sohuHistoryStrategy)
                }
            }
        }

        fun recordResult(strategy: HistoryStockStrategy, result: HistoryStockFetchResult) {
            if (strategy.name != EAST_MONEY_HISTORY_SOURCE) return
            if (result.histories.isNotEmpty()) {
                if (eastMoneyConsecutiveFailures > 0 || eastMoneyCooldownUntil > 0L) {
                    logger.info("EastMoney history recovered: rows={}, previousFailures={}", result.histories.size, eastMoneyConsecutiveFailures)
                }
                eastMoneyConsecutiveFailures = 0
                eastMoneyCooldownUntil = 0L
                return
            }
            eastMoneyConsecutiveFailures += 1
            if (eastMoneyConsecutiveFailures >= EAST_MONEY_COOLDOWN_FAILURES) {
                eastMoneyCooldownUntil = System.currentTimeMillis() + EAST_MONEY_COOLDOWN_MS
                logger.warn(
                    "EastMoney history cooldown started: failures={}, cooldownMs={}, message={}",
                    eastMoneyConsecutiveFailures,
                    EAST_MONEY_COOLDOWN_MS,
                    result.message
                )
                eastMoneyConsecutiveFailures = 0
            }
        }

        fun isEastMoneyCoolingDown(): Boolean {
            val remainingMs = eastMoneyCooldownUntil - System.currentTimeMillis()
            return remainingMs > 0L
        }
    }

    companion object {
        private const val PROGRESS_LOG_INTERVAL = 200
        private const val HISTORY_TARGET_ROWS = 120
        private const val HISTORY_REQUEST_DELAY_MS = 100L
        private const val HISTORY_TRANSIENT_RETRY_COUNT = 2
        private const val SOHU_HISTORY_SOURCE = "SohuHisHq"
        private const val EAST_MONEY_HISTORY_SOURCE = "EastMoneyKLine"
        private const val EAST_MONEY_ASSIGNMENT_INTERVAL = 3
        private const val EAST_MONEY_COOLDOWN_FAILURES = 3
        private const val EAST_MONEY_COOLDOWN_MS = 60_000L
        private const val STOP_CHECK_DELAY_MS = 100L

        private fun historyEndDate(): Int {
            return ChinaMarketCalendar.currentHistoryTargetDate(LocalDateTime.now(RealtimeStockSync.CHINA_ZONE))
        }
    }

    private suspend fun delayBeforeHistoryRequest(strategy: HistoryStockStrategy) {
        logger.debug("History request delay: strategy={}, delayMs={}", strategy.name, HISTORY_REQUEST_DELAY_MS)
        delay(HISTORY_REQUEST_DELAY_MS)
    }

    private suspend fun fetchHistoryWithTransientRetry(
        strategy: HistoryStockStrategy,
        stock: StockRef,
        retry: Boolean,
        shouldStop: () -> Boolean
    ): HistoryStockFetchResult {
        var attempt = 0
        var lastError: Throwable? = null
        val maxAttempts = if (retry) HISTORY_TRANSIENT_RETRY_COUNT + 1 else 1
        while (attempt < maxAttempts) {
            if (shouldStop()) throw HistorySyncStoppedException()
            attempt += 1
            try {
                return strategy.fetchHistory(stock)
            } catch (error: Throwable) {
                lastError = error
                if (!retry || attempt >= maxAttempts || !error.isTransientHistoryFailure()) throw error
                val delayMs = (1000.0 * 2.0.pow((attempt - 1).toDouble())).toLong() + Random.nextLong(0, 300)
                logger.warn(
                    "History strategy transient failure, retrying: strategy={}, code={}, attempt={}/{}, delayMs={}, error={}",
                    strategy.name,
                    stock.code,
                    attempt,
                    maxAttempts,
                    delayMs,
                    error.message
                )
                delayWithStopCheck(delayMs, shouldStop)
            }
        }
        throw lastError ?: IllegalStateException("history sync failed")
    }

    private suspend fun delayWithStopCheck(delayMs: Long, shouldStop: () -> Boolean) {
        var remaining = delayMs
        while (remaining > 0) {
            if (shouldStop()) throw HistorySyncStoppedException()
            val step = minOf(remaining, STOP_CHECK_DELAY_MS)
            delay(step)
            remaining -= step
        }
    }

    private class HistorySyncStoppedException : RuntimeException("history sync stopped")

    private fun Throwable.isTransientHistoryFailure(): Boolean {
        val text = listOfNotNull(message, cause?.message).joinToString(" ").lowercase()
        return text.contains("503") ||
            text.contains("502") ||
            text.contains("429") ||
            text.contains("unexpected end of stream") ||
            text.contains("empty reply") ||
            text.contains("connection reset") ||
            text.contains("timeout") ||
            text.contains("timed out")
    }
}
