#!/usr/bin/env python3
"""
La particule des chocs de vehicules : sa texture et sa definition.

UNE SEULE, ET NEUVE : jak_vehicle_spark, l'eclat de tole d'un choc (voiture contre
voiture, contre un mur, contre une pile de monstres). Aucune particule d'un autre
systeme du mod n'est reprise -- ni celles des armes, ni celles des meteos.

La texture est BLANCHE ; la couleur vient du code (jak/vehicle/VehicleParticles).
C'est un eclat court et epais, plus proche du metal que des etincelles fines du
Morph Gun : deux branches croisees, l'une deux fois plus longue.

Usage :
    python tools/vehicle_particles.py
"""

import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "emeraldweapons")
NAME = "jak_vehicle_spark"


def spark(size=16):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            u = (x - c) / c
            v = (y - c) / c
            d = math.hypot(u, v)
            if d > 1.0:
                continue
            # la branche longue (horizontale) et la courte (verticale), plus un coeur
            long_arm = max(0.0, 1.0 - abs(v) / 0.22) * max(0.0, 1.0 - (abs(u) / 0.95) ** 2)
            short_arm = max(0.0, 1.0 - abs(u) / 0.22) * max(0.0, 1.0 - (abs(v) / 0.5) ** 2)
            core = max(0.0, 1.0 - d / 0.3) ** 1.4
            a = max(long_arm, short_arm, core) * (1.0 - 0.25 * d)
            px[x, y] = (255, 255, 255, int(round(max(0.0, min(1.0, a)) * 255)))
    return img


def main():
    tex = os.path.join(ASSETS, "textures", "particle")
    defs = os.path.join(ASSETS, "particles")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(defs, exist_ok=True)
    spark().save(os.path.join(tex, NAME + ".png"))
    with open(os.path.join(defs, NAME + ".json"), "w", encoding="utf-8") as handle:
        json.dump({"textures": ["emeraldweapons:" + NAME]}, handle, indent=2)
        handle.write("\n")
    print("ecrit :", NAME)


if __name__ == "__main__":
    main()
