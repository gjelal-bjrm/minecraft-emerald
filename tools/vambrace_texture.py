#!/usr/bin/env python3
"""
Texture des Lames d'Arcencium -- la scie.

Cinq formes ont echoue avant celle-ci, dont un modele en VOLUME. Ce dernier
echec est le plus instructif : voulant une lame sanglee a l'avant-bras, j'ai
bati des boites et les ai posees dans la main. Le resultat occupe un quart de
l'ecran en premiere personne et ne ressemble a rien -- un objet en volume tenu
au poing est enorme, parce que rien ne le met a l'echelle.

La reference tranche le debat : cette lame-la se TIENT, par une poignee courte
a l'arriere, et son tranchant est DENTE. C'est la denture qui fait tout le
caractere -- une lame lisse, meme large, reste une epee ; une lame a dents est
un outil de boucherie, et cela se voit a seize pixels.

On revient donc a une image plate, comme les trois autres armes du mod, mais
dessinee autour de la bonne idee :

  - une masse qui remplit la tuile, non un trait sur la diagonale ;
  - un dos droit qui se termine en croc, comme sur la reference ;
  - un ventre en SCIE, sept dents, qui est la ligne qu'on regarde ;
  - des veines irisees dans l'acier, qui remplacent les runes rouges.

Six etats, un par cran de Rage : les cinq dents du talon s'allument une a une.
Chaque etat est anime sur douze images -- les veines et le fil font tourner
leur teinte, comme la lame de l'epee, la corde de l'arc et le cristal du
sceptre. L'acier, lui, ne bouge pas.

Usage :
    python tools/vambrace_texture.py [--preview]
"""

import colorsys
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scepter_mockups import (S, DARK, SHAFT, SHAFT_HI, GOLD_D, GOLD_M,  # noqa: E402
                             GOLD_L, grip_from_sword, outline)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "emeraldweapons", "textures", "item")
PREVIEW = os.path.join(ROOT, "tools", "preview")

NFRAMES = 12
FRAMETIME = 3
NAME = "arcencium_vambraces"

# LE CONTOUR, donne explicitement.
#
# Trois profils procéduraux ont echoue avant celui-ci, et toujours de la meme
# facon : une epine plus une demi-largeur produit un TRIANGLE, quelle que soit
# la fonction de largeur. Or un couperet n'est pas un triangle, c'est un
# quadrilatere -- un dos qui file vers la pointe, un bout carre et lourd, un
# tranchant qui revient vers la main. On donne donc les sommets, et l'on
# remplit ; c'est moins elegant qu'une formule, et c'est la seule facon de
# tenir une silhouette.
OUTLINE = [(9, 23), (25, 3), (30, 6), (30, 16), (13, 22)]

# Le tranchant : le segment du contour ou mordent les dents.
EDGE = ((30, 16), (13, 22))
TEETH = 5
TOOTH = 2.6


def put(dst, x, y, color):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < S and 0 <= y < S:
        dst[x, y] = color + (255,) if len(color) == 3 else color


def inside(px, py):
    """Le test du rayon : le point est-il dans le contour ?"""
    hit = False
    n = len(OUTLINE)
    for i in range(n):
        x0, y0 = OUTLINE[i]
        x1, y1 = OUTLINE[(i + 1) % n]
        if (y0 > py) != (y1 > py):
            cut = x0 + (x1 - x0) * (py - y0) / float(y1 - y0)
            if px < cut:
                hit = not hit
    return hit


def edge_distance(px, py):
    """La distance au tranchant, qui commande tout l'ombrage."""
    (ax, ay), (bx, by) = EDGE
    dx, dy = bx - ax, by - ay
    span = dx * dx + dy * dy
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / span))
    return math.hypot(px - (ax + dx * t), py - (ay + dy * t)), t


def bitten(px, py):
    """Une dent mord-elle ici ? Les dents se retranchent, jamais ne s'ajoutent."""
    d, t = edge_distance(px, py)
    saw = (t * TEETH) % 1.0
    return d < TOOTH * saw


def blade(dst, shift):
    """La lame : acier sombre vers le dos, fil irise, tranchant dente."""
    for py in range(S):
        for px in range(S):
            if not inside(px, py) or bitten(px, py):
                continue
            d, t = edge_distance(px, py)
            # Les bandes sont LARGES. Serrees, presque toute la lame tombait
            # dans le ton le plus sombre et la piece paraissait noire : le
            # degrade doit occuper la masse, pas seulement l'ourler.
            if d < 1.4:
                hue = (shift + t * 0.5) % 1.0
                r, g, b = colorsys.hsv_to_rgb(hue, 0.55, 1.0)
                put(dst, px, py, (int(r * 255), int(g * 255), int(b * 255)))
            elif d < 3.4:
                put(dst, px, py, SHAFT_HI)
            elif d < 7.5:
                put(dst, px, py, SHAFT)
            else:
                put(dst, px, py, DARK)


def spine_light(dst):
    """
    L'arete claire du dos.

    Une lame eclairee d'un seul cote se lit comme une decoupe de papier. Deux
    bords lumineux et une masse sombre entre eux, c'est ce qui fait du volume.
    """
    (x0, y0), (x1, y1) = OUTLINE[0], OUTLINE[1]
    steps = 40
    for i in range(steps + 1):
        t = i / steps
        px = x0 + (x1 - x0) * t
        py = y0 + (y1 - y0) * t
        put(dst, px, py, SHAFT_HI)
        put(dst, px + 1, py + 1, SHAFT)


def veins(dst, shift):
    """Trois breches de lumiere dans l'acier -- les runes de la reference."""
    for m, (vx, vy) in enumerate(((19, 11), (23, 13), (16, 16))):
        hue = (shift + m * 0.2) % 1.0
        r, g, b = colorsys.hsv_to_rgb(hue, 0.60, 0.92)
        color = (int(r * 255), int(g * 255), int(b * 255))
        for j in range(3):
            put(dst, vx + j, vy + j, color)
        put(dst, vx + 1, vy + 2, tuple(int(c * 0.6) for c in color))


def teeth_gauge(dst, lit, shift):
    """Les cinq gemmes du dos. Les `lit` premieres brulent."""
    (x0, y0), (x1, y1) = OUTLINE[0], OUTLINE[1]
    for m in range(5):
        t = 0.24 + m * 0.15
        px = x0 + (x1 - x0) * t + 1.4
        py = y0 + (y1 - y0) * t + 1.0
        if m < lit:
            hue = (shift + m * 0.15) % 1.0
            r, g, b = colorsys.hsv_to_rgb(hue, 0.68, 1.0)
            color = (int(r * 255), int(g * 255), int(b * 255))
            put(dst, px, py, color)
            put(dst, px + 1, py + 1, tuple(int(c * 0.65) for c in color))
        else:
            put(dst, px, py, GOLD_D)


def heel(dst):
    """Le talon d'or, entre la poignee et la lame."""
    for k in range(-2, 3):
        put(dst, 10 + k, 22 - k, GOLD_M)
        put(dst, 11 + k, 23 - k, GOLD_D)
    put(dst, 10, 22, GOLD_L)


def frame(grip, lit, shift):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dst = img.load()
    blade(dst, shift)
    spine_light(dst)
    veins(dst, shift)
    teeth_gauge(dst, lit, shift)
    heel(dst)
    img.alpha_composite(grip)
    # le cerne noir de rigueur en pixel art : sans lui, une silhouette posee
    # sur l'inventaire se dissout dans ce qu'il y a derriere
    return outline(img)


def write(name, frames):
    sheet = Image.new("RGBA", (S, S * len(frames)))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (0, i * S))
    dest = os.path.join(ITEM_DIR, name + ".png")
    sheet.save(dest)
    with open(dest + ".mcmeta", "w") as fh:
        fh.write('{"animation": {"frametime": %d, "interpolate": true}}' % FRAMETIME)
    print("  %s (%dx%d, %d images)" % (name, S, S, len(frames)))


def main():
    grip, kept = grip_from_sword()
    print("poignee reprise de l'epee : %d pixels" % kept)
    os.makedirs(ITEM_DIR, exist_ok=True)
    for lit in range(6):
        frames = [frame(grip, lit, f / NFRAMES) for f in range(NFRAMES)]
        write("%s_%d" % (NAME, lit) if lit < 5 else "%s_full" % NAME, frames)
    write(NAME, [frame(grip, 0, f / NFRAMES) for f in range(NFRAMES)])

    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        strip = Image.new("RGBA", (S * 6, S))
        for lit in range(6):
            strip.paste(frame(grip, lit, 0.0), (lit * S, 0))
        out = os.path.join(PREVIEW, "vambrace_states.png")
        strip.resize((S * 6 * 6, S * 6), Image.NEAREST).save(out)
        print("apercu : %s" % os.path.relpath(out, ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
