package pro.wingerapp.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Служебный туннель для проверки пинга.
 *
 * Когда основной VPN выключен, проверить «реально ли работает выход через
 * сервер» нечем: ядро не запущено. Поэтому на время проверки поднимаем
 * отдельный процесс sing-box **без tun** — с локальным mixed-входом и Clash API.
 * Он не заворачивает трафик системы, живёт несколько секунд и гасится.
 *
 * Так пинг остаётся честным: числа и доступность проверяются одинаково — и при
 * включённом VPN, и при выключенном.
 */
object ProbeTunnel {

    suspend fun check(raw: String, servers: List<ServerNode>): Map<String, Int?> =
        withContext(Dispatchers.IO) {
            if (raw.isBlank() || servers.isEmpty()) return@withContext emptyMap()

            // Прослойка нужна для шлюзов LTE4/LTE5: без неё их выход не проверить.
            val shim = runCatching {
                Lte4Shim(
                    mode = Store.lte4Mode(),
                    initialStreams = Store.lte4Streams(),
                    maxStreams = Store.lte4StreamsDefault(),
                    protect = { true },
                ).also { it.start() }
            }.getOrNull()

            val mixedPort = freePort()
            val clashPort = freePort()
            try {
                val opts = TunnelOptions(
                    useMux = Store.useMux(),
                    shimPort = shim?.port ?: 0,
                    ipv6Proxy = Store.ipv6Proxy(),
                    subRouting = RoutingPlan.OFF,
                )
                val (extra, tags) = measurementOutbounds(raw, selected = "", opts)
                if (tags.isEmpty()) return@withContext emptyMap()

                val config = probeConfig(extra, mixedPort, clashPort)
                val file = Platform.file("probe.json")
                file.writeText(config.toString())
                val process = ProcessBuilder(
                    TunnelController.coreBinary().absolutePath, "run", "-c", file.absolutePath,
                ).directory(Platform.dataDir).redirectErrorStream(true).start()
                try {
                    if (!waitApi(clashPort, 8_000)) {
                        VpnLog.add("проверка пинга: служебное ядро не поднялось")
                        return@withContext emptyMap()
                    }
                    measure(tags, clashPort)
                } finally {
                    process.destroy()
                    if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
                }
            } catch (error: Throwable) {
                VpnLog.add("проверка пинга: ${error.javaClass.simpleName}: ${error.message}")
                emptyMap()
            } finally {
                runCatching { shim?.stop() }
            }
        }

    /** Замер по каждому серверу; null — выхода нет. */
    private suspend fun measure(tags: Map<String, String>, port: Int): Map<String, Int?> = coroutineScope {
        val gate = Semaphore(6)
        tags.map { (name, tag) ->
            async(Dispatchers.IO) {
                name to gate.withPermit {
                    val first = runCatching { coreDelay(proxyTag = tag, timeoutMs = 2_000, port = port) }
                        .getOrNull()
                    if (first != null) return@withPermit first
                    kotlinx.coroutines.delay(250)
                    runCatching { coreDelay(proxyTag = tag, timeoutMs = 2_000, port = port) }.getOrNull()
                }
            }
        }.awaitAll().toMap()
    }

    private fun probeConfig(extra: JSONArray, mixedPort: Int, clashPort: Int): JSONObject {
        val outbounds = JSONArray().put(JSONObject().put("type", "direct").put("tag", "direct"))
        for (i in 0 until extra.length()) outbounds.put(extra.getJSONObject(i))
        return JSONObject()
            .put("log", JSONObject().put("disabled", true))
            .put(
                "dns",
                JSONObject()
                    .put(
                        "servers",
                        JSONArray().put(
                            JSONObject().put("type", "udp").put("tag", "dns").put("server", "1.1.1.1"),
                        ),
                    )
                    .put("final", "dns"),
            )
            .put(
                "inbounds",
                JSONArray().put(
                    JSONObject()
                        .put("type", "mixed")
                        .put("listen", "127.0.0.1")
                        .put("listen_port", mixedPort),
                ),
            )
            .put("outbounds", outbounds)
            .put(
                "route",
                JSONObject()
                    .put("default_domain_resolver", "dns")
                    .put("rules", JSONArray().put(JSONObject().put("action", "sniff"))),
            )
            .put(
                "experimental",
                JSONObject().put(
                    "clash_api",
                    JSONObject().put("external_controller", "127.0.0.1:$clashPort"),
                ),
            )
    }

    private fun waitApi(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val ok = runCatching {
                val connection = (URL("http://127.0.0.1:$port/proxies").openConnection() as HttpURLConnection)
                    .apply {
                        connectTimeout = 400
                        readTimeout = 400
                        requestMethod = "GET"
                    }
                connection.responseCode in 200..299
            }.getOrDefault(false)
            if (ok) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun freePort(): Int = runCatching {
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    }.getOrDefault(0)
}
