package com.masarif.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuleMatcherTest {
    private fun rule(id: Long, pattern: String, type: MatchType, cat: Long, source: RuleSource = RuleSource.SEED, priority: Int = 10) =
        Rule(id, pattern, type, cat, null, source, priority)

    @Test
    fun `user rule beats seed rule`() {
        val rules = listOf(
            rule(1, "stc", MatchType.CONTAINS, cat = 11),
            rule(2, "costco", MatchType.EXACT, cat = 2, source = RuleSource.USER, priority = 100),
        )
        assertEquals(2L, RuleMatcher.match("costco", rules)?.id)
    }

    @Test
    fun `exact beats startsWith beats contains at same priority`() {
        val rules = listOf(
            rule(1, "pan", MatchType.CONTAINS, 1),
            rule(2, "pand", MatchType.STARTS_WITH, 2),
            rule(3, "panda", MatchType.EXACT, 3),
        )
        assertEquals(3L, RuleMatcher.match("panda", rules)?.id)
        assertEquals(2L, RuleMatcher.match("panda express", rules)?.id)
        assertEquals(1L, RuleMatcher.match("hyperpanda", rules)?.id)
    }

    @Test
    fun `longer pattern wins among contains`() {
        val rules = listOf(
            rule(1, "station", MatchType.CONTAINS, 1),
            rule(2, "aldrees station", MatchType.CONTAINS, 2),
        )
        assertEquals(2L, RuleMatcher.match("aldrees station company", rules)?.id)
    }

    @Test
    fun `patterns are normalized like keys`() {
        val rules = listOf(rule(1, "  إحسان ", MatchType.EXACT, 1))
        assertEquals(1L, RuleMatcher.match(MerchantCleaner.key("احسان"), rules)?.id)
    }

    @Test
    fun `no match returns null and empty pattern never matches`() {
        assertNull(RuleMatcher.match("skyline", listOf(rule(1, "", MatchType.CONTAINS, 1))))
        assertNull(RuleMatcher.match("skyline", emptyList()))
    }
}
