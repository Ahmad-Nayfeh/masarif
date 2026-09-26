package com.masarif.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** المخططات مرسومة بـ Compose Canvas مباشرة: بلا مكتبة، تحكّم كامل بالـ RTL والوضع الداكن. */

data class Slice(val label: String, val value: Long, val color: Color)

@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    size: Dp = 170.dp,
    stroke: Dp = 26.dp,
    centerTitle: String? = null,
    centerSubtitle: String? = null,
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)
            if (slices.isEmpty()) {
                drawArc(emptyColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Butt))
                return@Canvas
            }
            var start = -90f
            val gap = if (slices.size > 1) 2f else 0f
            slices.forEach { s ->
                val sweep = s.value.toFloat() / total * 360f
                val drawn = (sweep - gap).coerceAtLeast(0.5f)
                drawArc(s.color, start + gap / 2, drawn, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        if (centerTitle != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (centerSubtitle != null) {
                    Text(centerSubtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * مخطط أعمدة (الأقدم يساراً، الأحدث يميناً) مع خط متقطع للمتوسط وتمييز العمود المحدد.
 * يُرسم بترتيب LTR دائماً لأن محور الزمن يُقرأ من اليسار لليمين حتى في الواجهات العربية.
 */
@Composable
fun BarChart(
    bars: List<Pair<String, Long>>,
    average: Long?,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    highlightIndex: Int? = null,
) {
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val highlightColor = MaterialTheme.colorScheme.primary
    val avgColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val maxValue = maxOf(bars.maxOfOrNull { it.second } ?: 0L, average ?: 0L).coerceAtLeast(1L)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth().height(height)) {
                val w = size.width
                val h = size.height
                val n = bars.size.coerceAtLeast(1)
                val slot = w / n
                val barW = slot * 0.58f
                // خطوط شبكة خفيفة
                for (i in 1..3) {
                    val y = h - h * i / 4f
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                bars.forEachIndexed { i, (_, v) ->
                    val barH = (v.toFloat() / maxValue) * (h - 4f)
                    val x = slot * i + (slot - barW) / 2
                    drawRect(
                        color = if (i == highlightIndex) highlightColor else barColor,
                        topLeft = Offset(x, h - barH),
                        size = Size(barW, barH),
                    )
                }
                if (average != null && average > 0) {
                    val y = h - (average.toFloat() / maxValue) * (h - 4f)
                    drawLine(
                        avgColor, Offset(0f, y), Offset(w, y), strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                bars.forEachIndexed { i, (label, _) ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = if (i == highlightIndex) highlightColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
