package com.masarif.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** المبالغ تُخزَّن كأعداد صحيحة بالهللات لتجنّب أخطاء الفاصلة العائمة. */
object Money {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val withGrouping = DecimalFormat("#,##0.00", symbols)
    private val plain = DecimalFormat("0.00", symbols)

    /** "47" → 4700، "18.27" → 1827، "5.50" → 550. تعيد null إن لم يكن النص رقماً صالحاً. */
    fun parseHalalas(text: String): Long? {
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        return try {
            BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** 1827 → "18.27"، 123456 → "1,234.56" بأرقام لاتينية دائماً. */
    fun format(halalas: Long, grouping: Boolean = true): String {
        val value = BigDecimal(halalas).movePointLeft(2)
        return (if (grouping) withGrouping else plain).format(value)
    }

    fun formatSar(halalas: Long): String = "${format(halalas)} ر.س"
}
