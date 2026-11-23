package pro.wingerapp.vpn

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlin.system.exitProcess

/**
 * Дымовой тест туннеля: `WINGER_SMOKE=1 ./gradlew run`.
 *
 * Поднимает ядро тем же кодом, что и приложение, и проверяет три вещи:
 * процесс жив, состояние «включено», Clash API отвечает (значит, счётчики
 * трафика будут работать). Сервер в демо-конфиге фальшивый, поэтому проверяется
 * именно запуск и связь с ядром, а не интернет через него.
 */
object TunnelSmoke {
    fun enabled(): Boolean = System.getenv("WINGER_SMOKE") == "1"

    fun run(): Unit = runBlocking {
        TunnelController.start(ConfigDump.DEMO_XRAY, allowInstall = false)
        delay(6_000)

        val alive = TunnelController.isRunning()
        val running = TunnelState.running.value
        val clash = runCatching {
            (java.net.URL("http://127.0.0.1:9090/connections").openConnection() as java.net.HttpURLConnection)
                .let { it.connectTimeout = 2000; it.readTimeout = 2000; it.responseCode }
        }.getOrNull()
        val error = TunnelState.error.value

        println("SMOKE: процесс=$alive состояние=$running clash=$clash ошибка=${error ?: "нет"}")
        println("SMOKE-лог:")
        VpnLog.text().lines().takeLast(10).forEach { println("  $it") }

        TunnelController.stop()
        delay(1_500)
        exitProcess(if (alive && running && clash == 200) 0 else 1)
    }
}
