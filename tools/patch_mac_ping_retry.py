#!/usr/bin/env python3
"""Повтор прохода, если первый дал «все n/a» (ядро ещё прогревается)."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/AppViewModel.kt"
text = io.open(PATH, encoding="utf-8").read()

OLD = """            val now = System.currentTimeMillis()
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
            _state.update { it.copy(pings = result) }"""

NEW = """            suspend fun measure(): Map<String, Int?> {
                val now = System.currentTimeMillis()
                val gate = Semaphore(6)
                return coroutineScope {
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
            }

            var result = measure()
            // Если не ответил никто, скорее всего ядро ещё «разогревается»:
            // сбрасываем отметки и пробуем ещё раз. Иначе список на 30 секунд
            // выглядел бы полностью мёртвым.
            if (TunnelController.pingTagCount() > 0 && result.values.none { it != null }) {
                synchronized(availabilityLock) { availability.clear() }
                delay(1_500)
                result = measure()
            }
            _state.update { it.copy(pings = result) }"""

if OLD not in text:
    raise SystemExit("блок замера не найден")
io.open(PATH, "w", encoding="utf-8").write(text.replace(OLD, NEW, 1))
print("AppViewModel: повтор прохода")
