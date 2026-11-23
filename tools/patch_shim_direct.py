#!/usr/bin/env python3
"""Шлюзы LTE4/LTE5 не должны заходить в туннель: иначе прослойка замыкается сама на себя."""
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


patch(VPN + "SingBox.kt", [(
    "internal fun measurementOutbounds(",
    '''/**
 * Домены шлюзов, с которыми работает прослойка LTE4/LTE5.
 *
 * Их трафик должен идти мимо туннеля: на macOS в tun попадает всё, включая
 * собственные POST/WSS прослойки. Без исключения запрос идёт в прокси, который
 * сам же является этой прослойкой — получается петля, соединения рвутся по
 * таймауту (именно это и видно в логе LTE5 на macOS). На Android петли нет:
 * там сокеты прослойки защищены через VpnService.protect().
 */
internal fun shimHosts(rawSubscription: String): List<String> {
    val configs = runCatching { JSONArray(rawSubscription) }.getOrNull() ?: return emptyList()
    val hosts = LinkedHashSet<String>()
    for (i in 0 until configs.length()) {
        val cfg = configs.optJSONObject(i) ?: continue
        val outbounds = cfg.optJSONArray("outbounds") ?: continue
        for (j in 0 until outbounds.length()) {
            val out = outbounds.optJSONObject(j) ?: continue
            val stream = out.optJSONObject("streamSettings") ?: continue
            val network = stream.optString("network").ifBlank { "tcp" }
            val vnext = out.optJSONObject("settings")?.optJSONArray("vnext")?.optJSONObject(0) ?: continue
            val address = vnext.optString("address")
            if (address.isBlank()) continue
            val wsPath = stream.optJSONObject("wsSettings")?.optString("path").orEmpty()
            val lte4 = network == "ws" && Lte4.isLte4Gateway(address) && Lte4.isGatewayPath(wsPath)
            val xhttp = stream.optJSONObject("xhttpSettings")
            val extra = xhttp?.optJSONObject("extra")
            val extraValues = HashMap<String, Any?>()
            if (extra != null) {
                val keys = extra.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    extraValues[key] = extra.opt(key)
                }
            }
            val lte5 = network in setOf("xhttp", "splithttp") &&
                PostChunkParams.isPostChunksHost(address) &&
                PostChunkParams.isPostChunksMode(xhttp?.optString("mode").orEmpty(), extraValues)
            if (lte4 || lte5) hosts += address
        }
    }
    return hosts.toList()
}

internal fun measurementOutbounds(''',
    "shimHosts",
)])

patch(VPN + "TunnelController.kt", [(
    """        val outbounds = base.getJSONArray("outbounds")
            for (i in 0 until extra.length()) outbounds.put(extra.getJSONObject(i))
            pingTags = if (selectedName.isBlank()) tags else tags + (selectedName to "proxy")
            VpnLog.add("пинг: outbound'ов замера ${extra.length()}")""",
    """        val outbounds = base.getJSONArray("outbounds")
            for (i in 0 until extra.length()) outbounds.put(extra.getJSONObject(i))
            pingTags = if (selectedName.isBlank()) tags else tags + (selectedName to "proxy")
            VpnLog.add("пинг: outbound'ов замера ${extra.length()}")
        }

        // Шлюзы прослойки LTE4/LTE5 — напрямую, иначе на macOS её собственные
        // запросы уходят в туннель и замыкаются на этот же прокси.
        val gateways = if (raw.isBlank()) emptyList() else shimHosts(raw)
        if (gateways.isNotEmpty()) {
            val route = base.getJSONObject("route")
            val old = route.getJSONArray("rules")
            val rules = org.json.JSONArray()
            rules.put(
                JSONObject()
                    .put("action", "route")
                    .put("outbound", "direct")
                    .put("domain", org.json.JSONArray(gateways)),
            )
            for (i in 0 until old.length()) rules.put(old.getJSONObject(i))
            route.put("rules", rules)
            VpnLog.add("шлюзы прослойки напрямую: ${gateways.size}")""",
    "правило для шлюзов",
)])

print("готово")
