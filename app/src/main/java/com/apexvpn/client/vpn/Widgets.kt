package com.apexvpn.client.vpn

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.apexvpn.client.R
import com.apexvpn.client.data.AUTO_TAG
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.data.Subscription

/** 10. Круглый виджет 1×1: кнопка цвета состояния и пинг. */
class ApexWidgetRound : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = NewWidgets.update(context)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NewWidgets.ACTION_TOGGLE) VpnToggle.toggle(context.applicationContext) else super.onReceive(context, intent)
    }
}

/** 10. Информационный виджет: состояние, сервер, скорость ↓↑ и пинг. */
class ApexWidgetInfo : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = NewWidgets.update(context)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NewWidgets.ACTION_TOGGLE) VpnToggle.toggle(context.applicationContext) else super.onReceive(context, intent)
    }
}

object NewWidgets {
    const val ACTION_TOGGLE = "com.apexvpn.client.WIDGET2_TOGGLE"

    private fun ids(context: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    /** Есть ли на экране виджеты со скоростью и пингом — тогда сервис их обновляет. */
    fun anyLive(context: Context) = ids(context, ApexWidgetRound::class.java).isNotEmpty() || ids(context, ApexWidgetInfo::class.java).isNotEmpty()

    private fun toggle(context: Context, cls: Class<*>) = PendingIntent.getBroadcast(
        context, cls.name.hashCode(),
        Intent(context, cls).setAction(ACTION_TOGGLE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun update(context: Context) = runCatching {
        val manager = AppWidgetManager.getInstance(context)
        val status = VpnState.status.value
        val on = status == VpnStatus.Started || status == VpnStatus.Paused
        val icon = when (status) {
            VpnStatus.Blocked -> R.drawable.widget_power_blocked
            else -> if (on) R.drawable.widget_power_on else R.drawable.widget_power_off
        }
        val ping = VpnState.ping.value
        val pingText = if (status == VpnStatus.Started && ping > 0) "$ping мс" else ""

        ids(context, ApexWidgetRound::class.java).takeIf { it.isNotEmpty() }?.let { list ->
            val v = RemoteViews(context.packageName, R.layout.widget_round).apply {
                setImageViewResource(R.id.round_button, icon)
                setTextViewText(R.id.round_ping, pingText)
                setOnClickPendingIntent(R.id.round_root, toggle(context, ApexWidgetRound::class.java))
            }
            list.forEach { manager.updateAppWidget(it, v) }
        }

        ids(context, ApexWidgetInfo::class.java).takeIf { it.isNotEmpty() }?.let { list ->
            val sub = Subscription.state.value
            val server = if (sub.selectedTag == AUTO_TAG) "Автопилот" else
                sub.nodes.find { it.tag == sub.selectedTag }?.name?.let { ServerInfo.title(it) } ?: "Сервер"
            val speed = VpnState.speed.value
            val v = RemoteViews(context.packageName, R.layout.widget_info).apply {
                setTextViewText(
                    R.id.info_status,
                    when (status) {
                        VpnStatus.Started -> "Защищено"
                        VpnStatus.Paused -> "Пауза"
                        VpnStatus.Starting -> "Подключение…"
                        VpnStatus.Stopping -> "Отключение…"
                        VpnStatus.Blocked -> "Блокировка"
                        VpnStatus.Stopped -> "Выключен"
                    },
                )
                setTextViewText(R.id.info_server, server)
                setTextViewText(R.id.info_down, if (status == VpnStatus.Started) "↓ ${formatSpeed(speed.first)}" else "↓ —")
                setTextViewText(R.id.info_up, if (status == VpnStatus.Started) "↑ ${formatSpeed(speed.second)}" else "↑ —")
                setTextViewText(R.id.info_ping, pingText.ifEmpty { "—" })
                setImageViewResource(R.id.info_button, icon)
                setOnClickPendingIntent(R.id.info_button, toggle(context, ApexWidgetInfo::class.java))
                setOnClickPendingIntent(R.id.info_root, PendingIntent.getActivity(
                    context, 31, Intent(context, com.apexvpn.client.ui.MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ))
            }
            list.forEach { manager.updateAppWidget(it, v) }
        }
    }
}
