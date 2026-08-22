#!/usr/bin/env python3
"""
Rendu d'un blueprint ASCII vers PNG, et installation dans le mod.

Le blueprint est un simple fichier texte : les lignes commencant par '#' sont
des commentaires (dont la palette), le reste est la grille de pixels.
Tu peux l'editer dans n'importe quel editeur de texte, puis relancer ce script.

Usage :
    python tools/render_blueprint.py v1_taillee
    python tools/render_blueprint.py v1_taillee --install
    python tools/render_blueprint.py v1_taillee --install --as fulgurite

Options :
    --install       copie le PNG dans assets/emeraldweapons/textures/item/
    --as <nom>      nom du fichier installe (defaut : emerald_sword)
    --scale <n>     facteur de l'apercu agrandi (defaut : 14)
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BP_DIR = os.path.join(ROOT, "tools", "blueprints")
PV_DIR = os.path.join(ROOT, "tools", "preview")
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "emeraldweapons", "textures", "item")

PALETTE_RE = re.compile(r"^#\s*'(.)'\s*=\s*(transparent|#([0-9A-Fa-f]{6}))\s*$")


def parse_blueprint(path):
    """Retourne (grille, palette). Leve ValueError si le fichier est incoherent."""
    palette = {}
    grid = []
    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.rstrip("\n\r")
            if line.startswith("#"):
                m = PALETTE_RE.match(line)
                if m:
                    ch, kind, hexval = m.groups()
                    if kind == "transparent":
                        palette[ch] = (0, 0, 0, 0)
                    else:
                        palette[ch] = (int(hexval[0:2], 16),
                                       int(hexval[2:4], 16),
                                       int(hexval[4:6], 16), 255)
                continue
            if not line.strip():
                continue
            grid.append(list(line))

    if not grid:
        raise ValueError("aucune grille trouvee dans %s" % path)

    width = max(len(r) for r in grid)
    for r in grid:                       # tolere les lignes plus courtes
        r.extend('.' * (width - len(r)))

    unknown = {c for row in grid for c in row if c not in palette}
    if unknown:
        raise ValueError(
            "caracteres absents de la palette : %s\n"
            "Ajoute une ligne de commentaire, ex :   #   'x' = #17A05C"
            % ", ".join("'%s'" % c for c in sorted(unknown)))

    return grid, palette


def render(grid, palette, out_path, scale=14):
    from PIL import Image
    h = len(grid)
    w = len(grid[0])
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = palette[grid[y][x]]
    img.save(out_path)
    big = os.path.splitext(out_path)[0] + "_x%d.png" % scale
    img.resize((w * scale, h * scale), Image.NEAREST).save(big)
    return img, big


def main(argv):
    if not argv:
        print(__doc__)
        return 1

    name = argv[0]
    install = "--install" in argv
    scale = 14
    target = "emerald_sword"
    if "--as" in argv:
        target = argv[argv.index("--as") + 1]
    if "--scale" in argv:
        scale = int(argv[argv.index("--scale") + 1])

    bp_path = os.path.join(BP_DIR, name if name.endswith(".txt") else name + ".txt")
    if not os.path.exists(bp_path):
        print("Blueprint introuvable :", bp_path)
        print("Disponibles :", ", ".join(
            sorted(f[:-4] for f in os.listdir(BP_DIR) if f.endswith(".txt"))))
        return 1

    grid, palette = parse_blueprint(bp_path)
    os.makedirs(PV_DIR, exist_ok=True)
    out = os.path.join(PV_DIR, os.path.basename(bp_path)[:-4] + ".png")
    img, big = render(grid, palette, out, scale)

    print("Grille   : %dx%d" % (len(grid[0]), len(grid)))
    print("Couleurs : %d" % len(palette))
    print("PNG      :", os.path.relpath(out, ROOT))
    print("Apercu   :", os.path.relpath(big, ROOT))

    if install:
        dest = os.path.join(ITEM_DIR, target + ".png")
        img.save(dest)
        print("Installe :", os.path.relpath(dest, ROOT))
        mcmeta = dest + ".mcmeta"
        if os.path.exists(mcmeta):
            print("  ! %s existe et decrit une animation."
                  % os.path.basename(mcmeta))
            print("    Cette texture est fixe (1 frame) : supprime le .mcmeta,")
            print("    sinon Minecraft n'affichera qu'une bande de l'image.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
