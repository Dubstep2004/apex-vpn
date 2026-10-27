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

/** Виджет на рабочий стол: состояние, сервер и кнопка включения. */
class ApexWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            VpnToggle.toggle(context.applicationContext)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_TOGGLE = "com.apexvpn.client.WIDGET_TOGGLE"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ApexWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val status = VpnState.status.value
            val on = status == VpnStatus.Started || status == VpnStatus.Paused
            val sub = Subscription.state.value
            val server = if (sub.selectedTag == AUTO_TAG) {
                "Автопилот"
            } else {
                sub.nodes.find { it.tag == sub.selectedTag }?.name?.let { ServerInfo.title(it) } ?: "Сервер"
            }
            val toggle = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, ApexWidget::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return RemoteViews(context.packageName, R.layout.widget_apex).apply {
                setTextViewText(
                    R.id.widget_status,
                    when (status) {
                        VpnStatus.Started -> "Защищено"
                        VpnStatus.Paused -> "Пауза"
                        VpnStatus.Starting -> "Подключение…"
                        VpnStatus.Stopping -> "Отключение…"
                        VpnStatus.Stopped -> "Выключен"
                    },
                )
                setTextViewText(R.id.widget_server, server)
                setImageViewResource(R.id.widget_button, if (on) R.drawable.widget_power_on else R.drawable.widget_power_off)
                setOnClickPendingIntent(R.id.widget_button, toggle)
                setOnClickPendingIntent(R.id.widget_root, toggle)
            }
        }
    }
}
