#!/usr/bin/env python3
"""
Texture des Griffes d'Arcencium.

Le premier dessin etait un fouet : une laniere fine posee sur la diagonale.
Sur une tuile de trente-deux pixels tenue en main, cela lit « canne a peche »
quoi qu'on y ajoute, parce que la silhouette est celle d'un trait mince partant
d'un manche. Le probleme n'etait pas le detail, c'etait la FORME.

Les griffes occupent la tuile au lieu de la traverser : un gantelet d'or en bas
a gauche, trois lames recourbees qui balaient tout le reste. Rien de tout cela
ne peut se confondre avec une epee, un arc ou un marteau, et la lecture est la
meme a seize pixels qu'a trente-deux.

Six etats, un par cran de Charge d'Orage : les cinq gemmes du gantelet
s'allument une a une. Chaque etat est anime sur douze images -- les lames et
les gemmes font tourner leur teinte, comme la lame de l'epee, la corde de l'arc
et le cristal du sceptre. Le gantelet, lui, ne bouge pas.

Usage :
    python tools/talons_texture.py [--preview]
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
NAME = "arcencium_talons"

# Les trois lames : depart, point de tension, pointe.
#
# Elles sont COURBES, et c'est tout ce qui compte. Le premier jet les tracait
# droites, paralleles et larges d'un pixel : cela donnait une fourche de
# jardin. Une griffe se reconnait a sa courbure -- le tranchant creuse d'un
# cote, le dos plein de l'autre -- et a son epaisseur qui fond vers la pointe.
BLADES = [
    ((13, 17), (24, 11), (29, 3)),
    ((15, 20), (26, 16), (31, 8)),
    ((16, 23), (25, 21), (30, 15)),
]

# Le gantelet : le poing, du poignet aux jointures.
GUARD = [(4, 26), (7, 21), (13, 21), (17, 25), (14, 30), (7, 31)]

# Les cinq gemmes, sur le dos du gantelet.
GEMS = [(7, 25), (9, 24), (11, 24), (13, 25), (10, 27)]


def put(dst, x, y, color):
    x, y = int(x), int(y)
    if 0 <= x < S and 0 <= y < S:
        dst[x, y] = color + (255,) if len(color) == 3 else color


def bezier(p0, p1, p2, t):
    u = 1.0 - t
    return (u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0],
            u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1])


def polyline(points, density=4):
    """Les points d'une ligne brisee, echantillonnes avec leur avancement."""
    out = []
    total = len(points) - 1
    for i in range(total):
        x0, y0 = points[i]
        x1, y1 = points[i + 1]
        steps = int(max(abs(x1 - x0), abs(y1 - y0))) * density + 1
        for k in range(steps):
            t = k / steps
            out.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, (i + t) / total))
    out.append((points[-1][0], points[-1][1], 1.0))
    return out


def guard(dst):
    """
    Le gantelet, en or plein.

    On remplit par balayage horizontal plutot que de tracer les aretes : une
    monture creuse se lit comme un cadre, et l'on veut une MASSE -- c'est elle
    qui donne du poids a l'arme dans l'inventaire, et qui ancre les lames au
    lieu de les laisser flotter.
    """
    ys = [p[1] for p in GUARD]
    for y in range(min(ys), max(ys) + 1):
        xs = []
        for i in range(len(GUARD)):
            x0, y0 = GUARD[i]
            x1, y1 = GUARD[(i + 1) % len(GUARD)]
            if (y0 <= y < y1) or (y1 <= y < y0):
                xs.append(x0 + (x1 - x0) * (y - y0) / float(y1 - y0))
        if len(xs) < 2:
            continue
        lo, hi = min(xs), max(xs)
        for x in range(int(lo), int(hi) + 1):
            if x <= lo + 0.5 or x >= hi - 0.5:
                put(dst, x, y, GOLD_D)
            elif x <= lo + 1.5:
                put(dst, x, y, GOLD_L)          # la lumiere vient de la gauche
            else:
                put(dst, x, y, GOLD_M)
    # LES PHALANGES. Une masse d'or unie se lit comme un caillou ; trois
    # rainures suffisent a dire « poing ferme », et c'est ce qui rattache
    # l'arme a une main plutot qu'a un manche.
    for a, bpt in (((7, 22), (6, 26)), ((10, 21), (9, 26)), ((13, 22), (12, 27))):
        for x, y, _ in polyline([a, bpt]):
            put(dst, x, y, GOLD_D)
        put(dst, a[0] + 1, a[1], GOLD_L)
    for x, y, _ in polyline([(4, 28), (8, 31)]):
        put(dst, x, y, (0x3A, 0x25, 0x0C))      # la sangle du poignet


def blades(dst, shift):
    """
    Les trois lames, pleines et effilees.

    On ne trace pas un trait : on epaissit la courbe D'UN SEUL COTE. Le dos
    prend la matiere, le tranchant reste net -- c'est ce qui donne un croissant
    plutot qu'un tuyau, et l'on voit du premier coup d'oeil de quel cote ca
    coupe.
    """
    for b, (p0, p1, p2) in enumerate(BLADES):
        steps = 40
        for i in range(steps + 1):
            t = i / steps
            x, y = bezier(p0, p1, p2, t)
            nx, ny = bezier(p0, p1, p2, min(1.0, t + 0.03))
            dx, dy = nx - x, ny - y
            norm = max(1e-6, (dx * dx + dy * dy) ** 0.5)
            # la normale, cote dos
            ox, oy = -dy / norm, dx / norm
            width = 5.0 * (1.0 - t) ** 0.55
            k = 0.0
            while k <= width:
                px, py = x + ox * k, y + oy * k
                # LE CORPS EST SOMBRE. Le premier essai teintait toute la lame
                # et donnait trois rubans pastel : dans notre langage, la
                # matiere est noire-verte et seule la veine s'irise. C'est ce
                # contraste qui fait lire du metal plutot que du tissu.
                shade = k / max(0.8, width)
                put(dst, px, py, SHAFT_HI if shade < 0.22
                    else SHAFT if shade < 0.6 else DARK)
                k += 0.5
            # le tranchant : la seule ligne qui prend la couleur, et l'anime
            hue = (shift + t * 0.5 + b * 0.12) % 1.0
            r, g, bl = colorsys.hsv_to_rgb(hue, 0.55, 1.0)
            put(dst, x, y, (int(r * 255), int(g * 255), int(bl * 255)))
        put(dst, p2[0], p2[1], GOLD_L)


def gems(dst, lit, shift):
    """Les cinq gemmes du gantelet. Les `lit` premieres brulent."""
    for i, (x, y) in enumerate(GEMS):
        if i < lit:
            hue = (shift + i * 0.15) % 1.0
            r, g, b = colorsys.hsv_to_rgb(hue, 0.65, 1.0)
            put(dst, x, y, (int(r * 255), int(g * 255), int(b * 255)))
        else:
            put(dst, x, y, GOLD_D)


def frame(lit, shift):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dst = img.load()
    blades(dst, shift)
    guard(dst)
    gems(dst, lit, shift)
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
        out = os.path.join(PREVIEW, "talons_states.png")
        strip.resize((S * 6 * 6, S * 6), Image.NEAREST).save(out)
        print("apercu : %s" % os.path.relpath(out, ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
