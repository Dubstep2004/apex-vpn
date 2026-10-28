package com.apexvpn.client.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.Celebrate
import com.apexvpn.client.ui.theme.Apex
import kotlin.random.Random

private class Piece(
    val x0: Float, val vx: Float, val vy: Float, val spin: Float, val rot0: Float,
    val w: Float, val h: Float, val color: Color, val round: Boolean,
)

/**
 * 382. Конфетти поверх всего экрана: две «пушки» снизу по краям,
 * кусочки летят вверх, кружатся и падают. В «Плавном режиме» — только сообщение.
 */
@Composable
fun ConfettiOverlay(onMessage: (String) -> Unit) {
    val event by Celebrate.event.collectAsStateWithLifecycle()
    var pieces by remember { mutableStateOf<List<Piece>>(emptyList()) }
    val t = remember { Animatable(0f) }
    val colors = listOf(Apex.Accent, Apex.Cyan, Color(0xFFFFD166), Color(0xFFFF5D8F), Color(0xFF5BD69A), Color.White)
    LaunchedEffect(event?.id) {
        val e = event ?: return@LaunchedEffect
        e.text?.let(onMessage)
        Celebrate.consume()
        if (Apex.feel.smooth) return@LaunchedEffect
        val r = Random(e.id)
        pieces = List(140) { i ->
            val left = i % 2 == 0
            Piece(
                x0 = if (left) 0.02f else 0.98f,
                vx = (if (left) 1f else -1f) * (0.15f + r.nextFloat() * 0.55f),
                vy = -(1.0f + r.nextFloat() * 0.75f),
                spin = (r.nextFloat() - 0.5f) * 1400f,
                rot0 = r.nextFloat() * 360f,
                w = 6f + r.nextFloat() * 8f,
                h = 10f + r.nextFloat() * 10f,
                color = colors[r.nextInt(colors.size)],
                round = r.nextFloat() < 0.25f,
            )
        }
        t.snapTo(0f)
        t.animateTo(1f, tween(2800, easing = LinearEasing))
        pieces = emptyList()
    }
    if (pieces.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        val time = t.value * 2.8f
        val g = 1.15f // «гравитация» в долях высоты экрана за с²
        pieces.forEach { p ->
            // Сопротивление воздуха: скорость гаснет, падение плавное
            val drag = 1f - kotlin.math.exp(-time * 1.6f)
            val x = (p.x0 + p.vx * drag / 1.6f) * size.width
            val y = (1f + p.vy * drag / 1.6f * 1.2f + 0.5f * g * time * time * 0.35f) * size.height
            if (y > size.height + 40f) return@forEach
            val alpha = (1f - ((t.value - 0.8f) / 0.2f).coerceIn(0f, 1f))
            val dp = density
            rotate(p.rot0 + p.spin * time, Offset(x, y)) {
                if (p.round) {
                    drawCircle(p.color.copy(alpha = alpha), p.w * 0.45f * dp, Offset(x, y))
                } else {
                    // «Переворот» кусочка: ширина пульсирует, как у бумажки в воздухе
                    val flip = kotlin.math.abs(kotlin.math.cos(time * 6f + p.rot0))
                    drawRect(
                        p.color.copy(alpha = alpha),
                        Offset(x - p.w * dp * flip / 2, y - p.h * dp / 2),
                        Size(p.w * dp * flip + 1f, p.h * dp),
                    )
                }
            }
        }
    }
}
