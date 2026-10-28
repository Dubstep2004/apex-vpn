package com.apexvpn.client.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Авто-выбор DNS: меряем, как быстро отвечают Cloudflare, Google, Quad9, Mullvad, OpenDNS и Control D по DNS-over-HTTPS
 * (если VPN включён — через VPN, как ходит и сам DNS), и берём самый быстрый.
 * AdGuard, Яндекс, Comss и NextDNS в выбор не входят: они меняют поведение (фильтры, геолокация), а не только скорость.
 */
object DnsAuto {
    data class Result(val id: String, val ms: Int)

    private val candidates = listOf(
        DnsChoice.Cloudflare to "https://1.1.1.1/dns-query",
        DnsChoice.Google to "https://dns.google/dns-query",
        DnsChoice.Quad9 to "https://dns.quad9.net/dns-query",
        DnsChoice.Mullvad to "https://dns.mullvad.net/dns-query",
        DnsChoice.OpenDns to "https://doh.opendns.com/dns-query",
        DnsChoice.ControlD to "https://freedns.controld.com/p0",
    )

    private val _results = MutableStateFlow<List<Result>>(emptyList())
    val results = _results.asStateFlow()

    /** Текущий выбор (id для ядра); по умолчанию — Cloudflare. */
    val best: String get() = Prefs.autoDnsBest.ifBlank { DnsChoice.Cloudflare.id }

    /** Замер; возвращает true, если лучший DNS сменился. */
    suspend fun measure(): Boolean = withContext(Dispatchers.IO) {
        val res = coroutineScope {
            candidates.map { (c, url) -> async { Result(c.id, probe(url)) } }.awaitAll()
        }.sortedBy { if (it.ms < 0) Int.MAX_VALUE else it.ms }
        _results.value = res
        val win = res.firstOrNull { it.ms >= 0 } ?: return@withContext false
        val old = res.firstOrNull { it.id == best }?.ms ?: -1
        // Меняем, только если новый заметно быстрее — чтобы DNS не «прыгал»
        val change = win.id != best && (old < 0 || old - win.ms > 25)
        if (change) Prefs.autoDnsBest = win.id
        change
    }

    /** Лучшее из трёх запросов, мс; -1 — не ответил. */
    private fun probe(url: String): Int {
        val q = Base64.encodeToString(query(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        var bestMs = -1
        repeat(3) {
            runCatching {
                val t = System.nanoTime()
                val c = URL("$url?dns=$q").openConnection() as HttpURLConnection
                c.connectTimeout = 3000
                c.readTimeout = 3000
                c.setRequestProperty("Accept", "application/dns-message")
                try {
                    if (c.responseCode == 200) {
                        c.inputStream.use { it.readBytes() }
                        val ms = ((System.nanoTime() - t) / 1_000_000).toInt()
                        if (bestMs < 0 || ms < bestMs) bestMs = ms
                    }
                } finally {
                    c.disconnect()
                }
            }
        }
        return bestMs
    }

    /** DNS-запрос A для www.example.com в формате RFC 1035. */
    private fun query(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in "www.example.com".split('.')) {
            out.write(label.length)
            out.write(label.toByteArray())
        }
        out.write(byteArrayOf(0, 0, 1, 0, 1))
        return out.toByteArray()
    }
}
