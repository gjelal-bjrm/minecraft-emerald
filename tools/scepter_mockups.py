#!/usr/bin/env python3
"""
Maquettes du Sceptre d'Arcencium.

Le sceptre doit se lire comme le troisieme membre d'une famille, pas comme un
objet voisin. On ne redessine donc pas son manche : on REPREND les pixels d'or
et de cuir de l'Epee d'Emeraude deja validee, et on remplace la lame par une
hampe surmontee d'une couronne.

Trois variantes a departager :
  S1  couronne fermee   -- les ailes se referment sur le cristal, compact
  S2  couronne ouverte  -- les ailes s'ecartent, le cristal flotte dans le vide
  S3  couronne haute    -- couronne etroite, cristal detache au-dessus

Usage :
    python tools/scepter_mockups.py
"""

import colorsys
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SWORD = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons",
                     "textures", "item", "emerald_sword.png")
PREVIEW = os.path.join(ROOT, "tools", "preview")

S = 32

# Palette reprise pixel pour pixel de l'epee.
DARK = (0x04, 0x18, 0x10)
SHAFT = (0x05, 0x22, 0x15)
SHAFT_HI = (0x0A, 0x42, 0x28)
GOLD_D = (0x70, 0x4C, 0x10)
GOLD_M = (0xC9, 0x96, 0x26)
GOLD_L = (0xF8, 0xD8, 0x70)
WHITE = (0xE7, 0xFF, 0xF4)

# Les cinq cristaux de Fureur, dans l'ordre ou ils s'allument.
CRYSTALS = [(0xFF, 0x61, 0x6B), (0xFF, 0x9C, 0x30), (0x61, 0xC4, 0xFF),
            (0xFF, 0x7D, 0xD6), (0x78, 0xE8, 0xAE)]


def grip_from_sword():
    """Ne garde de l'epee que l'or et le cuir : la garde et le manche."""
    src = Image.open(SWORD).convert("RGBA").crop((0, 0, S, S))
    px = src.load()
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dst = out.load()
    kept = 0
    for y in range(S):
        for x in range(S):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s_, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            # l'or et le cuir vivent entre le rouge et le jaune ; la lame est verte
            is_warm = (h < 0.14 or h > 0.94) and s_ > 0.15
            is_neutral_dark = s_ <= 0.15 and v < 0.30
            if is_warm or is_neutral_dark:
                dst[x, y] = (r, g, b, a)
                kept += 1
    return out, kept


def line(dst, x0, y0, x1, y1, color, thick=1):
    steps = int(max(abs(x1 - x0), abs(y1 - y0))) * 2 + 1
    for i in range(steps + 1):
        t = i / steps
        cx = x0 + (x1 - x0) * t
        cy = y0 + (y1 - y0) * t
        for ox in range(thick):
            for oy in range(thick):
                x, y = int(round(cx)) + ox, int(round(cy)) + oy
                if 0 <= x < S and 0 <= y < S:
                    dst[x, y] = color + (255,)


def outline(img):
    """Cerne d'un pixel sombre tout ce qui est opaque."""
    px = img.load()
    add = []
    for y in range(S):
        for x in range(S):
            if px[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < S and 0 <= ny < S and px[nx, ny][3] != 0:
                    add.append((x, y))
                    break
    for (x, y) in add:
        px[x, y] = DARK + (255,)
    return img


def shaft(dst, x0, y0, x1, y1):
    """Hampe sombre nervuree, dans l'axe diagonal de la lame d'origine."""
    line(dst, x0, y0, x1, y1, SHAFT, thick=2)
    line(dst, x0 + 1, y0 - 1, x1 + 1, y1 - 1, SHAFT_HI, thick=1)


def crystal(dst, cx, cy, radius, lit=1.0):
    """Le cristal prismatique : un losange irise."""
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            if abs(dx) + abs(dy) > radius:
                continue
            x, y = cx + dx, cy + dy
            if not (0 <= x < S and 0 <= y < S):
                continue
            hue = ((dx + radius) / (2 * radius + 1) * 0.6
                   + (dy + radius) / (2 * radius + 1) * 0.4) % 1.0
            v = 1.0 if abs(dx) + abs(dy) < radius else 0.75
            r, g, b = colorsys.hsv_to_rgb(hue, 0.55, v * lit)
            dst[x, y] = (int(r * 255), int(g * 255), int(b * 255), 255)
    if 0 <= cx - 1 < S and 0 <= cy - 1 < S:
        dst[cx - 1, cy - 1] = WHITE + (255,)


def band(dst, cx, y, half, lit=5):
    """Bandeau de la couronne, portant les cinq eclats de Fureur.

    Les eclats sont DANS le bandeau et non en orbite : c'est ce qui les rend
    lisibles a 32 pixels, et c'est la rangee qui s'allumera de gauche a droite
    pour afficher le rechargement de l'Onde de Concorde.
    """
    for x in range(cx - half, cx + half + 1):
        if 0 <= x < S:
            if 0 <= y < S:
                dst[x, y] = GOLD_M + (255,)
            if 0 <= y + 1 < S:
                dst[x, y + 1] = GOLD_D + (255,)
    step = max(1, (2 * half) // 4)
    for i in range(5):
        x = cx - half + i * step
        if 0 <= x < S and 0 <= y < S:
            dst[x, y] = (CRYSTALS[i] if i < lit else GOLD_D) + (255,)


def crown_wing(dst, x0, y0, span, rise, direction, tone=GOLD_M):
    """Aile de la couronne : deux pixels d'epaisseur, sinon elle disparait."""
    for i in range(span):
        t = (i + 1) / span
        x = x0 + direction * (i + 1)
        y = y0 - int(round(rise * t ** 0.75))
        for oy in (0, 1):
            if 0 <= x < S and 0 <= y + oy < S:
                dst[x, y + oy] = (tone if oy == 0 else GOLD_D) + (255,)
    x = x0 + direction * span
    y = y0 - rise
    if 0 <= x < S and 0 <= y - 1 < S:
        dst[x, y - 1] = GOLD_L + (255,)


def variant(name, build):
    base, kept = grip_from_sword()
    build(base.load())
    outline(base)
    return name, base, kept


# ------------------------------------------------------------- variantes

def s1(dst):
    """Couronne fermee : compacte, le cristal serti au creux des ailes."""
    shaft(dst, 9, 20, 20, 10)
    band(dst, 21, 11, 3)
    crown_wing(dst, 18, 11, 3, 3, -1)
    crown_wing(dst, 24, 11, 3, 3, +1)
    crystal(dst, 21, 7, 2)


def s2(dst):
    """Couronne ouverte : ailes largement deployees, grand cristal entre elles."""
    shaft(dst, 9, 20, 20, 10)
    band(dst, 21, 12, 4)
    crown_wing(dst, 17, 12, 5, 6, -1)
    crown_wing(dst, 25, 12, 5, 6, +1)
    crystal(dst, 21, 6, 4)


def s3(dst):
    """Couronne haute : ailes etroites et dressees, cristal detache au-dessus."""
    shaft(dst, 9, 20, 20, 10)
    band(dst, 21, 12, 3)
    crown_wing(dst, 18, 12, 3, 7, -1, GOLD_L)
    crown_wing(dst, 24, 12, 3, 7, +1, GOLD_L)
    crystal(dst, 21, 5, 3)


if __name__ == "__main__":
    os.makedirs(PREVIEW, exist_ok=True)
    variants = [variant("S1", s1), variant("S2", s2), variant("S3", s3)]

    scale = 12
    board = Image.new("RGBA", (S * scale * 3 + 40, S * scale + 20), (22, 22, 26, 255))
    for i, (name, img, kept) in enumerate(variants):
        r = img.resize((S * scale, S * scale), Image.NEAREST)
        board.paste(r, (10 + i * (S * scale + 10), 10), r)
        print("  %s  manche repris de l'epee : %d pixels" % (name, kept))
    p = os.path.join(PREVIEW, "sceptre_maquettes.png")
    board.save(p)
    print("  planche %s" % p)
