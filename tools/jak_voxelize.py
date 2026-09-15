"""Transforme la collision d'un niveau de Jak 3 en volume de blocs.

Il lit la geometrie de COLLISION -- la forme solide du niveau, celle sur
laquelle on marche -- et la rasterise en voxels. Sous la ville, la ou la
collision s'arrete, il complete avec la forme du decor visuel. Il ne copie
aucune texture.

CE QUE LES ESSAIS EN JEU ONT APPRIS, dans l'ordre.

1. LA SURFACE DOIT ETRE ETANCHE. Un echantillonnage sur treillis laissait des
   cellules traversees par la surface sans voxel : sols et murs etaient
   cribles de trous. On rasterise par projection sur l'axe dominant de la
   normale, qui garantit au moins un voxel par cellule traversee.

2. UN QUARTIER N'EST PAS UN SEUL FICHIER. Le bar du Hip Hog et le stand de tir
   sont des niveaux separes, charges en streaming, mais poses aux memes
   coordonnees du monde. On fusionne donc plusieurs DGO dans un meme repere,
   sans quoi leurs portes donnent sur le vide.

3. L'EAU EST A L'ALTITUDE 0. Le port ne declare aucune hauteur d'ocean dans sa
   definition de niveau (level-info.gc), et le moteur prend alors la valeur
   par defaut, 0 ; la barge du port flotte exactement a y=0, et les digues du
   decor plongent jusqu'a y=-3. Une version precedente posait l'eau a 6,
   deduite de la geometrie : les places en contrebas etaient noyees, et aucune
   regle de remplissage ne pouvait rattraper une hauteur fausse.

4. PAS DE LEVER, PAS DE JUPE. La porte du Hip Hog est a y=9,1, au niveau de la
   rue. Lever le bar de deux blocs a decolle sa porte de la rue ; la jupe pleine
   batie sous lui a mure l'entree et coupe l'eau autour. Le bar reste ou le jeu
   le pose. Seuls les plafonds sont ajoutes, parce que la collision les omet.

5. UN BLOC PAR SURFACE. Le sol, les murs et les toits doivent se distinguer
   entre eux, et c'est tout. Les melanges de variantes par plaques se lisaient
   comme du bruit, pas comme une ville.

6. LA COLLISION S'ARRETE AU-DESSUS DE L'EAU. Elle ne porte que ce que le joueur
   peut toucher : le quai s'arrete a la rue. Avec l'eau a sa vraie hauteur, la
   ville flottait de six a neuf blocs au-dessus du bassin sur sept dixiemes de
   son bord. Le jeu, lui, DESSINE des digues qui plongent sous l'eau
   (city-port-seawall-*). On reprend donc la forme du decor visuel, mais
   seulement SOUS la collision : sous la plus basse surface de chaque colonne,
   et jamais plus haut que la rue. Rien de ce qui est ajoute ne peut boucher
   une porte ni encombrer une rue.

7. L'EAU PASSE SOUS CE QUI LA SURPLOMBE. Une colonne n'est plus exclue de la
   mer parce qu'un plancher la couvre plus haut : seule compte la cellule de la
   surface. Sans cela le dessous du bar restait sec, et c'etait le trou dans
   l'eau qu'on voyait depuis le quai.

8. LE BORD EXTERIEUR N'A PAS DE DIGUE. Cote rade, le decor fournit la digue
   (city-port-seawalll, de y=0,3 a 8,2). Mais le bord exterieur du port -- le
   bar, le stand de tir, les bras -- n'a rien sous sa rue, ni collision ni
   decor : dans le jeu il touche les autres quartiers. Une fois la mer posee
   tout autour, la ville y flottait. On y descend donc un rideau de mur d'une
   colonne, du fond de la nappe jusque sous le bord, pour les seules
   structures au niveau de la rue ; pas les cables ni les ponts hauts. La
   muraille de Haven, dans ctywide, part elle aussi de l'eau (y=0).

9. LA COLLISION DETACHE DES PIECES. Elle ne garde que ce que Jak peut toucher,
   si bien que des morceaux perdent ce qui les portait : 305 pieces flottaient
   au-dessus du port -- anneaux des tours du bras central, sommets des tours
   du large, rails de glisse en pointille sous les arcades, catenaires, lampes
   pendues du stand de tir. Aucune regle ajoutee ne les fabriquait. On rend
   d'abord leur corps aux tours, depuis le decor visuel et dans leur seule
   emprise ; puis on retire tout ce qui ne touche ni la ville ni l'eau, en
   26-connexite -- sauf les cables et les pieces nommees, que la lecon 12
   rend. Ce qui plonge dans l'eau reste, meme petit : le joueur l'a
   voulu pour une colonne isolee de 11 blocs du bassin. Les sommets de colonne
   se calculent APRES cet elagage : un anneau retire laissait sinon le toit de
   sa tour classe en sol.

10. LA GRILLE EST FIGEE. Elle se deduisait de la collision : une retouche qui
   la debordait deplacait l'origine, et toutes les cellules relevees -- la
   porte du bar, les salles -- glissaient d'autant sans erreur. L'origine et la
   taille sont maintenant des constantes ; ce qui deborde arrete tout, et
   --pad-top grandit la grille vers le haut sans toucher l'origine. Le fichier
   (version 2) porte l'origine et le sha1 de ses donnees.

11. LA MER DU GENERATEUR EST DEJA LA. Dans la dimension haven, le generateur
   plat pose de l'eau de la cellule 52 a 57 sur tout le monde, et la pose saute
   l'air. L'air enferme sous les quais serait donc noye : on l'ecrit en
   cave_air. Et l'on sortait de la ville a la nage, sous un rideau qui partait
   au-dessus de l'eau : le rideau de barrieres du pourtour descend jusqu'a la
   cellule 52, sur la pierre du generateur, et monte jusqu'en haut de la grille.
   DANS LA MER, CE RIDEAU EST NOYE : une barriere se pose seche par defaut, et
   n'occulte rien. Posee au milieu de l'eau du generateur, elle laissait sur
   tout le pourtour une fente d'un bloc de large et six de profond, dont l'eau
   voisine dessinait les parois. Les cellules 52 a 57 du rideau sont donc
   ecrites en barrier[waterlogged=true], qui bloque autant et reste de l'eau
   a l'oeil.
   Les bouts des bras sont mures au bloc de mur.

12. LES CABLES FONT LA VILLE. L'elagage de la lecon 9 avait emporte, avec
   quelques eclats, les cables qui partent des deux tours du large vers le bord
   sud, les deux catenaires et les rails de glisse sous les arcades : 1 338 blocs
   en 285 pieces. Le joueur les a vus disparaitre, et il y tenait. La collision
   ne les donnait qu'en pointille ; on les reprend donc du decor visuel, APRES
   l'elagage : l'axe de chaque tube (ses anneaux de sommets), pose en ligne de
   blocs de mur reliees par leurs faces, les troncons voisins raccordes. Les
   autres pieces visibles reviennent aussi, nommees dans KEEP_DETACHED : la
   pointe de la tour du large ouest, l'applique du bras central, et les quatorze
   lampes du stand de tir, pendues a leur plafond par une tige. Aucune regle
   de taille ne separe un eclat d'une piece voulue : on regarde les rendus (de
   cote et de dessus, pieces retirees en couleur) avant d'elaguer quoi que ce soit.

Usage :
    python tools/jak_voxelize.py CPO --name ctyport --with HHG GGA --rooms haven_rooms.json
"""

import argparse
import glob
import hashlib
import json
import math
import os
import re
import struct
import sys
import zlib
from collections import deque

from jak_assets import mesh_triangles

DECOMP = os.path.join(os.environ.get("USERPROFILE", ""), "Documents", "OpenGoal",
                      "active", "jak3", "data", "decompiler_out", "jak3")
COLLISION = os.path.join(DECOMP, "collision")
LEVELS = os.path.join(DECOMP, "levels")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "data",
                       "emeraldweapons", "jak")

# Le decor visuel de chaque niveau, par code de DGO (voir dgo.txt).
VISUALS = {"CPO": "ctyport", "HHG": "hiphog", "GGA": "gungame"}

# Un bloc par surface. Le pave du port est gris moyen, ses murs de metal plus
# sombres, ses toits sombres et lisses : trois gris distincts, rien d'autre.
# L'index 0 est toujours l'air, dont le decodeur se sert pour sauter les plages
# vides d'un coup.
PALETTE = [
    "minecraft:air",                  # 0
    "minecraft:polished_andesite",    # 1  le sol : rues, places, quais, rampes
    "minecraft:deepslate_bricks",     # 2  les murs, les digues sous la ville
    "minecraft:deepslate_tiles",      # 3  les toits
    "minecraft:water",                # 4  le bassin
    "minecraft:cave_air",             # 5  l'air enferme sous la mer (lecon 11)
    "minecraft:barrier",              # 6  le rideau invisible du bord (lecon 11)
    "minecraft:barrier[waterlogged=true]",  # 7  le meme rideau, dans la mer (lecon 11)
]
AIR, FLOOR, WALL, ROOF, WATER, CAVE_AIR, BARRIER, WET_BARRIER = range(8)

# L'altitude de l'eau, celle du jeu (voir la lecon 3).
WATER_HEIGHT = 0.0

# La grille du port (CPO + HHG + GGA), FIGEE (voir la lecon 9) : le coin
# (minx, miny, minz) de la collision fusionnee et le nombre de cellules. Toute
# cellule citee ailleurs -- la porte du bar en (361, 66, 197), les salles de
# haven_rooms.json -- se compte depuis ce coin. Le y est a pleine precision :
# arrondi a -57,5016, il passerait trois cent-milliemes au-dessus du plus bas
# sommet de la collision (-57,50163), et la grille deborderait.
GRID_ORIGIN = (-434.7777, -57.50163, 1126.3511)
GRID_DIMS = (1227, 158, 695)

# Les tours a completer (voir la lecon 9), en cellules de la grille figee :
# emprise en x, tranche en y, emprise en z. Mesurees sur le volume : l'emprise
# couvre la collision de la tour sous le trou et le decor de son corps ; la
# tranche part d'un peu sous le trou et monte jusqu'au sommet du decor.
TOWERS = [
    # jeu x 137..175, z 1308..1356 ; anneaux en y 116..127, corps coupe a 101
    ("tour ouest du bras central", (572, 609), (96, 157), (182, 229)),
    # jeu x 215..251, z 1308..1356
    ("tour est du bras central", (650, 685), (96, 157), (182, 229)),
    # jeu x -97..-55, z 1481..1523 ; sommet en y 92..120, plancher a 79
    ("tour du large ouest", (338, 380), (76, 157), (355, 397)),
    # jeu x 434..477, z 1478..1519 ; sommet en y 93..120
    ("tour du large est", (869, 911), (76, 157), (352, 393)),
]
# Les tuyaux 5x5 des tours du large : emprise en x, sommet de la collision,
# emprise en z. On suit leur coude dans le decor a partir de ce sommet.
TOWER_PIPES = [
    ("tuyau ouest 1", (346, 350), 87, (304, 309)),
    ("tuyau ouest 2", (411, 415), 79, (297, 301)),
    ("tuyau ouest 3", (390, 394), 79, (317, 321)),
    ("tuyau ouest 4", (333, 337), 79, (369, 373)),
    ("tuyau ouest 5", (354, 358), 79, (349, 353)),
    ("tuyau est 1", (828, 834), 82, (288, 294)),
    ("tuyau est 2", (853, 857), 79, (309, 314)),
    ("tuyau est 3", (913, 917), 79, (365, 369)),
    ("tuyau est 4", (890, 895), 79, (344, 348)),
]
PIPE_REACH = 30       # colonnes autour du sommet d'un tuyau ou chercher son coude
PIPE_RISE = 40        # cellules au-dessus du sommet
# Ce qui, dans l'emprise d'une tour, n'est pas la tour : les cables (ceux du
# palais tombe, les catenaires), les debris du palais, les decalcomanies et les
# lumieres, qui ne sont que des aplats ou des points.
TOWER_SKIP = ("cable", "fallen-palace", "decal", "stain", "blotch", "lamp", "light", "bulb")

# Les cables du port, repris du decor visuel APRES l'elagage (voir la lecon 12) :
# ceux qui partent des tours du large et les catenaires (city-port-tower-cable-01),
# et les rails de glisse sous les arcades (city-port-smallpipe-straight-grind-01).
# L'axe de chaque tube est pose en ligne continue de blocs de mur.
CABLE_MESHES = ("city-port-tower-cable-01", "city-port-smallpipe-straight-grind-01")
# Deux bouts de tubes a moins de cette distance, en unites du jeu, sont relies :
# un cable du jeu est une suite de troncons droits qui ne partagent aucun sommet.
CABLE_JOIN = 1.5
# L'ecart, le long de l'axe, au-dela duquel deux sommets ne sont plus du meme anneau.
CABLE_RING = 0.3

# Les pieces detachees gardees malgre l'elagage (voir la lecon 12), en cellules :
# emprise en x, en y, en z, et s'il faut les pendre. Une piece n'est gardee que si
# elle tient entiere dans sa boite ; une piece a pendre recoit une tige de mur
# jusqu'au bloc au-dessus d'elle, s'il est a moins de HANG_REACH cellules.
KEEP_DETACHED = [
    # les lampes du stand de tir (gun-hanging-lamp) : le decor les accroche au
    # plafond en y 80, la collision s'arrete une cellule dessous
    ("lampes pendues du stand de tir", (720, 830), (76, 79), (15, 75), True),
    # la pointe de la couronne de la tour (city-port-metal-green-main-side), a une
    # cellule de sa couronne
    ("pointe de la tour du large ouest", (348, 350), (113, 127), (364, 366), False),
    # l'applique contre le mur du bras central (city-port-lamp-wallsconse-01)
    ("applique du bras central", (660, 661), (72, 76), (106, 108), False),
]
HANG_REACH = 4

# Les bouts des bras, a murer (voir la lecon 11), en cellules : x, y, z. Le
# mur monte du sol du quai (cellule 61) jusqu'a la hauteur donnee.
ARM_ENDS = [
    # le quai du bras ouest touche le bord x=0 ; ses immeubles montent a 91
    ("bout du bras ouest", (0, 0), (62, 91), (77, 132)),
    # le quai du bras est touche x=1226, sans rien autour : 24 blocs, comme
    # le plafond des garages
    ("bout du bras est", (1226, 1226), (62, 85), (74, 91)),
    # le ponton du bras central devant les portes city-port-door01 (z 48-49,
    # sommet a la cellule 94), au bord nord du ponton
    ("bout nord du bras central", (579, 674), (62, 94), (47, 47)),
]

# Les appartements : les trois garages du bout du bras ouest les plus proches
# du bar, du plus proche au plus loin. Chacun va d'un mur de separation a
# l'autre en z ; son interieur va de x=95 au mur du cote de l'eau (x=104),
# du sol (61) au toit (85). Sa face ouest, en x=94, est ouverte sur la rue.
APARTMENTS = [
    ("appartement_1", (181, 195)),
    ("appartement_2", (157, 171)),
    ("appartement_3", (134, 147)),
]
APT_FACE_X = 94
APT_INNER_X = (95, 104)
APT_FLOOR_Y = 61
APT_CEILING_Y = 85
DOOR_WIDTH = 3
DOOR_HEIGHT = 4

# La place de voiture devant chaque porte. Ses cotes viennent des bornes des
# trois voitures, lues dans l'en-tete des .bin car*.bin de jak_vehicles a chaque
# execution (vehicle_extents) : 5,59 m de large au plus (carb), 3,52 de haut
# (cara), 8,43 de long (cara). La boite libre fait 7 x 4 x 9. Les cotes
# restent IMPAIRES : la voiture se centre en floor + 0,5, et une largeur paire
# decalerait la boite d'une demi-case sous elle -- carb en deborderait. Elle recule de
# CAR_GAP cellules devant la porte, pour qu'on sorte sans buter sur la voiture.
CAR_WIDTH = 7
CAR_HEIGHT = 4
CAR_LENGTH = 9
CAR_GAP = 2
# Le lacet Minecraft de la voiture garee, en degres : 0 regarde +Z, 90 regarde
# -X. Les portes s'ouvrent a l'ouest sur la rue du bras : le nez vers la rue,
# l'arriere vers la porte. Le modele a son avant en +z (tools/jak_vehicle.py).
CAR_YAW = 90.0
VEHICLES = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons", "jak_vehicles")

# La place de la moto monoplace, a cote de la voiture (une par appartement,
# decision du joueur). Ses cotes viennent des bike*.bin comme celles des
# voitures : 2,40 m de large au plus (bikec), 2,76 de haut (bikeb), 5,81 de long
# (bikec). La boite libre fait 3 x 3 x 7, cotes impaires pour la meme raison.
# Elle se tient du cote +z de la place de voiture -- la gauche de la voiture
# garee au lacet 90 -- et, si ce cote est pris, du cote -z. BIKE_GAP cellules
# d'air sur sol plein la separent de la voiture, pour passer a pied entre les
# deux ; son arriere part de celui de la voiture et recule vers la rue. Elle ne
# touche jamais le passage devant la porte. Lacet et centrage : ceux des voitures.
BIKE_WIDTH = 3
BIKE_HEIGHT = 3
BIKE_LENGTH = 7
BIKE_GAP = 1

# Le sol principal de la ville : l'altitude ou la surface horizontale est de
# loin la plus etendue du port, 133 000 unites carrees a y=8.
QUAY_HEIGHT = 8.0

# Les niveaux d'interieur, dont la collision omet les plafonds.
INTERIORS = ("HHG", "GGA")

# La portee, en colonnes, du decor ajoute hors de l'emprise de la collision :
# assez pour le renflement du pied des digues, trop peu pour aller chercher un
# debris au milieu de la rade.
REACH = 4

NEIGHBOURS = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


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


def load_all(codes):
    """Plusieurs niveaux dans UN repere, a leurs coordonnees du monde."""
    verts = []
    faces = []
    groups = []
    bounds = {}
    for code in codes:
        base = len(verts)
        v, f = read_triangles(find_obj(code))
        verts.extend(v)
        start = len(faces)
        faces.extend((a + base, b + base, c + base) for a, b, c in f)
        groups.append((code, start, len(faces)))
        bounds[code] = (min(q[1] for q in v), max(q[1] for q in v))
        print("  %-6s %6d triangles" % (code, len(f)))
    return verts, faces, groups, bounds


def load_visuals(codes, below, origin, dims, cell):
    """Les triangles du decor visuel qui passent sous `below`, dans la grille."""
    verts = []
    faces = []
    x_hi = origin[0] + dims[0] * cell
    z_hi = origin[2] + dims[2] * cell
    for code in codes:
        name = VISUALS.get(code)
        path = os.path.join(LEVELS, name or "", "%s-background.glb" % name)
        if not name or not os.path.isfile(path):
            print("  visuel %-6s absent" % code)
            continue
        v, f = mesh_triangles(path)
        base = len(verts)
        verts.extend(v)
        kept = 0
        for a, b, c in f:
            pa, pb, pc = v[a], v[b], v[c]
            if min(pa[1], pb[1], pc[1]) >= below:
                continue
            # le decor deborde largement la collision (jusqu'a z=-36 pour le
            # port) : on ecarte ce qui tombe hors de la grille
            if max(pa[0], pb[0], pc[0]) < origin[0] or min(pa[0], pb[0], pc[0]) > x_hi:
                continue
            if max(pa[2], pb[2], pc[2]) < origin[2] or min(pa[2], pb[2], pc[2]) > z_hi:
                continue
            faces.append((a + base, b + base, c + base))
            kept += 1
        print("  visuel %-6s %6d triangles sous y=%.0f" % (code, kept, below))
    return verts, faces


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


def grid(verts, cell):
    """Les dimensions et l'origine de la grille qui contient la collision."""
    lo = [min(v[i] for v in verts) for i in range(3)]
    hi = [max(v[i] for v in verts) for i in range(3)]
    dims = tuple(int((hi[i] - lo[i]) / cell) + 2 for i in range(3))
    return dims, tuple(lo)


def fixed_grid(verts, origin, dims, cell):
    """Verifie que la collision tient dans la grille figee (voir la lecon 9).

    La grille ne se deduit plus de la geometrie : une retouche qui la
    deborderait deplacerait l'origine, et toutes les cellules relevees -- les
    salles, la porte du bar -- ne voudraient plus rien dire. On s'arrete donc
    en erreur plutot que de grandir en silence.
    """
    hi = [origin[i] + dims[i] * cell for i in range(3)]
    lo_seen = [min(v[i] for v in verts) for i in range(3)]
    hi_seen = [max(v[i] for v in verts) for i in range(3)]
    faults = []
    for i, axis in enumerate("xyz"):
        if lo_seen[i] < origin[i]:
            faults.append("%s min %.4f < origine %.4f" % (axis, lo_seen[i], origin[i]))
        if hi_seen[i] >= hi[i]:
            faults.append("%s max %.4f >= bord %.4f" % (axis, hi_seen[i], hi[i]))
    if faults:
        sys.exit("la collision deborde de la grille figee : " + " ; ".join(faults))


def rasterize(voxels, verts, faces, cell, dims, origin, groups=(), columns=None):
    """La peau des triangles, rasterisee sans trou (voir la lecon 1).

    On garde la peau seulement : les batiments restent creux, donc visitables.
    La valeur de chaque voxel est la planeite de sa face, |ny|.
    """
    minx, miny, minz = origin
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

        # borne a la grille : un triangle du decor peut etre immense
        lo_u = max(0, int(min(au, bu, cu)))
        hi_u = min(dims[u1] - 1, int(max(au, bu, cu)) + 1)
        lo_v = max(0, int(min(av, bv, cv)))
        hi_v = min(dims[u2] - 1, int(max(av, bv, cv)) + 1)
        for iu in range(lo_u, hi_u + 1):
            for iv in range(lo_v, hi_v + 1):
                su, sv = iu + 0.5, iv + 0.5
                s = ((su - au) * (cv - av) - (cu - au) * (sv - av)) / area
                t = ((bu - au) * (sv - av) - (su - au) * (bv - av)) / area
                # un vingtieme de marge : deux triangles voisins se recouvrent
                # legerement au lieu de laisser une couture
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
                if not (0 <= key[0] < dims[0] and 0 <= key[1] < dims[1] and 0 <= key[2] < dims[2]):
                    continue
                old = voxels.get(key)
                # la face la plus plate gagne : un sol traverse par un mur doit
                # rester un sol
                if old is None or flat > old:
                    voxels[key] = flat
                if columns is not None:
                    code = owner.get(index)
                    if code is not None:
                        columns.setdefault(code, set()).add((key[0], key[2]))


def pinholes(voxels, dims):
    """Bouche les trous d'un bloc que la rasterisation laisse aux aretes."""
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


def column_extremes(cells):
    """Le voxel le plus bas et le plus haut de chaque colonne."""
    low = {}
    top = {}
    for (x, y, z) in cells:
        key = (x, z)
        if low.get(key, 1 << 30) > y:
            low[key] = y
        if top.get(key, -1) < y:
            top[key] = y
    return low, top


def underside(visual, voxels, quay_y, floor_y):
    """Ce que la collision omet sous la ville : digues, piles, soubassements.

    Un voxel du decor n'est garde que s'il est SOUS la plus basse surface de
    collision de sa colonne, et jamais au-dessus du niveau de la rue plus un --
    la hauteur du plancher du bar, pour que le pied de son mur rejoigne la
    digue sans fente. Hors de l'emprise de la collision, il doit rester sous la
    rue et a moins de REACH colonnes d'elle. Il ne descend pas sous le fond de
    la nappe : ce qui plonge plus bas ne se verrait pas.
    """
    low, _ = column_extremes(voxels)
    kept = {}
    near = {}
    for (x, y, z) in visual:
        if y < floor_y or y > quay_y + 1 or (x, y, z) in voxels:
            continue
        limit = low.get((x, z))
        if limit is not None:
            if y >= limit:
                continue
        else:
            if y >= quay_y:
                continue
            ok = near.get((x, z))
            if ok is None:
                ok = any((x + dx, z + dz) in low
                         for dx in range(-REACH, REACH + 1)
                         for dz in range(-REACH, REACH + 1))
                near[(x, z)] = ok
            if not ok:
                continue
        kept[(x, y, z)] = WALL
    return kept


def water_mask(dims, water_y, occupied, surface):
    """Les colonnes de mer.

    La mer part des colonnes NUES, sans le moindre bloc -- le jeu ne pose rien
    sous l'eau, la rade est un grand vide -- et gagne toute colonne voisine
    dont la cellule de SURFACE est libre, meme si un plancher la couvre plus
    haut (voir la lecon 7). Une digue qui traverse la surface l'arrete.
    """
    w, h, d = dims
    reached = set()
    queue = deque()
    for x in range(w):
        for z in range(d):
            if (x, z) not in occupied:
                reached.add((x, z))
                queue.append((x, z))
    while queue:
        x, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nxt = (x + dx, z + dz)
            if nxt in reached or nxt in surface:
                continue
            if 0 <= nxt[0] < w and 0 <= nxt[1] < d:
                reached.add(nxt)
                queue.append(nxt)
    return reached


def water_edges(sea, dims):
    """Les colonnes solides qui bordent la mer sur au moins deux cotes.

    Un pieu, une pile de pont : sa colonne traverse la surface, donc n'est pas
    de la mer, et l'eau s'arretait net autour en laissant un puits sec sous son
    pied. On remplit leurs cellules VIDES sous la ligne d'eau ; l'eau ne
    remplace jamais un bloc.
    """
    w, h, d = dims
    edges = set()
    for (x, z) in sea:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nxt = (x + dx, z + dz)
            if nxt in sea or nxt in edges:
                continue
            if not (0 <= nxt[0] < w and 0 <= nxt[1] < d):
                continue
            around = sum(1 for ex, ez in ((1, 0), (-1, 0), (0, 1), (0, -1))
                         if (nxt[0] + ex, nxt[1] + ez) in sea)
            if around >= 2:
                edges.add(nxt)
    return edges


def curtains(voxels, forced, sea, quay_y, water_y, floor_y):
    """Le rideau de mur sous le bord qui donne sur l'eau libre (voir la lecon 8).

    Une colonne le recoit si elle borde de l'eau A CIEL OUVERT, si son plus bas
    bloc est a plus d'un bloc au-dessus de la surface, et si sa collision la
    plus basse est au niveau de la rue -- trois blocs de marge pour le plancher
    du bar. Le rideau monte du fond de la nappe jusque sous ce plus bas bloc :
    il ne touche jamais une case ou l'on marche.
    """
    low_all, _ = column_extremes(list(voxels) + list(forced))
    low_collision, _ = column_extremes(voxels)
    open_sea = {c for c in sea if c not in low_all}
    added = {}
    for (x, z), low in low_all.items():
        if low <= water_y + 1:
            continue
        lowest = low_collision.get((x, z))
        if lowest is None or lowest > quay_y + 3:
            continue
        if not any((x + dx, z + dz) in open_sea for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            continue
        for y in range(floor_y, low):
            added[(x, y, z)] = WALL
    return added


def classify(flat, x, y, z, quay_y, tops):
    """Le bloc d'un voxel de collision : sol, mur ou toit, et rien d'autre.

    Une face assez plate pour qu'on y marche -- sol ou rampe -- est du sol,
    sauf si elle coiffe sa colonne bien au-dessus des quais : c'est alors un
    toit. Tout le reste est du mur.
    """
    if flat >= 0.5:
        if y >= tops.get((x, z), 0) and y > quay_y + 5:
            return ROOF
        return FLOOR
    return WALL


def cap_interiors(voxels, columns, codes, roofs):
    """Pose un plafond sur les interieurs, a la hauteur de leur propre coque.

    La collision du bar ne porte presque aucun plafond : 184 triangles tournes
    vers le bas contre 1 883 unites carrees de sol. Le toit prend la hauteur la
    plus haute du niveau qui le porte, pas celle des tours du port qui le
    surplombent.
    """
    forced = {}
    for code in codes:
        if code not in INTERIORS:
            continue
        own = columns.get(code, set())
        roof_y = roofs.get(code)
        if not own or roof_y is None:
            continue
        tops = {}
        for (x, y, z) in voxels:
            if (x, z) in own and tops.get((x, z), -1) < y:
                tops[(x, z)] = y
        posed = 0
        for (x, z), top in tops.items():
            if top >= roof_y - 1:
                continue
            for dy in (roof_y, roof_y - 1):
                if (x, dy, z) not in voxels:
                    forced[(x, dy, z)] = ROOF
                    posed += 1
        print("  toit      %s : %d blocs a la hauteur %d" % (code, posed, roof_y))
    return forced


def complete_towers(voxels, forced, dims, origin, cell, floor_y):
    """Rend leur corps aux tours dont la collision ne garde que le sommet.

    Les anneaux des deux tours du bras central et les sommets des deux tours du
    large flottaient : la collision de la tour s'arrete sous eux (voir la
    lecon 9). On reprend la peau du decor visuel, mais seulement dans
    l'emprise de chaque tour, en ecartant ce qui n'est pas la tour -- les
    cables du palais tombe qui la traversent, les decalcomanies, les lampes.
    Les voxels ajoutes se classent comme la collision, sol, mur ou toit selon
    leur normale. Ils ne remplacent jamais un bloc, et ne descendent pas sous
    le fond de la nappe.

    Les tuyaux des tours du large, eux, tiennent dans l'eau : on suit leur
    coude dans le decor, depuis leur sommet, tant qu'il reste un tuyau.
    """
    path = os.path.join(LEVELS, "ctyport", "ctyport-background.glb")
    if not os.path.isfile(path):
        sys.exit("decor des tours absent : %s" % path)
    verts, faces, names = mesh_triangles(path, with_names=True)
    kept = [i for i, n in enumerate(names) if not any(s in n for s in TOWER_SKIP)]

    def skin(box, only=None):
        (x0, x1), (y0, y1), (z0, z1) = box
        gx0, gx1 = origin[0] + x0 * cell, origin[0] + (x1 + 1) * cell
        gy0, gy1 = origin[1] + y0 * cell, origin[1] + (y1 + 1) * cell
        gz0, gz1 = origin[2] + z0 * cell, origin[2] + (z1 + 1) * cell
        chosen = []
        for i in kept:
            if only and only not in names[i]:
                continue
            a, b, c = (verts[k] for k in faces[i])
            if max(a[0], b[0], c[0]) < gx0 or min(a[0], b[0], c[0]) > gx1:
                continue
            if max(a[1], b[1], c[1]) < gy0 or min(a[1], b[1], c[1]) > gy1:
                continue
            if max(a[2], b[2], c[2]) < gz0 or min(a[2], b[2], c[2]) > gz1:
                continue
            chosen.append(faces[i])
        cells = {}
        rasterize(cells, verts, chosen, cell, dims, origin)
        pinholes(cells, dims)
        return {k: v for k, v in cells.items()
                if x0 <= k[0] <= x1 and max(y0, floor_y) <= k[1] <= y1 and z0 <= k[2] <= z1
                and k not in voxels and k not in forced}

    report = []
    for label, xs, ys, zs in TOWERS:
        added = skin((xs, ys, zs))
        voxels.update(added)
        report.append((label, len(added)))
        print("  tour      %-34s %6d blocs repris du decor" % (label, len(added)))
    for label, xs, top, zs in TOWER_PIPES:
        # la boite de recherche : le sommet du tuyau, elargi de PIPE_REACH
        box = ((xs[0] - PIPE_REACH, xs[1] + PIPE_REACH), (top - 3, top + PIPE_RISE),
               (zs[0] - PIPE_REACH, zs[1] + PIPE_REACH))
        pool = skin(box, only="pipe")
        seeds = [(x, y, z) for x in range(xs[0], xs[1] + 1) for y in range(top - 3, top + 1)
                 for z in range(zs[0], zs[1] + 1) if (x, y, z) in voxels or (x, y, z) in forced]
        grown = {}
        stack = []
        for (x, y, z) in seeds:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        key = (x + dx, y + dy, z + dz)
                        if key in pool and key not in grown:
                            grown[key] = pool[key]
                            stack.append(key)
        while stack:
            x, y, z = stack.pop()
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        key = (x + dx, y + dy, z + dz)
                        if key in pool and key not in grown:
                            grown[key] = pool[key]
                            stack.append(key)
        voxels.update(grown)
        report.append((label, len(grown)))
        print("  tuyau     %-34s %6d blocs repris du decor" % (label, len(grown)))
    return report


def cable_axes(verts, faces, names):
    """Les axes des cables du decor, en polylignes (voir la lecon 12).

    Un tube est une composante de triangles relies par leurs sommets. On
    projette ses sommets sur sa direction principale et on les regroupe en
    anneaux, tant que les projections se suivent a moins de CABLE_RING ; le
    centre de chaque anneau est un point de l'axe. Un troncon droit du jeu n'a
    que ses deux anneaux de bout.
    """
    chosen = [i for i, n in enumerate(names) if any(m in n for m in CABLE_MESHES)]
    parent = {}

    def find(key):
        root = key
        while parent.setdefault(root, root) != root:
            root = parent[root]
        while parent[key] != root:
            parent[key], key = root, parent[key]
        return root

    def rounded(p):
        return (round(p[0], 2), round(p[1], 2), round(p[2], 2))

    for i in chosen:
        keys = [rounded(verts[k]) for k in faces[i]]
        for other in keys[1:]:
            a, b = find(keys[0]), find(other)
            if a != b:
                parent[a] = b
    groups = {}
    for i in chosen:
        for k in faces[i]:
            key = rounded(verts[k])
            groups.setdefault(find(key), set()).add(key)

    axes = []
    for points in groups.values():
        pts = list(points)
        n = len(pts)
        c = [sum(p[a] for p in pts) / n for a in range(3)]
        cov = [[sum((p[a] - c[a]) * (p[b] - c[b]) for p in pts) for b in range(3)] for a in range(3)]
        spans = [max(p[a] for p in pts) - min(p[a] for p in pts) for a in range(3)]
        longest = spans.index(max(spans))
        # la puissance iteree part de l'axe le plus etendu, jamais d'un vecteur
        # qui pourrait etre orthogonal a la direction cherchee
        e = [1.0 if a == longest else 0.3137 for a in range(3)]
        norm = 1.0
        for _ in range(60):
            e = [sum(cov[a][b] * e[b] for b in range(3)) for a in range(3)]
            norm = math.sqrt(sum(x * x for x in e))
            if norm < 1e-12:
                break
            e = [x / norm for x in e]
        if norm < 1e-12:
            continue
        along = sorted((sum((p[a] - c[a]) * e[a] for a in range(3)), p) for p in pts)
        if along[-1][0] - along[0][0] < 1.0:
            continue
        rings = [[along[0][1]]]
        for (t, p), (prev, _) in zip(along[1:], along):
            if t - prev > CABLE_RING:
                rings.append([])
            rings[-1].append(p)
        axes.append([tuple(sum(p[a] for p in ring) / len(ring) for a in range(3)) for ring in rings])
    return axes


def voxel_line(a, b):
    """Les cellules que traverse le segment a-b, reliees par leurs faces.

    Traversee d'Amanatides et Woo : on passe d'une cellule a la voisine par la
    face que le segment franchit en premier. Le nombre de pas est l'ecart de
    Manhattan entre les deux cellules ; a egalite de franchissement, on ne
    choisit qu'un axe qui doit encore avancer.
    """
    cell = [int(math.floor(a[i])) for i in range(3)]
    last = [int(math.floor(b[i])) for i in range(3)]
    step = [0, 0, 0]
    t_max = [math.inf] * 3
    t_delta = [math.inf] * 3
    for i in range(3):
        d = b[i] - a[i]
        if d > 0:
            step[i] = 1
            t_max[i] = (math.floor(a[i]) + 1 - a[i]) / d
            t_delta[i] = 1.0 / d
        elif d < 0:
            step[i] = -1
            t_max[i] = (math.floor(a[i]) - a[i]) / d
            t_delta[i] = -1.0 / d
    cells = [tuple(cell)]
    for _ in range(sum(abs(last[i] - cell[i]) for i in range(3))):
        i = min((k for k in range(3) if cell[k] != last[k]), key=lambda k: t_max[k])
        cell[i] += step[i]
        t_max[i] += t_delta[i]
        cells.append(tuple(cell))
    return cells


def lay_cables(voxels, forced, dims, origin, cell):
    """Pose les cables du port en lignes continues de mur (voir la lecon 12).

    Chaque axe devient une suite de cellules reliees par leurs faces, et les
    bouts de troncons voisins sont relies de la meme facon. Rien ne remplace un
    bloc, rien ne sort de la grille. Rend les cellules posees.
    """
    path = os.path.join(LEVELS, "ctyport", "ctyport-background.glb")
    if not os.path.isfile(path):
        sys.exit("decor des cables absent : %s" % path)
    verts, faces, names = mesh_triangles(path, with_names=True)
    axes = cable_axes(verts, faces, names)

    def to_cell(p):
        return tuple((p[i] - origin[i]) / cell for i in range(3))

    segments = []
    for line in axes:
        pts = [to_cell(p) for p in line]
        segments.extend(zip(pts, pts[1:]))
    ends = [(k, to_cell(p)) for k, line in enumerate(axes) for p in (line[0], line[-1])]
    joins = 0
    for i, (ka, pa) in enumerate(ends):
        for kb, pb in ends[i + 1:]:
            if ka != kb and math.dist(pa, pb) <= CABLE_JOIN / cell:
                segments.append((pa, pb))
                joins += 1
    posed = []
    for a, b in segments:
        for key in voxel_line(a, b):
            if not all(0 <= key[i] < dims[i] for i in range(3)):
                continue
            if key in voxels or key in forced:
                continue
            forced[key] = WALL
            posed.append(key)
    print("  cables    %d tubes, %d raccords, %d blocs de mur" % (len(axes), joins, len(posed)))
    return posed


def hang(voxels, forced, members, dims):
    """Pend une piece au bloc au-dessus d'elle par une tige de mur ; rend la longueur de la tige.

    On prend la colonne de la piece dont le bloc du dessus est le plus proche,
    puis la plus proche du centre de la piece.
    """
    cx = sum(p[0] for p in members) / len(members)
    cz = sum(p[2] for p in members) / len(members)
    tops = {}
    for (x, y, z) in members:
        if tops.get((x, z), -1) < y:
            tops[(x, z)] = y
    best = None
    for (x, z), top in tops.items():
        for gap in range(1, HANG_REACH + 1):
            if top + gap >= dims[1]:
                break
            if (x, top + gap, z) in voxels or (x, top + gap, z) in forced:
                rank = (gap, (x - cx) ** 2 + (z - cz) ** 2)
                if best is None or rank < best[0]:
                    best = (rank, x, top, z)
                break
    if best is None:
        return 0
    (gap, _), x, top, z = best
    for y in range(top + 1, top + gap):
        forced[(x, y, z)] = WALL
    return gap - 1


def prune_detached(voxels, forced, dims, water_y, keep_min, keep=()):
    """Retire ce qui ne touche ni la ville ni l'eau, en 26-connexite (lecon 9).

    Garde la plus grande composante -- la ville -- et toute composante dont le
    plus bas bloc plonge sous la surface, QUELLE QUE SOIT SA TAILLE : elle
    tient dans la rade, comme les cables geants tombes du palais ou la colonne
    isolee de 11 blocs du bassin (x 127, z 560). Le joueur l'a decide : ce qui
    plonge dans l'eau et tient seul reste.

    Le seuil keep_min ne vaut que pour les pieces qui ne touchent pas l'eau :
    elles ne restent qu'a partir de keep_min blocs. Dans le port, la plus
    grosse en fait 198 (x 528 a 725, cellules 106 a 120). Ce sont des eclats, et
    les cables et rails en pointille que lay_cables repose en entier apres
    (lecon 12).

    Une piece qui tient entiere dans une boite de `keep` (KEEP_DETACHED) reste
    aussi, et recoit sa tige si la boite est a pendre.

    Rend la liste des pieces retirees : (taille, boite, plus bas bloc, cellules).
    """
    solids = set(voxels) | set(forced)
    label = {}
    comps = []
    for seed in solids:
        if seed in label:
            continue
        cid = len(comps)
        label[seed] = cid
        stack = [seed]
        members = []
        while stack:
            p = stack.pop()
            members.append(p)
            x, y, z = p
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        q = (x + dx, y + dy, z + dz)
                        if q in solids and q not in label:
                            label[q] = cid
                            stack.append(q)
        comps.append(members)
    main = max(range(len(comps)), key=lambda c: len(comps[c]))
    removed = []
    kept = []
    named = {}
    for cid, members in enumerate(comps):
        if cid == main:
            continue
        low = min(p[1] for p in members)
        # dans l'eau, on garde tout ; hors de l'eau, seulement les grosses pieces
        if low <= water_y or len(members) >= keep_min:
            kept.append(len(members))
            continue
        entry = next((e for e in keep if all(e[1][0] <= p[0] <= e[1][1] and e[2][0] <= p[1] <= e[2][1]
                                             and e[3][0] <= p[2] <= e[3][1] for p in members)), None)
        if entry is not None:
            kept.append(len(members))
            stem = hang(voxels, forced, members, dims) if entry[4] else 0
            count, blocks, stems = named.get(entry[0], (0, 0, 0))
            named[entry[0]] = (count + 1, blocks + len(members), stems + stem)
            continue
        for p in members:
            voxels.pop(p, None)
            forced.pop(p, None)
        box = (min(p[0] for p in members), max(p[0] for p in members),
               low, max(p[1] for p in members),
               min(p[2] for p in members), max(p[2] for p in members))
        removed.append((len(members), box, low, members))
    print("  elagage   %d composantes : ville %d blocs, %d gardees (dans l'eau ou grosses) %s, %d retirees (%d blocs)" % (
        len(comps), len(comps[main]), len(kept), sorted(kept, reverse=True), len(removed),
        sum(r[0] for r in removed)))
    for label, (count, blocks, stems) in named.items():
        print("  gardee    %-34s %3d piece(s), %4d blocs, tiges %d blocs" % (label, count, blocks, stems))
    return removed, len(comps), 1 + len(kept)


def sealed_pockets(dims, water_y, depth, voxels, forced, sea, edges, barrier_y):
    """L'air de la nappe qui ne touche pas la mer (voir la lecon 11).

    Dans la dimension haven, le generateur a deja mis de l'eau de la cellule
    52 a 57 partout, et la pose saute l'air. Une cellule d'air de la nappe
    serait donc noyee ; on l'ecrit en cave_air, qui se pose, si elle n'est pas
    reliee a l'eau du volume par l'air, en 6-connexite dans la nappe. Le
    rideau du bord compte comme un mur : la mer du dehors ne passe pas.
    """
    w, h, d = dims
    y0 = water_y - depth + 1
    layers = depth
    area = w * d
    band = bytearray(area * layers)            # 0 air, 1 eau, 2 plein
    for (x, z) in sea:
        base = z * w + x
        for k in range(layers):
            band[k * area + base] = 1
    for (x, z) in edges:
        base = z * w + x
        for k in range(layers):
            band[k * area + base] = 1
    for source in (voxels, forced):
        for (x, y, z) in source:
            if y0 <= y <= water_y:
                band[(y - y0) * area + z * w + x] = 2
    if barrier_y is not None:
        for k in range(layers):
            if y0 + k < barrier_y:
                continue
            for x in range(w):
                band[k * area + x] = 2
                band[k * area + (d - 1) * w + x] = 2
            for z in range(d):
                band[k * area + z * w] = 2
                band[k * area + z * w + w - 1] = 2
    air = []
    at = band.find(0)
    while at >= 0:
        air.append(at)
        at = band.find(0, at + 1)
    reached = set()
    queue = deque()
    for i in air:
        k, r = divmod(i, area)
        z, x = divmod(r, w)
        for j, ok in ((i - 1, x > 0), (i + 1, x < w - 1), (i - w, z > 0), (i + w, z < d - 1),
                      (i - area, k > 0), (i + area, k < layers - 1)):
            if ok and band[j] == 1:
                reached.add(i)
                queue.append(i)
                break
    while queue:
        i = queue.popleft()
        k, r = divmod(i, area)
        z, x = divmod(r, w)
        for j, ok in ((i - 1, x > 0), (i + 1, x < w - 1), (i - w, z > 0), (i + w, z < d - 1),
                      (i - area, k > 0), (i + area, k < layers - 1)):
            if ok and band[j] == 0 and j not in reached:
                reached.add(j)
                queue.append(j)
    pockets = set()
    for i in air:
        if i not in reached:
            k, r = divmod(i, area)
            z, x = divmod(r, w)
            pockets.add((x, y0 + k, z))
    print("  poches    %d cellules d'air dans la nappe : %d fermees (cave_air), %d reliees a la mer" % (
        len(air), len(pockets), len(reached)))
    return pockets


def encode(voxels, dims, quay_y, water_y, depth, tops, sea, edges, forced,
           pockets=frozenset(), barrier_y=None):
    """Les plages, en parcourant y puis z puis x.

    L'eau est une nappe de quelques blocs sous la surface, pas une colonne
    jusqu'au fond : remplir la rade jusqu'en bas coute des dizaines de millions
    de blocs pour une image identique depuis la surface.

    Avec `barrier_y`, l'air et l'eau du pourtour de la grille deviennent un
    rideau de barrieres, de cette cellule jusqu'en haut (lecon 11) ; l'air des
    poches fermees de la nappe devient du cave_air. Dans la nappe, le rideau
    est noye : le generateur y a mis de l'eau partout, et une barriere seche y
    creuserait une fente visible.
    """
    w, h, d = dims
    runs = []
    current = None
    count = 0
    for y in range(h):
        flooded = water_y - depth < y <= water_y
        curtain = barrier_y is not None and y >= barrier_y
        for z in range(d):
            rim = curtain and (z == 0 or z == d - 1)
            for x in range(w):
                key = (x, y, z)
                imposed = forced.get(key)
                flat = voxels.get(key)
                if imposed is not None:
                    block = imposed
                elif flat is not None:
                    block = classify(flat, x, y, z, quay_y, tops)
                elif curtain and (rim or x == 0 or x == w - 1):
                    block = WET_BARRIER if flooded else BARRIER
                elif flooded and ((x, z) in sea or (x, z) in edges):
                    block = WATER
                elif flooded and key in pockets:
                    block = CAVE_AIR
                else:
                    block = AIR
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


def write_blob(path, dims, runs, origin=None, cell=1.0):
    """Ecrit le volume, compresse d'un bloc par zlib.

    Le format, gros-boutiste :
      "JAKV", version (1 octet), largeur, hauteur, profondeur (3 entiers) ;
      en version 2 seulement : origine x, y, z et taille de cellule (4 doubles,
      en unites du jeu), puis le sha1 (20 octets) de tout ce qui suit ;
      la palette (compte, puis chaque nom precede de sa longueur) ;
      les plages (compte, puis index de palette sur un octet et longueur en
      varint), parcourues en y, puis z, puis x.

    La version 2 porte l'origine avec les donnees : une salle relevee sur une
    ancienne version du port peut ainsi etre refusee au lieu d'etre posee a
    cote. Sans origine, on ecrit encore la version 1.

    Rend la taille compressee et le sha1 (None en version 1).
    """
    body = bytearray()
    body += struct.pack(">H", len(PALETTE))
    for name in PALETTE:
        raw = name.encode("utf-8")
        body += struct.pack(">H", len(raw)) + raw
    body += struct.pack(">I", len(runs))
    for block, count in runs:
        body += struct.pack(">B", block)
        while True:
            part = count & 0x7F
            count >>= 7
            body += struct.pack(">B", part | (0x80 if count else 0))
            if not count:
                break
    out = bytearray(b"JAKV")
    digest = None
    if origin is None:
        out += struct.pack(">B", 1)
        out += struct.pack(">III", *dims)
    else:
        digest = hashlib.sha1(bytes(body)).digest()
        out += struct.pack(">B", 2)
        out += struct.pack(">III", *dims)
        out += struct.pack(">dddd", origin[0], origin[1], origin[2], cell)
        out += digest
    out += body
    packed = zlib.compress(bytes(out), 9)
    with open(path, "wb") as handle:
        handle.write(packed)
    return len(packed), (digest.hex() if digest else None)


def close_ends(voxels, forced):
    """Mure les bouts des bras au bloc de mur (voir la lecon 11).

    Dans le jeu, les bras continuent vers d'autres quartiers ; ici ils
    s'arretaient net au bord de la grille, sur un quai nu. On ne remplace
    jamais un bloc : seul l'air du plan de fermeture devient du mur.
    """
    total = 0
    for label, (x0, x1), (y0, y1), (z0, z1) in ARM_ENDS:
        posed = 0
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    key = (x, y, z)
                    if key not in voxels and key not in forced:
                        forced[key] = WALL
                        posed += 1
        total += posed
        print("  bout      %-34s %6d blocs de mur" % (label, posed))
    return total


def solid_at(voxels, forced, key):
    return key in voxels or key in forced


def vehicle_extents(prefix):
    """Les cotes des vehicules d'une famille, lues dans l'en-tete de leurs .bin.

    L'en-tete (voir tools/jak_vehicle.py), petit-boutiste : magie "JKVH",
    version, triangles, os, largeur et hauteur de l'atlas, puis les coins
    minimum et maximum de la boite du modele, en metres. Le modele a sa
    largeur en x, sa hauteur en y, sa longueur en z.

    prefix : "car" pour les voitures, "bike" pour les motos.

    Rend (largeur, hauteur, longueur) de la plus grande, cote par cote, et le
    detail par modele.
    """
    header = struct.Struct("<4sIIIII3f3f")
    models = {}
    for path in sorted(glob.glob(os.path.join(VEHICLES, prefix + "*.bin"))):
        with open(path, "rb") as handle:
            fields = header.unpack(handle.read(header.size))
        if fields[0] != b"JKVH":
            sys.exit("%s : ce n'est pas un modele de voiture" % path)
        lo, hi = fields[6:9], fields[9:12]
        models[os.path.splitext(os.path.basename(path))[0]] = tuple(hi[i] - lo[i] for i in range(3))
    if not models:
        sys.exit("aucun modele %s*.bin dans %s" % (prefix, VEHICLES))
    return tuple(max(m[i] for m in models.values()) for i in range(3)), models


def build_apartments(voxels, forced, dims):
    """Ferme trois garages du bras ouest en appartements, et les decrit.

    Le garage est ouvert sur toute sa face ouest. On y pose un mur au bloc de
    mur, en laissant une ouverture de porte centree au ras du sol. Puis on le
    VERIFIE : l'air de l'interieur, porte bouchee, ne doit rejoindre aucune
    cellule hors du garage en 6-connexite. Un appartement qui fuit arrete tout,
    plutot que d'etre livre ouvert.
    """
    w, h, d = dims
    # la place doit contenir chaque voiture, centree ; et le lacet doit bien
    # tourner le nez vers la rue, a l'ouest des portes
    need, models = vehicle_extents("car")
    if need[0] > CAR_WIDTH or need[1] > CAR_HEIGHT or need[2] > CAR_LENGTH:
        sys.exit("place de voiture %d x %d x %d trop petite pour %.2f x %.2f x %.2f m (%s)" % (
            CAR_WIDTH, CAR_HEIGHT, CAR_LENGTH, need[0], need[1], need[2], models))
    bike_need, bike_models = vehicle_extents("bike")
    if bike_need[0] > BIKE_WIDTH or bike_need[1] > BIKE_HEIGHT or bike_need[2] > BIKE_LENGTH:
        sys.exit("place de moto %d x %d x %d trop petite pour %.2f x %.2f x %.2f m (%s)" % (
            BIKE_WIDTH, BIKE_HEIGHT, BIKE_LENGTH, bike_need[0], bike_need[1], bike_need[2], bike_models))
    ahead = (round(-math.sin(math.radians(CAR_YAW))), round(math.cos(math.radians(CAR_YAW))))
    if ahead != (-1, 0):
        sys.exit("lacet %.0f : la voiture garee doit regarder -X, vers la rue des portes" % CAR_YAW)
    print("  voitures  %s ; place %d x %d x %d, lacet %.0f" % (
        ", ".join("%s %.2f x %.2f x %.2f" % (m, e[0], e[1], e[2]) for m, e in sorted(models.items())),
        CAR_WIDTH, CAR_HEIGHT, CAR_LENGTH, CAR_YAW))
    print("  motos     %s ; place %d x %d x %d, a %d cellule(s) de la voiture" % (
        ", ".join("%s %.2f x %.2f x %.2f" % (m, e[0], e[1], e[2]) for m, e in sorted(bike_models.items())),
        BIKE_WIDTH, BIKE_HEIGHT, BIKE_LENGTH, BIKE_GAP))
    rooms = []
    for name, (za, zb) in APARTMENTS:
        mid = (za + zb) // 2
        top = APT_FLOOR_Y + DOOR_HEIGHT
        door = [(APT_FACE_X, y, z) for y in range(APT_FLOOR_Y + 1, top + 1)
                for z in range(mid - DOOR_WIDTH // 2, mid - DOOR_WIDTH // 2 + DOOR_WIDTH)]
        doorset = set(door)
        posed = 0
        for z in range(za, zb + 1):
            for y in range(APT_FLOOR_Y + 1, APT_CEILING_Y):
                key = (APT_FACE_X, y, z)
                if key not in doorset and not solid_at(voxels, forced, key):
                    forced[key] = WALL
                    posed += 1
        for key in door:
            if solid_at(voxels, forced, key):
                sys.exit("%s : l'ouverture de porte %s est bouchee" % (name, key))

        seed = ((APT_INNER_X[0] + APT_INNER_X[1]) // 2, APT_FLOOR_Y + 5, mid)
        inside = {seed}
        queue = deque([seed])
        while queue:
            x, y, z = queue.popleft()
            if not (APT_FACE_X < x <= APT_INNER_X[1] + 2 and za - 1 <= z <= zb + 1
                    and APT_FLOOR_Y < y < APT_CEILING_Y + 1):
                sys.exit("%s fuit : l'air de l'interieur sort en %s" % (name, (x, y, z)))
            for dx, dy, dz in NEIGHBOURS:
                key = (x + dx, y + dy, z + dz)
                if key in inside or key in doorset or solid_at(voxels, forced, key):
                    continue
                inside.add(key)
                queue.append(key)
        lo = [min(p[i] for p in inside) for i in range(3)]
        hi = [max(p[i] for p in inside) for i in range(3)]

        def standable(x, z):
            return (solid_at(voxels, forced, (x, APT_FLOOR_Y, z))
                    and (x, APT_FLOOR_Y + 1, z) in inside and (x, APT_FLOOR_Y + 2, z) in inside)

        spawns = []
        cx = (APT_INNER_X[0] + APT_INNER_X[1]) // 2 + 2
        for want in (mid - 4, mid, mid + 4):
            best = min(((abs(x - cx) + abs(z - want), x, z) for x in range(lo[0], hi[0] + 1)
                        for z in range(za, zb + 1) if standable(x, z)), default=None)
            if best is None:
                sys.exit("%s : aucun sol libre pour un point d'apparition" % name)
            spawns.append((best[1], APT_FLOOR_Y, best[2]))

        # LA PLACE DE VOITURE : une boite d'air libre sur un sol plein, centree
        # sur la porte, la longueur dans le sens du lacet. On sort d'abord par
        # CAR_GAP cellules de passage, puis on recule vers la rue jusqu'a
        # trouver la place.
        zs = range(mid - CAR_WIDTH // 2, mid - CAR_WIDTH // 2 + CAR_WIDTH)
        ys = range(APT_FLOOR_Y + 1, APT_FLOOR_Y + 1 + CAR_HEIGHT)
        passage = [(x, z) for x in range(APT_FACE_X - CAR_GAP, APT_FACE_X)
                   for z in sorted({c[2] for c in door})]
        if any(solid_at(voxels, forced, (x, y, z)) for (x, z) in passage for y in range(APT_FLOOR_Y + 1, top + 1)) \
                or not all(solid_at(voxels, forced, (x, APT_FLOOR_Y, z)) for (x, z) in passage):
            sys.exit("%s : le passage devant la porte n'est pas libre" % name)
        car = None
        for rear in range(APT_FACE_X - 1 - CAR_GAP, APT_FACE_X - 1 - CAR_GAP - 30, -1):
            xs = range(rear - CAR_LENGTH + 1, rear + 1)
            if any(solid_at(voxels, forced, (x, y, z)) for x in xs for y in ys for z in zs):
                continue
            if not all(solid_at(voxels, forced, (x, APT_FLOOR_Y, z)) for x in xs for z in zs):
                continue
            car = {"min": [xs[0], ys[0], zs[0]], "max": [xs[-1], ys[-1], zs[-1]],
                   "floor": [(xs[0] + xs[-1]) // 2, APT_FLOOR_Y, mid], "yaw": CAR_YAW}
            break
        if car is None:
            sys.exit("%s : pas de place de voiture libre devant la porte" % name)

        # LA PLACE DE MOTO : une boite d'air libre sur sol plein a cote de la
        # voiture, +z d'abord. Les cellules entre les deux places sont de l'air
        # sur sol plein elles aussi ; le passage devant la porte reste hors de
        # la boite. Rien n'est pose ni retire : le volume ne change pas.
        passage_cells = {(x, y, z) for (x, z) in passage for y in range(APT_FLOOR_Y + 1, top + 1)}
        bys = range(APT_FLOOR_Y + 1, APT_FLOOR_Y + 1 + BIKE_HEIGHT)
        bike = None
        for side in (1, -1):
            if side > 0:
                bz0 = car["max"][2] + 1 + BIKE_GAP
                between = range(car["max"][2] + 1, bz0)
            else:
                bz0 = car["min"][2] - BIKE_GAP - BIKE_WIDTH
                between = range(bz0 + BIKE_WIDTH, car["min"][2])
            bzs = range(bz0, bz0 + BIKE_WIDTH)
            columns_z = list(bzs) + list(between)
            for rear in range(car["max"][0], car["max"][0] - 30, -1):
                bxs = range(rear - BIKE_LENGTH + 1, rear + 1)
                cells = [(x, y, z) for x in bxs for y in bys for z in columns_z]
                if any(solid_at(voxels, forced, c) or c in passage_cells for c in cells):
                    continue
                if not all(solid_at(voxels, forced, (x, APT_FLOOR_Y, z)) for x in bxs for z in columns_z):
                    continue
                bike = {"min": [bxs[0], bys[0], bzs[0]], "max": [bxs[-1], bys[-1], bzs[-1]],
                        "floor": [(bxs[0] + bxs[-1]) // 2, APT_FLOOR_Y, bzs[0] + BIKE_WIDTH // 2],
                        "yaw": CAR_YAW}
                break
            if bike is not None:
                break
        if bike is None:
            sys.exit("%s : pas de place de moto libre a cote de la voiture" % name)
        print("  logement  %-14s mur %d blocs, porte z %d..%d, air interieur %d cellules sans fuite ;"
              " voiture x %d..%d z %d..%d ; moto x %d..%d z %d..%d ; lacet %.0f" % (
                  name, posed, door[0][2], door[-1][2], len(inside),
                  car["min"][0], car["max"][0], car["min"][2], car["max"][2],
                  bike["min"][0], bike["max"][0], bike["min"][2], bike["max"][2], CAR_YAW))
        rooms.append({
            "id": name,
            "box": {"min": lo, "max": hi},
            "capacity": 3,
            "spawns": [list(s) for s in spawns],
            "door": {"min": [APT_FACE_X, APT_FLOOR_Y + 1, door[0][2]],
                     "max": [APT_FACE_X, top, door[-1][2]]},
            "car": car,
            "bike": bike,
        })
    return rooms


def hq_zone(voxels, forced, columns, bounds, roofs, origin, cell):
    """La zone du QG : l'interieur du Hip Hog, et une place pour l'element de vote.

    La boite est l'emprise de la collision du bar (HHG), du sol a son toit. La
    place du vote est la case de sol libre la plus proche du comptoir
    (hip-tcounter* dans hiphog-background.glb), hors de son emprise.
    """
    own = columns.get("HHG")
    if not own:
        return None
    x0 = min(c[0] for c in own); x1 = max(c[0] for c in own)
    z0 = min(c[1] for c in own); z1 = max(c[1] for c in own)
    y0 = int((bounds["HHG"][0] - origin[1]) / cell)
    y1 = roofs["HHG"]
    path = os.path.join(LEVELS, "hiphog", "hiphog-background.glb")
    verts, faces, names = mesh_triangles(path, with_names=True)
    pts = [verts[k] for i, n in enumerate(names) if "tcounter" in n for k in faces[i]]
    if not pts:
        sys.exit("comptoir du Hip Hog introuvable dans %s" % path)
    cx0 = int((min(p[0] for p in pts) - origin[0]) / cell)
    cx1 = int((max(p[0] for p in pts) - origin[0]) / cell)
    cz0 = int((min(p[2] for p in pts) - origin[2]) / cell)
    cz1 = int((max(p[2] for p in pts) - origin[2]) / cell)
    top = int((max(p[1] for p in pts) - origin[1]) / cell)
    centre = ((cx0 + cx1) / 2.0, (cz0 + cz1) / 2.0)
    best = None
    for x in range(cx0 - 4, cx1 + 5):
        for z in range(cz0 - 4, cz1 + 5):
            if cx0 <= x <= cx1 and cz0 <= z <= cz1:
                continue
            for y in range(y0, top + 1):
                if (solid_at(voxels, forced, (x, y, z)) and not solid_at(voxels, forced, (x, y + 1, z))
                        and not solid_at(voxels, forced, (x, y + 2, z))):
                    # la distance au bord du comptoir d'abord, puis a son centre
                    edge = max(cx0 - x, x - cx1, 0) + max(cz0 - z, z - cz1, 0)
                    score = (edge, abs(x - centre[0]) + abs(z - centre[1]), y)
                    if best is None or score < best[0]:
                        best = (score, (x, y, z))
                    break
    if best is None:
        sys.exit("aucune case libre pres du comptoir du Hip Hog")
    vote = best[1]
    print("  QG        Hip Hog x %d..%d y %d..%d z %d..%d ; comptoir x %d..%d z %d..%d ; vote sur le sol %s" % (
        x0, x1, y0, y1, z0, z1, cx0, cx1, cz0, cz1, vote))
    return {"box": {"min": [x0, y0, z0], "max": [x1, y1, z1]},
            "counter": {"min": [cx0, top, cz0], "max": [cx1, top, cz1]},
            "vote": {"floor": list(vote)}}


def write_rooms(path, rooms, hq, name, origin, cell, dims, digest):
    """Ecrit haven_rooms.json : les appartements et le QG, en cellules du volume.

    Le format est decrit dans le fichier lui-meme (champ "_format"), pour qui
    l'ouvre sans avoir lu ce code.
    """
    doc = {
        "_format": [
            "Salles du port de Haven, ecrites par tools/jak_voxelize.py avec le volume.",
            "Toutes les coordonnees sont des cellules du volume : (x, y, z) depuis le coin",
            "(minx, miny, minz) du .jakv, le meme que JakBuilder pose a son origine.",
            "Un bloc du monde = origine de pose + cellule.",
            "volume, sha1, origin, cell, dims : le .jakv dont ces cellules sont tirees ;",
            "si le sha1 du volume pose differe, les cellules ne sont plus garanties.",
            "rooms[].box : l'air interieur de l'appartement, bornes incluses.",
            "rooms[].capacity : nombre de joueurs.",
            "rooms[].spawns : cellules de SOL ; le joueur se tient en y+1, et y+1, y+2 sont de l'air.",
            "rooms[].door : l'ouverture de porte laissee dans le mur (air), bornes incluses.",
            "rooms[].car : place de voiture devant la porte. min..max est de l'air libre, bornes incluses :",
            "  %d de large, %d de haut, %d de long dans le sens du lacet, sur un sol plein en y = min.y - 1,"
            % (CAR_WIDTH, CAR_HEIGHT, CAR_LENGTH),
            "  a au moins %d cellules de la porte, laissees libres pour sortir." % CAR_GAP,
            "  yaw : lacet Minecraft de la voiture garee, en degres (0 regarde +Z, 90 regarde -X) ;",
            "  le nez vers la rue du bras, l'arriere vers la porte.",
            "  floor : la cellule de sol sous le centre de la place. On pose le centre horizontal de la",
            "  boite du modele (bornes de l'en-tete du .bin) en (x + 0,5, z + 0,5), et son point le plus",
            "  bas sur le sol, en y + 1 : la place contient alors chacune des trois voitures.",
            "rooms[].bike : place de la moto monoplace, a cote de la voiture. min..max est de l'air libre :",
            "  %d de large, %d de haut, %d de long dans le sens du lacet, sur un sol plein en y = min.y - 1,"
            % (BIKE_WIDTH, BIKE_HEIGHT, BIKE_LENGTH),
            "  du cote +z de la place de voiture (-z si +z est pris), a %d cellule(s) d'air sur sol plein" % BIKE_GAP,
            "  de la voiture, hors du passage devant la porte. yaw et floor comme pour la voiture, avec les",
            "  bornes des .bin des motos : la place contient chacune des trois motos.",
            "hq.box : l'emprise du Hip Hog, du sol a son toit, bornes incluses.",
            "hq.counter : l'emprise du comptoir, a la hauteur de son plateau.",
            "hq.vote.floor : cellule de sol libre proposee pour l'element de vote, pres du comptoir.",
        ],
        "volume": name,
        "sha1": digest,
        "origin": list(origin),
        "cell": cell,
        "dims": list(dims),
        "rooms": rooms,
        "hq": hq,
    }
    text = json.dumps(doc, indent=2, ensure_ascii=False)
    # les triplets de coordonnees sur une ligne : le fichier se lit d'un coup d'oeil
    text = re.sub(r"\[\s+(-?[\d.]+),\s+(-?[\d.]+),\s+(-?[\d.]+)\s+\]", r"[\1, \2, \3]", text)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text + "\n")
    print("  salles    %s" % path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("code", help="code de DGO principal, par exemple CPO")
    parser.add_argument("--name", required=True, help="nom de sortie")
    parser.add_argument("--with", dest="extra", nargs="*", default=[],
                        help="DGO a fusionner dans le meme repere")
    parser.add_argument("--cell", type=float, default=1.0)
    parser.add_argument("--water", type=float, default=WATER_HEIGHT,
                        help="altitude de la surface de l'eau, en unites du jeu")
    parser.add_argument("--water-depth", dest="depth", type=int, default=6,
                        help="epaisseur de la nappe, en blocs")
    parser.add_argument("--origin", nargs=3, type=float, default=GRID_ORIGIN,
                        metavar=("X", "Y", "Z"),
                        help="coin de la grille figee, en unites du jeu (defaut : le port)")
    parser.add_argument("--dims", nargs=3, type=int, default=GRID_DIMS,
                        metavar=("W", "H", "D"),
                        help="taille de la grille figee, en cellules (defaut : le port)")
    parser.add_argument("--auto-grid", action="store_true",
                        help="deduire la grille de la collision, pour un autre quartier")
    parser.add_argument("--pad-top", type=int, default=0,
                        help="cellules ajoutees en haut de la grille, sans toucher l'origine")
    parser.add_argument("--keep-detached", type=int, default=500,
                        help="taille minimale d'une piece detachee gardee si elle ne touche pas l'eau "
                             "(celles qui plongent dans l'eau restent toutes) ; -1 n'elague rien")
    parser.add_argument("--dump-pruned", help="fichier JSON ou lister les pieces retirees")
    parser.add_argument("--dump-cables", help="fichier JSON ou lister les cellules des cables poses")
    parser.add_argument("--no-haven", action="store_true",
                        help="sans les retouches du port pour la dimension haven : tours, bouts "
                             "des bras, appartements, poches, rideau de barrieres")
    parser.add_argument("--rooms", help="ecrire les salles et le QG dans ce JSON (a cote du volume)")
    parser.add_argument("--out-dir", default=OUT_DIR,
                        help="dossier du volume et des salles (defaut : les donnees du mod)")
    args = parser.parse_args()

    codes = [args.code] + list(args.extra)
    verts, faces, groups, bounds = load_all(codes)
    print("  total  %d triangles" % len(faces))

    if args.auto_grid:
        dims, origin = grid(verts, args.cell)
    else:
        origin = tuple(args.origin)
        dims = tuple(args.dims)
        fixed_grid(verts, origin, dims, args.cell)
    dims = (dims[0], dims[1] + args.pad_top, dims[2])
    voxels = {}
    columns = {}
    rasterize(voxels, verts, faces, args.cell, dims, origin, groups, columns)
    print("  grille    %d x %d x %d" % dims)
    print("  origine   %.4f %.4f %.4f" % origin)
    print("  surfaces  %d voxels" % len(voxels))
    print("  bouches   %d trous d'un bloc" % pinholes(voxels, dims))

    quay_y = int((QUAY_HEIGHT - origin[1]) / args.cell)
    water_y = int((args.water - origin[1]) / args.cell)
    floor_y = water_y - args.depth + 1
    print("  quais y=%d, eau y=%d, fond de nappe y=%d (en cellules)" % (quay_y, water_y, floor_y))

    # sous la ville : le decor visuel, la ou la collision s'arrete (lecon 6)
    below = origin[1] + (quay_y + 2) * args.cell
    vverts, vfaces = load_visuals(codes, below, origin, dims, args.cell)
    visual = {}
    rasterize(visual, vverts, vfaces, args.cell, dims, origin)
    pinholes(visual, dims)
    forced = underside(visual, voxels, quay_y, floor_y)
    print("  dessous   %d blocs repris du decor" % len(forced))

    roofs = {code: int((hi - origin[1]) / args.cell) - 1 for code, (lo, hi) in bounds.items()}
    forced.update(cap_interiors(voxels, columns, codes, roofs))

    def flood():
        solids = list(voxels) + list(forced)
        occupied = {(x, z) for (x, y, z) in solids}
        surface = {(x, z) for (x, y, z) in solids if y == water_y}
        sea = water_mask(dims, water_y, occupied, surface)
        covered = sum(1 for c in sea if c in occupied)
        return sea, covered

    # la mer une premiere fois, pour savoir quels bords donnent sur l'eau
    # libre ; puis le rideau ; puis la mer a nouveau, arretee par le rideau
    sea, covered = flood()
    curtain = curtains(voxels, forced, sea, quay_y, water_y, floor_y)
    forced.update(curtain)
    print("  rideau    %d blocs sous le bord exterieur" % len(curtain))

    # les retouches du port pour la dimension haven (lecons 9 et 11)
    haven = args.code == "CPO" and not args.auto_grid and not args.no_haven
    if haven:
        complete_towers(voxels, forced, dims, origin, args.cell, floor_y)
    if args.keep_detached >= 0:
        removed, before, after = prune_detached(voxels, forced, dims, water_y, args.keep_detached,
                                                KEEP_DETACHED if haven else ())
        if args.dump_pruned:
            with open(args.dump_pruned, "w", encoding="utf-8") as handle:
                json.dump({"components_before": before, "components_after": after,
                           "removed": [{"size": n, "box": list(box), "low": low, "cells": sorted(cells)}
                                       for n, box, low, cells in removed]}, handle, indent=1)
    if haven:
        cables = lay_cables(voxels, forced, dims, origin, args.cell)
        if args.dump_cables:
            with open(args.dump_cables, "w", encoding="utf-8") as handle:
                json.dump({"cells": sorted(cables)}, handle)
    rooms = hq = None
    if haven:
        close_ends(voxels, forced)
        rooms = build_apartments(voxels, forced, dims)
        hq = hq_zone(voxels, forced, columns, bounds, roofs, origin, args.cell)

    # les sommets APRES l'elagage et les ajouts : un anneau retire laissait
    # sinon le toit de sa tour classe en sol (lecon 9)
    _, tops = column_extremes(voxels)

    sea, covered = flood()
    edges = water_edges(sea, dims)
    print("  mer       %d colonnes, dont %d sous un plancher ; bords %d" % (len(sea), covered, len(edges)))

    barrier_y = floor_y if haven else None
    pockets = (sealed_pockets(dims, water_y, args.depth, voxels, forced, sea, edges, barrier_y)
               if haven else frozenset())
    runs = encode(voxels, dims, quay_y, water_y, args.depth, tops, sea, edges, forced,
                  pockets, barrier_y)
    os.makedirs(args.out_dir, exist_ok=True)
    path = os.path.join(args.out_dir, "%s.jakv" % args.name)
    packed, digest = write_blob(path, dims, runs, origin, args.cell)
    print("  plages    %d" % len(runs))
    print("  sha1      %s" % digest)
    print("  fichier   %s  (%.1f Mo compresses)" % (path, packed / 1048576.0))
    if haven and args.rooms:
        write_rooms(os.path.join(args.out_dir, args.rooms), rooms, hq,
                    os.path.splitext(os.path.basename(path))[0], origin, args.cell, dims, digest)


if __name__ == "__main__":
    main()
