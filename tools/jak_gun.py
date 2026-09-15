"""Cuit le Morph Gun de Jak 3 et les munitions d'eco a ramasser, pour Minecraft.

Le Morph Gun est UN maillage habille (gun-lod0, niveau common) : 1115 triangles
et un squelette de 47 os, dont chaque piece de l'arme est un os. Les douze armes
ne sont pas douze maillages mais douze POSES du meme squelette (animations a
une clef gun-idle-<famille><n>), qui rentrent les pieces inutiles en mettant
l'echelle de leur os a 0 ou 0,05. Changer d'arme, c'est changer de pose ; se
transformer, c'est jouer une animation gun-gun-<a>-<b>.

CE QUE L'OUTIL GARDE, ET POURQUOI (etude : scratchpad/armes2/etude-modele.md).

1. UN OS PAR SOMMET. L'habillage est rigide sauf pour 16 sommets sur 1471 ;
   garder l'os de plus fort poids deplace un sommet visible d'au plus 1,7 cm.
   50 triangles ont leurs sommets sur deux os differents (aucun sur trois) :
   l'os est donc range PAR SOMMET, et le triangle porte en plus l'os
   majoritaire de ses sommets, qui decide s'il est cache.

2. LES TRIANGLES SONT DANS LE REPERE DE REPOS. La pose de repos habillee vaut
   l'identite (ecart mesure et imprime). Le mod calculera, a chaque image :
       monde[i]      = monde[parent(i)] x TRS(i)      (parent(i) < i, verifie)
       habillage[i]  = monde[i] x inverse_repos[i]
       sommet pose   = habillage[os du sommet] x position
       normale posee = habillage[os du sommet] x normale, renormalisee

3. CACHER, C'EST L'ECHELLE. Un triangle est cache quand l'echelle de son os,
   racine cubique de |det(habillage[os])|, est sous RATIO (0,2) x celle de
   main. C'est un choix de rendu : le jeu dessine les pieces rentrees a 0,05,
   qui restent sous le pixel.

4. LA COULEUR DE FAMILLE EST UN REFLET. Le corps et les chargeurs sont gris ;
   le jeu ajoute aux materiaux « envmap » un reflet colore (4 x 4 : rouge,
   jaune, bleu, violet) dont la force est un masque dans le canal bleu d'une
   texture « envmap-strength ». Minecraft n'a pas d'envmap : on cuit, comme
   l'etude, base + reflet x (bleu / 255 x 2) dans l'atlas. C'est FIXE, alors
   que le reflet du jeu depend de la vue. Ces materiaux sont opaques dans le
   glb : l'alpha de la tuile cuite est force a 128 (opaque PS2), 255 dans
   l'atlas, sinon les texels d'alpha 0 des chargeurs seraient des trous.

4 bis. LA TETE DU PEACE MAKER EST ATTENUEE (decision du joueur, 13 sept.). Ses
   trois materiaux a reflet (gun-tip, gun-eye, jakc-armor) ne prennent pas un
   reflet de famille mais un decor (environment-oldmetal, talkbox-light-02,
   environment-title) ; cuit en entier, le metal passait de 64 a 169 de
   luminance et l'armure de 99 a 194, un tiers des texels satures : une tete
   presque blanche. Leur reflet est cuit a GAIN_OS["peace"] (0,25) : metal 103,
   armure 132, aucun texel sature (mesure imprimee a chaque cuisson). La regle
   est posee PAR OS et verifiee : une tuile attenuee ne doit servir qu'aux
   triangles de cet os, sinon l'outil refuse -- un chargeur qui partagerait la
   tuile perdrait sa couleur de famille sans que personne le voie.

5. ON NE CUIT QUE CE QUI SERT A L'ARME : 13 poses et 13 transformations. Les
   15 tirs ne sont pas repris (le plan ne garde que la rotation du canon) ;
   les 13 animations de projectiles et gun-ammo-idle visent les premiers
   noeuds d'AUTRES squelettes (skel-ammo-*, skel-gun-red-cone) : les appliquer
   a l'arme la deformerait.

6. Les lecons de jak_vehicle.py valent ici (on reprend son lecteur et son
   atlas) : ne lire que les sommets cites, couleur de sommet x 2, alpha des
   textures x 2, UV qui se repetent cuites en tuiles repetees.

7. LES QUATERNIONS DU GLB NE SONT PAS TOUT A FAIT UNITAIRES (norme mesuree et
   imprimee, vers 0,99998 : quantification du jeu). Construits tels quels, ils
   ajoutent une echelle parasite de 2e-5 et un ecart de 1,4e-4 sur les
   matrices. Le .bin les range TELS QUELS ; le lecteur les renormalise
   toujours (nlerp le fait deja entre deux clefs, il faut aussi le faire sur
   une clef exacte et dans les poses).

8. ON CONTROLE LA SORTIE. Le .bin est relu comme le fera le mod et compare au
   glb : triangles, couleurs, texels ; pour chaque pose, memes triangles caches
   que l'habillage pondere du glb, tailles visibles, ecart de l'os unique ;
   fins et debuts des transformations. Les images sont dessinees depuis le .bin.

REPERE : metres = blocs. Canon en +z, haut en +y, +x a GAUCHE de l'arme (la
main gauche et la poignee laterale bleue y sont). L'origine est le joint `gun`
de Jak (a sa droite, 1,15 m de haut, 34 cm devant lui en garde).

LES DOUZE FORMES (pickup-type Jak = 26 + indice de pose) et la treizieme :
    0 gun-idle-red     Scatter Gun      6 gun-idle-blue    Vulcan Fury
    1 gun-idle-red2    Wave Concussor   7 gun-idle-blue2   Arc Wielder
    2 gun-idle-red3    Plasmite RPG     8 gun-idle-blue3   Needle Lazer
    3 gun-idle-yellow  Blaster          9 gun-idle-dark    Peace Maker
    4 gun-idle-yellow2 Beam Reflexor   10 gun-idle-dark2   Mass Inverter
    5 gun-idle-yellow3 Gyro Burster    11 gun-idle-dark3   Super Nova
   12 gun-idle         arme rangee dans le dos (main x 1,43)

LES TREIZE TRANSFORMATIONS, dans cet ordre (11 clefs sur 0,333 s, sauf
gun-gun-red-blue : 26 clefs sur 0,833 s) :
    red-yellow, red-blue, red-dark, yellow-red, yellow-blue, blue-red,
    blue-dark, dark-yellow, red1-red2, yellow2-yellow3, blue1-blue2,
    dark1-dark2, dark2-dark3   (toutes prefixees gun-gun-)

FORMAT DE <modele>.bin (petit-boutiste, flottants IEEE 32 bits)
    en-tete, 64 octets
        4s    magie b"JKGN"
        I     version (1)
        I     nombre de triangles T
        I     nombre d'os B
        I     nombre de poses P            (0 pour les munitions)
        I     nombre de transformations A  (0 pour les munitions)
        I, I  largeur et hauteur de l'atlas en pixels
        i     indice de l'os `main` (-1 s'il manque)
        f     RATIO de masquage (0,2)
        3f    coin minimum de la boite au repos (m)
        3f    coin maximum de la boite au repos (m)
    B os, 124 octets chacun, dans l'ordre des joints du squelette ; le parent
    d'un os est toujours range AVANT lui
        32s   nom ASCII complete par des zeros
        h     parent (indice dans cette table, -1 pour une racine)
        H     reserve (0)
        10f   TRS de repos : translation (3), rotation quaternion x, y, z, w (4),
              echelle (3)
        12f   inverse de la matrice monde de repos, 3 x 4 rangee par colonnes :
              m00 m10 m20, m01 m11 m21, m02 m12 m22, tx ty tz
    T triangles, 124 octets chacun
        B     os du triangle (majoritaire parmi ses sommets) : decide du masquage
        B     drapeaux : 1 = materiau BLEND (translucide), 2 = reflet envmap cuit
        H     reserve (0)
        3 sommets de 40 octets :
              B    os du sommet (poids le plus fort) : c'est lui qui le deplace
              3x   reserve
              3f   position au repos (m)
              2f   UV dans l'atlas en [0, 1] (v vers le bas, comme Minecraft)
              3f   normale au repos
              4B   couleur RGBA deja multipliee par 2
    P poses, 32 + 40 B octets chacune
        32s   nom de l'animation (gun-idle-red...)
        B x 10f   TRS local de chaque os (les os non animes gardent leur repos)
    A transformations
        32s   nom (gun-gun-red-yellow...)
        I     nombre de clefs K
        f     duree (s)
        K f   instants des clefs (s), croissants, le premier a 0
        K x B x 10f   TRS local de chaque os a chaque clef ; entre deux clefs :
              interpolation lineaire de la translation et de l'echelle,
              nlerp du quaternion (signe aligne), comme le glTF LINEAR

Les cinq .bin (morph_gun et gun_ammo_red/yellow/blue/dark) partagent UN atlas,
morph_gun.png : les munitions reprennent les textures des chargeurs.

Usage :
    python tools/jak_gun.py --out <dossier>            (bin, png, controle.json)
    python tools/jak_gun.py --out <dossier> --no-png   (sans images de controle)
    Au mod :
    python tools/jak_gun.py --out src/main/resources/assets/emeraldweapons/jak_gun --report build/jak/gun
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
import jak_assets as ja  # noqa: E402  (read_glb, export_textures, LEVELS)
from jak_vehicle import (Accessor, BORDER, CUTOFF, EPS, build_atlas, check_against_glb,  # noqa: E402
                         mat_dir, mat_mul, mat_point, node_matrix, normalize)

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow est necessaire : pip install pillow")

LEVEL = "common"
GUN = ("morph_gun", "gun-lod0")
AMMO = [("gun_ammo_red", "gun-ammo-red-lod0"), ("gun_ammo_yellow", "gun-ammo-yellow-lod0"),
        ("gun_ammo_blue", "gun-ammo-blue-lod0"), ("gun_ammo_dark", "gun-ammo-dark-lod0")]

# (pickup-type de Jak, nom de forme, pose, nom de l'arme)
FORMS = [(26, "gun-red-1", "gun-idle-red", "Scatter Gun"),
         (27, "gun-red-2", "gun-idle-red2", "Wave Concussor"),
         (28, "gun-red-3", "gun-idle-red3", "Plasmite RPG"),
         (29, "gun-yellow-1", "gun-idle-yellow", "Blaster"),
         (30, "gun-yellow-2", "gun-idle-yellow2", "Beam Reflexor"),
         (31, "gun-yellow-3", "gun-idle-yellow3", "Gyro Burster"),
         (32, "gun-blue-1", "gun-idle-blue", "Vulcan Fury"),
         (33, "gun-blue-2", "gun-idle-blue2", "Arc Wielder"),
         (34, "gun-blue-3", "gun-idle-blue3", "Needle Lazer"),
         (35, "gun-dark-1", "gun-idle-dark", "Peace Maker"),
         (36, "gun-dark-2", "gun-idle-dark2", "Mass Inverter"),
         (37, "gun-dark-3", "gun-idle-dark3", "Super Nova")]
HOLSTER = (None, "gun-holster", "gun-idle", "rangee")
POSES = [f[2] for f in FORMS] + [HOLSTER[2]]
TRANSFORMS = ["gun-gun-" + n for n in (
    "red-yellow", "red-blue", "red-dark", "yellow-red", "yellow-blue", "blue-red", "blue-dark",
    "dark-yellow", "red1-red2", "yellow2-yellow3", "blue1-blue2", "dark1-dark2", "dark2-dark3")]
# pose de depart et d'arrivee de chaque transformation, d'apres son nom
IDLE = {"red": "gun-idle-red", "red1": "gun-idle-red", "red2": "gun-idle-red2",
        "yellow": "gun-idle-yellow", "yellow2": "gun-idle-yellow2", "yellow3": "gun-idle-yellow3",
        "blue": "gun-idle-blue", "blue1": "gun-idle-blue", "blue2": "gun-idle-blue2",
        "dark": "gun-idle-dark", "dark1": "gun-idle-dark", "dark2": "gun-idle-dark2", "dark3": "gun-idle-dark3"}
RATIO = 0.2
# gain du reflet cuit, par os (lecon 4 bis) : seules les tuiles propres a cet os sont touchees
GAIN_OS = {"peace": 0.25}

MAGIC = b"JKGN"
VERSION = 1
HEADER = struct.Struct("<4sIIIIIIIif3f3f")
BONE = struct.Struct("<32shH10f12f")
TRI = struct.Struct("<BBH" + "B3x3f2f3f4B" * 3)
NAME = struct.Struct("<32s")
TRS = struct.Struct("<10f")
ANIM = struct.Struct("<32sIf")
FLAG_BLEND = 1
FLAG_ENVMAP = 2
assert HEADER.size == 64 and BONE.size == 124 and TRI.size == 124 and ANIM.size == 40

ROOT = ja.ROOT
TEX_DIR = os.path.join(ROOT, "build", "jak", "gun", "textures")
REFERENCE_JSON = os.path.join(ROOT, "build", "jak", "gun", "morph-gun.json")


# ------------------------------------------------------------------ maths

def det3(m):
    return (m[0] * (m[5] * m[10] - m[9] * m[6]) - m[4] * (m[1] * m[10] - m[9] * m[2])
            + m[8] * (m[1] * m[6] - m[5] * m[2]))


def nlerp(q0, q1, f):
    if sum(a * b for a, b in zip(q0, q1)) < 0:
        q1 = tuple(-x for x in q1)
    q = [a + (b - a) * f for a, b in zip(q0, q1)]
    n = math.sqrt(sum(x * x for x in q)) or 1.0
    return tuple(x / n for x in q)


def trs_matrix(trs):
    """Matrice d'un TRS du .bin ; le quaternion est TOUJOURS renormalise (lecon 7)."""
    return node_matrix({"translation": trs[0:3], "rotation": normalize4(trs[3:7]), "scale": trs[7:10]})


def decompose(m):
    """TRS d'une matrice monde locale (sans cisaillement), verifie en la recomposant."""
    t = (m[12], m[13], m[14])
    cols = [m[0:3], m[4:7], m[8:11]]
    s = [math.sqrt(sum(c * c for c in col)) for col in cols]
    if det3(m) < 0:
        s[0] = -s[0]
    if any(abs(x) < 1e-12 for x in s):
        q = (0.0, 0.0, 0.0, 1.0)
    else:
        R = [[cols[c][r] / s[c] for c in range(3)] for r in range(3)]
        tr = R[0][0] + R[1][1] + R[2][2]
        if tr > 0:
            k = 0.5 / math.sqrt(tr + 1.0)
            q = ((R[2][1] - R[1][2]) * k, (R[0][2] - R[2][0]) * k, (R[1][0] - R[0][1]) * k, 0.25 / k)
        elif R[0][0] > R[1][1] and R[0][0] > R[2][2]:
            k = 2.0 * math.sqrt(1.0 + R[0][0] - R[1][1] - R[2][2])
            q = (0.25 * k, (R[1][0] + R[0][1]) / k, (R[0][2] + R[2][0]) / k, (R[2][1] - R[1][2]) / k)
        elif R[1][1] > R[2][2]:
            k = 2.0 * math.sqrt(1.0 + R[1][1] - R[0][0] - R[2][2])
            q = ((R[1][0] + R[0][1]) / k, 0.25 * k, (R[2][1] + R[1][2]) / k, (R[0][2] - R[2][0]) / k)
        else:
            k = 2.0 * math.sqrt(1.0 + R[2][2] - R[0][0] - R[1][1])
            q = ((R[0][2] + R[2][0]) / k, (R[2][1] + R[1][2]) / k, 0.25 * k, (R[1][0] - R[0][1]) / k)
        q = normalize4(q)
    trs = (*t, *q, *s)
    back = trs_matrix(trs)
    err = max(abs(a - b) for a, b in zip(back, m))
    return trs, err


def normalize4(q):
    n = math.sqrt(sum(x * x for x in q)) or 1.0
    return tuple(x / n for x in q)


def ibm12(m):
    return [m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10], m[12], m[13], m[14]]


def ibm16(v):
    return [v[0], v[1], v[2], 0.0, v[3], v[4], v[5], 0.0, v[6], v[7], v[8], 0.0, v[9], v[10], v[11], 1.0]


# --------------------------------------------------------------- lecture glb

class Glb:
    """Un glb habille : noeuds, squelette, animations (echantillonnage glTF LINEAR)."""

    def __init__(self, name):
        self.name = name
        self.path = os.path.join(ja.LEVELS, LEVEL, name + ".glb")
        if not os.path.isfile(self.path):
            sys.exit("modele introuvable : %s" % self.path)
        self.js, self.blob = ja.read_glb(self.path)
        js = self.js
        self.nodes = js["nodes"]
        self.parent = {}
        for i, node in enumerate(self.nodes):
            for child in node.get("children", []):
                self.parent[child] = i
        mesh_nodes = [n for n in self.nodes if "mesh" in n]
        if len(mesh_nodes) != 1 or len(js.get("skins", [])) != 1:
            sys.exit("%s : %d noeuds de maillage et %d squelettes, un seul de chaque attendu"
                     % (name, len(mesh_nodes), len(js.get("skins", []))))
        if any(k in mesh_nodes[0] for k in ("matrix", "translation", "rotation", "scale")):
            sys.exit("%s : le noeud du maillage est transforme, non gere" % name)
        skin = js["skins"][0]
        self.joints = skin["joints"]
        acc = Accessor(js, self.blob, skin["inverseBindMatrices"])
        self.ibm = [list(acc[k]) for k in range(len(self.joints))]
        self.names = [self.nodes[j].get("name", "os%d" % k) for k, j in enumerate(self.joints)]
        self.anims = {a["name"]: a for a in js.get("animations", [])}
        self._keys = {}

    def rest_local(self, joint):
        node = self.nodes[self.joints[joint]]
        return list(node["matrix"]) if "matrix" in node else node_matrix(node)

    def keys(self, name):
        """(instants, {noeud: {chemin: [valeur par clef]}}) ; une seule suite d'instants exigee."""
        if name not in self._keys:
            anim = self.anims.get(name)
            if anim is None:
                sys.exit("%s : animation %s absente" % (self.name, name))
            times = None
            chans = {}
            for ch in anim["channels"]:
                s = anim["samplers"][ch["sampler"]]
                if s.get("interpolation", "LINEAR") != "LINEAR":
                    sys.exit("%s/%s : interpolation %s non geree" % (self.name, name, s["interpolation"]))
                node = ch["target"]["node"]
                if node not in self.joints:
                    sys.exit("%s/%s : le canal vise le noeud %d hors du squelette" % (self.name, name, node))
                inp = Accessor(self.js, self.blob, s["input"])
                out = Accessor(self.js, self.blob, s["output"])
                t = [inp[i][0] for i in range(inp.count)]
                if times is None:
                    times = t
                elif len(t) != len(times) or max(abs(a - b) for a, b in zip(t, times)) > 1e-6:
                    sys.exit("%s/%s : instants differents selon les canaux, non gere" % (self.name, name))
                chans.setdefault(node, {})[ch["target"]["path"]] = [out[i] for i in range(out.count)]
            self._keys[name] = (times, chans)
        return self._keys[name]

    def duration(self, name):
        return self.keys(name)[0][-1]

    def sample(self, name, t, raw=False):
        """TRS locaux des noeuds animes a l'instant t (lerp, nlerp).

        raw=True rend le quaternion d'une clef tel qu'il est range dans le glb,
        sans le renormaliser : sert seulement a mesurer l'effet de la lecon 7.
        """
        times, chans = self.keys(name)
        if t <= times[0] or len(times) == 1:
            k, f = 0, 0.0
        elif t >= times[-1]:
            k, f = len(times) - 1, 0.0
        else:
            k = 0
            while times[k + 1] <= t:
                k += 1
            f = (t - times[k]) / (times[k + 1] - times[k])
        local = {}
        for node, paths in chans.items():
            for path, vals in paths.items():
                if f == 0.0:
                    v = normalize4(vals[k]) if path == "rotation" and not raw else vals[k]
                elif path == "rotation":
                    v = nlerp(vals[k], vals[k + 1], f)
                else:
                    v = tuple(a + (b - a) * f for a, b in zip(vals[k], vals[k + 1]))
                local.setdefault(node, {})[path] = v
        return local

    def world(self, local=None):
        local = local or {}
        mats = {}

        def get(i):
            if i not in mats:
                if i in local:
                    trs = local[i]
                    m = node_matrix({"translation": trs.get("translation", (0, 0, 0)),
                                     "rotation": trs.get("rotation", (0, 0, 0, 1)),
                                     "scale": trs.get("scale", (1, 1, 1))})
                else:
                    node = self.nodes[i]
                    m = list(node["matrix"]) if "matrix" in node else node_matrix(node)
                mats[i] = mat_mul(get(self.parent[i]), m) if i in self.parent else m
            return mats[i]

        return [get(j) for j in self.joints]

    def skin(self, world):
        return [mat_mul(w, ib) for w, ib in zip(world, self.ibm)]


def luminance(image):
    """Luminance moyenne (Rec. 601) et part des texels satures sur les trois canaux."""
    px = image.convert("RGB").tobytes()
    n = len(px) // 3
    lum = sum(0.299 * px[i] + 0.587 * px[i + 1] + 0.114 * px[i + 2] for i in range(0, len(px), 3)) / n
    sat = sum(1 for i in range(0, len(px), 3) if px[i] == 255 and px[i + 1] == 255 and px[i + 2] == 255) / n
    return lum, sat


def bake_envmap(base, spec, strength, gain=1.0):
    """base + reflet x gain x (bleu / 255 x 2), alpha opaque PS2 (128). Formule de l'etude, gain de la lecon 4 bis."""
    bw, bh = base.size
    sw, sh = spec.size
    fw, fh = strength.size
    bp, sp, fp = base.load(), spec.load(), strength.load()
    out = Image.new("RGBA", base.size)
    op = out.load()
    for y in range(bh):
        for x in range(bw):
            r, g, b, _a = bp[x, y]
            sr, sg, sb, _sa = sp[x * sw // bw, y * sh // bh]
            k = fp[x * fw // bw, y * fh // bh][2] / 255.0 * 2.0 * gain
            op[x, y] = (min(255, int(r + sr * k)), min(255, int(g + sg * k)), min(255, int(b + sb * k)), 128)
    return out


def read_model(glb, model):
    """Triangles au repos, os par sommet, images (dont les reflets cuits)."""
    js, blob = glb.js, glb.blob
    out = os.path.join(TEX_DIR, glb.name)
    raw = {}
    for path, _size in ja.export_textures(js, blob, out):
        index = int(os.path.basename(path).split("-", 1)[0])
        image = Image.open(path).convert("RGBA")
        image.load()
        digest = hashlib.sha1(image.tobytes()).hexdigest()[:12]
        raw[index] = ("%s-%s" % (js["images"][index].get("name") or "image-%03d" % index, digest), image)
    sources = {}
    pending = {}

    def source(tex_index):
        key, image = raw[js["textures"][tex_index]["source"]]
        sources[key] = image
        return key, image

    rest_world = glb.world()
    joint_mats = glb.skin(rest_world)
    bones = []
    decompose_err = 0.0
    for k, j in enumerate(glb.joints):
        p = glb.parent.get(j)
        parent = glb.joints.index(p) if p in glb.joints else -1
        if parent >= k:
            sys.exit("%s : l'os %d a pour parent %d, range apres lui" % (model, k, parent))
        trs, err = decompose(glb.rest_local(k))
        decompose_err = max(decompose_err, err)
        bones.append({"name": glb.names[k], "parent": parent, "trs": trs, "ibm": ibm12(glb.ibm[k])})

    tris = []
    drift = 0.0
    cited = 0
    baked = {}
    for mesh in js["meshes"]:
        for prim in mesh["primitives"]:
            if prim.get("mode", 4) != 4:
                sys.exit("%s : primitive en mode %s, non geree" % (model, prim.get("mode")))
            material = js["materials"][prim["material"]]
            pbr = material.get("pbrMetallicRoughness", {})
            if "baseColorTexture" not in pbr:
                sys.exit("%s : triangle sans texture, non gere" % model)
            if any(abs(f - 2.0) > 1e-3 for f in pbr.get("baseColorFactor", [1.0] * 4)):
                sys.exit("%s : facteur de couleur %s, 2 attendu" % (model, pbr.get("baseColorFactor")))
            spec = material.get("extensions", {}).get("KHR_materials_specular")
            flags = 0
            if spec:
                if "metallicRoughnessTexture" not in pbr:
                    sys.exit("%s : envmap %s sans texture de force" % (model, material.get("name")))
                bk, bimg = source(pbr["baseColorTexture"]["index"])
                sk, simg = source(spec["specularColorTexture"]["index"])
                fk, fimg = source(pbr["metallicRoughnessTexture"]["index"])
                key = "env-%s+%s+%s" % (bk, sk.rsplit("-", 1)[-1], fk.rsplit("-", 1)[-1])
                pending.setdefault(key, (bimg, simg, fimg))
                image_key = key
                flags |= FLAG_ENVMAP
            else:
                if material.get("alphaMode") not in ("MASK", "BLEND"):
                    sys.exit("%s : materiau %s opaque sans envmap, non prevu" % (model, material.get("name")))
                image_key, _ = source(pbr["baseColorTexture"]["index"])
                if material.get("alphaMode") == "BLEND":
                    flags |= FLAG_BLEND
            at = prim["attributes"]
            pos = Accessor(js, blob, at["POSITION"])
            nrm = Accessor(js, blob, at["NORMAL"])
            tex = Accessor(js, blob, at["TEXCOORD_0"])
            col = Accessor(js, blob, at["COLOR_0"])
            jnt = Accessor(js, blob, at["JOINTS_0"])
            wts = Accessor(js, blob, at["WEIGHTS_0"])
            ind = Accessor(js, blob, prim["indices"])
            idx = [ind[i][0] for i in range(ind.count)]
            verts = {}
            for vi in sorted(set(idx)):
                p = pos[vi]
                w = wts[vi]
                jj = jnt[vi]
                inf = [(jj[q], w[q]) for q in range(4) if w[q] > 0.0]
                if not inf:
                    sys.exit("%s : sommet %d sans poids" % (model, vi))
                if any(j >= len(glb.joints) for j, _ in inf):
                    sys.exit("%s : sommet %d cite un os absent" % (model, vi))
                tot = sum(x for _, x in inf)
                sp_ = [sum(x * mat_point(joint_mats[j], p)[k] for j, x in inf) / tot for k in range(3)]
                drift = max(drift, math.dist(sp_, p))
                c = col[vi]
                rgba = [min(255, max(0, int(round(c[k] * 2.0 * 255.0)))) for k in range(3)]
                rgba.append(min(255, max(0, int(round((c[3] if len(c) > 3 else 1.0) * 255.0)))))
                verts[vi] = {"pos": tuple(p), "normal": normalize(nrm[vi]), "uv": tex[vi], "rgba": tuple(rgba),
                             "bone": max(inf, key=lambda x: x[1])[0], "inf": inf}
            cited += len(verts)
            for t in range(0, len(idx) - 2, 3):
                v = [verts[idx[t]], verts[idx[t + 1]], verts[idx[t + 2]]]
                bone = Counter(x["bone"] for x in v).most_common(1)[0][0]
                us = [x["uv"][0] for x in v]
                vs = [x["uv"][1] for x in v]
                ou = math.floor(min(us) + EPS)
                ov = math.floor(min(vs) + EPS)
                tris.append({"verts": v, "bone": bone, "flags": flags, "image": image_key,
                             "material": material.get("name"),
                             "local": [(max(0.0, u - ou), max(0.0, w - ov)) for u, w in zip(us, vs)],
                             "raw_uv": list(zip(us, vs))})
    # cuisson des reflets, une fois connus les os de chaque tuile (lecon 4 bis)
    for key, (bimg, simg, fimg) in pending.items():
        users = {glb.names[v["bone"]] for t in tris if t["image"] == key for v in t["verts"]}
        users |= {glb.names[t["bone"]] for t in tris if t["image"] == key}
        gain = 1.0
        attenuated = users & set(GAIN_OS)
        if attenuated:
            if len(users) != 1:
                sys.exit("%s : la tuile %s sert aux os %s ; une tuile attenuee doit etre propre a %s"
                         % (model, key, sorted(users), sorted(attenuated)))
            gain = GAIN_OS[next(iter(attenuated))]
        baked[key] = bake_envmap(bimg, simg, fimg, gain)
        if gain != 1.0:
            full = luminance(bake_envmap(bimg, simg, fimg, 1.0))
            now = luminance(baked[key])
            print("  reflet attenue x%.2f pour %s (%s) : luminance %.1f -> %.1f, satures %.0f %% -> %.0f %% "
                  "(base seule %.1f)" % (gain, key.split("+")[0], ", ".join(sorted(users)), full[0], now[0],
                                         100 * full[1], 100 * now[1], luminance(bimg)[0]))
    sources.update(baked)
    main = glb.names.index("main") if "main" in glb.names else -1
    return {"model": model, "glb": glb, "tris": tris, "bones": bones, "drift": drift, "cited": cited,
            "images": sources, "main": main, "decompose_err": decompose_err}


def bake_pose_tables(glb, bones):
    """Les 13 poses et les 13 transformations en TRS complets (repos pour les os non animes)."""
    rest = [b["trs"] for b in bones]

    def full(chans, k):
        out = []
        for j, node in enumerate(glb.joints):
            paths = chans.get(node)
            if not paths:
                out.append(rest[j])
                continue
            t = paths.get("translation", [rest[j][0:3]] * (k + 1))[k]
            r = paths.get("rotation", [rest[j][3:7]] * (k + 1))[k]
            s = paths.get("scale", [rest[j][7:10]] * (k + 1))[k]
            out.append((*t, *r, *s))
        return out

    poses = []
    for name in POSES:
        times, chans = glb.keys(name)
        if len(times) != 1:
            sys.exit("%s : %d clefs, une seule attendue pour une pose" % (name, len(times)))
        poses.append((name, full(chans, 0)))
    anims = []
    for name in TRANSFORMS:
        times, chans = glb.keys(name)
        if abs(times[0]) > 1e-6:
            sys.exit("%s : premiere clef a %.4f s, 0 attendu" % (name, times[0]))
        anims.append((name, times, [full(chans, k) for k in range(len(times))]))
    return poses, anims


# ------------------------------------------------------------------- ecriture

def write_bin(data, tiles, atlas_size, poses, anims, path):
    aw, ah = atlas_size
    by_key = {t["key"]: t for t in tiles}
    mn = [min(v["pos"][k] for tri in data["tris"] for v in tri["verts"]) for k in range(3)]
    mx = [max(v["pos"][k] for tri in data["tris"] for v in tri["verts"]) for k in range(3)]
    parts = [HEADER.pack(MAGIC, VERSION, len(data["tris"]), len(data["bones"]), len(poses), len(anims),
                         aw, ah, data["main"], RATIO, *mn, *mx)]
    for bone in data["bones"]:
        parts.append(BONE.pack(bone["name"].encode("ascii", "replace")[:32], bone["parent"], 0,
                               *bone["trs"], *bone["ibm"]))
    for tri in data["tris"]:
        tile = by_key[tri["image"]]
        x0, y0 = tile["at"]
        w, h = tile["size"]
        fields = [tri["bone"], tri["flags"], 0]
        for v, (lu, lv) in zip(tri["verts"], tri["local"]):
            fields.append(v["bone"])
            fields.extend(v["pos"])
            fields.append((x0 + BORDER + lu * w) / aw)
            fields.append((y0 + BORDER + lv * h) / ah)
            fields.extend(v["normal"])
            fields.extend(v["rgba"])
        parts.append(TRI.pack(*fields))
    for name, table in poses:
        parts.append(NAME.pack(name.encode("ascii")))
        parts.extend(TRS.pack(*trs) for trs in table)
    for name, times, keys in anims:
        parts.append(ANIM.pack(name.encode("ascii"), len(times), times[-1]))
        parts.append(struct.pack("<%df" % len(times), *times))
        for table in keys:
            parts.extend(TRS.pack(*trs) for trs in table)
    with open(path, "wb") as handle:
        handle.write(b"".join(parts))
    return mn, mx


def read_bin(path):
    """Relit un .bin comme le fera le mod."""
    raw = open(path, "rb").read()
    (magic, version, count, bone_count, pose_count, anim_count, aw, ah, main, ratio,
     *box) = HEADER.unpack_from(raw, 0)
    if magic != MAGIC or version != VERSION:
        sys.exit("%s : magie %r version %d" % (path, magic, version))
    off = HEADER.size
    bones = []
    for i in range(bone_count):
        f = BONE.unpack_from(raw, off)
        off += BONE.size
        if f[1] >= i:
            sys.exit("%s : os %d de parent %d" % (path, i, f[1]))
        bones.append({"name": f[0].rstrip(b"\0").decode("ascii"), "parent": f[1],
                      "trs": f[3:13], "ibm": ibm16(f[13:25])})
    tris = []
    for _ in range(count):
        f = TRI.unpack_from(raw, off)
        off += TRI.size
        verts = []
        for q in range(3):
            b = 3 + q * 13
            verts.append({"bone": f[b], "pos": f[b + 1:b + 4], "uv": f[b + 4:b + 6],
                          "normal": f[b + 6:b + 9], "rgba": f[b + 9:b + 13]})
        tris.append({"bone": f[0], "flags": f[1], "verts": verts})
    poses = {}
    pose_order = []
    for _ in range(pose_count):
        name = NAME.unpack_from(raw, off)[0].rstrip(b"\0").decode("ascii")
        off += NAME.size
        table = [TRS.unpack_from(raw, off + i * TRS.size) for i in range(bone_count)]
        off += bone_count * TRS.size
        poses[name] = table
        pose_order.append(name)
    anims = {}
    anim_order = []
    for _ in range(anim_count):
        name, keys, duration = ANIM.unpack_from(raw, off)
        name = name.rstrip(b"\0").decode("ascii")
        off += ANIM.size
        times = list(struct.unpack_from("<%df" % keys, raw, off))
        off += 4 * keys
        tables = []
        for _k in range(keys):
            tables.append([TRS.unpack_from(raw, off + i * TRS.size) for i in range(bone_count)])
            off += bone_count * TRS.size
        anims[name] = {"times": times, "keys": tables, "duration": duration}
        anim_order.append(name)
    if off != len(raw):
        sys.exit("%s : %d octets lus sur %d" % (path, off, len(raw)))
    return {"atlas": (aw, ah), "main": main, "ratio": ratio, "min": box[:3], "max": box[3:],
            "bones": bones, "tris": tris, "poses": poses, "pose_order": pose_order,
            "anims": anims, "anim_order": anim_order, "size": len(raw)}


# ------------------------------------------------- pose depuis le .bin (= Java)

def rest_table(back):
    return [b["trs"] for b in back["bones"]]


def sample_anim(anim, t):
    times, keys = anim["times"], anim["keys"]
    if t <= times[0] or len(times) == 1:
        return keys[0]
    if t >= times[-1]:
        return keys[-1]
    k = 0
    while times[k + 1] <= t:
        k += 1
    f = (t - times[k]) / (times[k + 1] - times[k])
    out = []
    for a, b in zip(keys[k], keys[k + 1]):
        tr = [a[i] + (b[i] - a[i]) * f for i in range(3)]
        q = nlerp(a[3:7], b[3:7], f)
        s = [a[i] + (b[i] - a[i]) * f for i in range(7, 10)]
        out.append((*tr, *q, *s))
    return out


def skin_of(back, table):
    worlds = []
    for bone, trs in zip(back["bones"], table):
        m = trs_matrix(trs)
        worlds.append(mat_mul(worlds[bone["parent"]], m) if bone["parent"] >= 0 else m)
    return [mat_mul(w, b["ibm"]) for w, b in zip(worlds, back["bones"])], worlds


def bone_scales(skin):
    return [abs(det3(m)) ** (1.0 / 3.0) for m in skin]


def pose_bin(back, table):
    """Triangles poses a l'os unique, et leur visibilite (regle RATIO x main)."""
    skin, worlds = skin_of(back, table)
    scale = bone_scales(skin)
    limit = back["ratio"] * scale[back["main"]] if back["main"] >= 0 else -1.0
    out = []
    for tri in back["tris"]:
        pts = [mat_point(skin[v["bone"]], v["pos"]) for v in tri["verts"]]
        nrm = [normalize(mat_dir(skin[v["bone"]], v["normal"])) for v in tri["verts"]]
        out.append({"p": pts, "n": nrm, "visible": scale[tri["bone"]] >= limit, "tri": tri})
    return out, skin, worlds


def visible_box(posed):
    pts = [p for t in posed if t["visible"] for p in t["p"]]
    lo = [min(p[k] for p in pts) for k in range(3)]
    hi = [max(p[k] for p in pts) for k in range(3)]
    return lo, hi


# ------------------------------------------------------ reference : glb pondere

def pose_glb(data, local):
    """Habillage pondere du glb, visibilite a l'os majoritaire (regle de l'etude)."""
    glb = data["glb"]
    skin = glb.skin(glb.world(local))
    scale = bone_scales(skin)
    main = data["main"]
    out = []
    for tri in data["tris"]:
        pts = []
        for v in tri["verts"]:
            tot = sum(w for _, w in v["inf"])
            pts.append(tuple(sum(w * mat_point(skin[j], v["pos"])[k] for j, w in v["inf"]) / tot
                             for k in range(3)))
        out.append({"p": pts, "visible": scale[tri["bone"]] >= RATIO * scale[main]})
    return out, skin, scale


def compare_visible(ta, tb):
    mism = 0
    dist = 0.0
    for a, b in zip(ta, tb):
        if a["visible"] != b["visible"]:
            mism += 1
        elif a["visible"]:
            dist = max(dist, max(math.dist(p, q) for p, q in zip(a["p"], b["p"])))
    return mism, dist


# ------------------------------------------------------------------ controles

def check_gun(data, back, reference):
    glb = data["glb"]
    report = {"poses": {}, "transformations": {}}
    failed = []

    skin0, _ = skin_of(back, rest_table(back))
    ident = max(abs(m[k] - (1.0 if k in (0, 5, 10, 15) else 0.0)) for m in skin0 for k in range(16))
    report["repos_identite"] = ident
    print("  pose de repos relue du .bin, habillage - identite : %.2e ; recomposition des TRS de repos : %.2e"
          % (ident, data["decompose_err"]))
    if ident > 1e-5:
        failed.append("repos")

    norms = [math.sqrt(sum(x * x for x in trs[3:7]))
             for table in list(back["poses"].values()) + [k for a in back["anims"].values() for k in a["keys"]]
             for trs in table]
    raw_gap = 0.0
    for name in back["pose_order"]:
        skin_b, _ = skin_of(back, back["poses"][name])
        skin_raw = glb.skin(glb.world(glb.sample(name, 0.0, raw=True)))
        raw_gap = max(raw_gap, max(abs(a - b) for ma, mb in zip(skin_b, skin_raw) for a, b in zip(ma, mb)))
    report["quaternions_norme_min_max"] = [min(norms), max(norms)]
    report["habillage_normalise_moins_brut"] = raw_gap
    print("  quaternions ranges : norme de %.6f a %.6f ; renormaliser deplace les matrices des poses de %.1e"
          % (min(norms), max(norms), raw_gap))

    ref_forms = {}
    if reference:
        for pickup, s in reference["formes"].items():
            ref_forms[s["anim"]] = s
    worst_dom = 0.0
    print("  %-17s %5s %5s %6s %9s %-22s %-22s %8s %8s" % ("pose", "vis", "vis*", "diff", "habill.", "taille .bin (m)",
                                                           "etude (m)", "ecart", "os seul"))
    posed_cache = {}
    for name in back["pose_order"]:
        table = back["poses"][name]
        posed, skin_b, _ = pose_bin(back, table)
        posed_cache[name] = posed
        ref, skin_g, scale_g = pose_glb(data, glb.sample(name, 0.0))
        skin_diff = max(abs(a - b) for ma, mb in zip(skin_b, skin_g) for a, b in zip(ma, mb))
        mism = sum(1 for a, b in zip(posed, ref) if a["visible"] != b["visible"])
        nvis = sum(1 for t in posed if t["visible"])
        lo, hi = visible_box(posed)
        size = [hi[k] - lo[k] for k in range(3)]
        rlo, rhi = visible_box(ref)
        size_glb = [rhi[k] - rlo[k] for k in range(3)]
        # ecart de l'os unique, sur les sommets dont l'os est visible (critere de l'etude, section C)
        limit = RATIO * scale_g[data["main"]]
        dom = 0.0
        for tri, a, b in zip(data["tris"], posed, ref):
            for q, v in enumerate(tri["verts"]):
                if scale_g[v["bone"]] >= limit:
                    dom = max(dom, math.dist(a["p"][q], b["p"][q]))
        entry = {"triangles_visibles": nvis, "visibilite_differente_du_glb": mism,
                 "habillage_bin_moins_glb": skin_diff, "taille_bin_m": [round(x, 4) for x in size],
                 "taille_glb_pondere_m": [round(x, 4) for x in size_glb], "ecart_os_unique_m": round(dom, 4)}
        s = ref_forms.get(name)
        size_err = None
        if s:
            size_err = max(abs(a - b) for a, b in zip(size, s["taille_xyz_m"]))
            entry["taille_etude_m"] = s["taille_xyz_m"]
            entry["ecart_taille_etude_m"] = round(size_err, 4)
            entry["triangles_visibles_etude"] = s["triangles_visibles"]
            if size_err > 0.001 + 0.0005 or s["triangles_visibles"] != nvis:
                failed.append("taille ou visibles " + name)
        if mism or skin_diff > 1e-4:
            failed.append("pose " + name)
        if name != "gun-idle":
            worst_dom = max(worst_dom, dom)
        report["poses"][name] = entry
        print("  %-17s %5d %5s %6d %9.1e %-22s %-22s %8s %8.4f" % (
            name, nvis, s["triangles_visibles"] if s else "-", mism, skin_diff,
            "%.3f x %.3f x %.3f" % tuple(size), "%.3f x %.3f x %.3f" % tuple(s["taille_xyz_m"]) if s else "-",
            "%.4f" % size_err if size_err is not None else "-", dom))
    report["ecart_os_unique_max_12_formes_m"] = worst_dom
    print("  ecart maximal de l'os unique sur les 12 formes : %.4f m (etude : 0,017)" % worst_dom)
    if worst_dom > 0.0175:
        failed.append("os unique")

    print("  %-24s %6s %4s | %-40s | %-40s | %s" % ("transformation", "duree", "clef", "debut (tri. diff., ecart)",
                                                   "fin (tri. diff., ecart)", "bin-glb"))
    for name in back["anim_order"]:
        anim = back["anims"][name]
        src, dst = name[len("gun-gun-"):].split("-")
        res = {"duree_s": round(anim["duration"], 4), "clefs": len(anim["times"])}
        cells = []
        for tag, t, want in (("debut", 0.0, IDLE[src]), ("fin", anim["duration"], IDLE[dst])):
            posed, _, _ = pose_bin(back, sample_anim(anim, t))
            mism, dist = compare_visible(posed, posed_cache[want])
            res[tag] = {"pose": want, "triangles_differents": mism, "ecart_m": round(dist, 4)}
            cells.append("%s : %3d, %.3f m" % (want, mism, dist))
        # l'interpolation relue redonne-t-elle celle du glb, entre les clefs ?
        diff = 0.0
        for i in range(9):
            t = anim["duration"] * (i + 0.5) / 9.0
            skin_b, _ = skin_of(back, sample_anim(anim, t))
            skin_g = glb.skin(glb.world(glb.sample(name, t)))
            diff = max(diff, max(abs(a - b) for ma, mb in zip(skin_b, skin_g) for a, b in zip(ma, mb)))
        res["habillage_bin_moins_glb_entre_clefs"] = diff
        if diff > 1e-4:
            failed.append("interpolation " + name)
        report["transformations"][name] = res
        print("  %-24s %6.3f %4d | %-40s | %-40s | %.1e" % (name, anim["duration"], len(anim["times"]),
                                                          cells[0], cells[1], diff))
    return report, failed, posed_cache


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True, help="dossier de sortie des .bin et de morph_gun.png")
    parser.add_argument("--no-png", action="store_true", help="ne pas dessiner l'image de controle")
    parser.add_argument("--report", default=None,
                        help="dossier de controle.json et atlas.json (defaut : --out) ; hors des assets du mod")
    parser.add_argument("--reference", default=REFERENCE_JSON, help="mesures de l'etude (morph-gun.json)")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    gun_glb = Glb(GUN[1])
    models = [read_model(gun_glb, GUN[0])] + [read_model(Glb(name), model) for model, name in AMMO]
    atlas, tiles = build_atlas(models)
    atlas_path = os.path.join(args.out, "morph_gun.png")
    atlas.save(atlas_path)
    atlas = Image.open(atlas_path).convert("RGBA")
    used = sum(t["outer"][0] * t["outer"][1] for t in tiles)
    print("atlas %d x %d, %d tuiles, remplissage %.1f %% -> %s" % (atlas.size[0], atlas.size[1], len(tiles),
                                                                  100.0 * used / (atlas.size[0] * atlas.size[1]),
                                                                  atlas_path))
    for tile in sorted(tiles, key=lambda t: t["key"]):
        print("  %-60s %3d x %-3d repetee %d x %d en %s" % (tile["key"][:60], tile["size"][0], tile["size"][1],
                                                         tile["reps"][0], tile["reps"][1], tile["at"]))

    reference = json.load(open(args.reference)) if os.path.isfile(args.reference) else None
    if reference is None:
        print("  (mesures de l'etude introuvables : %s)" % args.reference)
    report = {"atlas": list(atlas.size), "modeles": {}}
    failed = []
    for data in models:
        if data["model"] == GUN[0]:
            poses, anims = bake_pose_tables(gun_glb, data["bones"])
        else:
            poses, anims = [], []
        path = os.path.join(args.out, data["model"] + ".bin")
        mn, mx = write_bin(data, tiles, atlas.size, poses, anims, path)
        back = read_bin(path)
        mixed = sum(1 for t in back["tris"] if len({v["bone"] for v in t["verts"]}) > 1)
        env = sum(1 for t in back["tris"] if t["flags"] & FLAG_ENVMAP)
        print("%s : %d triangles (%d a plusieurs os, %d a reflet cuit), %d sommets cites, %d os, %d poses, "
              "%d transformations, %d octets -> %s" % (data["model"], len(back["tris"]), mixed, env, data["cited"],
                                                       len(back["bones"]), len(back["poses"]), len(back["anims"]),
                                                       back["size"], path))
        print("  boite au repos min (%.3f, %.3f, %.3f) max (%.3f, %.3f, %.3f) ; %.3f x %.3f x %.3f m"
              % (*mn, *mx, mx[0] - mn[0], mx[1] - mn[1], mx[2] - mn[2]))
        print("  ecart de l'habillage pondere au repos : %.6f m" % data["drift"])
        # relecture : positions, couleurs, texel central, tuiles (controle de jak_vehicle)
        bad_pos, bad_col, bad_texel, outside = check_against_glb(data, back, atlas, tiles)
        bad_bone = sum(1 for a, b in zip(data["tris"], back["tris"])
                       if a["bone"] != b["bone"] or [v["bone"] for v in a["verts"]] != [v["bone"] for v in b["verts"]])
        print("  relecture : %d positions, %d couleurs, %d texels centraux, %d os differents ; %d sommets hors tuile"
              % (bad_pos, bad_col, bad_texel, bad_bone, outside))
        entry = {"triangles": len(back["tris"]), "octets": back["size"], "boite_repos": [list(mn), list(mx)],
                 "relecture": {"positions": bad_pos, "couleurs": bad_col, "texels": bad_texel, "os": bad_bone,
                               "hors_tuile": outside}, "triangles_multi_os": mixed}
        if bad_pos or bad_col or bad_texel or bad_bone or outside:
            failed.append("relecture " + data["model"])
        if data["model"] == GUN[0]:
            gun_report, gun_failed, _ = check_gun(data, back, reference)
            entry.update(gun_report)
            failed.extend(gun_failed)
        report["modeles"][data["model"]] = entry

    report_dir = args.report or args.out
    os.makedirs(report_dir, exist_ok=True)
    with open(os.path.join(report_dir, "controle.json"), "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=1)
    with open(os.path.join(report_dir, "atlas.json"), "w", encoding="utf-8") as handle:
        json.dump({"size": atlas.size, "border": BORDER,
                   "tiles": [{k: t[k] for k in ("key", "size", "reps", "at")} for t in tiles]}, handle, indent=1)
    if failed:
        sys.exit("controles en echec : %s" % ", ".join(failed))
    print("tous les controles passent")


if __name__ == "__main__":
    main()
