#!/usr/bin/env python3
"""Если туннель поднят конфигом без замеров — пересобрать его, когда подписка есть."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/ui/HomeScreen.kt"
text = io.open(PATH, encoding="utf-8").read()

OLD = """    // Ключ включает число серверов: подписка приходит асинхронно, и первый
    // запуск эффекта мог случиться до её загрузки (тогда пинги не считались).
    LaunchedEffect(on, st.servers.size) {
        vm.clearPingCache()"""

NEW = """    // Туннель мог быть поднят конфигом без outbound'ов замера (старая версия):
    // как только подписка доступна — пересобираем конфиг, иначе пинг «нет» у всех.
    LaunchedEffect(st.raw.isNotBlank(), on) {
        if (on && st.raw.isNotBlank() && TunnelController.pingTagCount() == 0) {
            vm.config()?.let { connect(it, vm.rawSubscription()) }
        }
    }

    // Ключ включает число серверов: подписка приходит асинхронно, и первый
    // запуск эффекта мог случиться до её загрузки (тогда пинги не считались).
    LaunchedEffect(on, st.servers.size) {
        vm.clearPingCache()"""

if OLD not in text:
    raise SystemExit("эффект не найден")
io.open(PATH, "w", encoding="utf-8").write(text.replace(OLD, NEW, 1))
print("HomeScreen: автообновление конфига с замерами")
