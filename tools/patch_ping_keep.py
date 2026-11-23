#!/usr/bin/env python3
"""При включённом туннеле сохраняем честные TCP-замеры, а не переписываем их."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/AppViewModel.kt"
text = io.open(PATH, encoding="utf-8").read()

OLD = """        if (TunnelState.running.value) {
            val gate = Semaphore(4)"""

NEW = """        if (TunnelState.running.value) {
            // Пока туннель включён, честный TCP-замер снаружи невозможен: tun
            // завершает рукопожатие локально. Поэтому сохраняем последние
            // значения, снятые при выключенном VPN (как показывает Incy), и
            // обновляем их, как только туннель выключат.
            val known = _state.value.pings.values.any { it != null && it > 0 }
            if (known) {
                VpnLog.add("пинг: оставляем TCP-замеры (туннель включён)")
                return
            }

            val gate = Semaphore(4)"""
if OLD not in text:
    raise SystemExit("ветка туннеля не найдена")
io.open(PATH, "w", encoding="utf-8").write(text.replace(OLD, NEW, 1))
print("AppViewModel: сохраняем TCP-замеры")
