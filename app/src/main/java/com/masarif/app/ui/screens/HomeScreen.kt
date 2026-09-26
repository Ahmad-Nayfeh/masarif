package com.masarif.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.masarif.app.MasarifApp
import com.masarif.app.ui.Fmt
import com.masarif.app.ui.components.BarChart
import com.masarif.app.ui.components.DonutChart
import com.masarif.app.ui.components.SectionCard
import com.masarif.app.ui.components.Slice
import com.masarif.app.ui.theme.ExpenseRed
import com.masarif.app.ui.theme.IncomeGreen
import com.masarif.core.ChannelGroup
import com.masarif.core.MonthStats
import com.masarif.core.TxSummary
import java.time.LocalDate
import java.time.YearMonth

/**
 * الرئيسية: الشهر الحالي مع تبديل للأشهر السابقة. كل الأرقام من MonthStats (core) على نفس
 * قائمة العمليات، فهي تطابق مجموع العمليات المعروضة لنفس الفلتر.
 */
@Composable
fun HomeScreen(app: MasarifApp) {
    val txs by app.repository.observeTransactions().collectAsState(initial = emptyList())
    val categories by app.repository.observeCategories().collectAsState(initial = emptyList())
    val catById = remember(categories) { categories.associateBy { it.id } }
    val zone = Fmt.zone
    val today = LocalDate.now(zone)
    val current = YearMonth.from(today)

    val summaries = remember(txs) { txs.map { TxSummary(it.dateTime, it.amountHalalas, it.direction, it.channel, it.categoryId) } }
    val earliest = remember(txs) { txs.minOfOrNull { it.dateTime }?.let { MonthStats.monthOf(it, zone) } ?: current }

    var selectedKey by rememberSaveable { mutableStateOf(current.toString()) }
    val month = remember(selectedKey) { YearMonth.parse(selectedKey) }

    val inMonth = remember(summaries, month) { MonthStats.inMonth(summaries, month, zone) }
    val totals = remember(inMonth) { MonthStats.totals(inMonth) }
    val byCategory = remember(inMonth) { MonthStats.expenseByCategory(inMonth) }
    val byGroup = remember(inMonth) { MonthStats.expenseByChannelGroup(inMonth) }
    val last12 = remember(summaries, month) { MonthStats.last12Months(summaries, month, zone) }
    val comparison = remember(summaries, month) { MonthStats.comparison(summaries, month, zone, today) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // اختيار الشهر
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { selectedKey = month.minusMonths(1).toString() }, enabled = month > earliest) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "الشهر السابق")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.month(month), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (month == current) Text("الشهر الحالي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { selectedKey = month.plusMonths(1).toString() }, enabled = month < current) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "الشهر التالي")
            }
        }

        // الأرقام الأربعة
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("الدخل", Fmt.money(totals.income), IncomeGreen, Modifier.weight(1f))
            StatTile("الصرف", Fmt.money(totals.expense), ExpenseRed, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("الصافي", Fmt.money(totals.net), if (totals.net >= 0) IncomeGreen else ExpenseRed, Modifier.weight(1f))
            StatTile("عدد العمليات", totals.count.toString(), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }

        // المقارنات والتقدير
        SectionCard(title = "مقارنة الصرف") {
            ComparisonLine("مقارنةً بالشهر الماضي", comparison.vsPreviousPercent, comparison.previousExpense)
            Spacer(Modifier.height(6.dp))
            ComparisonLine("مقارنةً بمتوسط آخر 3 أشهر", comparison.vsAvgPercent, comparison.avgLast3)
            comparison.projected?.let { p ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "الصرف المتوقع بنهاية الشهر: ${Fmt.money(p)}",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "تقدير من المعدّل اليومي (${comparison.daysElapsed} من ${comparison.daysInMonth} يوماً)، ليس رقماً مؤكداً.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // الصرف حسب التصنيف
        SectionCard(title = "الصرف حسب التصنيف") {
            if (byCategory.isEmpty()) {
                Text("لا صرف في هذا الشهر.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val slices = byCategory.map { s -> Slice(catById[s.categoryId]?.nameAr ?: "؟", s.amount, catById[s.categoryId]?.let { Color(it.color) } ?: Color.Gray) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DonutChart(slices, centerTitle = Fmt.amount(totals.expense), centerSubtitle = "ر.س")
                }
                Spacer(Modifier.height(12.dp))
                byCategory.forEach { s ->
                    val c = catById[s.categoryId]
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(c?.let { Color(it.color) } ?: Color.Gray, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text("${c?.icon ?: ""} ${c?.nameAr ?: "؟"}", Modifier.weight(1f), maxLines = 1)
                        Text(String.format(java.util.Locale.US, "%.1f%%", s.share * 100), Modifier.width(60.dp), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        Text(Fmt.money(s.amount), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // أونلاين / حضوري / سحب نقدي / تحويلات
        SectionCard(title = "الصرف حسب القناة") {
            if (byGroup.isEmpty()) {
                Text("لا صرف في هذا الشهر.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val colors = groupColors()
                val slices = byGroup.map { (g, v) -> Slice(Fmt.group(g), v, colors.getValue(g)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(slices, size = 130.dp, stroke = 22.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        byGroup.forEach { (g, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(colors.getValue(g), CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(Fmt.group(g), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(String.format(java.util.Locale.US, "%.0f%%", v * 100.0 / totals.expense.coerceAtLeast(1)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // آخر 12 شهراً
        SectionCard(title = "إجمالي الصرف لكل شهر (آخر 12 شهراً)") {
            BarChart(
                bars = last12.map { Fmt.monthShort(it.month) to it.expense },
                average = comparison.avgLast3,
                highlightIndex = last12.indexOfFirst { it.month == month }.takeIf { it >= 0 },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(18.dp).height(3.dp).background(MaterialTheme.colorScheme.error))
                Spacer(Modifier.width(8.dp))
                Text(
                    comparison.avgLast3?.let { "متوسط آخر 3 أشهر: ${Fmt.money(it)}" } ?: "لا يوجد متوسط بعد (يلزم شهر سابق واحد على الأقل)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

/** للصرف: الزيادة حمراء والنقصان أخضر. */
@Composable
private fun ComparisonLine(label: String, percent: Double?, base: Long?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (base != null) Text(Fmt.money(base), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (percent == null) {
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val color = when {
                percent > 0.5 -> ExpenseRed
                percent < -0.5 -> IncomeGreen
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(Fmt.percent(percent), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun groupColors(): Map<ChannelGroup, Color> = mapOf(
    ChannelGroup.ONLINE to Color(0xFF7DD3FC),
    ChannelGroup.IN_PERSON to Color(0xFF5EEAD4),
    ChannelGroup.CASH to Color(0xFFFBBF24),
    ChannelGroup.TRANSFER to Color(0xFFC084FC),
    ChannelGroup.OTHER to Color(0xFF9CA3AF),
)
