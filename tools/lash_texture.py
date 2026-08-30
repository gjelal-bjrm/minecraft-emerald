#!/usr/bin/env python3
"""
Texture du Fouet d'Arcencium.

Six etats, un par cran de Charge d'Orage : de zero a cinq maillons allumes.
C'est la meme grammaire que le bandeau du sceptre -- la jauge se lit dans la
main du joueur, sans interface -- mais elle dit ici l'inverse. Le sceptre
montre un rechargement qui se remplit tout seul ; le fouet montre un orage qui
ne tient que par les coups qu'on porte, et qui s'eteint d'un coup si l'on
s'arrete.

Chaque etat est anime sur douze images : les maillons allumes et la griffe font
tourner leur teinte, comme la lame de l'epee, la corde de l'arc et le cristal
du sceptre. La laniere, elle, ne bouge pas -- ce qui vit doit se detacher de ce
qui ne vit pas, sinon rien ne se lit.

Le manche n'est pas dessine : ce sont les pixels d'or et de cuir extraits de
emerald_sword.png, pour que les quatre armes forment une famille evidente.

Usage :
    python tools/lash_texture.py [--preview]
"""

import colorsys
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scepter_mockups import S, DARK, SHAFT_HI, GOLD_D, GOLD_M, grip_from_sword  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "emeraldweapons", "textures", "item")
PREVIEW = os.path.join(ROOT, "tools", "preview")

NFRAMES = 12
FRAMETIME = 3
NAME = "arcencium_lash"

# La laniere, en coordonnees de la tuile 32x32.
#
# Elle part du pommeau, fouette en diagonale -- l'orientation de tout objet
# tenu en main -- et se RECOURBE au bout, comme une meche qui claque. Le
# premier dessin finissait en crochet arrondi : la silhouette dans la barre
# d'inventaire disait « canne » et non « arme ». Une arme se reconnait a son
# profil avant qu'on en lise le detail, et un profil agressif est effile,
# asymetrique, et pointe quelque part.
PATH = [(9, 26), (12, 23), (15, 21), (18, 18), (21, 15),
        (24, 12), (26, 9), (26, 6), (24, 4), (21, 3), (18, 3)]

# Les cinq maillons, en position sur la laniere (indice de segment).
LINKS = [1, 3, 5, 7, 9]

# Les barbes : le cote ou elles saillent, segment par segment.
BARBS = [(2, 1, 1), (4, 1, 1), (6, 1, 0), (8, 0, 1)]


def curve():
    """La laniere echantillonnee point par point, du manche a la griffe."""
    pts = []
    for i in range(len(PATH) - 1):
        x0, y0 = PATH[i]
        x1, y1 = PATH[i + 1]
        steps = int(max(abs(x1 - x0), abs(y1 - y0))) * 3 + 1
        for k in range(steps):
            t = k / steps
            pts.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, i + t))
    pts.append((PATH[-1][0], PATH[-1][1], len(PATH) - 1.0))
    return pts


def put(dst, x, y, color):
    if 0 <= int(x) < S and 0 <= int(y) < S:
        dst[int(x), int(y)] = color + (255,) if len(color) == 3 else color


def strap(dst):
    """
    La laniere, EFFILEE du manche a la pointe.

    Une epaisseur constante donne un tuyau ; c'est la reduction progressive
    qui fait lire un fouet, parce qu'elle indique le sens -- on voit d'ou part
    la force et ou elle finit.
    """
    pts = curve()
    span = len(PATH) - 1.0
    for x, y, seg in pts:
        thin = seg / span                       # 0 au manche, 1 a la pointe
        put(dst, x, y, DARK)
        if thin < 0.75:
            put(dst, x, y + 1, DARK)
        if thin < 0.40:
            put(dst, x + 1, y + 1, (0x06, 0x24, 0x16))
        put(dst, x + 1, y, (0x08, 0x2E, 0x1C))
    for x, y, seg in pts:
        if int(seg) % 2 == 0:
            put(dst, x, y - 1, SHAFT_HI)


def barbs(dst):
    """Les crochets du dos. Ce sont eux qui disent que l'arme mord."""
    pts = curve()
    for seg, up, out in BARBS:
        spot = min(pts, key=lambda p: abs(p[2] - seg))
        x, y = spot[0], spot[1]
        put(dst, x + out, y - 1 - up, GOLD_M)
        put(dst, x + out, y - up, GOLD_D)


def links(dst, lit, shift):
    """Les cinq maillons. Les `lit` premiers brulent, les autres dorment."""
    pts = curve()
    for i, seg in enumerate(LINKS):
        spot = min(pts, key=lambda p: abs(p[2] - seg))
        x, y = spot[0], spot[1]
        if i < lit:
            hue = (shift + i * 0.13) % 1.0
            r, g, b = colorsys.hsv_to_rgb(hue, 0.62, 1.0)
            color = (int(r * 255), int(g * 255), int(b * 255))
            put(dst, x, y, color)
            put(dst, x, y - 1, color)
            put(dst, x + 1, y, tuple(int(c * 0.72) for c in color))
            put(dst, x - 1, y, tuple(int(c * 0.72) for c in color))
        else:
            put(dst, x, y, GOLD_D)


def claw(dst, lit, shift):
    """
    La griffe : TROIS pointes, et non une boule.

    Le bout d'un fouet est ce qu'on regarde en dernier et ce dont on se
    souvient. Une extremite arrondie desamorce tout le reste du dessin.
    """
    tx, ty = PATH[-1]
    glow = 0.50 + 0.10 * lit
    for k, (dx, dy) in enumerate(((0, 0), (-1, 0), (-2, -1), (-1, 1), (-2, 2), (0, -1))):
        hue = (shift + k * 0.08) % 1.0
        r, g, b = colorsys.hsv_to_rgb(hue, 0.60, min(1.0, glow + (0.32 if k == 0 else 0.0)))
        put(dst, tx + dx, ty + dy, (int(r * 255), int(g * 255), int(b * 255)))
    put(dst, tx - 3, ty - 1, GOLD_M)
    put(dst, tx - 3, ty + 2, GOLD_M)


def frame(grip, lit, shift):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dst = img.load()
    strap(dst)
    barbs(dst)
    links(dst, lit, shift)
    claw(dst, lit, shift)
    img.alpha_composite(grip)
    return img


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
    # l'etat de repos sert aussi de texture par defaut du modele parent
    write(NAME, [frame(grip, 0, f / NFRAMES) for f in range(NFRAMES)])

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        strip = Image.new("RGBA", (S * 6, S))
        for lit in range(6):
            strip.paste(frame(grip, lit, 0.0), (lit * S, 0))
        out = os.path.join(PREVIEW, "lash_states.png")
        strip.resize((S * 6 * 6, S * 6), Image.NEAREST).save(out)
        print("apercu : %s" % os.path.relpath(out, ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
