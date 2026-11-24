#!/usr/bin/env python3
"""Синхронная защита от параллельных проверок пинга (старт + кнопка)."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/AppViewModel.kt"
text = io.open(PATH, encoding="utf-8").read()

OLD_FIELD = """    /** Когда в последний раз проверяли реальный выход через серверы. */
    private var availabilityAt = 0L"""

NEW_FIELD = """    /** Когда в последний раз проверяли реальный выход через серверы. */
    private var availabilityAt = 0L

    /**
     * Синхронная защита от параллельных проверок: два быстрых вызова
     * (старт + нажатие) успевали поднять два служебных ядра, и одно падало.
     */
    @Volatile
    private var pingCheckRunning = false"""

OLD_GUARD = """        // Уже идёт проверка (например, стартовая) — второй раз не запускаем.
        if (_state.value.pinging) return
        val snapshot = _state.value"""

NEW_GUARD = """        // Уже идёт проверка (стартовая или ручная) — второй раз не запускаем.
        if (pingCheckRunning) return
        pingCheckRunning = true
        val snapshot = _state.value"""

OLD_EMPTY = """            return
        }
        scope.launch {
            _state.update { it.copy(pinging = true) }
            VpnLog.add("проверка пинга: старт")"""

NEW_EMPTY = """            pingCheckRunning = false
            return
        }
        scope.launch {
            _state.update { it.copy(pinging = true) }
            VpnLog.add("проверка пинга: старт")"""

OLD_FINALLY = """            } finally {
                _state.update { it.copy(pinging = false) }
                VpnLog.add("проверка пинга: готово")
            }"""

NEW_FINALLY = """            } finally {
                pingCheckRunning = false
                _state.update { it.copy(pinging = false) }
                VpnLog.add("проверка пинга: готово")
            }"""

for old, new, label in (
    (OLD_FIELD, NEW_FIELD, "поле"),
    (OLD_GUARD, NEW_GUARD, "проверка"),
    (OLD_EMPTY, NEW_EMPTY, "пустой список"),
    (OLD_FINALLY, NEW_FINALLY, "finally"),
):
    if old not in text:
        raise SystemExit(f"не найдено: {label}")
    text = text.replace(old, new, 1)

io.open(PATH, "w", encoding="utf-8").write(text)
print("AppViewModel: защита от параллельных проверок")
