@file:OptIn(ExperimentalMaterial3Api::class)

package com.masarif.app.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masarif.app.MasarifApp
import com.masarif.app.data.ScanResult
import com.masarif.app.ui.Permissions
import com.masarif.app.ui.Routes
import com.masarif.app.ui.components.ConfirmDialog
import com.masarif.app.ui.components.RestrictedSettingsHelp
import com.masarif.core.ImportPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(
    app: MasarifApp,
    snackbar: SnackbarHostState,
    onNavigate: (String) -> Unit,
    onPermissionsLost: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val senders by app.settings.senders.collectAsState(initial = emptyList())
    val dark by app.settings.darkMode.collectAsState(initial = true)
    val unparsedCount by app.repository.observeUnparsedCount().collectAsState(initial = 0)
    val periodMonths by app.settings.periodMonths.collectAsState(initial = null)

    var scanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<ScanResult?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showRestricted by remember { mutableStateOf(false) }
    var choosingPeriod by remember { mutableStateOf(false) }
    val ignoringBattery = remember { Permissions.isIgnoringBatteryOptimizations(context) }

    fun rescan() {
        if (!Permissions.hasSms(context)) { onPermissionsLost(); return }
        scanning = true
        scope.launch {
            scanResult = runCatching { app.scanner.scan() }.getOrElse { ScanResult() }
            scanning = false
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val json = app.repository.exportBackupJson()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) } }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "تم حفظ النسخة الاحتياطية" else "فشل التصدير")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
            }.getOrNull()
            if (text == null) snackbar.showSnackbar("تعذّر قراءة الملف") else pendingImport = text
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("الإعدادات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

        SettingsHeader("الرسائل")
        ListItem(
            headlineContent = { Text("المرسلون") },
            supportingContent = { Text(senders.joinToString("، ") + " — يدعم التطبيق صيغ الإنماء فقط حالياً") },
            modifier = Modifier.clickable { onNavigate(Routes.SETTINGS_SENDERS) },
        )
        ListItem(
            headlineContent = { Text("الفترة المستوردة") },
            supportingContent = { Text((periodMonths?.let { ImportPeriod.label(it) } ?: "غير محددة") + " — كل الشاشات مقيّدة بها؛ توسيعها يستورد الأقدم، وتضييقها يخفيه دون حذف") },
            modifier = Modifier.clickable { choosingPeriod = true },
        )
        ListItem(
            headlineContent = { Text(if (scanning) "جارٍ المسح…" else "إعادة مسح الرسائل") },
            supportingContent = { Text("يستورد كل رسائل البنك ضمن الفترة. لا يُنشئ أي تكرار.") },
            modifier = Modifier.clickable(enabled = !scanning) { rescan() },
        )
        ListItem(
            headlineContent = { Text("رسائل غير مفهومة") },
            supportingContent = { Text("رسائل من البنك فيها مبلغ لكن صيغتها مجهولة") },
            trailingContent = { Text(unparsedCount.toString(), fontWeight = FontWeight.Bold) },
            modifier = Modifier.clickable { onNavigate(Routes.SETTINGS_UNPARSED) },
        )
        HorizontalDivider()

        SettingsHeader("التصنيف")
        ListItem(
            headlineContent = { Text("التصنيفات") },
            supportingContent = { Text("إضافة، إعادة تسمية، دمج، حذف") },
            modifier = Modifier.clickable { onNavigate(Routes.SETTINGS_CATEGORIES) },
        )
        ListItem(
            headlineContent = { Text("قواعد التجار") },
            supportingContent = { Text("بحث، تعديل، حذف، إضافة بكلمة مفتاحية، تصدير/استيراد") },
            modifier = Modifier.clickable { onNavigate(Routes.SETTINGS_RULES) },
        )
        HorizontalDivider()

        SettingsHeader("المظهر")
        ListItem(
            headlineContent = { Text("الوضع الداكن") },
            trailingContent = { Switch(checked = dark, onCheckedChange = { v -> scope.launch { app.settings.setDarkMode(v) } }) },
        )
        HorizontalDivider()

        SettingsHeader("النسخة الاحتياطية")
        ListItem(
            headlineContent = { Text("تصدير نسخة احتياطية كاملة") },
            supportingContent = { Text("كل العمليات والقواعد والتصنيفات وغير المفهومة والمرسلين في ملف JSON تختاره") },
            modifier = Modifier.clickable { exportLauncher.launch("masarif-backup-${LocalDate.now()}.json") },
        )
        ListItem(
            headlineContent = { Text("استيراد نسخة احتياطية") },
            supportingContent = { Text("يستبدل كل البيانات الحالية بمحتوى الملف") },
            modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        )
        HorizontalDivider()

        SettingsHeader("النظام")
        ListItem(
            headlineContent = { Text("تحسين البطارية") },
            supportingContent = {
                Text(
                    if (ignoringBattery) "التطبيق مستثنى من تحسين البطارية ✓"
                    else "استثنِ التطبيق حتى لا يؤخر النظام استقبال الرسائل في وضع Doze. إن فاتت رسالة، إعادة المسح تلتقطها."
                )
            },
            modifier = Modifier.clickable { Permissions.openBatterySettings(context) },
        )
        ListItem(
            headlineContent = { Text("صلاحيات الرسائل") },
            supportingContent = { Text(if (Permissions.hasSms(context)) "ممنوحة ✓" else "غير ممنوحة — اضغط للشرح") },
            modifier = Modifier.clickable { showRestricted = !showRestricted },
        )
        if (showRestricted) {
            RestrictedSettingsHelp(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        HorizontalDivider()

        SettingsHeader("منطقة الخطر")
        ListItem(
            headlineContent = { Text("مسح كل البيانات", color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text("يحذف كل العمليات والقواعد والتصنيفات المخصصة ويعيد التطبيق لشاشة الإعداد الأولى") },
            modifier = Modifier.clickable { confirmClear = true },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "مصاريف ${versionName(context)} — يدعم رسائل بنك الإنماء حالياً. كل البيانات على هذا الجهاز فقط.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    if (choosingPeriod) {
        AlertDialog(
            onDismissRequest = { choosingPeriod = false },
            title = { Text("الفترة المستوردة") },
            text = {
                Column {
                    ImportPeriod.options.forEach { m ->
                        Row(Modifier.fillMaxWidth().clickable {
                            choosingPeriod = false
                            scope.launch {
                                val widened = app.repository.setPeriod(m)
                                if (widened) rescan() else snackbar.showSnackbar("الفترة الآن: ${ImportPeriod.label(m)}")
                            }
                        }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = periodMonths == m, onClick = null)
                            Text(ImportPeriod.label(m))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingPeriod = false }) { Text("إغلاق") } },
        )
    }

    scanResult?.let { r ->
        AlertDialog(
            onDismissRequest = { scanResult = null },
            title = { Text("نتيجة المسح") },
            text = {
                Text(
                    "رسائل الجهاز: ${r.scanned}\nرسائل البنك: ${r.matched}\nعمليات جديدة: ${r.inserted}\nمكررة (تم تجاهلها): ${r.duplicates}\nخارج الفترة: ${r.tooOld}\nغير مفهومة: ${r.unparsed}\nغير مالية: ${r.ignored}"
                )
            },
            confirmButton = { TextButton(onClick = { scanResult = null }) { Text("حسناً") } },
        )
    }

    pendingImport?.let { text ->
        ConfirmDialog(
            title = "استيراد النسخة الاحتياطية",
            text = "سيُستبدل كل ما في التطبيق الآن بمحتوى الملف. هل أنت متأكد؟",
            confirmText = "استبدال", destructive = true,
            onConfirm = {
                pendingImport = null
                scope.launch {
                    val ok = runCatching { app.repository.importBackupJson(text) }.isSuccess
                    snackbar.showSnackbar(if (ok) "تم الاستيراد" else "ملف غير صالح")
                }
            },
            onDismiss = { pendingImport = null },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "مسح كل البيانات",
            text = "لا يمكن التراجع. خذ نسخة احتياطية أولاً إن أردت.",
            confirmText = "مسح الكل", destructive = true,
            onConfirm = {
                confirmClear = false
                scope.launch { app.repository.clearAllData(); snackbar.showSnackbar("تم مسح كل البيانات") }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }.getOrDefault("")

@Composable
fun SettingsHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
