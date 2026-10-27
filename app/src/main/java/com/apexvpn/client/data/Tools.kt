package com.apexvpn.client.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

// ─── 5. Тест скорости ───────────────────────────────────────────────────

data class SpeedResult(
    val pingMs: Int = -1,
    val downMbps: Double = -1.0,
    val upMbps: Double = -1.0,
)

/** Замер через серверы Cloudflare. Если VPN включён — меряется скорость через VPN. */
object SpeedTest {
    private const val BASE = "https://speed.cloudflare.com"

    suspend fun ping(): Int = withContext(Dispatchers.IO) {
        val samples = (1..5).mapNotNull {
            runCatching {
                val c = open("$BASE/__down?bytes=0")
                val t = System.nanoTime()
                try {
                    c.inputStream.use { it.readBytes() }
                } finally {
                    c.disconnect()
                }
                ((System.nanoTime() - t) / 1_000_000).toInt()
            }.getOrNull()
        }
        // Первый запрос включает установку соединения — берём лучший
        samples.minOrNull() ?: -1
    }

    /** Загрузка до 10 секунд. [progress] получает текущую скорость в Мбит/с. */
    suspend fun download(progress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val c = open("$BASE/__down?bytes=50000000")
        var bytes = 0L
        val start = System.nanoTime()
        try {
            c.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var lastReport = start
                while (true) {
                    ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    bytes += n
                    val now = System.nanoTime()
                    if (now - lastReport > 250_000_000) {
                        progress(mbps(bytes, now - start)); lastReport = now
                    }
                    if (now - start > 10_000_000_000L) break
                }
            }
        } finally {
            c.disconnect()
        }
        mbps(bytes, System.nanoTime() - start)
    }

    /** Отдача: несколько POST по 4 МБ, до 8 секунд. */
    suspend fun upload(progress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val chunk = Random.nextBytes(4 * 1024 * 1024)
        var bytes = 0L
        val start = System.nanoTime()
        while (System.nanoTime() - start < 8_000_000_000L) {
            ensureActive()
            val c = open("$BASE/__up")
            try {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setFixedLengthStreamingMode(chunk.size)
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.outputStream.use { out ->
                    var off = 0
                    while (off < chunk.size) {
                        val n = minOf(64 * 1024, chunk.size - off)
                        out.write(chunk, off, n)
                        off += n
                        bytes += n
                        progress(mbps(bytes, System.nanoTime() - start))
                    }
                }
                c.responseCode
            } finally {
                c.disconnect()
            }
        }
        mbps(bytes, System.nanoTime() - start)
    }

    private fun mbps(bytes: Long, nanos: Long): Double =
        if (nanos <= 0) 0.0 else bytes * 8.0 / (nanos / 1e9) / 1_000_000.0

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 15000
        useCaches = false
        setRequestProperty("Cache-Control", "no-cache")
    }
}

// ─── 49. Живой пинг ─────────────────────────────────────────────────────

object LivePing {
    /** Задержка HTTPS-запроса (через VPN, если он включён); -1 — нет ответа. */
    suspend fun measure(): Int = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://www.gstatic.com/generate_204").openConnection() as HttpURLConnection
            c.connectTimeout = 4000
            c.readTimeout = 4000
            c.useCaches = false
            val t = System.nanoTime()
            try {
                c.responseCode
            } finally {
                c.disconnect()
            }
            ((System.nanoTime() - t) / 1_000_000).toInt()
        }.getOrDefault(-1)
    }
}

// ─── 6. Список приложений ───────────────────────────────────────────────

data class AppInfo(val packageName: String, val label: String, val icon: Drawable?)

object InstalledApps {
    suspend fun load(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        activities
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { AppInfo(it.packageName, it.loadLabel(pm).toString(), runCatching { it.loadIcon(pm) }.getOrNull()) }
            .sortedBy { it.label.lowercase() }
    }
}
