package com.masarif.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MonthStatsTest {
    private val zone: ZoneId = ZoneId.of("Asia/Riyadh")

    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun tx(millis: Long, halalas: Long, dir: Direction = Direction.EXPENSE, ch: Channel = Channel.POS, cat: Long = 1) =
        TxSummary(millis, halalas, dir, ch, cat)

    private val sep = YearMonth.of(2026, 9)

    private val sample = listOf(
        tx(at(2026, 9, 21), 4700, cat = 5),                       // ALDREES بنزين
        tx(at(2026, 9, 20), 1827, cat = 1),                       // بقالة
        tx(at(2026, 9, 20), 550, cat = 1),                        // بقالة
        tx(at(2026, 9, 20), 8050, ch = Channel.ONLINE, cat = 11), // STC
        tx(at(2026, 9, 22), 100, ch = Channel.ONLINE, cat = 16),  // GIVEBOX
        tx(at(2026, 9, 20), 1923, ch = Channel.ONLINE, cat = 22), // SKYLINE غير مصنّف
        tx(at(2026, 9, 27), 1_200_000, Direction.INCOME, Channel.SALARY, 23),
        tx(at(2026, 8, 10), 10_000),                              // أغسطس
        tx(at(2026, 7, 10), 30_000),                              // يوليو
        tx(at(2026, 5, 10), 5_000),                               // مايو (يونيو فارغ)
    )

    @Test
    fun `totals match the sum of the month's transactions`() {
        val month = MonthStats.inMonth(sample, sep, zone)
        val t = MonthStats.totals(month)
        assertEquals(1_200_000L, t.income)
        assertEquals(4700L + 1827 + 550 + 8050 + 100 + 1923, t.expense)
        assertEquals(t.income - t.expense, t.net)
        assertEquals(7, t.count)
    }

    @Test
    fun `category shares sum to the expense total and are sorted descending`() {
        val month = MonthStats.inMonth(sample, sep, zone)
        val shares = MonthStats.expenseByCategory(month)
        assertEquals(MonthStats.totals(month).expense, shares.sumOf { it.amount })
        assertEquals(shares.map { it.amount }.sortedDescending(), shares.map { it.amount })
        assertEquals(11L, shares.first().categoryId)
        assertEquals(1.0, shares.sumOf { it.share }, 1e-9)
        assertEquals(1827L + 550, shares.first { it.categoryId == 1L }.amount)
    }

    @Test
    fun `channel groups sum to the expense total`() {
        val month = MonthStats.inMonth(sample, sep, zone)
        val groups = MonthStats.expenseByChannelGroup(month)
        assertEquals(MonthStats.totals(month).expense, groups.sumOf { it.second })
        assertEquals(8050L + 100 + 1923, groups.first { it.first == ChannelGroup.ONLINE }.second)
        assertEquals(4700L + 1827 + 550, groups.first { it.first == ChannelGroup.IN_PERSON }.second)
    }

    @Test
    fun `last 12 months ends at selected month with zeros for empty months`() {
        val bars = MonthStats.last12Months(sample, sep, zone)
        assertEquals(12, bars.size)
        assertEquals(YearMonth.of(2025, 10), bars.first().month)
        assertEquals(sep, bars.last().month)
        assertEquals(10_000L, bars.first { it.month == YearMonth.of(2026, 8) }.expense)
        assertEquals(0L, bars.first { it.month == YearMonth.of(2026, 6) }.expense)
        assertEquals(MonthStats.totals(MonthStats.inMonth(sample, sep, zone)).expense, bars.last().expense)
    }

    @Test
    fun `comparison percentages and projection`() {
        val c = MonthStats.comparison(sample, sep, zone, today = LocalDate.of(2026, 9, 23))
        val current = 4700L + 1827 + 550 + 8050 + 100 + 1923 // 17150
        assertEquals(current, c.currentExpense)
        assertEquals(10_000L, c.previousExpense)
        assertEquals(71.5, c.vsPreviousPercent!!, 1e-9)
        // آخر 3 أشهر قبل سبتمبر: أغسطس 10000، يوليو 30000، يونيو فارغ (يُستبعد) → المتوسط 20000
        assertEquals(20_000L, c.avgLast3)
        assertEquals((current - 20_000) * 100.0 / 20_000, c.vsAvgPercent!!, 1e-9)
        assertEquals(23, c.daysElapsed)
        assertEquals(30, c.daysInMonth)
        assertEquals(current * 30 / 23, c.projected)
    }

    @Test
    fun `no projection for past months and null percent when base is zero`() {
        val c = MonthStats.comparison(sample, YearMonth.of(2026, 5), zone, today = LocalDate.of(2026, 9, 23))
        assertNull(c.projected)
        assertNull(c.vsPreviousPercent)
        assertNull(c.avgLast3)
        assertNull(c.vsAvgPercent)
    }
}
