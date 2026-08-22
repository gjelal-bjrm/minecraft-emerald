#!/usr/bin/env python3
"""
Rendu isometrique d'une scene de blocs a partir des textures du mod.

Sert a juger un batiment (proportions, dosage des couleurs, lisibilite)
AVANT de l'integrer en NBT/jigsaw. Projection dimetrique classique 2:1 :
chaque bloc est un sprite (dessus + face +z + face +x, ombrees) pose en
ordre peintre. Les dalles sont des demi-cubes, les plantes des
silhouettes en croix, les vitres des cubes translucides.

Usage :
    python tools/iso_render.py            # maison test -> tools/preview/iso/
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                       "emeraldweapons", "textures", "block")
OUT_DIR = os.path.join(ROOT, "tools", "preview", "iso")
T = 32                       # taille ecran d'une arete (texture 16 -> x2)

# bloc -> (texture dessus, texture cote). None = meme texture.
BLOCKS = {
    "gangue_stone": ("gangue_stone", None),
    "gangue_bricks": ("gangue_bricks", None),
    "polished_gangue": ("polished_gangue", None),
    "veined_stone": ("veined_stone", None),
    "arcencium_bricks": ("arcencium_bricks", None),
    "chiseled_arcencium": ("chiseled_arcencium", None),
    "corrupted_bricks": ("corrupted_bricks", None),
    "prismatic_glass": ("prismatic_glass", None),
    "crystal_planks": ("crystal_planks", None),
    "prism_log": ("prism_log_top", "prism_log"),
    "prism_leaves": ("prism_leaves", None),
    "prismatic_grass_block": ("prismatic_grass_block_top", "prismatic_grass_block_side"),
    "verdigris_wool": ("verdigris_wool", None),
    "ochre_wool": ("ochre_wool", None),
    "old_rose_wool": ("old_rose_wool", None),
    "slate_blue_wool": ("slate_blue_wool", None),
    "ecru_wool": ("ecru_wool", None),
}
CROSS = {"prism_bloom", "prism_tuft", "prism_sapling"}
SPRITES = {"arcencium_lantern": "arcencium_lantern"}

_tex_cache = {}


def tex(name):
    if name not in _tex_cache:
        im = Image.open(os.path.join(TEX_DIR, name + ".png")).convert("RGBA")
        _tex_cache[name] = im.crop((0, 0, 16, 16))        # 1re frame si animee
    return _tex_cache[name]


def shade(im, f):
    r, g, b, a = im.split()
    r = r.point(lambda v: int(v * f))
    g = g.point(lambda v: int(v * f))
    b = b.point(lambda v: int(v * f))
    return Image.merge("RGBA", (r, g, b, a))


def face(texture, A, B, D, size):
    """Peint la texture sur le parallelogramme ecran A(0,0) B(16,0) D(0,16)."""
    ex = ((B[0] - A[0]) / 16.0, (B[1] - A[1]) / 16.0)
    ey = ((D[0] - A[0]) / 16.0, (D[1] - A[1]) / 16.0)
    det = ex[0] * ey[1] - ey[0] * ex[1]
    a, b = ey[1] / det, -ey[0] / det
    d, e = -ex[1] / det, ex[0] / det
    c = -(a * A[0] + b * A[1])
    f = -(d * A[0] + e * A[1])
    return texture.transform(size, Image.AFFINE, (a, b, c, d, e, f), Image.NEAREST)


def p(dx, dy, dz, h=1.0):
    """Projection locale d'un coin de bloc (h = hauteur du bloc)."""
    return ((dx - dz) * T + T, (dx + dz) * T / 2 - dy * T * h + T)


_sprite_cache = {}


def cube_sprite(top, side, h=1.0, alpha=255):
    key = (top, side, h, alpha)
    if key in _sprite_cache:
        return _sprite_cache[key]
    size = (2 * T, 2 * T)
    ttop, tside = tex(top), tex(side or top)
    if h < 1.0:                                         # dalle : cote tronque
        tside = tside.crop((0, int(16 * (1 - h)), 16, 16)).resize((16, 16), Image.NEAREST)
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    img.alpha_composite(face(shade(tside, 0.62), p(1, 1, 1, h), p(1, 1, 0, h), p(1, 0, 1, h), size))
    img.alpha_composite(face(shade(tside, 0.80), p(0, 1, 1, h), p(1, 1, 1, h), p(0, 0, 1, h), size))
    img.alpha_composite(face(ttop, p(0, 1, 0, h), p(1, 1, 0, h), p(0, 1, 1, h), size))
    if alpha < 255:
        r, g, b, a = img.split()
        a = a.point(lambda v: int(v * alpha / 255))
        img = Image.merge("RGBA", (r, g, b, a))
    _sprite_cache[key] = img
    return img


def cross_sprite(name):
    key = ("cross", name)
    if key in _sprite_cache:
        return _sprite_cache[key]
    size = (2 * T, 2 * T)
    t = tex(name)
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    # En dimetrique 45 degres, le plan diagonal x=z est vu par la tranche
    # (degenere). On pose donc les deux plans LE LONG des axes, au centre
    # du bloc : meme silhouette, pas de division par zero.
    img.alpha_composite(face(shade(t, 0.85), p(0.5, 1, 1), p(0.5, 1, 0), p(0.5, 0, 1), size))
    img.alpha_composite(face(t, p(0, 1, 0.5), p(1, 1, 0.5), p(0, 0, 0.5), size))
    _sprite_cache[key] = img
    return img


def flat_sprite(name, scale=0.55):
    """Petit objet (lanterne) : la texture posee en billboard, reduite."""
    key = ("flat", name)
    if key in _sprite_cache:
        return _sprite_cache[key]
    size = (2 * T, 2 * T)
    t = tex(name).resize((int(16 * scale * 2), int(16 * scale * 2)), Image.NEAREST)
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    img.alpha_composite(t, (T - t.width // 2, T + T // 2 - t.height))
    _sprite_cache[key] = img
    return img


def render(scene, margin=40, bg=(139, 139, 139, 255)):
    """scene : liste de (x, y, z, bloc). bloc peut porter un suffixe :
    '@slab' (demi-hauteur), ou etre une plante / une lanterne."""
    xs = [s[0] for s in scene]; ys = [s[1] for s in scene]; zs = [s[2] for s in scene]
    # bornes ecran
    def P(x, y, z):
        return ((x - z) * T, (x + z) * T / 2 - y * T)
    pts = [P(x, y, z) for x, y, z in ((min(xs), 0, max(zs)), (max(xs) + 1, 0, min(zs)),
                                      (min(xs), max(ys) + 1, min(zs)), (max(xs) + 1, 0, max(zs) + 1))]
    minx = min(q[0] for q in pts) - T
    miny = min(q[1] for q in pts) - T
    maxx = max(q[0] for q in pts) + T
    maxy = max(q[1] for q in pts) + 2 * T
    W = int(maxx - minx) + 2 * margin
    H = int(maxy - miny) + 2 * margin
    canvas = Image.new("RGBA", (W, H), bg)

    # ordre peintre : loin -> pres (x+z croissant), puis bas -> haut
    for x, y, z, block in sorted(scene, key=lambda s: (s[0] + s[2], s[1])):
        name, _, mod = block.partition("@")
        h = 0.5 if mod == "slab" else 1.0
        if name in CROSS:
            spr = cross_sprite(name)
        elif name in SPRITES:
            spr = flat_sprite(SPRITES[name])
        else:
            top, side = BLOCKS[name]
            alpha = 150 if name == "prismatic_glass" else 255
            spr = cube_sprite(top, side, h, alpha)
        sx, sy = P(x, y, z)
        canvas.alpha_composite(spr, (int(sx - minx - T + margin), int(sy - miny - T + margin)))
    return canvas


# ------------------------------------------------------------ maison test

def test_house():
    """Une maison de prospecteur : murs de gangue taillee, chainages d'angle
    en brique d'Arcencium, fenetres prismatiques, toit en planches, lanterne
    sur son poteau, sol mele (herbe prismatique + dallage de gangue), un
    arbre de Prisme, fleurs et touffes."""
    S = []
    W_, D_ = 7, 6                # emprise maison : x 0..6, z 0..5
    # sol 13 x 12 : herbe prismatique, avec un chemin de gangue
    for x in range(-3, 10):
        for z in range(-3, 9):
            path = (z == 7 and -1 <= x <= 8) or (x == 3 and 6 <= z <= 8)
            S.append((x, 0, z, "gangue_stone" if path else "prismatic_grass_block"))
    # soubassement : pierre veinee
    for x in range(W_):
        for z in range(D_):
            if x in (0, W_ - 1) or z in (0, D_ - 1):
                S.append((x, 1, z, "veined_stone"))
    # murs : gangue taillee, angles en brique d'Arcencium, 3 de haut
    for y in (2, 3, 4):
        for x in range(W_):
            for z in range(D_):
                edge = x in (0, W_ - 1) or z in (0, D_ - 1)
                if not edge:
                    continue
                corner = x in (0, W_ - 1) and z in (0, D_ - 1)
                if corner:
                    S.append((x, y, z, "arcencium_bricks"))
                    continue
                # porte (face +z, au centre), fenetres (y=3)
                if z == D_ - 1 and x == 3 and y in (2, 3):
                    continue
                if y == 3 and ((z == D_ - 1 and x in (1, 5)) or (x == W_ - 1 and z in (2, 3))
                               or (z == 0 and x in (2, 4)) or (x == 0 and z in (2, 3))):
                    S.append((x, y, z, "prismatic_glass"))
                    continue
                S.append((x, y, z, "gangue_bricks"))
    # linteau cisele au-dessus de la porte
    S.append((3, 4, D_ - 1, "chiseled_arcencium"))
    # toit a deux pans en dalles de planches (demi-pas), debordant d'un bloc ;
    # pignons en gangue taillee sous les pans, aux deux bouts
    for x in range(-1, W_ + 1):
        for z in range(-1, D_ + 1):
            level = min(z + 1, D_ - z)          # 0 aux gouttieres, 3 au faite
            S.append((x, 5 + level * 0.5, z, "crystal_planks@slab"))
    for x in (0, W_ - 1):
        for z in range(1, D_ - 1):
            S.append((x, 5, z, "gangue_bricks"))
            if z in (2, 3):
                S.append((x, 6, z, "gangue_bricks@slab"))
    # interieur : sol en gangue polie + tapis
    for x in range(1, W_ - 1):
        for z in range(1, D_ - 1):
            S.append((x, 1, z, "polished_gangue"))
    for x in range(2, 5):
        for z in range(2, 4):
            S.append((x, 2, z, "verdigris_wool@slab"))
    # lanterne sur poteau pres de la porte
    S.append((5, 1, 7, "crystal_planks"))
    S.append((5, 2, 7, "crystal_planks"))
    S.append((5, 3, 7, "arcencium_lantern"))
    # arbre de Prisme a l'angle
    tx, tz = -2, -1
    for y in range(1, 6):
        S.append((tx, y, tz, "prism_log"))
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in (4, 5, 6):
                if abs(dx) + abs(dz) + (dy - 5) ** 2 <= 3 and not (dx == 0 and dz == 0 and dy < 6):
                    S.append((tx + dx, dy, tz + dz, "prism_leaves"))
    # fleurs et touffes
    for (x, z, kind) in ((-2, 4, "prism_bloom"), (8, 2, "prism_tuft"), (7, 8, "prism_bloom"),
                         (-1, 7, "prism_tuft"), (1, 8, "prism_bloom"), (8, 5, "prism_tuft"),
                         (-3, 1, "prism_tuft"), (9, 8, "prism_sapling")):
        S.append((x, 1, z, kind))
    return S


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    img = render(test_house())
    out = os.path.join(OUT_DIR, "test_house.png")
    img.save(out)
    print("Rendu :", os.path.relpath(out, ROOT), img.size)
