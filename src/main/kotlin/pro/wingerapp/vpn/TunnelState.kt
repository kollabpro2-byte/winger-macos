package pro.wingerapp.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One source of truth for the tunnel state: the controller publishes, the UI
 * collects. This is what keeps «подключено» from appearing before the core is
 * actually up (the core can fail to start, the port can be taken).
 */
object TunnelState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Жив ли процесс ядра. На Android это был сервис, который система могла
     * убить; на десктопе — дочерний процесс sing-box, который может упасть сам.
     */
    @Volatile
    var serviceAlive: Boolean = false

    /** Идёт восстановление туннеля после падения ядра. */
    @Volatile
    var restoring: Boolean = false

    /** Восстановить сохранённый флаг после перезапуска приложения. */
    fun sync() {
        val persisted = Store.running()
        if (persisted && !serviceAlive) {
            // Флаг устарел: процесса нет. Сбрасываем, чтобы не врать пользователю.
            Store.running(false)
            _running.value = false
            return
        }
        _running.value = persisted
    }

    /** Устаревший флаг «включено»: ядра нет, но в настройках записано обратное. */
    fun looksStale(): Boolean = Store.running() && !serviceAlive

    fun on() {
        _error.value = null
        _running.value = true
    }

    fun off() {
        _running.value = false
    }

    fun fail(message: String?) {
        _running.value = false
        _error.value = message?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun clearError() {
        _error.value = null
    }
}
