package com.masarif.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** دفعة اشتراك واحدة. */
data class SubscriptionPayment(val txId: Long, val dateTimeMillis: Long, val amountHalalas: Long)

enum class SubscriptionPeriod { WEEKLY, MONTHLY, QUARTERLY, YEARLY, IRREGULAR, UNKNOWN }

/** اشتراك واحد (جهة واحدة) مع دفعاته ودوريته المستنتجة. */
data class Subscription(
    val merchant: String,
    val payments: List<SubscriptionPayment>,
    val period: SubscriptionPeriod,
    /** المبلغ الأخير المدفوع. */
    val lastAmount: Long,
    val lastPaidAt: Long,
    /** الموعد المتوقع القادم (آخر دفعة + الدورة)، null إن كانت الدورية مجهولة. */
    val nextExpectedAt: Long?,
    /** تكلفة شهرية مكافئة للمقارنة (سنوي ÷ 12، أسبوعي × 4.33). */
    val monthlyEquivalent: Long,
)

object SubscriptionStats {

    /** يجمع عمليات فئة الاشتراكات حسب الجهة ويستنتج الدورية من الفواصل الزمنية بين الدفعات. */
    fun build(
        transactions: List<Pair<String, SubscriptionPayment>>,
        zone: ZoneId,
    ): List<Subscription> =
        transactions.groupBy({ it.first }, { it.second })
            .map { (merchant, payments) -> one(merchant, payments.sortedByDescending { it.dateTimeMillis }, zone) }
            .sortedByDescending { it.lastPaidAt }

    private fun one(merchant: String, paymentsDesc: List<SubscriptionPayment>, zone: ZoneId): Subscription {
        val last = paymentsDesc.first()
        val period = inferPeriod(paymentsDesc.map { it.dateTimeMillis }, zone)
        val next = when (period) {
            SubscriptionPeriod.WEEKLY -> addDays(last.dateTimeMillis, 7, zone)
            SubscriptionPeriod.MONTHLY -> addMonths(last.dateTimeMillis, 1, zone)
            SubscriptionPeriod.QUARTERLY -> addMonths(last.dateTimeMillis, 3, zone)
            SubscriptionPeriod.YEARLY -> addMonths(last.dateTimeMillis, 12, zone)
            SubscriptionPeriod.IRREGULAR, SubscriptionPeriod.UNKNOWN -> null
        }
        val monthly = when (period) {
            SubscriptionPeriod.WEEKLY -> last.amountHalalas * 433 / 100
            SubscriptionPeriod.MONTHLY, SubscriptionPeriod.IRREGULAR, SubscriptionPeriod.UNKNOWN -> last.amountHalalas
            SubscriptionPeriod.QUARTERLY -> last.amountHalalas / 3
            SubscriptionPeriod.YEARLY -> last.amountHalalas / 12
        }
        return Subscription(merchant, paymentsDesc, period, last.amountHalalas, last.dateTimeMillis, next, monthly)
    }

    /** الدورية من وسيط الفواصل بالأيام بين الدفعات المتتالية. دفعة واحدة = مجهولة. */
    fun inferPeriod(datesMillis: List<Long>, zone: ZoneId): SubscriptionPeriod {
        val days = datesMillis.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.distinct().sorted()
        if (days.size < 2) return SubscriptionPeriod.UNKNOWN
        val gaps = days.zipWithNext { a, b -> Duration.between(a.atStartOfDay(), b.atStartOfDay()).toDays() }.sorted()
        val median = gaps[gaps.size / 2]
        return when (median) {
            in 5..9 -> SubscriptionPeriod.WEEKLY
            in 25..35 -> SubscriptionPeriod.MONTHLY
            in 80..100 -> SubscriptionPeriod.QUARTERLY
            in 350..380 -> SubscriptionPeriod.YEARLY
            else -> SubscriptionPeriod.IRREGULAR
        }
    }

    private fun addDays(millis: Long, days: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zone).plusDays(days).toInstant().toEpochMilli()

    private fun addMonths(millis: Long, months: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zone).plusMonths(months).toInstant().toEpochMilli()

    /** متأخر إن مرّ الموعد المتوقع بأكثر من 3 أيام بلا دفعة. */
    fun isOverdue(s: Subscription, today: LocalDate, zone: ZoneId): Boolean {
        val next = s.nextExpectedAt ?: return false
        return Instant.ofEpochMilli(next).atZone(zone).toLocalDate().plusDays(3).isBefore(today)
    }
}
