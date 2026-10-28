package com.apexvpn.client.vpn

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.apexvpn.client.ApexApp
import io.nekohasekai.libbox.InterfaceUpdateListener
import java.net.NetworkInterface

/**
 * Следит за «настоящей» сетью устройства (Wi-Fi / мобильная), минуя сам VPN,
 * и сообщает ядру sing-box, через какой интерфейс выходить в интернет.
 */
object NetworkMonitor {
    @Volatile
    var defaultNetwork: Network? = null
        private set

    @Volatile
    private var listener: InterfaceUpdateListener? = null

    /**
     * Текущая сеть «тип:номер»: mobile:…, wifi:…, ethernet:…, other:… или "" (нет сети) —
     * для DNS «Авто» и белых списков. Номер меняется при переходе на другую сеть того же типа.
     */
    val transport = kotlinx.coroutines.flow.MutableStateFlow("")

    private fun updateTransport(network: Network?) {
        val caps = network?.let { runCatching { ApexApp.connectivity.getNetworkCapabilities(it) }.getOrNull() }
        val type = when {
            caps == null -> ""
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        transport.value = if (type.isEmpty()) "" else "$type:${network?.networkHandle ?: 0}"
    }
    private var registered = false
    // Отдельный поток: колбэки сети не должны блокировать UI
    private val handler by lazy {
        Handler(HandlerThread("apex-network").apply { start() }.looper)
    }

    private val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .build()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            defaultNetwork = network
            updateTransport(network)
            notifyListener()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (network == defaultNetwork) {
                updateTransport(network)
                notifyListener()
            }
        }

        override fun onLost(network: Network) {
            if (network == defaultNetwork) {
                defaultNetwork = null
                updateTransport(null)
                notifyListener()
            }
        }
    }

    @Synchronized
    fun start() {
        if (registered) return
        val cm = ApexApp.connectivity
        when {
            Build.VERSION.SDK_INT >= 31 -> cm.registerBestMatchingNetworkCallback(request, callback, handler)
            Build.VERSION.SDK_INT >= 28 -> cm.requestNetwork(request, callback, handler)
            Build.VERSION.SDK_INT >= 26 -> cm.registerDefaultNetworkCallback(callback, handler)
            else -> cm.registerDefaultNetworkCallback(callback)
        }
        registered = true
        if (defaultNetwork == null) defaultNetwork = cm.activeNetwork
        updateTransport(defaultNetwork)
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        runCatching { ApexApp.connectivity.unregisterNetworkCallback(callback) }
        registered = false
        defaultNetwork = null
        transport.value = ""
    }

    fun setListener(l: InterfaceUpdateListener?) {
        listener = l
        notifyListener()
    }

    private fun notifyListener() {
        val l = listener ?: return
        val network = defaultNetwork
        if (network == null) {
            l.updateDefaultInterface("", -1, false, false)
            return
        }
        repeat(10) {
            val props = ApexApp.connectivity.getLinkProperties(network)
            val name = props?.interfaceName
            val index = name?.let { runCatching { NetworkInterface.getByName(it)?.index }.getOrNull() }
            if (name != null && index != null) {
                l.updateDefaultInterface(name, index, false, false)
                return
            }
            Thread.sleep(100)
        }
    }
}
