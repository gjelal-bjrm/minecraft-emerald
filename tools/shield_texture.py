#!/usr/bin/env python3
"""
Le bouclier d'Arcencium : sa texture animee et ses deux modeles (22 sept., cahier §83).

LA MATIERE EST CELLE DE L'ARMURE. Le joueur a demande un bouclier « inspire de
l'armure » : metal noir, fissures lumineuses aux couleurs de l'arc-en-ciel qui
tournent. On prend donc la meme planche de reference (refs/arcencium_material_ref.png),
reechantillonnee par vote comme les icones (item_from_ref.downsample), et seules les
fissures changent de teinte d'une image a l'autre (item_from_ref.hue_frames).

LE MODELE EST CELUI DU BOUCLIER VANILLA, EN JSON. Le bouclier du jeu est dessine par
code (BlockEntityWithoutLevelRenderer, ShieldModel) : une plaque de 12 x 22 x 1 et une
poignee de 2 x 6 x 6, retournees par scale(1, -1, -1). Les memes boites, retournees a la
main, donnent un modele JSON que les transformations d'affichage de item/shield.json
placent exactement comme le bouclier vanilla -- tenu, leve, au sol, dans l'inventaire.
Et un modele JSON prend une texture de l'atlas des objets, qui s'anime (.mcmeta) :
le rendu par code ne le permettrait pas.

La face avant (+Z une fois retournee) porte le decor ; la poignee est derriere (-Z).

DENSITE DOUBLE : 2 pixels par pixel de modele (la face avant fait 24 x 44), dans une
image de 64 x 64 par trame -- comme les icones d'armure, pour que les fissures se
lisent en traits et non en pates.

Usage :
    python tools/shield_texture.py [--preview]
"""

import colorsys
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from item_from_ref import downsample, hue_frames              # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons")
TEXTURE = os.path.join(ASSETS, "textures", "item", "arcencium_shield.png")
MODELS = os.path.join(ASSETS, "models", "item")
MATERIAL = os.path.join(ROOT, "tools", "refs", "arcencium_material_ref.png")
PREVIEW = os.path.join(ROOT, "tools", "preview")

FRAME = 64                  # une trame : 64 x 64 pixels, soit 4 pixels par unite d'UV
NFRAMES = 12                # comme les icones d'armure
FRAMETIME = 3
MATERIAL_SIZE = 96          # a 96, une fissure d'un pixel de la planche survit au vote
UV = FRAME / 16.0           # pixels par unite d'UV

# Les regions de la trame, en pixels : (x0, y0, x1, y1)
FRONT = (0, 0, 24, 44)
BACK = (24, 0, 48, 44)
SIDE_L = (48, 0, 50, 44)
SIDE_R = (50, 0, 52, 44)
TOP = (0, 44, 24, 46)
BOTTOM = (0, 46, 24, 48)
HANDLE_SIDE = (52, 0, 64, 12)
HANDLE_FACE = (52, 12, 56, 24)
HANDLE_CAP = (56, 12, 60, 24)

CRACK_SAT = 0.55
CRACK_VAL = 0.45

OUTLINE = (9, 9, 13)
RIM_LIGHT = (86, 88, 104)
RIM_DARK = (34, 35, 44)
LEATHER = (52, 38, 30)
LEATHER_STITCH = (92, 74, 56)


def split(material):
    """La matiere sombre et les fissures vives de la planche reechantillonnee."""
    px = material.load()
    w, h = material.size
    dark, cracks = {}, {}
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s >= CRACK_SAT and v >= CRACK_VAL:
                cracks[(x, y)] = (r, g, b)
            else:
                dark[(x, y)] = (r, g, b)
    return dark, cracks


def best_window(cracks, size, w, h, want=0.16):
    """La fenetre de la planche dont la part de fissures est la plus proche de {want},
    en evitant qu'elles se massent contre un bord (le centre porte la gemme)."""
    best, best_score = (0, 0), 1e9
    for oy in range(0, size - h + 1, 2):
        for ox in range(0, size - w + 1, 2):
            n, centre = 0, 0
            for y in range(h):
                for x in range(w):
                    if (ox + x, oy + y) in cracks:
                        n += 1
                        if w * 0.2 < x < w * 0.8 and h * 0.2 < y < h * 0.8:
                            centre += 1
            share = n / float(w * h)
            score = abs(share - want) - 0.004 * centre
            if score < best_score:
                best, best_score = (ox, oy), score
    return best


def metal(rgb, value):
    """Le metal, a la valeur voulue (la planche tourne autour de 12 sur 255).

    Neutre, a peine froid : les teintes de la planche autour des fissures, etalees,
    faisaient des halos rouges ou verts qui ne tournaient pas avec la fissure voisine.
    Le grain de la planche reste, par la luminosite.
    """
    r, g, b = rgb
    grain = max(0.75, min(1.25, ((r + g + b) / 3.0) / 12.0))
    v = value * (0.85 + 0.15 * grain)
    return (min(255, int(v * 0.94)), min(255, int(v * 0.97)), min(255, int(v * 1.10)), 255)


def plate(dark, cracks, origin, back=False):
    """Une face de la plaque (24 x 44) : metal bombe, liseré, fissures ; la gemme devant."""
    w, h = 24, 44
    ox, oy = origin
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    for y in range(h):
        for x in range(w):
            key = (ox + x, oy + y)
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge == 0:
                px[x, y] = OUTLINE + (255,)
                continue
            if edge == 1:
                # le liseré : eclaire en haut et a gauche, dans l'ombre en bas et a droite
                lit = (x <= 1 or y <= 1) and not (x >= w - 2 or y >= h - 2)
                px[x, y] = (RIM_LIGHT if lit and not back else RIM_DARK) + (255,)
                continue
            # le bombe : la lumiere vient d'en haut a gauche
            dx, dy = (x - cx) / (w / 2.0), (y - cy) / (h / 2.0)
            t = max(0.0, 1.0 - 0.55 * (dx * dx + dy * dy) - 0.18 * (dx + dy))
            if back:
                value = 10 + 24 * t
            else:
                value = 14 + 50 * t
            if key in cracks and edge >= 2:
                r, g, b = cracks[key]
                if back:
                    # derriere, les fissures ne font que rougeoyer
                    hh, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                    rr, gg, bb = colorsys.hsv_to_rgb(hh, s, 0.5)
                    px[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), 255)
                else:
                    px[x, y] = (r, g, b, 255)
            else:
                px[x, y] = metal(dark.get(key, (18, 18, 21)), value)
    if not back:
        gem(px, w, h)
    return img


def gem(px, w, h):
    """La gemme d'Arcencium au centre : un losange, coeur blanc, anneau aux couleurs du prisme."""
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    hw, hh = 4.6, 6.6
    for y in range(h):
        for x in range(w):
            d = abs(x - cx) / hw + abs(y - cy) / hh
            if d > 1.18:
                continue
            if d > 1.0:
                px[x, y] = OUTLINE + (255,)                   # le chaton
            elif d <= 0.34:
                px[x, y] = (236, 240, 255, 255)               # le coeur, qui ne tourne pas
            else:
                # l'anneau : la teinte suit l'angle, comme un prisme -- et tourne avec les fissures
                import math
                hue = (math.atan2(y - cy, x - cx) / (2 * math.pi)) % 1.0
                r, g, b = colorsys.hsv_to_rgb(hue, 0.85, 1.0 if d < 0.7 else 0.82)
                px[x, y] = (int(r * 255), int(g * 255), int(b * 255), 255)


def fill(img, box, colour):
    px = img.load()
    for y in range(box[1], box[3]):
        for x in range(box[0], box[2]):
            px[x, y] = colour + (255,)


def frame_base():
    ref = Image.open(MATERIAL).convert("RGBA")
    material = downsample(ref, MATERIAL_SIZE, vivid_min=0.15)
    dark, cracks = split(material)
    front_at = best_window(cracks, MATERIAL_SIZE, 24, 44)
    back_at = ((front_at[0] + 37) % (MATERIAL_SIZE - 24), (front_at[1] + 29) % (MATERIAL_SIZE - 44))
    base = Image.new("RGBA", (FRAME, FRAME), (0, 0, 0, 0))
    base.paste(plate(dark, cracks, front_at), FRONT[:2])
    base.paste(plate(dark, cracks, back_at, back=True), BACK[:2])
    # la tranche : le liseré en epaisseur
    fill(base, SIDE_L, RIM_DARK)
    fill(base, SIDE_R, RIM_DARK)
    fill(base, TOP, RIM_LIGHT)
    fill(base, BOTTOM, RIM_DARK)
    # la poignee : du cuir sombre, une couture
    fill(base, HANDLE_SIDE, LEATHER)
    fill(base, HANDLE_FACE, LEATHER)
    fill(base, HANDLE_CAP, LEATHER)
    px = base.load()
    for x in range(HANDLE_SIDE[0] + 1, HANDLE_SIDE[2] - 1):
        if x % 2 == 0:
            px[x, HANDLE_SIDE[1] + 2] = LEATHER_STITCH + (255,)
            px[x, HANDLE_SIDE[3] - 3] = LEATHER_STITCH + (255,)
    share = sum(1 for y in range(44) for x in range(24)
                if (front_at[0] + x, front_at[1] + y) in cracks) / (24.0 * 44)
    print("Face avant : fenetre %s de la planche, %.0f %% de fissures" % (front_at, 100 * share))
    return base


# ------------------------------------------------------------------ les modeles

def uv(box):
    return [round(box[0] / UV, 4), round(box[1] / UV, 4), round(box[2] / UV, 4), round(box[3] / UV, 4)]


def elements():
    """La plaque et la poignee de ShieldModel, retournees comme le fait scale(1, -1, -1)."""
    t = "#shield"
    plate_el = {
        "from": [-6, -11, 1], "to": [6, 11, 2],
        "faces": {
            "south": {"uv": uv(FRONT), "texture": t},
            "north": {"uv": uv(BACK), "texture": t},
            "west": {"uv": uv(SIDE_L), "texture": t},
            "east": {"uv": uv(SIDE_R), "texture": t},
            "up": {"uv": uv(TOP), "texture": t},
            "down": {"uv": uv(BOTTOM), "texture": t},
        },
    }
    handle_el = {
        "from": [-1, -3, -5], "to": [1, 3, 1],
        "faces": {
            "west": {"uv": uv(HANDLE_SIDE), "texture": t},
            "east": {"uv": uv(HANDLE_SIDE), "texture": t},
            "north": {"uv": uv(HANDLE_FACE), "texture": t},
            "south": {"uv": uv(HANDLE_FACE), "texture": t},
            "up": {"uv": uv(HANDLE_CAP), "texture": t},
            "down": {"uv": uv(HANDLE_CAP), "texture": t},
        },
    }
    return [plate_el, handle_el]


# Les transformations d'affichage de item/shield.json et item/shield_blocking.json (1.21.1)
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 2, -10], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
    "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
}
DISPLAY_BLOCKING = {
    "thirdperson_righthand": {"rotation": [45, 155, 0], "translation": [-3.49, 11, -2], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [45, 155, 0], "translation": [11.51, 7, 2.5], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
}


def model(display, blocking=False):
    body = {
        "gui_light": "front",
        "textures": {"shield": "emeraldweapons:item/arcencium_shield",
                     "particle": "emeraldweapons:item/arcencium_shield"},
        "elements": elements(),
        "display": display,
    }
    if not blocking:
        body["overrides"] = [{"predicate": {"blocking": 1},
                              "model": "emeraldweapons:item/arcencium_shield_blocking"}]
    return body


def write_json(path, payload):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(payload, fh, indent=2)
        fh.write("\n")
    print("ecrit %s" % os.path.relpath(path, ROOT))


def main():
    base = frame_base()
    frames = hue_frames(base, NFRAMES, CRACK_SAT, 0.35)
    sheet = Image.new("RGBA", (FRAME, FRAME * NFRAMES), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * FRAME))
    sheet.save(TEXTURE)
    with open(TEXTURE + ".mcmeta", "w", encoding="utf-8", newline="\n") as fh:
        fh.write('{"animation": {"frametime": %d, "interpolate": true}}\n' % FRAMETIME)
    print("ecrit %s (%d trames)" % (os.path.relpath(TEXTURE, ROOT), NFRAMES))
    write_json(os.path.join(MODELS, "arcencium_shield.json"), model(DISPLAY))
    write_json(os.path.join(MODELS, "arcencium_shield_blocking.json"), model(DISPLAY_BLOCKING, True))

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        s = 8
        board = Image.new("RGBA", (4 * (24 * s + 12) + 12, 44 * s + 24), (40, 40, 46, 255))
        for i, f in enumerate((0, 3, 6, 9)):
            face = frames[f].crop(FRONT).resize((24 * s, 44 * s), Image.NEAREST)
            board.paste(face, (12 + i * (24 * s + 12), 12), face)
        p = os.path.join(PREVIEW, "bouclier_face.png")
        board.save(p)
        back = frames[0].crop(BACK).resize((24 * s, 44 * s), Image.NEAREST)
        b = Image.new("RGBA", (24 * s + 24, 44 * s + 24), (40, 40, 46, 255))
        b.paste(back, (12, 12), back)
        b.save(os.path.join(PREVIEW, "bouclier_dos.png"))
        print("apercu %s" % p)


if __name__ == "__main__":
    main()
