#!/usr/bin/env python3
"""Переносит ресурсы Android-версии в desktop: R-идентификаторы, строки, шрифты, картинки.

Android-код обращается к ресурсам через `R.string.x` и `Strings(resources)`. Чтобы
UI-файлы переносились без правок, генерируем такой же `R` (Int-константы) и таблицы
строк, а `Strings` в desktop читает их из map вместо `Resources`.
"""
import os
import re
import shutil
import xml.etree.ElementTree as ET

SRC = os.path.expanduser(
    "~/.cline/data/workspaces/chat/winger-review/winger-android/app/src/main/res")
DST = os.path.expanduser(
    "~/.cline/data/workspaces/chat/winger-review/winger-desktop/src/main/resources")
OUT = os.path.expanduser(
    "~/.cline/data/workspaces/chat/winger-review/winger-desktop/src/main/kotlin/pro/wingerapp/vpn/R.kt")


def unescape(value: str) -> str:
    """Android string -> Kotlin literal content (без кавычек)."""
    value = (value.replace("\\'", "'").replace('\\"', '"')
             .replace("\\n", "\n").replace("\\t", "\t"))
    # Экранирование для Kotlin: $ запускает шаблон, \ и " — спецсимволы.
    value = value.replace("\\", "\\\\").replace('"', '\\"')
    value = value.replace("$", "\\$")
    return value


def parse_strings(path):
    tree = ET.parse(path)
    strings, plurals = {}, {}
    for node in tree.getroot():
        name = node.get("name")
        if node.tag == "string":
            strings[name] = unescape("".join(node.itertext()))
        elif node.tag == "plurals":
            plurals[name] = {item.get("quantity"): unescape("".join(item.itertext()))
                             for item in node}
    return strings, plurals


ru_strings, ru_plurals = parse_strings(os.path.join(SRC, "values/strings.xml"))
en_strings, en_plurals = parse_strings(os.path.join(SRC, "values-en/strings.xml"))

drawables = sorted(os.path.splitext(f)[0] for f in os.listdir(os.path.join(SRC, "drawable"))
                   if f.endswith((".png", ".webp", ".jpg")))
fonts = sorted(os.path.splitext(f)[0] for f in os.listdir(os.path.join(SRC, "font")))

lines = ["package pro.wingerapp.vpn", "",
         "/**",
         " * Идентификаторы ресурсов вместо android.R.",
         " *",
         " * Сгенерировано tools/gen_resources.py из app/src/main/res Android-версии.",
         " * Значения — произвольные стабильные Int: UI использует их только как ключи.",
         " */",
         "internal object R {"]

counter = [1]


def block(kind: str, names):
    lines.append(f"    object {kind} {{")
    for name in names:
        lines.append(f"        const val {name} = {counter[0]}")
        counter[0] += 1
    lines.append("    }")
    lines.append("")


block("string", sorted(ru_strings))
block("plurals", sorted(ru_plurals))
block("drawable", drawables)
block("font", fonts)
lines.append("}")
lines.append("")


def emit_table(name: str, mapping: dict, ref: str):
    lines.append(f"internal val {name}: Map<Int, String> = mapOf(")
    for key in sorted(mapping):
        lines.append(f'    R.{ref}.{key} to "{mapping[key]}",')
    lines.append(")")
    lines.append("")


def emit_plurals(name: str, mapping: dict):
    lines.append(f"internal val {name}: Map<Int, Map<String, String>> = mapOf(")
    for key in sorted(mapping):
        items = ", ".join(f'"{q}" to "{mapping[key][q]}"' for q in mapping[key])
        lines.append(f"    R.plurals.{key} to mapOf({items}),")
    lines.append(")")
    lines.append("")


emit_table("RU_STRINGS", ru_strings, "string")
emit_table("EN_STRINGS", en_strings, "string")
emit_plurals("RU_PLURALS", ru_plurals)
emit_plurals("EN_PLURALS", en_plurals)

lines.append("""internal val DRAWABLE_PATHS: Map<Int, String> = mapOf(""")
for name in drawables:
    ext = next(e for e in (".png", ".webp", ".jpg")
               if os.path.exists(os.path.join(SRC, f"drawable/{name}{e}")))
    lines.append(f'    R.drawable.{name} to "drawable/{name}{ext}",')
lines.append(")")
lines.append("")
lines.append("internal val FONT_PATHS: Map<Int, String> = mapOf(")
for name in fonts:
    ext = next(e for e in (".otf", ".ttf") if os.path.exists(os.path.join(SRC, f"font/{name}{e}")))
    lines.append(f'    R.font.{name} to "font/{name}{ext}",')
lines.append(")")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as fh:
    fh.write("\n".join(lines) + "\n")

# Файлы ресурсов рядом с классами: Compose Desktop читает их из classpath.
os.makedirs(os.path.join(DST, "drawable"), exist_ok=True)
os.makedirs(os.path.join(DST, "font"), exist_ok=True)
for f in os.listdir(os.path.join(SRC, "drawable")):
    if f.endswith((".png", ".webp", ".jpg")):
        shutil.copy2(os.path.join(SRC, "drawable", f), os.path.join(DST, "drawable", f))
for f in os.listdir(os.path.join(SRC, "font")):
    if f.endswith((".otf", ".ttf")):
        shutil.copy2(os.path.join(SRC, "font", f), os.path.join(DST, "font", f))

print(f"строк: {len(ru_strings)} ru / {len(en_strings)} en")
print(f"плюралов: {len(ru_plurals)}, drawable: {len(drawables)}, шрифтов: {len(fonts)}")
print(f"-> {OUT}")
