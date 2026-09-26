@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.masarif.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masarif.app.MasarifApp
import com.masarif.app.data.db.CategoryEntity
import com.masarif.app.data.db.TransactionEntity
import com.masarif.app.ui.Fmt
import com.masarif.app.ui.components.CategoryChip
import com.masarif.app.ui.components.CategoryPickerSheet
import com.masarif.app.ui.components.EmptyState
import com.masarif.app.ui.theme.ExpenseRed
import com.masarif.app.ui.theme.IncomeGreen
import com.masarif.core.Channel
import com.masarif.core.Direction
import com.masarif.core.MonthStats
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
fun TransactionsScreen(app: MasarifApp, snackbar: SnackbarHostState, onOpen: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val all by app.repository.observeTransactions().collectAsState(initial = emptyList())
    val categories by app.repository.observeCategories().collectAsState(initial = emptyList())
    val cards by app.repository.observeCards().collectAsState(initial = emptyList())
    val catById = remember(categories) { categories.associateBy { it.id } }

    var query by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf<String?>(null) }       // "yyyy-MM" أو null = الكل
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var channel by rememberSaveable { mutableStateOf<String?>(null) }
    var card by rememberSaveable { mutableStateOf<String?>(null) }

    val months = remember(all) {
        all.map { MonthStats.monthOf(it.dateTime, Fmt.zone) }.distinct().sortedDescending()
    }

    val filtered = remember(all, query, month, categoryId, channel, card, catById) {
        val q = query.trim().lowercase()
        all.filter { t ->
            (month == null || MonthStats.monthOf(t.dateTime, Fmt.zone).toString() == month) &&
                (categoryId == null || t.categoryId == categoryId) &&
                (channel == null || t.channel.name == channel) &&
                (card == null || t.cardLast4 == card) &&
                (q.isEmpty() || t.merchantClean.lowercase().contains(q) || t.merchantRaw.lowercase().contains(q) ||
                    t.rawSms.lowercase().contains(q) || Fmt.amount(t.amountHalalas).contains(q) ||
                    (catById[t.categoryId]?.nameAr?.contains(q) == true))
        }
    }

    var longPressed by remember { mutableStateOf<TransactionEntity?>(null) }
    var pickFor by remember { mutableStateOf<Pair<TransactionEntity, Boolean>?>(null) } // (العملية، لهذا التاجر دائماً؟)

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpen(-1L) }) { Icon(Icons.Filled.Add, contentDescription = "إضافة يدوية") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("بحث في التاجر أو المبلغ أو النص") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = month == null && categoryId == null && channel == null && card == null,
                    onClick = { month = null; categoryId = null; channel = null; card = null }, label = { Text("الكل") })
                CycleChip(label = month?.let { Fmt.month(YearMonth.parse(it)) } ?: "الشهر", selected = month != null,
                    options = months.map { it.toString() }, current = month, onSelect = { month = it })
                CycleChip(label = categoryId?.let { catById[it]?.nameAr } ?: "التصنيف", selected = categoryId != null,
                    options = categories.map { it.id }, current = categoryId, onSelect = { categoryId = it },
                    labelOf = { catById[it]?.nameAr ?: "" })
                CycleChip(label = channel?.let { Fmt.channel(Channel.valueOf(it)) } ?: "القناة", selected = channel != null,
                    options = Channel.entries.map { it.name }, current = channel, onSelect = { channel = it },
                    labelOf = { Fmt.channel(Channel.valueOf(it)) })
                if (cards.isNotEmpty()) {
                    CycleChip(label = card?.let { "بطاقة $it" } ?: "البطاقة", selected = card != null,
                        options = cards, current = card, onSelect = { card = it }, labelOf = { "بطاقة $it" })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${filtered.size} عملية — صرف ${Fmt.money(filtered.filter { it.direction == Direction.EXPENSE }.sumOf { it.amountHalalas })}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (filtered.isEmpty()) {
                EmptyState(if (all.isEmpty()) "لا عمليات بعد. ستظهر هنا تلقائياً عند وصول رسائل الإنماء." else "لا نتائج لهذا الفلتر.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { t ->
                        TransactionRow(
                            t = t,
                            category = catById[t.categoryId],
                            modifier = Modifier.combinedClickable(onClick = { onOpen(t.id) }, onLongClick = { longPressed = t }),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }

    longPressed?.let { t ->
        AlertDialog(
            onDismissRequest = { longPressed = null },
            title = { Text("تغيير التصنيف") },
            text = { Text("«${t.merchantClean}»\nهل تريد تغيير التصنيف لهذه العملية فقط، أم لهذا التاجر دائماً؟") },
            confirmButton = { TextButton(onClick = { pickFor = t to true; longPressed = null }) { Text("لهذا التاجر دائماً") } },
            dismissButton = { TextButton(onClick = { pickFor = t to false; longPressed = null }) { Text("لهذه العملية فقط") } },
        )
    }

    pickFor?.let { (t, always) ->
        CategoryPickerSheet(
            title = if (always) "تصنيف «${t.merchantClean}» دائماً" else "تصنيف هذه العملية",
            categories = categories,
            onPick = { c ->
                pickFor = null
                scope.launch {
                    if (always) app.repository.setCategoryForMerchantAlways(t, c.id)
                    else app.repository.overrideTransactionCategory(t.id, c.id)
                    snackbar.showSnackbar(if (always) "أُنشئت قاعدة: ${t.merchantClean} → ${c.nameAr}" else "تم تغيير التصنيف")
                }
            },
            onCreate = { c ->
                pickFor = null
                scope.launch {
                    val id = app.repository.saveCategory(c)
                    if (always) app.repository.setCategoryForMerchantAlways(t, id)
                    else app.repository.overrideTransactionCategory(t.id, id)
                    snackbar.showSnackbar("أُنشئت فئة «${c.nameAr}»")
                }
            },
            incomeOnly = t.direction == Direction.INCOME,
            onDismiss = { pickFor = null },
        )
    }
}

/** شريحة فلتر تدور بين الخيارات: لمسة تنتقل للخيار التالي، والعودة إلى "الكل" بعد الأخير. */
@Composable
private fun <T> CycleChip(
    label: String,
    selected: Boolean,
    options: List<T>,
    current: T?,
    onSelect: (T?) -> Unit,
    labelOf: (T) -> String = { it.toString() },
) {
    var open by remember { mutableStateOf(false) }
    FilterChip(selected = selected, onClick = { open = true }, label = { Text(label) })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("اختر") },
            text = {
                Column {
                    TextButton(onClick = { onSelect(null); open = false }) { Text("الكل") }
                    options.forEach { o ->
                        TextButton(onClick = { onSelect(o); open = false }) {
                            Text(labelOf(o), fontWeight = if (o == current) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("إغلاق") } },
        )
    }
}

@Composable
fun TransactionRow(t: TransactionEntity, category: CategoryEntity?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(category?.icon ?: "❔", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.merchantClean.ifBlank { t.merchantRaw }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CategoryChip(category)
            Text(
                "${Fmt.dateTime(t.dateTime)} · ${Fmt.channel(t.channel)}" + (t.cardLast4?.let { " · $it" } ?: "") + (if (t.isManual) " · يدوي" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            Fmt.signedMoney(t.amountHalalas, t.direction),
            color = if (t.direction == Direction.INCOME) IncomeGreen else ExpenseRed,
            fontWeight = FontWeight.Bold,
        )
    }
}
