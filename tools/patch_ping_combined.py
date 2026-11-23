#!/usr/bin/env python3
"""Пинг = TCP RTT мимо туннеля, доступность = реальный замер через ядро."""
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


patch(VPN + "AppViewModel.kt", [(
    """                            node.name to gate.withPermit {
                                runCatching { coreDelay(proxyTag = tag, timeoutMs = 3000) }.getOrNull()
                            }""",
    """                            node.name to gate.withPermit {
                                // Доступность проверяем реальным запросом через этот
                                // сервер: порт может быть открыт, а прокси не работать.
                                val usable = runCatching {
                                    coreDelay(proxyTag = tag, timeoutMs = 3000)
                                }.getOrNull() != null
                                if (!usable) return@withPermit null
                                // Число берём из TCP-рукопожатия мимо туннеля:
                                // оно близко к тому, что показывает Incy. Если
                                // привязка к интерфейсу недоступна — оставляем
                                // значение, измеренное через ядро.
                                InterfacePing.ping(node.host, node.port)
                                    ?: runCatching { coreDelay(proxyTag = tag, timeoutMs = 3000) }.getOrNull()
                            }""",
    "замер в туннеле",
)])

# «n/a» вместо «нет»: так просил пользователь и так принято в клиентах.
patch(VPN + "ui/HomeTab.kt", [(
    """                        when {
                            ms == null -> noneLabel
                            // −1: сервер не ответил. −2: узел через CDN, честного
                            // числа нет — показываем «—», а не расстояние до края.
                            ms == -1 -> noneLabel
                            ms == -2 -> unknownLabel
                            else -> ms.toString()
                        },""",
    """                        when {
                            // Сервер реально недоступен как прокси.
                            ms == null -> "n/a"
                            ms == -1 -> "n/a"
                            // −2: узел через CDN, честного числа нет.
                            ms == -2 -> unknownLabel
                            else -> ms.toString()
                        },""",
    "подпись n/a",
)])

print("готово")
