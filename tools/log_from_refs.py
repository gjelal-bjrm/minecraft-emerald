#!/usr/bin/env python3
"""
Textures de buche de Prisme a partir des images de reference de l'auteur
(tools/refs/prism_log_top_ref.png, prism_log_side_ref.png).

Les references sont du pixel art agrandi (~1254 px). On :
  1. detecte la grille de pixels native (periode dominante des aretes) ;
  2. re-echantillonne a cette resolution (BOX = moyenne par cellule) ;
  3. anime en faisant tourner la TEINTE des seuls pixels satures (les
     eclairs), le bois restant fixe -> les couleurs changent, 8 frames ;
  4. ecrit les spritesheets + .mcmeta dans le mod.

Usage :
    python tools/log_from_refs.py            # detecte la grille
    python tools/log_from_refs.py --size 64  # force une resolution
"""

import colorsys
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REFS = os.path.join(ROOT, "tools", "refs")
BLOCK_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                         "emeraldweapons", "textures", "block")
PV_DIR = os.path.join(ROOT, "tools", "preview", "blocks")
NFRAMES = 8
FRAMETIME = 6


def detect_grid(img):
    """Periode dominante des aretes verticales -> taille de cellule en px."""
    g = img.convert("L")
    w, h = g.size
    px = g.load()
    col = [0.0] * w
    for y in range(0, h, 4):
        for x in range(1, w):
            col[x] += abs(px[x, y] - px[x - 1, y])
    best, best_score = None, -1.0
    for n in range(32, 257, 8):               # candidats : 32..256 cellules
        cell = w / n
        score = 0.0
        for k in range(1, n):
            x = int(round(k * cell))
            if 0 < x < w:
                score += col[x]
        score /= (n - 1)
        if score > best_score:
            best, best_score = n, score
    return best


def downsample(img, n):
    return img.convert("RGBA").resize((n, n), Image.BOX)


def hue_rotate_frames(base, nframes=NFRAMES, sat_min=0.28):
    """Frames ou seuls les pixels satures tournent sur la roue des teintes."""
    w, h = base.size
    src = base.load()
    frames = []
    for f in range(nframes):
        shift = f / nframes
        out = Image.new("RGBA", (w, h))
        dst = out.load()
        for y in range(h):
            for x in range(w):
                r, g, b, a = src[x, y]
                hh, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                if s >= sat_min and v > 0.25:
                    # les eclairs : teinte qui tourne, saturation/valeur gardees
                    rr, gg, bb = colorsys.hsv_to_rgb((hh + shift) % 1.0, s, v)
                    dst[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)
                else:
                    dst[x, y] = (r, g, b, a)
        frames.append(out)
    return frames


def wrap_edges(img, margin):
    """Fond croise sur les bords pour que la texture se raccorde (ecorce)."""
    w, h = img.size
    src = img.load()
    out = img.copy()
    dst = out.load()
    for y in range(h):
        for x in range(w):
            fx = min(x, w - 1 - x)
            fy = min(y, h - 1 - y)
            if fx >= margin and fy >= margin:
                continue
            # melange avec le pixel du bord oppose, plus on est pres du bord
            tx = (margin - fx) / (2.0 * margin) if fx < margin else 0.0
            ty = (margin - fy) / (2.0 * margin) if fy < margin else 0.0
            ox, oy = (w - 1 - x), (h - 1 - y)
            r, g, b, a = src[x, y]
            if tx > 0:
                r2, g2, b2, _ = src[ox, y]
                r, g, b = r + (r2 - r) * tx, g + (g2 - g) * tx, b + (b2 - b) * tx
            if ty > 0:
                r2, g2, b2, _ = src[x, oy]
                r, g, b = r + (r2 - r) * ty, g + (g2 - g) * ty, b + (b2 - b) * ty
            dst[x, y] = (int(r), int(g), int(b), a)
    return out


def write_sheet(name, frames):
    w = frames[0].width
    sheet = Image.new("RGBA", (w, w * len(frames)))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * w))
    dest = os.path.join(BLOCK_DIR, name + ".png")
    sheet.save(dest)
    with open(dest + ".mcmeta", "w") as fh:
        fh.write('{"animation": {"frametime": %d, "interpolate": true}}' % FRAMETIME)
        fh.write(chr(10))
    # apercus : frame 0 agrandie + gif
    os.makedirs(PV_DIR, exist_ok=True)
    frames[0].resize((512, 512), Image.NEAREST).save(os.path.join(PV_DIR, name + "_ref_x.png"))
    gif = [fr.resize((256, 256), Image.NEAREST).convert("P", palette=Image.ADAPTIVE)
           for fr in frames]
    gif[0].save(os.path.join(PV_DIR, name + "_ref_anim.gif"), save_all=True,
                append_images=gif[1:], duration=FRAMETIME * 50, loop=0)
    print("Installe : %s (%dx%d, %d frames)" % (os.path.relpath(dest, ROOT), w, w, len(frames)))


def main():
    size = None
    if "--size" in sys.argv:
        size = int(sys.argv[sys.argv.index("--size") + 1])
    for name, ref, wrap in (("prism_log_top", "prism_log_top_ref.png", False),
                            ("prism_log", "prism_log_side_ref.png", True)):
        img = Image.open(os.path.join(REFS, ref)).convert("RGBA")
        n = size or detect_grid(img)
        print("%s : reference %s, grille detectee %d cellules" % (name, img.size, n))
        small = downsample(img, n)
        if wrap:
            small = wrap_edges(small, max(2, n // 24))
        write_sheet(name, hue_rotate_frames(small))


if __name__ == "__main__":
    main()
