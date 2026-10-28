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

    /** Всё для переноса: настройки, подписка, выбранный сервер. */
    fun payload(): JSONObject = JSONObject()
        .put("app", "APEX VPN")
        .put("format", 1)
        .put("version", BuildConfig.VERSION_NAME)
        .put("settings", AppSettings.exportJson())
        .put("subscription", Prefs.subscriptionUrl)
        .put("selected", Prefs.selectedTag)

    fun export(context: Context, uri: Uri): Boolean = runCatching {
        val o = payload()
        // «wt» — перезаписать файл целиком; некоторые папки его не понимают, тогда обычная запись
        val out = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri)
        out?.use { it.write(o.toString(2).toByteArray()) } ?: error("нет доступа к файлу")
        true
    }.getOrDefault(false)

    /** Что восстановили: ссылка подписки (может быть пустой) и выбранный сервер. */
    data class Restored(val subscription: String, val selected: String)

    /** Читает файл и применяет настройки; null — файл не подошёл. */
    fun import(context: Context, uri: Uri): Restored? = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
        restore(JSONObject(text))
    }.getOrNull()

    /** Применить копию (из файла или QR-кода); null — это не копия APEX VPN. */
    fun restore(o: JSONObject): Restored? = runCatching {
        if (o.optString("app") != "APEX VPN") return null
        val settings = o.optJSONObject("settings") ?: return null
        if (!AppSettings.importJson(settings)) return null
        val selected = o.optString("selected")
        selected.takeIf { it.isNotBlank() }?.let { Prefs.selectedTag = it }
        Restored(o.optString("subscription"), selected)
    }.getOrNull()
}
