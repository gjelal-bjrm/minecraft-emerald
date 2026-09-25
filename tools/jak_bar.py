"""Le QG en blocs : le bar du Hip Hog de Jak 3, version Minecraft (cahier §103).

« Le vrai decor, mais en version Minecraft, car ca denature trop le jeu quand ce n'est pas en
blocs » (le joueur, 25 sept.). Le QG etait la collision du bar posee en trois pierres (lecon 5 de
tools/jak_voxelize.py) : « juste des blocs de pierre, on ne peut pas le laisser comme ca ».

CE QUE LES MAQUETTES ONT APPRIS (rendus du 25 sept., avant tout bloc en jeu) :

1. LA FORME RESTE, LA TEXTURE CHANGE. La collision du bar EST sa forme : sol, murs, comptoir,
   marches, caisses, tables, tonneaux (98 des 109 cellules des caisses sont deja pleines). Tout ce
   qui s'y appuie reste juste -- la porte, le comptoir, Torn, le ratelier, la borne, les cellules
   que le joueur a posees dans l'atelier. Chaque cellule pleine garde sa place ; sa matiere vient de
   son role (lecon 6), le decor visuel (hiphog-background.glb, rasterise comme la collision) disant
   ce qui est un objet : caisse, tonneau, table, tabouret.
2. LE DECOR CONVERTI AU BLOC PRES EST DU BRUIT. Le bar est tourne de 33,6 degres sur la grille ;
   une toiture en pente fine, posee de biais, fait une marche a chaque colonne -- un nid
   d'abeilles -- et des poutres d'un metre, en biais, sortent en troncons epars. D'ou une toiture
   EN PALIERS (cinq, de deux blocs, contremarches fermees), unie ; les poutres ne sont
   gardees que sur les murs, dans leurs piliers.
3. LES OBJETS FINS SONT DES BLOCS DESSINES. Un tabouret de cinquante centimetres, une etagere de
   bouteilles, une lampe suspendue ne tiennent pas dans un cube : tabourets, tabourets hauts,
   tables hautes et lampes suspendues sont des modeles, poses a la place de chaque objet du jeu ;
   les bouteilles, des rangs de flacons sur le mur jaune du bar. Les lampes du jeu, elles, sont de grands
   caissons poses sur la toiture : a plat dans le toit, deux blocs sur deux de lampe ambre.
4. LES ALCOVES SE VOIENT, ET L'ON Y ENTRE. Les longs murs ont quatre alcoves chacun, derriere une
   ouverture en trou de serrure (une fenetre ronde de cinq metres, un passage de deux metres et demi
   dessous). En blocs, « une entree en cercle » (le joueur) : un cercle de cinq blocs dans le mur, le bas
   sur l'allee, cercle de chrome -- on y entre (son choix). La collision n'en donnait ni le fond ni
   tout le plafond (le vide se voyait derriere) : les alcoves sont baties entieres, avec des murs d'un
   metre trois, sans quoi la grille de biais laissait des fentes.
5. UN BLOC PAR ROLE : 26 matieres et 4 objets, pas 99 textures.
6. CHAQUE ELEMENT SE RECONNAIT. « Au centre, un carre rouge ; dedans, une table ronde et deux cartons
   dans un coin ; sur les cotes, huit petites salles a l'entree ronde, une table, des chaises sur
   les cotes ; tout au fond, le bar » (le joueur, sur les premieres images, ou la texture la plus
   proche de chaque bloc noyait tout). La matiere se choisit donc par ROLE et par REGION du plan --
   le fond en metal, l'anneau de moquette releve d'une demi-marche, l'allee, les murs (socle rouge,
   mur jaune, piliers et leurs capsules entre les alcoves), les alcoves (moquette, banquettes en
   escaliers face a face, table au milieu, lampe bleue au plafond), le comptoir et ses etageres --,
   et la table ronde, les deux caisses et les meubles des alcoves sont poses expres.
7. LA LUMIERE DESCEND. Les lampes suspendues pendent a dix blocs du sol, comme dans le jeu : en jeu,
   leur lumiere s'eteignait avant le sol, et le bar etait noir (photos). Des lumieres invisibles de
   Minecraft la portent plus bas : sous chaque lampe, dans les alcoves, sur la table ronde, devant le
   comptoir, aux coins du carre, dans le renfoncement et l'entree.
8. L'ENVELOPPE EST FERMEE. Un remplissage de l'air depuis la salle, porte bouchee, sortait du
   batiment (photos : le ciel par des trous) : la collision avait des vides entre les alcoves, la
   grille de biais ne donnait aucune cellule a certaines rangees d'une bande trop mince, et vider les
   cubes d'un tabouret haut ouvrait un puits dans l'allee (un bloc au-dessus de l'eau). Les longs murs
   sont pleins sur plus de deux metres, du sol a l'avant-toit, perces des seules entrees rondes ; les
   meubles ne vident jamais le sol ou ils posent ; l'outil refait ce remplissage et s'arrete sur une
   fuite (sealed).
9. CE QUI BRILLE ET CE QUI PEND. Le chrome du jeu (hip-tmetring02, un metal a reflets) passait, en
   aplat gris clair, pour un miroir aux shaders : les cercles des alcoves montraient le ciel, comme
   des trous (photos). C'est l'acier brosse des portes du Hip Hog. Et la toiture qui monte laissait
   flotter ce que le joueur avait pendu au plafond plat (sa lampe-ventilateur) : une chaine l'y
   rattache. Ses cellules (le releve de la ville) ne sont jamais touchees.

La toiture monte au vrai faitage (cellule 85) : le bar est plus haut qu'avant, et se voit de la rue.
Aucune voie du trafic ne passe au-dessus.

Sorties :
  - data/emeraldweapons/jak/haven_bar.json : chaque cellule du volume que le bar change (etat voulu,
    etat du volume a cet endroit) ; HavenBar la pose apres la ville, et au demarrage d'une ville deja
    posee, sans toucher a ce que le joueur y a mis ;
  - les blocs : textures (block/hiphog_*.png, 16 x 16), modeles, etats, objets, butins.

    python tools/jak_bar.py
"""

import base64
import collections
import io
import json
import math
import os
import sys

from PIL import Image

import haven_window
import jak_preview
import nbt_structure
from jak_assets import LEVELS, read_glb

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons")
OUT_JSON = os.path.join(DATA, "jak", "haven_bar.json")
CABLES_JSON = os.path.join(DATA, "jak", "haven_cables.json")
USER_ZONE = os.path.join(DATA, "jak", "zones", "ctyport", "ville.nbt")
GLB = os.path.join(LEVELS, "hiphog", "hiphog-background.glb")

GRID_ORIGIN = (-434.7777, -57.50163, 1126.3511)   # jak_voxelize.GRID_ORIGIN
# le repere du bar : u le long (comptoir vers -21, entree vers +22), v en travers
CENTER = (-87.41, 1305.63)
EU = (math.cos(math.radians(57.0)), math.sin(math.radians(57.0)))
EV = (-EU[1], EU[0])
EAVE_CELL = 76
U_BACK_GABLE = -20.5
U_FRONT_GABLE = 20.3
WALL_V = 12.6
WALL_OUT = 13.6
POD_V = 19.6
TERRACES = 5
REGION = (-24.6, 28.3, POD_V + 0.3)
# le volume autour du QG (hq.box de haven_rooms.json, elargie)
BOX = (316, 378, 147, 213)
STONE = ("minecraft:polished_andesite", "minecraft:deepslate_bricks", "minecraft:deepslate_tiles")
# ce que le mod pose ou tient dans le bar (Haven.BAR_DOOR_CELL et HavenDoors.BAR, HavenRack.CELL, Torn,
# le bouton du comptoir, la borne sur hq.vote.floor) : rien ne s'y pose
ANCHORS = ((331, 69, 166), (334, 69, 166), (333, 69, 167), (335, 68, 168))
DOOR_BOX = ((358, 364), (66, 72), (194, 201))

BLOCKS = [
    # id, texture de Jak 3, lumiere, rendu
    ("roof", "hip-troofmetal01", 0, "solid"),
    ("green_metal", "hip-tgreenmetal01", 0, "solid"),
    ("yellow_wall", "hip-tyellwall01", 0, "solid"),
    ("yellow_bricks", "hip-tyellwall02", 0, "solid"),
    ("yellow_metal", "hip-tyellmetal01", 0, "solid"),
    ("red_metal", "hip-tredmetal09", 0, "solid"),
    ("red_panel", "hip-tredmetal04", 0, "solid"),
    ("carpet", "hip-tred-check01", 0, "solid"),
    ("step", "hip-tred-step01", 0, "solid"),
    ("metal_floor", "hip-tmetfloor01", 0, "solid"),
    ("floor_grate", "hip-tmetfloor11", 0, "solid"),
    ("pillar", "hip-tpillerpaint06", 0, "solid"),
    ("chrome", "hip-tmetring02", 0, "solid"),
    ("wood", "hip-twood02", 0, "solid"),
    ("booth", "hip-tbooth01", 0, "solid"),
    ("grey_metal", "hip-tmetbooth01", 0, "solid"),
    ("counter", "hip-tcounter02", 0, "solid"),
    ("crate", "hip-crate-body", 0, "solid"),
    ("curtain", "hip-curtain", 0, "solid"),
    ("glass", None, 0, "translucent"),
    ("blue_lamp", "hip-tbluelit01", 12, "solid"),
    ("red_lamp", "hip-tredlight01", 10, "solid"),
    ("amber_lamp", "hip-tamblit01", 14, "solid"),
    ("bottle_shelf", None, 0, "solid"),
    ("carpet_slab", "carpet", 0, "slab"),
    ("booth_stairs", "booth", 0, "stairs"),
]
MODELS = [("stool", 0), ("high_stool", 0), ("high_table", 0), ("hanging_lamp", 15)]
# ce qui n'arrete pas le regard (le controle d'etancheite, sealed)
PASSABLE = {m[0] for m in MODELS} | {"carpet_slab", "booth_stairs", "glass"}
# les textures dont le motif, repete a chaque bloc, faisait une grille (le toit raye), et le toit
# assombri : il s'efface, comme dans le jeu, derriere les murs et les lampes
SMOOTH = {"hip-troofmetal01": 0.75}
DARKEN = {"hip-troofmetal01": 0.85}

ROLE = {}


def _role(block, *names):
    for n in names:
        ROLE[n] = block


_role("roof", "hip-troofmetal01")
_role("green_metal", "hip-tgreenmetal01", "hip-tgreendark01", "hip-tgreenmed01")
_role("yellow_wall", "hip-tyellwall01", "hip-tyellwall03", "hip-tyellwall04")
_role("yellow_bricks", "hip-tyellwall02")
_role("yellow_metal", "hip-tyellmetal01", "hip-tyellmetal02", "hip-tyellmetal03", "hip-tyellmetal04")
_role("red_metal", "hip-tredmetal09", "hip-tredmetal01", "hip-tredmetal03", "hip-tredmed01", "hip-treddark01",
      "hip-tred-trim01", "hip-tred-trim02", "hip-tred-trim03")
_role("red_panel", "hip-tredmetal04")
_role("carpet", *["hip-tred-check%02d" % i for i in range(1, 13)])
_role("step", "hip-tred-step01", "hip-tred-step02", "hip-tred-step03", "hip-tred-step04", "hip-tred-step05",
      "hip-tred-step06", "hip-tred-steptrim01")
_role("metal_floor", "hip-tmetfloor01", "hip-tmetfloor02", "hip-tmetfloor03", "hip-tmetfloor04", "hip-tmetfloor06",
      "hip-tmetfloor12", "hip-tmetfloor13", "hip-tmetfloor-vent04")
_role("floor_grate", "hip-tmetfloor11")
_role("pillar", *["hip-tpillerpaint%02d" % i for i in range(1, 7)])
_role("chrome", "hip-tmetring01", "hip-tmetring02", "hip-tmetring02-envmap", "hip-tgoldring01")
_role("wood", "hip-twood01", "hip-twood02")
_role("booth", "hip-tbooth01", "hip-tbooth02")
_role("grey_metal", "hip-tmetbooth01", "hip-tmetcan01", "placeholder-white", "hip-carawing01", "gun-gunrack-01",
      "gun-gunrack-02", "hip-gun-gray-01", "hip-gun-gray-02", "hip-gun-main", "hip-gun-barrel-01",
      "hip-gun-barrel-alt", "hip-gun-leather", "hip-gun-magport", "hip-gun-cover", "hip-gun-pump",
      "hip-gun-dark-mag")
_role("counter", "hip-tcounter01", "hip-tcounter02", "hip-tcounter03", "hip-tcounter04", "hip-temp-02")
_role("crate", "hip-crate-body")
_role("curtain", "hip-curtain")
_role("glass", "hip-glass-shard-01-envmap", "hip-glass-shard-01")
_role("blue_lamp", "hip-tbluelit01", "hip-blue-light", "hip-tbluecup", "hip-tboothlight01", "hip-tgreenlite01")
_role("red_lamp", "hip-tredlight01", "hip-tredlite01")
_role("amber_lamp", "hip-tamblit01")
_role("bottle_shelf", "hip-tbotyel01", "hip-tbotblue01", "hip-tbotblue02", "hip-tbotred01")

# les objets du jeu poses en blocs dessines
PROPS = {"hip-stool.mb": "stool", "hip-stool-high.mb": "high_stool", "hip-table-high.mb": "high_table"}
LAMPS = "hip-roof-light.mb"
ROUND_TABLE = "hip-table.mb"
FLOOR_LIGHTS = "hip-floorlight.mb"
# les objets de la collision qui gardent leur forme, a la matiere de leur role
PROP_BLOCK = {"hip-crate-body.mb": "crate", "hip-barrel-b.mb": "red_panel", "hip-gun-1.mb": "grey_metal",
              "hip-gun-2.mb": "grey_metal", "hip-screen.mb": "blue_lamp",
              "hip-button.mb": "red_lamp", "hip-car-part-1.mb": "grey_metal", "hip-car-part-3.mb": "grey_metal",
              "hip-wall-mount.mb": "red_metal", "hip-trophy.mb": "yellow_metal", "hip-plaque.mb": "yellow_metal"}

# LE PLAN DU BAR (lecon 6), dans son repere, mesure sur le decor du jeu
ARCHES = (-20.5, -10.5, -0.5, 9.5, 19.5)     # les piliers entre les alcoves (hip-pilar.mb)
PODS = (-15.5, -5.5, 4.5, 14.5)              # le milieu des alcoves, des deux cotes (hip-booth-b.mb)
SUNKEN = (-5.5, 14.5, 6.3)                   # le fond en metal : de u, a u, |v| sous (hip-tmetfloor06/12/02)
SUNKEN_Y = 66                                # sa cellule de sol
RING = (-9.5, 16.5, 9.0)                     # le carre rouge autour (hip-tred-step02..05)
TABLE = (3.9, -0.2, 3.1)                     # la table ronde : u, v, rayon (hip-table.mb)
CRATES_AT = (-4.4, 4.7)                      # les deux caisses du coin (hip-crate-body.mb)
CRATES = ((-5.0, 4.4), (-3.8, 5.0))
COUNTER = (-20.3, -18.9, 7.5)                # le comptoir a batir : de u, a u, |v| sous (hip-tcounter*)
COUNTER_Y = 68                               # un bloc de haut, sur l'allee (dessus 69)
RECESS_U = -21.5                             # derriere : le renfoncement des etageres
# les appuis de ce que le mod pose : ils restent pleins (le bouton du QG sur le bout du comptoir,
# haven_invasion.json ; le ratelier sur son socle derriere le comptoir, HavenRack.CELL)
SUPPORTS = ((333, 68, 167), (331, 68, 166))
# la place de Torn (HavenNpcs, ancre 334 69 166) : il y cherche le sol le plus proche ; le cube de
# tabouret qui le portait s'en va, et rien ne se pose a ses pieds
TORN_FEET = (334, 68, 166)
# une alcove (hip-booth-b.mb), dans le repere : sa moitie de largeur interieure, sa profondeur (|v|)
POD_HALF = 2.9
POD_FRONT = WALL_OUT + 0.4
POD_BACK = 18.9
POD_FLOOR = 67                               # le sol de l'allee : on y entre de plain-pied
POD_CEILING = 73
POD_TABLE_V = 16.6                           # la table, au milieu de la profondeur
DOOR_R = 2.5                                 # l'entree ronde d'une alcove : rayon, en blocs
DOOR_Y = 70.5                                # son centre : le bas du cercle touche l'allee (68)
DOOR_FRAME = 3.3                             # le cercle de chrome autour
# LA LUMIERE (photos du 25 sept. : le bar etait noir). Les lampes suspendues pendent a dix blocs du
# sol, comme dans le jeu : leur lumiere s'eteignait avant d'y arriver. Des lumieres invisibles de
# Minecraft la portent plus bas -- sous chaque lampe, dans les alcoves, sur la table ronde, devant
# le comptoir, aux coins du carre, dans le renfoncement et l'entree.
LIGHT = "minecraft:light[level=15]"
CHAIN = "minecraft:chain[axis=y]"
LAMP_HALF = 1.1                              # le caisson de lumiere : demi-cote, en metres
LIGHT_Y = 71                                 # trois blocs au-dessus de l'allee : le sol recoit 12
LIGHTS_AT = ((-18.5, -5.0), (-18.5, 0.0), (-18.5, 5.0),        # devant le comptoir
             (-22.3, -4.0), (-22.3, 4.0),                        # le renfoncement des bouteilles
             (-8.0, -7.0), (-8.0, 7.0), (15.0, -7.0), (15.0, 7.0),   # les coins du carre rouge
             (TABLE[0], TABLE[1]),                               # la table ronde
             (23.0, 0.0))                                        # l'entree
# et une grille sur toute la salle, tous les six metres : le sol n'avait de lumiere que pres des lampes
LIGHTS_AT += tuple((gu, gv) for gu in (-15.0, -9.0, -3.0, 3.0, 9.0, 15.0) for gv in (-6.0, 0.0, 6.0))


# ============================================================ le decor du jeu

def uv_of(x, z):
    dx, dz = x - CENTER[0], z - CENTER[1]
    return dx * EU[0] + dz * EU[1], dx * EV[0] + dz * EV[1]


def uvc(cx, cz):
    return uv_of(cx + 0.5 + GRID_ORIGIN[0], cz + 0.5 + GRID_ORIGIN[2])


def cell_at(u, v):
    """La colonne de la grille ou tombe ce point du repere du bar."""
    x = CENTER[0] + EU[0] * u + EV[0] * v
    z = CENTER[1] + EU[1] * u + EV[1] * v
    return int(math.floor(x - GRID_ORIGIN[0])), int(math.floor(z - GRID_ORIGIN[2]))


def cell_of(p):
    return tuple(int(math.floor(p[k] - GRID_ORIGIN[k])) for k in range(3))


def _floats(js, blob, index, width):
    import struct
    acc = js["accessors"][index]
    view = js["bufferViews"][acc["bufferView"]]
    start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    stride = view.get("byteStride", 4 * width)
    out = []
    for i in range(acc["count"]):
        out.append(struct.unpack_from("<%df" % width, blob, start + i * stride))
    return out


def _indices(js, blob, index):
    import struct
    acc = js["accessors"][index]
    view = js["bufferViews"][acc["bufferView"]]
    start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    kind = {5125: "I", 5123: "H", 5121: "B"}[acc["componentType"]]
    return struct.unpack_from("<%d%s" % (acc["count"], kind), blob, start)


def load_bar():
    """Les triangles du bar (noeud, nom de texture, sommets) et ses textures (nom -> image RGBA)."""
    js, blob = read_glb(GLB)
    images = {}
    for img in js["images"]:
        data = base64.b64decode(img["uri"].split(",", 1)[1])
        images[img["name"]] = Image.open(io.BytesIO(data)).convert("RGBA")
    names = [m.get("name") for m in js["materials"]]
    tris = []
    cache = {}
    for node in js["nodes"]:
        if "mesh" not in node:
            continue
        if any(k in node for k in ("matrix", "translation", "rotation", "scale")):
            sys.exit("%s : le noeud %s est transforme, non gere" % (GLB, node.get("name")))
        for prim in js["meshes"][node["mesh"]]["primitives"]:
            a = prim["attributes"]["POSITION"]
            if a not in cache:
                cache[a] = _floats(js, blob, a, 3)
            pos = cache[a]
            idx = _indices(js, blob, prim["indices"])
            mat = names[prim["material"]] if prim.get("material") is not None else None
            for i in range(0, len(idx) - 2, 3):
                tris.append((node.get("name"), mat, (pos[idx[i]], pos[idx[i + 1]], pos[idx[i + 2]])))
    return tris, images


def voxelize(tris):
    """Chaque cellule traversee par le decor : sa texture dominante et son noeud (lecon 1 du voxeliseur,
    plus le centre de chaque petit triangle, pour que les petits objets ne disparaissent pas)."""
    ox, oy, oz = GRID_ORIGIN
    weights = collections.defaultdict(lambda: collections.defaultdict(float))
    nodes = collections.defaultdict(lambda: collections.defaultdict(float))
    for name, mat, ps in tris:
        a, b, c = [(p[0] - ox, p[1] - oy, p[2] - oz) for p in ps]
        ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
        vx, vy, vz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
        nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
        n2 = math.sqrt(nx * nx + ny * ny + nz * nz)
        if n2 < 1e-12:
            continue
        nx, ny, nz = nx / n2, ny / n2, nz / n2
        if abs(ny) >= abs(nx) and abs(ny) >= abs(nz):
            drop, u1, u2 = 1, 0, 2
        elif abs(nx) >= abs(nz):
            drop, u1, u2 = 0, 1, 2
        else:
            drop, u1, u2 = 2, 0, 1
        dom = abs((nx, ny, nz)[drop])
        au, av, aw = a[u1], a[u2], a[drop]
        bu, bv, bw = b[u1], b[u2], b[drop]
        cu, cv, cw = c[u1], c[u2], c[drop]
        det = (bu - au) * (cv - av) - (cu - au) * (bv - av)
        hits = []
        if abs(det) > 1e-9:
            for iu in range(int(min(au, bu, cu)), int(max(au, bu, cu)) + 2):
                for iv in range(int(min(av, bv, cv)), int(max(av, bv, cv)) + 2):
                    su, sv = iu + 0.5, iv + 0.5
                    s = ((su - au) * (cv - av) - (cu - au) * (sv - av)) / det
                    t = ((bu - au) * (sv - av) - (su - au) * (bv - av)) / det
                    if s < -0.05 or t < -0.05 or s + t > 1.05:
                        continue
                    iw = int(aw + s * (bw - aw) + t * (cw - aw))
                    hits.append((iu, iw, iv) if drop == 1 else (iw, iu, iv) if drop == 0 else (iu, iv, iw))
        w = 1.0 / max(dom, 1e-3)
        if not hits:
            hits = [tuple(int((a[k] + b[k] + c[k]) / 3.0) for k in range(3))]
            w = n2 / 2.0
        for key in hits:
            weights[key][mat] += w
            nodes[key][name] += w
    cells = {}
    for key, per in weights.items():
        cells[key] = (max(per.items(), key=lambda kv: kv[1])[0], max(nodes[key].items(), key=lambda kv: kv[1])[0],
                      sum(per.values()))
    return cells


def pieces(tris, node):
    """Les pieces reliees d'un noeud (sommets communs) : un objet du jeu chacune."""
    own = [t for t in tris if t[0] == node]
    parent = list(range(len(own)))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    owner = {}
    for i, t in enumerate(own):
        for p in t[2]:
            j = owner.setdefault((round(p[0], 3), round(p[1], 3), round(p[2], 3)), i)
            a, b = find(i), find(j)
            if a != b:
                parent[a] = b
    groups = collections.defaultdict(list)
    for i, t in enumerate(own):
        groups[find(i)].append(t)
    out = []
    for ts in groups.values():
        ps = [p for t in ts for p in t[2]]
        lo = [min(p[k] for p in ps) for k in range(3)]
        hi = [max(p[k] for p in ps) for k in range(3)]
        out.append({"center": [(lo[k] + hi[k]) / 2.0 for k in range(3)], "lo": lo, "hi": hi})
    return out


def load_volume():
    vol = jak_preview.read_volume("ctyport")
    cells, bw, bd = jak_preview.dense(vol["dims"], vol["runs"], BOX)
    x0, x1, z0, z1 = BOX
    out = {}
    for y in range(vol["dims"][1]):
        for z in range(z0, z1 + 1):
            base = (y * bd + (z - z0)) * bw
            for x in range(x0, x1 + 1):
                b = cells[base + x - x0]
                if b:
                    out[(x, y, z)] = vol["palette"][b]
    return vol["sha1"], out


def load_cables():
    with open(CABLES_JSON, encoding="utf-8") as f:
        return {(c[0], c[1], c[2]) for c in json.load(f)["cells"]}


# ============================================================ le bar en blocs

def terrace_top(v):
    a = abs(v)
    if a >= WALL_V:
        return EAVE_CELL + 1
    k = min(TERRACES - 1, int((WALL_V - a) / (WALL_V / TERRACES)))
    return EAVE_CELL + 1 + 2 * k


def protected():
    """Les cellules ou rien ne se pose (elles restent vides) : les places du mod et la porte du bar."""
    out = {TORN_FEET}
    for (x, y, z) in ANCHORS:
        for dy in range(4):
            out.add((x, y + dy, z))
    (xa, xb), (ya, yb), (za, zb) = DOOR_BOX
    for x in range(xa, xb + 1):
        for y in range(ya, yb + 1):
            for z in range(za, zb + 1):
                out.add((x, y, z))
    return out


def region(u, v):
    if u > U_FRONT_GABLE + 0.2:
        return "vestibule"
    if u < RECESS_U:
        return "recess"
    if abs(v) > WALL_OUT + 0.4:
        return "pod"
    if abs(v) >= WALL_V - 0.6:
        return "wall"
    return "room"


def room_floor(u, v):
    """La matiere du sol de la salle (lecon 6) : le fond en metal, le carre rouge, l'allee."""
    if u < -16.5:
        return "carpet"                   # devant le comptoir, la moquette du bar
    if SUNKEN[0] <= u <= SUNKEN[1] and abs(v) < SUNKEN[2]:
        return "metal_floor"
    if RING[0] <= u <= RING[1] and abs(v) < RING[2]:
        return "carpet"
    return "floor_grate" if abs(v) > 9.5 else "metal_floor"


def wall_block(u, y, v):
    """Le mur, de bas en haut : le socle rouge, le mur jaune, les piliers entre les alcoves et leurs capsules."""
    if any(abs(u - a) < 1.0 for a in ARCHES):
        if 71 <= y <= 72 and abs(v) < WALL_V + 0.4:
            return "red_lamp"
        return "pillar"
    if y <= 68:
        return "red_metal"
    if y >= 74:
        return "yellow_metal"
    return "yellow_wall"


def pod_of(u, v):
    """L'alcove de cette colonne (son milieu en u, son cote), ou None."""
    if abs(v) <= WALL_OUT + 0.4 or abs(v) > POD_V + 0.3:
        return None
    for pu in PODS:
        if abs(u - pu) <= 3.6:
            return pu, 1 if v > 0 else -1
    return None


def load_user():
    """Les cellules que le joueur a relevees dans l'atelier (le releve de la ville, dans le mod) : etat par cellule."""
    if not os.path.exists(USER_ZONE):
        return {}
    with open(USER_ZONE, "rb") as f:
        name, root = nbt_structure.parse(f.read())
    return {tuple(c["pos"]): root["palette"][c["state"]] for c in root["cells"]}


def build(tris, vox, cur, cables, user):
    """{cellule: etat} du bar, et les cellules du QG actuel qui deviennent de l'air (lecons 1, 2, 4, 6, 9)."""
    # jamais touchees : les cables, que HavenCables vide, et les cellules du joueur (son releve passe
    # apres le bar, mais une lumiere invisible ou une chaine ne doit pas prendre sa place)
    fixed = cables | set(user)
    no_fill = protected()                 # jamais remplies : les places du mod, la porte
    out = {}
    air = set()

    def put(key, state):
        if key in fixed or (key in no_fill and key not in cur):
            return
        out[key] = state
        air.discard(key)

    def clear(key):
        if key in fixed:
            return
        out.pop(key, None)
        if key in cur:
            air.add(key)

    def roofed(u, v):
        return U_BACK_GABLE <= u <= U_FRONT_GABLE and abs(v) < WALL_OUT

    shape = {}
    for key, block in cur.items():
        x, y, z = key
        if block not in STONE or not 66 <= y <= 77:
            continue
        u, v = uvc(x, z)
        if REGION[0] <= u <= REGION[1] and abs(v) <= REGION[2]:
            shape[key] = (u, v)
    # le haut du sol de chaque colonne : le plus haut plein, sous 70, avec deux cellules vides au-dessus
    floor_top = {}
    for (x, y, z) in sorted(shape, key=lambda k: k[1]):
        if y <= 69 and (x, y + 1, z) not in cur and (x, y + 2, z) not in cur:
            floor_top[(x, z)] = y
    # 1. la forme actuelle, chaque bloc a la matiere de son role
    for key, (u, v) in shape.items():
        x, y, z = key
        block = cur[key]
        if y >= 74 and block == "minecraft:deepslate_tiles" and roofed(u, v) and abs(v) < WALL_V - 0.4:
            clear(key)                    # le plafond plat de la salle : la toiture le remplace
            continue
        if key in SUPPORTS:
            put(key, "counter")           # le bout du comptoir, le socle du ratelier
            continue
        node = vox.get(key, (None, None))[1]
        # dessines a part : les tabourets, les tables, la table ronde -- mais jamais le sol ou ils
        # posent (l'allee n'a qu'un bloc au-dessus de l'eau : un trou s'ouvrait sous un tabouret haut)
        if (node in PROPS and y >= 68) or (node == ROUND_TABLE and y >= SUNKEN_Y + 1):
            clear(key)
            continue
        if node in PROP_BLOCK:
            put(key, PROP_BLOCK[node])
            continue
        # devant le comptoir, a hauteur d'assise : les cubes de la collision (tabourets, poteaux du bar)
        # restaient en blocs pleins de chrome ou de moquette ; les tabourets dessines posent sur le sol
        if y == COUNTER_Y and COUNTER[1] < u < -16.0 and abs(v) < COUNTER[2] and key not in SUPPORTS:
            clear(key)
            floor_top[(x, z)] = COUNTER_Y - 1
            continue
        top = floor_top.get((x, z))
        where = region(u, v)
        is_floor = top is not None and y <= top
        if where == "room":
            if is_floor:
                put(key, room_floor(u, v))
            elif y <= 69 and COUNTER[0] <= u <= COUNTER[1] and abs(v) < COUNTER[2]:
                put(key, "counter")
            else:
                # en haut, les bouts d'arches et de poutres que la collision garde : du metal rouge
                put(key, "red_metal" if y >= 71 else "grey_metal")
        elif where == "wall":
            put(key, "floor_grate" if is_floor and y <= 67 else wall_block(u, y, v))
        elif where == "pod":
            put(key, "carpet" if is_floor else "green_metal")
        elif where == "recess":
            if is_floor:
                put(key, "carpet")
            elif 69 <= y <= 73 and abs(v) < COUNTER[2] and any(
                    (x + dx, y, z + dz) not in cur and uvc(x + dx, z + dz)[0] > u
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                put(key, "bottle_shelf")  # la face du mur du fond, cote salle : les bouteilles
            else:
                put(key, "yellow_bricks")
        else:
            put(key, "metal_floor" if is_floor else ("green_metal" if y >= 73 else "yellow_wall"))
    # 2. la toiture en paliers, les murs jusqu'a l'avant-toit, les pignons jusqu'au faitage (lecon 2)
    tops = {}
    for cx in range(BOX[0], BOX[1] + 1):
        for cz in range(BOX[2], BOX[3] + 1):
            u, v = uvc(cx, cz)
            if roofed(u, v):
                tops[(cx, cz)] = (terrace_top(v), u, v)
    for (cx, cz), (top, u, v) in tops.items():
        for y in range(74, top):
            if abs(v) < WALL_V - 0.4:
                clear((cx, y, cz))
        low = min(tops.get((cx + dx, cz + dz), (top,))[0] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        for y in range(min(low + 1, top), top + 1):
            put((cx, y, cz), "roof")
        if abs(v) >= WALL_V - 0.4:
            for y in range(74, EAVE_CELL + 1):
                if (cx, y, cz) not in out:
                    put((cx, y, cz), wall_block(u, y, v))
        if u < U_BACK_GABLE + 1.0 or u > U_FRONT_GABLE - 1.0:
            for y in range(74, top):
                put((cx, y, cz), "yellow_wall")
    # 2 bis. L'ENVELOPPE FERMEE (lecon 8) : les longs murs pleins, du sol a l'avant-toit, sur deux
    # metres quarante d'epaisseur, et les pignons sur un metre six -- la collision avait des vides
    # entre les alcoves, ou l'on voyait le ciel ; seules les entrees rondes (etape 3) y seront percees
    for cx in range(BOX[0], BOX[1] + 1):
        for cz in range(BOX[2], BOX[3] + 1):
            u, v = uvc(cx, cz)
            long_wall = U_BACK_GABLE <= u <= U_FRONT_GABLE and WALL_V <= abs(v) < WALL_OUT + 1.2
            gable = abs(v) < WALL_OUT + 1.0 and (U_BACK_GABLE - 0.3 <= u < U_BACK_GABLE + 1.3
                                                 or U_FRONT_GABLE - 1.3 < u <= U_FRONT_GABLE + 0.3)
            if not (long_wall or gable):
                continue
            low = 65 if long_wall else 74
            high = EAVE_CELL if long_wall else (tops.get((cx, cz), (EAVE_CELL + 1,))[0] - 1)
            for y in range(low, high + 1):
                key = (cx, y, cz)
                if key not in out and (key not in cur or key in air):
                    put(key, wall_block(u, y, v) if long_wall else "yellow_wall")
    # 3. les entrees rondes des alcoves (lecon 4) : un cercle de cinq blocs dans le mur, le bas sur
    # l'allee -- on y entre (le joueur, 25 sept.) --, cercle de chrome
    opened = set()
    for key in [k for k in list(out) if 68 <= k[1] <= 74]:
        x, y, z = key
        u, v = uvc(x, z)
        if not WALL_V - 0.6 <= abs(v) < WALL_OUT + 1.0:
            continue
        for p in PODS:
            r = math.hypot(u - p, y + 0.5 - DOOR_Y)
            if r <= DOOR_R:
                clear(key)
                opened.add(key)
            elif r <= DOOR_FRAME:
                put(key, "chrome")
    # 4. le carre rouge : l'anneau de moquette releve d'une demi-marche (les marches du jeu)
    for (x, z), top in floor_top.items():
        u, v = uvc(x, z)
        if (top == 66 and region(u, v) == "room" and RING[0] <= u <= RING[1] and abs(v) < RING[2]
                and not (SUNKEN[0] <= u <= SUNKEN[1] and abs(v) < SUNKEN[2])):
            if (x, 67, z) not in cur:
                put((x, 67, z), "carpet_slab[type=bottom]")
    # 5. la table ronde, et les deux caisses dans le coin du carre
    # l'ancienne table et les anciennes caisses de la collision passaient pour du sol : on les ote, et
    # tout se pose sur le fond (cellule 66, dessus 67)
    tu, tv, radius = TABLE
    for key, (u, v) in shape.items():
        near_table = math.hypot(u - tu, v - tv) <= radius + 0.6
        near_crates = math.hypot(u - CRATES_AT[0], v - CRATES_AT[1]) < 2.2
        if key[1] in (67, 68) and (near_table or near_crates):
            clear(key)
            floor_top[(key[0], key[2])] = SUNKEN_Y
    for (x, z) in list(floor_top):
        u, v = uvc(x, z)
        d = math.hypot(u - tu, v - tv)
        if d <= radius:
            put((x, SUNKEN_Y + 1, z), "blue_lamp" if d <= radius - 0.9 else "chrome")
    for (cu, cv) in CRATES:
        best = min(floor_top, key=lambda c: math.hypot(uvc(*c)[0] - cu, uvc(*c)[1] - cv))
        put((best[0], SUNKEN_Y + 1, best[1]), "crate")
    # 5 bis. le bar, tout au fond : un comptoir d'un bloc sur l'allee, les tabourets devant (etape 7)
    for (x, z), top in floor_top.items():
        u, v = uvc(x, z)
        if COUNTER[0] <= u <= COUNTER[1] and abs(v) < COUNTER[2] and top < COUNTER_Y:
            put((x, COUNTER_Y, z), "counter")
    # 6. les huit alcoves, baties entieres (la collision n'en donnait ni le fond ni tout le plafond) : une
    # petite salle de moquette, les cotes, le fond et le plafond de metal vert, deux banquettes face a
    # face le long des cotes, la table au milieu, la lampe bleue au-dessus
    for cx in range(BOX[0], BOX[1] + 1):
        for cz in range(BOX[2], BOX[3] + 1):
            u, v = uvc(cx, cz)
            av = abs(v)
            for pu in PODS:
                du = u - pu
                # derriere le mur de la salle, qui garde son entree ronde
                # des murs d'un metre trois : plus minces, sur la grille de biais, ils laissaient des fentes
                if abs(du) > POD_HALF + 1.3 or not POD_FRONT <= av <= POD_BACK + 1.3:
                    continue
                inside = abs(du) <= POD_HALF and av <= POD_BACK
                for y in range(POD_FLOOR - 1, POD_CEILING + 1):
                    key = (cx, y, cz)
                    if y < POD_FLOOR:
                        put(key, "green_metal")
                    elif y == POD_FLOOR:
                        put(key, "carpet" if inside else "green_metal")
                    elif y == POD_CEILING or not inside:
                        put(key, "green_metal")
                    elif y == POD_FLOOR + 1 and abs(du) >= POD_HALF - 0.9 and av >= POD_FRONT + 0.6:
                        # le dossier contre le cote de l'alcove, l'assise vers la table
                        put(key, "booth_stairs[facing=%s]" % ("north" if du < 0 else "south"))
                    else:
                        clear(key)
    # une table au milieu de chaque alcove -- une seule : deux cellules voisines en posaient deux --, et
    # la lampe bleue au plafond, au-dessus
    for pu in PODS:
        for side in (1, -1):
            x, z = cell_at(pu, side * POD_TABLE_V)
            put((x, POD_FLOOR + 1, z), "high_table")
            put((x, POD_CEILING, z), "blue_lamp")
    # 7. les objets dessines (lecon 3), les lampes suspendues, les lumieres du sol devant le comptoir
    props = collections.Counter()
    columns = collections.defaultdict(list)
    for (x, y, z) in out:
        columns[(x, z)].append(y)
    for (x, y, z) in cur:
        if y <= 65:
            columns[(x, z)].append(y)
    for node, block in PROPS.items():
        for p in pieces(tris, node):
            u, v = uv_of(p["center"][0], p["center"][2])
            if abs(v) > WALL_V - 0.8:
                v = math.copysign(WALL_V - 0.8, v)   # contre le mur plein, pas dedans
            x, z = cell_at(u, v)
            y = cell_of(p["lo"])[1]
            # sur le plein le plus haut, au plus une cellule au-dessus du bas de l'objet : les tabourets du
            # comptoir se posent sur la marche ou se tient Torn
            ground = max((yy for yy in columns.get((x, z), ()) if yy <= y + 1), default=y - 1)
            key = (x, ground + 1, z)
            if key in out or key in no_fill:
                continue
            put(key, block)
            props[block] += 1
    # les lampes du jeu sont de grands caissons (2,8 m) poses sur les pentes du toit : a plat dans la
    # toiture, un carre de lampe ambre de deux blocs sur deux -- les petites lampes suspendues ne
    # rendaient ni leur taille ni leur lumiere, et la voute restait noire (comparaison au jeu)
    lamp_cells = []
    for p in pieces(tris, LAMPS):
        lu, lv = uv_of(p["center"][0], p["center"][2])
        lamp_cells.append(cell_at(lu, lv))
        for (cx, cz), (top, u, v) in tops.items():
            if abs(u - lu) < LAMP_HALF and abs(v - lv) < LAMP_HALF:
                put((cx, top, cz), "amber_lamp")
                props["roof_lamp"] += 1
    for key, v in vox.items():
        if v[1] == FLOOR_LIGHTS and out.get(key) in ("metal_floor", "floor_grate", "carpet"):
            put(key, "blue_lamp")
            props["floor_light"] += 1
    # 7 bis. ce que le joueur a pendu au plafond plat, que la toiture remonte (lecon 9) : une chaine
    # jusqu'au nouveau plafond -- sa lampe-ventilateur, au milieu de la salle, flottait sinon
    for (x, y, z) in user:
        above = (x, y + 1, z)
        if above not in air:
            continue
        for yy in range(y + 1, 100):
            key = (x, yy, z)
            if key in out or (key in cur and key not in air):
                break
            put(key, CHAIN)
            props["chain"] += 1
    # 8. la lumiere (voir LIGHT) : dans une cellule vide, au-dessus des tetes
    wanted = [((x, z), LIGHT_Y) for (x, z) in lamp_cells]
    wanted += [(cell_at(u, v), LIGHT_Y) for (u, v) in LIGHTS_AT]
    wanted += [(cell_at(pu, side * 16.6), POD_CEILING - 1) for pu in PODS for side in (1, -1)]
    for (x, z), y in wanted:
        key = (x, y, z)
        if key not in out and key not in cur:
            put(key, LIGHT)
            props["light"] += 1
    return out, air, len(opened), props


# ============================================================ les blocs

def steel_texture():
    """Le chrome du bar (lecon 9) : l'acier brosse de la porte du Hip Hog, celui du cadre des vitres."""
    atlas = Image.open(haven_window.ATLAS).convert("RGBA")
    return haven_window.flatten(haven_window.metal(atlas, haven_window.STEEL_LIGHT, 0.8))


def block_texture(im, name):
    small = im.resize((16, 16), Image.BOX)
    px = [(r, g, b, 255 if a >= 64 else 0) for (r, g, b, a) in small.getdata()]
    k = SMOOTH.get(name)
    if k:
        mean = [sum(p[i] for p in px) / len(px) for i in range(3)]
        px = [tuple(int(p[i] + (mean[i] - p[i]) * k) for i in range(3)) + (p[3],) for p in px]
    k = DARKEN.get(name)
    if k:
        px = [(int(p[0] * k), int(p[1] * k), int(p[2] * k), p[3]) for p in px]
    out = Image.new("RGBA", (16, 16))
    out.putdata(px)
    return out


def average(im):
    px = list(im.convert("RGB").resize((16, 16), Image.BOX).getdata())
    return tuple(sum(p[i] for p in px) // len(px) for i in range(3))


def shelf_texture(images):
    """L'etagere a bouteilles : deux rangs de bouteilles aux couleurs de celles du jeu, sur le mur jaune du
    bar (sur du metal sombre, elle passait pour une bibliotheque)."""
    base = block_texture(images["hip-tmetbooth01"], "hip-tmetbooth01")
    px = base.load()
    wall = block_texture(images["hip-tyellwall01"], "hip-tyellwall01").load()
    out = Image.new("RGBA", (16, 16))
    o = out.load()
    for y in range(16):
        for x in range(16):
            r, g, b, _ = wall[x, y]
            o[x, y] = (int(r * 0.8), int(g * 0.8), int(b * 0.8), 255)
    colours = [average(images[n]) for n in ("hip-tbotyel01", "hip-tbotblue01", "hip-tbotred01", "hip-tbotblue02")]
    for row in (0, 8):
        for x in range(16):
            o[x, row + 7] = px[x, row + 7][:3] + (255,)
        for i, bx in enumerate((1, 4, 7, 10, 13)):
            c = tuple(min(255, int(v * 1.35)) for v in colours[(i + row // 8) % len(colours)])
            h = 4 + (i * 7 + row) % 3
            for y in range(row + 7 - h, row + 7):
                o[bx, y] = c + (255,)
                o[bx + 1, y] = tuple(int(v * 0.75) for v in c) + (255,)
            o[bx, row + 6 - h] = (40, 36, 30, 255)
    return out


def glass_texture():
    out = Image.new("RGBA", (16, 16), (168, 214, 236, 70))
    o = out.load()
    for i in range(16):
        o[i, 0] = o[i, 15] = o[0, i] = o[15, i] = (205, 232, 246, 150)
    for i in range(3, 9):
        o[i + 2, 12 - i] = (235, 248, 255, 130)
    return out


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down")):
    uvs = {
        "north": [16 - to[0], 16 - to[1], 16 - frm[0], 16 - frm[1]],
        "south": [frm[0], 16 - to[1], to[0], 16 - frm[1]],
        "east": [16 - to[2], 16 - to[1], 16 - frm[2], 16 - frm[1]],
        "west": [frm[2], 16 - to[1], to[2], 16 - frm[1]],
        "up": [frm[0], frm[2], to[0], to[2]],
        "down": [frm[0], 16 - to[2], to[0], 16 - frm[2]],
    }
    return {"from": list(frm), "to": list(to),
            "faces": {f: {"uv": uvs[f], "texture": tex if isinstance(tex, str) else tex[f]} for f in faces}}


MODEL_ELEMENTS = {
    "stool": ({"leg": "chrome", "seat": "cushion"},
              [box((7, 0, 7), (9, 10, 9), "#leg"), box((5, 1, 5), (11, 2, 11), "#leg"),
               box((4, 10, 4), (12, 12, 12), "#seat")]),
    "high_stool": ({"leg": "chrome", "seat": "cushion"},
                   [box((7, 0, 7), (9, 13, 9), "#leg"), box((5, 5, 5), (11, 6, 11), "#leg"),
                    box((4, 13, 4), (12, 15, 12), "#seat")]),
    # le plateau prend toute la case : deux tables voisines font une seule table
    "high_table": ({"leg": "chrome", "top": "counter"},
                   [box((7, 0, 7), (9, 14, 9), "#leg"), box((5, 0, 5), (11, 1, 11), "#leg"),
                    box((0, 14, 0), (16, 16, 16), "#top")]),
    "hanging_lamp": ({"rod": "chrome", "glow": "amber_lamp", "cap": "grey_metal"},
                     [box((7, 12, 7), (9, 16, 9), "#rod"),
                      box((4, 3, 4), (12, 12, 12), {"north": "#glow", "south": "#glow", "east": "#glow",
                                                    "west": "#glow", "up": "#cap", "down": "#glow"}),
                      box((3, 11, 3), (13, 12, 13), "#cap")]),
}


def write_slab(name, texture):
    block = os.path.join(ASSETS, "models", "block")
    tex = {"bottom": texture, "top": texture, "side": texture}
    write_json(os.path.join(block, name + ".json"), {"parent": "minecraft:block/slab", "textures": tex})
    write_json(os.path.join(block, name + "_top.json"), {"parent": "minecraft:block/slab_top", "textures": tex})
    full = "emeraldweapons:block/" + texture.split("/")[-1]
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
        "type=bottom": {"model": "emeraldweapons:block/" + name},
        "type=top": {"model": "emeraldweapons:block/" + name + "_top"},
        "type=double": {"model": full}}})


def write_stairs(name, texture):
    """L'escalier de Minecraft (les memes variantes que ceux du jeu) : la banquette des alcoves."""
    block = os.path.join(ASSETS, "models", "block")
    tex = {"bottom": texture, "top": texture, "side": texture}
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        write_json(os.path.join(block, name + suffix + ".json"),
                   {"parent": "minecraft:block/" + parent, "textures": tex})
    base_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, y0 in base_y.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = name + ("" if shape == "straight" else "_inner" if shape.startswith("inner") else "_outer")
                y = y0
                if shape in ("inner_left", "outer_left"):
                    y = (y0 + 270) % 360
                x = 0
                if half == "top":
                    x = 180
                    if shape in ("inner_left", "outer_left"):
                        y = y0
                    elif shape in ("inner_right", "outer_right"):
                        y = (y0 + 90) % 360
                v = {"model": "emeraldweapons:block/" + model}
                if x:
                    v["x"] = x
                if y:
                    v["y"] = y
                if x or y:
                    v["uvlock"] = True
                variants["facing=%s,half=%s,shape=%s" % (facing, half, shape)] = v
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})


def write_blocks(images):
    tex_dir = os.path.join(ASSETS, "textures", "block")
    os.makedirs(tex_dir, exist_ok=True)
    for bid, src, light, kind in BLOCKS:
        if kind in ("slab", "stairs"):
            continue                      # la texture du bloc plein de leur matiere
        if bid == "glass":
            im = glass_texture()
        elif bid == "chrome":
            im = steel_texture()
        elif bid == "bottle_shelf":
            im = shelf_texture(images)
        else:
            im = block_texture(images[src], src)
        im.save(os.path.join(tex_dir, "hiphog_%s.png" % bid))
    block_texture(images["hip-tredstool01"], "hip-tredstool01").save(os.path.join(tex_dir, "hiphog_cushion.png"))
    for bid, src, light, kind in BLOCKS:
        name = "hiphog_" + bid
        if kind == "slab":
            write_slab(name, "emeraldweapons:block/hiphog_" + src)
            continue
        if kind == "stairs":
            write_stairs(name, "emeraldweapons:block/hiphog_" + src)
            continue
        model = {"parent": "minecraft:block/cube_all", "textures": {"all": "emeraldweapons:block/" + name}}
        if kind == "translucent":
            model["render_type"] = "minecraft:translucent"
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"), model)
    for mid, light in MODELS:
        name = "hiphog_" + mid
        textures, elements = MODEL_ELEMENTS[mid]
        tex = {k: "emeraldweapons:block/hiphog_" + v for k, v in textures.items()}
        tex["particle"] = tex[next(iter(textures))]
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"),
                   {"parent": "minecraft:block/block", "textures": tex, "elements": elements})
    for bid, kind in [(b[0], b[3]) for b in BLOCKS] + [(m[0], "model") for m in MODELS]:
        name = "hiphog_" + bid
        if kind not in ("slab", "stairs"):
            write_json(os.path.join(ASSETS, "blockstates", name + ".json"),
                       {"variants": {"": {"model": "emeraldweapons:block/" + name}}})
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "emeraldweapons:block/" + name})
        write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                       "entries": [{"type": "minecraft:item", "name": "emeraldweapons:" + name}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": "emeraldweapons:blocks/" + name})


def sealed(cur, cells, air, cables):
    """Le bar est-il ferme ? Un remplissage de l'air du milieu de la salle, porte bouchee (lecon 8).

    L'etat du jeu : le volume, les cables vides (HavenCables), puis le bar. Rend None, ou le chemin
    de l'air vers dehors (ses dernieres cellules).
    """
    solid = {k for k, b in cur.items() if b not in ("minecraft:air", "minecraft:cave_air", "minecraft:water")}
    solid -= cables
    for key, state in cells.items():
        # ni les lumieres, ni ce qui n'est pas un bloc plein (meubles, chaine, dalle, siege, verre) : on
        # voit le jour au travers -- une lampe posee dans la toiture ouvrait un trou que le premier
        # controle, qui les comptait pleins, n'a pas vu
        if state.startswith(("minecraft:", )) or state.split("[")[0] in PASSABLE:
            solid.discard(key)
        else:
            solid.add(key)
    solid -= air
    (xa, xb), (ya, yb), (za, zb) = DOOR_BOX
    door = {(x, y, z) for x in range(xa, xb + 1) for y in range(ya, yb + 1) for z in range(za, zb + 1)}
    seed = (cell_at(0.0, 0.0)[0], 72, cell_at(0.0, 0.0)[1])
    parent = {seed: None}
    queue = collections.deque([seed])
    while queue:
        c = queue.popleft()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (c[0] + d[0], c[1] + d[1], c[2] + d[2])
            if n in parent or n in solid or n in door or not 58 <= n[1] <= 95:
                continue
            parent[n] = c
            u, v = uvc(n[0], n[2])
            if not (REGION[0] <= u <= REGION[1] - 0.6 and abs(v) <= REGION[2] and 60 <= n[1] <= 87):
                path = []
                while n is not None:
                    path.append(n)
                    n = parent[n]
                return path[:8]
            queue.append(n)
    return None


def main():
    print("decor du bar :", GLB)
    tris, images = load_bar()
    vox = voxelize(tris)
    sha1, cur = load_volume()
    cables = load_cables()
    user = load_user()
    cells, air, opened, props = build(tris, vox, cur, cables, user)
    leak = sealed(cur, cells, air, cables)
    if leak is not None:
        sys.exit("le bar n'est pas ferme : l'air sort par " + ", ".join(
            "%s (u %.1f, v %.1f)" % ((c,) + uvc(c[0], c[2])) for c in leak))
    print("bar ferme : l'air de la salle ne sort que par la porte")

    def full(state):
        return state if state.startswith("minecraft:") else "emeraldweapons:hiphog_" + state

    palette = ["minecraft:air"] + sorted({full(c) for c in cells.values()})
    index = {p: i for i, p in enumerate(palette)}
    originals = []
    rows = []
    for key in sorted(set(cells) | air):
        want = "minecraft:air" if key in air else full(cells[key])
        was = cur.get(key, "minecraft:air")
        if was == want:
            continue
        if was not in originals:
            originals.append(was)
        rows.append([key[0], key[1], key[2], index[want], originals.index(was)])
    # compact, comme haven_cables.json : onze mille cellules, une valeur par ligne en faisaient 80 000
    with open(OUT_JSON, "w", encoding="utf-8", newline="\n") as f:
        json.dump({
            "_format": ["Le bar du Hip Hog de Jak 3 en blocs (cahier §103), ecrit par tools/jak_bar.py.",
                        "cells : x, y, z (cellules du volume), etat voulu (palette), etat du volume ici (volume).",
                        "HavenBar ne pose une cellule que si le monde y a encore l'etat du volume."],
            "sha1": sha1, "palette": palette, "volume": originals, "cells": rows},
            f, separators=(",", ":"), ensure_ascii=False)
    write_blocks(images)
    counts = collections.Counter(palette[r[3]] for r in rows)
    print("cellules : %d (dont %d vides, %d ouvertes pour les entrees des alcoves), objets %s" % (
        len(rows), counts["minecraft:air"], opened, dict(props)))
    for name, n in counts.most_common():
        print("  %-36s %5d" % (name, n))
    print("ecrit :", OUT_JSON)


if __name__ == "__main__":
    main()
