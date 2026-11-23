#!/usr/bin/env python3
"""Пинг как в Happ/Incy: задержка КАЖДОГО сервера через ядро (Clash API)."""
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


# 1. Outbound'ы для замеров: по одному на каждый сервер подписки.
patch(VPN + "SingBox.kt", [(
    "private fun firstVless(obs: JSONArray): JSONObject? {",
    """/**
 * Дополнительные outbound'ы для замера задержки — по одному на каждый сервер
 * подписки (кроме выбранного, у него уже есть тег `proxy`).
 *
 * Маршрутизация их не использует: они нужны только Clash API
 * (`/proxies/{tag}/delay`), чтобы показывать реальный пинг у ВСЕХ серверов,
 * как Happ/Incy. Транспорт собирается в минимальном виде: один неизвестный
 * ключ XHTTP уронил бы ядро на старте всего конфига.
 */
internal fun measurementOutbounds(
    rawSubscription: String,
    selected: String,
    opts: TunnelOptions,
): Pair<JSONArray, Map<String, String>> {
    val out = JSONArray()
    val tags = LinkedHashMap<String, String>()
    val configs = runCatching { JSONArray(rawSubscription) }.getOrNull() ?: return out to tags
    var index = 0
    for (i in 0 until configs.length()) {
        val cfg = configs.optJSONObject(i) ?: continue
        val name = cfg.optString("remarks")
        if (name.isBlank() || name == selected) continue
        val proxy = firstVless(cfg.optJSONArray("outbounds") ?: continue) ?: continue
        val tag = "ping-$index"
        val converted = runCatching {
            convertVless(
                proxy, opts.useMux, minimalTransport = true,
                dropXhttpFields = opts.dropXhttpFields, ipv6Proxy = opts.ipv6Proxy,
                shimPort = opts.shimPort, tag = tag,
            )
        }.getOrNull() ?: continue
        index++
        out.put(converted)
        tags[name] = tag
    }
    return out to tags
}

private fun firstVless(obs: JSONArray): JSONObject? {""",
    "measurementOutbounds",
)])

# 2. Лёгкий URL для замера: без TLS-рукопожатия числа близки к TCP-пингу.
patch(VPN + "Ping.kt", [(
    '    url: String = "https://www.gstatic.com/generate_204",',
    '    url: String = "http://www.gstatic.com/generate_204",',
    "url замера",
)])

# 3. Сохранение подписки целиком: нужна для outbound'ов замера после перезапуска.
patch(VPN + "Store.kt", [(
    "    fun lastConfig(): String = secretGet(\"lastConfig\")",
    """    /** Вся подписка: из неё строятся outbound'ы для пинга всех серверов. */
    fun lastRaw(): String = secretGet("lastRaw")

    fun lastRaw(raw: String) = secretPut("lastRaw", raw)

    fun lastConfig(): String = secretGet("lastConfig")""",
    "lastRaw",
)])

print("часть 1 готова")
