#!/usr/bin/env python3
"""Пинг проверяется только при открытии и по кнопке; без промежуточных TCP-цифр."""
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


patch(VPN + "AppViewModel.kt", [
    # При ping=true состояние «идёт проверка» ведёт checkPings, а не refresh.
    ("                it.copy(pinging = ping, refreshing = !ping, pings = if (ping) emptyMap() else it.pings)",
     "                it.copy(refreshing = !ping, pings = if (ping) emptyMap() else it.pings)",
     "старт refresh"),
    # Вместо «тихого» TCP-замера — полноценная проверка (ядро или служебный туннель).
    ("""            if (ping) {
                result.nodes?.let {
                    // Замер пингов — это N параллельных TCP-подключений. На старте
                    // они конкурировали с первым кадром и скроллом, поэтому даём
                    // интерфейсу устояться и только потом меряем.
                    delay(PING_SETTLE_MS)
                    pingAll(it)
                }
            } else {""",
     """            if (ping) {
                if (result.nodes != null) {
                    // Проверка доступности настоящая: при включённом VPN — через
                    // его ядро, при выключенном — через служебный туннель.
                    delay(PING_SETTLE_MS)
                    checkPings()
                }
            } else {""",
     "проверка в refresh"),
    ("            _state.update { it.copy(pinging = false, refreshing = false) }",
     "            _state.update { it.copy(refreshing = false) }",
     "финал refresh"),
    # refreshPings теперь ведёт себя как кнопка проверки.
    ("""    fun refreshPings() {
        val snapshot = _state.value
        if (snapshot.servers.isEmpty()) return
        scope.launch {
            _state.update { it.copy(pinging = true) }
            runCatching { pingAll(SubConfig(snapshot.raw, snapshot.servers)) }
            _state.update { it.copy(pinging = false) }
        }
    }""",
     """    fun refreshPings() = checkPings()""",
     "refreshPings"),
])

patch(VPN + "ui/HomeScreen.kt", [
    # Убираем автопроверку при возврате сети.
    ("""    // A new network means a new route: re-probe latency so the list does not keep
    // the numbers measured on the previous Wi-Fi.
    // Десктоп: системного NetworkCallback нет, поэтому раз в 15 секунд
    // проверяем, вернулась ли сеть после обрыва. Пинги пересчитываем только на
    // переходе «офлайн → онлайн», чтобы не дёргать серверы зря.
    LaunchedEffect(Unit) {
        var wasOnline = Platform.isOnline()
        while (true) {
            delay(15_000)
            val online = Platform.isOnline()
            if (online && !wasOnline && AppVisibility.visible &&
                System.currentTimeMillis() - lastTunnelAt.get() >= 6000L
            ) {
                clearPingCaches()
                vm.refresh(ping = true)
            }
            wasOnline = online
        }
    }

""",
     "",
     "эффект сети"),
    # Проверка конфига без тегов: после перезапуска сразу проверяем пинг.
    ("            kotlinx.coroutines.delay(2_500)\n            vm.clearPingCache()\n            vm.refreshPings()",
     "            kotlinx.coroutines.delay(2_500)\n            vm.clearPingCache()\n            vm.checkPings()",
     "upgrade-эффект"),
    # Только одна проверка — при открытии; без периодических и при переключении.
    ("""    // Ключ включает число серверов: подписка приходит асинхронно, и первый
    // запуск эффекта мог случиться до её загрузки (тогда пинги не считались).
    // Смена списка серверов — единственный повод забыть, кто был мёртв.
    LaunchedEffect(st.servers.size) { vm.clearAvailability() }

    LaunchedEffect(on, st.servers.size) {
        vm.clearPingCache()
        // При выключении демон гасит ядро не мгновенно: если замерить сразу,
        // рукопожатие опять завершит локальный стек и пинги станут 1 мс.
        if (!on) delay(800)
        vm.refreshPings()
    }
    LaunchedEffect(on) {
        while (true) {
            delay(5 * 60 * 1000L)
            if (AppVisibility.visible) vm.refreshPings()
        }
    }
""",
     """    // Проверка пинга — только при открытии приложения и по кнопке.
    // Пока она идёт, числа не показываем: иначе пользователь видит «пинг есть»
    // у всех, а через пару секунд у мёртвых появляется n/a.
    LaunchedEffect(st.servers.size) {
        if (st.servers.isNotEmpty() && st.pings.isEmpty()) vm.checkPings()
    }
""",
     "эффекты пинга"),
])

print("готово")
