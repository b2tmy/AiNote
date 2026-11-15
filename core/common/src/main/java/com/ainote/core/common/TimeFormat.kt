package com.ainote.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 时间展示统一入口。全部基于设备本地时区。
 */
object TimeFormat {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
    private val md = DateTimeFormatter.ofPattern("MM-dd")
    private val ymd = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val ymdHm = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun localDateTime(ts: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), zone)

    fun localDate(ts: Long): LocalDate = localDateTime(ts).toLocalDate()

    /** yyyy-MM-dd，用于日报/分组的日期键 */
    fun dayKey(ts: Long): String = localDate(ts).format(ymd)

    fun dayKey(date: LocalDate): String = date.format(ymd)

    fun time(ts: Long): String = localDateTime(ts).format(hhmm)

    fun dateTime(ts: Long): String = localDateTime(ts).format(ymdHm)

    /** 列表里用的相对时间：刚刚 / n 分钟前 / 今天 HH:mm / 昨天 HH:mm / MM-dd / yyyy-MM-dd */
    fun relative(ts: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - ts
        val today = LocalDate.now(zone)
        val target = localDate(ts)
        return when {
            diff in 0 until 60_000 -> "刚刚"
            diff in 60_000 until 3_600_000 -> "${diff / 60_000} 分钟前"
            target == today -> "今天 ${time(ts)}"
            target == today.minusDays(1) -> "昨天 ${time(ts)}"
            target.year == today.year -> target.format(md)
            else -> target.format(ymd)
        }
    }

    /** 判断是否是同一天 */
    fun isSameDay(a: Long, b: Long): Boolean = dayKey(a) == dayKey(b)

    fun startOfDay(ts: Long): Long =
        localDate(ts).atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(ts: Long): Long =
        startOfDay(ts) + 24 * 60 * 60 * 1000L - 1

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(date: LocalDate): Long =
        startOfDay(date.plusDays(1)) - 1

    /** 把「某天的几点几分」转成时间戳 */
    fun toEpochMillis(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}
