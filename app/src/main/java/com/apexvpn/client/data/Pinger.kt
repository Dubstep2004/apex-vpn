package com.apexvpn.client.data

import com.apexvpn.client.vpn.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** Замер задержки до серверов: время установки TCP-соединения, мимо VPN. */
object Pinger {
    private const val TIMEOUT_MS = 3000

    const val UNREACHABLE = -1
    const val NOT_APPLICABLE = -2 // UDP-протоколы (Hysteria, TUIC, WireGuard) по TCP не измерить

    /** tag → задержка в мс, UNREACHABLE или NOT_APPLICABLE */
    suspend fun pingAll(nodes: List<Node>): Map<String, Int> = withContext(Dispatchers.IO) {
        val limit = Semaphore(8)
        nodes.map { node ->
            async {
                if (node.udp) {
                    node.tag to NOT_APPLICABLE
                } else {
                    limit.withPermit { node.tag to ping(node.server, node.port) }
                }
            }
        }.awaitAll().toMap()
    }

    private fun ping(host: String, port: Int): Int {
        if (port !in 1..65535) return -1
        return runCatching {
            Socket().use { socket ->
                // Если VPN включён, привязываем сокет к реальной сети, иначе замер пойдёт через туннель
                NetworkMonitor.defaultNetwork?.bindSocket(socket)
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
            }
        }.getOrDefault(-1)
    }
}
