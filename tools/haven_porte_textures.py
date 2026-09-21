#!/usr/bin/env python3
"""
Les textures de la porte precurseur de la victoire (parcours de Haven, lot 2, cahier §81),
tirees de Jak 3.

Le WARP GATE precurseur de Jak 3 (la page de textures de la chambre de Vin, vinroom) :
warpgate-precursormetal, la bande de metal brun a glyphes precurseurs ; warpgate-post-01,
le metal de ses montants ; warpgate-circuitpattern2, la bande a circuits bleus. S'y
ajoutent le plateau rond des niveaux precurseurs (precur-platform-plate), la bordure
(precur-trim-01), la lueur bleue de la foret (fora-precursor-light) et l'eau du temple
(tpl-symbl-yellow-glow-01, des vagues cyan).

Les textures de la PS2 portent un alpha de 128 pour « opaque » : les surfaces pleines
sont rendues opaques. Les lueurs prennent leur luminosite comme alpha : le noir devient
transparent. L'alpha de la bande a glyphes est un masque de reflet qui dessine le trait
des glyphes : on en tire « glyphes », les memes traits en cyan, qui s'allument par-dessus
le metal.

    python tools/haven_porte_textures.py
"""

import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAK = os.path.join(os.path.expanduser("~"), "Documents", "OpenGoal", "active", "jak3", "data",
                   "decompiler_out", "jak3", "textures")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons", "textures", "block",
                   "haven_porte")

# nom de sortie -> (page, nom dans Jak 3, traitement)
SOURCES = {
    "metal": ("vinroom-vis-tfrag", "warpgate-precursormetal", "opaque"),
    "pilier": ("vinroom-vis-tfrag", "warpgate-post-01", "opaque"),
    "circuit": ("vinroom-vis-tfrag", "warpgate-circuitpattern2", "lueur"),
    "plaque": ("precura-vis-tfrag", "precur-platform-plate", "opaque"),
    "bord": ("precura-vis-tfrag", "precur-trim-01", "opaque"),
    "lumiere": ("foresta-vis-pris", "fora-precursor-light", "lueur"),
    "voile": ("templea-vis-tfrag", "tpl-symbl-yellow-glow-01", "lueur"),
}


def opaque(img):
    r, g, b, _ = img.convert("RGBA").split()
    return Image.merge("RGBA", (r, g, b, Image.new("L", img.size, 255)))


def glow(img):
    """La luminosite devient l'alpha : le noir s'efface, le clair reste."""
    img = img.convert("RGBA")
    r, g, b, _ = img.split()
    lum = Image.merge("RGB", (r, g, b)).convert("L").point(lambda v: min(255, int(v * 1.15)))
    return Image.merge("RGBA", (r, g, b, lum))


def glyphs(img):
    """Le trait des glyphes (masque de reflet de la bande du milieu), en cyan."""
    img = img.convert("RGBA")
    w, h = img.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    src = img.load()
    dst = out.load()
    for y in range(h):
        # la bande a glyphes seulement : les bords clairs de l'alpha sont du metal poli
        if y < h * 0.19 or y > h * 0.73:
            continue
        for x in range(w):
            a = src[x, y][3]
            if a > 24:
                level = min(255, a * 3)
                dst[x, y] = (110, 235, 255, level)
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (page, jak, how) in SOURCES.items():
        src = Image.open(os.path.join(JAK, page, jak + ".png"))
        img = opaque(src) if how == "opaque" else glow(src)
        img.save(os.path.join(OUT, name + ".png"))
        print("%-10s <- %s/%s (%s)" % (name, page, jak, how))
    src = Image.open(os.path.join(JAK, "vinroom-vis-tfrag", "warpgate-precursormetal.png"))
    glyphs(src).save(os.path.join(OUT, "glyphes.png"))
    print("%-10s <- vinroom-vis-tfrag/warpgate-precursormetal (masque des glyphes)" % "glyphes")


if __name__ == "__main__":
    main()
