package com.apexvpn.client.data

import com.apexvpn.client.vpn.NetworkMonitor
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import io.nekohasekai.apexcore.Apexcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder

/**
 * Замер задержки — настоящий, через сам протокол сервера (как «Проверить пинг» в Happ).
 * Работает и для UDP (Hysteria, TUIC, WireGuard): раньше там было «UDP».
 *  • VPN включён — спрашиваем работающее ядро (Clash API на 127.0.0.1).
 *  • VPN выключен — временное ядро замера (Apexcore.urlTest).
 *  • Серверы только для Xray при выключенном VPN — время TCP-соединения.
 */
object Pinger {
    private const val TIMEOUT_MS = 5000
    private const val TEST_URL = "https://www.gstatic.com/generate_204"

    const val UNREACHABLE = -1

    /** tag → задержка в мс или UNREACHABLE */
    suspend fun pingAll(nodes: List<Node>): Map<String, Int> = withContext(Dispatchers.IO) {
        if (nodes.isEmpty()) return@withContext emptyMap()
        val running = VpnState.status.value == VpnStatus.Started
        if (running) {
            val viaCore = runCatching { viaClashApi(nodes) }.getOrNull()
            if (viaCore != null) return@withContext viaCore
        }
        val result = mutableMapOf<String, Int>()
        runCatching {
            val json = JSONObject(Apexcore.urlTest(Prefs.nodesJson, TIMEOUT_MS))
            json.keys().forEach { k -> result[k] = json.getInt(k) }
        }
        // Остальное (Xray-серверы при выключенном VPN) — TCP-соединение мимо туннеля
        val rest = nodes.filter { it.tag !in result }
        val limit = Semaphore(8)
        rest.map { node -> async { limit.withPermit { node.tag to tcpPing(node.server, node.port) } } }
            .awaitAll().toMap(result)
        result
    }

    private fun clashGet(path: String, readTimeout: Int): Pair<Int, String> {
        val conn = URL("http://127.0.0.1:$CLASH_API_PORT$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 2000
        conn.readTimeout = readTimeout
        conn.setRequestProperty("Authorization", "Bearer ${Prefs.xrayAuth}")
        return try {
            val code = conn.responseCode
            code to (if (code == 200) conn.inputStream.bufferedReader().readText() else "")
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun viaClashApi(nodes: List<Node>): Map<String, Int> = withContext(Dispatchers.IO) {
        // Ядро без Clash API (старый конфиг) — пусть замер сделает временное ядро
        check(clashGet("/version", 2000).first == 200) { "clash api недоступен" }
        val limit = Semaphore(10)
        nodes.map { node ->
            async {
                limit.withPermit {
                    node.tag to runCatching {
                        val (code, body) = clashGet(
                            "/proxies/${URLEncoder.encode(node.tag, "UTF-8")}/delay" +
                                "?timeout=$TIMEOUT_MS&url=${URLEncoder.encode(TEST_URL, "UTF-8")}",
                            TIMEOUT_MS + 2000,
                        )
                        if (code == 200) JSONObject(body).optInt("delay", UNREACHABLE) else UNREACHABLE
                    }.getOrDefault(UNREACHABLE)
                }
            }
        }.awaitAll().toMap()
    }

    private fun tcpPing(host: String, port: Int): Int {
        if (port !in 1..65535) return UNREACHABLE
        return runCatching {
            Socket().use { socket ->
                // Если VPN включён, привязываем сокет к реальной сети, иначе замер пойдёт через туннель
                NetworkMonitor.defaultNetwork?.bindSocket(socket)
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(host, port), 3000)
                ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
            }
        }.getOrDefault(UNREACHABLE)
    }
}
