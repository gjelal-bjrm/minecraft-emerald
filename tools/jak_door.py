#!/usr/bin/env python3
"""
Cuit les portes de Jak 3 pour les portes de Haven (cahier §95) : la porte du Hip Hog
(hip-door-a) et le grand sas du port (com-airlock-outer), toutes deux du niveau ctyport.

Meme cuisson que les vehicules (tools/jak_vehicle.py, format JKVH : triangles, atlas, os
dominant par triangle) -- on reutilise ses fonctions telles quelles --, plus ce que les
vehicules n'ont pas : L'ANIMATION. Chaque glb porte un clip « idle » que le jeu fait
avancer pour ouvrir (airlock.gc : fermee a l'image 0, ouverte a la derniere). On
echantillonne ce clip en N pas et l'on ecrit, pour chaque os anime, la matrice qui amene
un sommet de la pose de repos a la pose du pas : le rendu du mod choisit le pas d'apres
l'avancement de la porte et deplace les triangles de l'os. Un triangle n'a qu'un os
dominant : une porte est faite de panneaux rigides, c'est exact.

Sorties :
    assets/emeraldweapons/jak_doors/<nom>.bin           les triangles (JKVH)
    assets/emeraldweapons/jak_doors/<nom>.anim.json     les pas de l'animation
    assets/emeraldweapons/textures/entity/jak_doors/atlas.png
    build/jak/doors/<nom>_repos.png, _ferme.png, _ouvert.png   images de controle

Usage : python tools/jak_door.py [--steps 24] [--no-png]
"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jak_assets as ja  # noqa: E402
import jak_vehicle as jv  # noqa: E402

from PIL import Image  # noqa: E402

DOORS = {"hip_door_a": "hip-door-a", "com_airlock_outer": "com-airlock-outer"}
BIN_DIR = os.path.join(jv.ASSETS, "jak_doors")
ATLAS_PATH = os.path.join(jv.ASSETS, "textures", "entity", "jak_doors", "atlas.png")
OUT_DIR = os.path.join(ja.ROOT, "build", "jak", "doors")


def quat_matrix(q):
    """La matrice 4x4 (colonnes, comme jak_vehicle) d'un quaternion x, y, z, w."""
    x, y, z, w = q
    return [1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0,
            2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0,
            2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0,
            0, 0, 0, 1]


def trs_matrix(t, r, s):
    m = quat_matrix(r)
    for col in range(3):
        for row in range(3):
            m[col * 4 + row] *= s[col]
    m[12], m[13], m[14] = t
    return m


def invert_rigid(m):
    """L'inverse d'une matrice rotation + translation (sans echelle)."""
    r = [[m[0], m[4], m[8]], [m[1], m[5], m[9]], [m[2], m[6], m[10]]]   # lignes
    t = (m[12], m[13], m[14])
    out = [0.0] * 16
    for col in range(3):
        for row in range(3):
            out[col * 4 + row] = r[col][row]       # la transposee
    for row in range(3):
        out[12 + row] = -sum(r[c][row] * t[c] for c in range(3))
    out[15] = 1.0
    return out


def sample(track, t):
    """Une piste de cles (temps, valeur) lue au temps t, interpolee lineairement (quaternions : nlerp)."""
    times, values = track
    if t <= times[0]:
        return values[0]
    if t >= times[-1]:
        return values[-1]
    for i in range(1, len(times)):
        if t <= times[i]:
            a, b = values[i - 1], values[i]
            k = (t - times[i - 1]) / max(1e-9, times[i] - times[i - 1])
            if len(a) == 4 and sum(x * y for x, y in zip(a, b)) < 0:
                b = tuple(-x for x in b)
            v = tuple(x + (y - x) * k for x, y in zip(a, b))
            if len(v) == 4:
                n = math.sqrt(sum(x * x for x in v)) or 1.0
                v = tuple(x / n for x in v)
            return v
    return values[-1]


def read_animation(js, blob):
    """Les pistes du clip : {noeud: {"translation": (temps, valeurs), "rotation": ..., "scale": ...}}, et sa duree."""
    anims = js.get("animations", [])
    if len(anims) != 1:
        sys.exit("%d clips d'animation, un seul attendu" % len(anims))
    anim = anims[0]
    tracks = {}
    end = 0.0
    for ch in anim["channels"]:
        smp = anim["samplers"][ch["sampler"]]
        times = [v[0] for v in iter_accessor(js, blob, smp["input"])]
        values = list(iter_accessor(js, blob, smp["output"]))
        tracks.setdefault(ch["target"]["node"], {})[ch["target"]["path"]] = (times, values)
        end = max(end, times[-1])
    return anim.get("name", "?"), tracks, end


def iter_accessor(js, blob, index):
    acc = jv.Accessor(js, blob, index)
    for i in range(acc.count):
        yield acc[i]


def node_local(node, tracks, t):
    """La transformation locale d'un noeud au temps t : les pistes animees, sinon celle du repos.

    OpenGOAL ecrit le repos des articulations en MATRICE (pas en translation-rotation-echelle)
    et anime par-dessus : sans la lire, le repos etait l'identite, la porte fermee paraissait
    ouverte et l'ouverte deux fois plus (premiere cuisson).
    """
    if tracks is None or not tracks:
        return jv.node_matrix(node)
    tr = sample(tracks["translation"], t) if "translation" in tracks else (0.0, 0.0, 0.0)
    ro = sample(tracks["rotation"], t) if "rotation" in tracks else (0.0, 0.0, 0.0, 1.0)
    sc = sample(tracks["scale"], t) if "scale" in tracks else (1.0, 1.0, 1.0)
    return trs_matrix(tuple(tr), tuple(ro), tuple(sc))


def bone_deltas(model, steps):
    """Pour chaque os, a chaque pas : la matrice repos -> pas (monde du modele), et la duree du clip."""
    path = os.path.join(ja.LEVELS, jv.LEVELS[model], "%s-lod0.glb" % model)
    js, blob = ja.read_glb(path)
    nodes = js["nodes"]
    parent = {}
    for i, node in enumerate(nodes):
        for child in node.get("children", []):
            parent[child] = i
    name, tracks, end = read_animation(js, blob)

    def world(i, t):
        local = node_local(nodes[i], tracks.get(i) if t is not None else None, t if t is not None else 0.0)
        return jv.mat_mul(world(parent[i], t), local) if i in parent else local

    joints = js["skins"][0]["joints"]
    deltas = {}
    animated = []
    for j in joints:
        rest = world(j, None)
        inv = invert_rigid(rest)
        frames = []
        moving = False
        for k in range(steps + 1):
            t = end * k / steps
            m = jv.mat_mul(world(j, t), inv)
            frames.append([round(v, 5) for v in m])
            if any(abs(a - b) > 1e-4 for a, b in zip(m, [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1])):
                moving = True
        if moving:
            deltas[nodes[j].get("name", "os%d" % joints.index(j))] = frames
            animated.append(nodes[j].get("name"))
    return name, end, deltas, animated


def posed(back, deltas, step):
    """Les triangles du .bin relus, chaque os deplace au pas donne (None : le repos)."""
    if step is None:
        return back
    names = [b[0] for b in back["bones"]]
    tris = []
    for tri in back["tris"]:
        frames = deltas.get(names[tri["bone"]])
        if frames is None:
            tris.append(tri)
            continue
        m = frames[step]
        verts = []
        for v in tri["verts"]:
            p = jv.mat_point(m, v["pos"])
            n = jv.mat_dir(m, v["normal"])
            verts.append(dict(v, pos=p, normal=n))
        tris.append(dict(tri, verts=verts))
    return dict(back, tris=tris)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--steps", type=int, default=24, help="pas de l'animation ecrits")
    parser.add_argument("--no-png", action="store_true")
    args = parser.parse_args()

    for out, model in DOORS.items():
        jv.LEVELS[model] = "ctyport"
    jv.BIN_DIR = BIN_DIR
    jv.ATLAS_PATH = ATLAS_PATH
    os.makedirs(BIN_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(ATLAS_PATH), exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)

    models = [jv.read_model(model) for model in DOORS.values()]
    atlas, tiles = jv.build_atlas(models)
    atlas.save(ATLAS_PATH)
    atlas = Image.open(ATLAS_PATH).convert("RGBA")
    print("atlas %d x %d, %d tuiles -> %s" % (atlas.size[0], atlas.size[1], len(tiles), ATLAS_PATH))

    for (out, model), data in zip(DOORS.items(), models):
        path, mn, mx = jv.write_bin(data, tiles, atlas.size)
        final = os.path.join(BIN_DIR, out + ".bin")
        if os.path.abspath(path) != os.path.abspath(final):
            if os.path.exists(final):
                os.remove(final)
            os.rename(path, final)
        back = jv.read_bin(final)
        clip, end, deltas, animated = bone_deltas(model, args.steps)
        with open(os.path.join(BIN_DIR, out + ".anim.json"), "w", encoding="utf-8") as handle:
            json.dump({"clip": clip, "seconds": round(end, 4), "steps": args.steps, "bones": deltas}, handle,
                      separators=(",", ":"))
        by_bone = {}
        for tri in back["tris"]:
            by_bone[back["bones"][tri["bone"]][0]] = by_bone.get(back["bones"][tri["bone"]][0], 0) + 1
        print("%s : %d triangles, boite (%.2f, %.2f, %.2f) -> (%.2f, %.2f, %.2f), clip %s de %.2f s, os animes %s"
              % (out, len(back["tris"]), *mn, *mx, clip, end, animated))
        print("  triangles par os : %s" % ", ".join("%s %d" % kv for kv in sorted(by_bone.items())))
        # l'etendue des os animes au repos, au debut et a la fin du clip
        for bone in animated:
            xs = {"repos": [], "debut": [], "fin": []}
            for label, step in (("repos", None), ("debut", 0), ("fin", args.steps)):
                for tri in posed(back, deltas, step)["tris"]:
                    if back["bones"][tri["bone"]][0] == bone:
                        xs[label].extend(v["pos"] for v in tri["verts"])
            for label, pts in xs.items():
                if pts:
                    print("  %-6s %-6s x %6.2f..%6.2f  y %6.2f..%6.2f  z %6.2f..%6.2f" % (
                        bone, label, min(p[0] for p in pts), max(p[0] for p in pts), min(p[1] for p in pts),
                        max(p[1] for p in pts), min(p[2] for p in pts), max(p[2] for p in pts)))
        if args.no_png:
            continue
        for label, step in (("repos", None), ("ferme", 0), ("ouvert", args.steps)):
            sheet = jv.render_sheet(out, posed(back, deltas, step), atlas, 40)
            target = os.path.join(OUT_DIR, "%s_%s.png" % (out, label))
            sheet.save(target)
            print("  controle %s -> %s" % (label, target))


if __name__ == "__main__":
    main()
