package com.apexvpn.client.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.apexvpn.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Информация об обновлении из JSON на вашем сервере (BuildConfig.UPDATE_URL):
 * {
 *   "versionCode": 2,
 *   "versionName": "1.0.1",
 *   "apkUrl": "https://example.com/apexvpn/apex-vpn-1.0.1.apk",
 *   "sha256": "…",                      // необязательно, но рекомендуется
 *   "changelog": "Что нового…",
 *   "mandatory": false,                 // обязательное обновление
 *   "minSupportedVersionCode": 1        // версии ниже — обновление обязательно
 * }
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val changelog: String,
    val mandatory: Boolean,
    /** 83. Запасные адреса APK (только при наличии sha256 — файл всё равно проверяется) */
    val mirrors: List<String> = emptyList(),
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

object UpdateManager {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Адрес сервера обновлений задан при сборке (не заглушка example.com). */
    val configured: Boolean
        get() = BuildConfig.UPDATE_URL.startsWith("https://") && "example.com" !in BuildConfig.UPDATE_URL

    suspend fun check(silent: Boolean = false) {
        if (_state.value is UpdateState.Downloading) return
        if (!configured) {
            _state.value = if (silent) UpdateState.Idle else UpdateState.Failed("Сервер обновлений не настроен в этой сборке")
            return
        }
        if (!silent) _state.value = UpdateState.Checking
        try {
            val info = fetchInfo() ?: run {
                _state.value = UpdateState.UpToDate // на сервере обновление не выпущено
                return
            }
            _state.value = if (info.versionCode > BuildConfig.VERSION_CODE) {
                // Уже скачано заранее (по Wi-Fi в фоне) — сразу «Установить»
                downloadedFile(info)?.let { UpdateState.ReadyToInstall(info, it) } ?: UpdateState.Available(info)
            } else {
                UpdateState.UpToDate
            }
        } catch (e: Exception) {
            _state.value = if (silent) UpdateState.Idle else UpdateState.Failed(e.message ?: "Ошибка проверки")
        }
    }

    fun dismiss() {
        val s = _state.value
        if (s is UpdateState.Available && s.info.mandatory) return
        _state.value = UpdateState.Idle
    }

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun updatesDir() = File(appContext.cacheDir, "updates").apply { mkdirs() }

    /** Файл этой версии, если он уже скачан целиком и контрольная сумма совпадает. */
    private fun downloadedFile(info: UpdateInfo): File? {
        if (!::appContext.isInitialized) return null
        val f = File(updatesDir(), "apex-vpn-${info.versionName}.apk")
        if (!f.exists() || f.length() == 0L) return null
        val expected = info.sha256?.lowercase() ?: return f
        val actual = runCatching {
            val d = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    d.update(buf, 0, n)
                }
            }
            d.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
        return if (actual == expected) f else null.also { f.delete() }
    }

    /**
     * Скачать обновление. [autoInstall] — сразу открыть установщик (когда пользователь
     * сам нажал «Обновить»). Фоновая загрузка по Wi-Fi вызывает с false.
     */
    suspend fun download(context: Context, info: UpdateInfo, autoInstall: Boolean = false) = withContext(Dispatchers.IO) {
        if (!::appContext.isInitialized) init(context)
        downloadedFile(info)?.let { ready ->
            _state.value = UpdateState.ReadyToInstall(info, ready)
            if (autoInstall) launchInstaller(context, ready)
            return@withContext
        }
        _state.value = UpdateState.Downloading(info, 0f)
        val dir = updatesDir()
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "apex-vpn-${info.versionName}.apk")
        // 83. Основной адрес, затем зеркала: если сервер недоступен, APK скачается с запасного
        val urls = listOf(info.apkUrl) + info.mirrors
        var lastError: Exception? = null
        for (url in urls) {
            try {
                fetchApk(url, info, target)
                _state.value = UpdateState.ReadyToInstall(info, target)
                if (autoInstall) launchInstaller(context, target)
                return@withContext
            } catch (e: kotlinx.coroutines.CancellationException) {
                target.delete()
                throw e
            } catch (e: Exception) {
                target.delete()
                lastError = e
            }
        }
        _state.value = UpdateState.Failed(lastError?.message ?: "Ошибка загрузки", info)
    }

    private suspend fun fetchApk(url: String, info: UpdateInfo, target: File) = withContext(Dispatchers.IO) {
        val conn = open(url)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                error("Сервер обновлений ответил кодом ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastReported = 0f
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = done.toFloat() / total
                            if (p - lastReported >= 0.01f) {
                                lastReported = p
                                _state.value = UpdateState.Downloading(info, p)
                            }
                        }
                    }
                }
            }
            val expected = info.sha256?.lowercase()?.takeIf { it.isNotBlank() }
            if (expected != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (actual != expected) error("Файл обновления повреждён (контрольная сумма не совпала)")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Нужно ли сначала разрешить установку из этого приложения (Android 8+). */
    fun needsInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Открыть установщик; если Android ещё не разрешил установку из APEX VPN — экран разрешения. */
    fun launchInstaller(context: Context, file: File) {
        val ctx = context.applicationContext
        kotlinx.coroutines.MainScope().launch {
            runCatching {
                if (needsInstallPermission(ctx)) openInstallPermissionSettings(ctx) else install(ctx, file)
            }
        }
    }

    /** Скачано и ждёт установки (для уведомления и кнопок). */
    fun readyFile(): File? = (_state.value as? UpdateState.ReadyToInstall)?.file

    /**
     * Фоновая проверка (раз в 3 часа): если вышла новая версия и сеть без лимита (Wi-Fi) —
     * скачиваем заранее. Возвращает версию, готовую к установке, или null.
     */
    suspend fun prefetch(context: Context): UpdateInfo? {
        if (!configured) return null
        if (!::appContext.isInitialized) init(context)
        check(silent = true)
        val s = _state.value
        if (s is UpdateState.ReadyToInstall) return s.info
        if (s !is UpdateState.Available) return null
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        if (cm?.isActiveNetworkMetered != false) return null // мобильный интернет — не тратим трафик
        download(context, s.info, autoInstall = false)
        return (_state.value as? UpdateState.ReadyToInstall)?.info
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** null — обновление на сервере не выпущено (update.json нет или он пустой). */
    private suspend fun fetchInfo(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = open(BuildConfig.UPDATE_URL)
        try {
            if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                error("Сервер обновлений ответил кодом ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.optInt("versionCode", 0) <= 0) return@withContext null
            val apkUrl = json.getString("apkUrl")
            require(apkUrl.startsWith("https://")) { "Ссылка на APK должна быть https" }
            val minSupported = json.optInt("minSupportedVersionCode", 0)
            val sha = json.optString("sha256").takeIf { it.isNotBlank() }
            val mirrors = if (sha == null) emptyList() else json.optJSONArray("mirrors")?.let { a ->
                List(a.length()) { a.optString(it) }.filter { it.startsWith("https://") && it != apkUrl }
            }.orEmpty()
            UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = apkUrl,
                sha256 = sha,
                changelog = json.optString("changelog"),
                mandatory = json.optBoolean("mandatory", false) || BuildConfig.VERSION_CODE < minSupported,
                mirrors = mirrors,
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "APEX VPN/${BuildConfig.VERSION_NAME} (Android)")
            setRequestProperty("Cache-Control", "no-cache")
        }
}
