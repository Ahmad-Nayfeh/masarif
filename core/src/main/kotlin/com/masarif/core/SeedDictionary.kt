package com.masarif.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * قاموس التجار المبدئي (يُقرأ من assets/merchant_seed.json).
 * كل مدخل: أسماء بديلة (عربي/إنجليزي)، اسم معياري للعرض، مفتاح تصنيف، طريقة مطابقة، أولوية.
 */
@Serializable
data class SeedEntry(
    val aliases: List<String>,
    val canonical: String? = null,
    val category: String,
    val match: String = "contains",
    val priority: Int = RuleMatcher.PRIORITY_SEED_NAMED,
) {
    fun matchType(): MatchType = when (match.lowercase()) {
        "exact" -> MatchType.EXACT
        "startswith", "starts_with" -> MatchType.STARTS_WITH
        else -> MatchType.CONTAINS
    }
}

@Serializable
data class SeedDictionary(val entries: List<SeedEntry>)

object SeedDictionaryParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): SeedDictionary = json.decodeFromString(SeedDictionary.serializer(), text)

    /** يحوّل القاموس إلى قواعد؛ يُرجع categoryKey مع كل قاعدة ليربطها التطبيق بمعرّف التصنيف. */
    fun toRules(dict: SeedDictionary): List<Pair<String, Rule>> {
        var id = -1L
        return dict.entries.flatMap { e ->
            e.aliases.map { alias ->
                id -= 1
                e.category to Rule(
                    id = id,
                    pattern = alias,
                    matchType = e.matchType(),
                    categoryId = 0L,
                    canonicalName = e.canonical,
                    source = RuleSource.SEED,
                    priority = e.priority,
                )
            }
        }
    }
}
