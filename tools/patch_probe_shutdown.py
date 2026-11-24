#!/usr/bin/env python3
"""Служебное ядро гасим и при выходе приложения — чтобы не оставалось сирот."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/ProbeTunnel.kt"
text = io.open(PATH, encoding="utf-8").read()

OLD_HEAD = "object ProbeTunnel {\n"
NEW_HEAD = (
    "object ProbeTunnel {\n\n"
    "    /** Текущий служебный процесс: гасим его и при выходе из приложения. */\n"
    "    @Volatile\n"
    "    private var running: Process? = null\n\n"
    "    init {\n"
    "        Runtime.getRuntime().addShutdownHook(\n"
    "            Thread({ runCatching { running?.destroyForcibly() } }, \"probe-shutdown\"),\n"
    "        )\n"
    "    }\n"
)

OLD_START = "                ).directory(Platform.dataDir).redirectErrorStream(true).start()\n                try {"
NEW_START = (
    "                ).directory(Platform.dataDir).redirectErrorStream(true).start()\n"
    "                running = process\n"
    "                try {"
)

OLD_FINALLY = (
    "                } finally {\n"
    "                    process.destroy()\n"
    "                    if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()\n"
    "                }"
)
NEW_FINALLY = (
    "                } finally {\n"
    "                    running = null\n"
    "                    process.destroy()\n"
    "                    if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()\n"
    "                }"
)

for old, new, label in (
    (OLD_HEAD, NEW_HEAD, "шапка"),
    (OLD_START, NEW_START, "старт процесса"),
    (OLD_FINALLY, NEW_FINALLY, "finally"),
):
    if old not in text:
        raise SystemExit(f"не найдено: {label}")
    text = text.replace(old, new, 1)

io.open(PATH, "w", encoding="utf-8").write(text)
print("ProbeTunnel: процесс гасится при выходе")
