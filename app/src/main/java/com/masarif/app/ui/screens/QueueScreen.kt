package com.masarif.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masarif.app.MasarifApp
import com.masarif.app.data.db.PendingMerchant
import com.masarif.app.ui.Fmt
import com.masarif.app.ui.components.CategoryPickerSheet
import com.masarif.app.ui.components.EmptyState
import com.masarif.app.ui.components.SectionCard
import com.masarif.core.Direction
import kotlinx.coroutines.launch

/**
 * قائمة الانتظار (ضمن الفترة المختارة): كل تاجر غير مصنّف في بطاقة واحدة مع عدد عملياته ومجموعها
 * وتاريخ ووقت آخر عملية. لمسة على البطاقة تعرض عملياته، وزر "صنّف" يفتح اختيار الفئة.
 */
@Composable
fun QueueScreen(app: MasarifApp, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val pending by app.repository.observePending().collectAsState(initial = emptyList())
    val categories by app.repository.observeCategories().collectAsState(initial = emptyList())
    var picking by remember { mutableStateOf<PendingMerchant?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text("قائمة الانتظار", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        if (pending.isEmpty()) {
            EmptyState("لا تجار بانتظار التصنيف. كل شيء مصنّف ✓")
        } else {
            Text(
                "${pending.size} تاجر. اختر فئة واحدة لكل تاجر: تُطبَّق على عملياته السابقة والقادمة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pending, key = { it.merchant.lowercase() }) { m ->
                    PendingCard(
                        app = app, m = m, expanded = expanded == m.merchant,
                        onToggle = { expanded = if (expanded == m.merchant) null else m.merchant },
                        onPick = { picking = m },
                    )
                }
            }
        }
    }

    picking?.let { m ->
        CategoryPickerSheet(
            title = "تصنيف «${m.merchant}»",
            subtitle = "سيُطبَّق على ${m.count} عملية وعلى كل رسالة قادمة من هذا التاجر.",
            categories = categories,
            onPick = { c ->
                picking = null
                scope.launch {
                    app.repository.applyCategoryToMerchant(m.merchant, c.id)
                    snackbar.showSnackbar("${m.merchant} → ${c.nameAr}")
                }
            },
            onCreate = { c ->
                picking = null
                scope.launch {
                    val id = app.repository.saveCategory(c)
                    app.repository.applyCategoryToMerchant(m.merchant, id)
                    snackbar.showSnackbar("أُنشئت فئة «${c.nameAr}» وصُنّف ${m.merchant} فيها")
                }
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun PendingCard(app: MasarifApp, m: PendingMerchant, expanded: Boolean, onToggle: () -> Unit, onPick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onToggle)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.merchant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${Fmt.count(m.count, "عملية واحدة", "عمليتان", "عمليات")} · آخرها ${Fmt.dateTime(m.lastAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Fmt.money(m.total), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPick, modifier = Modifier.weight(1f)) { Text("صنّف") }
            TextButton(onClick = onToggle) { Text(if (expanded) "إخفاء العمليات" else "عرض العمليات") }
        }
        if (expanded) {
            val txs by app.repository.observeMerchantTransactions(m.merchant).collectAsState(initial = emptyList())
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            txs.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.dateTime(t.dateTime), style = MaterialTheme.typography.bodyMedium)
                        Text(Fmt.channel(t.channel) + (t.cardLast4?.let { " · بطاقة $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Fmt.signedMoney(t.amountHalalas, t.direction), fontWeight = FontWeight.SemiBold,
                        color = if (t.direction == Direction.INCOME) com.masarif.app.ui.theme.IncomeGreen else com.masarif.app.ui.theme.ExpenseRed)
                }
            }
        }
    }
}
