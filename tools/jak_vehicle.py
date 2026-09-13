"""Cuit les voitures civiles de Haven City (Jak 3) en triangles pour Minecraft.

Les trois voitures du port -- cara, carb, carc -- sont des modeles « merc »
extraits par OpenGOAL en `.glb`. Minecraft ne lit pas le glTF : on en tire ici
une liste de triangles prets a dessiner par modele, et UNE image, l'atlas, qui
regroupe toutes leurs textures. Le rendu du mod n'a plus rien a calculer.

CE QUE LES MESURES SUR LES GLB ONT APPRIS, dans l'ordre.

1. LE TAMPON EST PARTAGE. L'accesseur POSITION couvre TOUS les modeles du DGO,
   pas seulement la voiture : le parcourir en entier donnait les memes bornes
   aux trois voitures, et des sommets citant des os absents du squelette de
   carb. On ne lit donc QUE les sommets cites par les indices, un par un.

2. LES COULEURS DE SOMMET SUIVENT LA CONVENTION PS2. COLOR_0 plafonne a 0,502 :
   0,5 y est la valeur neutre. Le glb le compense par un facteur de couleur de
   base de 2 sur chaque materiau ; Minecraft n'a pas ce facteur, on multiplie
   donc la couleur de sommet par 2. L'alpha des textures suit la meme regle
   (128 = opaque) et est double dans l'atlas ; celui des sommets vaut deja 1.
   Oublier ce facteur rendait des voitures deux fois trop sombres.

3. LES UV SE REPETENT. Plus de la moitie des triangles ont des UV hors de
   [0, 1] : le PS2 repete la texture, un atlas ne repete rien. Pour chaque
   triangle on retire la partie entiere commune de ses UV, puis on cuit dans
   l'atlas chaque texture repetee autant de fois que le triangle le plus etale
   l'exige (jusqu'a 3 x 5 pour pipe01), entouree de 2 pixels de bord pris dans
   la repetition elle-meme, pour qu'un echantillon au bord ne morde pas sur la
   tuile voisine.

4. LA POSE DE REPOS EST DEJA LA. Le rapport dit qu'appliquer le squelette ne
   deplace rien. On l'applique quand meme, et on MESURE l'ecart plutot que de
   le supposer.

5. ON CONTROLE LA SORTIE, PAS L'ENTREE. Les images de controle sont dessinees
   depuis les .bin et l'atlas ecrits, relus comme le fera le mod ; et chaque
   triangle relu est compare au glb : position, couleur, texel sous son centre.
   Un rendu tire du glb aurait montre une voiture juste avec un fichier faux.

Repere : metres = blocs, avant du modele en +z, gauche en +x (phares a
z = +3,8 m, noeud frontfinl_ a x = +1,465). Une entite Minecraft de lacet 0
regarde +Z et a sa gauche en +X : meme main, aucun miroir (a verifier en jeu).

FORMAT DE <modele>.bin (petit-boutiste)
    en-tete, 48 octets
        4s    magie b"JKVH"
        I     version (1)
        I     nombre de triangles T
        I     nombre d'os B
        I, I  largeur et hauteur de l'atlas en pixels
        3f    coin minimum de la boite (m)
        3f    coin maximum de la boite (m)
    B os, 40 octets chacun, dans l'ordre des joints du squelette
        24s   nom ASCII, complete par des zeros
        h     parent (indice dans cette table, -1 pour la racine)
        H     reserve (0)
        3f    pivot au repos (m)
    T triangles, 112 octets chacun
        B     os dominant (indice dans la table des os)
        B     drapeaux : 1 = materiau BLEND (translucide)
        H     reserve (0)
        3 sommets de 36 octets : 3f position (m), 2f UV dans l'atlas en [0, 1]
                                 (v vers le bas, comme Minecraft), 3f normale,
                                 4B couleur RGBA deja multipliee par 2

L'atlas est commun aux trois voitures : l'outil les traite donc toujours
ensemble, sans quoi une voiture regeneree seule decalerait les autres.

Usage :
    python tools/jak_vehicle.py
    python tools/jak_vehicle.py --no-png
    python tools/jak_vehicle.py --reference <dossier des veh_*-lod0.png>
"""

import argparse
import hashlib
import json
import math
import os
import struct
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jak_assets as ja  # noqa: E402  (read_glb, slice_view, export_textures)

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow est necessaire : pip install pillow")

MODELS = ("cara", "carb", "carc")
LEVEL = "ctycara"

ASSETS = os.path.join(ja.ROOT, "src", "main", "resources", "assets", "emeraldweapons")
BIN_DIR = os.path.join(ASSETS, "jak_vehicles")
ATLAS_PATH = os.path.join(ASSETS, "textures", "entity", "jak_vehicles", "atlas.png")
OUT_DIR = os.path.join(ja.ROOT, "build", "jak", "vehicles")

MAGIC = b"JKVH"
VERSION = 1
HEADER = struct.Struct("<4sIIIII3f3f")
BONE = struct.Struct("<24shH3f")
TRI = struct.Struct("<BBH" + "3f2f3f4B" * 3)
FLAG_BLEND = 1

BORDER = 2
EPS = 1e-4
# seuil de decoupe des shaders d'entite de Minecraft (alpha < 0,1 jete) : c'est
# celui qu'on applique aux images de controle, pas celui du glb (0,149)
CUTOFF = 0.1

COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
FORMATS = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}
NORMS = {5120: 127.0, 5121: 255.0, 5122: 32767.0, 5123: 65535.0}


# --------------------------------------------------------------- lecture glb

class Accessor:
    """Un accesseur glTF lu element par element, a la demande.

    Jamais d'un bloc : c'est ce qui evite de lire les sommets des autres
    modeles du DGO (lecon 1).
    """

    def __init__(self, js, blob, index):
        acc = js["accessors"][index]
        view = js["bufferViews"][acc["bufferView"]]
        self.count = acc["count"]
        self.struct = struct.Struct("<%d%s" % (COMPONENTS[acc["type"]], FORMATS[acc["componentType"]]))
        self.stride = view.get("byteStride", self.struct.size)
        self.start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
        self.blob = blob
        self.div = NORMS.get(acc["componentType"]) if acc.get("normalized") else None

    def __getitem__(self, i):
        if not 0 <= i < self.count:
            sys.exit("indice %d hors de l'accesseur (%d elements)" % (i, self.count))
        value = self.struct.unpack_from(self.blob, self.start + i * self.stride)
        if self.div:
            value = tuple(x / self.div for x in value)
        return value


def mat_mul(a, b):
    """Produit de deux matrices 4x4 rangees par colonnes, comme le glTF."""
    out = [0.0] * 16
    for col in range(4):
        for row in range(4):
            out[col * 4 + row] = sum(a[k * 4 + row] * b[col * 4 + k] for k in range(4))
    return out


def mat_point(m, p):
    x, y, z = p
    return (m[0] * x + m[4] * y + m[8] * z + m[12],
            m[1] * x + m[5] * y + m[9] * z + m[13],
            m[2] * x + m[6] * y + m[10] * z + m[14])


def mat_dir(m, d):
    x, y, z = d
    return (m[0] * x + m[4] * y + m[8] * z,
            m[1] * x + m[5] * y + m[9] * z,
            m[2] * x + m[6] * y + m[10] * z)


def normalize(v):
    n = math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    return (0.0, 1.0, 0.0) if n < 1e-12 else (v[0] / n, v[1] / n, v[2] / n)


def node_matrix(node):
    if "matrix" in node:
        return list(node["matrix"])
    tx, ty, tz = node.get("translation", (0.0, 0.0, 0.0))
    x, y, z, w = node.get("rotation", (0.0, 0.0, 0.0, 1.0))
    sx, sy, sz = node.get("scale", (1.0, 1.0, 1.0))
    return [(1 - 2 * (y * y + z * z)) * sx, (2 * (x * y + z * w)) * sx, (2 * (x * z - y * w)) * sx, 0.0,
            (2 * (x * y - z * w)) * sy, (1 - 2 * (x * x + z * z)) * sy, (2 * (y * z + x * w)) * sy, 0.0,
            (2 * (x * z + y * w)) * sz, (2 * (y * z - x * w)) * sz, (1 - 2 * (x * x + y * y)) * sz, 0.0,
            tx, ty, tz, 1.0]


def load_images(js, blob, model):
    """Les images du glb, ecrites par export_textures puis rouvertes.

    Les ecrire a un interet en soi : on peut les regarder une a une dans
    build/jak/vehicles/textures/<modele>/ quand une tuile parait fausse.
    """
    out = os.path.join(OUT_DIR, "textures", model)
    images = {}
    for path, _size in ja.export_textures(js, blob, out):
        index = int(os.path.basename(path).split("-", 1)[0])
        image = Image.open(path).convert("RGBA")
        image.load()
        # deux materiaux citent souvent la MEME image sous deux indices, et
        # les trois voitures partagent moter01 ou pipe01 : on les reconnait a
        # leurs pixels, pas a leur nom, pour ne cuire chaque tuile qu'une fois
        digest = hashlib.sha1(image.tobytes()).hexdigest()[:12]
        name = js["images"][index].get("name") or ("image-%03d" % index)
        images[index] = ("%s-%s" % (name, digest), image)
    return images


def read_model(model):
    """Les triangles d'une voiture, dans son repere, avec leurs UV brutes."""
    path = os.path.join(ja.LEVELS, LEVEL, "%s-lod0.glb" % model)
    if not os.path.isfile(path):
        sys.exit("modele introuvable : %s" % path)
    js, blob = ja.read_glb(path)
    nodes = js["nodes"]
    images = load_images(js, blob, model)

    mesh_nodes = [n for n in nodes if "mesh" in n]
    if len(mesh_nodes) != 1 or len(js.get("skins", [])) != 1:
        sys.exit("%s : %d noeuds de maillage et %d squelettes, un seul de chaque attendu"
                 % (model, len(mesh_nodes), len(js.get("skins", []))))
    if any(key in mesh_nodes[0] for key in ("matrix", "translation", "rotation", "scale")):
        sys.exit("%s : le noeud du maillage est transforme, non gere" % model)

    parent = {}
    for i, node in enumerate(nodes):
        for child in node.get("children", []):
            parent[child] = i
    glob = {}

    def world(i):
        if i not in glob:
            local = node_matrix(nodes[i])
            glob[i] = mat_mul(world(parent[i]), local) if i in parent else local
        return glob[i]

    skin = js["skins"][0]
    joints = skin["joints"]
    if "inverseBindMatrices" in skin:
        ibm = Accessor(js, blob, skin["inverseBindMatrices"])
        joint_mats = [mat_mul(world(j), list(ibm[k])) for k, j in enumerate(joints)]
    else:
        joint_mats = [world(j) for j in joints]
    bones = []
    for k, j in enumerate(joints):
        m = world(j)
        p = parent.get(j)
        bones.append({"name": nodes[j].get("name", "os%d" % k),
                      "parent": joints.index(p) if p in joints else -1,
                      "pivot": (m[12], m[13], m[14])})

    tris = []
    drift = 0.0
    cited = 0
    for mesh_index, mesh in enumerate(js["meshes"]):
        for prim in mesh["primitives"]:
            if prim.get("mode", 4) != 4:
                sys.exit("%s : primitive en mode %s, non geree" % (model, prim.get("mode")))
            material = js["materials"][prim["material"]] if "material" in prim else {}
            pbr = material.get("pbrMetallicRoughness", {})
            texture = pbr.get("baseColorTexture")
            if texture is None:
                # aucune ne l'est dans les trois voitures (mesure) : plutot que
                # d'inventer une couleur, on s'arrete
                sys.exit("%s : triangle sans texture, non gere" % model)
            if texture.get("texCoord", 0) != 0:
                sys.exit("%s : jeu d'UV %d, non gere" % (model, texture["texCoord"]))
            factor = pbr.get("baseColorFactor", [1.0, 1.0, 1.0, 1.0])
            if any(abs(f - 2.0) > 1e-3 for f in factor):
                # tout le x 2 de la lecon 2 repose sur ce facteur
                sys.exit("%s : facteur de couleur %s, 2 attendu" % (model, factor))
            key, image = images[js["textures"][texture["index"]]["source"]]
            blend = material.get("alphaMode") == "BLEND"

            at = prim["attributes"]
            pos = Accessor(js, blob, at["POSITION"])
            nrm = Accessor(js, blob, at["NORMAL"]) if "NORMAL" in at else None
            tex = Accessor(js, blob, at["TEXCOORD_0"])
            col = Accessor(js, blob, at["COLOR_0"]) if "COLOR_0" in at else None
            jnt = Accessor(js, blob, at["JOINTS_0"])
            wts = Accessor(js, blob, at["WEIGHTS_0"])
            ind = Accessor(js, blob, prim["indices"])
            idx = [ind[i][0] for i in range(ind.count)]

            verts = {}
            for vi in sorted(set(idx)):
                p = pos[vi]
                weights = wts[vi]
                bone_ids = jnt[vi]
                sp = [0.0, 0.0, 0.0]
                sn = [0.0, 0.0, 0.0]
                n = nrm[vi] if nrm else (0.0, 1.0, 0.0)
                total = 0.0
                for q in range(4):
                    w = weights[q]
                    if w <= 0.0:
                        continue
                    if bone_ids[q] >= len(joints):
                        sys.exit("%s : sommet %d cite l'os %d sur %d" % (model, vi, bone_ids[q], len(joints)))
                    m = joint_mats[bone_ids[q]]
                    rp = mat_point(m, p)
                    rn = mat_dir(m, n)
                    for k in range(3):
                        sp[k] += w * rp[k]
                        sn[k] += w * rn[k]
                    total += w
                if total <= 0.0:
                    sys.exit("%s : sommet %d sans poids" % (model, vi))
                sp = [c / total for c in sp]
                drift = max(drift, math.dist(sp, p))
                c = col[vi] if col else (0.5, 0.5, 0.5, 1.0)
                rgba = [min(255, max(0, int(round(c[k] * 2.0 * 255.0)))) for k in range(3)]
                rgba.append(min(255, max(0, int(round((c[3] if len(c) > 3 else 1.0) * 255.0)))))
                dominant = max(range(4), key=lambda q: weights[q])
                verts[vi] = {"pos": tuple(sp), "normal": normalize(sn), "uv": tex[vi],
                             "rgba": tuple(rgba), "bone": bone_ids[dominant]}
            cited += len(verts)

            for t in range(0, len(idx) - 2, 3):
                v = [verts[idx[t]], verts[idx[t + 1]], verts[idx[t + 2]]]
                bone = Counter(x["bone"] for x in v).most_common(1)[0][0]
                us = [x["uv"][0] for x in v]
                vs = [x["uv"][1] for x in v]
                # decalage ENTIER commun au triangle : il ne change aucun texel
                # d'une texture qui se repete, et ramene le triangle pres de 0
                ou = math.floor(min(us) + EPS)
                ov = math.floor(min(vs) + EPS)
                tris.append({"verts": v, "bone": bone, "blend": blend, "image": key,
                             "local": [(max(0.0, u - ou), max(0.0, w - ov)) for u, w in zip(us, vs)],
                             "raw_uv": list(zip(us, vs))})
    return {"model": model, "tris": tris, "bones": bones, "drift": drift, "cited": cited,
            "images": {key: image for key, image in images.values()}}


# --------------------------------------------------------------------- atlas

def build_atlas(models):
    """Une tuile par image, repetee autant que l'exige son triangle le plus etale."""
    reps = {}
    sources = {}
    for data in models:
        sources.update(data["images"])
        for tri in data["tris"]:
            ru = max(1, math.ceil(max(u for u, _ in tri["local"]) - EPS))
            rv = max(1, math.ceil(max(v for _, v in tri["local"]) - EPS))
            old = reps.get(tri["image"], (1, 1))
            reps[tri["image"]] = (max(old[0], ru), max(old[1], rv))

    tiles = []
    for key, (ru, rv) in reps.items():
        image = sources[key]
        w, h = image.size
        tiles.append({"key": key, "reps": (ru, rv), "size": (w, h),
                      "outer": (ru * w + 2 * BORDER, rv * h + 2 * BORDER)})

    # rangement en etageres, les plus hautes d'abord ; on garde le plus petit
    # atlas carre ou presque qui contient tout
    best = None
    for width in (256, 512, 1024, 2048, 4096):
        if max(t["outer"][0] for t in tiles) > width:
            continue
        x = y = shelf = 0
        placed = {}
        for tile in sorted(tiles, key=lambda t: (-t["outer"][1], -t["outer"][0], t["key"])):
            tw, th = tile["outer"]
            if x + tw > width:
                x, y, shelf = 0, y + shelf, 0
            placed[tile["key"]] = (x, y)
            x += tw
            shelf = max(shelf, th)
        height = 1 << max(0, math.ceil(math.log2(max(1, y + shelf))))
        if height > 4096:
            continue
        if best is None or width * height < best[0] * best[1] or \
                (width * height == best[0] * best[1] and abs(width - height) < abs(best[0] - best[1])):
            best = (width, height, placed)
    if best is None:
        sys.exit("les tuiles ne tiennent pas dans un atlas de 4096 px")
    aw, ah, placed = best

    atlas = Image.new("RGBA", (aw, ah), (0, 0, 0, 0))
    for tile in tiles:
        image = sources[tile["key"]]
        w, h = tile["size"]
        ru, rv = tile["reps"]
        # la tuile est taillee dans une repetition PLUS LARGE d'une image de
        # chaque cote : ses deux pixels de bord continuent donc le motif
        big = Image.new("RGBA", ((ru + 2) * w, (rv + 2) * h))
        for i in range(ru + 2):
            for j in range(rv + 2):
                big.paste(image, (i * w, j * h))
        piece = big.crop((w - BORDER, h - BORDER, w + ru * w + BORDER, h + rv * h + BORDER))
        alpha = piece.getchannel("A").point(lambda a: min(255, a * 2))
        piece.putalpha(alpha)
        atlas.paste(piece, placed[tile["key"]])
        tile["at"] = placed[tile["key"]]
    return atlas, tiles


# ------------------------------------------------------------------- ecriture

def write_bin(data, tiles, atlas_size):
    aw, ah = atlas_size
    by_key = {t["key"]: t for t in tiles}
    mn = [min(v["pos"][k] for tri in data["tris"] for v in tri["verts"]) for k in range(3)]
    mx = [max(v["pos"][k] for tri in data["tris"] for v in tri["verts"]) for k in range(3)]
    parts = [HEADER.pack(MAGIC, VERSION, len(data["tris"]), len(data["bones"]), aw, ah, *mn, *mx)]
    for bone in data["bones"]:
        name = bone["name"].encode("ascii", "replace")[:24]
        parts.append(BONE.pack(name, bone["parent"], 0, *bone["pivot"]))
    for tri in data["tris"]:
        tile = by_key[tri["image"]]
        x0, y0 = tile["at"]
        w, h = tile["size"]
        fields = [tri["bone"], FLAG_BLEND if tri["blend"] else 0, 0]
        for v, (lu, lv) in zip(tri["verts"], tri["local"]):
            fields.extend(v["pos"])
            fields.append((x0 + BORDER + lu * w) / aw)
            fields.append((y0 + BORDER + lv * h) / ah)
            fields.extend(v["normal"])
            fields.extend(v["rgba"])
        parts.append(TRI.pack(*fields))
    path = os.path.join(BIN_DIR, "%s.bin" % data["model"])
    with open(path, "wb") as handle:
        handle.write(b"".join(parts))
    return path, mn, mx


def read_bin(path):
    """Relit un .bin comme le fera le mod : l'en-tete, les os, les triangles."""
    raw = open(path, "rb").read()
    magic, version, count, bone_count, aw, ah, *box = HEADER.unpack_from(raw, 0)
    if magic != MAGIC or version != VERSION:
        sys.exit("%s : magie %r version %d" % (path, magic, version))
    expected = HEADER.size + bone_count * BONE.size + count * TRI.size
    if len(raw) != expected:
        sys.exit("%s : %d octets, %d attendus" % (path, len(raw), expected))
    off = HEADER.size
    bones = []
    for _ in range(bone_count):
        name, parent, _r, px, py, pz = BONE.unpack_from(raw, off)
        bones.append((name.rstrip(b"\0").decode("ascii"), parent, (px, py, pz)))
        off += BONE.size
    tris = []
    for _ in range(count):
        f = TRI.unpack_from(raw, off)
        off += TRI.size
        verts = []
        for q in range(3):
            b = 3 + q * 12
            verts.append({"pos": f[b:b + 3], "uv": f[b + 3:b + 5], "normal": f[b + 5:b + 8], "rgba": f[b + 8:b + 12]})
        tris.append({"bone": f[0], "flags": f[1], "verts": verts})
    return {"atlas": (aw, ah), "min": box[:3], "max": box[3:], "bones": bones, "tris": tris}


# ---------------------------------------------------------------- controles

def check_against_glb(data, back, atlas, tiles):
    """Compare chaque triangle relu a sa source : position, couleur, texel central."""
    aw, ah = atlas.size
    pixels = atlas.tobytes()
    by_key = {t["key"]: t for t in tiles}
    sources = data["images"]
    bad_pos = bad_col = bad_texel = outside = 0
    for src, got in zip(data["tris"], back["tris"]):
        for a, b in zip(src["verts"], got["verts"]):
            if max(abs(a["pos"][k] - b["pos"][k]) for k in range(3)) > 1e-4:
                bad_pos += 1
            if tuple(a["rgba"]) != tuple(b["rgba"]):
                bad_col += 1
        # texel sous le centre du triangle : dans l'image d'origine, en UV
        # brutes repetees a la PS2 ; dans l'atlas, en UV relues du .bin
        image = sources[src["image"]]
        w, h = image.size
        cu = sum(u for u, _ in src["raw_uv"]) / 3.0
        cv = sum(v for _, v in src["raw_uv"]) / 3.0
        want = image.getpixel((int(math.floor(cu * w)) % w, int(math.floor(cv * h)) % h))
        au = sum(v["uv"][0] for v in got["verts"]) / 3.0
        av = sum(v["uv"][1] for v in got["verts"]) / 3.0
        tx = min(aw - 1, int(au * aw))
        ty = min(ah - 1, int(av * ah))
        i = (ty * aw + tx) * 4
        have = tuple(pixels[i:i + 4])
        if have[:3] != tuple(want[:3]) or have[3] != min(255, want[3] * 2):
            bad_texel += 1
        # chaque sommet doit rester dans l'interieur de SA tuile
        tile = by_key[src["image"]]
        x0, y0 = tile["at"]
        ru, rv = tile["reps"]
        tw, th = tile["size"]
        for v in got["verts"]:
            px = v["uv"][0] * aw
            py = v["uv"][1] * ah
            if not (x0 + BORDER - 0.01 <= px <= x0 + BORDER + ru * tw + 0.01
                    and y0 + BORDER - 0.01 <= py <= y0 + BORDER + rv * th + 0.01):
                outside += 1
    return bad_pos, bad_col, bad_texel, outside


VIEWS = [("profil (+x vers la camera ; z -> droite, y -> haut)", 2, 1, 0),
         ("dessus (y vers la camera ; z -> droite, x -> haut)", 2, 0, 1),
         ("face (z vers la camera ; x -> droite, y -> haut)", 0, 1, 2)]


def render_view(back, atlas, view, scale, pad=20):
    """Une vue orthographique texturee, au tampon de profondeur, pixel par pixel.

    Memes axes et meme ombrage que les rendus de l'etude (veh_skin.py), pour
    pouvoir les poser cote a cote ; mais la texture est lue a CHAQUE pixel,
    dans l'atlas, avec les UV du .bin : un decalage d'un texel s'y voit.
    """
    title, ax_u, ax_v, ax_d = view
    mn, mx = back["min"], back["max"]
    width = int((mx[ax_u] - mn[ax_u]) * scale) + 2 * pad
    height = int((mx[ax_v] - mn[ax_v]) * scale) + 2 * pad + 14
    aw, ah = atlas.size
    texels = atlas.tobytes()
    depth = [-1e30] * (width * height)
    out = bytearray(bytes((40, 44, 52)) * (width * height))
    for tri in back["tris"]:
        v = tri["verts"]
        sx = [pad + (p["pos"][ax_u] - mn[ax_u]) * scale for p in v]
        sy = [14 + pad + (mx[ax_v] - p["pos"][ax_v]) * scale for p in v]
        sd = [p["pos"][ax_d] for p in v]
        area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0])
        if abs(area) < 1e-9:
            continue
        p0, p1, p2 = (x["pos"] for x in v)
        e1 = [p1[k] - p0[k] for k in range(3)]
        e2 = [p2[k] - p0[k] for k in range(3)]
        n = normalize((e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]))
        shade = 0.55 + 0.45 * abs(n[ax_d])
        x_lo = max(0, int(math.floor(min(sx))))
        x_hi = min(width - 1, int(math.ceil(max(sx))))
        y_lo = max(0, int(math.floor(min(sy))))
        y_hi = min(height - 1, int(math.ceil(max(sy))))
        inv = 1.0 / area
        for py in range(y_lo, y_hi + 1):
            cy = py + 0.5
            for px in range(x_lo, x_hi + 1):
                cx = px + 0.5
                w0 = ((sx[1] - cx) * (sy[2] - cy) - (sx[2] - cx) * (sy[1] - cy)) * inv
                w1 = ((sx[2] - cx) * (sy[0] - cy) - (sx[0] - cx) * (sy[2] - cy)) * inv
                w2 = 1.0 - w0 - w1
                if w0 < -1e-6 or w1 < -1e-6 or w2 < -1e-6:
                    continue
                d = w0 * sd[0] + w1 * sd[1] + w2 * sd[2]
                cell = py * width + px
                if d <= depth[cell]:
                    continue
                u = w0 * v[0]["uv"][0] + w1 * v[1]["uv"][0] + w2 * v[2]["uv"][0]
                t = w0 * v[0]["uv"][1] + w1 * v[1]["uv"][1] + w2 * v[2]["uv"][1]
                i = (min(ah - 1, max(0, int(t * ah))) * aw + min(aw - 1, max(0, int(u * aw)))) * 4
                if texels[i + 3] < CUTOFF * 255:
                    continue
                depth[cell] = d
                o = cell * 3
                for k in range(3):
                    c = w0 * v[0]["rgba"][k] + w1 * v[1]["rgba"][k] + w2 * v[2]["rgba"][k]
                    out[o + k] = min(255, int(texels[i + k] * c / 255.0 * shade))
    image = Image.frombytes("RGB", (width, height), bytes(out))
    draw = ImageDraw.Draw(image)
    for k in range(int(mx[ax_u] - mn[ax_u]) + 1):
        x = pad + k * scale
        draw.line([(x, height - 6), (x, height - 1)], fill=(255, 255, 0))
    draw.text((4, 1), title, fill=(230, 230, 230))
    return image


def render_sheet(model, back, atlas, scale):
    panels = [render_view(back, atlas, view, scale) for view in VIEWS]
    width = sum(p.width for p in panels) + 10 * (len(panels) - 1)
    height = max(p.height for p in panels) + 16
    sheet = Image.new("RGB", (width, height), (20, 22, 26))
    x = 0
    for panel in panels:
        sheet.paste(panel, (x, 16))
        x += panel.width + 10
    mn, mx = back["min"], back["max"]
    ImageDraw.Draw(sheet).text((4, 2), "%s.bin + atlas.png  taille %.2f x %.2f x %.2f m (x,y,z) ; %d px/m ; traits jaunes = 1 m"
                               % (model, mx[0] - mn[0], mx[1] - mn[1], mx[2] - mn[2], scale), fill=(255, 255, 255))
    return sheet


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-png", action="store_true", help="ne pas dessiner les images de controle")
    parser.add_argument("--reference", help="dossier des rendus de l'etude (veh_<modele>-lod0.png), poses au-dessus")
    parser.add_argument("--detail", type=int, default=100, help="echelle de la planche de detail, en px/m (0 : aucune)")
    args = parser.parse_args()

    os.makedirs(BIN_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(ATLAS_PATH), exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)

    models = [read_model(m) for m in MODELS]
    atlas, tiles = build_atlas(models)
    atlas.save(ATLAS_PATH)
    atlas = Image.open(ATLAS_PATH).convert("RGBA")  # relue : c'est elle que le mod verra
    print("atlas %d x %d, %d tuiles -> %s" % (atlas.size[0], atlas.size[1], len(tiles), ATLAS_PATH))
    used = sum(t["outer"][0] * t["outer"][1] for t in tiles)
    print("  remplissage %.1f %%" % (100.0 * used / (atlas.size[0] * atlas.size[1])))
    for tile in sorted(tiles, key=lambda t: t["key"]):
        print("  %-24s %4d x %-4d repetee %d x %d  en %s" % (tile["key"], tile["size"][0], tile["size"][1],
                                                        tile["reps"][0], tile["reps"][1], tile["at"]))
    with open(os.path.join(OUT_DIR, "atlas.json"), "w", encoding="utf-8") as handle:
        json.dump({"size": atlas.size, "border": BORDER,
                   "tiles": [{k: t[k] for k in ("key", "size", "reps", "at")} for t in tiles]}, handle, indent=1)

    failed = False
    for data in models:
        path, mn, mx = write_bin(data, tiles, atlas.size)
        back = read_bin(path)
        blend = sum(1 for t in back["tris"] if t["flags"] & FLAG_BLEND)
        bones = Counter(t["bone"] for t in back["tris"])
        print("%s : %d triangles, %d sommets cites, %d os, %d BLEND, %d octets -> %s"
              % (data["model"], len(back["tris"]), data["cited"], len(back["bones"]), blend,
                 os.path.getsize(path), path))
        print("  boite min (%.3f, %.3f, %.3f) max (%.3f, %.3f, %.3f) ; taille %.2f x %.2f x %.2f m"
              % (*mn, *mx, mx[0] - mn[0], mx[1] - mn[1], mx[2] - mn[2]))
        print("  ecart du squelette au repos : %.6f m" % data["drift"])
        print("  triangles par os : %s" % ", ".join("%s %d" % (back["bones"][b][0], n)
                                                   for b, n in sorted(bones.items())))
        bad_pos, bad_col, bad_texel, outside = check_against_glb(data, back, atlas, tiles)
        print("  relecture : %d positions, %d couleurs, %d texels centraux differents ; %d sommets hors de leur tuile"
              % (bad_pos, bad_col, bad_texel, outside))
        failed |= bool(bad_pos or bad_col or bad_texel or outside)

        if args.no_png:
            continue
        sheet = render_sheet(data["model"], back, atlas, 40)
        if args.reference:
            ref_path = os.path.join(args.reference, "veh_%s-lod0.png" % data["model"])
            if os.path.isfile(ref_path):
                ref = Image.open(ref_path).convert("RGB")
                both = Image.new("RGB", (max(ref.width, sheet.width), ref.height + sheet.height + 8), (0, 0, 0))
                both.paste(ref, (0, 0))
                both.paste(sheet, (0, ref.height + 8))
                sheet = both
        out = os.path.join(OUT_DIR, "%s.png" % data["model"])
        sheet.save(out)
        print("  controle -> %s" % out)
        if args.detail:
            out = os.path.join(OUT_DIR, "%s-detail.png" % data["model"])
            render_sheet(data["model"], back, atlas, args.detail).save(out)
            print("  detail -> %s" % out)

    if failed:
        sys.exit("la relecture ne redonne pas la source : voir les compteurs ci-dessus")


if __name__ == "__main__":
    main()
