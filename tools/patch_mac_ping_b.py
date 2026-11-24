#!/usr/bin/env python3
"""macOS пинг, часть 2: единый замер и кэшированная проверка доступности."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/AppViewModel.kt"
text = io.open(PATH, encoding="utf-8").read()


def sub(old, new, label):
    global text
    if old not in text:
        raise SystemExit(f"не найдено: {label}")
    text = text.replace(old, new, 1)


OLD = '''        if (TunnelState.running.value) {
            // Ядро после старта догружает rule-set'ы, и первые замеры могут не
            // пройти вовсе. Тогда ждём и повторяем: иначе «нет» висело бы до
            // следующего обновления через 5 минут.
            val gate = Semaphore(4)
            var result: Map<String, Int?> = emptyMap()
            for (attempt in 1..3) {
                result = coroutineScope {
                    parsed.servers.map { node ->
                        async(Dispatchers.IO) {
                            val tag = TunnelController.pingTag(node.name)
                                ?: return@async node.name to null
                            node.name to gate.withPermit {
                                // Доступность проверяем реальным запросом через этот
                                // сервер: порт может быть открыт, а прокси не работать.
                                val usable = runCatching {
                                    coreDelay(proxyTag = tag, timeoutMs = 3000)
                                }.getOrNull() != null
                                if (!usable) return@withPermit null
                                // Число берём из TCP-рукопожатия мимо туннеля:
                                // оно близко к тому, что показывает Incy. Если
                                // привязка к интерфейсу недоступна — оставляем
                                // значение, измеренное через ядро.
                                InterfacePing.ping(node.host, node.port)
                                    ?: runCatching { coreDelay(proxyTag = tag, timeoutMs = 3000) }.getOrNull()
                            }
                        }
                    }.awaitAll()
                }.toMap()
                if (result.values.any { it != null }) break
                if (attempt < 3) delay(2_000)
            }
            _state.update { it.copy(pings = result) }
            val known = result.values.count { it != null }
            VpnLog.add("пинг через ядро: $known из ${parsed.servers.size} серверов ответили")
            return
        }
'''

NEW = '''        if (TunnelState.running.value) {
            // Туннель включён. Число — TCP-рукопожатие мимо туннеля (быстро и
            // близко к Incy), доступность — реальный запрос через сервер, но с
            // кэшем: иначе «n/a» мигает на каждом обновлении.
            val now = System.currentTimeMillis()
            val gate = Semaphore(6)
            val result = coroutineScope {
                parsed.servers.map { node ->
                    async(Dispatchers.IO) {
                        val tag = TunnelController.pingTag(node.name)
                            ?: return@async node.name to null
                        node.name to gate.withPermit {
                            val usable = availabilityOf(node.name, tag, now)
                            if (!usable) return@withPermit null
                            InterfacePing.ping(node.host, node.port)
                                ?: runCatching { coreDelay(proxyTag = tag, timeoutMs = 2_000) }.getOrNull()
                        }
                    }
                }.awaitAll()
            }.toMap()
            _state.update { it.copy(pings = result) }
            val known = result.values.count { it != null }
            VpnLog.add("пинг через ядро: $known из ${parsed.servers.size} серверов ответили")
            return
        }
'''

sub(OLD, NEW, "ветка туннеля")

sub(
    "    private fun str(id: Int): String = pro.wingerapp.vpn.ui.stringsNow()[id]",
    '''    /**
     * Работает ли выход через сервер. Кэш на 10 минут + один повтор: единичный
     * таймаут не должен превращать живой сервер в «n/a».
     */
    private suspend fun availabilityOf(name: String, tag: String, now: Long): Boolean {
        synchronized(availabilityLock) { availability[name] }
            ?.takeIf { now - it.first < AVAILABILITY_TTL_MS }
            ?.let { return it.second }

        val first = runCatching { coreDelay(proxyTag = tag, timeoutMs = 2_000) }.getOrNull() != null
        val ok = first || runBlockingRetry(tag)
        synchronized(availabilityLock) {
            availability[name] = System.currentTimeMillis() to ok
        }
        return ok
    }

    private suspend fun runBlockingRetry(tag: String): Boolean {
        delay(300)
        return runCatching { coreDelay(proxyTag = tag, timeoutMs = 2_000) }.getOrNull() != null
    }

    private fun str(id: Int): String = pro.wingerapp.vpn.ui.stringsNow()[id]''',
    "availabilityOf",
)

io.open(PATH, "w", encoding="utf-8").write(text)
print("часть 2 готова")
