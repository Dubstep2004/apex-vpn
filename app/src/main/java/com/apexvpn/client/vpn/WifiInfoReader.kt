package com.apexvpn.client.vpn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.apexvpn.client.ApexApp

/**
 * Имя текущей сети Wi-Fi. Android отдаёт его только с разрешением на геолокацию
 * и при включённой геолокации — иначе вернёт null.
 */
object WifiInfoReader {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun currentSsid(context: Context): String? {
        if (!hasPermission(context)) return null
        val raw: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val network = NetworkMonitor.defaultNetwork ?: ApexApp.connectivity.activeNetwork
            val caps = network?.let { ApexApp.connectivity.getNetworkCapabilities(it) }
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                // Без колбэка с FLAG_INCLUDE_LOCATION_INFO имя может быть скрыто — тогда старый способ
                clean((caps.transportInfo as? WifiInfo)?.ssid) ?: legacySsid(context)
            } else {
                null
            }
        } else {
            legacySsid(context)
        }
        return clean(raw)
    }

    @Suppress("DEPRECATION")
    private fun legacySsid(context: Context): String? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        if (!wm.isWifiEnabled) return null
        return wm.connectionInfo?.ssid
    }

    private fun clean(raw: String?): String? {
        if (raw == null || raw == WifiManager.UNKNOWN_SSID || raw.isBlank()) return null
        return raw.removeSurrounding("\"")
    }
}
