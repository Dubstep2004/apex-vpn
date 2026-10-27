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

    suspend fun check(silent: Boolean = false) {
        if (_state.value is UpdateState.Downloading) return
        if (!silent) _state.value = UpdateState.Checking
        try {
            val info = fetchInfo()
            _state.value = if (info.versionCode > BuildConfig.VERSION_CODE) {
                UpdateState.Available(info)
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

    suspend fun download(context: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
        _state.value = UpdateState.Downloading(info, 0f)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "apex-vpn-${info.versionName}.apk")
        try {
            val conn = open(info.apkUrl)
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
                    if (actual != expected) {
                        target.delete()
                        error("Файл обновления повреждён (контрольная сумма не совпала)")
                    }
                }
            } finally {
                conn.disconnect()
            }
            _state.value = UpdateState.ReadyToInstall(info, target)
        } catch (e: Exception) {
            target.delete()
            _state.value = UpdateState.Failed(e.message ?: "Ошибка загрузки", info)
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

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private suspend fun fetchInfo(): UpdateInfo = withContext(Dispatchers.IO) {
        val conn = open(BuildConfig.UPDATE_URL)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                error("Сервер обновлений ответил кодом ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val apkUrl = json.getString("apkUrl")
            require(apkUrl.startsWith("https://")) { "Ссылка на APK должна быть https" }
            val minSupported = json.optInt("minSupportedVersionCode", 0)
            UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = apkUrl,
                sha256 = json.optString("sha256").takeIf { it.isNotBlank() },
                changelog = json.optString("changelog"),
                mandatory = json.optBoolean("mandatory", false) || BuildConfig.VERSION_CODE < minSupported,
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
