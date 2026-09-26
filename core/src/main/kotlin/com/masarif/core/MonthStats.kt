package com.masarif.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** ملخص عملية للإحصاء (مستقل عن Room). */
data class TxSummary(
    val dateTimeMillis: Long,
    val amountHalalas: Long,
    val direction: Direction,
    val channel: Channel,
    val categoryId: Long,
)

data class MonthTotals(val income: Long, val expense: Long, val net: Long, val count: Int)

data class CategoryShare(val categoryId: Long, val amount: Long, val share: Double)

enum class ChannelGroup { ONLINE, IN_PERSON, CASH, TRANSFER, OTHER }

data class MonthExpense(val month: YearMonth, val expense: Long)

data class Comparison(
    val currentExpense: Long,
    val previousExpense: Long,
    /** null إن كان الشهر الماضي صفراً. */
    val vsPreviousPercent: Double?,
    /** متوسط الأشهر الثلاثة السابقة التي فيها عمليات. null إن لم يوجد أي منها. */
    val avgLast3: Long?,
    val vsAvgPercent: Double?,
    /** الصرف المتوقع بنهاية الشهر من المعدّل اليومي (للشهر الجاري فقط). */
    val projected: Long?,
    val daysElapsed: Int,
    val daysInMonth: Int,
)

/** كل حسابات الرئيسية. الأرقام هنا هي نفسها مجموع العمليات المعروضة لنفس الفلتر. */
object MonthStats {

    fun monthOf(millis: Long, zone: ZoneId): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))

    fun inMonth(txs: List<TxSummary>, month: YearMonth, zone: ZoneId): List<TxSummary> =
        txs.filter { monthOf(it.dateTimeMillis, zone) == month }

    fun totals(txs: List<TxSummary>): MonthTotals {
        val income = txs.filter { it.direction == Direction.INCOME }.sumOf { it.amountHalalas }
        val expense = txs.filter { it.direction == Direction.EXPENSE }.sumOf { it.amountHalalas }
        return MonthTotals(income, expense, income - expense, txs.size)
    }

    /** الصرف حسب التصنيف مرتّباً من الأكبر، مع النسبة من إجمالي صرف الشهر. */
    fun expenseByCategory(txs: List<TxSummary>): List<CategoryShare> {
        val expenses = txs.filter { it.direction == Direction.EXPENSE }
        val total = expenses.sumOf { it.amountHalalas }
        if (total == 0L) return emptyList()
        return expenses.groupBy { it.categoryId }
            .map { (cat, list) ->
                val amount = list.sumOf { it.amountHalalas }
                CategoryShare(cat, amount, amount.toDouble() / total)
            }
            .sortedByDescending { it.amount }
    }

    fun groupOf(channel: Channel): ChannelGroup = when (channel) {
        Channel.ONLINE -> ChannelGroup.ONLINE
        Channel.POS -> ChannelGroup.IN_PERSON
        Channel.ATM -> ChannelGroup.CASH
        Channel.TRANSFER -> ChannelGroup.TRANSFER
        Channel.SALARY, Channel.REFUND, Channel.OTHER -> ChannelGroup.OTHER
    }

    /** الصرف حسب مجموعة القناة (أونلاين / حضوري / سحب نقدي / تحويلات / أخرى)، بلا أصفار. */
    fun expenseByChannelGroup(txs: List<TxSummary>): List<Pair<ChannelGroup, Long>> =
        txs.filter { it.direction == Direction.EXPENSE }
            .groupBy { groupOf(it.channel) }
            .map { (g, list) -> g to list.sumOf { it.amountHalalas } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

    /** إجمالي الصرف لكل شهر من آخر 12 شهراً (الأقدم أولاً، وينتهي بالشهر المحدد). */
    fun last12Months(txs: List<TxSummary>, end: YearMonth, zone: ZoneId): List<MonthExpense> {
        val byMonth = txs.filter { it.direction == Direction.EXPENSE }
            .groupBy { monthOf(it.dateTimeMillis, zone) }
            .mapValues { (_, list) -> list.sumOf { it.amountHalalas } }
        return (11 downTo 0).map { back ->
            val m = end.minusMonths(back.toLong())
            MonthExpense(m, byMonth[m] ?: 0L)
        }
    }

    private fun percent(current: Long, base: Long): Double? =
        if (base <= 0L) null else (current - base) * 100.0 / base

    fun comparison(txs: List<TxSummary>, month: YearMonth, zone: ZoneId, today: LocalDate): Comparison {
        val current = totals(inMonth(txs, month, zone)).expense
        val previous = totals(inMonth(txs, month.minusMonths(1), zone)).expense

        val last3 = (1..3).map { inMonth(txs, month.minusMonths(it.toLong()), zone) }
            .filter { it.isNotEmpty() }
            .map { totals(it).expense }
        val avg = if (last3.isEmpty()) null else last3.sum() / last3.size

        val daysInMonth = month.lengthOfMonth()
        val isCurrentMonth = YearMonth.from(today) == month
        val daysElapsed = if (isCurrentMonth) today.dayOfMonth else daysInMonth
        val projected = if (isCurrentMonth && daysElapsed in 1 until daysInMonth && current > 0) {
            current * daysInMonth / daysElapsed
        } else null

        return Comparison(
            currentExpense = current,
            previousExpense = previous,
            vsPreviousPercent = percent(current, previous),
            avgLast3 = avg,
            vsAvgPercent = avg?.let { percent(current, it) },
            projected = projected,
            daysElapsed = daysElapsed,
            daysInMonth = daysInMonth,
        )
    }
}
