package com.masarif.core

/**
 * تنظيف اسم التاجر: حذف أرقام الفروع والمدن والرموز والفراغات الزائدة.
 * "PANDA 0412 RIYADH" → "PANDA"، "بنده فرع 12" → "بنده".
 * التوحيد العربي/الإنجليزي (PANDA ↔ بنده) يأتي من القاموس عبر الاسم المعياري في القاعدة.
 */
object MerchantCleaner {

    private val cityWords: Set<String> = setOf(
        // إنجليزي
        "riyadh", "riyad", "jeddah", "jiddah", "jedda", "dammam", "khobar", "alkhobar", "makkah", "mecca",
        "madinah", "medina", "jubail", "taif", "tabuk", "abha", "hail", "qassim", "buraidah", "buraydah",
        "yanbu", "khamis", "mushait", "dhahran", "hofuf", "alahsa", "ahsa", "najran", "jazan", "jizan",
        "ksa", "saudi", "arabia", "sa",
        // عربي (بعد توحيد الألف والتاء المربوطة)
        "الرياض", "جده", "الدمام", "الخبر", "مكه", "المدينه", "الجبيل", "الطائف", "تبوك", "ابها", "حائل",
        "القصيم", "بريده", "ينبع", "خميس", "مشيط", "الظهران", "الهفوف", "الاحساء", "نجران", "جازان",
        "جيزان", "السعوديه",
    )

    private val noiseWords: Set<String> = setOf("branch", "br", "فرع")

    private val symbols = Regex("""[^\p{L}\p{N}\s]""")
    private val whitespace = Regex("""\s+""")
    private val arabicDiacritics = Regex("""[ً-ْٰـ]""")

    /** توحيد الحروف العربية للمقارنة: أ إ آ → ا، ة → ه، ى → ي، وحذف التشكيل. */
    fun normalizeArabic(s: String): String =
        s.replace(arabicDiacritics, "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')

    /** الاسم النظيف للعرض والتجميع (يحافظ على حالة الأحرف الأصلية). */
    fun clean(raw: String): String {
        val tokens = raw.replace(symbols, " ")
            .split(whitespace)
            .filter { it.isNotBlank() }
        val kept = tokens.filter { token ->
            val key = normalizeArabic(token.lowercase())
            !token.all { it.isDigit() } && key !in cityWords && key !in noiseWords
        }
        val result = kept.joinToString(" ")
        // إن حُذف كل شيء (اسم رقمي بحت مثلاً) نعود للنص الأصلي مضغوطاً.
        return result.ifBlank { raw.replace(whitespace, " ").trim() }
    }

    /** مفتاح المطابقة: الاسم النظيف بأحرف صغيرة وحروف عربية موحّدة. */
    fun key(raw: String): String = normalizeForMatch(clean(raw))

    /** يُطبَّق على المفتاح وعلى أنماط القواعد معاً حتى تتطابق بنفس الصورة. */
    fun normalizeForMatch(s: String): String =
        normalizeArabic(s.lowercase()).replace(whitespace, " ").trim()
}
