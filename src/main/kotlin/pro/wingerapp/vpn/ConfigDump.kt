package pro.wingerapp.vpn

import java.io.File

/**
 * Служебный режим: `WINGER_DUMP_CONFIG=/tmp/core.json ./gradlew run` пишет
 * конфиг ядра для одного демонстрационного сервера и выходит.
 *
 * Зачем: конфиг собирается тем же кодом, что и в бою ([TunnelController.coreConfig]),
 * поэтому `sing-box check -c core.json` проверяет реальную совместимость с ядром,
 * а не отдельную копию логики.
 */
object ConfigDump {
    fun enabled(): Boolean = !System.getenv("WINGER_DUMP_CONFIG").isNullOrBlank()

    fun run(path: String) {
        val config = TunnelController.coreConfig(DEMO_XRAY, shimPort = 0, useTun = false)
        File(path).writeText(config)
        println("конфиг записан: $path (${config.length} байт)")
    }

    /** Реалистичный узел подписки Remnawave: VLESS + REALITY + Vision. */
    internal val DEMO_XRAY = """
        {
          "remarks": "Нидерланды",
          "outbounds": [
            {
              "protocol": "vless",
              "settings": {
                "vnext": [
                  {
                    "address": "nl.wingerapp.pro",
                    "port": 443,
                    "users": [
                      {
                        "id": "11111111-2222-3333-4444-555555555555",
                        "encryption": "none",
                        "flow": "xtls-rprx-vision"
                      }
                    ]
                  }
                ]
              },
              "streamSettings": {
                "network": "tcp",
                "security": "reality",
                "realitySettings": {
                  "serverName": "www.microsoft.com",
                  "publicKey": "jNXHt1yRo0vDuchQlIP6Z0ZvjT3KtzVI-T4E7RoLJS0",
                  "shortId": "0123456789abcdef",
                  "fingerprint": "chrome"
                }
              }
            }
          ]
        }
    """.trimIndent()
}
