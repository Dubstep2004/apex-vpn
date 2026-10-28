package com.apexvpn.client.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.apexvpn.client.ui.theme.Apex

/**
 * 94. Анимированные флаги: флаг колышется на ветру (Android 13+ — шейдер),
 * на старых телефонах по флагу пробегает блик. Одни «часы» на весь список —
 * анимация не пересобирает экран, только перерисовывает флаги.
 */
val LocalFlagClock = compositionLocalOf<State<Float>?> { null }

/** Запускает общие «часы» флагов для [content], если анимация включена. */
@Composable
fun ProvideFlagClock(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled || Apex.feel.smooth) {
        CompositionLocalProvider(LocalFlagClock provides null, content = content)
        return
    }
    val t = rememberInfiniteTransition(label = "flags")
    val clock = t.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "clock")
    CompositionLocalProvider(LocalFlagClock provides clock, content = content)
}

private const val WAVE = """
uniform shader content;
uniform float2 size;
uniform float time;
half4 main(float2 p) {
    float2 uv = p / size;
    float phase = uv.x * 7.5 - time * 6.2831;
    float amp = size.y * 0.06 * (0.35 + uv.x);
    float2 q = float2(p.x, p.y + sin(phase) * amp);
    half4 c = content.eval(q);
    float shade = 0.9 + 0.16 * cos(phase);
    return half4(c.rgb * shade, c.a);
}
"""

/** Модификатор для картинки флага. [phase] — сдвиг, чтобы флаги в списке колыхались не синхронно. */
@Composable
fun Modifier.animatedFlag(phase: Float = 0f): Modifier {
    val clock = LocalFlagClock.current ?: return this
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(WAVE) }
        this.waveShader(shader, clock, phase)
    } else {
        this.drawWithContent {
            drawContent()
            val t = (clock.value + phase) % 1f
            val x = -size.width + t * size.width * 3f
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + size.width * 0.7f, size.height),
                ),
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.waveShader(shader: RuntimeShader, clock: State<Float>, phase: Float): Modifier =
    this.graphicsLayer {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("time", (clock.value + phase) % 1f)
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
