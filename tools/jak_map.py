"""Dessine le plan d'un niveau de Jak 3 a partir de sa geometrie de collision.

Pourquoi ce detour avant de voxeliser : on ne sait pas a quoi ressemble un
niveau tant qu'on ne l'a pas vu, et decider d'une echelle ou d'une palette a
l'aveugle coute bien plus cher qu'un apercu. Ce script rend deux images -- une
vue de dessus teintee par l'altitude, et une coupe -- a partir du seul fichier
de collision, qui est une soupe de triangles en coordonnees du monde.

La collision plutot que le decor : c'est deja la forme SOLIDE du niveau, celle
sur laquelle on marche et contre laquelle on bute. Le maillage decoratif porte
des biseaux et des courbes qui ne survivent pas a une grille de blocs, et pese
trente fois plus lourd.

Usage :
    python tools/jak_map.py CPO            # le Port de Haven
    python tools/jak_map.py WCA --cell 2   # Spargus, un bloc pour deux unites
"""

import argparse
import glob
import os
import sys

from PIL import Image

DECOMP = os.path.join(os.environ.get("USERPROFILE", ""), "Documents", "OpenGoal",
                      "active", "jak3", "data", "decompiler_out", "jak3")
COLLISION = os.path.join(DECOMP, "collision")

OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "build", "jak")


def find_obj(code):
    """Le fichier de collision d'un niveau, designe par son code de DGO."""
    hits = glob.glob(os.path.join(COLLISION, "collide-%s.DGO-*-collide.obj" % code))
    if not hits:
        sys.exit("aucune collision pour %s dans %s" % (code, COLLISION))
    return hits[0]


def read_triangles(path):
    """Les sommets et les faces, sans dependance : le format est trivial.

    Les indices OBJ commencent a 1, et peuvent etre negatifs (relatifs a la fin
    de la liste) -- ce second cas est rare mais il existe, et l'ignorer decale
    silencieusement toute la geometrie.
    """
    verts = []
    faces = []
    with open(path, "r") as handle:
        for line in handle:
            if line.startswith("v "):
                parts = line.split()
                verts.append((float(parts[1]), float(parts[2]), float(parts[3])))
            elif line.startswith("f "):
                idx = []
                for token in line.split()[1:]:
                    raw = int(token.split("/")[0])
                    idx.append(raw - 1 if raw > 0 else len(verts) + raw)
                for k in range(1, len(idx) - 1):
                    faces.append((idx[0], idx[k], idx[k + 1]))
    return verts, faces


def rasterise(verts, faces, cell, bounds=None, grid=None, dims=None):
    """La hauteur maximale par cellule : une carte de relief vue de dessus.

    Chaque triangle est REMPLI, par coordonnees barycentriques. Une premiere
    version se contentait d'echantillonner les sommets : sur une grille de
    douze cents cellules de cote, cela ne remplissait que trois pour cent de
    l'image, et le plan ne montrait rien.

    `bounds` et `grid` permettent d'accumuler PLUSIEURS niveaux dans le meme
    repere. Les coordonnees de la collision sont absolues dans le monde du
    jeu : deux quartiers voisins se placent donc cote a cote tout seuls, ce qui
    est le seul moyen de voir a quoi ressemble la ville entiere.
    """
    if bounds is None:
        xs = [v[0] for v in verts]
        zs = [v[2] for v in verts]
        bounds = (min(xs), min(zs), max(xs), max(zs))
    minx, minz, maxx, maxz = bounds
    if dims is None:
        w = int((maxx - minx) / cell) + 1
        h = int((maxz - minz) / cell) + 1
    else:
        w, h = dims
    if grid is None:
        grid = [[None] * w for _ in range(h)]
    for tri in faces:
        pts = [verts[i] for i in tri]
        cols = [(p[0] - minx) / cell for p in pts]
        rows = [(p[2] - minz) / cell for p in pts]
        c0, c1 = int(min(cols)), int(max(cols)) + 1
        r0, r1 = int(min(rows)), int(max(rows)) + 1
        # l'aire signee du triangle en coordonnees de grille : elle sert de
        # denominateur aux coordonnees barycentriques, et vaut zero pour un
        # triangle degenere -- un mur parfaitement vertical, vu de dessus, en
        # est un, et il y en a beaucoup dans une collision
        area = ((cols[1] - cols[0]) * (rows[2] - rows[0])
                - (cols[2] - cols[0]) * (rows[1] - rows[0]))
        if abs(area) < 1e-9:
            # on le trace quand meme, en segment : c'est un mur, et un plan de
            # ville sans ses murs ne montre rien
            for t in range(21):
                f = t / 20.0
                for a, b in ((0, 1), (1, 2), (2, 0)):
                    col = int(cols[a] + (cols[b] - cols[a]) * f)
                    row = int(rows[a] + (rows[b] - rows[a]) * f)
                    y = max(pts[a][1], pts[b][1])
                    if 0 <= col < w and 0 <= row < h:
                        if grid[row][col] is None or y > grid[row][col]:
                            grid[row][col] = y
            continue
        for row in range(max(0, r0), min(h, r1 + 1)):
            for col in range(max(0, c0), min(w, c1 + 1)):
                px, pz = col + 0.5, row + 0.5
                u = ((px - cols[0]) * (rows[2] - rows[0])
                     - (cols[2] - cols[0]) * (pz - rows[0])) / area
                v = ((cols[1] - cols[0]) * (pz - rows[0])
                     - (px - cols[0]) * (rows[1] - rows[0])) / area
                if u < -0.001 or v < -0.001 or u + v > 1.001:
                    continue
                y = pts[0][1] + u * (pts[1][1] - pts[0][1]) + v * (pts[2][1] - pts[0][1])
                if grid[row][col] is None or y > grid[row][col]:
                    grid[row][col] = y
    return grid, (minx, minz, maxx, maxz), w, h


def render(grid, w, h, path):
    """Du bleu sombre pour le bas, du blanc chaud pour le haut."""
    flat = [v for row in grid for v in row if v is not None]
    if not flat:
        sys.exit("geometrie vide")
    lo, hi = min(flat), max(flat)
    span = max(1e-6, hi - lo)

    img = Image.new("RGB", (w, h), (12, 12, 18))
    px = img.load()
    for row in range(h):
        for col in range(w):
            value = grid[row][col]
            if value is None:
                continue
            t = (value - lo) / span
            px[col, row] = (int(30 + 225 * t ** 0.8),
                            int(40 + 190 * t),
                            int(90 + 120 * (1 - t) ** 2))
    img.save(path)
    return lo, hi


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("code", nargs="+",
                        help="un ou plusieurs codes de DGO, par exemple CPO, ou CTA CTB CPO")
    parser.add_argument("--cell", type=float, default=1.0,
                        help="unites de jeu par pixel (et, plus tard, par bloc)")
    args = parser.parse_args()

    meshes = []
    for code in args.code:
        verts, faces = read_triangles(find_obj(code))
        meshes.append((code, verts, faces))
        print("  %-10s %6d triangles" % (code, len(faces)))

    # le repere commun : l'union des emprises, pour que les quartiers voisins
    # tombent a leur vraie place les uns par rapport aux autres
    minx = min(v[0] for _, vs, _ in meshes for v in vs)
    maxx = max(v[0] for _, vs, _ in meshes for v in vs)
    minz = min(v[2] for _, vs, _ in meshes for v in vs)
    maxz = max(v[2] for _, vs, _ in meshes for v in vs)
    bounds = (minx, minz, maxx, maxz)
    w = int((maxx - minx) / args.cell) + 1
    h = int((maxz - minz) / args.cell) + 1

    grid = [[None] * w for _ in range(h)]
    for _, verts, faces in meshes:
        grid, bounds, w, h = rasterise(verts, faces, args.cell, bounds, grid, (w, h))

    os.makedirs(OUT_DIR, exist_ok=True)
    name = "-".join(c.lower() for c in args.code[:4])
    if len(args.code) > 4:
        name += "-et-%d-autres" % (len(args.code) - 4)
    out = os.path.join(OUT_DIR, "%s-plan.png" % name)
    lo, hi = render(grid, w, h, out)

    filled = sum(1 for row in grid for v in row if v is not None)
    print("%d niveaux" % len(meshes))
    print("  emprise   %.0f x %.0f unites, altitudes %.0f a %.0f"
          % (bounds[2] - bounds[0], bounds[3] - bounds[1], lo, hi))
    print("  grille    %d x %d cellules, %d occupees (%.0f %%)"
          % (w, h, filled, 100.0 * filled / (w * h)))
    print("  plan      %s" % out)


if __name__ == "__main__":
    main()
