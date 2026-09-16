"""Extrait le graphe de trafic civil d'un quartier de Jak 3 depuis son fichier -vis.go.

Le decompilateur d'OpenGOAL n'exporte pas ce graphe (aucune option ne le
fait) : il vit dans le bsp-header du niveau, champ city-level-info a l'offset
208, en binaire GOAL. On le lit ici octet a octet, d'apres les types de
levels/city/common/nav-graph-h.gc et levels/city/traffic/traffic-engine-h.gc :

    nav-node 32 octets : position (3 floats), angle u16, id u16, radius u8,
        branch-count s8, flags u8, branch-array, nav-mesh-id, level ;
    nav-branch 16 : src-node, dest-node, speed-limit u8, density u8, clock-type,
        clock-mask, max-user-count, user-count, width u8, flags ;
    nav-segment 48 : vertex0 (w = longueur), vertex1 (w = espacement d'apparition),
        branch, nav-mesh-id, id, cell-id, from-cell-id, tracker-id ;
    vis-cell 32 : sphere, segment-array, vis-id, id, incoming, count, flags ;
    nav-graph-link 48 : id, dest-graph-id, src-branch-id, dest-node-id, dest-graph,
        dummy-node.

AVANT LE PATCH DU CHARGEMENT (level-link, traffic-engine.gc:466-610), les champs
pointeurs des noeuds, branches, segments et cellules portent des INDEX ; ceux du
nav-graph et du city-level-info portent des pointeurs relatifs au debut des
donnees (l'en-tete du fichier fait 128 octets : offset = pointeur + 128). Un
dest-node au-dela de 100000 designe le lien 0xFFFFFFFF - valeur : la sortie
vers un autre quartier.

Unites : 4096 unites = 1 m ; speed-limit x 1024 u/s (u8 / 4 m/s) ; width x 256 u
(u8 / 16 m) ; radius x 1024 u (u8 / 4 m) ; density x 1/128 ; angle sur 65536.

La carte de hauteur *traffic-height-map* (levels/city/traffic/traffic-height-map.gc)
donne l'altitude de la voie haute : 17,5 m + donnee (int8) m, grille de 24 m
depuis (-776, -776) m, interpolation bilineaire. Le trafic PNJ ne roule que la.

Sorties, dans build/jak/traffic :
    <niveau>_navgraph.json  noeuds, branches, liens, segments, cellules, carte de
                            hauteur, en metres du jeu ET en cellules du volume du port ;
    <niveau>_navgraph.png   les voies posees sur la vue de dessus du volume.

    python tools/jak_navgraph.py ctyport
"""

import json
import os
import re
import struct
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from jak_voxelize import DECOMP, GRID_ORIGIN, ROOT  # noqa: E402
from jak_preview import color_of, read_volume, top_view, visible_runs  # noqa: E402

GOAL_SRC = os.path.join(os.environ.get("USERPROFILE", ""), "Documents", "OpenGoal",
                        "active", "jak3", "data", "goal_src", "jak3")
RAW_OBJ = os.path.join(DECOMP, "raw_obj")
HEIGHT_MAP = os.path.join(GOAL_SRC, "levels", "city", "traffic", "traffic-height-map.gc")
OUT_DIR = os.path.join(ROOT, "build", "jak", "traffic")

UNIT = 4096.0
NODE, BRANCH, SEGMENT, CELL, LINK = 32, 16, 48, 32, 48
CITY_LEVEL_INFO_OFFSET = 208
PEDESTRIAN = 4          # nav-node-flag-byte pedestrian
LINK_MARK = 100000      # dest-node au-dela : un lien vers un autre graphe


class Blob:
    """Le fichier .go : lecture petit-boutiste, et pointeurs relatifs aux donnees."""

    def __init__(self, data):
        self.data = data
        self.start = struct.unpack_from("<I", data, 4)[0]

    def at(self, pointer):
        return pointer + self.start

    def u8(self, o):
        return self.data[o]

    def s8(self, o):
        return struct.unpack_from("<b", self.data, o)[0]

    def u16(self, o):
        return struct.unpack_from("<H", self.data, o)[0]

    def s16(self, o):
        return struct.unpack_from("<h", self.data, o)[0]

    def u32(self, o):
        return struct.unpack_from("<I", self.data, o)[0]

    def f32(self, o):
        return struct.unpack_from("<f", self.data, o)[0]

    def metres(self, o):
        return [self.f32(o) / UNIT, self.f32(o + 4) / UNIT, self.f32(o + 8) / UNIT]


def cells(point):
    """Un point en metres du jeu, en cellules du volume du port."""
    return [round(point[0] - GRID_ORIGIN[0], 2), round(point[1] - GRID_ORIGIN[1], 2),
            round(point[2] - GRID_ORIGIN[2], 2)]


def read_graph(level):
    path = os.path.join(RAW_OBJ, level + "-vis.go")
    if not os.path.isfile(path):
        sys.exit("fichier absent : %s" % path)
    b = Blob(open(path, "rb").read())
    bsp = b.start
    cli = b.at(b.u32(bsp + CITY_LEVEL_INFO_OFFSET))
    dims = [b.s8(cli + 12 + i) for i in range(3)]
    grid = {"dims": dims,
            "box_min_m": b.metres(cli + 16), "box_max_m": b.metres(cli + 32),
            "cell_size_m": b.metres(cli + 48)}
    cell_array = b.at(b.u32(cli + 64))
    segment_count = b.s16(cli + 68)
    cell_count = b.u16(cli + 70)
    segment_array = b.at(b.u32(cli + 72))
    graph = b.at(b.u32(cli + 76))
    ceiling = b.f32(cli + 80) / UNIT

    node_count = b.s16(graph)
    branch_count = b.s16(graph + 2)
    node_array = b.at(b.u32(graph + 4))
    branch_array = b.at(b.u32(graph + 8))
    link_count = b.s16(graph + 12)
    link_array = b.at(b.u32(graph + 16))
    first_node = b.s16(graph + 20)
    graph_id = b.u32(graph + 28)

    nodes = []
    for i in range(node_count):
        o = node_array + i * NODE
        pos = b.metres(o)
        nodes.append({
            "index": i, "id": b.u16(o + 14),
            "pos_m": pos, "pos_cell": cells(pos),
            "angle_deg": round(b.u16(o + 12) * 360.0 / 65536.0, 2),
            "radius_m": b.u8(o + 16) / 4.0,
            "branch_count": b.s8(o + 17),
            "flags": b.u8(o + 18),
            "pedestrian": bool(b.u8(o + 18) & PEDESTRIAN),
            "first_branch": b.u32(o + 20),
            "nav_mesh_id": b.u32(o + 24),
        })
    branches = []
    for i in range(branch_count):
        o = branch_array + i * BRANCH
        dest = b.u32(o + 4)
        branches.append({
            "index": i, "src": b.u32(o), "dest": dest if dest < LINK_MARK else None,
            "link": None if dest < LINK_MARK else 0xFFFFFFFF - dest,
            "speed_ms": b.u8(o + 8) / 4.0, "density": b.u8(o + 9) / 128.0,
            "clock_type": b.u8(o + 10), "clock_mask": b.u8(o + 11),
            "max_users": b.u8(o + 12), "users": b.u8(o + 13),
            "width_m": b.u8(o + 14) / 16.0, "flags": b.u8(o + 15),
        })
    links = []
    for i in range(link_count):
        o = link_array + i * LINK
        dummy = b.metres(o + 16)
        links.append({"index": i, "id": b.u32(o), "dest_graph_id": b.u32(o + 4),
                      "src_branch": b.u16(o + 8), "dest_node_id": b.u16(o + 10),
                      "dummy_pos_m": dummy, "dummy_pos_cell": cells(dummy)})
    segments = []
    for i in range(segment_count):
        o = segment_array + i * SEGMENT
        a = b.metres(o)
        c = b.metres(o + 16)
        segments.append({
            "index": i, "id": b.u16(o + 40),
            "a_m": a, "b_m": c, "a_cell": cells(a), "b_cell": cells(c),
            "length_m": round(b.f32(o + 12) / UNIT, 2),
            "spawn_spacing_m": round(b.f32(o + 28) / UNIT, 2),
            "branch": b.u32(o + 32), "nav_mesh_id": b.u32(o + 36),
            "cell": b.u16(o + 42), "from_cell": b.u16(o + 44),
            "vehicle": b.s8(o + 46) == 0,
        })
    vis_cells = []
    for i in range(cell_count):
        o = cell_array + i * CELL
        centre = b.metres(o)
        vis_cells.append({"index": i, "id": b.u16(o + 22), "centre_m": centre, "centre_cell": cells(centre),
                          "radius_m": b.f32(o + 12) / UNIT, "first_segment": b.u32(o + 16),
                          "incoming": b.s8(o + 24), "segment_count": b.s8(o + 25),
                          "flags": b.u8(o + 26), "territories": b.u32(o + 28)})
    return {"level": level, "graph_id": graph_id, "first_node": first_node, "camera_ceiling_m": ceiling,
            "grid": grid, "nodes": nodes, "branches": branches, "links": links,
            "segments": segments, "cells": vis_cells}


def read_height_map():
    """La carte de hauteur de la voie haute, telle qu'ecrite en source GOAL."""
    text = open(HEIGHT_MAP, encoding="utf-8").read()

    def number(token):
        return int(token[2:], 16) if token.startswith("#x") else int(token)

    offset = [float(v) / UNIT for v in re.search(
        r":offset \(new 'static 'array float 3 ([-\d.e]+) ([-\d.e]+) ([-\d.e]+)\)", text).groups()]
    x_spacing = 1.0 / float(re.search(r":x-inv-spacing ([-\d.e]+)", text).group(1)) / UNIT
    z_spacing = 1.0 / float(re.search(r":z-inv-spacing ([-\d.e]+)", text).group(1)) / UNIT
    y_scale = float(re.search(r":y-scale ([-\d.e]+)", text).group(1)) / UNIT
    dim = [number(v) for v in re.search(r":dim \(new 'static 'array int16 2 (\S+) (\S+)\)", text).groups()]
    match = re.search(r":data \(new 'static 'array int8 (\d+)", text)
    count = int(match.group(1))
    values = [int(v) for v in re.findall(r"-?\d+", text[match.end():])][:count]
    if len(values) != count:
        sys.exit("carte de hauteur : %d valeurs lues, %d attendues" % (len(values), count))
    return {"offset_m": offset, "x_spacing_m": x_spacing, "z_spacing_m": z_spacing, "y_scale_m": y_scale,
            "x_dim": dim[0], "z_dim": dim[1], "data": values}


def height_at(height_map, x_m, z_m):
    """get-height-at-point (height-map.gc:86-111) : bilineaire, bornee, data[z * x-dim + x]."""
    fx = (x_m - height_map["offset_m"][0]) / height_map["x_spacing_m"]
    fz = (z_m - height_map["offset_m"][2]) / height_map["z_spacing_m"]
    xd, zd = height_map["x_dim"], height_map["z_dim"]
    fx = min(max(fx, 0.0), xd - 1.001)
    fz = min(max(fz, 0.0), zd - 1.001)
    ix, iz = int(fx), int(fz)
    tx, tz = fx - ix, fz - iz
    data = height_map["data"]

    def h(x, z):
        return data[z * xd + x]

    top = h(ix, iz) * (1 - tx) + h(ix + 1, iz) * tx
    bottom = h(ix, iz + 1) * (1 - tx) + h(ix + 1, iz + 1) * tx
    return height_map["offset_m"][1] + (top * (1 - tz) + bottom * tz) * height_map["y_scale_m"]


def summary(graph, height_map):
    vehicle_nodes = [n for n in graph["nodes"] if not n["pedestrian"]]
    vehicle_branches = [b for b in graph["branches"] if not graph["nodes"][b["src"]]["pedestrian"]]
    vehicle_segments = [s for s in graph["segments"] if s["vehicle"]]
    print("  graphe %d : %d noeuds (%d vehicule), %d branches (%d vehicule), %d lien(s), %d segments (%d vehicule), %d cellules"
          % (graph["graph_id"], len(graph["nodes"]), len(vehicle_nodes), len(graph["branches"]),
             len(vehicle_branches), len(graph["links"]), len(graph["segments"]), len(vehicle_segments),
             len(graph["cells"])))
    speeds = {}
    for b in vehicle_branches:
        speeds[b["speed_ms"]] = speeds.get(b["speed_ms"], 0) + 1
    print("  vitesses des branches vehicule (m/s) :", dict(sorted(speeds.items())))
    widths = sorted({b["width_m"] for b in vehicle_branches})
    radii = sorted({n["radius_m"] for n in vehicle_nodes})
    print("  largeurs %s m, rayons de virage %s m, carrefours (2 branches et plus) : %d"
          % (widths, radii, sum(1 for n in vehicle_nodes if n["branch_count"] >= 2)))
    length = sum(s["length_m"] for s in vehicle_segments)
    print("  longueur des voies vehicule : %.0f m ; espacement d'apparition %s m"
          % (length, sorted({s["spawn_spacing_m"] for s in vehicle_segments})))
    above = [n["pos_m"][1] - height_at(height_map, n["pos_m"][0], n["pos_m"][2]) for n in vehicle_nodes]
    print("  noeuds vehicule au-dessus de la carte de hauteur : moyenne %.2f m, min %.2f, max %.2f"
          % (sum(above) / len(above), min(above), max(above)))
    ys = [n["pos_cell"][1] for n in vehicle_nodes]
    print("  altitude des noeuds vehicule en cellules : %.1f a %.1f ; carte de hauteur en cellule %.1f (17,5 m)"
          % (min(ys), max(ys), 17.5 - GRID_ORIGIN[1]))


def render(graph, out_png):
    """Les voies sur la vue de dessus du volume du port : jaune vehicule, cyan pieton, rouge noeud, vert carrefour."""
    volume = read_volume("ctyport")
    dims, palette = volume["dims"], volume["palette"]
    runs = visible_runs(palette, volume["runs"])
    top_y, top_b = top_view(dims, palette, runs)
    w, h, d = dims
    img = Image.new("RGB", (w, d), (178, 206, 232))
    px = img.load()
    for col in range(w * d):
        block = top_b[col]
        if block:
            shade = 0.45 + 0.55 * min(1.0, max(0.0, (top_y[col] - 52) / 80.0))
            px[col % w, col // w] = tuple(int(c * shade) for c in color_of(palette[block]))
    draw = ImageDraw.Draw(img)
    for s in graph["segments"]:
        a, b = s["a_cell"], s["b_cell"]
        if s["vehicle"]:
            draw.line([(a[0], a[2]), (b[0], b[2])], fill=(255, 205, 0), width=2)
        else:
            draw.line([(a[0], a[2]), (b[0], b[2])], fill=(0, 200, 255), width=1)
    for n in graph["nodes"]:
        if n["pedestrian"]:
            continue
        x, z = n["pos_cell"][0], n["pos_cell"][2]
        colour = (0, 230, 60) if n["branch_count"] >= 2 else (235, 35, 35)
        r = 3 if n["branch_count"] >= 2 else 2
        draw.ellipse([x - r, z - r, x + r, z + r], fill=colour)
    for link in graph["links"]:
        x, z = link["dummy_pos_cell"][0], link["dummy_pos_cell"][2]
        draw.rectangle([x - 4, z - 4, x + 4, z + 4], outline=(255, 255, 255), width=2)
    img = img.resize((w * 2, d * 2), Image.NEAREST)
    canvas = Image.new("RGB", (img.width, img.height + 22), (24, 24, 30))
    canvas.paste(img, (0, 22))
    ImageDraw.Draw(canvas).text((6, 5), "%s : voies vehicule (jaune), pieton (cyan), noeuds (rouge), carrefours (vert), sortie (blanc) ; vue de dessus du volume"
                                % graph["level"], fill=(255, 255, 255))
    canvas.save(out_png)
    print("  image     %s" % out_png)



def clearance(graph, height_map):
    """Ce que les voies vehicule traversent dans le volume du port.

    Le long de chaque segment, tous les metres, a l'altitude de la carte de
    hauteur : les blocs pleins (sol, mur, toit) dans la boite d'une voiture,
    7 x 5 x 7 cellules (une largeur de voie de 4 m, un bloc sous l'origine et
    trois au-dessus). Rend, par segment touche, le pire compte et sa cellule.
    """
    from jak_preview import dense
    volume = read_volume("ctyport")
    dims, palette = volume["dims"], volume["palette"]
    w, h, d = dims
    solid = {i for i, name in enumerate(palette)
             if name in ("minecraft:polished_andesite", "minecraft:deepslate_bricks", "minecraft:deepslate_tiles")}
    cells_b, bw, bd = dense(dims, volume["runs"], (0, w - 1, 0, d - 1))
    hits = []
    for s in graph["segments"]:
        if not s["vehicle"]:
            continue
        a, b = s["a_m"], s["b_m"]
        n = max(1, int(round(s["length_m"])))
        worst, where = 0, None
        for i in range(n + 1):
            t = i / n
            x = a[0] + (b[0] - a[0]) * t
            z = a[2] + (b[2] - a[2]) * t
            y = height_at(height_map, x, z)
            cx, cy, cz = int(x - GRID_ORIGIN[0]), int(y - GRID_ORIGIN[1]), int(z - GRID_ORIGIN[2])
            count = 0
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    for dy in range(-1, 4):
                        X, Y, Z = cx + dx, cy + dy, cz + dz
                        if 0 <= X < w and 0 <= Y < h and 0 <= Z < d and cells_b[(Y * bd + Z) * bw + X] in solid:
                            count += 1
            if count > worst:
                worst, where = count, (cx, cy, cz)
        if worst:
            hits.append((s["index"], worst, where, s["a_cell"], s["b_cell"]))
    return hits


def write_mod(graph, height_map, path):
    """Les noeuds et branches vehicule, en cellules du volume, pour le mod.

    L'altitude d'un noeud est celle de la carte de hauteur (le y du graphe n'est
    pas utilise par le jeu : hvehicle.gc pose la voiture sur la carte). Une
    branche sans destination est une sortie du quartier : on y disparait.
    """
    vehicle = [n for n in graph["nodes"] if not n["pedestrian"]]
    index = {n["index"]: i for i, n in enumerate(vehicle)}
    branches = []
    for b in graph["branches"]:
        if b["src"] not in index:
            continue
        src = graph["nodes"][b["src"]]
        dest = graph["nodes"][b["dest"]] if b["dest"] is not None else None
        entry = {"index": len(branches), "src": index[b["src"]],
                 "dest": None if dest is None else index[b["dest"]],
                 "speed_ms": b["speed_ms"], "width": b["width_m"], "max_users": b["max_users"]}
        if dest is not None:
            far = dest["pos_m"]
        else:
            # une sortie : on s'eloigne vers le noeud factice du lien (le quartier voisin)
            link = graph["links"][b["link"]]
            far = link["dummy_pos_m"]
            entry["exit_x"] = round(far[0] - GRID_ORIGIN[0], 3)
            entry["exit_z"] = round(far[2] - GRID_ORIGIN[2], 3)
        entry["length"] = round(((far[0] - src["pos_m"][0]) ** 2 + (far[2] - src["pos_m"][2]) ** 2) ** 0.5, 2)
        branches.append(entry)
    nodes = []
    for i, n in enumerate(vehicle):
        x, y, z = n["pos_m"]
        lane = height_at(height_map, x, z)
        nodes.append({"index": i, "id": n["id"],
                      "x": round(x - GRID_ORIGIN[0], 3), "y": round(lane - GRID_ORIGIN[1], 3),
                      "z": round(z - GRID_ORIGIN[2], 3),
                      "angle_deg": n["angle_deg"], "radius": n["radius_m"],
                      "branches": [b["index"] for b in branches if b["src"] == i]})
    out = {
        "_format": [
            "Les voies du trafic civil de Jak 3 dans le port, lues par tools/jak_navgraph.py dans ctyport-vis.go (nav-graph 112).",
            "UNITES : cellules du volume ctyport (1 cellule = 1 m du jeu = 1 bloc) ; un bloc du monde = origine de pose + cellule.",
            "nodes : x, z du noeud ; y = altitude de la voie haute a cet endroit, d'apres *traffic-height-map* (17,5 m = 75,0 partout",
            "  sur le port, sauf quelques bosses) ; angle_deg = cap du noeud dans le repere de Jak 3 (direction = (sin a, 0, -cos a)) ;",
            "  radius = rayon de virage en cellules ; branches = indices des branches qui en partent.",
            "branches : a sens unique, de src vers dest ; dest absent = sortie du quartier : exit_x, exit_z est le point",
            "  vers lequel on s'eloigne (le noeud du quartier voisin), et le vehicule disparait en chemin ;",
            "  speed_ms = vitesse de croisiere de Jak 3 ; width = largeur de voie ; max_users = vehicules au plus sur la branche.",
        ],
        "volume": "ctyport", "graph_id": graph["graph_id"],
        "lane_y_default": round(17.5 - GRID_ORIGIN[1], 3),
        "nodes": nodes, "branches": branches,
    }
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(out, handle, indent=1)
    print("  mod       %s : %d noeuds, %d branches (%d sorties)" % (
        path, len(nodes), len(branches), sum(1 for b in branches if b["dest"] is None)))

def main():
    level = sys.argv[1] if len(sys.argv) > 1 else "ctyport"
    graph = read_graph(level)
    height_map = read_height_map()
    graph["height_map"] = height_map
    summary(graph, height_map)
    os.makedirs(OUT_DIR, exist_ok=True)
    out_json = os.path.join(OUT_DIR, "%s_navgraph.json" % level)
    with open(out_json, "w", encoding="utf-8") as handle:
        json.dump(graph, handle, indent=1)
    print("  fichier   %s" % out_json)
    if level == "ctyport":
        render(graph, os.path.join(OUT_DIR, "%s_navgraph.png" % level))
        hits = clearance(graph, height_map)
        print("  voies     %d segment(s) vehicule sur %d traversent un bloc plein du volume" % (
            len(hits), sum(1 for s in graph["segments"] if s["vehicle"])))
        for index, worst, where, a, b in sorted(hits, key=lambda h: -h[1])[:20]:
            print("            segment %3d : %3d blocs pleins au pire, en cellule %s ; de %s a %s" % (index, worst, where, a, b))
        if "--mod" in sys.argv:
            write_mod(graph, height_map, os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons",
                                                      "jak", "haven_traffic.json"))


if __name__ == "__main__":
    main()
