"""L'ORBE PRECURSEUR de Haven : la texture de l'orbe cache (lot 3, cahier §86).

Pourquoi une texture, et non un modele du jeu : Jak 3 n'a pas d'orbe dans ce qui est
extrait. Ses collectables sont l'oeuf de Ndi Madman (`collectables-skill`, rose) et la
gemme a tete de mort (`collectables-gem`, couchee et plate) -- vues en photo le 22 sept.,
ni l'une ni l'autre ne passe pour « la gemme orange ronde » que le joueur veut. On dessine
donc l'orbe : une sphere doree qui brille, avec son reflet et sa couronne.

    python tools/haven_orb_texture.py

Ecrit assets/emeraldweapons/textures/entity/haven_orbe.png (64 x 64) et une planche de
controle dans build/jak/orbe.png (l'orbe agrandi sur trois fonds).
"""

import math
import os
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow est necessaire : pip install pillow")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons", "textures", "entity", "haven_orbe.png")
CHECK = os.path.join(ROOT, "build", "jak", "orbe.png")
SIZE = 64

# du coeur au bord : blanc chaud, or, ambre, brun dore
RAMP = [(0.00, (255, 252, 226)), (0.22, (255, 236, 150)), (0.52, (255, 196, 64)),
        (0.80, (226, 138, 22)), (1.00, (150, 82, 10))]


def ramp(t):
    t = max(0.0, min(1.0, t))
    for i in range(len(RAMP) - 1):
        a, ca = RAMP[i]
        b, cb = RAMP[i + 1]
        if t <= b:
            k = 0.0 if b == a else (t - a) / (b - a)
            return tuple(int(round(ca[c] + (cb[c] - ca[c]) * k)) for c in range(3))
    return RAMP[-1][1]


def orb():
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()
    center = (SIZE - 1) / 2.0
    radius = SIZE * 0.44
    for y in range(SIZE):
        for x in range(SIZE):
            dx = (x - center) / radius
            dy = (y - center) / radius
            d = math.hypot(dx, dy)
            if d > 1.12:
                continue
            if d > 1.0:
                # la couronne : ce qui deborde du disque s'eteint doucement
                glow = max(0.0, 1.0 - (d - 1.0) / 0.12)
                pixels[x, y] = (255, 190, 80, int(90 * glow * glow))
                continue
            # la sphere : le point de lumiere est en haut a gauche
            lx, ly = dx + 0.34, dy + 0.34
            lit = max(0.0, 1.0 - math.hypot(lx, ly) / 1.5)
            shade = 0.25 + 0.75 * lit
            r, g, b = ramp(d * 0.85 + (1.0 - shade) * 0.35)
            # les deux arcs clairs des orbes precurseurs
            angle = math.atan2(dy, dx)
            swirl = math.cos(angle * 2.0 - d * 3.2) * (0.5 - abs(d - 0.55)) * 0.9
            k = 1.0 + max(0.0, swirl) * 0.45
            edge = min(1.0, (1.0 - d) / 0.08)
            pixels[x, y] = (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)),
                            int(255 * edge))
    return image


def main():
    image = orb()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT)
    print("orbe : %s (%d x %d)" % (OUT, SIZE, SIZE))
    os.makedirs(os.path.dirname(CHECK), exist_ok=True)
    big = image.resize((192, 192), Image.NEAREST)
    board = Image.new("RGBA", (192 * 3, 192), (0, 0, 0, 255))
    for i, back in enumerate([(24, 24, 30, 255), (150, 150, 158, 255), (30, 70, 130, 255)]):
        tile = Image.new("RGBA", (192, 192), back)
        tile.alpha_composite(big)
        board.paste(tile, (i * 192, 0))
    board.save(CHECK)
    print("controle : %s" % CHECK)


if __name__ == "__main__":
    main()
