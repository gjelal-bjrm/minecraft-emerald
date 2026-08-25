#!/usr/bin/env python3
"""
Interface de l'Etabli de Sertissage.

Elle part de l'ecran de l'enclume vanilla : sa disposition est exactement la
notre -- deux entrees cote a cote et un resultat a droite -- et un joueur la
reconnait sans avoir rien a apprendre. On en retire le champ de renommage,
inutile ici, et on refroidit legerement le panneau pour qu'il appartienne a la
palette du mod sans devenir illisible.

Usage :
    python tools/gui_texture.py [--preview]
"""

import io as _io
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUI_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons",
                       "textures", "gui", "container")
PREVIEW = os.path.join(ROOT, "tools", "preview")

VANILLA_JAR = os.path.join(os.environ.get("USERPROFILE", ""), "curseforge",
                           "minecraft", "Install", "versions", "1.21.1", "1.21.1.jar")
SOURCE = "assets/minecraft/textures/gui/container/anvil.png"

# Toute la bande haute de l'enclume est effacee : elle porte son marteau et son
# champ de renommage, dont aucun n'a de sens ici. Le titre etant dessine par le
# code par-dessus, on peut la vider entierement sans rien perdre.
TOP_STRIP = (6, 4, 170, 42)
# Un pixel de panneau nu, dont on reprend la couleur pour reboucher.
PANEL_SAMPLE = (166, 40)


def cool(img):
    """Refroidit le gris vanilla d'un souffle, sans toucher au contraste.

    Un panneau franchement colore fatigue et gene la lecture des objets ; on se
    contente donc de tirer les gris vers le bleu-vert de la palette.
    """
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if abs(r - g) < 24 and abs(g - b) < 24:          # un gris
                px[x, y] = (max(0, r - 10), g, min(255, b + 6), a)
    return img


def main():
    if not os.path.isfile(VANILLA_JAR):
        sys.exit("jar vanilla introuvable : %s" % VANILLA_JAR)
    with zipfile.ZipFile(VANILLA_JAR) as z:
        img = Image.open(_io.BytesIO(z.read(SOURCE))).convert("RGBA")

    panel = img.getpixel(PANEL_SAMPLE)
    px = img.load()
    x0, y0, x1, y1 = TOP_STRIP
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = panel
    img = cool(img)

    os.makedirs(GUI_DIR, exist_ok=True)
    dest = os.path.join(GUI_DIR, "socket_bench.png")
    img.save(dest)
    print("  socket_bench.png  %dx%d" % img.size)
    print("  bande haute effacee (marteau et champ de renommage), panneau refroidi")

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        p = os.path.join(PREVIEW, "etabli_gui.png")
        img.crop((0, 0, 176, 166)).resize((176 * 3, 166 * 3), Image.NEAREST).save(p)
        print("  apercu %s" % p)


if __name__ == "__main__":
    main()
