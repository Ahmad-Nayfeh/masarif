package com.masarif.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionStatsTest {
    private val zone: ZoneId = ZoneId.of("Asia/Riyadh")
    private fun at(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 10, 0).atZone(zone).toInstant().toEpochMilli()
    private fun pay(id: Long, y: Int, m: Int, d: Int, amount: Long) = SubscriptionPayment(id, at(y, m, d), amount)

    @Test
    fun `monthly subscription is detected with next expected date and monthly equivalent`() {
        val list = SubscriptionStats.build(
            listOf("Netflix" to pay(1, 2026, 7, 5, 4900), "Netflix" to pay(2, 2026, 8, 5, 4900), "Netflix" to pay(3, 2026, 9, 5, 4900)),
            zone,
        )
        assertEquals(1, list.size)
        val s = list.first()
        assertEquals(SubscriptionPeriod.MONTHLY, s.period)
        assertEquals(3, s.payments.size)
        assertEquals(at(2026, 9, 5), s.lastPaidAt)
        assertEquals(at(2026, 10, 5), s.nextExpectedAt)
        assertEquals(4900L, s.monthlyEquivalent)
        assertEquals(listOf(3L, 2L, 1L), s.payments.map { it.txId })
    }

    @Test
    fun `yearly subscription monthly equivalent is divided by 12`() {
        val s = SubscriptionStats.build(listOf("iCloud" to pay(1, 2025, 3, 1, 12_000), "iCloud" to pay(2, 2026, 3, 1, 12_000)), zone).first()
        assertEquals(SubscriptionPeriod.YEARLY, s.period)
        assertEquals(1000L, s.monthlyEquivalent)
        assertEquals(at(2027, 3, 1), s.nextExpectedAt)
    }

    @Test
    fun `single payment has unknown period and no next date`() {
        val s = SubscriptionStats.build(listOf("Shahid" to pay(1, 2026, 9, 1, 2000)), zone).first()
        assertEquals(SubscriptionPeriod.UNKNOWN, s.period)
        assertNull(s.nextExpectedAt)
        assertEquals(2000L, s.monthlyEquivalent)
    }

    @Test
    fun `irregular gaps are marked irregular`() {
        val s = SubscriptionStats.build(listOf("X" to pay(1, 2026, 1, 1, 100), "X" to pay(2, 2026, 1, 20, 100), "X" to pay(3, 2026, 5, 3, 100)), zone).first()
        assertEquals(SubscriptionPeriod.IRREGULAR, s.period)
    }

    @Test
    fun `overdue only after three days past the expected date`() {
        val s = SubscriptionStats.build(listOf("Netflix" to pay(1, 2026, 7, 5, 4900), "Netflix" to pay(2, 2026, 8, 5, 4900)), zone).first()
        assertFalse(SubscriptionStats.isOverdue(s, LocalDate.of(2026, 9, 7), zone))
        assertTrue(SubscriptionStats.isOverdue(s, LocalDate.of(2026, 9, 9), zone))
    }

    @Test
    fun `groups are sorted by most recent payment`() {
        val list = SubscriptionStats.build(listOf("A" to pay(1, 2026, 1, 1, 1), "B" to pay(2, 2026, 6, 1, 1)), zone)
        assertEquals(listOf("B", "A"), list.map { it.merchant })
    }
}
