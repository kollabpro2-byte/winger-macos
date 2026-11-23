#!/usr/bin/env python3
"""Повтор замера, если ядро ещё не готово: 0 ответов — ждём и пробуем снова."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/AppViewModel.kt"
text = io.open(PATH, encoding="utf-8").read()

OLD = """            val gate = Semaphore(4)
            val fresh = coroutineScope {
                parsed.servers.map { node ->
                    async(Dispatchers.IO) {
                        val tag = TunnelController.pingTag(node.name) ?: return@async node.name to null
                        node.name to gate.withPermit {
                            runCatching { coreDelay(proxyTag = tag, timeoutMs = 3000) }.getOrNull()
                        }
                    }
                }.awaitAll()
            }.toMap()
            _state.update { it.copy(pings = fresh) }
            val known = fresh.values.count { it != null }
            VpnLog.add("пинг через ядро: $known из ${parsed.servers.size} серверов ответили")
            return"""

NEW = """            // Ядро после старта догружает rule-set'ы, и первые замеры могут не
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
                                runCatching { coreDelay(proxyTag = tag, timeoutMs = 3000) }.getOrNull()
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
            return"""

if OLD not in text:
    raise SystemExit("блок замера не найден")
io.open(PATH, "w", encoding="utf-8").write(text.replace(OLD, NEW, 1))
print("AppViewModel: повтор замера")
