#!/usr/bin/env python3
"""
Textures du Coffre d'Arcencium, a partir de la planche de matiere.

Meme principe que pour l'armure : une texture de coffre est un depliage UV,
pas un dessin. On reprend donc le gabarit vanilla -- qui garantit que chaque
pixel tombe sur la bonne face du modele -- et on y plaque notre matiere.

Trois fichiers, imposes par le moteur : le coffre simple, et les moities
gauche et droite du coffre double.

Une difference avec l'armure : le loquet reste DORE. Sans lui le coffre
devient un cube noir illisible, et l'or fait le lien avec la garde de l'epee.

Usage :
    python tools/chest_textures.py [--preview]
"""

import colorsys
import io as _io
import os
import sys
import zipfile

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from item_from_ref import downsample                       # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons")
CHEST_DIR = os.path.join(ASSETS, "textures", "entity", "chest")
REFS = os.path.join(ROOT, "tools", "refs")
PREVIEW = os.path.join(ROOT, "tools", "preview")

VANILLA_JAR = os.path.join(os.environ.get("USERPROFILE", ""), "curseforge",
                           "minecraft", "Install", "versions", "1.21.1", "1.21.1.jar")
MATERIAL = os.path.join(REFS, "arcencium_material_ref.png")

SCALE = 2                   # 64x64 vanilla -> 128x128
MATERIAL_SIZE = 128
CELL = 2

CRACK_SAT = 0.55
CRACK_VAL = 0.45

GOLD = ((0x70, 0x4C, 0x10), (0xC9, 0x96, 0x26), (0xF8, 0xD8, 0x70))

PARTS = {"arcencium": "normal", "arcencium_left": "normal_left",
         "arcencium_right": "normal_right"}


def vanilla(name):
    with zipfile.ZipFile(VANILLA_JAR) as z:
        data = z.read("assets/minecraft/textures/entity/chest/%s.png" % name)
    return Image.open(_io.BytesIO(data)).convert("RGBA")


def split_material():
    small = downsample(Image.open(MATERIAL).convert("RGBA"), MATERIAL_SIZE, vivid_min=0.15)
    px = small.load()
    matrix, cracks = {}, {}
    for y in range(MATERIAL_SIZE):
        for x in range(MATERIAL_SIZE):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            (cracks if s >= CRACK_SAT and v >= CRACK_VAL else matrix)[(x, y)] = (r, g, b)
    return matrix, cracks


def is_latch(r, g, b):
    """Le loquet vanilla est le seul element franchement clair et neutre."""
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return v > 0.62 and s < 0.30


def build(name, source, matrix, cracks):
    src = vanilla(source)
    src = src.resize((src.width * SCALE, src.height * SCALE), Image.NEAREST)
    w, h = src.size
    spx = src.load()

    lums = [((spx[x, y][0] * 299 + spx[x, y][1] * 587 + spx[x, y][2] * 114) // 1000)
            for y in range(h) for x in range(w) if spx[x, y][3] > 0 and not is_latch(*spx[x, y][:3])]
    lo, hi = (min(lums), max(lums)) if lums else (0, 1)
    span = max(1, hi - lo)

    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dst = out.load()
    n_crack = n_latch = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = spx[x, y]
            if a == 0:
                continue
            if is_latch(r, g, b):
                lum = (r * 299 + g * 587 + b * 114) // 1000
                dst[x, y] = GOLD[min(2, lum * 3 // 256)] + (255,)
                n_latch += 1
                continue
            key = ((x // CELL) % MATERIAL_SIZE, (y // CELL) % MATERIAL_SIZE)
            if key in cracks:
                dst[x, y] = cracks[key] + (255,)
                n_crack += 1
                continue
            mr, mg, mb = matrix.get(key, (18, 18, 21))
            lum = (r * 299 + g * 587 + b * 114) // 1000
            t = (lum - lo) / span
            v = 14 + 46 * t
            m = max(1, (mr + mg + mb) / 3.0)
            dst[x, y] = (min(255, int(mr / m * v)), min(255, int(mg / m * v)),
                         min(255, int(mb / m * v)), 255)

    os.makedirs(CHEST_DIR, exist_ok=True)
    out.save(os.path.join(CHEST_DIR, name + ".png"))
    print("  %-20s %dx%d, %d fissures, %d pixels de loquet" % (name, w, h, n_crack, n_latch))
    return out


def main():
    for path, label in ((VANILLA_JAR, "jar vanilla"), (MATERIAL, "planche de matiere")):
        if not os.path.isfile(path):
            sys.exit("%s introuvable : %s" % (label, path))
    matrix, cracks = split_material()
    built = {n: build(n, src, matrix, cracks) for n, src in PARTS.items()}

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        s = 3
        widths = [im.width * s for im in built.values()]
        board = Image.new("RGBA", (sum(widths) + 20, max(im.height for im in built.values()) * s + 10),
                          (105, 105, 115, 255))
        x = 5
        for im in built.values():
            r = im.resize((im.width * s, im.height * s), Image.NEAREST)
            board.paste(r, (x, 5), r)
            x += r.width + 5
        p = os.path.join(PREVIEW, "coffre_arcencium.png")
        board.save(p)
        print("  apercu %s" % p)


if __name__ == "__main__":
    main()
