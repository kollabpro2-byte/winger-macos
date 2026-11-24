#!/usr/bin/env python3
"""macOS пинг, часть 1: кэш доступности и сброс при переключении."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/AppViewModel.kt"
text = io.open(PATH, encoding="utf-8").read()


def sub(old, new, label):
    global text
    if old not in text:
        raise SystemExit(f"не найдено: {label}")
    text = text.replace(old, new, 1)


sub(
    "private const val PING_TTL_MS = 5 * 60 * 1000L",
    "private const val PING_TTL_MS = 5 * 60 * 1000L\n\n"
    "/**\n"
    " * Сколько живёт результат проверки «работает ли выход через сервер».\n"
    " * Проверка ходит в интернет через сам сервер, поэтому делаем её редко и\n"
    " * кэшируем: иначе список n/a «мигает» на каждом обновлении.\n"
    " */\n"
    "private const val AVAILABILITY_TTL_MS = 10 * 60 * 1000L",
    "константа доступности",
)

sub(
    "    /** Кэш замеров: имя ноды → (время, мс). Живёт [PING_TTL_MS]. */",
    "    /** Доступность: имя ноды → (время проверки, работает ли выход). */\n"
    "    private val availability = HashMap<String, Pair<Long, Boolean>>()\n"
    "    private val availabilityLock = Any()\n\n"
    "    /** Кэш замеров: имя ноды → (время, мс). Живёт [PING_TTL_MS]. */",
    "поля кэша",
)

sub(
    "    fun clearPingCache() {\n        synchronized(pingCacheLock) { pingCache.clear() }\n    }",
    "    fun clearPingCache() {\n"
    "        synchronized(pingCacheLock) { pingCache.clear() }\n"
    "        synchronized(availabilityLock) { availability.clear() }\n"
    "    }",
    "clearPingCache",
)

io.open(PATH, "w", encoding="utf-8").write(text)
print("часть 1 готова")
