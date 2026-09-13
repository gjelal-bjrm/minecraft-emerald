"""Sort les textures et l'inventaire d'un modele glTF binaire de Jak 3.

Les modeles extraits par OpenGOAL sont des `.glb` : un en-tete, un bloc JSON,
un bloc binaire. Les textures y sont EMBARQUEES -- c'est pour cela qu'on n'en
trouve aucune dans le dossier `textures/` pour l'arme, et qu'il faut ouvrir le
fichier pour les voir.

Le format se lit sans dependance : trois entiers d'en-tete, puis des morceaux
precedes de leur longueur. Une bibliotheque glTF ferait la meme chose en
important trois mille lignes.

A quoi cela sert ici : une texture de jeu n'entre pas telle quelle dans
Minecraft, mais elle donne la PALETTE et le dessin d'un objet -- de quoi
peindre une icone de seize pixels qui se reconnaisse au premier coup d'oeil.
L'inventaire (maillages, os, animations) sert, lui, a juger ce qu'on peut
raisonnablement porter en jeu et ce qu'il faudra refaire a la main.

Usage :
    python tools/jak_assets.py common/gun-lod0.glb
    python tools/jak_assets.py common/gun-lod0.glb --textures
"""

import argparse
import base64
import json
import os
import struct
import sys

DECOMP = os.path.join(os.environ.get("USERPROFILE", ""), "Documents", "OpenGoal",
                      "active", "jak3", "data", "decompiler_out", "jak3")
LEVELS = os.path.join(DECOMP, "levels")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "build", "jak", "assets")

JSON_CHUNK = 0x4E4F534A
BIN_CHUNK = 0x004E4942


def read_glb(path):
    """Le JSON et le bloc binaire d'un .glb."""
    raw = open(path, "rb").read()
    magic, version, total = struct.unpack("<III", raw[:12])
    if magic != 0x46546C67:
        sys.exit("%s n'est pas un glTF binaire" % path)
    chunks = {}
    off = 12
    while off < total:
        length, kind = struct.unpack("<II", raw[off:off + 8])
        chunks[kind] = raw[off + 8:off + 8 + length]
        # les morceaux sont alignes sur quatre octets : sauter ce bourrage,
        # sinon on lit la longueur suivante au milieu d'un octet de remplissage
        off += 8 + length
        if length % 4:
            off += 4 - (length % 4)
    return json.loads(chunks[JSON_CHUNK].decode("utf-8")), chunks.get(BIN_CHUNK, b"")


def slice_view(js, blob, index):
    view = js["bufferViews"][index]
    start = view.get("byteOffset", 0)
    return blob[start:start + view["byteLength"]]


def _accessor(js, index):
    acc = js["accessors"][index]
    view = js["bufferViews"][acc["bufferView"]]
    return acc, view, view.get("byteOffset", 0) + acc.get("byteOffset", 0)


def _positions(js, blob, index):
    acc, view, start = _accessor(js, index)
    if acc["componentType"] != 5126 or acc["type"] != "VEC3":
        sys.exit("positions en %s %s, non gerees" % (acc["componentType"], acc["type"]))
    stride = view.get("byteStride", 12)
    count = acc["count"]
    if stride == 12:
        flat = struct.unpack_from("<%df" % (count * 3), blob, start)
        return [flat[i * 3:i * 3 + 3] for i in range(count)]
    return [struct.unpack_from("<3f", blob, start + i * stride) for i in range(count)]


def _indices(js, blob, index):
    acc, view, start = _accessor(js, index)
    fmt = {5121: "B", 5123: "H", 5125: "I"}[acc["componentType"]]
    return struct.unpack_from("<%d%s" % (acc["count"], fmt), blob, start)


def mesh_triangles(path, with_names=False):
    """Tous les triangles d'un decor de niveau, en coordonnees du monde.

    Les decors exportes par OpenGOAL (`<niveau>-background.glb`) n'ont aucune
    transformation de noeud : leurs sommets sont deja places dans le monde, en
    metres, et tombent pile sur la collision (le bar : -113,2 a -61,7 en x pour
    le visuel, -112,9 a -61,9 pour la collision). On le VERIFIE a chaque
    lecture plutot que de le supposer : un noeud transforme ailleurs poserait
    ses triangles au mauvais endroit sans la moindre erreur.

    Avec `with_names`, rend aussi le nom du noeud de chaque triangle : c'est
    ce qui permet de trier un decor, les murs d'une tour d'un cable qui passe
    devant.
    """
    js, blob = read_glb(path)
    verts = []
    faces = []
    names = []
    for node in js.get("nodes", []):
        if "mesh" not in node:
            continue
        if any(key in node for key in ("matrix", "translation", "rotation", "scale")):
            sys.exit("%s : le noeud %s est transforme, non gere" % (path, node.get("name")))
        for prim in js["meshes"][node["mesh"]]["primitives"]:
            if prim.get("mode", 4) != 4:
                sys.exit("%s : primitive en mode %s, non geree" % (path, prim.get("mode")))
            pos = _positions(js, blob, prim["attributes"]["POSITION"])
            base = len(verts)
            verts.extend(pos)
            idx = _indices(js, blob, prim["indices"]) if "indices" in prim else range(len(pos))
            for t in range(0, len(idx) - 2, 3):
                faces.append((base + idx[t], base + idx[t + 1], base + idx[t + 2]))
                names.append(node.get("name", ""))
    if with_names:
        return verts, faces, names
    return verts, faces


def sniff(data):
    """L'extension d'une image, devinee sur ses premiers octets."""
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return "png"
    if data[:2] == b"\xff\xd8":
        return "jpg"
    return "bin"


def export_textures(js, blob, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    written = []
    for i, image in enumerate(js.get("images", [])):
        # Deux rangements possibles, et l'extracteur d'OpenGOAL utilise le
        # SECOND : soit un morceau du bloc binaire, soit une adresse `data:`
        # en base64 dans le JSON. Ne lire que le premier rendait zero image
        # sans la moindre erreur, ce qui est la pire facon d'echouer.
        if "bufferView" in image:
            data = slice_view(js, blob, image["bufferView"])
        elif str(image.get("uri", "")).startswith("data:"):
            data = base64.b64decode(image["uri"].split(",", 1)[1])
        else:
            continue
        name = image.get("name") or ("image-%03d" % i)
        # deux images peuvent porter le meme nom (les variantes d'un materiau) :
        # on prefixe par l'index plutot que d'en perdre une en silence
        path = os.path.join(out_dir, "%02d-%s.%s" % (i, name, sniff(data)))
        with open(path, "wb") as handle:
            handle.write(data)
        written.append((path, len(data)))
    return written


def inventory(js):
    tris = 0
    for mesh in js.get("meshes", []):
        for prim in mesh.get("primitives", []):
            if "indices" in prim:
                tris += js["accessors"][prim["indices"]]["count"] // 3
    return {
        "maillages": len(js.get("meshes", [])),
        "primitives": sum(len(m.get("primitives", [])) for m in js.get("meshes", [])),
        "triangles": tris,
        "materiaux": len(js.get("materials", [])),
        "images": len(js.get("images", [])),
        "noeuds": len(js.get("nodes", [])),
        "squelettes": len(js.get("skins", [])),
        "animations": len(js.get("animations", [])),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("model", help="chemin relatif a levels/, par exemple common/gun-lod0.glb")
    parser.add_argument("--textures", action="store_true", help="ecrire les images")
    parser.add_argument("--animations", action="store_true", help="lister les animations")
    args = parser.parse_args()

    path = args.model if os.path.isabs(args.model) else os.path.join(LEVELS, args.model)
    if not os.path.isfile(path):
        sys.exit("modele introuvable : %s" % path)

    js, blob = read_glb(path)
    stem = os.path.splitext(os.path.basename(path))[0]
    print("%s" % stem)
    for key, value in inventory(js).items():
        print("  %-12s %d" % (key, value))

    if args.animations:
        names = [a.get("name", "?") for a in js.get("animations", [])]
        print("  animations : %s" % ", ".join(names))

    if args.textures:
        out = os.path.join(OUT_DIR, stem)
        written = export_textures(js, blob, out)
        print("  %d images ecrites dans %s" % (len(written), out))
        for p, size in written[:12]:
            print("     %-46s %6d o" % (os.path.basename(p), size))


if __name__ == "__main__":
    main()
