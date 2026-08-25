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

NFRAMES = 12
FRAMETIME = 3

# Le loquet occupe un coin precis du depliage : x 0..5, y 0..4 en resolution
# vanilla. On le repere par ses COORDONNEES et non par sa couleur -- son gris
# va de 0x80 a 0xC0, si bien qu'un seuil de clarte n'en attrape que la moitie
# et dore au passage des reflets du coffre ailleurs dans la texture.
LATCH_W = 6 * SCALE
LATCH_H = 5 * SCALE

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


def is_latch(x, y):
    """Le coin haut-gauche du depliage, que seul le loquet occupe."""
    return x < LATCH_W and y < LATCH_H


def build(name, source, matrix, cracks):
    src = vanilla(source)
    src = src.resize((src.width * SCALE, src.height * SCALE), Image.NEAREST)
    w, h = src.size
    spx = src.load()

    # deux plages de luminance separees : le corps du coffre, et le loquet, qui
    # est bien plus clair et ecraserait l'echelle s'il etait mesure avec
    body, latch = [], []
    for y in range(h):
        for x in range(w):
            if spx[x, y][3] == 0:
                continue
            lum = (spx[x, y][0] * 299 + spx[x, y][1] * 587 + spx[x, y][2] * 114) // 1000
            (latch if is_latch(x, y) else body).append(lum)

    def span(values):
        return (min(values), max(values)) if values else (0, 1)

    b_lo, b_hi = span(body)
    l_lo, l_hi = span(latch)
    b_span = max(1, b_hi - b_lo)
    l_span = max(1, l_hi - l_lo)

    base = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dst = base.load()
    crack_cells = {}
    n_latch = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = spx[x, y]
            if a == 0:
                continue
            lum = (r * 299 + g * 587 + b * 114) // 1000
            if is_latch(x, y):
                t = (lum - l_lo) / l_span
                dst[x, y] = GOLD[0 if t < 0.34 else (1 if t < 0.72 else 2)] + (255,)
                n_latch += 1
                continue
            key = ((x // CELL) % MATERIAL_SIZE, (y // CELL) % MATERIAL_SIZE)
            if key in cracks:
                crack_cells[(x, y)] = cracks[key]
            mr, mg, mb = matrix.get(key, (18, 18, 21))
            t = (lum - b_lo) / b_span
            v = 14 + 46 * t
            m = max(1, (mr + mg + mb) / 3.0)
            dst[x, y] = (min(255, int(mr / m * v)), min(255, int(mg / m * v)),
                         min(255, int(mb / m * v)), 255)

    # Le sheet des coffres EST un atlas : contrairement aux calques d'armure,
    # il accepte l'animation par .mcmeta. Les fissures y defilent donc comme
    # sur les icones d'objet.
    frames = []
    for f in range(NFRAMES):
        shift = f / NFRAMES
        frame = base.copy()
        fpx = frame.load()
        for (x, y), (r, g, b) in crack_cells.items():
            hh, sa, va = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            rr, gg, bb = colorsys.hsv_to_rgb((hh + shift) % 1.0, sa, va)
            fpx[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), 255)
        frames.append(frame)

    os.makedirs(CHEST_DIR, exist_ok=True)
    dest = os.path.join(CHEST_DIR, name + ".png")
    sheet = Image.new("RGBA", (w, h * NFRAMES), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * h))
    sheet.save(dest)
    with open(dest + ".mcmeta", "w") as fh:
        fh.write('{"animation": {"frametime": %d, "interpolate": true}}\n' % FRAMETIME)

    print("  %-20s %dx%d, %d images, %d fissures, %d pixels de loquet"
          % (name, w, h, NFRAMES, len(crack_cells), n_latch))
    return frames[0]


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
