#!/usr/bin/env python3
"""
Texture du Glaive d'Arcencium.

Deux methodes ont echoue avant celle-ci, et pour la meme raison de fond : une
reference de mille deux cent cinquante-quatre pixels ne survit pas a une
reduction en trente-deux. Le vote majoritaire rendait un bruit de fond ; la
moyenne suivie d'une quantification a noye la lame dans sa bordure d'or. Les
trois autres armes du mod n'ont pas ce defaut parce qu'elles sont DESSINEES a
trente-deux, avec une palette de quinze couleurs.

D'ou le partage. On garde de la reference ce qu'elle seule apporte -- sa
SILHOUETTE, qui a coute cinq essais a la main -- et l'on peint tout le reste
par regle, comme pour l'epee et le sceptre :

  la bordure d'or, en trois tons, sur les deux cases du pourtour ;
  le corps, un degrade sombre qui s'assombrit vers le coeur ;
  les VEINES, tracees depuis la naissance de la lame et qui se divisent en
    remontant vers la pointe, chacune de sa couleur.

C'est le point que demandait le joueur : la foudre part du centre et s'etend le
long de la lame comme des veines, chacune d'une teinte differente. Un halo
uniforme ne raconte rien ; des veines qui se ramifient racontent une decharge.

L'animation les fait COURIR : une onde etroite remonte chaque veine de sa
racine vers sa pointe. Six etats de Rage reglent leur eclat -- presque eteintes
a vide, debordantes a plein.

Usage :
    python tools/glaive_texture.py [--preview]
"""

import colorsys
import math
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REF = os.path.join(ROOT, "tools", "refs", "arcencium_glaive_ref.png")
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "emeraldweapons", "textures", "item")
PREVIEW = os.path.join(ROOT, "tools", "preview")

NAME = "arcencium_glaive"
S = 32
NFRAMES = 12
FRAMETIME = 2

GOLD = ((0xF8, 0xD8, 0x70), (0xC9, 0x96, 0x26), (0x70, 0x4C, 0x10))
STEEL = ((0x1B, 0x2B, 0x2A), (0x11, 0x1C, 0x1E), (0x08, 0x10, 0x14))
GRIP = ((0x3A, 0x25, 0x0C), (0x24, 0x3A, 0x22))

# Les teintes des veines, une par branche : elles doivent se distinguer entre
# elles, pas seulement du fond.
VEIN_HUES = (0.52, 0.75, 0.90, 0.33, 0.62, 0.08)


def mask():
    """La silhouette de la reference, ramenee a trente-deux cases."""
    img = Image.open(REF).convert("RGBA")
    img = img.crop(img.getbbox())
    side = max(img.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    small = square.resize((S, S), Image.BOX)
    px = small.load()
    return [[px[x, y][3] > 110 for x in range(S)] for y in range(S)]


def depth(solid):
    """
    A quelle distance du bord chaque case se trouve.

    C'est ce qui remplace un ombrage dessine a la main : la bordure d'or occupe
    les deux premieres, le corps s'assombrit ensuite vers le coeur. Une piece
    ombree par sa propre forme n'a pas de contour a corriger.
    """
    far = [[0 if not solid[y][x] else 99 for x in range(S)] for y in range(S)]
    for _ in range(S):
        changed = False
        for y in range(S):
            for x in range(S):
                if not solid[y][x]:
                    continue
                best = 99
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    near = far[ny][nx] if 0 <= nx < S and 0 <= ny < S else 0
                    best = min(best, near + 1)
                if best < far[y][x]:
                    far[y][x] = best
                    changed = True
        if not changed:
            break
    return far


def veins(solid):
    """
    Les veines, tracees depuis la naissance de la lame.

    Chacune part du meme point -- le coeur, la ou la lame quitte la garde -- et
    remonte vers la pointe en se divisant. On les trace a la regle et non au
    hasard : une texture doit etre la meme a chaque construction, et un trace
    seme differemment a chaque appel ne se corrige pas.

    @return pour chaque case, l'indice de sa veine et son avancement le long
            d'elle, ou None.
    """
    ys = [y for y in range(S) for x in range(S) if solid[y][x]]
    xs = [x for y in range(S) for x in range(S) if solid[y][x]]
    if not xs:
        return {}
    # le coeur : bas-droite de la masse, la ou le manche rejoint la lame
    hx = (min(xs) + max(xs)) // 2 + 2
    hy = (min(ys) + max(ys)) // 2 + 3

    out = {}
    for i, angle in enumerate((-2.55, -2.05, -1.62, -1.20, -0.78, -2.90)):
        x, y = float(hx), float(hy)
        a = angle
        steps = 22 - (i % 3) * 4
        for t in range(steps):
            a += math.sin(t * 0.9 + i) * 0.11        # une ondulation reguliere
            x += math.cos(a)
            y += math.sin(a)
            cx, cy = int(round(x)), int(round(y))
            if not (0 <= cx < S and 0 <= cy < S) or not solid[cy][cx]:
                break
            out.setdefault((cx, cy), (i, t / float(steps)))
    return out


# Les retournements dont je connais la formule EXACTE.
#
# Les rotations d'un quart de tour sont ecartees a dessein : leur sens differe
# d'une bibliotheque a l'autre, et il ne s'agit pas seulement de tourner
# l'image -- il faut appliquer la MEME transformation aux coordonnees des
# veines, sinon l'animation s'allume a cote du dessin. Six suffisent a
# atteindre n'importe quel coin.
TURNS = {
    "identite": (None, lambda x, y: (x, y)),
    "miroir horizontal": (Image.FLIP_LEFT_RIGHT, lambda x, y: (S - 1 - x, y)),
    "miroir vertical": (Image.FLIP_TOP_BOTTOM, lambda x, y: (x, S - 1 - y)),
    "demi-tour": (Image.ROTATE_180, lambda x, y: (S - 1 - x, S - 1 - y)),
    "diagonale": (Image.TRANSPOSE, lambda x, y: (y, x)),
    "anti-diagonale": (Image.TRANSVERSE, lambda x, y: (S - 1 - y, S - 1 - x)),
}


def orient(img, lines, far):
    """
    Met le manche dans le coin BAS-GAUCHE, image ET coordonnees.

    Le jeu saisit un objet par ce coin-la et pointe l'oppose vers l'avant : un
    manche ailleurs, et le joueur tient sa lame par le tranchant -- ce qui
    s'est produit, faute d'avoir verifie. On essaie donc les six
    retournements et l'on garde celui dont le manche tombe le plus pres du bon
    coin, au lieu de raisonner : deux miroirs se sont deja annules sous mes
    yeux.
    """
    grips = {GRIP[0], GRIP[1]}
    best = None
    for label, (turn, remap) in TURNS.items():
        cand = img if turn is None else img.transpose(turn)
        px = cand.load()
        pts = [(x, y) for y in range(S) for x in range(S) if px[x, y][:3] in grips]
        if not pts:
            continue
        mx = sum(p[0] for p in pts) / len(pts)
        my = sum(p[1] for p in pts) / len(pts)
        here = mx + (S - my)                 # petit x, grand y : bas-gauche
        if best is None or here < best[0]:
            best = (here, label, cand, remap)
    if best is None:
        return img, lines, far
    _, label, cand, remap = best
    print("  orientation : %s" % label)
    moved = {remap(x, y): v for (x, y), v in lines.items()}
    deep = [[0] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            nx, ny = remap(x, y)
            deep[ny][nx] = far[y][x]
    return cand, moved, deep


def art():
    solid = mask()
    far = depth(solid)
    lines = veins(solid)

    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    for y in range(S):
        for x in range(S):
            if not solid[y][x]:
                continue
            d = far[y][x]
            # le manche, en bas a droite : cuir et bronze, non de l'acier
            handle = x > S * 0.62 and y > S * 0.55
            if handle:
                px[x, y] = (GRIP[(x + y) % 2] + (255,))
            elif d <= 1:
                px[x, y] = GOLD[0] + (255,)
            elif d == 2:
                px[x, y] = GOLD[1] + (255,)
            elif d == 3:
                px[x, y] = GOLD[2] + (255,)
            else:
                px[x, y] = STEEL[min(2, (d - 4) // 2)] + (255,)

    # les veines par-dessus, dans le corps seulement : elles ne mordent pas la
    # bordure, sinon on ne lit plus le contour
    for (x, y), (i, t) in lines.items():
        if far[y][x] < 3:
            continue
        r, g, b = colorsys.hsv_to_rgb(VEIN_HUES[i % len(VEIN_HUES)], 0.62, 0.95)
        px[x, y] = (int(r * 255), int(g * 255), int(b * 255), 255)
    return img, lines, far


def outline(img):
    px = img.load()
    ring = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    rp = ring.load()
    for y in range(S):
        for x in range(S):
            if px[x, y][3] > 0:
                continue
            if any(0 <= x + dx < S and 0 <= y + dy < S and px[x + dx, y + dy][3] > 0
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                rp[x, y] = (6, 4, 10, 255)
    ring.alpha_composite(img)
    return ring


def frame(base, lines, far, lit, step):
    """Un instant de la decharge : l'onde remonte chaque veine."""
    out = base.copy()
    px = out.load()
    glow = 0.52 + 0.12 * lit
    for (x, y), (i, t) in lines.items():
        if far[y][x] < 3:
            continue
        # CHAQUE BRANCHE A SON PROPRE RETARD.
        #
        # Elles brillaient toutes ensemble : la lame passait d'eteinte a vive
        # d'un bloc, ce qui donne une lampe et non une decharge. Un decalage
        # par branche suffit a ce que l'une s'allume quand l'autre s'eteint,
        # et c'est ce chevauchement qui fait lire de la foudre.
        phase = (step + i * 0.37) % 1.0
        gap = min(abs(t - phase), 1.0 - abs(t - phase))
        wave = math.exp(-(gap ** 2) / 0.05)
        hue = (VEIN_HUES[i % len(VEIN_HUES)] + 0.05 * wave) % 1.0
        val = min(1.0, glow * (1.05 + 1.4 * wave))
        r, g, b = colorsys.hsv_to_rgb(hue, 0.60, val)
        px[x, y] = (int(r * 255), int(g * 255), int(b * 255), 255)
    return out


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
    base, lines, far = art()
    base, lines, far = orient(base, lines, far)
    os.makedirs(ITEM_DIR, exist_ok=True)
    for lit in range(6):
        frames = [outline(frame(base, lines, far, lit, f / NFRAMES))
                  for f in range(NFRAMES)]
        write("%s_%d" % (NAME, lit) if lit < 5 else "%s_full" % NAME, frames)
    write(NAME, [outline(frame(base, lines, far, 0, f / NFRAMES))
                 for f in range(NFRAMES)])

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        board = Image.new("RGBA", (S * 6 * 8, S * 8), (22, 22, 26, 255))
        for lit in range(6):
            fr = outline(frame(base, lines, far, lit, 0.35)).resize(
                    (S * 8, S * 8), Image.NEAREST)
            board.paste(fr, (lit * S * 8, 0), fr)
        out = os.path.join(PREVIEW, "glaive_states.png")
        board.save(out)
        print("apercu : %s" % os.path.relpath(out, ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
