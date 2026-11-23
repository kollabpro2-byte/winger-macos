#!/usr/bin/env python3
"""Пинг всех серверов через теги outbound'ов (как Incy) и передача подписки в connect."""
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


# 1. pingAll: задержка каждого сервера через его outbound.
old_branch = """        // Туннель включён: прямой TCP-пинг врёт (~1 мс, рукопожатие завершает
        // локальный стек ядра). Честно измеряем только выбранный сервер —
        // запросом через ядро; для остальных показываем «—», а не выдуманное 1.
        if (TunnelState.running.value) {
            val selected = _state.value.selected
            val delay = withContext(Dispatchers.IO) { runCatching { coreDelay() }.getOrNull() }
            _state.update { state ->
                state.copy(pings = parsed.servers.associate { node ->
                    node.name to if (node.name == selected) delay else null
                })
            }
            VpnLog.add("пинг через туннель: ${delay?.let { "$it мс" } ?: "нет ответа"}")
            return
        }
"""

new_branch = """        // Туннель включён: прямой TCP-пинг врёт (~1 мс, рукопожатие завершает
        // локальный стек ядра). Как в Happ/Incy, просим задержку у самого ядра
        // для КАЖДОГО сервера: в конфиге на них заведены отдельные outbound'ы,
        // а Clash API меряет реальный запрос через нужный сервер.
        if (TunnelState.running.value) {
            val gate = Semaphore(4)
            val fresh = coroutineScope {
                parsed.servers.map { node ->
                    async(Dispatchers.IO) {
                        val tag = TunnelController.pingTag(node.name) ?: return@async node.name to null
                        node.name to gate.withPermit {
                            runCatching { coreDelay(tag = tag, timeoutMs = 3000) }.getOrNull()
                        }
                    }
                }.awaitAll()
            }.toMap()
            _state.update { it.copy(pings = fresh) }
            val known = fresh.values.count { it != null }
            VpnLog.add("пинг через ядро: $known из ${parsed.servers.size} серверов ответили")
            return
        }
"""

patch(VPN + "AppViewModel.kt", [(old_branch, new_branch, "ветка туннеля")])

# 2. HomeScreen: connect получает и подписку.
patch(VPN + "ui/HomeScreen.kt", [
    ("    connect: (String) -> Unit,", "    connect: (String, String) -> Unit,", "тип connect"),
    ("            connect(config)", "            connect(config, vm.rawSubscription())", "автоподключение"),
    ("if (config != null) connect(config)", "if (config != null) connect(config, vm.rawSubscription())", "переключение"),
])

# 3. Main: прокидка подписки в контроллер.
patch(VPN + "Main.kt", [(
    "                connect = { config -> TunnelController.start(config) },",
    "                connect = { config, raw -> TunnelController.start(config, raw) },",
    "connect в Main",
)])

# 4. VM: доступ к подписке для connect.
patch(VPN + "AppViewModel.kt", [(
    "    fun clearPingCache() {",
    """    /** Сырая подписка: нужна для outbound'ов замера пинга. */
    fun rawSubscription(): String = _state.value.raw

    fun clearPingCache() {""",
    "rawSubscription",
)])

print("готово")
