#!/usr/bin/env python3
"""
Re-habillage de structures : remplace les blocs d'un batiment existant par
ceux de notre palette, en conservant la geometrie.

Le village taiga de CTOV sert de base : sa composition tombe presque 1:1 sur
notre palette (bois sombre + pierre sombre + lanternes). On garde donc leur
architecture -- proportions, toits, avancees, ameublement -- et on substitue
la matiere.

POINT DELICAT : un blockstate ne peut porter que les proprietes que son bloc
declare. Mapper grass_block[snowy=false] vers notre prismatic_grass_block
(un Block simple, sans propriete) produirait un etat invalide et Minecraft
refuserait la structure. On filtre donc les proprietes selon le bloc cible.

Usage :
    python tools/reskin.py <structure> [--jar ctov] [--out nom] [--render]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_structure as nbtlib

ROOT = nbtlib.ROOT
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "data",
                       "emeraldweapons", "structure")
MOD = "emeraldweapons:"

# ----------------------------------------------------------- correspondance
# Regle de dosage : la masse (bois + gangue) porte le batiment, l'Arcencium
# et les couleurs restent des accents.

MAP = {
    # --- bois : tout le resineux devient bois cristallise
    "minecraft:spruce_planks": MOD + "crystal_planks",
    "minecraft:spruce_stairs": MOD + "crystal_stairs",
    "minecraft:spruce_slab": MOD + "crystal_slab",
    "minecraft:spruce_fence": MOD + "crystal_fence",
    "minecraft:spruce_log": MOD + "prism_log",
    "minecraft:stripped_spruce_log": MOD + "prism_log",
    "minecraft:spruce_wood": MOD + "prism_log",
    "minecraft:spruce_leaves": MOD + "prism_leaves",
    "minecraft:spruce_sapling": MOD + "prism_sapling",
    "minecraft:dark_oak_planks": MOD + "crystal_planks",
    "minecraft:dark_oak_stairs": MOD + "crystal_stairs",
    "minecraft:dark_oak_slab": MOD + "crystal_slab",
    "minecraft:dark_oak_fence": MOD + "crystal_fence",
    "minecraft:dark_oak_log": MOD + "prism_log",
    "minecraft:dark_oak_leaves": MOD + "prism_leaves",
    "minecraft:oak_planks": MOD + "crystal_planks",
    "minecraft:oak_stairs": MOD + "crystal_stairs",
    "minecraft:oak_slab": MOD + "crystal_slab",
    "minecraft:oak_fence": MOD + "crystal_fence",
    "minecraft:oak_log": MOD + "prism_log",
    "minecraft:oak_leaves": MOD + "prism_leaves",

    # --- pierre : le deepslate (sombre) devient notre gangue
    "minecraft:deepslate_bricks": MOD + "gangue_bricks",
    "minecraft:deepslate_brick_stairs": MOD + "gangue_brick_stairs",
    "minecraft:deepslate_brick_slab": MOD + "gangue_brick_slab",
    "minecraft:deepslate_brick_wall": MOD + "gangue_brick_wall",
    "minecraft:cobbled_deepslate": MOD + "gangue_stone",
    "minecraft:cobbled_deepslate_stairs": MOD + "gangue_stone_stairs",
    "minecraft:cobbled_deepslate_slab": MOD + "gangue_stone_slab",
    "minecraft:cobbled_deepslate_wall": MOD + "gangue_stone_wall",
    "minecraft:deepslate": MOD + "gangue_stone",
    "minecraft:polished_deepslate": MOD + "polished_gangue",
    "minecraft:polished_deepslate_stairs": MOD + "polished_gangue_stairs",
    "minecraft:polished_deepslate_slab": MOD + "polished_gangue_slab",
    "minecraft:stone_bricks": MOD + "gangue_bricks",
    "minecraft:stone_brick_stairs": MOD + "gangue_brick_stairs",
    "minecraft:stone_brick_slab": MOD + "gangue_brick_slab",
    "minecraft:stone_brick_wall": MOD + "gangue_brick_wall",
    "minecraft:cobblestone": MOD + "gangue_stone",
    "minecraft:cobblestone_stairs": MOD + "gangue_stone_stairs",
    "minecraft:cobblestone_slab": MOD + "gangue_stone_slab",
    "minecraft:cobblestone_wall": MOD + "gangue_stone_wall",
    "minecraft:stone": MOD + "gangue_stone",
    "minecraft:andesite": MOD + "gangue_stone",
    "minecraft:calcite": MOD + "polished_gangue",
    "minecraft:smooth_stone": MOD + "polished_gangue",

    # --- l'accent noble : les finitions les plus soignees, ~2% du volume
    "minecraft:deepslate_tiles": MOD + "arcencium_bricks",
    "minecraft:deepslate_tile_stairs": MOD + "arcencium_brick_stairs",
    "minecraft:deepslate_tile_slab": MOD + "arcencium_brick_slab",
    "minecraft:deepslate_tile_wall": MOD + "arcencium_brick_wall",
    "minecraft:chiseled_deepslate": MOD + "chiseled_arcencium",
    "minecraft:chiseled_stone_bricks": MOD + "chiseled_arcencium",

    # --- verre et lumiere
    "minecraft:glass": MOD + "prismatic_glass",
    "minecraft:glass_pane": MOD + "prismatic_glass_pane",
    "minecraft:white_stained_glass": MOD + "prismatic_glass",
    "minecraft:white_stained_glass_pane": MOD + "prismatic_glass_pane",
    "minecraft:light_blue_stained_glass": MOD + "prismatic_glass",
    "minecraft:light_blue_stained_glass_pane": MOD + "prismatic_glass_pane",
    "minecraft:brown_stained_glass_pane": MOD + "prismatic_glass_pane",
    "minecraft:lantern": MOD + "arcencium_lantern",
    "minecraft:green_stained_glass": MOD + "prismatic_glass",
    "minecraft:green_stained_glass_pane": MOD + "prismatic_glass_pane",
    "minecraft:gray_stained_glass_pane": MOD + "prismatic_glass_pane",
    "minecraft:black_stained_glass_pane": MOD + "prismatic_glass_pane",
    "minecraft:tinted_glass": MOD + "prismatic_glass",
    # blocs d'autres mods du pack : cadres sombres -> notre materiau noble
    "create:andesite_casing": MOD + "arcencium_bricks",
    "create:andesite_alloy_block": MOD + "arcencium_bricks",
    "minecraft:soul_lantern": MOD + "arcencium_lantern",

    # --- sol et vegetation
    "minecraft:grass_block": MOD + "prismatic_grass_block",
    "minecraft:podzol": MOD + "prismatic_grass_block",
    "minecraft:short_grass": MOD + "prism_tuft",
    "minecraft:fern": MOD + "prism_tuft",
    "minecraft:poppy": MOD + "prism_bloom",
    "minecraft:dandelion": MOD + "prism_bloom",
    "minecraft:cornflower": MOD + "prism_bloom",
    "minecraft:oxeye_daisy": MOD + "prism_bloom",
    "minecraft:azure_bluet": MOD + "prism_bloom",
    "minecraft:allium": MOD + "prism_bloom",

    # --- textile : les teintures a l'arcencium
    "minecraft:white_wool": MOD + "ecru_wool",
    "minecraft:light_gray_wool": MOD + "ecru_wool",
    "minecraft:brown_wool": MOD + "ochre_wool",
    "minecraft:yellow_wool": MOD + "ochre_wool",
    "minecraft:orange_wool": MOD + "ochre_wool",
    "minecraft:red_wool": MOD + "old_rose_wool",
    "minecraft:pink_wool": MOD + "old_rose_wool",
    "minecraft:magenta_wool": MOD + "old_rose_wool",
    "minecraft:green_wool": MOD + "verdigris_wool",
    "minecraft:lime_wool": MOD + "verdigris_wool",
    "minecraft:cyan_wool": MOD + "verdigris_wool",
    "minecraft:blue_wool": MOD + "slate_blue_wool",
    "minecraft:light_blue_wool": MOD + "slate_blue_wool",
    "minecraft:purple_wool": MOD + "slate_blue_wool",
    # --- boue et terre cuite : la masse des grands edifices
    "minecraft:mud_bricks": MOD + "gangue_bricks",
    "minecraft:mud_brick_stairs": MOD + "gangue_brick_stairs",
    "minecraft:mud_brick_slab": MOD + "gangue_brick_slab",
    "minecraft:mud_brick_wall": MOD + "gangue_brick_wall",
    "minecraft:packed_mud": MOD + "gangue_stone",
    "minecraft:mud": MOD + "gangue_stone",
    "minecraft:black_terracotta": MOD + "arcencium_bricks",
    "minecraft:gray_terracotta": MOD + "polished_gangue",
    "minecraft:light_gray_terracotta": MOD + "polished_gangue",
    "minecraft:green_terracotta": MOD + "veined_stone",
    "minecraft:lime_terracotta": MOD + "veined_stone",
    "minecraft:brown_terracotta": MOD + "gangue_bricks",
    "minecraft:white_terracotta": MOD + "polished_gangue",
    "minecraft:black_concrete": MOD + "arcencium_bricks",
    "minecraft:green_concrete": MOD + "veined_stone",
    # --- quartz, basalte : finitions claires et sombres
    "minecraft:quartz_block": MOD + "polished_gangue",
    "minecraft:smooth_quartz": MOD + "polished_gangue",
    "minecraft:smooth_quartz_stairs": MOD + "polished_gangue_stairs",
    "minecraft:smooth_quartz_slab": MOD + "polished_gangue_slab",
    "minecraft:quartz_bricks": MOD + "gangue_bricks",
    "minecraft:quartz_pillar": MOD + "polished_gangue",
    "minecraft:quartz_stairs": MOD + "polished_gangue_stairs",
    "minecraft:chiseled_sandstone": MOD + "gangue_bricks",
    "minecraft:sandstone": MOD + "gangue_stone",
    "minecraft:sandstone_slab": MOD + "gangue_stone_slab",
    "minecraft:smooth_sandstone_slab": MOD + "polished_gangue_slab",
    "minecraft:basalt": MOD + "corrupted_bricks",
    "minecraft:polished_basalt": MOD + "corrupted_bricks",
    "minecraft:smooth_basalt": MOD + "corrupted_bricks",
    "minecraft:stone_slab": MOD + "gangue_stone_slab",
    "minecraft:stone_stairs": MOD + "gangue_stone_stairs",
    "minecraft:red_carpet": MOD + "old_rose_carpet",
    # --- feuillages : notre feuillage de Prisme
    "minecraft:acacia_leaves": MOD + "prism_leaves",
    "minecraft:birch_leaves": MOD + "prism_leaves",
    "minecraft:mangrove_leaves": MOD + "prism_leaves",
    "minecraft:azalea_leaves": MOD + "prism_leaves",
    "minecraft:flowering_azalea_leaves": MOD + "prism_leaves",

    # --- pierres sombres et infernales : notre materiau corrompu
    "minecraft:nether_bricks": MOD + "corrupted_bricks",
    "minecraft:nether_brick_stairs": MOD + "corrupted_brick_stairs",
    "minecraft:nether_brick_slab": MOD + "corrupted_brick_slab",
    "minecraft:nether_brick_wall": MOD + "corrupted_brick_wall",
    "minecraft:blackstone": MOD + "corrupted_bricks",
    "minecraft:blackstone_wall": MOD + "corrupted_brick_wall",
    "minecraft:polished_blackstone_bricks": MOD + "arcencium_bricks",
    "minecraft:polished_blackstone_brick_stairs": MOD + "arcencium_brick_stairs",
    "minecraft:polished_blackstone_brick_slab": MOD + "arcencium_brick_slab",
    "minecraft:polished_blackstone_brick_wall": MOD + "arcencium_brick_wall",
    # --- prismarine : la coque sombre et minerale -> notre materiau noble
    "minecraft:dark_prismarine": MOD + "arcencium_bricks",
    "minecraft:dark_prismarine_stairs": MOD + "arcencium_brick_stairs",
    "minecraft:dark_prismarine_slab": MOD + "arcencium_brick_slab",
    "minecraft:prismarine_bricks": MOD + "arcencium_bricks",
    # --- terre cuite coloree : accents -> pierre veinee
    "minecraft:cyan_terracotta": MOD + "veined_stone",
    "minecraft:red_terracotta": MOD + "veined_stone",
    "minecraft:terracotta": MOD + "veined_stone",
    # --- pierres claires et gres : la masse
    "minecraft:polished_andesite": MOD + "polished_gangue",
    "minecraft:polished_andesite_slab": MOD + "polished_gangue_slab",
    "minecraft:polished_andesite_stairs": MOD + "polished_gangue_stairs",
    "minecraft:clay": MOD + "polished_gangue",
    "minecraft:gray_concrete": MOD + "polished_gangue",
    "minecraft:smooth_stone_slab": MOD + "polished_gangue_slab",
    "minecraft:cut_sandstone": MOD + "gangue_bricks",
    "minecraft:cut_sandstone_slab": MOD + "gangue_brick_slab",
    "minecraft:smooth_sandstone": MOD + "polished_gangue",
    "minecraft:smooth_sandstone_stairs": MOD + "polished_gangue_stairs",
    "minecraft:smooth_red_sandstone": MOD + "veined_stone",
    "minecraft:smooth_red_sandstone_stairs": MOD + "veined_stone_stairs",
    "minecraft:rooted_dirt": "minecraft:dirt",
    "minecraft:coarse_dirt": "minecraft:dirt",
    # --- lumiere : shroomlight -> notre bloc noble lumineux
    "minecraft:shroomlight": MOD + "chiseled_arcencium",
    "minecraft:sea_lantern": MOD + "chiseled_arcencium",
    "minecraft:glowstone": MOD + "chiseled_arcencium",
    # --- vitraux colores : notre verre prismatique
    "minecraft:red_stained_glass": MOD + "prismatic_glass",
    "minecraft:orange_stained_glass": MOD + "prismatic_glass",
    "minecraft:yellow_stained_glass": MOD + "prismatic_glass",
    "minecraft:purple_stained_glass": MOD + "prismatic_glass",
    "minecraft:red_stained_glass_pane": MOD + "prismatic_glass_pane",
    "minecraft:orange_stained_glass_pane": MOD + "prismatic_glass_pane",
    "minecraft:yellow_stained_glass_pane": MOD + "prismatic_glass_pane",
    # --- laines et tapis restants
    "minecraft:gray_wool": MOD + "slate_blue_wool",
    "minecraft:black_wool": MOD + "slate_blue_wool",
    "minecraft:purple_wool": MOD + "slate_blue_wool",
    "minecraft:black_carpet": MOD + "slate_blue_carpet",
    "minecraft:gray_carpet": MOD + "slate_blue_carpet",
    "minecraft:white_carpet": MOD + "ecru_carpet",
    "minecraft:light_gray_carpet": MOD + "ecru_carpet",
    "minecraft:brown_carpet": MOD + "ochre_carpet",
    "minecraft:yellow_carpet": MOD + "ochre_carpet",
    "minecraft:orange_carpet": MOD + "ochre_carpet",
    "minecraft:red_carpet": MOD + "old_rose_carpet",
    "minecraft:pink_carpet": MOD + "old_rose_carpet",
    "minecraft:green_carpet": MOD + "verdigris_carpet",
    "minecraft:lime_carpet": MOD + "verdigris_carpet",
    "minecraft:cyan_carpet": MOD + "verdigris_carpet",
    "minecraft:blue_carpet": MOD + "slate_blue_carpet",
    "minecraft:light_blue_carpet": MOD + "slate_blue_carpet",
}

# Proprietes acceptees par nos blocs. Un etat portant une propriete non
# declaree serait invalide : on filtre.
PROPS_OK = {
    "stairs": {"facing", "half", "shape", "waterlogged"},
    "slab": {"type", "waterlogged"},
    "wall": {"up", "north", "east", "south", "west", "waterlogged"},
    "fence": {"north", "east", "south", "west", "waterlogged"},
    "pane": {"north", "east", "south", "west", "waterlogged"},
    "lantern": {"hanging", "waterlogged"},
    "log": {"axis"},
    "leaves": {"distance", "persistent", "waterlogged"},
    "sapling": {"stage"},
    "plain": set(),          # Block simple : aucune propriete
}


def kind_of(block_id):
    n = block_id.split(":", 1)[-1]
    if n.endswith("_stairs"):
        return "stairs"
    if n.endswith("_slab"):
        return "slab"
    if n.endswith("_wall"):
        return "wall"
    if n.endswith("_fence"):
        return "fence"
    if n.endswith("_pane"):
        return "pane"
    if n.endswith("lantern"):
        return "lantern"
    if n == "prism_log":
        return "log"
    if n == "prism_leaves":
        return "leaves"
    if n == "prism_sapling":
        return "sapling"
    return "plain"


# ------------------------------------------------------ blocs jigsaw
# Les structures CTOV portent des blocs jigsaw dont le champ "pool" pointe
# vers les pools ctov:. Pour que NOS villages s'assemblent, on les redirige
# vers nos propres pools (memes noms, namespace emeraldweapons), et vers les
# pools vanilla pour les PNJ / golems / chats. Les pools d'autres mods
# (monobank, incubation...) deviennent vides : ils peuvent ne pas etre la.

VANILLA_OK = ("minecraft:village/", "minecraft:empty")

# Redirections posees par l'appelant avant conversion : prefixe d'un autre
# mod -> le notre (ex. la citadelle celeste, cf. tools/make_citadel.py).
POOL_PREFIX = {}
LOOT_MAP = {}


def map_pool(pool):
    for src, dst in POOL_PREFIX.items():
        if pool.startswith(src):
            return dst + pool[len(src):]
    if pool.startswith("ctov:village/"):
        rest = pool[len("ctov:village/"):]            # taiga/house, common/pet...
        theme = rest.split("/")[0]
        if theme in ("taiga", "common"):
            return "emeraldweapons:village/" + rest
        if rest.endswith("/house"):
            return "emeraldweapons:village/taiga/house"
        if "villager" in rest:
            return "minecraft:village/taiga/villagers"
        return "minecraft:empty"
    if pool == "minecraft:village/plains/streets":
        return "emeraldweapons:village/taiga/roads"
    if pool.startswith(VANILLA_OK):
        return pool
    return "minecraft:empty"


def map_final_state(state, mapping):
    """'minecraft:spruce_stairs[facing=east]' -> notre bloc, proprietes filtrees."""
    name, _, rest = state.partition("[")
    target = mapping.get(name)
    if not target:
        return state
    if not rest:
        return target
    props = dict(kv.split("=", 1) for kv in rest.rstrip("]").split(",") if "=" in kv)
    allowed = PROPS_OK[kind_of(target)]
    props = {k: v for k, v in props.items() if k in allowed}
    if not props:
        return target
    return target + "[" + ",".join("%s=%s" % kv for kv in sorted(props.items())) + "]"


def rewrite_block_nbt(tag, mapping):
    """Copie du NBT d'un bloc : pools et final_state des jigsaw rediriges,
    tables de butin remappees. Les spawners sont laisses tels quels (leurs
    entites sont vanilla, avec attributs et equipement personnalises)."""
    out = None
    if "pool" in tag:
        out = dict(tag)
        out["pool"] = map_pool(str(tag["pool"]))
        if "final_state" in tag:
            out["final_state"] = map_final_state(str(tag["final_state"]), mapping)
    if "LootTable" in tag:
        lt = str(tag["LootTable"])
        for src, dst in LOOT_MAP.items():
            if lt.startswith(src):
                out = dict(out or tag)
                out["LootTable"] = dst
                break
    return out if out is not None else tag


def reskin_structure(struct, mapping=None):
    """Retourne (racine NBT rehabillee, statistiques)."""
    mapping = mapping or MAP
    root = struct.root
    old_pal = struct.palette
    new_entries = []
    index_of = {}
    remap = {}
    changed = 0

    for i, (name, props) in enumerate(old_pal):
        target = mapping.get(name)
        if target:
            allowed = PROPS_OK[kind_of(target)]
            props = {k: v for k, v in props.items() if k in allowed}
            name = target
            changed += 1
        key = (name, tuple(sorted(props.items())))
        if key not in index_of:
            index_of[key] = len(new_entries)
            entry = {"Name": name}
            if props:
                entry["Properties"] = dict(props)
            new_entries.append(entry)
        remap[i] = index_of[key]

    blocks = nbtlib.TypedList([], nbtlib.TAG_COMPOUND)
    n_blocks = 0
    for b in root.get("blocks", []):
        state = int(b["state"])
        if state >= len(old_pal):
            continue                      # etat incoherent : voir Structure
        nb_entry = {"pos": nbtlib.TypedList([int(v) for v in b["pos"]], nbtlib.TAG_INT),
                    "state": remap[state]}
        if "nbt" in b:
            nb_entry["nbt"] = rewrite_block_nbt(b["nbt"], mapping)
        blocks.append(nb_entry)
        n_blocks += 1

    palette = nbtlib.TypedList(new_entries, nbtlib.TAG_COMPOUND)
    out = {
        "size": nbtlib.TypedList([int(v) for v in struct.size], nbtlib.TAG_INT),
        "palette": palette,
        "blocks": blocks,
        "entities": root.get("entities", nbtlib.TypedList([], nbtlib.TAG_COMPOUND)),
        "DataVersion": int(root.get("DataVersion", 3955)),
    }
    stats = {
        "palette_avant": len(old_pal),
        "palette_apres": len(new_entries),
        "entrees_converties": changed,
        "blocs": n_blocks,
    }
    return out, stats


def unmapped(struct, mapping=None):
    """Blocs non convertis, tries par frequence : sert a completer MAP."""
    from collections import Counter
    mapping = mapping or MAP
    c = Counter()
    for name in struct.solid_cells().values():
        if name not in mapping and name != "minecraft:jigsaw":
            c[name] += 1
    return c


def convert(struct_name, jar=None, out_name=None, write=True):
    st = nbtlib.load(struct_name, jar)
    root, stats = reskin_structure(st)
    out_name = out_name or os.path.basename(struct_name).replace(".nbt", "")
    if write:
        os.makedirs(OUT_DIR, exist_ok=True)
        path = os.path.join(OUT_DIR, out_name + ".nbt")
        with open(path, "wb") as f:
            f.write(nbtlib.serialize(root))
        print("Ecrit :", os.path.relpath(path, ROOT))
    print("  taille %s | %d blocs | palette %d -> %d (%d entrees converties)"
          % (str(st.size), stats["blocs"], stats["palette_avant"],
             stats["palette_apres"], stats["entrees_converties"]))
    rest = unmapped(st)
    if rest:
        tot = sum(st.counts().values())
        left = sum(rest.values())
        print("  non converti : %d blocs (%.1f%%), %d types" % (left, 100 * left / tot, len(rest)))
        for n, c in rest.most_common(12):
            print("      %-46s %5d" % (n, c))
    return root, stats


if __name__ == "__main__":
    argv = sys.argv[1:]
    jar_arg = None
    if "--jar" in argv:
        i = argv.index("--jar")
        jar_arg = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    out_arg = None
    if "--out" in argv:
        i = argv.index("--out")
        out_arg = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    args = [a for a in argv if not a.startswith("--")]
    convert(args[0], jar_arg, out_arg, write="--dry" not in argv)
