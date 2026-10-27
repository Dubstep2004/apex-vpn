package com.apexvpn.client.vpn

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnStatus { Stopped, Starting, Started, Paused, Stopping }

/** Общее состояние VPN: сервис, интерфейс, виджет и плитка работают в одном процессе. */
object VpnState {
    private val _status = MutableStateFlow(VpnStatus.Stopped)
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Сообщения для пользователя («сервер не отвечает — включён автопилот»). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Момент подключения (elapsedRealtime), 0 — не подключено */
    private val _connectedAt = MutableStateFlow(0L)
    val connectedAt: StateFlow<Long> = _connectedAt.asStateFlow()

    /** Скорость: загрузка / отдача, байт в секунду */
    private val _speed = MutableStateFlow(0L to 0L)
    val speed: StateFlow<Pair<Long, Long>> = _speed.asStateFlow()

    /** Когда выключить VPN по таймеру (elapsedRealtime), 0 — таймер не задан */
    private val _autoOffAt = MutableStateFlow(0L)
    val autoOffAt: StateFlow<Long> = _autoOffAt.asStateFlow()

    val isOn: Boolean get() = _status.value == VpnStatus.Started || _status.value == VpnStatus.Paused

    internal fun set(status: VpnStatus) {
        val wasOn = _status.value == VpnStatus.Started || _status.value == VpnStatus.Paused
        _status.value = status
        when (status) {
            VpnStatus.Started -> {
                if (!wasOn || _connectedAt.value == 0L) _connectedAt.value = SystemClock.elapsedRealtime()
                _error.value = null
            }
            VpnStatus.Stopped -> {
                _connectedAt.value = 0L
                _speed.value = 0L to 0L
                _autoOffAt.value = 0L
            }
            else -> Unit
        }
    }

    internal fun setSpeed(down: Long, up: Long) {
        _speed.value = down to up
    }

    internal fun fail(message: String?) {
        _error.value = message ?: "Неизвестная ошибка"
    }

    internal fun notice(message: String) {
        _notice.value = message
    }

    fun consumeNotice() {
        _notice.value = null
    }

    /** Выключить VPN через [minutes] минут; 0 — отменить таймер. */
    fun setTimer(minutes: Int) {
        _autoOffAt.value = if (minutes <= 0) 0L else SystemClock.elapsedRealtime() + minutes * 60_000L
    }

    fun clearError() {
        _error.value = null
    }
}
