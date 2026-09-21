#!/usr/bin/env python3
"""
Les textures du ratelier d'armes du QG de Haven (cahier §81), tirees de Jak 3.

Comme celles de la borne : les textures du jeu (decompiler_out d'OpenGOAL), ramenees
en 32 x 32. Le ratelier du Hip Hog porte gun-gunrack-01 (le panneau d'acier biseaute,
ici le socle) et gun-gunrack-02 (la barre a encoches, le berceau) ; le pied, le metal
rouge du Hip Hog (hip-tredmetal04, celui de la borne) ; les montants,
hip-tblack-trim01 ; le voyant, hip-tredlight01. Une bande etroite (128 x 16) est
repetee en hauteur : n'importe quelle portion d'UV montre le motif. Les propositions
murale et vitrine, ecartees par le joueur, prenaient aussi gun-guncase-* du parcours
de tir.

    python tools/haven_rack_textures.py
"""

import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAK = os.path.join(os.path.expanduser("~"), "Documents", "OpenGoal", "active", "jak3", "data",
                   "decompiler_out", "jak3", "textures")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons", "textures", "block",
                   "haven_ratelier")
SIZE = 32

# nom de sortie -> (page de textures, nom dans Jak 3) : le ratelier SUR PIED, choisi par le joueur
SOURCES = {
    "panneau": ("hiphog-vis-tfrag", "gun-gunrack-01"),
    "rail": ("hiphog-vis-tfrag", "gun-gunrack-02"),
    "pied": ("hiphog-vis-tfrag", "hip-tredmetal04"),
    "crochet": ("hiphog-vis-tfrag", "hip-tblack-trim01"),
    "voyant": ("hiphog-vis-tfrag", "hip-tredlight01"),
}


def square(img):
    """Ramene en 32 x 32 ; une bande plus large que haute est repetee en hauteur."""
    img = img.convert("RGBA")
    w, h = img.size
    band = max(1, round(SIZE * h / w))
    strip = img.resize((SIZE, band), Image.LANCZOS)
    out = Image.new("RGBA", (SIZE, SIZE))
    for y in range(0, SIZE, band):
        out.paste(strip, (0, y))
    out = out.convert("RGBA")
    # opaque partout : ce sont des surfaces pleines
    px = out.load()
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255)
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (page, jak) in SOURCES.items():
        src = os.path.join(JAK, page, jak + ".png")
        square(Image.open(src)).save(os.path.join(OUT, name + ".png"))
        print("%-15s <- %s/%s" % (name, page, jak))


if __name__ == "__main__":
    main()
