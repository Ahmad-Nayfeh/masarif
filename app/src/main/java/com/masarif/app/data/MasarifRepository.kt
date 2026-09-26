package com.masarif.app.data

import com.masarif.app.data.db.AppDatabase
import com.masarif.app.data.db.CategoryEntity
import com.masarif.app.data.db.MerchantRuleEntity
import com.masarif.app.data.db.PendingMerchant
import com.masarif.app.data.db.TransactionEntity
import com.masarif.app.data.db.UnparsedSmsEntity
import com.masarif.app.data.prefs.SettingsStore
import com.masarif.core.BackupFile
import com.masarif.core.BackupJson
import com.masarif.core.CategoryJson
import com.masarif.core.Channel
import com.masarif.core.DefaultCategories
import com.masarif.core.Direction
import com.masarif.core.ImportPeriod
import com.masarif.core.MatchType
import com.masarif.core.MerchantCleaner
import com.masarif.core.ParseResult
import com.masarif.core.ParsedSms
import com.masarif.core.Rule
import com.masarif.core.RuleJson
import com.masarif.core.RuleMatcher
import com.masarif.core.RuleSource
import com.masarif.core.RulesExport
import com.masarif.core.SeedDictionaryParser
import com.masarif.core.SmsHash
import com.masarif.core.SmsParser
import com.masarif.core.SmsPatterns
import com.masarif.core.Subscription
import com.masarif.core.SubscriptionPayment
import com.masarif.core.SubscriptionStats
import com.masarif.core.TransactionJson
import com.masarif.core.UnparsedJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class IngestOutcome { NOT_SENDER, DUPLICATE, IGNORED, UNPARSED, INSERTED, TOO_OLD }

data class ScanResult(
    val scanned: Int = 0,
    val matched: Int = 0,
    val inserted: Int = 0,
    val unparsed: Int = 0,
    val ignored: Int = 0,
    val duplicates: Int = 0,
    val tooOld: Int = 0,
) {
    operator fun plus(o: IngestOutcome): ScanResult = when (o) {
        IngestOutcome.NOT_SENDER -> this
        IngestOutcome.DUPLICATE -> copy(matched = matched + 1, duplicates = duplicates + 1)
        IngestOutcome.IGNORED -> copy(matched = matched + 1, ignored = ignored + 1)
        IngestOutcome.UNPARSED -> copy(matched = matched + 1, unparsed = unparsed + 1)
        IngestOutcome.INSERTED -> copy(matched = matched + 1, inserted = inserted + 1)
        IngestOutcome.TOO_OLD -> copy(matched = matched + 1, tooOld = tooOld + 1)
    }
}

/**
 * المسار الموحّد لكل رسالة (حيّة أو من المسح): مرسل → hash → تحليل → فترة → تنظيف → قاعدة → إدراج.
 * كل عمليات التصنيف والقواعد والتصنيفات والنسخ الاحتياطي هنا أيضاً. كل الاستعلامات مقيّدة
 * بالفترة التي اختارها المستخدم (importStart).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MasarifRepository(
    val db: AppDatabase,
    val settings: SettingsStore,
    private val seedJsonProvider: () -> String,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val onPendingChanged: suspend (Int) -> Unit = {},
) {
    private val txDao get() = db.transactionDao()
    private val ruleDao get() = db.merchantRuleDao()
    private val catDao get() = db.categoryDao()
    private val unparsedDao get() = db.unparsedSmsDao()

    @Volatile private var keyIdCache: Map<String, Long> = emptyMap()
    private val seedMutex = Mutex()

    // ---------- التهيئة والترحيل ----------

    /** يزرع التصنيفات الافتراضية وقاموس التجار عند أول تشغيل (آمن للاستدعاء المتكرر والمتزامن). */
    suspend fun ensureSeeded() = seedMutex.withLock {
        if (catDao.count() == 0) {
            catDao.insertAll(DefaultCategories.all.map { it.toEntity() })
            keyIdCache = emptyMap()
        }
        if (ruleDao.countBySource(RuleSource.SEED) == 0) {
            seedRules()
        }
    }

    private fun com.masarif.core.DefaultCategory.toEntity() = CategoryEntity(
        catKey = key, nameAr = nameAr, icon = icon, color = color, isIncome = isIncome, sortOrder = sortOrder, isProtected = protected,
    )

    private suspend fun seedRules() {
        val dict = SeedDictionaryParser.parse(seedJsonProvider())
        val now = System.currentTimeMillis()
        val entities = SeedDictionaryParser.toRules(dict).mapNotNull { (key, r) ->
            val cat = catDao.getByKey(key) ?: return@mapNotNull null
            MerchantRuleEntity(
                pattern = r.pattern, matchType = r.matchType, categoryId = cat.id,
                canonicalName = r.canonicalName, createdBy = RuleSource.SEED, priority = r.priority, createdAt = now,
            )
        }
        ruleDao.insertAll(entities)
    }

    /**
     * ترحيل الإصدار 1 → 2 بلا فقدان: التصنيفات القديمة المكافئة تُدمج في الجديدة، المستخدمة
     * تبقى كتصنيفات عادية، الفارغة تُحذف، وقواعد القاموس القديمة التي بقيت مفيدة تصبح قواعد
     * مستخدم ثم يُزرع القاموس الجديد. آمن للاستدعاء المتكرر.
     */
    suspend fun migrateLegacyIfNeeded(force: Boolean = false) {
        if (!force && settings.setupVersionNow() >= SettingsStore.CURRENT_SETUP_VERSION) return
        seedMutex.withLock {
            val existing = catDao.getAll()
            if (existing.isEmpty()) return@withLock // تثبيت جديد: ensureSeeded يكفي
            val byKey = existing.filter { it.catKey != null }.associateBy { it.catKey!! }
            val legacyPresent = byKey.keys.any { it in DefaultCategories.legacyMap && it !in DefaultCategories.keys }
            val missing = DefaultCategories.all.any { it.key !in byKey }
            if (!legacyPresent && !missing) return@withLock
            // 1) أنشئ التصنيفات الجديدة الناقصة.
            val newIds = mutableMapOf<String, Long>()
            for (d in DefaultCategories.all) {
                val found = byKey[d.key]
                newIds[d.key] = if (found != null) {
                    catDao.update(found.copy(nameAr = d.nameAr, icon = d.icon, color = d.color, isIncome = d.isIncome, sortOrder = d.sortOrder, isProtected = d.protected))
                    found.id
                } else catDao.insert(d.toEntity())
            }
            // 2) عالج القديمة: دمج أو إبقاء أو حذف.
            for (old in existing) {
                val key = old.catKey ?: continue
                if (key in DefaultCategories.keys) continue
                val target = DefaultCategories.legacyMap[key]
                if (target != null) {
                    val to = newIds.getValue(target)
                    txDao.moveCategory(old.id, to)
                    ruleDao.moveCategory(old.id, to)
                    catDao.delete(old.id)
                } else {
                    val used = txDao.countByCategory(old.id) > 0 || ruleDao.getAll().any { it.categoryId == old.id && it.createdBy == RuleSource.USER }
                    if (used) catDao.update(old.copy(catKey = null, isProtected = false, sortOrder = 100 + old.sortOrder))
                    else {
                        ruleDao.moveCategory(old.id, newIds.getValue(DefaultCategories.UNCATEGORIZED_KEY))
                        catDao.delete(old.id)
                    }
                }
            }
            // 3) قواعد القاموس القديم: تبقى كقواعد مستخدم إن كان تصنيفها ما زال موجوداً وليس "غير مصنّف".
            val uncategorized = newIds.getValue(DefaultCategories.UNCATEGORIZED_KEY)
            val remaining = catDao.getAll().map { it.id }.toSet()
            for (r in ruleDao.getAll().filter { it.createdBy == RuleSource.SEED }) {
                if (r.categoryId in remaining && r.categoryId != uncategorized) {
                    ruleDao.update(r.copy(createdBy = RuleSource.USER, priority = RuleMatcher.PRIORITY_SEED_NAMED))
                } else ruleDao.delete(r.id)
            }
            keyIdCache = emptyMap()
            seedRules()
        }
        reapplyRules()
    }

    /** يعيد تحليل "غير المفهومة" تلقائياً عندما يتغير إصدار المحلّل (بعد تحديث التطبيق). */
    suspend fun retryUnparsedIfParserChanged(): Int {
        if (settings.parserVersionNow() == SmsPatterns.VERSION) return 0
        val n = retryUnparsed()
        settings.setParserVersion(SmsPatterns.VERSION)
        return n
    }

    suspend fun categoryId(key: String): Long {
        keyIdCache[key]?.let { return it }
        val id = catDao.getByKey(key)?.id ?: run { ensureSeeded(); catDao.getByKey(key)?.id }
            ?: error("missing category $key")
        keyIdCache = keyIdCache + (key to id)
        return id
    }

    suspend fun uncategorizedId(): Long = categoryId(DefaultCategories.UNCATEGORIZED_KEY)

    // ---------- الاستقبال ----------

    suspend fun isKnownSender(sender: String?): Boolean {
        val s = sender?.trim().orEmpty()
        if (s.isEmpty()) return false
        return settings.sendersNow().any { s.contains(it, ignoreCase = true) }
    }

    /** يُعالج رسالة واحدة. لا يُنشئ تكراراً أبداً ولا يستورد ما قبل الفترة المختارة. */
    suspend fun ingest(sender: String?, body: String, timestampMillis: Long): IngestOutcome {
        if (!isKnownSender(sender)) return IngestOutcome.NOT_SENDER
        ensureSeeded()
        val normalized = SmsParser.normalize(body)
        val hash = SmsHash.compute(normalized, timestampMillis)
        if (txDao.countByHash(hash) > 0 || unparsedDao.countByHash(hash) > 0) return IngestOutcome.DUPLICATE
        val start = settings.importStartNow()

        return when (val r = SmsParser.parse(normalized, timestampMillis, zone)) {
            ParseResult.Ignored -> IngestOutcome.IGNORED
            is ParseResult.Unrecognized -> {
                if (timestampMillis < start) return IngestOutcome.TOO_OLD
                val id = unparsedDao.insert(
                    UnparsedSmsEntity(sender = sender!!.trim(), body = normalized, receivedAt = timestampMillis, smsHash = hash, reason = r.reason)
                )
                if (id == -1L) IngestOutcome.DUPLICATE else IngestOutcome.UNPARSED
            }
            is ParseResult.Parsed -> {
                val p = r.sms
                if (p.dateTimeMillis < start) return IngestOutcome.TOO_OLD
                if (txDao.countSimilar(normalized, p.dateTimeMillis - SIMILAR_WINDOW_MS, p.dateTimeMillis + SIMILAR_WINDOW_MS) > 0) {
                    return IngestOutcome.DUPLICATE
                }
                val id = txDao.insert(entityFor(p, normalized, hash))
                if (id == -1L) IngestOutcome.DUPLICATE else IngestOutcome.INSERTED
            }
        }
    }

    private suspend fun entityFor(p: ParsedSms, normalized: String, hash: String): TransactionEntity {
        val (clean, categoryId) = categorize(p.merchantRaw, p.direction, p.channel)
        return TransactionEntity(
            dateTime = p.dateTimeMillis, amountHalalas = p.amountHalalas, direction = p.direction, rawSms = normalized,
            merchantRaw = p.merchantRaw, merchantClean = clean, categoryId = categoryId, channel = p.channel,
            country = p.country, cardLast4 = p.cardLast4, smsHash = hash, isManual = false, categoryOverridden = false,
            createdAt = System.currentTimeMillis(),
        )
    }

    suspend fun notifyPendingChanged() {
        onPendingChanged(txDao.countPendingMerchants(uncategorizedId(), settings.importStartNow()))
    }

    // ---------- التصنيف ----------

    private fun MerchantRuleEntity.toRule() = Rule(id, pattern, matchType, categoryId, canonicalName, createdBy, priority)

    /** التصنيف التلقائي من القناة (لا يحتاج قاعدة): سحب صراف، راتب، تحويل وارد، استرداد. */
    private suspend fun channelCategory(direction: Direction, channel: Channel): Long? = when {
        channel == Channel.ATM -> categoryId(DefaultCategories.CASH_KEY)
        channel == Channel.SALARY -> categoryId(DefaultCategories.SALARY_KEY)
        channel == Channel.REFUND -> categoryId(DefaultCategories.REFUND_KEY)
        direction == Direction.INCOME -> categoryId(DefaultCategories.TRANSFERS_IN_KEY)
        else -> null
    }

    /** (الاسم النظيف للعرض، معرّف التصنيف) لتاجر خام. */
    suspend fun categorize(merchantRaw: String, direction: Direction, channel: Channel): Pair<String, Long> {
        val key = MerchantCleaner.key(merchantRaw)
        val rules = ruleDao.getAll().map { it.toRule() }
        val match = RuleMatcher.match(key, rules)
        val clean = match?.canonicalName?.takeIf { it.isNotBlank() } ?: MerchantCleaner.clean(merchantRaw)
        val auto = channelCategory(direction, channel)
        return clean to (auto ?: match?.categoryId ?: uncategorizedId())
    }

    // ---------- الاستعلامات المقيّدة بالفترة ----------

    fun observeTransactions(): Flow<List<TransactionEntity>> =
        settings.importStart.flatMapLatest { start -> txDao.observeSince(start) }

    fun observePending(): Flow<List<PendingMerchant>> = flow {
        val id = uncategorizedId()
        emitAll(settings.importStart.flatMapLatest { start -> txDao.observePending(id, start) })
    }

    fun observeMerchantTransactions(merchant: String): Flow<List<TransactionEntity>> =
        settings.importStart.flatMapLatest { start -> txDao.observeByMerchant(merchant, start) }

    /** الاشتراكات: عمليات فئة "اشتراكات دورية" مجمّعة حسب الجهة مع الدورية والموعد القادم. */
    fun observeSubscriptions(): Flow<List<Subscription>> = flow {
        val id = categoryId(DefaultCategories.SUBSCRIPTIONS_KEY)
        emitAll(
            settings.importStart.flatMapLatest { start -> txDao.observeByCategory(id, start) }.map { list ->
                SubscriptionStats.build(
                    list.filter { it.direction == Direction.EXPENSE }.map { it.merchantClean to SubscriptionPayment(it.id, it.dateTime, it.amountHalalas) },
                    zone,
                )
            }
        )
    }

    fun observeCards(): Flow<List<String>> = txDao.observeCards()
    suspend fun getTransaction(id: Long): TransactionEntity? = txDao.getById(id)

    // ---------- الفترة ----------

    /** يحفظ الفترة الجديدة ويُرجع true إن كانت أوسع من السابقة (فيلزم إعادة مسح لاستيراد الأقدم). */
    suspend fun setPeriod(months: Int): Boolean {
        val old = settings.importStartNow()
        val start = ImportPeriod.startMillis(months, LocalDate.now(zone), zone)
        settings.setPeriod(months, start)
        notifyPendingChanged()
        return start < old
    }

    // ---------- قائمة الانتظار وتغيير التصنيف ----------

    suspend fun applyCategoryToMerchant(merchantClean: String, categoryId: Long) {
        val pattern = MerchantCleaner.normalizeForMatch(merchantClean)
        val existing = ruleDao.find(pattern, MatchType.EXACT, RuleSource.USER)
        if (existing != null) {
            ruleDao.update(existing.copy(categoryId = categoryId))
        } else {
            ruleDao.insert(
                MerchantRuleEntity(pattern = pattern, matchType = MatchType.EXACT, categoryId = categoryId, canonicalName = null, createdBy = RuleSource.USER, priority = RuleMatcher.PRIORITY_USER, createdAt = System.currentTimeMillis())
            )
        }
        txDao.setCategoryForMerchant(merchantClean, categoryId)
        notifyPendingChanged()
    }

    suspend fun overrideTransactionCategory(txId: Long, categoryId: Long) {
        txDao.setCategory(txId, categoryId, overridden = true)
        notifyPendingChanged()
    }

    suspend fun setCategoryForMerchantAlways(tx: TransactionEntity, categoryId: Long) {
        val key = MerchantCleaner.key(tx.merchantRaw)
        val rules = ruleDao.getAll()
        val matched = RuleMatcher.match(key, rules.map { it.toRule() })?.let { m -> rules.first { it.id == m.id } }
        when {
            matched == null -> ruleDao.insert(
                MerchantRuleEntity(pattern = key, matchType = MatchType.EXACT, categoryId = categoryId, canonicalName = null, createdBy = RuleSource.USER, priority = RuleMatcher.PRIORITY_USER, createdAt = System.currentTimeMillis())
            )
            matched.createdBy == RuleSource.USER -> ruleDao.update(matched.copy(categoryId = categoryId))
            matched.canonicalName != null -> ruleDao.setCategoryForCanonical(matched.canonicalName, categoryId, RuleSource.SEED)
            else -> ruleDao.update(matched.copy(categoryId = categoryId))
        }
        txDao.setCategory(tx.id, categoryId, overridden = false)
        txDao.setCategoryForMerchant(tx.merchantClean, categoryId)
        notifyPendingChanged()
    }

    /** يعيد تطبيق كل القواعد على العمليات غير المعدّلة يدوياً (بعد تعديل/حذف/استيراد القواعد). */
    suspend fun reapplyRules() {
        val rules = ruleDao.getAll().map { it.toRule() }
        val uncategorized = uncategorizedId()
        for (tx in txDao.getRuleDriven()) {
            val raw = tx.merchantRaw.ifBlank { tx.merchantClean }
            val match = RuleMatcher.match(MerchantCleaner.key(raw), rules)
            val clean = match?.canonicalName?.takeIf { it.isNotBlank() } ?: MerchantCleaner.clean(raw)
            val cat = channelCategory(tx.direction, tx.channel) ?: match?.categoryId ?: uncategorized
            if (cat != tx.categoryId || clean != tx.merchantClean) {
                txDao.update(tx.copy(categoryId = cat, merchantClean = clean))
            }
        }
        notifyPendingChanged()
    }

    // ---------- العمليات ----------

    suspend fun addManualTransaction(
        dateTime: Long, amountHalalas: Long, direction: Direction, merchant: String, categoryId: Long, channel: Channel, cardLast4: String?, country: String?,
    ): Long {
        ensureSeeded()
        return txDao.insert(
            TransactionEntity(
                dateTime = dateTime, amountHalalas = amountHalalas, direction = direction, rawSms = "",
                merchantRaw = merchant, merchantClean = MerchantCleaner.clean(merchant), categoryId = categoryId, channel = channel,
                country = country?.takeIf { it.isNotBlank() }, cardLast4 = cardLast4?.takeIf { it.isNotBlank() },
                smsHash = "manual:" + UUID.randomUUID(), isManual = true, categoryOverridden = true, createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateTransaction(updated: TransactionEntity) {
        val before = txDao.getById(updated.id)
        val overridden = updated.categoryOverridden || (before != null && before.categoryId != updated.categoryId)
        txDao.update(updated.copy(categoryOverridden = overridden))
        notifyPendingChanged()
    }

    suspend fun deleteTransaction(id: Long) {
        txDao.delete(id)
        notifyPendingChanged()
    }

    // ---------- القواعد ----------

    fun observeRules(): Flow<List<MerchantRuleEntity>> = ruleDao.observeAll()

    suspend fun saveRule(rule: MerchantRuleEntity) {
        val normalized = rule.copy(pattern = MerchantCleaner.normalizeForMatch(rule.pattern))
        if (normalized.pattern.isBlank()) return
        if (normalized.id == 0L) ruleDao.insert(normalized) else ruleDao.update(normalized)
        reapplyRules()
    }

    suspend fun deleteRule(id: Long) {
        ruleDao.delete(id)
        reapplyRules()
    }

    suspend fun exportRulesJson(): String {
        val cats = catDao.getAll().associateBy { it.id }
        val rules = ruleDao.getAll().map { r ->
            RuleJson(r.id, r.pattern, r.matchType.name, cats[r.categoryId]?.nameAr ?: "", r.canonicalName, r.createdBy.name.lowercase(), r.priority, r.createdAt)
        }
        return BackupJson.encodeRules(RulesExport(exportedAt = System.currentTimeMillis(), rules = rules))
    }

    suspend fun importRulesJson(text: String, replaceExisting: Boolean): Int {
        val export = BackupJson.decodeRules(text)
        val byName = catDao.getAll().associateBy { it.nameAr.trim() }
        val uncategorized = uncategorizedId()
        if (replaceExisting) ruleDao.deleteAll()
        val now = System.currentTimeMillis()
        val entities = export.rules.mapNotNull { r ->
            val pattern = MerchantCleaner.normalizeForMatch(r.pattern)
            if (pattern.isBlank()) return@mapNotNull null
            MerchantRuleEntity(
                pattern = pattern,
                matchType = runCatching { MatchType.valueOf(r.matchType.uppercase()) }.getOrDefault(MatchType.CONTAINS),
                categoryId = byName[r.categoryName.trim()]?.id ?: uncategorized,
                canonicalName = r.canonicalName,
                createdBy = if (r.createdBy.equals("seed", true)) RuleSource.SEED else RuleSource.USER,
                priority = r.priority,
                createdAt = if (r.createdAt > 0) r.createdAt else now,
            )
        }
        ruleDao.insertAll(entities)
        reapplyRules()
        return entities.size
    }

    // ---------- التصنيفات ----------

    fun observeCategories(): Flow<List<CategoryEntity>> = catDao.observeAll()
    suspend fun getCategories(): List<CategoryEntity> = catDao.getAll()

    suspend fun saveCategory(c: CategoryEntity): Long {
        return if (c.id == 0L) {
            val order = (catDao.maxSortOrder() ?: 0) + 1
            catDao.insert(c.copy(sortOrder = order))
        } else {
            catDao.update(c); c.id
        }
    }

    suspend fun deleteCategory(id: Long, moveTo: Long): Boolean {
        val c = catDao.getById(id) ?: return false
        if (c.isProtected || id == moveTo) return false
        txDao.moveCategory(id, moveTo)
        ruleDao.moveCategory(id, moveTo)
        catDao.delete(id)
        keyIdCache = emptyMap()
        notifyPendingChanged()
        return true
    }

    suspend fun categoryUsage(id: Long): Pair<Int, Int> = txDao.countByCategory(id) to ruleDao.countByCategory(id)

    // ---------- غير المفهومة ----------

    fun observeUnparsed(): Flow<List<UnparsedSmsEntity>> = unparsedDao.observeAll()
    fun observeUnparsedCount(): Flow<Int> = unparsedDao.observeCount()
    suspend fun deleteUnparsed(id: Long) = unparsedDao.delete(id)

    /** يعيد محاولة تحليل غير المفهومة (بعد تحديث المحلّل). يُرجع عدد ما صار عملية. */
    suspend fun retryUnparsed(): Int {
        var ok = 0
        val start = settings.importStartNow()
        for (u in unparsedDao.getAll()) {
            when (val r = SmsParser.parse(u.body, u.receivedAt, zone)) {
                is ParseResult.Parsed -> {
                    unparsedDao.delete(u.id)
                    val p = r.sms
                    if (p.dateTimeMillis < start) continue
                    if (txDao.countSimilar(u.body, p.dateTimeMillis - SIMILAR_WINDOW_MS, p.dateTimeMillis + SIMILAR_WINDOW_MS) > 0) continue
                    if (txDao.insert(entityFor(p, u.body, u.smsHash)) != -1L) ok++
                }
                ParseResult.Ignored -> unparsedDao.delete(u.id)
                is ParseResult.Unrecognized -> Unit
            }
        }
        notifyPendingChanged()
        return ok
    }

    // ---------- النسخ الاحتياطي ----------

    suspend fun exportBackupJson(): String {
        val cats = catDao.getAll()
        val catById = cats.associateBy { it.id }
        val backup = BackupFile(
            exportedAt = System.currentTimeMillis(),
            senders = settings.sendersNow(),
            categories = cats.map { CategoryJson(it.id, it.catKey, it.nameAr, it.icon, it.color, it.isIncome, it.sortOrder, it.isProtected) },
            rules = ruleDao.getAll().map { r -> RuleJson(r.id, r.pattern, r.matchType.name, catById[r.categoryId]?.nameAr ?: "", r.canonicalName, r.createdBy.name.lowercase(), r.priority, r.createdAt) },
            transactions = txDao.getAll().map { t ->
                TransactionJson(t.id, t.dateTime, t.amountHalalas, t.direction.name, t.rawSms, t.merchantRaw, t.merchantClean, t.categoryId, t.channel.name, t.country, t.cardLast4, t.smsHash, t.isManual, t.categoryOverridden, t.createdAt)
            },
            unparsed = unparsedDao.getAll().map { UnparsedJson(it.id, it.sender, it.body, it.receivedAt, it.smsHash, it.reason) },
        )
        return BackupJson.encodeBackup(backup)
    }

    /** استبدال كامل للبيانات من ملف نسخة احتياطية. */
    suspend fun importBackupJson(text: String) {
        val b = BackupJson.decodeBackup(text)
        db.clearAllTables()
        keyIdCache = emptyMap()
        catDao.insertAll(b.categories.map { CategoryEntity(it.id, it.key, it.nameAr, it.icon, it.color, it.isIncome, it.sortOrder, it.protected) })
        for (d in DefaultCategories.all.filter { it.protected }) {
            if (catDao.getByKey(d.key) == null) catDao.insert(d.toEntity())
        }
        val cats = catDao.getAll()
        val byName = cats.associateBy { it.nameAr.trim() }
        val uncategorized = uncategorizedId()
        ruleDao.insertAll(
            b.rules.map { r ->
                MerchantRuleEntity(
                    id = r.id, pattern = r.pattern,
                    matchType = runCatching { MatchType.valueOf(r.matchType.uppercase()) }.getOrDefault(MatchType.CONTAINS),
                    categoryId = byName[r.categoryName.trim()]?.id ?: uncategorized,
                    canonicalName = r.canonicalName,
                    createdBy = if (r.createdBy.equals("seed", true)) RuleSource.SEED else RuleSource.USER,
                    priority = r.priority, createdAt = r.createdAt,
                )
            }
        )
        val validCat = cats.map { it.id }.toSet()
        txDao.insertAll(
            b.transactions.map { t ->
                TransactionEntity(
                    id = t.id, dateTime = t.dateTime, amountHalalas = t.amountHalalas,
                    direction = runCatching { Direction.valueOf(t.direction) }.getOrDefault(Direction.EXPENSE),
                    rawSms = t.rawSms, merchantRaw = t.merchantRaw, merchantClean = t.merchantClean,
                    categoryId = if (t.categoryId in validCat) t.categoryId else uncategorized,
                    channel = runCatching { Channel.valueOf(t.channel) }.getOrDefault(Channel.OTHER),
                    country = t.country, cardLast4 = t.cardLast4, smsHash = t.smsHash, isManual = t.isManual,
                    categoryOverridden = t.categoryOverridden, createdAt = t.createdAt,
                )
            }
        )
        unparsedDao.insertAll(b.unparsed.map { UnparsedSmsEntity(it.id, it.sender, it.body, it.receivedAt, it.smsHash, it.reason) })
        if (b.senders.isNotEmpty()) settings.setSenders(b.senders)
        // ملف من إصدار قديم: طبّق الترحيل على ما استُورد.
        migrateLegacyIfNeeded(force = true)
        notifyPendingChanged()
    }

    /** مسح كل البيانات ثم إعادة الزرع الافتراضي. الإعداد يعود للترحيب. */
    suspend fun clearAllData() {
        db.clearAllTables()
        keyIdCache = emptyMap()
        settings.clearAll()
        ensureSeeded()
        notifyPendingChanged()
    }

    companion object {
        /** نافذة اعتبار رسالتين بنفس النص نفس العملية (وقت الوصول قد يختلف بين الاستلام الحي وإعادة المسح). */
        const val SIMILAR_WINDOW_MS = 10 * 60 * 1000L
    }
}
