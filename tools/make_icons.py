#!/usr/bin/env python3
"""Иконка приложения из орла: .icns для macOS и .ico для Windows.

Орёл — тот же растр, что внутри интерфейса (drawable/eagle.png), поэтому
иконка в Dock и на рабочем столе совпадает с брендом в приложении.

Фон непрозрачный: скруглённый квадрат с «огранкой» — левая половина рамки
синяя, правая фиолетовая (как крылья орла), внутри бело-фиолетовая подложка.
"""
import os
import subprocess

from PIL import Image, ImageDraw

SRC = "src/main/resources/drawable/eagle.png"
ICONSET = "icons/Winger.iconset"
ICNS = "icons/winger.icns"
ICO = "icons/winger.ico"

# Доля холста под сам квадрат: у иконок macOS вокруг скруглённого квадрата
# остаётся поле, иначе Dock «съедает» края.
SQUARE = 0.84
# Толщина огранки относительно стороны квадрата.
RING = 0.055
# Радиус скругления квадрата.
RADIUS = 0.225
# Насколько широко половины рамки переходят друг в друга.
SEAM = 0.06

BLUE_TOP = (78, 176, 240)
BLUE_BOTTOM = (26, 92, 168)
PURPLE_TOP = (168, 92, 235)
PURPLE_BOTTOM = (98, 38, 158)
PAPER_TOP = (255, 255, 255)
PAPER_BOTTOM = (240, 231, 255)
# Орёл не должен касаться огранки: доля стороны квадрата.
EAGLE = 0.70

os.makedirs(ICONSET, exist_ok=True)
os.makedirs("icons", exist_ok=True)

source = Image.open(SRC).convert("RGBA")


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def vertical_gradient(size, top, bottom):
    """Полоса 1×size → растягиваем до size×size: вертикальный градиент."""
    strip = Image.new("RGB", (1, size))
    draw = ImageDraw.Draw(strip)
    for y in range(size):
        draw.point((0, y), fill=lerp(top, bottom, y / max(1, size - 1)))
    return strip.resize((size, size), Image.NEAREST)


def frame_gradient(size, blue_top, blue_bottom, purple_top, purple_bottom):
    """Левая половина кадра синяя, правая фиолетовая, шов мягкий."""
    blue = vertical_gradient(size, blue_top, blue_bottom)
    purple = vertical_gradient(size, purple_top, purple_bottom)
    half = size / 2
    seam = size * SEAM
    mask = Image.new("L", (size, size))
    draw = ImageDraw.Draw(mask)
    for x in range(size):
        t = (x - (half - seam)) / (2 * seam)
        draw.line([(x, 0), (x, size)], fill=round(max(0.0, min(1.0, t)) * 255))
    return Image.composite(purple, blue, mask)


def rounded_mask(size, radius, inset=0):
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle(
        [inset, inset, size - 1 - inset, size - 1 - inset],
        radius=radius,
        fill=255,
    )
    return mask


def icon(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    side = round(size * SQUARE)
    offset = (size - side) // 2
    radius = round(side * RADIUS)
    # В мелких размерах огранку делаем тоньше, а орла крупнее: иначе в Dock
    # и в списке приложений значок превращается в цветное пятно.
    small = size <= 64
    ring = max(2, round(side * (0.04 if small else RING)))
    eagle = 0.80 if small else EAGLE

    # 1. Огранка: сине-фиолетовый скруглённый квадрат.
    square = frame_gradient(
        side, BLUE_TOP, BLUE_BOTTOM, PURPLE_TOP, PURPLE_BOTTOM
    ).convert("RGBA")
    square.putalpha(rounded_mask(side, radius))

    # 2. Внутренняя бело-фиолетовая подложка со скруглением.
    paper = vertical_gradient(side, PAPER_TOP, PAPER_BOTTOM).convert("RGBA")
    paper.putalpha(rounded_mask(side, max(2, radius - ring // 2), inset=ring))
    square.alpha_composite(paper)

    # 3. Фаска: светлая грань по краю — «огранка» читается объёмно.
    if not small:
        bevel = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        bdraw = ImageDraw.Draw(bevel)
        bdraw.rounded_rectangle(
            [ring // 2, ring // 2, side - 1 - ring // 2, side - 1 - ring // 2],
            radius=radius,
            outline=(255, 255, 255, 110),
            width=max(1, ring // 4),
        )
        square.alpha_composite(bevel)

    # 4. Тень подложки: у краёв чуть темнее, рамка отделяется от белого.
    shade = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shade)
    sdraw.rounded_rectangle(
        [ring, ring, side - 1 - ring, side - 1 - ring],
        radius=max(2, radius - ring // 2),
        outline=(48, 24, 92, 30),
        width=max(1, round(ring * 0.5)),
    )
    square.alpha_composite(shade)

    canvas.alpha_composite(square, (offset, offset))

    # 5. Орёл по центру подложки.
    art = source.copy()
    scale = round(side * eagle)
    art.thumbnail((scale, scale), Image.LANCZOS)
    canvas.alpha_composite(
        art,
        (offset + (side - art.width) // 2, offset + (side - art.height) // 2),
    )
    return canvas


for base in (16, 32, 128, 256, 512):
    icon(base).save(os.path.join(ICONSET, f"icon_{base}x{base}.png"))
    icon(base * 2).save(os.path.join(ICONSET, f"icon_{base}x{base}@2x.png"))

subprocess.run(["iconutil", "-c", "icns", ICONSET, "-o", ICNS], check=True)

# Windows: многоразмерный .ico.
icon(256).save(
    ICO,
    format="ICO",
    sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
)

# Превью для проверки вёрстки значка.
icon(512).save("/tmp/winger-icon-preview.png")
print("готово:", ICNS, ICO)

