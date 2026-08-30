#!/usr/bin/env python3
"""
Textures des Brassards d'Arcencium, pour un modele EN VOLUME.

Quatre silhouettes plates ont echoue avant celle-ci, et la derniere a montre
pourquoi : une image plate tenue en main ne pourra JAMAIS paraitre sanglee a
l'avant-bras. Minecraft affiche un objet ordinaire comme un panneau sortant du
poing -- c'est vrai de l'epee comme de la carotte. Aucun dessin ne corrige cela,
parce que ce n'est pas un probleme de dessin.

On ne fabrique donc plus une image mais un OBJET : des boites, placees le long
du bras, comme le bouclier ou le trident. Ce fichier ne produit plus une
silhouette mais les deux MATIERES que ces boites portent :

  vambrace_bracer_0..5 : le fourreau de cuir cercle d'acier. Six etats, un par
                         cran de Rage, ses cinq rivets s'allumant un a un.
  vambrace_blade       : la lame. Sombre, avec la veine irisee le long du
                         tranchant, animee sur douze images comme la lame de
                         l'epee et le cristal du sceptre.

Usage :
    python tools/vambrace_texture.py [--preview]
"""

import colorsys
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scepter_mockups import DARK, SHAFT, SHAFT_HI, GOLD_D, GOLD_M, GOLD_L  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "emeraldweapons", "textures", "item")
PREVIEW = os.path.join(ROOT, "tools", "preview")

T = 16                 # une face de boite : seize pixels suffisent
NFRAMES = 12
FRAMETIME = 3
LEATHER = (0x3A, 0x25, 0x0C)
LEATHER_HI = (0x50, 0x34, 0x12)

# Les cinq rivets, en colonne sur le fourreau.
RIVETS = [(4, 3), (7, 3), (10, 3), (5, 12), (10, 12)]


def bracer(lit):
    """
    Le fourreau : cuir tresse, cercles d'acier, cinq rivets.

    La meme image habille les six faces de la boite. C'est volontaire : un
    brassard est enroule, donc identique de tous les cotes, et cela evite six
    dessins qu'on ne verrait jamais ensemble.
    """
    img = Image.new("RGBA", (T, T), (0, 0, 0, 255))
    px = img.load()
    for y in range(T):
        for x in range(T):
            if y in (0, T - 1) or y in (5, 6, 9, 10):
                px[x, y] = GOLD_M + (255,) if y % 2 == 0 else GOLD_D + (255,)
            else:
                px[x, y] = (LEATHER_HI if (x + y) % 3 == 0 else LEATHER) + (255,)
    for i, (x, y) in enumerate(RIVETS):
        if i < lit:
            hue = (i * 0.15) % 1.0
            r, g, b = colorsys.hsv_to_rgb(hue, 0.66, 1.0)
            px[x, y] = (int(r * 255), int(g * 255), int(b * 255), 255)
            px[x, y + 1] = (int(r * 160), int(g * 160), int(b * 160), 255)
        else:
            px[x, y] = GOLD_D + (255,)
            px[x, y + 1] = (0x30, 0x22, 0x08, 255)
    return img


def blade(shift):
    """
    La lame : corps sombre, veine irisee le long du tranchant.

    Le tranchant est EN HAUT de l'image, et les faces du modele sont orientees
    pour qu'il tombe du bon cote. Teinter toute la lame donnerait un ruban --
    c'est le contraste sombre/veine qui fait lire du metal, comme sur l'epee.
    """
    img = Image.new("RGBA", (T, T), (0, 0, 0, 255))
    px = img.load()
    for y in range(T):
        for x in range(T):
            if y <= 1:
                hue = (shift + x / float(T) * 0.5) % 1.0
                r, g, b = colorsys.hsv_to_rgb(hue, 0.55, 1.0 if y == 0 else 0.7)
                px[x, y] = (int(r * 255), int(g * 255), int(b * 255), 255)
            elif y <= 3:
                px[x, y] = SHAFT_HI + (255,)
            elif y <= 8:
                px[x, y] = SHAFT + (255,)
            else:
                px[x, y] = DARK + (255,)
    # quelques eclats dans la masse, pour qu'elle ne soit pas un aplat
    for k in range(6):
        x = (k * 5 + 3) % T
        y = 5 + (k * 3) % 9
        px[x, y] = GOLD_D + (255,)
    return img


def write(name, frames):
    sheet = Image.new("RGBA", (T, T * len(frames)))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * T))
    dest = os.path.join(ITEM_DIR, name + ".png")
    sheet.save(dest)
    meta = dest + ".mcmeta"
    if len(frames) > 1:
        with open(meta, "w") as fh:
            fh.write('{"animation": {"frametime": %d, "interpolate": true}}' % FRAMETIME)
    elif os.path.exists(meta):
        os.remove(meta)
    print("  %s (%dx%d, %d image%s)" % (name, T, T, len(frames),
                                        "s" if len(frames) > 1 else ""))


def main():
    os.makedirs(ITEM_DIR, exist_ok=True)
    for lit in range(6):
        write("vambrace_bracer_%d" % lit, [bracer(lit)])
    write("vambrace_blade", [blade(f / NFRAMES) for f in range(NFRAMES)])

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        strip = Image.new("RGBA", (T * 7, T))
        for lit in range(6):
            strip.paste(bracer(lit), (lit * T, 0))
        strip.paste(blade(0.0), (6 * T, 0))
        out = os.path.join(PREVIEW, "vambrace_materials.png")
        strip.resize((T * 7 * 10, T * 10), Image.NEAREST).save(out)
        print("apercu : %s" % os.path.relpath(out, ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
