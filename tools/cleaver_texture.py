#!/usr/bin/env python3
"""
Texture du Couperet d'Arcencium.

Trois formes ont precede celle-ci. Le fouet et les griffes echouaient pour la
meme raison -- une silhouette MINCE sur la diagonale, que l'oeil range aussitot
parmi les cannes et les outils. Le disque reglait le probleme mais n'etait plus
une arme de melee.

Le couperet garde la diagonale, qui est l'axe naturel de tout objet tenu en
main, et la remplit : une lame large de dix pixels, a dos droit et a ventre
courbe, qui deborde jusqu'aux bords de la tuile. Ce n'est pas la direction qui
faisait paraitre l'arme menue, c'est la MAIGREUR.

Six etats, un par cran de Rage : les cinq entailles du dos s'allument une a
une. Chaque etat est anime sur douze images -- le tranchant et les entailles
font tourner leur teinte, comme la lame de l'epee, la corde de l'arc et le
cristal du sceptre. Le corps de la lame, lui, ne bouge pas.

Le manche n'est pas dessine : ce sont les pixels d'or et de cuir extraits de
emerald_sword.png, pour que les quatre armes forment une famille evidente.

Usage :
    python tools/cleaver_texture.py [--preview]
"""

import colorsys
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scepter_mockups import (S, DARK, SHAFT, SHAFT_HI, GOLD_D, GOLD_M,  # noqa: E402
                             GOLD_L, grip_from_sword, outline)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "emeraldweapons", "textures", "item")
PREVIEW = os.path.join(ROOT, "tools", "preview")

NFRAMES = 12
FRAMETIME = 3
NAME = "arcencium_cleaver"

# Le DOS de la lame : droit, du talon a la pointe.
SPINE = ((11, 20), (29, 2))
# Le VENTRE : le meme trajet, mais renfle par une courbe.
#
# C'est ce renflement qui fait le couperet. Un dos droit et un ventre bombe se
# lisent comme une masse qui coupe ; deux bords paralleles se lisent comme une
# regle, et deux bords courbes comme une faucille.
BELLY_BULGE = 9.5


def put(dst, x, y, color):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < S and 0 <= y < S:
        dst[x, y] = color + (255,) if len(color) == 3 else color


def blade(dst, shift):
    """La lame : dos droit, ventre renfle, tranchant irise."""
    (x0, y0), (x1, y1) = SPINE
    dx, dy = x1 - x0, y1 - y0
    length = (dx * dx + dy * dy) ** 0.5
    # la normale du dos, dirigee vers le ventre (en bas a droite)
    nx, ny = -dy / length, dx / length

    steps = int(length * 4)
    for i in range(steps + 1):
        t = i / steps
        bx, by = x0 + dx * t, y0 + dy * t
        # le ventre : nul aux deux bouts, maximal au tiers -- le talon reste
        # etroit pour se raccorder au manche, la pointe pour rester pointue
        width = BELLY_BULGE * (t ** 0.45) * ((1.0 - t) ** 0.55) * 2.6
        k = 0.0
        while k <= width:
            px, py = bx + nx * k, by + ny * k
            edge = k / max(0.8, width)
            if edge > 0.88:
                # LE FIL, seule ligne qui prenne la couleur -- et qui s'anime
                hue = (shift + t * 0.5) % 1.0
                r, g, b = colorsys.hsv_to_rgb(hue, 0.55, 1.0)
                put(dst, px, py, (int(r * 255), int(g * 255), int(b * 255)))
            elif edge > 0.66:
                put(dst, px, py, SHAFT_HI)
            elif edge > 0.3:
                put(dst, px, py, SHAFT)
            else:
                put(dst, px, py, DARK)
            k += 0.5
        # le dos, en arete claire
        put(dst, bx, by, GOLD_D if i % 5 == 0 else SHAFT_HI)


def notches(dst, lit, shift):
    """Les cinq entailles du dos. Les `lit` premieres brulent."""
    (x0, y0), (x1, y1) = SPINE
    for i in range(5):
        t = 0.18 + i * 0.16
        bx, by = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        if i < lit:
            hue = (shift + i * 0.15) % 1.0
            r, g, b = colorsys.hsv_to_rgb(hue, 0.66, 1.0)
            color = (int(r * 255), int(g * 255), int(b * 255))
            put(dst, bx, by, color)
            put(dst, bx - 1, by - 1, color)
            put(dst, bx, by - 1, tuple(int(c * 0.65) for c in color))
        else:
            put(dst, bx, by, GOLD_D)
            put(dst, bx - 1, by - 1, GOLD_D)


def heel(dst):
    """Le talon d'or, ou la lame se raccorde au manche."""
    for k in range(-2, 3):
        put(dst, 11 + k, 20 - k, GOLD_M)
        put(dst, 12 + k, 21 - k, GOLD_D)
    put(dst, 11, 20, GOLD_L)


def frame(grip, lit, shift):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dst = img.load()
    blade(dst, shift)
    notches(dst, lit, shift)
    heel(dst)
    img.alpha_composite(grip)
    # le cerne noir de rigueur en pixel art : sans lui, une silhouette posee
    # sur l'inventaire se dissout dans ce qu'il y a derriere
    return outline(img)


def write(name, frames):
    sheet = Image.new("RGBA", (S, S * len(frames)))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * S))
    dest = os.path.join(ITEM_DIR, name + ".png")
    sheet.save(dest)
    with open(dest + ".mcmeta", "w") as fh:
        fh.write('{"animation": {"frametime": %d, "interpolate": true}}' % FRAMETIME)
    print("  %s (%dx%d, %d images)" % (name, S, S, len(frames)))


def main():
    grip, kept = grip_from_sword()
    print("manche repris de l'epee : %d pixels" % kept)
    os.makedirs(ITEM_DIR, exist_ok=True)
    for lit in range(6):
        frames = [frame(grip, lit, f / NFRAMES) for f in range(NFRAMES)]
        write("%s_%d" % (NAME, lit) if lit < 5 else "%s_full" % NAME, frames)
    write(NAME, [frame(grip, 0, f / NFRAMES) for f in range(NFRAMES)])

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        strip = Image.new("RGBA", (S * 6, S))
        for lit in range(6):
            strip.paste(frame(grip, lit, 0.0), (lit * S, 0))
        out = os.path.join(PREVIEW, "cleaver_states.png")
        strip.resize((S * 6 * 6, S * 6), Image.NEAREST).save(out)
        print("apercu : %s" % os.path.relpath(out, ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
