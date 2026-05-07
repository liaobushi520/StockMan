package com.liaobusi.stockman.monitor

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ChinaMarketCalendar {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    val basicDateFormatter: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    private val dailyCloseTime: LocalTime = LocalTime.of(15, 0)

    private val closedDates: Set<LocalDate> = buildSet {
        addClosedRange("2026-05-01", "2026-05-05")
    }

    fun isTradingDay(date: LocalDate): Boolean {
        return date.dayOfWeek != DayOfWeek.SATURDAY &&
            date.dayOfWeek != DayOfWeek.SUNDAY &&
            date !in closedDates
    }

    fun latestTradingDateOnOrBefore(date: LocalDate): LocalDate {
        var candidate = date
        while (!isTradingDay(candidate)) {
            candidate = candidate.minusDays(1)
        }
        return candidate
    }

    fun currentHistoryTargetDate(now: LocalDateTime = LocalDateTime.now(zone)): Int {
        val naturalTarget = if (now.toLocalTime() >= dailyCloseTime) now.toLocalDate() else now.toLocalDate().minusDays(1)
        return toBasicInt(latestTradingDateOnOrBefore(naturalTarget))
    }

    fun currentRealtimeSnapshotDate(now: LocalDateTime = LocalDateTime.now(zone)): Int {
        return toBasicInt(latestTradingDateOnOrBefore(now.toLocalDate()))
    }

    fun normalizeTradingDate(date: Int): Int {
        val parsed = runCatching { LocalDate.parse(date.toString(), basicDateFormatter) }.getOrNull()
        return toBasicInt(latestTradingDateOnOrBefore(parsed ?: LocalDate.now(zone)))
    }

    fun isAfterTradingClose(now: LocalDateTime = LocalDateTime.now(zone)): Boolean {
        return isTradingDay(now.toLocalDate()) && now.toLocalTime() >= dailyCloseTime
    }

    fun toBasicInt(date: LocalDate): Int = date.format(basicDateFormatter).toInt()

    private fun MutableSet<LocalDate>.addClosedRange(start: String, end: String) {
        var date = LocalDate.parse(start)
        val endDate = LocalDate.parse(end)
        while (!date.isAfter(endDate)) {
            add(date)
            date = date.plusDays(1)
        }
    }
}
