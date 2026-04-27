package com.liaobusi.stockman.monitor

class Tracker {
    private val cache = mutableListOf<StockTick>()
    private val cacheSize = 80

    fun update(stock: StockTick): AlertEvent? {
        val reasons = buildList {
            val last = cache.lastOrNull()
            if (last != null) {
                if (samePrice(last.ztPrice, last.price) && stock.price < stock.ztPrice) {
                    add("[炸板]")
                }
                if (samePrice(last.dtPrice, last.price) && stock.price > stock.dtPrice) {
                    add("[翘板]")
                }
            }

            cache.relativeRecordFromEnd(5)?.let { previous ->
                val zf = stock.chg - previous.chg
                val seconds = (stock.time - previous.time) / 1000
                if (zf >= 1 && seconds <= 15) {
                    add("${seconds}秒内涨幅${"%.2f".format(zf)}%")
                }
            }

            cache.getOrNull(cache.size * 2 / 3)?.let { previous ->
                val zf = stock.chg - previous.chg
                val seconds = (stock.time - previous.time) / 1000
                if (zf >= 2 && seconds in 15..60) {
                    add("${seconds}秒内涨幅${"%.2f".format(zf)}%")
                }
            }

            cache.getOrNull(cache.size / 3)?.let { previous ->
                val zf = stock.chg - previous.chg
                val seconds = (stock.time - previous.time) / 1000
                if (zf >= 3 && seconds in 60..90) {
                    add("${seconds}秒内涨幅${"%.2f".format(zf)}%")
                }
            }

            cache.firstOrNull()?.let { previous ->
                val zf = stock.chg - previous.chg
                val seconds = (stock.time - previous.time) / 1000
                if (zf >= 4 && seconds in 90..180) {
                    add("${seconds}秒内涨幅${"%.2f".format(zf)}%")
                }
            }
        }

        cache.add(stock)
        if (cache.size >= cacheSize) {
            cache.removeAt(0)
        }

        if (reasons.isEmpty()) return null

        return AlertEvent(
            code = stock.code,
            name = stock.name,
            title = "${stock.code}${stock.name}异动,涨跌幅${stock.chg}%",
            content = reasons.joinToString(" "),
            chg = stock.chg,
            price = stock.price,
            time = stock.time
        )
    }

    private fun List<StockTick>.relativeRecordFromEnd(offset: Int): StockTick? {
        return if (size >= offset) get(size - offset) else lastOrNull()
    }
}
