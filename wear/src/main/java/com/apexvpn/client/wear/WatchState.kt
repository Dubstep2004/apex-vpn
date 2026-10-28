package com.apexvpn.client.wear

import android.content.Context

/** Последнее состояние VPN с телефона — для плитки, значка на циферблате и вибрации. */
object WatchState {
    data class S(val status: String, val server: String, val ping: Int)

    private fun sp(c: Context) = c.getSharedPreferences("watch_state", Context.MODE_PRIVATE)

    fun load(c: Context) = sp(c).let { S(it.getString("status", "") ?: "", it.getString("server", "") ?: "", it.getInt("ping", 0)) }

    fun save(c: Context, s: S) {
        sp(c).edit().putString("status", s.status).putString("server", s.server).putInt("ping", s.ping).apply()
    }

    fun label(status: String) = when (status) {
        "started" -> "Защищено"
        "starting" -> "Подключение…"
        "stopping" -> "Отключение…"
        "paused" -> "Пауза"
        "blocked" -> "Блокировка"
        "stopped" -> "Выключен"
        else -> "Нет данных"
    }
}
