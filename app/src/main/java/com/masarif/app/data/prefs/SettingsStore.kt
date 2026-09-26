package com.masarif.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.masarif.core.ImportPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** إعدادات بسيطة: المرسلون، الفترة، الوضع الداكن، وإصدارا الإعداد والمحلّل. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val senders = stringPreferencesKey("senders")
        val darkMode = booleanPreferencesKey("dark_mode")
        val initialScanDone = booleanPreferencesKey("initial_scan_done")
        val periodMonths = intPreferencesKey("period_months")
        val importStart = longPreferencesKey("import_start_millis")
        val setupVersion = intPreferencesKey("setup_version")
        val parserVersion = intPreferencesKey("parser_version")
        val onboardingStep = intPreferencesKey("onboarding_step")
    }

    /** خطوة الترحيب المحفوظة (0 = المرسلون، 1 = الفترة) حتى لا تضيع عند إعادة إنشاء الشاشة. */
    val onboardingStep: Flow<Int> = context.dataStore.data.map { p -> p[Keys.onboardingStep] ?: 0 }

    suspend fun setOnboardingStep(step: Int) {
        context.dataStore.edit { it[Keys.onboardingStep] = step }
    }

    /** المرسلون كما أدخلهم المستخدم وتحقق التطبيق منهم في الترحيب. فارغة = لم يُكمل الإعداد. */
    val senders: Flow<List<String>> = context.dataStore.data.map { p -> decodeSenders(p[Keys.senders]) }
    val darkMode: Flow<Boolean> = context.dataStore.data.map { p -> p[Keys.darkMode] ?: true }
    val initialScanDone: Flow<Boolean> = context.dataStore.data.map { p -> p[Keys.initialScanDone] ?: false }

    /** عدد الأشهر الماضية المختارة، أو [ImportPeriod.ALL]. null = لم يُختر بعد. */
    val periodMonths: Flow<Int?> = context.dataStore.data.map { p -> p[Keys.periodMonths] }

    /** بداية الفترة بالمللي ثانية (0 = كل الوقت). كل الاستيراد والعرض مقيّد بها. */
    val importStart: Flow<Long> = context.dataStore.data.map { p -> p[Keys.importStart] ?: 0L }

    /** إصدار شاشة الإعداد الذي أكمله المستخدم؛ أقل من [CURRENT_SETUP_VERSION] = يعود للترحيب. */
    val setupVersion: Flow<Int> = context.dataStore.data.map { p -> p[Keys.setupVersion] ?: 0 }

    suspend fun sendersNow(): List<String> = senders.first()
    suspend fun importStartNow(): Long = importStart.first()
    suspend fun setupVersionNow(): Int = setupVersion.first()
    suspend fun parserVersionNow(): Int = context.dataStore.data.first()[Keys.parserVersion] ?: 0

    suspend fun setSenders(list: List<String>) {
        val cleaned = list.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        context.dataStore.edit { it[Keys.senders] = cleaned.joinToString("\n") }
    }

    suspend fun setPeriod(months: Int, startMillis: Long) {
        context.dataStore.edit {
            it[Keys.periodMonths] = months
            it[Keys.importStart] = startMillis
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.darkMode] = enabled }
    }

    suspend fun setInitialScanDone(done: Boolean) {
        context.dataStore.edit { it[Keys.initialScanDone] = done }
    }

    suspend fun setSetupVersion(v: Int) {
        context.dataStore.edit { it[Keys.setupVersion] = v }
    }

    suspend fun setParserVersion(v: Int) {
        context.dataStore.edit { it[Keys.parserVersion] = v }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private fun decodeSenders(raw: String?): List<String> =
        raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    companion object {
        /** الافتراضي المقترح في الترحيب؛ التطبيق يدعم صيغ الإنماء فقط حالياً. */
        const val DEFAULT_SENDER = "alinma"
        const val CURRENT_SETUP_VERSION = 2
    }
}
