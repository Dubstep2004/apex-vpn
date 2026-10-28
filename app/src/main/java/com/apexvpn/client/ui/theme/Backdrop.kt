package com.apexvpn.client.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Фон экранов для выбранного дизайна: стеклянные пятна, северное сияние,
 * неоновая сетка, строки терминала или пастельный градиент.
 */
@Composable
fun DesignBackground(modifier: Modifier = Modifier) {
    val p = Apex.palette
    // Размытие стекла (Android 12+): пятна фона становятся мягче
    val blurred = Apex.feel.blur && android.os.Build.VERSION.SDK_INT >= 31 &&
        p.backdrop in setOf(Backdrop.Glass, Backdrop.Aurora, Backdrop.Pastel, Backdrop.Neon)
    val modifier = if (blurred) modifier.blur(36.dp) else modifier
    when (p.backdrop) {
        Backdrop.Solid -> Canvas(modifier.fillMaxSize()) { drawRect(p.base) }
        Backdrop.Glass -> Canvas(modifier.fillMaxSize()) { drawGlass(p) }
        Backdrop.Aurora -> if (Apex.feel.smooth) Canvas(modifier.fillMaxSize()) { drawAurora(p, 0f) } else AuroraBackground(modifier, p)
        Backdrop.Neon -> Canvas(modifier.fillMaxSize()) { drawNeon(p) }
        Backdrop.Terminal -> Canvas(modifier.fillMaxSize()) { drawTerminal(p) }
        Backdrop.Pastel -> Canvas(modifier.fillMaxSize()) { drawPastel(p) }
        Backdrop.Apex2 -> Canvas(modifier.fillMaxSize()) { drawApex2(p) }
    }
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
}

private fun DrawScope.drawGlass(p: Palette) {
    drawRect(p.base)
    val w = size.width
    val h = size.height
    val a = if (p.dark) 0.55f else 0.45f
    blob(p.accent.copy(alpha = a), Offset(w * 0.1f, h * 0.12f), w * 0.75f)
    blob(Color(0xFF22D3EE).copy(alpha = a * 0.8f), Offset(w * 0.95f, h * 0.38f), w * 0.7f)
    blob(Color(0xFFF472B6).copy(alpha = a * 0.7f), Offset(w * 0.25f, h * 0.85f), w * 0.8f)
}

@Composable
private fun AuroraBackground(modifier: Modifier, p: Palette) {
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier.fillMaxSize()) { drawAurora(p, phase) }
}

private fun DrawScope.drawAurora(p: Palette, phase: Float) {
    drawRect(p.base)
    val w = size.width
    val h = size.height
    val a = if (p.dark) 0.6f else 0.45f
    val colors = listOf(Color(0xFF34D399), Color(0xFF818CF8), Color(0xFFF472B6), Color(0xFF22D3EE))
    colors.forEachIndexed { i, c ->
        val ph = phase + i * 1.6f
        val center = Offset(
            w * (0.5f + 0.38f * cos(ph)),
            h * (0.25f + 0.18f * sin(ph * 0.7f) + i * 0.17f),
        )
        blob(c.copy(alpha = a), center, w * 0.7f)
    }
}

private fun DrawScope.drawNeon(p: Palette) {
    drawRect(p.base)
    val step = 32.dp.toPx()
    val line = p.brand2.copy(alpha = 0.06f)
    var x = 0f
    while (x < size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    blob(p.brand1.copy(alpha = 0.28f), Offset(size.width * 0.1f, 0f), size.width * 0.8f)
    blob(p.brand2.copy(alpha = 0.22f), Offset(size.width, size.height), size.width * 0.9f)
}

private fun DrawScope.drawTerminal(p: Palette) {
    drawRect(p.base)
    // Строки развёртки старого монитора
    val step = 3.dp.toPx()
    val line = p.accent.copy(alpha = 0.035f)
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    blob(p.accent.copy(alpha = 0.08f), Offset(size.width / 2, size.height / 3), size.width)
}

/** 212. APEX 2.0: мягкое свечение акцента сверху и бирюзы снизу. */
private fun DrawScope.drawApex2(p: Palette) {
    drawRect(p.base)
    val w = size.width
    val h = size.height
    val a = if (p.dark) 0.30f else 0.18f
    blob(p.brand1.copy(alpha = a), Offset(w * 0.5f, -h * 0.05f), w * 1.0f)
    blob(p.brand2.copy(alpha = a * 0.55f), Offset(w * 1.05f, h * 0.95f), w * 0.85f)
}

private fun DrawScope.drawPastel(p: Palette) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF1E8), Color(0xFFF6EEFF), Color(0xFFEFF7FF))))
    blob(p.brand1.copy(alpha = 0.35f), Offset(size.width * 0.9f, size.height * 0.1f), size.width * 0.7f)
    blob(p.brand2.copy(alpha = 0.30f), Offset(0f, size.height * 0.6f), size.width * 0.8f)
}
