package com.masarif.core

import java.security.MessageDigest

/** hash فريد من (نص الرسالة + وقت وصولها) لمنع التكرار. */
object SmsHash {
    fun compute(body: String, timestampMillis: Long): String {
        val input = SmsParser.normalize(body) + "\u0000" + timestampMillis
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
