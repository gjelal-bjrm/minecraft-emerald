#!/usr/bin/env python3
"""
Les textures des portails d'Arcencium (22 sept., cahier §84) : la Porte doree de l'Heure
Doree, les Brumes etoilees et la Brume de rappel de l'Aurore.

« Je n'aime pas du tout les portails. Ils sont moches, mal faits et pas pratiques a
prendre. » C'etaient six cubes translucides en croix. Ils deviennent des portes
dessinees (client/ArcPortalRenderer), aux materiaux du mode :

  - metal     la bande de metal noir, tiree de la planche de reference de la matiere
              (refs/arcencium_material_ref.png), sans ses fissures ;
  - fissures  les memes fissures seules, en blanc : le rendu les teinte d'un arc-en-ciel
              qui court le long de la bande ;
  - bord      la tranche, un metal sombre a liseré ;
  - socle     le seuil, en gangue polie (la texture du bloc du mode) ;
  - lueur, tourbillon, etoiles, colonne : les voiles, en BLANC -- le rendu leur donne
              la couleur de chaque portail (or, nuit etoilee, aube).

    python tools/arc_porte_textures.py [--preview]
"""

import colorsys
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from item_from_ref import downsample                          # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons")
OUT = os.path.join(ASSETS, "textures", "block", "arc_porte")
MATERIAL = os.path.join(ROOT, "tools", "refs", "arcencium_material_ref.png")
GANGUE = os.path.join(ASSETS, "textures", "block", "polished_gangue.png")
PREVIEW = os.path.join(ROOT, "tools", "preview")

CRACK_SAT = 0.55
CRACK_VAL = 0.45


def split(material):
    px = material.load()
    w, h = material.size
    dark, cracks = {}, set()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s >= CRACK_SAT and v >= CRACK_VAL:
                cracks.add((x, y))
            else:
                dark[(x, y)] = (r + g + b) / 3.0
    return dark, cracks


def band(dark, cracks, w, h, ox, oy):
    """La bande de metal et son masque de fissures : v va d'un bord a l'autre de la bande."""
    metal = Image.new("RGBA", (w, h))
    mask = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    mp, kp = metal.load(), mask.load()
    for y in range(h):
        # un leger bombe : plus clair au milieu de la bande, un filet sombre aux bords
        t = 1.0 - abs((y + 0.5) / h - 0.5) * 2.0
        edge = y in (0, h - 1)
        for x in range(w):
            key = (ox + x, oy + y)
            grain = dark.get(key, 12.0) / 12.0
            v = (9 if edge else 16 + 34 * t) * (0.85 + 0.15 * max(0.6, min(1.4, grain)))
            mp[x, y] = (int(v * 0.95), int(v * 0.97), int(v * 1.12), 255)
            if key in cracks and not edge:
                kp[x, y] = (255, 255, 255, 255)
    return metal, mask


def edge(w, h):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            v = 22 if y in (h // 2 - 1, h // 2) else 12 + (x * 7 + y * 3) % 5
            px[x, y] = (v, v, v + 6, 255)
    return img


def glow(size):
    """Une lueur ronde, blanche : pleine au centre, effacee au bord, avec des rides lentes."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c) / c
            if d > 1.0:
                continue
            a = (1.0 - d ** 1.6) * (0.82 + 0.18 * math.cos(d * 18.0))
            px[x, y] = (255, 255, 255, int(255 * max(0.0, min(1.0, a))))
    return img


def swirl(size, arms=3):
    """Un tourbillon : des bras en spirale, blancs, doux au bord."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            dx, dy = x - c, y - c
            d = math.hypot(dx, dy) / c
            if d > 1.0 or d < 0.02:
                continue
            ang = math.atan2(dy, dx)
            phase = arms * (ang + 3.2 * math.log(d + 0.05))
            s = 0.5 + 0.5 * math.cos(phase)
            a = (s ** 3) * (1.0 - d ** 2) * min(1.0, d * 4.0)
            px[x, y] = (255, 255, 255, int(255 * max(0.0, min(1.0, a * 0.9))))
    return img


def stars(size, count, seed):
    """Un champ d'etoiles : des points et quelques croix, blancs."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2.0
    for _ in range(count):
        while True:
            x, y = rnd.randrange(size), rnd.randrange(size)
            if math.hypot(x - c, y - c) < c * 0.92:
                break
        bright = rnd.random()
        a = int(140 + 115 * bright)
        px[x, y] = (255, 255, 255, a)
        if bright > 0.72:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < size and 0 <= y + dy < size:
                    px[x + dx, y + dy] = (255, 255, 255, a // 2)
    return img


def column(w, h, seed):
    """La colonne du plateau : une lumiere douce, de larges cotes a peine marquees et des
    bandes lentes qui montent (le rendu fait defiler v). Les filets fins d'avant se lisaient
    comme des brins separes sur la photo de pres."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for x in range(w):
        rib = 0.78 + 0.22 * math.sin(x / w * math.pi * 2 * 3)
        for y in range(h):
            band = 0.72 + 0.28 * math.sin(y / h * math.pi * 2 * 2)
            px[x, y] = (255, 255, 255, int(255 * 0.62 * rib * band))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    ref = Image.open(MATERIAL).convert("RGBA")
    material = downsample(ref, 128, vivid_min=0.15)
    dark, cracks = split(material)
    metal, mask = band(dark, cracks, 64, 16, 20, 40)
    metal.save(os.path.join(OUT, "metal.png"))
    mask.save(os.path.join(OUT, "fissures.png"))
    # les piliers de l'arche : une bande plus haute, tiree ailleurs dans la planche
    pmetal, pmask = band(dark, cracks, 16, 64, 90, 30)
    pmetal.save(os.path.join(OUT, "pilier.png"))
    pmask.save(os.path.join(OUT, "pilier_fissures.png"))
    edge(32, 8).save(os.path.join(OUT, "bord.png"))
    gangue = Image.open(GANGUE).convert("RGBA")
    gangue.resize((gangue.width * 2, gangue.height * 2), Image.NEAREST).save(os.path.join(OUT, "socle.png"))
    glow(64).save(os.path.join(OUT, "lueur.png"))
    swirl(128).save(os.path.join(OUT, "tourbillon.png"))
    stars(128, 90, 7).save(os.path.join(OUT, "etoiles.png"))
    column(32, 64, 3).save(os.path.join(OUT, "colonne.png"))
    print("textures des portails dans %s" % os.path.relpath(OUT, ROOT))
    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        board = Image.new("RGBA", (660, 300), (40, 40, 46, 255))
        x = 10
        for name, scale in (("metal", 4), ("fissures", 4), ("tourbillon", 1), ("etoiles", 1), ("lueur", 2)):
            im = Image.open(os.path.join(OUT, name + ".png"))
            im = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
            board.paste(im, (x, 10), im)
            x += im.width + 10 if im.width < 200 else 0
            if name == "fissures":
                x = 10
        board.save(os.path.join(PREVIEW, "portails_textures.png"))


if __name__ == "__main__":
    main()
