#!/usr/bin/env python3
"""TunnelController: ветка привилегированного туннеля (macOS-демон / Windows-задача)."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/TunnelController.kt"
text = io.open(PATH, encoding="utf-8").read()


def sub(old, new):
    global text
    if old not in text:
        raise SystemExit("не найдено: " + old[:80].replace("\n", "\\n"))
    text = text.replace(old, new, 1)


# 1. Состояние «работает через системный компонент».
sub(
    "    @Volatile\n    private var trafficJob: Job? = null\n",
    "    @Volatile\n    private var trafficJob: Job? = null\n\n"
    "    /** Текущая сессия поднята системным демоном, а не нашим процессом. */\n"
    "    @Volatile\n    private var privilegedActive = false\n",
)

# 2. Остановка: гасим демон, если он держал туннель.
sub(
    "    private suspend fun stopInternal(notify: Boolean) {\n"
    "        trafficJob?.cancel()\n",
    "    private suspend fun stopInternal(notify: Boolean) {\n"
    "        if (privilegedActive) {\n"
    "            PrivilegedTunnel.stop()\n"
    "            privilegedActive = false\n"
    "        }\n"
    "        trafficJob?.cancel()\n",
)

# 3. Сигнатуры конфига: режим tun задаётся вызывающим.
sub(
    "    internal fun coreConfig(xrayJson: String, shimPort: Int): String {",
    "    internal fun coreConfig(xrayJson: String, shimPort: Int, useTun: Boolean): String {",
)
sub(
    '        base.put("inbounds", org.json.JSONArray().put(inbound()))',
    '        base.put("inbounds", org.json.JSONArray().put(inbound(useTun)))',
)
sub(
    "    private fun inbound(): JSONObject = if (Store.desktopTun()) {",
    "    private fun inbound(useTun: Boolean): JSONObject = if (useTun) {",
)

# 4. Новая ветка и вспомогательные проверки — перед «Конфиг ядра».
sub(
    "    /**\n     * Конфиг ядра: сначала полноценный тун (весь трафик системы), при отсутствии\n",
    "    /** Системный компонент установлен и tun включён в настройках. */\n"
    "    private fun usePrivileged(): Boolean =\n"
    "        PrivilegedTunnel.isSupported() && PrivilegedTunnel.isInstalled() && Store.desktopTun()\n\n"
    "    /**\n"
    "     * Может ли наш собственный процесс поднять tun: на Windows он запускается\n"
    "     * из run.bat с повышением прав, на macOS — только через `sudo` с\n"
    "     * `WINGER_MODE=tun`. Иначе это прокси-режим.\n"
    "     */\n"
    "    private fun processTun(): Boolean = when {\n"
    "        !Store.desktopTun() -> false\n"
    "        System.getenv(\"WINGER_MODE\").equals(\"proxy\", ignoreCase = true) -> false\n"
    "        else -> Platform.isWindows || System.getenv(\"WINGER_MODE\").equals(\"tun\", ignoreCase = true)\n"
    "    }\n\n"
    "    /**\n"
    "     * Поднимает туннель через системный компонент.\n"
    "     *\n"
    "     * Локальная прослойка LTE4 остаётся в нашем процессе: ядро от root\n"
    "     * подключается к 127.0.0.1:<порт> так же, как обычное приложение.\n"
    "     */\n"
    "    private suspend fun startPrivileged(xrayJson: String, gen: Int) {\n"
    "        val shimPort = startShim()\n"
    "        val config = coreConfig(xrayJson, shimPort, useTun = true)\n"
    "        PrivilegedTunnel.configFile.writeText(config)\n"
    "        PrivilegedTunnel.start()\n"
    "        privilegedActive = true\n"
    "        Store.running(true)\n"
    "        Store.lastConfig(xrayJson)\n"
    "        TunnelState.serviceAlive = true\n"
    "        TunnelState.on()\n"
    "        CoreTraffic.reset()\n"
    "        startTrafficPolling(gen)\n\n"
    "        // Демон не возвращает код: проверяем, что ядро реально слушает API.\n"
    "        delay(2_500)\n"
    "        if (gen != generation) return\n"
    "        if (!clashAlive()) {\n"
    "            VpnLog.add(\"системный туннель не ответил на Clash API\")\n"
    "            PrivilegedTunnel.stop()\n"
    "            privilegedActive = false\n"
    "            TunnelState.fail(\"системный туннель не поднялся, подробности в логе\")\n"
    "            Store.running(false)\n"
    "            TunnelState.serviceAlive = false\n"
    "        } else {\n"
    "            VpnLog.add(\"системный туннель поднят: tun, весь трафик\")\n"
    "        }\n"
    "    }\n\n"
    "    /** Отвечает ли Clash API ядра (признак, что ядро живое). */\n"
    "    private fun clashAlive(): Boolean = runCatching {\n"
    "        (URL(\"http://127.0.0.1:9090/connections\").openConnection() as HttpURLConnection).run {\n"
    "            connectTimeout = 1_500\n"
    "            readTimeout = 1_500\n"
    "            responseCode in 200..299\n"
    "        }\n"
    "    }.getOrDefault(false)\n\n"
    "    /**\n     * Конфиг ядра: сначала полноценный тун (весь трафик системы), при отсутствии\n",
)

io.open(PATH, "w", encoding="utf-8").write(text)
print("TunnelController обновлён")
