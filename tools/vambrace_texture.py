#!/usr/bin/env python3
"""
Texture des Brassards d'Arcencium.

Quatre formes ont precede celle-ci, et la derniere echouait sur un point que
je n'avais pas compris : je dessinais UNE lame, avec une garde et un manche.
Or ces lames-la ne se tiennent pas, elles se SANGLENT -- elles prolongent
l'avant-bras au lieu de sortir du poing -- et il y en a DEUX, une par bras.
Tant que le dessin gardait un manche, il restait une epee quelle que soit sa
largeur.

D'ou la paire croisee. Deux brassards, deux lames qui partent en sens opposes,
et rien qui ressemble a une poignee : au premier coup d'oeil dans l'inventaire,
c'est un equipement, pas une arme blanche. Le X remplit la tuile jusqu'aux
quatre coins, ce qui repond aussi a « petit ».

Six etats, un par cran de Rage : les cinq rivets des brassards s'allument un a
un -- trois sur l'un, deux sur l'autre. Chaque etat est anime sur douze images ;
les tranchants et les rivets font tourner leur teinte, comme la lame de l'epee,
la corde de l'arc et le cristal du sceptre. Le cuir et l'acier ne bougent pas :
ce qui vit doit se detacher de ce qui ne vit pas.

Usage :
    python tools/vambrace_texture.py [--preview]
"""

import colorsys
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scepter_mockups import (S, DARK, SHAFT, SHAFT_HI, GOLD_D, GOLD_M,  # noqa: E402
                             GOLD_L, outline)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "emeraldweapons", "textures", "item")
PREVIEW = os.path.join(ROOT, "tools", "preview")

NFRAMES = 12
FRAMETIME = 3
NAME = "arcencium_vambraces"

LEATHER = (0x3A, 0x25, 0x0C)

# Les deux pieces. Chacune : le talon du brassard, la pointe de la lame, et le
# cote vers lequel le ventre se renfle.
#
# PARALLELES ET DECALEES, et non croisees. Croisees, leurs ventres se
# rejoignaient au centre et les deux lames fondaient en une seule masse en V --
# on lisait un oiseau, pas une paire. Decalees le long de la meme diagonale,
# chacune garde son contour, et la repetition de la MEME forme est precisement
# ce qui dit « il y en a deux ».
UNITS = [
    {"heel": (2, 20), "tip": (21, 2), "side": +1, "rivets": 3},
    {"heel": (10, 30), "tip": (29, 12), "side": +1, "rivets": 2},
]

BULGE = 5.2
BRACER_LEN = 11.0       # ce que le brassard occupe, depuis le talon
BRACER_HALF = 3.0


def put(dst, x, y, color):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < S and 0 <= y < S:
        dst[x, y] = color + (255,) if len(color) == 3 else color


def axes(unit):
    """Le vecteur de la piece, sa longueur, et sa normale cote ventre."""
    (hx, hy), (tx, ty) = unit["heel"], unit["tip"]
    dx, dy = tx - hx, ty - hy
    length = (dx * dx + dy * dy) ** 0.5
    ux, uy = dx / length, dy / length
    nx, ny = -uy * unit["side"], ux * unit["side"]
    return (hx, hy), (ux, uy), (nx, ny), length


def blade(dst, unit, shift):
    """La lame, a partir du bout du brassard : dos droit, ventre renfle."""
    (hx, hy), (ux, uy), (nx, ny), length = axes(unit)
    start = BRACER_LEN
    steps = int((length - start) * 4)
    for i in range(steps + 1):
        t = i / steps
        along = start + (length - start) * t
        bx, by = hx + ux * along, hy + uy * along
        # nul au raccord et a la pointe, maximal au premier tiers
        width = BULGE * (t ** 0.4) * ((1.0 - t) ** 0.5) * 2.7
        k = 0.0
        while k <= width:
            px, py = bx + nx * k, by + ny * k
            edge = k / max(0.8, width)
            if edge > 0.88:
                hue = (shift + t * 0.5 + unit["side"] * 0.12) % 1.0
                r, g, b = colorsys.hsv_to_rgb(hue, 0.55, 1.0)
                put(dst, px, py, (int(r * 255), int(g * 255), int(b * 255)))
            elif edge > 0.66:
                put(dst, px, py, SHAFT_HI)
            elif edge > 0.32:
                put(dst, px, py, SHAFT)
            else:
                put(dst, px, py, DARK)
            k += 0.5
        put(dst, bx, by, SHAFT_HI)          # le dos, en arete claire


def bracer(dst, unit):
    """
    Le brassard : le fourreau de cuir cercle d'acier qui prend l'avant-bras.
    """
    (hx, hy), (ux, uy), (nx, ny), _ = axes(unit)
    for i in range(int(BRACER_LEN * 4) + 1):
        along = i / 4.0
        bx, by = hx + ux * along, hy + uy * along
        k = -BRACER_HALF
        while k <= BRACER_HALF:
            px, py = bx + nx * k, by + ny * k
            rim = abs(k) > BRACER_HALF - 1.0
            band = int(along) % 4 == 0        # les cercles d'acier
            put(dst, px, py, GOLD_D if rim else (GOLD_M if band else LEATHER))
            k += 0.5
    # la lumiere sur l'arete haute, pour que le fourreau ait du volume
    for i in range(int(BRACER_LEN * 4) + 1):
        along = i / 4.0
        put(dst, hx + ux * along - nx * BRACER_HALF,
            hy + uy * along - ny * BRACER_HALF, GOLD_L)


def rivets(dst, lit, shift):
    """Les cinq rivets, trois sur un brassard et deux sur l'autre."""
    done = 0
    for unit in UNITS:
        (hx, hy), (ux, uy), (nx, ny), _ = axes(unit)
        for j in range(unit["rivets"]):
            along = 2.5 + j * 3.2
            bx, by = hx + ux * along, hy + uy * along
            if done < lit:
                hue = (shift + done * 0.15) % 1.0
                r, g, b = colorsys.hsv_to_rgb(hue, 0.66, 1.0)
                color = (int(r * 255), int(g * 255), int(b * 255))
                put(dst, bx, by, color)
                put(dst, bx + nx, by + ny, tuple(int(c * 0.68) for c in color))
            else:
                put(dst, bx, by, GOLD_D)
            done += 1


def frame(lit, shift):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dst = img.load()
    # la piece du fond d'abord, pour que celle du dessus la recouvre proprement
    for unit in reversed(UNITS):
        blade(dst, unit, shift)
        bracer(dst, unit)
    rivets(dst, lit, shift)
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
    os.makedirs(ITEM_DIR, exist_ok=True)
    for lit in range(6):
        frames = [frame(lit, f / NFRAMES) for f in range(NFRAMES)]
        write("%s_%d" % (NAME, lit) if lit < 5 else "%s_full" % NAME, frames)
    write(NAME, [frame(0, f / NFRAMES) for f in range(NFRAMES)])

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        strip = Image.new("RGBA", (S * 6, S))
        for lit in range(6):
            strip.paste(frame(lit, 0.0), (lit * S, 0))
        out = os.path.join(PREVIEW, "vambrace_states.png")
        strip.resize((S * 6 * 6, S * 6), Image.NEAREST).save(out)
        print("apercu : %s" % os.path.relpath(out, ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
