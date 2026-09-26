package com.masarif.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** نماذج ملفات JSON: تصدير/استيراد القواعد، والنسخة الاحتياطية الكاملة. مستقلة عن Room. */

@Serializable
data class CategoryJson(
    val id: Long,
    val key: String? = null,
    val nameAr: String,
    val icon: String,
    val color: Long,
    val isIncome: Boolean,
    val sortOrder: Int,
    val protected: Boolean = false,
)

@Serializable
data class RuleJson(
    val id: Long = 0,
    val pattern: String,
    val matchType: String,
    /** اسم التصنيف بالعربي: يُطابَق بالاسم عند الاستيراد حتى يعمل الملف بين الأجهزة. */
    val categoryName: String,
    val canonicalName: String? = null,
    val createdBy: String = "user",
    val priority: Int = RuleMatcher.PRIORITY_USER,
    val createdAt: Long = 0,
)

@Serializable
data class TransactionJson(
    val id: Long,
    val dateTime: Long,
    val amountHalalas: Long,
    val direction: String,
    val rawSms: String,
    val merchantRaw: String,
    val merchantClean: String,
    val categoryId: Long,
    val channel: String,
    val country: String? = null,
    val cardLast4: String? = null,
    val smsHash: String,
    val isManual: Boolean,
    val categoryOverridden: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
data class UnparsedJson(
    val id: Long,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val smsHash: String,
    val reason: String = "",
)

@Serializable
data class RulesExport(
    val format: String = "masarif-rules",
    val version: Int = 1,
    val exportedAt: Long,
    val rules: List<RuleJson>,
)

@Serializable
data class BackupFile(
    val format: String = "masarif-backup",
    val version: Int = 1,
    val exportedAt: Long,
    val senders: List<String>,
    val categories: List<CategoryJson>,
    val rules: List<RuleJson>,
    val transactions: List<TransactionJson>,
    val unparsed: List<UnparsedJson>,
)

object BackupJson {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encodeBackup(b: BackupFile): String = json.encodeToString(BackupFile.serializer(), b)
    fun decodeBackup(text: String): BackupFile = json.decodeFromString(BackupFile.serializer(), text)
    fun encodeRules(r: RulesExport): String = json.encodeToString(RulesExport.serializer(), r)
    fun decodeRules(text: String): RulesExport = json.decodeFromString(RulesExport.serializer(), text)
}
