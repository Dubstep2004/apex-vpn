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
    if (style == "none") return
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
