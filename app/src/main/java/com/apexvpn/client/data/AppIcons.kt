package com.apexvpn.client.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.apexvpn.client.R

/** 20. Иконка приложения на выбор: каждая — отдельный ярлык запуска (activity-alias). */
object AppIcons {
    data class Icon(val id: String, val title: String, val alias: String, val background: Int, val foreground: Int)

    val all = listOf(
        Icon("classic", "APEX", "com.apexvpn.client.ui.LauncherClassic", R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground),
        Icon("neon", "Неон", "com.apexvpn.client.ui.LauncherNeon", R.drawable.ic_launcher_neon_background, R.drawable.ic_launcher_neon_foreground),
        Icon("light", "Светлая", "com.apexvpn.client.ui.LauncherLight", R.drawable.ic_launcher_light_background, R.drawable.ic_launcher_light_foreground),
        Icon("gold", "Золото", "com.apexvpn.client.ui.LauncherGold", R.drawable.ic_launcher_gold_background, R.drawable.ic_launcher_gold_foreground),
        Icon("terminal", "Терминал", "com.apexvpn.client.ui.LauncherTerminal", R.drawable.ic_launcher_terminal_background, R.drawable.ic_launcher_terminal_foreground),
    )

    /** Включает выбранный ярлык и выключает остальные. Приложение при этом не закрывается. */
    fun apply(context: Context, id: String) {
        val pm = context.packageManager
        val chosen = all.firstOrNull { it.id == id } ?: all.first()
        // Сначала включаем новый — чтобы на мгновение не остаться без иконки
        pm.setComponentEnabledSetting(
            ComponentName(context.packageName, chosen.alias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        all.filter { it != chosen }.forEach {
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, it.alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
