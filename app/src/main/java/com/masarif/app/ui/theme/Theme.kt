package com.masarif.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val IncomeGreen = Color(0xFF4ADE80)
val ExpenseRed = Color(0xFFF87171)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF002233),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF232733),
    onSurfaceVariant = Color(0xFFB4B9C5),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0000),
    outline = Color(0xFF3A3F4B),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF0369A1),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF15181E),
    surface = Color.White,
    onSurface = Color(0xFF15181E),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Color(0xFF4B5160),
    error = Color(0xFFDC2626),
    outline = Color(0xFFC9CDD6),
)

/** واجهة عربية RTL بالكامل، داكنة افتراضياً. */
@Composable
fun MasarifTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
