package com.apexvpn.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.ui.theme.Apex

/** Пинг серверов: тип, режим «через прокси», тайм-аут, адрес проверки и вид результата. */
@Composable
internal fun PingSettingsPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    PageScaffold("Пинг", onBack) {
        SectionLabel("Тип пинга")
        Group {
            val types = listOf(
                Triple("tcp", "TCP — до сервера", "Время соединения с самим сервером (для Hysteria — QUIC). Быстро и честно, как в других клиентах"),
                Triple("get", "Через прокси · GET", "Запрос к сайту через сервер — показывает, что интернет через него правда работает"),
                Triple("head", "Через прокси · HEAD", "То же, но без тела ответа — чуть быстрее и экономнее"),
                Triple("icmp", "ICMP", "Классический ping. Некоторые серверы и операторы его не пропускают"),
            )
            types.forEachIndexed { i, (id, title, sub) ->
                if (i > 0) GroupDivider()
                ListRow(title, sub, leading = { RadioMark(s.pingType == id) }, onClick = {
                    viewModel.change(reload = false) { copy(pingType = id) }
                    viewModel.pingAll()
                })
            }
        }
        if (s.pingType == "get" || s.pingType == "head") {
            Caption("Через прокси меряется только при включённом VPN. Без VPN — пинг до сервера (TCP).")
        }

        SectionLabel("Режим (через прокси)")
        Group {
            val modes = listOf(
                Triple("default", "Обычный", "Один запрос: подключение, шифрование и ответ"),
                Triple("double", "Double", "Два запроса с новым соединением, берётся лучший — второй быстрее за счёт DNS"),
                Triple("keepalive", "KeepAlive", "Два запроса по одному соединению — второй показывает чистое время ответа через сервер"),
            )
            modes.forEachIndexed { i, (id, title, sub) ->
                if (i > 0) GroupDivider()
                ListRow(title, sub, leading = { RadioMark(s.pingMode == id) }, onClick = {
                    viewModel.change(reload = false) { copy(pingMode = id) }
                })
            }
        }

        SectionLabel("Тайм-аут пинга через прокси", "${s.pingTimeout} сек")
        var t by remember(s.pingTimeout) { mutableFloatStateOf(s.pingTimeout.toFloat()) }
        Column(Modifier.fillMaxWidth().apexCard().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Slider(
                value = t,
                onValueChange = { t = it },
                onValueChangeFinished = { viewModel.change(reload = false) { copy(pingTimeout = t.toInt()) } },
                valueRange = 5f..15f,
                steps = 9,
                colors = SliderDefaults.colors(thumbColor = Apex.Accent, activeTrackColor = Apex.Accent, inactiveTrackColor = Apex.SurfaceHigh),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("5 сек", color = Apex.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${t.toInt()} сек", color = Apex.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("15 сек", color = Apex.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }

        SectionLabel("Адрес проверки (URL-тест)")
        var url by remember { mutableStateOf(s.pingUrl) }
        var err by remember { mutableStateOf<String?>(null) }
        PlainField(url, "https://www.gstatic.com/generate_204") { url = it.trim(); err = null }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                if (url.isNotEmpty() && !url.startsWith("https://") && !url.startsWith("http://")) {
                    err = "Адрес должен начинаться с https:// или http://"
                } else {
                    // Адрес нужен и автопилоту — ядро перезагрузится
                    viewModel.change { copy(pingUrl = url) }
                    viewModel.showMessage(if (url.isEmpty()) "Адрес проверки — по умолчанию" else "Адрес проверки сохранён")
                }
            }) { Text("Сохранить", color = Apex.AccentText) }
            if (s.pingUrl.isNotEmpty()) {
                TextButton(onClick = { url = ""; viewModel.change { copy(pingUrl = "") } }) { Text("По умолчанию", color = Apex.TextDim) }
            }
        }
        err?.let { Text(it, color = Apex.Bad, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp)) }
        Caption("По этому адресу проверяют серверы пинг «через прокси» и автопилот. По умолчанию — https://www.gstatic.com/generate_204. Адрес должен отвечать быстро и надёжно.")

        SectionLabel("Результат пинга")
        Group {
            val views = listOf("time" to "Время (мс)", "bars" to "Столбики", "both" to "Время и столбики")
            views.forEachIndexed { i, (id, title) ->
                if (i > 0) GroupDivider()
                ListRow(title, leading = { RadioMark(s.pingView == id) }, onClick = { viewModel.change(reload = false) { copy(pingView = id) } }) {
                    PingResult(84, id)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RadioMark(on: Boolean) {
    Box(
        Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (on) Apex.Accent else Apex.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Apex.OnAccent))
    }
}

/** Пинг в выбранном виде: «84 мс», столбики сигнала или вместе. */
@Composable
internal fun PingResult(ms: Int, view: String, text: String? = null) {
    val color = pingColor(ms)
    val bars = when {
        ms < 0 -> 0
        ms < 120 -> 3
        ms < 300 -> 2
        else -> 1
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (view != "time") {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 1..3) {
                    Box(
                        Modifier.width(4.dp).height((5 + i * 4).dp).clip(RoundedCornerShape(1.5.dp))
                            .background(if (i <= bars) color else color.copy(alpha = 0.22f)),
                    )
                }
            }
        }
        if (view != "bars") {
            if (view == "both") Spacer(Modifier.width(6.dp))
            Text(text ?: if (ms >= 0) "$ms мс" else "нет ответа", color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
