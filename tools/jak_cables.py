#!/usr/bin/env python3
"""
Les cables du port de Haven, cellule par cellule, pour les rendre en chaines (cahier §94).

« Les cables qui relient les tours devraient etre plus fins, il faudrait des barres de fer ou
quelque chose de fin » (le joueur, 24 sept.). Le voxeliseur (jak_voxelize.py, lecon 12) pose
les cables des tours du large, les catenaires et les rails de glisse en LIGNES DE BLOCS PLEINS
de la classe mur : un cable d'un metre d'epaisseur. Choix du joueur : des CHAINES
(minecraft:chain), orientees le long du cable.

ON NE TOUCHE PAS AU VOLUME. Changer ctyport.jakv changerait son sha1, et tout ce qui s'y
accroche -- le releve de l'atelier du joueur, les salles, la faune, l'invasion, la ville deja
posee dans chaque monde -- serait a refaire ou a reposer. Ce script lit le volume TEL QUEL,
retrouve les cellules que lay_cables y a posees, et les ecrit dans

    src/main/resources/data/emeraldweapons/jak/haven_cables.json

que HavenCables (Java) rejoue apres chaque pose de la ville, et une fois sur les villes deja
posees : chaque cellule listee devient une chaine sur son axe. Le releve de l'atelier ignore
ces cellules (JakCityCapture), comme la borne et le bouton.

COMMENT ON LES RETROUVE. Les axes des cables se relisent dans le decor visuel comme au
voxeliseur (cable_axes, meme code), les segments sont tires en cellules de la meme facon
(voxel_line, memes raccords). Une cellule d'un segment est un cable si le volume y a un MUR
et si ce mur ne tient a rien d'autre qu'a la ligne : aucun voisin de face plein qui ne soit
pas lui-meme sur une ligne. La ou le cable entre dans une tour, la derniere cellule touche la
tour : elle reste un bloc plein, l'attache du cable.

L'axe d'une cellule est la composante la plus forte de la direction de son segment ; une chaine
posee sur cet axe suit le cable au plus pres.

Le fichier porte aussi les AXES eux-memes (les polylignes, en cellules) : c'est sur eux que
le JET-Board glissera.

Usage : python tools/jak_cables.py [--volume ctyport] [--out chemin.json]
"""
import argparse
import json
import math
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jak_voxelize as vox  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAIL_JOIN = 2.5
DATA_JAK = os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons", "jak")


def read_volume(path):
    """Le volume : dims, origine, cellule, sha1, palette et la grille aplatie (index ((y * d) + z) * w + x)."""
    with open(path, "rb") as handle:
        data = zlib.decompress(handle.read())
    if data[:4] != b"JAKV" or data[4] != 2:
        sys.exit("%s : pas un volume JAKV v2" % path)
    w, h, d = struct.unpack_from(">III", data, 5)
    ox, oy, oz, cell = struct.unpack_from(">dddd", data, 17)
    sha1 = data[49:69].hex()
    pos = 69
    count, = struct.unpack_from(">H", data, pos)
    pos += 2
    palette = []
    for _ in range(count):
        n, = struct.unpack_from(">H", data, pos)
        pos += 2
        palette.append(data[pos:pos + n].decode("utf-8"))
        pos += n
    runs, = struct.unpack_from(">I", data, pos)
    pos += 4
    grid = bytearray(w * h * d)
    at = 0
    for _ in range(runs):
        block = data[pos]
        pos += 1
        length = 0
        shift = 0
        while True:
            byte = data[pos]
            pos += 1
            length |= (byte & 0x7F) << shift
            shift += 7
            if not byte & 0x80:
                break
        grid[at:at + length] = bytes([block]) * length
        at += length
    if at != w * h * d:
        sys.exit("volume incomplet : %d cellules sur %d" % (at, w * h * d))
    return (w, h, d), (ox, oy, oz), cell, sha1, palette, grid


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--volume", default="ctyport")
    parser.add_argument("--out", default=os.path.join(DATA_JAK, "haven_cables.json"))
    args = parser.parse_args()

    dims, origin, cell, sha1, palette, grid = read_volume(os.path.join(DATA_JAK, args.volume + ".jakv"))
    w, h, d = dims
    wall = palette.index(vox.PALETTE[vox.WALL])
    air = 0

    def index(x, y, z):
        return (y * d + z) * w + x

    def inside(c):
        return 0 <= c[0] < w and 0 <= c[1] < h and 0 <= c[2] < d

    path = os.path.join(vox.LEVELS, args.volume, args.volume + "-background.glb")
    if not os.path.isfile(path):
        sys.exit("decor des cables absent : %s" % path)
    verts, faces, names = mesh_triangles_named(path)
    axes, kinds = cable_axes_kinds(verts, faces, names)
    print("%d axes lus dans le decor (%d cables, %d rails)" % (len(axes), kinds.count("cable"), kinds.count("rail")))

    def to_cell(p):
        return tuple((p[i] - origin[i]) / cell for i in range(3))

    segments = []
    for k, line in enumerate(axes):
        pts = [to_cell(p) for p in line]
        segments.extend((a, b, kinds[k]) for a, b in zip(pts, pts[1:]))
    ends = [(k, to_cell(p)) for k, line in enumerate(axes) for p in (line[0], line[-1])]
    joins = 0
    for i, (ka, pa) in enumerate(ends):
        for kb, pb in ends[i + 1:]:
            if ka != kb and math.dist(pa, pb) <= vox.CABLE_JOIN / cell:
                segments.append((pa, pb, kinds[ka]))
                joins += 1

    # les cellules des lignes, avec l'axe dominant de leur segment
    on_line = {}
    for a, b, kind in segments:
        direction = [b[i] - a[i] for i in range(3)]
        axis = "xyz"[max(range(3), key=lambda i: abs(direction[i]))]
        for key in vox.voxel_line(a, b):
            if inside(key):
                on_line.setdefault(key, (axis, kind))

    # un cable : un mur du volume sur une ligne, qui ne tient a rien d'autre qu'a la ligne
    faces6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    cells = []
    walls_on_line = 0
    anchors = 0
    for key, (axis, kind) in sorted(on_line.items()):
        if grid[index(*key)] != wall:
            continue
        walls_on_line += 1
        attached = False
        for f in faces6:
            n = (key[0] + f[0], key[1] + f[1], key[2] + f[2])
            if inside(n) and grid[index(*n)] != air and n not in on_line:
                attached = True
                break
        if attached:
            anchors += 1
            continue
        cells.append([key[0], key[1], key[2], axis, kind])
    print("%d cellules de ligne, %d murs sur les lignes, %d attaches gardees pleines, %d chaines"
          % (len(on_line), walls_on_line, anchors, len(cells)))
    by_axis = {a: sum(1 for c in cells if c[3] == a) for a in "xyz"}
    print("axes des chaines :", by_axis, "; raccords", joins)

    # les axes, sans ceux qui sortent de la grille (un tube du decor traine loin au nord)
    kept = []
    for k, line in enumerate(axes):
        pts = [to_cell(p) for p in line]
        if all(inside(tuple(int(math.floor(v)) for v in p)) for p in pts):
            kept.append((kinds[k], pts))
    print("%d axes gardes dans la grille" % len(kept))

    # LES CABLES ENTIERS. Un cable du jeu est une suite de tubes droits qui ne partagent aucun
    # sommet : on les recoud bout a bout (les memes raccords que lay_cables), pour qu'une chaine
    # dessinee le long de la ligne soit continue d'un bout a l'autre.
    # Les rails de glisse sont faits de tubes separes de 1,8 a 2,2 m (leurs raccords dans le jeu) :
    # on recoud jusqu'a 2,5 m ; au-dela (6 a 16 m), le rail s'interrompt vraiment, c'est un saut.
    lines = stitch(kept, RAIL_JOIN / cell)
    cable_set = {(c[0], c[1], c[2]) for c in cells}
    for line in lines:
        # le temoin : l'attache dans la tour, a trois cellules ; un rail pend sous une arcade, son
        # temoin peut etre plus loin (huit)
        line["witness"] = [witness(end, grid, index, inside, cable_set, air, 3)
                           or witness(end, grid, index, inside, cable_set, air, 8)
                           for end in (line["points"][0], line["points"][-1])]
    missing = sum(1 for line in lines for w_ in line["witness"] if w_ is None)
    print("%d cables recousus (%d cables, %d rails), %d bouts sans bloc temoin"
          % (len(lines), sum(1 for l in lines if l["kind"] == "cable"),
             sum(1 for l in lines if l["kind"] == "rail"), missing))
    out = {
        "_format": [
            "Les cables du port de Haven (tools/jak_cables.py, cahier §94).",
            "UNITES : cellules du volume ctyport (1 cellule = 1 bloc) ; un bloc du monde = origine de pose + cellule.",
            "cells : x, y, z, axe, sorte (cable des tours ou rail de glisse) : les murs que lay_cables a poses le long",
            "des cables, qui ne tiennent a rien d'autre qu'a leur ligne ; la ville posee les vide (HavenCables).",
            "lines : chaque cable entier, recousu bout a bout, en polyligne de cellules (flottantes) : la chaine s'y",
            "dessine (HavenCableRenderer) et le JET-Board y glissera ; witness : pour chaque bout, une cellule pleine",
            "de la tour ou il s'accroche -- le client ne dessine le cable que si ce bloc est la (la ville est posee).",
        ],
        "volume": args.volume,
        "sha1": sha1,
        "origin": [origin[0], origin[1], origin[2]],
        "cell": cell,
        "dims": [w, h, d],
        "cells": cells,
        "lines": lines,
    }
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(out, handle, separators=(",", ":"))
    print("ecrit", args.out, os.path.getsize(args.out), "octets")


def stitch(parts, join):
    """Recoud les tubes en cables : un bout rejoint le bout le plus proche d'un autre tube, a moins de join."""
    ends = []
    for k, (_, pts) in enumerate(parts):
        ends.append((k, 0, pts[0]))
        ends.append((k, 1, pts[-1]))
    link = {}
    pairs = []
    for i, (ka, ea, pa) in enumerate(ends):
        for kb, eb, pb in ends[i + 1:]:
            if ka != kb:
                d = math.dist(pa, pb)
                if d <= join:
                    pairs.append((d, (ka, ea), (kb, eb)))
    for d, a, b in sorted(pairs):
        if a not in link and b not in link:
            link[a] = b
            link[b] = a
    used = set()
    lines = []

    def walk(start_part, start_end):
        pts = []
        k, e = start_part, start_end
        while k not in used:
            used.add(k)
            seq = parts[k][1] if e == 0 else list(reversed(parts[k][1]))
            for p in seq:
                if not pts or math.dist(pts[-1], p) > 0.05:
                    pts.append(p)
            nxt = link.get((k, 1 - e))
            if nxt is None:
                break
            k, e = nxt
        return pts

    # d'abord depuis les bouts libres, puis ce qui reste (une boucle)
    for k in range(len(parts)):
        for e in (0, 1):
            if k not in used and (k, e) not in link:
                kind = parts[k][0]
                lines.append({"kind": kind, "points": [[round(v, 2) for v in p] for p in walk(k, e)]})
    for k in range(len(parts)):
        if k not in used:
            kind = parts[k][0]
            lines.append({"kind": kind, "points": [[round(v, 2) for v in p] for p in walk(k, 0)]})
    return lines


def witness(point, grid, index, inside, cable_set, air, reach=3):
    """La cellule pleine la plus proche d'un bout de cable, hors du cable lui-meme : l'attache, dans la tour."""
    cx, cy, cz = (int(math.floor(v)) for v in point)
    best = None
    for dx in range(-reach, reach + 1):
        for dy in range(-reach, reach + 1):
            for dz in range(-reach, reach + 1):
                c = (cx + dx, cy + dy, cz + dz)
                if not inside(c) or c in cable_set or grid[index(*c)] in (air, 4, 5, 6, 7):
                    continue
                d = math.dist((c[0] + 0.5, c[1] + 0.5, c[2] + 0.5), point)
                if best is None or d < best[0]:
                    best = (d, c)
    return list(best[1]) if best else None


def mesh_triangles_named(path):
    """Les triangles du decor avec le nom de leur maillage (comme lay_cables les lit)."""
    return vox.mesh_triangles(path, with_names=True)


def cable_axes_kinds(verts, faces, names):
    """Les axes de cable_axes, et pour chacun sa sorte : « cable » (tours, catenaires) ou « rail » (glisse)."""
    axes = vox.cable_axes(verts, faces, names)
    # cable_axes ne rend pas les noms : on retrouve la sorte de chaque axe par le maillage
    # dont ses points sont les plus proches (les deux familles ne se touchent pas)
    cable_pts = []
    rail_pts = []
    for i, n in enumerate(names):
        if "grind" in n:
            rail_pts.extend(verts[k] for k in faces[i])
        elif any(m in n for m in vox.CABLE_MESHES):
            cable_pts.extend(verts[k] for k in faces[i])

    def nearest(p, pts):
        return min(math.dist(p, q) for q in pts) if pts else float("inf")

    kinds = []
    for line in axes:
        p = line[len(line) // 2]
        kinds.append("rail" if nearest(p, rail_pts) < nearest(p, cable_pts) else "cable")
    return axes, kinds


if __name__ == "__main__":
    main()
