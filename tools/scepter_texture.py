#!/usr/bin/env python3
"""
Texture definitive du Sceptre d'Arcencium (variante S2, couronne ouverte).

Six textures, une par etat du bandeau : de zero a cinq eclats allumes. C'est
ce qui affiche le rechargement de l'Onde de Concorde sur l'objet lui-meme,
sans passer par une interface -- le joueur voit sa jauge dans sa main.

Chaque etat est anime sur douze images : le cristal prismatique fait tourner
sa teinte, comme la lame de l'Epee d'Emeraude et la corde de l'arc.

Le manche et la garde ne sont pas dessines : ce sont les pixels d'or et de
cuir extraits de emerald_sword.png, pour que les trois armes forment une
famille evidente.

Usage :
    python tools/scepter_texture.py [--preview]
"""

import colorsys
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scepter_mockups import (S, DARK, SHAFT, SHAFT_HI, GOLD_D, GOLD_M, GOLD_L,  # noqa: E402
                             CRYSTALS, grip_from_sword, line, outline, shaft,
                             crown_wing)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "emeraldweapons", "textures", "item")
PREVIEW = os.path.join(ROOT, "tools", "preview")

NFRAMES = 12
FRAMETIME = 3
NAME = "arcencium_scepter"

CRYSTAL_CENTER = (21, 6)
CRYSTAL_RADIUS = 4
BAND_CENTER = (21, 12)
BAND_HALF = 4


def band(dst, lit):
    """Bandeau de la couronne. Les `lit` premiers eclats sont allumes."""
    cx, y = BAND_CENTER
    for x in range(cx - BAND_HALF, cx + BAND_HALF + 1):
        dst[x, y] = GOLD_M + (255,)
        dst[x, y + 1] = GOLD_D + (255,)
    step = (2 * BAND_HALF) // 4
    for i in range(5):
        x = cx - BAND_HALF + i * step
        dst[x, y] = (CRYSTALS[i] if i < lit else GOLD_D) + (255,)


def crystal(dst, shift):
    """Losange irise. `shift` decale la teinte : c'est l'animation."""
    cx, cy = CRYSTAL_CENTER
    r = CRYSTAL_RADIUS
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            d = abs(dx) + abs(dy)
            if d > r:
                continue
            hue = ((dx + r) / (2 * r + 1) * 0.6 + (dy + r) / (2 * r + 1) * 0.4 + shift) % 1.0
            v = 1.0 if d < r else 0.7
            rr, gg, bb = colorsys.hsv_to_rgb(hue, 0.55, v)
            dst[cx + dx, cy + dy] = (int(rr * 255), int(gg * 255), int(bb * 255), 255)
    dst[cx - 1, cy - 1] = (0xE7, 0xFF, 0xF4, 255)


def frame(lit, shift):
    img, _ = grip_from_sword()
    dst = img.load()
    shaft(dst, 9, 20, 20, 10)
    band(dst, lit)
    crown_wing(dst, 17, 12, 5, 6, -1)
    crown_wing(dst, 25, 12, 5, 6, +1)
    crystal(dst, shift)
    outline(img)
    return img


def write(name, frames):
    dest = os.path.join(ITEM_DIR, name + ".png")
    sheet = Image.new("RGBA", (S, S * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, i * S))
    sheet.save(dest)
    with open(dest + ".mcmeta", "w") as fh:
        fh.write('{"animation": {"frametime": %d, "interpolate": true}}' % FRAMETIME)
        fh.write("\n")
    return dest


def main():
    os.makedirs(ITEM_DIR, exist_ok=True)
    previews = []
    for lit in range(6):
        frames = [frame(lit, f / NFRAMES) for f in range(NFRAMES)]
        # cinq eclats allumes = pret : c'est la texture de base de l'objet
        name = NAME if lit == 5 else "%s_%d" % (NAME, lit)
        write(name, frames)
        previews.append((lit, frames[0]))
        print("  %-24s %d eclat(s) allume(s), %d images" % (name, lit, NFRAMES))

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        s = 10
        board = Image.new("RGBA", (S * s * 6, S * s), (22, 22, 26, 255))
        for i, (_, img) in enumerate(previews):
            r = img.resize((S * s, S * s), Image.NEAREST)
            board.paste(r, (i * S * s, 0), r)
        p = os.path.join(PREVIEW, "sceptre_recharge.png")
        board.save(p)
        print("  apercu %s" % p)


if __name__ == "__main__":
    main()
