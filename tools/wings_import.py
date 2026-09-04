#!/usr/bin/env python3
"""
Importe les ailes peintes (ChatGPT) dans les textures du mod.

Entree : tools/wings_input/wing_<apparence>.png -- une aile DROITE, vue de
dos, racine en bas a gauche, idealement a fond transparent (voir
tools/prompts/ailes_specialisation.md).

Sortie : src/main/resources/assets/emeraldweapons/textures/wings/<apparence>.png,
512 x 512, RGBA. Si l'image n'a pas de transparence, le fond BLANC est
detoure : l'opacite est deduite de l'ecart au blanc, et les couleurs sont
"de-melangees" du blanc pour que les bords et le halo gardent leur teinte.
Un fond VERT pur (#00FF00) est detoure de la meme facon.

Usage :
    python tools/wings_import.py            # toutes les entrees presentes
    python tools/wings_import.py rubis      # une seule
"""

import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IN_DIR = os.path.join(ROOT, "tools", "wings_input")
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons", "textures", "wings")
SIZE = 1024        # les ailes recentes sont peintes en 1254 : on garde du detail


def has_transparency(im):
    lo, hi = im.split()[3].getextrema()
    return lo < 250


def key_out(im):
    """Detoure un fond uni blanc ou vert pur, en de-melangeant les couleurs."""
    px = im.load()
    w, h = im.size
    corner = px[0, 0]
    green = corner[1] > 200 and corner[0] < 80 and corner[2] < 80
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    op = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if green:
                # opacite : ce qui s'ecarte du vert pur
                a2 = min(255, int(max(255 - g + max(r, b), r, b) * 1.0))
                if a2 <= 6:
                    continue
                k = a2 / 255.0
                r2 = min(255, int(r / k)) if k > 0 else 0
                g2 = min(255, int(max(0, g - 255 * (1 - k)) / k)) if k > 0 else 0
                b2 = min(255, int(b / k)) if k > 0 else 0
            else:
                # fond blanc : opacite = ecart au blanc (le plus sombre des canaux)
                a2 = 255 - min(r, g, b)
                if a2 <= 6:
                    continue
                k = a2 / 255.0
                r2 = min(255, int(max(0, r - 255 * (1 - k)) / k))
                g2 = min(255, int(max(0, g - 255 * (1 - k)) / k))
                b2 = min(255, int(max(0, b - 255 * (1 - k)) / k))
            op[x, y] = (r2, g2, b2, a2)
    return out


def key_out_black(im, threshold=16):
    """
    Detoure un fond NOIR.

    LA LUMINANCE NE SUFFIT PAS. Sur un fond blanc, tout ce qui n'est pas blanc
    appartient au dessin ; sur un fond noir, une aile SOMBRE -- l'astrale est
    d'un bleu presque noir -- se confond avec le fond, et un simple seuil la
    troue de part en part.

    On procede donc par REGION : le fond est la zone sombre CONNEXE qui touche
    le bord de l'image. Tout ce qui est sombre mais enferme dans le dessin --
    l'ombre entre deux plumes, le creux d'une gemme -- reste opaque. Les bords
    recoivent ensuite un degrade sur un pixel, pris de la luminance, pour que
    la decoupe ne soit pas crenelee.
    """
    w, h = im.size
    px = im.load()

    def dark(x, y):
        r, g, b, _ = px[x, y]
        return max(r, g, b) <= threshold

    # remplissage par diffusion depuis les quatre bords
    background = bytearray(w * h)
    stack = []
    for x in range(w):
        for y in (0, h - 1):
            if dark(x, y) and not background[y * w + x]:
                background[y * w + x] = 1
                stack.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if dark(x, y) and not background[y * w + x]:
                background[y * w + x] = 1
                stack.append((x, y))
    while stack:
        x, y = stack.pop()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not background[ny * w + nx] and dark(nx, ny):
                background[ny * w + nx] = 1
                stack.append((nx, ny))

    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    op = out.load()
    for y in range(h):
        for x in range(w):
            if background[y * w + x]:
                continue
            r, g, b, _ = px[x, y]
            a = 255
            # un pixel du dessin qui touche le fond : on adoucit le bord
            if x > 0 and y > 0 and x < w - 1 and y < h - 1 and (
                    background[y * w + x - 1] or background[y * w + x + 1]
                    or background[(y - 1) * w + x] or background[(y + 1) * w + x]):
                a = max(96, min(255, int(max(r, g, b) * 1.6)))
            op[x, y] = (r, g, b, a)
    return out


def import_one(name):
    src = os.path.join(IN_DIR, "wing_%s.png" % name)
    if not os.path.exists(src):
        print("  absent :", src)
        return False
    im = Image.open(src).convert("RGBA")
    mode = "transparent"
    if not has_transparency(im):
        corner = im.getpixel((0, 0))
        if max(corner[0], corner[1], corner[2]) <= 24:
            im = key_out_black(im)
            mode = "detoure du noir"
        else:
            im = key_out(im)
            mode = "detoure"
    # carre, puis 512
    side = max(im.size)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.alpha_composite(im, ((side - im.width) // 2, (side - im.height) // 2))
    sq = sq.resize((SIZE, SIZE), Image.LANCZOS)
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, "%s.png" % name)
    sq.save(out)
    print("  %-20s %s -> %s (%s)" % (name, im.size, out, mode))
    return True


def main():
    names = sys.argv[1:]
    if not names:
        names = sorted(f[5:-4] for f in os.listdir(IN_DIR) if f.startswith("wing_") and f.endswith(".png"))
    if not names:
        print("rien a importer dans", IN_DIR)
        return 1
    n = sum(1 for name in names if import_one(name))
    print("%d aile(s) importee(s)" % n)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
