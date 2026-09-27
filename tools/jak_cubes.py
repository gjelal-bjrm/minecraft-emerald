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
   couleur et de meme os (le « maillage glouton ») : bien moins de triangles.

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
FOLDER = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons", "jak_gun")
ATLAS = os.path.join(FOLDER, "morph_gun.png")
OUT = os.path.join(FOLDER, "cubes")
PREVIEW = os.path.join(ROOT, "build", "jak", "cubes")

# le format de jak_gun.py (repris ici : importer jak_gun tirerait les outils d'extraction)
MAGIC = b"JKGN"
VERSION = 1
HEADER = struct.Struct("<4sIIIIIIIif3f3f")
BONE = struct.Struct("<32shH10f12f")
TRI = struct.Struct("<BBH" + "B3x3f2f3f4B" * 3)
FLAG_BLEND = 1
assert HEADER.size == 64 and BONE.size == 124 and TRI.size == 124

# les six faces d'un cube : direction, puis les deux axes du plan (u, v) et le cote
FACES = [((1, 0, 0), 1, 2), ((-1, 0, 0), 1, 2), ((0, 1, 0), 0, 2),
         ((0, -1, 0), 0, 2), ((0, 0, 1), 0, 1), ((0, 0, -1), 0, 1)]


def read_model(path):
    raw = open(path, "rb").read()
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
    return {"raw": raw, "head": head, "start": start, "end": start + count * TRI.size, "tris": tris}


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
    step = size / 3.0
    keys, rgbs, blends = [], [], []
    holes = 0
    for tri in model["tris"]:
        p = [v["pos"] for v in tri["verts"]]
        uv = [v["uv"] for v in tri["verts"]]
        col = [v["rgba"] for v in tri["verts"]]
        blend = bool(tri["flags"] & FLAG_BLEND)
        edge = max(math.dist(p[0], p[1]), math.dist(p[1], p[2]), math.dist(p[2], p[0]))
        n = max(1, int(math.ceil(edge / step)))
        for i in range(n + 1):
            for j in range(n + 1 - i):
                a, b = i / n, j / n
                c = 1.0 - a - b
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


def palette_of(keys, rgbs, blends, colors, gain=1.0):
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
    for key, rgb, blend in zip(keys, rgbs, blends):
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
    color = {key: nearest((acc[0] / acc[3], acc[1] / acc[3], acc[2] / acc[3])) for key, acc in sums.items()}
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
            fixed[(bone, i, j, k)] = around.most_common(1)[0][0]
            lonely += 1
    color.update(fixed)
    out = {}
    for key, index in color.items():
        see_through = translucent[key] * 2 > sums[key][3]
        r, g, b = (min(255, int(round(c * gain))) for c in swatches[index])
        out[key] = (r, g, b, 160 if see_through else 255, see_through)
    return out, lonely


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
    tris = []
    for quad in quads:
        c = quad["corners"]
        r, g, b, a, translucent = quad["look"]
        for tri in ((c[0], c[1], c[2]), (c[0], c[2], c[3])):
            fields = [quad["bone"], FLAG_BLEND if translucent else 0, 0]
            for corner in tri:
                fields.append(quad["bone"])
                fields.extend(corner)
                fields.extend(white)
                fields.extend(quad["normal"])
                fields.extend((r, g, b, a))
            tris.append(TRI.pack(*fields))
    points = [corner for quad in quads for corner in quad["corners"]]
    mn = [min(p[k] for p in points) for k in range(3)]
    mx = [max(p[k] for p in points) for k in range(3)]
    head = model["head"]
    header = HEADER.pack(head[0], head[1], len(tris), head[3], head[4], head[5], head[6], head[7], head[8],
                         head[9], *mn, *mx)
    raw = model["raw"]
    body = raw[HEADER.size:model["start"]] + b"".join(tris) + raw[model["end"]:]
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
    parser.add_argument("--palette", type=int, default=16, help="nombre de couleurs")
    parser.add_argument("--gain", type=float, default=1.3, help="eclaircissement de la palette")
    parser.add_argument("--apercu", action="store_true", help="dessiner les vues de controle")
    args = parser.parse_args()
    cubes = args.cube or [16]
    model = read_model(os.path.join(FOLDER, args.model + ".bin"))
    atlas = Image.open(ATLAS).convert("RGBA")
    white = white_texel(atlas)
    print("%s : %d triangles de Jak 3" % (args.model, len(model["tris"])))
    if args.apercu:
        draw(original_tris(model, atlas), "%s : le modele de Jak 3" % args.model,
             os.path.join(PREVIEW, args.model + "_jak.png"))
    for cube in cubes:
        keys, rgbs, blends, holes = sample(model, atlas, cube)
        voxels, lonely = palette_of(keys, rgbs, blends, args.palette, args.gain)
        quads = greedy_faces(voxels, cube)
        path = os.path.join(OUT, "%s_c%d.bin" % (args.model, cube))
        count, mn, mx = write_model(model, quads, white, path)
        bones = Counter(key[0] for key in voxels)
        print("  1/%d de bloc : %d cubes sur %d os (%d points isoles repeints), %d rectangles, %d triangles, "
              "%.2f x %.2f x %.2f blocs -> %s"
              % (cube, len(voxels), len(bones), lonely, len(quads), count, mx[0] - mn[0], mx[1] - mn[1],
                 mx[2] - mn[2], os.path.relpath(path, ROOT)))
        if args.apercu:
            back = read_model(path)
            tris = []
            for tri in back["tris"]:
                pts = [v["pos"] for v in tri["verts"]]
                rgb = tri["verts"][0]["rgba"][:3]
                e1 = [pts[1][k] - pts[0][k] for k in range(3)]
                e2 = [pts[2][k] - pts[0][k] for k in range(3)]
                n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
                ln = math.sqrt(sum(c * c for c in n)) or 1.0
                tris.append((pts, tuple(abs(c / ln) for c in n), rgb))
            draw(tris, "%s : cubes de 1/%d de bloc, %d couleurs" % (args.model, cube, args.palette),
                 os.path.join(PREVIEW, "%s_c%d.png" % (args.model, cube)))


if __name__ == "__main__":
    main()
