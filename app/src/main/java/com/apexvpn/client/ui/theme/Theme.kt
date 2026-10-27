package com.apexvpn.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.apexvpn.client.data.AccentColor
import com.apexvpn.client.data.ThemeMode

/** Набор цветов одной темы. */
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val divider: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentText: Color,
    val selectedCard: Color,
    val heroCard: Color,
)

private fun darkPalette(accent: Color, amoled: Boolean) = Palette(
    dark = true,
    bg = if (amoled) Color.Black else Color(0xFF0A0A0D),
    surface = if (amoled) Color(0xFF0E0E11) else Color(0xFF16161B),
    surfaceHigh = if (amoled) Color(0xFF19191E) else Color(0xFF202027),
    outline = Color(0xFF26262E),
    divider = if (amoled) Color(0xFF1A1A1F) else Color(0xFF232329),
    text = Color(0xFFF4F4F7),
    textDim = Color(0xFF8E8E9A),
    accent = accent,
    accentSoft = lerp(Color(0xFF16161B), accent, 0.22f),
    accentText = lerp(accent, Color.White, 0.45f),
    selectedCard = lerp(if (amoled) Color.Black else Color(0xFF0A0A0D), accent, 0.12f),
    heroCard = lerp(Color(0xFF16161B), accent, 0.14f),
)

private fun lightPalette(accent: Color) = Palette(
    dark = false,
    bg = Color(0xFFF4F4F7),
    surface = Color.White,
    surfaceHigh = Color(0xFFEDEDF2),
    outline = Color(0xFFDDDDE4),
    divider = Color(0xFFEAEAF0),
    text = Color(0xFF121216),
    textDim = Color(0xFF6B6B77),
    accent = accent,
    accentSoft = lerp(Color.White, accent, 0.14f),
    accentText = lerp(accent, Color.Black, 0.25f),
    selectedCard = lerp(Color.White, accent, 0.08f),
    heroCard = lerp(Color.White, accent, 0.10f),
)

/**
 * Цвета APEX VPN. Свойства читают текущую палитру из состояния Compose,
 * поэтому смена темы мгновенно перерисовывает все экраны.
 */
object Apex {
    var palette by mutableStateOf(darkPalette(Color(AccentColor.Violet.argb), amoled = false))
        internal set

    val Bg get() = palette.bg
    val Surface get() = palette.surface
    val SurfaceHigh get() = palette.surfaceHigh
    val Outline get() = palette.outline
    val Divider get() = palette.divider
    val Text get() = palette.text
    val TextDim get() = palette.textDim
    val Accent get() = palette.accent
    val AccentSoft get() = palette.accentSoft
    val AccentText get() = palette.accentText
    val SelectedCard get() = palette.selectedCard
    val HeroCard get() = palette.heroCard
    val isDark get() = palette.dark

    // Фирменные цвета логотипа — одинаковые во всех темах
    val Cyan = Color(0xFF22D3EE)
    val CyanLight = Color(0xFFA5F3FC)
    val Violet = Color(0xFF7C3AED)
    val Good get() = if (palette.dark) Color(0xFF5BD69A) else Color(0xFF16A35A)
    val Warn get() = if (palette.dark) Color(0xFFF2B75B) else Color(0xFFC77700)
    val Bad get() = if (palette.dark) Color(0xFFFF6B6B) else Color(0xFFDC2626)

    val accent = Brush.linearGradient(listOf(Violet, Cyan))
}

private val typography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

/** Палитра для настроек темы; [systemDark] — включена ли тёмная тема в системе. */
fun paletteFor(theme: ThemeMode, accent: AccentColor, systemDark: Boolean): Palette {
    val accentColor = Color(accent.argb)
    return when (theme) {
        ThemeMode.Light -> lightPalette(accentColor)
        ThemeMode.Dark -> darkPalette(accentColor, amoled = false)
        ThemeMode.Amoled -> darkPalette(accentColor, amoled = true)
        ThemeMode.System -> if (systemDark) darkPalette(accentColor, false) else lightPalette(accentColor)
    }
}

/** Применить палитру (вызывается вне отрисовки — из Activity). */
fun applyPalette(p: Palette) {
    if (Apex.palette != p) Apex.palette = p
}

@Composable
fun ApexTheme(content: @Composable () -> Unit) {
    val p = Apex.palette
    val base = if (p.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = p.accent,
        onPrimary = Color.White,
        secondary = Apex.Cyan,
        background = p.bg,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.textDim,
        surfaceContainer = p.surface,
        surfaceContainerLow = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.surfaceHigh,
        outline = p.outline,
        outlineVariant = p.divider,
        error = Apex.Bad,
    )
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
