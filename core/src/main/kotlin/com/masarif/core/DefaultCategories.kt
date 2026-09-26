package com.masarif.core

/** تصنيف افتراضي. المفتاح ثابت ويُستخدم في قاموس التجار والنسخ الاحتياطي. */
data class DefaultCategory(
    val key: String,
    val nameAr: String,
    val icon: String,
    val color: Long,
    val isIncome: Boolean,
    val sortOrder: Int,
    /** لا يمكن حذفه (النظام يعتمد عليه). */
    val protected: Boolean = false,
    /** وصف قصير يظهر في نافذة الاختيار (للاشتراكات مثلاً). */
    val hint: String? = null,
)

object DefaultCategories {
    const val UNCATEGORIZED_KEY = "uncategorized"
    const val SUBSCRIPTIONS_KEY = "subscriptions"
    const val CASH_KEY = "cash_withdrawal"
    const val SALARY_KEY = "salary"
    const val TRANSFERS_IN_KEY = "transfers_in"
    const val REFUND_KEY = "refund"

    /** قليلة عمداً؛ المستخدم ينشئ ما يريد من نافذة الاختيار مباشرة. */
    val all: List<DefaultCategory> = listOf(
        DefaultCategory(SUBSCRIPTIONS_KEY, "اشتراكات دورية", "🔁", 0xFF7C3AED, false, 1,
            hint = "كل ما يُسحب من البطاقة بشكل متكرر: شهري أو سنوي (تطبيقات، باقات، عضويات)"),
        DefaultCategory("supermarket", "سوبرماركت", "🛒", 0xFF4CAF50, false, 2),
        DefaultCategory("restaurants", "مطاعم", "🍽️", 0xFFFF9800, false, 3),
        DefaultCategory("cafes", "كافيهات", "☕", 0xFF8D6E63, false, 4),
        DefaultCategory("fuel_transport", "بنزين ومواصلات", "⛽", 0xFF795548, false, 5),
        DefaultCategory("clothes", "ملابس وأحذية", "👕", 0xFF3F51B5, false, 6),
        DefaultCategory("beauty", "عطور وتجميل", "🧴", 0xFFE91E63, false, 7),
        DefaultCategory(CASH_KEY, "سحب نقدي", "🏧", 0xFF78909C, false, 8),
        DefaultCategory(UNCATEGORIZED_KEY, "غير مصنّف", "❔", 0xFFBDBDBD, false, 9, protected = true),
        // الدخل: يُعيَّن تلقائياً من صيغة الرسالة ولا يظهر في اختيار المصروفات.
        DefaultCategory(SALARY_KEY, "راتب", "💼", 0xFF43A047, true, 10, protected = true),
        DefaultCategory(TRANSFERS_IN_KEY, "تحويلات واردة", "📥", 0xFF66BB6A, true, 11, protected = true),
        DefaultCategory(REFUND_KEY, "استرداد", "↩️", 0xFF26A69A, true, 12, protected = true),
    )

    val keys: Set<String> = all.map { it.key }.toSet()

    /**
     * ترحيل الإصدار الأول: مفتاح قديم → مفتاح جديد يُدمج فيه، أو null = يبقى كتصنيف عادي إن كان
     * مستخدماً (فيه عمليات أو قواعد) ويُحذف إن كان فارغاً.
     */
    val legacyMap: Map<String, String?> = mapOf(
        "grocery" to "supermarket",
        "supermarket" to "supermarket",
        "restaurants" to "restaurants",
        "food_delivery" to "restaurants",
        "fuel" to "fuel_transport",
        "transport" to "fuel_transport",
        "perfume_care" to "beauty",
        "clothes" to "clothes",
        "telecom" to SUBSCRIPTIONS_KEY,
        "subscriptions" to SUBSCRIPTIONS_KEY,
        "cash_withdrawal" to CASH_KEY,
        "uncategorized" to UNCATEGORIZED_KEY,
        "salary" to SALARY_KEY,
        "other_income" to TRANSFERS_IN_KEY,
        "barber" to null,
        "electronics" to null,
        "bills" to null,
        "health" to null,
        "housing" to null,
        "donations" to null,
        "transfers_people" to null,
        "online_shopping" to null,
        "entertainment" to null,
        "other" to null,
    )

    /** لوحة ألوان يختار منها المستخدم عند إنشاء تصنيف. */
    val palette: List<Long> = listOf(
        0xFF4CAF50, 0xFF8BC34A, 0xFFFF9800, 0xFFFF5722, 0xFF795548, 0xFFFFC107, 0xFFE91E63, 0xFF9C27B0,
        0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688, 0xFFF44336, 0xFF607D8B, 0xFF8D6E63,
        0xFF5C6BC0, 0xFF78909C, 0xFFAB47BC, 0xFF26A69A, 0xFF9E9E9E, 0xFFFFEB3B,
    )

    /** رموز مقترحة للفئة الجديدة (يمكن كتابة أي رمز آخر). */
    val emojiSuggestions: List<String> = listOf(
        "🏷️", "🏠", "💊", "🎮", "📚", "🎁", "🤲", "💸", "🛍️", "📱", "🧾", "✈️", "🚗", "🧺", "💧", "🪑", "💈", "🐾",
    )
}
