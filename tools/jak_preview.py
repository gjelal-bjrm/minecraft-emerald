"""Dessine un quartier converti, vu du dessus et en coupe, pour le REGARDER.

Pourquoi cet outil existe : les corrections precedentes du port ont ete
validees par des chiffres -- « zero colonne d'eau sur un sol », « bois a
3,5 % » -- qui etaient justes et ne disaient pourtant rien de ce que le joueur
voyait. Les rues restaient noyees, la porte du bar etait muree. Un chiffre
repond a la question qu'on lui pose ; une image montre aussi ce qu'on n'a pas
pense a demander.

Il lit le `.jakv` final, celui que le jeu pose, et non les donnees
intermediaires du convertisseur : c'est le seul moyen de voir exactement ce
qui arrivera en jeu.

Un fichier en version 2 porte l'origine du repere dans son en-tete, et c'est
elle qu'on prend. Pour un fichier en version 1, on la passe en argument ; la
valeur par defaut est celle du port fusionne avec le bar et le stand de tir
(CPO + HHG + GGA).

Quatre vues :
  --top       le bloc le plus haut de chaque colonne, ombre par l'altitude ;
  --section   une coupe verticale, pour les portes et les marches ;
  --layer     une tranche horizontale. C'est elle qui montre les trous dans
              l'eau : a la hauteur de la surface, l'eau est bleue, l'air a ciel
              ouvert JAUNE et l'air sous un plancher brun sombre. Une poche
              jaune ou brune au milieu du bleu est un trou que le joueur verra ;
  --facade    la zone vue d'un cote, comme depuis un bateau. C'est elle qui
              montre si la ville touche l'eau ou flotte au-dessus.

Usage :
    python tools/jak_preview.py ctyport --top
    python tools/jak_preview.py ctyport --crop -140 1250 -10 1400 --scale 4 --top
    python tools/jak_preview.py ctyport --section-x -73.4 --crop -140 1250 -10 1400 --scale 4
    python tools/jak_preview.py ctyport --layer 0 --crop -140 1250 -10 1400 --scale 4
    python tools/jak_preview.py ctyport --facade=-z --crop -140 1250 -20 1380 --scale 4
    python tools/jak_preview.py chemin/vers/autre.jakv --top --tag avant
"""

import argparse
import hashlib
import os
import struct
import sys
import zlib

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons", "jak")
OUT = os.path.join(ROOT, "build", "jak")

# origine du port fusionne (CPO + HHG + GGA), mesuree par le convertisseur
DEFAULT_ORIGIN = (-434.7777, -57.50163, 1126.3511)

# couleurs moyennes approchees des textures, par fragment de nom de bloc
COLORS = [
    ("water", (63, 118, 228)),
    ("polished_andesite", (132, 134, 133)),
    ("andesite", (136, 136, 136)),
    ("smooth_stone", (158, 158, 158)),
    ("deepslate_tiles", (54, 54, 55)),
    ("deepslate_bricks", (70, 70, 71)),
    ("cobbled_deepslate", (77, 77, 80)),
    ("polished_deepslate", (72, 72, 73)),
    ("chiseled_deepslate", (54, 54, 54)),
    ("tuff_bricks", (98, 102, 95)),
    ("tuff", (108, 109, 102)),
    ("gray_concrete", (54, 57, 61)),
    ("stone_bricks", (122, 121, 122)),
    ("mossy", (100, 112, 90)),
    ("blackstone", (41, 34, 40)),
    ("copper", (160, 110, 80)),
    ("planks", (115, 85, 50)),
    ("wood", (115, 85, 50)),
    ("mud", (140, 106, 80)),
    ("stone_tile", (130, 130, 130)),
    # l'arene de Spargus (tools/jak_arena.py)
    ("smooth_red_sandstone", (181, 98, 31)),
    ("smooth_sandstone", (223, 214, 170)),
    ("nether_bricks", (44, 21, 26)),
    ("terracotta", (152, 94, 67)),
    ("lava", (255, 120, 20)),
]

OPEN_AIR = (250, 214, 60)       # air a ciel ouvert, dans une tranche
COVERED_AIR = (92, 60, 40)      # air sous un plancher, dans une tranche

# Ce que le joueur ne voit pas : le rideau de barrieres du bord et l'air des
# poches sous la mer. Les vues de dessus, de face et en coupe les sautent,
# comme le jeu ; seule la tranche les colore, pour qu'on puisse les verifier.
INVISIBLE = ("minecraft:barrier", "minecraft:barrier[waterlogged=true]", "minecraft:cave_air")
SLICE_ONLY = {"minecraft:barrier": (240, 120, 210),
              "minecraft:barrier[waterlogged=true]": (110, 90, 240),   # le rideau noye, dans la mer
              "minecraft:cave_air": (40, 170, 160)}


def visible_runs(palette, runs):
    """Les plages ou les blocs invisibles deviennent de l'air."""
    hidden = {i for i, n in enumerate(palette) if n in INVISIBLE}
    if not hidden:
        return runs
    return [(0 if b in hidden else b, n) for b, n in runs]


def color_of(name):
    for key, rgb in COLORS:
        if key in name:
            return rgb
    return (200, 0, 200)          # un bloc sans couleur connue se voit tout de suite


def load(name):
    """Les dimensions, la palette et les plages : ce dont se servent les vues."""
    volume = read_volume(name)
    return volume["dims"], volume["palette"], volume["runs"]


def read_volume(name):
    """Le volume entier, en-tete compris.

    La version 1 ne dit pas ou elle se pose ; la version 2 porte l'origine, la
    taille de cellule et le sha1 de ses donnees (voir write_blob dans
    jak_voxelize.py). Un sha1 faux arrete tout : une image tiree d'un fichier
    abime serait pire qu'aucune image.
    """
    path = name if name.endswith(".jakv") else os.path.join(DATA, name + ".jakv")
    raw = zlib.decompress(open(path, "rb").read())
    if raw[:4] != b"JAKV":
        sys.exit("pas un volume Jak")
    version = raw[4]
    off = 5
    w, h, d = struct.unpack(">III", raw[off:off + 12])
    off += 12
    origin = cell = digest = None
    if version == 2:
        ox, oy, oz, cell = struct.unpack(">dddd", raw[off:off + 32])
        off += 32
        digest = raw[off:off + 20].hex()
        off += 20
        if hashlib.sha1(raw[off:]).hexdigest() != digest:
            sys.exit("%s : le sha1 ne correspond pas aux donnees" % path)
        origin = (ox, oy, oz)
    elif version != 1:
        sys.exit("%s : version de format inconnue %d" % (path, version))
    (n,) = struct.unpack(">H", raw[off:off + 2])
    off += 2
    palette = []
    for _ in range(n):
        (ln,) = struct.unpack(">H", raw[off:off + 2])
        off += 2
        palette.append(raw[off:off + ln].decode("utf-8"))
        off += ln
    (count,) = struct.unpack(">I", raw[off:off + 4])
    off += 4
    runs = []
    for _ in range(count):
        block = raw[off]
        off += 1
        value = 0
        shift = 0
        while True:
            part = raw[off]
            off += 1
            value |= (part & 0x7F) << shift
            if not part & 0x80:
                break
            shift += 7
        runs.append((block, value))
    return {"dims": (w, h, d), "palette": palette, "runs": runs, "version": version,
            "origin": origin, "cell": cell, "sha1": digest}


def top_view(dims, palette, runs):
    """Le bloc le plus haut de chaque colonne, et son altitude."""
    w, h, d = dims
    layer = w * d
    top_y = [-1] * layer
    top_b = [0] * layer
    index = 0
    for block, count in runs:
        if block:
            i = index
            end = index + count
            while i < end:
                y, col = divmod(i, layer)
                span = min(end - i, layer - col)
                for c in range(col, col + span):
                    if y >= top_y[c]:
                        top_y[c] = y
                        top_b[c] = block
                i += span
        index += count
    return top_y, top_b


def section(dims, runs, axis, fixed):
    """Les blocs d'une coupe verticale : x fixe (plan z-y) ou z fixe (plan x-y).

    Pour x fixe, les cellules d'une couche se trouvent tous les w indices : on
    calcule directement la tranche de z que chaque plage recouvre, au lieu de
    parcourir toute la profondeur pour chacune -- ce qui aurait coute des
    centaines de millions de tests sur le port.
    """
    w, h, d = dims
    layer = w * d
    cells = {}
    index = 0
    for block, count in runs:
        if block:
            start, end = index, index + count
            for y in range(start // layer, (end - 1) // layer + 1):
                base = y * layer
                if axis == "x":
                    lo = start - base - fixed
                    hi = end - 1 - base - fixed
                    z_lo = max(0, -(-lo // w))
                    z_hi = min(d - 1, hi // w)
                    for z in range(z_lo, z_hi + 1):
                        cells[(z, y)] = block
                else:
                    row0 = base + fixed * w
                    lo, hi = max(start, row0), min(end, row0 + w)
                    for i in range(lo, hi):
                        cells[(i - row0, y)] = block
        index += count
    return cells


def horizontal(dims, runs, y):
    """Les blocs d'une couche horizontale entiere, a la cellule d'altitude y."""
    w, h, d = dims
    layer = w * d
    lo, hi = y * layer, (y + 1) * layer
    cells = [0] * layer
    index = 0
    for block, count in runs:
        end = index + count
        if block and end > lo and index < hi:
            for i in range(max(index, lo), min(end, hi)):
                cells[i - lo] = block
        index = end
        if index >= hi:
            break
    return cells


def dense(dims, runs, box):
    """Les cellules d'une boite (x0, x1, z0, z1), dans un tableau plein.

    Les vues qui regardent A TRAVERS le volume ont besoin d'un acces direct a
    chaque cellule ; on ne decode que la boite demandee.
    """
    w, h, d = dims
    x0, x1, z0, z1 = box
    bw, bd = x1 - x0 + 1, z1 - z0 + 1
    cells = bytearray(bw * h * bd)
    layer = w * d
    index = 0
    for block, count in runs:
        if block:
            i, end = index, index + count
            while i < end:
                y, rem = divmod(i, layer)
                z, x = divmod(rem, w)
                n = min(end - i, w - x)
                if z0 <= z <= z1:
                    base = (y * bd + (z - z0)) * bw - x0
                    for xx in range(max(x, x0), min(x + n - 1, x1) + 1):
                        cells[base + xx] = block
                i += n
        index += count
    return cells, bw, bd


def facade(dims, runs, box, side, y_lo):
    """Ce qu'on voit en regardant la boite depuis un cote, sans perspective.

    `side` dit ou se tient l'observateur : "+z" regarde vers les z
    decroissants, "-x" vers les x croissants, etc. Chaque pixel prend le
    premier bloc rencontre, assombri avec la distance. C'est la vue qui montre
    si la ville touche l'eau ou flotte au-dessus : sous un bord qui flotte, on
    voit le ciel ou le fond, pas un mur.
    """
    w, h, d = dims
    cells, bw, bd = dense(dims, runs, box)
    along_z = side[1] == "z"
    width, depth = (bw, bd) if along_z else (bd, bw)
    order = range(depth - 1, -1, -1) if side[0] == "+" else range(depth)
    pixels = {}
    for y in range(y_lo, h):
        for u in range(width):
            for step, k in enumerate(order):
                if along_z:
                    block = cells[(y * bd + k) * bw + u]
                else:
                    block = cells[(y * bd + u) * bw + k]
                if block:
                    pixels[(u, y)] = (block, step / max(1, depth - 1))
                    break
    return pixels, width


def save(img, scale, path, message):
    if scale > 1:
        img = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
    img.save(path)
    print(message % path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("name", help="nom du quartier, ou chemin d'un fichier .jakv")
    parser.add_argument("--origin", nargs=3, type=float, default=None,
                        help="coin du volume ; par defaut celui de l'en-tete (version 2)")
    parser.add_argument("--crop", nargs=4, type=float, metavar=("X0", "Z0", "X1", "Z1"),
                        help="zone en coordonnees du monde du jeu")
    parser.add_argument("--scale", type=int, default=1)
    parser.add_argument("--top", action="store_true")
    parser.add_argument("--section-x", type=float)
    parser.add_argument("--section-z", type=float)
    parser.add_argument("--layer", type=float, nargs="+", default=[],
                        help="altitudes du monde du jeu des tranches horizontales")
    parser.add_argument("--facade", choices=("+x", "-x", "+z", "-z"),
                        help="vue de face depuis ce cote de la zone decoupee")
    parser.add_argument("--tag", default="", help="suffixe des images produites")
    args = parser.parse_args()

    volume = read_volume(args.name)
    dims, palette, runs = volume["dims"], volume["palette"], volume["runs"]
    stem = os.path.splitext(os.path.basename(args.name))[0] + (
        "-" + args.tag if args.tag else "")
    w, h, d = dims
    # l'origine vient de l'en-tete quand le fichier la porte : une image
    # recadree sur une origine perimee montrerait le mauvais endroit
    ox, oy, oz = args.origin or volume["origin"] or DEFAULT_ORIGIN
    x0, z0, x1, z1 = 0, 0, w - 1, d - 1
    if args.crop:
        x0 = max(0, int(args.crop[0] - ox))
        z0 = max(0, int(args.crop[1] - oz))
        x1 = min(w - 1, int(args.crop[2] - ox))
        z1 = min(d - 1, int(args.crop[3] - oz))
    os.makedirs(OUT, exist_ok=True)
    colors = [SLICE_ONLY.get(n) or color_of(n) for n in palette]
    # ce que le joueur voit : sans le rideau de barrieres ni l'air des poches
    seen_runs = visible_runs(palette, runs)

    top_y = top_b = None
    if args.top or args.layer:
        top_y, top_b = top_view(dims, palette, seen_runs)

    if args.top:
        img = Image.new("RGB", (x1 - x0 + 1, z1 - z0 + 1), (10, 10, 14))
        px = img.load()
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                c = z * w + x
                if top_y[c] < 0:
                    continue
                r, g, b = colors[top_b[c]]
                # l'ombrage par l'altitude fait lire le relief sans perspective
                k = 0.55 + 0.45 * top_y[c] / max(1, h - 1)
                px[x - x0, z - z0] = (int(r * k), int(g * k), int(b * k))
        save(img, args.scale, os.path.join(OUT, "%s-dessus.png" % stem), "vue de dessus : %s")

    for value in args.layer:
        y = int(value - oy)
        cells = horizontal(dims, runs, y)
        img = Image.new("RGB", (x1 - x0 + 1, z1 - z0 + 1))
        px = img.load()
        counts = {"eau": 0, "air ouvert": 0, "air couvert": 0, "solide": 0}
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                c = z * w + x
                block = cells[c]
                if block:
                    px[x - x0, z - z0] = colors[block]
                    # le nom exact : « barrier[waterlogged=true] » n'est pas de l'eau
                    counts["eau" if palette[block] == "minecraft:water" else "solide"] += 1
                elif top_y[c] > y:
                    px[x - x0, z - z0] = COVERED_AIR
                    counts["air couvert"] += 1
                else:
                    px[x - x0, z - z0] = OPEN_AIR
                    counts["air ouvert"] += 1
        tag = ("%+g" % value).replace("+", "p").replace("-", "m").replace(".", "_")
        save(img, args.scale, os.path.join(OUT, "%s-tranche-%s.png" % (stem, tag)),
             "tranche y=%g (cellule %d) : %%s" % (value, y))
        print("   " + ", ".join("%s %d" % kv for kv in counts.items()))

    if args.facade:
        # on commence deux blocs sous le fond de la nappe : l'eau et ce qui y
        # plonge doivent se voir
        y_lo = max(0, int(-8.0 - oy))
        pixels, width = facade(dims, seen_runs, (x0, x1, z0, z1), args.facade, y_lo)
        y_hi = max([y for (_, y) in pixels] or [y_lo]) + 2
        img = Image.new("RGB", (width, y_hi - y_lo + 1), (230, 236, 245))
        px = img.load()
        for (u, y), (block, far) in pixels.items():
            r, g, b = colors[block]
            k = 1.0 - 0.65 * far
            # le premier plan un peu eclairci : un mur au bord se detache du fond
            px[u, y_hi - y] = (int(r * k), int(g * k), int(b * k))
        side = args.facade.replace("+", "p").replace("-", "m")
        save(img, args.scale, os.path.join(OUT, "%s-facade-%s.png" % (stem, side)),
             "facade depuis %s : %%s" % args.facade)

    for axis, value in (("x", args.section_x), ("z", args.section_z)):
        if value is None:
            continue
        fixed = int(value - (ox if axis == "x" else oz))
        cells = section(dims, seen_runs, axis, fixed)
        span_lo, span_hi = (z0, z1) if axis == "x" else (x0, x1)
        ys = [y for (_, y) in cells] or [0]
        y_lo, y_hi = max(0, min(ys) - 2), min(h - 1, max(ys) + 2)
        img = Image.new("RGB", (span_hi - span_lo + 1, y_hi - y_lo + 1), (230, 236, 245))
        px = img.load()
        for (u, y), block in cells.items():
            if span_lo <= u <= span_hi and y_lo <= y <= y_hi:
                px[u - span_lo, y_hi - y] = colors[block]
        save(img, args.scale, os.path.join(OUT, "%s-coupe-%s.png" % (stem, axis)),
             "coupe %s=%.1f (cellule %d, altitudes de cellule %d a %d) : %%s"
             % (axis, value, fixed, y_lo, y_hi))


if __name__ == "__main__":
    main()
