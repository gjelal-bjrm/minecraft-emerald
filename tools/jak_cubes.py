"""Met un modele de Jak 3 en cubes, a la maniere de Minecraft (cahier §109).

Le joueur, 27 sept. : « on a les voitures originales du jeu Jak, pareil pour l'arme, et pour toutes
les armes de Jak 3, et pour le JET-Board : est-ce que ce serait difficile de les faire en version
Minecraft ? Je trouve que ca deconnecte un petit peu de la realite du jeu, qui est cense etre
cubique. »

L'outil relit un .bin cuit par jak_gun.py, TEL QUE LE MOD LE LIT, et en fait des cubes :

1. ECHANTILLONNER. Chaque triangle est parcouru en points serres (un tiers de cube) ; en chaque
   point, la couleur que le mod dessinerait : le texel de l'atlas multiplie par la couleur de
   sommet (deja doublee, convention PS2). Un texel transparent (le decoupage « cutout ») ne compte
   pas : c'est un trou du modele.
2. RANGER CES POINTS DANS DES CUBES de 1/N bloc (N = 16 : le pixel d'un bloc de Minecraft).
3. REDUIRE LA PALETTE (16 couleurs par defaut), sur les POINTS ; chaque cube prend la moyenne des
   siens, RAMENEE a la couleur la plus proche de la palette. Une moyenne brute melangeait les details
   de la texture en taches boueuses ; la couleur la plus frequente tombait sur les jointures sombres
   (en jeu, deux fois plus sombre que Jak). La palette est eclaircie (gain 1,3) : les faces droites
   d'un cube prennent moins de lumiere que les pentes du modele. Enfin, un cube dont aucun voisin ne
   partage la couleur prend celle de ses voisins : les points isoles s'en vont, les lignes fines
   restent.
4. NE GARDER QUE LES FACES A L'AIR LIBRE, et fusionner en rectangles les faces voisines de meme
   couleur et de meme os (le « maillage glouton ») : bien moins de triangles. Un rectangle s'ecrit en
   UN enregistrement (drapeau 4, trois coins ; le mod deduit le quatrieme) : moitie moins de sommets
   a dessiner a chaque image.
5. POUR LES VEHICULES, remplir ce que l'air du dehors n'atteint pas (l'interieur d'une carrosserie :
   ses parois cachees ne comptent plus), et lisser les couleurs (un cube prend celle d'au moins quatre
   de ses six voisins) : les grandes faces deviennent unies, donc de grands rectangles.

LES OS RESTENT. La grille est faite OS PAR OS : chaque cube appartient a l'os du triangle qui l'a
fait naitre. Les pieces qui bougent (ailerons de la planche, formes du Morph Gun) bougent avec leurs
cubes, et deux pieces rangees l'une dans l'autre au repos ne se melangent pas. Les os, les poses et
les transformations du .bin d'origine sont recopies octet pour octet.

LA COULEUR EST DANS LE SOMMET. Chaque face vise un texel BLANC de l'atlas et porte sa couleur en
couleur de sommet : le dessin du mod (texture x couleur de sommet) la rend telle quelle, sans atlas
nouveau ni code nouveau.

Sortie : src/main/resources/assets/emeraldweapons/jak_gun/cubes/<modele>_c<N>.bin, que le mod lit a
la place du modele de Jak quand EMERALDWEAPONS_JAK_CUBES=<N> (essai, JakGunModel.load ; le joueur a
choisi 1/16, le 27 sept.) ; et, avec
--apercu, une image de controle dessinee depuis le .bin ecrit (build/jak/cubes/).

Usage :
    python tools/jak_cubes.py jet_board --cube 16 --apercu
    python tools/jak_cubes.py jet_board --cube 16 --palette 24 --gain 1.4
"""

import argparse
import math
import os
import struct
import sys
from collections import Counter, defaultdict

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow est necessaire")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons")
FOLDER = os.path.join(ASSETS, "jak_gun")
ATLAS = os.path.join(FOLDER, "morph_gun.png")
OUT = os.path.join(FOLDER, "cubes")
VEHICLES = os.path.join(ASSETS, "jak_vehicles")
VEHICLE_ATLAS = os.path.join(ASSETS, "textures", "entity", "jak_vehicles", "atlas.png")
PREVIEW = os.path.join(ROOT, "build", "jak", "cubes")

# le format de jak_gun.py, JKGN (repris ici : importer jak_gun tirerait les outils d'extraction)
MAGIC = b"JKGN"
VERSION = 1
HEADER = struct.Struct("<4sIIIIIIIif3f3f")
BONE = struct.Struct("<32shH10f12f")
TRI = struct.Struct("<BBH" + "B3x3f2f3f4B" * 3)
FLAG_BLEND = 1
# un rectangle en un seul enregistrement : trois coins, le mod en deduit le quatrieme (c0 + c2 - c1)
FLAG_QUAD = 4
assert HEADER.size == 64 and BONE.size == 124 and TRI.size == 124
# celui de jak_vehicle.py, JKVH : les voitures et les motos, sans os par sommet ni poses. Leur atlas
# n'a pas de texel blanc : les cubes des vehicules disent un atlas de 16 x 16, et le rendu leur donne
# alors la texture blanche des cubes (textures/entity/jak_cubes.png).
VH_MAGIC = b"JKVH"
VH_HEADER = struct.Struct("<4sIIIII3f3f")
VH_BONE = struct.Struct("<24shH3f")
VH_TRI = struct.Struct("<BBH" + "3f2f3f4B" * 3)
assert VH_HEADER.size == 48 and VH_BONE.size == 40 and VH_TRI.size == 112
CUBE_ATLAS = 16

# les six faces d'un cube : direction, puis les deux axes du plan (u, v) et le cote
FACES = [((1, 0, 0), 1, 2), ((-1, 0, 0), 1, 2), ((0, 1, 0), 0, 2),
         ((0, -1, 0), 0, 2), ((0, 0, 1), 0, 1), ((0, 0, -1), 0, 1)]


def read_model(path):
    raw = open(path, "rb").read()
    if raw[:4] == VH_MAGIC:
        head = list(VH_HEADER.unpack_from(raw, 0))
        count, bones = head[2], head[3]
        start = VH_HEADER.size + bones * VH_BONE.size
        tris = []
        for t in range(count):
            f = VH_TRI.unpack_from(raw, start + t * VH_TRI.size)
            verts = []
            for q in range(3):
                b = 3 + q * 12
                verts.append({"bone": f[0], "pos": f[b:b + 3], "uv": f[b + 3:b + 5], "rgba": f[b + 8:b + 12]})
            tris.append({"bone": f[0], "flags": f[1], "verts": verts})
        return {"kind": "JKVH", "raw": raw, "head": head, "start": start, "end": start + count * VH_TRI.size,
                "tris": tris}
    head = list(HEADER.unpack_from(raw, 0))
    if head[0] != MAGIC or head[1] != VERSION:
        sys.exit("%s : magie %r version %d" % (path, head[0], head[1]))
    count, bones = head[2], head[3]
    start = HEADER.size + bones * BONE.size
    tris = []
    for t in range(count):
        f = TRI.unpack_from(raw, start + t * TRI.size)
        verts = []
        for q in range(3):
            b = 3 + q * 13
            verts.append({"bone": f[b], "pos": f[b + 1:b + 4], "uv": f[b + 4:b + 6], "rgba": f[b + 9:b + 13]})
        tris.append({"bone": f[0], "flags": f[1], "verts": verts})
    return {"kind": "JKGN", "raw": raw, "head": head, "start": start, "end": start + count * TRI.size, "tris": tris}


def white_texel(atlas):
    """Le centre d'un carre de 5 x 5 texels blancs opaques : la face y lit un blanc sans voisin."""
    w, h = atlas.size
    px = atlas.load()
    for y in range(2, h - 2):
        for x in range(2, w - 2):
            if all(px[x + dx, y + dy][3] == 255 and min(px[x + dx, y + dy][:3]) >= 250
                   for dx in range(-2, 3) for dy in range(-2, 3)):
                return (x + 0.5) / w, (y + 0.5) / h
    sys.exit("pas de texel blanc dans l'atlas")


def sample(model, atlas, cube):
    """Les points de chaque triangle : leur cube (os, i, j, k), leur couleur, s'ils sont translucides."""
    w, h = atlas.size
    px = atlas.load()
    size = 1.0 / cube
    step = size / 4.0
    keys, rgbs, blends = [], [], []
    holes = 0
    for tri in model["tris"]:
        verts = tri["verts"]
        # LE COTE LE PLUS COURT D'ABORD : on part d'une de ses extremites, et le pas suit chaque cote.
        # Un pas unique, sur le plus long cote, couvrait une languette de 4 m sur 5 cm comme un
        # triangle plein -- des millions de points pour une voiture, un quart d'heure de calcul.
        order = min(((0, 1, 2), (1, 2, 0), (2, 0, 1)),
                    key=lambda o: math.dist(verts[o[0]]["pos"], verts[o[1]]["pos"]))
        verts = [verts[order[0]], verts[order[1]], verts[order[2]]]
        p = [v["pos"] for v in verts]
        uv = [v["uv"] for v in verts]
        col = [v["rgba"] for v in verts]
        blend = bool(tri["flags"] & FLAG_BLEND)
        n1 = max(1, int(math.ceil(math.dist(p[0], p[1]) / step)))
        n2 = max(1, int(math.ceil(max(math.dist(p[0], p[2]), math.dist(p[1], p[2])) / step)))
        for i in range(n1 + 1):
            b = i / n1                                  # le long du cote court, de p0 vers p1
            for j in range(int((1.0 - b) * n2 + 1e-9) + 1):
                c = j / n2                              # vers p2
                a = 1.0 - b - c
                x = a * p[0][0] + b * p[1][0] + c * p[2][0]
                y = a * p[0][1] + b * p[1][1] + c * p[2][1]
                z = a * p[0][2] + b * p[1][2] + c * p[2][2]
                u = a * uv[0][0] + b * uv[1][0] + c * uv[2][0]
                v = a * uv[0][1] + b * uv[1][1] + c * uv[2][1]
                tr, tg, tb, ta = px[min(w - 1, max(0, int(u * w))), min(h - 1, max(0, int(v * h)))]
                if not blend and ta < 128:
                    holes += 1
                    continue                    # un trou du decoupage : rien a cet endroit
                vr = a * col[0][0] + b * col[1][0] + c * col[2][0]
                vg = a * col[0][1] + b * col[1][1] + c * col[2][1]
                vb = a * col[0][2] + b * col[1][2] + c * col[2][2]
                keys.append((tri["bone"], math.floor(x / size), math.floor(y / size), math.floor(z / size)))
                rgbs.append((int(min(255.0, tr * vr / 255.0)), int(min(255.0, tg * vg / 255.0)),
                             int(min(255.0, tb * vb / 255.0))))
                blends.append(blend)
    return keys, rgbs, blends, holes


def saturation(rgb):
    return max(rgb) - min(rgb)


def palette_of(keys, rgbs, blends, colors, gain=1.0, smooth=0):
    """
    La couleur de chaque cube. La palette se fait sur les POINTS ; chaque cube prend la MOYENNE de
    ses points, ramenee a la couleur la plus proche de la palette : franche, et juste en clarte. (La
    couleur la plus frequente, essayee avant, tombait souvent sur le trait sombre d'une jointure : en
    jeu, la planche sortait deux fois plus sombre que celle de Jak.) Le gain eclaircit ensuite la
    palette : une face de cube est droite, la ou le modele de Jak a des pentes tournees vers le ciel,
    et le pack de shaders ombre les creux entre les cubes. Enfin, un cube dont aucun voisin ne
    partage la couleur prend celle de ses voisins -- les points isoles s'en vont, les lignes fines
    (les traits d'eco bleu) restent.
    """
    strip = Image.new("RGB", (len(rgbs), 1))
    strip.putdata(rgbs)
    reduced = strip.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    table = reduced.getpalette()
    indices = reduced.get_flattened_data() if hasattr(reduced, "get_flattened_data") else reduced.getdata()
    sums = defaultdict(lambda: [0, 0, 0, 0])
    translucent = Counter()
    votes = defaultdict(Counter)
    for key, rgb, blend, index in zip(keys, rgbs, blends, indices):
        votes[key][index] += 1
        acc = sums[key]
        acc[0] += rgb[0]
        acc[1] += rgb[1]
        acc[2] += rgb[2]
        acc[3] += 1
        if blend:
            translucent[key] += 1
    swatches = [(table[i * 3], table[i * 3 + 1], table[i * 3 + 2]) for i in range(colors)]

    def nearest(rgb):
        return min(range(colors), key=lambda i: (swatches[i][0] - rgb[0]) ** 2 * 3 + (swatches[i][1] - rgb[1]) ** 2 * 4
                   + (swatches[i][2] - rgb[2]) ** 2 * 2)
    def pick(key, acc):
        # UNE COULEUR VIVE QUI COUVRE LE TIERS DU CUBE L'EMPORTE : la moyenne delavait le violet d'un
        # chargeur en gris rose ; sinon, la moyenne ramenee a la palette
        index, count = votes[key].most_common(1)[0]
        if saturation(swatches[index]) >= 60 and count * 10 >= acc[3] * 3:
            return index
        return nearest((acc[0] / acc[3], acc[1] / acc[3], acc[2] / acc[3]))
    color = {key: pick(key, acc) for key, acc in sums.items()}
    lonely = 0
    fixed = {}
    for (bone, i, j, k), own in color.items():
        around = Counter()
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if dx or dy or dz:
                        other = color.get((bone, i + dx, j + dy, k + dz))
                        if other is not None:
                            around[other] += 1
        if around and around[own] == 0:
            other = around.most_common(1)[0][0]
            # UN VOYANT N'EST PAS UN POINT ISOLE : une couleur vive au milieu du gris (les lumieres et
            # les chargeurs colores du Morph Gun) reste ; seule une teinte proche est repeinte
            if saturation(swatches[own]) >= 60 and saturation(swatches[own]) - saturation(swatches[other]) >= 30:
                continue
            fixed[(bone, i, j, k)] = other
            lonely += 1
    color.update(fixed)
    # LE LISSAGE (les vehicules) : un cube prend la couleur que partagent au moins quatre de ses six
    # voisins de face. Les grandes faces d'une carrosserie deviennent unies, et le maillage glouton en
    # fait de grands rectangles : la voiture passe de 80 000 triangles a quelques milliers.
    for _ in range(smooth):
        fixed = {}
        for (bone, i, j, k), own in color.items():
            around = Counter()
            for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                other = color.get((bone, i + d[0], j + d[1], k + d[2]))
                if other is not None:
                    around[other] += 1
            if around:
                best, count = around.most_common(1)[0]
                if best != own and count >= 4:
                    fixed[(bone, i, j, k)] = best
        color.update(fixed)
    out = {}
    for key, index in color.items():
        see_through = translucent[key] * 2 > sums[key][3]
        r, g, b = (min(255, int(round(c * gain))) for c in swatches[index])
        out[key] = (r, g, b, 160 if see_through else 255, see_through)
    return out, lonely


def fill_inside(voxels):
    """
    Les cubes que l'air du dehors n'atteint pas (six directions, os par os) : l'interieur d'une
    carrosserie, entre deux parois. Remplis, ils ne laissent plus de faces cachees dans le modele --
    la moitie des triangles d'une voiture. Un habitacle ouvert reste vide : l'air y entre.
    """
    by_bone = defaultdict(set)
    for (bone, i, j, k) in voxels:
        by_bone[bone].add((i, j, k))
    added = {}
    for bone, cells in by_bone.items():
        lo = [min(c[a] for c in cells) - 1 for a in range(3)]
        hi = [max(c[a] for c in cells) + 1 for a in range(3)]
        outside = {tuple(lo)}
        stack = [tuple(lo)]
        while stack:
            x, y, z = stack.pop()
            for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                n = (x + d[0], y + d[1], z + d[2])
                if (lo[0] <= n[0] <= hi[0] and lo[1] <= n[1] <= hi[1] and lo[2] <= n[2] <= hi[2]
                        and n not in cells and n not in outside):
                    outside.add(n)
                    stack.append(n)
        # la couleur d'un cube interieur ne se voit jamais : celle du premier cube de l'os
        any_look = voxels[(bone,) + next(iter(cells))]
        for x in range(lo[0] + 1, hi[0]):
            for y in range(lo[1] + 1, hi[1]):
                for z in range(lo[2] + 1, hi[2]):
                    if (x, y, z) not in cells and (x, y, z) not in outside:
                        added[(bone, x, y, z)] = any_look
    voxels.update(added)
    return len(added)


def greedy_faces(voxels, cube):
    """Les faces a l'air libre, fusionnees en rectangles de meme os et de meme couleur."""
    size = 1.0 / cube
    by_bone = defaultdict(dict)
    for (bone, i, j, k), look in voxels.items():
        by_bone[bone][(i, j, k)] = look
    quads = []
    for bone, grid in by_bone.items():
        for normal, ua, va in FACES:
            axis = next(n for n in range(3) if normal[n] != 0)
            # les faces visibles, rangees par couche (coordonnee sur l'axe de la normale)
            layers = defaultdict(dict)
            for cell, look in grid.items():
                nb = (cell[0] + normal[0], cell[1] + normal[1], cell[2] + normal[2])
                if nb in grid:
                    continue
                layers[cell[axis]][(cell[ua], cell[va])] = look
            for layer, faces in layers.items():
                done = set()
                for (u0, v0) in sorted(faces):
                    if (u0, v0) in done:
                        continue
                    look = faces[(u0, v0)]
                    u1 = u0
                    while (u1 + 1, v0) in faces and (u1 + 1, v0) not in done and faces[(u1 + 1, v0)] == look:
                        u1 += 1
                    v1 = v0
                    while all((u, v1 + 1) in faces and (u, v1 + 1) not in done and faces[(u, v1 + 1)] == look
                              for u in range(u0, u1 + 1)):
                        v1 += 1
                    for u in range(u0, u1 + 1):
                        for v in range(v0, v1 + 1):
                            done.add((u, v))
                    # le rectangle, en blocs : la face est au bord du cube, du cote de la normale
                    plane = (layer + (1 if normal[axis] > 0 else 0)) * size
                    corners = []
                    for cu, cv in ((u0, v0), (u1 + 1, v0), (u1 + 1, v1 + 1), (u0, v1 + 1)):
                        point = [0.0, 0.0, 0.0]
                        point[axis] = plane
                        point[ua] = cu * size
                        point[va] = cv * size
                        corners.append(tuple(point))
                    quads.append({"bone": bone, "normal": tuple(float(n) for n in normal), "corners": corners,
                                  "look": look})
    return quads


def write_model(model, quads, white, path):
    vehicle = model["kind"] == "JKVH"
    tris = []
    for quad in quads:
        c = quad["corners"]
        r, g, b, a, translucent = quad["look"]
        for tri in ((c[0], c[1], c[2]),):
            fields = [quad["bone"], FLAG_QUAD | (FLAG_BLEND if translucent else 0), 0]
            for corner in tri:
                if not vehicle:
                    fields.append(quad["bone"])
                fields.extend(corner)
                fields.extend(white)
                fields.extend(quad["normal"])
                fields.extend((r, g, b, a))
            tris.append((VH_TRI if vehicle else TRI).pack(*fields))
    points = [corner for quad in quads for corner in quad["corners"]]
    mn = [min(p[k] for p in points) for k in range(3)]
    mx = [max(p[k] for p in points) for k in range(3)]
    head = model["head"]
    if vehicle:
        header = VH_HEADER.pack(head[0], head[1], len(tris), head[3], CUBE_ATLAS, CUBE_ATLAS, *mn, *mx)
    else:
        header = HEADER.pack(head[0], head[1], len(tris), head[3], head[4], head[5], head[6], head[7], head[8],
                             head[9], *mn, *mx)
    raw = model["raw"]
    size = VH_HEADER.size if vehicle else HEADER.size
    body = raw[size:model["start"]] + b"".join(tris) + raw[model["end"]:]
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(header + body)
    return len(tris), mn, mx


def draw(tris_of, title, path, yaw=35.0, pitch=28.0, width=900, height=520):
    """Une vue de controle, en projection parallele, ombrée d'une lumiere fixe (peintre)."""
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        x = p[0] * cy - p[2] * sy
        z = p[0] * sy + p[2] * cy
        y = p[1] * cp - z * sp
        depth = p[1] * sp + z * cp
        return x, y, depth
    light = (0.45, 0.8, -0.4)
    norm = math.sqrt(sum(l * l for l in light))
    light = tuple(l / norm for l in light)
    polys = []
    for pts, normal, rgb in tris_of:
        v = [view(p) for p in pts]
        shade = 0.55 + 0.45 * max(0.0, sum(n * l for n, l in zip(normal, light)))
        polys.append((sum(q[2] for q in v) / len(v), [(q[0], q[1]) for q in v],
                      tuple(int(min(255, c * shade)) for c in rgb)))
    xs = [p[0] for _, pts, _ in polys for p in pts]
    ys = [p[1] for _, pts, _ in polys for p in pts]
    scale = min((width - 40) / max(1e-6, max(xs) - min(xs)), (height - 60) / max(1e-6, max(ys) - min(ys)))
    img = Image.new("RGB", (width, height), (38, 40, 46))
    d = ImageDraw.Draw(img)
    for _, pts, rgb in sorted(polys, key=lambda t: -t[0]):
        d.polygon([(20 + (x - min(xs)) * scale, height - 20 - (y - min(ys)) * scale) for x, y in pts], fill=rgb)
    d.text((10, 8), title, fill=(235, 235, 235))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def original_tris(model, atlas):
    w, h = atlas.size
    px = atlas.load()
    out = []
    for tri in model["tris"]:
        verts = tri["verts"]
        u = sum(v["uv"][0] for v in verts) / 3.0
        v = sum(v["uv"][1] for v in verts) / 3.0
        tr, tg, tb, ta = px[min(w - 1, int(u * w)), min(h - 1, int(v * h))]
        vc = [sum(x["rgba"][k] for x in verts) / 3.0 for k in range(3)]
        p = [x["pos"] for x in verts]
        e1 = [p[1][k] - p[0][k] for k in range(3)]
        e2 = [p[2][k] - p[0][k] for k in range(3)]
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        ln = math.sqrt(sum(c * c for c in n)) or 1.0
        n = tuple(abs(c / ln) for c in n)     # sans culling : la face se voit des deux cotes
        out.append((p, n, (tr * vc[0] / 255.0, tg * vc[1] / 255.0, tb * vc[2] / 255.0)))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("model", help="nom du .bin du dossier jak_gun (jet_board, morph_gun...)")
    parser.add_argument("--cube", type=int, action="append", help="cubes par bloc (16 : le pixel de Minecraft)")
    parser.add_argument("--palette", type=int, default=None,
                        help="nombre de couleurs (par defaut 16 ; 40 pour un modele a plus de vingt os, le Morph Gun)")
    parser.add_argument("--gain", type=float, default=1.3, help="eclaircissement de la palette")
    parser.add_argument("--lisse", type=int, default=None,
                        help="passes de lissage des couleurs (par defaut : 2 pour un vehicule, 0 sinon)")
    parser.add_argument("--apercu", action="store_true", help="dessiner les vues de controle")
    args = parser.parse_args()
    cubes = args.cube or [16]
    if os.path.exists(os.path.join(VEHICLES, args.model + ".bin")):
        model = read_model(os.path.join(VEHICLES, args.model + ".bin"))
        atlas = Image.open(VEHICLE_ATLAS).convert("RGBA")
        white = (0.5, 0.5)                      # le centre de la texture blanche des cubes
        out_dir = os.path.join(VEHICLES, "cubes")
    else:
        model = read_model(os.path.join(FOLDER, args.model + ".bin"))
        atlas = Image.open(ATLAS).convert("RGBA")
        white = white_texel(atlas)
        out_dir = OUT
    print("%s : %d triangles de Jak 3" % (args.model, len(model["tris"])))
    if args.apercu:
        draw(original_tris(model, atlas), "%s : le modele de Jak 3" % args.model,
             os.path.join(PREVIEW, args.model + "_jak.png"))
    for cube in cubes:
        keys, rgbs, blends, holes = sample(model, atlas, cube)
        smooth = args.lisse if args.lisse is not None else (2 if model["kind"] == "JKVH" else 0)
        # LE MORPH GUN et ses quarante-sept os : ses couleurs de famille (chargeurs rouges, jaunes, bleus,
        # violets) sont de petites pieces ; seize couleurs pour toute l'arme les noyaient dans le gris
        colors = args.palette or (40 if model["head"][3] > 20 else 16)
        voxels, lonely = palette_of(keys, rgbs, blends, colors, args.gain, smooth)
        inside = fill_inside(voxels)
        quads = greedy_faces(voxels, cube)
        path = os.path.join(out_dir, "%s_c%d.bin" % (args.model, cube))
        count, mn, mx = write_model(model, quads, white, path)
        bones = Counter(key[0] for key in voxels)
        print("  1/%d de bloc : %d cubes sur %d os (%d points isoles repeints, %d cubes interieurs, lissage %d), "
              "%d rectangles, %d triangles, %.2f x %.2f x %.2f blocs, %d Ko -> %s"
              % (cube, len(voxels), len(bones), lonely, inside, smooth, len(quads), count, mx[0] - mn[0],
                 mx[1] - mn[1], mx[2] - mn[2], os.path.getsize(path) // 1024, os.path.relpath(path, ROOT)))
        if args.apercu:
            back = read_model(path)
            tris = []
            for tri in back["tris"]:
                pts = [v["pos"] for v in tri["verts"]]
                if tri["flags"] & FLAG_QUAD:
                    pts = pts + [tuple(pts[0][k] + pts[2][k] - pts[1][k] for k in range(3))]
                rgb = tri["verts"][0]["rgba"][:3]
                e1 = [pts[1][k] - pts[0][k] for k in range(3)]
                e2 = [pts[2][k] - pts[0][k] for k in range(3)]
                n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
                ln = math.sqrt(sum(c * c for c in n)) or 1.0
                tris.append((pts, tuple(abs(c / ln) for c in n), rgb))
            draw(tris, "%s : cubes de 1/%d de bloc, %d couleurs" % (args.model, cube, colors),
                 os.path.join(PREVIEW, "%s_c%d.png" % (args.model, cube)))


if __name__ == "__main__":
    main()
