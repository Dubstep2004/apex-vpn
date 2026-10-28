package com.apexvpn.client.data

import android.content.Context
import android.net.Uri
import com.apexvpn.client.BuildConfig
import org.json.JSONObject

/**
 * Резервная копия настроек в файл: все настройки, свои списки, правила, избранное
 * и ссылка подписки. Файл личный — в нём ссылка подписки.
 */
object SettingsBackup {
    const val MIME = "application/json"

    fun fileName() = "apex-vpn-settings.json"

    fun export(context: Context, uri: Uri): Boolean = runCatching {
        val o = JSONObject()
            .put("app", "APEX VPN")
            .put("format", 1)
            .put("version", BuildConfig.VERSION_NAME)
            .put("settings", AppSettings.exportJson())
            .put("subscription", Prefs.subscriptionUrl)
            .put("selected", Prefs.selectedTag)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(o.toString(2).toByteArray()) } ?: error("нет доступа к файлу")
        true
    }.getOrDefault(false)

    /** Читает файл и применяет настройки. Возвращает ссылку подписки из файла (может быть пустой) или null — файл не подошёл. */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
        val o = JSONObject(text)
        if (o.optString("app") != "APEX VPN") return null
        val settings = o.optJSONObject("settings") ?: return null
        if (!AppSettings.importJson(settings)) return null
        o.optString("selected").takeIf { it.isNotBlank() }?.let { Prefs.selectedTag = it }
        o.optString("subscription")
    }.getOrNull()
}
