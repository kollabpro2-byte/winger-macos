#!/usr/bin/env python3
"""Привилегированный путь тоже должен получать подписку для outbound'ов замера."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/TunnelController.kt"
text = io.open(PATH, encoding="utf-8").read()


def sub(old, new, label):
    global text
    if old not in text:
        raise SystemExit(f"не найдено: {label}")
    text = text.replace(old, new, 1)


sub(
    "                if (usePrivileged()) {\n                    startPrivileged(xrayJson, gen)",
    "                if (usePrivileged()) {\n                    startPrivileged(xrayJson, rawSubscription, gen)",
    "вызов startPrivileged",
)

sub(
    "    private suspend fun startPrivileged(xrayJson: String, gen: Int) {",
    "    private suspend fun startPrivileged(xrayJson: String, raw: String, gen: Int) {",
    "сигнатура startPrivileged",
)

sub(
    "        val config = coreConfig(\n"
    "            xrayJson, shimPort, useTun = true, privileged = true,\n"
    "            raw = Store.lastRaw(),\n"
    "        )",
    "        val config = coreConfig(\n"
    "            xrayJson, shimPort, useTun = true, privileged = true,\n"
    "            raw = raw.ifBlank { Store.lastRaw() },\n"
    "        )\n"
    "        if (raw.isNotBlank()) Store.lastRaw(raw)",
    "конфиг демона",
)

io.open(PATH, "w", encoding="utf-8").write(text)
print("TunnelController: подписка в демоне")
