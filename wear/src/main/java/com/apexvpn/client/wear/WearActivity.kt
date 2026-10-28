package com.apexvpn.client.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ScalingLazyColumn
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Состояние с телефона: путь /apex/status в Wearable Data Layer. */
data class PhoneState(val status: String = "", val server: String = "", val ping: Int = 0, val down: String = "")

class WearActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    /** На часах обычно нет браузера — ссылка открывается на сопряжённом телефоне. */
    private fun openOnPhone(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        val helper = androidx.wear.remote.interactions.RemoteActivityHelper(this, java.util.concurrent.Executors.newSingleThreadExecutor())
        runCatching { helper.startRemoteActivity(intent, null) }
            .onSuccess { android.widget.Toast.makeText(this, "Открыто на телефоне", android.widget.Toast.LENGTH_SHORT).show() }
            .onFailure { android.widget.Toast.makeText(this, "Не удалось открыть на телефоне", android.widget.Toast.LENGTH_SHORT).show() }
    }


    private var state by mutableStateOf(PhoneState())
    private var linked by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Защита: APEX Watch — только для часов. Если его как-то поставили на телефон — объясняем, что делать
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WATCH)) {
            setContent {
                MaterialTheme {
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("APEX Watch — приложение для часов", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "На телефон нужно установить apex-vpn-<версия>.apk со страницы github.com/Dubstep2004/apex-vpn/releases",
                            color = Color(0xFF8E8E9A), fontSize = 14.sp, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                runCatching {
                                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Dubstep2004/apex-vpn/releases/latest")))
                                }
                            },
                            modifier = Modifier.size(width = 200.dp, height = 48.dp),
                        ) { Text("Скачать APEX VPN") }
                    }
                }
            }
            return
        }
        setContent {
            MaterialTheme {
                WearScreen(state, linked, onToggle = { send("/apex/toggle") }, onOpenLink = { openOnPhone(it) })
            }
            LaunchedEffect(Unit) { loadCurrent() }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        send("/apex/hello") // телефон пришлёт свежее состояние
    }

    override fun onPause() {
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == "/apex/status") {
                state = parse(DataMapItem.fromDataItem(e.dataItem).dataMap)
            }
        }
    }

    private fun parse(m: com.google.android.gms.wearable.DataMap) = PhoneState(
        status = m.getString("status", ""),
        server = m.getString("server", ""),
        ping = m.getInt("ping", 0),
        down = m.getString("down", ""),
    )

    private suspend fun loadCurrent() {
        runCatching {
            val items = Wearable.getDataClient(this).dataItems.await()
            items.forEach { if (it.uri.path == "/apex/status") state = parse(DataMapItem.fromDataItem(it).dataMap) }
            items.release()
        }
    }

    private fun send(path: String) {
        val activity = this
        kotlinx.coroutines.MainScope().launch {
            runCatching {
                val nodes = Wearable.getNodeClient(activity).connectedNodes.await()
                linked = nodes.isNotEmpty()
                nodes.forEach { Wearable.getMessageClient(activity).sendMessage(it.id, path, ByteArray(0)).await() }
            }.onFailure { linked = false }
        }
    }
}

/** Наши ссылки. Telegram пустой — кнопка не показывается (появится, когда будет канал). */
object Links {
    const val GITHUB = "https://github.com/Dubstep2004/apex-vpn"
    const val TELEGRAM = ""
}

@Composable
private fun WearScreen(s: PhoneState, linked: Boolean, onToggle: () -> Unit, onOpenLink: (String) -> Unit) {
    val on = s.status == "started"
    val label = when (s.status) {
        "started" -> "Защищено"
        "starting" -> "Подключение…"
        "stopping" -> "Отключение…"
        "paused" -> "Пауза"
        "blocked" -> "Блокировка"
        "stopped" -> "Выключен"
        else -> if (linked) "Нет данных" else "Нет связи с телефоном"
    }
    // Список с «изгибом» под круглый экран: сверху щит, ниже — ссылки (крутите колёсико или листайте)
    ScalingLazyColumn(
        Modifier.fillMaxSize().background(Color(0xFF0A0A0D)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Text("APEX Watch", color = Color(0xFF8E8E9A), fontSize = 12.sp) }
        item {
            Button(
                onClick = onToggle,
                modifier = Modifier.size(76.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
            ) {
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            if (on) Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF22D3EE)))
                            else Brush.linearGradient(listOf(Color(0xFF26262E), Color(0xFF3A3A44))),
                        ),
                    contentAlignment = Alignment.Center,
                ) { Text("⏻", color = Color.White, fontSize = 30.sp) }
            }
        }
        item { Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        if (s.server.isNotEmpty()) {
            item { Text(s.server, color = Color(0xFF8E8E9A), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
        }
        if (on && s.ping > 0) {
            item { Text("${s.ping} мс · ${s.down}", color = Color(0xFF5BD69A), fontSize = 12.sp) }
        }
        // Наши ссылки: открываются на телефоне
        item { Spacer(Modifier.height(8.dp)) }
        item { LinkChip("GitHub", "Исходный код и версии") { onOpenLink(Links.GITHUB) } }
        if (Links.TELEGRAM.isNotEmpty()) {
            item { LinkChip("Telegram", "Наш канал") { onOpenLink(Links.TELEGRAM) } }
        }
    }
}

@Composable
private fun LinkChip(title: String, subtitle: String, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(title, fontWeight = FontWeight.SemiBold) },
        secondaryLabel = { Text(subtitle, color = Color(0xFF8E8E9A)) },
        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1C1C24), contentColor = Color.White),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
