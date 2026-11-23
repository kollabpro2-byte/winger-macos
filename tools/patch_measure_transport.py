#!/usr/bin/env python3
"""Замерные outbound'ы собираются полным транспортом + повторный пинг после пересборки."""
import io

VPN = "src/main/kotlin/pro/wingerapp/vpn/"


def patch(path, pairs):
    text = io.open(path, encoding="utf-8").read()
    for old, new, label in pairs:
        if old not in text:
            raise SystemExit(f"{path}: не найдено — {label}")
        text = text.replace(old, new, 1)
    io.open(path, "w", encoding="utf-8").write(text)
    print(path.split("/")[-1], "ok")


patch(VPN + "SingBox.kt", [(
    """            convertVless(
                proxy, opts.useMux, minimalTransport = true,
                dropXhttpFields = opts.dropXhttpFields, ipv6Proxy = opts.ipv6Proxy,
                shimPort = opts.shimPort, tag = tag,
            )""",
    """            // Транспорт собираем так же, как у рабочего прокси: у части
            // серверов (XHTTP-релей) обязательны свои заголовки padding/seq,
            // без них замер получает отказ, хотя обычный запуск работает.
            convertVless(
                proxy, opts.useMux, opts.minimalTransport,
                dropXhttpFields = opts.dropXhttpFields, ipv6Proxy = opts.ipv6Proxy,
                shimPort = opts.shimPort, tag = tag,
            )""",
    "полный транспорт замеров",
)])

patch(VPN + "ui/HomeScreen.kt", [(
    """        if (on && st.raw.isNotBlank() && TunnelController.pingTagCount() == 0) {
            vm.config()?.let { connect(it, vm.rawSubscription()) }
        }""",
    """        if (on && st.raw.isNotBlank() && TunnelController.pingTagCount() == 0) {
            vm.config()?.let { connect(it, vm.rawSubscription()) }
            // Даём ядру перезапуститься и сразу пересчитываем пинги уже по тегам.
            kotlinx.coroutines.delay(2_500)
            vm.clearPingCache()
            vm.refreshPings()
        }""",
    "повторный пинг",
)])

print("готово")
