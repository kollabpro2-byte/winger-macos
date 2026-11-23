#!/usr/bin/env python3
"""Пинги считаются по кэшу серверов, без запроса подписки; обновление раз в 5 минут."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/ui/HomeScreen.kt"
text = io.open(PATH, encoding="utf-8").read()

OLD = """    // Пинги обновляем, когда туннель выключен: только тогда TCP-замер честный.
    LaunchedEffect(on) {
        if (!on) vm.refresh(ping = true)
    }
    LaunchedEffect(on) {
        while (true) {
            delay(5 * 60 * 1000L)
            if (!on && AppVisibility.visible) vm.refresh(ping = true)
        }
    }"""

NEW = """    // Пинги считаем по уже загруженному списку, без обращения к панели:
    // при включённом туннеле — только выбранный сервер (через ядро), при
    // выключенном — все. Раз в 5 минут обновляем, чтобы числа не застывали.
    LaunchedEffect(on) { vm.refreshPings() }
    LaunchedEffect(on) {
        while (true) {
            delay(5 * 60 * 1000L)
            if (AppVisibility.visible) vm.refreshPings()
        }
    }"""

if OLD not in text:
    raise SystemExit("блок пингов не найден")
io.open(PATH, "w", encoding="utf-8").write(text.replace(OLD, NEW, 1))
print("HomeScreen: пинги через refreshPings")
