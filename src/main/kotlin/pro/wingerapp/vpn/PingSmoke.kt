package pro.wingerapp.vpn

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Проверка пинга «как в приложении»: `WINGER_SMOKE_PING=1 ./gradlew run`.
 *
 * Берёт последнюю подписку из кэша, поднимает туннель тем же кодом, что и UI,
 * и меряет задержку нескольких серверов через Clash API — ровно так, как это
 * делает список серверов. Нужен, чтобы проверять пинг без ручных нажатий.
 */
object PingSmoke {
    fun enabled(): Boolean = System.getenv("WINGER_SMOKE_PING") == "1"

    fun run(): Unit = runBlocking {
        val cached = SubCache.load()
        if (cached == null) {
            println("SMOKE-PING: кэша подписки нет")
            exitProcess(1)
        }
        val config = runCatching { xrayConfigFor(cached.raw, cached.selected) }.getOrNull()
        if (config == null) {
            println("SMOKE-PING: не удалось собрать конфиг выбранного сервера")
            exitProcess(1)
        }
        println("SMOKE-PING: сервер «${cached.selected}», серверов в подписке ${readHapp(cached.raw).servers.size}")

        TunnelController.start(config, cached.raw, allowInstall = false)
        delay(8_000)

        val tags = TunnelController.pingTagCount()
        println("SMOKE-PING: outbound'ов замера $tags")
        println("SMOKE-PING: InterfacePing доступен=${InterfacePing.supported}")
        val nodes = readHapp(cached.raw).servers
        for (node in nodes.take(6)) {
            val tcp = InterfacePing.ping(node.host, node.port)
            val core = TunnelController.pingTag(node.name)?.let { tag ->
                runCatching { coreDelay(proxyTag = tag, timeoutMs = 4000) }.getOrNull()
            }
            // Что увидит пользователь: число показываем только если выход
            // реально работает, иначе «n/a».
            val shown = if (core == null) "n/a" else "${tcp ?: core} мс"
            println("SMOKE-PING: ${node.name} → tcp=${tcp ?: "n/a"} мс, через ядро=${core ?: "n/a"} мс, в списке: $shown")
            InterfacePing.lastError?.let { println("SMOKE-PING: ошибка TCP: $it") }
        }

        TunnelController.stop()
        delay(1_500)
        exitProcess(0)
    }
}
