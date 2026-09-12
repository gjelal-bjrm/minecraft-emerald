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
    "minecraft:spruce_planks",             # 5   les quais, AU BORD DE L'EAU
    "minecraft:dark_oak_planks",           # 6
    "minecraft:stripped_spruce_wood",      # 7
    "minecraft:tuff",                      # 8   le sol nu, les talus
    "minecraft:polished_andesite",         # 9
    "minecraft:packed_mud",                # 10
    "minecraft:deepslate_bricks",          # 11  les murs
    "minecraft:deepslate_tiles",           # 12
    "minecraft:tuff_bricks",               # 13
    "minecraft:smooth_stone",              # 14
    "supplementaries:blackstone_tile",     # 15  les bandeaux, les plus sombres
    "minecraft:polished_blackstone",       # 16
    "minecraft:mossy_stone_bricks",        # 17  les soubassements, la digue
    "minecraft:mossy_cobblestone",         # 18
    "minecraft:stone_bricks",              # 19
    "minecraft:polished_tuff",             # 20  les toits, le metal
    "minecraft:oxidized_cut_copper",       # 21
    "minecraft:weathered_cut_copper",      # 22
    "minecraft:copper_block",              # 23
    "minecraft:water",                     # 24  le bassin
    "minecraft:deepslate_brick_slab",      # 25  reserve
    "minecraft:cracked_deepslate_bricks",  # 26
]

AIR = 0
PAVE = (1, 2, 3, 4)
DOCK = (5, 6, 7)
GROUND = (8, 9, 10)
# Les murs s'etalent maintenant de 0,21 a 0,62 de luminance, contre 0,14 a 0,28
# auparavant ou les quatre variantes etaient du deepslate a 36/255 les unes des
# autres. Mesure : l'ecart moyen entre deux cellules voisines passe de 0,020 a
# 0,058 -- c'est lui, et non le nombre de variantes, qui faisait « rocher uni ».
WALL = (11, 12, 13, 14, 26)
# Le bandeau doit TRANCHER sur le mur, pas le repeter. Les deux blocs les plus
# sombres de la palette lui sont reserves ; aucune paire bandeau-mur ne descend
# sous 0,07 de luminance d'ecart.
BAND = (15, 16)
FOOT = (17, 18, 19)
ROOF = (20, 21, 22, 23)
WATER = 24

NEIGHBOURS = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))

# Taille minimale d'une etendue d'eau, en colonnes. La rade en fait deux cent
# mille ; la plus grande place en contrebas faussement noyee en faisait six
# mille six cents. Le seuil les separe sans ambiguite.
SEA_MIN = 20000

# Les niveaux d'INTERIEUR, que le jeu charge en streaming par-dessus le port.
# Leur collision ne porte presque aucun plafond -- mesure sur le bar : cent
# quatre-vingt-quatre triangles tournes vers le bas pour cent quatre-vingt-sept
# unites carrees, contre mille huit cent quatre-vingt-trois de sol, soit dix
# pour cent. Le jeu n'en avait pas besoin, le joueur ne pouvant pas les
# toucher ; nous si, sans quoi on voit le ciel depuis le comptoir.
INTERIORS = ("HHG", "GGA")


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


def load_all(codes, lift):
    """Plusieurs niveaux dans UN repere.

    Les coordonnees de la collision sont absolues dans le monde du jeu : deux
    niveaux voisins se placent l'un par rapport a l'autre tout seuls, et
    l'interieur du bar retombe exactement derriere sa porte.
    """
    verts = []
    faces = []
    groups = []
    bounds = {}
    for code in codes:
        base = len(verts)
        v, f = read_triangles(find_obj(code))
        # le LEVER, applique avant la fusion : la porte est dans le meme
        # fichier que la piece, elle monte donc rigidement avec elle et rien
        # ne se decroche
        dy = lift.get(code, 0.0)
        if dy:
            v = [(p[0], p[1] + dy, p[2]) for p in v]
        verts.extend(v)
        start = len(faces)
        faces.extend((a + base, b + base, c + base) for a, b, c in f)
        groups.append((code, start, len(faces)))
        bounds[code] = (min(q[1] for q in v), max(q[1] for q in v))
        print("  %-6s %6d triangles%s" % (code, len(f), "  (leve de %g)" % dy if dy else ""))
    return verts, faces, groups, bounds


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


def voxelize(verts, faces, cell, groups=()):
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
    columns = {}
    owner = {}
    for code, start, end in groups:
        for i in range(start, end):
            owner[i] = code

    for index, tri in enumerate(faces):
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
                code = owner.get(index)
                if code is not None:
                    columns.setdefault(code, set()).add((key[0], key[2]))

    return voxels, (w, hgt, d), (minx, miny, minz), columns


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


def column_tops(voxels):
    """Le voxel solide le plus haut de chaque colonne."""
    tops = {}
    for (x, y, z) in voxels:
        key = (x, z)
        if tops.get(key, -1) < y:
            tops[key] = y
    return tops


def water_mask(voxels, dims, water_y, tops):
    """La nappe : les colonnes dont le SOMMET est sous la ligne d'eau.

    Deux erreurs ont ete commises ici, l'une apres l'autre, et les deux valent
    d'etre gardees par ecrit.

    LA PREMIERE testait une seule cellule par colonne, a la hauteur de l'eau,
    et propageait depuis le bord de la carte. Comme on ne garde que la PEAU des
    surfaces, le massif sous la ville est creux : une colonne dont le sol est a
    la cellule 65 est parfaitement vide a 63. Mesure, cela declarait « ouvertes
    sur le large » 128 274 colonnes portant un vrai sol de ville, soit 72,5 %
    d'entre elles ; la nappe circulait sous la ville et ressortait dans les
    places en contrebas -- quarante et un amas, le plus grand de 6 613
    cellules. C'est exactement ce qu'on nous a rapporte : « les intersections
    ou l'on descend quelques marches ».

    LA SECONDE, qui semblait evidente, aurait VIDE LA RADE. Tester le sommet ET
    exiger une liaison au bord de la carte laisse le bassin a sec : le jeu ne
    pose aucune collision sous l'eau, la baie est donc un trou de 209 738
    colonnes sans la moindre geometrie, et la digue qui l'enferme a son sommet
    au-dessus de la ligne d'eau. La propagation ne l'atteint jamais. Le port
    serait devenu une fosse d'air de cent cinquante blocs de fond.

    LA BONNE REGLE ne propage pas depuis le bord : une colonne est de la mer si
    son sommet est sous la ligne d'eau, colonne vide comprise. On decoupe
    ensuite en composantes et l'on ne garde que les GRANDES -- la rade, le
    large, les canaux. Les petites poches fermees sont precisement les places
    en contrebas, et elles restent seches.
    """
    w, h, d = dims

    # Les CANDIDATS : toute colonne dont le sommet est sous la ligne d'eau,
    # colonne vide comprise.
    candidate = set()
    for x in range(w):
        for z in range(d):
            if tops.get((x, z), -1) < water_y:
                candidate.add((x, z))

    # LES AMORCES SONT LES COLONNES VIDES, et elles seules. C'est la
    # distinction qui manquait : la rade n'a AUCUNE geometrie -- le jeu n'en
    # pose pas sous l'eau -- tandis qu'une place en contrebas a un sol, a la
    # cellule 61 ou 62. Amorcer partout ou le sommet est bas noyait donc les
    # deux ; amorcer depuis le vide ne noie que la mer, et la propagation
    # s'arrete a la levre des places, dont le pourtour est a la ligne d'eau.
    seeds = [c for c in candidate if (c[0], c[1]) not in tops]

    reached = set()
    queue = deque(seeds)
    reached.update(seeds)
    while queue:
        x, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nxt = (x + dx, z + dz)
            if nxt in candidate and nxt not in reached:
                reached.add(nxt)
                queue.append(nxt)

    # LES POCHES FERMEES RESTENT SECHES.
    #
    # Il subsiste des places en contrebas dont le sol est sous la ligne d'eau
    # et qui communiquent par un couloir bas : physiquement elles seraient
    # noyees, mais le jeu n'y met pas d'eau, et le joueur nous l'a signale --
    # « les intersections ou l'on descend quelques marches ». On ne garde donc
    # une etendue A FOND SOLIDE que si elle touche vraiment le large, c'est-a-
    # dire une colonne sans aucune geometrie. Une cuvette entouree de ville
    # n'en touche aucune.
    empty = {c for c in reached if c not in tops}
    floored = reached - empty
    keep = set(empty)
    seen = set()
    for start in floored:
        if start in seen:
            continue
        blob = []
        touches = False
        stack = [start]
        seen.add(start)
        while stack:
            x, z = stack.pop()
            blob.append((x, z))
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nxt = (x + dx, z + dz)
                if nxt in empty:
                    touches = True
                elif nxt in floored and nxt not in seen:
                    seen.add(nxt)
                    stack.append(nxt)
        if touches:
            keep.update(blob)
    return keep


def water_mask_unused(voxels, dims, water_y):
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


def classify(flat, x, y, z, quay_y, water_y, tops, shore):
    """Le bloc d'un voxel : sa classe d'abord, sa variante ensuite.

    LE BOIS NE VA QU'AU BORD DE L'EAU. La regle precedente disait « horizontal
    et y <= quay_y + 2 », ce qui n'est pas un test de quai mais un test
    d'altitude -- et quay_y EST le sol principal de la ville. Elle peignait
    donc en planches 239 743 voxels, soit 32,8 % du niveau entier et la classe
    la plus nombreuse : tout le pave de Haven etait un plancher. Un quai se
    definit par ce qu'il borde, pas par sa hauteur.

    LE TOIT SE JUGE PAR COLONNE. Le seuil precedent, quay_y + 45 % de la
    hauteur totale, valait 106 parce qu'UNE fleche du port monte a 99 : les
    toits ne couvraient que 2,6 % du niveau et jamais le bar. On demande
    desormais si le voxel est le SOMMET de sa colonne et s'il domine le quai,
    ce qui ne depend plus d'un point isole de la carte.
    """
    horizontal = flat > 0.80
    vertical = flat < 0.35

    if y <= quay_y - 6:
        return FOOT[mix(x, y, z, 5) % len(FOOT)]
    if horizontal and (x, z) in shore and water_y - 1 <= y <= water_y + 3:
        return DOCK[mix(x, y, z, 4) % len(DOCK)]
    if horizontal:
        if y >= tops.get((x, z), 0) and y > quay_y + 5:
            return ROOF[mix(x, y, z, 5) % len(ROOF)]
        return PAVE[mix(x, y, z, 3) % len(PAVE)]
    if vertical:
        # le bandeau : une assise differente toutes les cinq. C'est le detail le
        # moins cher et le plus efficace pour qu'un mur cesse d'etre une falaise
        if y % 5 == 0:
            return BAND[mix(x, y, z, 9) % len(BAND)]
        # maille 3 et non 6 : a la maille 6, quatre voisins sur cinq tiraient le
        # MEME bloc et les plaques fusionnaient en aplats
        return WALL[mix(x, y, z, 3) % len(WALL)]
    return GROUND[mix(x, y, z, 4) % len(GROUND)]


def cap_interiors(voxels, dims, columns, codes, roofs):
    """Pose un plafond sur les niveaux d'interieur, que la collision omet.

    Le bar et le stand de tir sont a ciel ouvert dans les donnees : sur les
    mille cinq cent six colonnes du plancher du bar, mille quatre-vingt-douze
    n'ont rien du tout au-dessus du sol plus quatre. On coiffe donc chaque
    piece a la hauteur de sa propre coque -- pas a une hauteur inventee : le
    toit prend l'altitude du point le plus haut du niveau qui le porte, ce qui
    donne un batiment d'un seul tenant plutot qu'un couvercle pose dessus.
    """
    forced = {}
    for code in codes:
        if code not in INTERIORS:
            continue
        own = columns.get(code, set())
        if not own:
            continue
        # LA HAUTEUR PROPRE DU NIVEAU, pas celle de ses colonnes. Prendre le
        # sommet de tout ce qui traverse l'emprise attrapait les tours du port
        # qui la surplombent : le toit du bar se posait a la cellule 120, soit
        # cinquante blocs au-dessus de sa propre coque.
        roof_y = roofs.get(code)
        if roof_y is None:
            continue
        posed = 0
        # le sommet par colonne, calcule une seule fois
        tops = {}
        for (x, y, z) in voxels:
            if (x, z) in own and tops.get((x, z), -1) < y:
                tops[(x, z)] = y
        for (x, z), top in tops.items():
            if top >= roof_y - 1:
                continue                      # deja couverte
            for dy in (roof_y, roof_y - 1):
                if (x, dy, z) not in voxels:
                    forced[(x, dy, z)] = ROOF[mix(x, dy, z, 5) % len(ROOF)]
                    posed += 1
        print("  toit      %s : %d blocs a la hauteur %d" % (code, posed, roof_y))
    return forced


def skirt(voxels, dims, columns, codes, water_y):
    """Donne un SOL aux interieurs qui flottent.

    Le bar n'est pas une dalle posee sur la rue : sur les mille cinq cent sept
    colonnes de son emprise, mille trois cent vingt-six n'ont rien du tout en
    dessous, et toutes sont au-dessus du bassin. Il est raccorde au pave par
    son seul coin. On lui batit donc une jupe qui descend jusqu'a la ligne
    d'eau -- faute de quoi le lever ne ferait que l'ecarter davantage du sol.
    """
    forced = {}
    for code in codes:
        if code not in INTERIORS:
            continue
        own = columns.get(code, set())
        floors = {}
        for (x, y, z) in voxels:
            if (x, z) in own and floors.get((x, z), 10 ** 9) > y:
                floors[(x, z)] = y
        posed = 0
        for (x, z), floor in floors.items():
            for y in range(water_y, floor):
                if (x, y, z) not in voxels:
                    forced[(x, y, z)] = FOOT[mix(x, y, z, 5) % len(FOOT)]
                    posed += 1
        print("  jupe      %s : %d blocs" % (code, posed))
    return forced


def shoreline(sea, dims, reach=3):
    """Les colonnes assez pres de l'eau pour porter un quai.

    C'est ce qui remplace le test d'altitude : un quai borde l'eau, il n'est
    pas simplement bas. Trois cellules de portee suffisent -- au-dela on
    planche des rues entieres, ce qui etait precisement le defaut.
    """
    w, h, d = dims
    near = set()
    for (x, z) in sea:
        for dx in range(-reach, reach + 1):
            for dz in range(-reach, reach + 1):
                nxt = (x + dx, z + dz)
                if 0 <= nxt[0] < w and 0 <= nxt[1] < d and nxt not in sea:
                    near.add(nxt)
    return near


def encode(voxels, dims, quay_y, water_y, mask, depth, tops, shore, forced):
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
                imposed = forced.get((x, y, z))
                flat = voxels.get((x, y, z))
                if imposed is not None:
                    block = imposed
                elif flat is None:
                    block = WATER if flooded and (x, z) in mask else AIR
                else:
                    block = classify(flat, x, y, z, quay_y, water_y, tops, shore)
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
    parser.add_argument("--lift", nargs="*", default=[],
                        help="lever un niveau, par exemple HHG:2")
    parser.add_argument("--cell", type=float, default=1.0)
    parser.add_argument("--water", type=float, default=6.0,
                        help="altitude de la surface de l'eau, en unites du jeu")
    parser.add_argument("--water-depth", dest="depth", type=int, default=6,
                        help="epaisseur de la nappe, en blocs")
    args = parser.parse_args()

    lift = {}
    for spec in args.lift:
        code, _, amount = spec.partition(":")
        lift[code] = float(amount or 0)

    codes = [args.code] + list(args.extra)
    verts, faces, groups, bounds = load_all(codes, lift)
    print("  total  %d triangles" % len(faces))

    voxels, dims, origin, columns = voxelize(verts, faces, args.cell, groups)
    print("  grille    %d x %d x %d" % dims)
    print("  surfaces  %d voxels" % len(voxels))
    filled = pinholes(voxels, dims)
    print("  bouches   %d trous d'un bloc" % filled)

    # Le niveau des quais est MESURE : c'est l'altitude ou la surface
    # horizontale est de loin la plus etendue du niveau -- 133 000 unites
    # carrees a y=8, contre 60 000 a la suivante.
    quay_y = int((8.0 - origin[1]) / args.cell)
    water_y = int((args.water - origin[1]) / args.cell)
    print("  quais y=%d, eau y=%d (en cellules)" % (quay_y, water_y))

    forced = {}
    forced.update(skirt(voxels, dims, columns, codes, water_y))
    # la hauteur de chaque interieur, tiree de sa propre boite englobante
    roofs = {}
    for code, (lo, hi) in bounds.items():
        roofs[code] = int((hi - origin[1]) / args.cell) - 1
    forced.update(cap_interiors(voxels, dims, columns, codes, roofs))
    # les blocs imposes comptent comme de la matiere pour la suite : sans cela
    # la nappe passerait au travers de la jupe qu'on vient de batir
    for key in forced:
        voxels.setdefault(key, 1.0)

    tops = column_tops(voxels)
    sea = water_mask(voxels, dims, water_y, tops)
    shore = shoreline(sea, dims)
    print("  mer       %d colonnes, rivage %d colonnes" % (len(sea), len(shore)))

    runs = encode(voxels, dims, quay_y, water_y, sea, args.depth, tops, shore, forced)
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, "%s.jakv" % args.name)
    packed, _ = write_blob(path, dims, runs)
    print("  plages    %d" % len(runs))
    print("  fichier   %s  (%.1f Mo compresses)" % (path, packed / 1048576.0))


if __name__ == "__main__":
    main()
