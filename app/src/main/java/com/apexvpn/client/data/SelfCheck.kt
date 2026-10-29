package com.apexvpn.client.data

import com.apexvpn.client.vpn.NetworkMonitor
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * «Не работает?» — самопроверка по шагам: интернет без VPN, подписка, серверы, VPN и сайты.
 * В конце — понятный совет и, если нужно, рабочий сервер, который можно выбрать одним нажатием.
 */
object SelfCheck {
    enum class Mark { Wait, Run, Ok, Warn, Fail }

    data class Step(val title: String, val mark: Mark = Mark.Wait, val detail: String = "")

    data class State(
        val running: Boolean = false,
        val steps: List<Step> = emptyList(),
        /** Итог одной фразой */
        val verdict: String? = null,
        /** Рабочий сервер, который стоит выбрать (tag и название) */
        val fixTag: String? = null,
        val fixTitle: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val titles = listOf("Интернет без VPN", "Подписка", "Серверы", "VPN", "Сайты через VPN")

    private fun set(i: Int, mark: Mark, detail: String = "") {
        val list = _state.value.steps.toMutableList()
        list[i] = list[i].copy(mark = mark, detail = detail)
        _state.value = _state.value.copy(steps = list)
    }

    /** Результат замера серверов — чтобы экран серверов тоже показал свежие цифры. */
    suspend fun run(): Map<String, Int> {
        if (_state.value.running) return emptyMap()
        _state.value = State(running = true, steps = titles.map { Step(it) })
        var pings = emptyMap<String, Int>()
        val problems = mutableListOf<String>()
        try {
            // 1. Интернет без VPN: соединение мимо туннеля, через настоящую сеть
            set(0, Mark.Run)
            val net = rawInternet()
            if (net) set(0, Mark.Ok, "Есть") else {
                set(0, Mark.Fail, "Нет связи — проверьте Wi-Fi или мобильные данные")
                problems += "Нет интернета без VPN: сначала включите Wi-Fi или мобильные данные."
            }

            // 2. Подписка: есть серверы, не истекла, не кончился трафик
            set(1, Mark.Run)
            val sub = Subscription.state.value
            val nowSec = System.currentTimeMillis() / 1000
            when {
                sub.nodes.isEmpty() -> {
                    set(1, Mark.Fail, "В подписке нет серверов")
                    problems += "В подписке нет серверов — обновите её или проверьте ссылку."
                }
                sub.expireAt in 1 until nowSec -> {
                    set(1, Mark.Fail, "Подписка закончилась")
                    problems += "Подписка закончилась — продлите её."
                }
                sub.total > 0 && sub.used >= sub.total -> {
                    set(1, Mark.Fail, "Трафик закончился")
                    problems += "Трафик по подписке закончился."
                }
                else -> set(1, Mark.Ok, "Серверов: ${sub.nodes.size}")
            }

            // 3. Серверы
            if (sub.nodes.isNotEmpty() && net) {
                set(2, Mark.Run, "Проверяем ${sub.nodes.size}…")
                pings = runCatching { Pinger.pingAll(sub.nodes) }.getOrDefault(emptyMap())
                val ok = sub.nodes.count { (pings[it.tag] ?: -1) > 0 }
                when {
                    ok == sub.nodes.size -> set(2, Mark.Ok, "Отвечают все ($ok)")
                    ok > 0 -> set(2, Mark.Warn, "Отвечают $ok из ${sub.nodes.size}")
                    else -> {
                        set(2, Mark.Fail, "Ни один сервер не ответил")
                        problems += "Серверы не отвечают. Возможно, оператор блокирует VPN — попробуйте другую сеть или серверы «LTE-обход»."
                    }
                }
                // Лучший рабочий сервер: сначала обычные, LTE-обход и Россия — если других нет
                val working = sub.nodes.mapNotNull { n -> pings[n.tag]?.takeIf { it > 0 }?.let { n to it } }
                val best = working.filter { !ServerInfo.isSpecial(it.first.name) }.minByOrNull { it.second }
                    ?: working.minByOrNull { it.second }
                val selected = Prefs.selectedTag
                val selectedOk = selected == AUTO_TAG || (pings[selected] ?: -1) > 0
                if (best != null && !selectedOk) {
                    _state.value = _state.value.copy(fixTag = best.first.tag, fixTitle = ServerInfo.title(best.first.name))
                    problems += "Выбранный сервер не отвечает — выберите рабочий."
                }
            } else {
                set(2, Mark.Wait, "Пропущено")
            }

            // 4. VPN
            set(3, Mark.Run)
            val vpnOn = VpnState.status.value == VpnStatus.Started
            if (vpnOn) set(3, Mark.Ok, "Подключён") else set(3, Mark.Warn, "Выключен — включите VPN и проверьте ещё раз")

            // 5. Сайты через VPN
            if (vpnOn) {
                set(4, Mark.Run)
                val ms = LivePing.measure()
                if (ms >= 0) set(4, Mark.Ok, "Открываются · $ms мс") else {
                    set(4, Mark.Fail, "Не открываются")
                    if (_state.value.fixTag == null) {
                        // Сервер «отвечает», но сайты не идут — предложим другой
                        val alt = sub.nodes
                            .filter { it.tag != Prefs.selectedTag && (pings[it.tag] ?: -1) > 0 && !ServerInfo.isSpecial(it.name) }
                            .minByOrNull { pings[it.tag] ?: Int.MAX_VALUE }
                        if (alt != null) _state.value = _state.value.copy(fixTag = alt.tag, fixTitle = ServerInfo.title(alt.name))
                    }
                    problems += "Через VPN сайты не открываются — выберите другой сервер или включите автопилот."
                }
            } else {
                set(4, Mark.Wait, "Нужно включить VPN")
            }

            val verdict = when {
                problems.isNotEmpty() -> problems.first()
                !vpnOn -> "Всё в порядке. Включите VPN — и можно пользоваться."
                else -> "Всё работает ✓"
            }
            _state.value = _state.value.copy(running = false, verdict = verdict)
        } catch (e: kotlinx.coroutines.CancellationException) {
            _state.value = State()
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(running = false, verdict = "Проверка прервалась: ${e.message ?: "ошибка"}")
        }
        return pings
    }

    fun reset() {
        if (!_state.value.running) _state.value = State()
    }

    /** Соединение с 1.1.1.1 или Яндексом через настоящую сеть (мимо VPN). */
    private suspend fun rawInternet(): Boolean = withContext(Dispatchers.IO) {
        listOf("1.1.1.1" to 443, "77.88.8.8" to 443).any { (host, port) ->
            runCatching {
                Socket().use { s ->
                    NetworkMonitor.defaultNetwork?.bindSocket(s)
                    s.connect(InetSocketAddress(host, port), 3000)
                    true
                }
            }.getOrDefault(false)
        }
    }
}
