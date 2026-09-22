"""La carte de la faune de Haven : l'eau du port, sa profondeur, et les quais.

Les animaux de la ville (cahier §79.4 et §85) vivent a des places qu'aucune
carte ne donnait encore : les poissons dans le BASSIN du port -- l'eau fermee
par l'arc de la ville et la jetee des deux tours --, les dangers AU LARGE, hors
de la jetee, les mouettes, les phoques et les crabes sur les QUAIS. Cet outil
les tire du volume que le jeu pose (ctyport.jakv), comme jak_preview.py.

  - L'EAU LIBRE : une colonne dont la cellule de surface (eau_y_max de
    haven_invasion.json) est de l'eau et que rien ne couvre a moins de vingt
    cellules au-dessus. Sous un ponton ou le pont des tours (cellules 58 a 74),
    l'eau est couverte : elle ne compte pas pour la separation, et c'est ce qui
    ferme le bassin sous le pont des tours. Les cables et les rails de glisse
    (cellules 86 a 106) ne couvrent rien : sans cette limite, le cable de la tour
    ouest coupait le bassin en deux.
  - LA PROFONDEUR : les cellules d'eau d'affilee depuis la surface, jusqu'a 26.
  - LE BASSIN : la plus grande etendue d'eau libre d'un seul tenant qui ne touche
    pas le bord de la grille. LE LARGE : toute eau libre reliee au bord (a deux
    colonnes pres : le rideau de barrieres noyees tient la premiere et la derniere). Le reste
    (bassins des tours, flaques) : ni l'un ni l'autre.
  - LES QUAIS : les cellules de pieds de la carte de l'invasion (sol plein, deux
    cellules d'air) a trois colonnes au plus d'une eau libre, et pas plus haut que
    douze cellules au-dessus de la surface (la jetee et les rues du bord sont a
    neuf ; les pontons, a une ou deux). Le jeu trie ensuite par hauteur : les
    phoques et les crabes veulent les pontons, les mouettes prennent tout.

Sortie : data/emeraldweapons/jak/haven_fauna.json (lu par HavenFaunaData) et
une image build/jak/faune-carte.png pour la REGARDER (bleu : bassin ; bleu nuit :
large ; turquoise : flaques ; jaune : quais du bassin ; orange : quais du large).

    python tools/haven_fauna_map.py
"""

import hashlib
import json
import os
import sys
from collections import deque

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jak_preview  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons", "jak")
OUT_JSON = os.path.join(DATA, "haven_fauna.json")
OUT_IMG = os.path.join(ROOT, "build", "jak", "faune-carte.png")

TILE = 16
MAX_DEPTH = 26
QUAY_REACH = 3
QUAY_RISE = 12
FEET_BASE = 58
EDGE = 2
COVER_REACH = 20


def pack(x, y, z):
    return x | (z << 11) | (y << 21)


def layer_masks(dims, palette, runs, wanted_layers, blocks):
    """Un masque par couche demandee : 1 la ou le bloc est dans {@code blocks}."""
    w, h, d = dims
    layer = w * d
    masks = {y: bytearray(layer) for y in wanted_layers}
    index = 0
    one = b"\x01"
    for block, count in runs:
        end = index + count
        if block in blocks:
            y0 = index // layer
            y1 = (end - 1) // layer
            for y in range(y0, y1 + 1):
                if y not in masks:
                    continue
                base = y * layer
                lo = max(index, base) - base
                hi = min(end, base + layer) - base
                masks[y][lo:hi] = one * (hi - lo)
        index = end
    return masks


def to_int(mask):
    return int.from_bytes(mask, "little")


def main():
    volume = jak_preview.read_volume("ctyport")
    dims = volume["dims"]
    w, h, d = dims
    layer = w * d
    palette = volume["palette"]
    runs = volume["runs"]
    with open(os.path.join(DATA, "haven_invasion.json"), encoding="utf-8") as f:
        invasion = json.load(f)
    surface = invasion["eau_y_max"]

    water_ids = {i for i, n in enumerate(palette) if n == "minecraft:water"}
    open_ids = {i for i, n in enumerate(palette) if n in ("minecraft:air", "minecraft:cave_air")}
    solid_ids = set(range(len(palette))) - water_ids - open_ids

    print("volume %s : %d x %d x %d, surface %d" % (volume["sha1"][:10], w, h, d, surface))
    water = layer_masks(dims, palette, runs, range(max(0, surface - MAX_DEPTH), surface + 1), water_ids)
    solid = layer_masks(dims, palette, runs, range(surface + 1, min(h, surface + 1 + COVER_REACH)), solid_ids)

    covered = 0
    for y in range(surface + 1, min(h, surface + 1 + COVER_REACH)):
        covered |= to_int(solid[y])
    top = to_int(water[surface])
    full = int.from_bytes(b"\x01" * layer, "little")
    open_top = top & (full ^ covered)

    # la profondeur en voies d'un octet : la somme de 26 masques de 0 ou 1 ne deborde pas
    alive = top
    depth_sum = alive
    for k in range(1, MAX_DEPTH):
        y = surface - k
        if y < 0 or y not in water:
            break
        alive &= to_int(water[y])
        depth_sum += alive
    depth = depth_sum.to_bytes(layer, "little")
    opened = open_top.to_bytes(layer, "little")

    # les etendues d'eau libre, par remplissage
    label = bytearray(layer)          # 0 rien, 1 bassin, 2 large, 3 flaque
    comp = [-1] * layer
    components = []
    for start in range(layer):
        if not opened[start] or comp[start] >= 0:
            continue
        cid = len(components)
        comp[start] = cid
        queue = deque([start])
        size = 0
        edge = False
        while queue:
            i = queue.popleft()
            size += 1
            z, x = divmod(i, w)
            # le bord : le rideau de barrieres noyees occupe la premiere et la derniere colonne
            if x <= EDGE or z <= EDGE or x >= w - 1 - EDGE or z >= d - 1 - EDGE:
                edge = True
            for j in ((i - 1) if x > 0 else -1, (i + 1) if x < w - 1 else -1,
                      (i - w) if z > 0 else -1, (i + w) if z < d - 1 else -1):
                if j >= 0 and opened[j] and comp[j] < 0:
                    comp[j] = cid
                    queue.append(j)
        components.append((size, edge))
    inner = [(size, cid) for cid, (size, edge) in enumerate(components) if not edge]
    basin = max(inner)[1] if inner else -1
    kinds = []
    for cid, (size, edge) in enumerate(components):
        kinds.append(1 if cid == basin else 2 if edge else 3)
    for i in range(layer):
        if comp[i] >= 0:
            label[i] = kinds[comp[i]]
    count = [0, 0, 0, 0]
    for v in label:
        count[v] += 1
    print("eau libre : bassin %d colonnes, large %d, flaques %d (%d etendues)"
          % (count[1], count[2], count[3], len(components)))

    # les quais : cellules de pieds de l'invasion au bord de l'eau libre
    quays = {}
    quay_count = [0, 0, 0]
    for t in invasion["apparition_sol"]["tuiles"]:
        tx, tz = t["tuile"]
        feet = t["pieds"]
        cells = []
        for i, c in enumerate(feet):
            if c != ".":
                cells.append((tx * TILE + i % TILE, FEET_BASE + (ord(c) - 65), tz * TILE + i // TILE))
        for a in t.get("etages", []):
            cells.append((tx * TILE + a[0], a[1], tz * TILE + a[2]))
        for (x, y, z) in cells:
            if y > surface + 1 + QUAY_RISE:
                continue
            side = 0
            for dz in range(-QUAY_REACH, QUAY_REACH + 1):
                for dx in range(-QUAY_REACH, QUAY_REACH + 1):
                    xx, zz = x + dx, z + dz
                    if 0 <= xx < w and 0 <= zz < d:
                        v = label[zz * w + xx]
                        if v == 1:
                            side = 1
                        elif v == 2 and side == 0:
                            side = 2
            if side:
                quays.setdefault((tx, tz), []).append((x, y, z, side))
                quay_count[side] += 1
    print("quais : %d cellules au bord du bassin, %d au bord du large" % (quay_count[1], quay_count[2]))

    # les tuiles
    tiles = []
    for tz in range((d + TILE - 1) // TILE):
        for tx in range((w + TILE - 1) // TILE):
            chars = []
            n_basin = n_large = 0
            for dz in range(TILE):
                for dx in range(TILE):
                    x, z = tx * TILE + dx, tz * TILE + dz
                    if x >= w or z >= d:
                        chars.append(".")
                        continue
                    i = z * w + x
                    v = label[i]
                    dep = min(depth[i], MAX_DEPTH)
                    if v == 1 and dep > 0:
                        chars.append(chr(96 + dep))
                        n_basin += 1
                    elif v == 2 and dep > 0:
                        chars.append(chr(64 + dep))
                        n_large += 1
                    else:
                        chars.append(".")
            q = quays.get((tx, tz), [])
            if n_basin == 0 and n_large == 0 and not q:
                continue
            entry = {"tuile": [tx, tz], "bassin": n_basin, "large": n_large}
            if n_basin or n_large:
                entry["eau"] = "".join(chars)
            if q:
                entry["quais"] = [[x - tx * TILE, y, z - tz * TILE, s] for (x, y, z, s) in q]
            tiles.append(entry)

    out = {
        "_format": [
            "Carte de la faune de Haven, ecrite par tools/haven_fauna_map.py depuis ctyport.jakv ; lue par",
            "com.emerald.haven.fauna.HavenFaunaData. UNITES : cellules du volume (Y monde = cellule + 5).",
            "eau_y : la cellule de surface de l'eau. Une tuile couvre 16 x 16 colonnes (tuile [tx, tz] : x de 16 tx a 16 tx + 15).",
            "eau : 256 caracteres, ligne par ligne (dz de 0 a 15, puis dx de 0 a 15) ; '.' = pas d'eau libre ;",
            "  'a' a 'z' = BASSIN du port, profondeur 1 a 26 cellules (z : 26 ou plus) ; 'A' a 'Z' = LARGE, de meme.",
            "  L'eau libre : surface d'eau que rien ne couvre. Le bassin : la plus grande etendue qui ne touche pas le bord ;",
            "  le large : toute etendue qui touche le bord de la grille. Les flaques (bassins des tours) ne sont pas ecrites.",
            "quais : [dx, y, dz, cote] ; une cellule de pieds de la carte de l'invasion a trois colonnes au plus d'une eau",
            "  libre, et au plus douze cellules au-dessus de la surface ; cote 1 = bassin, 2 = large.",
        ],
        "volume": "ctyport",
        "sha1": volume["sha1"],
        "dims": [w, h, d],
        "eau_y": surface,
        "tuiles_eau": sum(1 for t in tiles if "eau" in t),
        "colonnes": {"bassin": count[1], "large": count[2]},
        "tuiles": tiles,
    }
    text = json.dumps(out, ensure_ascii=False, separators=(",", ":"))
    with open(OUT_JSON, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("ecrit : %s (%d tuiles, %d octets, sha1 %s)" % (OUT_JSON, len(tiles), len(text),
                                                          hashlib.sha1(text.encode("utf-8")).hexdigest()[:10]))

    # l'image, pour regarder
    img = Image.new("RGB", (w, d), (120, 120, 120))
    px = img.load()
    for i in range(layer):
        z, x = divmod(i, w)
        v = label[i]
        if v == 1:
            dep = min(depth[i], MAX_DEPTH)
            px[x, z] = (40, 110 - dep * 2, 230)
        elif v == 2:
            px[x, z] = (20, 30, 110)
        elif v == 3:
            px[x, z] = (40, 200, 190)
        elif depth[i] and not opened[i]:
            px[x, z] = (70, 90, 140)        # eau couverte (sous un ponton, un pont)
    for (tx, tz), q in quays.items():
        for (x, y, z, s) in q:
            px[x, z] = (250, 220, 40) if s == 1 else (250, 130, 30)
    os.makedirs(os.path.dirname(OUT_IMG), exist_ok=True)
    img.save(OUT_IMG)
    print("image : %s" % OUT_IMG)


if __name__ == "__main__":
    main()
