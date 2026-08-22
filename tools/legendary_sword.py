#!/usr/bin/env python3
"""
Variantes legendaires animees de l'epee V1 (Emeraude Taillee).

Chaque variante est generee en 12 frames 32x32 :
  - une vague de lumiere remonte la lame de la base vers la pointe
  - la ou les gemmes pulsent (battement lent)
  - des etincelles scintillent autour de la lame

Sorties par variante (dans tools/preview/) :
  <nom>_x14.png    apercu fixe agrandi de la frame 0
  <nom>_anim.gif   apercu ANIME agrandi (pour juger la vague avant d'installer)
  <nom>_sheet.png  spritesheet 32x384 (12 frames empilees), pret pour Minecraft

Usage :
    python tools/legendary_sword.py                       # genere les 3 variantes
    python tools/legendary_sword.py --install l1_couronnee
    python tools/legendary_sword.py --install l1_couronnee --as fulgurite

--install copie la spritesheet dans assets/.../textures/item/<nom>.png
et ecrit le .mcmeta d'animation correspondant.
"""

import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sword_designer as sd
from PIL import Image

W, H = sd.W, sd.H
NFRAMES = 12
RAMP = '123456'

ROOT = sd.ROOT
PV_DIR = sd.PV_DIR
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "emeraldweapons", "textures", "item")


# ------------------------------------------------------------------ lames

def draw_blade_core(g, base_hw=2.6, runes=False, seed=5):
    """Lame a coeur lumineux : un axe central incandescent qui fond vers
    des aretes sombres. Rend l'arme 'chargee d'energie' meme a l'arret."""
    rng = random.Random(seed)
    for y in range(H):
        for x in range(W):
            u, v = sd.to_uv(x, y, sd.BLADE_X0, sd.BLADE_Y0)
            if u < -0.5 or u > sd.BLADE_LEN:
                continue
            hw = sd.blade_half_width(max(u, 0.0), sd.BLADE_LEN, base_hw)
            if hw <= 0 or abs(v) > hw:
                continue
            s = v / hw
            a = abs(s)
            if a <= 0.15:
                ch = '6'                      # coeur incandescent
            elif a <= 0.45:
                ch = '5'
            elif s < 0:
                ch = '4'                      # face eclairee
            elif s < 0.78:
                ch = '2'                      # face a l'ombre
            else:
                ch = '1'
            # petits eclats sur l'arete superieure
            if s < -0.6 and rng.random() < 0.15:
                ch = '5'
            # runes : glyphes graves en travers du coeur, tous les 4 crans
            if runes and int(round(u)) % 4 == 2 and a <= 0.5:
                ch = 'R'
            put = sd.put
            put(g, x, y, ch)


def add_wings(g, chars=('a', 'b', 'c')):
    """Prolonge les extremites de la garde en ailerons dores vers le haut."""
    dark, mid, light = chars
    hi = 6
    for sign in (1, -1):
        bx, by = sd.GUARD_X + sign * hi, sd.GUARD_Y + sign * hi
        sd.put(g, bx + 1, by - 1, light)
        sd.put(g, bx + 2, by - 2, mid)
        sd.put(g, bx + 3, by - 3, dark)


def add_shards(g):
    """Eclats de cristal flottants autour de la lame (aura magique).
    Poses uniquement sur des cases vides, AVANT le contour pour rester libres."""
    spots = [(4.0, 1), (7.0, -1), (10.0, 1), (13.0, -1), (15.5, 1), (6.0, 1)]
    placed = []
    for u, side in spots:
        hw = sd.blade_half_width(u, sd.BLADE_LEN, 2.6)
        v = side * (hw + 1.8)
        x = int(round(sd.BLADE_X0 + u + v))
        y = int(round(sd.BLADE_Y0 + v - u))
        if 0 <= x < W and 0 <= y < H and g[y][x] == '.':
            g[y][x] = 'x'
            placed.append((x, y))
    return placed


SPARKLES = [(26, 2, 0), (20, 5, 4), (29, 8, 8), (14, 11, 2),
            (23, 13, 6), (9, 16, 10), (17, 18, 7)]


def place_sparkles(frame_grid, frame_idx, base_grid):
    """Etincelles blanches visibles 3 frames chacune, en quinconce."""
    for x, y, phase in SPARKLES:
        if base_grid[y][x] != '.':
            continue
        if (frame_idx + phase) % NFRAMES < 3:
            frame_grid[y][x] = '*'


# Particules ephemeres : naissent pres de la lame, TOMBENT en s'estompant,
# puis disparaissent. (x, y) = point d'apparition, birth = frame de naissance.
FALL_LIFE = 4                     # duree de vie en frames
FALL_FADE = ['*', 'p', 'p', 'q']  # flash blanc -> vert clair -> vert eteint


# Positions d'apparition exprimees en FRACTION de la longueur de lame, pour
# rester correctes quel que soit le layout. (fraction, cote de la lame)
FALL_SITES = [(0.19, 1), (0.33, -1), (0.47, 1), (0.61, -1),
              (0.75, 1), (0.86, -1), (0.28, 1), (0.56, 1)]


def falling_spawns(base_hw=2.3):
    """Points d'apparition le long des deux tranches de la lame, naissances
    reparties sur toute la boucle. Lit le layout courant : a appeler pendant
    la construction de la variante, pas au chargement du module."""
    spawns = []
    for i, (frac, side) in enumerate(FALL_SITES):
        u = sd.BLADE_LEN * frac
        hw = sd.blade_half_width(u, sd.BLADE_LEN, base_hw)
        v = side * (hw + 1.3)
        x = int(round(sd.BLADE_X0 + u + v))
        y = int(round(sd.BLADE_Y0 + v - u))
        spawns.append((x, y, (i * 5) % NFRAMES))
    return spawns


def place_falling(frame_grid, frame_idx, base_grid, spawns):
    """Dessine chaque particule a sa position du moment : elle descend d'un
    pixel par frame et s'eteint progressivement, puis n'est plus dessinee."""
    for x, y0, birth in spawns:
        age = (frame_idx - birth) % NFRAMES
        if age >= FALL_LIFE:
            continue                        # morte : invisible le reste du cycle
        y = y0 + age                        # chute d'un pixel par frame
        if 0 <= x < W and 0 <= y < H and base_grid[y][x] == '.':
            frame_grid[y][x] = FALL_FADE[age]


# Palette assombrie : on etire la plage tonale vers le bas (noir-vert profond)
# au lieu de rester dans les verts clairs. Le contraste est ce qui fait lire
# une arme comme dangereuse ; une lame uniformement claire lit "decoratif".
DARK_EMERALD = {
    'o': (4, 24, 16, 255),        # contour sombre, mais pas confondu avec '1'
    '1': (5, 34, 21, 255),
    '2': (10, 66, 40, 255),
    '3': (18, 112, 67, 255),
    '4': (52, 178, 116, 255),
    '5': (120, 232, 174, 255),
    '6': (231, 255, 244, 255),
}


def draw_blade_barbed(g, base_hw=2.4, barb_zone=0.5, runes=False, seed=7):
    """Lame dentelee : sur la tranche a l'ombre, des barbelures saillent pres
    de la garde. Brise la ligne droite et donne une silhouette agressive."""
    rng = random.Random(seed)
    for y in range(H):
        for x in range(W):
            u, v = sd.to_uv(x, y, sd.BLADE_X0, sd.BLADE_Y0)
            if u < -0.5 or u > sd.BLADE_LEN:
                continue
            hw = sd.blade_half_width(max(u, 0.0), sd.BLADE_LEN, base_hw)
            if hw <= 0:
                continue
            # les dents ne mordent que le premier tiers, cote ombre
            barb = (u / sd.BLADE_LEN < barb_zone and int(round(u)) % 4 == 1)
            hw_shadow = hw + (0.75 if barb else 0.0)
            if v < -hw or v > hw_shadow:
                continue

            if v > hw:
                ch = '1'                        # la dent elle-meme : arete sombre
            else:
                s = v / hw
                ch = sd.shade_char(s)
                if s < -0.45 and rng.random() < 0.14:
                    ch = '6'
                if runes and int(round(u)) % 4 == 2 and abs(s) <= 0.35:
                    ch = 'R'
            sd.put(g, x, y, ch)


def forged_char(s):
    """Coupe transversale 'forgee' (A+B+C), du fil (s=-1, arete haute,
    cote lumiere comme vanilla et comme la V1) au dos (s=+1) :

        fil       flanc    GOUTTIERE   flanc    dos
        '6' '5'   '4' '4'     '2'      '3' '3'  '1' '1'

    - non monotone : la gouttiere centrale est plus sombre que ses flancs
      ('4' > '2' < '3' — l'anatomie d'une vraie lame, comme vanilla) ;
    - asymetrique : fil affute clair d'un pixel en haut, dos epais et
      sombre en bas ;
    - majoritairement sombre : le clair reste rare, donc il brille."""
    if s <= -0.78:
        return '6'
    if s <= -0.55:
        return '5'
    if s <= -0.10:
        return '4'
    if s <= 0.20:
        return '2'
    if s <= 0.60:
        return '3'
    return '1'


def draw_blade_forged(g, base_hw=2.4, runes=False, seed=7):
    """Lame forgee : gouttiere, fil asymetrique, runes gravees dans la
    gouttiere. Voir forged_char pour la coupe."""
    rng = random.Random(seed)
    for y in range(H):
        for x in range(W):
            u, v = sd.to_uv(x, y, sd.BLADE_X0, sd.BLADE_Y0)
            if u < -0.5 or u > sd.BLADE_LEN:
                continue
            hw = sd.blade_half_width(max(u, 0.0), sd.BLADE_LEN, base_hw)
            if hw <= 0 or abs(v) > hw:
                continue
            s = v / hw
            if hw <= 1.2:
                # la pointe : tout devient fil, elle s'illumine
                ch = '6' if s < 0 else '5'
            else:
                ch = forged_char(s)
                # reflet ponctuel sur le flanc eclaire : tres rare, donc precieux
                if -0.55 < s <= -0.10 and rng.random() < 0.05:
                    ch = '6'
                # runes gravees dans la gouttiere (la ou une vraie lame
                # porte ses inscriptions) : le pulse ressort sur le sombre
                if runes and int(round(u)) % 4 == 2 and abs(s) <= 0.20 \
                        and 1.5 <= u <= sd.BLADE_LEN - 3.0:
                    ch = 'R'
            sd.put(g, x, y, ch)


# -------------------------------------------------------------- variantes

def build_l1():
    """L1 - Lame Couronnee : coeur lumineux, garde doree ailee, double gemme."""
    sd.set_layout(**sd.LAYOUT_CLASSIC)
    g = sd.blank()
    draw_blade_core(g, base_hw=2.6, seed=5)
    sd.draw_guard(g, half=6.0, thickness=1.5, hooks=True,
                  chars=('a', 'b', 'c'), hook_len=3.5)
    add_wings(g)
    sd.draw_handle(g, half=1.4, wrapped=False)
    sd.draw_pommel(g, radius=2.5, gem=True)
    sd.draw_center_gem(g, big=True)
    sd.add_outline(g)
    pal = sd.palette_of(sd.GOLD, sd.HANDLE_DARK, sd.GEM_BRIGHT)
    return g, pal, dict(sparkles=True)


def build_l2():
    """L2 - Lame Astrale : lame facettee V1, eclats de cristal en orbite."""
    sd.set_layout(**sd.LAYOUT_CLASSIC)
    g = sd.blank()
    sd.draw_blade(g, base_hw=2.4, seed=7)
    sd.draw_guard(g, half=5.5, thickness=1.4, hooks=True, chars=('a', 'b', 'c'))
    sd.draw_handle(g, half=1.35, wrapped=False)
    sd.draw_pommel(g, radius=2.4, gem=True)
    sd.draw_center_gem(g, big=True)
    sd.add_outline(g)
    add_shards(g)          # apres le contour : les eclats flottent librement
    pal = sd.palette_of(sd.GOLD, sd.HANDLE_DARK, sd.GEM_BRIGHT)
    return g, pal, dict(sparkles=True)


def build_l3():
    """L3 - Apotheose : coeur lumineux + runes pulsantes + ailerons + eclats."""
    sd.set_layout(**sd.LAYOUT_CLASSIC)
    g = sd.blank()
    draw_blade_core(g, base_hw=2.7, runes=True, seed=9)
    sd.draw_guard(g, half=6.2, thickness=1.5, hooks=True,
                  chars=('a', 'b', 'c'), hook_len=3.5)
    add_wings(g)
    sd.draw_handle(g, half=1.4, wrapped=False)
    sd.draw_pommel(g, radius=2.5, gem=True)
    sd.draw_center_gem(g, big=True)
    sd.add_outline(g)
    add_shards(g)          # apres le contour : les eclats flottent librement
    pal = sd.palette_of(sd.GOLD, sd.HANDLE_DARK, sd.GEM_BRIGHT)
    return g, pal, dict(sparkles=True)


def add_runes(g, base_hw):
    """Grave des runes pulsantes ('R') sur l'axe d'une lame deja dessinee."""
    for y in range(H):
        for x in range(W):
            if g[y][x] not in RAMP:
                continue
            u, v = sd.to_uv(x, y, sd.BLADE_X0, sd.BLADE_Y0)
            if u < 1.0 or u > sd.BLADE_LEN - 2.5:
                continue
            hw = sd.blade_half_width(u, sd.BLADE_LEN, base_hw)
            if hw > 0 and int(round(u)) % 4 == 2 and abs(v / hw) <= 0.35:
                g[y][x] = 'R'


def add_guard_ornaments(g, half=5.0):
    """Bordure travaillee, sans crochets : clous graves en alternance le long
    de la garde, et pointes serties d'eclats d'emeraude."""
    r = max(int(half) - 1, 1)
    for k in range(-r, r + 1, 2):
        ch = 'c' if k % 4 == 0 else 'a'
        sd.put(g, sd.GUARD_X + k, sd.GUARD_Y + k, ch)
    tip = int(round(half))
    for sign in (1, -1):
        sd.put(g, sd.GUARD_X + sign * tip, sd.GUARD_Y + sign * tip, 'g')


def add_handle_rings(g, positions=(2, 4)):
    """Anneaux d'or ouvrages sur le manche (viroles)."""
    for s in positions:
        for v in (-1, 0, 1):
            ch = 'c' if v == -1 else ('b' if v == 0 else 'a')
            sd.put(g, sd.GUARD_X - s + v, sd.GUARD_Y + s + v, ch)


def build_l4():
    """L4 - Souveraine : geometrie V1 (lame fine, garde droite) + toutes les
    animations de l'Apotheose. Garde ouvragee et manche a viroles d'or."""
    g = sd.blank()
    sd.draw_blade(g, base_hw=2.3, seed=7)                 # la lame V1, inchangee
    add_runes(g, base_hw=2.3)
    sd.draw_guard(g, half=5.5, thickness=1.5, hooks=False)
    add_guard_ornaments(g, half=5.5)
    sd.draw_handle(g, half=1.35, wrapped=False)
    add_handle_rings(g)
    sd.draw_pommel(g, radius=2.4, gem=True)
    sd.draw_center_gem(g, big=True)
    sd.add_outline(g)
    pal = sd.palette_of(sd.GOLD, sd.HANDLE_DARK, sd.GEM_BRIGHT)
    return g, pal, dict(falling=falling_spawns(base_hw=2.3))


def build_l5():
    """L5 - Lame Longue : proportions vanilla. Hilt compact (garde reduite,
    manche court, pommeau resserre) et lame etiree jusqu'au coin du canvas,
    pour que l'arme se lise comme une epee longue et non comme une dague."""
    sd.set_layout(**sd.LAYOUT_LONG)
    g = sd.blank()
    sd.draw_blade(g, base_hw=2.4, seed=7)
    add_runes(g, base_hw=2.4)
    sd.draw_guard(g, half=3.9, thickness=1.4, hooks=False)
    add_guard_ornaments(g, half=3.9)
    sd.draw_handle(g, half=1.3, wrapped=False)
    add_handle_rings(g, positions=(2, 3))
    sd.draw_pommel(g, radius=1.9, gem=True)
    sd.draw_center_gem(g, big=False)
    sd.add_outline(g)
    pal = sd.palette_of(sd.GOLD, sd.HANDLE_DARK, sd.GEM_BRIGHT)
    return g, pal, dict(falling=falling_spawns(base_hw=2.4))


def build_l6():
    """L6 - Fleau d'Emeraude : lame longue ET menacante. Palette assombrie
    pour le contraste, tranche dentelee pres de la garde, hilt compact mais
    plus present que sur L5."""
    sd.set_layout(**sd.LAYOUT_LONG)
    g = sd.blank()
    sd.draw_blade(g, base_hw=2.5, seed=7)
    add_runes(g, base_hw=2.5)
    # Pas d'ornements pixel-par-pixel ici : sur un hilt compact ils se lisent
    # comme du bruit, pas comme de l'orfevrerie. Un seul anneau suffit.
    sd.draw_guard(g, half=4.6, thickness=1.5, hooks=False)
    sd.draw_handle(g, half=1.35, wrapped=False)
    add_handle_rings(g, positions=(2,))
    sd.draw_pommel(g, radius=2.1, gem=True)
    sd.draw_center_gem(g, big=False)
    sd.add_outline(g)
    pal = sd.palette_of(DARK_EMERALD, sd.GOLD, sd.HANDLE_DARK, sd.GEM_BRIGHT)
    return g, pal, dict(falling=falling_spawns(base_hw=2.5))


def build_l7():
    """L7 - Prise Franche : lame longue de L5, mais hilt allonge et pommeau
    pousse dans le coin bas-gauche pour qu'il DEPASSE du poing au lieu d'y
    etre avale. Garde amincie pour ne pas former un bloc plein a l'endroit
    exact ou la main serre."""
    sd.set_layout(**sd.LAYOUT_HELD)
    g = sd.blank()
    sd.draw_blade(g, base_hw=2.4, seed=7)
    add_runes(g, base_hw=2.4)
    sd.draw_guard(g, half=3.4, thickness=1.2, hooks=False)
    sd.draw_handle(g, half=1.25, wrapped=False)
    add_handle_rings(g, positions=(3,))
    sd.draw_pommel(g, radius=2.2, gem=True)
    sd.draw_center_gem(g, big=False)
    sd.add_outline(g)
    pal = sd.palette_of(sd.GOLD, sd.HANDLE_DARK, sd.GEM_BRIGHT)
    return g, pal, dict(falling=falling_spawns(base_hw=2.4))


# ------------------------------------------------- fusion Master x Obscure

def glow_hw(u, length, base):
    """Profil anti-bouteille : corps stable sur la premiere moitie, puis
    EFFILAGE LONG et continu jusqu'a un vrai point (0.35, un seul pixel).
    L'ancien profil gardait la lame large jusqu'a 60% puis l'etranglait :
    corps + goulot + bouchon clair = silhouette de bouteille."""
    r = u / length
    if r < 0.50:
        return base
    return base - (base - 0.35) * (r - 0.50) / 0.50


def draw_blade_glow(g, base_hw=2.5):
    """Lame inversee, facon 'arme habitee' : corps quasi noir, et ce sont les
    TRANCHANTS et la GOUTTIERE qui brillent (reference : l'epee sombre du
    joueur). Le contour lumineux dessine la forme -> plus de rectangle.

    La pointe garde son ame sombre entre les deux tranchants jusqu'aux
    tout derniers pixels : les aretes lumineuses CONVERGENT vers le point
    au lieu de se fondre en un capuchon blanc."""
    for y in range(H):
        for x in range(W):
            u, v = sd.to_uv(x, y, sd.BLADE_X0, sd.BLADE_Y0)
            if u < -0.5 or u > sd.BLADE_LEN:
                continue
            hw = glow_hw(max(u, 0.0), sd.BLADE_LEN, base_hw)
            if abs(v) > hw:
                continue
            if hw <= 0.8:
                # l'extreme pointe garde la meme grammaire que le reste :
                # aretes en '6', interieur nuance -- pas de bloc uniforme
                if abs(v) > hw - 0.55:
                    ch = '6'
                else:
                    ch = '4' if int(round(u)) % 2 == 0 else '5'
            elif abs(v) > hw - 0.55:
                # tranchants : lumiere continue, ponctuee d'eclats
                ch = '6' if int(round(u)) % 3 == 1 else '5'
            elif hw <= 1.6:
                ch = '2'                          # ame sombre du bout de lame
            elif abs(v) <= 0.3:
                # gouttiere lumineuse ('F' pulse), runes encore plus vives
                if int(round(u)) % 4 == 2 and 1.5 <= u <= sd.BLADE_LEN - 3.0:
                    ch = 'R'
                else:
                    ch = 'F'
                # corps sombre : la matiere noire entre les lumieres
            elif abs(v) <= 0.9:
                ch = '2'
            else:
                ch = '1'
            sd.put(g, x, y, ch)


def draw_guard_master(g, span=5.5, chars=('a', 'b', 'c'), flames=False):
    """Garde ailee facon Master Sword : deux ailes LARGES et pleines qui
    balaient vers la lame (rien a voir avec les crochets fins d'1 px qui
    faisaient fourchette), autour d'un bloc central qui porte la gemme.
    flames=True ajoute une flamme ambre au bout de chaque aile."""
    dark, mid, light = chars
    for y in range(H):
        for x in range(W):
            u, v = sd.to_uv(x, y, sd.GUARD_X, sd.GUARD_Y)
            av = abs(v)
            if av > span:
                continue
            if av <= 1.8:
                # bloc central (ecrin de la gemme)
                if -1.6 <= u <= 1.6:
                    ch = light if u > 0.7 else (dark if u < -0.7 else mid)
                    sd.put(g, x, y, ch)
                continue
            # aile : plus on s'eloigne du centre, plus elle monte vers la lame
            # (montee douce : 0.55 -- au-dela l'aile s'etire en 'botte')
            rise = (av - 1.8) * 0.55
            lo, hi = -1.3 + rise * 0.3, 0.7 + rise
            if lo <= u <= hi:
                if av > span - 0.6:
                    ch = light                    # pointe d'aile polie
                elif u > hi - 0.8:
                    ch = light                    # face cote lame, eclairee
                elif u < lo + 0.8:
                    ch = dark
                else:
                    ch = mid
                sd.put(g, x, y, ch)
    if flames:
        for sign in (1, -1):
            vv = sign * span
            uu = 0.7 + (span - 1.8) * 0.55
            x0 = int(round(sd.GUARD_X + uu + vv))
            y0 = int(round(sd.GUARD_Y + vv - uu))
            # petite langue de feu : coeur clair, pourtour ambre
            sd.put(g, x0, y0, 'W')
            sd.put(g, x0 + 1, y0 - 1, 'W')
            sd.put(g, x0 + 1, y0, 'w')
            sd.put(g, x0 + 2, y0 - 2, 'w')


def build_z1():
    """Z1 - Lame de l'Elue : structure Master Sword (ailes d'or larges,
    gemme en ecrin, pommeau serti) + lame noire aux tranchants d'emeraude."""
    sd.set_layout(**sd.LAYOUT_HELD)
    g = sd.blank()
    draw_blade_glow(g, base_hw=2.5)
    draw_guard_master(g, span=5.5, chars=('a', 'b', 'c'), flames=False)
    sd.draw_handle(g, half=1.3, wrapped=True)
    sd.draw_pommel(g, radius=2.2, gem=True)
    sd.draw_center_gem(g, big=True)
    sd.add_outline(g)
    pal = sd.palette_of(DARK_EMERALD, sd.GOLD, sd.HANDLE_DARK, sd.GEM_BRIGHT)
    return g, pal, dict(falling=falling_spawns(base_hw=2.5))


def build_z2():
    """Z2 - Lame des Tenebres : meme fusion mais hilt obsidienne et flammes
    ambre au bout des ailes (reference : l'epee sombre). Zero or, tout en
    contraste vert/noir/ambre."""
    sd.set_layout(**sd.LAYOUT_HELD)
    g = sd.blank()
    draw_blade_glow(g, base_hw=2.5)
    draw_guard_master(g, span=5.5, chars=('a', 'b', 'c'), flames=True)
    sd.draw_handle(g, half=1.3, wrapped=True)
    sd.draw_pommel(g, radius=2.2, gem=True)
    sd.draw_center_gem(g, big=True)
    sd.add_outline(g)
    pal = sd.palette_of(DARK_EMERALD, sd.OBSIDIAN, sd.HANDLE_DARK,
                        sd.GEM_BRIGHT)
    return g, pal, dict(falling=falling_spawns(base_hw=2.5))


# Les 5 cristaux de la Fureur Cristalline, sertis dans le hilt. Chaque gemme
# a son caractere propre -> chacune pulse avec sa propre phase (arc-en-ciel
# qui court de gemme en gemme, signature du mod).
ACCENT_GEMS = [
    # char, couleur eteinte,   couleur vive,     phase (frames)
    ('r', (150, 34, 44),  (255, 96, 106), 0),    # rouge   - feu
    ('n', (160, 76, 12),  (255, 156, 48), 2),    # orange  - knockback
    ('y', (160, 138, 24), (255, 232, 88), 5),    # jaune   - cecite (epee)
    ('k', (158, 52, 116), (255, 124, 214), 7),   # rose    - poison
    ('u', (28, 84, 160),  (96, 196, 255), 9),    # bleu    - gel (arc)
]


def set_gem(g, x, y, ch):
    """Sertit une gemme : le pixel de couleur, entoure d'un ECRIN sombre
    partout ou il y a de la matiere. Sans sertissage, un pixel de couleur
    isole sur l'or lit comme du bruit ; l'ecrin en fait un bijou."""
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if 0 <= x + dx < W and 0 <= y + dy < H and g[y + dy][x + dx] != '.':
            g[y + dy][x + dx] = 'o'
    sd.put(g, x, y, ch)


def add_fury_gems(g):
    """Sertit les cristaux de la Fureur : un a la pointe de chaque aile,
    deux en clous sur le manche. Le vert reste porte par les gemmes 'G'."""
    for sign, ch in ((-1, 'r'), (1, 'k')):
        vv, uu = sign * 4.6, 1.3
        set_gem(g, int(round(sd.GUARD_X + uu + vv)),
                int(round(sd.GUARD_Y + vv - uu)), ch)
    set_gem(g, sd.GUARD_X - 2, sd.GUARD_Y + 2, 'n')    # clou du manche, haut
    set_gem(g, sd.GUARD_X - 4, sd.GUARD_Y + 4, 'y')    # clou du manche, bas


def build_z3():
    """Z3 - Fureur Incarnee : la fusion identitaire. Lame noire aux
    tranchants d'emeraude (ref. epee sombre), ailes d'or Master Sword,
    et les 5 cristaux de la Fureur Cristalline sertis dans le hilt,
    pulsant en sequence. L'epee porte sa propre mecanique."""
    sd.set_layout(**sd.LAYOUT_HELD)
    g = sd.blank()
    draw_blade_glow(g, base_hw=2.5)
    draw_guard_master(g, span=5.0, chars=('a', 'b', 'c'), flames=False)
    sd.draw_handle(g, half=1.3, wrapped=True)
    add_handle_rings(g, positions=(1, 3))
    sd.draw_pommel(g, radius=2.2, gem=True)
    sd.draw_center_gem(g, big=True)
    add_fury_gems(g)
    sd.add_outline(g)
    pal = sd.palette_of(DARK_EMERALD, sd.GOLD, sd.HANDLE_DARK, sd.GEM_BRIGHT)
    return g, pal, dict(falling=falling_spawns(base_hw=2.5))


def _forged_hilt(g):
    """Hilt commun aux variantes forgees : celui de L7, valide en jeu
    (depasse du poing, pommeau dans le coin bas)."""
    sd.draw_guard(g, half=3.4, thickness=1.2, hooks=False)
    sd.draw_handle(g, half=1.25, wrapped=False)
    add_handle_rings(g, positions=(3,))
    sd.draw_pommel(g, radius=2.2, gem=True)
    sd.draw_center_gem(g, big=False)


def build_f1():
    """F1 - Lame Forgee : gouttiere + fil asymetrique + runes dans la
    gouttiere, palette emeraude standard. Layout HELD (prise validee)."""
    sd.set_layout(**sd.LAYOUT_HELD)
    g = sd.blank()
    draw_blade_forged(g, base_hw=2.4, runes=True, seed=7)
    _forged_hilt(g)
    sd.add_outline(g)
    pal = sd.palette_of(sd.GOLD, sd.HANDLE_DARK, sd.GEM_BRIGHT)
    return g, pal, dict(falling=falling_spawns(base_hw=2.4))


def build_f2():
    """F2 - Lame Forgee Sombre : meme anatomie, palette DARK_EMERALD.
    Les sombres descendent plus bas, le fil contraste d'autant plus."""
    sd.set_layout(**sd.LAYOUT_HELD)
    g = sd.blank()
    draw_blade_forged(g, base_hw=2.4, runes=True, seed=7)
    _forged_hilt(g)
    sd.add_outline(g)
    pal = sd.palette_of(DARK_EMERALD, sd.GOLD, sd.HANDLE_DARK, sd.GEM_BRIGHT)
    return g, pal, dict(falling=falling_spawns(base_hw=2.4))


VARIANTS = {
    "l1_couronnee": (build_l1, "Lame Couronnee - coeur lumineux, garde ailee"),
    "l2_astrale":   (build_l2, "Lame Astrale - eclats de cristal en orbite"),
    "l3_apotheose": (build_l3, "Apotheose - runes pulsantes + ailerons + eclats"),
    "l4_souveraine": (build_l4, "Souveraine - lame V1 + animations Apotheose, "
                                "garde ouvragee sans crochets"),
    "l5_lame_longue": (build_l5, "Lame Longue - proportions vanilla, "
                                 "hilt compact et lame etiree"),
    "l6_fleau": (build_l6, "Fleau d'Emeraude - lame longue, dentelee, "
                           "palette assombrie"),
    "l7_prise": (build_l7, "Prise Franche - lame longue + hilt qui depasse "
                           "vraiment du poing"),
    "f1_forgee": (build_f1, "Lame Forgee - gouttiere, fil asymetrique, "
                            "runes gravees"),
    "f2_forgee_sombre": (build_f2, "Lame Forgee Sombre - meme anatomie, "
                                   "palette approfondie"),
    "z1_elue": (build_z1, "Lame de l'Elue - fusion Master Sword x lame "
                          "noire aux tranchants d'emeraude"),
    "z2_tenebres": (build_z2, "Lame des Tenebres - hilt obsidienne, "
                              "flammes ambre"),
    "z3_fureur": (build_z3, "Fureur Incarnee - ailes d'or + les 5 cristaux "
                            "de la Fureur sertis et pulsants"),
}


# -------------------------------------------------------------- animation

def lerp(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(3)) + (255,)


def frame_palette(base_pal, frame_idx):
    """Palette de la frame : gemmes, runes et eclats pulsent en sinus lent."""
    t = 0.5 + 0.5 * math.sin(2 * math.pi * frame_idx / NFRAMES)
    pal = dict(base_pal)
    pal['G'] = lerp((142, 243, 190), (235, 255, 246), t)
    pal['g'] = lerp((25, 158, 94), (80, 220, 150), t)
    pal['R'] = lerp((25, 158, 94), (231, 255, 244), t)
    pal['x'] = lerp((66, 209, 138), (231, 255, 244), t)
    pal['*'] = (240, 255, 248, 255)
    pal['p'] = (142, 243, 190, 255)      # particule en chute, encore vive
    pal['q'] = (48, 165, 108, 255)       # particule en fin de vie, eteinte
    pal['F'] = lerp((24, 140, 88), (96, 240, 170), t)     # gouttiere luisante
    pal['w'] = lerp((190, 92, 18), (224, 120, 32), t)     # flamme, base
    pal['W'] = lerp((252, 168, 44), (255, 222, 120), t)   # flamme, coeur
    # cristaux de la Fureur : chacun pulse avec sa propre phase, l'eclat
    # court de gemme en gemme comme la mecanique en jeu
    for ch, dim, vivid, phase in ACCENT_GEMS:
        tt = 0.5 + 0.5 * math.sin(2 * math.pi * (frame_idx + phase) / NFRAMES)
        pal[ch] = lerp(dim, vivid, tt)
    return pal


def apply_wave(grid, frame_idx):
    """Vague de lumiere : une bande brillante remonte la lame en boucle.

    Pour chaque pixel de lame, la distance (le long de la lame) a la crete
    de la vague determine un bonus de luminosite : +2 crans sur la crete,
    +1 juste derriere, 0 ailleurs.
    """
    phase = frame_idx / NFRAMES
    out = [row[:] for row in grid]
    for y in range(H):
        for x in range(W):
            ch = grid[y][x]
            if ch not in RAMP:
                continue
            u, _ = sd.to_uv(x, y, sd.BLADE_X0, sd.BLADE_Y0)
            if u < -0.5 or u > sd.BLADE_LEN:
                continue                      # gris du pommeau etc. : intouche
            d = ((u / sd.BLADE_LEN) - phase) % 1.0
            if d < 0.10:
                boost = 2
            elif d < 0.22:
                boost = 1
            else:
                boost = 0
            if boost:
                idx = RAMP.index(ch)
                out[y][x] = RAMP[min(idx + boost, len(RAMP) - 1)]
    return out


def render_frames(grid, base_pal, opts):
    frames = []
    for f in range(NFRAMES):
        fg = apply_wave(grid, f)
        if opts.get("sparkles"):
            place_sparkles(fg, f, grid)
        if opts.get("falling"):
            place_falling(fg, f, grid, opts["falling"])
        pal = frame_palette(base_pal, f)
        img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        px = img.load()
        for y in range(H):
            for x in range(W):
                px[x, y] = pal.get(fg[y][x], (255, 0, 255, 255))
        frames.append(img)
    return frames


def export(name, frames, scale=14, gif_scale=10):
    os.makedirs(PV_DIR, exist_ok=True)
    # apercu fixe (frame 0)
    still = os.path.join(PV_DIR, name + "_x%d.png" % scale)
    frames[0].resize((W * scale, H * scale), Image.NEAREST).save(still)
    # spritesheet verticale pour Minecraft
    sheet = Image.new("RGBA", (W, H * NFRAMES), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * H))
    sheet_path = os.path.join(PV_DIR, name + "_sheet.png")
    sheet.save(sheet_path)
    # GIF anime sur fond sombre (la transparence GIF rend mal)
    gif_frames = []
    for fr in frames:
        bg = Image.new("RGBA", (W, H), (43, 43, 43, 255))
        bg.alpha_composite(fr)
        gif_frames.append(bg.convert("P", palette=Image.ADAPTIVE)
                            .resize((W * gif_scale, H * gif_scale), Image.NEAREST))
    gif_path = os.path.join(PV_DIR, name + "_anim.gif")
    gif_frames[0].save(gif_path, save_all=True, append_images=gif_frames[1:],
                       duration=110, loop=0)
    return still, sheet_path, gif_path


def install(name, target="emerald_sword"):
    sheet_path = os.path.join(PV_DIR, name + "_sheet.png")
    if not os.path.exists(sheet_path):
        print("Spritesheet absente, generation...")
        generate_all()
    dest = os.path.join(ITEM_DIR, target + ".png")
    Image.open(sheet_path).save(dest)
    with open(dest + ".mcmeta", "w", encoding="utf-8") as f:
        f.write('{\n  "animation": {\n    "frametime": 2,\n'
                '    "interpolate": true\n  }\n}\n')
    print("Installe :", os.path.relpath(dest, ROOT))
    print("mcmeta   :", os.path.relpath(dest + ".mcmeta", ROOT))


def generate_all():
    for name, (fn, desc) in VARIANTS.items():
        grid, pal, opts = fn()
        frames = render_frames(grid, pal, opts)
        still, sheet, gif = export(name, frames)
        print("%-14s %s" % (name, desc))
        print("   apercu : %s" % os.path.relpath(still, ROOT))
        print("   anime  : %s" % os.path.relpath(gif, ROOT))
        print("   sheet  : %s" % os.path.relpath(sheet, ROOT))


if __name__ == "__main__":
    args = sys.argv[1:]
    if "--install" in args:
        name = args[args.index("--install") + 1]
        target = "emerald_sword"
        if "--as" in args:
            target = args[args.index("--as") + 1]
        if name not in VARIANTS:
            print("Variante inconnue :", name)
            print("Choix :", ", ".join(VARIANTS))
            sys.exit(1)
        grid, pal, opts = VARIANTS[name][0]()
        export(name, render_frames(grid, pal, opts))
        install(name, target)
    else:
        generate_all()
