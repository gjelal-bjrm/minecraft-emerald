#!/usr/bin/env python3
"""
Textures des runes.

Une rune n'est pas un artefact et ne doit pas lui ressembler. L'artefact part
d'une silhouette vanilla et garde une forme d'objet ; la rune est une PIERRE
GRAVEE -- une tablette carree, un signe dessus, et rien d'autre.

Trois lectures, dans cet ordre :
  1. la dalle : un carre de pierre sombre a bord biseaute, identique pour les
     douze, pour qu'on reconnaisse une rune avant de savoir laquelle ;
  2. le SIGNE, propre a chaque rune, trace en clair au centre ;
  3. la teinte de FAMILLE en fond, qui repond a la question qu'on se pose en
     premier -- arme, armure ou casque ?

La famille passe par le fond et non par le signe, parce que c'est la famille
qui decide si la rune sert a quelque chose : un signe qu'il faut identifier
pour savoir ou poser la pierre serait une devinette.

Usage :
    python tools/rune_texture.py [--preview]
"""

import io as _io
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons",
                        "textures", "item")
PREVIEW = os.path.join(ROOT, "tools", "preview")

SIZE = 16

# fond par famille, du plus chaud au plus froid
FAMILY_BG = {
    "weapon":    (0x3A, 0x1E, 0x1E),
    "armor":     (0x1C, 0x28, 0x3A),
}

STONE_EDGE = (0x0C, 0x0C, 0x12)
STONE_FACE = (0x1A, 0x1A, 0x22)

# famille -> (teinte du signe, trace)
#
# DEUX pierres : depuis que la rune porte plusieurs options, il n'y a plus une
# statistique a dessiner. Et c'est la bonne information -- ce qu'on veut savoir
# en voyant une pierre au sol, c'est si elle ira sur l'arme ou sur l'armure. La
# rarete, elle, se lit au nom colore.
#
# Le trace est une grille de 7x7 lue en caracteres : '#' allume le pixel. Sept
# et non huit : un signe impair a un centre, et un signe centre se lit mieux a
# cette taille qu'un signe qui penche d'un demi-pixel.
RUNES = [
    ("weapon", "weapon", 0xFF7A5C, [
        "..#....", ".##....", "###....", ".###...", "..###..", "...###.", "....##."]),
    ("armor", "armor", 0xB0C4FF, [
        ".#####.", "#.....#", "#.###.#", "#.#.#.#", "#.###.#", "#.....#", ".#####."]),
]


def draw(name, family, tint, glyph):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    bg = FAMILY_BG[family]

    # la dalle : 14x14 centree, bord biseaute
    for y in range(1, SIZE - 1):
        for x in range(1, SIZE - 1):
            edge = x in (1, SIZE - 2) or y in (1, SIZE - 2)
            base = STONE_EDGE if edge else STONE_FACE
            # le fond de famille se melange a la pierre plutot que de la
            # remplacer : une dalle franchement coloree ne ressemblerait plus a
            # de la pierre, et c'est la pierre qui dit « rune ».
            mix = tuple(int(base[i] * 0.55 + bg[i] * 0.45) for i in range(3))
            px[x, y] = mix + (255,)

    # une lumiere en haut a gauche, une ombre en bas a droite : sans elles la
    # dalle est un carre plat et se confond avec un bloc
    for i in range(2, SIZE - 2):
        px[i, 2] = tuple(min(255, c + 26) for c in px[i, 2][:3]) + (255,)
        px[2, i] = tuple(min(255, c + 18) for c in px[2, i][:3]) + (255,)
        px[i, SIZE - 3] = tuple(max(0, c - 20) for c in px[i, SIZE - 3][:3]) + (255,)
        px[SIZE - 3, i] = tuple(max(0, c - 14) for c in px[SIZE - 3, i][:3]) + (255,)

    # le signe, 7x7 au centre, avec une ombre portee d'un pixel
    colour = ((tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF)
    ox = oy = (SIZE - 7) // 2
    for gy, row in enumerate(glyph):
        for gx, ch in enumerate(row):
            if ch != "#":
                continue
            x, y = ox + gx, oy + gy
            if y + 1 < SIZE - 1:
                px[x, y + 1] = (8, 8, 12, 255)
    for gy, row in enumerate(glyph):
        for gx, ch in enumerate(row):
            if ch == "#":
                px[ox + gx, oy + gy] = colour + (255,)
    return img


def main():
    preview = "--preview" in sys.argv
    out = PREVIEW if preview else ITEM_DIR
    if not os.path.isdir(out):
        os.makedirs(out)
    for name, family, tint, glyph in RUNES:
        img = draw(name, family, tint, glyph)
        path = os.path.join(out, "rune_%s.png" % name)
        img.save(path)
        print("  rune_%-14s %-10s #%06X" % (name, family, tint))
    print("%d runes ecrites dans %s" % (len(RUNES), os.path.relpath(out, ROOT)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
