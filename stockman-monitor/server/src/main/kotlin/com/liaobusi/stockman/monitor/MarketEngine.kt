package com.liaobusi.stockman.monitor

import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Collections
import kotlin.math.sin
import kotlin.random.Random

class MarketEngine {
    private val logger = LoggerFactory.getLogger(MarketEngine::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { encodeDefaults = true }
    private val sessions = Collections.synchronizedSet(mutableSetOf<DefaultWebSocketSession>())
    private val seeds = listOf(
        StockSeed("600519", "贵州茅台", 1688.00, baseChg = 0.2, circulationMarketValue = 18200.0, toMarketTime = 20010827, bk = "白酒,消费"),
        StockSeed("300750", "宁德时代", 192.40, limitRate = 0.2, baseChg = -0.1, circulationMarketValue = 8200.0, toMarketTime = 20180611, bk = "锂电池,新能源车"),
        StockSeed("000001", "平安银行", 10.48, baseChg = 0.0, circulationMarketValue = 2030.0, toMarketTime = 19910403, bk = "银行"),
        StockSeed("002594", "比亚迪", 211.60, baseChg = 0.4, circulationMarketValue = 6100.0, toMarketTime = 20110630, bk = "新能源车,电池"),
        StockSeed("688981", "中芯国际", 49.32, limitRate = 0.2, baseChg = 0.1, circulationMarketValue = 3900.0, toMarketTime = 20200716, bk = "半导体,芯片")
    )
    val database = StockDatabase()
    private val realtimeStockSync = RealtimeStockSync(database)
    private val historyStockSync = HistoryStockSync(database)
    private val trackers = seeds.associate { it.code to Tracker() }
    private val stocks: MutableMap<String, StockTick>
    private val alerts = ArrayDeque<AlertEvent>()
    private var step = 0
    @Volatile private var historyProgress = HistorySyncProgress()
    @Volatile private var stopHistorySyncRequested = false
    private var historySyncJob: Job? = null

    init {
        database.initialize(seeds)
        logger.info("MarketEngine initialized: database={}", database.path())
        stocks = database.getStocks().associateBy { it.code }.toMutableMap()
        scope.launch {
            runCatching { realtimeStockSync.initializeRealtimeStocksIfEmpty() }
                .onSuccess { status ->
                    if (status != null) reloadStocksAndBroadcast()
                }
                .onFailure { logger.warn("Stock sync failed: {}", it.message) }
        }
        scope.launch {
            while (isActive) {
                delay(60_000)
                runCatching { realtimeStockSync.refreshRealtimeStocksIfScheduled() }
                    .onSuccess { status ->
                        if (status != null) reloadStocksAndBroadcast()
                    }
                    .onFailure { logger.warn("Stock sync failed: {}", it.message) }
            }
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                if (stocks.size <= seeds.size) {
                    simulate()
                    broadcast(ClientMessage(type = "stocks", stocks = stocks.values.sortedBy { it.code }))
                }
            }
        }
    }

    suspend fun connect(session: DefaultWebSocketSession) {
        sessions.add(session)
        session.send(json.encodeToString(ClientMessage(type = "snapshot", stocks = stocks.values.sortedBy { it.code })))
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Close) break
            }
        } finally {
            sessions.remove(session)
        }
    }

    fun snapshot(): MonitorSnapshot {
        return MonitorSnapshot(
            stocks = stocks.values.sortedBy { it.code },
            alerts = alerts.toList().asReversed()
        )
    }

    suspend fun manualTick(request: ManualTickRequest): StockTick? {
        val seed = seeds.firstOrNull { it.code == request.code } ?: return null
        val tick = when {
            request.price != null -> seed.tickByPrice(request.price)
            request.chg != null -> seed.tick(request.chg)
            else -> return null
        }
        accept(tick)
        broadcast(ClientMessage(type = "stocks", stocks = stocks.values.sortedBy { it.code }))
        return tick
    }

    suspend fun refreshRealtimeStocksNow(): SyncStatus {
        logger.info("Manual realtime stock sync requested")
        val status = realtimeStockSync.refreshRealtimeStocks(retry = false)
        reloadStocksAndBroadcast()
        logger.info("Manual realtime stock sync completed: count={}, source={}", status.lastStockSyncCount, status.lastStockSyncSource)
        return status
    }

    fun syncStatus(): SyncStatus = database.syncStatus()

    suspend fun syncHistoryStocksNow(codes: List<String>): HistorySyncStatus {
        logger.info("Manual history stock sync requested: codes={}", codes.size)
        return historyStockSync.syncRecentKLines(codes = codes, retry = true)
            .also { logger.info("Manual history stock sync completed: date={}, count={}, stocks={}, source={}", it.lastHistorySyncDate, it.lastHistorySyncCount, it.lastHistorySyncStockCount, it.lastHistorySyncSource) }
    }

    fun historySyncStatus(): HistorySyncStatus = database.historySyncStatus()

    fun startHistoryStocksSync(codes: List<String> = emptyList(), full: Boolean = false): HistorySyncProgress {
        val runningJob = historySyncJob
        if (runningJob?.isActive == true) return historyProgress
        stopHistorySyncRequested = false
        historyProgress = HistorySyncProgress(running = true, lastMessage = if (full) "历史全量同步排队中" else "历史增量同步排队中")
        historySyncJob = scope.launch {
            runCatching {
                historyStockSync.syncRecentKLinesIncremental(
                    codes = codes,
                    retry = true,
                    full = full && codes.isEmpty(),
                    onProgress = { progress -> historyProgress = progress },
                    shouldStop = { stopHistorySyncRequested }
                )
            }.onFailure { error ->
                logger.warn("Background history stock sync failed: {}", error.message)
                historyProgress = historyProgress.copy(
                    running = false,
                    failed = historyProgress.failed + 1,
                    endedAt = System.currentTimeMillis(),
                    lastMessage = "同步异常: ${error.message ?: "unknown error"}"
                )
            }.onSuccess {
                historyProgress = historyProgress.copy(
                    running = false,
                    endedAt = System.currentTimeMillis(),
                    lastMessage = if (stopHistorySyncRequested) {
                        "同步已停止，成功 ${historyProgress.success}，失败 ${historyProgress.failed}"
                    } else {
                        "同步完成，成功 ${historyProgress.success}，失败 ${historyProgress.failed}"
                    }
                )
            }
        }
        return historyProgress
    }

    fun stopHistoryStocksSync(): HistorySyncProgress {
        stopHistorySyncRequested = true
        historyProgress = historyProgress.copy(stopRequested = true, lastMessage = "正在停止，当前请求结束后生效")
        return historyProgress
    }

    fun historyStocksSyncProgress(): HistorySyncProgress = historyProgress

    private suspend fun simulate() {
        step += 1
        seeds.forEachIndexed { index, seed ->
            val previous = stocks.getValue(seed.code)
            val wave = sin((step + index * 9) / 7.0) * 0.35
            val noise = Random.nextDouble(-0.12, 0.12)
            val pulse = when {
                seed.code == "000001" && step % 45 in 8..12 -> 1.15
                seed.code == "002594" && step % 80 == 20 -> 3.2
                seed.code == "600519" && step % 70 == 30 -> 9.98
                seed.code == "600519" && step % 70 == 31 -> 8.7
                seed.code == "300750" && step % 90 == 40 -> -19.95
                seed.code == "300750" && step % 90 == 41 -> -17.2
                else -> 0.0
            }
            val nextChg = if (pulse != 0.0) pulse else previous.chg + wave + noise
            accept(seed.tick(nextChg))
        }
    }

    private suspend fun accept(tick: StockTick) {
        stocks[tick.code] = tick
        seeds.firstOrNull { it.code == tick.code }?.let { seed ->
            database.upsertTick(seed, tick)
        }
        val alert = trackers[tick.code]?.update(tick) ?: return
        alerts.addLast(alert)
        while (alerts.size > 50) alerts.removeFirst()
        broadcast(ClientMessage(type = "alert", alert = alert))
    }

    private suspend fun broadcast(message: ClientMessage) {
        val payload = json.encodeToString(message)
        sessions.toList().forEach { session ->
            runCatching { session.send(payload) }.onFailure { sessions.remove(session) }
        }
    }

    private suspend fun reloadStocksAndBroadcast() {
        stocks.clear()
        stocks.putAll(database.getStocks().associateBy { it.code })
        broadcast(ClientMessage(type = "stocks", stocks = stocks.values.sortedBy { it.code }))
    }
}
