#!/usr/bin/env python3
"""
Texture de la Lanterne d'Arcencium.

Elle etait dessinee en 16x16, alors que le gabarit vanilla
`block/template_lantern` attend une planche de **16x48** : corps, chaine et
dessus y occupent des zones precises. Une image carree faisait donc lire des UV
au hasard -- d'ou une lanterne completement deformee en jeu.

On repart de la lanterne vanilla, dont le decoupage est juste par construction,
et on la reteinte : armature noircie comme le reste de l'equipement d'Arcencium,
coeur prismatique a la place de la flamme.

Usage :
    python tools/lantern_texture.py [--preview]
"""

import colorsys
import io as _io
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons",
                         "textures", "block")
PREVIEW = os.path.join(ROOT, "tools", "preview")

VANILLA_JAR = os.path.join(os.environ.get("USERPROFILE", ""), "curseforge",
                           "minecraft", "Install", "versions", "1.21.1", "1.21.1.jar")
SOURCE = "assets/minecraft/textures/block/lantern.png"

NFRAMES = 8

# La hauteur d'une IMAGE, a declarer explicitement.
#
# Sans elle, le jeu suppose des images carrees : notre planche de 16 par 384 se
# lisait donc comme vingt-quatre vignettes de 16 par 16, chacune un tiers de
# lanterne. Le bloc s'en tirait par chance, l'icone de l'inventaire non -- elle
# paraissait coupee en deux.
FRAME_H = 48
FRAMETIME = 4

# L'armature. Sombre, mais assez etalee pour que la forme de la lanterne se
# lise encore : une premiere version la comprimait entre 0x0A et 0x26, ce qui la
# transformait en silhouette noire sans relief.
FRAME = ((0x12, 0x13, 0x1A), (0x22, 0x24, 0x2E), (0x33, 0x36, 0x42), (0x45, 0x49, 0x58))

# La flamme vanilla est CHAUDE (orange, teinte proche de 0,08) tandis que
# l'armature est bleu-gris (teinte proche de 0,6). Une premiere version separait
# les deux par la saturation : l'armature, a 0,25-0,39, passait pour de la
# flamme et la lanterne entiere virait a la couleur.
def is_flame(hue, sat, val):
    """Vrai pour la flamme, faux pour l'armature.

    Deux criteres, et le second compte autant que le premier : la flamme est
    chaude et saturee sur ses bords, mais son coeur est presque BLANC
    (#FFFFD5, saturation 0,16). Ne tester que la saturation le rangeait dans
    l'armature -- on le peignait donc en noir, en plein milieu du halo, et sa
    luminance ecrasait au passage l'echelle de gris du metal.
    """
    return val > 0.85 or (sat > 0.45 and (hue < 0.14 or hue > 0.92))


def vanilla():
    with zipfile.ZipFile(VANILLA_JAR) as z:
        return Image.open(_io.BytesIO(z.read(SOURCE))).convert("RGBA")


def build(shift):
    src = vanilla()
    w, h = src.size
    px = src.load()
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dst = out.load()

    metal = [c for c in (px[x, y] for y in range(h) for x in range(w))
             if c[3] > 0 and not is_flame(*colorsys.rgb_to_hsv(*[v / 255 for v in c[:3]]))]
    lums = [((c[0] * 299 + c[1] * 587 + c[2] * 114) // 1000) for c in metal]
    lo, hi = (min(lums), max(lums)) if lums else (0, 1)
    span = max(1, hi - lo)

    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hue0, sat, val = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if is_flame(hue0, sat, val):
                # le coeur : il fait le tour du cercle des teintes, comme les
                # fissures de l'armure, pour signer la famille d'un coup d'oeil
                hue = (shift + (y / h) * 0.35) % 1.0
                # on conserve la gradation interne de la flamme : bord soutenu,
                # coeur presque blanc, comme dans l'original
                rr, gg, bb = colorsys.hsv_to_rgb(hue, 0.70 - 0.45 * val, 0.55 + 0.45 * val)
                dst[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), 255)
            else:
                t = (((r * 299 + g * 587 + b * 114) // 1000) - lo) / span
                idx = min(len(FRAME) - 1, int(t * len(FRAME)))
                dst[x, y] = FRAME[idx] + (255,)
    return out


def main():
    if not os.path.isfile(VANILLA_JAR):
        sys.exit("jar vanilla introuvable : %s" % VANILLA_JAR)
    os.makedirs(BLOCK_DIR, exist_ok=True)
    frames = [build(f / NFRAMES) for f in range(NFRAMES)]
    w, h = frames[0].size

    dest = os.path.join(BLOCK_DIR, "arcencium_lantern.png")
    sheet = Image.new("RGBA", (w, h * NFRAMES), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * h))
    sheet.save(dest)
    with open(dest + ".mcmeta", "w") as fh:
        # La hauteur d'image est INDISPENSABLE ici.
        #
        # Sans elle, le jeu suppose des vignettes CARREES : notre planche de
        # 16 par 384 se lisait comme vingt-quatre images de 16 par 16, chacune
        # un tiers de lanterne. Le bloc pose s'en tirait par chance, l'icone de
        # l'inventaire non -- elle paraissait coupee en deux.
        fh.write('{"animation": {"frametime": %d, "interpolate": true, '
                 '"width": %d, "height": %d}}\n' % (FRAMETIME, w, h))
    print("  arcencium_lantern  %dx%d, %d images (gabarit vanilla : %dx%d)"
          % (w, h * NFRAMES, NFRAMES, w, h))

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        s = 8
        board = Image.new("RGBA", (w * s * 4 + 30, h * s), (105, 105, 115, 255))
        for i, f in enumerate([0, 2, 4, 6]):
            r = frames[f].resize((w * s, h * s), Image.NEAREST)
            board.paste(r, (i * (w * s + 10), 0), r)
        p = os.path.join(PREVIEW, "lanterne.png")
        board.save(p)
        print("  apercu %s" % p)


if __name__ == "__main__":
    main()
