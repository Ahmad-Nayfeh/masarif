package com.masarif.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportPeriodTest {
    private val zone: ZoneId = ZoneId.of("Asia/Riyadh")
    private val today = LocalDate.of(2026, 9, 26)
    private fun startOf(y: Int, m: Int) = LocalDateTime.of(y, m, 1, 0, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `two months back starts on the first of july`() {
        assertEquals(startOf(2026, 7), ImportPeriod.startMillis(2, today, zone))
    }

    @Test
    fun `one month back starts on the first of august and a year crosses the year boundary`() {
        assertEquals(startOf(2026, 8), ImportPeriod.startMillis(1, today, zone))
        assertEquals(startOf(2025, 9), ImportPeriod.startMillis(12, today, zone))
    }

    @Test
    fun `all time has no lower bound`() {
        assertEquals(0L, ImportPeriod.startMillis(ImportPeriod.ALL, today, zone))
    }

    @Test
    fun `labels`() {
        assertEquals("الشهر الماضي", ImportPeriod.label(1))
        assertEquals("شهران", ImportPeriod.label(2))
        assertEquals("3 أشهر", ImportPeriod.label(3))
        assertEquals("سنة كاملة", ImportPeriod.label(12))
        assertEquals("كل الوقت", ImportPeriod.label(ImportPeriod.ALL))
    }
}
