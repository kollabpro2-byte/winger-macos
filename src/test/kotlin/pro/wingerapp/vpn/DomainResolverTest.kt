package pro.wingerapp.vpn

import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Регрессия: подписка с реальным сервером отдаёт адрес прокси, который сразу
 * резолвится в IP. В этом случае раньше `route.default_domain_resolver` не
 * выставлялся, а DNS из подписки требует его — ядро 1.12+ падало с
 * «missing route.default_domain_resolver or domain_resolver in dial fields».
 */
class DomainResolverTest {

    /** Сервер с IP-адресом + DNS и правила из подписки, как в Remnawave. */
    private val xray = """
        {
          "remarks": "Нидерланды",
          "outbounds": [
            {
              "protocol": "vless",
              "settings": {
                "vnext": [
                  {
                    "address": "157.22.172.42",
                    "port": 443,
                    "users": [{ "id": "11111111-2222-3333-4444-555555555555", "encryption": "none" }]
                  }
                ]
              },
              "streamSettings": {
                "network": "xhttp",
                "security": "tls",
                "tlsSettings": { "serverName": "fly.example.com", "fingerprint": "chrome" },
                "xhttpSettings": { "path": "/xhttp" }
              }
            }
          ],
          "dns": {
            "servers": [
              { "address": "https://dns.google/dns-query", "domains": ["geosite:google"] }
            ]
          },
          "routing": {
            "rules": [
              { "type": "field", "domain": ["geosite:private"], "outboundTag": "direct" }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `резолвер есть и его тег существует`() {
        val config = JSONObject(
            toSingBox(xray, TunnelOptions(subRouting = RoutingPlan.RULES_AND_DNS)),
        )
        // Файл для проверки настоящим ядром: sing-box check -c /tmp/wd-sub.json
        File("/tmp/wd-sub.json").writeText(config.toString())

        val route = config.getJSONObject("route")
        val tag = route.optString("default_domain_resolver")
        assertTrue(tag.isNotBlank(), "default_domain_resolver должен быть выставлен")

        val servers = config.getJSONObject("dns").getJSONArray("servers")
        val tags = (0 until servers.length()).map { servers.getJSONObject(it).optString("tag") }
        assertTrue(tag in tags, "тег $tag должен быть среди dns.servers: $tags")

        // Резолвер не должен ходить через прокси: иначе петля при резолве его же адреса.
        val resolver = (0 until servers.length())
            .map { servers.getJSONObject(it) }
            .first { it.optString("tag") == tag }
        assertTrue(resolver.optString("detour") != "proxy", "резолвер не должен иметь detour=proxy")
    }
}
