package com.masarif.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * اختبار اختياري على بيانات حقيقية: يقرأ ملف نسخة احتياطية من المسار في المتغير البيئي
 * MASARIF_BACKUP (إن وُجد) ويتحقق أن كل رسالة "غير مفهومة" سابقة صارت مفهومة أو متجاهَلة عن قصد.
 * لا يعمل في CI (المتغير غير معرّف)، ولا يضع أي بيانات شخصية في المستودع.
 */
class RealDataSmokeTest {
    @Test
    fun `every previously unparsed real message is now parsed or deliberately ignored`() {
        val path = System.getenv("MASARIF_BACKUP") ?: return
        val file = File(path)
        if (!file.exists()) return
        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(file.readText()).jsonObject
        val zone = ZoneId.of("Asia/Riyadh")
        val counts = mutableMapOf<String, Int>()
        val examples = mutableMapOf<String, String>()
        val unrecognized = mutableListOf<String>()
        for (u in root.getValue("unparsed").jsonArray) {
            val o = u.jsonObject
            val body = o.getValue("body").jsonPrimitive.content
            val at = o.getValue("receivedAt").jsonPrimitive.content.toLong()
            when (val r = SmsParser.parse(body, at, zone)) {
                is ParseResult.Parsed -> {
                    counts.merge(r.sms.typeName, 1, Int::plus)
                    val s = r.sms
                    examples.putIfAbsent(s.typeName, "${s.direction}/${s.channel} | ${Money.format(s.amountHalalas)} | ${s.merchantRaw} | card=${s.cardLast4} | date=${java.time.Instant.ofEpochMilli(s.dateTimeMillis).atZone(zone).toLocalDateTime()} fromMsg=${s.dateFromMessage}")
                }
                ParseResult.Ignored -> counts.merge("IGNORED", 1, Int::plus)
                is ParseResult.Unrecognized -> unrecognized += body
            }
        }
        println("real-data summary: $counts")
        examples.forEach { (t, e) -> println("example[$t]: $e") }
        if (unrecognized.isNotEmpty()) {
            println("still unrecognized (${unrecognized.size}):")
            unrecognized.take(10).forEach { println("---\n$it") }
        }
        assertTrue(unrecognized.isEmpty(), "${unrecognized.size} real messages are still unrecognized")
    }
}
