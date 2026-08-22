#!/usr/bin/env python3
"""
Assemble le datapack du village d'Arcencium (systeme Jigsaw) a partir du
village taiga de CTOV, rehabille avec notre palette.

Produit, sous src/main/resources/data/emeraldweapons/ :
  structure/<theme>_<piece>.nbt                 (via import_village / reskin)
  worldgen/template_pool/village/taiga/*.json   pools : centre, rues, maisons,
  worldgen/template_pool/village/common/*.json   deco, terminateurs, commun
  worldgen/structure/arcencium_village_taiga.json
  worldgen/structure_set/arcencium_villages.json
  tags/worldgen/biome/has_structure/arcencium_village.json

Les pools CTOV sont copies et reecrits : namespace -> le notre, structures
-> nos .nbt rehabilles, processeurs -> vides (les leurs fissurent du deepslate
qui n'existe plus apres reskin). Toute structure referencee mais pas encore
importee l'est a la volee, pour qu'aucune reference ne pende.

Usage :
    python tools/make_village.py
En jeu :  /locate structure emeraldweapons:arcencium_village_taiga
"""

import json
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_structure as nbtlib
import reskin
import import_village

ROOT = nbtlib.ROOT
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons")
STRUCT_DIR = os.path.join(DATA, "structure")
POOL_DIR = os.path.join(DATA, "worldgen", "template_pool", "village")
NS = "emeraldweapons"
THEMES = ("taiga", "common")
STRUCTURE_ID = "arcencium_village_taiga"
BIOMES = ["minecraft:taiga", "minecraft:old_growth_pine_taiga",
          "minecraft:old_growth_spruce_taiga", "minecraft:snowy_taiga"]


def our_name(theme, rel):
    return "%s_%s" % (theme, rel.replace("/", "_"))


def ensure_structure(ctov_loc, imported):
    """ctov:village/<theme>/<rel> -> emeraldweapons:<theme>_<rel>, en
    important la piece a la volee si elle manque."""
    rest = ctov_loc[len("ctov:village/"):]
    theme, rel = rest.split("/", 1)
    name = our_name(theme, rel)
    if name not in imported:
        try:
            st = nbtlib.load("data/ctov/structure/village/%s.nbt" % rest, "ctov")
        except Exception:
            return None
        root, _ = reskin.reskin_structure(st)
        with open(os.path.join(STRUCT_DIR, name + ".nbt"), "wb") as f:
            f.write(nbtlib.serialize(root))
        imported.add(name)
    return "%s:%s" % (NS, name)


def convert_pools(imported):
    jp = nbtlib.find_jar("ctov")
    written = 0
    dropped = []
    with zipfile.ZipFile(jp) as z:
        for n in z.namelist():
            if not (n.startswith("data/ctov/worldgen/template_pool/village/")
                    and n.endswith(".json")):
                continue
            rel = n[len("data/ctov/worldgen/template_pool/village/"):-5]
            theme = rel.split("/")[0]
            if theme not in THEMES:
                continue
            src = json.loads(z.read(n))
            elements = []
            for e in src.get("elements", []):
                el = dict(e["element"])
                loc = el.get("location")
                if loc:
                    if loc.startswith("ctov:village/"):
                        new = ensure_structure(loc, imported)
                        if not new:
                            dropped.append(loc)
                            continue
                        el["location"] = new
                    elif not loc.startswith("minecraft:"):
                        dropped.append(loc)
                        continue
                if "processors" in el:
                    el["processors"] = "minecraft:empty"
                # Elements "feature" (decoration par placed feature) : une
                # feature d'un autre mod n'est pas chargee hors du modpack ->
                # "Unbound values in registry placed_feature". On rabat sur
                # un equivalent vanilla, sinon on ecarte l'element.
                feat = el.get("feature")
                if feat and not feat.startswith("minecraft:"):
                    if "flower" in feat:
                        el["feature"] = "minecraft:flower_plain"
                    elif "tree" in feat or "spruce" in feat or "pine" in feat:
                        el["feature"] = "minecraft:spruce"
                    else:
                        dropped.append(feat)
                        continue
                elements.append({"weight": e.get("weight", 1), "element": el})
            fallback = reskin.map_pool(src.get("fallback", "minecraft:empty"))
            out = {"fallback": fallback, "elements": elements}
            dest = os.path.join(POOL_DIR, rel + ".json")
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, "w", encoding="utf-8") as f:
                json.dump(out, f, indent=2)
            written += 1
    return written, dropped


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def write_structure_files():
    write_json(os.path.join(DATA, "worldgen", "structure", STRUCTURE_ID + ".json"), {
        "type": "minecraft:jigsaw",
        "biomes": "#%s:has_structure/arcencium_village" % NS,
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": "beard_thin",
        "start_pool": "%s:village/taiga/town_centers" % NS,
        "size": 6,
        "start_height": {"absolute": 0},
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 80,
        "use_expansion_hack": True,
    })
    write_json(os.path.join(DATA, "worldgen", "structure_set", "arcencium_villages.json"), {
        "structures": [{"structure": "%s:%s" % (NS, STRUCTURE_ID), "weight": 1}],
        "placement": {
            "type": "minecraft:random_spread",
            "spacing": 28,
            "separation": 10,
            "salt": 1519453651,
        },
    })
    write_json(os.path.join(DATA, "tags", "worldgen", "biome", "has_structure",
                            "arcencium_village.json"),
               {"replace": False, "values": BIOMES})


def validate(imported):
    """Chaque pool referencee par un jigsaw doit exister ; chaque structure
    referencee par une pool doit exister."""
    pool_files = set()
    for base, _, files in os.walk(POOL_DIR):
        for f in files:
            rel = os.path.relpath(os.path.join(base, f), POOL_DIR)
            pool_files.add("%s:village/%s" % (NS, rel[:-5].replace(os.sep, "/")))
    problems = []
    for base, _, files in os.walk(POOL_DIR):
        for f in files:
            d = json.load(open(os.path.join(base, f), encoding="utf-8"))
            for e in d["elements"]:
                loc = e["element"].get("location", "")
                if loc.startswith(NS + ":") and loc.split(":", 1)[1] not in imported:
                    problems.append("structure manquante : " + loc)
            fb = d.get("fallback", "")
            if fb.startswith(NS + ":") and fb not in pool_files:
                problems.append("fallback manquant : " + fb)
    for name in sorted(imported):
        st = nbtlib.load(os.path.join(STRUCT_DIR, name + ".nbt"))
        for tag in st.block_nbt.values():
            p = str(tag.get("pool", ""))
            if p.startswith(NS + ":") and p not in pool_files:
                problems.append("pool jigsaw manquante : %s (dans %s)" % (p, name))
    # Toute reference a un autre namespace (ctov:, monobank:...) serait un
    # "Unbound values in registry" au chargement du monde hors modpack.
    import re
    ref = re.compile(r'"([a-z_]+):[a-z0-9_/.]+"')
    for base, _, files in os.walk(os.path.join(DATA, "worldgen")):
        for f in files:
            txt = open(os.path.join(base, f), encoding="utf-8").read()
            for ns in set(ref.findall(txt)):
                if ns not in ("minecraft", NS):
                    problems.append("namespace etranger '%s:' dans %s" % (ns, f))
    return pool_files, problems


def main():
    os.makedirs(STRUCT_DIR, exist_ok=True)
    imported = set()
    for theme in THEMES:
        done = import_village.import_theme(theme=theme, jar="ctov")
        imported.update(n for n, _, _ in done)
    n_pools, dropped = convert_pools(imported)
    write_structure_files()
    pool_files, problems = validate(imported)
    print()
    print("=== datapack village ===")
    print("  structures : %d" % len(imported))
    print("  pools      : %d  (%s)" % (n_pools, ", ".join(sorted(
        p.split("village/")[1] for p in pool_files))[:300]))
    if dropped:
        print("  elements ecartes (autres mods) : %d" % len(dropped))
    print("  structure  : %s:%s" % (NS, STRUCTURE_ID))
    if problems:
        print("  PROBLEMES :")
        for p in problems[:20]:
            print("     ", p)
    else:
        print("  validation : toutes les references resolvent")
    print()
    print("  En jeu (creatif) :  /locate structure %s:%s" % (NS, STRUCTURE_ID))


if __name__ == "__main__":
    main()
