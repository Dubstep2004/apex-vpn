package com.apexvpn.client.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.ui.theme.Apex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Заставка при запуске: логотип «выстреливает» вверх, вспыхивает свечение, появляется название. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.4f) }
    val rise = remember { Animatable(40f) }
    val glow = remember { Animatable(0f) }
    val spin = remember { Animatable(-25f) }
    val text = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
        launch { rise.animateTo(0f, tween(600, easing = FastOutSlowInEasing)) }
        launch { spin.animateTo(0f, tween(700, easing = FastOutSlowInEasing)) }
        launch {
            delay(250)
            glow.animateTo(1f, tween(450))
            glow.animateTo(0.55f, tween(500))
        }
        launch {
            delay(420)
            text.animateTo(1f, tween(450))
        }
        delay(1350)
        onFinished()
    }

    Box(Modifier.fillMaxSize().background(Apex.Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(240.dp)
                        .alpha(glow.value)
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(listOf(Apex.Accent.copy(alpha = 0.55f), Color.Transparent)),
                                radius = size.minDimension / 2,
                            )
                        },
                )
                ApexLogo(
                    120.dp,
                    Modifier
                        .scale(scale.value)
                        .rotate(spin.value)
                        .offset(y = rise.value.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "APEX VPN",
                color = Apex.Text,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                modifier = Modifier.alpha(text.value).offset(y = ((1f - text.value) * 12f).dp),
            )
        }
    }
}
