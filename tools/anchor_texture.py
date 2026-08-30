"""Dessine l'Ancre Prismatique : un socle CREUX, ou l'on depose l'arcencium.

Pourquoi la refaire : la premiere version reutilisait les textures de briques
d'arcencium sur un cube plein. Vue en jeu, elle passait pour un tronc d'arbre --
« si je n'avais pas regarde le nom, je n'aurais pas su ». Deux choses lui
manquaient, et ce sont les deux seules qui comptent ici : on ne voyait pas que
c'etait un RECEPTACLE, et rien ne disait qu'il fallait y mettre quelque chose.

D'ou le parti pris : une cuve visiblement vide au sommet, des chevrons qui
pointent vers elle, et une couronne de lingots graves sur les flancs -- la
matiere qu'elle attend, montree sur elle-meme.

Trois textures : le flanc, le dessus eteint, le dessus actif. Le dessus actif
est anime, la cuve passant par toutes les teintes, ce qui distingue d'un coup
d'oeil une ancre tenue d'une ancre a activer.
"""

import colorsys
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets",
                   "emeraldweapons", "textures", "block")

# La pierre du socle : la meme gamme que la gangue, pour rester de la famille.
DARK = (0x1A, 0x1B, 0x24)
STONE = (0x3A, 0x3D, 0x4A)
LIGHT = (0x55, 0x59, 0x68)
EDGE = (0x6E, 0x74, 0x86)

NFRAMES = 8
FRAMETIME = 5


def prism(t, sat=0.75, val=1.0):
    """Une couleur du cercle prismatique, signature du mode."""
    r, g, b = colorsys.hsv_to_rgb(t % 1.0, sat, val)
    return int(r * 255), int(g * 255), int(b * 255)


def side():
    """Le flanc : un socle taille, ceint d'une couronne de lingots graves."""
    img = Image.new("RGBA", (16, 16), STONE + (255,))
    px = img.load()
    for y in range(16):
        for x in range(16):
            # l'assise et le couronnement, plus sombres : ils donnent l'assise
            if y >= 13 or y <= 1:
                px[x, y] = DARK + (255,)
            elif y == 2 or y == 12:
                px[x, y] = EDGE + (255,)
            elif (x + y) % 7 == 0:
                px[x, y] = LIGHT + (255,)     # le grain de la pierre
    # la couronne de lingots : six alveoles gravees, teintes du prisme
    for i in range(6):
        cx = 1 + i * 3 // 1 % 16
        cx = 1 + (i * 3)
        if cx > 13:
            continue
        colour = prism(i / 6.0, 0.55, 0.85)
        for dy in range(5, 10):
            for dx in range(cx, min(cx + 2, 15)):
                edge = dy in (5, 9) or dx == cx
                px[dx, dy] = (DARK if edge else colour) + (255,)
    return img


def top(active, shift=0.0):
    """Le dessus : une CUVE, et des chevrons qui la designent."""
    img = Image.new("RGBA", (16, 16), STONE + (255,))
    px = img.load()
    for y in range(16):
        for x in range(16):
            ring = max(abs(x - 7.5), abs(y - 7.5))
            if ring > 6.5:
                px[x, y] = DARK + (255,)          # le rebord exterieur
            elif ring > 5.5:
                px[x, y] = EDGE + (255,)          # l'aretier
            elif ring > 3.5:
                px[x, y] = LIGHT + (255,)         # la margelle
            elif ring > 2.5:
                px[x, y] = DARK + (255,)          # la gorge : l'ombre du creux
            else:
                # le fond de cuve
                if active:
                    t = shift + (x + y) / 32.0
                    px[x, y] = prism(t, 0.8, 1.0) + (255,)
                else:
                    px[x, y] = (0x0C, 0x0D, 0x12, 255)
    # les quatre chevrons de la margelle : ils pointent vers le creux
    for i, (dx, dy) in enumerate(((0, -1), (0, 1), (-1, 0), (1, 0))):
        colour = prism(i / 4.0 + shift, 0.6, 1.0) if active else EDGE
        for k in range(3):
            x = int(7.5 + dx * (5 - k))
            y = int(7.5 + dy * (5 - k))
            for s in (-k, k):
                sx = x + (s if dx == 0 else 0)
                sy = y + (s if dy == 0 else 0)
                if 0 <= sx < 16 and 0 <= sy < 16:
                    px[sx, sy] = colour + (255,)
    return img


def inner():
    """La paroi interieure de la cuve, vue depuis le dessus."""
    img = Image.new("RGBA", (16, 16), DARK + (255,))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if y < 3:
                px[x, y] = EDGE + (255,)
            elif (x * 3 + y) % 5 == 0:
                px[x, y] = STONE + (255,)
    return img


def save(img, name):
    path = os.path.join(OUT, name + ".png")
    img.save(path)
    print("ecrit %s" % path)


def main():
    os.makedirs(OUT, exist_ok=True)
    save(side(), "prismatic_anchor_side")
    save(top(False), "prismatic_anchor_top")
    save(inner(), "prismatic_anchor_inner")

    # le dessus actif, anime : la cuve fait le tour des teintes
    frames = [top(True, f / NFRAMES) for f in range(NFRAMES)]
    sheet = Image.new("RGBA", (16, 16 * NFRAMES))
    for i, frame in enumerate(frames):
        sheet.paste(frame, (0, i * 16))
    save(sheet, "prismatic_anchor_top_active")
    meta = os.path.join(OUT, "prismatic_anchor_top_active.png.mcmeta")
    with open(meta, "w", encoding="utf-8", newline="\n") as fh:
        fh.write('{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % FRAMETIME)
    print("ecrit %s" % meta)


if __name__ == "__main__":
    main()
