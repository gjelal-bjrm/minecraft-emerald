#!/usr/bin/env python3
"""
Visualiseur de batiments : rend une structure .nbt en isometrique, sous
TOUS LES ANGLES, avec vue en coupe (maison de poupee) et plans par etage.

C'est l'outil qui manquait : sans lui je construisais a l'aveugle, en
boucles for, d'ou des batiments cubiques. Ici on peut etudier un batiment
vanilla (ou d'un autre mod) sous 4 angles, l'ouvrir pour voir l'interieur,
et lire chaque etage a plat.

Les textures viennent du jar client pour le vanilla, et de nos assets pour
les blocs du mod. Les formes courantes sont respectees (escaliers, dalles,
vitres, barrieres, plantes) : rendre un escalier comme un cube plein
donnerait une lecture completement fausse du volume.

Usage :
    python tools/build_view.py <structure> [--out NOM] [--cut] [--plans]
    python tools/build_view.py village/plains/houses/plains_medium_house_1
"""

import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_structure as nbtlib
from PIL import Image

ROOT = nbtlib.ROOT
OUT_DIR = os.path.join(ROOT, "tools", "preview", "builds")
MOD_TEX = os.path.join(ROOT, "src", "main", "resources", "assets",
                       "emeraldweapons", "textures", "block")
T = 20                       # demi-largeur ecran d'un bloc (px)

# Teintes de biome : ces textures sont en niveaux de gris dans le jar et
# sont colorees au rendu par le jeu. Sans ca, herbe et feuilles sont grises.
TINTS = {
    "grass_block_top": (0x91, 0xBD, 0x59), "short_grass": (0x91, 0xBD, 0x59),
    "tall_grass_top": (0x91, 0xBD, 0x59), "tall_grass_bottom": (0x91, 0xBD, 0x59),
    "fern": (0x91, 0xBD, 0x59), "large_fern_top": (0x91, 0xBD, 0x59),
    "large_fern_bottom": (0x91, 0xBD, 0x59), "grass_block_side_overlay": (0x91, 0xBD, 0x59),
    "oak_leaves": (0x77, 0xAB, 0x2F), "birch_leaves": (0x80, 0xA7, 0x55),
    "spruce_leaves": (0x61, 0x99, 0x61), "jungle_leaves": (0x77, 0xAB, 0x2F),
    "acacia_leaves": (0x77, 0xAB, 0x2F), "dark_oak_leaves": (0x77, 0xAB, 0x2F),
    "vine": (0x77, 0xAB, 0x2F), "lily_pad": (0x71, 0xC3, 0x5F),
    "water_still": (0x3F, 0x76, 0xE4), "sugar_cane": (0x91, 0xBD, 0x59),
}

_jar = None
_tex_cache = {}


def jar():
    global _jar
    if _jar is None:
        _jar = zipfile.ZipFile(nbtlib.CLIENT_JAR)
    return _jar


def texture(name):
    """Charge une texture de bloc (mod d'abord, puis jar vanilla)."""
    if name in _tex_cache:
        return _tex_cache[name]
    img = None
    local = os.path.join(MOD_TEX, name + ".png")
    if os.path.exists(local):
        img = Image.open(local).convert("RGBA")
    else:
        try:
            import io
            img = Image.open(io.BytesIO(
                jar().read("assets/minecraft/textures/block/%s.png" % name))).convert("RGBA")
        except KeyError:
            img = None
    if img is None:
        img = Image.new("RGBA", (16, 16), (200, 60, 200, 255))    # manquant : magenta
    # 1re frame si animee : la frame est carree, donc de cote = largeur.
    # Un crop fixe a 16x16 decoupait un coin des textures 64x64 (les buches).
    w = img.width
    img = img.crop((0, 0, w, w))
    if w != 16:
        img = img.resize((16, 16), Image.BOX)
    if name in TINTS:
        t = TINTS[name]
        r, g, b, a = img.split()
        img = Image.merge("RGBA", (
            r.point(lambda v: v * t[0] // 255),
            g.point(lambda v: v * t[1] // 255),
            b.point(lambda v: v * t[2] // 255), a))
    _tex_cache[name] = img
    return img


# ------------------------------------------------- resolution des textures

# Suffixes de forme a retirer pour retrouver la texture de base
STRIP = ["_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate",
         "_button", "_trapdoor", "_sign", "_pane"]
# Cas ou le nom de bloc ne donne pas le nom de texture
ALIAS = {
    "cobblestone_stairs": "cobblestone", "cobblestone_slab": "cobblestone",
    "stone_bricks": "stone_bricks", "grass_block": "grass_block_top",
    "dirt_path": "dirt_path_top", "farmland": "farmland",
    "glass_pane": "glass", "oak_door": "oak_door_lower",
    "wall_torch": "torch", "lantern": "lantern",
    "crafting_table": "crafting_table_top", "furnace": "furnace_front",
    "composter": "composter_side", "barrel": "barrel_side",
    "cartography_table": "cartography_table_side3",
    "fletching_table": "fletching_table_side", "smoker": "smoker_side",
    "loom": "loom_side", "smithing_table": "smithing_table_side",
    "stonecutter": "stonecutter_side", "grindstone": "grindstone_side",
    "blast_furnace": "blast_furnace_front", "cauldron": "cauldron_side",
    "bell": "bell_bottom", "lectern": "lectern_sides",
    "bookshelf": "bookshelf", "hay_block": "hay_block_side",
    "white_bed": "white_wool", "red_bed": "red_wool",
    "jigsaw": "jigsaw_side", "structure_block": "structure_block",
    "torch": "torch", "campfire": "campfire_log",
    "flower_pot": "flower_pot", "water": "water_still",
    "wheat": "wheat_stage7", "carrots": "carrots_stage3",
    "potatoes": "potatoes_stage3", "beetroots": "beetroots_stage3",
}
# Blocs a deux textures : (dessus, cote)
TOP_SIDE = {
    "grass_block": ("grass_block_top", "grass_block_side"),
    "podzol": ("podzol_top", "podzol_side"),
    "dirt_path": ("dirt_path_top", "dirt_path_side"),
    "farmland": ("farmland", "dirt"),
    "hay_block": ("hay_block_top", "hay_block_side"),
    "crafting_table": ("crafting_table_top", "crafting_table_side"),
    "furnace": ("furnace_top", "furnace_front"),
    "smoker": ("smoker_top", "smoker_side"),
    "blast_furnace": ("blast_furnace_top", "blast_furnace_front"),
    "composter": ("composter_top", "composter_side"),
    "barrel": ("barrel_top", "barrel_side"),
    "loom": ("loom_top", "loom_side"),
    "smithing_table": ("smithing_table_top", "smithing_table_side"),
    "cartography_table": ("cartography_table_top", "cartography_table_side3"),
    "fletching_table": ("fletching_table_top", "fletching_table_side"),
    "stonecutter": ("stonecutter_top", "stonecutter_side"),
    "melon": ("melon_top", "melon_side"),
    "pumpkin": ("pumpkin_top", "pumpkin_side"),
    "carved_pumpkin": ("pumpkin_top", "carved_pumpkin"),
    "bookshelf": ("oak_planks", "bookshelf"),
    "prismatic_grass_block": ("prismatic_grass_block_top", "prismatic_grass_block_side"),
    "prism_log": ("prism_log_top", "prism_log"),
}

CROSS_BLOCKS = {"poppy", "dandelion", "short_grass", "grass", "fern", "sapling",
                "azure_bluet", "oxeye_daisy", "cornflower", "allium", "blue_orchid",
                "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
                "lily_of_the_valley", "wither_rose", "dead_bush", "sweet_berry_bush",
                "wheat", "carrots", "potatoes", "beetroots", "prism_bloom",
                "prism_tuft", "prism_sapling"}


def base_texture(block, props):
    """(texture dessus, texture cote) pour un nom de bloc court."""
    if block in TOP_SIDE:
        return TOP_SIDE[block]
    if block in ALIAS:
        t = ALIAS[block]
        return (t, t)
    name = block
    for suf in STRIP:
        if name.endswith(suf):
            name = name[:-len(suf)]
            break
    if name.endswith("_log") or name.endswith("_stem"):
        return (name + "_top", name)
    if name.endswith("_wood") or name.endswith("_hyphae"):
        return (name.replace("_wood", "_log").replace("_hyphae", "_stem"),) * 2
    for cand in (name, name + "s", name + "_planks", name + "_block", name + "_top"):
        local = os.path.join(MOD_TEX, cand + ".png")
        if os.path.exists(local):
            return (cand, cand)
        try:
            jar().getinfo("assets/minecraft/textures/block/%s.png" % cand)
            return (cand, cand)
        except KeyError:
            continue
    return (name, name)


# ------------------------------------------------------- formes des blocs

def shape_boxes(block, props):
    """Liste de boites (x0,y0,z0,x1,y1,z1) en unites de bloc [0,1].
    Retourne None pour les plantes en croix, [] pour l'invisible."""
    p = props
    if block.endswith("_slab"):
        t = p.get("type", "bottom")
        if t == "double":
            return [(0, 0, 0, 1, 1, 1)]
        return [(0, 0.5, 0, 1, 1, 1)] if t == "top" else [(0, 0, 0, 1, 0.5, 1)]
    if block.endswith("_stairs"):
        # Le modele vanilla place la marche haute cote +x, et la blockstate
        # tourne le modele : facing=east -> marche a l'est.
        half = p.get("half", "bottom")
        facing = p.get("facing", "east")
        low = (0, 0, 0, 1, 0.5, 1) if half == "bottom" else (0, 0.5, 0, 1, 1, 1)
        step_y = (0.5, 1) if half == "bottom" else (0, 0.5)
        step = {
            "east":  (0.5, step_y[0], 0, 1, step_y[1], 1),
            "west":  (0, step_y[0], 0, 0.5, step_y[1], 1),
            "south": (0, step_y[0], 0.5, 1, step_y[1], 1),
            "north": (0, step_y[0], 0, 1, step_y[1], 0.5),
        }[facing]
        return [low, step]
    if block.endswith("_pane") or block == "iron_bars":
        return [(0.44, 0, 0, 0.56, 1, 1), (0, 0, 0.44, 1, 1, 0.56)]
    if block.endswith("_fence") or block.endswith("_wall"):
        return [(0.35, 0, 0.35, 0.65, 1, 0.65),
                (0.44, 0.3, 0, 0.56, 0.9, 1), (0, 0.3, 0.44, 1, 0.9, 0.56)]
    if block.endswith("_fence_gate"):
        return [(0.35, 0, 0, 0.65, 1, 1)]
    if block.endswith("_door"):
        f = p.get("facing", "north")
        d = 0.19
        return {"north": [(0, 0, 0, 1, 1, d)], "south": [(0, 0, 1 - d, 1, 1, 1)],
                "west": [(0, 0, 0, d, 1, 1)], "east": [(1 - d, 0, 0, 1, 1, 1)]}[f]
    if block.endswith("_trapdoor"):
        return [(0, 0, 0, 1, 0.19, 1)] if p.get("half") == "bottom" else [(0, 0.81, 0, 1, 1, 1)]
    if block.endswith("_carpet") or block == "moss_carpet":
        return [(0, 0, 0, 1, 0.0625, 1)]
    if block.endswith("_pressure_plate"):
        return [(0.06, 0, 0.06, 0.94, 0.0625, 0.94)]
    if block.endswith("_bed"):
        return [(0, 0, 0, 1, 0.5625, 1)]
    if block in ("torch", "wall_torch", "soul_torch", "soul_wall_torch"):
        return [(0.44, 0, 0.44, 0.56, 0.62, 0.56)]
    if block == "lantern" or block == "arcencium_lantern":
        return [(0.31, 0.06, 0.31, 0.69, 0.62, 0.69)]
    if block in ("cauldron", "composter", "lectern", "stonecutter", "grindstone",
                 "enchanting_table", "brewing_stand", "cake"):
        return [(0.06, 0, 0.06, 0.94, 0.87, 0.94)]
    if block == "flower_pot" or block.startswith("potted_"):
        return [(0.31, 0, 0.31, 0.69, 0.37, 0.69)]
    if block == "campfire":
        return [(0, 0, 0, 1, 0.44, 1)]
    if block == "bell":
        return [(0.25, 0.25, 0.25, 0.75, 1, 0.75)]
    if block in ("water", "lava"):
        return [(0, 0, 0, 1, 0.87, 1)]
    if block in CROSS_BLOCKS or block.endswith("_sapling") or block.endswith("_bush"):
        return None
    return [(0, 0, 0, 1, 1, 1)]


# --------------------------------------------------------------- rendu iso

def shade(im, f):
    r, g, b, a = im.split()
    return Image.merge("RGBA", (r.point(lambda v: int(v * f)),
                                g.point(lambda v: int(v * f)),
                                b.point(lambda v: int(v * f)), a))


def _affine(tex_img, A, B, D, size):
    """Peint tex_img sur le parallelogramme d'origine A, cotes AB et AD."""
    ex = ((B[0] - A[0]) / 16.0, (B[1] - A[1]) / 16.0)
    ey = ((D[0] - A[0]) / 16.0, (D[1] - A[1]) / 16.0)
    det = ex[0] * ey[1] - ey[0] * ex[1]
    if abs(det) < 1e-9:
        return Image.new("RGBA", size, (0, 0, 0, 0))     # face vue par la tranche
    a, b = ey[1] / det, -ey[0] / det
    d, e = -ex[1] / det, ex[0] / det
    c = -(a * A[0] + b * A[1])
    f = -(d * A[0] + e * A[1])
    return tex_img.transform(size, Image.AFFINE, (a, b, c, d, e, f), Image.NEAREST)


def proj(x, y, z):
    """Projection dimetrique 2:1, en pixels ecran (origine locale)."""
    return ((x - z) * T, (x + z) * T * 0.5 - y * T)


_sprite_cache = {}


def box_sprite(boxes, top_tex, side_tex, alpha=255):
    """Sprite d'un bloc : ses boites, faces dessus / +z / +x ombrees."""
    key = (tuple(boxes), top_tex, side_tex, alpha)
    if key in _sprite_cache:
        return _sprite_cache[key]
    W = H = 4 * T
    cx, cy = 2 * T, 3 * T                       # origine locale dans le sprite
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ttop, tside = texture(top_tex), texture(side_tex)

    def P(x, y, z):
        sx, sy = proj(x, y, z)
        return (sx + cx, sy + cy)

    for (x0, y0, z0, x1, y1, z1) in boxes:
        # decoupe de la texture pour les faces partielles (dalles, marches)
        sx = tside.crop((int(x0 * 16), int((1 - y1) * 16), max(int(x1 * 16), int(x0 * 16) + 1),
                         max(int((1 - y0) * 16), int((1 - y1) * 16) + 1))).resize((16, 16), Image.NEAREST)
        sz = tside.crop((int(z0 * 16), int((1 - y1) * 16), max(int(z1 * 16), int(z0 * 16) + 1),
                         max(int((1 - y0) * 16), int((1 - y1) * 16) + 1))).resize((16, 16), Image.NEAREST)
        tp = ttop.crop((int(x0 * 16), int(z0 * 16), max(int(x1 * 16), int(x0 * 16) + 1),
                        max(int(z1 * 16), int(z0 * 16) + 1))).resize((16, 16), Image.NEAREST)
        img.alpha_composite(_affine(shade(sx, 0.62), P(x1, y1, z1), P(x1, y1, z0), P(x1, y0, z1), (W, H)))
        img.alpha_composite(_affine(shade(sz, 0.80), P(x0, y1, z1), P(x1, y1, z1), P(x0, y0, z1), (W, H)))
        img.alpha_composite(_affine(tp, P(x0, y1, z0), P(x1, y1, z0), P(x0, y1, z1), (W, H)))
    if alpha < 255:
        r, g, b, a = img.split()
        img = Image.merge("RGBA", (r, g, b, a.point(lambda v: v * alpha // 255)))
    _sprite_cache[key] = img
    return img


def cross_sprite(tex_name):
    key = ("cross", tex_name)
    if key in _sprite_cache:
        return _sprite_cache[key]
    W = H = 4 * T
    cx, cy = 2 * T, 3 * T
    t = texture(tex_name)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    def P(x, y, z):
        sx, sy = proj(x, y, z)
        return (sx + cx, sy + cy)

    # plans le long des axes (le plan diagonal serait vu par la tranche)
    img.alpha_composite(_affine(shade(t, 0.85), P(0.5, 1, 1), P(0.5, 1, 0), P(0.5, 0, 1), (W, H)))
    img.alpha_composite(_affine(t, P(0, 1, 0.5), P(1, 1, 0.5), P(0, 0, 0.5), (W, H)))
    _sprite_cache[key] = img
    return img


TRANSPARENT = {"glass", "glass_pane", "prismatic_glass", "prismatic_glass_pane",
               "water", "ice", "tinted_glass"}

FACINGS_CW = {"north": "east", "east": "south", "south": "west", "west": "north"}


def rotate_cells(cells, size, turns):
    """Tourne le nuage de blocs de turns*90 deg (sens horaire vu de dessus),
    en corrigeant aussi les proprietes 'facing' et 'axis'."""
    turns %= 4
    sx, sy, sz = size
    out = {}
    for _ in range(turns):
        out = {}
        for (x, y, z), (name, props) in cells.items():
            nx, nz = sz - 1 - z, x
            np = dict(props)
            if "facing" in np and np["facing"] in FACINGS_CW:
                np["facing"] = FACINGS_CW[np["facing"]]
            if np.get("axis") in ("x", "z"):
                np["axis"] = "z" if np["axis"] == "x" else "x"
            out[(nx, y, nz)] = (name, np)
        cells = out
        sx, sz = sz, sx
    return (cells if turns else dict(cells)), (sx, sy, sz)


def render_cells(cells, size, cut=0.0, bg=(122, 126, 132, 255), label=None):
    """cells : {(x,y,z): (nom_court, props)}. cut : fraction des murs avant
    retiree (0 = ferme, 0.35 = maison de poupee)."""
    sx, sy, sz = size
    corners = [proj(0, 0, sz), proj(sx, 0, 0), proj(0, sy, 0), proj(sx, 0, sz),
               proj(0, 0, 0), proj(sx, sy, sz)]
    minx = min(c[0] for c in corners) - 2 * T
    maxx = max(c[0] for c in corners) + 2 * T
    miny = min(c[1] for c in corners) - 2 * T
    maxy = max(c[1] for c in corners) + 2 * T
    m = 24
    W = int(maxx - minx) + 2 * m
    H = int(maxy - miny) + 2 * m
    canvas = Image.new("RGBA", (W, H), bg)

    # coupe : on masque une tranche des deux faces les plus proches du regard
    cut_x = int(sx * cut) if cut else 0
    cut_z = int(sz * cut) if cut else 0

    # Elagage des faces cachees : un bloc dont les 6 voisins sont pleins et
    # opaques n'est jamais visible. Sur les gros batiments monolithiques
    # (jusqu'a 944 000 blocs) c'est ce qui rend le rendu possible.
    opaque = {k for k, v in cells.items()
              if v[0] not in TRANSPARENT and shape_boxes(v[0], v[1]) == [(0, 0, 0, 1, 1, 1)]}
    visible = []
    for k in cells:
        x, y, z = k
        if k in opaque and all((x + dx, y + dy, z + dz) in opaque
                               for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0),
                                                  (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            continue
        visible.append(k)

    for (x, y, z) in sorted(visible, key=lambda k: (k[0] + k[2], k[1])):
        if cut and (x >= sx - cut_x or z >= sz - cut_z):
            continue
        name, props = cells[(x, y, z)]
        boxes = shape_boxes(name, props)
        if boxes == []:
            continue
        top, side = base_texture(name, props)
        if boxes is None:
            spr = cross_sprite(side)
        else:
            spr = box_sprite(boxes, top, side, 140 if name in TRANSPARENT else 255)
        px, py = proj(x, y, z)
        canvas.alpha_composite(spr, (int(px - minx + m - 2 * T), int(py - miny + m - 3 * T)))
    return canvas


def floor_plans(cells, size, cols=4):
    """Plan de chaque etage, vu de dessus, en pastilles de couleur."""
    sx, sy, sz = size
    cell = 12
    rows = (sy + cols - 1) // cols
    pw, ph = sx * cell + 10, sz * cell + 10
    out = Image.new("RGBA", (cols * pw, rows * ph), (60, 62, 66, 255))
    from PIL import ImageDraw
    d = ImageDraw.Draw(out)
    for y in range(sy):
        ox, oy = (y % cols) * pw + 5, (y // cols) * ph + 5
        d.rectangle([ox - 3, oy - 3, ox + sx * cell + 1, oy + sz * cell + 1],
                    fill=(38, 40, 44, 255))
        for x in range(sx):
            for z in range(sz):
                e = cells.get((x, y, z))
                if not e:
                    continue
                t = texture(base_texture(e[0], e[1])[0])
                col = t.resize((1, 1), Image.BOX).getpixel((0, 0))
                d.rectangle([ox + x * cell, oy + z * cell,
                             ox + (x + 1) * cell - 1, oy + (z + 1) * cell - 1],
                            fill=col[:3] + (255,))
        d.text((ox + 2, oy + sz * cell + 2), "y=%d" % y, fill=(220, 220, 220, 255))
    return out


def short(name):
    return name.split(":", 1)[-1]


def view(struct_name, out_name=None, cut=0.35, plans=True, angles=(0, 1, 2, 3),
         jar=None, scale=None):
    global T
    s = nbtlib.load(struct_name, jar)
    if scale:
        T = scale
        _sprite_cache.clear()
    else:
        # gros batiments : on reduit l'echelle pour que l'image reste lisible
        vol = max(s.size)
        T = 20 if vol <= 20 else (12 if vol <= 40 else (7 if vol <= 90 else 3))
        _sprite_cache.clear()
    cells = {}
    for (x, y, z), full in s.solid_cells().items():
        cells[(x, y, z)] = (short(full), s.props_at(x, y, z))
    out_name = out_name or os.path.basename(struct_name)
    os.makedirs(OUT_DIR, exist_ok=True)
    made = []

    views = []
    for t in angles:
        c, sz = rotate_cells(cells, s.size, t)
        views.append(render_cells(c, sz))
    if views:
        h = max(v.height for v in views)
        strip = Image.new("RGBA", (sum(v.width for v in views), h), (122, 126, 132, 255))
        ox = 0
        for v in views:
            strip.alpha_composite(v, (ox, h - v.height))
            ox += v.width
        p = os.path.join(OUT_DIR, out_name + "_angles.png")
        strip.save(p)
        made.append(p)

    if cut:
        cuts = []
        for t in angles[:2]:
            c, sz = rotate_cells(cells, s.size, t)
            cuts.append(render_cells(c, sz, cut=cut))
        h = max(v.height for v in cuts)
        strip = Image.new("RGBA", (sum(v.width for v in cuts), h), (122, 126, 132, 255))
        ox = 0
        for v in cuts:
            strip.alpha_composite(v, (ox, h - v.height))
            ox += v.width
        p = os.path.join(OUT_DIR, out_name + "_cut.png")
        strip.save(p)
        made.append(p)

    if plans:
        p = os.path.join(OUT_DIR, out_name + "_plans.png")
        floor_plans(cells, s.size).save(p)
        made.append(p)

    print("taille %s, %d blocs poses" % (str(s.size), len(cells)))
    for p in made:
        print("  ", os.path.relpath(p, ROOT))
    return made


if __name__ == "__main__":
    argv = sys.argv[1:]
    jar_arg = None
    if "--jar" in argv:
        i = argv.index("--jar")
        jar_arg = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    scale = None
    if "--scale" in argv:
        i = argv.index("--scale")
        scale = int(argv[i + 1]); argv = argv[:i] + argv[i + 2:]
    args = [a for a in argv if not a.startswith("--")]
    flags = [a for a in argv if a.startswith("--")]
    name = args[0] if args else "village/plains/houses/plains_medium_house_1"
    out = args[1] if len(args) > 1 else None
    view(name, out, cut=0.0 if "--nocut" in flags else 0.35,
         plans="--noplans" not in flags, jar=jar_arg, scale=scale)
