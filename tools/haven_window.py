"""La vitre de Jak 3 (cahier §101) : ses textures, tirees des metaux des portes de Jak 3, et ses
modeles de bloc.

« Style futuriste comme les portes, bordure tres legere, vitre au centre » (le joueur). Les
metaux viennent de l'atlas des portes cuit par tools/jak_door.py -- les textures du jeu :
  - LE CADRE : l'acier brosse clair de la porte du Hip Hog, ramene a seize pixels ;
  - L'IRIS : l'acier sombre des panneaux du sas du port ; son lisere, l'acier clair ;
  - LE VERRE : un bleu pale et translucide, deux reflets en biais.

Sorties : textures/block/haven_window_glass.png, haven_window_frame.png ; textures/entity/
haven_window_iris.png (16 x 32 : l'acier en haut, le lisere en bas, pour HavenWindowRenderer) ;
textures/item/haven_window.png ; le modele du verre, les quatre bouts de cadre (une vitre en X ;
l'etat de bloc les tourne pour une vitre en Z), l'etat de bloc a morceaux, le modele d'objet, et
la table de butin. Une planche de controle : build/jak/vitre-planche.png.

    python tools/haven_window.py
"""

import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons")
ATLAS = os.path.join(ASSETS, "textures", "entity", "jak_doors", "atlas.png")
BOARD = os.path.join(ROOT, "build", "jak", "vitre-planche.png")

# dans l'atlas des portes (1024 x 1024) : l'acier clair brosse, et l'interieur d'un panneau du sas -- son
# chanfrein sombre, repete a chaque bloc, faisait de l'iris ferme une grille de carreaux (photo)
STEEL_LIGHT = (0, 775, 130, 845)
STEEL_DARK = (534, 529, 636, 631)
GLASS = (168, 214, 236)


def metal(atlas, box, gain):
    """Un metal de l'atlas ramene a seize pixels, eclairci ou assombri."""
    im = atlas.crop(box).convert("RGB").resize((16, 16), Image.LANCZOS)
    px = im.load()
    for y in range(16):
        for x in range(16):
            r, g, b = px[x, y]
            px[x, y] = tuple(max(0, min(255, int(c * gain))) for c in (r, g, b))
    return im.convert("RGBA")


def flatten(im):
    """Ote le degrade d'un metal, colonne par colonne puis ligne par ligne : il garde son brossage et se
    repete d'un bloc a l'autre sans couture (l'ombre du panneau du sas, a gauche, rayait l'iris ferme)."""
    px = im.load()
    for axis in (0, 1):
        means = []
        for i in range(16):
            cells = [px[i, j] if axis == 0 else px[j, i] for j in range(16)]
            means.append(sum(sum(c[:3]) for c in cells) / (3.0 * 16))
        overall = sum(means) / 16.0
        for i in range(16):
            k = overall / max(1.0, means[i])
            for j in range(16):
                x, y = (i, j) if axis == 0 else (j, i)
                r, g, b, a = px[x, y]
                px[x, y] = (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), a)
    return im


def glass():
    """Le verre : bleu pale, translucide, deux reflets en biais plus clairs."""
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = (x + y) % 16
            streak = d in (3, 4) or d == 9
            alpha = 150 if streak else 72
            light = 1.18 if streak else 1.0
            px[x, y] = tuple(min(255, int(c * light)) for c in GLASS) + (alpha,)
    return im


def frame(steel):
    """Le cadre : l'acier clair, une arete plus sombre sur le pourtour pour qu'il se detache."""
    im = steel.copy()
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                r, g, b, a = px[x, y]
                px[x, y] = (int(r * 0.72), int(g * 0.72), int(b * 0.72), 255)
    return im


def iris(dark, rim):
    """L'iris : l'acier sombre en haut (lignes 0-15), le lisere clair en bas (16-31)."""
    im = Image.new("RGBA", (16, 32))
    im.paste(dark, (0, 0))
    im.paste(rim, (0, 16))
    return im


def icon(glass_im, frame_im):
    """L'objet : une vitre et son cadre de deux pixels."""
    im = glass_im.copy()
    fp = frame_im.load()
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x < 2 or x > 13 or y < 2 or y > 13:
                px[x, y] = fp[x, y]
    return im


def save(im, *parts):
    path = os.path.join(ASSETS, "textures", *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    print("  " + os.path.relpath(path, ROOT))


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("  " + os.path.relpath(path, ROOT))


def faces(texture, uvs, cull):
    """Les six faces d'une boite ; uvs par face, cull : les faces tranchees contre une voisine reliee."""
    out = {}
    for face, uv in uvs.items():
        out[face] = {"uv": uv, "texture": texture}
        if face in cull:
            out[face]["cullface"] = face
    return out


def models():
    block = os.path.join(ASSETS, "models", "block")
    glass_tex = "emeraldweapons:block/haven_window_glass"
    frame_tex = "emeraldweapons:block/haven_window_frame"
    # le verre : un pixel d'epaisseur au milieu du bloc, le long de x
    write_json(os.path.join(block, "haven_window_glass.json"), {
        "render_type": "minecraft:translucent",
        "textures": {"glass": glass_tex, "particle": frame_tex},
        "elements": [{
            "from": [0, 0, 7.5], "to": [16, 16, 8.5],
            "faces": faces("#glass", {
                "north": [0, 0, 16, 16], "south": [0, 0, 16, 16],
                "east": [7.5, 0, 8.5, 16], "west": [7.5, 0, 8.5, 16],
                "up": [0, 7.5, 16, 8.5], "down": [0, 7.5, 16, 8.5]},
                ("east", "west", "up", "down")),
        }],
    })
    # les bouts de cadre : deux pixels de large, trois d'epaisseur, sur les bords sans voisine
    strips = {
        "up": ([0, 14, 6.5], [16, 16, 9.5]),
        "down": ([0, 0, 6.5], [16, 2, 9.5]),
        "left": ([0, 0, 6.5], [2, 16, 9.5]),
        "right": ([14, 0, 6.5], [16, 16, 9.5]),
    }
    for name, (lo, hi) in strips.items():
        x0, y0, z0 = lo
        x1, y1, z1 = hi
        uvs = {
            "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
            "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0], "west": [z0, 16 - y1, z1, 16 - y0],
            "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0],
        }
        # tranches : aux deux bouts, la ou la fenetre continue (la voisine reliee a son cadre) ; et la face
        # du bord, contre un bloc plein (une vitre posee dans un mur)
        cull = {"up": ("east", "west", "up"), "down": ("east", "west", "down"),
                "left": ("up", "down", "west"), "right": ("up", "down", "east")}[name]
        write_json(os.path.join(block, "haven_window_frame_%s.json" % name), {
            "render_type": "minecraft:translucent",
            "textures": {"frame": frame_tex, "particle": frame_tex},
            "elements": [{"from": lo, "to": hi, "faces": faces("#frame", uvs, cull)}],
        })
    # l'etat de bloc a morceaux : une vitre en z est la meme, tournee d'un quart de tour
    parts = []
    for axis, extra in (("x", {}), ("z", {"y": 90})):
        parts.append({"when": {"axis": axis}, "apply": dict({"model": "emeraldweapons:block/haven_window_glass"}, **extra)})
        for name in ("up", "down", "left", "right"):
            parts.append({"when": {"axis": axis, name: "false"},
                          "apply": dict({"model": "emeraldweapons:block/haven_window_frame_%s" % name}, **extra)})
    write_json(os.path.join(ASSETS, "blockstates", "haven_window.json"), {"multipart": parts})
    write_json(os.path.join(ASSETS, "models", "item", "haven_window.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "emeraldweapons:item/haven_window"},
    })
    write_json(os.path.join(DATA, "loot_table", "blocks", "haven_window.json"), {
        "type": "minecraft:block",
        "pools": [{
            "bonus_rolls": 0.0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": "emeraldweapons:haven_window"}],
            "rolls": 1.0,
        }],
        "random_sequence": "emeraldweapons:blocks/haven_window",
    })


def main():
    atlas = Image.open(ATLAS).convert("RGBA")
    light = metal(atlas, STEEL_LIGHT, 1.0)
    dark = flatten(metal(atlas, STEEL_DARK, 0.85))
    rim = metal(atlas, STEEL_LIGHT, 1.12)
    glass_im = glass()
    frame_im = frame(light)
    print("textures :")
    save(glass_im, "block", "haven_window_glass.png")
    save(frame_im, "block", "haven_window_frame.png")
    save(iris(dark, rim), "entity", "haven_window_iris.png")
    save(icon(glass_im, frame_im), "item", "haven_window.png")
    print("modeles :")
    models()
    # la planche de controle : chaque texture en grand, sur un fond clair puis sombre
    board = Image.new("RGBA", (4 * 136, 2 * 136), (0, 0, 0, 0))
    for row, bg in enumerate(((225, 225, 225, 255), (40, 44, 52, 255))):
        for col, im in enumerate((glass_im, frame_im, iris(dark, rim).crop((0, 0, 16, 16)), icon(glass_im, frame_im))):
            tile = Image.new("RGBA", (128, 128), bg)
            big = im.resize((128, 128), Image.NEAREST)
            tile.alpha_composite(big)
            board.paste(tile, (col * 136 + 4, row * 136 + 4))
    os.makedirs(os.path.dirname(BOARD), exist_ok=True)
    board.save(BOARD)
    print("planche : " + os.path.relpath(BOARD, ROOT))


if __name__ == "__main__":
    main()
