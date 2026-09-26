package com.masarif.core

/** قاعدة تاجر مستقلة عن قاعدة البيانات (لتُختبر بلا Android). */
data class Rule(
    val id: Long,
    val pattern: String,
    val matchType: MatchType,
    val categoryId: Long,
    /** اسم معياري للعرض (بنده) يوحّد العربي/الإنجليزي. */
    val canonicalName: String?,
    val source: RuleSource,
    /** الأعلى يفوز. قواعد المستخدم أعلى من القاموس دائماً. */
    val priority: Int,
)

object RuleMatcher {
    const val PRIORITY_USER = 100
    const val PRIORITY_SEED_NAMED = 10
    const val PRIORITY_SEED_GENERIC = 1

    private fun rank(t: MatchType) = when (t) {
        MatchType.EXACT -> 0
        MatchType.STARTS_WITH -> 1
        MatchType.CONTAINS -> 2
    }

    /** الترتيب: الأولوية الأعلى، ثم exact > startsWith > contains، ثم النمط الأطول. */
    fun sorted(rules: List<Rule>): List<Rule> =
        rules.sortedWith(
            compareByDescending<Rule> { it.priority }
                .thenBy { rank(it.matchType) }
                .thenByDescending { it.pattern.length }
                .thenBy { it.id }
        )

    fun matches(rule: Rule, merchantKey: String): Boolean {
        val p = MerchantCleaner.normalizeForMatch(rule.pattern)
        if (p.isEmpty() || merchantKey.isEmpty()) return false
        return when (rule.matchType) {
            MatchType.EXACT -> merchantKey == p
            MatchType.STARTS_WITH -> merchantKey.startsWith(p)
            MatchType.CONTAINS -> merchantKey.contains(p)
        }
    }

    /** أول قاعدة تطابق مفتاح التاجر (القواعد تُرتّب داخلياً). */
    fun match(merchantKey: String, rules: List<Rule>): Rule? =
        sorted(rules).firstOrNull { matches(it, merchantKey) }
}
