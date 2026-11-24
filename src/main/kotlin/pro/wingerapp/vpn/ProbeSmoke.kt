package pro.wingerapp.vpn

import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Проверка служебного туннеля: `WINGER_SMOKE_PROBE=1 ./gradlew run`.
 *
 * Поднимает отдельный процесс sing-box без tun и меряет реальный выход через
 * каждый сервер — ровно то, что делает кнопка «проверить пинг» при выключенном VPN.
 */
object ProbeSmoke {
    fun enabled(): Boolean = System.getenv("WINGER_SMOKE_PROBE") == "1"

    fun run(): Unit = runBlocking {
        val cached = SubCache.load()
        if (cached == null) {
            println("PROBE: кэша подписки нет")
            exitProcess(1)
        }
        val servers = readHapp(cached.raw).servers
        val started = System.currentTimeMillis()
        val result = ProbeTunnel.check(cached.raw, servers)
        val spent = System.currentTimeMillis() - started
        val alive = result.values.count { it != null }
        println("PROBE: серверов ${servers.size}, работают $alive, за $spent мс")
        result.entries.take(8).forEach { (name, ms) ->
            println("PROBE: $name → ${ms?.let { "$it мс" } ?: "n/a"}")
        }
        exitProcess(0)
    }
}
