#!/usr/bin/env python3
"""
Rend les arbres fruitiers sauvages de Pam's HarvestCraft 2 - Trees plus rares.

LE JOUEUR (21 sept. 2026) : « je me retrouve avec des arbres parfois tres petits,
ça bloque les chemins ». Les cinquante arbres fruitiers de Pam ont tous un tronc de
4 a 6 blocs (straight_trunk_placer 4 + 1..2), des feuilles a hauteur de tete, et
chacun pousse dans les forets de son climat a raison d'une chance sur 90 par
troncon. Choix du joueur : les rendre TROIS FOIS plus rares, pas les retirer (leurs
fruits ne viennent que d'eux).

CE QUE CE SCRIPT ECRIT, dans les donnees du mod, sans toucher aux fichiers de Pam :
  - un modificateur de biome qui RETIRE les cinquante elements places de Pam ;
  - pour chaque arbre, un element place a nous, copie du sien a la rarete x 3
    (rarity_filter 90 -> 270), et un modificateur qui l'ajoute aux MEMES biomes.
Tout porte la condition neoforge:mod_loaded pamhc2trees : sans Pam's Trees (le dev
n'a qu'une partie du modpack), rien n'est charge et rien ne casse.

Les biomes et les elements sont relus dans le jar de Pam, a chaque passage : une mise
a jour de Pam est suivie en relancant le script.

    python tools/pam_trees_rarity.py [--jar CHEMIN] [--factor 3]
"""

import argparse
import glob
import json
import os
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons")
PLACED_DIR = os.path.join(DATA, "worldgen", "placed_feature", "pam_rarete")
MODIFIER_DIR = os.path.join(DATA, "neoforge", "biome_modifier", "pam_rarete")
PROFILE_MODS = os.path.join(os.path.expanduser("~"), "curseforge", "minecraft", "Instances", "Mode Arcencium", "mods")
CONDITION = [{"type": "neoforge:mod_loaded", "modid": "pamhc2trees"}]


def find_jar():
    found = sorted(glob.glob(os.path.join(PROFILE_MODS, "pamhc2trees-*.jar")))
    return found[-1] if found else None


def main():
    parser = argparse.ArgumentParser(description="Arbres fruitiers de Pam plus rares.")
    parser.add_argument("--jar", default=None)
    parser.add_argument("--factor", type=int, default=3)
    args = parser.parse_args()
    jar = args.jar or find_jar()
    if not jar or not os.path.isfile(jar):
        sys.exit("jar de Pam's Trees introuvable (--jar)")
    archive = zipfile.ZipFile(jar)
    modifiers = sorted(n for n in archive.namelist()
                       if n.startswith("data/pamhc2trees/neoforge/biome_modifier/") and n.endswith(".json"))
    for folder in (PLACED_DIR, MODIFIER_DIR):
        if os.path.isdir(folder):
            shutil.rmtree(folder)
        os.makedirs(folder)
    removed = []
    rarities = []
    for name in modifiers:
        modifier = json.loads(archive.read(name))
        if modifier.get("type") != "neoforge:add_features":
            continue
        feature = modifier["features"]
        if not isinstance(feature, str) or not feature.startswith("pamhc2trees:"):
            continue
        path = feature.split(":", 1)[1]
        placed = json.loads(archive.read("data/pamhc2trees/worldgen/placed_feature/%s.json" % path))
        for step in placed["placement"]:
            if step.get("type") == "minecraft:rarity_filter":
                rarities.append(step["chance"])
                step["chance"] = step["chance"] * args.factor
        tree = path[:-len("_placed")] if path.endswith("_placed") else path
        mine = dict(placed)
        mine = {"neoforge:conditions": CONDITION, **mine}
        with open(os.path.join(PLACED_DIR, tree + ".json"), "w", encoding="utf-8") as handle:
            json.dump(mine, handle, indent=2)
            handle.write("\n")
        added = {"neoforge:conditions": CONDITION, "type": "neoforge:add_features",
                 "biomes": modifier["biomes"], "features": "emeraldweapons:pam_rarete/" + tree,
                 "step": modifier.get("step", "vegetal_decoration")}
        with open(os.path.join(MODIFIER_DIR, tree + ".json"), "w", encoding="utf-8") as handle:
            json.dump(added, handle, indent=2)
            handle.write("\n")
        removed.append(feature)
    remove = {"neoforge:conditions": CONDITION, "type": "neoforge:remove_features",
              "biomes": "#minecraft:is_overworld", "features": removed, "steps": ["vegetal_decoration"]}
    with open(os.path.join(MODIFIER_DIR, "_retirer_pam.json"), "w", encoding="utf-8") as handle:
        json.dump(remove, handle, indent=2)
        handle.write("\n")
    print("jar : %s" % jar)
    print("%d arbres de Pam : raretes d'origine %s, x %d" % (len(removed), sorted(set(rarities)), args.factor))
    print("ecrit : %s et %s" % (os.path.relpath(PLACED_DIR, ROOT), os.path.relpath(MODIFIER_DIR, ROOT)))


if __name__ == "__main__":
    main()
