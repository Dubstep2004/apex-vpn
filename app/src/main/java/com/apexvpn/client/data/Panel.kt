package com.apexvpn.client.data

import android.os.Build
import com.apexvpn.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Связь с панелью (Telegram-бот, папка panel/): статистика версий и сообщения о проблемах.
 * Отправляется только хэш идентификатора устройства, версия приложения и Android.
 */
object Panel {
    val enabled get() = BuildConfig.PANEL_URL.isNotBlank()

    private fun anonId(): String = MessageDigest.getInstance("SHA-256")
        .digest(("apex:" + Prefs.deviceId).toByteArray())
        .joinToString("") { "%02x".format(it) }.take(32)

    private fun post(path: String, json: JSONObject): Int {
        val c = Http.open(BuildConfig.PANEL_URL + path)
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        return try {
            c.outputStream.use { it.write(json.toString().toByteArray()) }
            c.responseCode
        } finally {
            c.disconnect()
        }
    }

    /** Раз в сутки: «это устройство пользуется версией X» — для /stats в боте. */
    suspend fun ping() = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext
        val now = System.currentTimeMillis()
        if (now - Prefs.lastPanelPing < 20 * 3_600_000L) return@withContext
        runCatching {
            val code = post("/ping", JSONObject().put("id", anonId()).put("v", BuildConfig.VERSION_NAME).put("sdk", Build.VERSION.SDK_INT))
            if (code == 200) Prefs.lastPanelPing = now
        }
    }

    /** «Сообщить о проблеме»: текст уходит владельцу сервиса в Telegram. */
    suspend fun feedback(text: String, contact: String): String? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext "Панель не настроена в этой сборке"
        runCatching {
            val code = post(
                "/feedback",
                JSONObject()
                    .put("text", text.take(2000))
                    .put("contact", contact.take(100))
                    .put("v", BuildConfig.VERSION_NAME)
                    .put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                    .put("android", Build.VERSION.RELEASE),
            )
            if (code == 200) null else "Сервер ответил кодом $code"
        }.getOrElse { "Нет связи: ${it.message}" }
    }
}
