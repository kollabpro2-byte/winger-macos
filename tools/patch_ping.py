#!/usr/bin/env python3
"""Пинг через ядро при включённом tun, автообновление и масштаб статистики."""
import io

VPN = "src/main/kotlin/pro/wingerapp/vpn/"
UI = VPN + "ui/"


def patch(path, pairs):
    text = io.open(path, encoding="utf-8").read()
    for old, new, label in pairs:
        if old not in text:
            raise SystemExit(f"{path}: не найдено — {label}")
        text = text.replace(old, new, 1)
    io.open(path, "w", encoding="utf-8").write(text)
    print(path.split("/")[-1], "ok")


# 1. Замер через Clash API ядра.
patch(VPN + "Ping.kt", [(
    "private const val CONNECT_TIMEOUT_MS = 1200",
    """/**
 * Задержка через ядро: Clash API `/proxies/{tag}/delay`.
 *
 * Зачем: при включённом tun TCP-рукопожатие завершает локальный стек ядра
 * (gvisor), поэтому прямой `tcpPing` из приложения даёт ~1 мс у любого сервера —
 * это честное время до ядра, а не до сервера. Здесь запрос реально проходит
 * через выбранный outbound до `generate_204`, и число отражает путь до сервера.
 */
fun coreDelay(
    proxyTag: String = "proxy",
    timeoutMs: Int = 3000,
    url: String = "https://www.gstatic.com/generate_204",
): Int? = runCatching {
    val encoded = java.net.URLEncoder.encode(url, "UTF-8")
    val connection = (
        java.net.URL("http://127.0.0.1:9090/proxies/$proxyTag/delay?timeout=$timeoutMs&url=$encoded")
            .openConnection() as java.net.HttpURLConnection
        )
    connection.connectTimeout = 1_500
    connection.readTimeout = timeoutMs + 1_500
    connection.requestMethod = "GET"
    if (connection.responseCode !in 200..299) return null
    val body = connection.inputStream.bufferedReader().readText()
    org.json.JSONObject(body).optInt("delay", -1).takeIf { it > 0 }
}.getOrNull()

private const val CONNECT_TIMEOUT_MS = 1200""",
    "coreDelay",
)])

# 2. pingAll: при включённом туннеле — только через ядро.
patch(VPN + "AppViewModel.kt", [(
    "    private suspend fun pingAll(parsed: SubConfig) {",
    """    private suspend fun pingAll(parsed: SubConfig) {
        // Туннель включён: прямой TCP-пинг врёт (~1 мс, рукопожатие завершает
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
""",
    "ветка туннеля",
)])

# 3. Автообновление пингов: при выключении и раз в 5 минут без туннеля.
patch(UI + "HomeScreen.kt", [(
    "    LaunchedEffect(Unit) {\n        while (true) {\n            delay(60 * 60 * 1000L)",
    """    // Пинги обновляем, когда туннель выключен: только тогда TCP-замер честный.
    LaunchedEffect(on) {
        if (!on) vm.refresh(ping = true)
    }
    LaunchedEffect(on) {
        while (true) {
            delay(5 * 60 * 1000L)
            if (!on && AppVisibility.visible) vm.refresh(ping = true)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60 * 60 * 1000L)""",
    "автообновление пингов",
)])

print("готово")
