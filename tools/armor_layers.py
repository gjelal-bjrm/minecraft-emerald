#!/usr/bin/env python3
"""
Calques de l'armure d'Arcencium portee, a partir d'une planche de matiere.

POURQUOI ce script separe. Une texture d'armure portee n'est pas un dessin :
c'est un depliage UV ou chaque pixel doit tomber sur une face precise du modele
du joueur. Aucun generateur d'images ne peut le produire. On procede donc en
deux temps : la reference fournit la MATIERE (metal noir fissure et lumineux),
et on la plaque ici sur le gabarit vanilla, qui garantit un placage correct.

Deux sorties, parce que Minecraft n'anime pas les calques d'armure (ils ne
passent pas par un atlas) :
  - arcencium_layer_N.png        le metal noir seul, calque normal
  - arcencium_cracks_N_XX.png    les fissures seules, 12 images, posees par
                                 notre calque de rendu ArcenciumArmorLayer

Usage :
    python tools/armor_layers.py [--preview]
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
ARMOR_DIR = os.path.join(ASSETS, "textures", "models", "armor")
REFS = os.path.join(ROOT, "tools", "refs")
PREVIEW = os.path.join(ROOT, "tools", "preview")

VANILLA_JAR = os.path.join(os.environ.get("USERPROFILE", ""), "curseforge",
                           "minecraft", "Install", "versions", "1.21.1", "1.21.1.jar")

MATERIAL = os.path.join(REFS, "arcencium_material_ref.png")
SCALE = 4                   # 64x32 vanilla -> 256x128 : voir plus bas
MATERIAL_SIZE = 128         # finesse de la planche une fois reechantillonnee
CELL = 2                    # pixels de sortie par cellule de matiere

# POURQUOI quadrupler la resolution. Sur un calque 64x32, le torse du joueur ne
# couvre que 16 pixels de large : une fissure d'un pixel y occupe un seizieme
# du plastron et se lit comme un gros pate, pas comme un trait de foudre. En
# 256x128 la meme fissure fait un trente-deuxieme -- l'echelle des references.
# Minecraft accepte n'importe quel multiple de la taille vanilla.
NFRAMES = 12                # doit rester egal au FRAMES d'ArcenciumArmorLayer

CRACK_SAT = 0.55            # au-dela : c'est une fissure
CRACK_VAL = 0.45


def vanilla(path):
    with zipfile.ZipFile(VANILLA_JAR) as z:
        return Image.open(_io.BytesIO(z.read(path))).convert("RGBA")


def shading(img):
    """Extrait le modele du calque vanilla, normalise entre 0 et 1.

    On le reapplique ensuite sur la matiere : sans lui, l'armure devient un
    aplat noir ou aucun volume ne se lit sur le personnage.
    """
    px = img.load()
    w, h = img.size
    lums = {}
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > 0:
                lums[(x, y)] = (r * 299 + g * 587 + b * 114) // 1000
    if not lums:
        return {}
    lo, hi = min(lums.values()), max(lums.values())
    span = max(1, hi - lo)
    return {k: (v - lo) / span for k, v in lums.items()}


def split_material():
    """Separe la planche en matiere sombre et en fissures vives."""
    ref = Image.open(MATERIAL).convert("RGBA")
    small = downsample(ref, MATERIAL_SIZE, vivid_min=0.15)
    px = small.load()
    matrix, cracks = {}, {}
    for y in range(MATERIAL_SIZE):
        for x in range(MATERIAL_SIZE):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s >= CRACK_SAT and v >= CRACK_VAL:
                cracks[(x, y)] = (r, g, b)
            else:
                matrix[(x, y)] = (r, g, b)
    return matrix, cracks


def build(layer_n, matrix, cracks):
    src = vanilla("assets/minecraft/textures/models/armor/netherite_layer_%d.png" % layer_n)
    src = src.resize((src.width * SCALE, src.height * SCALE), Image.NEAREST)
    w, h = src.size
    shade = shading(src)

    base = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bpx = base.load()
    crack_cells = {}
    for (x, y), t in shade.items():
        key = ((x // CELL) % MATERIAL_SIZE, (y // CELL) % MATERIAL_SIZE)
        if key in cracks:
            crack_cells[(x, y)] = cracks[key]
            # sous une fissure, la matiere reste sombre : c'est le calque
            # lumineux qui l'eclairera par-dessus
            r, g, b = matrix.get(key, (18, 18, 21))
        else:
            r, g, b = matrix.get(key, (18, 18, 21))
        # La matiere de reference tourne autour de 12 sur 255 : la moduler
        # d'un facteur proche de 1 ne produirait qu'un aplat noir sans volume.
        # On l'etale donc largement, avec un plancher pour qu'aucune face ne
        # tombe a zero et ne se confonde avec un trou dans le modele.
        v = 14 + 46 * t
        m = max(1, (r + g + b) / 3.0)
        bpx[x, y] = (min(255, int(r / m * v)), min(255, int(g / m * v)),
                     min(255, int(b / m * v)), 255)
    base.save(os.path.join(ARMOR_DIR, "arcencium_layer_%d.png" % layer_n))

    for f in range(NFRAMES):
        shift = f / NFRAMES
        glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        gpx = glow.load()
        for (x, y), (r, g, b) in crack_cells.items():
            hh, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            rr, gg, bb = colorsys.hsv_to_rgb((hh + shift) % 1.0, s, v)
            gpx[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), 255)
        glow.save(os.path.join(ARMOR_DIR, "arcencium_cracks_%d_%02d.png" % (layer_n, f)))

    print("  calque %d : %dx%d, %d pixels de matiere, %d de fissure, %d images"
          % (layer_n, w, h, len(shade), len(crack_cells), NFRAMES))
    return base, crack_cells


def main():
    for path, label in ((VANILLA_JAR, "jar vanilla"), (MATERIAL, "planche de matiere")):
        if not os.path.isfile(path):
            sys.exit("%s introuvable : %s" % (label, path))
    os.makedirs(ARMOR_DIR, exist_ok=True)

    matrix, cracks = split_material()
    print("Planche de matiere : %d pixels sombres, %d pixels de fissure (%.0f %%)"
          % (len(matrix), len(cracks),
             100.0 * len(cracks) / max(1, len(matrix) + len(cracks))))

    # on efface les anciennes images avant d'ecrire : un reste d'une version
    # precedente serait pose en jeu sans qu'on le voie ici
    for old in os.listdir(ARMOR_DIR):
        if old.startswith("arcencium_cracks_"):
            os.remove(os.path.join(ARMOR_DIR, old))

    built = [build(n, matrix, cracks) for n in (1, 2)]

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        s = 6
        board = Image.new("RGBA", (64 * s * 2 + 20, 32 * s + 20), (22, 22, 26, 255))
        for i, (base, crack_cells) in enumerate(built):
            merged = base.copy()
            mpx = merged.load()
            for (x, y), c in crack_cells.items():
                mpx[x, y] = c + (255,)
            r = merged.resize((64 * s, 32 * s), Image.NEAREST)
            board.paste(r, (10 + i * (64 * s + 10), 10), r)
        p = os.path.join(PREVIEW, "armure_calques.png")
        board.save(p)
        print("  apercu %s" % p)


if __name__ == "__main__":
    main()
