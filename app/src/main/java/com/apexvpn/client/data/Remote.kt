package com.apexvpn.client.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.apexvpn.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Общий HTTP-клиент для служебных запросов к вашему серверу. */
internal object Http {
    fun open(url: String, timeout: Int = 15_000): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout
            readTimeout = timeout
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "APEX VPN/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})")
            setRequestProperty("Cache-Control", "no-cache")
        }

    fun getText(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

// ─── 59. Обновление баз российских сайтов и рекламы ─────────────────────

object RulesUpdater {
    val FILES = listOf(
        "geoip-ru.srs", "geosite-category-ru.srs", "geosite-category-ads-all.srs",
        // 15. Семейный режим
        "geosite-category-porn.srs", "geosite-category-betting-ru.srs",
    )
    private const val WEEK = 7L * 24 * 3600 * 1000

    /** Откуда качать: ваш сервер (RULES_BASE_URL) или зеркало официальных баз sing-box. */
    fun urlFor(name: String): String {
        // Свой источник из «Для опытных» важнее заданного при сборке
        val base = AppSettings.value.rulesSource.trim().ifEmpty { BuildConfig.RULES_BASE_URL }.trimEnd('/')
        if (base.isNotEmpty()) return "$base/$name"
        val repo = if (name.startsWith("geoip")) "sing-geoip" else "sing-geosite"
        return "https://cdn.jsdelivr.net/gh/SagerNet/$repo@rule-set/$name"
    }

    fun remoteDir(context: Context) = File(context.filesDir, "rules-remote")

    /** Папка с базами для ядра: свежие скачанные, иначе встроенные в приложение. */
    fun activeDir(context: Context): String? {
        val remote = remoteDir(context)
        if (FILES.all { File(remote, it).length() > 0 }) return remote.path
        return installBundled(context)
    }

    /** Встроенные базы копируются один раз на версию приложения, а не при каждом подключении. */
    private fun installBundled(context: Context): String? = runCatching {
        val dir = File(context.filesDir, "rules").apply { mkdirs() }
        val stamp = File(dir, ".version")
        val current = BuildConfig.VERSION_CODE.toString()
        val ready = stamp.exists() && stamp.readText() == current && FILES.all { File(dir, it).length() > 0 }
        if (!ready) {
            for (name in FILES) {
                val tmp = File(dir, "$name.tmp")
                context.assets.open("rules/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                check(tmp.renameTo(File(dir, name))) { "не удалось записать $name" }
            }
            stamp.writeText(current)
        }
        dir.path
    }.getOrNull()

    fun lastUpdated(context: Context): Long =
        FILES.minOfOrNull { File(remoteDir(context), it).lastModified() } ?: 0L

    /** Скачивает базы раз в неделю (или сразу при [force]). true — обновлено. */
    suspend fun update(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - lastUpdated(context) < WEEK) return@withContext false
        val dir = remoteDir(context).apply { mkdirs() }
        val tmp = File(context.cacheDir, "rules-tmp").apply { deleteRecursively(); mkdirs() }
        runCatching {
            for (name in FILES) {
                val c = Http.open(urlFor(name), 30_000)
                try {
                    if (c.responseCode != 200) error("HTTP ${c.responseCode}")
                    c.inputStream.use { input -> File(tmp, name).outputStream().use { input.copyTo(it) } }
                } finally {
                    c.disconnect()
                }
                // В начале файла .srs — сигнатура «SRS»
                val head = File(tmp, name).inputStream().use { s -> ByteArray(3).also { s.read(it) } }
                check(String(head) == "SRS") { "повреждён $name" }
            }
            for (name in FILES) File(tmp, name).copyTo(File(dir, name), overwrite = true)
            true
        }.getOrDefault(false).also { tmp.deleteRecursively() }
    }
}

// ─── Отчёты о сбоях: хранятся на телефоне, уходят только в «Написать в поддержку» ─

object CrashReporter {
    private fun dir(context: Context) = File(context.filesDir, "crashes").apply { mkdirs() }

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                // Только техническая информация: без ссылки подписки, IP и списков сайтов
                val report = buildString {
                    appendLine("app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
                    appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("thread=${thread.name}")
                    appendLine()
                    append(error.stackTraceToString().take(20_000))
                }
                File(dir(app), "crash-${System.currentTimeMillis()}.txt").writeText(report)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Сбой ядра (Go) закрывает приложение мимо обработчика Java — его след остаётся только
     * в stderr.log. Если там panic/fatal error с прошлого запуска, сохраняем как отчёт.
     */
    fun collectNativeCrash(context: Context, stderr: File) {
        runCatching {
            if (!stderr.exists() || stderr.length() == 0L) return@runCatching
            val text = stderr.readText().takeLast(30_000)
            val start = listOf("panic:", "fatal error:", "SIGSEGV", "SIGABRT").map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()
                ?: return@runCatching
            val report = buildString {
                appendLine("app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("type=native (ядро)")
                appendLine()
                append(text.substring(start).take(20_000))
            }
            File(dir(context), "crash-native-${System.currentTimeMillis()}.txt").writeText(report)
        }
        runCatching { stderr.delete() }
    }

    /** Отчёты хранятся только на телефоне (для «Написать в поддержку»); старше 30 дней удаляются. */
    fun cleanup(context: Context) {
        dir(context).listFiles().orEmpty()
            .filter { System.currentTimeMillis() - it.lastModified() > 30L * 24 * 3600 * 1000 }
            .forEach { it.delete() }
    }
}
