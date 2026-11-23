package pro.wingerapp.vpn

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import pro.wingerapp.vpn.ui.AppTheme
import pro.wingerapp.vpn.ui.HomeScreen
import pro.wingerapp.vpn.ui.LoginScreen
import pro.wingerapp.vpn.ui.Splash
import pro.wingerapp.vpn.ui.WingerFonts
import java.io.File

/**
 * Снимает экраны без окна: `WINGER_SNAPSHOT=/tmp/wd ./gradlew run`.
 *
 * Зачем: на CI и в среде без дисплея (или с заблокированным экраном) нельзя
 * сделать скриншот окна, а проверить вёрстку после переноса на десктоп нужно.
 * `ImageComposeScene` рисует тот же самый интерфейс в память, результат —
 * обычный PNG, который можно сравнить глазами.
 *
 * Режим включается только переменной окружения и на обычный запуск не влияет.
 */
object Snapshot {
    fun enabled(): Boolean = !System.getenv("WINGER_SNAPSHOT").isNullOrBlank()

    fun renderAll(dirPath: String) {
        val dir = File(dirPath).apply { mkdirs() }
        val shots = listOf("mid", "dark", "light")

        shots.forEach { mode ->
            AppTheme.mode = when (mode) {
                "dark" -> pro.wingerapp.vpn.ui.ThemeMode.DARK
                "light" -> pro.wingerapp.vpn.ui.ThemeMode.LIGHT
                else -> pro.wingerapp.vpn.ui.ThemeMode.MID
            }
            render(File(dir, "splash-$mode.png")) { Splash() }
            render(File(dir, "login-$mode.png")) {
                LoginScreen(
                    vm = AppViewModel(),
                    hwid = "snapshot",
                    disconnect = {},
                    onOk = {},
                )
            }
            render(File(dir, "home-off-$mode.png")) { demoHome(on = false) }
            render(File(dir, "home-on-$mode.png")) { demoHome(on = true) }
        }
        AppTheme.mode = AppTheme.fromStored(Store.themeMode())
        println("снимки: ${dir.absolutePath}")
    }

    /** Главная с демонстрационной подпиской: проверяет списки, трафик, орла. */
    @androidx.compose.runtime.Composable
    private fun demoHome(on: Boolean) {
        val vm = AppViewModel()
        vm.seedForSnapshot(
            plan = pro.wingerapp.vpn.net.Sub(
                name = "Pro",
                expires = "2027-03-01",
                url = "",
                devices = 3,
                devicesUsed = 2,
                trafficUsedGb = 47.0,
                trafficLimitGb = 300.0,
                unlimitedTraffic = false,
            ),
            servers = DEMO_SERVERS,
            selected = "Автопилот",
            pings = mapOf(
                "Автопилот" to 42, "Нидерланды" to 38, "Германия" to 61,
                "Финляндия" to 54, "LTE 4 · обход" to null,
            ),
        )
        if (on) TunnelState.on() else TunnelState.off()
        HomeScreen(
            vm = vm,
            // Пустой hwid: сетевые запросы в режиме снимка не нужны.
            hwid = "",
            connect = {},
            disconnect = {},
            onLogout = {},
        )
    }

    private val DEMO_SERVERS = listOf(
        ServerNode("Автопилот", "nl.wingerapp.pro", 443, "VLESS TCP REALITY"),
        ServerNode("Нидерланды", "nl.wingerapp.pro", 443, "VLESS TCP REALITY"),
        ServerNode("Германия", "de.wingerapp.pro", 443, "VLESS XHTTP"),
        ServerNode("Финляндия", "fi.wingerapp.pro", 443, "VLESS TCP REALITY"),
        ServerNode("LTE 4 · обход", "gateway.apigw.yandexcloud.net", 443, "VLESS WS TLS"),
    )

    /** 420×900 dp при плотности 2 — те же пропорции, что у окна и телефона. */
    private fun render(target: File, content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = ImageComposeScene(
            width = 840,
            height = 1800,
            density = Density(2f),
        ) {
            WingerFonts { content() }
        }
        try {
            // Несколько кадров: первый только раскладывает композицию, на втором
            // уже видны шрифты и картинки, загруженные из ресурсов.
            scene.render()
            val image = scene.render()
            target.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }
}
