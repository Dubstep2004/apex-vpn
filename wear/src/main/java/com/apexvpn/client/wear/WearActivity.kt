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

    private var state by mutableStateOf(PhoneState())
    private var linked by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WearScreen(state, linked, onToggle = { send("/apex/toggle") })
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

@Composable
private fun WearScreen(s: PhoneState, linked: Boolean, onToggle: () -> Unit) {
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
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0A0A0D)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("APEX Watch", color = Color(0xFF8E8E9A), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
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
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (s.server.isNotEmpty()) {
            Text(s.server, color = Color(0xFF8E8E9A), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        if (on && s.ping > 0) {
            Text("${s.ping} мс · ${s.down}", color = Color(0xFF5BD69A), fontSize = 12.sp)
        }
    }
}
