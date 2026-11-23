package pro.wingerapp.vpn

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.exitProcess

/**
 * Проверка «туннель реально грузит интернет» без UI:
 * `WINGER_SMOKE_ONLINE=1 [WINGER_SMOKE_SERVER=POST] ./gradlew run`.
 *
 * Нужна для LTE4/LTE5: на macOS их прослойка идёт через tun, и ошибку
 * «POST'ы замкнулись на свой же прокси» видно только по факту — открывается ли
 * сайт через поднятый туннель.
 */
object TunnelOnlineSmoke {
    fun enabled(): Boolean = System.getenv("WINGER_SMOKE_ONLINE") == "1"

    fun run(): Unit = runBlocking {
        val cached = SubCache.load()
        if (cached == null) {
            println("SMOKE-ONLINE: кэша подписки нет")
            exitProcess(1)
        }
        val needle = System.getenv("WINGER_SMOKE_SERVER").orEmpty().ifBlank { "POST" }
        val nodes = readHapp(cached.raw).servers
        val node = nodes.firstOrNull { it.name.contains(needle, ignoreCase = true) }
        if (node == null) {
            println("SMOKE-ONLINE: сервер с «$needle» не найден; есть: ${nodes.map { it.name }}")
            exitProcess(1)
        }
        val config = runCatching { xrayConfigFor(cached.raw, node.name) }.getOrNull()
        if (config == null) {
            println("SMOKE-ONLINE: не удалось собрать конфиг «${node.name}»")
            exitProcess(1)
        }
        println("SMOKE-ONLINE: сервер «${node.name}»")

        TunnelController.start(config, cached.raw, allowInstall = false)
        delay(12_000)

        val body = runCatching {
            val connection = (URL("https://ipinfo.io/json").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
            }
            if (connection.responseCode !in 200..299) "HTTP ${connection.responseCode}"
            else connection.inputStream.bufferedReader().readText()
        }.getOrElse { "${it.javaClass.simpleName}: ${it.message}" }
        println("SMOKE-ONLINE: ответ=$body")

        TunnelController.stop()
        delay(1_500)
        exitProcess(if (body.contains("\"country\"")) 0 else 2)
    }
}
