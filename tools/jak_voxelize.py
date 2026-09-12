"""Transforme la collision d'un niveau de Jak 3 en volume de blocs.

Ce que fait ce script, et ce qu'il ne fait pas.

Il lit la geometrie de COLLISION -- la forme solide du niveau, celle sur
laquelle on marche -- et la rasterise en voxels. Il ne copie AUCUNE texture :
la forme est reprise, l'habillage est choisi. Les biseaux et les courbes d'un
maillage de PS2 ne survivent de toute facon pas a une grille de cubes.

Les blocs, eux, sont choisis d'apres les couleurs MOYENNES des textures du
niveau -- une mesure, pas une copie. Le port de Haven est gris et gris-olive,
entre 0,20 et 0,45 de luminance : il sort donc en ardoise et en tuf, pas dans
nos materiaux d'Arcencium, qui lui donneraient une allure qui n'est pas la
sienne.

Le classement suit la NORMALE de la surface, et l'altitude :

    tout en bas              un soubassement, la digue -> tuiles d'ardoise
    a plat, au ras de l'eau  un quai                   -> planches d'epicea
    a plat, a mi-hauteur     le pavement               -> pierre noire taillee
    a plat, tres haut        un toit, une passerelle   -> briques de tuf
    penche                   un talus, une rampe       -> tuf
    vertical                 un mur de metal           -> briques d'ardoise

La sortie n'est PAS un fichier de structure du jeu. Une structure plafonne a
quarante-huit blocs de cote : le port en demanderait un millier de fichiers.
On ecrit donc un format a nous, compresse par plages, que le mod lit et pose
progressivement -- ce qui permet aussi d'etaler la pose sur plusieurs ticks au
lieu de figer le serveur.

Usage :
    python tools/jak_voxelize.py CPO --name ctyport
    python tools/jak_voxelize.py CPO --name ctyport --cell 2   # deux fois plus petit
"""

import argparse
import glob
import math
import os
import struct
import sys
import zlib

DECOMP = os.path.join(os.environ.get("USERPROFILE", ""), "Documents", "OpenGoal",
                      "active", "jak3", "data", "decompiler_out", "jak3")
COLLISION = os.path.join(DECOMP, "collision")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "data",
                       "emeraldweapons", "jak")

# La palette, calee sur les VRAIES couleurs du niveau.
#
# Les quatre-vingt-cinq textures de terrain du port ont ete moyennees (voir le
# journal de la session) : luminance 0,20 a 0,45, saturation presque toujours
# sous 0,20. Du beton et du metal industriels, gris et gris-olive. Rien qui
# ressemble a la pierre claire de Minecraft, et rien qui ressemble non plus a
# nos materiaux d'Arcencium -- d'ou le choix de rester en vanilla ici : le
# quartier doit garder SON allure, pas prendre la notre.
#
# Les correspondances, texture par texture :
#
#   city-port-pavmnt-01      #515151  ->  pierre noire taillee
#   city-port-wall-metal-01  #555653  ->  briques d'ardoise
#   city-port-seawalll       #464D48  ->  tuiles d'ardoise
#   city-port-roofmetal      #545A4F  ->  briques de tuf (l'olive du metal)
#   city-port-ground-01      #737160  ->  tuf
#   hip-twood01              #5A4929  ->  planches d'epicea (le bois du bar)
#
# L'index 0 est TOUJOURS l'air : le decodeur s'en sert pour sauter les plages
# vides sans les parcourir, et elles sont l'ecrasante majorite.
PALETTE = [
    "minecraft:air",
    "minecraft:cobbled_deepslate",   # 1  les dalles, le pavement
    "minecraft:spruce_planks",       # 2  les quais
    "minecraft:tuff",                # 3  les talus, le sol nu
    "minecraft:deepslate_bricks",    # 4  les murs de metal
    "minecraft:deepslate_tiles",     # 5  les soubassements et la digue
    "minecraft:polished_deepslate",  # 6  les joints et les bordures
    "minecraft:tuff_bricks",         # 7  les toits et les passerelles hautes
]

AIR, SLAB, DOCK, SLOPE, WALL, FOOT, TRIM, HIGH = range(8)


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


def normal_of(a, b, c):
    ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    vx, vy, vz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
    nx = uy * vz - uz * vy
    ny = uz * vx - ux * vz
    nz = ux * vy - uy * vx
    length = math.sqrt(nx * nx + ny * ny + nz * nz)
    if length < 1e-9:
        return 0.0, 0.0, 0.0
    return nx / length, ny / length, nz / length


def classify(ny, y, floor_y, water_y, top_y):
    """Le bloc d'un voxel, decide par l'inclinaison de sa face et son altitude."""
    flat = abs(ny) > 0.80
    steep = abs(ny) < 0.35
    if y <= floor_y + 2:
        return FOOT
    if flat and y <= water_y + 3:
        return DOCK
    if flat:
        # les tres hautes plateformes se distinguent : ce sont les toits et les
        # passerelles, et les laisser dans la meme pierre que le sol aplatit
        # completement la lecture du relief
        return HIGH if y > floor_y + (top_y - floor_y) * 0.62 else SLAB
    if steep:
        return WALL
    return SLOPE


def voxelize(verts, faces, cell):
    """Une grille creuse : seules les surfaces existent, pas les volumes pleins.

    Remplir l'interieur des batiments n'apporterait rien de visible et
    multiplierait le poids par dix. On pose la peau, ce qui donne des batiments
    creux -- exactement ce qu'on veut pouvoir visiter.
    """
    minx = min(v[0] for v in verts)
    miny = min(v[1] for v in verts)
    minz = min(v[2] for v in verts)
    maxx = max(v[0] for v in verts)
    maxy = max(v[1] for v in verts)
    maxz = max(v[2] for v in verts)

    w = int((maxx - minx) / cell) + 1
    hgt = int((maxy - miny) / cell) + 1
    d = int((maxz - minz) / cell) + 1

    voxels = {}
    floor_y = 0
    top_y = hgt - 1
    water_y = int(hgt * 0.10)          # provisoire, affine plus bas

    for tri in faces:
        a, b, c = (verts[i] for i in tri)
        _, ny, _ = normal_of(a, b, c)
        # on marche le triangle en le subdivisant : plus simple qu'un balayage
        # 3D, et suffisant des lors que le pas est plus fin qu'une cellule
        ab = max(abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2]))
        ac = max(abs(c[0] - a[0]), abs(c[1] - a[1]), abs(c[2] - a[2]))
        steps = int(max(ab, ac) / cell) + 1
        steps = min(steps, 512)        # garde-fou contre un triangle degenere
        for i in range(steps + 1):
            for j in range(steps + 1 - i):
                u = i / float(steps)
                v = j / float(steps)
                x = a[0] + (b[0] - a[0]) * u + (c[0] - a[0]) * v
                y = a[1] + (b[1] - a[1]) * u + (c[1] - a[1]) * v
                z = a[2] + (b[2] - a[2]) * u + (c[2] - a[2]) * v
                gx = int((x - minx) / cell)
                gy = int((y - miny) / cell)
                gz = int((z - minz) / cell)
                if 0 <= gx < w and 0 <= gy < hgt and 0 <= gz < d:
                    key = (gx, gy, gz)
                    # la face la plus PLATE gagne : un sol traverse par un mur
                    # doit rester un sol, faute de quoi les dalles se piquent de
                    # briques a chaque intersection
                    old = voxels.get(key)
                    if old is None or abs(ny) > old:
                        voxels[key] = abs(ny)

    return voxels, (w, hgt, d), (minx, miny, minz), floor_y, water_y, top_y


def encode(voxels, dims, floor_y, water_y, top_y):
    """Les plages : (index de palette, longueur), en parcourant y puis z puis x.

    L'ordre compte pour la compression comme pour la pose : parcourir par
    couches horizontales donne de tres longues plages d'air, et permet au mod
    de batir etage par etage, ce qui se regarde bien mieux qu'un remplissage
    par colonnes.
    """
    w, h, d = dims
    runs = []
    current = None
    count = 0
    for y in range(h):
        for z in range(d):
            for x in range(w):
                ny = voxels.get((x, y, z))
                block = AIR if ny is None else classify(ny, y, floor_y, water_y, top_y)
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
    """Un format a nous, volontairement bete : en-tete, palette, plages.

    Les longueurs sont ecrites en varint : la plupart des plages d'air font des
    milliers de cellules, la plupart des plages pleines en font une ou deux, et
    un entier de taille fixe gacherait l'un des deux cas.
    """
    out = bytearray()
    out += b"JAKV"
    out += struct.pack(">B", 1)                       # version du format
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
    parser.add_argument("code", help="code de DGO, par exemple CPO")
    parser.add_argument("--name", required=True, help="nom de sortie, par exemple ctyport")
    parser.add_argument("--cell", type=float, default=1.0,
                        help="unites de jeu par bloc")
    args = parser.parse_args()

    verts, faces = read_triangles(find_obj(args.code))
    print("%s : %d triangles" % (args.code, len(faces)))

    voxels, dims, origin, floor_y, water_y, top_y = voxelize(verts, faces, args.cell)
    print("  grille    %d x %d x %d" % dims)
    print("  surfaces  %d voxels" % len(voxels))

    runs = encode(voxels, dims, floor_y, water_y, top_y)
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, "%s.jakv" % args.name)
    packed, raw = write_blob(path, dims, runs)
    print("  plages    %d" % len(runs))
    print("  fichier   %s  (%.1f Mo compresses, %.1f bruts)"
          % (path, packed / 1048576.0, raw / 1048576.0))


if __name__ == "__main__":
    main()
