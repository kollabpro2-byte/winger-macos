#!/usr/bin/env python3
"""Иконка приложения: .icns для macOS и .ico для Windows.

Рисуем белую скруглённую плитку с «огранкой» — левая половина рамки синяя,
правая фиолетовая, — а в центре тяжёлая буква W тем же градиентом: слева
синяя, справа фиолетовая. Буква набрана Montserrat Black (OFL, лежит в
tools/fonts), поэтому значок одинаково собирается на любой машине.
"""
import os
import subprocess

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ICONSET = "icons/Winger.iconset"
ICNS = "icons/winger.icns"
ICO = "icons/winger.ico"

FONT = "tools/fonts/Montserrat-Black.ttf"
GLYPH = "W"
# Высота буквы относительно стороны квадрата и её отрисовка в высоком
# разрешении: из большого растра уменьшаем — края остаются чистыми.
GLYPH_SCALE = 0.48
GLYPH_SCALE_SMALL = 0.52
GLYPH_EM = 900

# Доля холста под сам квадрат: у иконок macOS вокруг скруглённого квадрата
# остаётся поле, иначе Dock «съедает» края.
SQUARE = 0.84
# Толщина огранки относительно стороны квадрата.
RING = 0.055
# Радиус скругления квадрата.
RADIUS = 0.225
# Насколько широко половины переходят друг в друга.
SEAM = 0.06

BLUE_TOP = (78, 176, 240)
BLUE_BOTTOM = (26, 92, 168)
PURPLE_TOP = (168, 92, 235)
PURPLE_BOTTOM = (98, 38, 158)

# Буква светлее рамки: на белом фоне нужен контраст.
INK_BLUE_TOP = (86, 190, 250)
INK_BLUE_BOTTOM = (18, 78, 162)
INK_PURPLE_TOP = (178, 110, 246)
INK_PURPLE_BOTTOM = (92, 32, 152)

PAPER = (255, 255, 255)

os.makedirs(ICONSET, exist_ok=True)
os.makedirs("icons", exist_ok=True)


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c)


def vertical_gradient(size, top, bottom):
    """Полоса 1×size → растягиваем до size×size: вертикальный градиент."""
    strip = Image.new("RGB", (1, size))
    draw = ImageDraw.Draw(strip)
    for y in range(size):
        draw.point((0, y), fill=lerp(top, bottom, y / max(1, size - 1)))
    return strip.resize((size, size), Image.NEAREST)


def half_gradient(size, left_top, left_bottom, right_top, right_bottom, seam):
    """Левая половина синяя, правая фиолетовая, шов мягкий."""
    left = vertical_gradient(size, left_top, left_bottom)
    right = vertical_gradient(size, right_top, right_bottom)
    half = size / 2
    edge = size * seam
    mask = Image.new("L", (size, size))
    draw = ImageDraw.Draw(mask)
    for x in range(size):
        t = (x - (half - edge)) / (2 * edge)
        draw.line([(x, 0), (x, size)], fill=round(max(0.0, min(1.0, t)) * 255))
    return Image.composite(right, left, mask)


def rounded_mask(size, radius, inset=0):
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle(
        [inset, inset, size - 1 - inset, size - 1 - inset],
        radius=radius,
        fill=255,
    )
    return mask


_glyph = None


def glyph_art() -> Image.Image:
    """Буква W с градиентом: слева синяя, справа фиолетовая, сверху светлее."""
    global _glyph
    if _glyph is not None:
        return _glyph

    font = ImageFont.truetype(FONT, GLYPH_EM)
    bbox = font.getbbox(GLYPH)
    mask = Image.new("L", (bbox[2] - bbox[0] + 80, bbox[3] - bbox[1] + 80), 0)
    ImageDraw.Draw(mask).text(
        (40 - bbox[0], 40 - bbox[1]), GLYPH, font=font, fill=255
    )
    mask = mask.crop(mask.getbbox())

    w, h = mask.size
    src = mask.load()
    art = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dst = art.load()
    for y in range(h):
        t = y / max(1, h - 1)
        # Вертикальная «шлифовка»: сверху свет, снизу тень, как у орла.
        vert = 1.22 - 0.46 * t
        blue = lerp(INK_BLUE_TOP, INK_BLUE_BOTTOM, t)
        purple = lerp(INK_PURPLE_TOP, INK_PURPLE_BOTTOM, t)
        for x in range(w):
            alpha = src[x, y]
            if not alpha:
                continue
            s = x / max(1, w - 1)
            k = max(0.0, min(1.0, (s - (0.5 - SEAM)) / (2 * SEAM)))
            dst[x, y] = (*shade(lerp(blue, purple, k), vert), alpha)

    _glyph = art
    return art


def icon(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    side = round(size * SQUARE)
    offset = (size - side) // 2
    radius = round(side * RADIUS)
    # В мелких размерах огранку делаем тоньше, а букву крупнее: иначе в Dock
    # и в списке приложений значок превращается в цветное пятно.
    small = size <= 64
    ring = max(2, round(side * (0.04 if small else RING)))

    # 1. Огранка: сине-фиолетовый скруглённый квадрат.
    square = half_gradient(
        side, BLUE_TOP, BLUE_BOTTOM, PURPLE_TOP, PURPLE_BOTTOM, SEAM
    ).convert("RGBA")
    square.putalpha(rounded_mask(side, radius))

    # 2. Внутренняя белая подложка: цвет только в огранке и букве.
    paper = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    paper.paste(
        Image.new("RGBA", (side, side), PAPER + (255,)),
        (0, 0),
        rounded_mask(side, max(2, radius - ring // 2), inset=ring),
    )
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

    # 4. Тонкая линия у края подложки, чтобы белое не сливалось с рамкой.
    edge = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    edraw = ImageDraw.Draw(edge)
    edraw.rounded_rectangle(
        [ring, ring, side - 1 - ring, side - 1 - ring],
        radius=max(2, radius - ring // 2),
        outline=(90, 80, 120, 18),
        width=max(1, round(ring * 0.35)),
    )
    square.alpha_composite(edge)

    canvas.alpha_composite(square, (offset, offset))

    # 5. Буква W по центру: слева синяя, справа фиолетовая.
    art = glyph_art()
    target = round(side * (GLYPH_SCALE_SMALL if small else GLYPH_SCALE))
    glyph = art.resize(
        (max(1, round(art.width * target / art.height)), target), Image.LANCZOS
    )
    gx = offset + (side - glyph.width) // 2
    gy = offset + (side - glyph.height) // 2

    if not small:
        # Мягкая тень: буква отделяется от белой подложки.
        pad = max(2, round(side * 0.05))
        shadow = Image.new(
            "RGBA", (glyph.width + pad * 2, glyph.height + pad * 2), (0, 0, 0, 0)
        )
        shadow.paste((30, 18, 62, 64), (pad, pad + max(2, side // 100)), glyph)
        shadow = shadow.filter(ImageFilter.GaussianBlur(max(1, side * 0.012)))
        canvas.alpha_composite(shadow, (gx - pad, gy - pad))

    canvas.alpha_composite(glyph, (gx, gy))
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

