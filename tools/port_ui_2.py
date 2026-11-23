#!/usr/bin/env python3
"""UI, часть 2/2: Settings, HomeScreen, HomeTab, LoginScreen."""
import io
import re

UI = "src/main/kotlin/pro/wingerapp/vpn/ui/"


def patch(name, replacements):
    path = UI + name
    text = io.open(path, encoding="utf-8").read()
    for old, new in replacements:
        if old not in text:
            raise SystemExit(f"{name}: не найдено -> {old[:70]!r}")
        text = text.replace(old, new)
    io.open(path, "w", encoding="utf-8").write(text)
    print(f"{name}: ok")


patch("SettingsTab.kt", [
    ("internal fun SettingsTab(ctx: Context, onApply: () -> Unit) {",
     "internal fun SettingsTab(onApply: () -> Unit) {"),
    ("""                            ctx.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("winger", text))""",
     "                            Platform.copyText(text)"),
])

settings = io.open(UI + "SettingsTab.kt", encoding="utf-8").read()
settings = re.sub(
    r'        SettingsSection\(s\[R\.string\.set_bg\], "bg" in open, onToggle = \{ toggle\("bg"\) \}\) \{.*?\n        \}\n\n',
    "        // На Windows нет «оптимизации батареи»: раздел остался только в Android-сборке.\n\n",
    settings,
    flags=re.S,
)
io.open(UI + "SettingsTab.kt", "w", encoding="utf-8").write(settings)
print("SettingsTab.kt: раздел батареи убран")

patch("HomeScreen.kt", [
    ("    val ctx = LocalContext.current\n", ""),
    ("""    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // 6 секунд — заведомо больше, чем занимает подъём/снятие tun
                // и пересборка маршрутов.
                if (System.currentTimeMillis() - lastTunnelAt.get() < 6000L) return
                // Экран погашен — не будим сеть замерами: результат никто не видит.
                if (!AppVisibility.visible) return
                // Другая сеть — другие адреса: кэш резолва прошлой сети не годится.
                clearPingCaches()
                vm.refresh(ping = true)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }""",
     """    // Десктоп: системного NetworkCallback нет, поэтому раз в 15 секунд
    // проверяем, вернулась ли сеть после обрыва. Пинги пересчитываем только на
    // переходе «офлайн → онлайн», чтобы не дёргать серверы зря.
    LaunchedEffect(Unit) {
        var wasOnline = Platform.isOnline()
        while (true) {
            delay(15_000)
            val online = Platform.isOnline()
            if (online && !wasOnline && AppVisibility.visible &&
                System.currentTimeMillis() - lastTunnelAt.get() >= 6000L
            ) {
                clearPingCaches()
                vm.refresh(ping = true)
            }
            wasOnline = online
        }
    }"""),
    ("                ctx = ctx, hwid = hwid,", "                hwid = hwid,"),
    ("1 -> AccountTab(ctx, hwid,", "1 -> AccountTab(hwid,"),
    ("2 -> SettingsTab(ctx) {", "2 -> SettingsTab {"),
    ("if (chat) SupportChat(ctx) { chat = false }", "if (chat) SupportChat { chat = false }"),
])

patch("HomeTab.kt", [("    ctx: Context,\n", "")])
patch("LoginScreen.kt", [("    ctx: Context,\n", "")])

print("часть 2 готова")
