package com.apexvpn.client.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apexvpn.client.ApexApp
import com.apexvpn.client.R
import com.apexvpn.client.ui.MainActivity
import com.apexvpn.client.vpn.ApexVpnService
import java.util.concurrent.TimeUnit

/**
 * Фоновая задача раз в 3 часа (только при наличии интернета):
 * обновление подписки, предупреждение о конце подписки, новости,
 * обновление баз и отправка отчётов о сбоях.
 */
class BackgroundWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Subscription.needsRefresh()) {
            runCatching {
                Subscription.refresh()
                ApexVpnService.reload(ctx)
            }
        }
        ExpiryAlerts.check(ctx)
        News.refresh()
        if (RulesUpdater.update(ctx)) ApexVpnService.reload(ctx)
        CrashReporter.uploadPending(ctx)
        // 1. Обновление заранее по Wi-Fi: потом установка в одно касание
        if (AppSettings.value.autoDownloadUpdates) {
            runCatching { com.apexvpn.client.update.UpdateManager.prefetch(ctx) }.getOrNull()?.let { notifyUpdateReady(ctx, it.versionName) }
        }
        return Result.success()
    }

    private fun notifyUpdateReady(context: Context, version: String) {
        val sp = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        if (sp.getString("update_ready", "") == version) return // уже говорили про эту версию
        sp.edit { putString("update_ready", version) }
        val nm = context.getSystemService<NotificationManager>() ?: return
        val pi = PendingIntent.getActivity(
            context, 12,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_INSTALL_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            5,
            NotificationCompat.Builder(context, ApexApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setContentTitle("APEX VPN $version готов к установке")
                .setContentText("Нажмите, чтобы установить обновление")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackgroundWork>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("apex-background", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

// ─── 13. Подписка скоро закончится ──────────────────────────────────────

object ExpiryAlerts {
    private const val DAY = 24L * 3600 * 1000

    /** Текст предупреждения или null, если всё в порядке. */
    fun warning(state: SubscriptionState): String? {
        val now = System.currentTimeMillis() / 1000
        if (state.expireAt > 0) {
            val daysLeft = ((state.expireAt - now) / 86400).toInt()
            when {
                state.expireAt <= now -> return "Подписка закончилась — продлите её"
                daysLeft <= 3 -> return "Подписка закончится через ${daysWord(daysLeft.coerceAtLeast(1))}"
            }
        }
        if (state.total > 0 && state.used >= 0) {
            val left = state.total - state.used
            when {
                left <= 0 -> return "Трафик закончился"
                left.toDouble() / state.total < 0.1 -> return "Осталось меньше 10% трафика"
            }
        }
        return null
    }

    private fun daysWord(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "$n день"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "$n дня"
        else -> "$n дней"
    }

    /** Показывает уведомление не чаще раза в сутки. */
    fun check(context: Context) {
        if (!AppSettings.value.expiryAlerts) return
        val text = warning(Subscription.state.value) ?: return
        val sp = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - sp.getLong("expiry", 0) < DAY) return
        sp.edit { putLong("expiry", System.currentTimeMillis()) }
        val nm = context.getSystemService<NotificationManager>() ?: return
        val pi = PendingIntent.getActivity(
            context, 11, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            4,
            NotificationCompat.Builder(context, ApexApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setContentTitle("APEX VPN")
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }
}
