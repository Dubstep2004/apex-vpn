package com.apexvpn.client.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.apexvpn.client.ApexApp
import com.apexvpn.client.R
import com.apexvpn.client.ui.MainActivity

/**
 * 491. Белые списки у оператора. Бывает, что мобильный интернет пропускает только «разрешённые»
 * сайты: обычные серверы VPN недоступны, а LTE-обход («обход белых списков») работает.
 * На мобильной сети с автопилотом проверяем это раз в 5 минут и сами переключаем автопилот
 * на LTE-обход, а когда обычные серверы снова доступны (или включился Wi-Fi) — обратно.
 * Отключается в Настройки → Профиль.
 */
object WhiteList {
    /** Сейчас автопилот выбирает только LTE-обход. */
    @Volatile
    var active = false
        private set

    /** Сбросить (Wi-Fi, автопилот выключен, функция отключена); true — состояние изменилось. */
    fun reset(context: Context): Boolean {
        if (!active) return false
        active = false
        notify(context, false)
        return true
    }

    /** Проверка; true — состояние изменилось (нужно перезагрузить ядро). */
    suspend fun check(context: Context): Boolean {
        if (Prefs.selectedTag != AUTO_TAG) return reset(context) // сервер выбран вручную — не вмешиваемся
        val nodes = Subscription.state.value.nodes
        val lte = nodes.filter { ServerInfo.isLte(it.name) }
        val normal = nodes.filter { !ServerInfo.isSpecial(it.name) }
        if (lte.isEmpty() || normal.isEmpty()) return reset(context)
        val pings = Pinger.pingAll(nodes)
        val normalOk = normal.any { (pings[it.tag] ?: -1) > 0 }
        val lteOk = lte.any { (pings[it.tag] ?: -1) > 0 }
        val now = !normalOk && lteOk
        if (now == active) return false
        active = now
        notify(context, now)
        return true
    }

    private fun notify(context: Context, on: Boolean) = runCatching {
        val nm = context.getSystemService<NotificationManager>() ?: return@runCatching
        val pi = PendingIntent.getActivity(
            context, 13, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            7,
            NotificationCompat.Builder(context, ApexApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setContentTitle(if (on) "Мобильный интернет по белым спискам" else "Белые списки сняты")
                .setContentText(
                    if (on) "Автопилот включил LTE-обход. Отключить: Настройки → Профиль" else "Автопилот снова выбирает обычные серверы",
                )
                .setStyle(NotificationCompat.BigTextStyle())
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }
}
