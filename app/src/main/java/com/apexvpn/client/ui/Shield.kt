package com.apexvpn.client.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.vpn.VpnStatus

/** Контур щита в координатах 100×100. */
private fun shieldPath(scale: Float) = Path().apply {
    moveTo(50f * scale, 6f * scale)
    lineTo(86f * scale, 20f * scale)
    lineTo(86f * scale, 48f * scale)
    cubicTo(86f * scale, 70f * scale, 70f * scale, 86f * scale, 50f * scale, 94f * scale)
    cubicTo(30f * scale, 86f * scale, 14f * scale, 70f * scale, 14f * scale, 48f * scale)
    lineTo(14f * scale, 20f * scale)
    close()
}

private fun checkPath(scale: Float) = Path().apply {
    moveTo(33f * scale, 50f * scale)
    lineTo(45f * scale, 62f * scale)
    lineTo(68f * scale, 38f * scale)
}

/**
 * Большая кнопка-щит. Выключено — пустой щит со значком питания,
 * подключение — бегущий контур, подключено — щит с градиентом и галочкой.
 */
@Composable
fun ShieldButton(status: VpnStatus, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val active = status == VpnStatus.Started
    val paused = status == VpnStatus.Paused
    val busy = status == VpnStatus.Starting || status == VpnStatus.Stopping

    val transition = rememberInfiniteTransition(label = "shield")
    val glow by transition.animateFloat(
        0.85f, 1.08f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val runner by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "runner",
    )
    val check = remember { Animatable(0f) }
    val fill = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            fill.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
            check.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        } else {
            check.snapTo(0f)
            fill.animateTo(0f, tween(300))
        }
    }

    val surface = Apex.SurfaceHigh
    val dim = Apex.TextDim
    val accent = Apex.Accent

    Box(
        Modifier
            .size(240.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = status != VpnStatus.Stopping,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(240.dp)) {
            val s = size.minDimension / 100f
            val shield = shieldPath(s)

            // Свечение
            if (active || busy || paused) {
                val r = size.minDimension * 0.48f * (if (active) glow else 1f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = if (active) 0.45f else 0.2f), Color.Transparent),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                )
            }

            // Тело щита
            scale(0.82f, center) {
                drawPath(shield, surface)
                if (fill.value > 0f) {
                    drawPath(
                        shield,
                        Brush.linearGradient(listOf(Apex.Violet, Apex.Cyan), Offset(0f, size.height), Offset(size.width, 0f)),
                        alpha = fill.value,
                    )
                }
                // Контур
                if (busy) {
                    drawRunner(shield, runner, accent)
                } else {
                    drawPath(
                        shield,
                        if (active) Color.White.copy(alpha = 0.35f) else if (paused) accent else Apex.Outline,
                        style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round),
                    )
                }

                // Значок: галочка при подключении, иначе «питание»
                if (active) {
                    val path = checkPath(s)
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val part = Path()
                    measure.getSegment(0f, measure.length * check.value, part, true)
                    drawPath(part, Color.White, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else {
                    drawPower(s, if (paused) accent else dim)
                }
            }
        }
    }
}

/** Бегущий отрезок по контуру щита. */
private fun DrawScope.drawRunner(shield: Path, progress: Float, color: Color) {
    drawPath(shield, color.copy(alpha = 0.18f), style = Stroke(width = 3.dp.toPx()))
    val measure = PathMeasure().apply { setPath(shield, true) }
    val len = measure.length
    val start = progress * len
    val seg = len * 0.28f
    val part = Path()
    if (start + seg <= len) {
        measure.getSegment(start, start + seg, part, true)
    } else {
        measure.getSegment(start, len, part, true)
        measure.getSegment(0f, start + seg - len, part, true)
    }
    drawPath(part, color, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
}

/** Значок питания в центре щита. */
private fun DrawScope.drawPower(s: Float, color: Color) {
    val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
    val c = Offset(50f * s, 52f * s)
    val r = 16f * s
    rotate(0f, c) {
        drawArc(
            color, startAngle = -60f, sweepAngle = 300f, useCenter = false,
            topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = stroke,
        )
    }
    drawLine(color, Offset(c.x, c.y - r - 4f * s), Offset(c.x, c.y - 2f * s), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
}
