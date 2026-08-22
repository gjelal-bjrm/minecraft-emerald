#!/usr/bin/env python3
"""
La Citadelle d'Arcencium -- forteresse celeste, rehabillee depuis le
"Heavenly Challenger" de Dungeons Arise (modpack ATM10, usage prive).

Ce qu'elle a de particulier :
  - elle genere a Y=200, donc DANS LES NUAGES (les nuages sont a 192) ;
  - 6 parties de 48 blocs de haut, assemblees par jigsaw (size 7) ;
  - 41 points d'apparition : cavaliers-phantomes, squelettes juggernauts en
    netherite, wither skeletons, cavaliers-hoglins. Toutes ces entites sont
    VANILLA (attributs et equipement personnalises), donc conservees telles
    quelles -- rien a remapper.
  - ses coffres pointaient vers les tables de butin de Dungeons Arise :
    on les remplace par les notres, ou l'Arcencium se trouve.

Les bois y jouent sur deux valeurs (chene noir sombre / acacia clair). On
respecte ce contraste : le sombre devient notre bois, le clair notre pierre.
Tout mapper vers crystal_planks aplatirait 48% du volume en un seul bloc.

Usage :
    python tools/make_citadel.py
En jeu :  /locate structure emeraldweapons:arcencium_citadel
"""

import json
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_structure as nbtlib
import reskin

ROOT = nbtlib.ROOT
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons")
STRUCT_DIR = os.path.join(DATA, "structure")
NS = "emeraldweapons"
STRUCTURE_ID = "arcencium_citadel"
SRC_JAR = "DungeonsArise"
SRC_NS = "dungeons_arise"
SRC_POOL = "dungeons_arise:eerie/heavenly_challenger/"
OUR_POOL = "%s:citadel/" % NS
PREFIX = "heavenly_challenger_"

# Le contraste de valeur du batiment d'origine, transpose sur notre palette.
CITADEL_MAP = {
    # bois sombres (ponts, coques) -> notre bois
    "minecraft:dark_oak_planks": NS + ":crystal_planks",
    "minecraft:spruce_planks": NS + ":crystal_planks",
    "minecraft:birch_planks": NS + ":crystal_planks",
    "minecraft:birch_stairs": NS + ":crystal_stairs",
    "minecraft:birch_slab": NS + ":crystal_slab",
    # acacia (clair, chaud) -> notre pierre : garde le contraste clair/sombre
    "minecraft:acacia_planks": NS + ":gangue_bricks",
    "minecraft:acacia_stairs": NS + ":gangue_brick_stairs",
    "minecraft:acacia_slab": NS + ":gangue_brick_slab",
    "minecraft:stripped_acacia_wood": NS + ":polished_gangue",
    "minecraft:acacia_fence": NS + ":crystal_fence",
    # rondins bruts -> nos troncs de Prisme (poutres, ~9% du volume)
    "minecraft:acacia_wood": NS + ":prism_log",
    "minecraft:stripped_birch_wood": NS + ":prism_log",
    "minecraft:stripped_dark_oak_wood": NS + ":prism_log",
    "minecraft:stripped_spruce_wood": NS + ":prism_log",
    "minecraft:stripped_oak_wood": NS + ":prism_log",
}

# Butin : quatre roles, comme l'original.
LOOT = {
    "supply": [("minecraft:bread", 3, 8), ("minecraft:cooked_beef", 2, 5),
               ("minecraft:arrow", 8, 24), ("minecraft:torch", 4, 12),
               ("emeraldweapons:raw_arcencium", 1, 3)],
    "normal": [("minecraft:iron_ingot", 2, 6), ("minecraft:gold_ingot", 1, 4),
               ("minecraft:emerald", 2, 6), ("emeraldweapons:arcencium_ingot", 1, 2),
               ("minecraft:experience_bottle", 2, 6)],
    "theater": [("minecraft:emerald", 4, 12), ("minecraft:gold_ingot", 3, 8),
                ("emeraldweapons:arcencium_ingot", 1, 3),
                ("minecraft:enchanted_golden_apple", 1, 1)],
    "treasure": [("emeraldweapons:arcencium_ingot", 3, 7),
                 ("emeraldweapons:arcencium_block", 1, 2),
                 ("minecraft:diamond", 2, 6), ("minecraft:netherite_scrap", 1, 2),
                 ("minecraft:enchanted_golden_apple", 1, 2)],
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def our_name(entry):
    """data/.../heavenly_challenger_part_2.nbt -> citadel_part_2"""
    base = os.path.basename(entry)[:-4]
    if base.startswith(PREFIX):
        base = base[len(PREFIX):]
    return "citadel_" + base


def write_loot_tables():
    for role, entries in LOOT.items():
        write_json(os.path.join(DATA, "loot_table", "chests", "citadel", role + ".json"), {
            "type": "minecraft:chest",
            "pools": [{
                "rolls": {"min": 3, "max": 6},
                "entries": [{
                    "type": "minecraft:item",
                    "name": item,
                    "weight": 1,
                    "functions": [{
                        "function": "minecraft:set_count",
                        "count": {"min": lo, "max": hi},
                    }],
                } for item, lo, hi in entries],
            }],
        })


def convert_pieces():
    """Rehabille toutes les pieces, y compris les marqueurs de spawner
    (1-2 blocs) que l'import de village ecarte par leur petite taille."""
    # Les pools s'appellent ".../heavenly_challenger/heavenly_challenger_main".
    # On absorbe le prefixe de piece dans la redirection, pour retomber sur
    # nos fichiers "citadel/main.json" et non "citadel/heavenly_challenger_main".
    reskin.POOL_PREFIX = {SRC_POOL + PREFIX: OUR_POOL, SRC_POOL: OUR_POOL}
    reskin.LOOT_MAP = {
        "%s:chests/heavenly_challenger/heavenly_challenger_%s" % (SRC_NS, role):
            "%s:chests/citadel/%s" % (NS, role) for role in LOOT
    }
    mapping = dict(reskin.MAP)
    mapping.update(CITADEL_MAP)
    os.makedirs(STRUCT_DIR, exist_ok=True)
    made, unmapped = [], {}
    jp = nbtlib.find_jar(SRC_JAR)
    with zipfile.ZipFile(jp) as z:
        entries = sorted(n for n in z.namelist()
                         if n.endswith(".nbt") and PREFIX in n)
        for n in entries:
            st = nbtlib.Structure(nbtlib.parse(z.read(n))[1])
            root, stats = reskin.reskin_structure(st, mapping)
            name = our_name(n)
            with open(os.path.join(STRUCT_DIR, name + ".nbt"), "wb") as f:
                f.write(nbtlib.serialize(root))
            for k, v in reskin.unmapped(st, mapping).items():
                unmapped[k] = unmapped.get(k, 0) + v
            # blocs POSES (stats["blocs"] compte aussi l'air, que le NBT doit
            # garder pour degager le volume a la generation)
            made.append((name, st.size, len(st.solid_cells())))
    return made, unmapped


def convert_pools(made):
    names = {m[0] for m in made}
    jp = nbtlib.find_jar(SRC_JAR)
    written = []
    with zipfile.ZipFile(jp) as z:
        for n in z.namelist():
            if not (n.endswith(".json") and "template_pool" in n and PREFIX in n):
                continue
            src = json.loads(z.read(n))
            elements = []
            for e in src.get("elements", []):
                el = dict(e["element"])
                loc = el.get("location", "")
                if loc.startswith(SRC_NS + ":"):
                    piece = "citadel_" + loc.split("/")[-1][len(PREFIX):] \
                        if loc.split("/")[-1].startswith(PREFIX) else None
                    if not piece or piece not in names:
                        continue
                    el["location"] = "%s:%s" % (NS, piece)
                elif loc and not loc.startswith("minecraft:"):
                    continue
                el["processors"] = "minecraft:empty"
                elements.append({"weight": e.get("weight", 1), "element": el})
            pool = os.path.basename(n)[:-5]
            if pool.startswith(PREFIX):
                pool = pool[len(PREFIX):]
            write_json(os.path.join(DATA, "worldgen", "template_pool",
                                    "citadel", pool + ".json"),
                       {"fallback": "minecraft:empty", "elements": elements})
            written.append("%s%s" % (OUR_POOL, pool))
    return written


def write_structure_files():
    write_json(os.path.join(DATA, "worldgen", "structure", STRUCTURE_ID + ".json"), {
        "type": "minecraft:jigsaw",
        "biomes": "#%s:has_structure/arcencium_citadel" % NS,
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": "none",
        "start_pool": OUR_POOL + "start",
        "size": 7,
        # Y=200 : au-dessus de la couche de nuages (192). C'est l'identite
        # du batiment -- une citadelle qu'on voit de loin, dans le ciel.
        "start_height": {"absolute": 200},
        "max_distance_from_center": 116,
        "use_expansion_hack": False,
        "liquid_settings": "ignore_waterlogging",
    })
    write_json(os.path.join(DATA, "worldgen", "structure_set",
                            "arcencium_citadels.json"), {
        "structures": [{"structure": "%s:%s" % (NS, STRUCTURE_ID), "weight": 1}],
        # rare : c'est un point de repere, pas un decor
        "placement": {"type": "minecraft:random_spread", "spacing": 60,
                      "separation": 24, "salt": 402318877},
    })
    write_json(os.path.join(DATA, "tags", "worldgen", "biome", "has_structure",
                            "arcencium_citadel.json"),
               {"replace": False,
                "values": ["#minecraft:is_forest", "#minecraft:is_jungle",
                           "minecraft:plains", "minecraft:sunflower_plains",
                           "minecraft:savanna", "minecraft:savanna_plateau",
                           "minecraft:desert"]})


def validate(made, pools):
    problems = []
    names = {m[0] for m in made}
    pool_set = set(pools)
    import re
    for base, _, files in os.walk(os.path.join(DATA, "worldgen", "template_pool", "citadel")):
        for f in files:
            d = json.load(open(os.path.join(base, f), encoding="utf-8"))
            for e in d["elements"]:
                loc = e["element"].get("location", "")
                if loc.startswith(NS + ":") and loc.split(":", 1)[1] not in names:
                    problems.append("piece manquante : " + loc)
    for name in sorted(names):
        st = nbtlib.load(os.path.join(STRUCT_DIR, name + ".nbt"))
        for t in st.block_nbt.values():
            p = str(t.get("pool", ""))
            if p and p != "minecraft:empty" and p not in pool_set:
                problems.append("pool manquante : %s (dans %s)" % (p, name))
            lt = str(t.get("LootTable", ""))
            if lt and not lt.startswith((NS + ":", "minecraft:")):
                problems.append("butin etranger : %s (dans %s)" % (lt, name))
    ref = re.compile(r'"([a-z_]+):[a-z0-9_/.]+"')
    for sub in ("worldgen", "loot_table"):
        for base, _, files in os.walk(os.path.join(DATA, sub)):
            for f in files:
                if "citadel" not in os.path.join(base, f):
                    continue
                txt = open(os.path.join(base, f), encoding="utf-8").read()
                for ns in set(ref.findall(txt)):
                    if ns not in ("minecraft", NS):
                        problems.append("namespace etranger '%s:' dans %s" % (ns, f))
    return problems


def main():
    made, unmapped = convert_pieces()
    pools = convert_pools(made)
    write_loot_tables()
    write_structure_files()
    problems = validate(made, pools)

    print("=== Citadelle d'Arcencium ===")
    print("  pieces : %d" % len(made))
    for name, size, blocks in sorted(made, key=lambda m: -m[2])[:6]:
        print("     %-34s %-12s %6d blocs poses" % (name, "x".join(map(str, size)), blocks))
    print("  pools  : %s" % ", ".join(p.split("/")[-1] for p in pools))
    print("  butin  : %s" % ", ".join(sorted(LOOT)))
    if unmapped:
        tot = sum(unmapped.values())
        print("  laisse vanilla : %d blocs, %d types" % (tot, len(unmapped)))
        for k, v in sorted(unmapped.items(), key=lambda kv: -kv[1])[:8]:
            print("     %-44s %5d" % (k, v))
    print("  validation : %s" % ("OK, toutes les references resolvent"
                                 if not problems else "%d PROBLEMES" % len(problems)))
    for p in problems[:15]:
        print("     ", p)
    print()
    print("  En jeu :  /locate structure %s:%s" % (NS, STRUCTURE_ID))


if __name__ == "__main__":
    main()
