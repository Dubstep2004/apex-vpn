package com.apexvpn.client.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Простое хранилище настроек приложения. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("apex", Context.MODE_PRIVATE)
    }

    var subscriptionUrl: String
        get() = sp.getString("sub_url", "") ?: ""
        set(v) = sp.edit { putString("sub_url", v) }

    /** JSON-массив серверов из Apexcore.parseSubscription */
    var nodesJson: String
        get() = sp.getString("nodes", "[]") ?: "[]"
        set(v) = sp.edit { putString("nodes", v) }

    var selectedTag: String
        get() = sp.getString("selected", "auto") ?: "auto"
        set(v) = sp.edit { putString("selected", v) }

    var profileTitle: String
        get() = sp.getString("title", "") ?: ""
        set(v) = sp.edit { putString("title", v) }

    var trafficUsed: Long
        get() = sp.getLong("used", -1)
        set(v) = sp.edit { putLong("used", v) }

    var trafficTotal: Long
        get() = sp.getLong("total", -1)
        set(v) = sp.edit { putLong("total", v) }

    /** Время окончания подписки, секунды Unix (0 — бессрочно, -1 — неизвестно) */
    var expireAt: Long
        get() = sp.getLong("expire", -1)
        set(v) = sp.edit { putLong("expire", v) }

    var lastUpdated: Long
        get() = sp.getLong("updated", 0)
        set(v) = sp.edit { putLong("updated", v) }

    /** Интервал автообновления подписки, часы */
    var updateIntervalHours: Int
        get() = sp.getInt("interval", 12)
        set(v) = sp.edit { putInt("interval", v) }

    /** Последняя версия, о которой уже показали уведомление «Позже» */
    var dismissedUpdateCode: Int
        get() = sp.getInt("dismissed_update", 0)
        set(v) = sp.edit { putInt("dismissed_update", v) }

    /** Версия, для которой пользователь уже видел «Что нового» */
    var lastSeenVersionCode: Int
        get() = sp.getInt("seen_version", 0)
        set(v) = sp.edit { putInt("seen_version", v) }

    fun clearSubscription() = sp.edit {
        remove("sub_url"); remove("nodes"); remove("selected"); remove("title")
        remove("used"); remove("total"); remove("expire"); remove("updated")
    }
}
