#!/usr/bin/env python3
"""Неоновая версия орла для кнопки подключения.

Референс (image (9)): тёмный круг, а внутри — орёл как неоновая вывеска:
левое крыло бирюзовое, правое пурпурное, по контуру светлая «трубка», вокруг
мягкое свечение. Исходник — тот же `drawable/eagle.png`, что и в остальном
интерфейсе, поэтому силуэт совпадает с брендом.
"""
from PIL import Image, ImageDraw, ImageFilter

SRC = "src/main/resources/drawable/eagle.png"
DST = "src/main/resources/drawable/eagle_neon.png"

# 600 px по ширине: в кнопке логотип занимает ~92 dp, на Retina это ~184 px,
# запас втрое — контур остаётся чистым.
OUT_W = 600
# Поле вокруг: в него уходит внешнее свечение, иначе оно обрежется по краю.
PAD = 60

CYAN = (122, 236, 246)
CYAN_DEEP = (26, 148, 178)
MAGENTA = (214, 132, 252)
MAGENTA_DEEP = (134, 46, 196)


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c)


def tint(mask: Image.Image, relief: Image.Image) -> Image.Image:
    """Заливка по маске: слева циан, справа пурпур, рельеф — по яркости исходника.

    Рельеф важен: без него крылья выглядят плоской заливкой, а в референсе
    видны «перья» — светлые рёбра на тёмном теле.
    """
    w, h = mask.size
    src = mask.load()
    lum = relief.load()
    art = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dst = art.load()
    for y in range(h):
        t = y / max(1, h - 1)
        vert = 1.06 - 0.30 * t
        left = lerp(CYAN, CYAN_DEEP, t)
        right = lerp(MAGENTA, MAGENTA_DEEP, t)
        for x in range(w):
            a = src[x, y]
            if not a:
                continue
            s = x / max(1, w - 1)
            # Шов мягкий: крылья сходятся у головы, там же и переход цвета.
            k = max(0.0, min(1.0, (s - 0.46) / 0.08))
            # 0.34 — тёмное тело, 1.15 — светлые рёбра перьев.
            f = 0.46 + (lum[x, y] / 255) * 0.80
            dst[x, y] = (*shade(lerp(left, right, k), vert * f), a)
    return art


def light_edge(mask: Image.Image) -> Image.Image:
    """Светлая «трубка» по контуру — то, что делает неон неоном."""
    inner = mask.filter(ImageFilter.MinFilter(7))
    edge = Image.new("L", mask.size)
    edge.putdata([max(0, a - b) for a, b in zip(mask.getdata(), inner.getdata())])
    edge = edge.filter(ImageFilter.GaussianBlur(1.6))
    w, h = mask.size
    src = edge.load()
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dst = out.load()
    for y in range(h):
        for x in range(w):
            a = src[x, y]
            if not a:
                continue
            s = x / max(1, w - 1)
            k = max(0.0, min(1.0, (s - 0.46) / 0.08))
            # Контур насыщеннее тела, но не белый: белый «выжигает» неон.
            c = lerp((176, 248, 255), (250, 198, 255), k)
            dst[x, y] = (*c, min(255, round(a * 1.6)))
    return out



def main() -> None:
    src = Image.open(SRC).convert("RGBA")
    width = OUT_W
    height = round(width * src.height / src.width)
    art_mask = src.getchannel("A").resize((width, height), Image.LANCZOS)
    relief = src.convert("L").resize((width, height), Image.LANCZOS)

    body = tint(art_mask, relief)
    edge = light_edge(art_mask)

    canvas_size = (width + PAD * 2, height + PAD * 2)
    canvas = Image.new("RGBA", canvas_size, (0, 0, 0, 0))

    # 1. Широкое свечение: размытая копия тела, сильно прозрачная.
    halo = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    halo.paste(body, (PAD, PAD), body)
    halo = halo.filter(ImageFilter.GaussianBlur(width * 0.045))
    halo.putalpha(halo.getchannel("A").point(lambda v: round(v * 0.55)))
    canvas.alpha_composite(halo)

    # 2. Ближнее свечение — плотнее и резче, оно рисует «трубку» изнутри.
    near = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    near.paste(body, (PAD, PAD), body)
    near = near.filter(ImageFilter.GaussianBlur(width * 0.016))
    near.putalpha(near.getchannel("A").point(lambda v: round(v * 0.75)))
    canvas.alpha_composite(near)

    # 3. Само тело и светлый контур.
    canvas.paste(body, (PAD, PAD), body)
    canvas.paste(edge, (PAD, PAD), edge)

    canvas.save(DST)
    print("готово:", DST, canvas.size)


if __name__ == "__main__":
    main()
