package com.apexvpn.client.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.apexvpn.client.ui.theme.Apex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 17. Анимированный фон за щитом: волны, частицы или звёзды. */
@Composable
fun ShieldAmbient(style: String, active: Boolean, modifier: Modifier = Modifier) {
    if (style !in setOf("waves", "particles", "stars")) return // «нет» и своё фото — без анимации
    val smooth = Apex.feel.smooth
    val t = rememberInfiniteTransition(label = "ambient")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (active) 6000 else 12000, easing = LinearEasing)), label = "phase")
    val p = if (smooth) 0.25f else phase
    val a1 = Apex.Accent
    val a2 = Apex.Cyan
    val dim = Apex.TextDim
    val seeds = remember { List(60) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    Canvas(modifier) {
        when (style) {
            "waves" -> waves(p, if (active) a1 else dim, if (active) a2 else dim)
            "particles" -> particles(p, seeds, if (active) a2 else dim)
            "stars" -> stars(p, seeds, if (active) Color.White else dim)
        }
    }
}

private fun DrawScope.waves(p: Float, c1: Color, c2: Color) {
    val h = size.height
    val w = size.width
    for (k in 0 until 4) {
        val path = Path()
        val base = h * (0.38f + k * 0.08f)
        val amp = h * (0.05f + k * 0.012f)
        val shift = (p + k * 0.21f) * 2 * PI.toFloat()
        var x = 0f
        path.moveTo(0f, base)
        while (x <= w) {
            val y = base + amp * sin(x / w * 2 * PI.toFloat() * (1.2f + k * 0.3f) + shift)
            path.lineTo(x, y)
            x += 6f
        }
        val c = if (k % 2 == 0) c1 else c2
        drawPath(path, c.copy(alpha = 0.22f - k * 0.04f), style = Stroke(2.dp.toPx()))
    }
}

private fun DrawScope.particles(p: Float, seeds: List<Triple<Float, Float, Float>>, c: Color) {
    seeds.take(40).forEach { (sx, sy, sz) ->
        val y = ((sy - p * (0.4f + sz * 0.6f)) % 1f + 1f) % 1f
        val x = sx + 0.03f * sin((p + sz) * 2 * PI.toFloat())
        val fade = sin(y * PI.toFloat())
        drawCircle(c.copy(alpha = 0.55f * fade), (1.2f + sz * 2.2f).dp.toPx(), Offset(x * size.width, y * size.height))
    }
}

private fun DrawScope.stars(p: Float, seeds: List<Triple<Float, Float, Float>>, c: Color) {
    seeds.forEach { (sx, sy, sz) ->
        val tw = 0.35f + 0.65f * abs(cos((p * 2 + sz * 3) * PI.toFloat()))
        val r = (0.6f + sz * 1.4f).dp.toPx()
        val o = Offset(sx * size.width, sy * size.height)
        drawCircle(c.copy(alpha = 0.7f * tw), r, o)
        if (sz > 0.85f) {
            drawLine(c.copy(alpha = 0.4f * tw), o.copy(x = o.x - r * 3), o.copy(x = o.x + r * 3), 1f)
            drawLine(c.copy(alpha = 0.4f * tw), o.copy(y = o.y - r * 3), o.copy(y = o.y + r * 3), 1f)
        }
    }
}

/** 12. Свайп влево/вправо — следующий или предыдущий сервер. */
@Composable
fun Modifier.swipeServers(onSwipe: (Int) -> Unit): Modifier {
    val latest = androidx.compose.runtime.rememberUpdatedState(onSwipe)
    return this.then(swipeModifier { latest.value(it) })
}

private fun swipeModifier(onSwipe: (Int) -> Unit): Modifier =
    Modifier.pointerInput(Unit) {
        val threshold = 72.dp.toPx()
        var acc = 0f
        detectHorizontalDragGestures(
            onDragStart = { acc = 0f },
            onDragEnd = {
                if (abs(acc) > threshold) onSwipe(if (acc < 0) 1 else -1)
                acc = 0f
            },
            onDragCancel = { acc = 0f },
        ) { change, dx ->
            acc += dx
            change.consume()
        }
    }

/**
 * 430. Космос: две планеты и спутник летают по наклонённым орбитам вокруг щита.
 * Планета «за щитом» темнее и меньше — так видно глубину.
 */
@Composable
fun CosmosOrbit(modifier: Modifier) {
    val smooth = com.apexvpn.client.ui.theme.Apex.feel.smooth
    val t = rememberInfiniteTransition(label = "orbit")
    val aAnim by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "orbitA")
    val bAnim by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "orbitB")
    val c1 = com.apexvpn.client.ui.theme.Apex.Violet
    val c2 = com.apexvpn.client.ui.theme.Apex.Cyan
    androidx.compose.foundation.Canvas(modifier) {
        // В «Плавном режиме» планеты стоят на месте
        val a = if (smooth) 1.2f else aAnim
        val b = if (smooth) 3.8f else bAnim
        val cx = size.width / 2
        val cy = size.height / 2
        val rx = size.minDimension * 0.58f
        val ry = size.minDimension * 0.16f
        // Орбиты
        fun orbit(rot: Float, rxx: Float, ryy: Float) = rotate(rot, Offset(cx, cy)) {
            drawOval(Color.White.copy(alpha = 0.10f), Offset(cx - rxx, cy - ryy), androidx.compose.ui.geometry.Size(rxx * 2, ryy * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        orbit(-14f, rx, ry)
        orbit(18f, rx * 0.82f, ry * 1.2f)
        fun body(angle: Float, rot: Float, rxx: Float, ryy: Float, r: Float, color: Color, ring: Boolean) {
            val x0 = rxx * kotlin.math.cos(angle)
            val y0 = ryy * kotlin.math.sin(angle)
            val rr = Math.toRadians(rot.toDouble())
            val x = cx + (x0 * kotlin.math.cos(rr) - y0 * kotlin.math.sin(rr)).toFloat()
            val y = cy + (x0 * kotlin.math.sin(rr) + y0 * kotlin.math.cos(rr)).toFloat()
            val depth = 0.65f + 0.35f * kotlin.math.sin(angle) // спереди — ярче и крупнее
            val rad = r * depth
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * depth), color, color.copy(alpha = 0.3f)), Offset(x - rad * 0.35f, y - rad * 0.35f), rad * 1.4f),
                rad, Offset(x, y),
            )
            if (ring) {
                drawOval(color.copy(alpha = 0.6f * depth), Offset(x - rad * 1.8f, y - rad * 0.45f), androidx.compose.ui.geometry.Size(rad * 3.6f, rad * 0.9f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
        }
        body(a, -14f, rx, ry, 9.dp.toPx(), c2, ring = true)
        body(b + 2f, 18f, rx * 0.82f, ry * 1.2f, 6.dp.toPx(), c1, ring = false)
        // Спутник: корпус и две солнечные панели
        val sa = -a * 1.6f
        val sx = cx + size.minDimension * 0.42f * kotlin.math.cos(sa)
        val sy = cy + size.minDimension * 0.30f * kotlin.math.sin(sa)
        rotate(Math.toDegrees(sa.toDouble()).toFloat(), Offset(sx, sy)) {
            drawRect(Color(0xFFE2E6FF), Offset(sx - 2.dp.toPx(), sy - 2.dp.toPx()), androidx.compose.ui.geometry.Size(4.dp.toPx(), 4.dp.toPx()))
            drawRect(c2.copy(alpha = 0.8f), Offset(sx - 9.dp.toPx(), sy - 1.5.dp.toPx()), androidx.compose.ui.geometry.Size(6.dp.toPx(), 3.dp.toPx()))
            drawRect(c2.copy(alpha = 0.8f), Offset(sx + 3.dp.toPx(), sy - 1.5.dp.toPx()), androidx.compose.ui.geometry.Size(6.dp.toPx(), 3.dp.toPx()))
        }
    }
}
