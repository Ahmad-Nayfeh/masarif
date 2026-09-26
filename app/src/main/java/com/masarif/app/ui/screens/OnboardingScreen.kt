package com.masarif.app.ui.screens

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masarif.app.MasarifApp
import com.masarif.app.data.prefs.SettingsStore
import com.masarif.app.ui.Permissions
import com.masarif.app.ui.components.RestrictedSettingsHelp
import com.masarif.app.ui.components.SectionCard
import com.masarif.app.ui.findActivity
import com.masarif.core.ImportPeriod
import kotlinx.coroutines.launch

/**
 * الإعداد الأول (ويعود إليه التطبيق بعد تحديث يغيّر خطواته):
 * 1) الصلاحيات → 2) المرسلون مع التحقق من وجود رسائلهم → 3) الفترة → 4) الاستيراد.
 * كل تقدّم يُحفظ في الإعدادات فوراً، والاستيراد يعمل على نطاق التطبيق، فإعادة إنشاء الشاشة
 * (تدوير، مكالمة، ضغط ذاكرة) لا تضيع شيئاً.
 */
@Composable
fun OnboardingScreen(app: MasarifApp, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(Permissions.hasSms(context)) }
    var denied by remember { mutableStateOf(false) }
    var restricted by remember { mutableStateOf(false) }

    val savedSenders by app.settings.senders.collectAsState(initial = null)
    val savedPeriod by app.settings.periodMonths.collectAsState(initial = null)
    val step by app.settings.onboardingStep.collectAsState(initial = null)
    val scan by app.initialScan.collectAsState()

    // عدد الرسائل لكل مرسل تحقق منه في هذه الجلسة (للعرض فقط؛ القائمة نفسها محفوظة).
    val counts = remember { mutableStateMapOf<String, Int>() }
    var input by rememberSaveable { mutableStateOf(SettingsStore.DEFAULT_SENDER) }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        Log.i("Onboarding", "composed")
        onDispose { Log.i("Onboarding", "disposed") }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        val ok = Permissions.sms.all { map[it] == true }
        granted = ok
        denied = !ok
        restricted = !ok && (context.findActivity()?.let { Permissions.looksRestricted(it) } ?: false)
        if (ok && Build.VERSION.SDK_INT >= 33 && !Permissions.hasNotifications(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun verifySender() {
        val s = input.trim()
        val current = savedSenders ?: emptyList()
        if (s.isEmpty()) return
        if (current.any { it.equals(s, true) }) { checkError = "هذا المرسل مضاف بالفعل."; return }
        checking = true
        checkError = null
        scope.launch {
            val n = runCatching { app.scanner.countMessagesFrom(s) }.getOrElse { Log.e("Onboarding", "sender check failed", it); 0 }
            Log.i("Onboarding", "sender check '$s' -> $n messages")
            if (n == 0) {
                checkError = "لم أجد أي رسالة من «$s» في صندوق الوارد. تأكد من الاسم كما يظهر في تطبيق الرسائل ثم حاول مجدداً."
            } else {
                counts[s] = n
                app.settings.setSenders(current + s) // يُحفظ فوراً
                input = ""
            }
            checking = false
        }
    }

    if (savedSenders == null || step == null) return

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("مصاريف", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "يقرأ رسائل بنكك على جهازك فقط، يحلّلها ويصنّفها تلقائياً. لا إنترنت، لا حسابات، لا سيرفر.",
            style = MaterialTheme.typography.bodyLarge,
        )
        SectionCard(title = "ملاحظة مهمة") {
            Text(
                "التطبيق يفهم حالياً صيغ رسائل بنك الإنماء (alinma) فقط. البنوك الأخرى غير مدعومة بعد؛ رسائلها ستُحفظ في «رسائل غير مفهومة» دون احتساب.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // ---------- 1) الصلاحيات ----------
        StepHeader(1, "صلاحية الرسائل", done = granted)
        if (!granted) {
            SectionCard {
                Text("• استقبال الرسائل: لالتقاط كل عملية لحظة وصول رسالة البنك، حتى والتطبيق مغلق.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text("• قراءة الرسائل: لاستيراد رسائل الفترة التي تختارها، ولإعادة المسح إن فاتت رسالة.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { smsLauncher.launch(Permissions.sms) }, modifier = Modifier.fillMaxWidth()) { Text("منح الصلاحيات") }
                if (denied) Text("لم تُمنح الصلاحيات. بدونها لا يستطيع التطبيق العمل.", color = MaterialTheme.colorScheme.error)
            }
            if (restricted) RestrictedSettingsHelp()
            return@Column
        }

        // ---------- 2) المرسلون ----------
        val senders = savedSenders!!
        val sendersConfirmed = (step ?: 0) >= 1
        StepHeader(2, "من أي مرسل تصلك رسائل البنك؟", done = sendersConfirmed)
        if (!sendersConfirmed) {
            SectionCard {
                Text(
                    "اكتب اسم المرسل كما يظهر في تطبيق الرسائل (مثل alinma). سيبحث التطبيق في صندوق الوارد ولن يتابع حتى يجد رسائل منه. يمكنك إضافة أكثر من بنك.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                senders.forEach { s ->
                    Row(Modifier.fillMaxWidth().testTag("sender_$s"), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(counts[s]?.let { "$s — $it رسالة" } ?: s, Modifier.weight(1f))
                        IconButton(onClick = { scope.launch { app.settings.setSenders(senders - s) } }) { Icon(Icons.Filled.Delete, contentDescription = "حذف") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it; checkError = null }, singleLine = true,
                        label = { Text("اسم المرسل") }, modifier = Modifier.weight(1f), enabled = !checking,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { verifySender() }, enabled = !checking && input.isNotBlank(), modifier = Modifier.testTag("btn_verify")) { Text(if (checking) "…" else "تحقق") }
                }
                checkError?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { scope.launch { app.settings.setOnboardingStep(1) } },
                    enabled = senders.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("btn_continue"),
                ) { Text("متابعة") }
            }
            return@Column
        }

        // ---------- 3) الفترة ----------
        val running = scan is MasarifApp.ScanState.Running
        val done = scan as? MasarifApp.ScanState.Done
        StepHeader(3, "إلى أي مدة ماضية تريد تسجيل مصروفاتك؟", done = running || done != null)
        if (!running && done == null) {
            SectionCard {
                Text("بالأشهر الميلادية الكاملة قبل الشهر الحالي، والشهر الحالي مشمول دائماً. يمكن تغييرها لاحقاً من الإعدادات.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                val chosen = period ?: savedPeriod
                ImportPeriod.options.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { period = m }.testTag("period_$m"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen == m, onClick = { period = m })
                        Text(ImportPeriod.label(m), Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = chosen != null,
                    modifier = Modifier.fillMaxWidth().testTag("btn_import"),
                    onClick = { chosen?.let { app.startInitialScan(it) } },
                ) { Text("ابدأ الاستيراد") }
                TextButton(onClick = { scope.launch { app.settings.setOnboardingStep(0) } }) { Text("رجوع لتعديل المرسلين") }
            }
        }

        // ---------- 4) الاستيراد ----------
        if (running) {
            val r = scan as MasarifApp.ScanState.Running
            StepHeader(4, "جارٍ استيراد الرسائل…", done = false)
            SectionCard {
                if (r.total > 0) {
                    LinearProgressIndicator(progress = { r.done.toFloat() / r.total }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${r.done} / ${r.total} رسالة", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        } else if (done != null) {
            val r = done.result
            StepHeader(4, "اكتمل الاستيراد", done = true)
            SectionCard {
                Text("رسائل البنك: ${r.matched}\nعمليات جديدة: ${r.inserted}\nخارج الفترة: ${r.tooOld}\nغير مفهومة: ${r.unparsed}\nغير مالية (رموز تحقق وغيرها): ${r.ignored}")
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { scope.launch { app.settings.setSetupVersion(SettingsStore.CURRENT_SETUP_VERSION); onDone() } },
                    modifier = Modifier.fillMaxWidth().testTag("btn_start"),
                ) { Text("ابدأ") }
            }
        }
    }
}

@Composable
private fun StepHeader(n: Int, title: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$n.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (done) Icon(Icons.Filled.Check, contentDescription = "تم", tint = MaterialTheme.colorScheme.primary)
    }
}
