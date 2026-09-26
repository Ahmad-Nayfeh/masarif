package com.masarif.app

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.masarif.app.data.IngestOutcome
import com.masarif.app.data.MasarifRepository
import com.masarif.app.data.db.AppDatabase
import com.masarif.app.data.prefs.SettingsStore
import com.masarif.app.sms.InboxScanner
import com.masarif.core.Channel
import com.masarif.core.DefaultCategories
import com.masarif.core.Direction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** اختبارات على جهاز/محاكي: منع التكرار، التصنيف التلقائي، قائمة الانتظار، الفترة، وإعادة المسح مرتين. */
@RunWith(AndroidJUnit4::class)
class IngestionTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: MasarifRepository
    private val app get() = ApplicationProvider.getApplicationContext<MasarifApp>()

    private val posSample = "Purchase by mada Pay\nAmount:47 SAR\nMada card:4321*\nAt:ALDREES Station Company\nOn:26-09-21 15:58"
    private val skyline = "Online Purchase 19.23 SAR\nmada Card: 4321*\nAccount: *1000\nAt: SKYLINE\nIn: United Kingdom\nOn: 26-09-20 12:05"
    private val otp = "Please use the code: 6850\nTo: Alinma App"
    private val otpWithAmount = "Please use the code:6110\nFor card:*4321\nAmount:33.50 SAR\nMerchant:Hungerstation\nOn:26-09-25 22:26"
    private val unknown = "Salary credited\nAmount:12000 SAR\nAccount:*1000\nOn:26-09-27 09:00"
    private val salary = "Incoming salary transfer\nAmount: 9000 SAR\nAccount: **1000\nOn: 26-08-31 12:38"
    private val atm = "ATM withdrawal\nmada Card:4321*\nAmount:200 SAR\nIn:BANKX\nOn:2026-5-27 15:26"
    private val oldPurchase = "Purchase by mada Pay\nAmount:9 SAR\nMada card:4321*\nAt:OLD SHOP\nOn:24-01-05 10:00"

    @Before
    fun setUp() {
        db = AppDatabase.inMemory(app)
        settings = SettingsStore(app)
        runBlocking {
            settings.setSenders(listOf("alinma"))
            settings.setPeriod(-1, 0L)
        }
        repo = MasarifRepository(
            db = db,
            settings = settings,
            seedJsonProvider = { app.assets.open("merchant_seed.json").bufferedReader().use { it.readText() } },
        )
        runBlocking { repo.ensureSeeded() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun sameSmsTwiceIsStoredOnce() = runBlocking {
        assertEquals(IngestOutcome.INSERTED, repo.ingest("alinma", posSample, 1_000L))
        assertEquals(IngestOutcome.DUPLICATE, repo.ingest("alinma", posSample, 1_000L))
        assertEquals(1, db.transactionDao().count())
    }

    @Test
    fun liveThenRescanWithDifferentArrivalTimeIsStoredOnce() = runBlocking {
        assertEquals(IngestOutcome.INSERTED, repo.ingest("alinma", posSample, 1_000L))
        assertEquals(IngestOutcome.DUPLICATE, repo.ingest("ALINMA", posSample, 1_000L + 90_000L))
        assertEquals(1, db.transactionDao().count())
    }

    @Test
    fun senderFilterIsCaseInsensitiveAndRejectsOthers() = runBlocking {
        assertEquals(IngestOutcome.NOT_SENDER, repo.ingest("STC", posSample, 1L))
        assertEquals(IngestOutcome.NOT_SENDER, repo.ingest(null, posSample, 1L))
        assertEquals(IngestOutcome.INSERTED, repo.ingest("AlInma", posSample, 1L))
    }

    @Test
    fun otpIgnoredEvenWithAmountAndUnknownFormatGoesToUnparsed() = runBlocking {
        assertEquals(IngestOutcome.IGNORED, repo.ingest("alinma", otp, 1L))
        assertEquals(IngestOutcome.IGNORED, repo.ingest("alinma", otpWithAmount, 2L))
        assertEquals(IngestOutcome.UNPARSED, repo.ingest("alinma", unknown, 3L))
        assertEquals(IngestOutcome.DUPLICATE, repo.ingest("alinma", unknown, 3L))
        assertEquals(0, db.transactionDao().count())
        assertEquals(1, db.unparsedSmsDao().count())
    }

    @Test
    fun knownMerchantIsCategorizedBySeedAndUnknownGoesToQueue() = runBlocking {
        repo.ingest("alinma", posSample, 1L)
        repo.ingest("alinma", skyline, 2L)
        val fuel = db.categoryDao().getByKey("fuel_transport")!!
        val txs = db.transactionDao().getAll()
        assertEquals(fuel.id, txs.first { it.merchantRaw == "ALDREES Station Company" }.categoryId)
        assertEquals("الدريس", txs.first { it.merchantRaw == "ALDREES Station Company" }.merchantClean)
        assertEquals(repo.uncategorizedId(), txs.first { it.merchantRaw == "SKYLINE" }.categoryId)
        val pending = repo.observePending().first()
        assertEquals(1, pending.size)
        assertEquals("SKYLINE", pending.first().merchant)
    }

    @Test
    fun salaryAndAtmAreCategorizedAutomaticallyByChannel() = runBlocking {
        assertEquals(IngestOutcome.INSERTED, repo.ingest("alinma", salary, 1L))
        assertEquals(IngestOutcome.INSERTED, repo.ingest("alinma", atm, 2L))
        val txs = db.transactionDao().getAll()
        val s = txs.first { it.channel == Channel.SALARY }
        val a = txs.first { it.channel == Channel.ATM }
        assertEquals(Direction.INCOME, s.direction)
        assertEquals(db.categoryDao().getByKey(DefaultCategories.SALARY_KEY)!!.id, s.categoryId)
        assertEquals(db.categoryDao().getByKey(DefaultCategories.CASH_KEY)!!.id, a.categoryId)
        assertEquals(0, repo.observePending().first().size)
    }

    @Test
    fun messagesBeforeImportPeriodAreSkipped() = runBlocking {
        settings.setPeriod(3, 1_750_000_000_000L) // بداية الفترة = منتصف 2025؛ رسالة يناير 2024 أقدم منها
        assertEquals(IngestOutcome.TOO_OLD, repo.ingest("alinma", oldPurchase, 1_800_000_000_000L))
        assertEquals(IngestOutcome.INSERTED, repo.ingest("alinma", posSample, 1_800_000_000_000L))
        assertEquals(1, db.transactionDao().count())
    }

    @Test
    fun oneTapFromQueueCategorizesPastAndFutureTransactions() = runBlocking {
        repo.ingest("alinma", skyline, 1L)
        repo.ingest("alinma", skyline.replace("19.23", "5.00"), 2L)
        val pending = repo.observePending().first()
        assertEquals(1, pending.size)
        assertEquals(2, pending.first().count)

        val subscriptions = db.categoryDao().getByKey(DefaultCategories.SUBSCRIPTIONS_KEY)!!
        repo.applyCategoryToMerchant("SKYLINE", subscriptions.id)

        assertTrue(db.transactionDao().getAll().all { it.categoryId == subscriptions.id })
        assertEquals(0, repo.observePending().first().size)
        repo.ingest("alinma", skyline.replace("19.23", "7.10"), 3L)
        val newest = db.transactionDao().getAll().first { it.amountHalalas == 710L }
        assertEquals(subscriptions.id, newest.categoryId)
        assertEquals(0, repo.observePending().first().size)
        // وتظهر في شاشة الاشتراكات مجمّعة تحت جهة واحدة بثلاث دفعات
        val subs = repo.observeSubscriptions().first()
        assertEquals(1, subs.size)
        assertEquals(3, subs.first().payments.size)
    }

    @Test
    fun overrideOneTransactionIsNotTouchedByMerchantRule() = runBlocking {
        repo.ingest("alinma", skyline, 1L)
        repo.ingest("alinma", skyline.replace("19.23", "5.00"), 2L)
        val txs = db.transactionDao().getAll()
        val cafes = db.categoryDao().getByKey("cafes")!!
        val subs = db.categoryDao().getByKey(DefaultCategories.SUBSCRIPTIONS_KEY)!!
        repo.overrideTransactionCategory(txs[0].id, cafes.id)
        repo.applyCategoryToMerchant("SKYLINE", subs.id)
        val after = db.transactionDao().getAll().associateBy { it.id }
        assertEquals(cafes.id, after[txs[0].id]!!.categoryId)
        assertEquals(subs.id, after[txs[1].id]!!.categoryId)
    }

    @Test
    fun deletingRuleSendsMerchantBackToQueue() = runBlocking {
        repo.ingest("alinma", skyline, 1L)
        val subs = db.categoryDao().getByKey(DefaultCategories.SUBSCRIPTIONS_KEY)!!
        repo.applyCategoryToMerchant("SKYLINE", subs.id)
        val rule = db.merchantRuleDao().getAll().first { it.pattern == "skyline" }
        repo.deleteRule(rule.id)
        assertEquals(repo.uncategorizedId(), db.transactionDao().getAll().first().categoryId)
        assertEquals(1, repo.observePending().first().size)
    }

    @Test
    fun rescanTwiceCreatesNoDuplicates() = runBlocking {
        val scanner = InboxScanner(app, repo)
        val first = scanner.scan()
        val countAfterFirst = db.transactionDao().count()
        val unparsedAfterFirst = db.unparsedSmsDao().count()
        val second = scanner.scan()
        assertEquals(first.scanned, second.scanned)
        assertEquals(0, second.inserted)
        assertEquals(0, second.unparsed)
        assertEquals(countAfterFirst, db.transactionDao().count())
        assertEquals(unparsedAfterFirst, db.unparsedSmsDao().count())
        if (first.matched > 0) assertNotEquals(0, first.inserted + first.unparsed + first.ignored + first.duplicates + first.tooOld)
    }

    @Test
    fun senderVerificationCountsInboxMessages() = runBlocking {
        val scanner = InboxScanner(app, repo)
        assertEquals(0, scanner.countMessagesFrom("no-such-sender-xyz"))
        assertTrue(scanner.countMessagesFrom("alinma") >= 0)
    }

    @Test
    fun defaultCategoriesAreSeeded() = runBlocking {
        val cats = db.categoryDao().getAll()
        assertEquals(DefaultCategories.all.size, cats.size)
        assertTrue(cats.first { it.catKey == DefaultCategories.UNCATEGORIZED_KEY }.isProtected)
    }
}
