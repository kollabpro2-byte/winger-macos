#!/usr/bin/env python3
"""Проверка пинга по кнопке: при выключенном VPN — через служебный туннель; n/a не сбрасывать."""
import io

VPN = "src/main/kotlin/pro/wingerapp/vpn/"


def patch(path, pairs):
    text = io.open(path, encoding="utf-8").read()
    for old, new, label in pairs:
        if old not in text:
            raise SystemExit(f"{path}: не найдено — {label}")
        text = text.replace(old, new, 1)
    io.open(path, "w", encoding="utf-8").write(text)
    print(path.split("/")[-1], "ok")


# 1. Кнопка «проверить пинг»: отдельный путь, который всегда даёт реальные данные.
patch(VPN + "AppViewModel.kt", [(
    "    fun refreshPings() {",
    '''    /**
     * Проверка пинга по кнопке. Если VPN включён — меряем через его ядро,
     * если выключен — поднимаем служебный туннель без tun и меряем реальный
     * выход через каждый сервер. После этого «n/a» остаётся у мёртвых, а не
     * превращается в цифры при отключении VPN.
     */
    fun checkPings() {
        val snapshot = _state.value
        if (snapshot.servers.isEmpty()) return
        scope.launch {
            _state.update { it.copy(pinging = true) }
            if (TunnelState.running.value) {
                runCatching { pingAll(SubConfig(snapshot.raw, snapshot.servers)) }
            } else {
                val measured = runCatching {
                    ProbeTunnel.check(snapshot.raw, snapshot.servers)
                }.getOrElse { emptyMap() }
                if (measured.isNotEmpty()) {
                    val at = System.currentTimeMillis()
                    synchronized(availabilityLock) {
                        measured.forEach { (name, ms) -> availability[name] = at to (ms != null) }
                    }
                    _state.update { state ->
                        state.copy(pings = measured)
                    }
                    VpnLog.add(
                        "пинг (проверка): ${measured.values.count { it != null }} из " +
                            "${snapshot.servers.size} серверов работают",
                    )
                }
            }
            _state.update { it.copy(pinging = false) }
        }
    }

    fun refreshPings() {''',
    "checkPings",
)])

# 2. Отдельный сброс отметок доступности (при смене списка серверов).
patch(VPN + "AppViewModel.kt", [(
    "        synchronized(availabilityLock) { availability.clear() }\n    }",
    "    }\n\n"
    "    /** Сбрасывает отметки доступности: нужен, когда сменился список серверов. */\n"
    "    fun clearAvailability() {\n"
    "        synchronized(availabilityLock) { availability.clear() }\n"
    "    }",
    "clearAvailability",
)])

# 3. Кнопка пинга вызывает checkPings; переключение VPN больше не стирает n/a.
patch(VPN + "ui/HomeScreen.kt", [
    ("                reload = { ping -> vm.refresh(ping) },",
     "                reload = { ping -> if (ping) vm.checkPings() else vm.refresh(false) },",
     "кнопка пинга"),
    ("""    LaunchedEffect(on, st.servers.size) {
        vm.clearPingCache()""",
     """    // Смена списка серверов — единственный повод забыть, кто был мёртв.
    LaunchedEffect(st.servers.size) { vm.clearAvailability() }

    LaunchedEffect(on, st.servers.size) {
        vm.clearPingCache()""",
     "сброс только чисел"),
])

print("готово")
