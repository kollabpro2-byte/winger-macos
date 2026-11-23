package pro.wingerapp.vpn

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Пинг как в Happ/Incy: в конфиге должен быть отдельный outbound на каждый
 * сервер подписки, чтобы Clash API мог померять задержку именно через него.
 */
class MeasurementOutboundsTest {

    private val raw = """
        [
          {
            "remarks": "Нидерланды",
            "outbounds": [{
              "protocol": "vless",
              "settings": { "vnext": [{ "address": "nl.example.com", "port": 443,
                "users": [{ "id": "11111111-2222-3333-4444-555555555555", "encryption": "none" }] }] },
              "streamSettings": { "network": "tcp", "security": "tls",
                "tlsSettings": { "serverName": "nl.example.com" } }
            }]
          },
          {
            "remarks": "Германия",
            "outbounds": [{
              "protocol": "vless",
              "settings": { "vnext": [{ "address": "de.example.com", "port": 443,
                "users": [{ "id": "11111111-2222-3333-4444-555555555555", "encryption": "none" }] }] },
              "streamSettings": { "network": "xhttp", "security": "tls",
                "tlsSettings": { "serverName": "de.example.com" },
                "xhttpSettings": { "path": "/xhttp" } }
            }]
          }
        ]
    """.trimIndent()

    @Test
    fun `каждому серверу соответствует тег замера`() {
        val selected = runCatching { JSONArray(raw).getJSONObject(0).toString() }.getOrThrow()
        val config = JSONObject(
            TunnelController.coreConfig(selected, shimPort = 0, useTun = false, raw = raw),
        )
        File("/tmp/wd-ping.json").writeText(config.toString())

        // direct + proxy (выбранный) + один замер на второй сервер
        val outbounds = config.getJSONArray("outbounds")
        assertEquals(3, outbounds.length())
        val tags = (0 until outbounds.length()).map { outbounds.getJSONObject(it).getString("tag") }
        assertTrue("proxy" in tags)
        assertTrue(tags.any { it.startsWith("ping-") })

        // В карте есть и выбранный сервер (proxy), и остальные (ping-N).
        assertEquals("proxy", TunnelController.pingTag("Нидерланды"))
        assertTrue(TunnelController.pingTag("Германия")?.startsWith("ping-") == true)
    }
}
