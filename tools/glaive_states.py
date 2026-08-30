#!/usr/bin/env python3
"""
Les six etats de Rage du Glaive d'Arcencium.

La texture vient de la reference du joueur, integree par item_from_ref.py : on
ne la redessine pas. Six dessins a la main seraient six occasions de s'ecarter
du modele, et cinq d'entre eux ne seraient jamais regardes de pres.

Ce qui change d'un cran a l'autre, c'est UNIQUEMENT l'eclair. La foudre qui
court dans la lame s'eteint quand la Rage retombe et brule quand elle est
pleine, ce qui est exactement ce que raconte l'arme. L'or de la bordure, le
vert du manche et l'acier sombre ne bougent pas : ils sont la matiere, et la
matiere ne s'excite pas.

Le tri se fait sur la SATURATION et la teinte : on ne touche qu'aux pixels
vifs qui ne sont ni or ni vert, c'est-a-dire aux bleus, violets, magentas et
cyans de la foudre. Un seuil sur la seule luminosite aurait aussi allume les
reflets dores.

Usage :
    python tools/glaive_states.py [--preview]
"""

import colorsys
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "emeraldweapons", "textures", "item")
PREVIEW = os.path.join(ROOT, "tools", "preview")

NAME = "arcencium_glaive"
NFRAMES = 8
FRAMETIME = 4

# La foudre vit entre le cyan et le magenta ; l'or et le vert sont ailleurs.
BOLT_HUE = (0.42, 0.95)
BOLT_SAT = 0.35


def is_bolt(r, g, b):
    h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
    return s >= BOLT_SAT and BOLT_HUE[0] <= h <= BOLT_HUE[1] and v > 0.25


def state(base, lit, phase):
    """
    Un cran de Rage, a un instant de sa pulsation.

    L'eclair passe de presque eteint a plus vif que la reference, et respire en
    plus d'un souffle lent : une jauge qui ne bouge pas se confond avec un
    detail de la texture, et l'on ne la lit plus.
    """
    out = base.copy()
    px = out.load()
    w, h = out.size
    glow = 0.38 + 0.16 * lit
    breath = 1.0 + 0.12 * lit * phase
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0 or not is_bolt(r, g, b):
                continue
            hh, ss, vv = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            vv = min(1.0, vv * glow * breath)
            ss = min(1.0, ss * (0.75 + 0.05 * lit))
            rr, gg, bb = colorsys.hsv_to_rgb(hh, ss, vv)
            px[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)
    return out


def write(name, frames):
    w, h = frames[0].size
    sheet = Image.new("RGBA", (w, h * len(frames)))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * h))
    dest = os.path.join(ITEM_DIR, name + ".png")
    sheet.save(dest)
    with open(dest + ".mcmeta", "w") as fh:
        fh.write('{"animation": {"frametime": %d, "interpolate": true}}' % FRAMETIME)
    print("  %s (%dx%d, %d images)" % (name, w, h, len(frames)))


def main():
    base = Image.open(os.path.join(ITEM_DIR, NAME + ".png")).convert("RGBA")
    if base.height != base.width:
        base = base.crop((0, 0, base.width, base.width))   # deja anime : on reprend la premiere
    for lit in range(6):
        frames = [state(base, lit, __import__("math").sin(f / NFRAMES * 6.2832))
                  for f in range(NFRAMES)]
        write("%s_%d" % (NAME, lit) if lit < 5 else "%s_full" % NAME, frames)
    # l'etat de repos sert aussi de texture par defaut du modele parent
    write(NAME, [state(base, 0, __import__("math").sin(f / NFRAMES * 6.2832))
                 for f in range(NFRAMES)])

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        s = base.width
        board = Image.new("RGBA", (s * 6 * 8, s * 8), (22, 22, 26, 255))
        for lit in range(6):
            fr = state(base, lit, 1.0).resize((s * 8, s * 8), Image.NEAREST)
            board.paste(fr, (lit * s * 8, 0), fr)
        out = os.path.join(PREVIEW, "glaive_states.png")
        board.save(out)
        print("apercu : %s" % os.path.relpath(out, ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
