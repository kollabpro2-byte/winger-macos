#!/usr/bin/env python3
"""Два оставшихся места, где connect вызывается с одним аргументом."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/ui/HomeScreen.kt"
text = io.open(PATH, encoding="utf-8").read()

text = text.replace(
    "                onSelect = { name ->\n"
    "                    vm.select(name)\n"
    "                    vm.config(name)?.let(connect)\n"
    "                },",
    "                onSelect = { name ->\n"
    "                    vm.select(name)\n"
    "                    vm.config(name)?.let { connect(it, vm.rawSubscription()) }\n"
    "                },",
    1,
)

text = text.replace(
    "            2 -> SettingsTab {\n"
    "                // Settings only reach the core through the config: rebuild it.\n"
    "                if (on) vm.config()?.let(connect)\n"
    "            }",
    "            2 -> SettingsTab {\n"
    "                // Settings only reach the core through the config: rebuild it.\n"
    "                if (on) vm.config()?.let { connect(it, vm.rawSubscription()) }\n"
    "            }",
    1,
)

io.open(PATH, "w", encoding="utf-8").write(text)
print("HomeScreen: connect с подпиской во всех местах")
