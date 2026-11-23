#!/usr/bin/env python3
"""Store: делает persist-функции обычными блоками (иначе тип выводится как Result)."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/Store.kt"

text = io.open(PATH, encoding="utf-8").read()

text = text.replace(
    "    private fun persistSettings() = synchronized(lock) {",
    "    private fun persistSettings() {\n        synchronized(lock) {",
)
text = text.replace(
    "    private fun persistSecrets() = synchronized(lock) {",
    "    private fun persistSecrets() {\n        synchronized(lock) {",
)

# У добавленных блоков на один уровень вложенности больше — закрываем его.
text = text.replace(
    "            atomicWrite(settingsFile, json.toString().toByteArray())\n        }\n    }",
    "            atomicWrite(settingsFile, json.toString().toByteArray())\n        }\n        }\n    }",
)
text = text.replace(
    "            atomicWrite(secretsFile, iv + body)\n        }\n    }",
    "            atomicWrite(secretsFile, iv + body)\n        }\n        }\n    }",
)

io.open(PATH, "w", encoding="utf-8").write(text)
print("Store: persist-функции исправлены")
