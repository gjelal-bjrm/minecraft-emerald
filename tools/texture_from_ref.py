#!/usr/bin/env python3
"""
Integre une image de reference (dessinee ou generee par IA) comme texture
de bloc du mod.

Meme chaine que pour les buches de Prisme : detection de la grille de pixels
native, re-echantillonnage, animation optionnelle par rotation de teinte des
seuls pixels satures, ecriture de la spritesheet + .mcmeta dans le mod.

Options utiles :
  --wrap    fond croise sur les bords pour que la texture se raccorde. A
            utiliser pour les murs : un generateur d'images produit rarement
            un motif periodique.
  --static  pas d'animation (une seule frame).
  --blur    moyenne les cases au lieu de lire leur centre. Pour une source qui
            n'est PAS du pixel art (photo, rendu) ; sur du pixel art la moyenne
            delave les traits fins.
  --check   ecrit un apercu tuile 3x3 dans tools/preview/blocks/ pour juger
            le raccord d'un coup d'oeil.

Usage :
    python tools/texture_from_ref.py <image> <nom_du_bloc> [options]
    python tools/texture_from_ref.py refs/arc.png arcencium_bricks --wrap --check
    python tools/texture_from_ref.py refs/corr.png corrupted_bricks --wrap --size 32
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


def detect_grid(img, cands=(16, 24, 32, 48, 64, 96, 128, 192, 256)):
    """Resolution native d'une image de pixel art agrandie.

    On cherche la PLUS FINE grille dont chaque cellule est encore uniforme.
    Une version precedente prenait le maximum d'un score d'aretes : comme les
    aretes d'une grille 16 coincident avec celles des grilles 32/64/128, elle
    repondait toujours 16 et ecrasait le detail."""
    im = img.convert("RGB")
    w, h = im.size
    px = im.load()
    best = cands[-1]
    for n in cands:
        if w % n and abs(w / n - round(w / n)) > 0.02:
            pass                        # cellule non entiere : on tolere
        cw = w / n
        ch = h / n
        var = 0.0
        samples = 0
        for gy in range(1, n, max(1, n // 12)):
            for gx in range(1, n, max(1, n // 12)):
                x0, y0 = int(gx * cw), int(gy * ch)
                x1, y1 = int((gx + 1) * cw) - 1, int((gy + 1) * ch) - 1
                if x1 <= x0 or y1 <= y0:
                    continue
                a = px[x0 + 1, y0 + 1]
                for q in ((x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
                    b = px[q]
                    var += sum(abs(a[i] - b[i]) for i in range(3))
                    samples += 3
        if samples and var / samples < 2.0:      # cellules uniformes
            best = n
            break
    return best
def wrap_edges(img, margin):
    """Fond croise avec le bord oppose : rend le raccord invisible."""
    w, h = img.size
    src = img.load()
    out = img.copy()
    dst = out.load()
    for y in range(h):
        for x in range(w):
            fx, fy = min(x, w - 1 - x), min(y, h - 1 - y)
            if fx >= margin and fy >= margin:
                continue
            tx = (margin - fx) / (2.0 * margin) if fx < margin else 0.0
            ty = (margin - fy) / (2.0 * margin) if fy < margin else 0.0
            r, g, b, a = src[x, y]
            if tx > 0:
                r2, g2, b2, _ = src[w - 1 - x, y]
                r, g, b = r + (r2 - r) * tx, g + (g2 - g) * tx, b + (b2 - b) * tx
            if ty > 0:
                r2, g2, b2, _ = src[x, h - 1 - y]
                r, g, b = r + (r2 - r) * ty, g + (g2 - g) * ty, b + (b2 - b) * ty
            dst[x, y] = (int(r), int(g), int(b), a)
    return out


def hue_rotate_frames(base, nframes=NFRAMES, sat_min=0.28, val_min=0.20):
    """Seuls les pixels satures tournent sur la roue des teintes : les
    veines vibrent, la pierre ne bouge pas."""
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
                if s >= sat_min and v > val_min:
                    rr, gg, bb = colorsys.hsv_to_rgb((hh + shift) % 1.0, s, v)
                    dst[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)
                else:
                    dst[x, y] = (r, g, b, a)
        frames.append(out)
    return frames


def tile3(img):
    w = img.width
    t = Image.new("RGBA", (w * 3, w * 3))
    for a in range(3):
        for b in range(3):
            t.paste(img, (a * w, b * w))
    return t


def install(name, frames, check=False):
    os.makedirs(BLOCK_DIR, exist_ok=True)
    os.makedirs(PV_DIR, exist_ok=True)
    w = frames[0].width
    dest = os.path.join(BLOCK_DIR, name + ".png")
    if len(frames) > 1:
        sheet = Image.new("RGBA", (w, w * len(frames)))
        for i, fr in enumerate(frames):
            sheet.paste(fr, (0, i * w))
        sheet.save(dest)
        with open(dest + ".mcmeta", "w") as fh:
            fh.write('{"animation": {"frametime": %d, "interpolate": true}}' % FRAMETIME)
            fh.write(chr(10))
    else:
        frames[0].save(dest)
        mc = dest + ".mcmeta"
        if os.path.exists(mc):
            os.remove(mc)
    frames[0].resize((512, 512), Image.NEAREST).save(
        os.path.join(PV_DIR, name + "_ref_x.png"))
    if len(frames) > 1:
        gif = [fr.resize((256, 256), Image.NEAREST).convert("P", palette=Image.ADAPTIVE)
               for fr in frames]
        gif[0].save(os.path.join(PV_DIR, name + "_ref_anim.gif"), save_all=True,
                    append_images=gif[1:], duration=FRAMETIME * 50, loop=0)
    if check:
        tile3(frames[0]).resize((768, 768), Image.NEAREST).save(
            os.path.join(PV_DIR, name + "_ref_tiling.png"))
    print("Installe : %s (%dx%d, %d frame%s)"
          % (os.path.relpath(dest, ROOT), w, w, len(frames),
             "s" if len(frames) > 1 else ""))


def cell_centres(img, n):
    """
    Reechantillonne en prenant la couleur au CENTRE de chaque case.

    La moyenne (BOX) est le bon choix pour une photo ou un rendu, mais elle
    est destructrice sur du pixel art dont la grille ne divise pas la largeur :
    1254 / 64 = 19,59, donc chaque case de sortie mord sur sa voisine et une
    veine d'un pixel de large se dilue dans la pierre noire qui l'entoure. On
    perdait ainsi la moitie des pixels vifs.

    Au centre, on relit la couleur d'origine telle qu'elle a ete posee.
    """
    src = img.convert("RGBA")
    w, h = src.size
    px = src.load()
    out = Image.new("RGBA", (n, n))
    dst = out.load()
    for j in range(n):
        y = min(h - 1, int((j + 0.5) * h / n))
        for i in range(n):
            x = min(w - 1, int((i + 0.5) * w / n))
            dst[i, j] = px[x, y]
    return out


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) < 2:
        print(__doc__)
        return 1
    src, name = args[0], args[1]
    if not os.path.exists(src):
        alt = os.path.join(REFS, os.path.basename(src))
        src = alt if os.path.exists(alt) else src
    size = None
    if "--size" in sys.argv:
        size = int(sys.argv[sys.argv.index("--size") + 1])

    img = Image.open(src).convert("RGBA")
    n = size or detect_grid(img)
    print("reference %s -> grille %d cellules" % (str(img.size), n))
    if "--blur" in sys.argv:
        small = img.resize((n, n), Image.BOX)
    else:
        small = cell_centres(img, n)
    if "--wrap" in sys.argv:
        small = wrap_edges(small, max(2, n // 16))
    frames = [small] if "--static" in sys.argv else hue_rotate_frames(small)
    install(name, frames, check="--check" in sys.argv)
    return 0


if __name__ == "__main__":
    sys.exit(main())
