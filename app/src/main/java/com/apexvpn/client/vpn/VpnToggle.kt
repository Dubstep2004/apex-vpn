package com.apexvpn.client.vpn

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.apexvpn.client.ApexApp
import com.apexvpn.client.R
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.ui.MainActivity

/** Включение/выключение из плитки, виджета и ярлыков. */
object VpnToggle {
    const val EXTRA_CONNECT = "connect"

    fun appIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_CONNECT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /**
     * [openApp] вызывается, если без приложения не обойтись (нет подписки или
     * разрешения на VPN). По умолчанию — уведомление, т.к. из фона Android
     * не даёт открывать экраны.
     */
    fun toggle(context: Context, openApp: ((Intent) -> Unit)? = null) {
        if (VpnState.status.value != VpnStatus.Stopped) {
            ApexVpnService.stop(context)
            return
        }
        if (Subscription.state.value.nodes.isEmpty() || VpnService.prepare(context) != null) {
            val intent = appIntent(context)
            if (openApp != null) openApp(intent) else askToOpen(context, intent)
            return
        }
        VpnState.clearError()
        try {
            ApexVpnService.start(context)
        } catch (e: Exception) {
            // Android 12+ может не разрешить запуск из фона (например, команда с часов) —
            // тогда просим открыть приложение одним касанием
            askToOpen(context, appIntent(context))
        }
    }

    private fun askToOpen(context: Context, intent: Intent) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val pi = PendingIntent.getActivity(context, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(
            3,
            NotificationCompat.Builder(context, ApexApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("Откройте приложение, чтобы разрешить VPN")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }
}
