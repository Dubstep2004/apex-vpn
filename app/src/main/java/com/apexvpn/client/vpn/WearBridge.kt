package com.apexvpn.client.vpn

import android.content.Context
import android.util.Log
import com.apexvpn.client.data.AUTO_TAG
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.data.Subscription
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * 11. Связь с часами Wear OS (модуль wear). Телефон публикует состояние в /apex/status,
 * часы присылают /apex/toggle. Без Google Play Services просто ничего не делает.
 */
object WearBridge {
    /** Часы откликались недавно — тогда сервис присылает им скорость и пинг. */
    @Volatile
    var connected = false
        private set
    private var lastHello = 0L
    private var lastPayload = ""

    fun markSeen() {
        connected = true
        lastHello = System.currentTimeMillis()
    }

    fun publish(context: Context) {
        if (connected && System.currentTimeMillis() - lastHello > 10 * 60_000) connected = false
        runCatching {
            val status = VpnState.status.value.name.lowercase()
            val sub = Subscription.state.value
            val server = if (sub.selectedTag == AUTO_TAG) "Автопилот" else
                sub.nodes.find { it.tag == sub.selectedTag }?.name?.let { ServerInfo.title(it) } ?: ""
            val ping = VpnState.ping.value
            val down = formatSpeed(VpnState.speed.value.first)
            val payload = "$status|$server|$ping|$down"
            if (payload == lastPayload) return
            lastPayload = payload
            val req = PutDataMapRequest.create("/apex/status").apply {
                dataMap.putString("status", status)
                dataMap.putString("server", server)
                dataMap.putInt("ping", ping)
                dataMap.putString("down", down)
                dataMap.putLong("t", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }.onFailure { Log.d("ApexWear", "no wear: ${it.message}") }
    }
}

/** Сообщения от часов. */
class WearListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        WearBridge.markSeen()
        when (event.path) {
            "/apex/toggle" -> runCatching { VpnToggle.toggle(applicationContext) }
                .onFailure { Log.w("ApexWear", "toggle failed", it) }
            "/apex/hello" -> Unit
        }
        WearBridge.publish(applicationContext)
    }
}
