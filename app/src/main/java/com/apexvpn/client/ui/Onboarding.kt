package com.apexvpn.client.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.vpn.VpnStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class Slide(val title: String, val text: String)

private val slides = listOf(
    Slide("Защита одной кнопкой", "Нажмите на щит — APEX сам выберет самый быстрый сервер. Свайп по щиту — другой сервер."),
    Slide("Умная маршрутизация", "Российские сайты и банки идут напрямую, остальное — через VPN. Свои списки и готовые шаблоны — в один тап."),
    Slide("Весь мир рядом", "Карта и 3D-глобус, тест скорости, сервер под задачу — игры, видео или звонки. 10 дизайнов и любой свой цвет."),
)

/**
 * 320. Знакомство при первом запуске: три экрана с анимацией.
 * Листаются пальцем; «Пропустить» — сразу к добавлению подписки.
 */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Пропустить", color = Apex.TextDim) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            // Параллакс: картинка и текст сдвигаются с разной скоростью
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(280.dp)
                        .graphicsLayer {
                            translationX = offset * size.width * 0.35f
                            alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f) * 0.8f
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when (page) {
                        0 -> ShieldDemo()
                        1 -> RoutesDemo()
                        else -> Globe(
                            Modifier.size(260.dp),
                            from = WorldMap.coords["ru"],
                            to = WorldMap.coords["nl"],
                            connected = true,
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    slides[page].title,
                    color = Apex.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { translationX = offset * size.width * 0.15f },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    slides[page].text,
                    color = Apex.TextDim, fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { translationX = offset * size.width * 0.08f },
                )
            }
        }
        // Точки и кнопка
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(slides.size) { i ->
                val w by animateDpAsState(if (pager.currentPage == i) 26.dp else 8.dp, label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(w)
                        .clip(CircleShape)
                        .background(if (pager.currentPage == i) Apex.Accent else Apex.SurfaceHigh),
                )
            }
        }
        val last = pager.currentPage == slides.lastIndex
        Button(
            onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
        ) { Text(if (last) "Начать" else "Дальше", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(16.dp))
    }
}

/** Щит сам «подключается» по кругу: выключен → подключение → защищено. */
@Composable
private fun ShieldDemo() {
    var status by remember { mutableStateOf(VpnStatus.Stopped) }
    LaunchedEffect(Unit) {
        while (true) {
            status = VpnStatus.Stopped; delay(1200)
            status = VpnStatus.Starting; delay(1400)
            status = VpnStatus.Started; delay(2600)
        }
    }
    ShieldButton(status, onClick = {}, diameter = 240.dp)
}

/** Две дорожки: «🇷🇺 → напрямую» и «🌍 → через VPN», по ним бегут точки. */
@Composable
private fun RoutesDemo() {
    val t = rememberInfiniteTransition(label = "routes")
    val run by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "run")
    val good = Apex.Good
    val accent = Apex.Accent
    val cyan = Apex.Cyan
    val dim = Apex.TextDim
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        listOf(
            Triple("🇷🇺", "Напрямую", false),
            Triple("🌍", "Через VPN", true),
        ).forEach { (glyph, title, vpn) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Apex.Surface), contentAlignment = Alignment.Center) {
                    Text(glyph, fontSize = 28.sp)
                }
                Canvas(Modifier.weight(1f).height(56.dp).padding(horizontal = 8.dp)) {
                    val y = size.height / 2
                    val color = if (vpn) accent else good
                    if (vpn) {
                        // Дорожка через «тоннель»
                        drawLine(Brush.horizontalGradient(listOf(accent, cyan)), Offset(0f, y), Offset(size.width, y), 6.dp.toPx(), cap = StrokeCap.Round)
                    } else {
                        drawLine(dim.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                    }
                    for (k in 0..2) {
                        val f = (run + k / 3f) % 1f
                        drawCircle(Color.White, 5.dp.toPx(), Offset(f * size.width, y))
                        drawCircle(color.copy(alpha = 0.35f), 10.dp.toPx(), Offset(f * size.width, y))
                    }
                }
                Text(title, color = if (vpn) Apex.AccentText else good, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
            }
        }
    }
}
