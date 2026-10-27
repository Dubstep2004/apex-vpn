package com.apexvpn.client.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.apexvpn.client.BuildConfig
import io.nekohasekai.apexcore.Apexcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Node(
    val tag: String,
    val name: String,
    val type: String,
    val server: String,
    val port: Int,
    /** Категория протокола: VLESS, Hysteria 2, ShadowTLS… */
    val category: String = Protocols.category(type),
    /** Описание подключения: «Reality · Vision · TCP» */
    val detail: String = "",
    /** Протокол поверх UDP/QUIC — пинг по TCP не измерить */
    val udp: Boolean = type in Protocols.udpTypes,
)

/** Категории протоколов (то же, что в core/apexcore/protocols.go). */
object Protocols {
    private val names = mapOf(
        "vless" to "VLESS", "vmess" to "VMess", "trojan" to "Trojan", "shadowsocks" to "Shadowsocks",
        "hysteria2" to "Hysteria 2", "hysteria" to "Hysteria", "tuic" to "TUIC", "wireguard" to "WireGuard",
        "anytls" to "AnyTLS", "naive" to "NaiveProxy", "socks" to "SOCKS", "http" to "HTTP", "ssh" to "SSH",
        "shadowtls" to "ShadowTLS",
    )
    private val short = mapOf("Shadowsocks" to "SS", "Hysteria 2" to "Hy2", "Hysteria" to "Hy", "WireGuard" to "WG", "NaiveProxy" to "Naive")
    val udpTypes = setOf("hysteria2", "hysteria", "tuic", "wireguard")

    fun category(type: String) = names[type] ?: type.uppercase()
    fun shortName(category: String) = short[category] ?: category
}

data class SubscriptionState(
    val url: String = "",
    val title: String = "",
    val nodes: List<Node> = emptyList(),
    val selectedTag: String = AUTO_TAG,
    val used: Long = -1,
    val total: Long = -1,
    val expireAt: Long = -1,
    val lastUpdated: Long = 0,
)

const val AUTO_TAG = "auto"

/** Загрузка и хранение подписки. Разрешена только ссылка на sub.knownhosts.ru. */
object Subscription {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SubscriptionState> = _state.asStateFlow()

    private fun load(): SubscriptionState = SubscriptionState(
        url = Prefs.subscriptionUrl,
        title = Prefs.profileTitle,
        nodes = parseNodes(Prefs.nodesJson),
        selectedTag = Prefs.selectedTag,
        used = Prefs.trafficUsed,
        total = Prefs.trafficTotal,
        expireAt = Prefs.expireAt,
        lastUpdated = Prefs.lastUpdated,
    )

    /**
     * Проверяет ссылку. Возвращает нормализованную ссылку или бросает
     * IllegalArgumentException с понятным текстом.
     */
    fun validate(raw: String): String {
        val text = raw.trim()
        require(text.isNotEmpty()) { "Вставьте ссылку на подписку" }
        val uri = runCatching { Uri.parse(text) }.getOrNull()
        val host = uri?.host?.lowercase()
        require(host == BuildConfig.SUBSCRIPTION_HOST) {
            "Поддерживается только ссылка https://${BuildConfig.SUBSCRIPTION_HOST}/…"
        }
        require(uri!!.scheme.equals("https", ignoreCase = true)) { "Ссылка должна начинаться с https://" }
        return text
    }

    /** Сохраняет новую ссылку и загружает серверы. */
    suspend fun setUrl(raw: String) {
        val url = validate(raw)
        fetch(url)
        Prefs.subscriptionUrl = url
        _state.value = load()
    }

    /** Обновляет текущую подписку. */
    suspend fun refresh() {
        val url = Prefs.subscriptionUrl
        require(url.isNotEmpty()) { "Подписка не добавлена" }
        fetch(validate(url))
        _state.value = load()
    }

    fun needsRefresh(): Boolean {
        if (Prefs.subscriptionUrl.isEmpty()) return false
        val age = System.currentTimeMillis() - Prefs.lastUpdated
        return age > Prefs.updateIntervalHours * 3_600_000L
    }

    fun select(tag: String) {
        Prefs.selectedTag = tag
        _state.value = _state.value.copy(selectedTag = tag)
    }

    fun remove() {
        Prefs.clearSubscription()
        _state.value = load()
    }

    /** Полный конфиг sing-box для выбранного сервера и текущих настроек. */
    fun buildConfig(context: Context): String {
        val nodes = Prefs.nodesJson
        require(parseNodes(nodes).isNotEmpty()) { "Нет серверов — обновите подписку" }
        return Apexcore.buildConfig(nodes, Prefs.selectedTag, AppSettings.coreOptions(RulesUpdater.activeDir(context)))
    }

    /**
     * Ищет ссылку подписки в тексте: QR-код, «Поделиться», apexvpn://import?url=…
     * Возвращает ссылку только на разрешённый хост.
     */
    fun extractLink(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val decoded = runCatching { Uri.decode(text) }.getOrDefault(text)
        val re = Regex("https://${Regex.escape(BuildConfig.SUBSCRIPTION_HOST)}/[^\\s\"'<>]*", RegexOption.IGNORE_CASE)
        return re.find(decoded)?.value?.trimEnd('.', ',', ')', ']')
    }

    /**
     * Панели подписок (Remnawave, Marzban, 3x-ui) по-разному отвечают разным клиентам.
     * Пробуем несколько вариантов User-Agent, пока один не вернёт серверы.
     */
    val builtInUserAgents = listOf(
        "v2rayNG/1.10.23",
        "Happ/3.7.0",
        "v2rayN/7.13.8",
        "Hiddify/2.5.7",
        "sing-box 1.13.21",
        "APEX VPN/${BuildConfig.VERSION_NAME}",
    )

    /** Порядок попыток: свой User-Agent → последний сработавший → встроенные. */
    private fun userAgents(): List<String> =
        (listOf(Prefs.customUserAgent.trim(), Prefs.workingUserAgent) + builtInUserAgents)
            .filter { it.isNotBlank() }
            .distinct()

    private suspend fun fetch(url: String) = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (ua in userAgents()) {
            try {
                fetchWith(url, ua)
                Prefs.workingUserAgent = ua
                return@withContext
            } catch (e: NotFound) {
                throw e // ссылка неверная — другие варианты не помогут
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Не удалось загрузить подписку")
    }

    private class NotFound(message: String) : Exception(message)

    private fun fetchWith(url: String, userAgent: String) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "*/*")
            // Заголовки устройства: их ждут панели с ограничением числа устройств (HWID)
            setRequestProperty("x-hwid", Prefs.deviceId)
            setRequestProperty("x-device-os", "Android")
            setRequestProperty("x-ver-os", android.os.Build.VERSION.RELEASE ?: "")
            setRequestProperty("x-device-model", android.os.Build.MODEL ?: "")
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val detail = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                        ?.replace(Regex("<[^>]*>"), " ")?.replace(Regex("\\s+"), " ")?.trim()?.take(120)
                }.getOrNull().orEmpty()
                val suffix = if (detail.isNotEmpty() && detail.length < 120) ": $detail" else ""
                when (code) {
                    404 -> throw NotFound("Подписка не найдена — проверьте ссылку (код 404)")
                    401, 403 -> error("Подписка отключена или превышен лимит устройств (код $code)$suffix")
                    in 500..599 -> error("Сервер подписки временно недоступен (код $code). Попробуйте позже$suffix")
                    else -> error("Сервер подписки ответил кодом $code$suffix")
                }
            }
            // Защита от редиректа на чужой домен
            require(conn.url.host.equals(BuildConfig.SUBSCRIPTION_HOST, ignoreCase = true)) {
                "Подписка перенаправила на другой домен — отклонено"
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val parsed = JSONObject(Apexcore.parseSubscription(body))
            val nodes = parsed.getJSONArray("nodes")

            Prefs.nodesJson = nodes.toString()
            Prefs.lastUpdated = System.currentTimeMillis()

            val title = decodeTitle(conn.getHeaderField("profile-title"))
            Prefs.profileTitle = title ?: ""
            conn.getHeaderField("profile-update-interval")?.trim()?.toIntOrNull()?.let {
                if (it in 1..168) Prefs.updateIntervalHours = it
            }
            parseUserInfo(conn.getHeaderField("subscription-userinfo"))

            // Если выбранного сервера больше нет — переключаемся на «Авто»
            val tags = parseNodes(Prefs.nodesJson).map { it.tag }
            if (Prefs.selectedTag != AUTO_TAG && Prefs.selectedTag !in tags) {
                Prefs.selectedTag = AUTO_TAG
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun decodeTitle(raw: String?): String? {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return null
        if (v.startsWith("base64:")) {
            return runCatching {
                String(Base64.decode(v.removePrefix("base64:"), Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull()
        }
        return v
    }

    private fun parseUserInfo(raw: String?) {
        if (raw.isNullOrBlank()) {
            Prefs.trafficUsed = -1; Prefs.trafficTotal = -1; Prefs.expireAt = -1
            return
        }
        val map = raw.split(";").mapNotNull {
            val (k, v) = it.split("=", limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            k.trim().lowercase() to (v.trim().toDoubleOrNull()?.toLong() ?: return@mapNotNull null)
        }.toMap()
        val up = map["upload"] ?: 0
        val down = map["download"] ?: 0
        Prefs.trafficUsed = up + down
        Prefs.trafficTotal = map["total"] ?: -1
        Prefs.expireAt = map["expire"] ?: -1
    }

    private fun parseNodes(json: String): List<Node> = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val type = o.optString("type")
            Node(
                tag = o.getString("tag"),
                name = o.optString("name"),
                type = type,
                server = o.optString("server"),
                port = o.optInt("port"),
                category = o.optString("category").ifEmpty { Protocols.category(type) },
                detail = o.optString("detail"),
                udp = o.optBoolean("udp", type in Protocols.udpTypes),
            )
        }
    }.getOrDefault(emptyList())
}
