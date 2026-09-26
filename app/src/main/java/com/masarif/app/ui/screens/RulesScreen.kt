@file:OptIn(ExperimentalMaterial3Api::class)

package com.masarif.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masarif.app.MasarifApp
import com.masarif.app.data.db.CategoryEntity
import com.masarif.app.data.db.MerchantRuleEntity
import com.masarif.app.ui.Fmt
import com.masarif.app.ui.components.ConfirmDialog
import com.masarif.app.ui.components.EmptyState
import com.masarif.app.ui.components.Selector
import com.masarif.core.MatchType
import com.masarif.core.RuleMatcher
import com.masarif.core.RuleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** إدارة القواعد: بحث، تعديل، حذف، إضافة قاعدة بكلمة مفتاحية، تصدير/استيراد JSON. */
@Composable
fun RulesScreen(app: MasarifApp, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rules by app.repository.observeRules().collectAsState(initial = emptyList())
    val categories by app.repository.observeCategories().collectAsState(initial = emptyList())
    val catById = remember(categories) { categories.associateBy { it.id } }

    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MerchantRuleEntity?>(null) }
    var deleting by remember { mutableStateOf<MerchantRuleEntity?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val filtered = remember(rules, query, catById) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) rules else rules.filter {
            it.pattern.contains(q) || (it.canonicalName?.lowercase()?.contains(q) == true) || (catById[it.categoryId]?.nameAr?.contains(q) == true)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val json = app.repository.exportRulesJson()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) } }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "تم تصدير ${rules.size} قاعدة" else "فشل التصدير")
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("القواعد (${rules.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                actions = {
                    TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("استيراد") }
                    IconButton(onClick = { exportLauncher.launch("masarif-rules-${LocalDate.now()}.json") }) { Icon(Icons.Filled.Share, contentDescription = "تصدير") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = MerchantRuleEntity(pattern = "", matchType = MatchType.CONTAINS, categoryId = categories.firstOrNull()?.id ?: 0L, createdBy = RuleSource.USER, priority = RuleMatcher.PRIORITY_USER, createdAt = System.currentTimeMillis())
            }) { Icon(Icons.Filled.Add, contentDescription = "إضافة قاعدة") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("بحث في القواعد") }, leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (filtered.isEmpty()) {
                EmptyState("لا قواعد.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { r ->
                        val cat = catById[r.categoryId]
                        ListItem(
                            headlineContent = { Text(r.pattern, fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Text(
                                    "${Fmt.matchType(r.matchType)} → ${cat?.icon ?: ""} ${cat?.nameAr ?: "؟"}" +
                                        (r.canonicalName?.let { " · يُعرض كـ «$it»" } ?: "") +
                                        (if (r.createdBy == RuleSource.SEED) " · قاموس" else " · أنت"),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { deleting = r }) { Icon(Icons.Filled.Delete, contentDescription = "حذف") }
                            },
                            modifier = Modifier.clickable { editing = r },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }

    editing?.let { r ->
        RuleEditDialog(
            rule = r, categories = categories,
            onSave = { saved ->
                editing = null
                scope.launch { app.repository.saveRule(saved); snackbar.showSnackbar("حُفظت القاعدة وأُعيد تطبيق القواعد") }
            },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { r ->
        ConfirmDialog(
            title = "حذف القاعدة",
            text = "«${r.pattern}» — العمليات التي تعتمد عليها تعود إلى «غير مصنّف» وتظهر في قائمة الانتظار.",
            confirmText = "حذف", destructive = true,
            onConfirm = { deleting = null; scope.launch { app.repository.deleteRule(r.id) } },
            onDismiss = { deleting = null },
        )
    }

    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("استيراد القواعد") },
            text = { Text("هل تريد إضافة القواعد إلى الموجودة، أم استبدال كل القواعد الحالية؟") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        val n = runCatching { app.repository.importRulesJson(text, replaceExisting = false) }.getOrElse { -1 }
                        snackbar.showSnackbar(if (n >= 0) "أُضيفت $n قاعدة" else "ملف غير صالح")
                    }
                }) { Text("إضافة") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        val n = runCatching { app.repository.importRulesJson(text, replaceExisting = true) }.getOrElse { -1 }
                        snackbar.showSnackbar(if (n >= 0) "استُبدلت القواعد بـ $n قاعدة" else "ملف غير صالح")
                    }
                }) { Text("استبدال") }
            },
        )
    }
}

@Composable
private fun RuleEditDialog(rule: MerchantRuleEntity, categories: List<CategoryEntity>, onSave: (MerchantRuleEntity) -> Unit, onDismiss: () -> Unit) {
    var pattern by remember { mutableStateOf(rule.pattern) }
    var matchType by remember { mutableStateOf(rule.matchType) }
    var categoryId by remember { mutableStateOf(rule.categoryId) }
    var canonical by remember { mutableStateOf(rule.canonicalName.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule.id == 0L) "قاعدة جديدة" else "تعديل القاعدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = pattern, onValueChange = { pattern = it }, label = { Text("الكلمة المفتاحية") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Selector(label = "طريقة المطابقة", options = MatchType.entries, selected = matchType, labelOf = { Fmt.matchType(it) }, onSelect = { matchType = it })
                Selector(label = "التصنيف", options = categories, selected = categories.firstOrNull { it.id == categoryId }, labelOf = { "${it.icon} ${it.nameAr}" }, onSelect = { categoryId = it.id })
                OutlinedTextField(value = canonical, onValueChange = { canonical = it }, label = { Text("اسم العرض الموحّد (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("تُطابَق الكلمة مع اسم التاجر بعد التنظيف وبلا حساسية لحالة الأحرف.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                enabled = pattern.isNotBlank() && categoryId != 0L,
                onClick = { onSave(rule.copy(pattern = pattern.trim(), matchType = matchType, categoryId = categoryId, canonicalName = canonical.trim().ifBlank { null })) },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
