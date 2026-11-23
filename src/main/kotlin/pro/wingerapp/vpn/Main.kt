package pro.wingerapp.vpn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import pro.wingerapp.vpn.net.Api
import pro.wingerapp.vpn.ui.AppTheme
import pro.wingerapp.vpn.ui.HomeScreen
import pro.wingerapp.vpn.ui.LoginScreen
import pro.wingerapp.vpn.ui.Splash
import pro.wingerapp.vpn.ui.WingerFonts
import java.awt.Dimension

/**
 * Точка входа десктопного клиента.
 *
 * На Android запуском занималась `MainActivity` (Application, тема, splash,
 * ViewModel). Здесь то же самое делает `main()`: готовит журнал и сеть,
 * восстанавливает тему из настроек и открывает окно.
 *
 * Окно повторяет пропорции телефона: интерфейс проектировался под ширину
 * 360–430 dp, и на широком окне он растягивался бы непропорционально.
 */
fun main() {
    VpnLog.init()
    Diag.install()
    VpnLog.setPeriodMinutes(Store.logClearMinutes())
    Diag.harvestCrash()
    Api.init()
    AppTheme.mode = AppTheme.fromStored(Store.themeMode())

    // Режим проверки вёрстки без окна: WINGER_SNAPSHOT=/tmp/wd ./gradlew run
    if (Snapshot.enabled()) {
        Snapshot.renderAll(System.getenv("WINGER_SNAPSHOT")!!)
        return
    }
    // Служебная проверка конфига ядром: WINGER_DUMP_CONFIG=/tmp/core.json ./gradlew run
    if (ConfigDump.enabled()) {
        ConfigDump.run(System.getenv("WINGER_DUMP_CONFIG")!!)
        return
    }
    // Дымовой тест туннеля: WINGER_SMOKE=1 ./gradlew run
    if (TunnelSmoke.enabled()) {
        TunnelSmoke.run()
        return
    }

    val vm = AppViewModel()
    val hwid = DesktopHwid.value()

    // VPN мог остаться включённым с прошлого запуска (особенно через системный
    // демон) — возвращаем состояние до открытия окна, чтобы UI не мигал «выкл».
    TunnelController.resumeSaved()

    application {
        val state = rememberWindowState(
            width = 420.dp,
            height = 900.dp,
            position = WindowPosition(Alignment.Center),
        )
        Window(
            onCloseRequest = {
                TunnelController.stop()
                vm.close()
                exitApplication()
            },
            title = "Winger VPN",
            state = state,
        ) {
            window.minimumSize = Dimension(360, 640)
            Root(vm, hwid)
        }
    }
}

@Composable
private fun Root(vm: AppViewModel, hwid: String) {
    val signedIn by vm.signedIn.collectAsState()
    val ready by vm.ready.collectAsState()

    // Регистрация и подписка уходят с идентификатором устройства, а экран входа
    // показывается раньше, чем главная начинает загрузку.
    LaunchedEffect(hwid) { vm.attachDevice(hwid) }

    WingerFonts {
        when {
            // Пока читается сессия из файла — заставка, без мигания экраном входа.
            !ready -> Splash()
            !signedIn -> LoginScreen(
                vm = vm,
                hwid = hwid,
                disconnect = { TunnelController.stop() },
                onOk = { vm.signedIn() },
            )
            else -> HomeScreen(
                vm = vm,
                hwid = hwid,
                connect = { config -> TunnelController.start(config) },
                disconnect = { TunnelController.stop() },
                onLogout = {
                    TunnelController.stop()
                    TunnelState.clearError()
                },
            )
        }
    }
}
