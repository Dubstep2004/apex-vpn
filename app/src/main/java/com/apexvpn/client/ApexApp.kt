package com.apexvpn.client

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.getSystemService
import com.apexvpn.client.data.AppSettings
import com.apexvpn.client.data.BackgroundWork
import com.apexvpn.client.data.CrashReporter
import com.apexvpn.client.data.Prefs
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.vpn.ApexTileService
import com.apexvpn.client.vpn.ApexWidget
import com.apexvpn.client.vpn.VpnState
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class ApexApp : Application() {

    /** Область для фоновых дел всего приложения. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashReporter.install(this)
        com.apexvpn.client.data.AdCounter.init(this)
        Prefs.init(this)
        AppSettings.init(this)
        com.apexvpn.client.update.UpdateManager.init(this)

        val workingDir = File(filesDir, "work").apply { mkdirs() }
        val tempDir = File(cacheDir, "box").apply { mkdirs() }
        Libbox.setup(
            SetupOptions().also {
                it.basePath = filesDir.path
                it.workingPath = workingDir.path
                it.tempPath = tempDir.path
                // Обход бага Go на Android: https://github.com/golang/go/issues/68760
                it.fixAndroidStack = BuildConfig.DEBUG ||
                    Build.VERSION.SDK_INT in Build.VERSION_CODES.N..Build.VERSION_CODES.N_MR1 ||
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                it.logMaxLines = 300
                it.debug = BuildConfig.DEBUG
            },
        )
        val stderr = File(workingDir, "stderr.log")
        CrashReporter.collectNativeCrash(this, stderr) // сбой ядра в прошлый раз
        runCatching { Libbox.redirectStderr(stderr.path) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService<NotificationManager>()!!
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_VPN, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, getString(R.string.notification_channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        // Виджет и плитка следят за состоянием VPN и выбранным сервером
        appScope.launch {
            combine(VpnState.status, Subscription.state) { s, sub -> s to sub.selectedTag }
                .distinctUntilChanged()
                .collect {
                    ApexWidget.refresh(this@ApexApp)
                    com.apexvpn.client.vpn.NewWidgets.update(this@ApexApp)
                    com.apexvpn.client.vpn.WearBridge.publish(this@ApexApp)
                    ApexTileService.refresh(this@ApexApp)
                }
        }

        // 20. Иконка с зелёной точкой, пока VPN включён
        appScope.launch {
            combine(VpnState.status, AppSettings.state) { st, set ->
                (st == com.apexvpn.client.vpn.VpnStatus.Started || st == com.apexvpn.client.vpn.VpnStatus.Paused) to (set.iconStatus to set.appIcon)
            }
                .distinctUntilChanged()
                .collect { (on, _) -> com.apexvpn.client.data.AppIcons.syncStatus(this@ApexApp, on) }
        }

        // «Ощущения»: звук и вибрация при подключении
        appScope.launch {
            var last = VpnState.status.value
            VpnState.status.collect { st ->
                if (st == com.apexvpn.client.vpn.VpnStatus.Started &&
                    (last == com.apexvpn.client.vpn.VpnStatus.Starting || last == com.apexvpn.client.vpn.VpnStatus.Stopped)
                ) {
                    com.apexvpn.client.data.ConnectFeedback.onConnected(this@ApexApp)
                    // 382. Конфетти на первом и юбилейных подключениях
                    com.apexvpn.client.data.Celebrate.onConnected()
                    // 220. Задача «Видео»: на автопилоте сами подбираем самый быстрый сервер
                    if (AppSettings.value.task == "video" && Prefs.selectedTag == com.apexvpn.client.data.AUTO_TAG) {
                        appScope.launch {
                            kotlinx.coroutines.delay(3000)
                            if (VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started) {
                                com.apexvpn.client.data.FastestServer.run(this@ApexApp)
                            }
                        }
                    }
                }
                // 271. Звук отключения
                if (st == com.apexvpn.client.vpn.VpnStatus.Stopped && last == com.apexvpn.client.vpn.VpnStatus.Stopping) {
                    com.apexvpn.client.data.ConnectFeedback.onDisconnected(this@ApexApp)
                }
                last = st
            }
        }

        // Ярлыки избранных серверов (долгое нажатие на иконку)
        appScope.launch {
            combine(AppSettings.state, Subscription.state) { st, sub -> st.favorites to sub.nodes.map { it.name } }
                .distinctUntilChanged()
                .collect { com.apexvpn.client.vpn.Shortcuts.refresh(this@ApexApp) }
        }

        Prefs.initConnectCount()
        BackgroundWork.schedule(this)
        // 259. Точные напоминания о конце подписки — при каждой новой дате окончания
        appScope.launch {
            Subscription.state.map { it.expireAt }.distinctUntilChanged().collect {
                runCatching { com.apexvpn.client.data.ExpiryAlerts.schedule(this@ApexApp) }
                runCatching { com.apexvpn.client.data.ExpiryAlerts.check(this@ApexApp) }
            }
        }
    }

    companion object {
        const val CHANNEL_VPN = "vpn_status"
        const val CHANNEL_ALERTS = "alerts"
        lateinit var instance: ApexApp
            private set

        val connectivity: ConnectivityManager
            get() = instance.getSystemService()!!
    }
}
