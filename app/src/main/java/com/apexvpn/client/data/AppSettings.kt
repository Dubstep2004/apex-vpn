package com.apexvpn.client.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class Settings(
    // Маршрутизация
    val mode: RouteMode = RouteMode.Smart,
    val services: Set<String> = ServiceCatalog.defaultEnabled,
    val customDomains: List<String> = emptyList(),   // всегда через VPN
    val directDomains: List<String> = emptyList(),   // всегда напрямую
    val hideFromBanks: Boolean = true,
    val adBlock: Boolean = false,
    // Приложения
    val appsMode: AppsMode = AppsMode.AllApps,
    val apps: Set<String> = emptySet(),
    // DNS
    val dns: DnsChoice = DnsChoice.Cloudflare,
    val customDns: String = "",
    val kids: Boolean = false,
    // Подключение
    val ipv6: Boolean = false,
    val profile: ConnProfile = ConnProfile.Normal,
    val failover: Boolean = true,
    val wifiRules: Boolean = false,
    val trustedWifi: Set<String> = emptySet(),
    val share: Boolean = false,
    val sharePort: Int = 2080,
    val shareUser: String = "apex",
    val sharePass: String = "",
    // Серверы
    val favorites: Set<String> = emptySet(),          // по названию сервера
    // Уведомления и фон
    val speedInNotification: Boolean = true,
    val expiryAlerts: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    val crashReports: Boolean = true,
    val refreshHours: Int = 12,
    // Внешний вид
    val theme: ThemeMode = ThemeMode.Dark,
    val accent: AccentColor = AccentColor.Violet,
)

/** Настройки маршрутизации и приложения. Хранятся одним JSON, изменения сразу видны всем. */
object AppSettings {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val loaded = sp.getString("json", null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }
            ?: Settings(refreshHours = Prefs.updateIntervalHours)
        _state.value = if (loaded.sharePass.isEmpty()) loaded.copy(sharePass = randomPassword()) else loaded
        save()
    }

    fun update(change: Settings.() -> Settings) {
        _state.value = _state.value.change()
        Prefs.updateIntervalHours = _state.value.refreshHours
        save()
    }

    private fun save() = sp.edit { putString("json", toJson(_state.value).toString()) }

    /** Возвращает null при успехе или текст ошибки. */
    fun addDomain(raw: String, direct: Boolean): String? {
        val d = normalizeDomain(raw) ?: return "Введите адрес сайта, например twitch.tv"
        val s = _state.value
        if (d in s.customDomains || d in s.directDomains) return "Этот сайт уже есть в списках"
        update { if (direct) copy(directDomains = directDomains + d) else copy(customDomains = customDomains + d) }
        return null
    }

    fun removeDomain(domain: String) = update {
        copy(customDomains = customDomains - domain, directDomains = directDomains - domain)
    }

    fun toggleFavorite(name: String) = update {
        copy(favorites = if (name in favorites) favorites - name else favorites + name)
    }

    /** Настройки для ядра (routeOptions в core/apexcore/config.go). */
    fun coreOptions(rulesDir: String?): String = JSONObject().apply {
        val s = _state.value
        put("mode", s.mode.id)
        put("services", JSONArray(s.services.toList()))
        put("customDomains", JSONArray(s.customDomains))
        put("directDomains", JSONArray(s.directDomains))
        put("rulesDir", rulesDir ?: "")
        put("dns", s.dns.id)
        put("customDns", s.customDns)
        put("kids", s.kids)
        put("adBlock", s.adBlock)
        put("ipv6", s.ipv6)
        put("profile", s.profile.id)
        put(
            "share",
            JSONObject().put("enabled", s.share).put("port", s.sharePort)
                .put("username", s.shareUser).put("password", s.sharePass),
        )
    }.toString()

    fun normalizeDomain(raw: String): String? {
        var d = raw.trim().lowercase()
        d.indexOf("://").takeIf { it >= 0 }?.let { d = d.substring(it + 3) }
        d = d.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        d = d.trim('.').removePrefix("www.")
        val ok = d.contains('.') && d.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        return d.takeIf { ok }
    }

    fun randomPassword(): String {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        val r = SecureRandom()
        return (1..10).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }

    // ─── JSON ───────────────────────────────────────────────────────────

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
    private fun list(v: Collection<String>) = JSONArray(v.toList())

    private fun toJson(s: Settings) = JSONObject().apply {
        put("mode", s.mode.id); put("services", list(s.services))
        put("custom", list(s.customDomains)); put("direct", list(s.directDomains))
        put("banks", s.hideFromBanks); put("adBlock", s.adBlock)
        put("appsMode", s.appsMode.id); put("apps", list(s.apps))
        put("dns", s.dns.id); put("customDns", s.customDns); put("kids", s.kids)
        put("ipv6", s.ipv6); put("profile", s.profile.id); put("failover", s.failover)
        put("wifiRules", s.wifiRules); put("trustedWifi", list(s.trustedWifi))
        put("share", s.share); put("sharePort", s.sharePort); put("shareUser", s.shareUser); put("sharePass", s.sharePass)
        put("favorites", list(s.favorites))
        put("speedNotif", s.speedInNotification); put("expiryAlerts", s.expiryAlerts)
        put("autoUpdates", s.autoCheckUpdates); put("crashReports", s.crashReports); put("refreshHours", s.refreshHours)
        put("theme", s.theme.id); put("accent", s.accent.id)
    }

    private fun fromJson(o: JSONObject): Settings {
        val d = Settings()
        fun arr(k: String, def: Collection<String>) = o.optJSONArray(k)?.strings() ?: def.toList()
        return Settings(
            mode = RouteMode.of(o.optString("mode", d.mode.id)),
            services = arr("services", d.services).toSet(),
            customDomains = arr("custom", d.customDomains),
            directDomains = arr("direct", d.directDomains),
            hideFromBanks = o.optBoolean("banks", d.hideFromBanks),
            adBlock = o.optBoolean("adBlock", d.adBlock),
            appsMode = AppsMode.of(o.optString("appsMode", d.appsMode.id)),
            apps = arr("apps", d.apps).toSet(),
            dns = DnsChoice.of(o.optString("dns", d.dns.id)),
            customDns = o.optString("customDns", d.customDns),
            kids = o.optBoolean("kids", d.kids),
            ipv6 = o.optBoolean("ipv6", d.ipv6),
            profile = ConnProfile.of(o.optString("profile", d.profile.id)),
            failover = o.optBoolean("failover", d.failover),
            wifiRules = o.optBoolean("wifiRules", d.wifiRules),
            trustedWifi = arr("trustedWifi", d.trustedWifi).toSet(),
            share = o.optBoolean("share", d.share),
            sharePort = o.optInt("sharePort", d.sharePort),
            shareUser = o.optString("shareUser", d.shareUser),
            sharePass = o.optString("sharePass", d.sharePass),
            favorites = arr("favorites", d.favorites).toSet(),
            speedInNotification = o.optBoolean("speedNotif", d.speedInNotification),
            expiryAlerts = o.optBoolean("expiryAlerts", d.expiryAlerts),
            autoCheckUpdates = o.optBoolean("autoUpdates", d.autoCheckUpdates),
            crashReports = o.optBoolean("crashReports", d.crashReports),
            refreshHours = o.optInt("refreshHours", d.refreshHours),
            theme = ThemeMode.of(o.optString("theme", d.theme.id)),
            accent = AccentColor.of(o.optString("accent", d.accent.id)),
        )
    }
}
