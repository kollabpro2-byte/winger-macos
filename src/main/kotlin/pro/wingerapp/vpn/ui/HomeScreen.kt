package pro.wingerapp.vpn.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import pro.wingerapp.vpn.AppViewModel
import pro.wingerapp.vpn.BuildConfig
import pro.wingerapp.vpn.R
import pro.wingerapp.vpn.*
import pro.wingerapp.vpn.net.*

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    hwid: String,
    connect: (String, String) -> Unit,
    disconnect: () -> Unit,
    onLogout: () -> Unit,
) {
    val s = rememberStrings()
    var tab by remember { mutableIntStateOf(0) }
    var chat by remember { mutableStateOf(false) }
    val on by TunnelState.running.collectAsState()
    val tunnelError by TunnelState.error.collectAsState()
    val st by vm.state.collectAsState()

    LaunchedEffect(hwid) { vm.start(hwid) }

    // Когда включается или выключается туннель, система меняет «сеть по
    // умолчанию» — и коллбек ниже срабатывает. Пинговать в этот момент
    // бессмысленно: маршрут только что поменялся, половина серверов не ответит
    // вовремя, список на секунду обнуляется. Поэтому запоминаем время
    // последнего переключения и в такие окна пинг не запускаем.
    val lastTunnelAt = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    LaunchedEffect(on) { lastTunnelAt.set(System.currentTimeMillis()) }

    // Туннель мог быть поднят конфигом без outbound'ов замера (остался от
    // прошлой версии приложения): пересобираем конфиг, чтобы замер пинга
    // вообще мог работать. Сами пинги здесь не считаем — только при открытии
    // приложения и по кнопке, иначе числа переписывались бы «на лету».
    LaunchedEffect(st.raw.isNotBlank(), on) {
        if (on && st.raw.isNotBlank() && TunnelController.pingTagCount() == 0) {
            vm.config()?.let { connect(it, vm.rawSubscription()) }
        }
    }

    // Проверка пинга — один раз при открытии приложения и по кнопке. Пока идёт
    // замер, у серверов крутится индикатор: пользователь не должен видеть
    // «пинг есть у всех», который через пару секунд станет n/a.
    LaunchedEffect(st.servers.size) {
        if (st.servers.isNotEmpty()) vm.checkPingsOnOpen()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60 * 60 * 1000L)
            vm.refresh()
        }
    }

    // Автозапуск после входа (v87): подписка загрузилась, «Автопилот» выбран —
    // включаемся. Небольшая пауза, чтобы пользователь увидел, как кнопка
    // «оживает»: сначала выключенный круг без орла, потом орёл опускается и
    // ободок загорается. Флаг одноразовый: ручное выключение больше не
    // перезапустит туннель.
    LaunchedEffect(st.autoConnect) {
        if (!st.autoConnect) return@LaunchedEffect
        val alreadyOn = on || pro.wingerapp.vpn.Store.running()
        val config = if (alreadyOn) null else vm.config()
        if (!alreadyOn && config != null) {
            // Пауза: пользователь успевает увидеть выключенную кнопку без орла,
            // а затем орёл опускается и ободок загорается.
            delay(450)
            connect(config, vm.rawSubscription())
        }
        // Гасим флаг последним: его изменение перезапустит эффект, и если бы мы
        // сделали это раньше, корутина отменилась бы до вызова connect().
        vm.consumeAutoConnect()
    }

    // Интервал счётчиков трафика читаем при каждом входе на главную: если
    // изменили в настройках, при возврате значение подхватится.
    val trafficSec = remember(tab) { pro.wingerapp.vpn.Store.trafficIntervalSec() }

    // Десктопный режим: на широком окне — сайдбар слева, как в Happ/Incy.
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
                reload = { ping -> if (ping) vm.checkPings(force = true) else vm.refresh() },
                onSelect = { name ->
                    vm.select(name)
                    vm.config(name)?.let { connect(it, vm.rawSubscription()) }
                },
                hideBrand = wide,
                onToggle = {
                    // Состояние берём из двух источников: UI-флоу и persisted-флаг
                    // сервиса. На HyperOS они расходятся (сервис переживает
                    // пересоздание активности), и тогда нажатие снова включало
                    // VPN вместо выключения — значок висел до закрытия приложения.
                    if (on || pro.wingerapp.vpn.Store.running()) {
                        disconnect()
                    } else {
                        val config = vm.config()
                        if (config != null) connect(config, vm.rawSubscription())
                        else vm.fail(s[R.string.home_select_server_first])
                    }
                },
            )
            1 -> AccountTab(hwid, onLogout = { onLogout(); vm.logout() }) { chat = true }
            2 -> SettingsTab {
                // Settings only reach the core through the config: rebuild it.
                if (on) vm.config()?.let { connect(it, vm.rawSubscription()) }
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

}

@Composable
internal fun NavItem(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) TabOn else TabOff,
        fontSize = 14.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/**
 * Боковое меню для широкого окна (десктоп).
 *
 * Повторяет привычную схему Happ/Incy: слева бренд и разделы, справа контент.
 * Нижнее меню при этом не показывается — на большом экране оно выглядит
 * как панель телефона.
 */
@Composable
private fun DesktopNav(tab: Int, onPick: (Int) -> Unit) {
    val s = rememberStrings()
    Column(
        Modifier.width(240.dp).fillMaxHeight()
            .background(Color(0x33000000))
            .border(1.dp, Line)
            .padding(horizontal = 16.dp, vertical = 22.dp),
    ) {
        Text("WINGER", color = Cyan, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "v${BuildConfig.VERSION_NAME}",
            color = Muted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(26.dp))
        listOf(s[R.string.nav_home], s[R.string.nav_account], s[R.string.nav_settings])
            .forEachIndexed { index, label ->
                val active = tab == index
                Text(
                    label,
                    color = if (active) TabOn else TabOff,
                    fontSize = 16.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Purple.copy(alpha = .18f) else Color.Transparent)
                        .clickable { onPick(index) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
    }
}
