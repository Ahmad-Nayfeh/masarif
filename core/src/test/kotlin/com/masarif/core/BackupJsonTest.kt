package com.masarif.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupJsonTest {
    @Test
    fun `backup round trip`() {
        val backup = BackupFile(
            exportedAt = 1L,
            senders = listOf("alinma"),
            categories = listOf(CategoryJson(1, "grocery", "بقالة", "🛒", 0xFF4CAF50, false, 1)),
            rules = listOf(RuleJson(1, "aldrees", "CONTAINS", "بنزين", "الدريس", "seed", 10, 0)),
            transactions = listOf(
                TransactionJson(1, 2L, 4700, "EXPENSE", "raw", "ALDREES Station Company", "الدريس", 5, "POS", null, "4321", "h", false)
            ),
            unparsed = listOf(UnparsedJson(1, "alinma", "body", 3L, "h2", "no_known_header")),
        )
        val text = BackupJson.encodeBackup(backup)
        assertEquals(backup, BackupJson.decodeBackup(text))
    }

    @Test
    fun `rules export round trip`() {
        val export = RulesExport(exportedAt = 5L, rules = listOf(RuleJson(pattern = "cafe", matchType = "CONTAINS", categoryName = "مطاعم وكافيهات")))
        assertEquals(export, BackupJson.decodeRules(BackupJson.encodeRules(export)))
    }

    @Test
    fun `money formatting`() {
        assertEquals("18.27", Money.format(1827))
        assertEquals("1,234.50", Money.format(123450))
        assertEquals(4700L, Money.parseHalalas("47"))
        assertEquals(160L, Money.parseHalalas("1.60"))
        assertEquals(null, Money.parseHalalas("abc"))
    }
}
