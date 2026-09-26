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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masarif.app.MasarifApp
import com.masarif.app.ui.Fmt
import com.masarif.app.ui.components.EmptyState
import com.masarif.app.ui.components.SectionCard
import com.masarif.app.ui.theme.ExpenseRed
import com.masarif.core.Subscription
import com.masarif.core.SubscriptionPeriod
import com.masarif.core.SubscriptionStats
import java.time.LocalDate

/** الاشتراكات: كل ما صُنّف "اشتراكات دورية" مجمّعاً حسب الجهة مع دفعاته ودوريته وموعده القادم. */
@Composable
fun SubscriptionsScreen(app: MasarifApp, onOpenTransaction: (Long) -> Unit) {
    val subs by app.repository.observeSubscriptions().collectAsState(initial = emptyList())
    val today = remember { LocalDate.now(Fmt.zone) }
    val monthlyTotal = subs.sumOf { it.monthlyEquivalent }
    var expanded by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text("الاشتراكات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        if (subs.isEmpty()) {
            EmptyState("لا اشتراكات بعد. صنّف أي عملية متكررة (تطبيق، باقة، عضوية) تحت «اشتراكات دورية» لتظهر هنا مع مواعيدها.")
        } else {
            Text(
                "${subs.size} اشتراك · تكلفة شهرية مكافئة: ${Fmt.money(monthlyTotal)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(subs, key = { it.merchant.lowercase() }) { s ->
                    SubscriptionCard(
                        s = s, today = today, expanded = expanded == s.merchant,
                        onToggle = { expanded = if (expanded == s.merchant) null else s.merchant },
                        onOpenTransaction = onOpenTransaction,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(s: Subscription, today: LocalDate, expanded: Boolean, onToggle: () -> Unit, onOpenTransaction: (Long) -> Unit) {
    val overdue = SubscriptionStats.isOverdue(s, today, Fmt.zone)
    SectionCard(modifier = Modifier.clickable(onClick = onToggle)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.merchant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    periodLabel(s.period) + " · ${s.payments.size} دفعة · آخر دفعة ${Fmt.dateTime(s.lastPaidAt)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.nextExpectedAt?.let { next ->
                    Text(
                        (if (overdue) "كان متوقعاً في " else "الموعد المتوقع القادم: ") + Fmt.date(next) + (if (overdue) " ولم تُرصد دفعة" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.money(s.lastAmount), fontWeight = FontWeight.Bold, color = ExpenseRed)
                if (s.period != SubscriptionPeriod.MONTHLY) {
                    Text("≈ ${Fmt.money(s.monthlyEquivalent)}/شهر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            s.payments.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenTransaction(p.txId) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Fmt.dateTime(p.dateTimeMillis), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(Fmt.money(p.amountHalalas), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun periodLabel(p: SubscriptionPeriod): String = when (p) {
    SubscriptionPeriod.WEEKLY -> "أسبوعي"
    SubscriptionPeriod.MONTHLY -> "شهري"
    SubscriptionPeriod.QUARTERLY -> "كل 3 أشهر"
    SubscriptionPeriod.YEARLY -> "سنوي"
    SubscriptionPeriod.IRREGULAR -> "غير منتظم"
    SubscriptionPeriod.UNKNOWN -> "دفعة واحدة حتى الآن"
}
