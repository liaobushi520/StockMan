package com.liaobusi.stockman.monitor

import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Collections
import kotlin.math.sin
import kotlin.random.Random

class MarketEngine {
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
    private val dailyStockSync = DailyStockSync(database)
    private val historyStockSync = HistoryStockSync(database)
    private val trackers = seeds.associate { it.code to Tracker() }
    private val stocks: MutableMap<String, StockTick>
    private val alerts = ArrayDeque<AlertEvent>()
    private var step = 0

    init {
        database.initialize(seeds)
        stocks = database.getStocks().associateBy { it.code }.toMutableMap()
        scope.launch {
            runCatching { dailyStockSync.init() }
                .onSuccess { status ->
                    if (status != null) reloadStocksAndBroadcast()
                }
                .onFailure { println("Stock sync failed: ${it.message}") }
        }
        scope.launch {
            while (isActive) {
                delay(60_000)
                runCatching { dailyStockSync.syncIfNeededBySchedule() }
                    .onSuccess { status ->
                        if (status != null) reloadStocksAndBroadcast()
                    }
                    .onFailure { println("Stock sync failed: ${it.message}") }
            }
        }
        scope.launch {
            while (isActive) {
                delay(60_000)
                runCatching { historyStockSync.syncTodayIfNeededAfterClose() }
                    .onFailure { println("History stock sync failed: ${it.message}") }
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

    suspend fun syncStocksNow(): SyncStatus {
        val status = dailyStockSync.sync()
        reloadStocksAndBroadcast()
        return status
    }

    fun syncStatus(): SyncStatus = database.syncStatus()

    suspend fun syncHistoryNow(limit: Int, stockLimit: Int?, codes: List<String>): HistorySyncStatus {
        return historyStockSync.syncRecentKLines(limit = limit, stockLimit = stockLimit, codes = codes)
    }

    fun historySyncStatus(): HistorySyncStatus = database.historySyncStatus()

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
