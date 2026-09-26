package com.masarif.core

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId

/** محلّل رسائل الإنماء. يعتمد على [SmsPatterns] فقط. */
object SmsParser {

    /**
     * @param body نص الرسالة كاملاً.
     * @param receivedAtMillis وقت وصول الرسالة (احتياط إن فشل تحليل التاريخ من النص).
     * @param zone المنطقة الزمنية لتفسير تاريخ الرسالة.
     */
    fun parse(body: String, receivedAtMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ParseResult {
        val text = normalize(body)
        if (text.isEmpty()) return ParseResult.Ignored

        // بوابتا التجاهل: لا مبلغ، أو رمز تحقق (فيه مبلغ لكنه ليس عملية).
        if (!SmsPatterns.anyAmount.containsMatchIn(text)) return ParseResult.Ignored
        if (SmsPatterns.otp.containsMatchIn(text)) return ParseResult.Ignored

        val type = SmsPatterns.types.firstOrNull { t ->
            t.header.containsMatchIn(text) && (t.requires == null || t.requires.containsMatchIn(text))
        } ?: return ParseResult.Unrecognized("no_known_header")

        val amount = type.amount(text)
            ?: return ParseResult.Unrecognized("amount_not_found:${type.name}")
        if (amount <= 0) return ParseResult.Unrecognized("amount_not_positive:${type.name}")

        val merchant = type.counterparty(text)?.let(::collapse)?.takeIf { it.isNotBlank() }
            ?: type.fallbackMerchant
            ?: return ParseResult.Unrecognized("merchant_not_found:${type.name}")

        val card = SmsPatterns.card.find(text)?.groupValues?.get(1)
        val country = if (type.channel == Channel.ONLINE) {
            SmsPatterns.country.find(text)?.groupValues?.get(1)?.let(::collapse)?.takeIf { it.isNotBlank() }
        } else null
        val messageDate = parseDate(text, zone)

        return ParseResult.Parsed(
            ParsedSms(
                amountHalalas = amount,
                direction = type.direction,
                channel = type.channel,
                merchantRaw = merchant,
                cardLast4 = card,
                country = country,
                dateTimeMillis = messageDate ?: receivedAtMillis,
                dateFromMessage = messageDate != null,
                typeName = type.name,
            )
        )
    }

    /** توحيد نهايات الأسطر وقصّ الفراغات الطرفية. يُستخدم أيضاً قبل حساب الـ hash. */
    fun normalize(body: String): String =
        body.replace("\r\n", "\n").replace('\r', '\n').trim()

    private fun collapse(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    /** يبحث عن أي تاريخ بصيغة معروفة في النص. null إن لم يوجد أو كان غير صالح. */
    internal fun parseDate(text: String, zone: ZoneId): Long? {
        SmsPatterns.dateYyyyMmDd.find(text)?.let { m ->
            val (y, mo, d, h, mi) = m.destructured
            return toMillis(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), zone)
        }
        SmsPatterns.timeThenDate.find(text)?.let { m ->
            val (h, mi, y, mo, d) = m.destructured
            return toMillis(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), zone)
        }
        SmsPatterns.dateYyMmDd.find(text)?.let { m ->
            val (yy, mo, d, h, mi) = m.destructured
            return toMillis(2000 + yy.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), zone)
        }
        return null
    }

    private fun toMillis(y: Int, mo: Int, d: Int, h: Int, mi: Int, zone: ZoneId): Long? = try {
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    } catch (e: DateTimeException) {
        null
    }
}
