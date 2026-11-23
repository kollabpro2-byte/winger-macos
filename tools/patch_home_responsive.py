#!/usr/bin/env python3
"""HomeScreen: на широком окне — сайдбар и центрированный контент, на узком — нижнее меню."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/ui/HomeScreen.kt"
text = io.open(PATH, encoding="utf-8").read()

START = "    Box(Modifier.fillMaxSize().background(BgBrush).statusBarsPadding()) {"
END = "\n}\n\n@Composable\ninternal fun NavItem"

start = text.find(START)
end = text.find(END)
if start < 0 or end < 0:
    raise SystemExit("не найдены границы блока HomeScreen")

replacement = '''    // Десктопный режим: на широком окне — сайдбар слева, как в Happ/Incy.
    // Порог 820 dp: уже него телефонная колонка читается лучше.
    val density = LocalDensity.current
    val windowWidthDp = with(density) {
        LocalWindowInfo.current.containerSize.width / density.density
    }
    val wide = windowWidthDp >= 820f

    val content: @Composable () -> Unit = {
        when (tab) {
            0 -> HomeTab(
                hwid = hwid, sub = st.plan, servers = st.servers, pings = st.pings,
                place = st.selected, on = on,
                pinging = st.pinging, refreshing = st.refreshing,
                err = st.error ?: tunnelError, notice = st.notice,
                noticeSeq = st.noticeSeq,
                trafficIntervalSec = trafficSec,
                raw = st.raw, connect = connect, disconnect = disconnect,
                reload = { ping -> vm.refresh(ping) },
                onSelect = { name ->
                    vm.select(name)
                    vm.config(name)?.let(connect)
                },
                onToggle = {
                    // Состояние берём из двух источников: UI-флоу и persisted-флаг
                    // сервиса. На HyperOS они расходятся (сервис переживает
                    // пересоздание активности), и тогда нажатие снова включало
                    // VPN вместо выключения — значок висел до закрытия приложения.
                    if (on || pro.wingerapp.vpn.Store.running()) {
                        disconnect()
                    } else {
                        val config = vm.config()
                        if (config != null) connect(config)
                        else vm.fail(s[R.string.home_select_server_first])
                    }
                },
            )
            1 -> AccountTab(hwid, onLogout = { onLogout(); vm.logout() }) { chat = true }
            2 -> SettingsTab {
                // Settings only reach the core through the config: rebuild it.
                if (on) vm.config()?.let(connect)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(BgBrush).statusBarsPadding()) {
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                DesktopNav(tab, onPick = { tab = it })
                // Контент не растягиваем на всю ширину: телефонные карточки на
                // 1400 px выглядят пустыми, поэтому колонка ограничена и по центру.
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 760.dp).fillMaxHeight()) { content() }
                }
            }
        } else {
            content()
            Row(
                Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().height(60.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xE6081224))
                    .border(1.dp, Line, RoundedCornerShape(20.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItem(s[R.string.nav_home], tab == 0) { tab = 0 }
                NavItem(s[R.string.nav_account], tab == 1) { tab = 1 }
                NavItem(s[R.string.nav_settings], tab == 2) { tab = 2 }
            }
        }
        if (chat) SupportChat { chat = false }
    }
'''

text = text[:start] + replacement + text[end:]

# Импорты для адаптива.
if "import androidx.compose.ui.platform.LocalWindowInfo" not in text:
    text = text.replace(
        "import androidx.compose.ui.text.style.TextAlign",
        "import androidx.compose.ui.platform.LocalWindowInfo\n"
        "import androidx.compose.ui.text.style.TextAlign",
        1,
    )
if "import androidx.compose.ui.platform.LocalDensity" not in text:
    text = text.replace(
        "import androidx.compose.ui.platform.LocalWindowInfo",
        "import androidx.compose.ui.platform.LocalDensity\n"
        "import androidx.compose.ui.platform.LocalWindowInfo",
        1,
    )
if "@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)" not in text:
    text = text.replace(
        "@Composable\nfun HomeScreen(",
        "@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)\n@Composable\nfun HomeScreen(",
        1,
    )

io.open(PATH, "w", encoding="utf-8").write(text)
print("HomeScreen: адаптивный layout")
