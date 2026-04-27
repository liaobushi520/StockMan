package com.liaobusi.stockman.monitor

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

const val FULL_MARKET_STOCK_MIN_COUNT = 5000

class StockDatabase(private val dbPath: Path = Path.of("data", "stockman-monitor.db")) {
    private val jdbcUrl: String

    init {
        Files.createDirectories(dbPath.parent)
        Class.forName("org.sqlite.JDBC")
        jdbcUrl = "jdbc:sqlite:${dbPath.toAbsolutePath()}"
    }

    fun initialize(seeds: List<StockSeed>) {
        connection().use { conn ->
            conn.createStatement().use { statement ->
                statement.executeUpdate("PRAGMA journal_mode=WAL")
                statement.executeUpdate("PRAGMA foreign_keys=ON")
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS stock (
                        code TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        price REAL NOT NULL,
                        chg REAL NOT NULL,
                        amplitude REAL NOT NULL,
                        turnoverRate REAL NOT NULL,
                        highest REAL NOT NULL,
                        lowest REAL NOT NULL,
                        circulationMarketValue REAL NOT NULL,
                        toMarketTime INTEGER NOT NULL,
                        openPrice REAL NOT NULL,
                        yesterdayClosePrice REAL NOT NULL,
                        ztPrice REAL NOT NULL DEFAULT -1.0,
                        dtPrice REAL NOT NULL DEFAULT -1.0,
                        averagePrice REAL NOT NULL DEFAULT -1.0,
                        bk TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS historystock (
                        code TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        closePrice REAL NOT NULL,
                        openPrice REAL NOT NULL,
                        highest REAL NOT NULL,
                        lowest REAL NOT NULL,
                        chg REAL NOT NULL,
                        amplitude REAL NOT NULL,
                        turnoverRate REAL NOT NULL,
                        ztPrice REAL NOT NULL DEFAULT -1.0,
                        dtPrice REAL NOT NULL DEFAULT -1.0,
                        yesterdayClosePrice REAL NOT NULL DEFAULT -1.0,
                        averagePrice REAL NOT NULL DEFAULT -1.0,
                        PRIMARY KEY (code, date)
                    )
                    """.trimIndent()
                )
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_historystock_date ON historystock(date)")
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_historystock_code_date ON historystock(code, date)")
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS sync_meta (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }

            if (tableCount(conn, "stock") == 0) {
                seedCurrentStocks(conn, seeds)
            }
            if (tableCount(conn, "historystock") == 0) {
                seedHistoryStocks(conn, seeds)
            }
        }
    }

    fun getStocks(): List<StockTick> = connection().use { conn ->
        conn.prepareStatement("SELECT code, name, price, chg, ztPrice, dtPrice FROM stock ORDER BY code").use { ps ->
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            StockTick(
                                code = rs.getString("code"),
                                name = rs.getString("name"),
                                price = rs.getDouble("price"),
                                chg = rs.getDouble("chg"),
                                ztPrice = rs.getDouble("ztPrice"),
                                dtPrice = rs.getDouble("dtPrice")
                            )
                        )
                    }
                }
            }
        }
    }

    fun stockCount(): Int = connection().use { conn -> tableCount(conn, "stock") }

    fun stockRefs(limit: Int? = null): List<StockRef> = connection().use { conn ->
        val sql = buildString {
            append("SELECT code, name, toMarketTime FROM stock ORDER BY code")
            if (limit != null) append(" LIMIT ?")
        }
        conn.prepareStatement(sql).use { ps ->
            if (limit != null) ps.setInt(1, limit.coerceAtLeast(1))
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            StockRef(
                                code = rs.getString("code"),
                                name = rs.getString("name"),
                                toMarketTime = rs.getInt("toMarketTime")
                            )
                        )
                    }
                }
            }
        }
    }

    fun stockRefsByCodes(codes: List<String>): List<StockRef> {
        if (codes.isEmpty()) return emptyList()
        return connection().use { conn ->
            val placeholders = codes.joinToString(",") { "?" }
            conn.prepareStatement("SELECT code, name, toMarketTime FROM stock WHERE code IN ($placeholders) ORDER BY code").use { ps ->
                codes.forEachIndexed { index, code -> ps.setString(index + 1, code) }
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                StockRef(
                                    code = rs.getString("code"),
                                    name = rs.getString("name"),
                                    toMarketTime = rs.getInt("toMarketTime")
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun upsertTick(seed: StockSeed, tick: StockTick) {
        connection().use { conn ->
            conn.autoCommit = false
            updateDailyStock(conn, seed, tick)
            upsertTodayHistory(conn, seed, tick)
            conn.commit()
        }
    }

    fun replaceStocksFromSync(stocks: List<SyncedStock>, date: Int, clearBeforeInsert: Boolean = false, source: String = "unknown") {
        require(stocks.size > FULL_MARKET_STOCK_MIN_COUNT) { "daily stock snapshot is incomplete: ${stocks.size}" }
        connection().use { conn ->
            conn.autoCommit = false
            try {
                if (clearBeforeInsert) {
                    conn.createStatement().use { statement ->
                        statement.executeUpdate("DELETE FROM stock")
                    }
                }
                stocks.forEach { stock ->
                    updateDailyStock(conn, stock)
                }
                setMeta(conn, "last_stock_sync_date", date.toString())
                setMeta(conn, "last_stock_sync_time", System.currentTimeMillis().toString())
                setMeta(conn, "last_stock_sync_count", stocks.size.toString())
                setMeta(conn, "last_stock_sync_source", source)
                conn.commit()
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            }
        }
    }

    fun markDailyStockSlot(slot: String) {
        connection().use { conn ->
            setMeta(conn, "last_stock_sync_slot", slot)
        }
    }

    fun upsertHistoryFromSync(histories: List<SyncedHistoryStock>, source: String, requestedStocks: Int): HistorySyncStatus {
        connection().use { conn ->
            conn.autoCommit = false
            try {
                histories.forEach { upsertHistory(conn, it) }
                val distinctCodes = histories.map { it.code }.distinct().size
                val syncDate = histories.maxOfOrNull { it.date }
                setMeta(conn, "last_history_sync_time", System.currentTimeMillis().toString())
                if (syncDate != null) setMeta(conn, "last_history_sync_date", syncDate.toString())
                setMeta(conn, "last_history_sync_count", histories.size.toString())
                setMeta(conn, "last_history_sync_stock_count", distinctCodes.toString())
                setMeta(conn, "last_history_sync_requested_stock_count", requestedStocks.toString())
                setMeta(conn, "last_history_sync_source", source)
                conn.commit()
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            }
        }
        return historySyncStatus()
    }

    fun syncStatus(): SyncStatus = connection().use { conn ->
        SyncStatus(
            database = path(),
            stockCount = tableCount(conn, "stock"),
            historyCount = tableCount(conn, "historystock"),
            lastStockSyncDate = getMeta(conn, "last_stock_sync_date")?.toIntOrNull(),
            lastStockSyncTime = getMeta(conn, "last_stock_sync_time")?.toLongOrNull(),
            lastStockSyncCount = getMeta(conn, "last_stock_sync_count")?.toIntOrNull(),
            lastStockSyncSource = getMeta(conn, "last_stock_sync_source"),
            lastStockSyncSlot = getMeta(conn, "last_stock_sync_slot")
        )
    }

    fun historySyncStatus(): HistorySyncStatus = connection().use { conn ->
        HistorySyncStatus(
            database = path(),
            historyCount = tableCount(conn, "historystock"),
            lastHistorySyncTime = getMeta(conn, "last_history_sync_time")?.toLongOrNull(),
            lastHistorySyncDate = getMeta(conn, "last_history_sync_date")?.toIntOrNull(),
            lastHistorySyncCount = getMeta(conn, "last_history_sync_count")?.toIntOrNull(),
            lastHistorySyncStockCount = getMeta(conn, "last_history_sync_stock_count")?.toIntOrNull(),
            lastHistorySyncRequestedStockCount = getMeta(conn, "last_history_sync_requested_stock_count")?.toIntOrNull(),
            lastHistorySyncSource = getMeta(conn, "last_history_sync_source")
        )
    }

    fun tableNames(): List<String> = listOf("stock", "historystock", "sync_meta")

    fun dailyLines(code: String, limit: Int): DailyLineResponse = connection().use { conn ->
        val safeLimit = limit.coerceIn(1, 500)
        val stockName = conn.prepareStatement("SELECT name FROM stock WHERE code = ?").use { ps ->
            ps.setString(1, code)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString("name") else "" }
        }
        conn.prepareStatement(
            """
            SELECT date, closePrice, openPrice, highest, lowest, chg, averagePrice
            FROM historystock
            WHERE code = ?
            ORDER BY date DESC
            LIMIT ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, code)
            ps.setInt(2, safeLimit)
            ps.executeQuery().use { rs ->
                val rows = buildList {
                    while (rs.next()) {
                        add(
                            DailyLine(
                                date = rs.getInt("date"),
                                closePrice = rs.getDouble("closePrice"),
                                openPrice = rs.getDouble("openPrice"),
                                highest = rs.getDouble("highest"),
                                lowest = rs.getDouble("lowest"),
                                chg = rs.getDouble("chg"),
                                averagePrice = rs.getDouble("averagePrice")
                            )
                        )
                    }
                }.asReversed()
                DailyLineResponse(code = code, name = stockName, lines = rows)
            }
        }
    }

    fun queryTable(name: String, limit: Int, offset: Int): DbTable {
        require(name in tableNames()) { "Unsupported table: $name" }
        val safeLimit = limit.coerceIn(1, 500)
        val safeOffset = max(0, offset)
        return connection().use { conn ->
            val total = tableCount(conn, name)
            conn.prepareStatement("SELECT * FROM $name LIMIT ? OFFSET ?").use { ps ->
                ps.setInt(1, safeLimit)
                ps.setInt(2, safeOffset)
                ps.executeQuery().use { rs ->
                    val columns = (1..rs.metaData.columnCount).map { rs.metaData.getColumnName(it) }
                    val rows = buildList {
                        while (rs.next()) {
                            add(columns.associateWith { column -> rs.getObject(column)?.toString() ?: "" })
                        }
                    }
                    DbTable(name, total, columns, rows)
                }
            }
        }
    }

    fun path(): String = dbPath.toAbsolutePath().toString()

    private fun connection(): Connection = DriverManager.getConnection(jdbcUrl)

    private fun tableCount(conn: Connection, table: String): Int {
        return conn.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $table").use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    private fun seedCurrentStocks(conn: Connection, seeds: List<StockSeed>) {
        seeds.forEach { seed ->
            updateDailyStock(conn, seed, seed.tick(seed.baseChg))
        }
    }

    private fun updateDailyStock(conn: Connection, stock: SyncedStock) {
        conn.prepareStatement(
            """
            INSERT INTO stock (
                code, name, price, chg, amplitude, turnoverRate, highest, lowest,
                circulationMarketValue, toMarketTime, openPrice, yesterdayClosePrice,
                ztPrice, dtPrice, averagePrice, bk
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(code) DO UPDATE SET
                name = excluded.name,
                price = excluded.price,
                chg = excluded.chg,
                amplitude = excluded.amplitude,
                turnoverRate = excluded.turnoverRate,
                highest = excluded.highest,
                lowest = excluded.lowest,
                circulationMarketValue = excluded.circulationMarketValue,
                toMarketTime = excluded.toMarketTime,
                openPrice = excluded.openPrice,
                yesterdayClosePrice = excluded.yesterdayClosePrice,
                ztPrice = excluded.ztPrice,
                dtPrice = excluded.dtPrice,
                averagePrice = excluded.averagePrice,
                bk = excluded.bk
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, stock.code)
            ps.setString(2, stock.name)
            ps.setDouble(3, stock.price)
            ps.setDouble(4, stock.chg)
            ps.setDouble(5, stock.amplitude)
            ps.setDouble(6, stock.turnoverRate)
            ps.setDouble(7, stock.highest)
            ps.setDouble(8, stock.lowest)
            ps.setDouble(9, stock.circulationMarketValue)
            ps.setInt(10, stock.toMarketTime)
            ps.setDouble(11, stock.openPrice)
            ps.setDouble(12, stock.yesterdayClosePrice)
            ps.setDouble(13, stock.ztPrice)
            ps.setDouble(14, stock.dtPrice)
            ps.setDouble(15, stock.averagePrice)
            ps.setString(16, stock.bk)
            ps.executeUpdate()
        }
    }

    private fun seedHistoryStocks(conn: Connection, seeds: List<StockSeed>) {
        val tradingDays = recentTradingDays(160)
        seeds.forEachIndexed { seedIndex, seed ->
            var previousClose = seed.yesterdayClose * (1 - tradingDays.size * 0.0008)
            tradingDays.forEachIndexed { index, date ->
                val trend = sin((index + seedIndex * 6) / 9.0) * 1.4
                val noise = Random(seed.code.hashCode() * 31 + index).nextDouble(-0.75, 0.75)
                val chg = percent((trend + noise).coerceIn(-8.8, 9.6))
                val close = money(previousClose * (1 + chg / 100))
                val open = money(previousClose * (1 + Random(seed.code.hashCode() + index).nextDouble(-0.018, 0.018)))
                val high = money(max(open, close) * (1 + Random(index + seedIndex).nextDouble(0.002, 0.026)))
                val low = money(min(open, close) * (1 - Random(index * 7 + seedIndex).nextDouble(0.002, 0.024)))
                val amplitude = percent(((high - low) / previousClose) * 100)
                val turnover = percent(1.2 + Random(seed.code.hashCode() - index).nextDouble(0.0, 5.5))
                val average = money((open + close + high + low) / 4)
                upsertHistory(
                    conn = conn,
                    code = seed.code,
                    date = date,
                    closePrice = close,
                    openPrice = open,
                    highest = high,
                    lowest = low,
                    chg = chg,
                    amplitude = amplitude,
                    turnoverRate = turnover,
                    ztPrice = money(previousClose * (1 + seed.limitRate)),
                    dtPrice = money(previousClose * (1 - seed.limitRate)),
                    yesterdayClosePrice = money(previousClose),
                    averagePrice = average
                )
                previousClose = close
            }
        }
    }

    private fun updateDailyStock(conn: Connection, seed: StockSeed, tick: StockTick) {
        conn.prepareStatement(
            """
            INSERT INTO stock (
                code, name, price, chg, amplitude, turnoverRate, highest, lowest,
                circulationMarketValue, toMarketTime, openPrice, yesterdayClosePrice,
                ztPrice, dtPrice, averagePrice, bk
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(code) DO UPDATE SET
                name = excluded.name,
                price = excluded.price,
                chg = excluded.chg,
                amplitude = excluded.amplitude,
                turnoverRate = excluded.turnoverRate,
                highest = excluded.highest,
                lowest = excluded.lowest,
                circulationMarketValue = excluded.circulationMarketValue,
                toMarketTime = excluded.toMarketTime,
                openPrice = excluded.openPrice,
                yesterdayClosePrice = excluded.yesterdayClosePrice,
                ztPrice = excluded.ztPrice,
                dtPrice = excluded.dtPrice,
                averagePrice = excluded.averagePrice,
                bk = excluded.bk
            """.trimIndent()
        ).use { ps ->
            val open = money(seed.yesterdayClose * (1 + (seed.baseChg / 2) / 100))
            val high = money(max(open, tick.price) * 1.012)
            val low = money(min(open, tick.price) * 0.992)
            val amplitude = percent(((high - low) / seed.yesterdayClose) * 100)
            val average = money((open + tick.price + high + low) / 4)
            ps.setString(1, seed.code)
            ps.setString(2, seed.name)
            ps.setDouble(3, tick.price)
            ps.setDouble(4, tick.chg)
            ps.setDouble(5, amplitude)
            ps.setDouble(6, percent(2.0 + kotlin.math.abs(tick.chg) / 2))
            ps.setDouble(7, high)
            ps.setDouble(8, low)
            ps.setDouble(9, seed.circulationMarketValue)
            ps.setInt(10, seed.toMarketTime)
            ps.setDouble(11, open)
            ps.setDouble(12, seed.yesterdayClose)
            ps.setDouble(13, seed.ztPrice)
            ps.setDouble(14, seed.dtPrice)
            ps.setDouble(15, average)
            ps.setString(16, seed.bk)
            ps.executeUpdate()
        }
    }

    private fun upsertTodayHistory(conn: Connection, seed: StockSeed, tick: StockTick) {
        val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE).toInt()
        val open = money(seed.yesterdayClose * (1 + (seed.baseChg / 2) / 100))
        val high = money(max(open, tick.price) * 1.012)
        val low = money(min(open, tick.price) * 0.992)
        upsertHistory(
            conn = conn,
            code = seed.code,
            date = date,
            closePrice = tick.price,
            openPrice = open,
            highest = high,
            lowest = low,
            chg = tick.chg,
            amplitude = percent(((high - low) / seed.yesterdayClose) * 100),
            turnoverRate = percent(2.0 + kotlin.math.abs(tick.chg) / 2),
            ztPrice = seed.ztPrice,
            dtPrice = seed.dtPrice,
            yesterdayClosePrice = seed.yesterdayClose,
            averagePrice = money((open + tick.price + high + low) / 4)
        )
    }

    private fun upsertHistory(conn: Connection, history: SyncedHistoryStock) {
        upsertHistory(
            conn = conn,
            code = history.code,
            date = history.date,
            closePrice = history.closePrice,
            openPrice = history.openPrice,
            highest = history.highest,
            lowest = history.lowest,
            chg = history.chg,
            amplitude = history.amplitude,
            turnoverRate = history.turnoverRate,
            ztPrice = history.ztPrice,
            dtPrice = history.dtPrice,
            yesterdayClosePrice = history.yesterdayClosePrice,
            averagePrice = history.averagePrice
        )
    }

    private fun upsertHistory(
        conn: Connection,
        code: String,
        date: Int,
        closePrice: Double,
        openPrice: Double,
        highest: Double,
        lowest: Double,
        chg: Double,
        amplitude: Double,
        turnoverRate: Double,
        ztPrice: Double,
        dtPrice: Double,
        yesterdayClosePrice: Double,
        averagePrice: Double
    ) {
        conn.prepareStatement(
            """
            INSERT INTO historystock (
                code, date, closePrice, openPrice, highest, lowest, chg, amplitude,
                turnoverRate, ztPrice, dtPrice, yesterdayClosePrice, averagePrice
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(code, date) DO UPDATE SET
                closePrice = excluded.closePrice,
                openPrice = excluded.openPrice,
                highest = excluded.highest,
                lowest = excluded.lowest,
                chg = excluded.chg,
                amplitude = excluded.amplitude,
                turnoverRate = excluded.turnoverRate,
                ztPrice = excluded.ztPrice,
                dtPrice = excluded.dtPrice,
                yesterdayClosePrice = excluded.yesterdayClosePrice,
                averagePrice = excluded.averagePrice
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, code)
            ps.setInt(2, date)
            ps.setDouble(3, closePrice)
            ps.setDouble(4, openPrice)
            ps.setDouble(5, highest)
            ps.setDouble(6, lowest)
            ps.setDouble(7, chg)
            ps.setDouble(8, amplitude)
            ps.setDouble(9, turnoverRate)
            ps.setDouble(10, ztPrice)
            ps.setDouble(11, dtPrice)
            ps.setDouble(12, yesterdayClosePrice)
            ps.setDouble(13, averagePrice)
            ps.executeUpdate()
        }
    }

    private fun recentTradingDays(count: Int): List<Int> {
        val formatter = DateTimeFormatter.BASIC_ISO_DATE
        val days = ArrayDeque<Int>()
        var date = LocalDate.now().minusDays(1)
        while (days.size < count) {
            if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
                days.addFirst(date.format(formatter).toInt())
            }
            date = date.minusDays(1)
        }
        return days.toList()
    }

    private fun setMeta(conn: Connection, key: String, value: String) {
        conn.prepareStatement(
            """
            INSERT INTO sync_meta(key, value) VALUES(?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, key)
            ps.setString(2, value)
            ps.executeUpdate()
        }
    }

    private fun getMeta(conn: Connection, key: String): String? {
        return conn.prepareStatement("SELECT value FROM sync_meta WHERE key = ?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getString("value") else null
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class DbTable(
    val name: String,
    val total: Int,
    val columns: List<String>,
    val rows: List<Map<String, String>>
)

@kotlinx.serialization.Serializable
data class DbTables(
    val database: String,
    val tables: List<String>
)

@kotlinx.serialization.Serializable
data class SyncStatus(
    val database: String,
    val stockCount: Int,
    val historyCount: Int,
    val lastStockSyncDate: Int?,
    val lastStockSyncTime: Long?,
    val lastStockSyncCount: Int?,
    val lastStockSyncSource: String?,
    val lastStockSyncSlot: String?
)

@kotlinx.serialization.Serializable
data class DailyLine(
    val date: Int,
    val closePrice: Double,
    val openPrice: Double,
    val highest: Double,
    val lowest: Double,
    val chg: Double,
    val averagePrice: Double
)

@kotlinx.serialization.Serializable
data class DailyLineResponse(
    val code: String,
    val name: String,
    val lines: List<DailyLine>
)

@kotlinx.serialization.Serializable
data class HistorySyncStatus(
    val database: String,
    val historyCount: Int,
    val lastHistorySyncTime: Long?,
    val lastHistorySyncDate: Int?,
    val lastHistorySyncCount: Int?,
    val lastHistorySyncStockCount: Int?,
    val lastHistorySyncRequestedStockCount: Int?,
    val lastHistorySyncSource: String?
)

data class StockRef(
    val code: String,
    val name: String,
    val toMarketTime: Int
)

data class SyncedStock(
    val code: String,
    val name: String,
    val price: Double,
    val chg: Double,
    val amplitude: Double,
    val turnoverRate: Double,
    val highest: Double,
    val lowest: Double,
    val circulationMarketValue: Double,
    val toMarketTime: Int,
    val openPrice: Double,
    val yesterdayClosePrice: Double,
    val ztPrice: Double,
    val dtPrice: Double,
    val averagePrice: Double,
    val bk: String
) {
    fun toHistory(date: Int): SyncedHistoryStock = SyncedHistoryStock(
        code = code,
        date = date,
        closePrice = price,
        openPrice = openPrice,
        highest = highest,
        lowest = lowest,
        chg = chg,
        amplitude = amplitude,
        turnoverRate = turnoverRate,
        ztPrice = ztPrice,
        dtPrice = dtPrice,
        yesterdayClosePrice = yesterdayClosePrice,
        averagePrice = averagePrice
    )
}

data class SyncedHistoryStock(
    val code: String,
    val date: Int,
    val closePrice: Double,
    val openPrice: Double,
    val highest: Double,
    val lowest: Double,
    val chg: Double,
    val amplitude: Double,
    val turnoverRate: Double,
    val ztPrice: Double,
    val dtPrice: Double,
    val yesterdayClosePrice: Double,
    val averagePrice: Double
)
