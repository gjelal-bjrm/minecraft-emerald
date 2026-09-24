"""L'arene du boss : l'arene de Spargus de Jak 3 (niveau wasstada), en blocs (cahier §91).

« Le boss apparait sur un pilier de un de largeur, tres haut dans le ciel, ce n'est pas du
tout logique ni realiste. Peut-etre que nous devrions faire une vraie arene pour le boss.
Par exemple, l'arene de Jak 3 avec la lave » (le joueur, 24 sept.). Choix du joueur : le
SOL PRATICABLE, avec des fosses et des rigoles de lave -- pas la fosse de lave a
plates-formes du jeu ; les murs invisibles et les echafaudages de WASSTADB retires.

Ce que fait le script, dans l'ordre :

1. LA COLLISION DE WASSTADA, rasterisee a --cell metres par bloc (2 par defaut : l'arene
   fait deux cents metres de large, cent blocs suffisent a un boss). Rien de WASSTADB.
2. LES MURS INVISIBLES RETIRES. La collision porte, autour de l'arene et au-dessus des
   gradins, des parois de cent trente metres que le jeu ne dessine pas : elles retiennent
   Jak. On ne garde que la collision a moins d'une cellule du decor VISUEL
   (wasstada-background.glb) ; le reste est rendu en rouge sur les apercus.
3. LE DECOR AUSSI. La collision seule donnait un sol et des gradins en etageres
   flottantes : la couronne de mesas qui ferme l'arene -- jusqu'a cent quarante metres --
   n'a pas de collision (les murs invisibles empechent Jak d'y monter). Le decor visuel est
   donc pose avec elle : c'est lui qui fait l'arene.
   UN BLOC PAR SURFACE (lecon 5 du port) : un plat de la collision est un sol (on y
   marche), un plat du decor seul un toit (le dessus des rochers), le reste un mur.
4. L'ASSISE. La collision n'est qu'une peau : sous le sol de l'arene, tout est plein
   jusqu'au bas de la grille, pour que la lave ait un fond et que rien ne flotte.
5. LA LAVE, dans le sol de l'arene : un anneau de rigoles coupe de quatre passages, et des
   fosses rondes entre l'anneau et les gradins. Le centre reste libre : le boss y nait.
   Une rigole est une tranchee d'un bloc, la lave au fond : on la voit, on l'enjambe.
6. Le volume en data/emeraldweapons/jak/arene_spargus.jakv, et ses reperes (le centre du
   sol, le rayon du sol, les places des gardes) dans arene_spargus.json, que Finale lit.

Usage :
    python tools/jak_arena.py                  # volume, reperes et apercus
    python tools/jak_arena.py --cell 1.5
    python tools/jak_arena.py --out-dir chemin # ailleurs que dans les ressources
"""

import argparse
import json
import math
import os
import sys
from collections import Counter, deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jak_voxelize as jv  # noqa: E402
from jak_assets import mesh_triangles  # noqa: E402

from PIL import Image, ImageDraw  # noqa: E402

LEVEL = "WASSTADA"
VISUAL = os.path.join(jv.LEVELS, "wasstada", "wasstada-background.glb")
NAME = "arene_spargus"
PREVIEW = os.path.join(jv.ROOT, "build", "jak")

# Un bloc par surface. Le gres clair du sol (le sable de l'arene), la terre cuite des
# parois de rocher et des gradins, le gres rouge du dessus des mesas : les couleurs de
# Spargus -- et la lave des rigoles, vive sur le sable. L'assise, invisible, est de la
# meme pierre que les murs.
PALETTE = [
    "minecraft:air",                   # 0
    "minecraft:smooth_sandstone",      # 1 le sol : l'arene, les gradins
    "minecraft:terracotta",            # 2 les murs : parois de rocher, contremarches
    "minecraft:smooth_red_sandstone",  # 3 les toits : le dessus des rochers
    "minecraft:lava",                  # 4 les rigoles et les fosses
]
AIR, FLOOR, WALL, ROOF, LAVA = range(5)

NEIGHBOURS = jv.NEIGHBOURS


def load_collision():
    path = jv.find_obj(LEVEL)
    verts, faces = jv.read_triangles(path)
    print("collision %s : %d triangles" % (os.path.basename(path), len(faces)))
    return verts, faces


def near_visual(collision, visual, reach=1):
    """La collision que le decor dessine, a une cellule pres ; et celle qu'il ne dessine pas."""
    kept = {}
    removed = {}
    for key, flat in collision.items():
        x, y, z = key
        seen = False
        for dx in range(-reach, reach + 1):
            for dy in range(-reach, reach + 1):
                for dz in range(-reach, reach + 1):
                    if (x + dx, y + dy, z + dz) in visual:
                        seen = True
                        break
                if seen:
                    break
            if seen:
                break
        (kept if seen else removed)[key] = flat
    return kept, removed


def column_tops(cells):
    tops = {}
    for (x, y, z) in cells:
        if y > tops.get((x, z), -1):
            tops[(x, z)] = y
    return tops


def arena_floor(kept, dims):
    """Le sol de l'arene : la plus grande nappe de sol plat au niveau le plus etendu.

    Les faces plates (|ny| > 0,9) donnent, par hauteur, une surface ; la hauteur la plus
    etendue est le sol de l'arene (dix metres dans le jeu). On y garde la nappe connexe
    la plus grande -- le sol, pas un toit de gradin a la meme hauteur.
    """
    by_level = Counter(y for (x, y, z), flat in kept.items() if flat > 0.9)
    level = by_level.most_common(1)[0][0]
    flat_cells = {(x, z) for (x, y, z), flat in kept.items() if flat > 0.9 and abs(y - level) <= 1}
    best = set()
    seen = set()
    for start in flat_cells:
        if start in seen:
            continue
        part = set()
        queue = deque([start])
        seen.add(start)
        while queue:
            cx, cz = queue.popleft()
            part.add((cx, cz))
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nxt = (cx + dx, cz + dz)
                if nxt in flat_cells and nxt not in seen:
                    seen.add(nxt)
                    queue.append(nxt)
        if len(part) > len(best):
            best = part
    return level, best


def classify_all(kept, visual):
    """Sol, mur ou toit (lecon 5 du port), sur la collision ET le decor.

    LE DECOR FAIT L'ARENE. La collision seule ne donnait qu'un sol et des gradins en
    etageres flottantes : la couronne de rochers qui ferme l'arene de Spargus -- des
    mesas empilees, de trente a cent quarante metres -- n'a pas de collision, puisque les
    murs invisibles empechent Jak d'y monter. On pose donc aussi le decor visuel.

    Un plat de la COLLISION est un sol (on y marche : l'arene, les gradins) ; un plat du
    decor seul est un toit (le dessus des rochers, qu'on ne foule pas) ; le reste, mur.
    """
    blocks = {}
    for (x, y, z), flat in visual.items():
        blocks[(x, y, z)] = ROOF if flat >= 0.5 else WALL
    for (x, y, z), flat in kept.items():
        blocks[(x, y, z)] = FLOOR if flat >= 0.5 else WALL
    return blocks


def floor_heights(kept, floor, level):
    """La hauteur du sol de chaque colonne de l'arene (le plat a un bloc du niveau)."""
    heights = {}
    for (x, y, z), flat in kept.items():
        if (x, z) in floor and flat > 0.9 and abs(y - level) <= 1:
            heights[(x, z)] = max(heights.get((x, z), y), y)
    return heights


def found(blocks, heights):
    """L'assise : tout est plein sous le sol de l'arene, jusqu'au bas de la grille."""
    for (x, z), top in heights.items():
        for y in range(0, top):
            blocks.setdefault((x, y, z), WALL)


def centre_of(floor):
    """Le centre du sol, et son rayon : celui du disque de meme surface."""
    cx = sum(x for x, z in floor) / len(floor) + 0.5
    cz = sum(z for x, z in floor) / len(floor) + 0.5
    return (cx, cz), math.sqrt(len(floor) / math.pi)


def clear_disc(floor, x0, z0, r):
    """Toutes les colonnes a moins de r de ce point sont du sol de l'arene (ni rocher, ni gradin)."""
    ri = int(math.ceil(r))
    for dx in range(-ri, ri + 1):
        for dz in range(-ri, ri + 1):
            if dx * dx + dz * dz <= r * r and (x0 + dx, z0 + dz) not in floor:
                return False
    return True


def lay_lava(floor, centre, radius):
    """Les rigoles et les fosses (choix du joueur) : colonnes de lave, et profondeur de chacune.

    L'ANNEAU court a 46 % du rayon, un bloc et demi de large, coupe de quatre passages de six
    blocs aux quatre points cardinaux -- la porte est au sud, on marche droit jusqu'au boss.
    LES FOSSES, huit, rondes, a 74 % du rayon entre les passages ; une fosse qui toucherait un
    rocher ou un gradin est laissee. Le centre, dans le tiers du rayon, reste libre.
    """
    cx, cz = centre
    lava = {}
    ring = 0.46 * radius
    for (x, z) in floor:
        dx, dz = x + 0.5 - cx, z + 0.5 - cz
        r = math.hypot(dx, dz)
        if abs(r - ring) > 0.8:
            continue
        angle = math.atan2(dz, dx)
        gap = min(abs(math.remainder(angle - k * math.pi / 2, 2 * math.pi)) for k in range(4)) * r
        # une rigole ne longe pas un rocher : sous lui, rien ne la retiendrait
        if gap > 3.0 and clear_disc(floor, x, z, 1.5):
            lava[(x, z)] = 1
    for k in range(8):
        angle = (k + 0.5) * math.pi / 4
        px = int(round(cx + math.cos(angle) * 0.74 * radius - 0.5))
        pz = int(round(cz + math.sin(angle) * 0.74 * radius - 0.5))
        if not clear_disc(floor, px, pz, 4.5):
            continue
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if dx * dx + dz * dz <= 7:
                    lava[(px + dx, pz + dz)] = 2
    return lava


def dig(blocks, heights, lava):
    """La tranchee : le sol retire, la lave dessous, sur la profondeur voulue.

    UNE NAPPE A UNE SEULE HAUTEUR PAR RIGOLE OU PAR FOSSE. Le sol de l'arene ondule d'un
    bloc : une lave posee sous chaque colonne a sa propre hauteur se retrouvait, d'une
    colonne a l'autre, a cote de l'air de la tranchee voisine -- elle aurait coule au ras du
    sol (le banc l'a vu). La lave d'un meme morceau est donc posee sous la plus basse de ses
    colonnes, et la tranchee creusee jusqu'a elle.
    """
    seen = set()
    for start in lava:
        if start in seen:
            continue
        part = []
        queue = deque([start])
        seen.add(start)
        while queue:
            cx, cz = queue.popleft()
            part.append((cx, cz))
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nxt = (cx + dx, cz + dz)
                if nxt in lava and nxt not in seen:
                    seen.add(nxt)
                    queue.append(nxt)
        surface = min(heights[c] for c in part) - 1
        for (x, z) in part:
            for y in range(surface + 1, heights[(x, z)] + 1):
                blocks.pop((x, y, z), None)
            for d in range(lava[(x, z)]):
                blocks[(x, surface - d, z)] = LAVA


def spots(blocks, floor, heights, lava, centre, radius):
    """Le boss au centre, les gardes en couronne entre l'anneau et les fosses : sur un sol
    degage (deux cellules d'air au-dessus, rien du decor), jamais dans la lave."""
    def clear(x, z):
        top = heights[(x, z)]
        return (blocks.get((x, top, z)) == FLOOR and (x, top + 1, z) not in blocks
                and (x, top + 2, z) not in blocks)

    cx, cz = centre
    boss = None
    best = None
    for (x, z) in floor:
        d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
        if (best is None or d < best) and clear_disc(floor, x, z, 3.0) and clear(x, z):
            best, boss = d, (x, z)
    guards = []
    for k in range(16):
        angle = (k + 0.25) * math.pi / 8
        gx = int(round(cx + math.cos(angle) * 0.60 * radius - 0.5))
        gz = int(round(cz + math.sin(angle) * 0.60 * radius - 0.5))
        if (gx, gz) in floor and (gx, gz) not in lava and clear(gx, gz):
            guards.append([gx, heights[(gx, gz)] + 1, gz])
    return [boss[0], heights[boss] + 1, boss[1]], guards


def trim(blocks):
    """La boite au plus juste, une cellule de marge : le decalage, et les dimensions."""
    lo = [min(k[i] for k in blocks) for i in range(3)]
    hi = [max(k[i] for k in blocks) for i in range(3)]
    shift = (lo[0] - 1, 0, lo[2] - 1)
    dims = (hi[0] - shift[0] + 2, hi[1] + 3, hi[2] - shift[2] + 2)
    return shift, dims


def encode_runs(blocks, shift, dims):
    """Les plages du format JAKV : en y, puis z, puis x."""
    w, h, d = dims
    sx, sy, sz = shift
    runs = []
    current, count = None, 0
    for y in range(h):
        for z in range(d):
            for x in range(w):
                block = blocks.get((x + sx, y + sy, z + sz), AIR)
                if block == current:
                    count += 1
                else:
                    if current is not None:
                        runs.append((current, count))
                    current, count = block, 1
    runs.append((current, count))
    return runs


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cell", type=float, default=2.0)
    parser.add_argument("--out-dir", default=jv.OUT_DIR)
    parser.add_argument("--reach", type=int, default=1,
                        help="distance au decor visuel en deca de laquelle la collision est gardee")
    args = parser.parse_args()

    verts, faces = load_collision()
    dims, origin = jv.grid(verts, args.cell)
    print("grille %s cellules, origine %s, cellule %.2f m" % (dims, origin, args.cell))

    collision = {}
    jv.rasterize(collision, verts, faces, args.cell, dims, origin)
    vverts, vfaces = mesh_triangles(VISUAL)
    visual = {}
    jv.rasterize(visual, vverts, vfaces, args.cell, dims, origin)
    print("collision %d voxels, decor %d voxels" % (len(collision), len(visual)))

    kept, removed = near_visual(collision, visual, args.reach)
    print("gardes %d, retires (murs invisibles) %d" % (len(kept), len(removed)))

    level, floor = arena_floor(kept, dims)
    heights = floor_heights(kept, floor, level)
    centre, radius = centre_of(floor)
    print("sol de l'arene a y=%d (%.1f m), %d colonnes, rayon %.1f, centre %.1f %.1f"
          % (level, origin[1] + level * args.cell, len(floor), radius, centre[0], centre[1]))

    blocks = classify_all(kept, visual)
    found(blocks, heights)
    lava = lay_lava(floor, centre, radius)
    dig(blocks, heights, lava)
    boss, guards = spots(blocks, floor, heights, lava, centre, radius)
    counts = Counter(blocks.values())
    print("blocs : sol %d, murs %d, toits %d, lave %d ; lave sur %d colonnes ; boss %s ; %d gardes"
          % (counts[FLOOR], counts[WALL], counts[ROOF], counts[LAVA], len(lava), boss, len(guards)))

    shift, vdims = trim(blocks)
    runs = encode_runs(blocks, shift, vdims)
    jv.PALETTE = PALETTE
    os.makedirs(args.out_dir, exist_ok=True)
    path = os.path.join(args.out_dir, NAME + ".jakv")
    vorigin = (origin[0] + shift[0] * args.cell, origin[1], origin[2] + shift[2] * args.cell)
    size, digest = jv.write_blob(path, vdims, runs, vorigin, args.cell)
    marks = {
        "volume": NAME,
        "cellule": args.cell,
        "dimensions": list(vdims),
        "sol": level,
        "rayon": round(radius, 1),
        "centre": [boss[0] - shift[0], boss[1], boss[2] - shift[2]],
        "gardes": [[g[0] - shift[0], g[1], g[2] - shift[2]] for g in guards],
        "sha1": digest,
    }
    with open(os.path.join(args.out_dir, NAME + ".json"), "w", encoding="utf-8") as handle:
        json.dump(marks, handle, indent=1)
    print("volume %s : %s cellules, %d plages, %d octets" % (path, vdims, len(runs), size))

    preview(dims, kept, removed, floor, level, args.cell, lava=frozenset(lava))


def preview(dims, kept, removed, floor, level, cell, lava=frozenset(), tag=""):
    """Vue du dessus et de face : le garde en gris, le retire en rouge, le sol en ocre."""
    os.makedirs(PREVIEW, exist_ok=True)
    scale = 4
    w, h, d = dims
    top = Image.new("RGB", (w * scale, d * scale), (20, 22, 28))
    draw = ImageDraw.Draw(top)
    tops = {}
    for (x, y, z) in kept:
        if y > tops.get((x, z), (-1,))[0]:
            tops[(x, z)] = (y, "kept")
    for (x, y, z) in removed:
        if y > tops.get((x, z), (-1,))[0]:
            tops[(x, z)] = (y, "removed")
    for (x, z), (y, kind) in tops.items():
        shade = int(70 + 150 * y / max(1, h))
        if kind == "removed":
            color = (200, 40, 40)
        elif (x, z) in lava:
            color = (255, 110, 20)
        elif (x, z) in floor and abs(y - level) <= 1:
            color = (190, 120, 70)
        else:
            color = (shade, shade, shade)
        draw.rectangle([x * scale, z * scale, x * scale + scale - 1, z * scale + scale - 1], fill=color)
    out = os.path.join(PREVIEW, "%s%s-dessus.png" % (NAME, tag))
    top.save(out)
    side = Image.new("RGB", (w * scale, h * scale), (20, 22, 28))
    draw = ImageDraw.Draw(side)
    front = {}
    for (x, y, z) in kept:
        if z > front.get((x, y), (-1,))[0]:
            front[(x, y)] = (z, "kept")
    for (x, y, z) in removed:
        if z > front.get((x, y), (-1,))[0]:
            front[(x, y)] = (z, "removed")
    for (x, y), (z, kind) in front.items():
        shade = int(60 + 160 * z / max(1, d))
        color = (200, 40, 40) if kind == "removed" else (shade, shade, shade)
        draw.rectangle([x * scale, (h - 1 - y) * scale, x * scale + scale - 1, (h - 1 - y) * scale + scale - 1],
                       fill=color)
    out2 = os.path.join(PREVIEW, "%s%s-face-sud.png" % (NAME, tag))
    side.save(out2)
    print("apercus : %s, %s" % (out, out2))


if __name__ == "__main__":
    main()
