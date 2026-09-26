package com.masarif.app

import android.app.Application
import android.util.Log
import com.masarif.app.data.MasarifRepository
import com.masarif.app.data.ScanResult
import com.masarif.app.data.db.AppDatabase
import com.masarif.app.data.prefs.SettingsStore
import com.masarif.app.sms.InboxScanner
import com.masarif.app.sms.PendingNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** حاوية بسيطة للاعتماديات (بلا مكتبة حقن). */
class MasarifApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val notifier: PendingNotifier by lazy { PendingNotifier(this) }

    val repository: MasarifRepository by lazy {
        MasarifRepository(
            db = database,
            settings = settings,
            seedJsonProvider = { assets.open("merchant_seed.json").bufferedReader().use { it.readText() } },
            onPendingChanged = { count -> notifier.update(count) },
        )
    }

    val scanner: InboxScanner by lazy { InboxScanner(this, repository) }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** حالة الاستيراد الأولي، على نطاق التطبيق حتى لا تضيع إن أُعيد إنشاء الشاشة أثناءه. */
    sealed class ScanState {
        data object Idle : ScanState()
        data class Running(val done: Int, val total: Int) : ScanState()
        data class Done(val result: ScanResult) : ScanState()
    }

    val initialScan = MutableStateFlow<ScanState>(ScanState.Idle)

    /** يبدأ الاستيراد الأولي مرة واحدة (آمن للاستدعاء المتكرر). */
    fun startInitialScan(periodMonths: Int) {
        if (initialScan.value is ScanState.Running) return
        initialScan.value = ScanState.Running(0, 0)
        appScope.launch {
            val result = runCatching {
                repository.setPeriod(periodMonths)
                scanner.scan { d, t -> initialScan.value = ScanState.Running(d, t) }
            }.getOrElse { Log.e("MasarifApp", "initial scan failed", it); ScanResult() }
            settings.setInitialScanDone(true)
            initialScan.value = ScanState.Done(result)
        }
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching {
                repository.ensureSeeded()
                // بعد تحديث التطبيق: ترحيل البيانات القديمة وإعادة تحليل غير المفهومة بالمحلّل الجديد.
                repository.migrateLegacyIfNeeded()
                val n = repository.retryUnparsedIfParserChanged()
                if (n > 0) Log.i("MasarifApp", "re-parsed $n previously unparsed messages")
            }.onFailure { Log.e("MasarifApp", "startup maintenance failed", it) }
        }
    }
}
