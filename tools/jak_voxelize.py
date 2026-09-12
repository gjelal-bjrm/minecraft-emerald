"""Transforme la collision d'un niveau de Jak 3 en volume de blocs.

Il lit la geometrie de COLLISION -- la forme solide du niveau, celle sur
laquelle on marche -- et la rasterise en voxels. Il ne copie AUCUNE texture :
la forme est reprise, l'habillage est choisi.

TROIS LECONS D'UN PREMIER ESSAI, dans l'ordre ou elles se sont vues en jeu.

1. LA SURFACE DOIT ETRE ETANCHE. La premiere version echantillonnait chaque
   triangle sur un treillis barycentrique. Un treillis ne s'aligne pas sur la
   grille : des cellules traversees par la surface n'etaient jamais touchees,
   et sols comme murs se sont retrouves cribles de trous par lesquels on voyait
   le ciel. On rasterise maintenant par PROJECTION SUR L'AXE DOMINANT de la
   normale -- la methode habituelle -- qui garantit au moins un voxel par
   cellule traversee.

2. UN QUARTIER N'EST PAS UN SEUL FICHIER. Le bar du Hip Hog et le stand de tir
   sont des niveaux SEPARES, charges en streaming par le jeu, mais poses aux
   memes coordonnees du monde : l'interieur du bar occupe x -113..-62, soit en
   plein dans l'emprise du port. Ne convertir que le port laissait donc leurs
   portes ouvertes sur le vide. On fusionne plusieurs DGO dans un meme repere.

3. UNE VILLE N'EST PAS MONOCHROME. Classer par la seule normale donnait de
   vastes aplats d'un seul bloc -- « on dirait un rocher uni au lieu d'une
   ville ». La collision ne porte aucune matiere (ni `usemtl` ni groupes : que
   des `v` et des `f`), donc la variete ne peut venir que de regles. On tire
   par PLAQUES de quelques blocs plutot qu'au pixel, ce qui imite des panneaux
   et des reprises de maconnerie, et on pose des bandeaux a intervalle regulier
   sur les murs -- ce que l'oeil lit comme de l'architecture.

Les blocs sont choisis d'apres les couleurs MOYENNES des textures du niveau --
une mesure, pas une copie. Le port de Haven est gris et gris-olive, entre 0,20
et 0,45 de luminance : il sort donc en ardoise et en tuf.

Usage :
    python tools/jak_voxelize.py CPO --name ctyport --with HHG GGA
"""

import argparse
import glob
import math
import os
import struct
import sys
import zlib
from collections import deque

DECOMP = os.path.join(os.environ.get("USERPROFILE", ""), "Documents", "OpenGoal",
                      "active", "jak3", "data", "decompiler_out", "jak3")
COLLISION = os.path.join(DECOMP, "collision")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "data",
                       "emeraldweapons", "jak")

# ---------------------------------------------------------------- la palette
#
# Mesuree sur les quatre-vingt-cinq textures de terrain du port :
#
#   city-port-pavmnt-01      #515151  ->  ardoise
#   city-port-wall-metal-01  #555653  ->  briques d'ardoise
#   city-port-seawalll       #464D48  ->  tuiles d'ardoise
#   city-port-roofmetal      #545A4F  ->  cuivre oxyde, tuf (l'olive du metal)
#   city-port-ground-01      #737160  ->  tuf
#   hip-twood01              #5A4929  ->  planches d'epicea (le bois du bar)
#
# Chaque classe a PLUSIEURS blocs : c'est ce qui empeche le quartier de
# ressembler a un rocher uni. L'index 0 est toujours l'air, dont le decodeur se
# sert pour sauter les plages vides d'un coup.
PALETTE = [
    "minecraft:air",                       # 0
    "minecraft:cobbled_deepslate",         # 1   le pavement
    "minecraft:gray_concrete",             # 2
    "minecraft:andesite",                  # 3
    "supplementaries:stone_tile",          # 4
    "minecraft:spruce_planks",             # 5   les quais
    "minecraft:dark_oak_planks",           # 6
    "minecraft:stripped_spruce_wood",      # 7
    "minecraft:tuff",                      # 8   le sol nu, les talus
    "minecraft:polished_andesite",         # 9
    "minecraft:packed_mud",                # 10
    "minecraft:deepslate_bricks",          # 11  les murs de metal
    "minecraft:cracked_deepslate_bricks",  # 12
    "minecraft:deepslate_tiles",           # 13
    "supplementaries:blackstone_tile",     # 14
    "minecraft:polished_deepslate",        # 15  les bandeaux
    "minecraft:chiseled_deepslate",        # 16
    "minecraft:mossy_stone_bricks",        # 17  les soubassements, la digue
    "minecraft:mossy_cobblestone",         # 18
    "minecraft:stone_bricks",              # 19
    "minecraft:tuff_bricks",               # 20  les toits, les passerelles
    "minecraft:oxidized_cut_copper",       # 21
    "minecraft:weathered_cut_copper",      # 22
    "minecraft:polished_tuff",             # 23
    "minecraft:water",                     # 24  le bassin
]

AIR = 0
PAVE = (1, 2, 3, 4)
DOCK = (5, 6, 7)
GROUND = (8, 9, 10)
WALL = (11, 12, 13, 14)
BAND = (15, 16)
FOOT = (17, 18, 19)
ROOF = (20, 21, 22, 23)
WATER = 24

NEIGHBOURS = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


def find_obj(code):
    hits = glob.glob(os.path.join(COLLISION, "collide-%s.DGO-*-collide.obj" % code))
    if not hits:
        sys.exit("aucune collision pour %s dans %s" % (code, COLLISION))
    return hits[0]


def read_triangles(path):
    verts = []
    faces = []
    with open(path, "r") as handle:
        for line in handle:
            if line.startswith("v "):
                p = line.split()
                verts.append((float(p[1]), float(p[2]), float(p[3])))
            elif line.startswith("f "):
                idx = []
                for token in line.split()[1:]:
                    raw = int(token.split("/")[0])
                    idx.append(raw - 1 if raw > 0 else len(verts) + raw)
                for k in range(1, len(idx) - 1):
                    faces.append((idx[0], idx[k], idx[k + 1]))
    return verts, faces


def load_all(codes):
    """Plusieurs niveaux dans UN repere.

    Les coordonnees de la collision sont absolues dans le monde du jeu : deux
    niveaux voisins se placent l'un par rapport a l'autre tout seuls, et
    l'interieur du bar retombe exactement derriere sa porte.
    """
    verts = []
    faces = []
    for code in codes:
        base = len(verts)
        v, f = read_triangles(find_obj(code))
        verts.extend(v)
        faces.extend((a + base, b + base, c + base) for a, b, c in f)
        print("  %-6s %6d triangles" % (code, len(f)))
    return verts, faces


def normal_of(a, b, c):
    ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    vx, vy, vz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
    nx = uy * vz - uz * vy
    ny = uz * vx - ux * vz
    nz = ux * vy - uy * vx
    length = math.sqrt(nx * nx + ny * ny + nz * nz)
    if length < 1e-9:
        return 0.0, 1.0, 0.0
    return nx / length, ny / length, nz / length


def voxelize(verts, faces, cell):
    """La peau du niveau, rasterisee sans trou.

    Pour chaque triangle on projette sur le plan perpendiculaire a l'axe
    DOMINANT de sa normale, puis on parcourt les cellules de ce plan en
    calculant la troisieme coordonnee. Projeter selon l'axe dominant garantit
    que le triangle couvre au moins une cellule par pas de grille : c'est ce
    qui rend la surface etanche, la ou un treillis laissait passer le jour.

    On garde la peau seulement : les batiments restent creux, donc visitables.
    """
    minx = min(v[0] for v in verts)
    miny = min(v[1] for v in verts)
    minz = min(v[2] for v in verts)
    maxx = max(v[0] for v in verts)
    maxy = max(v[1] for v in verts)
    maxz = max(v[2] for v in verts)

    w = int((maxx - minx) / cell) + 2
    hgt = int((maxy - miny) / cell) + 2
    d = int((maxz - minz) / cell) + 2

    voxels = {}
    for tri in faces:
        a, b, c = (verts[i] for i in tri)
        nx, ny, nz = normal_of(a, b, c)
        flat = abs(ny)

        pa = ((a[0] - minx) / cell, (a[1] - miny) / cell, (a[2] - minz) / cell)
        pb = ((b[0] - minx) / cell, (b[1] - miny) / cell, (b[2] - minz) / cell)
        pc = ((c[0] - minx) / cell, (c[1] - miny) / cell, (c[2] - minz) / cell)

        if abs(ny) >= abs(nx) and abs(ny) >= abs(nz):
            drop, u1, u2 = 1, 0, 2
        elif abs(nx) >= abs(nz):
            drop, u1, u2 = 0, 1, 2
        else:
            drop, u1, u2 = 2, 0, 1

        au, av, aw = pa[u1], pa[u2], pa[drop]
        bu, bv, bw = pb[u1], pb[u2], pb[drop]
        cu, cv, cw = pc[u1], pc[u2], pc[drop]

        area = (bu - au) * (cv - av) - (cu - au) * (bv - av)
        if abs(area) < 1e-9:
            continue

        lo_u, hi_u = int(min(au, bu, cu)), int(max(au, bu, cu)) + 1
        lo_v, hi_v = int(min(av, bv, cv)), int(max(av, bv, cv)) + 1
        for iu in range(lo_u, hi_u + 1):
            for iv in range(lo_v, hi_v + 1):
                su, sv = iu + 0.5, iv + 0.5
                s = ((su - au) * (cv - av) - (cu - au) * (sv - av)) / area
                t = ((bu - au) * (sv - av) - (su - au) * (bv - av)) / area
                # la marge elargit d'un vingtieme : deux triangles voisins se
                # recouvrent alors legerement au lieu de laisser une couture
                if s < -0.05 or t < -0.05 or s + t > 1.05:
                    continue
                sw = aw + s * (bw - aw) + t * (cw - aw)
                iw = int(sw)
                if drop == 1:
                    key = (iu, iw, iv)
                elif drop == 0:
                    key = (iw, iu, iv)
                else:
                    key = (iu, iv, iw)
                if not (0 <= key[0] < w and 0 <= key[1] < hgt and 0 <= key[2] < d):
                    continue
                old = voxels.get(key)
                # la face la plus PLATE gagne : un sol traverse par un mur doit
                # rester un sol, faute de quoi les dalles se piquent de briques
                # a chaque intersection
                if old is None or flat > old:
                    voxels[key] = flat

    return voxels, (w, hgt, d), (minx, miny, minz)


def pinholes(voxels, dims):
    """Bouche les trous d'un seul bloc que la rasterisation laisse aux aretes.

    La ou deux faces se rencontrent de biais, chacune peut manquer la meme
    cellule de son cote. Une cellule vide entouree sur quatre de ses six faces
    etait forcement de la matiere : on la rend.
    """
    w, h, d = dims
    added = {}
    for (x, y, z) in list(voxels):
        for dx, dy, dz in NEIGHBOURS:
            key = (x + dx, y + dy, z + dz)
            if key in voxels or key in added:
                continue
            if not (0 <= key[0] < w and 0 <= key[1] < h and 0 <= key[2] < d):
                continue
            count = 0
            best = 0.0
            for ex, ey, ez in NEIGHBOURS:
                other = voxels.get((key[0] + ex, key[1] + ey, key[2] + ez))
                if other is not None:
                    count += 1
                    if other > best:
                        best = other
            if count >= 4:
                added[key] = best
    voxels.update(added)
    return len(added)


def water_mask(voxels, dims, water_y):
    """La nappe : tout ce qui communique avec le large, a la hauteur de l'eau.

    Une propagation depuis le BORD de la carte. Sans elle, l'eau apparaitrait
    aussi dans les caves, les cours fermees et les batiments -- partout ou il
    se trouve du vide sous ce niveau, c'est-a-dire a peu pres partout.
    """
    w, h, d = dims
    if not 0 <= water_y < h:
        return set()
    seen = set()
    queue = deque()

    def offer(x, z):
        if 0 <= x < w and 0 <= z < d and (x, z) not in seen \
                and (x, water_y, z) not in voxels:
            seen.add((x, z))
            queue.append((x, z))

    for x in range(w):
        offer(x, 0)
        offer(x, d - 1)
    for z in range(d):
        offer(0, z)
        offer(w - 1, z)
    while queue:
        x, z = queue.popleft()
        offer(x + 1, z)
        offer(x - 1, z)
        offer(x, z + 1)
        offer(x, z - 1)
    return seen


def mix(x, y, z, scale):
    """Un entier stable tire de la position, par plaques.

    Par PLAQUES et non par bloc : un tirage au pixel donne du poivre et sel,
    qu'aucun batiment n'a jamais eu. Des plaques de quelques blocs se lisent
    comme des panneaux et des reprises de maconnerie.
    """
    a = ((x // scale) * 73856093) ^ ((y // max(2, scale // 2)) * 19349663) \
        ^ ((z // scale) * 83492791)
    a &= 0xFFFFFFFF
    a ^= a >> 13
    a = (a * 1274126177) & 0xFFFFFFFF
    return (a ^ (a >> 16)) & 0x7FFFFFFF


def classify(flat, x, y, z, quay_y, top_y):
    """Le bloc d'un voxel : sa classe d'abord, sa variante ensuite."""
    horizontal = flat > 0.80
    vertical = flat < 0.35

    if y <= quay_y - 6:
        return FOOT[mix(x, y, z, 5) % len(FOOT)]
    if horizontal and y <= quay_y + 2:
        return DOCK[mix(x, y, z, 4) % len(DOCK)]
    if horizontal:
        if y > quay_y + (top_y - quay_y) * 0.45:
            return ROOF[mix(x, y, z, 6) % len(ROOF)]
        return PAVE[mix(x, y, z, 7) % len(PAVE)]
    if vertical:
        # le bandeau : une assise differente toutes les cinq. C'est le detail le
        # moins cher et le plus efficace pour qu'un mur cesse d'etre une falaise
        if y % 5 == 0:
            return BAND[mix(x, y, z, 9) % len(BAND)]
        return WALL[mix(x, y, z, 6) % len(WALL)]
    return GROUND[mix(x, y, z, 6) % len(GROUND)]


def encode(voxels, dims, quay_y, top_y, water_y, mask, depth):
    """Les plages, en parcourant y puis z puis x.

    L'eau n'est qu'une NAPPE de quelques blocs, pas une colonne jusqu'au fond.
    Le niveau fait mille deux cents blocs sur sept cents, et quatre-vingt-quatorze
    pour cent de sa surface est ouverte sur le large a la hauteur de l'eau :
    remplir jusqu'en bas demanderait cinquante millions de blocs, deux minutes
    de pose et autant de donnees de monde. Une nappe donne la meme image depuis
    la surface pour un vingtieme du prix ; on ne decouvre le vide qu'en
    plongeant, ce qui est un defaut que j'assume plutot qu'un quartier
    impraticable.
    """
    w, h, d = dims
    runs = []
    current = None
    count = 0
    for y in range(h):
        flooded = water_y - depth < y <= water_y
        for z in range(d):
            for x in range(w):
                flat = voxels.get((x, y, z))
                if flat is None:
                    block = WATER if flooded and (x, z) in mask else AIR
                else:
                    block = classify(flat, x, y, z, quay_y, top_y)
                if block == current:
                    count += 1
                else:
                    if current is not None:
                        runs.append((current, count))
                    current = block
                    count = 1
    if current is not None:
        runs.append((current, count))
    return runs


def write_blob(path, dims, runs):
    out = bytearray()
    out += b"JAKV"
    out += struct.pack(">B", 1)
    out += struct.pack(">III", *dims)
    out += struct.pack(">H", len(PALETTE))
    for name in PALETTE:
        raw = name.encode("utf-8")
        out += struct.pack(">H", len(raw)) + raw
    out += struct.pack(">I", len(runs))
    for block, count in runs:
        out += struct.pack(">B", block)
        while True:
            part = count & 0x7F
            count >>= 7
            out += struct.pack(">B", part | (0x80 if count else 0))
            if not count:
                break
    body = zlib.compress(bytes(out), 9)
    with open(path, "wb") as handle:
        handle.write(body)
    return len(body), len(out)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("code", help="code de DGO principal, par exemple CPO")
    parser.add_argument("--name", required=True, help="nom de sortie")
    parser.add_argument("--with", dest="extra", nargs="*", default=[],
                        help="DGO a fusionner dans le meme repere")
    parser.add_argument("--cell", type=float, default=1.0)
    parser.add_argument("--water", type=float, default=6.0,
                        help="altitude de la surface de l'eau, en unites du jeu")
    parser.add_argument("--water-depth", dest="depth", type=int, default=6,
                        help="epaisseur de la nappe, en blocs")
    args = parser.parse_args()

    codes = [args.code] + list(args.extra)
    verts, faces = load_all(codes)
    print("  total  %d triangles" % len(faces))

    voxels, dims, origin = voxelize(verts, faces, args.cell)
    print("  grille    %d x %d x %d" % dims)
    print("  surfaces  %d voxels" % len(voxels))
    filled = pinholes(voxels, dims)
    print("  bouches   %d trous d'un bloc" % filled)

    # les reperes d'altitude, ramenes en cellules. Le niveau des quais est
    # MESURE : c'est l'altitude ou la surface horizontale est de loin la plus
    # etendue du niveau (133 000 unites carrees a y=8, contre 60 000 au
    # suivant), donc le sol sur lequel la ville est posee.
    quay_y = int((8.0 - origin[1]) / args.cell)
    water_y = int((args.water - origin[1]) / args.cell)
    top_y = dims[1] - 1
    print("  quais y=%d, eau y=%d (en cellules)" % (quay_y, water_y))

    mask = water_mask(voxels, dims, water_y)
    print("  nappe     %d cellules ouvertes sur le large" % len(mask))

    runs = encode(voxels, dims, quay_y, top_y, water_y, mask, args.depth)
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, "%s.jakv" % args.name)
    packed, _ = write_blob(path, dims, runs)
    print("  plages    %d" % len(runs))
    print("  fichier   %s  (%.1f Mo compresses)" % (path, packed / 1048576.0))


if __name__ == "__main__":
    main()
