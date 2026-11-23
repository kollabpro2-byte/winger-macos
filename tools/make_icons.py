#!/usr/bin/env python3
"""Иконка приложения из орла: .icns для macOS и .ico для Windows.

Орёл — тот же растр, что внутри интерфейса (drawable/eagle.png), поэтому
иконка в Dock и на рабочем столе совпадает с брендом в приложении.
"""
import io
import os
import subprocess

from PIL import Image

SRC = "src/main/resources/drawable/eagle.png"
ICONSET = "icons/Winger.iconset"
ICNS = "icons/winger.icns"
ICO = "icons/winger.ico"

os.makedirs(ICONSET, exist_ok=True)
os.makedirs("icons", exist_ok=True)

source = Image.open(SRC).convert("RGBA")

# macOS ждёт квадратные иконки с полями: орёл вписан в прозрачный холст.
def square(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    scale = int(size * 0.78)
    art = source.copy()
    art.thumbnail((scale, scale), Image.LANCZOS)
    canvas.paste(art, ((size - art.width) // 2, (size - art.height) // 2), art)
    return canvas


for base in (16, 32, 128, 256, 512):
    square(base).save(os.path.join(ICONSET, f"icon_{base}x{base}.png"))
    square(base * 2).save(os.path.join(ICONSET, f"icon_{base}x{base}@2x.png"))

subprocess.run(["iconutil", "-c", "icns", ICONSET, "-o", ICNS], check=True)

# Windows: многоразмерный .ico.
square(256).save(ICO, format="ICO", sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])

print("готово:", ICNS, ICO)
