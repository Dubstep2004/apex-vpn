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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.vpn.VpnStatus
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Точки суши на сфере: по 4 точки на клетку карты (единичные векторы). */
private object GlobeLand {
    val points: List<FloatArray> by lazy {
        val out = ArrayList<FloatArray>()
        val dLon = 360f / WorldMap.W
        val dLat = (WorldMap.N - WorldMap.S) / WorldMap.H
        WorldMap.rows.forEachIndexed { j, row ->
            row.forEachIndexed { i, c ->
                if (c != '#') return@forEachIndexed
                for (a in 0..1) for (b in 0..1) {
                    val lon = -180f + (i + 0.25f + a * 0.5f) * dLon
                    val lat = WorldMap.N - (j + 0.25f + b * 0.5f) * dLat
                    out += vec(lat, lon)
                }
            }
        }
        out
    }
}

private fun vec(lat: Float, lon: Float): FloatArray {
    val la = Math.toRadians(lat.toDouble())
    val lo = Math.toRadians(lon.toDouble())
    return floatArrayOf((cos(la) * cos(lo)).toFloat(), (cos(la) * sin(lo)).toFloat(), sin(la).toFloat())
}

/**
 * Ортографическая проекция: камера смотрит на точку (lat0, lon0).
 * Возвращает x, y (−1…1, y вверх) и глубину (>0 — видимая сторона).
 */
private class Projector(lat0: Float, lon0: Float) {
    private val sφ = sin(Math.toRadians(lat0.toDouble())).toFloat()
    private val cφ = cos(Math.toRadians(lat0.toDouble())).toFloat()
    private val sλ = sin(Math.toRadians(lon0.toDouble())).toFloat()
    private val cλ = cos(Math.toRadians(lon0.toDouble())).toFloat()
    fun project(v: FloatArray, out: FloatArray) {
        // Поворот вокруг оси Z на −lon0
        val x1 = v[0] * cλ + v[1] * sλ
        val y1 = -v[0] * sλ + v[1] * cλ
        val z1 = v[2]
        // Наклон на lat0
        val depth = x1 * cφ + z1 * sφ
        val up = -x1 * sφ + z1 * cφ
        out[0] = y1; out[1] = up; out[2] = depth
    }
}

private fun slerp(a: FloatArray, b: FloatArray, t: Float): FloatArray {
    val dot = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1f, 1f)
    val om = acos(dot)
    if (om < 1e-4f) return a
    val s = sin(om)
    val k1 = sin((1 - t) * om) / s
    val k2 = sin(t * om) / s
    return floatArrayOf(a[0] * k1 + b[0] * k2, a[1] * k1 + b[1] * k2, a[2] * k1 + b[2] * k2)
}

/**
 * 353. Вращающийся 3D-глобус из точек: поворачивается к стране сервера,
 * дуга от вас до сервера поднимается над поверхностью.
 */
@Composable
fun Globe(
    modifier: Modifier,
    from: Pair<Float, Float>?,
    to: Pair<Float, Float>?,
    connected: Boolean,
    busy: Boolean = false,
    spin: Boolean = false,
) {
    val live = Apex.feel.liveMap && !Apex.feel.smooth
    val focus = to ?: from ?: (50f to 30f)
    // Камера чуть ниже цели, чтобы дуга была видна
    val lat = remember { Animatable(focus.first - 12f) }
    val lon = remember { Animatable(focus.second) }
    LaunchedEffect(focus) {
        val spec = tween<Float>(if (Apex.feel.smooth) 0 else 1400, easing = FastOutSlowInEasing)
        // Кратчайший путь по долготе
        var target = focus.second
        while (target - lon.value > 180f) target -= 360f
        while (target - lon.value < -180f) target += 360f
        kotlinx.coroutines.coroutineScope {
            kotlinx.coroutines.launch { lat.animateTo((focus.first - 12f).coerceIn(-60f, 60f), spec) }
            kotlinx.coroutines.launch { lon.animateTo(target, spec) }
        }
    }
    val t = rememberInfiniteTransition(label = "globe")
    val drift by t.animateFloat(0f, 360f, infiniteRepeatable(tween(90000, easing = LinearEasing)), label = "drift")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "phase")

    val dot = Apex.Text
    val accent = Apex.Accent
    val accent2 = Apex.Cyan
    val ocean = Apex.SurfaceHigh
    val dark = Apex.isDark
    Canvas(modifier) {
        val r = size.minDimension * 0.44f
        val c = Offset(size.width / 2, size.height / 2)
        val p = Projector(lat.value, lon.value + if (spin && live) drift else 0f)
        // Атмосфера и океан
        drawCircle(Brush.radialGradient(listOf(accent2.copy(alpha = 0.22f), Color.Transparent), c, r * 1.25f), r * 1.25f, c)
        drawCircle(
            Brush.radialGradient(listOf(ocean, ocean.copy(alpha = 0.6f), Color.Black.copy(alpha = if (dark) 0.6f else 0.15f)), c - Offset(r * 0.3f, r * 0.3f), r * 1.5f),
            r, c,
        )
        // Суша: точки разной яркости по глубине (три слоя за один вызов каждый)
        val buckets = arrayOf(ArrayList<Offset>(), ArrayList<Offset>(), ArrayList<Offset>())
        val o = FloatArray(3)
        for (v in GlobeLand.points) {
            p.project(v, o)
            if (o[2] <= 0.02f) continue
            val b = if (o[2] > 0.6f) 2 else if (o[2] > 0.3f) 1 else 0
            buckets[b] += Offset(c.x + o[0] * r, c.y - o[1] * r)
        }
        val base = if (dark) 0.75f else 0.6f
        buckets.forEachIndexed { i, pts ->
            drawPoints(pts, PointMode.Points, dot.copy(alpha = base * (0.35f + i * 0.3f)), strokeWidth = r * 0.024f, cap = StrokeCap.Round)
        }
        // Блик
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), c - Offset(r * 0.45f, r * 0.45f), r * 0.9f), r, c)
        drawCircle(accent2.copy(alpha = 0.35f), r, c, style = Stroke(1.2.dp.toPx()))

        // Дуга и метки
        val a = from?.let { vec(it.first, it.second) }
        val b = to?.let { vec(it.first, it.second) }
        if (a != null && b != null) {
            val steps = 48
            var prev: Offset? = null
            for (k in 0..steps) {
                val tt = k / steps.toFloat()
                val v = slerp(a, b, tt)
                val lift = 1f + 0.18f * sin(tt * PI.toFloat())
                p.project(v, o)
                val visible = o[2] > -0.15f
                val pt = Offset(c.x + o[0] * r * lift, c.y - o[1] * r * lift)
                if (prev != null && visible) {
                    drawLine(
                        if (connected) androidx.compose.ui.graphics.lerp(accent, accent2, tt) else dot.copy(alpha = 0.4f),
                        prev, pt, (if (connected) 2.5f else 1.5f) * density, cap = StrokeCap.Round,
                    )
                }
                prev = if (visible) pt else null
            }
            if ((connected || busy) && live) {
                val v = slerp(a, b, phase)
                p.project(v, o)
                val lift = 1f + 0.18f * sin(phase * PI.toFloat())
                if (o[2] > -0.15f) {
                    val pt = Offset(c.x + o[0] * r * lift, c.y - o[1] * r * lift)
                    drawCircle(Brush.radialGradient(listOf(Color.White, accent2.copy(alpha = 0.5f), Color.Transparent), pt, 8.dp.toPx()), 8.dp.toPx(), pt)
                }
            }
            globePin(p, b, c, r, if (connected) accent2 else dot.copy(alpha = 0.7f), connected && live, phase)
        }
        if (a != null) globePin(p, a, c, r, accent, false, phase)
    }
}

private fun DrawScope.globePin(p: Projector, v: FloatArray, c: Offset, r: Float, color: Color, rings: Boolean, phase: Float) {
    val o = FloatArray(3)
    p.project(v, o)
    if (o[2] <= 0f) return
    val pt = Offset(c.x + o[0] * r, c.y - o[1] * r)
    if (rings) {
        drawCircle(color.copy(alpha = (1f - phase) * 0.6f), 4.dp.toPx() + phase * 16.dp.toPx(), pt, style = Stroke(1.5.dp.toPx()))
    }
    drawCircle(color.copy(alpha = 0.25f), 7.dp.toPx(), pt)
    drawCircle(color, 4.dp.toPx(), pt)
    drawCircle(Color.White, 1.6.dp.toPx(), pt)
}

/** Главный экран «Глобус»: как «Карта», но на вращающемся шаре. */
@Composable
fun GlobeCard(status: VpnStatus, serverCountry: String?, serverTitle: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val me = remember { userCountry(context) }
    val from = WorldMap.coords[me] ?: WorldMap.coords.getValue("ru")
    val to = serverCountry?.let { WorldMap.coords[it] }
    val connected = status == VpnStatus.Started
    Column(modifier.fillMaxWidth().apexCard().padding(14.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f), contentAlignment = Alignment.Center) {
            Globe(
                Modifier.fillMaxWidth().aspectRatio(1.25f),
                from = from,
                to = to,
                connected = connected,
                busy = status == VpnStatus.Starting || status == VpnStatus.Stopping,
                spin = to == null,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(countryName(me), color = Apex.TextDim, fontSize = 13.sp)
            Text("  →  ", color = Apex.TextDim, fontSize = 13.sp)
            Text(serverTitle, color = Apex.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Suppress("unused")
private fun norm(v: FloatArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
