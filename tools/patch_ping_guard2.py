#!/usr/bin/env python3
"""Синхронная защита от параллельных проверок пинга (старт + кнопка)."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/AppViewModel.kt"
text = io.open(PATH, encoding="utf-8").read()

PAIRS = [
    (
        "    private val availability = HashMap<String, Pair<Long, Boolean>>()\n"
        "    private val availabilityLock = Any()",
        "    private val availability = HashMap<String, Pair<Long, Boolean>>()\n"
        "    private val availabilityLock = Any()\n\n"
        "    /**\n"
        "     * Синхронная защита от параллельных проверок: два быстрых вызова\n"
        "     * (старт + нажатие) успевали поднять два служебных ядра, и одно падало.\n"
        "     */\n"
        "    @Volatile\n"
        "    private var pingCheckRunning = false",
        "поле",
    ),
    (
        "        // Уже идёт проверка (например, стартовая) — второй раз не запускаем.\n"
        "        if (_state.value.pinging) return\n"
        "        val snapshot = _state.value",
        "        // Уже идёт проверка (стартовая или ручная) — второй раз не запускаем.\n"
        "        if (pingCheckRunning) return\n"
        "        pingCheckRunning = true\n"
        "        val snapshot = _state.value",
        "проверка",
    ),
    (
        '            return\n'
        '        }\n'
        '        scope.launch {\n'
        '            _state.update { it.copy(pinging = true) }\n'
        '            VpnLog.add("проверка пинга: старт")',
        '            pingCheckRunning = false\n'
        '            return\n'
        '        }\n'
        '        scope.launch {\n'
        '            _state.update { it.copy(pinging = true) }\n'
        '            VpnLog.add("проверка пинга: старт")',
        "пустой список",
    ),
    (
        '            } finally {\n'
        '                _state.update { it.copy(pinging = false) }\n'
        '                VpnLog.add("проверка пинга: готово")\n'
        '            }',
        '            } finally {\n'
        '                pingCheckRunning = false\n'
        '                _state.update { it.copy(pinging = false) }\n'
        '                VpnLog.add("проверка пинга: готово")\n'
        '            }',
        "finally",
    ),
]

for old, new, label in PAIRS:
    if old not in text:
        raise SystemExit(f"не найдено: {label}")
    text = text.replace(old, new, 1)

io.open(PATH, "w", encoding="utf-8").write(text)
print("AppViewModel: защита от параллельных проверок")
