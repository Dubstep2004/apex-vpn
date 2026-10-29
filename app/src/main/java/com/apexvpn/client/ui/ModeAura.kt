package com.apexvpn.client.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.apexvpn.client.data.AutoModes
import com.apexvpn.client.ui.theme.Apex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 588. Живой фон автопилота: вокруг щита — анимация режима.
 * 🎮 частый красный пульс · 🚀 голубые полосы скорости · 🛡 ровное кольцо ·
 * 📞 звуковые волны · 🔋 медленное «дыхание».
 */
@Composable
fun ModeAura(modeId: String, active: Boolean, modifier: Modifier = Modifier) {
    val mode = AutoModes.Mode.of(modeId)
    if (!active || mode == AutoModes.Mode.Balance || Apex.feel.smooth) return
    val period = when (mode) {
        AutoModes.Mode.Games -> 900
        AutoModes.Mode.Speed -> 1100
        AutoModes.Mode.Battery -> 4000
        else -> 2600
    }
    val t by rememberInfiniteTransition(label = "aura").animateFloat(
        0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing)), label = "t",
    )
    val c1 = Apex.Violet
    val c2 = Apex.Cyan
    Canvas(modifier) {
        val c = center
        val r = size.minDimension * 0.36f
        when (mode) {
            AutoModes.Mode.Games -> for (k in 0..1) {
                // Два кольца расходятся по очереди — как пульс
                val p = (t + k * 0.5f) % 1f
                drawCircle(c1.copy(alpha = (1f - p) * 0.55f), r * (0.95f + p * 0.45f), c, style = Stroke(3f + (1f - p) * 5f))
            }
            AutoModes.Mode.Speed -> for (i in 0 until 10) {
                // Полосы летят мимо щита слева направо
                val lane = (i * 0.37f) % 1f
                val y = c.y - r * 1.1f + lane * r * 2.2f
                val x = ((t + i * 0.13f) % 1f) * size.width * 1.4f - size.width * 0.2f
                val len = r * (0.35f + (i % 3) * 0.15f)
                drawLine(
                    Brush.horizontalGradient(listOf(Color.Transparent, c2.copy(alpha = 0.7f)), x - len, x),
                    Offset(x - len, y), Offset(x, y), strokeWidth = 4f, cap = StrokeCap.Round,
                )
            }
            AutoModes.Mode.Stable -> rotate(t * 360f, c) {
                // Ровное кольцо с бегущей точкой
                drawCircle(c1.copy(alpha = 0.25f), r * 1.18f, c, style = Stroke(4f))
                drawCircle(c2, 7f, Offset(c.x + r * 1.18f, c.y))
            }
            AutoModes.Mode.Calls -> for (k in 0..2) {
                // Звуковые волны в стороны
                val p = (t + k / 3f) % 1f
                val rr = r * (1.0f + p * 0.5f)
                val a = (1f - p) * 0.6f
                for (side in listOf(0f, 180f)) {
                    drawArc(c2.copy(alpha = a), side - 35f, 70f, false, Offset(c.x - rr, c.y - rr), androidx.compose.ui.geometry.Size(rr * 2, rr * 2), style = Stroke(5f, cap = StrokeCap.Round))
                }
            }
            AutoModes.Mode.Battery -> {
                val b = (sin(t * 2 * PI).toFloat() + 1f) / 2f
                drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.10f + b * 0.15f), Color.Transparent), c, r * 1.5f), r * 1.5f, c)
            }
            else -> Unit
        }
        // Мелкие искры по кругу для всех режимов
        for (i in 0 until 6) {
            val ang = (t + i / 6f) * 2 * PI
            drawCircle(c2.copy(alpha = 0.35f), 3f, Offset(c.x + cos(ang).toFloat() * r * 1.32f, c.y + sin(ang).toFloat() * r * 1.32f))
        }
    }
}
