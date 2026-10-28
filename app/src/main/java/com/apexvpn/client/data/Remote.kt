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

// ─── 38. Новости и объявления ───────────────────────────────────────────

/**
 * news.json на вашем сервере (BuildConfig.NEWS_URL), объект или массив:
 * {"id":"2026-11-01","title":"Техработы","text":"С 02:00 до 03:00…","url":"https://…","level":"info|warning"}
 */
data class NewsItem(val id: String, val title: String, val text: String, val url: String?, val warning: Boolean)

object News {
    private lateinit var appContext: Context
    private val _current = MutableStateFlow<NewsItem?>(null)
    val current: StateFlow<NewsItem?> = _current.asStateFlow()

    private val sp by lazy { appContext.getSharedPreferences("news", Context.MODE_PRIVATE) }

    fun init(context: Context) {
        appContext = context.applicationContext
        sp.getString("cached", null)?.let { runCatching { pick(JSONArray(it)) } }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (BuildConfig.NEWS_URL.isBlank() || "example.com" in BuildConfig.NEWS_URL) return@withContext
        runCatching {
            val body = Http.getText(BuildConfig.NEWS_URL).trim()
            val arr = if (body.startsWith("[")) JSONArray(body) else JSONArray().put(JSONObject(body))
            sp.edit { putString("cached", arr.toString()) }
            pick(arr)
        }
    }

    fun dismiss() {
        val n = _current.value ?: return
        sp.edit { putStringSet("dismissed", (sp.getStringSet("dismissed", emptySet()) ?: emptySet()) + n.id) }
        _current.value = null
    }

    private fun pick(arr: JSONArray) {
        val dismissed = sp.getStringSet("dismissed", emptySet()) ?: emptySet()
        _current.value = (0 until arr.length()).asSequence()
            .map { arr.getJSONObject(it) }
            .map {
                NewsItem(
                    id = it.optString("id", it.optString("title")),
                    title = it.optString("title"),
                    text = it.optString("text"),
                    url = it.optString("url").takeIf { u -> u.startsWith("https://") },
                    warning = it.optString("level") == "warning",
                )
            }
            .firstOrNull { it.id.isNotBlank() && it.id !in dismissed && (it.title.isNotBlank() || it.text.isNotBlank()) }
    }
}

// ─── 59. Обновление баз российских сайтов и рекламы ─────────────────────

object RulesUpdater {
    val FILES = listOf("geoip-ru.srs", "geosite-category-ru.srs", "geosite-category-ads-all.srs")
    private const val WEEK = 7L * 24 * 3600 * 1000

    /** Откуда качать: ваш сервер (RULES_BASE_URL) или зеркало официальных баз sing-box. */
    private fun urlFor(name: String): String {
        val base = BuildConfig.RULES_BASE_URL.trimEnd('/')
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

// ─── 40. Анонимные отчёты о сбоях ───────────────────────────────────────

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

    suspend fun uploadPending(context: Context) = withContext(Dispatchers.IO) {
        val files = dir(context).listFiles().orEmpty()
        if (BuildConfig.CRASH_URL.isBlank() || !AppSettings.value.crashReports) {
            files.filter { System.currentTimeMillis() - it.lastModified() > 7L * 24 * 3600 * 1000 }.forEach { it.delete() }
            return@withContext
        }
        for (f in files.take(5)) {
            runCatching {
                val c = Http.open(BuildConfig.CRASH_URL)
                try {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                    val body = f.readBytes().let { if (it.size > 60_000) it.copyOf(60_000) else it }
                    c.setFixedLengthStreamingMode(body.size) // сервер ждёт Content-Length
                    c.outputStream.use { it.write(body) }
                    if (c.responseCode in 200..299) f.delete()
                } finally {
                    c.disconnect()
                }
            }
        }
    }
}
