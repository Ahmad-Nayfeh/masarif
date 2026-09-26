package com.masarif.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** يقرأ قاموس assets الحقيقي ويتحقق أن العينات تُصنَّف كما هو متوقع. */
class SeedDictionaryTest {
    private val file = File("../app/src/main/assets/merchant_seed.json")

    private val rulesByKey: List<Pair<String, Rule>> by lazy {
        SeedDictionaryParser.toRules(SeedDictionaryParser.parse(file.readText()))
    }

    private val rules: List<Rule> by lazy {
        val idByKey = DefaultCategories.all.associate { it.key to it.sortOrder.toLong() }
        rulesByKey.map { (key, r) -> r.copy(categoryId = idByKey.getValue(key)) }
    }

    private fun categoryKeyFor(merchantRaw: String): String? {
        val rule = RuleMatcher.match(MerchantCleaner.key(merchantRaw), rules) ?: return null
        return DefaultCategories.all.first { it.sortOrder.toLong() == rule.categoryId }.key
    }

    @Test
    fun `seed file exists and every category key is valid`() {
        assertTrue(file.exists(), "missing ${file.absolutePath}")
        rulesByKey.forEach { (key, r) ->
            assertTrue(key in DefaultCategories.keys, "unknown category '$key' for alias '${r.pattern}'")
            assertTrue(r.pattern.isNotBlank())
        }
    }

    @Test
    fun `spec samples are categorized by the seed dictionary`() {
        assertEquals("fuel_transport", categoryKeyFor("ALDREES Station Company"))
        assertEquals("supermarket", categoryKeyFor("tamwinat alnoor"))
        assertEquals("supermarket", categoryKeyFor("Sanabel Foodstuff Groce"))
        assertEquals("subscriptions", categoryKeyFor("Zain Recharge"))
        assertEquals("beauty", categoryKeyFor("Sephora"))
        assertEquals("restaurants", categoryKeyFor("Hungerstation"))
        assertEquals("cafes", categoryKeyFor("CUPS COFFEE"))
        assertNull(categoryKeyFor("SKYLINE"), "SKYLINE must stay uncategorized and go to the queue")
        assertNull(categoryKeyFor("GIVEBOX"), "no default category for donations: goes to the queue")
    }

    @Test
    fun `arabic and english aliases unify under one canonical name`() {
        val en = RuleMatcher.match(MerchantCleaner.key("PANDA 0412 RIYADH"), rules)
        val ar = RuleMatcher.match(MerchantCleaner.key("بنده فرع 12"), rules)
        assertEquals("بنده", en?.canonicalName)
        assertEquals("بنده", ar?.canonicalName)
        assertEquals(en?.categoryId, ar?.categoryId)
    }

    @Test
    fun `named merchant beats generic keyword`() {
        assertEquals("aldrees", RuleMatcher.match(MerchantCleaner.key("ALDREES Station Company"), rules)?.pattern)
    }
}
