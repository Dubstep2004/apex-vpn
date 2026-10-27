package com.apexvpn.client.vpn

import android.net.DnsResolver
import android.net.NetworkCapabilities
import android.os.Build
import android.os.CancellationSignal
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Base64
import androidx.annotation.RequiresApi
import com.apexvpn.client.ApexApp
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.StringIterator
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.net.UnknownHostException
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import io.nekohasekai.libbox.NetworkInterface as BoxInterface

class StringArray(private val items: List<String>) : StringIterator {
    private val iterator = items.iterator()
    override fun len(): Int = items.size
    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): String = iterator.next()
}

private class InterfaceArray(private val iterator: Iterator<BoxInterface>) : NetworkInterfaceIterator {
    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): BoxInterface = iterator.next()
}

/** Сетевые интерфейсы устройства для ядра sing-box. */
internal fun collectInterfaces(): NetworkInterfaceIterator {
    val cm = ApexApp.connectivity
    val javaInterfaces = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
    val result = mutableListOf<BoxInterface>()
    @Suppress("DEPRECATION")
    for (network in cm.allNetworks) {
        val link = cm.getLinkProperties(network) ?: continue
        val caps = cm.getNetworkCapabilities(network) ?: continue
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
        val name = link.interfaceName ?: continue
        val ni = javaInterfaces.find { it.name == name } ?: continue
        val box = BoxInterface()
        box.name = name
        box.index = ni.index
        runCatching { box.mtu = ni.mtu }
        box.dnsServer = StringArray(link.dnsServers.mapNotNull { it.hostAddress })
        box.addresses = StringArray(ni.interfaceAddresses.map { it.toPrefix() })
        box.type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
            else -> Libbox.InterfaceTypeOther
        }
        var flags = 0
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
        }
        if (ni.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
        if (ni.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
        if (ni.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
        box.flags = flags
        box.metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        result.add(box)
    }
    return InterfaceArray(result.iterator())
}

private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
    "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
} else {
    "${address.hostAddress}/$networkPrefixLength"
}

/** Системные корневые сертификаты (в т.ч. установленные пользователем). */
internal fun loadSystemCertificates(): StringIterator {
    val certs = mutableListOf<String>()
    runCatching {
        val ks = KeyStore.getInstance("AndroidCAStore")
        ks.load(null, null)
        val aliases = ks.aliases()
        while (aliases.hasMoreElements()) {
            val cert = ks.getCertificate(aliases.nextElement()) ?: continue
            certs += "-----BEGIN CERTIFICATE-----\n" +
                Base64.encodeToString(cert.encoded, Base64.NO_WRAP) +
                "\n-----END CERTIFICATE-----"
        }
    }
    return StringArray(certs)
}

/**
 * DNS-резолвер системы для «локального» DNS sing-box
 * (используется, чтобы узнать IP самих VPN-серверов).
 */
object LocalResolver : LocalDNSTransport {
    private const val RCODE_NXDOMAIN = 3
    private const val TIMEOUT_SECONDS = 10L
    private val executor = Executors.newCachedThreadPool()

    override fun raw(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun exchange(ctx: ExchangeContext, message: ByteArray) {
        val network = NetworkMonitor.defaultNetwork ?: error("нет активной сети")
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        val signal = CancellationSignal()
        ctx.onCancel { signal.cancel(); done.countDown() }
        DnsResolver.getInstance().rawQuery(
            network, message, DnsResolver.FLAG_NO_RETRY, executor, signal,
            object : DnsResolver.Callback<ByteArray> {
                override fun onAnswer(answer: ByteArray, rcode: Int) {
                    if (rcode == 0) ctx.rawSuccess(answer) else ctx.errorCode(rcode)
                    done.countDown()
                }

                override fun onError(error: DnsResolver.DnsException) {
                    val cause = error.cause
                    if (cause is ErrnoException) ctx.errnoCode(cause.errno) else failure = error
                    done.countDown()
                }
            },
        )
        if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            signal.cancel()
            error("DNS: превышено время ожидания")
        }
        failure?.let { throw it }
    }

    override fun lookup(ctx: ExchangeContext, network: String, domain: String) {
        val net = NetworkMonitor.defaultNetwork ?: error("нет активной сети")
        val answer: Array<InetAddress> = try {
            net.getAllByName(domain)
        } catch (e: UnknownHostException) {
            ctx.errorCode(RCODE_NXDOMAIN)
            return
        }
        val filtered = answer.filter {
            when {
                network.endsWith("4") -> it !is Inet6Address
                network.endsWith("6") -> it is Inet6Address
                else -> true
            }
        }
        ctx.success(filtered.mapNotNull { it.hostAddress }.joinToString("\n"))
    }
}
