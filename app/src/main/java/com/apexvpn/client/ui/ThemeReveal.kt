package com.apexvpn.client.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.apexvpn.client.ui.theme.Apex
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * 384. Смена темы «волной»: снимок экрана в старых цветах закрывает экран,
 * а от места нажатия растёт круг, в котором уже новые цвета.
 */
object ThemeReveal {
    internal var layer: GraphicsLayer? = null
    internal var lastTap = Offset.Unspecified
    internal var snapshot by mutableStateOf<ImageBitmap?>(null)
    internal var center by mutableStateOf(Offset.Zero)
    internal val progress = Animatable(0f)
    /**
     * Корутины из композиции: у них есть часы кадров. В обычном MainScope анимация
     * упала бы с ошибкой «MonotonicFrameClock is not available».
     */
    internal var scope: kotlinx.coroutines.CoroutineScope? = null
    private var running = false

    /** Применить смену оформления с волной (или сразу — в «Плавном режиме»). */
    fun run(apply: () -> Unit) {
        val l = layer
        val sc = scope
        if (l == null || sc == null || running || Apex.feel.smooth) {
            apply()
            return
        }
        running = true
        sc.launch {
            try {
                val bmp = runCatching { l.toImageBitmap() }.getOrNull()
                if (bmp == null) {
                    apply()
                    return@launch
                }
                center = if (lastTap.isSpecified) lastTap else Offset(bmp.width / 2f, bmp.height / 2f)
                progress.snapTo(0f)
                snapshot = bmp
                apply()
                progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            } finally {
                snapshot = null
                running = false
            }
        }
    }
}

/** Корень экрана: запоминает место нажатия и умеет делать снимок для волны. */
@Composable
fun ThemeRevealHost(content: @Composable BoxScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    ThemeReveal.layer = layer
    ThemeReveal.scope = androidx.compose.runtime.rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        e.changes.firstOrNull { it.pressed }?.let { ThemeReveal.lastTap = it.position }
                    }
                }
            }
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
    ) {
        content()
        val shot = ThemeReveal.snapshot
        if (shot != null) {
            Canvas(Modifier.fillMaxSize()) {
                val c = ThemeReveal.center
                val maxR = maxOf(
                    hypot(c.x, c.y), hypot(size.width - c.x, c.y),
                    hypot(c.x, size.height - c.y), hypot(size.width - c.x, size.height - c.y),
                )
                val r = maxR * ThemeReveal.progress.value
                val hole = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
                // Старые цвета — везде, кроме растущего круга
                clipPath(hole, ClipOp.Difference) { drawImage(shot) }
            }
        }
    }
}
