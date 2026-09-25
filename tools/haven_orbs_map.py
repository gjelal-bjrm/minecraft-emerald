"""Les places des orbes precurseurs caches dans Haven (lot 3, cahier §86).

« Des orbes partout dans la ville, a ramasser, pour pousser a explorer » (le joueur, §79.7).
Cent cinquante orbes, un de chaque par joueur, tires une fois pour toutes et ecrits dans
data/emeraldweapons/jak/haven_orbs.json (lu par HavenOrbs) :

  - 72 dans les RUES (cellules de pieds de la carte de l'invasion, au niveau de la rue) ;
  - 28 sur les TOITS et les terrasses (pieds a quatre cellules ou plus au-dessus de la rue) ;
  - 10 dans les TOURS : autour des stations de leurs portails (pied, terrasse, sommet), la
    ou l'on tient debout -- la carte de l'invasion ne descend pas si haut ;
  - 25 au fond du BASSIN du port ;
  - 15 au fond du LARGE, la ou rodent les dangers.

Tirage deterministe (graine fixe) et ECARTE : un orbe a au moins 36 blocs de tout autre de
sa famille (30 sous l'eau), hors des zones sures (appartements, Hip Hog), a plus de 6 blocs
d'un point d'eco. Une image de controle : build/jak/orbes-carte.png.

LES ORBES DES RAILS (cahier §100, choix du joueur : « des orbes le long des rails ») : une
trentaine au-dessus des six rails de Jak 3, un tous les onze blocs et demi, a hauteur du joueur
qui glisse -- on ne les prend qu'en JET-Board (ou avec les ailes +20). Ils s'AJOUTENT apres les
150 caches : le numero d'un orbe est son rang, et les fiches des joueurs retiennent les numeros
trouves. --rails les repose sans toucher aux autres ; ne pas relancer le tirage des 150.

    python tools/haven_orbs_map.py            # le tirage des 150 (NE PAS relancer : les rangs changeraient)
    python tools/haven_orbs_map.py --rails    # les orbes des rails, ajoutes a la fin
"""

import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons", "jak")
OUT = os.path.join(DATA, "haven_orbs.json")
IMG = os.path.join(ROOT, "build", "jak", "orbes-carte.png")

STREET = 66
FEET_BASE = 58
SEED = 86
QUOTAS = {"rue": 72, "toit": 28, "tour": 10, "bassin": 25, "large": 15}
SPACING = {"rue": 36, "toit": 26, "tour": 6, "bassin": 30, "large": 30}
# les stations des portails des deux tours (HavenGates) : pied, terrasse, sommet
TOWER_STATIONS = [(482, 66, 585), (482, 123, 590), (482, 156, 605), (770, 66, 585), (770, 123, 590), (770, 156, 605)]


def inside(box, x, y, z):
    (x0, y0, z0), (x1, y1, z1) = box
    return x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1


def tower_spots(safe):
    """Autour des stations des tours : un sol plein, deux cellules d'air, a 3 a 9 cellules de la station."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import jak_preview
    volume = jak_preview.read_volume("ctyport")
    w, h, d = volume["dims"]
    pal = volume["palette"]
    solid = {i for i, n in enumerate(pal) if n not in ("minecraft:air", "minecraft:cave_air", "minecraft:water")}
    spots = []
    layers = {}
    for (sx, sy, sz) in TOWER_STATIONS:
        for y in (sy - 1, sy, sy + 1):
            if y not in layers:
                layers[y] = jak_preview.horizontal(volume["dims"], volume["runs"], y)
        floor, feet, head = layers[sy - 1], layers[sy], layers[sy + 1]
        for dz in range(-9, 10):
            for dx in range(-9, 10):
                r2 = dx * dx + dz * dz
                if r2 < 9 or r2 > 81:
                    continue
                x, z = sx + dx, sz + dz
                i = z * w + x
                if floor[i] in solid and feet[i] not in solid and head[i] not in solid:
                    spots.append((x, sy, z))
    return spots


def main():
    inv = json.load(open(os.path.join(DATA, "haven_invasion.json"), encoding="utf-8"))
    fauna = json.load(open(os.path.join(DATA, "haven_fauna.json"), encoding="utf-8"))
    safe = [(tuple(z["min"]), tuple(z["max"])) for z in inv["zone_sure"]]
    eco = [tuple(p["pieds"]) for p in inv["points_eco"]]
    surface = fauna["eau_y"]

    groups = {k: [] for k in QUOTAS}
    for t in inv["apparition_sol"]["tuiles"]:
        tx, tz = t["tuile"]
        cells = []
        for i, c in enumerate(t["pieds"]):
            if c != ".":
                cells.append((tx * 16 + i % 16, FEET_BASE + ord(c) - 65, tz * 16 + i // 16))
        for a in t.get("etages", []):
            cells.append((tx * 16 + a[0], a[1], tz * 16 + a[2]))
        for (x, y, z) in cells:
            if any(inside(b, x, y, z) for b in safe):
                continue
            if any(abs(x - ex) + abs(z - ez) < 6 for (ex, ey, ez) in eco):
                continue
            kind = "tour" if y >= 100 else "toit" if y >= STREET + 4 else "rue"
            groups[kind].append((x, y, z))
    groups["tour"] += tower_spots(safe)
    for t in fauna["tuiles"]:
        if "eau" not in t:
            continue
        tx, tz = t["tuile"]
        for i, c in enumerate(t["eau"]):
            if c == ".":
                continue
            depth = ord(c.lower()) - 96
            if depth < 4:
                continue
            x, z = tx * 16 + i % 16, tz * 16 + i // 16
            y = surface - depth + 1          # au fond, dans l'eau
            groups["bassin" if c.islower() else "large"].append((x, y, z))

    rnd = random.Random(SEED)
    chosen = []
    for kind, quota in QUOTAS.items():
        pool = groups[kind][:]
        rnd.shuffle(pool)
        picked = []
        spacing = SPACING[kind]
        for (x, y, z) in pool:
            if len(picked) >= quota:
                break
            if all((x - px) ** 2 + (z - pz) ** 2 >= spacing ** 2 for (px, py, pz) in picked):
                picked.append((x, y, z))
        if len(picked) < quota:
            print("  %s : seulement %d sur %d" % (kind, len(picked), quota))
        chosen += [(kind, p) for p in picked]
        print("%-7s %3d orbes (%d places possibles, ecart %d)" % (kind, len(picked), len(pool), spacing))

    out = {
        "_format": [
            "Orbes precurseurs caches de Haven, ecrits par tools/haven_orbs_map.py ; lus par com.emerald.haven.quest.HavenOrbs.",
            "cellule : la cellule du volume ou flotte l'orbe (Y monde = cellule + 5) ; lieu : rue, toit, tour, bassin, large.",
            "Le NUMERO est l'indice dans la liste : les fiches des joueurs retiennent les numeros trouves. Ne pas reordonner.",
        ],
        "orbes": [{"cellule": list(p), "lieu": kind} for kind, p in chosen],
    }
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print("ecrit : %s (%d orbes)" % (OUT, len(chosen)))

    w, d = fauna["dims"][0], fauna["dims"][2]
    base = os.path.join(ROOT, "build", "jak", "faune-carte.png")
    img = Image.open(base).convert("RGB") if os.path.exists(base) else Image.new("RGB", (w, d), (60, 60, 60))
    dr = ImageDraw.Draw(img)
    colors = {"rue": (255, 140, 0), "toit": (255, 60, 200), "tour": (255, 255, 255), "bassin": (0, 255, 120), "large": (255, 0, 0)}
    for kind, (x, y, z) in chosen:
        dr.ellipse([x - 4, z - 4, x + 4, z + 4], fill=colors[kind], outline=(0, 0, 0))
    os.makedirs(os.path.dirname(IMG), exist_ok=True)
    img.save(IMG)
    print("image : %s" % IMG)


RAIL_SPACING = 11.4


def rails():
    """Les orbes des rails, ajoutes a la fin : les 150 d'avant gardent leur rang."""
    cables = json.load(open(os.path.join(DATA, "haven_cables.json"), encoding="utf-8"))
    with open(OUT, encoding="utf-8") as f:
        data = json.load(f)
    kept = [o for o in data["orbes"] if o["lieu"] != "rail"]
    added = []
    for line in cables["lines"]:
        if line["kind"] != "rail":
            continue
        pts = line["points"]
        lengths = [math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
        total = sum(lengths)
        count = max(1, round(total / RAIL_SPACING))
        step = total / count
        for k in range(count):
            # une demi-maille du bout, puis une maille : jamais sur le saut entre deux rails
            left = step * (k + 0.5)
            i = 0
            while i < len(lengths) - 1 and left > lengths[i]:
                left -= lengths[i]
                i += 1
            t = left / lengths[i] if lengths[i] > 0 else 0.0
            p = [pts[i][a] + (pts[i + 1][a] - pts[i][a]) * t for a in range(3)]
            # a hauteur des hanches de celui qui glisse : l'orbe (cellule + 0,25) flotte de 0,5 a 1,5 bloc au-dessus du tube
            added.append({"cellule": [math.floor(p[0]), round(p[1] + 0.75), math.floor(p[2])], "lieu": "rail"})
    data["orbes"] = kept + added
    data["_format"][1] = ("cellule : la cellule du volume ou flotte l'orbe (Y monde = cellule + 5) ; "
                          "lieu : rue, toit, tour, bassin, large, rail.")
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print("rails : %d orbes ajoutes apres les %d autres -> %s" % (len(added), len(kept), OUT))


if __name__ == "__main__":
    if "--rails" in sys.argv:
        rails()
    else:
        main()
