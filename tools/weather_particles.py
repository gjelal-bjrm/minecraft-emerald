#!/usr/bin/env python3
"""
Les particules des six meteos : quatorze textures, une par usage, aucune partagee.

C'est la reponse a un reproche precis : les meteos puisaient dans les memes
particules que les armes, le sceptre et les plantes -- la mote de Prisme, la
tige d'End, la poussiere de redstone -- et tout finissait par se ressembler.
Chaque meteo recoit ici son propre vocabulaire, dessine pour elle :

  Brume      mist_sheet (nappe), mist_wraith (forme fantomatique)
  Aurore     crystal_firefly (luciole de cristal)
  Nuit       prism_drop (goutte), prism_shard (eclat de cristal au sol)
  Meteores   meteor_head (tete), meteor_ember (braise), ash_flake (cendre),
             ground_shock (onde de choc), quake_dust (poussiere de secousse)
  Dechirure  float_debris (eclat de terre), float_blade (brin d'herbe)
  Orage      static_spark (etincelle statique), wind_rain (pluie oblique)

Les textures sont BLANCHES ou en niveaux de gris : la couleur vient du code,
au moment de l'emission. C'est ce qui permet a une goutte de Nuit de prendre la
couleur de l'eclair qu'elle annonce, et a une braise de refroidir de l'orange
au noir sans qu'il faille une image par teinte.

Usage :
    python tools/weather_particles.py
"""

import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons",
                       "textures", "particle")
DEF_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons",
                       "particles")


def canvas(size):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def soft_blob(size, falloff=2.2, rx=1.0, ry=1.0):
    """Un disque doux : alpha en cloche, sans bord. Sert aux nappes et aux halos."""
    img = canvas(size)
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            dx = (x - c) / (c * rx)
            dy = (y - c) / (c * ry)
            d = math.sqrt(dx * dx + dy * dy)
            a = max(0.0, 1.0 - d) ** falloff
            px[x, y] = (255, 255, 255, int(a * 255))
    return img


def wisp(size):
    """Une forme verticale, large en bas, effilochee en haut : le fantome de brume."""
    img = canvas(size)
    px = img.load()
    for y in range(size):
        t = y / (size - 1)                       # 0 en haut, 1 en bas
        width = 0.18 + 0.32 * t                  # s'elargit vers le bas
        cx = 0.5 + 0.10 * math.sin(t * 6.0)       # ondule
        for x in range(size):
            u = x / (size - 1)
            d = abs(u - cx) / width
            a = max(0.0, 1.0 - d * d) * (0.35 + 0.65 * t) * (1.0 - t * 0.55)
            # la base se dissout aussi, pour ne pas poser un pied net au sol
            if t > 0.85:
                a *= (1.0 - t) / 0.15
            px[x, y] = (255, 255, 255, int(min(1.0, a) * 255))
    return img


def dot(size, core=0.35):
    """Un point vif a coeur plein et bord doux : lucioles, etincelles."""
    img = canvas(size)
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            d = math.sqrt((x - c) ** 2 + (y - c) ** 2) / c
            a = 1.0 if d < core else max(0.0, 1.0 - (d - core) / (1.0 - core)) ** 1.6
            px[x, y] = (255, 255, 255, int(a * 255))
    return img


def streak(size, length=0.9, width=0.16, angle_deg=90.0):
    """Un trait allonge a bouts doux : gouttes et pluie. angle 90 = vertical."""
    img = canvas(size)
    px = img.load()
    c = (size - 1) / 2.0
    ang = math.radians(angle_deg)
    ux, uy = math.cos(ang), math.sin(ang)
    for y in range(size):
        for x in range(size):
            dx, dy = (x - c) / c, (y - c) / c
            along = dx * ux + dy * uy
            across = -dx * uy + dy * ux
            a_along = max(0.0, 1.0 - (abs(along) / length) ** 4)
            a_across = max(0.0, 1.0 - (abs(across) / width) ** 2)
            px[x, y] = (255, 255, 255, int(a_along * a_across * 255))
    return img


def ring(size, radius=0.78, thickness=0.16):
    """Un anneau doux : eclatement de goutte, onde de choc."""
    img = canvas(size)
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            d = math.sqrt((x - c) ** 2 + (y - c) ** 2) / c
            a = max(0.0, 1.0 - abs(d - radius) / thickness) ** 1.5
            px[x, y] = (255, 255, 255, int(a * 255))
    return img


def flake(size):
    """Un petit flocon irregulier, en gris : la cendre."""
    img = canvas(size)
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            d = math.sqrt((x - c) ** 2 + (y - c) ** 2) / c
            wob = 0.72 + 0.18 * math.sin(math.atan2(y - c, x - c) * 3.0)
            a = 1.0 if d < wob else 0.0
            g = 150 + int(60 * ((x + y) % 3) / 2)
            px[x, y] = (g, g, g, int(a * 255))
    return img


def chunk(size):
    """Un eclat anguleux, en gris : la terre qui decolle. La teinte vient du code."""
    img = canvas(size)
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            dx, dy = (x - c) / c, (y - c) / c
            # un polygone a cinq cotes, legerement penche
            ang = math.atan2(dy, dx)
            r = 0.55 + 0.25 * math.cos(ang * 5.0 + 0.7)
            d = math.sqrt(dx * dx + dy * dy)
            if d > r:
                continue
            shade = 200 if dy < -0.1 else (150 if dy < 0.3 else 105)
            px[x, y] = (shade, shade, shade, 255)
    return img


def blade(size):
    """Un brin d'herbe : fin, courbe, plus clair sur un bord."""
    img = canvas(size)
    px = img.load()
    for y in range(size):
        t = 1.0 - y / (size - 1)                 # 0 en bas, 1 en haut
        cx = (size - 1) * (0.45 + 0.18 * t * t)  # se courbe en montant
        w = (size - 1) * (0.14 * (1.0 - t) + 0.03)
        for x in range(size):
            d = abs(x - cx)
            if d > w:
                continue
            shade = 235 if x < cx else 175
            px[x, y] = (shade, shade, shade, 255)
    return img


def sliver(size):
    """Un eclat de cristal : un losange etroit et penche, blanc, au bord net."""
    img = canvas(size)
    px = img.load()
    c = (size - 1) / 2.0
    ang = math.radians(28.0)
    ca, sa = math.cos(ang), math.sin(ang)
    for y in range(size):
        for x in range(size):
            dx, dy = (x - c) / c, (y - c) / c
            u = dx * ca + dy * sa
            v = -dx * sa + dy * ca
            d = abs(u) / 0.95 + abs(v) / 0.30
            if d > 1.0:
                continue
            a = min(1.0, (1.0 - d) / 0.25)
            shade = 255 if abs(v) < 0.10 else 225
            px[x, y] = (shade, shade, shade, int(a * 255))
    return img


SPECS = [
    # nom, image, taille
    ("mist_sheet", lambda: soft_blob(32, falloff=1.6, rx=1.0, ry=0.62), 32),
    ("mist_wraith", lambda: wisp(32), 32),
    ("crystal_firefly", lambda: dot(8, core=0.3), 8),
    ("prism_drop", lambda: streak(16, length=0.95, width=0.14), 16),
    ("prism_shard", lambda: sliver(16), 16),
    ("meteor_head", lambda: dot(16, core=0.45), 16),
    ("meteor_ember", lambda: dot(8, core=0.4), 8),
    ("ash_flake", lambda: flake(8), 8),
    ("ground_shock", lambda: ring(32, radius=0.80, thickness=0.20), 32),
    ("quake_dust", lambda: soft_blob(16, falloff=1.4), 16),
    ("float_debris", lambda: chunk(8), 8),
    ("float_blade", lambda: blade(16), 16),
    ("static_spark", lambda: dot(8, core=0.25), 8),
    ("wind_rain", lambda: streak(16, length=0.95, width=0.10, angle_deg=70.0), 16),
]


def main():
    for d in (TEX_DIR, DEF_DIR):
        if not os.path.isdir(d):
            os.makedirs(d)
    for name, make, size in SPECS:
        img = make()
        assert img.size == (size, size)
        img.save(os.path.join(TEX_DIR, name + ".png"))
        with open(os.path.join(DEF_DIR, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({"textures": ["emeraldweapons:" + name]}, f, indent=2)
            f.write("\n")
        print("  %-16s %2dx%-2d" % (name, size, size))
    print("%d particules meteo ecrites" % len(SPECS))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
