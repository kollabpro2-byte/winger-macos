#!/usr/bin/env python3
"""Окно ~40% площади экрана, сброс кэша пингов при переключении туннеля."""
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


# 1. Версия схемы размеров окна: старый «на 90%» больше не применяем.
patch(VPN + "Store.kt", [(
    "    /** Размер окна, если пользователь уже менял его вручную. */",
    """    /**
     * Версия правил выбора размера окна. 1 (старая) — «почти на весь экран»,
     * 2 — «около 40% площади». Нужна, чтобы после обновления приложение один раз
     * взяло новый дефолт, а не сохранённый огромный размер.
     */
    fun windowSizingVersion(): Int = getInt("windowSizing", 1)

    fun windowSizingVersion(version: Int) = put("windowSizing", version.toString())

    /** Размер окна, если пользователь уже менял его вручную. */""",
    "версия размеров",
)])

# 2. Новый дефолт: 40% площади = 0.63 от каждой стороны.
patch(VPN + "Main.kt", [(
    """private fun applyWindowSize(window: java.awt.Window) {
    val saved = Store.windowSize()
    if (saved != null) {
        window.setSize(saved.first, saved.second)
        window.setLocationRelativeTo(null)
        return
    }
    val bounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val height = (bounds.height * 0.92).toInt()
    val width = if (bounds.width >= 1100) {
        (bounds.width * 0.92).toInt()
    } else {
        (height * 0.47).toInt().coerceAtMost((bounds.width * 0.92).toInt())
    }
    window.setSize(maxOf(width, 380), maxOf(height, 620))
    window.setLocationRelativeTo(null)
}""",
    """private fun applyWindowSize(window: java.awt.Window) {
    val bounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds

    // Сохранённый размер применяем только по новым правилам: иначе после
    // обновления окно снова открылось бы «на весь экран».
    if (Store.windowSizingVersion() >= 2) {
        val saved = Store.windowSize()
        if (saved != null) {
            window.setSize(saved.first, saved.second)
            window.setLocationRelativeTo(null)
            return
        }
    }

    // ~40% площади экрана: 0.63 от каждой стороны (0.63² ≈ 0.40).
    val width = (bounds.width * 0.63).toInt().coerceIn(380, bounds.width)
    val height = (bounds.height * 0.63).toInt().coerceIn(620, bounds.height)
    window.setSize(width, height)
    window.setLocationRelativeTo(null)
    Store.windowSize(width, height)
    Store.windowSizingVersion(2)
}""",
    "applyWindowSize",
)])

# 3. Кэш пингов можно сбросить: значения, снятые во время работы tun, — мусор.
patch(VPN + "AppViewModel.kt", [(
    "    /** Экран забрал запрос «включись сам» — второй раз он не сработает. */",
    """    /**
     * Сбрасывает кэш пингов. Нужен при переключении туннеля: замеры, сделанные
     * в момент, когда ядро ещё держало tun, показывают ~1 мс и не должны
     * подмешиваться к честным числам ближайшие 5 минут.
     */
    fun clearPingCache() {
        synchronized(pingCacheLock) { pingCache.clear() }
    }

    /** Экран забрал запрос «включись сам» — второй раз он не сработает. */""",
    "clearPingCache",
)])

# 4. UI: сброс кэша и пауза, чтобы ядро успело погасить tun перед замером.
patch(VPN + "ui/HomeScreen.kt", [(
    "    LaunchedEffect(on) { vm.refreshPings() }",
    """    LaunchedEffect(on) {
        vm.clearPingCache()
        // При выключении демон гасит ядро не мгновенно: если замерить сразу,
        // рукопожатие опять завершит локальный стек и пинги станут 1 мс.
        if (!on) delay(800)
        vm.refreshPings()
    }""",
    "LaunchedEffect(on)",
)])

print("готово")
