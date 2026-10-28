package com.apexvpn.client.ui

import com.apexvpn.client.BuildConfig

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexvpn.client.data.AppInfo
import com.apexvpn.client.data.AppSettings
import com.apexvpn.client.data.InstalledApps
import com.apexvpn.client.data.LivePing
import com.apexvpn.client.data.News
import com.apexvpn.client.data.Pinger
import com.apexvpn.client.data.RulesUpdater
import com.apexvpn.client.data.Settings
import com.apexvpn.client.data.SpeedResult
import com.apexvpn.client.data.SpeedTest
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.data.Prefs
import com.apexvpn.client.update.UpdateInfo
import com.apexvpn.client.update.UpdateManager
import com.apexvpn.client.vpn.ApexVpnService
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import io.nekohasekai.apexcore.Apexcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Результат проверки сайта: куда пойдёт трафик. */
data class SiteCheck(val domain: String, val outbound: String, val reason: String)

enum class SpeedPhase { Idle, Ping, Download, Upload, Done, Error }

data class SpeedState(
    val phase: SpeedPhase = SpeedPhase.Idle,
    val live: Double = 0.0,
    val result: SpeedResult = SpeedResult(),
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val subscription = Subscription.state
    val update = UpdateManager.state
    val settings = AppSettings.state
    val news = News.current

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _pings = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pings = _pings.asStateFlow()

    private val _pinging = MutableStateFlow(false)
    val pinging = _pinging.asStateFlow()

    init {
        viewModelScope.launch {
            // После обновления приложения перезагружаем серверы один раз: у новых версий новые поля
            val oldFormat = Subscription.state.value.nodes.isNotEmpty() && Prefs.nodesVersion < BuildConfig.VERSION_CODE
            if (Subscription.needsRefresh() || oldFormat) refresh(silent = true)
        }
        if (AppSettings.value.autoCheckUpdates) {
            viewModelScope.launch { UpdateManager.check(silent = true) }
        }
        viewModelScope.launch { News.refresh() }
        // Отчёты о сбоях с прошлого запуска — сразу, а не через 3 часа
        viewModelScope.launch { runCatching { com.apexvpn.client.data.CrashReporter.uploadPending(getApplication()) } }
        viewModelScope.launch {
            VpnState.notice.collect { n ->
                if (n != null) {
                    _message.value = n
                    VpnState.consumeNotice()
                }
            }
        }
    }

    // ─── Настройки: применяются к работающему VPN сразу ──────────────────

    private fun applyToVpn() = ApexVpnService.reload(getApplication<Application>())

    /** Изменить настройки; [reload] — нужно ли перезапустить туннель. */
    fun change(reload: Boolean = true, block: Settings.() -> Settings) {
        AppSettings.update(block)
        if (reload) applyToVpn()
    }

    /** null — добавлено, иначе текст ошибки. */
    fun addDomain(raw: String, direct: Boolean): String? =
        AppSettings.addDomain(raw, direct).also { if (it == null) applyToVpn() }

    fun removeDomain(d: String) {
        AppSettings.removeDomain(d)
        applyToVpn()
    }

    fun toggleFavorite(name: String) = AppSettings.toggleFavorite(name)

    // ─── Подписка ────────────────────────────────────────────────────────

    fun refresh(silent: Boolean = false) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                Subscription.refresh()
                _pings.value = emptyMap() // номера серверов могли смениться — старые замеры не к тем строкам
                applyToVpn()
                if (!silent) _message.value = "Подписка обновлена"
            } catch (e: Exception) {
                if (!silent) _message.value = e.message ?: "Не удалось обновить подписку"
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Добавить подписку. onDone(null) — успех, иначе текст ошибки. */
    fun setSubscription(url: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                Subscription.setUrl(url)
                _pings.value = emptyMap()
                applyToVpn()
                onDone(null)
            } catch (e: Exception) {
                onDone(e.message ?: "Не удалось загрузить подписку")
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun removeSubscription() {
        ApexVpnService.stop(getApplication<Application>())
        Subscription.remove()
        _pings.value = emptyMap()
    }

    fun select(tag: String) {
        Subscription.select(tag)
        applyToVpn()
    }

    fun pingAll() {
        if (_pinging.value) return
        viewModelScope.launch {
            _pinging.value = true
            try {
                _pings.value = Pinger.pingAll(Subscription.state.value.nodes)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // замер — не повод закрывать приложение
            } finally {
                _pinging.value = false
            }
        }
    }

    // ─── 49. Живой пинг на главном экране ────────────────────────────────

    private val _livePing = MutableStateFlow<List<Int>>(emptyList())
    val livePing = _livePing.asStateFlow()
    private var livePingJob: Job? = null

    fun startLivePing() {
        if (livePingJob?.isActive == true) return
        livePingJob = viewModelScope.launch {
            while (isActive) {
                if (VpnState.status.value == VpnStatus.Started) {
                    val ms = LivePing.measure()
                    _livePing.value = (_livePing.value + ms).takeLast(40)
                } else if (_livePing.value.isNotEmpty()) {
                    _livePing.value = emptyList()
                }
                delay(2000)
            }
        }
    }

    fun stopLivePing() {
        livePingJob?.cancel()
        livePingJob = null
    }

    // ─── 5. Тест скорости ────────────────────────────────────────────────

    private val _speed = MutableStateFlow(SpeedState())
    val speed = _speed.asStateFlow()
    private var speedJob: Job? = null

    fun runSpeedTest() {
        if (speedJob?.isActive == true) return
        speedJob = viewModelScope.launch {
            var r = SpeedResult()
            try {
                _speed.value = SpeedState(SpeedPhase.Ping)
                r = r.copy(pingMs = SpeedTest.ping())
                if (r.pingMs < 0) error("Нет связи с сервером теста")
                _speed.value = SpeedState(SpeedPhase.Download, result = r)
                r = r.copy(downMbps = SpeedTest.download { v -> _speed.value = _speed.value.copy(live = v) })
                _speed.value = SpeedState(SpeedPhase.Upload, result = r)
                r = r.copy(upMbps = SpeedTest.upload { v -> _speed.value = _speed.value.copy(live = v) })
                _speed.value = SpeedState(SpeedPhase.Done, result = r)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _speed.value = SpeedState(SpeedPhase.Error, result = r, error = e.message ?: "Ошибка теста")
            }
        }
    }

    fun cancelSpeedTest() {
        speedJob?.cancel()
        _speed.value = SpeedState()
    }

    // ─── 58. Проверка сайта ──────────────────────────────────────────────

    private val _siteCheck = MutableStateFlow<SiteCheck?>(null)
    val siteCheck = _siteCheck.asStateFlow()

    fun checkSite(domain: String) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val json = withContext(Dispatchers.IO) {
                runCatching { Apexcore.matchRoute(AppSettings.coreOptions(RulesUpdater.activeDir(ctx)), domain) }.getOrNull()
            }
            _siteCheck.value = json?.let {
                runCatching {
                    val o = JSONObject(it)
                    SiteCheck(o.optString("domain"), o.optString("outbound"), o.optString("reason"))
                }.getOrNull()
            }
        }
    }

    // ─── 59. Базы сайтов ─────────────────────────────────────────────────

    private val _rulesUpdating = MutableStateFlow(false)
    val rulesUpdating = _rulesUpdating.asStateFlow()

    fun updateRules() {
        if (_rulesUpdating.value) return
        viewModelScope.launch {
            _rulesUpdating.value = true
            val ok = RulesUpdater.update(getApplication<Application>(), force = true)
            _rulesUpdating.value = false
            if (ok) applyToVpn()
            _message.value = if (ok) "Базы сайтов обновлены" else "Не удалось обновить базы"
        }
    }

    fun rulesUpdatedAt(): Long = RulesUpdater.lastUpdated(getApplication<Application>())

    // ─── 6. Приложения ───────────────────────────────────────────────────

    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    val apps = _apps.asStateFlow()

    fun loadApps() {
        if (_apps.value != null) return
        viewModelScope.launch { _apps.value = InstalledApps.load(getApplication<Application>()) }
    }

    fun validateUserRule(value: String): String? =
        runCatching { Apexcore.validateUserRule(value).ifBlank { null } }.getOrElse { it.message ?: "Неверное правило" }

    fun validateDnsSpec(d: com.apexvpn.client.data.DnsSpec): String? =
        runCatching { Apexcore.validateDNSSpec(d.type, d.server, d.ip).ifBlank { null } }.getOrElse { it.message ?: "Неверный DNS" }

    fun validateDns(url: String): String? =
        runCatching { Apexcore.validateDNS(url).ifBlank { null } }.getOrElse { it.message ?: "Неверный адрес" }

    // ─── Обновления приложения ───────────────────────────────────────────

    fun checkUpdate() {
        com.apexvpn.client.data.Prefs.dismissedUpdateCode = 0
        viewModelScope.launch { UpdateManager.check(silent = false) }
    }

    fun downloadUpdate(info: UpdateInfo) {
        // Нажали «Обновить» — после загрузки сразу откроется установщик
        viewModelScope.launch { UpdateManager.download(getApplication<Application>(), info, autoInstall = true) }
    }

    fun dismissNews() = News.dismiss()

    fun consumeMessage() {
        _message.value = null
    }

    fun showMessage(text: String) {
        _message.value = text
    }
}
