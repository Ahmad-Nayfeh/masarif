package com.masarif.core

/** اتجاه العملية. */
enum class Direction { INCOME, EXPENSE }

/** القناة تُستنتج من صيغة الرسالة لا من التاجر. */
enum class Channel { POS, ONLINE, ATM, TRANSFER, SALARY, REFUND, OTHER }

/** طريقة مطابقة قاعدة التاجر. */
enum class MatchType { CONTAINS, STARTS_WITH, EXACT }

/** من أنشأ القاعدة. */
enum class RuleSource { SEED, USER }

/** ناتج تحليل رسالة مالية مفهومة. المبلغ بالهللات (موجب دائماً). */
data class ParsedSms(
    val amountHalalas: Long,
    val direction: Direction,
    val channel: Channel,
    val merchantRaw: String,
    val cardLast4: String?,
    val country: String?,
    val dateTimeMillis: Long,
    /** true إن استُخرج التاريخ من نص الرسالة، false إن اعتمدنا وقت الوصول. */
    val dateFromMessage: Boolean,
    /** اسم الصيغة التي طابقت الرسالة (من SmsPatterns). */
    val typeName: String,
)

/** نتيجة تحليل رسالة واحدة. */
sealed class ParseResult {
    data class Parsed(val sms: ParsedSms) : ParseResult()

    /** رسالة غير مالية (رمز تحقق، إعلان): تُتجاهل بصمت. */
    data object Ignored : ParseResult()

    /** رسالة فيها مبلغ بالريال لكن صيغتها مجهولة: تُحفظ كاملة في "غير مفهومة". */
    data class Unrecognized(val reason: String) : ParseResult()
}
