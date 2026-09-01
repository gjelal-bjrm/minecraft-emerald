#!/usr/bin/env python3
"""
Les quatre Pierres elementaires.

On ne redessine rien. La silhouette de l'eclat d'amethyste est deja lisible a
seize pixels, deja reconnue comme une pierre par tout joueur de Minecraft, et
deja pourvue de facettes -- il n'y a qu'a la TEINTER.

Le procede : on lit la luminance de chaque pixel vanilla et on la reporte sur
la teinte de l'element. Les facettes, l'ombre et le reflet sont donc conserves
tels quels ; seule la couleur change. C'est ce qui fait que les quatre pierres
se ressemblent assez pour se reconnaitre d'un coup d'oeil, et different assez
pour ne jamais se confondre.

Un LISERE sombre borde la silhouette, comme sur les artefacts : c'est la marque
commune de ce qui appartient au mode.

Usage :
    python tools/element_stone_texture.py [--preview]
"""

import colorsys
import io as _io
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons",
                        "textures", "item")
PREVIEW = os.path.join(ROOT, "tools", "preview")

VANILLA_JAR = os.path.join(os.environ.get("USERPROFILE", ""), "curseforge",
                           "minecraft", "Install", "versions", "1.21.1", "1.21.1.jar")

SOURCE = "amethyst_shard"

# element -> teinte
ELEMENTS = [
    ("eau", 0x61C4FF),
    ("feu", 0xFF7A3D),
    ("lumiere", 0xFFE96B),
    ("obscur", 0x9C6BFF),
]


def vanilla(name):
    with zipfile.ZipFile(VANILLA_JAR) as z:
        data = z.read("assets/minecraft/textures/item/%s.png" % name)
    return Image.open(_io.BytesIO(data)).convert("RGBA")


def tint(src, colour):
    """Reporte la luminance vanilla sur une teinte donnee."""
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    sp, op = src.load(), out.load()
    r, g, b = (colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF
    h, _, _ = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)

    for y in range(src.height):
        for x in range(src.width):
            pr, pg, pb, pa = sp[x, y]
            if pa == 0:
                continue
            # La LUMINANCE vanilla devient la valeur, la teinte vient de
            # l'element, et la saturation reste haute pour que la couleur porte
            # a seize pixels. Garder la saturation d'origine donnerait quatre
            # pierres grisatres qu'on ne distinguerait pas dans un coffre.
            lum = (0.299 * pr + 0.587 * pg + 0.114 * pb) / 255.0
            nr, ng, nb = colorsys.hsv_to_rgb(h, 0.72, min(1.0, lum * 1.15))
            op[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), pa)

    # le lisere : on assombrit chaque pixel qui borde le vide
    for y in range(src.height):
        for x in range(src.width):
            if op[x, y][3] == 0:
                continue
            edge = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < src.width and 0 <= ny < src.height) or sp[nx, ny][3] == 0:
                    edge = True
                    break
            if edge:
                cr, cg, cb, ca = op[x, y]
                op[x, y] = (cr // 3, cg // 3, cb // 3, ca)
    return out


def main():
    preview = "--preview" in sys.argv
    out = PREVIEW if preview else ITEM_DIR
    if not os.path.isdir(out):
        os.makedirs(out)
    src = vanilla(SOURCE)
    for name, colour in ELEMENTS:
        tint(src, colour).save(os.path.join(out, "element_stone_%s.png" % name))
        print("  element_stone_%-9s d'apres %s   #%06X" % (name, SOURCE, colour))
    print("%d pierres ecrites dans %s" % (len(ELEMENTS), os.path.relpath(out, ROOT)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
