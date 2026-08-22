#!/usr/bin/env python3
"""
Generateur de blueprints d'epees pour EmeraldWeapons.

Principe : la geometrie est definie en "espace lame" (u = le long de la lame,
v = perpendiculaire), puis rasterisee pixel par pixel sur la grille 32x32.
Cela garantit une lame pleine, sans trous (contrairement a un trace diagonal naif).

Sorties :
  1. Blueprint ASCII -> tools/blueprints/<nom>.txt   (editable a la main)
  2. PNG 32x32       -> tools/preview/<nom>.png
  3. Apercu x14      -> tools/preview/<nom>_x14.png

Usage :
    python tools/sword_designer.py
"""

import os
import math
import random

W = H = 32
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BP_DIR = os.path.join(ROOT, "tools", "blueprints")
PV_DIR = os.path.join(ROOT, "tools", "preview")

# --------------------------------------------------------------- reperes

# Lame : direction (+1, -1). Perpendiculaire : (+1, +1).
BLADE_X0, BLADE_Y0 = 11, 20     # base de la lame
BLADE_LEN = 18.0                # -> pointe vers (29, 2)
GUARD_X,  GUARD_Y = 10, 21      # centre de la garde
HANDLE_LEN = 6.0                # longueur du manche
POMMEL_X, POMMEL_Y = 4, 27


# Proportions de reference. CLASSIC = les blueprints v1/v2/v3 d'origine.
# LONG = proportions calquees sur l'epee vanilla : hilt compact, lame etiree
# jusqu'au coin du canvas (le bloc garde+manche ne doit pas manger la moitie
# du sprite, sinon l'arme se lit comme une dague).
LAYOUT_CLASSIC = dict(blade_x0=11, blade_y0=20, blade_len=18.0,
                      guard_x=10, guard_y=21, handle_len=6.0,
                      pommel_x=4, pommel_y=27)

LAYOUT_LONG = dict(blade_x0=9, blade_y0=22, blade_len=21.0,
                   guard_x=8, guard_y=23, handle_len=4.5,
                   pommel_x=3, pommel_y=28)

# HELD = lame longue de LONG, mais le hilt reprend sa longueur et le pommeau
# est pousse dans le coin bas-gauche. C'est ce qui compte pour la tenue en
# main : le modele "handheld" place le poing a ~25% du bas du sprite, donc
# tout ce qui doit depasser de la main vit sous cette ligne. Un hilt trop
# court ou qui n'atteint pas le bord bas se retrouve avale par le poing.
# La garde est REMONTEE (21 et non 23) : avec une garde ailee, l'aile basse
# descend de ~5 px sous le centre -- centree trop bas, elle envahit le poing.
LAYOUT_HELD = dict(blade_x0=11, blade_y0=20, blade_len=19.5,
                   guard_x=10, guard_y=21, handle_len=7.0,
                   pommel_x=3, pommel_y=29)


def set_layout(blade_x0, blade_y0, blade_len, guard_x, guard_y,
               handle_len, pommel_x, pommel_y):
    """Redefinit les reperes utilises par toutes les fonctions de trace.

    A appeler au debut de chaque variante : les fonctions lisent ces globales
    au moment de l'appel, donc une variante qui ne fixe pas son layout
    heriterait de celui de la precedente.
    """
    global BLADE_X0, BLADE_Y0, BLADE_LEN, GUARD_X, GUARD_Y
    global HANDLE_LEN, POMMEL_X, POMMEL_Y
    BLADE_X0, BLADE_Y0, BLADE_LEN = blade_x0, blade_y0, blade_len
    GUARD_X, GUARD_Y = guard_x, guard_y
    HANDLE_LEN = handle_len
    POMMEL_X, POMMEL_Y = pommel_x, pommel_y


def to_uv(x, y, ox, oy):
    """Repere local : u = avance le long de la lame, v = ecart perpendiculaire."""
    dx, dy = x - ox, y - oy
    return (dx - dy) / 2.0, (dx + dy) / 2.0


def blank():
    return [['.' for _ in range(W)] for _ in range(H)]


def put(g, x, y, ch):
    if 0 <= x < W and 0 <= y < H:
        g[y][x] = ch


def get(g, x, y):
    if 0 <= x < W and 0 <= y < H:
        return g[y][x]
    return '.'


# --------------------------------------------------------------- formes

def blade_half_width(u, length, base):
    """Profil de la lame : pleine sur les 2/3, puis effilee lineairement.

    Le minimum de 0.5 est essentiel : v ne prend que des valeurs multiples de 0.5,
    donc une largeur inferieure ne capturerait aucun pixel et trouerait la pointe.
    """
    r = u / length
    if r <= 0.66:
        return base
    return base - (base - 0.5) * (r - 0.66) / 0.34


def shade_char(s):
    """s dans [-1, 1] : -1 = arete eclairee (haut-gauche), +1 = arete a l'ombre."""
    if s <= -0.62:
        return '5'
    if s <= -0.20:
        return '4'
    if s <= 0.22:
        return '3'
    if s <= 0.64:
        return '2'
    return '1'


def draw_blade(g, base_hw=2.3, jagged=False, runes=False, seed=7):
    rng = random.Random(seed)
    # Bruit sur le bord, fige par pas de lame, pour un cristal brut
    notch = {}
    if jagged:
        for i in range(int(BLADE_LEN) + 2):
            notch[i] = rng.choice([0.0, 0.0, -0.75, 0.5, -0.4])

    for y in range(H):
        for x in range(W):
            u, v = to_uv(x, y, BLADE_X0, BLADE_Y0)
            if u < -0.5 or u > BLADE_LEN:
                continue
            hw = blade_half_width(max(u, 0.0), BLADE_LEN, base_hw)
            if jagged:
                hw += notch.get(int(round(u)), 0.0)
            if hw <= 0 or abs(v) > hw:
                continue

            s = v / hw
            ch = shade_char(s)

            # Facettes de cristal : eclats ponctuels, uniquement sur la face eclairee
            if not runes and s < -0.45 and rng.random() < 0.14:
                ch = '6'

            # Runes : glyphe lumineux sur l'axe, cerne sombre en dessous pour le detacher
            if runes and int(round(u)) % 4 == 2:
                if abs(s) < 0.30:
                    ch = '6'
                elif 0.30 <= s < 0.72:
                    ch = '1'

            put(g, x, y, ch)


def draw_guard(g, half=5.5, thickness=1.4, hooks=True, chars=('a', 'b', 'c'),
               hook_len=2.2):
    dark, mid, light = chars
    if not hooks:
        hook_len = 0.0
    for y in range(H):
        for x in range(W):
            u, v = to_uv(x, y, GUARD_X, GUARD_Y)
            inside = abs(v) <= half and -thickness <= u <= thickness
            # Crochets : les pointes de la garde se recourbent vers la lame
            if not inside and hooks and half - 1.2 <= abs(v) <= half:
                inside = 0 <= u <= hook_len
            if not inside:
                continue
            if abs(v) >= half - 0.55:
                ch = dark                       # extremites
            elif u >= thickness - 0.8:
                ch = light                      # face cote lame, eclairee
            elif u <= -thickness + 0.8:
                ch = dark                       # face cote manche, a l'ombre
            else:
                ch = mid
            put(g, x, y, ch)


def draw_handle(g, half=1.35, wrapped=False, chars=('h', 'i', 'j')):
    dark, mid, light = chars
    for y in range(H):
        for x in range(W):
            u, v = to_uv(x, y, GUARD_X, GUARD_Y)
            if not (-HANDLE_LEN <= u <= 0) or abs(v) > half:
                continue
            s = v / half
            if s <= -0.4:
                ch = light
            elif s <= 0.4:
                ch = mid
            else:
                ch = dark
            # Lanieres de cuir : anneaux clairs reguliers
            if wrapped and int(round(-u)) % 2 == 1:
                ch = light if s <= 0.35 else mid
            put(g, x, y, ch)


def draw_pommel(g, radius=2.4, gem=True, chars=('a', 'b', 'c')):
    dark, mid, light = chars
    for y in range(H):
        for x in range(W):
            d = math.hypot(x - POMMEL_X, y - POMMEL_Y)
            if d > radius:
                continue
            if d > radius - 0.9:
                ch = dark
            elif (x - POMMEL_X) + (y - POMMEL_Y) <= -1:
                ch = light                      # quart haut-gauche eclaire
            else:
                ch = mid
            put(g, x, y, ch)
    if gem:
        put(g, POMMEL_X, POMMEL_Y, 'G')
        put(g, POMMEL_X - 1, POMMEL_Y, 'g')
        put(g, POMMEL_X, POMMEL_Y - 1, 'g')


def draw_center_gem(g, big=False):
    """Gemme sertie a la jonction garde / lame."""
    cx, cy = GUARD_X, GUARD_Y
    put(g, cx, cy, 'G')
    put(g, cx - 1, cy, 'g')
    put(g, cx, cy - 1, 'g')
    put(g, cx + 1, cy, 'g')
    put(g, cx, cy + 1, 'g')
    if big:
        put(g, cx - 1, cy - 1, 'G')
        put(g, cx + 1, cy + 1, 'g')
        put(g, cx + 1, cy - 1, 'g')
        put(g, cx - 1, cy + 1, 'g')


def add_outline(g, ch='o'):
    """Contour sombre teinte (jamais noir pur) autour de la silhouette."""
    src = [row[:] for row in g]
    for y in range(H):
        for x in range(W):
            if src[y][x] != '.':
                continue
            if any(get(src, x + dx, y + dy) != '.'
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                g[y][x] = ch
    return g


# --------------------------------------------------------------- palettes

EMERALD = {
    'o': (6, 34, 22, 255),        # contour vert tres sombre (pas noir)
    '1': (9, 62, 39, 255),
    '2': (15, 105, 62, 255),
    '3': (25, 158, 94, 255),
    '4': (66, 209, 138, 255),
    '5': (142, 243, 190, 255),
    '6': (231, 255, 244, 255),
}

GOLD = {'a': (112, 76, 16, 255), 'b': (201, 150, 38, 255), 'c': (248, 216, 112, 255)}
SILVER = {'a': (54, 62, 68, 255), 'b': (116, 130, 140, 255), 'c': (198, 210, 218, 255)}
OBSIDIAN = {'a': (22, 17, 38, 255), 'b': (58, 48, 88, 255), 'c': (126, 108, 168, 255)}

HANDLE_DARK = {'h': (24, 19, 15, 255), 'i': (45, 35, 27, 255), 'j': (76, 58, 42, 255)}
HANDLE_LEATHER = {'h': (46, 29, 17, 255), 'i': (88, 57, 33, 255), 'j': (134, 94, 56, 255)}

GEM_BRIGHT = {'g': (25, 158, 94, 255), 'G': (142, 243, 190, 255)}
GEM_DEEP = {'g': (11, 92, 56, 255), 'G': (66, 209, 138, 255)}

TRANSPARENT = {'.': (0, 0, 0, 0)}


def palette_of(*parts):
    p = dict(TRANSPARENT)
    p.update(EMERALD)
    for part in parts:
        p.update(part)
    return p


# -------------------------------------------------------------- variantes

def variant_taillee():
    """V1 - Emeraude Taillee : lame facettee, garde doree a crochets, gemme."""
    g = blank()
    draw_blade(g, base_hw=2.3, seed=7)
    draw_guard(g, half=5.5, thickness=1.4, hooks=True)
    draw_handle(g, half=1.35, wrapped=False)
    draw_pommel(g, radius=2.4, gem=True)
    draw_center_gem(g, big=False)
    add_outline(g)
    return g, palette_of(GOLD, HANDLE_DARK, GEM_BRIGHT)


def variant_runique():
    """V2 - Emeraude Runique : runes sur la lame, garde obsidienne, grosse gemme."""
    g = blank()
    draw_blade(g, base_hw=2.5, runes=True, seed=11)
    draw_guard(g, half=6.2, thickness=1.5, hooks=True)
    draw_handle(g, half=1.45, wrapped=False)
    draw_pommel(g, radius=2.6, gem=True)
    draw_center_gem(g, big=True)
    add_outline(g)
    return g, palette_of(OBSIDIAN, HANDLE_DARK, GEM_BRIGHT)


def variant_eclat():
    """V3 - Eclat d'Emeraude : cristal brut aux bords irreguliers, manche en cuir."""
    g = blank()
    draw_blade(g, base_hw=2.6, jagged=True, seed=23)
    draw_guard(g, half=4.4, thickness=1.2, hooks=False)
    draw_handle(g, half=1.35, wrapped=True)
    draw_pommel(g, radius=2.2, gem=False)
    draw_center_gem(g, big=False)
    add_outline(g)
    return g, palette_of(SILVER, HANDLE_LEATHER, GEM_DEEP)


VARIANTS = {
    "v1_taillee": (variant_taillee, "Emeraude Taillee - lame facettee, garde doree a crochets"),
    "v2_runique": (variant_runique, "Emeraude Runique - runes gravees, garde obsidienne"),
    "v3_eclat":   (variant_eclat,   "Eclat d'Emeraude - cristal brut, manche en cuir"),
}


# ---------------------------------------------------------------- export

def write_blueprint(name, grid, palette, desc):
    path = os.path.join(BP_DIR, name + ".txt")
    lines = [
        "# " + desc,
        "# Blueprint EmeraldWeapons - %dx%d" % (W, H),
        "# Edite la grille librement puis relance :",
        "#     python tools/render_blueprint.py %s" % name,
        "#",
        "# PALETTE",
    ]
    for ch, rgba in sorted(palette.items()):
        if ch == '.':
            lines.append("#   '.' = transparent")
        else:
            lines.append("#   '%s' = #%02X%02X%02X" % (ch, rgba[0], rgba[1], rgba[2]))
    lines.append("#")
    lines.append("# GRILLE")
    lines.extend("".join(row) for row in grid)
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    return path


def render_png(name, grid, palette, scale=14):
    from PIL import Image
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        for x in range(W):
            px[x, y] = palette.get(grid[y][x], (255, 0, 255, 255))
    out = os.path.join(PV_DIR, name + ".png")
    img.save(out)
    big_out = os.path.join(PV_DIR, name + "_x%d.png" % scale)
    img.resize((W * scale, H * scale), Image.NEAREST).save(big_out)
    return out, big_out


if __name__ == "__main__":
    os.makedirs(BP_DIR, exist_ok=True)
    os.makedirs(PV_DIR, exist_ok=True)
    for name, (fn, desc) in VARIANTS.items():
        grid, palette = fn()
        bp = write_blueprint(name, grid, palette, desc)
        png, big = render_png(name, grid, palette)
        print("=" * 46)
        print(name, "-", desc)
        print("=" * 46)
        for row in grid:
            print("".join(row))
        print("blueprint :", os.path.relpath(bp, ROOT))
        print("apercu    :", os.path.relpath(big, ROOT))
        print()
