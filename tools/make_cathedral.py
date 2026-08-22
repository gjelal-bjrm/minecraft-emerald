#!/usr/bin/env python3
"""
La Cathedrale d'Arcencium -- rehabillee depuis "Keep Kayra" de Dungeons
Arise (modpack ATM10, usage prive).

C'est LE colosse du modpack : 163 x 250 x 163, 944 703 blocs en UNE seule
piece monolithique. Posee a Y=54 en marecage, elle culmine vers Y=300,
bien au-dessus des nuages -- d'ou le fait que le jeu peine a la charger.

Choix notables :
  - ses VITRAUX (14 couleurs, 4734 blocs) sont conserves tels quels : une
    rosace arc-en-ciel est deja dans l'esprit Arcencium, la ramener a notre
    verre unique l'aplatirait ;
  - la mousse, les lianes, l'eau, les bibliotheques, grenouilles et tableaux
    restent vanilla : c'est l'atmosphere du lieu ;
  - son type de structure est propre a Dungeons Arise
    ("dungeons_arise:generic_structures") : on le traduit en jigsaw vanilla,
    les champs sont les memes a "adapt_noise" pres.

Usage :
    python tools/make_cathedral.py
En jeu :  /locate structure emeraldweapons:arcencium_cathedral
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
STRUCTURE_ID = "arcencium_cathedral"
SRC_JAR = "DungeonsArise"
SRC_NS = "dungeons_arise"
SRC_POOL = "dungeons_arise:keep_kayra/keep_kayra_"
OUR_POOL = "%s:cathedral/" % NS
PREFIX = "keep_kayra_"

# Le bois de la cathedrale joue sur deux valeurs : lisse (parquets, lambris)
# et brut (poutres, colonnes). On garde ce contraste.
CATHEDRAL_MAP = {
    "minecraft:stripped_dark_oak_wood": NS + ":crystal_planks",
    "minecraft:stripped_mangrove_wood": NS + ":crystal_planks",
    "minecraft:stripped_acacia_wood": NS + ":crystal_planks",
    "minecraft:dark_oak_wood": NS + ":prism_log",
    "minecraft:mangrove_wood": NS + ":prism_log",
    "minecraft:acacia_wood": NS + ":prism_log",
    "minecraft:mangrove_planks": NS + ":crystal_planks",
    "minecraft:mangrove_stairs": NS + ":crystal_stairs",
    "minecraft:mangrove_slab": NS + ":crystal_slab",
    "minecraft:mangrove_fence": NS + ":crystal_fence",
    "minecraft:birch_stairs": NS + ":crystal_stairs",
    "minecraft:birch_slab": NS + ":crystal_slab",
    "minecraft:birch_planks": NS + ":crystal_planks",
}

# Les vitraux gardent leurs 14 couleurs : c'est l'ame du batiment.
KEEP_VANILLA = {
    "minecraft:moss_block", "minecraft:moss_carpet", "minecraft:glow_lichen",
    "minecraft:cave_vines", "minecraft:cave_vines_plant", "minecraft:vine",
    "minecraft:water", "minecraft:dirt", "minecraft:grass", "minecraft:chain",
    "minecraft:bookshelf", "minecraft:chiseled_bookshelf",
}

LOOT = {
    "normal": [("minecraft:iron_ingot", 2, 6), ("minecraft:gold_ingot", 1, 4),
               ("minecraft:emerald", 2, 5), ("minecraft:bread", 2, 5),
               ("emeraldweapons:raw_arcencium", 1, 3)],
    "treasure": [("emeraldweapons:arcencium_ingot", 3, 8),
                 ("emeraldweapons:arcencium_block", 1, 2),
                 ("minecraft:diamond", 2, 6), ("minecraft:netherite_scrap", 1, 2),
                 ("minecraft:enchanted_golden_apple", 1, 2)],
    "library_normal": [("minecraft:book", 3, 8), ("minecraft:paper", 4, 12),
                       ("minecraft:ink_sac", 2, 6), ("minecraft:bookshelf", 1, 4),
                       ("minecraft:experience_bottle", 2, 6)],
    "library_treasure": [("minecraft:enchanted_book", 1, 1),
                         ("minecraft:experience_bottle", 6, 16),
                         ("emeraldweapons:arcencium_ingot", 2, 5),
                         ("minecraft:lapis_lazuli", 8, 20)],
    "garden_normal": [("emeraldweapons:prism_sapling", 1, 3),
                      ("emeraldweapons:prism_bloom", 2, 6),
                      ("minecraft:bone_meal", 4, 10),
                      ("minecraft:moss_block", 2, 6)],
    "garden_treasure": [("emeraldweapons:arcencium_ingot", 2, 5),
                        ("emeraldweapons:prism_sapling", 2, 4),
                        ("minecraft:emerald", 4, 10),
                        ("minecraft:enchanted_golden_apple", 1, 1)],
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def our_name(entry):
    base = os.path.basename(entry)[:-4]
    if base.startswith(PREFIX):
        base = base[len(PREFIX):]
    return "cathedral_" + base


def write_loot_tables():
    for role, entries in LOOT.items():
        write_json(os.path.join(DATA, "loot_table", "chests", "cathedral", role + ".json"), {
            "type": "minecraft:chest",
            "pools": [{
                "rolls": {"min": 3, "max": 6},
                "entries": [{
                    "type": "minecraft:item", "name": item, "weight": 1,
                    "functions": [{"function": "minecraft:set_count",
                                   "count": {"min": lo, "max": hi}}],
                } for item, lo, hi in entries],
            }],
        })


def build_mapping():
    mapping = {k: v for k, v in reskin.MAP.items() if k not in KEEP_VANILLA}
    mapping.update(CATHEDRAL_MAP)
    for k in KEEP_VANILLA:
        mapping.pop(k, None)
    return mapping


def convert_pieces():
    reskin.POOL_PREFIX = {SRC_POOL: OUR_POOL}
    reskin.LOOT_MAP = {
        "%s:chests/keep_kayra/keep_kayra_%s" % (SRC_NS, role):
            "%s:chests/cathedral/%s" % (NS, role) for role in LOOT
    }
    mapping = build_mapping()
    os.makedirs(STRUCT_DIR, exist_ok=True)
    made, unmapped = [], {}
    with zipfile.ZipFile(nbtlib.find_jar(SRC_JAR)) as z:
        for n in sorted(x for x in z.namelist()
                        if x.endswith(".nbt") and "keep_kayra" in x):
            st = nbtlib.Structure(nbtlib.parse(z.read(n))[1])
            root, _ = reskin.reskin_structure(st, mapping)
            name = our_name(n)
            with open(os.path.join(STRUCT_DIR, name + ".nbt"), "wb") as f:
                f.write(nbtlib.serialize(root))
            for k, v in reskin.unmapped(st, mapping).items():
                unmapped[k] = unmapped.get(k, 0) + v
            made.append((name, st.size, len(st.solid_cells())))
            print("   %-30s %-14s %7d blocs" % (name, "x".join(map(str, st.size)),
                                                made[-1][2]))
    return made, unmapped


def convert_pools(made):
    names = {m[0] for m in made}
    written = []
    with zipfile.ZipFile(nbtlib.find_jar(SRC_JAR)) as z:
        for n in z.namelist():
            if not (n.endswith(".json") and "template_pool" in n and "keep_kayra" in n):
                continue
            src = json.loads(z.read(n))
            elements = []
            for e in src.get("elements", []):
                el = dict(e["element"])
                loc = el.get("location", "")
                if loc.startswith(SRC_NS + ":"):
                    tail = loc.split("/")[-1]
                    piece = "cathedral_" + tail[len(PREFIX):] if tail.startswith(PREFIX) else None
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
                                    "cathedral", pool + ".json"),
                       {"fallback": "minecraft:empty", "elements": elements})
            written.append(OUR_POOL + pool)
    return written


def write_structure_files():
    write_json(os.path.join(DATA, "worldgen", "structure", STRUCTURE_ID + ".json"), {
        # "dungeons_arise:generic_structures" chez eux -> jigsaw vanilla ici
        "type": "minecraft:jigsaw",
        "biomes": "#%s:has_structure/arcencium_cathedral" % NS,
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": "none",
        "start_pool": OUR_POOL + "start",
        "size": 7,
        "start_height": {"absolute": 54},
        "max_distance_from_center": 116,
        "use_expansion_hack": False,
        "liquid_settings": "ignore_waterlogging",
    })
    write_json(os.path.join(DATA, "worldgen", "structure_set",
                            "arcencium_cathedrals.json"), {
        "structures": [{"structure": "%s:%s" % (NS, STRUCTURE_ID), "weight": 1}],
        # tres rare : un seul edifice de cette taille par region
        "placement": {"type": "minecraft:random_spread", "spacing": 96,
                      "separation": 40, "salt": 771204533},
    })
    write_json(os.path.join(DATA, "tags", "worldgen", "biome", "has_structure",
                            "arcencium_cathedral.json"),
               {"replace": False,
                "values": ["minecraft:swamp", "minecraft:mangrove_swamp",
                           "minecraft:dark_forest"]})


def validate(made, pools):
    import re
    problems = []
    names = {m[0] for m in made}
    pool_set = set(pools)
    for base, _, files in os.walk(os.path.join(DATA, "worldgen", "template_pool", "cathedral")):
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
                if "cathedral" not in os.path.join(base, f):
                    continue
                for ns in set(ref.findall(open(os.path.join(base, f), encoding="utf-8").read())):
                    if ns not in ("minecraft", NS):
                        problems.append("namespace etranger '%s:' dans %s" % (ns, f))
    return problems


def main():
    print("=== Cathedrale d'Arcencium ===")
    print("  conversion (944 000 blocs, patience) :")
    made, unmapped = convert_pieces()
    pools = convert_pools(made)
    write_loot_tables()
    write_structure_files()
    problems = validate(made, pools)
    tot = sum(m[2] for m in made)
    left = sum(unmapped.values())
    print("  pools  : %s" % ", ".join(p.split("/")[-1] for p in pools))
    print("  butin  : %s" % ", ".join(sorted(LOOT)))
    print("  converti : %.1f%% du volume" % (100 * (tot - left) / tot))
    print("  laisse vanilla : %d blocs, %d types" % (left, len(unmapped)))
    for k, v in sorted(unmapped.items(), key=lambda kv: -kv[1])[:10]:
        print("     %-46s %6d" % (k, v))
    print("  validation : %s" % ("OK, toutes les references resolvent"
                                 if not problems else "%d PROBLEMES" % len(problems)))
    for p in problems[:15]:
        print("     ", p)
    print()
    print("  En jeu :  /locate structure %s:%s" % (NS, STRUCTURE_ID))


if __name__ == "__main__":
    main()
