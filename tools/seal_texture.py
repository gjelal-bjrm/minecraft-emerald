"""Dessine le Sceau du Tombeau : eteint, puis eveille.

Un sceau doit se LIRE comme une serrure, pas comme un bloc de decor : c'est lui
qui retient l'ancre du sommet, et le joueur doit comprendre en le voyant qu'il
y a quelque chose a faire. D'ou l'anneau grave et le disque central, qui
n'existent nulle part ailleurs dans le mode.

Eteint, tout est sourd : la pierre est noire et le disque a peine plus clair.
Eveille, le disque fait le tour des teintes et l'anneau s'allume avec lui, ce
qui distingue d'un coup d'oeil un sceau fait d'un sceau qui attend.
"""

import colorsys
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets",
                   "emeraldweapons", "textures", "block")

DARK = (0x11, 0x10, 0x18)
STONE = (0x26, 0x24, 0x33)
EDGE = (0x3E, 0x3B, 0x52)

NFRAMES = 8
FRAMETIME = 4


def prism(t, sat=0.8, val=1.0):
    r, g, b = colorsys.hsv_to_rgb(t % 1.0, sat, val)
    return int(r * 255), int(g * 255), int(b * 255)


def face(lit, shift=0.0):
    img = Image.new("RGBA", (16, 16), STONE + (255,))
    px = img.load()
    for y in range(16):
        for x in range(16):
            dx = x - 7.5
            dy = y - 7.5
            dist = (dx * dx + dy * dy) ** 0.5
            if dist > 7.2:
                px[x, y] = DARK + (255,)              # le cadre
            elif dist > 6.2:
                px[x, y] = EDGE + (255,)              # le chanfrein
            elif dist > 4.6:
                # l'anneau grave : c'est lui qui dit « mecanisme »
                spoke = (int((x + y) * 1.7) % 3) == 0
                if lit and spoke:
                    px[x, y] = prism(shift + dist * 0.05, 0.7, 0.95) + (255,)
                else:
                    px[x, y] = (DARK if spoke else EDGE) + (255,)
            elif dist > 3.4:
                px[x, y] = DARK + (255,)              # la gorge
            else:
                # le disque central
                if lit:
                    px[x, y] = prism(shift + (x + y) / 40.0, 0.85, 1.0) + (255,)
                else:
                    px[x, y] = (0x1B, 0x1A, 0x24, 255)
    return img


def save(img, name, animated=False):
    path = os.path.join(OUT, name + ".png")
    img.save(path)
    print("ecrit %s" % os.path.relpath(path, ROOT))
    if animated:
        with open(path + ".mcmeta", "w", encoding="utf-8", newline="\n") as fh:
            fh.write('{"animation": {"frametime": %d, "interpolate": true, '
                     '"width": 16, "height": 16}}\n' % FRAMETIME)


def main():
    os.makedirs(OUT, exist_ok=True)
    save(face(False), "tomb_seal")

    frames = [face(True, f / NFRAMES) for f in range(NFRAMES)]
    sheet = Image.new("RGBA", (16, 16 * NFRAMES))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * 16))
    save(sheet, "tomb_seal_lit", animated=True)


if __name__ == "__main__":
    main()
