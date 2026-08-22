#!/usr/bin/env python3
"""
Palette de blocs du village d'Arcencium -- generateur de textures 16x16.

PRINCIPE DIRECTEUR (le meme que pour les armes) : ~85% de matiere sourde,
~15% d'accents colores. La couleur n'est precieuse que si elle est rare.

Contrainte propre aux blocs : les textures doivent SE RACCORDER (tiling).
Tout le bruit est calcule par hachage des coordonnees modulo 16, et les
bruits lisses sont periodiques par construction. Le mode --check produit
un rendu 3x3 qui rend toute couture visible immediatement.

VEINES : lignes de contour d'un bruit lisse periodique -- elles errent dans
toutes les directions sans axe privilegie (une premiere version en
diagonales formait un treillis facon grillage). Leur couleur DERIVE le long
de la veine (champ de teinte lent) et dans le temps : ce sont des vibrations
de couleur, pas un trait vert.

Usage :
    python tools/block_designer.py            # genere + apercus
    python tools/block_designer.py --check    # + planches de tiling 3x3
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

S = 16
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PV_DIR = os.path.join(ROOT, "tools", "preview", "blocks")


# ------------------------------------------------------------------ bruit

def h(x, y, seed=0):
    """Bruit de hachage deterministe dans [0,1]. Appele avec x,y modulo 16,
    il se raccorde par construction."""
    n = (x * 374761393 + y * 668265263 + seed * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def mottle(x, y, seed, scale=4):
    return h((x // scale) % S, (y // scale) % S, seed)


def vnoise(x, y, seed, period=4, px=None, py=None):
    """Bruit de valeur lisse et PERIODIQUE (la grille de cellules boucle
    modulo 16/period) -> raccord garanti. px/py : periodes distinctes en x
    et y pour un bruit anisotrope (ecorce etiree verticalement)."""
    px = px or period
    py = py or period
    cx, cy = S // px, S // py
    fx, fy = x / px, y / py
    x0, y0 = math.floor(fx), math.floor(fy)
    tx, ty = fx - x0, fy - y0
    sx = tx * tx * (3 - 2 * tx)
    sy = ty * ty * (3 - 2 * ty)

    def c(ix, iy):
        return h(int(ix) % cx, int(iy) % cy, seed)

    a = c(x0, y0) + (c(x0 + 1, y0) - c(x0, y0)) * sx
    b = c(x0, y0 + 1) + (c(x0 + 1, y0 + 1) - c(x0, y0 + 1)) * sx
    return a + (b - a) * sy


def fbm(x, y, seed, px=None, py=None):
    return (0.62 * vnoise(x, y, seed, 8, px, py)
            + 0.38 * vnoise(x, y, seed + 991, 4,
                            (px or 8) // 2 or 1, (py or 8) // 2 or 1))


def mul(c, f):
    return (max(0, min(255, int(c[0] * f))),
            max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))), 255)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return (int(a[0] + (b[0] - a[0]) * t),
            int(a[1] + (b[1] - a[1]) * t),
            int(a[2] + (b[2] - a[2]) * t), 255)


# --------------------------------------------------------------- palettes

GANGUE = (134, 126, 113)
GANGUE_DARK = (96, 90, 80)
FLECK_DEAD = (104, 108, 102)

ARC_DARK = (34, 40, 37)
ARC_MID = (52, 60, 55)
EMERALD = (32, 168, 104)
EMERALD_HI = (140, 240, 186)
EMERALD_LO = (16, 92, 58)

GOLD = (201, 150, 38)
GOLD_HI = (248, 216, 112)
GOLD_LO = (112, 76, 16)

# Bois sombre : la matiere doit porter, les veines ne sont qu'un liseré.
WOOD = (60, 45, 34)          # planche, rangee paire
WOOD_ALT = (52, 39, 29)      # planche, rangee impaire
WOOD_HI = (78, 59, 45)
WOOD_LO = (30, 22, 17)       # joint entre planches
BARK = (38, 30, 26)
BARK_HI = (54, 43, 37)

GRASS = (86, 132, 66)
GRASS_HI = (118, 168, 88)
GRASS_LO = (58, 96, 46)
DIRT = (110, 84, 60)
LEAF = (58, 112, 86)
LEAF_HI = (92, 150, 116)
LEAF_LO = (36, 76, 60)

WOOLS = {
    "vert_de_gris": (122, 148, 128),
    "ocre":         (176, 142, 78),
    "rose_ancien":  (170, 122, 122),
    "bleu_ardoise": (108, 124, 148),
    "ecru":         (198, 188, 166),
}

# Les 5 cristaux : la couleur des veines derive le long de ce cycle
CYCLE = [(255, 96, 106), (255, 156, 48), (96, 196, 255),
         (255, 124, 214), (120, 255, 190)]
# Corruption : le cycle vire au violet, au noir, au vert malade
CYCLE_CORRUPT = [(150, 60, 180), (186, 132, 226), (60, 30, 82),
                 (122, 190, 90), (90, 40, 120)]


def rainbow(pos, cycle=CYCLE):
    n = len(cycle)
    p = (pos % 1.0) * n
    i = int(p) % n
    return mix(cycle[i], cycle[(i + 1) % n], p - int(p))


# --------------------------------------------------------------- veines
#
# Deux approches ecartees avant celle-ci :
#  - diagonales a pente +/-1 : repetees, elles font un treillis (grillage)
#  - contours d'un bruit lisse : a 16 px le bruit n'a que 2-4 cellules,
#    les contours sont des taches floues ou des anneaux epars
# Ici : de vrais MARCHEURS. Chaque veine serpente (cap qui derive), se
# ramifie, et ses coordonnees bouclent modulo 16 -> raccord garanti.
# Resultat : lignes fines et nerveuses qui partent dans tous les sens.

def vein_network(seed, walkers=3, length=14, branch_p=0.12, vertical=False):
    """Retourne {(x,y): (intensite, teinte)} pour un reseau de veines."""
    import random
    rng = random.Random(seed)
    pts = {}
    stack = []
    for w in range(walkers):
        x, y = rng.uniform(0, S), rng.uniform(0, S)
        if vertical:
            ang = math.pi / 2 * rng.choice([1, -1]) + rng.gauss(0, 0.35)
        else:
            ang = rng.uniform(0, 2 * math.pi)
        stack.append((x, y, ang, length, w * 0.37))
    while stack:
        x, y, ang, n, hue = stack.pop()
        for i in range(n):
            ang += rng.gauss(0, 0.28 if vertical else 0.5)
            x = (x + math.cos(ang)) % S
            y = (y + math.sin(ang)) % S
            key = (int(x) % S, int(y) % S)
            inten = 1.0 if rng.random() > 0.25 else 0.72
            pts[key] = (inten, hue + i * 0.045)         # la teinte derive
            if rng.random() < branch_p and n - i > 3:
                stack.append((x, y,
                              ang + rng.choice([-1, 1]) * rng.uniform(0.7, 1.3),
                              max(3, (n - i) // 2), hue + 0.2))
    return pts


def lightning_network(seed, forks=3, main_len=7, depth=3, fork_p=0.38,
                      vertical=False, origin=None):
    """Reseau en ECLAIR : d'un point central partent quelques branches qui
    se subdivisent en ramifications de plus en plus fines et pales, comme
    la foudre. Chaque generation perd en intensite -> les extremites
    s'eteignent naturellement. Coordonnees modulo 16 : ca se raccorde.

    Remplace les marcheurs errants de la version precedente : ceux-ci
    couvraient la tuile uniformement, sans point de depart lisible."""
    import random
    rng = random.Random(seed)
    pts = {}
    ox, oy = origin if origin else (rng.uniform(4, 12), rng.uniform(4, 12))
    stack = []
    for k in range(forks):
        if vertical:
            ang = (math.pi / 2 if k % 2 == 0 else -math.pi / 2) + rng.gauss(0, 0.30)
        else:
            ang = 2 * math.pi * k / forks + rng.gauss(0, 0.45)
        stack.append((ox, oy, ang, main_len, depth, k * 0.31))
    while stack:
        x, y, ang, n, d, hue = stack.pop()
        for i in range(n):
            ang += rng.gauss(0, 0.30 if vertical else 0.38)
            x = (x + math.cos(ang)) % S
            y = (y + math.sin(ang)) % S
            gen = depth - d                       # 0 = tronc, +1 par fourche
            inten = max(0.10, 1.0 - 0.32 * gen - 0.055 * i)
            key = (int(x) % S, int(y) % S)
            if key not in pts or pts[key][0] < inten:
                pts[key] = (inten, hue + i * 0.05)
            if d > 0 and i >= 1 and rng.random() < fork_p:
                stack.append((x, y,
                              ang + rng.choice([-1, 1]) * rng.uniform(0.5, 1.15),
                              max(2, (n - i) - 1), d - 1, hue + 0.17))
    return pts


def paint_network(px, net, phase, cycle=CYCLE, halo=0.22, white=0.30,
                  desat_to=None, desat=0.0):
    """Peint un reseau sur px. L'INTENSITE pilote la force du melange :
    les ramifications fines restent des traits pales, seul le depart claque.
    halo : lueur sur les 4 voisins. desat_to/desat : veines figees."""
    def col_at(hue):
        c = rainbow(hue + phase, cycle)
        return mix(c, desat_to, desat) if desat_to is not None else c

    for (x, y), (inten, hue) in net.items():
        if halo <= 0:
            break
        c = col_at(hue)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            k = ((x + dx) % S, (y + dy) % S)
            if k not in net:
                px[k] = mix(px[k], c, halo * inten)
    for (x, y), (inten, hue) in net.items():
        c = col_at(hue)
        if white > 0 and inten > 0.9:
            c = mix(c, (255, 255, 255), white)
        px[(x, y)] = mix(px[(x, y)], c, min(1.0, 0.10 + 0.90 * inten))
    return px


# -------------------------------------------------------------- motifs

def brick_mask(x, y, bh=4, bw=8):
    row = y // bh
    off = (row % 2) * (bw // 2)
    return (y % bh == 0) or ((x + off) % bw == 0)


# -------------------------------------------------------------- textures

def tex_gangue():
    px = {}
    for y in range(S):
        for x in range(S):
            f = 0.86 + 0.20 * h(x, y, 1) + 0.10 * mottle(x, y, 2, 5)
            c = mul(GANGUE, f)
            if h(x, y, 3) > 0.955:
                c = mul(FLECK_DEAD, 0.9 + 0.3 * h(x, y, 4))
            px[(x, y)] = c
    return px


def tex_gangue_bricks():
    px = tex_gangue()
    for y in range(S):
        for x in range(S):
            if brick_mask(x, y):
                px[(x, y)] = mul(GANGUE_DARK, 0.88 + 0.18 * h(x, y, 5))
            elif y % 4 == 1:
                px[(x, y)] = mul(px[(x, y)], 1.10)
    return px


def tex_gangue_polished():
    px = {}
    for y in range(S):
        for x in range(S):
            f = 0.94 + 0.09 * h(x, y, 6) + 0.05 * mottle(x, y, 7, 8)
            px[(x, y)] = mul(GANGUE, f * 1.06)
    for x in range(S):
        px[(x, 0)] = mul(GANGUE, 1.16)
        px[(x, S - 1)] = mul(GANGUE, 0.82)
    return px


def tex_arcencium_bricks(frame=0, nframes=8, cycle=CYCLE, mid=ARC_MID, seed=10):
    """Brique d'Arcencium : coeur sombre, veines organiques aux couleurs
    qui vibrent. Reservee aux edifices nobles."""
    phase = frame / nframes
    px = {}
    for y in range(S):
        for x in range(S):
            f = 0.88 + 0.24 * h(x, y, seed)
            c = mul(mid if h(x, y, seed + 1) > 0.7 else ARC_DARK, f)
            if brick_mask(x, y):
                c = mul(ARC_DARK, 0.7)
            px[(x, y)] = c
    net = lightning_network(seed + 2, forks=3, main_len=7, depth=3, fork_p=0.42)
    return paint_network(px, net, phase, cycle, halo=0.20, white=0.28)


def tex_corrupted_bricks(frame=0, nframes=8):
    return tex_arcencium_bricks(frame, nframes, CYCLE_CORRUPT, (46, 40, 52), 10)


def tex_veined_stone():
    """Pierre Veinee : liaison entre gangue et brique. Veines FIGEES,
    couleurs eteintes (melangees a la pierre), pas d'animation."""
    px = tex_gangue()
    net = lightning_network(20, forks=2, main_len=6, depth=2, fork_p=0.35)
    return paint_network(px, net, 0.0, CYCLE, halo=0.0, white=0.0,
                         desat_to=GANGUE, desat=0.5)


def tex_chiseled(frame=0, nframes=8):
    t = 0.5 + 0.5 * math.sin(2 * math.pi * frame / nframes)
    px = {}
    for y in range(S):
        for x in range(S):
            px[(x, y)] = mul(ARC_DARK, 0.9 + 0.22 * h(x, y, 30))
    for i in range(S):
        for (a, b) in ((i, 1), (i, S - 2), (1, i), (S - 2, i)):
            if 1 <= a <= S - 2 and 1 <= b <= S - 2:
                px[(a, b)] = GOLD + (255,) if (a + b) % 3 else GOLD_HI + (255,)
    for i in range(S):
        for p in ((i, 0), (i, S - 1), (0, i), (S - 1, i)):
            px[p] = mul(GOLD_LO, 0.8)
    cx = cy = 7.5
    for y in range(S):
        for x in range(S):
            d = abs(x - cx) + abs(y - cy)
            if d <= 4.2:
                hue = rainbow(frame / nframes + d * 0.08)
                if d <= 1.6:
                    px[(x, y)] = mix(hue, (255, 255, 255), 0.3 + 0.5 * t)
                elif d <= 3.0:
                    px[(x, y)] = mix(mul(hue, 0.7), hue, 0.3 + 0.7 * t)
                else:
                    px[(x, y)] = mix(ARC_DARK, mul(hue, 0.6), 0.4 + 0.6 * t)
    return px


def tex_prismatic_glass():
    px = {}
    for y in range(S):
        for x in range(S):
            if x in (0, S - 1) or y in (0, S - 1):
                px[(x, y)] = (198, 226, 214, 235)
                continue
            tint = rainbow(((x + y) % S) / S + 0.1)
            c = mix((225, 240, 235), tint, 0.30)
            a = 60 + int(28 * h(x, y, 40))
            if (x + y) % 7 == 0:
                a += 45
            px[(x, y)] = (c[0], c[1], c[2], min(255, a))
    return px


def tex_lantern():
    px = {(x, y): (0, 0, 0, 0) for y in range(S) for x in range(S)}

    def rect(x0, y0, x1, y1, c):
        for yy in range(y0, y1 + 1):
            for xx in range(x0, x1 + 1):
                px[(xx, yy)] = c + (255,) if len(c) == 3 else c

    rect(6, 0, 9, 1, GOLD_LO)
    rect(7, 1, 8, 2, GOLD)
    rect(4, 3, 11, 4, GOLD)
    rect(5, 2, 10, 2, GOLD_HI)
    rect(4, 11, 11, 12, GOLD)
    rect(5, 13, 10, 13, GOLD_LO)
    for y in range(5, 11):
        for x in range(5, 11):
            if x in (5, 10) or y in (5, 10):
                px[(x, y)] = GOLD_LO + (255,)
            else:
                d = abs(x - 7.5) + abs(y - 7.5)
                px[(x, y)] = (EMERALD_HI if d < 2 else mix(EMERALD, EMERALD_HI, 0.4)) \
                    if d < 2 else mix(EMERALD, EMERALD_HI, 0.4)
                if d < 2:
                    px[(x, y)] = EMERALD_HI + (255,)
    return px


def tex_grass_top():
    px = {}
    for y in range(S):
        for x in range(S):
            f = 0.84 + 0.26 * h(x, y, 50) + 0.10 * mottle(x, y, 51, 4)
            c = mul(GRASS, f)
            if h(x, y, 52) > 0.90:
                c = mul(GRASS_HI, 0.95 + 0.2 * h(x, y, 53))
            if h(x, y, 54) > 0.978:
                c = mix(rainbow(h(x, y, 57)), GRASS_HI, 0.35)   # eclat discret
            px[(x, y)] = c
    return px


def tex_grass_side():
    px = {}
    for y in range(S):
        for x in range(S):
            px[(x, y)] = mul(DIRT, 0.86 + 0.24 * h(x, y, 55))
    for x in range(S):
        for y in range(3 + int(h(x, 0, 56) * 2.4)):
            c = mul(GRASS, 0.84 + 0.26 * h(x, y, 50))
            if h(x, y, 54) > 0.975:
                c = mix(rainbow(h(x, y, 57)), GRASS_HI, 0.35)
            px[(x, y)] = c
    return px


def tex_prism_bloom():
    """Fleur de Prisme : luit faiblement, particules colorees la nuit."""
    px = {(x, y): (0, 0, 0, 0) for y in range(S) for x in range(S)}
    for y in range(6, S):
        px[(7, y)] = mul(GRASS_LO, 0.95 + 0.2 * h(7, y, 60))
        px[(8, y)] = mul(GRASS, 0.9 + 0.2 * h(8, y, 61))
    for (lx, ly, d) in ((5, 10, -1), (10, 12, 1)):
        for k in range(3):
            px[(lx + d * k, ly - k)] = mul(GRASS, 1.0 + 0.06 * k)
    petals = [(7, 3, 0), (8, 3, 1), (6, 4, 2), (9, 4, 3), (7, 5, 4), (8, 5, 4),
              (5, 5, 0), (10, 5, 2), (6, 6, 1), (9, 6, 3)]
    for (x, y, i) in petals:
        px[(x, y)] = CYCLE[i] + (255,)
    for (x, y) in ((7, 2), (8, 2)):
        px[(x, y)] = (250, 245, 255, 255)
    for (x, y) in ((7, 4), (8, 4)):
        px[(x, y)] = (255, 255, 250, 255)
    return px


def tex_wool(color):
    px = {}
    for y in range(S):
        for x in range(S):
            f = 0.90 + 0.16 * h(x, y, 70) + 0.06 * mottle(x, y, 71, 3)
            c = mul(color, f)
            if (x + y * 3) % 5 == 0:
                c = mul(c, 0.95)
            px[(x, y)] = c
    return px


def tex_carpet(color):
    px = tex_wool(color)
    for x in range(S):
        for p in ((x, 0), (x, S - 1), (0, x), (S - 1, x)):
            px[p] = mul(color, 0.80)
    return px


def tex_planks(frame=0, nframes=8):
    """Planches Cristallisees : bois sombre dont les JOINTS portent de fines
    veines colorees qui derivent. La couleur ne vit que dans les rainures --
    a l'interieur d'un batiment, des planches entierement veinees noyaient
    la piece."""
    phase = frame / nframes
    PH = 4                                   # hauteur d'une planche
    px = {}
    for y in range(S):
        for x in range(S):
            row = y // PH
            # fil du bois : bruit etire horizontalement
            f = (0.90 + 0.14 * h(x, y, 80)
                 + 0.10 * vnoise(x, y, 81 + row, px=8, py=2))
            c = mul(WOOD if row % 2 == 0 else WOOD_ALT, f)
            if h(x, y, 82) > 0.955:
                c = mul(WOOD_HI, 0.95 + 0.15 * h(x, y, 83))
            px[(x, y)] = c

    # joints horizontaux : rainure sombre + veine coloree par segments
    for row in range(S // PH):
        y = row * PH
        for x in range(S):
            px[(x, y)] = mul(WOOD_LO, 0.9 + 0.25 * h(x, y, 84))
            # Seuil haut : la veine n'apparait que par courts segments, avec
            # de longues interruptions. Sur toute la longueur du joint, elle
            # se lisait comme une rayure.
            seg = vnoise(x, y, 85 + row * 13, px=4, py=16)
            if seg > 0.60:
                strength = min(1.0, (seg - 0.60) / 0.22)
                col = rainbow(x / S * 0.55 + row * 0.23 + phase)
                px[(x, y)] = mix(px[(x, y)], col, 0.42 * strength)

    # bouts de planches : joints verticaux decales d'une rangee a l'autre
    for row in range(S // PH):
        xj = (row * 7 + 3) % S
        for dy in range(1, PH):
            px[(xj, row * PH + dy)] = mul(WOOD_LO, 1.0 + 0.2 * h(xj, dy, 86))
    return px


# ----------------------------------------------------- l'Arbre de Prisme

# ------------------------------------------------- l Arbre de Prisme : bois
#
# Buches en 64x64. Le motif est une DENDRITE, d apres les references :
#   - chaque branche principale garde SA couleur sur tout son trajet, le
#     spectre etant reparti entre les branches (et non le long d une branche)
#   - les branches sont epaisses au depart (2-3 px) et s affinent en se
#     ramifiant ; les ramifications sont courtes et nettes
#   - trajectoires LONGUES et LISSES : une courbure douce, pas de bruit
#   - fond sombre et calme, pour que l eclair porte

LOG_S = 64


def _vn(x, y, seed, N, px, py):
    """Bruit de valeur lisse periodique, a taille N (cf. vnoise)."""
    cx, cy = max(N // px, 1), max(N // py, 1)
    fx, fy = x / px, y / py
    x0, y0 = math.floor(fx), math.floor(fy)
    tx, ty = fx - x0, fy - y0
    sx = tx * tx * (3 - 2 * tx)
    sy = ty * ty * (3 - 2 * ty)

    def c(ix, iy):
        return h(int(ix) % cx, int(iy) % cy, seed)

    a = c(x0, y0) + (c(x0 + 1, y0) - c(x0, y0)) * sx
    b = c(x0, y0 + 1) + (c(x0 + 1, y0 + 1) - c(x0, y0 + 1)) * sx
    return a + (b - a) * sy


def spectrum(t):
    """Arc-en-ciel sature (rouge -> violet -> rouge)."""
    t = t % 1.0
    stops = [(255, 40, 40), (255, 140, 0), (250, 225, 30), (40, 220, 60),
             (30, 160, 255), (120, 60, 255), (230, 50, 220), (255, 40, 40)]
    q = t * (len(stops) - 1)
    i = int(q)
    return mix(stops[i], stops[i + 1], q - i)


def _stamp(px, x, y, col, radius, alpha, N, wrap):
    """Pose un disque de rayon radius (en px) a (x,y), fondu sur les bords."""
    r = max(radius, 0.5)
    ri = int(math.ceil(r))
    for dy in range(-ri, ri + 1):
        for dx in range(-ri, ri + 1):
            d = math.hypot(dx, dy)
            if d > r + 0.5:
                continue
            X, Y = int(round(x)) + dx, int(round(y)) + dy
            if wrap:
                X, Y = X % N, Y % N
            elif not (0 <= X < N and 0 <= Y < N):
                continue
            cover = 1.0 if d <= r - 0.5 else (r + 0.5 - d)
            a = alpha * cover
            if a <= 0:
                continue
            old = px[(X, Y)]
            px[(X, Y)] = mix(old, col, a)


def dendrite_draw(px, N, seed, origin, primaries, length, depth, fork_p,
                  phase, curve=0.03, wrap=False, spread=None, base_angle=0.0,
                  hue_span=1.0, hue_offset=0.0, thick0=1.4):
    """Dessine une dendrite directement (trait epais, fondu).

    Couleur : chaque branche PRIMAIRE a sa teinte (repartie sur hue_span),
    heritee par ses ramifications avec une legere derive. La phase anime.
    Trajectoire : courbure douce et constante par branche + tres leger
    tremblement -> longues branches lisses, comme la foudre."""
    import random
    rng = random.Random(seed)
    ox, oy = origin
    stack = []
    for k in range(primaries):
        if spread is None:
            ang = 2 * math.pi * k / primaries + rng.gauss(0, 0.25)
        else:
            ang = base_angle + spread * (k / max(primaries - 1, 1) - 0.5) \
                + rng.gauss(0, 0.15)
        hue = hue_offset + hue_span * k / primaries
        stack.append((ox, oy, ang, length, depth, hue, thick0,
                      rng.uniform(-curve, curve)))
    while stack:
        x, y, ang, n, d, hue, thick, bend = stack.pop()
        gen = depth - d
        for i in range(n):
            ang += bend + rng.gauss(0, 0.035)
            x += math.cos(ang)
            y += math.sin(ang)
            if wrap:
                x, y = x % N, y % N
            elif not (-1 <= x < N + 1 and -1 <= y < N + 1):
                break
            t = i / max(n - 1, 1)
            w = thick * (1.0 - 0.55 * t)             # la branche s affine
            alpha = (0.95 - 0.18 * gen) * (1.0 - 0.35 * t)
            col = spectrum(hue + phase + 0.06 * t)
            _stamp(px, x, y, col, w, alpha, N, wrap)
            # ramification courte, plus fine, qui herite la couleur
            if d > 0 and i >= 3 and i < n - 2 and rng.random() < fork_p:
                side = rng.choice([-1, 1])
                stack.append((x, y, ang + side * rng.uniform(0.55, 1.05),
                              max(4, int((n - i) * 0.55)), d - 1,
                              hue + rng.uniform(-0.04, 0.04),
                              w * 0.7, rng.uniform(-curve, curve) * 1.5))


def tex_prism_log_top(frame=0, nframes=8):
    """Coupe : eclat radial depuis le coeur. Fond : cernes sombres, calmes."""
    N = LOG_S
    phase = frame / nframes
    c = (N - 1) / 2.0
    px = {}
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - c, y - c)
            ring = 0.92 + 0.08 * math.sin(d * 0.9)
            f = ring * (0.90 + 0.10 * _vn(x, y, 96, N, 16, 16))
            base = mix(WOOD_LO, WOOD, 0.35)
            px[(x, y)] = mul(base, f)
    dendrite_draw(px, N, 200, (c, c), primaries=8, length=34, depth=3,
                  fork_p=0.11, phase=phase, curve=0.028, wrap=False,
                  hue_span=1.0, thick0=1.5)
    # coeur : petit disque blanc chaud
    _stamp(px, c, c, (255, 245, 235, 255), 2.0, 1.0, N, False)
    return px


def tex_prism_log(frame=0, nframes=8):
    """Ecorce : deux eclairs qui courent dans le fil, branches longues."""
    N = LOG_S
    phase = frame / nframes
    px = {}
    for y in range(N):
        for x in range(N):
            f = 0.86 + 0.20 * _vn(x, y, 90, N, 8, 32) + 0.06 * h(x, y, 91)
            base = BARK_HI if h(x, y // 8, 92) > 0.90 else BARK
            px[(x, y)] = mul(base, f)
    for k, (ox, oy, base, off) in enumerate(((14, 10, math.pi / 2, 0.0),
                                             (48, 42, -math.pi / 2, 0.5))):
        dendrite_draw(px, N, 95 + k * 31, (ox, oy), primaries=3, length=30,
                      depth=3, fork_p=0.10, phase=phase, curve=0.03, wrap=True,
                      spread=1.3, base_angle=base, hue_span=0.5,
                      hue_offset=off, thick0=1.3)
    return px


def tex_prism_leaves():
    """Feuillage : vert-bleu profond troue comme le feuillage vanilla,
    seme d'eclats des 5 cristaux. Pas de teinte de biome : couleur fixe."""
    px = {}
    for y in range(S):
        for x in range(S):
            n = h(x, y, 100)
            if n > 0.80:
                px[(x, y)] = (0, 0, 0, 0)                      # trou
                continue
            f = 0.82 + 0.30 * h(x, y, 101) + 0.10 * mottle(x, y, 102, 4)
            c = mul(LEAF_HI if h(x, y, 103) > 0.82 else LEAF, f)
            if h(x, y, 104) > 0.975:                           # eclat : rare
                c = mix(rainbow(h(x, y, 105)), (255, 255, 255), 0.25)
            px[(x, y)] = c
    return px


def tex_prism_tuft():
    """Herbe de Prisme : touffe de brins (modele en croix) dont les pointes
    portent une lueur coloree -- a melanger avec herbe et fleurs vanilla."""
    px = {(x, y): (0, 0, 0, 0) for y in range(S) for x in range(S)}
    blades = [                      # (x_base, hauteur, derive, teinte)
        (3, 7, 0.35, 0.05), (6, 10, -0.2, 0.3), (8, 12, 0.1, 0.55),
        (10, 9, -0.3, 0.8), (12, 6, 0.25, 0.2), (5, 5, 0.0, 0.65),
    ]
    for (bx, hgt, drift, hue) in blades:
        for k in range(hgt):
            x = int(round(bx + drift * k))
            y = S - 1 - k
            if not (0 <= x < S):
                continue
            f = 0.85 + 0.25 * (k / hgt)
            c = mul(GRASS if k < hgt - 3 else GRASS_HI, f)
            if k >= hgt - 2:                       # pointe lumineuse
                c = mix(rainbow(hue), (255, 255, 255), 0.35 if k == hgt - 1 else 0.1)
            px[(x, y)] = c
    return px


def tex_glass_pane_top():
    """Tranche superieure des vitres : un liseré de 2 px au centre."""
    px = {(x, y): (0, 0, 0, 0) for y in range(S) for x in range(S)}
    for y in range(S):
        px[(7, y)] = (198, 226, 214, 255)
        px[(8, y)] = (176, 208, 200, 255)
    return px


def tex_prism_sapling():
    px = {(x, y): (0, 0, 0, 0) for y in range(S) for x in range(S)}
    for y in range(8, S):
        px[(7, y)] = BARK + (255,)
        px[(8, y)] = BARK_HI + (255,)
    leaves = [(7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (9, 6), (5, 7), (6, 7),
              (9, 7), (10, 7), (7, 7), (8, 7), (6, 8), (9, 8), (7, 4), (8, 4)]
    for i, (x, y) in enumerate(leaves):
        px[(x, y)] = mul(LEAF_HI if i % 3 else LEAF, 1.0)
    for (x, y, i) in ((7, 3, 4), (6, 5, 2), (9, 5, 0), (8, 8, 3)):
        px[(x, y)] = CYCLE[i] + (255,)
    return px


# ------------------------------------------------------------ generation

STATIC = {
    "gangue_stone":        (tex_gangue, "Pierre de Gangue -- la masse"),
    "gangue_bricks":       (tex_gangue_bricks, "Gangue Taillee"),
    "gangue_polished":     (tex_gangue_polished, "Gangue Polie"),
    "veined_stone":        (tex_veined_stone, "Pierre Veinee -- liaison, veines figees"),
    "prismatic_glass":     (tex_prismatic_glass, "Verre Prismatique -- les fenetres"),
    "arcencium_lantern":   (tex_lantern, "Lanterne d'Arcencium"),
    "prismatic_grass_top": (tex_grass_top, "Herbe Prismatique (dessus)"),
    "prismatic_grass_side": (tex_grass_side, "Herbe Prismatique (cote)"),
    "prism_bloom":         (tex_prism_bloom, "Fleur de Prisme"),
    "prism_leaves":        (tex_prism_leaves, "Feuillage de Prisme"),
    "prism_sapling":       (tex_prism_sapling, "Pousse de Prisme"),
    "prismatic_glass_pane_top": (tex_glass_pane_top, "Vitre (tranche)"),
    "prism_tuft":          (tex_prism_tuft, "Herbe de Prisme -- touffe lumineuse"),
}
for _name, _col in WOOLS.items():
    STATIC["wool_" + _name] = ((lambda c: (lambda: tex_wool(c)))(_col),
                               "Laine " + _name.replace("_", " "))
    STATIC["carpet_" + _name] = ((lambda c: (lambda: tex_carpet(c)))(_col),
                                 "Tapis " + _name.replace("_", " "))

# (fonction(frame, n), n frames, frametime ticks) -- vibrations lentes
ANIMATED = {
    "arcencium_bricks":   (tex_arcencium_bricks, 8, 6, "Brique d'Arcencium -- veines vibrantes"),
    "corrupted_bricks":   (tex_corrupted_bricks, 8, 6, "Brique Corrompue"),
    # prism_log / prism_log_top : produits par tools/log_from_refs.py a partir
    # des images de reference de l'auteur (tools/refs/). NE PAS regenerer ici.
    "crystal_planks":     (tex_planks, 8, 6, "Planches Cristallisees -- veines dans les joints"),
    "chiseled_arcencium": (tex_chiseled, 8, 4, "Brique Ciselee -- coeur pulsant"),
}


def to_image(px):
    """La taille est deduite des cles : les buches sont en 32x32."""
    from PIL import Image
    n = max(k[0] for k in px) + 1
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    ip = img.load()
    for (x, y), c in px.items():
        ip[x, y] = c
    return img


def tile3(img):
    from PIL import Image
    w = img.width
    t = Image.new("RGBA", (w * 3, w * 3), (0, 0, 0, 0))
    for a in range(3):
        for b in range(3):
            t.alpha_composite(img, (a * w, b * w))
    return t


# Noms de fichiers dans le mod (IDs anglais, coherents avec le reste du mod)
INSTALL_NAMES = {
    "gangue_stone": "gangue_stone",
    "gangue_bricks": "gangue_bricks",
    "gangue_polished": "polished_gangue",
    "veined_stone": "veined_stone",
    "prismatic_glass": "prismatic_glass",
    "prismatic_glass_pane_top": "prismatic_glass_pane_top",
    "arcencium_lantern": "arcencium_lantern",
    "prismatic_grass_top": "prismatic_grass_block_top",
    "prismatic_grass_side": "prismatic_grass_block_side",
    "prism_bloom": "prism_bloom",
    "crystal_planks": "crystal_planks",
    "prism_log_top": "prism_log_top",
    "prism_leaves": "prism_leaves",
    "prism_sapling": "prism_sapling",
    "prism_tuft": "prism_tuft",
    "wool_vert_de_gris": "verdigris_wool", "carpet_vert_de_gris": "verdigris_carpet",
    "wool_ocre": "ochre_wool",             "carpet_ocre": "ochre_carpet",
    "wool_rose_ancien": "old_rose_wool",   "carpet_rose_ancien": "old_rose_carpet",
    "wool_bleu_ardoise": "slate_blue_wool", "carpet_bleu_ardoise": "slate_blue_carpet",
    "wool_ecru": "ecru_wool",              "carpet_ecru": "ecru_carpet",
    "arcencium_bricks": "arcencium_bricks",
    "corrupted_bricks": "corrupted_bricks",
    "prism_log": "prism_log",
    "chiseled_arcencium": "chiseled_arcencium",
}


def install():
    """Ecrit toutes les textures (et .mcmeta des animees) dans le mod,
    plus la texture de la particule 'prism_mote'."""
    from PIL import Image
    block_dir = os.path.join(ROOT, "src", "main", "resources", "assets",
                             "emeraldweapons", "textures", "block")
    part_dir = os.path.join(ROOT, "src", "main", "resources", "assets",
                            "emeraldweapons", "textures", "particle")
    os.makedirs(block_dir, exist_ok=True)
    os.makedirs(part_dir, exist_ok=True)
    for name, (fn, desc) in STATIC.items():
        dest = os.path.join(block_dir, INSTALL_NAMES[name] + ".png")
        to_image(fn()).save(dest)
    for name, (fn, n, ft, desc) in ANIMATED.items():
        dest = os.path.join(block_dir, INSTALL_NAMES[name] + ".png")
        first = to_image(fn(0, n))
        w = first.width
        sheet = Image.new("RGBA", (w, w * n))
        for i in range(n):
            sheet.paste(to_image(fn(i, n)), (0, i * w))
        sheet.save(dest)
        with open(dest + ".mcmeta", "w") as fh:
            fh.write('{"animation": {"frametime": %d, "interpolate": true}}' % ft)
            fh.write(chr(10))
    # particule : point doux 8x8, blanc (teinte par le code client)
    mote = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    mp = mote.load()
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            a = max(0, min(255, int(255 * (1.0 - d / 3.6))))
            mp[x, y] = (255, 255, 255, a)
    mote.save(os.path.join(part_dir, "prism_mote.png"))
    print("Installe : %d textures de blocs + prism_mote.png"
          % (len(STATIC) + len(ANIMATED)))


def main():
    from PIL import Image
    if "--install" in sys.argv:
        install()
        return
    os.makedirs(PV_DIR, exist_ok=True)
    check = "--check" in sys.argv
    made = []
    for name, (fn, desc) in STATIC.items():
        img = to_image(fn())
        img.save(os.path.join(PV_DIR, name + ".png"))
        made.append((name, desc, img))
        print("%-22s %s" % (name, desc))

    for name, (fn, n, ft, desc) in ANIMATED.items():
        frames = [to_image(fn(f, n)) for f in range(n)]
        w = frames[0].width
        sheet = Image.new("RGBA", (w, w * n))
        for i, fr in enumerate(frames):
            sheet.paste(fr, (0, i * w))
        sheet.save(os.path.join(PV_DIR, name + ".png"))
        with open(os.path.join(PV_DIR, name + ".png.mcmeta"), "w") as fh:
            fh.write('{"animation": {"frametime": %d, "interpolate": true}}\n' % ft)
        # GIF en tuile 3x3 : on juge le raccord ET la vibration d'un coup
        gif = [tile3(fr).resize((S * 3 * 5, S * 3 * 5), Image.NEAREST)
                        .convert("P", palette=Image.ADAPTIVE) for fr in frames]
        gif[0].save(os.path.join(PV_DIR, name + "_anim.gif"), save_all=True,
                    append_images=gif[1:], duration=ft * 50, loop=0)
        made.append((name, desc, frames[0]))
        print("%-22s %s (anime, %d frames)" % (name, desc, n))

    cols = 7
    rows = (len(made) + cols - 1) // cols
    cell = S * 6
    sheetimg = Image.new("RGBA", (cols * cell, rows * cell), (139, 139, 139, 255))
    for i, (_n, _d, im) in enumerate(made):
        sheetimg.alpha_composite(im.resize((cell, cell), Image.NEAREST),
                                 ((i % cols) * cell, (i // cols) * cell))
    sheetimg.save(os.path.join(PV_DIR, "_palette.png"))
    print("\nPlanche : %s" % os.path.relpath(os.path.join(PV_DIR, "_palette.png"), ROOT))

    if check:
        keys = ["gangue_stone", "gangue_bricks", "veined_stone",
                "prism_leaves", "prismatic_grass_top", "crystal_planks"]
        tile = S * 3 * 5
        out = Image.new("RGBA", (tile * len(keys), tile), (139, 139, 139, 255))
        for i, k in enumerate(keys):
            out.alpha_composite(tile3(to_image(STATIC[k][0]()))
                                .resize((tile, tile), Image.NEAREST), (i * tile, 0))
        out.save(os.path.join(PV_DIR, "_tiling.png"))
        print("Tiling  : %s" % os.path.relpath(os.path.join(PV_DIR, "_tiling.png"), ROOT))


if __name__ == "__main__":
    main()
