#!/usr/bin/env python3
"""
Etend les apparitions de Living Things aux biomes d'Oh The Biomes We've Gone et de
Regions Unexplored.

LE JOUEUR (21 sept. 2026) : « il n'y a pas assez d'animaux ». Living Things (venu
d'ATM10) a seize animaux de surface -- elephants, lions, chouettes, requins... --
mais sa config (config/livingthings/*.json) n'accepte que des NOMS de biomes, pas
d'etiquettes (SpawnData : « Ignoring invalid biome »), et ne cite que ceux du jeu de
base. Or les regions des deux mods de biomes pesent 44 contre 10 (TerraBlender) :
dans leurs biomes a eux, aucun de ces animaux.

CE QUE FAIT CE SCRIPT, dans la config du profil (jeu ferme) : chaque entree
d'apparition gagne les biomes des deux mods de la meme FAMILLE que ses biomes du jeu
de base (foret, taiga, savane, jungle, plage, riviere, ocean, marais, neige, plaine,
champignons), familles lues dans les etiquettes des jars (minecraft:is_forest,
c:is_swamp...). Un biome ne rejoint qu'une entree par animal, la premiere de sa
famille : pas de double poids. Les biomes souterrains et hors de la surface sont
ecartes ; poids et groupes ne changent pas. Relancer le script ne double rien ;
--retirer enleve les biomes des deux mods et rend la config d'origine.

Les biomes et les etiquettes sont relus dans les jars a chaque passage : une mise a
jour des mods de biomes est suivie en relancant le script.

    python tools/livingthings_biomes.py [--a-blanc] [--retirer] [--profil CHEMIN]
"""

import argparse
import datetime
import glob
import json
import os
import shutil
import sys
import zipfile
from collections import defaultdict

PROFILE = os.path.join(os.path.expanduser("~"), "curseforge", "minecraft", "Instances", "Mode Arcencium")
JARS = ("Oh-The-Biomes-Weve-Gone-*.jar", "regions_unexplored-*.jar")
MODDED = ("biomeswevegone:", "regions_unexplored:")

# famille -> etiquettes des deux mods qui la designent
FAMILIES = {
    "foret": ["minecraft:is_forest", "biomeswevegone:forest"],
    "taiga": ["minecraft:is_taiga", "biomeswevegone:taiga", "c:taiga"],
    "savane": ["minecraft:is_savanna", "biomeswevegone:savanna", "c:savanna"],
    "jungle": ["minecraft:is_jungle", "biomeswevegone:jungle", "c:tree_jungle"],
    "plage": ["minecraft:is_beach", "biomeswevegone:beach", "c:beach", "c:shore"],
    "riviere": ["minecraft:is_river", "c:river"],
    "ocean": ["minecraft:is_ocean", "biomeswevegone:ocean", "c:ocean", "c:oceans"],
    "marais": ["c:is_swamp", "c:swamp", "c:swamps", "biomeswevegone:swamp"],
    "neige": ["c:is_snowy", "c:snowy", "biomeswevegone:snowy"],
    "plaine": ["c:is_plains", "c:plains", "biomeswevegone:plains"],
    "champignons": ["c:mushroom", "c:mushrooms"],
}
SURFACE = ["minecraft:is_overworld", "biomeswevegone:overworld"]
UNDERGROUND = ["c:cave", "c:caves", "c:underground", "c:is_cave", "c:is_underground"]

# biome du jeu de base -> sa famille (ceux que cite la config de Living Things)
VANILLA = {
    "forest": "foret", "birch_forest": "foret", "old_growth_birch_forest": "foret",
    "dark_forest": "foret", "flower_forest": "foret",
    "taiga": "taiga", "old_growth_pine_taiga": "taiga", "old_growth_spruce_taiga": "taiga",
    "savanna": "savane", "savanna_plateau": "savane", "windswept_savanna": "savane",
    "jungle": "jungle", "sparse_jungle": "jungle", "bamboo_jungle": "jungle",
    "beach": "plage", "river": "riviere",
    "ocean": "ocean", "deep_ocean": "ocean", "frozen_ocean": "ocean", "deep_frozen_ocean": "ocean",
    "cold_ocean": "ocean", "deep_cold_ocean": "ocean", "warm_ocean": "ocean",
    "lukewarm_ocean": "ocean", "deep_lukewarm_ocean": "ocean",
    "swamp": "marais", "mangrove_swamp": "marais",
    "snowy_plains": "neige", "snowy_slopes": "neige", "snowy_beach": "neige", "snowy_taiga": "neige",
    "plains": "plaine", "sunflower_plains": "plaine",
    "mushroom_fields": "champignons",
}


def read_jars(mods):
    """Les biomes des deux mods et toutes les etiquettes de biomes de leurs jars."""
    biomes, tags, used = set(), defaultdict(set), []
    for pattern in JARS:
        found = sorted(glob.glob(os.path.join(mods, pattern)))
        if not found:
            sys.exit(f"jar introuvable : {pattern} dans {mods}")
        used.append(os.path.basename(found[-1]))
        with zipfile.ZipFile(found[-1]) as jar:
            for name in jar.namelist():
                parts = name.split("/")
                if not name.endswith(".json") or len(parts) < 5 or parts[0] != "data":
                    continue
                if parts[2:4] == ["worldgen", "biome"]:
                    biomes.add(parts[1] + ":" + "/".join(parts[4:])[:-5])
                elif len(parts) >= 6 and parts[2:5] == ["tags", "worldgen", "biome"]:
                    key = parts[1] + ":" + "/".join(parts[5:])[:-5]
                    try:
                        values = json.loads(jar.read(name)).get("values", [])
                    except ValueError:
                        continue
                    for value in values:
                        tags[key].add(value["id"] if isinstance(value, dict) else value)
    return biomes, tags, used


def resolve(tags, names):
    """Les biomes d'un groupe d'etiquettes, etiquettes imbriquees comprises."""
    out, todo, seen = set(), list(names), set()
    while todo:
        tag = todo.pop()
        if tag in seen:
            continue
        seen.add(tag)
        for value in tags.get(tag, ()):
            if value.startswith("#"):
                todo.append(value[1:])
            else:
                out.add(value)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    parser.add_argument("--profil", default=PROFILE)
    parser.add_argument("--a-blanc", action="store_true", help="n'ecrit rien, montre seulement")
    parser.add_argument("--retirer", action="store_true", help="enleve les biomes des deux mods")
    args = parser.parse_args()

    config = os.path.join(args.profil, "config", "livingthings")
    biomes, tags, used = read_jars(os.path.join(args.profil, "mods"))
    surface = resolve(tags, SURFACE) - resolve(tags, UNDERGROUND)
    families = {name: resolve(tags, names) & biomes & surface for name, names in FAMILIES.items()}
    print(f"jars : {', '.join(used)} ; {len(biomes)} biomes, {len(surface & biomes)} de surface")

    changed, gained = {}, set()
    for path in sorted(glob.glob(os.path.join(config, "*.json"))):
        text = open(path, encoding="utf-8").read()
        data = json.loads(text)
        entries = data.get("spawnBiomes")
        if not entries:
            continue
        before = sum(len(e["biomes"]) for e in entries)
        taken = set()
        for entry in entries:
            entry["biomes"] = [b for b in entry["biomes"] if not b.startswith(MODDED)]
            if args.retirer:
                continue
            wanted = set()
            for biome in entry["biomes"]:
                family = VANILLA.get(biome.split(":", 1)[1]) if biome.startswith("minecraft:") else None
                if family:
                    wanted |= families[family]
            fresh = sorted(wanted - taken)
            taken |= wanted
            entry["biomes"] += fresh
        after = sum(len(e["biomes"]) for e in entries)
        animal = os.path.basename(path)[:-5]
        gained |= taken
        print(f"  {animal:10s} {before:3d} -> {after:3d} biomes")
        new_text = json.dumps(data, indent="\t", ensure_ascii=False)
        if new_text != text:
            changed[path] = new_text

    print(f"biomes de surface des deux mods ou vit au moins un animal : {len(gained)} / {len(surface & biomes)}")
    if args.a_blanc or not changed:
        print("rien d'ecrit" + (" (a blanc)" if args.a_blanc else " : deja a jour"))
        return
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = os.path.join(args.profil, "sauvegardes_arcencium", f"livingthings-{stamp}")
    os.makedirs(backup)
    for path in glob.glob(os.path.join(config, "*.json")):
        shutil.copy2(path, backup)
    for path, text in changed.items():
        with open(path, "w", encoding="utf-8", newline="\n") as out:
            out.write(text)
    print(f"{len(changed)} fichiers ecrits ; l'ancienne config est dans {backup}")


if __name__ == "__main__":
    main()
