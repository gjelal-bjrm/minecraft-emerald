#!/usr/bin/env python3
"""
Le profil Distant Horizons du mode : voir loin, vraiment.

Le joueur voyait « pas si loin que ca » malgre DH. Trois causes, mesurees, et
deux ne venaient pas de DH :

 1. NOTRE brouillard de meteo coupait la vue a 56-210 blocs, soit 2 a 7 % des
    3 072 blocs que DH sait afficher (corrige dans WeatherClient) ;
 2. le brouillard PROPRE a DH commencait a 40 % de sa portee, en exponentiel
    carre a densite 2,5 : la moitie lointaine disparaissait d'elle-meme, et le
    brouillard de hauteur (densite 20 sous y=80) effacait les plaines basses ;
 3. la generation etait bridee -- quatre fils a 35 % du temps -- reglage pris
    quand on cherchait la cause des lags, qui etait en fait le tas de 32 Go.

Ce script pose le profil dans les trois configurations (dev et les deux
instances CurseForge). Il ne touche QUE les cles listees : le reste du fichier,
y compris ce que le joueur aurait regle a la main, est laisse tel quel.

    python tools/dh_profile.py            # pose le profil
    python tools/dh_profile.py --show     # montre les valeurs actuelles
"""
import argparse
import os
import re
import sys

# clef -> valeur. La clef est cherchee telle quelle, avec son indentation.
PROFILE = {
    # ---- la portee
    "lodChunkRenderDistanceRadius": "256",          # 4 096 blocs (etait 192)
    # ---- le brouillard de DH : il ne doit voiler que le tout dernier quart
    "farFogStart": '"0.75"',                        # etait 0.4
    "farFogDensity": '"1.0"',                       # etait 2.5
    "heightFogDensity": '"3.0"',                    # etait 20.0 : les plaines basses s'effacaient
    "heightFogEnd": '"0.9"',
    # ---- la generation : le Ryzen 5800X a seize fils, on en prend la moitie
    "numberOfThreads": "8",                         # etait 4
    "threadRunTimeRatio": '"0.7"',                  # etait 0.35
    "generationRequestRateLimit": "50",             # etait 20
    # ---- la qualite du relief lointain
    "verticalQuality": '"EXTREME"',
    "horizontalQuality": '"EXTREME"',
}

TARGETS = [
    r"run/config/DistantHorizons.toml",
    r"C:/Users/Gjelal/curseforge/minecraft/Instances/All the Mods 10 - CUSTOM/config/DistantHorizons.toml",
    r"C:/Users/Gjelal/curseforge/minecraft/Instances/Mode Arcencium/config/DistantHorizons.toml",
]


def patch(path, show):
    if not os.path.isfile(path):
        print(f"  absent : {path}")
        return
    text = open(path, encoding="utf-8").read()
    changed = 0
    for key, value in PROFILE.items():
        pattern = re.compile(rf"^(\s*){re.escape(key)}\s*=\s*(.+)$", re.M)
        match = pattern.search(text)
        if not match:
            print(f"    {key} : clef absente")
            continue
        before = match.group(2).strip()
        if show:
            print(f"    {key:<32} {before}")
            continue
        if before == value:
            continue
        text = pattern.sub(lambda m: f"{m.group(1)}{key} = {value}", text, count=1)
        print(f"    {key:<32} {before}  ->  {value}")
        changed += 1
    if not show and changed:
        open(path, "w", encoding="utf-8", newline="\n").write(text)
    print(f"  {os.path.basename(os.path.dirname(os.path.dirname(path)))} : {changed} valeur(s)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--show", action="store_true", help="montre sans rien changer")
    args = ap.parse_args()
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(root)
    for target in TARGETS:
        print(target)
        patch(target, args.show)


if __name__ == "__main__":
    main()
