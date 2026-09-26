package com.masarif.core

/**
 * كل أنماط رسائل الإنماء في ملف واحد.
 *
 * مبنية على عينات حقيقية (17 صيغة عمليات + 6 صيغ رموز تحقق). كل صيغة تعرّف بنفسها كيف تستخرج
 * المبلغ والطرف الآخر، لأن العنوان نفسه يتغير معناه بين الصيغ (At: تاجر في الشراء، وتاريخ في
 * التحويلات). التاريخ يُلتقط بأي صيغة من الصيغ المعروفة في أي مكان من النص.
 *
 * لإضافة صيغة جديدة: أضف عنصراً واحداً إلى [types].
 */
object SmsPatterns {
    /** يزداد عند كل تغيير في الأنماط حتى يعيد التطبيق تحليل "غير المفهومة" تلقائياً بعد التحديث. */
    const val VERSION = 2

    private val ci = setOf(RegexOption.IGNORE_CASE)
    private val ciMulti = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)

    /** رقم بفواصل آلاف اختيارية وكسور اختيارية: 47 / 18.27 / 1,150 / 10,000.50 */
    const val NUM = """(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)"""

    /** بوابة التجاهل 1: لا مبلغ بالريال. */
    val anyAmount = Regex("""$NUM\s*SAR\b""", ci)

    /** بوابة التجاهل 2: رموز التحقق تحتوي مبلغاً لكنها ليست عمليات؛ العملية تصل في رسالة مستقلة. */
    val otp = Regex(
        """(use\s+the\s+code|use\s+the\s+otp|use\s+otp|\botp\s*:|activation\s+code|complete\s+the\s+internet\s+purchase\s+transaction)""",
        ci,
    )

    /** Amount:47 SAR / Amount: 1,150 SAR / Amount 87 SAR (بلا نقطتين) */
    val amountField = Regex("""\bAmount\s*:?\s*$NUM\s*SAR\b""", ci)

    /** Mada card:4321* / mada Card: 4321* / Card 4321* / From mada Card: 4321* */
    val card = Regex("""\bcard\s*(?:number)?\s*:?\s*\*?(\d{4})""", ci)

    /** In: United Kingdom (الدولة في الشراء الدولي) */
    val country = Regex("""^\s*In\s*:\s*(.+?)\s*$""", ciMulti)

    /** التواريخ بأي صيغة: 26-09-21 15:58 | 2025-8-21 10:19 | 21:28 2025-09-23 */
    val dateYyMmDd = Regex("""\b(\d{2})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})\b""")
    val dateYyyyMmDd = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})\b""")
    val timeThenDate = Regex("""\b(\d{1,2}):(\d{2})\s+(\d{4})-(\d{1,2})-(\d{1,2})\b""")

    // ---------- مستخرِجات مساعدة ----------

    /** قيمة سطر يبدأ بأحد العناوين، بنقطتين أو بدونهما، حتى نهاية السطر. */
    fun lineAfter(vararg labels: String): (String) -> String? = { body ->
        labels.asSequence().mapNotNull { label ->
            Regex("""^\s*$label\s*:?\s*(.+?)\s*$""", ciMulti).find(body)?.groupValues?.get(1)
        }.firstOrNull { it.isNotBlank() }
    }

    /** قيمة عنوان قد تمتد لأكثر من سطر حتى العنوان التالي (At: في الشراء قد ينكسر سطرين). */
    fun fieldUntilNextLabel(label: String, nextLabels: String = "In|On|Amount|Account|mada\\s*card|From"): (String) -> String? = { body ->
        Regex("""\b$label\s*:\s*(.+?)(?=\s+(?:$nextLabels)\s*:|\s*$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(body)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() }
    }

    fun fieldAmount(body: String): Long? = amountField.find(body)?.let { Money.parseHalalas(it.groupValues[1]) }

    fun headerAmount(header: String): (String) -> Long? = { body ->
        Regex("""^\s*$header\s+$NUM\s*SAR\b""", ci).find(body)?.let { Money.parseHalalas(it.groupValues[1]) }
    }

    /** صيغة رسالة واحدة. */
    class MessageType(
        val name: String,
        val header: Regex,
        val direction: Direction,
        val channel: Channel,
        val amount: (String) -> Long? = ::fieldAmount,
        /** التاجر أو الطرف الآخر. null = يُستخدم [fallbackMerchant]. */
        val counterparty: (String) -> String? = { null },
        val fallbackMerchant: String? = null,
        /** شرط إضافي بعد العنوان (للصيغ التي عنوانها عام). */
        val requires: Regex? = null,
    )

    private fun header(pattern: String) = Regex("""^\s*$pattern""", ci)

    val types: List<MessageType> = listOf(
        // ===== شراء حضوري =====
        MessageType(
            name = "pos_purchase", header = header("""Purchase\s+by\s+mada\b"""),
            direction = Direction.EXPENSE, channel = Channel.POS,
            counterparty = fieldUntilNextLabel("At"),
        ),
        MessageType(
            name = "pos_atheer", header = header("""mada\s+Atheer\s+POS\s+Purchase\b"""),
            direction = Direction.EXPENSE, channel = Channel.POS,
            counterparty = fieldUntilNextLabel("At"),
        ),
        // mada Atheer Purchase 6.25 SAR / Card 4321* / At GREENWAY / 25-12-25 01:57
        MessageType(
            name = "pos_atheer_short", header = header("""mada\s+Atheer\s+Purchase\s+$NUM\s*SAR\b"""),
            direction = Direction.EXPENSE, channel = Channel.POS,
            amount = headerAmount("""mada\s+Atheer\s+Purchase"""),
            counterparty = lineAfter("At"),
        ),
        // ===== شراء أونلاين (محلي ودولي) =====
        MessageType(
            name = "online_purchase", header = header("""Online\s+Purchase\b"""),
            direction = Direction.EXPENSE, channel = Channel.ONLINE,
            amount = { body -> fieldAmount(body) ?: headerAmount("""Online\s+Purchase""")(body) },
            counterparty = fieldUntilNextLabel("At"),
        ),
        // ===== سحب صراف =====
        MessageType(
            name = "atm_withdrawal", header = header("""ATM\s+withdrawal\b"""),
            direction = Direction.EXPENSE, channel = Channel.ATM,
            counterparty = { body -> lineAfter("In")(body)?.let { "صراف $it" } },
            fallbackMerchant = "سحب نقدي",
        ),
        // ===== تحويلات صادرة =====
        MessageType(
            name = "transfer_out_internal", header = header("""Debit\s+Transfer\s+Internal\b"""),
            direction = Direction.EXPENSE, channel = Channel.TRANSFER,
            counterparty = lineAfter("To\\s+beneficiary", "To"),
        ),
        MessageType(
            name = "transfer_out_local", header = header("""Outgoing\s+Local\s+Transfer\b"""),
            direction = Direction.EXPENSE, channel = Channel.TRANSFER,
            counterparty = { body -> Regex("""^\s*To\s+(?!Account\b)(.+?)\s*$""", ciMulti).find(body)?.groupValues?.get(1) },
        ),
        MessageType(
            name = "transfer_out_sarie", header = header("""Debit\s+Transfer\s+Local\b"""),
            direction = Direction.EXPENSE, channel = Channel.TRANSFER,
            counterparty = lineAfter("To\\s+Beneficiary"),
        ),
        // ===== تحويلات واردة =====
        MessageType(
            name = "transfer_in_internal", header = header("""Credit\s+Transfer\s+Internal\b"""),
            direction = Direction.INCOME, channel = Channel.TRANSFER,
            counterparty = lineAfter("From"),
        ),
        MessageType(
            name = "transfer_in_local", header = header("""Incoming\s+Local\s+Transfer\b"""),
            direction = Direction.INCOME, channel = Channel.TRANSFER,
            counterparty = { body -> Regex("""^\s*From\s+(?!Account\b|Bank\b)(.+?)\s*$""", ciMulti).find(body)?.groupValues?.get(1) },
        ),
        // Transfer Incoming 13 SAR / From احمد*; *4000 / On 26-09-18 23:52
        MessageType(
            name = "transfer_in_short", header = header("""Transfer\s+Incoming\s+$NUM\s*SAR\b"""),
            direction = Direction.INCOME, channel = Channel.TRANSFER,
            amount = headerAmount("""Transfer\s+Incoming"""),
            counterparty = { body -> lineAfter("From")(body)?.replace(Regex("""\s*\*.*$"""), "")?.trim() },
        ),
        MessageType(
            name = "transfer_in_sarie", header = header("""Incoming\s+Funds\s+Transfer\b"""),
            direction = Direction.INCOME, channel = Channel.TRANSFER,
            counterparty = { body -> Regex("""^\s*From\s*:\s*(.+?)\s*$""", ciMulti).find(body)?.groupValues?.get(1) },
        ),
        // ===== راتب / استرداد =====
        MessageType(
            name = "salary", header = header("""Incoming\s+salary\s+transfer\b"""),
            direction = Direction.INCOME, channel = Channel.SALARY,
            fallbackMerchant = "راتب",
        ),
        MessageType(
            name = "refund", header = header("""Reverse\s+Transaction\b"""),
            direction = Direction.INCOME, channel = Channel.REFUND,
            counterparty = lineAfter("From"),
            fallbackMerchant = "استرداد",
        ),
        // ===== فواتير ومدفوعات حكومية =====
        // Bill Payment / No: / Amount / Biller: Mobily / Service: Bills
        MessageType(
            name = "bill_payment", header = header("""Bill\s+Payment[ \t]*(?:\n|$)"""),
            direction = Direction.EXPENSE, channel = Channel.OTHER,
            counterparty = lineAfter("Biller"),
            fallbackMerchant = "فاتورة",
        ),
        // Bill Payment: Bills for Mobily - Bills
        MessageType(
            name = "bill_payment_inline", header = header("""Bill\s+Payment\s*:"""),
            direction = Direction.EXPENSE, channel = Channel.OTHER,
            counterparty = { body ->
                lineAfter("Bill\\s+Payment")(body)?.let { raw ->
                    Regex("""(?:bills?\s+for\s+)?(.+?)(?:\s+-\s+.*)?$""", ci).find(raw)?.groupValues?.get(1)?.trim() ?: raw
                }
            },
            fallbackMerchant = "فاتورة",
        ),
        // Amount:150.0 SAR / Entity:Traffic Violation / Service: ... / Date: ...
        MessageType(
            name = "gov_payment", header = header("""Amount\s*:"""), requires = Regex("""^\s*Entity\s*:""", ciMulti),
            direction = Direction.EXPENSE, channel = Channel.OTHER,
            counterparty = lineAfter("Entity"),
            fallbackMerchant = "رسوم حكومية",
        ),
    )
}
