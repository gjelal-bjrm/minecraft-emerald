#!/usr/bin/env python3
"""
Integre une image de reference d'OBJET (generee par IA) comme texture d'item.

Difference avec texture_from_ref.py, qui traite les blocs : ici il n'y a pas de
motif a raccorder, mais un fond a detourer et une silhouette a preserver. Le
fond de reference est un magenta franc (#FF00FF) qu'aucune de nos palettes
n'emploie, ce qui rend le detourage sans ambiguite.

Chaine : detourage -> recadrage sur la silhouette -> reechantillonnage par vote
majoritaire (et non par moyenne, qui baverait les contours) -> animation
optionnelle par rotation de teinte des seuls pixels lumineux.

Usage :
    python tools/item_from_ref.py refs/prism_branch_ref.png prism_branch
    python tools/item_from_ref.py refs/prism_fiber_ref.png prism_fiber --static
    python tools/item_from_ref.py <ref> <nom> [--size 16] [--static] [--sat 0.45]
                                 [--pad 0.04]  marge autour de l'objet, pour
                                               harmoniser l'echelle entre pieces
"""

import colorsys
import os
import sys
from collections import Counter

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "emeraldweapons", "textures", "item")
PREVIEW = os.path.join(ROOT, "tools", "preview")

NFRAMES = 12
FRAMETIME = 3


def key_out(img, tol=90):
    """Retire le fond magenta. Tolerant : les generateurs bavent sur les bords."""
    img = img.convert("RGBA")
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if r > 150 and b > 150 and g < 120 and abs(r - b) < tol:
                px[x, y] = (0, 0, 0, 0)
    return img


def crop_to_content(img, pad_ratio=0.04):
    box = img.getbbox()
    if box is None:
        return img
    x0, y0, x1, y1 = box
    # on recadre sur un CARRE centre : un item ecrase par un recadrage
    # non uniforme perdrait ses proportions
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    half = max(x1 - x0, y1 - y0) / 2
    half *= 1 + pad_ratio
    side = int(round(half * 2))
    left = int(round(cx - half))
    top = int(round(cy - half))
    out = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    out.paste(img.crop((left, top, left + side, top + side)), (0, 0))
    return out


def downsample(img, size, vivid_min=0.25):
    """Vote majoritaire par cellule : garde les couleurs franches du pixel art.

    Une moyenne produirait des teintes intermediaires inexistantes dans la
    reference, et diluerait les veines lumineuses d'un pixel de large -- qui
    sont precisement ce qu'on veut garder.
    """
    w, h = img.size
    px = img.load()
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dst = out.load()
    for gy in range(size):
        for gx in range(size):
            x0, x1 = gx * w // size, (gx + 1) * w // size
            y0, y1 = gy * h // size, (gy + 1) * h // size
            opaque = []
            n = 0
            for y in range(y0, max(y0 + 1, y1)):
                for x in range(x0, max(x0 + 1, x1)):
                    p = px[x, y]
                    n += 1
                    if p[3] > 128:
                        opaque.append(p[:3])
            if not opaque or len(opaque) * 2 < n:
                continue                      # cellule majoritairement vide
            # Une veine lumineuse fait un pixel de large dans une reference qui
            # en compte plus de mille : une simple moyenne l'efface. On laisse
            # donc le vif l'emporter sur la matiere -- mais seulement s'il
            # occupe une part reelle de la cellule. Sans ce garde-fou, les
            # quelques pixels d'anticrenelage qui bordent chaque veine suffisent
            # a colorer toute la piece, et la branche vire au confetti.
            vivid = [c for c in opaque
                     if colorsys.rgb_to_hsv(*[v / 255 for v in c])[1] >= 0.55
                     and colorsys.rgb_to_hsv(*[v / 255 for v in c])[2] >= 0.50]
            if len(vivid) >= max(2, int(len(opaque) * vivid_min)):
                dst[gx, gy] = Counter(vivid).most_common(1)[0][0] + (255,)
            else:
                dst[gx, gy] = Counter(opaque).most_common(1)[0][0] + (255,)
    return out


def hue_frames(base, nframes, sat_min, val_min, sat_max=1.01):
    """Seules les veines lumineuses tournent : la matiere reste fixe."""
    w, h = base.size
    src = base.load()
    frames = []
    for f in range(nframes):
        shift = f / nframes
        out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        dst = out.load()
        for y in range(h):
            for x in range(w):
                r, g, b, a = src[x, y]
                if a == 0:
                    continue
                hh, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                if sat_min <= s <= sat_max and v >= val_min:
                    rr, gg, bb = colorsys.hsv_to_rgb((hh + shift) % 1.0, s, v)
                    dst[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)
                else:
                    dst[x, y] = (r, g, b, a)
        frames.append(out)
    return frames


def install(name, frames):
    os.makedirs(ITEM_DIR, exist_ok=True)
    dest = os.path.join(ITEM_DIR, name + ".png")
    w, h = frames[0].size
    mcmeta = dest + ".mcmeta"
    if len(frames) > 1:
        sheet = Image.new("RGBA", (w, h * len(frames)), (0, 0, 0, 0))
        for i, fr in enumerate(frames):
            sheet.paste(fr, (0, i * h))
        sheet.save(dest)
        with open(mcmeta, "w") as fh:
            fh.write('{"animation": {"frametime": %d, "interpolate": true}}' % FRAMETIME)
            fh.write("\n")
    else:
        frames[0].save(dest)
        if os.path.exists(mcmeta):
            os.remove(mcmeta)
    return dest


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) < 2:
        sys.exit(__doc__)
    ref, name = args[0], args[1]
    if not os.path.isabs(ref):
        ref = os.path.join(ROOT, "tools", ref) if ref.startswith("refs") else ref
    size = int(opt("--size", 16))
    sat_min = float(opt("--sat", 0.45))
    val_min = float(opt("--val", 0.35))
    static = "--static" in sys.argv

    img = key_out(Image.open(ref))
    img = crop_to_content(img, float(opt("--pad", 0.04)))
    small = downsample(img, size, float(opt("--vivid", 0.25)))
    filled = sum(1 for y in range(size) for x in range(size)
                 if small.load()[x, y][3] > 0)

    sat_max = float(opt("--sat-max", 1.01))
    frames = [small] if static else hue_frames(small, NFRAMES, sat_min, val_min, sat_max)
    dest = install(name, frames)

    os.makedirs(PREVIEW, exist_ok=True)
    s = 14
    board = Image.new("RGBA", (size * s * 4, size * s), (22, 22, 26, 255))
    for i, f in enumerate([0, 3, 6, 9][:len(frames)] if len(frames) > 1 else [0]):
        r = frames[f].resize((size * s, size * s), Image.NEAREST)
        board.paste(r, (i * size * s, 0), r)
    pv = os.path.join(PREVIEW, name + "_item.png")
    board.save(pv)

    print("%s : %dx%d, %d pixels remplis, %d image(s)"
          % (name, size, size, filled, len(frames)))
    print("  -> %s" % os.path.relpath(dest, ROOT))
    print("  -> %s" % os.path.relpath(pv, ROOT))


def opt(flag, default):
    if flag in sys.argv:
        i = sys.argv.index(flag)
        if i + 1 < len(sys.argv):
            return sys.argv[i + 1]
    return default


if __name__ == "__main__":
    main()
