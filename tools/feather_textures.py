#!/usr/bin/env python3
"""
Les deux plumes de la specialisation, a partir de la plume vanilla.

On ne redessine rien : la plume de Minecraft est deja lisible et deja
reconnue. On lit sa luminance et on la reporte sur une teinte, comme pour
les pierres elementaires, avec le lisere sombre commun aux objets du mode.

  arcencium_feather.png  la Plume d'Arcencium : un degrade de prisme le long
                         de la plume (violet, cyan, or) -- le materiau.
  skin_feather.png       la Plume d'apparence : en niveaux de gris, teintee
                         en jeu par la couleur de l'apparence qu'elle porte.

Usage :
    python tools/feather_textures.py
"""

import io as _io
import json
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLIENT_JAR = os.path.join(os.environ.get("USERPROFILE", ""), ".gradle", "caches", "neoformruntime",
                          "artifacts", "minecraft_1.21.1_client.jar")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons", "textures", "item")
MODEL_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons", "models", "item")

PRISM = [(200, 130, 255), (120, 200, 255), (140, 255, 220), (255, 240, 150)]


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def prism(t):
    t = max(0.0, min(0.999, t))
    k = t * (len(PRISM) - 1)
    i = int(k)
    return lerp(PRISM[i], PRISM[i + 1], k - i)


def vanilla_feather():
    with zipfile.ZipFile(CLIENT_JAR) as z:
        return Image.open(_io.BytesIO(z.read("assets/minecraft/textures/item/feather.png"))).convert("RGBA")


def outline(img, color=(28, 18, 40, 255)):
    """Un lisere sombre autour de la silhouette."""
    w, h = img.size
    src = img.load()
    out = img.copy()
    dst = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] > 0:
                    dst[x, y] = color
                    break
    return out


def main():
    base = vanilla_feather()
    w, h = base.size
    px = base.load()
    # la plume vanilla est blanche, ombree de gris : la luminance porte tout
    arc = Image.new("RGBA", base.size, (0, 0, 0, 0))
    grey = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ap, gp = arc.load(), grey.load()
    ys = [y for y in range(h) for x in range(w) if px[x, y][3]]
    y0, y1 = min(ys), max(ys)
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            t = (y - y0) / max(1, (y1 - y0))          # du haut (pointe) au bas (calamus)
            col = prism(t)
            col = lerp((30, 20, 50), col, 0.35 + 0.65 * lum)
            ap[x, y] = col + (a,)
            v = int(60 + 195 * lum)
            gp[x, y] = (v, v, v, a)
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(MODEL_DIR, exist_ok=True)
    outline(arc).save(os.path.join(TEX_DIR, "arcencium_feather.png"))
    outline(grey, (40, 40, 40, 255)).save(os.path.join(TEX_DIR, "skin_feather.png"))
    for name in ("arcencium_feather", "skin_feather"):
        with open(os.path.join(MODEL_DIR, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"parent": "minecraft:item/generated",
                       "textures": {"layer0": "emeraldweapons:item/" + name}}, f, indent=2)
            f.write("\n")
    print("plumes ecrites :", TEX_DIR)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
