package com.masarif.app.ui

import com.masarif.core.Channel
import com.masarif.core.ChannelGroup
import com.masarif.core.Direction
import com.masarif.core.MatchType
import com.masarif.core.Money
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** تنسيق موحّد: أرقام لاتينية دائماً، نصوص عربية. */
object Fmt {
    val zone: ZoneId get() = ZoneId.systemDefault()

    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.US)

    private val months = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )
    private val monthsShort = listOf("ينا", "فبر", "مار", "أبر", "ماي", "يون", "يول", "أغس", "سبت", "أكت", "نوف", "ديس")

    fun money(halalas: Long): String = Money.formatSar(halalas)
    fun amount(halalas: Long): String = Money.format(halalas)

    fun signedMoney(halalas: Long, direction: Direction): String =
        (if (direction == Direction.INCOME) "+" else "−") + Money.formatSar(halalas)

    fun local(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    fun date(millis: Long): String = local(millis).format(dateFmt)
    fun time(millis: Long): String = local(millis).format(timeFmt)
    fun dateTime(millis: Long): String = local(millis).format(dateTimeFmt)

    fun month(ym: YearMonth): String = "${months[ym.monthValue - 1]} ${ym.year}"
    fun monthShort(ym: YearMonth): String = monthsShort[ym.monthValue - 1]

    fun percent(p: Double): String {
        val sign = if (p > 0) "+" else if (p < 0) "−" else ""
        return sign + String.format(Locale.US, "%.1f", abs(p)) + "%"
    }

    fun channel(c: Channel): String = when (c) {
        Channel.POS -> "حضوري"
        Channel.ONLINE -> "أونلاين"
        Channel.ATM -> "صراف آلي"
        Channel.TRANSFER -> "تحويل"
        Channel.SALARY -> "راتب"
        Channel.REFUND -> "استرداد"
        Channel.OTHER -> "أخرى"
    }

    fun group(g: ChannelGroup): String = when (g) {
        ChannelGroup.ONLINE -> "أونلاين"
        ChannelGroup.IN_PERSON -> "حضوري"
        ChannelGroup.CASH -> "سحب نقدي"
        ChannelGroup.TRANSFER -> "تحويلات"
        ChannelGroup.OTHER -> "أخرى"
    }

    fun direction(d: Direction): String = when (d) {
        Direction.INCOME -> "دخل"
        Direction.EXPENSE -> "صرف"
    }

    fun matchType(m: MatchType): String = when (m) {
        MatchType.CONTAINS -> "يحتوي"
        MatchType.STARTS_WITH -> "يبدأ بـ"
        MatchType.EXACT -> "مطابق تماماً"
    }

    fun count(n: Int, singular: String, dual: String, plural: String): String = when {
        n == 1 -> singular
        n == 2 -> dual
        n in 3..10 -> "$n $plural"
        else -> "$n $singular"
    }
}
