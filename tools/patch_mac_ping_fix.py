#!/usr/bin/env python3
"""macOS пинг: не кэшировать «не работает» на 10 минут и дожидаться готовности API."""
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


# 1. Публичная проверка готовности Clash API.
patch(VPN + "TunnelController.kt", [(
    "    private fun clashAlive(): Boolean = runCatching {",
    "    /** Отвечает ли Clash API ядра: до его готовности замеры бессмысленны. */\n"
    "    fun coreApiAlive(): Boolean = clashAlive()\n\n"
    "    private fun clashAlive(): Boolean = runCatching {",
    "coreApiAlive",
)])

# 2. Отрицательный результат кэшируем максимум 30 секунд.
patch(VPN + "AppViewModel.kt", [
    ("private const val AVAILABILITY_TTL_MS = 10 * 60 * 1000L",
     "private const val AVAILABILITY_TTL_MS = 10 * 60 * 1000L\n\n"
     "/**\n"
     " * «Не работает» кэшируем совсем недолго. Иначе один ранний таймаут (ядро ещё\n"
     " * не поднялось) делает сервер мёртвым на десять минут.\n"
     " */\n"
     "private const val AVAILABILITY_NEGATIVE_TTL_MS = 30_000L",
     "отрицательный TTL"),
    ("""        synchronized(availabilityLock) { availability[name] }
            ?.takeIf { now - it.first < AVAILABILITY_TTL_MS }
            ?.let { return it.second }""",
     """        synchronized(availabilityLock) { availability[name] }?.let { cached ->
            val ttl = if (cached.second) AVAILABILITY_TTL_MS else AVAILABILITY_NEGATIVE_TTL_MS
            if (now - cached.first < ttl) return cached.second
        }""",
     "чтение кэша"),
])

# 3. Перед замером ждём готовности ядра; иначе не трогаем список.
patch(VPN + "AppViewModel.kt", [(
    """            val now = System.currentTimeMillis()
            val gate = Semaphore(6)
            val result = coroutineScope {""",
    """            // Ядро поднимается ~1–2 с после «туннель поднят». Если спросить
            // API раньше, все серверы получат отказ и список станет n/a.
            if (!TunnelController.coreApiAlive()) {
                delay(2_000)
                if (!TunnelController.coreApiAlive()) {
                    VpnLog.add("пинг: Clash API ещё не готов — оставляем прошлые значения")
                    return
                }
            }

            val now = System.currentTimeMillis()
            val gate = Semaphore(6)
            val result = coroutineScope {""",
    "предпроверка API",
)])

# 4. При выключенном VPN известные «мёртвые» тоже показываем как n/a.
patch(VPN + "AppViewModel.kt", [(
    "        _state.update { it.copy(pings = it.pings + fresh) }",
    """        // Если про сервер уже известно, что выход через него не работает
        // (проверяли при включённом VPN), не показываем ему TCP-пинг.
        val filtered = fresh.mapValues { (name, ms) ->
            val dead = synchronized(availabilityLock) {
                availability[name]?.takeIf { now - it.first < AVAILABILITY_TTL_MS }?.second == false
            }
            if (dead) null else ms
        }
        _state.update { it.copy(pings = it.pings + filtered) }""",
    "учёт n/a без туннеля",
)])

print("готово")
