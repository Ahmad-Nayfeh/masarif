package com.masarif.core

import java.time.LocalDate
import java.time.ZoneId

/**
 * "إلى أي مدة ماضية؟" بالأشهر الميلادية الكاملة قبل الشهر الحالي. الشهر الحالي مشمول دائماً.
 * مثال: اليوم 26 سبتمبر، شهران = من 1 يوليو 00:00 حتى الآن.
 */
object ImportPeriod {
    /** الخيارات المعروضة: عدد الأشهر السابقة، و[ALL] = كل الوقت. */
    const val ALL = -1
    val options: List<Int> = listOf(1, 2, 3, 6, 12, ALL)

    fun label(months: Int): String = when (months) {
        ALL -> "كل الوقت"
        1 -> "الشهر الماضي"
        2 -> "شهران"
        in 3..10 -> "$months أشهر"
        12 -> "سنة كاملة"
        else -> "$months شهراً"
    }

    /** بداية الفترة بالمللي ثانية (0 = بلا حد). */
    fun startMillis(months: Int, today: LocalDate, zone: ZoneId): Long {
        if (months == ALL) return 0L
        val start = today.withDayOfMonth(1).minusMonths(months.toLong())
        return start.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
